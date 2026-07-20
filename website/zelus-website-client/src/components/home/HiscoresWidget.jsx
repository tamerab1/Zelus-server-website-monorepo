import LoadingSpinner, { ServerDownBanner } from '../ui/LoadingSpinner.jsx';

const MEDAL = ['🥇', '🥈', '🥉'];

function RankBadge({ rank }) {
  if (rank < 3) return <span style={{ fontSize: '15px', lineHeight: 1 }}>{MEDAL[rank]}</span>;
  return (
    <span
      className="font-fantasy text-xs flex items-center justify-center"
      style={{
        width: '22px',
        height: '22px',
        background: 'linear-gradient(180deg, #1e1e2c, #141420)',
        border: '1px solid rgba(212,175,55,0.2)',
        color: '#666',
        borderRadius: '2px',
        fontSize: '10px',
      }}
    >
      {rank + 1}
    </span>
  );
}

export default function HiscoresWidget({ hiscores, loading, error }) {
  return (
    <div style={{
      background: 'linear-gradient(160deg, #18182a 0%, #111120 100%)',
      border: '1px solid rgba(212,175,55,0.15)',
      borderTop: '2px solid #d4af37',
      boxShadow: '0 8px 32px rgba(0,0,0,0.7), inset 0 1px 0 rgba(255,255,255,0.04)',
    }}>

      {/* Header */}
      <div style={{
        padding: '10px 16px',
        background: 'linear-gradient(90deg, rgba(212,175,55,0.1) 0%, transparent 100%)',
        borderBottom: '1px solid rgba(212,175,55,0.18)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
      }}>
        <div className="flex items-center gap-2">
          <span style={{ color: '#d4af37', fontSize: '13px' }}>⚔</span>
          <span className="font-fantasy text-xs tracking-widest" style={{ color: '#d4af37' }}>
            TOP PLAYERS
          </span>
        </div>
        <span className="font-fantasy text-xs" style={{ color: '#444', letterSpacing: '0.1em' }}>
          HISCORES
        </span>
      </div>

      {/* Column headers */}
      {!loading && !error && hiscores.length > 0 && (
        <div className="flex items-center px-4 py-1" style={{ borderBottom: '1px solid rgba(212,175,55,0.08)' }}>
          <span style={{ width: '28px' }} />
          <span className="font-fantasy text-xs flex-1" style={{ color: '#3a3430', letterSpacing: '0.15em', fontSize: '9px' }}>PLAYER</span>
          <span className="font-fantasy text-xs" style={{ color: '#3a3430', letterSpacing: '0.15em', fontSize: '9px' }}>TOTAL LVL</span>
          <span className="font-fantasy text-xs ml-4" style={{ color: '#3a3430', letterSpacing: '0.15em', fontSize: '9px' }}>KILLS</span>
        </div>
      )}

      <div style={{ padding: '4px 0' }}>
        {loading && (
          <div className="py-8"><LoadingSpinner text="Scanning the realms..." /></div>
        )}

        {!loading && error && <ServerDownBanner message={error} />}

        {!loading && !error && hiscores.length === 0 && (
          <p className="text-center py-10 font-fantasy text-xs tracking-widest" style={{ color: '#333' }}>
            No champions yet.
          </p>
        )}

        {!loading && !error && hiscores.slice(0, 10).map((p, i) => (
          <div
            key={i}
            className="flex items-center gap-3 px-4 transition-all duration-150"
            style={{
              padding: '7px 16px',
              borderBottom: i < 9 ? '1px solid rgba(20,20,32,0.8)' : 'none',
              cursor: 'default',
            }}
            onMouseOver={e  => e.currentTarget.style.background = 'rgba(212,175,55,0.04)'}
            onMouseOut={e   => e.currentTarget.style.background = 'transparent'}
          >
            {/* Rank */}
            <div style={{ width: '28px', display: 'flex', justifyContent: 'center' }}>
              <RankBadge rank={i} />
            </div>

            {/* Username */}
            <div className="flex-1 min-w-0">
              <span
                className="font-fantasy text-xs truncate block"
                style={{
                  color: i === 0 ? '#ffd700' : i === 1 ? '#c8c8c8' : i === 2 ? '#cd7f32' : '#a89880',
                  textShadow: i < 3 ? '0 0 8px rgba(212,175,55,0.2)' : 'none',
                  letterSpacing: '0.05em',
                }}
              >
                {p.username}
              </span>
            </div>

            {/* Total level */}
            <div className="text-right" style={{ minWidth: '44px' }}>
              <span className="font-mono text-xs" style={{ color: '#d4af37' }}>{p.total_level}</span>
            </div>

            {/* Kills */}
            <div className="text-right" style={{ minWidth: '36px', marginLeft: '14px' }}>
              <span className="font-mono text-xs" style={{ color: '#c0392b' }}>{p.kills ?? 0}</span>
            </div>
          </div>
        ))}
      </div>

      {/* Footer */}
      <div style={{
        padding: '8px 16px',
        borderTop: '1px solid rgba(212,175,55,0.1)',
        background: 'rgba(0,0,0,0.2)',
        display: 'flex',
        justifyContent: 'center',
      }}>
        <span className="font-fantasy text-xs" style={{ color: '#2e2820', letterSpacing: '0.15em', fontSize: '9px' }}>
          ◆ &nbsp; UPDATED LIVE &nbsp; ◆
        </span>
      </div>
    </div>
  );
}
