/**
 * DonatorBenefits — the donator rank benefits viewer for the store.
 *
 *   <DonatorBenefits onGetPoints={fn} />        inline section (the Ranks category)
 *   <DonatorBenefitsModal open onClose onGetPoints />   the same viewer in a modal
 *
 * Data: ../../data/donatorBenefits.js (values read from the live server source, one source note per perk).
 * Desktop shows the full 8-rank matrix with the selected rank's column highlighted; phones get swipeable rank tabs
 * and collapsible category cards for the selected rank instead of an overflowing table.
 */
import { useEffect, useMemo, useState } from 'react';
import { useApp } from '../../context/AppContext.jsx';
import { DONATOR_RANKS, BENEFIT_CATEGORIES, rankIndexFor } from '../../data/donatorBenefits.js';
import './DonatorBenefits.css';

const fmtUsd = (n) => `$${n.toLocaleString('en-US')}`;

function Cell({ value }) {
  if (value == null) return <span className="zb-none">—</span>;
  if (value === '✓') return <span className="zb-check" aria-label="Included">✓</span>;
  return <>{value}</>;
}

export function DonatorBenefits({ onGetPoints, compact = false }) {
  const { currentUser } = useApp();
  const spent = Number(currentUser?.total_spent ?? currentUser?.totalSpent ?? 0) || 0;
  const yourRank = rankIndexFor(spent);
  const [selected, setSelected] = useState(yourRank >= 0 ? Math.min(yourRank + 1, DONATOR_RANKS.length - 1) : 0);
  const [category, setCategory] = useState('all');

  const rank = DONATOR_RANKS[selected];
  const groups = useMemo(
    () => (category === 'all' ? BENEFIT_CATEGORIES : BENEFIT_CATEGORIES.filter((c) => c.id === category)),
    [category],
  );
  const toGo = Math.max(0, rank.spend - spent);

  return (
    <section className="zb" style={{ '--zb-rank': rank.color }} aria-labelledby="zb-title">
      {!compact && (
        <header className="zb-head">
          <div>
            <div className="zb-kicker">Donator Ranks</div>
            <h2 className="zb-title" id="zb-title">Rank Benefits</h2>
            <p className="zb-sub">
              Ranks unlock automatically as your lifetime Total Donated grows, and every perk below comes straight
              from the live game server. Higher ranks always include the lower ranks' access perks.
            </p>
          </div>
          {onGetPoints && (
            <button type="button" className="zb-btn" onClick={onGetPoints}>Get Donator Points</button>
          )}
        </header>
      )}

      {/* rank selector */}
      <div className="zb-ranks" role="tablist" aria-label="Donator ranks">
        {DONATOR_RANKS.map((r, i) => (
          <button
            key={r.id}
            type="button"
            role="tab"
            aria-selected={i === selected}
            className="zb-rank"
            style={{ '--c': r.color }}
            onClick={() => setSelected(i)}
          >
            {i === yourRank && <span className="zb-you">YOU</span>}
            <img src={r.badge} alt="" loading="lazy" />
            <span className="zb-rank-name" style={{ color: r.color }}>{r.name}</span>
            <span className="zb-rank-spend">{fmtUsd(r.spend)}+</span>
          </button>
        ))}
      </div>

      {/* selected rank */}
      <div className="zb-glass zb-focus">
        <div className="zb-focus-id">
          <img src={rank.badge} alt="" />
          <div>
            <div className="zb-focus-name">{rank.full}</div>
            <div className="zb-focus-meta">
              Unlocks at <strong>{fmtUsd(rank.spend)}</strong> Total Donated
              {currentUser && (
                selected <= yourRank
                  ? <> · <strong style={{ color: '#4ade80' }}>Unlocked</strong></>
                  : <> · <strong>{fmtUsd(Math.ceil(toGo))}</strong> to go</>
              )}
            </div>
          </div>
        </div>
        {onGetPoints && (
          <button type="button" className="zb-btn" onClick={onGetPoints}>
            {currentUser && selected <= yourRank ? 'Top up Donator Points' : `Reach ${rank.name}`}
          </button>
        )}
      </div>

      {/* category filter */}
      <div className="zb-cats" role="group" aria-label="Benefit categories">
        <button type="button" className="zb-cat" style={{ '--a': '#e9e6ef' }} aria-pressed={category === 'all'}
          onClick={() => setCategory('all')}>All benefits</button>
        {BENEFIT_CATEGORIES.map((c) => (
          <button key={c.id} type="button" className="zb-cat" style={{ '--a': c.accent }}
            aria-pressed={category === c.id} onClick={() => setCategory(c.id)}>{c.label}</button>
        ))}
      </div>

      {/* desktop matrix */}
      <div className="zb-glass zb-matrix">
        <table className="zb-table">
          <thead>
            <tr>
              <th scope="col">Benefit</th>
              <th scope="col" className="zb-base">No rank</th>
              {DONATOR_RANKS.map((r, i) => (
                <th key={r.id} scope="col" style={{ '--c': r.color }} className={i === selected ? 'zb-col-on' : ''}>
                  <button type="button" onClick={() => setSelected(i)}
                    style={{ all: 'unset', cursor: 'pointer', color: r.color }}>{r.name}</button>
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {groups.map((g) => [
              <tr key={`${g.id}-h`} className="zb-group" style={{ '--a': g.accent }}>
                <td colSpan={DONATOR_RANKS.length + 2}>{g.label}</td>
              </tr>,
              ...g.perks.map((p, pi) => (
                <tr key={`${g.id}-${p.label}`} className={pi % 2 === 0 ? 'zb-row-a' : 'zb-row-b'}>
                  <td>
                    <span className="zb-perk-label">{p.label}</span>
                    {p.unit && <span className="zb-perk-src">{p.unit}</span>}
                  </td>
                  <td className="zb-base">{p.base}</td>
                  {p.values.map((v, i) => (
                    <td key={i} className={i === selected ? 'zb-col-on' : ''}><Cell value={v} /></td>
                  ))}
                </tr>
              )),
            ])}
          </tbody>
        </table>
      </div>

      {/* phones: the selected rank, grouped */}
      <div className="zb-cards">
        {groups.map((g, gi) => (
          <details key={g.id} className="zb-glass zb-card" style={{ '--a': g.accent }} open={gi === 0}>
            <summary>{g.label}</summary>
            <ul>
              {g.perks.map((p) => (
                <li key={p.label}>
                  <span>{p.label}</span>
                  <span><Cell value={p.values[selected]} /></span>
                </li>
              ))}
            </ul>
          </details>
        ))}
      </div>

      <div className="zb-foot">
        <span>Ranks are based on lifetime Total Donated, not the Donator Points you hold.</span>
        {onGetPoints && (
          <button type="button" className="zb-btn zb-btn--ghost" onClick={onGetPoints}>Browse point packages →</button>
        )}
      </div>
    </section>
  );
}

export function DonatorBenefitsModal({ open, onClose, onGetPoints }) {
  useEffect(() => {
    if (!open) return undefined;
    const onKey = (e) => { if (e.key === 'Escape') onClose(); };
    const prev = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    window.addEventListener('keydown', onKey);
    return () => {
      document.body.style.overflow = prev;
      window.removeEventListener('keydown', onKey);
    };
  }, [open, onClose]);

  if (!open) return null;
  return (
    <div className="zb-overlay" role="dialog" aria-modal="true" aria-label="Donator rank benefits"
      onMouseDown={(e) => { if (e.target === e.currentTarget) onClose(); }}>
      <div className="zb-glass zb-modal">
        <button type="button" className="zb-close" onClick={onClose} aria-label="Close">×</button>
        <DonatorBenefits onGetPoints={onGetPoints && (() => { onClose(); onGetPoints(); })} />
      </div>
    </div>
  );
}

export default DonatorBenefits;
