// Re-export from paymentService for backwards compatibility.
// All new code should import directly from paymentService.js.
export { createStripeCheckout, createPayPalCheckout } from './paymentService.js';
