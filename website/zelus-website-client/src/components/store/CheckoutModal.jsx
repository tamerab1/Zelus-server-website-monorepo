/**
 * CheckoutModal
 *
 * Three-step flow:
 *   1. "method"   — collect in-game username + choose payment provider
 *   2. "confirm"  — OSRS GP only: show GP total & confirm
 *   3. "done"     — show success / error result for non-redirect providers
 *
 * Tebex (card/PayPal) and Crypto (NOWPayments) both redirect immediately (no
 * extra confirm step) — fulfillment happens off each provider's webhook.
 * OSRS GP opens a Discord ticket and stays on-page with a success message.
 * Tebex's checkout page itself is still Tebex-hosted (that part can't move
 * on-site — Tebex, not us, handles the actual card/PayPal payment form), but
 * the basket is created from our own site now instead of the old external
 * "Shop with Tebex" storefront link.
 *
 * Cart mode: when `pkg.cartLines` is present (StoreView's cart checkout), this
 * is a multi-item purchase. Only Tebex supports that today — its basket model
 * natively holds multiple packages, Stripe/PayPal/NOWPayments' single-amount-
 * per-session model doesn't — so Crypto and OSRS GP are hidden in that case
 * rather than silently failing.
 */
import { useState } from 'react';
import {
  createTebexCheckout,
  createTebexCartCheckout,
  createCryptoCheckout,
  initiateOsrsGpCheckout,
  calcGpMillions,
  OSRS_RATE_USD_PER_M,
} from '../../services/paymentService.js';

// ── Brand colours ──────────────────────────────────────────────────────────────
const TEBEX_BLUE    = '#4c86ff';
const GP_GREEN      = '#22c55e';
const CRYPTO_PURPLE = '#8b5cf6';

const REDIRECT_CHECKOUT_FNS = {
  tebex:  createTebexCheckout,
  crypto: createCryptoCheckout,
};

// ── Shared sub-components ──────────────────────────────────────────────────────

function Spinner({ color = '#fff' }) {
  return (
    <span
      className="inline-block w-4 h-4 border-2 border-t-transparent rounded-full animate-spin shrink-0"
      style={{ borderColor: `${color} ${color} ${color} transparent` }}
    />
  );
}

function ErrorBox({ msg }) {
  if (!msg) return null;
  return (
    <div
      className="p-3 text-xs text-center font-bold font-fantasy tracking-widest"
      style={{
        background: 'rgba(127,29,29,0.3)',
        border: '1px solid #991b1b',
        borderRadius: 2,
        color: '#fca5a5',
      }}
    >
      {msg}
    </div>
  );
}

function UsernameField({ value, onChange, disabled }) {
  return (
    <div>
      <label
        htmlFor="checkout-username"
        className="block font-fantasy text-xs tracking-widest mb-2"
        style={{ color: '#8a8070' }}
      >
        IN-GAME USERNAME
      </label>
      <input
        id="checkout-username"
        type="text"
        value={value}
        onChange={e => onChange(e.target.value)}
        maxLength={12}
        placeholder="Enter your character name"
        disabled={disabled}
        className="w-full px-3 py-2 text-sm font-mono"
        style={{
          background: 'rgba(0,0,0,0.5)',
          border: '1px solid #2a2520',
          borderRadius: 2,
          color: '#e8e0d0',
          outline: 'none',
        }}
        onFocus={e => (e.currentTarget.style.borderColor = '#d4af37')}
        onBlur={e  => (e.currentTarget.style.borderColor = '#2a2520')}
      />
      <p className="text-xs mt-1" style={{ color: '#555' }}>
        Items delivered here — type{' '}
        <span className="font-mono" style={{ color: '#d4af37' }}>::claim</span>{' '}
        in-game after payment.
      </p>
    </div>
  );
}

// ── Main component ─────────────────────────────────────────────────────────────

export default function CheckoutModal({ pkg, onClose }) {
  const [username, setUsername] = useState('');
  const [step,     setStep]     = useState('method');   // 'method' | 'confirm' | 'done'
  const [method,   setMethod]   = useState(null);       // 'tebex' | 'crypto' | 'osrs-gp'
  const [loading,  setLoading]  = useState(false);
  const [error,    setError]    = useState('');
  const [result,   setResult]   = useState(null);       // {message, gp_millions?} on done

  if (!pkg) return null;

  const isCartMode = Boolean(pkg.cartLines?.length);
  const packageId  = pkg.slug ?? pkg.id;
  const gpMillions = calcGpMillions(pkg.price);

  // ── Validate username before any payment action ──────────────────────────────
  const validateUsername = () => {
    const t = username.trim();
    if (!t)        { setError('Please enter your in-game username.');         return null; }
    if (t.length > 12) { setError('Username must be 12 characters or fewer.'); return null; }
    setError('');
    return t;
  };

  // ── Tebex / Crypto — redirect immediately ────────────────────────────────────
  const handleRedirectPayment = async (provider) => {
    const name = validateUsername();
    if (!name) return;
    setLoading(true);
    setError('');
    try {
      const data = isCartMode
        ? await createTebexCartCheckout(pkg.cartLines, name)
        : await REDIRECT_CHECKOUT_FNS[provider](packageId, name);
      window.location.href = data.checkout_url;
      // (no setLoading(false) — page is navigating away)
    } catch (err) {
      setError(err.message || 'Payment could not be initiated. Please try again.');
      setLoading(false);
    }
  };

  // ── OSRS GP — go to confirm screen ───────────────────────────────────────────
  const handleSelectOsrsGp = () => {
    if (!validateUsername()) return;
    setMethod('osrs-gp');
    setStep('confirm');
    setError('');
  };

  // ── OSRS GP — confirm & fire Discord ticket ───────────────────────────────────
  const handleConfirmOsrsGp = async () => {
    const name = username.trim();
    setLoading(true);
    setError('');
    try {
      const data = await initiateOsrsGpCheckout(packageId, name);
      setResult({ message: data.message, gp_millions: data.gp_millions });
      setStep('done');
    } catch (err) {
      setError(err.message || 'Could not open ticket. Please try again or contact staff on Discord.');
    } finally {
      setLoading(false);
    }
  };

  // ── Render helpers ────────────────────────────────────────────────────────────

  const OrderSummary = () => (
    <div
      className="p-4 flex justify-between items-center"
      style={{
        background: 'rgba(0,0,0,0.4)',
        border: '1px solid rgba(255,255,255,0.05)',
        borderRadius: 2,
      }}
    >
      <div>
        <p className="font-fantasy text-sm font-bold" style={{ color: pkg.badge }}>
          {pkg.name}
        </p>
        {pkg.points && (
          <p className="font-mono text-xs mt-0.5" style={{ color: '#d4af37' }}>
            +{pkg.points.toLocaleString()} Donator Points
          </p>
        )}
      </div>
      <p className="font-fantasy text-2xl font-bold" style={{ color: '#fff' }}>
        ${pkg.price}
        <span className="text-xs ml-1" style={{ color: '#6a6058' }}>USD</span>
      </p>
    </div>
  );

  // ── STEP: method selection ────────────────────────────────────────────────────
  const renderMethodStep = () => (
    <div className="p-6 flex flex-col gap-5">
      <OrderSummary />

      <UsernameField
        value={username}
        onChange={v => { setUsername(v); setError(''); }}
        disabled={loading}
      />

      <ErrorBox msg={error} />

      <div className="flex flex-col gap-3">

        {/* Tebex — card / PayPal / etc, Tebex-hosted payment form */}
        <button
          onClick={() => handleRedirectPayment('tebex')}
          disabled={loading}
          className="w-full py-3.5 font-bold text-sm tracking-wide rounded-sm transition-all duration-200 flex items-center justify-center gap-2.5"
          style={{
            background: TEBEX_BLUE,
            color: '#fff',
            opacity: loading ? 0.5 : 1,
            cursor: loading ? 'not-allowed' : 'pointer',
          }}
        >
          {loading ? <Spinner color="#fff" /> : <span className="text-xl">💳</span>}
          <span className="font-sans font-bold">Pay with Card / PayPal</span>
        </button>

        {!isCartMode && (
          <>
            {/* Divider */}
            <div className="flex items-center gap-3">
              <div className="flex-1 h-px" style={{ background: '#2a2520' }} />
              <span className="font-fantasy text-xs tracking-widest" style={{ color: '#3a3530' }}>OR</span>
              <div className="flex-1 h-px" style={{ background: '#2a2520' }} />
            </div>

            {/* OSRS GP */}
            <button
              onClick={handleSelectOsrsGp}
              disabled={loading}
              className="w-full py-3.5 rounded-sm transition-all duration-200 flex items-center justify-between px-5 group"
              style={{
                background: 'rgba(34,197,94,0.08)',
                border: '1px solid rgba(34,197,94,0.3)',
                cursor: loading ? 'not-allowed' : 'pointer',
                opacity: loading ? 0.5 : 1,
              }}
              onMouseOver={e => {
                e.currentTarget.style.background = 'rgba(34,197,94,0.15)';
                e.currentTarget.style.borderColor = 'rgba(34,197,94,0.6)';
              }}
              onMouseOut={e => {
                e.currentTarget.style.background = 'rgba(34,197,94,0.08)';
                e.currentTarget.style.borderColor = 'rgba(34,197,94,0.3)';
              }}
            >
              <div className="flex items-center gap-3">
                <span className="text-xl">🪙</span>
                <div className="text-left">
                  <p className="font-fantasy text-xs tracking-widest" style={{ color: GP_GREEN }}>
                    PAY WITH OSRS GP
                  </p>
                  <p className="font-sans text-xs mt-0.5" style={{ color: '#6a6058' }}>
                    via Discord Staff Ticket
                  </p>
                </div>
              </div>
              <div className="text-right">
                <p className="font-fantasy text-base font-bold" style={{ color: GP_GREEN }}>
                  {gpMillions.toLocaleString()}M
                </p>
                <p className="font-sans text-xs" style={{ color: '#4a4038' }}>OSRS GP</p>
              </div>
            </button>

            {/* Crypto — via NOWPayments */}
            <button
              onClick={() => handleRedirectPayment('crypto')}
              disabled={loading}
              className="w-full py-3.5 rounded-sm transition-all duration-200 flex items-center justify-between px-5"
              style={{
                background: 'rgba(139,92,246,0.06)',
                border: '1px solid rgba(139,92,246,0.2)',
                cursor: loading ? 'not-allowed' : 'pointer',
                opacity: loading ? 0.5 : 1,
              }}
              onMouseOver={e => {
                e.currentTarget.style.background = 'rgba(139,92,246,0.12)';
                e.currentTarget.style.borderColor = 'rgba(139,92,246,0.4)';
              }}
              onMouseOut={e => {
                e.currentTarget.style.background = 'rgba(139,92,246,0.06)';
                e.currentTarget.style.borderColor = 'rgba(139,92,246,0.2)';
              }}
            >
              <div className="flex items-center gap-3">
                {loading ? <Spinner color={CRYPTO_PURPLE} /> : <span className="text-xl">₿</span>}
                <div className="text-left">
                  <p className="font-fantasy text-xs tracking-widest" style={{ color: CRYPTO_PURPLE }}>
                    PAY WITH CRYPTO
                  </p>
                  <p className="font-sans text-xs mt-0.5" style={{ color: '#6a6058' }}>
                    BTC, ETH, USDT & more — via NOWPayments
                  </p>
                </div>
              </div>
            </button>
          </>
        )}

      </div>

      <p className="text-center text-xs" style={{ color: '#3a3530' }}>
        {isCartMode
          ? '🔒 Card & PayPal processed securely via Tebex. Crypto and OSRS GP checkout aren’t available for multi-item carts yet — check those out individually.'
          : '🔒 Card & PayPal payments processed securely. We never store card details.'}
      </p>
    </div>
  );

  // ── STEP: confirm (OSRS GP or Crypto) ────────────────────────────────────────
  const renderConfirmStep = () => {
    if (method === 'osrs-gp') {
      return (
        <div className="p-6 flex flex-col gap-5">

          {/* Back */}
          <button
            onClick={() => { setStep('method'); setError(''); }}
            className="self-start font-fantasy text-xs tracking-widest transition-colors flex items-center gap-1.5"
            style={{ color: '#555' }}
            onMouseOver={e => (e.currentTarget.style.color = '#d4af37')}
            onMouseOut={e  => (e.currentTarget.style.color = '#555')}
          >
            ← Back
          </button>

          <OrderSummary />

          {/* GP breakdown */}
          <div
            className="p-5 text-center flex flex-col gap-3"
            style={{
              background: 'rgba(34,197,94,0.06)',
              border: '1px solid rgba(34,197,94,0.25)',
              borderRadius: 2,
            }}
          >
            <p className="font-fantasy text-xs tracking-widest" style={{ color: '#6a6058' }}>
              TOTAL TO PAY
            </p>
            <p
              className="font-fantasy text-5xl font-bold"
              style={{ color: GP_GREEN, textShadow: `0 0 20px rgba(34,197,94,0.4)` }}
            >
              {gpMillions.toLocaleString()}M
            </p>
            <p className="font-fantasy text-sm tracking-widest" style={{ color: '#8a8070' }}>
              OSRS GP
            </p>
            <p className="font-sans text-xs" style={{ color: '#4a4038' }}>
              Rate: ${OSRS_RATE_USD_PER_M} per 1M GP · equivalent to ${pkg.price} USD
            </p>
          </div>

          {/* Username confirmation */}
          <div
            className="px-4 py-3 flex justify-between items-center"
            style={{
              background: 'rgba(0,0,0,0.3)',
              border: '1px solid #1e1a14',
              borderRadius: 2,
            }}
          >
            <span className="font-fantasy text-xs tracking-widest" style={{ color: '#6a6058' }}>
              DELIVERING TO
            </span>
            <span className="font-mono text-sm" style={{ color: '#e8e0d0' }}>
              {username.trim()}
            </span>
          </div>

          {/* Info box */}
          <div
            className="p-3 text-xs leading-relaxed font-sans"
            style={{
              background: 'rgba(212,175,55,0.06)',
              border: '1px solid rgba(212,175,55,0.2)',
              borderRadius: 2,
              color: '#8a8070',
            }}
          >
            <span style={{ color: '#d4af37' }}>ℹ </span>
            Clicking <strong style={{ color: '#d4af37' }}>Confirm</strong> will notify our staff on
            Discord. A staff member will contact you within 24 hours to arrange the in-game
            gold trade. Items are delivered via <span className="font-mono" style={{ color: '#d4af37' }}>::claim</span> once
            the trade is confirmed.
          </div>

          <ErrorBox msg={error} />

          <button
            onClick={handleConfirmOsrsGp}
            disabled={loading}
            className="w-full py-3.5 font-fantasy text-xs tracking-widest uppercase font-bold rounded-sm transition-all duration-200 flex items-center justify-center gap-2.5"
            style={{
              background: loading ? 'rgba(34,197,94,0.3)' : 'rgba(34,197,94,0.15)',
              border: `1px solid ${loading ? GP_GREEN : 'rgba(34,197,94,0.4)'}`,
              color: GP_GREEN,
              cursor: loading ? 'not-allowed' : 'pointer',
            }}
            onMouseOver={e => {
              if (!loading) {
                e.currentTarget.style.background = 'rgba(34,197,94,0.25)';
                e.currentTarget.style.borderColor = GP_GREEN;
              }
            }}
            onMouseOut={e => {
              if (!loading) {
                e.currentTarget.style.background = 'rgba(34,197,94,0.15)';
                e.currentTarget.style.borderColor = 'rgba(34,197,94,0.4)';
              }
            }}
          >
            {loading ? <Spinner color={GP_GREEN} /> : <span>🎫</span>}
            {loading ? 'Opening Ticket…' : `Confirm & Open Ticket — ${gpMillions.toLocaleString()}M GP`}
          </button>
        </div>
      );
    }

    return null;   // only osrs-gp reaches this step — paypal/crypto redirect immediately
  };

  // ── STEP: done (OSRS GP result) ───────────────────────────────────────────────
  const renderDoneStep = () => (
    <div className="p-6 flex flex-col gap-5">
      <div
        className="p-6 text-center flex flex-col items-center gap-4"
        style={{
          background: 'rgba(34,197,94,0.06)',
          border: '1px solid rgba(34,197,94,0.25)',
          borderRadius: 2,
        }}
      >
        <span className="text-4xl">🎫</span>
        <p className="font-fantasy text-sm tracking-widest" style={{ color: GP_GREEN }}>
          TICKET OPENED!
        </p>
        <p className="font-sans text-xs leading-relaxed max-w-xs" style={{ color: '#8a8070' }}>
          {result?.message}
        </p>
        {result?.gp_millions && (
          <div
            className="px-4 py-2"
            style={{
              background: 'rgba(0,0,0,0.3)',
              border: '1px solid rgba(34,197,94,0.2)',
              borderRadius: 2,
            }}
          >
            <p className="font-fantasy text-xs tracking-widest" style={{ color: '#6a6058' }}>
              YOU ARE SENDING
            </p>
            <p className="font-fantasy text-2xl font-bold mt-1" style={{ color: GP_GREEN }}>
              {result.gp_millions.toLocaleString()}M OSRS GP
            </p>
          </div>
        )}
        <p className="font-sans text-xs" style={{ color: '#4a4038' }}>
          Keep an eye on your Discord DMs. Staff will contact you shortly.
        </p>
      </div>

      <button
        onClick={onClose}
        className="w-full py-3 font-fantasy text-xs tracking-widest uppercase rounded-sm transition-all duration-200"
        style={{
          background: 'rgba(255,255,255,0.04)',
          border: '1px solid #2a2520',
          color: '#6a6058',
        }}
        onMouseOver={e => (e.currentTarget.style.color = '#d4af37')}
        onMouseOut={e  => (e.currentTarget.style.color = '#6a6058')}
      >
        Close
      </button>
    </div>
  );

  // ── Shell ─────────────────────────────────────────────────────────────────────
  const titles = {
    method:  'SECURE CHECKOUT',
    confirm: 'PAY WITH OSRS GP',
    done:    'ORDER RECEIVED',
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4"
      style={{ background: 'rgba(0,0,0,0.8)', backdropFilter: 'blur(2px)' }}
      onClick={e => { if (e.target === e.currentTarget) onClose(); }}
    >
      <div
        className="stone-panel w-full max-w-md relative overflow-hidden"
        style={{ borderRadius: 3, borderTopColor: step === 'confirm' ? GP_GREEN : pkg.badge }}
      >
        {/* Close */}
        <button
          onClick={onClose}
          className="absolute top-3 right-4 text-lg leading-none z-10 transition-colors"
          style={{ color: '#555' }}
          onMouseOver={e => (e.currentTarget.style.color = '#fff')}
          onMouseOut={e  => (e.currentTarget.style.color = '#555')}
          aria-label="Close"
        >
          ✕
        </button>

        {/* Header */}
        <div className="panel-header">
          <span className="font-fantasy text-sm tracking-widest" style={{ color: '#d4af37' }}>
            {titles[step]}
          </span>
        </div>

        {step === 'method'  && renderMethodStep()}
        {step === 'confirm' && renderConfirmStep()}
        {step === 'done'    && renderDoneStep()}
      </div>
    </div>
  );
}
