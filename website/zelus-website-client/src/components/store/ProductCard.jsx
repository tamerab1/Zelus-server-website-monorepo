/**
 * ProductCard — Universal card component for all store categories.
 *
 * Handles: dp | packages | ranks | boosters | misc
 *
 * Props:
 *   item        — item object from storeItems.js
 *   onAddToCart — fn(item) — add to cart
 *   onBuyNow    — fn(item) — open CheckoutModal directly
 */
import { useState } from 'react';

// ── Dynamic item icon with loading state and category-aware rendering ─────────
function ItemIcon({ item, size = 'lg' }) {
  const [errored, setErrored] = useState(false);
  const [loaded,  setLoaded]  = useState(false);

  // Emoji fallback when no icon_path or image fails to load
  if (errored || !item.icon_path) {
    return (
      <span
        className={`${size === 'lg' ? 'text-5xl' : 'text-2xl'} leading-none select-none`}
        role="img"
        aria-label={item.name}
      >
        {item.iconFallback ?? '◆'}
      </span>
    );
  }

  // Packages use thematic renders (boss/item detail) — smooth scaling + strong glow
  if (item.category === 'packages') {
    return (
      <div className="relative flex items-center justify-center" style={{ width: 80, height: 80 }}>
        {!loaded && (
          <span style={{ fontSize: 28, opacity: 0.2, position: 'absolute' }}>
            {item.iconFallback}
          </span>
        )}
        <img
          src={item.icon_path}
          alt={item.name}
          onError={() => setErrored(true)}
          onLoad={() => setLoaded(true)}
          className="w-20 h-20 object-contain"
          style={{
            imageRendering: 'auto',
            filter: `drop-shadow(0 0 10px ${item.badge ?? '#d4af37'}77) drop-shadow(0 0 20px ${item.badge ?? '#d4af37'}33)`,
            opacity: loaded ? 1 : 0,
            transition: 'opacity 0.3s ease',
          }}
        />
      </div>
    );
  }

  // All other categories (dp, ranks, boosters, misc) — pixelated inventory sprites
  const sizeClass = size === 'lg' ? 'w-14 h-14' : 'w-10 h-10';
  const isRank    = item.category === 'ranks';
  return (
    <div className={`relative ${sizeClass} flex items-center justify-center`}>
      {!loaded && (
        <span
          style={{ fontSize: size === 'lg' ? 28 : 20, opacity: 0.2, position: 'absolute' }}
        >
          {item.iconFallback}
        </span>
      )}
      <img
        src={item.icon_path}
        alt={item.name}
        onError={() => setErrored(true)}
        onLoad={() => setLoaded(true)}
        className={`${sizeClass} object-contain absolute inset-0`}
        style={{
          imageRendering: 'pixelated',
          filter: isRank
            ? `drop-shadow(0 0 6px ${item.badge ?? '#d4af37'}cc) drop-shadow(0 0 12px ${item.badge ?? '#d4af37'}66)`
            : `drop-shadow(0 0 6px ${item.badge ?? '#d4af37'}99) drop-shadow(0 0 12px ${item.badge ?? '#d4af37'}44)`,
          opacity: loaded ? 1 : 0,
          transition: 'opacity 0.25s ease',
        }}
      />
    </div>
  );
}

// ── Wiki reward icon (packages) ───────────────────────────────────────────────
function RewardIcon({ src, label }) {
  const [errored, setErrored] = useState(false);
  if (errored) return <span className="text-sm leading-none">◆</span>;
  return (
    <img
      src={src}
      alt={label}
      onError={() => setErrored(true)}
      className="w-5 h-5 object-contain shrink-0"
      style={{ imageRendering: 'pixelated' }}
    />
  );
}

// ── Category-specific card body content ──────────────────────────────────────

function CoinsContent({ item }) {
  return (
    <div className="flex-grow px-5 py-4">
      <div className="flex items-center justify-center gap-3 mb-4">
        <ItemIcon item={item} />
      </div>
      <div className="text-center">
        <p className="font-fantasy text-2xl font-bold tracking-wide" style={{ color: item.badge }}>
          {item.quantity?.toLocaleString()}
        </p>
        <p className="font-fantasy text-xs tracking-widest mt-0.5" style={{ color: '#8a8070' }}>
          DONATOR POINTS
        </p>
        {item.bonus && (
          <div
            className="mt-3 inline-block px-3 py-1 rounded-full font-fantasy text-xs tracking-wider"
            style={{ background: `${item.badge}22`, border: `1px solid ${item.badge}44`, color: item.badge }}
          >
            {item.bonus}
          </div>
        )}
      </div>
      <p className="text-center text-xs mt-3 leading-snug" style={{ color: '#7a7060' }}>
        {item.description}
      </p>
    </div>
  );
}

function PackageContent({ item }) {
  return (
    <div className="flex-grow px-5 py-4">
      <div className="flex items-center justify-center mb-4">
        <ItemIcon item={item} />
      </div>
      <p className="text-xs text-center mb-4 leading-snug" style={{ color: '#8a8070' }}>
        {item.description}
      </p>
      {item.rewards?.length > 0 && (
        <>
          <p
            className="font-fantasy text-xs tracking-widest mb-2"
            style={{ color: '#6a6058' }}
          >
            INCLUDES
          </p>
          <ul className="space-y-2">
            {item.rewards.map((r, i) => (
              <li key={i} className="flex items-center gap-2.5">
                <RewardIcon src={r.icon} label={r.label} />
                <span className="text-xs" style={{ color: '#c8bfb0' }}>{r.label}</span>
              </li>
            ))}
          </ul>
        </>
      )}
    </div>
  );
}

function RankContent({ item }) {
  const [expanded, setExpanded] = useState(false);
  const PREVIEW = 3; // benefits shown before "show more"
  const hasMore = item.benefits?.length > PREVIEW;
  const visible = expanded ? item.benefits : item.benefits?.slice(0, PREVIEW);

  return (
    <div className="flex-grow px-5 py-4">
      {/* Icon + token count */}
      <div className="flex flex-col items-center gap-1 mb-4">
        <ItemIcon item={item} />
        {item.tokens && (
          <span className="font-mono text-xs tracking-widest" style={{ color: '#d4af37' }}>
            +{item.tokens.toLocaleString()} DONATOR POINTS
          </span>
        )}
      </div>

      <p className="text-xs text-center mb-4 leading-snug" style={{ color: '#8a8070' }}>
        {item.description}
      </p>

      {/* Benefits accordion */}
      {item.benefits?.length > 0 && (
        <div>
          <p className="font-fantasy text-xs tracking-widest mb-2" style={{ color: '#6a6058' }}>
            PERKS
          </p>
          <ul className="space-y-1.5">
            {visible?.map((b, i) => (
              <li key={i} className="flex items-start gap-2">
                <span className="text-xs mt-0.5 shrink-0" style={{ color: item.badge }}>✦</span>
                <span className="text-xs leading-snug" style={{ color: '#c8bfb0' }}>{b}</span>
              </li>
            ))}
          </ul>

          {/* Expand / Collapse toggle */}
          {hasMore && (
            <button
              onClick={() => setExpanded(v => !v)}
              className="mt-3 w-full flex items-center justify-center gap-1.5 py-1.5 rounded-sm font-fantasy text-xs tracking-wider transition-all duration-150"
              style={{
                background: 'rgba(255,255,255,0.04)',
                border: '1px solid rgba(255,255,255,0.07)',
                color: '#8a8070',
              }}
              onMouseOver={e => {
                e.currentTarget.style.background = `${item.badge}18`;
                e.currentTarget.style.borderColor = `${item.badge}40`;
                e.currentTarget.style.color = item.badge;
              }}
              onMouseOut={e => {
                e.currentTarget.style.background = 'rgba(255,255,255,0.04)';
                e.currentTarget.style.borderColor = 'rgba(255,255,255,0.07)';
                e.currentTarget.style.color = '#8a8070';
              }}
            >
              <span
                className="text-xs transition-transform duration-200"
                style={{ display: 'inline-block', transform: expanded ? 'rotate(180deg)' : 'none' }}
              >
                ▼
              </span>
              <span>
                {expanded
                  ? 'Show Less'
                  : `+${item.benefits.length - PREVIEW} More Perks`}
              </span>
            </button>
          )}
        </div>
      )}
    </div>
  );
}

function BoosterContent({ item }) {
  return (
    <div className="flex-grow px-5 py-4">
      <div className="flex items-center justify-center mb-4">
        <ItemIcon item={item} />
      </div>
      <p className="text-xs text-center mb-3 leading-snug" style={{ color: '#8a8070' }}>
        {item.description}
      </p>
      {item.duration && (
        <div className="flex items-center justify-center gap-2">
          <span style={{ color: '#6a6058', fontSize: '11px' }}>⏱</span>
          <span
            className="font-fantasy text-xs tracking-wider"
            style={{ color: item.badge }}
          >
            {item.duration}
          </span>
        </div>
      )}
    </div>
  );
}

function MiscContent({ item }) {
  return (
    <div className="flex-grow px-5 py-4">
      <div className="flex items-center justify-center mb-4">
        <ItemIcon item={item} />
      </div>
      <p className="text-xs text-center leading-snug" style={{ color: '#8a8070' }}>
        {item.description}
      </p>
    </div>
  );
}

// ── Main card shell ───────────────────────────────────────────────────────────
export default function ProductCard({ item, onAddToCart, onBuyNow }) {
  const [inCart, setInCart] = useState(false);

  const handleAddToCart = () => {
    onAddToCart(item);
    setInCart(true);
    // Reset the "added" flash after 1.5 s
    setTimeout(() => setInCart(false), 1500);
  };

  const renderBody = () => {
    switch (item.category) {
      case 'dp':       return <CoinsContent    item={item} />;
      case 'packages': return <PackageContent  item={item} />;
      case 'ranks':    return <RankContent     item={item} />;
      case 'boosters': return <BoosterContent  item={item} />;
      case 'misc':     return <MiscContent     item={item} />;
      default:         return <MiscContent     item={item} />;
    }
  };

  return (
    <article
      className="stone-panel flex flex-col relative transition-all duration-300 hover:-translate-y-1"
      style={{
        borderRadius: 3,
        borderTopColor: item.badge,
        /* subtle glow intensifies on hover via inline override — handled by onMouseOver */
      }}
      onMouseOver={e => {
        e.currentTarget.style.boxShadow =
          `0 12px 40px rgba(0,0,0,0.7), 0 0 0 1px ${item.badge}22, inset 0 1px 0 rgba(255,255,255,0.07)`;
      }}
      onMouseOut={e => {
        e.currentTarget.style.boxShadow =
          '0 8px 32px rgba(0,0,0,0.6), inset 0 1px 0 rgba(255,255,255,0.06)';
      }}
    >
      {/* ── Ribbon badge (POPULAR / BEST VALUE / etc.) ── */}
      {item.badge_label && (
        <div className="absolute -top-3 left-1/2 -translate-x-1/2 z-10">
          <span
            className="font-fantasy text-xs tracking-widest px-3 py-1 rounded-full"
            style={{ background: item.badge, color: '#0a0a14', fontWeight: 700 }}
          >
            {item.badge_label}
          </span>
        </div>
      )}

      {/* ── Gradient banner / name ── */}
      <div
        className={`bg-gradient-to-br ${item.color ?? 'from-gray-700 to-gray-950'} px-5 pt-7 pb-4 text-center`}
      >
        <h3
          className="font-fantasy text-sm font-bold text-white tracking-widest"
          style={{ textShadow: '0 2px 10px rgba(0,0,0,0.9)' }}
        >
          {item.name.toUpperCase()}
        </h3>
        {/* colour accent line */}
        <div
          className="mt-2 w-10 h-px mx-auto"
          style={{
            background: `linear-gradient(90deg, transparent, ${item.badge}, transparent)`,
          }}
        />
      </div>

      {/* ── Price row ── */}
      <div
        className="text-center py-3 px-4"
        style={{ borderBottom: '1px solid rgba(255,255,255,0.05)' }}
      >
        <span className="font-fantasy text-3xl font-bold" style={{ color: item.badge }}>
          ${item.price}
        </span>
        <span className="text-xs ml-1" style={{ color: '#5a5248' }}>USD</span>
      </div>

      {/* ── Category-specific body ── */}
      {renderBody()}

      {/* ── CTA buttons ── */}
      <div className="px-4 pb-5 pt-3 flex flex-col gap-2">
        {/* Add to Cart */}
        <button
          onClick={handleAddToCart}
          className="w-full py-2.5 font-fantasy text-xs tracking-widest uppercase font-bold rounded-sm transition-all duration-200 flex items-center justify-center gap-2"
          style={{
            background: inCart ? `${item.badge}33` : 'rgba(255,255,255,0.05)',
            border: `1px solid ${inCart ? item.badge : 'rgba(255,255,255,0.1)'}`,
            color: inCart ? item.badge : '#9a9080',
          }}
          onMouseOver={e => {
            if (!inCart) {
              e.currentTarget.style.background = `${item.badge}18`;
              e.currentTarget.style.borderColor = `${item.badge}55`;
              e.currentTarget.style.color = item.badge;
            }
          }}
          onMouseOut={e => {
            if (!inCart) {
              e.currentTarget.style.background = 'rgba(255,255,255,0.05)';
              e.currentTarget.style.borderColor = 'rgba(255,255,255,0.1)';
              e.currentTarget.style.color = '#9a9080';
            }
          }}
        >
          {inCart ? (
            <>
              <span>✓</span>
              <span>Added!</span>
            </>
          ) : (
            <>
              <span>🛒</span>
              <span>Add to Cart</span>
            </>
          )}
        </button>

        {/* Buy Now */}
        <button
          onClick={() => onBuyNow(item)}
          className="w-full py-2.5 font-fantasy text-xs tracking-widest uppercase font-bold rounded-sm transition-all duration-200"
          style={{
            background: `${item.badge}22`,
            border: `1px solid ${item.badge}66`,
            color: item.badge,
          }}
          onMouseOver={e => {
            e.currentTarget.style.background = item.badge;
            e.currentTarget.style.color = '#0a0a14';
          }}
          onMouseOut={e => {
            e.currentTarget.style.background = `${item.badge}22`;
            e.currentTarget.style.color = item.badge;
          }}
        >
          Buy Now
        </button>
      </div>
    </article>
  );
}
