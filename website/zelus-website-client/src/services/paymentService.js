/**
 * Payment service — calls the backend checkout endpoints.
 * Prices are always validated server-side; the frontend only sends the slug + username.
 */
import { apiFetch } from './api.js';

/** OSRS GP rate: $0.25 per 1M GP — must match OSRS_RATE_USD_PER_M in checkout.py */
export const OSRS_RATE_USD_PER_M = 0.25;

/** Calculate GP millions for a given USD price (ceiling so player always pays full amount). */
export const calcGpMillions = (priceUsd) => Math.ceil(priceUsd / OSRS_RATE_USD_PER_M);

/**
 * @returns {Promise<{checkout_url: string, session_id: string}>}
 */
export const createStripeCheckout = (packageId, username) =>
  apiFetch('/api/checkout/stripe', {
    method: 'POST',
    body: JSON.stringify({ package_id: packageId, username }),
  });

/**
 * @returns {Promise<{checkout_url: string, order_id: string}>}
 */
export const createPayPalCheckout = (packageId, username) =>
  apiFetch('/api/checkout/paypal', {
    method: 'POST',
    body: JSON.stringify({ package_id: packageId, username }),
  });

/**
 * Initiates an OSRS GP payment — creates a pending transaction and fires a
 * Discord staff alert.  Staff manually confirm the trade and fulfil the order.
 * @returns {Promise<{transaction_id: number, gp_millions: number, message: string}>}
 */
export const initiateOsrsGpCheckout = (packageId, username) =>
  apiFetch('/api/checkout/osrs-gp', {
    method: 'POST',
    body: JSON.stringify({ package_id: packageId, username }),
  });

/**
 * Crypto checkout via NOWPayments — creates a hosted invoice and redirects
 * the player there. Fulfillment happens off NOWPayments' IPN webhook once the
 * payment settles; the player never leaves a redirect flow to see it happen.
 * @returns {Promise<{checkout_url: string, invoice_id: string}>}
 */
export const createCryptoCheckout = (packageId, username) =>
  apiFetch('/api/checkout/crypto', {
    method: 'POST',
    body: JSON.stringify({ package_id: packageId, username }),
  });

/**
 * Card/PayPal checkout via Tebex Headless — creates a basket and redirects to
 * Tebex's checkout page (still Tebex-hosted for the actual payment form, but
 * initiated from our own site instead of the old external storefront link).
 * Fulfillment happens off Tebex's payment.completed webhook, same as before.
 * @returns {Promise<{checkout_url: string, basket_ident: string}>}
 */
export const createTebexCheckout = (packageId, username) =>
  apiFetch('/api/checkout/tebex', {
    method: 'POST',
    body: JSON.stringify({ package_id: packageId, username }),
  });
