/**
 * CategoryNav — Left sidebar (desktop) / horizontal tab strip (mobile).
 *
 * Props:
 *   active   — string: current category id
 *   onChange — fn(categoryId)
 *   counts   — { [categoryId]: number }
 */
import { CATEGORIES } from '../../data/storeItems.js';

export default function CategoryNav({ active, onChange, counts = {} }) {
  return (
    <>
      {/* ── Desktop: sticky vertical sidebar ─────────────────────────── */}
      <aside className="hidden lg:block w-52 shrink-0">
        <div className="stone-panel sticky top-24" style={{ borderRadius: 3 }}>

          {/* Header */}
          <div className="panel-header">
            <span className="font-fantasy text-xs tracking-widest" style={{ color: '#d4af37' }}>
              CATEGORIES
            </span>
          </div>

          {/* Nav items */}
          <nav className="py-2">
            {CATEGORIES.map(cat => {
              const isActive = active === cat.id;
              return (
                <button
                  key={cat.id}
                  onClick={() => onChange(cat.id)}
                  className="w-full text-left px-4 py-3 flex items-center gap-3 transition-all duration-150"
                  style={{
                    background: isActive
                      ? 'linear-gradient(90deg, rgba(212,175,55,0.14) 0%, transparent 100%)'
                      : 'transparent',
                    borderLeft: `2px solid ${isActive ? '#d4af37' : 'transparent'}`,
                  }}
                  onMouseOver={e => {
                    if (!isActive) e.currentTarget.style.background = 'rgba(255,255,255,0.04)';
                  }}
                  onMouseOut={e => {
                    if (!isActive) e.currentTarget.style.background = 'transparent';
                  }}
                  aria-current={isActive ? 'page' : undefined}
                >
                  {/* Category icon */}
                  <span className="text-base w-5 text-center leading-none select-none">
                    {cat.icon}
                  </span>

                  {/* Label */}
                  <span
                    className="font-fantasy text-xs tracking-wider flex-1"
                    style={{ color: isActive ? '#d4af37' : '#9a8f7a' }}
                  >
                    {cat.label}
                  </span>

                  {/* Item count badge */}
                  {counts[cat.id] > 0 && (
                    <span
                      className="text-xs font-mono px-1.5 py-0.5 rounded"
                      style={{
                        background: isActive
                          ? 'rgba(212,175,55,0.2)'
                          : 'rgba(255,255,255,0.06)',
                        color: isActive ? '#d4af37' : '#6a6058',
                      }}
                    >
                      {counts[cat.id]}
                    </span>
                  )}
                </button>
              );
            })}
          </nav>

          {/* Footer note */}
          <div className="px-4 py-3" style={{ borderTop: '1px solid rgba(255,255,255,0.05)' }}>
            <p className="text-xs leading-relaxed" style={{ color: '#5a5248' }}>
              All purchases are permanent &amp; instant.
            </p>
          </div>
        </div>
      </aside>

      {/* ── Mobile: scrollable horizontal tabs ───────────────────────── */}
      <div
        className="lg:hidden w-full mb-6 overflow-x-auto"
        style={{ scrollbarWidth: 'none', WebkitOverflowScrolling: 'touch' }}
      >
        <div className="flex gap-2 min-w-max px-1 pb-1">
          {CATEGORIES.map(cat => {
            const isActive = active === cat.id;
            return (
              <button
                key={cat.id}
                onClick={() => onChange(cat.id)}
                className="flex items-center gap-2 px-4 py-2.5 rounded-sm font-fantasy text-xs tracking-wider whitespace-nowrap transition-all duration-150"
                style={{
                  background: isActive
                    ? 'rgba(212,175,55,0.16)'
                    : 'linear-gradient(160deg, #1a1a26 0%, #14141e 100%)',
                  border: `1px solid ${isActive ? 'rgba(212,175,55,0.55)' : '#2e2820'}`,
                  color: isActive ? '#d4af37' : '#7a7060',
                  boxShadow: isActive
                    ? '0 0 12px rgba(212,175,55,0.12)'
                    : '0 2px 8px rgba(0,0,0,0.4)',
                }}
              >
                <span className="leading-none">{cat.icon}</span>
                <span>{cat.label}</span>
                {counts[cat.id] > 0 && (
                  <span
                    className="font-mono rounded px-1"
                    style={{
                      background: isActive
                        ? 'rgba(212,175,55,0.25)'
                        : 'rgba(255,255,255,0.07)',
                      color: isActive ? '#d4af37' : '#5a5248',
                      fontSize: '10px',
                    }}
                  >
                    {counts[cat.id]}
                  </span>
                )}
              </button>
            );
          })}
        </div>
      </div>
    </>
  );
}
