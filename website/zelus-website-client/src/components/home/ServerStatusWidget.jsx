import { useApp } from '../../context/AppContext.jsx';
import { useState, useEffect } from 'react';

const API = import.meta.env.VITE_API_URL ?? 'http://localhost:8000';

/**
 * Server Status Widget — queries /players/online every 30 s.
 *
 * The backend now returns:
 *   { online: number, status: "online" | "offline" }
 *
 * Status is "online" when the game DB (online_characters table) or character
 * file fallback is reachable.  "offline" means neither source could be reached.
 * A null onlineCount (first load not yet resolved) shows the CHECKING state.
 */
export default function ServerStatusWidget({ hiscores, loading }) {
  const { setCurrentView } = useApp();

  // null = not yet fetched; number = latest known count
  const [onlineCount,   setOnlineCount]   = useState(null);
  // 'unknown' | 'online' | 'offline'
  const [serverStatus,  setServerStatus]  = useState('unknown');

  useEffect(() => {
    let cancelled = false;

    const fetchStatus = async () => {
      try {
        const res = await fetch(`${API}/players/online`);
        if (!res.ok) throw new Error('bad response');
        const data = await res.json();
        if (!cancelled) {
          setOnlineCount(data.online ?? 0);
          setServerStatus(data.status ?? 'online');
        }
      } catch {
        // Network error or 5xx — mark server unreachable
        if (!cancelled) setServerStatus('offline');
      }
    };

    fetchStatus();
    const interval = setInterval(fetchStatus, 30_000);
    return () => { cancelled = true; clearInterval(interval); };
  }, []);

  /* Derived display values */
  const isOnline    = serverStatus === 'online';
  const isOffline   = serverStatus === 'offline';
  const isUnknown   = serverStatus === 'unknown';

  const dotColor    = isOnline  ? '#22c55e' : isOffline ? '#ef4444' : '#f59e0b';
  const dotGlow     = isOnline  ? '0 0 8px rgba(34,197,94,0.8)'
                    : isOffline ? '0 0 8px rgba(239,68,68,0.6)'
                    : '0 0 8px rgba(245,158,11,0.6)';
  const statusLabel = isOnline  ? 'ONLINE' : isOffline ? 'OFFLINE' : 'CHECKING';
  const statusClass = isOnline  ? 'text-green-400' : isOffline ? 'text-red-400' : 'text-yellow-400';

  return (
    <div className="stone-panel">
      <div className="panel-header">
        <span className="font-fantasy text-xs tracking-widest" style={{ color: '#d4af37' }}>
          🌐&nbsp; SERVER STATUS
        </span>
      </div>

      <div className="p-5 flex flex-col gap-4">

        {/* Status indicator */}
        <div
          className="flex items-center justify-between p-3"
          style={{ background: 'rgba(0,0,0,0.4)', border: '1px solid #1e1a14', borderRadius: 2 }}
        >
          <span className="font-fantasy text-xs tracking-widest" style={{ color: '#888' }}>
            Status
          </span>
          <div className="flex items-center gap-2">
            <span
              className="w-2 h-2 rounded-full inline-block"
              style={{
                background: dotColor,
                boxShadow: dotGlow,
                animation: isOnline ? 'pulse 2s cubic-bezier(0.4,0,0.6,1) infinite' : 'none',
              }}
            />
            <span className={`font-fantasy text-xs tracking-widest ${statusClass}`}>
              {statusLabel}
            </span>
          </div>
        </div>

        {/* Stats grid */}
        <div className="grid grid-cols-2 gap-3">
          {[
            {
              label: 'Online Now',
              value: onlineCount === null
                ? (isOffline ? '—' : '…')
                : onlineCount,
            },
            {
              label: 'Top Players',
              value: loading ? '—' : hiscores.length,
            },
          ].map(({ label, value }) => (
            <div
              key={label}
              className="text-center p-4"
              style={{ background: 'rgba(0,0,0,0.4)', border: '1px solid #1e1a14', borderRadius: 2 }}
            >
              <div className="font-fantasy text-xl font-bold" style={{ color: '#d4af37' }}>
                {value}
              </div>
              <div className="font-fantasy text-xs tracking-widest mt-1" style={{ color: '#555' }}>
                {label}
              </div>
            </div>
          ))}
        </div>

        {/* Offline notice */}
        {isOffline && (
          <p
            className="font-fantasy text-xs tracking-widest text-center py-2"
            style={{ color: '#ef4444', background: 'rgba(239,68,68,0.06)', border: '1px solid rgba(239,68,68,0.2)', borderRadius: 2 }}
          >
            Game server unreachable
          </p>
        )}

        {/* Action buttons */}
        <button className="btn-download w-full py-3 font-fantasy text-xs tracking-widest uppercase">
          ⬇&nbsp; Download Client
        </button>
        <button
          onClick={() => setCurrentView('register')}
          className="w-full py-3 font-fantasy text-xs tracking-widest uppercase transition-colors"
          style={{ background: 'rgba(255,255,255,0.03)', border: '1px solid #1e1a14', borderRadius: 3, color: '#555' }}
          onMouseOver={e => (e.currentTarget.style.color = '#999')}
          onMouseOut={e  => (e.currentTarget.style.color = '#555')}
        >
          Create Account
        </button>
      </div>
    </div>
  );
}
