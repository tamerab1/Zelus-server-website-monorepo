/**
 * ProductCard — Universal card component for all store categories.
 *
 * Handles: dp | ranks
 *
 * Props:
 *   item        — item object from storeItems.js
 *   onAddToCart — fn(item) — add to cart
 *   onBuyNow    — fn(item) — open CheckoutModal directly
 */
import { useState } from 'react';
import './ProductCard.css';

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

  // dp / ranks — pixelated inventory sprites
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

// ── Category-specific card body content ──────────────────────────────────────

/** "+25% bonus" -> "+25% BONUS POINTS" */
const bonusTag = (bonus) => bonus.replace(/\s*bonus\s*$/i, '').trim().toUpperCase() + ' BONUS POINTS';

function CoinsContent({ item }) {
  return (
    <div className="zs-body">
      <ItemIcon item={item} />
      <div className="zs-points font-fantasy">{item.quantity?.toLocaleString()}</div>
      <div className="zs-points-label">DONATOR POINTS</div>
      {item.bonus && <span className="zs-bonus">{bonusTag(item.bonus)}</span>}
      <p className="zs-desc">{item.description}</p>
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

      <p className="text-xs text-center mb-4 leading-snug" style={{ color: '#c9d1d9' }}>
        {item.description}
      </p>

      {/* Benefits accordion */}
      {item.benefits?.length > 0 && (
        <div>
          <p className="font-fantasy text-xs tracking-widest mb-2" style={{ color: '#c3cbd6' }}>
            PERKS
          </p>
          <ul className="space-y-1.5">
            {visible?.map((b, i) => (
              <li key={i} className="flex items-start gap-2">
                <span className="text-xs mt-0.5 shrink-0" style={{ color: item.badge }}>✦</span>
                <span className="text-xs leading-snug" style={{ color: '#e6edf3' }}>{b}</span>
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

// ── Main card shell ───────────────────────────────────────────────────────────
export default function ProductCard({ item, onAddToCart, onBuyNow }) {
  const [inCart, setInCart] = useState(false);
  // Ranks are never bought directly — they unlock automatically once a
  // player's lifetime Total Donated crosses the threshold. No price/CTA.
  const isRank = item.category === 'ranks';
  const featured = Boolean(item.badge_label);

  const handleAddToCart = () => {
    onAddToCart(item);
    setInCart(true);
    // Reset the "added" flash after 1.5 s
    setTimeout(() => setInCart(false), 1500);
  };

  return (
    <article className={`zs-card ${featured ? 'zs-card--featured' : ''}`} style={{ '--acc': item.badge ?? '#d4af37' }}>
      {item.badge_label && (
        <span className={`zs-ribbon ${item.badge_label === 'BEST VALUE' ? 'zs-ribbon--value' : ''}`}>{item.badge_label}</span>
      )}

      <header className="zs-head">
        <h3 className="zs-name font-fantasy">{item.name.toUpperCase()}</h3>
        {isRank ? (
          <div className="zs-price">${item.price}<small>TOTAL DONATED</small></div>
        ) : (
          <div className="zs-price">${item.price}<small>USD</small></div>
        )}
      </header>

      {isRank ? <RankContent item={item} /> : <CoinsContent item={item} />}

      {isRank ? (
        <div className="zs-actions">
          <div className="zs-btn zs-btn--cart" style={{ cursor: 'default', letterSpacing: '0.08em' }}>
            🔒 Unlocks automatically — no purchase needed
          </div>
        </div>
      ) : (
        <div className="zs-actions">
          <button type="button" className="zs-btn zs-btn--buy" onClick={() => onBuyNow(item)}>
            Buy Now
          </button>
          <button
            type="button"
            className={`zs-btn zs-btn--cart ${inCart ? 'zs-btn--added' : ''}`}
            onClick={handleAddToCart}
          >
            {inCart ? <>✓ Added</> : <>🛒 Add to Cart</>}
          </button>
        </div>
      )}
    </article>
  );
}
