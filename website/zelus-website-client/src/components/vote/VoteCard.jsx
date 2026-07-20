import { useState, useEffect } from 'react';

const formatTime = (secs) => {
  const h = Math.floor(secs / 3600).toString().padStart(2, '0');
  const m = Math.floor((secs % 3600) / 60).toString().padStart(2, '0');
  const s = (secs % 60).toString().padStart(2, '0');
  return `${h}:${m}:${s}`;
};

/**
 * A single voting site card. Manages its own countdown timer.
 *
 * Props:
 *   site         - from VOTE_SITES data (id, name, tagline, icon, rewards)
 *   initialState - 'idle' | 'pending' | 'cooldown' | 'loading'
 *   initialSecondsLeft - number (used when state is pending or cooldown)
 *   onVoteClick(siteId) - called when the VOTE button is pressed
 */
export default function VoteCard({ site, initialState = 'idle', initialSecondsLeft = 0, onVoteClick }) {
  const [cardState,  setCardState]  = useState(initialState);
  const [timeLeft,   setTimeLeft]   = useState(initialSecondsLeft);

  // Sync if parent updates state (e.g. after status fetch)
  useEffect(() => { setCardState(initialState);  }, [initialState]);
  useEffect(() => { setTimeLeft(initialSecondsLeft); }, [initialSecondsLeft]);

  // Countdown tick
  useEffect(() => {
    if ((cardState !== 'cooldown' && cardState !== 'pending') || timeLeft <= 0) return;

    const timer = setInterval(() => {
      setTimeLeft(prev => {
        if (prev <= 1) {
          clearInterval(timer);
          setCardState('idle'); // cooldown expired → allow voting again
          return 0;
        }
        return prev - 1;
      });
    }, 1000);

    return () => clearInterval(timer);
  }, [cardState]);

  const handleClick = () => {
    if (cardState !== 'idle') return;
    setCardState('loading');
    onVoteClick(site.id);
  };

  /* ── Derived visuals ─────────────────────────────────────── */
  const isPending  = cardState === 'pending';
  const isCooldown = cardState === 'cooldown';
  const isLoading  = cardState === 'loading';
  const isIdle     = cardState === 'idle';

  return (
    <div
      className="stone-panel flex flex-col transition-transform duration-300"
      style={{
        borderRadius: 2,
        borderTopColor: isPending ? '#22c55e' : isCooldown ? '#ef4444' : '#d4af37',
        transform: isIdle ? undefined : 'none',
      }}
    >
      {/* ── Card header ── */}
      <div
        className="panel-header flex items-center gap-3 py-5"
        style={{ background: isPending ? 'rgba(34,197,94,0.06)' : isCooldown ? 'rgba(239,68,68,0.06)' : undefined }}
      >
        <span className="text-2xl">{site.icon}</span>
        <div>
          <p className="font-fantasy text-base font-bold text-white tracking-wide">{site.name}</p>
          <p className="font-sans text-xs mt-0.5" style={{ color: '#9a8f80' }}>{site.tagline}</p>
        </div>
      </div>

      {/* ── Rewards list ── */}
      <div className="px-5 py-4 flex-grow">
        <p className="font-fantasy text-xs tracking-widest mb-3" style={{ color: '#d4af37' }}>
          REWARDS PER VOTE
        </p>
        <ul className="space-y-2">
          {site.rewards.map(reward => (
            <li key={reward} className="flex items-center gap-2">
              <span className="text-xs" style={{ color: '#d4af37' }}>◆</span>
              <span className="font-sans text-xs" style={{ color: '#b0a590' }}>{reward}</span>
            </li>
          ))}
        </ul>
      </div>

      {/* ── State panel ── */}
      <div className="px-5 pb-6 pt-2 flex flex-col gap-3">

        {/* IDLE — Vote button */}
        {isIdle && (
          <button
            onClick={handleClick}
            className="btn-download w-full py-3.5 font-fantasy text-sm tracking-widest uppercase"
          >
            ⚔&nbsp; Vote Now
          </button>
        )}

        {/* LOADING — Submitting spinner */}
        {isLoading && (
          <div
            className="w-full py-3.5 flex items-center justify-center gap-3 font-fantasy text-xs tracking-widest"
            style={{ background: 'rgba(212,175,55,0.06)', border: '1px solid rgba(212,175,55,0.2)', borderRadius: 3 }}
          >
            <span
              className="w-4 h-4 rounded-full border-2 border-zelus-border border-t-zelus-gold animate-spin inline-block"
            />
            <span style={{ color: '#d4af37' }}>SUBMITTING...</span>
          </div>
        )}

        {/* PENDING — Ready to claim */}
        {isPending && (
          <>
            <div
              className="w-full py-3.5 flex items-center justify-center gap-2 font-fantasy text-xs tracking-widest"
              style={{ background: 'rgba(34,197,94,0.08)', border: '1px solid rgba(34,197,94,0.3)', borderRadius: 3 }}
            >
              <span className="text-green-400 text-base">✓</span>
              <span className="text-green-400">READY TO CLAIM IN-GAME</span>
            </div>
            <div className="text-center">
              <p className="font-fantasy text-xs tracking-widest" style={{ color: '#9a8f80' }}>
                Type&nbsp;
                <span
                  className="font-mono px-1.5 py-0.5"
                  style={{ background: 'rgba(255,255,255,0.05)', border: '1px solid #2a2420', borderRadius: 2, color: '#d4af37' }}
                >
                  ::claimvote
                </span>
                &nbsp;in-game
              </p>
              {timeLeft > 0 && (
                <p className="font-fantasy text-xs mt-2" style={{ color: '#9a8f80' }}>
                  Next vote in&nbsp;
                  <span className="font-mono" style={{ color: '#c8bfb0' }}>{formatTime(timeLeft)}</span>
                </p>
              )}
            </div>
          </>
        )}

        {/* COOLDOWN — Already voted */}
        {isCooldown && (
          <>
            <div
              className="w-full py-3.5 flex items-center justify-center gap-2 font-fantasy text-xs tracking-widest"
              style={{ background: 'rgba(239,68,68,0.06)', border: '1px solid rgba(239,68,68,0.2)', borderRadius: 3, opacity: 0.7, cursor: 'not-allowed' }}
            >
              <span className="text-red-500 text-base">✓</span>
              <span className="text-red-400">VOTED — ON COOLDOWN</span>
            </div>
            {timeLeft > 0 && (
              <div className="text-center">
                <p className="font-fantasy text-xs" style={{ color: '#9a8f80' }}>Available again in</p>
                <p
                  className="font-mono text-lg font-bold mt-1"
                  style={{ color: '#ef4444', textShadow: '0 0 8px rgba(239,68,68,0.3)' }}
                >
                  {formatTime(timeLeft)}
                </p>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
}
