/**
 * StoreView — Premium Zelus store with category navigation & cart system.
 *
 * Architecture:
 *   StoreView (state)
 *   ├── Page Header
 *   ├── CategoryNav   — sidebar (desktop) / tabs (mobile)
 *   ├── Product Grid  — filtered by activeCategory
 *   │   └── ProductCard × N
 *   ├── CartFAB       — floating action button (bottom-right)
 *   └── CartDrawer    — slide-out drawer
 *
 * Data flow:
 *   - Items come from storeItems.js (swap for API fetch later)
 *   - Cart state is local to this component
 *   - Individual purchases open the existing CheckoutModal via handleCheckout()
 *   - Cart checkout builds a synthetic package and opens the same modal
 *     TODO: replace with a multi-item cart API endpoint when backend is ready
 */
import { useState, useMemo } from 'react';
import { useApp } from '../../context/AppContext.jsx';
import AlertBox from '../ui/AlertBox.jsx';

import storeItems, { CATEGORIES } from '../../data/storeItems.js';
import CategoryNav from './CategoryNav.jsx';
import ProductCard from './ProductCard.jsx';
import CartDrawer  from './CartDrawer.jsx';

// ── Floating cart button ──────────────────────────────────────────────────────
function CartFAB({ count, onClick }) {
  return (
    <button
      onClick={onClick}
      className="fixed bottom-8 right-6 z-30 flex items-center gap-2 px-5 py-3 rounded-full font-fantasy text-sm tracking-wider shadow-xl transition-all duration-200"
      style={{
        background: 'linear-gradient(180deg, #1e1e2e 0%, #14141e 100%)',
        border: '1px solid rgba(212,175,55,0.5)',
        color: '#d4af37',
        boxShadow: '0 4px 24px rgba(0,0,0,0.7), 0 0 16px rgba(212,175,55,0.15)',
      }}
      onMouseOver={e => {
        e.currentTarget.style.border = '1px solid rgba(212,175,55,0.9)';
        e.currentTarget.style.boxShadow = '0 6px 32px rgba(0,0,0,0.8), 0 0 24px rgba(212,175,55,0.3)';
        e.currentTarget.style.transform = 'translateY(-2px)';
      }}
      onMouseOut={e => {
        e.currentTarget.style.border = '1px solid rgba(212,175,55,0.5)';
        e.currentTarget.style.boxShadow = '0 4px 24px rgba(0,0,0,0.7), 0 0 16px rgba(212,175,55,0.15)';
        e.currentTarget.style.transform = 'none';
      }}
      aria-label={`Open cart — ${count} item${count !== 1 ? 's' : ''}`}
    >
      <span className="text-base leading-none">🛒</span>
      <span>Cart</span>
      {count > 0 && (
        <span
          className="font-mono text-xs px-1.5 py-0.5 rounded-full"
          style={{ background: '#d4af37', color: '#0a0800', fontWeight: 700 }}
        >
          {count}
        </span>
      )}
    </button>
  );
}

// ── Section divider ───────────────────────────────────────────────────────────
function SectionDivider({ icon, label }) {
  return (
    <div className="flex items-center gap-4 mb-6">
      <div
        className="h-px flex-1"
        style={{ background: 'linear-gradient(90deg, transparent, rgba(212,175,55,0.3))' }}
      />
      <span className="font-fantasy text-sm tracking-[0.3em]" style={{ color: '#d4af37' }}>
        {icon}&nbsp;{label}
      </span>
      <div
        className="h-px flex-1"
        style={{ background: 'linear-gradient(90deg, rgba(212,175,55,0.3), transparent)' }}
      />
    </div>
  );
}

// ── Empty category placeholder ────────────────────────────────────────────────
function EmptyCategory({ category }) {
  return (
    <div className="col-span-full flex flex-col items-center justify-center py-20 gap-4">
      <span className="text-5xl opacity-30">{category?.icon ?? '🔍'}</span>
      <p className="font-fantasy text-sm tracking-wider" style={{ color: '#5a5248' }}>
        No items in this category yet
      </p>
      <p className="text-xs" style={{ color: '#3a3428' }}>
        Check back soon — more items are on the way.
      </p>
    </div>
  );
}

// ── Main view ─────────────────────────────────────────────────────────────────
export default function StoreView() {
  const { storeMessage, handleCheckout } = useApp();

  // ─── Category state ───
  const [activeCategory, setActiveCategory] = useState('dp');

  // ─── Cart state ───
  const [cartItems, setCartItems] = useState([]);   // [{ item, qty }]
  const [cartOpen,  setCartOpen]  = useState(false);

  // Item count per category (for nav badges)
  const categoryCounts = useMemo(() =>
    CATEGORIES.reduce((acc, cat) => ({
      ...acc,
      [cat.id]: storeItems.filter(i => i.category === cat.id).length,
    }), {}),
  []);

  // Items filtered for the active tab
  const filteredItems = useMemo(
    () => storeItems.filter(i => i.category === activeCategory),
    [activeCategory],
  );

  const activeCategoryMeta = CATEGORIES.find(c => c.id === activeCategory);

  // ─── Cart helpers ───
  const addToCart = (item) => {
    setCartItems(prev => {
      const hit = prev.find(ci => ci.item.id === item.id);
      return hit
        ? prev.map(ci => ci.item.id === item.id ? { ...ci, qty: ci.qty + 1 } : ci)
        : [...prev, { item, qty: 1 }];
    });
  };

  const updateQty = (itemId, delta) => {
    setCartItems(prev => {
      const hit = prev.find(ci => ci.item.id === itemId);
      if (!hit) return prev;
      if (hit.qty + delta <= 0) return prev.filter(ci => ci.item.id !== itemId);
      return prev.map(ci => ci.item.id === itemId ? { ...ci, qty: ci.qty + delta } : ci);
    });
  };

  const removeFromCart = (itemId) =>
    setCartItems(prev => prev.filter(ci => ci.item.id !== itemId));

  const clearCart = () => setCartItems([]);

  const cartTotal = cartItems.reduce((s, ci) => s + ci.item.price * ci.qty, 0);
  const cartCount = cartItems.reduce((s, ci) => s + ci.qty, 0);

  // ─── Cart checkout ───
  // Opens the existing CheckoutModal in "cart mode": `cartLines` carries the
  // REAL package_id/quantity pairs sent to the backend (createTebexCartCheckout),
  // while the rest of this object is display-only for the modal's order summary.
  // Previously sent a synthetic id/slug='cart' through the single-item endpoint,
  // which every provider's price validation correctly rejected as an unknown
  // package ("Unknown package: 'cart'") -- not Tebex-specific, every checkout
  // endpoint validates package_id against store_catalog.
  const handleCartCheckout = () => {
    if (!cartItems.length) return;
    const cartSummary = {
      id:        'cart-summary',
      name:      `Cart — ${cartCount} item${cartCount !== 1 ? 's' : ''}`,
      price:     cartTotal,
      badge:     '#d4af37',
      tokens:    null,
      cartLines: cartItems.map(ci => ({
        package_id: ci.item.slug ?? ci.item.id,
        quantity:   ci.qty,
      })),
    };
    handleCheckout(cartSummary);
    setCartOpen(false);
  };

  // Grid column count varies by category (ranks are tall — fewer columns looks better)
  const gridCols =
    activeCategory === 'ranks'
      ? 'grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4'
      : 'grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-3';

  return (
    <main className="store-page max-w-7xl mx-auto pt-16 px-4 sm:px-6 pb-32" style={{ width: '100%', maxWidth: '100%' }}>

      {/* ── Page header ─────────────────────────────────────────────── */}
      <div className="text-center mb-12">
        <div
          className="inline-block px-10 pt-8 pb-9 mb-2 rounded-sm"
          style={{
            background: 'linear-gradient(180deg, rgba(5,4,8,0.72) 0%, rgba(5,4,8,0.45) 100%)',
            backdropFilter: 'blur(2px)',
            boxShadow: '0 0 60px rgba(0,0,0,0.6)',
          }}
        >
          <p
            className="font-fantasy text-xs tracking-[0.55em] mb-3"
            style={{ color: '#d4af37', textShadow: '0 1px 8px rgba(0,0,0,0.9)' }}
          >
            SUPPORT THE SERVER
          </p>
          <h1
            className="font-fantasy text-4xl sm:text-5xl font-bold mb-4"
            style={{ color: '#ffffff', textShadow: '0 2px 20px rgba(0,0,0,0.9), 0 0 30px rgba(212,175,55,0.2)' }}
          >
            THE EMPORIUM
          </h1>
          <div className="gold-divider max-w-xs mx-auto mb-5" />
          <p
            className="max-w-lg mx-auto text-sm leading-relaxed"
            style={{ color: '#c8bfb0', textShadow: '0 1px 6px rgba(0,0,0,0.8)' }}
          >
            Unlock exclusive ranks, zones, items, and perks. All purchases are permanent.
          </p>
        </div>
      </div>

      {/* Alert (storeMessage from context — e.g. "Claimed!" feedback) */}
      <AlertBox status={storeMessage} className="max-w-2xl mx-auto mb-8" />

      {/* ── Store body: sidebar + content ────────────────────────────── */}
      {/* flex-col on mobile — a row here forces the full-width mobile category
          tabs to fight the content column for space, causing horizontal overflow */}
      <div className="store-body flex flex-col lg:flex-row gap-7 items-start w-full max-w-full">

        {/* Sidebar — desktop only; mobile tabs rendered inside CategoryNav */}
        <CategoryNav
          active={activeCategory}
          onChange={(id) => { setActiveCategory(id); window.scrollTo({ top: 0, behavior: 'smooth' }); }}
          counts={categoryCounts}
        />

        {/* Main content area */}
        <div className="flex-1 min-w-0">

          {/* Category heading */}
          <SectionDivider
            icon={activeCategoryMeta?.icon}
            label={activeCategoryMeta?.label?.toUpperCase() ?? ''}
          />

          {/* Category description — skipped on Ranks, where the banner below says it fully */}
          {activeCategoryMeta?.desc && activeCategory !== 'ranks' && (
            <p
              className="text-xs mb-6 -mt-2"
              style={{ color: '#c8bfb0' }}
            >
              {activeCategoryMeta.desc}
            </p>
          )}

          {/* Ranks: explain the auto-unlock model — no card here has a buy button.
              Solid dark backdrop (matches the page-header panel) so it reads over
              the busy hero background instead of blending into it. */}
          {activeCategory === 'ranks' && (
            <div
              className="mb-6 p-4 flex items-start gap-3"
              style={{
                background: 'rgba(5,4,8,0.88)',
                backdropFilter: 'blur(2px)',
                border: '1px solid rgba(212,175,55,0.3)',
                borderRadius: 2,
                boxShadow: '0 4px 24px rgba(0,0,0,0.5)',
              }}
            >
              <span style={{ color: '#d4af37', fontSize: '16px' }}>ℹ</span>
              <p className="text-xs leading-relaxed" style={{ color: '#e8e0d0' }}>
                Donator ranks are <strong style={{ color: '#f0d060' }}>not purchased directly</strong> — they unlock
                automatically once your lifetime <strong style={{ color: '#f0d060' }}>Total Donated</strong> reaches
                each threshold. Buy Donator Points or any other item in the store to build up your total; your rank
                (and its perks) upgrades on its own the moment you cross the line.
              </p>
            </div>
          )}

          {/* Product grid */}
          <div className={`grid ${gridCols} gap-5`}>
            {filteredItems.length === 0 ? (
              <EmptyCategory category={activeCategoryMeta} />
            ) : (
              filteredItems.map(item => (
                <ProductCard
                  key={item.id}
                  item={item}
                  onAddToCart={addToCart}
                  onBuyNow={handleCheckout}
                />
              ))
            )}
          </div>

          {/* ── How it works panel ─────────────────────────────────── */}
          <div className="mt-14 stone-panel" style={{ borderRadius: 3 }}>
            <div className="panel-header">
              <span className="font-fantasy text-sm tracking-widest" style={{ color: '#d4af37' }}>
                ℹ &nbsp;HOW IT WORKS
              </span>
            </div>
            <div className="p-6 grid grid-cols-1 sm:grid-cols-3 gap-6">
              {[
                {
                  icon:  '📦',
                  title: 'Permanent',
                  body:  'All ranks and items are permanent and never expire.',
                },
                {
                  icon:  '⬆',
                  title: 'Stackable',
                  body:  'Donating again upgrades your rank. All previous benefits carry over.',
                },
                {
                  icon:  '🎁',
                  title: 'Instant',
                  body:  'Items and ranks are applied in-game within minutes. Use ::claim.',
                },
              ].map(({ icon, title, body }) => (
                <div key={title} className="flex gap-4">
                  <span className="text-2xl shrink-0">{icon}</span>
                  <div>
                    <p className="font-fantasy text-sm tracking-wide mb-1" style={{ color: '#e8e0d0' }}>
                      {title}
                    </p>
                    <p className="text-xs leading-relaxed" style={{ color: '#9a8f80' }}>
                      {body}
                    </p>
                  </div>
                </div>
              ))}
            </div>
          </div>

        </div>
      </div>

      {/* ── Floating cart button ───────────────────────────────────────── */}
      <CartFAB count={cartCount} onClick={() => setCartOpen(true)} />

      {/* ── Cart drawer ───────────────────────────────────────────────── */}
      <CartDrawer
        isOpen={cartOpen}
        items={cartItems}
        total={cartTotal}
        onClose={() => setCartOpen(false)}
        onUpdateQty={updateQty}
        onRemove={removeFromCart}
        onClear={clearCart}
        onCheckout={handleCartCheckout}
      />

    </main>
  );
}
