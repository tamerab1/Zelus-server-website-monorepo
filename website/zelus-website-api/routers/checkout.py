"""
Checkout endpoints — Stripe, PayPal, OSRS GP (manual ticket), Crypto (NOWPayments),
Tebex (Headless).

Security contract:
  - Package price is ALWAYS looked up from store_catalog, never taken from the request.
  - Stripe/PayPal/Crypto: a Transaction row is created BEFORE the player is redirected,
    so we can reconcile webhook callbacks even if the browser never returns to our
    success URL.
  - OSRS GP: a Discord webhook notifies staff; fulfillment is manual until confirmed.
  - Crypto: NOWPayments hosted invoice; fulfillment happens off the IPN webhook
    in routers/webhooks.py (see that file for the signature verification contract).
  - Tebex: creates a Headless basket, no Transaction row up front -- fulfillment is
    entirely driven off the payment.completed webhook, same as the old external-link
    flow (see the create_tebex_checkout docstring/comment for why).
"""
import logging
import math
import os

import httpx
import stripe
from fastapi import APIRouter, Depends, HTTPException, Request
from pydantic import BaseModel, Field
from sqlalchemy.orm import Session

import models
from database import get_db
from net_utils import get_real_client_ip
from routers.webhooks import TEBEX_PACKAGE_MAP
from store_catalog import get_item

log = logging.getLogger("zelus.checkout")

router = APIRouter(prefix="/api/checkout", tags=["checkout"])

# ── Stripe ─────────────────────────────────────────────────────────────────────
stripe.api_key = os.getenv("STRIPE_SECRET_KEY", "")

# ── PayPal ─────────────────────────────────────────────────────────────────────
PAYPAL_CLIENT_ID     = os.getenv("PAYPAL_CLIENT_ID", "")
PAYPAL_CLIENT_SECRET = os.getenv("PAYPAL_CLIENT_SECRET", "")
PAYPAL_BASE_URL      = os.getenv("PAYPAL_BASE_URL", "https://api-m.sandbox.paypal.com")

# ── OSRS GP ────────────────────────────────────────────────────────────────────
# Rate: $0.25 per 1 M OSRS GP  →  M_GP = price_usd / 0.25
OSRS_RATE_USD_PER_M  = 0.25
DISCORD_STORE_WEBHOOK_URL = os.getenv("DISCORD_STORE_WEBHOOK_URL", "")

# ── NOWPayments (crypto) ─────────────────────────────────────────────────────────
NOWPAYMENTS_API_KEY = os.getenv("NOWPAYMENTS_API_KEY", "")
NOWPAYMENTS_BASE_URL = "https://api.nowpayments.io"

# ── Tebex Headless (card/PayPal/etc, stays on our site) ──────────────────────────
# 2026-08-26: contrary to Tebex's own docs ("most endpoints don't require
# credentials"), basket creation live-rejects with 422 "Basic auth credentials
# are required" without both. Both required -- HTTP Basic, public token as the
# username, private key as the password. From the Tebex creator panel:
# Developers > API Keys.
TEBEX_PUBLIC_TOKEN  = os.getenv("TEBEX_PUBLIC_TOKEN", "")
TEBEX_PRIVATE_KEY   = os.getenv("TEBEX_PRIVATE_KEY", "")
TEBEX_HEADLESS_BASE_URL = "https://headless.tebex.io/api"

# Reuses webhooks.py's TEBEX_PACKAGE_MAP (Tebex package id -> our slug) as the
# single source of truth, inverted for this outbound direction -- avoids
# hand-maintaining a second mapping that can silently drift out of sync.
_SLUG_TO_TEBEX_PACKAGE_ID = {slug: tebex_id for tebex_id, slug in TEBEX_PACKAGE_MAP.items()}

# ── Shared ─────────────────────────────────────────────────────────────────────
SITE_URL = os.getenv("SITE_URL", "http://localhost:5173")
# This API's OWN public URL, not the frontend's -- needed so NOWPayments knows
# where to POST the IPN callback to (unlike Stripe/PayPal, which we call FROM
# our backend and which call US back on a URL registered once in their own
# dashboard, NOWPayments' callback URL is supplied fresh on every invoice we
# create, so we have to know our own address).
API_BASE_URL = os.getenv("API_BASE_URL", "http://localhost:8000")


# ── Request schema ─────────────────────────────────────────────────────────────
class CheckoutRequest(BaseModel):
    username:   str = Field(..., min_length=1, max_length=12)
    package_id: str = Field(..., min_length=1, max_length=50)


# ── PayPal helper ──────────────────────────────────────────────────────────────
async def _paypal_access_token() -> str:
    """Exchange client_id/secret for a short-lived access token."""
    async with httpx.AsyncClient(timeout=10) as client:
        resp = await client.post(
            f"{PAYPAL_BASE_URL}/v1/oauth2/token",
            auth=(PAYPAL_CLIENT_ID, PAYPAL_CLIENT_SECRET),
            data={"grant_type": "client_credentials"},
        )
        resp.raise_for_status()
        return resp.json()["access_token"]


# ── Stripe checkout ────────────────────────────────────────────────────────────
@router.post("/stripe")
async def create_stripe_checkout(req: CheckoutRequest, db: Session = Depends(get_db)):
    log.info("[Stripe] Checkout — user='%s' pkg='%s'", req.username, req.package_id)

    if not stripe.api_key:
        raise HTTPException(503, "Stripe payments are not configured.")

    # ── Price validation (never trust the frontend) ────────────────────────────
    item = get_item(req.package_id)
    if not item:
        log.warning("[Stripe] Unknown package_id='%s' from user='%s'", req.package_id, req.username)
        raise HTTPException(400, f"Unknown package: '{req.package_id}'")

    # ── Create pending transaction row BEFORE contacting Stripe ───────────────
    txn = models.Transaction(
        username=req.username,
        package_id=item.slug,
        package_name=item.name,
        amount_usd=item.price_usd,
        provider=models.PaymentProvider.STRIPE.value,
        status=models.TransactionStatus.PENDING.value,
    )
    db.add(txn)
    db.flush()  # assign txn.id without committing

    # ── Create Stripe Checkout Session ────────────────────────────────────────
    try:
        session = stripe.checkout.Session.create(
            payment_method_types=["card"],
            line_items=[{
                "price_data": {
                    "currency": "usd",
                    "unit_amount": int(item.price_usd * 100),  # Stripe uses cents
                    "product_data": {
                        "name": f"Zelus — {item.name}",
                        "description": f"+{item.tokens:,} tokens · delivered to: {req.username}",
                    },
                },
                "quantity": 1,
            }],
            mode="payment",
            # {CHECKOUT_SESSION_ID} is a Stripe template literal — not Python formatting
            success_url=f"{SITE_URL}/?payment=success&session_id={{CHECKOUT_SESSION_ID}}",
            cancel_url=f"{SITE_URL}/?payment=cancelled",
            metadata={
                "username":       req.username,
                "package_id":     item.slug,
                "transaction_id": str(txn.id),
            },
            client_reference_id=str(txn.id),
        )
    except stripe.error.StripeError as exc:
        db.rollback()
        log.error("[Stripe] API error for user='%s': %s", req.username, exc)
        raise HTTPException(502, exc.user_message or "Stripe payment could not be initiated.")

    # ── Persist the Stripe session ID so the webhook can locate this txn ──────
    txn.provider_session_id = session.id
    db.commit()

    log.info("[Stripe] Session created: %s  txn_id=%d  user='%s'",
             session.id, txn.id, req.username)
    return {"checkout_url": session.url, "session_id": session.id}


# ── PayPal checkout ────────────────────────────────────────────────────────────
@router.post("/paypal")
async def create_paypal_checkout(req: CheckoutRequest, db: Session = Depends(get_db)):
    log.info("[PayPal] Checkout — user='%s' pkg='%s'", req.username, req.package_id)

    if not PAYPAL_CLIENT_ID or not PAYPAL_CLIENT_SECRET:
        raise HTTPException(503, "PayPal payments are not configured.")

    # ── Price validation ───────────────────────────────────────────────────────
    item = get_item(req.package_id)
    if not item:
        log.warning("[PayPal] Unknown package_id='%s' from user='%s'", req.package_id, req.username)
        raise HTTPException(400, f"Unknown package: '{req.package_id}'")

    # ── Create pending transaction row BEFORE contacting PayPal ───────────────
    txn = models.Transaction(
        username=req.username,
        package_id=item.slug,
        package_name=item.name,
        amount_usd=item.price_usd,
        provider=models.PaymentProvider.PAYPAL.value,
        status=models.TransactionStatus.PENDING.value,
    )
    db.add(txn)
    db.flush()

    # ── Create PayPal Order ────────────────────────────────────────────────────
    try:
        access_token = await _paypal_access_token()

        async with httpx.AsyncClient(timeout=10) as client:
            resp = await client.post(
                f"{PAYPAL_BASE_URL}/v2/checkout/orders",
                headers={
                    "Authorization":  f"Bearer {access_token}",
                    "Content-Type":   "application/json",
                    # Idempotency key — prevents duplicate orders on retry
                    "PayPal-Request-Id": f"zelus-txn-{txn.id}",
                },
                json={
                    "intent": "CAPTURE",
                    "purchase_units": [{
                        # custom_id is echoed back in capture webhooks for lookup
                        "custom_id":    str(txn.id),
                        "reference_id": str(txn.id),
                        "description":  (
                            f"Zelus — {item.name} "
                            f"(+{item.tokens:,} tokens for {req.username})"
                        ),
                        "amount": {
                            "currency_code": "USD",
                            "value": f"{item.price_usd:.2f}",
                        },
                    }],
                    "application_context": {
                        "brand_name":  "Zelus",
                        "user_action": "PAY_NOW",
                        "return_url":  f"{SITE_URL}/?payment=success",
                        "cancel_url":  f"{SITE_URL}/?payment=cancelled",
                    },
                },
            )
            resp.raise_for_status()
            order = resp.json()

    except httpx.HTTPStatusError as exc:
        db.rollback()
        log.error("[PayPal] HTTP %d for user='%s': %s",
                  exc.response.status_code, req.username, exc.response.text)
        raise HTTPException(502, "PayPal payment could not be initiated. Please try again.")
    except httpx.TimeoutException:
        db.rollback()
        log.error("[PayPal] Timeout creating order for user='%s'", req.username)
        raise HTTPException(504, "PayPal is taking too long to respond. Please try again.")
    except Exception as exc:
        db.rollback()
        log.error("[PayPal] Unexpected error for user='%s': %s", req.username, exc)
        raise HTTPException(502, "PayPal payment could not be initiated.")

    # ── Extract approval URL ───────────────────────────────────────────────────
    approval_url = next(
        (link["href"] for link in order.get("links", []) if link.get("rel") == "approve"),
        None,
    )
    if not approval_url:
        db.rollback()
        log.error("[PayPal] No approval URL in response for user='%s': %s", req.username, order)
        raise HTTPException(502, "PayPal did not return a payment URL.")

    txn.provider_session_id = order["id"]
    db.commit()

    log.info("[PayPal] Order created: %s  txn_id=%d  user='%s'",
             order["id"], txn.id, req.username)
    return {"checkout_url": approval_url, "order_id": order["id"]}


# ── OSRS GP checkout ───────────────────────────────────────────────────────────
# Rate: $0.25 per 1 M GP.  Staff fulfil the trade manually; this endpoint
# creates a PENDING transaction and fires a Discord ticket webhook.

@router.post("/osrs-gp")
async def create_osrs_gp_checkout(req: CheckoutRequest, db: Session = Depends(get_db)):
    log.info("[OSRS-GP] Initiate — user='%s' pkg='%s'", req.username, req.package_id)

    # ── Price validation ───────────────────────────────────────────────────────
    item = get_item(req.package_id)
    if not item:
        raise HTTPException(400, f"Unknown package: '{req.package_id}'")

    # ── GP amount calculation ──────────────────────────────────────────────────
    # math.ceil so the player always pays at least the full USD equivalent.
    gp_millions = math.ceil(item.price_usd / OSRS_RATE_USD_PER_M)

    # ── Create pending transaction ─────────────────────────────────────────────
    txn = models.Transaction(
        username=req.username,
        package_id=item.slug,
        package_name=item.name,
        amount_usd=item.price_usd,
        provider=models.PaymentProvider.OSRS_GP.value,
        status=models.TransactionStatus.PENDING.value,
    )
    db.add(txn)
    db.flush()   # get txn.id before the Discord call

    # ── Discord webhook — notify staff to open a ticket ────────────────────────
    if DISCORD_STORE_WEBHOOK_URL:
        await _send_osrs_gp_discord_alert(
            username=req.username,
            item_name=item.name,
            price_usd=item.price_usd,
            gp_millions=gp_millions,
            transaction_id=txn.id,
        )
    else:
        log.warning("[OSRS-GP] DISCORD_STORE_WEBHOOK_URL is not set — skipping Discord alert.")

    db.commit()
    log.info("[OSRS-GP] Transaction #%d created — user='%s' gp=%dM", txn.id, req.username, gp_millions)

    return {
        "transaction_id": txn.id,
        "gp_millions":    gp_millions,
        "message": (
            f"Ticket opened! A staff member will contact you on Discord to "
            f"arrange the {gp_millions:,}M OSRS GP trade for {item.name}. "
            f"Reference: #{txn.id}"
        ),
    }


async def _send_osrs_gp_discord_alert(
    username: str,
    item_name: str,
    price_usd: float,
    gp_millions: int,
    transaction_id: int,
) -> None:
    """
    Sends a rich embed to the staff Discord channel.
    Fails silently — a Discord outage must not block the checkout.
    """
    embed = {
        "title": "🪙  New OSRS GP Payment Request",
        "color": 0xD4AF37,   # gold
        "fields": [
            {"name": "Player",       "value": f"`{username}`",               "inline": True},
            {"name": "Package",      "value": item_name,                      "inline": True},
            {"name": "USD Value",    "value": f"${price_usd:.2f}",           "inline": True},
            {"name": "GP to Receive","value": f"**{gp_millions:,}M OSRS GP**","inline": True},
            {"name": "Rate",         "value": "$0.25 per 1M GP",             "inline": True},
            {"name": "Txn ID",       "value": f"#{transaction_id}",          "inline": True},
        ],
        "description": (
            f"User **{username}** wants to purchase **{item_name}** "
            f"for **{gp_millions:,}M OSRS GP** (${price_usd:.2f} USD).\n\n"
            f"Please open a ticket with this player and arrange the in-game trade.\n"
            f"After confirming receipt, use the admin panel to fulfil transaction **#{transaction_id}**."
        ),
        "footer": {"text": "Zelus Store — OSRS GP Payment"},
    }

    payload = {
        "content": "@here 🎫 New GP payment request — please open a ticket!",
        "embeds": [embed],
    }

    try:
        async with httpx.AsyncClient(timeout=8) as client:
            resp = await client.post(DISCORD_STORE_WEBHOOK_URL, json=payload)
            resp.raise_for_status()
            log.info("[OSRS-GP] Discord alert sent for txn #%d.", transaction_id)
    except Exception as exc:
        # Non-fatal: the transaction is already committed — just log.
        log.error("[OSRS-GP] Discord webhook failed for txn #%d: %s", transaction_id, exc)


# ── Crypto checkout (NOWPayments) ───────────────────────────────────────────────
# Creates a hosted NOWPayments invoice (POST /v1/invoice) and redirects the
# player there, same create-then-redirect shape as Stripe/PayPal above.
# pay_currency is deliberately omitted -- NOWPayments' own hosted invoice page
# lets the player pick which coin to pay with, so we don't need a picker here.
# Fulfillment happens entirely off the IPN webhook (routers/webhooks.py) since,
# like Tebex, there's no synchronous confirmation at checkout-creation time.
#
# Invoice amount history: 5.50 -> 8.00 -> 5.30 -> exact price_usd (no floor).
# The floor existed to dodge NOWPayments' own "less than minimal" per-currency
# rejection on cheap invoices (confirmed live on USDT-TRX) and to cushion
# against exchange withdrawal fees -- but padding the invoice also means a
# player who naturally sends a bit more than the item's real price (a common
# pattern -- wallets/exchanges round transfers up, e.g. sending ~$5.20-5.30
# for a "$5" purchase) still lands BELOW the padded target and
# gets NOWPayments' "Partially Paid" screen instead of "Completed", even
# though they paid enough to cover the real item price. Invoicing the exact
# item price lets that same natural overpay habit clear the target outright
# (green "Completed" screen) while routers/webhooks.py's partially_paid
# handling (_NOWPAYMENTS_PARTIAL_PAYMENT_THRESHOLD, >=90% actually_paid/
# pay_amount) still catches genuine underpayment as a fallback. Trade-off:
# this reopens a little exposure to the original "less than minimal"
# rejection on any currency whose minimum lands above the item's raw price --
# mitigate by keeping cheap/volatile-fee networks like USDT-TRX disabled in
# NOWPayments' dashboard coin settings, not something this API controls.

@router.post("/crypto")
async def create_crypto_checkout(req: CheckoutRequest, db: Session = Depends(get_db)):
    log.info("[Crypto] Checkout — user='%s' pkg='%s'", req.username, req.package_id)

    if not NOWPAYMENTS_API_KEY:
        raise HTTPException(503, "Crypto payments are not configured.")

    # ── Price validation (never trust the frontend) ────────────────────────────
    item = get_item(req.package_id)
    if not item:
        log.warning("[Crypto] Unknown package_id='%s' from user='%s'", req.package_id, req.username)
        raise HTTPException(400, f"Unknown package: '{req.package_id}'")

    # ── Create pending transaction row BEFORE contacting NOWPayments ──────────
    txn = models.Transaction(
        username=req.username,
        package_id=item.slug,
        package_name=item.name,
        amount_usd=item.price_usd,
        provider=models.PaymentProvider.CRYPTO.value,
        status=models.TransactionStatus.PENDING.value,
    )
    db.add(txn)
    db.flush()  # assign txn.id before the API call — order_id needs it

    # ── Create NOWPayments invoice ─────────────────────────────────────────────
    # No padding -- invoice the item's exact price. See the comment above this
    # function for why (natural wallet-rounding overpay clears the exact price
    # outright instead of landing short of a padded target).
    try:
        async with httpx.AsyncClient(timeout=10) as client:
            resp = await client.post(
                f"{NOWPAYMENTS_BASE_URL}/v1/invoice",
                headers={"x-api-key": NOWPAYMENTS_API_KEY, "Content-Type": "application/json"},
                json={
                    "price_amount":     item.price_usd,
                    "price_currency":   "usd",
                    "order_id":         str(txn.id),
                    "order_description": f"Zelus — {item.name} (+{item.tokens:,} tokens for {req.username})",
                    "ipn_callback_url": f"{API_BASE_URL}/api/webhooks/nowpayments",
                    "success_url":      f"{SITE_URL}/?payment=success",
                    "cancel_url":       f"{SITE_URL}/?payment=cancelled",
                },
            )
            resp.raise_for_status()
            invoice = resp.json()
    except httpx.HTTPStatusError as exc:
        db.rollback()
        log.error("[Crypto] HTTP %d for user='%s': %s",
                  exc.response.status_code, req.username, exc.response.text)
        raise HTTPException(502, "Crypto payment could not be initiated. Please try again.")
    except httpx.TimeoutException:
        db.rollback()
        log.error("[Crypto] Timeout creating invoice for user='%s'", req.username)
        raise HTTPException(504, "NOWPayments is taking too long to respond. Please try again.")
    except Exception as exc:
        db.rollback()
        log.error("[Crypto] Unexpected error for user='%s': %s", req.username, exc)
        raise HTTPException(502, "Crypto payment could not be initiated.")

    invoice_url = invoice.get("invoice_url")
    if not invoice_url:
        db.rollback()
        log.error("[Crypto] No invoice_url in response for user='%s': %s", req.username, invoice)
        raise HTTPException(502, "NOWPayments did not return a payment URL.")

    txn.provider_session_id = str(invoice.get("id", ""))
    db.commit()

    log.info("[Crypto] Invoice created: %s  txn_id=%d  user='%s'",
             invoice.get("id"), txn.id, req.username)
    return {"checkout_url": invoice_url, "invoice_id": invoice.get("id")}


def _extract_tebex_checkout_url(basket: dict) -> str | None:
    """
    2026-08-26: Tebex's docs describe `links` as a flat dict
    ({"checkout": "url"}), but the live API returned a LIST for this project,
    which crashed the flat-dict `.get()` call with an unhandled 500
    ("'list' object has no attribute 'get'"). Handles both shapes rather than
    assume either is stable. If a live basket ever hits neither branch, the
    caller's existing "no checkout link" log includes the full basket dict --
    check that log for the exact shape before guessing again.
    """
    links = basket.get("links")
    if isinstance(links, dict):
        return links.get("checkout")
    if isinstance(links, list):
        for link in links:
            if not isinstance(link, dict):
                continue
            rel = str(link.get("rel") or link.get("name") or "").lower()
            if rel == "checkout":
                return link.get("href") or link.get("url")
        log.error("[Tebex] links was a list but no 'checkout' entry matched: %s", links)
    return None


# ── Tebex Headless checkout ────────────────────────────────────────────────────
# Creates a Tebex basket via the Headless API so the player stays on our site
# instead of following the old external link to the hosted storefront.
#
# Deliberately does NOT create a Transaction row here, unlike Stripe/PayPal/
# Crypto above. Fulfillment for Tebex is entirely driven off payment.completed
# (see routers/webhooks.py's _tebex_payment_completed), which resolves the
# order purely from the product id plus the customer-username field Tebex's
# own checkout page collects -- and a headless basket's links.checkout points
# at that exact same page, so the existing webhook needs no changes. Creating
# a pending row here too would just leave an orphaned duplicate once the
# webhook creates its own (this mirrors why the current hosted-storefront
# flow has never created one either -- see the module docstring above).
#
# NOT YET LIVE-VERIFIED: whether the Tebex checkout page reached via a
# headless-created basket still prompts for the same custom "username" field
# the hosted storefront does -- that's what _tebex_payment_completed depends
# on to resolve who to deliver to. Test with one real low-tier purchase before
# trusting this path; if the field is missing, the webhook logs
# "missing customer username -- cannot fulfill" and nothing is delivered
# (payment still succeeds on Tebex's side, so this would need a manual
# refund/fulfil, not a silent loss either way).
@router.post("/tebex")
async def create_tebex_checkout(req: CheckoutRequest, request: Request, db: Session = Depends(get_db)):
    log.info("[Tebex] Checkout — user='%s' pkg='%s'", req.username, req.package_id)

    if not TEBEX_PUBLIC_TOKEN or not TEBEX_PRIVATE_KEY:
        raise HTTPException(503, "Card payments are not configured.")

    # ── Price validation (never trust the frontend) ────────────────────────────
    item = get_item(req.package_id)
    if not item:
        log.warning("[Tebex] Unknown package_id='%s' from user='%s'", req.package_id, req.username)
        raise HTTPException(400, f"Unknown package: '{req.package_id}'")

    tebex_package_id = _SLUG_TO_TEBEX_PACKAGE_ID.get(item.slug)
    if not tebex_package_id:
        log.error("[Tebex] No Tebex package id mapped for slug='%s' -- add it to TEBEX_PACKAGE_MAP.", item.slug)
        raise HTTPException(400, f"'{item.name}' is not available via card checkout yet.")

    client_ip = get_real_client_ip(request) or "0.0.0.0"
    tebex_auth = (TEBEX_PUBLIC_TOKEN, TEBEX_PRIVATE_KEY)

    try:
        async with httpx.AsyncClient(timeout=10) as client:
            basket_resp = await client.post(
                f"{TEBEX_HEADLESS_BASE_URL}/accounts/{TEBEX_PUBLIC_TOKEN}/baskets",
                auth=tebex_auth,
                json={
                    "complete_url":           f"{SITE_URL}/?payment=success",
                    "cancel_url":             f"{SITE_URL}/?payment=cancelled",
                    "complete_auto_redirect": True,
                    "ip_address":             client_ip,
                    # Top-level `username` (Tebex's own player-identity field for
                    # Minecraft/Overwolf-type stores) -- was previously only sent
                    # nested under `custom`, which Tebex treats as opaque metadata,
                    # not an identity field. Without this, package-add 422s with
                    # "User must login before adding packages to basket", since
                    # Tebex falls back to requiring a full Steam/Minecraft/Discord
                    # OAuth basket-authorization flow when it has no other way to
                    # identify the player.
                    "username": req.username,
                    "custom": {"username": req.username},
                },
            )
            basket_resp.raise_for_status()
            basket = basket_resp.json()["data"]

            add_resp = await client.post(
                f"{TEBEX_HEADLESS_BASE_URL}/baskets/{basket['ident']}/packages",
                auth=tebex_auth,
                json={"package_id": int(tebex_package_id), "quantity": 1},
            )
            add_resp.raise_for_status()

            # The basket object captured at creation time is a snapshot from
            # BEFORE any package existed -- confirmed live: it comes back with
            # `links: []` and `packages: []` even after a 200 OK add-package
            # call, because links.checkout only gets populated once the basket
            # actually has contents. Re-fetch the current state rather than
            # reuse the stale one.
            refetch_resp = await client.get(
                f"{TEBEX_HEADLESS_BASE_URL}/accounts/{TEBEX_PUBLIC_TOKEN}/baskets/{basket['ident']}",
                auth=tebex_auth,
            )
            refetch_resp.raise_for_status()
            basket = refetch_resp.json()["data"]
    except httpx.HTTPStatusError as exc:
        log.error("[Tebex] HTTP %d for user='%s': %s",
                  exc.response.status_code, req.username, exc.response.text)
        raise HTTPException(502, "Card payment could not be initiated. Please try again.")
    except httpx.TimeoutException:
        log.error("[Tebex] Timeout creating basket for user='%s'", req.username)
        raise HTTPException(504, "Tebex is taking too long to respond. Please try again.")
    except Exception as exc:
        log.error("[Tebex] Unexpected error for user='%s': %s", req.username, exc)
        raise HTTPException(502, "Card payment could not be initiated.")

    checkout_url = _extract_tebex_checkout_url(basket)
    if not checkout_url:
        log.error("[Tebex] No checkout link in basket response for user='%s': %s", req.username, basket)
        raise HTTPException(502, "Tebex did not return a payment URL.")

    log.info("[Tebex] Basket created: %s  user='%s'", basket["ident"], req.username)
    return {"checkout_url": checkout_url, "basket_ident": basket["ident"]}


# ── Tebex cart checkout (multi-item) ────────────────────────────────────────────
# The cart drawer previously synthesized a fake package (id/slug='cart') and sent
# it through the single-item endpoint above, which every provider's price
# validation correctly rejected as an unknown package_id ("Unknown package:
# 'cart'"). That was never Tebex-specific -- every checkout endpoint here
# validates package_id against store_catalog, so the cart was broken against all
# of them identically; it just surfaced now because Tebex is the one path
# actually wired to a UI button. Tebex's basket model natively supports multiple
# packages per basket (unlike Stripe/PayPal/NOWPayments' single-amount-per-
# session model above), so this is the one provider that can genuinely support a
# real multi-item cart today -- Crypto/OSRS-GP cart checkout stays unsupported.
#
# NOT YET LIVE-VERIFIED: whether Tebex's add-package endpoint actually honours
# a `quantity` in the request body vs. defaulting to 1 and requiring a separate
# update-quantity call. Test with a real >1-quantity cart before trusting this.

class CartLineItem(BaseModel):
    package_id: str = Field(..., min_length=1, max_length=50)
    quantity:   int = Field(..., ge=1, le=99)


class CartCheckoutRequest(BaseModel):
    username: str = Field(..., min_length=1, max_length=12)
    items:    list[CartLineItem] = Field(..., min_length=1, max_length=20)


@router.post("/tebex/cart")
async def create_tebex_cart_checkout(
    req: CartCheckoutRequest, request: Request, db: Session = Depends(get_db),
):
    log.info("[Tebex] Cart checkout — user='%s' items=%d", req.username, len(req.items))

    if not TEBEX_PUBLIC_TOKEN or not TEBEX_PRIVATE_KEY:
        raise HTTPException(503, "Card payments are not configured.")

    # ── Resolve + validate every line BEFORE contacting Tebex at all — same
    # "never trust the frontend" price/existence validation as every single-item
    # endpoint above, just looped across the cart.
    resolved: list[tuple[str, int]] = []
    for line in req.items:
        item = get_item(line.package_id)
        if not item:
            log.warning("[Tebex] Cart contains unknown package_id='%s' from user='%s'",
                        line.package_id, req.username)
            raise HTTPException(400, f"Unknown package: '{line.package_id}'")
        tebex_package_id = _SLUG_TO_TEBEX_PACKAGE_ID.get(item.slug)
        if not tebex_package_id:
            log.error("[Tebex] No Tebex package id mapped for slug='%s' -- add it to TEBEX_PACKAGE_MAP.",
                      item.slug)
            raise HTTPException(400, f"'{item.name}' is not available via card checkout yet.")
        resolved.append((tebex_package_id, line.quantity))

    client_ip = get_real_client_ip(request) or "0.0.0.0"
    tebex_auth = (TEBEX_PUBLIC_TOKEN, TEBEX_PRIVATE_KEY)

    try:
        async with httpx.AsyncClient(timeout=10) as client:
            basket_resp = await client.post(
                f"{TEBEX_HEADLESS_BASE_URL}/accounts/{TEBEX_PUBLIC_TOKEN}/baskets",
                auth=tebex_auth,
                json={
                    "complete_url":           f"{SITE_URL}/?payment=success",
                    "cancel_url":             f"{SITE_URL}/?payment=cancelled",
                    "complete_auto_redirect": True,
                    "ip_address":             client_ip,
                    # See the identical comment in create_tebex_checkout above --
                    # top-level `username` is the actual identity field Tebex
                    # reads; `custom` alone leaves the basket unauthorized.
                    "username": req.username,
                    "custom": {"username": req.username},
                },
            )
            basket_resp.raise_for_status()
            basket = basket_resp.json()["data"]

            # One add-package call per line item onto the same basket.
            for tebex_package_id, quantity in resolved:
                add_resp = await client.post(
                    f"{TEBEX_HEADLESS_BASE_URL}/baskets/{basket['ident']}/packages",
                    auth=tebex_auth,
                    json={"package_id": int(tebex_package_id), "quantity": quantity},
                )
                add_resp.raise_for_status()

            # See the identical comment in create_tebex_checkout above --
            # the creation-time basket snapshot has empty links/packages
            # until re-fetched after packages actually exist on it.
            refetch_resp = await client.get(
                f"{TEBEX_HEADLESS_BASE_URL}/accounts/{TEBEX_PUBLIC_TOKEN}/baskets/{basket['ident']}",
                auth=tebex_auth,
            )
            refetch_resp.raise_for_status()
            basket = refetch_resp.json()["data"]
    except httpx.HTTPStatusError as exc:
        log.error("[Tebex] Cart HTTP %d for user='%s': %s",
                  exc.response.status_code, req.username, exc.response.text)
        raise HTTPException(502, "Card payment could not be initiated. Please try again.")
    except httpx.TimeoutException:
        log.error("[Tebex] Cart timeout for user='%s'", req.username)
        raise HTTPException(504, "Tebex is taking too long to respond. Please try again.")
    except Exception as exc:
        log.error("[Tebex] Cart unexpected error for user='%s': %s", req.username, exc)
        raise HTTPException(502, "Card payment could not be initiated.")

    checkout_url = _extract_tebex_checkout_url(basket)
    if not checkout_url:
        log.error("[Tebex] No checkout link in cart basket response for user='%s': %s", req.username, basket)
        raise HTTPException(502, "Tebex did not return a payment URL.")

    log.info("[Tebex] Cart basket created: %s  user='%s'  items=%d",
             basket["ident"], req.username, len(resolved))
    return {"checkout_url": checkout_url, "basket_ident": basket["ident"]}
