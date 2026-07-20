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
 * Placeholder — creates a pending transaction; no automatic fulfillment yet.
 * @returns {Promise<{transaction_id: number, message: string}>}
 */
export const initiateCryptoCheckout = (packageId, username) =>
  apiFetch('/api/checkout/crypto', {
    method: 'POST',
    body: JSON.stringify({ package_id: packageId, username }),
  });
