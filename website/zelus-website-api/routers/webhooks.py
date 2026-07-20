"""
Webhook handlers for Stripe and PayPal.

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

  Race condition / replay defense (BOTH providers):
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
from store_catalog import get_item

log = logging.getLogger("zelus.webhooks")

router = APIRouter(prefix="/api/webhooks", tags=["webhooks"])

STRIPE_WEBHOOK_SECRET = os.getenv("STRIPE_WEBHOOK_SECRET", "")
PAYPAL_WEBHOOK_ID     = os.getenv("PAYPAL_WEBHOOK_ID", "")
PAYPAL_CLIENT_ID      = os.getenv("PAYPAL_CLIENT_ID", "")
PAYPAL_CLIENT_SECRET  = os.getenv("PAYPAL_CLIENT_SECRET", "")
PAYPAL_BASE_URL       = os.getenv("PAYPAL_BASE_URL", "https://api-m.sandbox.paypal.com")


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
    txn.status = models.TransactionStatus.COMPLETED
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
            provider=models.PaymentProvider.STRIPE,
            provider_session_id=session_id,
            status=models.TransactionStatus.PENDING,
            raw_webhook_payload=raw_payload,
        )
        db.add(txn)
        db.flush()  # assign txn.id; provider_session_id UNIQUE constraint fires here on dupe
    else:
        # ── Idempotency: row is locked — check status now that we hold the lock ─
        if txn.status == models.TransactionStatus.COMPLETED:
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
    if txn.status == models.TransactionStatus.COMPLETED:
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
