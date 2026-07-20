import { useState, useEffect, useRef } from 'react';

const API = import.meta.env.VITE_API_URL ?? 'http://localhost:8000';
const POLL_MS = 7_000;

/* ── Event type config ──────────────────────────────── */
const EVENT_STYLE = {
  pvp_kill:      { icon: '⚔',  color: '#c0392b', label: 'PVP KILL'  },
  killstreak:    { icon: '🔥', color: '#e67e22', label: 'STREAK'    },
  rare_drop:     { icon: '💎', color: '#9b59b6', label: 'RARE DROP' },
  level_up:      { icon: '⬆',  color: '#27ae60', label: 'LEVEL UP'  },
  tournament_win:{ icon: '🏆', color: '#f0d060', label: 'CHAMPION'  },
  bounty_claim:  { icon: '💀', color: '#e74c3c', label: 'BOUNTY'    },
};

function timeAgo(isoStr) {
  const diff = Math.floor((Date.now() - new Date(isoStr).getTime()) / 1000);
  if (diff < 60)  return `${diff}s ago`;
  if (diff < 3600) return `${Math.floor(diff / 60)}m ago`;
  return `${Math.floor(diff / 3600)}h ago`;
}

function FeedRow({ event, fresh }) {
  const cfg = EVENT_STYLE[event.type] ?? EVENT_STYLE.pvp_kill;
  return (
    <div
      className="flex items-start gap-3 px-4 py-2 transition-all duration-300"
      style={{
        borderBottom: '1px solid rgba(20,20,32,0.9)',
        background: fresh ? 'rgba(212,175,55,0.05)' : 'transparent',
        animation: fresh ? 'feedSlideIn 0.35s ease-out' : 'none',
      }}
    >
      {/* Icon badge */}
      <div
        className="shrink-0 flex items-center justify-center"
        style={{
          width: '28px',
          height: '28px',
          background: `linear-gradient(180deg, rgba(${hexToRgb(cfg.color)},0.15) 0%, rgba(${hexToRgb(cfg.color)},0.05) 100%)`,
          border: `1px solid rgba(${hexToRgb(cfg.color)},0.3)`,
          borderRadius: '2px',
          fontSize: '13px',
          marginTop: '1px',
        }}
      >
        {cfg.icon}
      </div>

      {/* Content */}
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2 mb-0.5">
          <span className="font-fantasy text-xs" style={{ color: cfg.color, fontSize: '9px', letterSpacing: '0.15em' }}>
            {cfg.label}
          </span>
          <span style={{ color: '#2a2a3a', fontSize: '9px' }}>•</span>
          <span className="font-mono text-xs" style={{ color: '#3a3846', fontSize: '9px' }}>
            {timeAgo(event.timestamp)}
          </span>
        </div>
        <p className="text-xs leading-snug" style={{ color: '#a89880' }}>
          {event.message}
        </p>
      </div>
    </div>
  );
}

export default function LiveFeed() {
  const [events, setEvents] = useState([]);
  const [freshIds, setFreshIds] = useState(new Set());
  const [connected, setConnected] = useState(false);
  const knownIds = useRef(new Set());

  useEffect(() => {
    let cancelled = false;

    const poll = async () => {
      try {
        const res = await fetch(`${API}/livefeed?limit=12`);
        if (!res.ok) return;
        const data = await res.json();

        if (cancelled) return;
        setConnected(true);

        const newIds = new Set();
        const incoming = (data.events ?? []);
        incoming.forEach(e => {
          if (!knownIds.current.has(e.id)) newIds.add(e.id);
        });
        incoming.forEach(e => knownIds.current.add(e.id));

        if (incoming.length > 0) {
          setEvents(incoming);
          if (newIds.size > 0) {
            setFreshIds(newIds);
            setTimeout(() => setFreshIds(new Set()), 2000);
          }
        }
      } catch {
        if (!cancelled) setConnected(false);
      }
    };

    poll();
    const id = setInterval(poll, POLL_MS);
    return () => { cancelled = true; clearInterval(id); };
  }, []);

  return (
    <div style={{
      background: 'linear-gradient(160deg, #18182a 0%, #111120 100%)',
      border: '1px solid rgba(212,175,55,0.15)',
      borderTop: '2px solid rgba(192,57,43,0.8)',
      boxShadow: '0 8px 32px rgba(0,0,0,0.7), inset 0 1px 0 rgba(255,255,255,0.04)',
    }}>

      {/* Header */}
      <div style={{
        padding: '10px 16px',
        background: 'linear-gradient(90deg, rgba(192,57,43,0.1) 0%, transparent 100%)',
        borderBottom: '1px solid rgba(212,175,55,0.12)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
      }}>
        <div className="flex items-center gap-2">
          <span style={{ fontSize: '13px' }}>📡</span>
          <span className="font-fantasy text-xs tracking-widest" style={{ color: '#d4af37' }}>
            LIVE GAME FEED
          </span>
        </div>
        {/* Live dot */}
        <div className="flex items-center gap-1.5">
          <div style={{
            width: '6px',
            height: '6px',
            borderRadius: '50%',
            background: connected ? '#27ae60' : '#555',
            boxShadow: connected ? '0 0 6px rgba(39,174,96,0.8)' : 'none',
            animation: connected ? 'livePulse 1.8s ease-in-out infinite' : 'none',
          }} />
          <span className="font-fantasy text-xs" style={{ color: connected ? '#27ae60' : '#444', fontSize: '9px', letterSpacing: '0.12em' }}>
            {connected ? 'LIVE' : 'OFFLINE'}
          </span>
        </div>
      </div>

      {/* Feed rows */}
      <div style={{ maxHeight: '320px', overflowY: 'auto' }}>
        {events.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-10 gap-2">
            <span style={{ fontSize: '22px', opacity: 0.3 }}>⚔</span>
            <span className="font-fantasy text-xs tracking-widest" style={{ color: '#2a2a38', fontSize: '10px' }}>
              WAITING FOR EVENTS...
            </span>
          </div>
        ) : (
          events.map(event => (
            <FeedRow key={event.id} event={event} fresh={freshIds.has(event.id)} />
          ))
        )}
      </div>

      {/* Footer */}
      <div style={{
        padding: '7px 16px',
        borderTop: '1px solid rgba(212,175,55,0.08)',
        background: 'rgba(0,0,0,0.2)',
        display: 'flex',
        justifyContent: 'center',
      }}>
        <span className="font-fantasy" style={{ color: '#242430', fontSize: '9px', letterSpacing: '0.15em' }}>
          ◆ &nbsp; POWERED BY ZELUS SERVER &nbsp; ◆
        </span>
      </div>
    </div>
  );
}

/* helper */
function hexToRgb(hex) {
  const r = parseInt(hex.slice(1,3),16);
  const g = parseInt(hex.slice(3,5),16);
  const b = parseInt(hex.slice(5,7),16);
  return `${r},${g},${b}`;
}
