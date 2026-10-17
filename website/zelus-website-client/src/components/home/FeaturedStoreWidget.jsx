import { useApp } from '../../context/AppContext.jsx';
import AlertBox from '../ui/AlertBox.jsx';
import storeItems from '../../data/storeItems.js';
import './HomeSections.css';

/**
 * Featured Store Widget — Homepage sidebar panel.
 *
 * Two hand-picked Donator Points bonds (the only directly purchasable category; ranks unlock from Total Donated):
 *   • dp_2180 — $25, MOST POPULAR
 *   • dp_9450 — $100, BEST VALUE
 * Labels come from storeItems.js so the widget and the main store always agree. Keep FEATURED_SLUGS 'dp' items.
 * Layout (2026-10-17): the badge has its own row above the title, so it can never sit on the price.
 */
const FEATURED_SLUGS = ['dp_2180', 'dp_9450'];
const featured = FEATURED_SLUGS
  .map(slug => storeItems.find(i => i.slug === slug))
  .filter(Boolean);

/** "+25% bonus" -> "+25% Bonus" */
const bonusText = (bonus) => bonus.replace(/\s*bonus\s*$/i, '').trim() + ' Bonus';

export default function FeaturedStoreWidget() {
  const { setCurrentView, handleCheckout, storeMessage } = useApp();

  return (
    <div className="stone-panel">
      <div className="panel-header flex items-center justify-between">
        <span className="font-fantasy text-xs tracking-widest" style={{ color: '#d4af37' }}>
          💎&nbsp; FEATURED STORE
        </span>
        <button type="button" onClick={() => setCurrentView('store')} className="zf-all font-fantasy">
          SHOP ALL →
        </button>
      </div>

      <div className="p-4 flex flex-col gap-3">
        <AlertBox status={storeMessage} />

        {featured.map(item => (
          <div key={item.slug} className="zf-card" style={{ '--acc': item.badge }}>
            {item.badge_label && (
              <span className={`zf-badge ${item.badge_label === 'BEST VALUE' ? 'zf-badge--value' : ''}`}>
                {item.badge_label}
              </span>
            )}

            <div className="zf-top">
              <img src={item.icon_path} alt="" className="zf-icon" loading="lazy" />
              <div className="zf-titles">
                <span className="zf-name font-fantasy">{item.name}</span>
                <span className="zf-points">
                  {item.quantity?.toLocaleString()} Donator Points
                  {item.bonus && <span className="zf-bonus"> ({bonusText(item.bonus)})</span>}
                </span>
              </div>
            </div>

            <div className="zf-bottom">
              <span className="zf-price">${item.price}<small>USD</small></span>
              <button type="button" className="zf-buy" onClick={() => handleCheckout(item)}>
                Buy Now
              </button>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
