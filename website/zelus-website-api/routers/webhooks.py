"""
Webhook handlers for Stripe, PayPal, Tebex, and NOWPayments.

Security contract:
  - Stripe:  signature is verified with stripe.Webhook.construct_event() using the
             endpoint secret from the Stripe dashboard. Raw bytes are read BEFORE any
             JSON parsing — this is required for HMAC verification.
             Stripe also validates the webhook timestamp (default: 300s tolerance),
             which prevents replay attacks at the SDK level.
  - PayPal:  signature is verified by calling PayPal's
             POST /v1/notifications/verify-webhook-signature API. The webhook_id from
             the PayPal developer dashboard must be set in PAYPAL_WEBHOOK_ID.
             PayPal does NOT expire old webhook payloads, so our idempotency check
             is the sole replay-attack defense on the PayPal path.
  - Tebex:   signature is verified locally (no round-trip API call, unlike PayPal).
             Per docs.tebex.io/developers/webhooks/overview, the X-Signature header
             is HMAC-SHA256(key=TEBEX_SECRET, data=SHA256(raw_body).hexdigest()) --
             a hash-of-a-hash, NOT a direct HMAC of the body like Stripe's scheme.
             Tebex also sends a one-time "validation.webhook" event when the
             callback URL is first saved in the creator panel; that event carries
             no signature and must be echoed back (see tebex_webhook below) before
             Tebex will activate the endpoint and start sending real events.
             Unlike Stripe/PayPal, there is no /api/checkout/tebex session --
             the entire purchase flow happens on Tebex's own hosted store, so this
             webhook is the ONLY place a Tebex order is ever seen by our backend.
  NOWPayments: signature is verified locally. Per NOWPayments' IPN docs, the
             x-nowpayments-sig header is HMAC-SHA512(key=NOWPAYMENTS_IPN_SECRET,
             data=<compact JSON of the body with ALL keys, including nested ones,
             sorted alphabetically>) -- note this is a re-serialization of the
             parsed body, not the raw bytes as received (unlike Stripe/Tebex),
             so field order/whitespace in the actual HTTP request don't matter,
             only the parsed values do. Must use json.dumps(..., separators=(',',
             ':')) to match JS's compact JSON.stringify(), or the signature won't
             match even with the right secret.

  Race condition / replay defense (all four providers):
    1. The Transaction row is loaded with SELECT FOR UPDATE, acquiring a Postgres
       row-level lock. A second concurrent webhook for the same transaction blocks
       at this point until the first one commits and releases the lock.
    2. After acquiring the lock we check txn.status. If it is already COMPLETED
       the duplicate is discarded without any DB write.
    3. As a hard safety net, pending_claims.transaction_id has a UNIQUE constraint.
       If two webhooks somehow both pass steps 1-2 (e.g. a different DB isolation
       level), the second INSERT raises IntegrityError, is rolled back, and we
       return 200 so the provider does not retry.

  All DB writes (Transaction update + PendingClaim insert) are wrapped in a single
  transaction; any failure triggers a full rollback so no partial state is persisted.
  Non-IntegrityError exceptions re-raise so FastAPI returns 500, causing the payment
  provider to retry delivery.
"""
import hashlib
import hmac
import json
import logging
import os
from datetime import datetime, timezone

import httpx
import stripe
from fastapi import APIRouter, Depends, HTTPException, Request
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

import models
from database import get_db
from store_catalog import CATALOG, get_item

log = logging.getLogger("zelus.webhooks")

router = APIRouter(prefix="/api/webhooks", tags=["webhooks"])

STRIPE_WEBHOOK_SECRET = os.getenv("STRIPE_WEBHOOK_SECRET", "")
PAYPAL_WEBHOOK_ID     = os.getenv("PAYPAL_WEBHOOK_ID", "")
PAYPAL_CLIENT_ID      = os.getenv("PAYPAL_CLIENT_ID", "")
PAYPAL_CLIENT_SECRET  = os.getenv("PAYPAL_CLIENT_SECRET", "")
PAYPAL_BASE_URL       = os.getenv("PAYPAL_BASE_URL", "https://api-m.sandbox.paypal.com")
TEBEX_SECRET          = os.getenv("TEBEX_SECRET", "")
NOWPAYMENTS_IPN_SECRET = os.getenv("NOWPAYMENTS_IPN_SECRET", "")

# Tebex's own numeric package id (as it appears in subject.products[].id, always
# stringified here for dict-key consistency) -> our internal store_catalog slug.
# Tebex only assigns package ids once you create the package in the webstore
# creator panel, so this starts empty -- fill it in per package after creating
# each one on Tebex's side, e.g. "184672": "donator". Until a package id (or a
# name matching a catalog item, see _tebex_resolve_item's fallback) is mapped
# here, payments for it are logged and NOT fulfilled -- money taken with no
# item/rank granted is worse than a webhook that no-ops until configured.
TEBEX_PACKAGE_MAP: dict[str, str] = {
    "7618121": "dp_350",   # $5 Donator Bond
    "7618124": "dp_770",   # $10 Donator Bond
    "7618131": "dp_2180",  # $25 Donator Bond
    "7618132": "dp_4350",  # $50 Donator Bond
    "7618134": "dp_9450",  # $100 Donator Bond
}


# ── Shared fulfillment helper ──────────────────────────────────────────────────

def _fulfill(db: Session, txn: models.Transaction) -> None:
    """
    Inserts a PendingClaim and marks the Transaction as COMPLETED.
    Must be called within an active DB transaction (caller commits/rollbacks).
    The Transaction row MUST already be locked with SELECT FOR UPDATE by the caller.
    """
    tokens = (get_item(txn.package_id) or type("_", (), {"tokens": 0})()).tokens
    claim = models.PendingClaim(
        username=txn.username,
        package_id=txn.package_id,
        package_name=txn.package_name,
        tokens_to_give=tokens,
        transaction_id=txn.id,
        claimed_status="unclaimed",
    )
    db.add(claim)
    txn.status = models.TransactionStatus.COMPLETED.value
    txn.completed_at = datetime.now(timezone.utc)
    log.info(
        "[Fulfill] PendingClaim written — user='%s' pkg='%s' tokens=%d txn_id=%d",
        txn.username, txn.package_id, tokens, txn.id,
    )


# ── Stripe webhook ─────────────────────────────────────────────────────────────

@router.post("/stripe")
async def stripe_webhook(request: Request, db: Session = Depends(get_db)):
    if not STRIPE_WEBHOOK_SECRET:
        log.error("[Stripe Webhook] STRIPE_WEBHOOK_SECRET is not set — rejecting request.")
        raise HTTPException(500, "Webhook endpoint is not configured.")

    # Raw bytes MUST be read before any JSON parsing for HMAC to match.
    raw_body   = await request.body()
    sig_header = request.headers.get("stripe-signature", "")

    try:
        # construct_event also validates the timestamp (default tolerance: 300s),
        # which prevents replay attacks at the SDK level.
        event = stripe.Webhook.construct_event(raw_body, sig_header, STRIPE_WEBHOOK_SECRET)
    except stripe.error.SignatureVerificationError:
        log.warning("[Stripe Webhook] Signature mismatch — possible spoofed request.")
        raise HTTPException(400, "Webhook signature verification failed.")
    except Exception as exc:
        log.error("[Stripe Webhook] Could not parse webhook: %s", exc)
        raise HTTPException(400, "Malformed webhook payload.")

    event_type = event["type"]
    event_id   = event["id"]
    log.info("[Stripe Webhook] Received event '%s' id=%s", event_type, event_id)

    if event_type == "checkout.session.completed":
        session = event["data"]["object"]
        await _stripe_session_completed(db, session, raw_payload=raw_body.decode("utf-8"))

    # Always return 200 so Stripe does not retry successfully processed events.
    return {"status": "received"}


async def _stripe_session_completed(db: Session, session: dict, raw_payload: str) -> None:
    session_id     = session.get("id", "")
    payment_status = session.get("payment_status", "")

    log.info("[Stripe] checkout.session.completed — session=%s payment_status=%s",
             session_id, payment_status)

    if payment_status != "paid":
        log.info("[Stripe] Skipping session %s (payment_status=%s)", session_id, payment_status)
        return

    # ── Locate the pre-created transaction row, locking it immediately ─────────
    # SELECT FOR UPDATE acquires a Postgres row-level lock. If a second webhook
    # arrives at the same time, it blocks here until this transaction commits.
    txn = (
        db.query(models.Transaction)
        .filter(models.Transaction.provider_session_id == session_id)
        .with_for_update()
        .first()
    )

    if not txn:
        # Rare: webhook arrived before the checkout endpoint committed.
        # Reconstruct from Stripe metadata so the order is not lost.
        metadata   = session.get("metadata", {})
        username   = metadata.get("username")
        package_id = metadata.get("package_id")
        log.warning(
            "[Stripe] No pre-created transaction for session %s — rebuilding from metadata "
            "(username='%s' package_id='%s').",
            session_id, username, package_id,
        )
        if not username or not package_id:
            log.error("[Stripe] Cannot fulfill session %s — metadata is incomplete.", session_id)
            return
        item = get_item(package_id)
        if not item:
            log.error("[Stripe] Unknown package_id='%s' in session %s.", package_id, session_id)
            return
        txn = models.Transaction(
            username=username,
            package_id=item.slug,
            package_name=item.name,
            amount_usd=item.price_usd,
            provider=models.PaymentProvider.STRIPE.value,
            provider_session_id=session_id,
            status=models.TransactionStatus.PENDING.value,
            raw_webhook_payload=raw_payload,
        )
        db.add(txn)
        db.flush()  # assign txn.id; provider_session_id UNIQUE constraint fires here on dupe
    else:
        # ── Idempotency: row is locked — check status now that we hold the lock ─
        if txn.status == models.TransactionStatus.COMPLETED.value:
            log.info("[Stripe] Session %s already fulfilled — skipping duplicate delivery.", session_id)
            return
        txn.raw_webhook_payload = raw_payload

    # ── Atomic fulfill: insert claim + update transaction ─────────────────────
    try:
        _fulfill(db, txn)
        db.commit()
        log.info("[Stripe] Order fulfilled — user='%s' session=%s", txn.username, session_id)
    except IntegrityError:
        # UNIQUE constraint on pending_claims.transaction_id fired — another worker
        # fulfilled this order between our lock and our commit (extremely rare).
        # Roll back silently and return 200; Stripe will not retry.
        db.rollback()
        log.warning("[Stripe] IntegrityError on session %s — duplicate already fulfilled.", session_id)
    except Exception as exc:
        db.rollback()
        log.error("[Stripe] DB error fulfilling session %s: %s", session_id, exc)
        raise  # re-raise → FastAPI returns 500 → Stripe retries


# ── PayPal webhook ─────────────────────────────────────────────────────────────

@router.post("/paypal")
async def paypal_webhook(request: Request, db: Session = Depends(get_db)):
    if not PAYPAL_WEBHOOK_ID:
        log.error("[PayPal Webhook] PAYPAL_WEBHOOK_ID is not set — rejecting request.")
        raise HTTPException(500, "Webhook endpoint is not configured.")

    raw_body = await request.body()

    # ── Verify PayPal signature via their API ──────────────────────────────────
    verified = await _paypal_verify_signature(dict(request.headers), raw_body)
    if not verified:
        log.warning("[PayPal Webhook] Signature verification failed — possible spoofed request.")
        raise HTTPException(400, "Webhook signature verification failed.")

    try:
        event = json.loads(raw_body)
    except json.JSONDecodeError:
        raise HTTPException(400, "Malformed JSON payload.")

    event_type = event.get("event_type", "")
    event_id   = event.get("id", "")
    log.info("[PayPal Webhook] Received event '%s' id=%s", event_type, event_id)

    if event_type == "PAYMENT.CAPTURE.COMPLETED":
        await _paypal_capture_completed(db, event, raw_payload=raw_body.decode("utf-8"))

    return {"status": "received"}


async def _paypal_verify_signature(headers: dict, body: bytes) -> bool:
    """
    Calls PayPal's verify-webhook-signature REST endpoint.
    Returns True only when PayPal confirms the signature is valid.
    On any network or API error, returns False (fail-closed).
    Note: PayPal signature verification does NOT check event age, so a captured
    valid payload could be replayed. Our SELECT FOR UPDATE + status check + DB
    UNIQUE constraint are the replay defense for PayPal.
    """
    try:
        async with httpx.AsyncClient(timeout=10) as client:
            # Step 1 — get a short-lived access token
            token_resp = await client.post(
                f"{PAYPAL_BASE_URL}/v1/oauth2/token",
                auth=(PAYPAL_CLIENT_ID, PAYPAL_CLIENT_SECRET),
                data={"grant_type": "client_credentials"},
            )
            token_resp.raise_for_status()
            access_token = token_resp.json()["access_token"]

            # Step 2 — call PayPal's verification API
            verify_resp = await client.post(
                f"{PAYPAL_BASE_URL}/v1/notifications/verify-webhook-signature",
                headers={
                    "Authorization": f"Bearer {access_token}",
                    "Content-Type":  "application/json",
                },
                json={
                    "webhook_id":        PAYPAL_WEBHOOK_ID,
                    "transmission_id":   headers.get("paypal-transmission-id", ""),
                    "transmission_time": headers.get("paypal-transmission-time", ""),
                    "cert_url":          headers.get("paypal-cert-url", ""),
                    "auth_algo":         headers.get("paypal-auth-algo", ""),
                    "transmission_sig":  headers.get("paypal-transmission-sig", ""),
                    "webhook_event":     json.loads(body),
                },
            )
            verify_resp.raise_for_status()
            result = verify_resp.json()
            status = result.get("verification_status")
            if status != "SUCCESS":
                log.warning("[PayPal] Signature verification returned status='%s'", status)
                return False
            return True

    except Exception as exc:
        log.error("[PayPal] Signature verification API error: %s", exc)
        return False  # fail-closed: treat API errors as invalid


async def _paypal_capture_completed(db: Session, event: dict, raw_payload: str) -> None:
    resource       = event.get("resource", {})
    capture_id     = resource.get("id", "")
    capture_status = resource.get("status", "")

    log.info("[PayPal] PAYMENT.CAPTURE.COMPLETED — capture=%s status=%s",
             capture_id, capture_status)

    if capture_status != "COMPLETED":
        log.info("[PayPal] Ignoring capture %s (status=%s)", capture_id, capture_status)
        return

    # ── Locate the original transaction ───────────────────────────────────────
    # Primary lookup: order_id from supplementary_data (PayPal v2 REST standard)
    order_id = (
        resource.get("supplementary_data", {})
        .get("related_ids", {})
        .get("order_id")
    )
    txn = None
    if order_id:
        # SELECT FOR UPDATE locks the row — concurrent webhooks for the same order
        # will block here until the first one commits, then see status=COMPLETED.
        txn = (
            db.query(models.Transaction)
            .filter(models.Transaction.provider_session_id == order_id)
            .with_for_update()
            .first()
        )

    # Fallback: custom_id (set to str(txn.id) when the order was created)
    if not txn:
        custom_id = resource.get("custom_id") or resource.get("purchase_unit", {}).get("custom_id")
        if custom_id:
            try:
                txn = (
                    db.query(models.Transaction)
                    .filter(models.Transaction.id == int(custom_id))
                    .with_for_update()
                    .first()
                )
            except (ValueError, TypeError):
                pass

    if not txn:
        log.error(
            "[PayPal] Cannot locate transaction for capture %s "
            "(order_id=%s, custom_id=%s) — order NOT fulfilled.",
            capture_id,
            resource.get("supplementary_data", {}).get("related_ids", {}).get("order_id"),
            resource.get("custom_id"),
        )
        return

    # ── Idempotency: row is locked — check status now that we hold the lock ────
    if txn.status == models.TransactionStatus.COMPLETED.value:
        log.info("[PayPal] Transaction %d already fulfilled — skipping duplicate.", txn.id)
        return

    txn.raw_webhook_payload = raw_payload

    # ── Atomic fulfill ─────────────────────────────────────────────────────────
    try:
        _fulfill(db, txn)
        db.commit()
        log.info("[PayPal] Order fulfilled — user='%s' capture=%s txn_id=%d",
                 txn.username, capture_id, txn.id)
    except IntegrityError:
        # UNIQUE constraint on pending_claims.transaction_id fired — already fulfilled.
        db.rollback()
        log.warning("[PayPal] IntegrityError on capture %s — duplicate already fulfilled.", capture_id)
    except Exception as exc:
        db.rollback()
        log.error("[PayPal] DB error fulfilling capture %s: %s", capture_id, exc)
        raise  # re-raise → FastAPI returns 500 → PayPal retries


# ── Tebex webhook ───────────────────────────────────────────────────────────────

@router.post("/tebex")
async def tebex_webhook(request: Request, db: Session = Depends(get_db)):
    raw_body = await request.body()

    try:
        event = json.loads(raw_body)
    except json.JSONDecodeError:
        raise HTTPException(400, "Malformed JSON payload.")

    event_type = event.get("type", "")
    event_id   = event.get("id", "")

    # Tebex's one-time handshake, sent with no signature when the callback URL is
    # first saved in the creator panel. Must be echoed back verbatim with a 200
    # or Tebex marks the endpoint invalid and never sends real events to it.
    if event_type == "validation.webhook":
        log.info("[Tebex Webhook] Validation handshake received id=%s", event_id)
        return {"id": event_id}

    if not TEBEX_SECRET:
        log.error("[Tebex Webhook] TEBEX_SECRET is not set — rejecting request.")
        raise HTTPException(500, "Webhook endpoint is not configured.")

    signature = request.headers.get("x-signature", "")
    if not _tebex_verify_signature(raw_body, signature):
        log.warning("[Tebex Webhook] Signature mismatch — possible spoofed request.")
        raise HTTPException(400, "Webhook signature verification failed.")

    log.info("[Tebex Webhook] Received event '%s' id=%s", event_type, event_id)

    if event_type == "payment.completed":
        await _tebex_payment_completed(db, event.get("subject", {}), raw_payload=raw_body.decode("utf-8"))
    else:
        # payment.declined / payment.refunded / dispute.* / recurring-payment.* --
        # none of these grant anything; a refund/chargeback on an already-fulfilled
        # order is a manual staff review case (revoking a rank isn't automated),
        # not something to silently reverse here.
        log.info("[Tebex Webhook] No fulfillment action for event type '%s' (id=%s).", event_type, event_id)

    # Always 2xx so Tebex doesn't treat a no-op event as a delivery failure and retry it.
    return {"status": "received"}


def _tebex_verify_signature(raw_body: bytes, signature: str) -> bool:
    """
    Tebex's scheme (docs.tebex.io/developers/webhooks/overview): HMAC-SHA256 of the
    SHA256 hash of the raw body, keyed with TEBEX_SECRET -- not a direct HMAC of the
    body like Stripe/PayPal use, so their verification helpers can't be reused here.
    """
    if not signature:
        return False
    body_hash = hashlib.sha256(raw_body).hexdigest()
    expected  = hmac.new(TEBEX_SECRET.encode("utf-8"), body_hash.encode("utf-8"), hashlib.sha256).hexdigest()
    return hmac.compare_digest(expected, signature)


def _tebex_resolve_item(tebex_package_id: str, tebex_package_name: str):
    """
    Maps a Tebex package back to our store_catalog. Prefers the explicit
    TEBEX_PACKAGE_MAP (by Tebex's numeric package id); falls back to an exact,
    case-insensitive match on the catalog item's display name, in case the store
    owner named the Tebex package identically to ours. Returns None if neither
    resolves -- callers must treat that as "do not fulfill".
    """
    slug = TEBEX_PACKAGE_MAP.get(tebex_package_id)
    if slug:
        item = get_item(slug)
        if item:
            return item
        log.error("[Tebex] TEBEX_PACKAGE_MAP has id=%s -> unknown slug '%s'.", tebex_package_id, slug)

    for item in CATALOG.values():
        if item.name.lower() == (tebex_package_name or "").strip().lower():
            log.info(
                "[Tebex] package id=%s matched catalog item '%s' by name, not by "
                "TEBEX_PACKAGE_MAP -- add an explicit id mapping to make this exact.",
                tebex_package_id, item.slug,
            )
            return item

    return None


async def _tebex_payment_completed(db: Session, subject: dict, raw_payload: str) -> None:
    from main import _game_username_exists  # deferred: avoids a circular import with main

    transaction_id = subject.get("transaction_id", "")
    products       = subject.get("products", []) or []
    username       = (subject.get("customer") or {}).get("username", {}).get("username")

    log.info("[Tebex] payment.completed — transaction=%s username=%s products=%d",
              transaction_id, username, len(products))

    if not transaction_id:
        log.error("[Tebex] payment.completed missing transaction_id — cannot fulfill.")
        return
    if not username:
        log.error("[Tebex] payment.completed %s missing customer username — cannot fulfill.", transaction_id)
        return
    if not _game_username_exists(username):
        # Tebex's own checkout has no equivalent pre-flight check, so this webhook is
        # the only place a Tebex order's username is ever validated against real characters.
        log.warning("[Tebex] payment.completed %s for unknown character '%s' — ignoring.",
                    transaction_id, username)
        return

    for product in products:
        await _tebex_fulfill_product(db, transaction_id, username, product, raw_payload)


async def _tebex_fulfill_product(db: Session, transaction_id: str, username: str, product: dict, raw_payload: str) -> None:
    tebex_package_id   = str(product.get("id", ""))
    tebex_package_name = product.get("name", "")

    item = _tebex_resolve_item(tebex_package_id, tebex_package_name)
    if not item:
        log.error(
            "[Tebex] No catalog mapping for package id=%s name='%s' (transaction=%s) -- "
            "add it to TEBEX_PACKAGE_MAP. Order NOT fulfilled.",
            tebex_package_id, tebex_package_name, transaction_id,
        )
        return

    # One Transaction row per line item, not per order -- a single Tebex payment can
    # bundle several packages in one basket. provider_session_id's UNIQUE constraint
    # (relied on below exactly like the Stripe/PayPal paths) needs a per-product key,
    # since reusing the bare transaction_id for every product in the basket would
    # collide on the second item.
    provider_session_id = f"tebex:{transaction_id}:{tebex_package_id}"

    txn = (
        db.query(models.Transaction)
        .filter(models.Transaction.provider_session_id == provider_session_id)
        .with_for_update()
        .first()
    )
    if txn:
        if txn.status == models.TransactionStatus.COMPLETED.value:
            log.info("[Tebex] %s already fulfilled — skipping duplicate.", provider_session_id)
            return
        txn.raw_webhook_payload = raw_payload
    else:
        txn = models.Transaction(
            username=username,
            package_id=item.slug,
            package_name=item.name,
            amount_usd=item.price_usd,
            provider=models.PaymentProvider.TEBEX.value,
            provider_session_id=provider_session_id,
            status=models.TransactionStatus.PENDING.value,
            raw_webhook_payload=raw_payload,
        )
        db.add(txn)
        db.flush()  # assign txn.id; provider_session_id UNIQUE constraint fires here on dupe

    try:
        _fulfill(db, txn)
        db.commit()
        log.info("[Tebex] Order fulfilled — user='%s' pkg='%s' session=%s",
                  username, item.slug, provider_session_id)
    except IntegrityError:
        db.rollback()
        log.warning("[Tebex] IntegrityError on %s — duplicate already fulfilled.", provider_session_id)
    except Exception as exc:
        db.rollback()
        log.error("[Tebex] DB error fulfilling %s: %s", provider_session_id, exc)
        raise  # re-raise → FastAPI returns 500 → Tebex retries


# ── NOWPayments IPN webhook ─────────────────────────────────────────────────────

# Statuses that mean the crypto payment is fully settled -- see
# https://nowpayments.zendesk.com/hc/en-us/articles/18372835216413 for the full
# waiting -> confirming -> confirmed -> sending -> finished progression.
# "confirmed"/"sending" are NOT included: the funds haven't actually settled to
# our payout wallet yet at those stages, only "finished" (and "partially_paid",
# see below) represent money we've actually received.
_NOWPAYMENTS_SUCCESS_STATUSES = {"finished", "partially_paid"}


def _nowpayments_verify_signature(parsed_body: dict, signature: str) -> bool:
    """
    NOWPayments' scheme: HMAC-SHA512, keyed with NOWPAYMENTS_IPN_SECRET, over a
    re-serialization of the PARSED body with every key (recursively) sorted
    alphabetically and compact separators -- matching JS's
    JSON.stringify(sortObjectDeep(body)), NOT the raw bytes as received.
    """
    if not signature:
        return False

    def _sort_deep(value):
        if isinstance(value, dict):
            return {k: _sort_deep(value[k]) for k in sorted(value.keys())}
        if isinstance(value, list):
            return [_sort_deep(v) for v in value]
        return value

    sorted_body = _sort_deep(parsed_body)
    serialized  = json.dumps(sorted_body, separators=(",", ":"))
    expected    = hmac.new(
        NOWPAYMENTS_IPN_SECRET.encode("utf-8"), serialized.encode("utf-8"), hashlib.sha512
    ).hexdigest()
    return hmac.compare_digest(expected, signature)


@router.post("/nowpayments")
async def nowpayments_webhook(request: Request, db: Session = Depends(get_db)):
    if not NOWPAYMENTS_IPN_SECRET:
        log.error("[NOWPayments Webhook] NOWPAYMENTS_IPN_SECRET is not set — rejecting request.")
        raise HTTPException(500, "Webhook endpoint is not configured.")

    raw_body = await request.body()
    try:
        event = json.loads(raw_body)
    except json.JSONDecodeError:
        raise HTTPException(400, "Malformed JSON payload.")

    signature = request.headers.get("x-nowpayments-sig", "")
    if not _nowpayments_verify_signature(event, signature):
        log.warning("[NOWPayments Webhook] Signature mismatch — possible spoofed request.")
        raise HTTPException(400, "Webhook signature verification failed.")

    payment_status = event.get("payment_status", "")
    order_id       = event.get("order_id", "")
    payment_id     = event.get("payment_id", "")
    log.info("[NOWPayments] IPN — order_id=%s payment_id=%s status=%s",
              order_id, payment_id, payment_status)

    if payment_status not in _NOWPAYMENTS_SUCCESS_STATUSES:
        log.info("[NOWPayments] order_id=%s status=%s — not a completion state, no fulfillment.",
                  order_id, payment_status)
        return {"status": "received"}

    await _nowpayments_payment_completed(db, event, raw_payload=raw_body.decode("utf-8"))
    return {"status": "received"}


async def _nowpayments_payment_completed(db: Session, event: dict, raw_payload: str) -> None:
    order_id = event.get("order_id", "")

    # order_id is str(txn.id), set when the invoice was created in
    # routers/checkout.py's create_crypto_checkout -- NOWPayments always echoes
    # it back verbatim, so this is a direct primary-key lookup, not a fuzzy match.
    try:
        txn_id = int(order_id)
    except (ValueError, TypeError):
        log.error("[NOWPayments] Non-numeric order_id=%r — cannot fulfill.", order_id)
        return

    # SELECT FOR UPDATE locks the row -- a second IPN for the same payment
    # (NOWPayments re-sends on every status transition) blocks here until the
    # first one commits, then sees status=COMPLETED and returns without a write.
    txn = (
        db.query(models.Transaction)
        .filter(models.Transaction.id == txn_id)
        .with_for_update()
        .first()
    )
    if not txn:
        log.error("[NOWPayments] No transaction #%d for order_id=%r — order NOT fulfilled.",
                  txn_id, order_id)
        return

    if txn.status == models.TransactionStatus.COMPLETED.value:
        log.info("[NOWPayments] Transaction #%d already fulfilled — skipping duplicate.", txn_id)
        return

    txn.raw_webhook_payload = raw_payload

    try:
        _fulfill(db, txn)
        db.commit()
        log.info("[NOWPayments] Order fulfilled — user='%s' txn_id=%d payment_id=%s",
                  txn.username, txn.id, event.get("payment_id"))
    except IntegrityError:
        db.rollback()
        log.warning("[NOWPayments] IntegrityError on txn #%d — duplicate already fulfilled.", txn_id)
    except Exception as exc:
        db.rollback()
        log.error("[NOWPayments] DB error fulfilling txn #%d: %s", txn_id, exc)
        raise  # re-raise → FastAPI returns 500 → NOWPayments retries
