import { useApp } from '../../context/AppContext.jsx';
import AlertBox from '../ui/AlertBox.jsx';
import storeItems from '../../data/storeItems.js';

/**
 * Featured Store Widget — Homepage sidebar panel.
 *
 * Displays two hand-picked Donator Points items — the only category that's
 * directly purchasable (ranks unlock automatically via Total Donated and
 * have no buy button, so they're never featured here):
 *   • dp_3000  — most popular top-up ($20, POPULAR)
 *   • dp_8000  — high-tier top-up    ($50)
 *
 * Both items have their slug in store_catalog.py so checkout works out-of-the-box.
 * To change the featured items, edit FEATURED_SLUGS below — keep them 'dp' items.
 */
const FEATURED_SLUGS = ['dp_3000', 'dp_8000'];
const featured = FEATURED_SLUGS
  .map(slug => storeItems.find(i => i.slug === slug))
  .filter(Boolean);

export default function FeaturedStoreWidget() {
  const { setCurrentView, handleCheckout, storeMessage } = useApp();

  return (
    <div className="stone-panel">
      <div className="panel-header flex items-center justify-between">
        <span className="font-fantasy text-xs tracking-widest" style={{ color: '#d4af37' }}>
          💎&nbsp; FEATURED STORE
        </span>
        <button
          onClick={() => setCurrentView('store')}
          className="font-fantasy text-xs tracking-widest transition-colors"
          style={{ color: '#444' }}
          onMouseOver={e => (e.currentTarget.style.color = '#d4af37')}
          onMouseOut={e  => (e.currentTarget.style.color = '#444')}
        >
          SHOP ALL →
        </button>
      </div>

      <div className="p-4 flex flex-col gap-3">
        <AlertBox status={storeMessage} />

        {featured.map(item => (
          <div
            key={item.slug}
            className="p-4 relative overflow-hidden"
            style={{
              background: 'rgba(0,0,0,0.4)',
              border: `1px solid ${item.badge}22`,
              borderTop: `2px solid ${item.badge}88`,
              borderRadius: 2,
            }}
          >
            {/* Badge label (BEST VALUE / RARE / etc.) */}
            {item.badge_label && (
              <span
                className="absolute top-2 right-2 font-fantasy text-xs tracking-widest px-2 py-0.5 rounded-full"
                style={{ background: item.badge, color: '#0a0a14', fontSize: '9px', fontWeight: 700 }}
              >
                {item.badge_label}
              </span>
            )}

            {/* Name + Price row */}
            <div className="flex justify-between items-start mb-1 pr-16">
              <div className="flex items-center gap-2">
                <span className="text-xl leading-none">{item.iconFallback}</span>
                <span className="font-fantasy text-sm text-white">{item.name}</span>
              </div>
              <span
                className="font-fantasy font-bold text-base shrink-0"
                style={{ color: item.badge, textShadow: `0 0 8px ${item.badge}55` }}
              >
                ${item.price}
              </span>
            </div>

            {/* Description */}
            <p
              className="text-xs leading-relaxed mb-3 font-sans mt-2"
              style={{ color: '#6a6058' }}
            >
              {item.description}
            </p>

            {/* CTA */}
            <div className="flex justify-end">
              <button
                onClick={() => handleCheckout(item)}
                className="font-fantasy text-xs tracking-widest px-4 py-1.5 transition-all duration-200"
                style={{
                  background: `${item.badge}22`,
                  border: `1px solid ${item.badge}66`,
                  color: item.badge,
                  borderRadius: 2,
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
                PURCHASE
              </button>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
