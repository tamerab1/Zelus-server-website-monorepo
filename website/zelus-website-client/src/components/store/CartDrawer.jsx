/**
 * CartDrawer — Slide-out cart panel (fixed right edge).
 *
 * Props:
 *   isOpen     — boolean
 *   items      — [{ item, qty }]
 *   total      — number (USD)
 *   onClose    — fn()
 *   onUpdateQty — fn(itemId, delta)  delta = +1 | -1
 *   onRemove   — fn(itemId)
 *   onClear    — fn()
 *   onCheckout — fn()
 */
import { useEffect } from 'react';

// ── Single item row inside the drawer ────────────────────────────────────────
function CartItemRow({ ci, onUpdateQty, onRemove }) {
  const { item, qty } = ci;

  return (
    <li
      className="flex items-start gap-3 py-3"
      style={{ borderBottom: '1px solid rgba(255,255,255,0.05)' }}
    >
      {/* Icon / emoji */}
      <div
        className="w-10 h-10 shrink-0 flex items-center justify-center rounded-sm text-xl"
        style={{
          background: `${item.badge}18`,
          border: `1px solid ${item.badge}33`,
        }}
      >
        {item.iconFallback}
      </div>

      {/* Name + price */}
      <div className="flex-1 min-w-0">
        <p
          className="font-fantasy text-xs tracking-wide truncate"
          style={{ color: '#e8e0d0' }}
        >
          {item.name}
        </p>
        <p className="font-mono text-xs mt-0.5" style={{ color: item.badge }}>
          ${(item.price * qty).toFixed(2)}
        </p>
      </div>

      {/* Qty controls */}
      <div className="flex items-center gap-1 shrink-0">
        <button
          onClick={() => onUpdateQty(item.id, -1)}
          className="w-6 h-6 flex items-center justify-center rounded-sm text-xs transition-colors duration-100"
          style={{ background: 'rgba(255,255,255,0.06)', color: '#9a9080' }}
          onMouseOver={e => { e.currentTarget.style.background = 'rgba(212,175,55,0.2)'; e.currentTarget.style.color = '#d4af37'; }}
          onMouseOut={e  => { e.currentTarget.style.background = 'rgba(255,255,255,0.06)'; e.currentTarget.style.color = '#9a9080'; }}
          aria-label="Decrease quantity"
        >
          −
        </button>
        <span className="w-5 text-center font-mono text-xs" style={{ color: '#e8e0d0' }}>
          {qty}
        </span>
        <button
          onClick={() => onUpdateQty(item.id, +1)}
          className="w-6 h-6 flex items-center justify-center rounded-sm text-xs transition-colors duration-100"
          style={{ background: 'rgba(255,255,255,0.06)', color: '#9a9080' }}
          onMouseOver={e => { e.currentTarget.style.background = 'rgba(212,175,55,0.2)'; e.currentTarget.style.color = '#d4af37'; }}
          onMouseOut={e  => { e.currentTarget.style.background = 'rgba(255,255,255,0.06)'; e.currentTarget.style.color = '#9a9080'; }}
          aria-label="Increase quantity"
        >
          +
        </button>
        <button
          onClick={() => onRemove(item.id)}
          className="w-6 h-6 ml-1 flex items-center justify-center rounded-sm text-xs transition-colors duration-100"
          style={{ color: '#5a5248' }}
          onMouseOver={e => { e.currentTarget.style.color = '#f87171'; }}
          onMouseOut={e  => { e.currentTarget.style.color = '#5a5248'; }}
          aria-label={`Remove ${item.name}`}
        >
          ✕
        </button>
      </div>
    </li>
  );
}

// ── Drawer ────────────────────────────────────────────────────────────────────
export default function CartDrawer({
  isOpen,
  items,
  total,
  onClose,
  onUpdateQty,
  onRemove,
  onClear,
  onCheckout,
}) {
  // Lock body scroll when drawer is open
  useEffect(() => {
    if (isOpen) document.body.style.overflow = 'hidden';
    else        document.body.style.overflow = '';
    return () => { document.body.style.overflow = ''; };
  }, [isOpen]);

  const itemCount = items.reduce((s, ci) => s + ci.qty, 0);
  const isEmpty   = items.length === 0;

  return (
    <>
      {/* ── Backdrop ─────────────────────────────────────────────────── */}
      <div
        className="fixed inset-0 z-40 transition-opacity duration-300"
        style={{
          background: 'rgba(0,0,0,0.65)',
          opacity:    isOpen ? 1 : 0,
          pointerEvents: isOpen ? 'auto' : 'none',
        }}
        onClick={onClose}
        aria-hidden="true"
      />

      {/* ── Drawer panel ─────────────────────────────────────────────── */}
      <aside
        className="fixed top-0 right-0 h-full z-50 flex flex-col"
        style={{
          width: '360px',
          maxWidth: '100vw',
          background: 'linear-gradient(160deg, #1a1a26 0%, #12121c 100%)',
          borderLeft: '1px solid #2e2820',
          borderTop:  '2px solid #d4af37',
          boxShadow: '-8px 0 40px rgba(0,0,0,0.8)',
          transform: isOpen ? 'translateX(0)' : 'translateX(100%)',
          transition: 'transform 0.3s cubic-bezier(0.22, 1, 0.36, 1)',
        }}
        aria-label="Shopping cart"
        role="dialog"
        aria-modal="true"
      >

        {/* Header */}
        <div
          className="flex items-center justify-between px-5 py-4 shrink-0"
          style={{
            background: 'linear-gradient(90deg, rgba(212,175,55,0.1) 0%, transparent 100%)',
            borderBottom: '1px solid rgba(212,175,55,0.22)',
          }}
        >
          <div className="flex items-center gap-3">
            <span className="text-lg">🛒</span>
            <span className="font-fantasy text-sm tracking-widest" style={{ color: '#d4af37' }}>
              YOUR CART
            </span>
            {itemCount > 0 && (
              <span
                className="font-mono text-xs px-1.5 py-0.5 rounded"
                style={{ background: 'rgba(212,175,55,0.2)', color: '#d4af37' }}
              >
                {itemCount}
              </span>
            )}
          </div>

          {/* Close button */}
          <button
            onClick={onClose}
            className="w-7 h-7 flex items-center justify-center rounded-sm text-sm transition-colors duration-100"
            style={{ color: '#5a5248' }}
            onMouseOver={e => { e.currentTarget.style.color = '#fff'; e.currentTarget.style.background = 'rgba(255,255,255,0.08)'; }}
            onMouseOut={e  => { e.currentTarget.style.color = '#5a5248'; e.currentTarget.style.background = 'transparent'; }}
            aria-label="Close cart"
          >
            ✕
          </button>
        </div>

        {/* Items list — scrollable */}
        <div className="flex-grow overflow-y-auto px-5">
          {isEmpty ? (
            /* ── Empty state ── */
            <div className="flex flex-col items-center justify-center h-full gap-4 py-16">
              <span className="text-5xl opacity-30">🛒</span>
              <p className="font-fantasy text-sm tracking-wider" style={{ color: '#5a5248' }}>
                Your cart is empty
              </p>
              <p className="text-xs text-center" style={{ color: '#3a3830' }}>
                Browse the store and add items to get started.
              </p>
            </div>
          ) : (
            <ul>
              {items.map(ci => (
                <CartItemRow
                  key={ci.item.id}
                  ci={ci}
                  onUpdateQty={onUpdateQty}
                  onRemove={onRemove}
                />
              ))}
            </ul>
          )}
        </div>

        {/* Footer — always visible at the bottom */}
        {!isEmpty && (
          <div
            className="shrink-0 px-5 py-5"
            style={{ borderTop: '1px solid rgba(255,255,255,0.07)' }}
          >
            {/* Order summary */}
            <div className="flex justify-between items-center mb-1">
              <span className="font-fantasy text-xs tracking-wider" style={{ color: '#6a6058' }}>
                SUBTOTAL
              </span>
              <span className="font-fantasy text-xs" style={{ color: '#c8bfb0' }}>
                {itemCount} item{itemCount !== 1 ? 's' : ''}
              </span>
            </div>
            <div className="flex justify-between items-center mb-5">
              <span className="font-fantasy text-xl font-bold" style={{ color: '#d4af37' }}>
                ${total.toFixed(2)}
              </span>
              <span className="text-xs" style={{ color: '#5a5248' }}>USD</span>
            </div>

            {/* Checkout CTA */}
            <button
              onClick={onCheckout}
              className="w-full py-3.5 font-fantasy text-sm tracking-widest uppercase font-bold rounded-sm transition-all duration-200 mb-3"
              style={{
                background: 'linear-gradient(180deg, #e8c040 0%, #d4af37 50%, #a87c18 100%)',
                border: '1px solid #f0d060',
                color: '#0a0800',
                boxShadow: '0 0 20px rgba(212,175,55,0.3)',
              }}
              onMouseOver={e => {
                e.currentTarget.style.background = 'linear-gradient(180deg, #f5d050 0%, #e8c040 50%, #c08820 100%)';
                e.currentTarget.style.boxShadow = '0 0 30px rgba(212,175,55,0.5)';
              }}
              onMouseOut={e => {
                e.currentTarget.style.background = 'linear-gradient(180deg, #e8c040 0%, #d4af37 50%, #a87c18 100%)';
                e.currentTarget.style.boxShadow = '0 0 20px rgba(212,175,55,0.3)';
              }}
            >
              🔒 Proceed to Checkout
            </button>

            {/* Clear cart */}
            <button
              onClick={onClear}
              className="w-full py-2 font-fantasy text-xs tracking-widest transition-colors duration-150"
              style={{ color: '#4a4440' }}
              onMouseOver={e => { e.currentTarget.style.color = '#f87171'; }}
              onMouseOut={e  => { e.currentTarget.style.color = '#4a4440'; }}
            >
              Clear Cart
            </button>

            {/* Trust note */}
            <p className="text-center text-xs mt-3" style={{ color: '#3a3428' }}>
              🔒 Secure checkout via Stripe &amp; PayPal
            </p>
          </div>
        )}
      </aside>
    </>
  );
}
