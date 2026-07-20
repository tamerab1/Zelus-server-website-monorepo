import { useState, useEffect } from 'react';
import { useApp } from '../../context/AppContext.jsx';
import { submitVote, fetchVoteStatus, checkGameUsername } from '../../services/voteService.js';
import { ServerUnreachableError } from '../../services/api.js';
import VOTE_SITES from '../../data/voteSites.js';
import VoteCard from './VoteCard.jsx';
import LoadingSpinner, { ServerDownBanner } from '../ui/LoadingSpinner.jsx';

/**
 * The Vote page.
 * - Fetches per-site statuses on mount (if logged in).
 * - handleVoteClick: opens the topsite URL in a new tab, then POSTs to /vote/submit.
 * - Per-card state is tracked in `siteStates` map.
 */
export default function VoteView() {
  const { currentUser, setCurrentView } = useApp();

  // { [siteId]: { state: 'idle'|'pending'|'cooldown'|'loading', secondsLeft: 0 } }
  const [siteStates,     setSiteStates]     = useState({});
  const [statusLoading,  setStatusLoading]  = useState(true);
  const [statusError,    setStatusError]    = useState(null);
  const [voteErrors,     setVoteErrors]     = useState({}); // { [siteId]: message }

  // Game username validation gate
  const [gameUsername,        setGameUsername]        = useState('');
  const [gameUsernameInput,   setGameUsernameInput]   = useState('');
  const [gameUsernameError,   setGameUsernameError]   = useState('');
  const [gameUsernameLoading, setGameUsernameLoading] = useState(false);

  /* ── Load vote statuses on mount ─────────────────────────── */
  useEffect(() => {
    if (!currentUser) { setStatusLoading(false); return; }

    fetchVoteStatus(currentUser.id)
      .then(data => {
        const map = {};
        data.forEach(entry => {
          map[entry.site_name] = {
            state:      entry.state,
            secondsLeft: entry.seconds_remaining ?? 0,
          };
        });
        setSiteStates(map);
      })
      .catch(err => setStatusError(err.message))
      .finally(() => setStatusLoading(false));
  }, [currentUser]);

  /* ── Game username verification ─────────────────────────── */
  const handleVerifyUsername = async (e) => {
    e.preventDefault();
    const name = gameUsernameInput.trim();
    if (!name) return;
    setGameUsernameError('');
    setGameUsernameLoading(true);
    try {
      const { exists } = await checkGameUsername(name);
      if (exists) {
        setGameUsername(name);
      } else {
        setGameUsernameError(
          `No in-game character named "${name}" was found. Please use your exact character name.`
        );
      }
    } catch {
      setGameUsernameError('Could not verify username. Please try again.');
    } finally {
      setGameUsernameLoading(false);
    }
  };

  /* ── Vote handler ────────────────────────────────────────── */
  const handleVoteClick = async (siteId) => {
    if (!gameUsername) return;

    // Clear any previous error for this site
    setVoteErrors(prev => ({ ...prev, [siteId]: null }));

    // Open the topsite in a new tab with the player's user_id as callback
    const site = VOTE_SITES.find(s => s.id === siteId);
    if (site && currentUser) {
      window.open(site.buildUrl(currentUser.id), '_blank', 'noopener,noreferrer');
    }

    try {
      await submitVote(currentUser.id, siteId, gameUsername);
      // Success → pending state (ready to claim in-game)
      setSiteStates(prev => ({
        ...prev,
        [siteId]: { state: 'pending', secondsLeft: 12 * 3600 },
      }));
    } catch (err) {
      // Restore idle and show the error under the card
      setSiteStates(prev => ({
        ...prev,
        [siteId]: { state: 'idle', secondsLeft: 0 },
      }));
      setVoteErrors(prev => ({ ...prev, [siteId]: err.message }));
    }
  };

  /* ── Render ──────────────────────────────────────────────── */
  return (
    <main className="max-w-7xl mx-auto pt-16 px-4 sm:px-6 pb-24">

      {/* Page header */}
      <div className="text-center mb-14">
        <div
          className="inline-block px-10 pt-8 pb-9 mb-2 rounded-sm"
          style={{
            background: 'linear-gradient(180deg, rgba(5,4,8,0.72) 0%, rgba(5,4,8,0.45) 100%)',
            backdropFilter: 'blur(2px)',
            boxShadow: '0 0 60px rgba(0,0,0,0.6)',
          }}
        >
          <p className="font-fantasy text-xs tracking-[0.55em] mb-3"
             style={{ color: '#d4af37', textShadow: '0 1px 8px rgba(0,0,0,0.9)' }}>
            SUPPORT THE SERVER
          </p>
          <h2 className="font-fantasy text-4xl sm:text-5xl font-bold mb-6"
              style={{ color: '#ffffff', textShadow: '0 2px 20px rgba(0,0,0,0.9), 0 0 30px rgba(212,175,55,0.2)' }}>
            VOTE FOR REWARDS
          </h2>
          <div className="gold-divider max-w-xs mx-auto" />
          <p className="max-w-md mx-auto mt-6 text-sm leading-relaxed" style={{ color: '#c8bfb0', textShadow: '0 1px 6px rgba(0,0,0,0.8)' }}>
            Vote daily on each topsite to earn Vote Points, Donator Points, and experience tomes.
            After voting, type{' '}
            <span className="font-mono text-xs px-1.5 py-0.5"
              style={{ background: 'rgba(0,0,0,0.5)', border: '1px solid rgba(212,175,55,0.4)', borderRadius: 2, color: '#d4af37' }}>
              ::claimvote
            </span>
            {' '}in-game to receive your rewards instantly.
          </p>
        </div>
      </div>

      {/* Not logged in */}
      {!currentUser && (
        <div className="stone-panel max-w-md mx-auto text-center py-12 px-8" style={{ borderRadius: 2 }}>
          <p className="text-3xl mb-4">🗝</p>
          <p className="font-fantasy text-lg font-bold text-white mb-2">Login Required</p>
          <p className="font-sans text-sm mb-8" style={{ color: '#666' }}>
            You must be logged in to track and claim voting rewards.
          </p>
          <div className="flex flex-col sm:flex-row justify-center gap-4">
            <button onClick={() => setCurrentView('login')} className="btn-download font-fantasy text-xs tracking-widest py-3 px-8 uppercase">
              Login
            </button>
            <button
              onClick={() => setCurrentView('register')}
              className="btn-glass font-fantasy text-xs tracking-widest py-3 px-8 uppercase"
            >
              Register
            </button>
          </div>
        </div>
      )}

      {/* Logged in */}
      {currentUser && (
        <>
          {statusLoading && <LoadingSpinner text="Checking vote statuses..." />}

          {!statusLoading && statusError && (
            statusError.includes('Unable to reach') || statusError.includes('503')
              ? <ServerDownBanner message={statusError} />
              : (
                <div className="max-w-md mx-auto p-4 mb-8 text-center font-fantasy text-xs tracking-widest"
                     style={{ background: 'rgba(239,68,68,0.1)', border: '1px solid rgba(239,68,68,0.3)', borderRadius: 2, color: '#f87171' }}>
                  {statusError}
                </div>
              )
          )}

          {!statusLoading && !statusError && (
            <>
              {/* Daily total bar */}
              <div
                className="max-w-2xl mx-auto mb-10 p-5 flex flex-col sm:flex-row items-center justify-between gap-4"
                style={{ background: 'rgba(0,0,0,0.2)', border: '1px solid #2e2820', borderRadius: 2 }}
              >
                <div>
                  <p className="font-fantasy text-xs tracking-widest" style={{ color: '#d4af37' }}>
                    DAILY VOTING REWARDS
                  </p>
                  <p className="font-sans text-xs mt-1" style={{ color: '#9a8f80' }}>
                    Vote on all {VOTE_SITES.length} sites for maximum rewards every 12 hours.
                  </p>
                </div>
                <div className="text-center sm:text-right shrink-0">
                  <p className="font-fantasy text-2xl font-bold" style={{ color: '#d4af37' }}>
                    {VOTE_SITES.length * 2}
                  </p>
                  <p className="font-fantasy text-xs tracking-widest" style={{ color: '#555' }}>
                    MAX VOTE POINTS/DAY
                  </p>
                </div>
              </div>

              {/* ── Game username validation gate ─────────────── */}
              {!gameUsername ? (
                <div className="stone-panel max-w-md mx-auto mb-10 p-8" style={{ borderRadius: 2 }}>
                  <p className="font-fantasy text-sm tracking-widest text-white mb-1">
                    Enter Your In-Game Name
                  </p>
                  <p className="font-sans text-xs mb-5" style={{ color: '#9a8f80' }}>
                    We verify your character exists before registering your vote.
                  </p>
                  <form onSubmit={handleVerifyUsername} className="flex flex-col gap-3">
                    <input
                      type="text"
                      placeholder="Character name (exact)"
                      maxLength={12}
                      required
                      className="rpg-input font-fantasy text-sm tracking-wide px-4 py-3"
                      value={gameUsernameInput}
                      onChange={e => { setGameUsernameInput(e.target.value); setGameUsernameError(''); }}
                    />
                    {gameUsernameError && (
                      <p className="font-sans text-xs text-red-400">{gameUsernameError}</p>
                    )}
                    <button
                      type="submit"
                      disabled={gameUsernameLoading || !gameUsernameInput.trim()}
                      className="btn-download py-3 font-fantasy text-xs tracking-widest uppercase"
                    >
                      {gameUsernameLoading ? 'Verifying...' : 'Verify Character'}
                    </button>
                  </form>
                </div>
              ) : (
                <div className="max-w-3xl mx-auto mb-6 flex items-center gap-3 px-1">
                  <span className="font-fantasy text-xs tracking-widest" style={{ color: '#d4af37' }}>
                    Voting as:
                  </span>
                  <span className="font-fantasy text-xs text-white">{gameUsername}</span>
                  <button
                    onClick={() => { setGameUsername(''); setGameUsernameInput(''); }}
                    className="font-fantasy text-xs tracking-widest"
                    style={{ color: '#555', marginLeft: 'auto' }}
                    onMouseOver={e => e.currentTarget.style.color = '#d4af37'}
                    onMouseOut={e  => e.currentTarget.style.color = '#555'}
                  >
                    Change
                  </button>
                </div>
              )}

              {/* Vote cards grid */}
              <div className={`grid grid-cols-1 sm:grid-cols-2 gap-6 max-w-3xl mx-auto${!gameUsername ? ' opacity-40 pointer-events-none select-none' : ''}`}>
                {VOTE_SITES.map(site => (
                  <div key={site.id}>
                    <VoteCard
                      site={site}
                      initialState={siteStates[site.id]?.state ?? 'idle'}
                      initialSecondsLeft={siteStates[site.id]?.secondsLeft ?? 0}
                      onVoteClick={handleVoteClick}
                    />
                    {/* Per-card error message */}
                    {voteErrors[site.id] && (
                      <div
                        className="mt-2 px-4 py-2.5 text-xs font-fantasy tracking-widest text-center"
                        style={{ background: 'rgba(239,68,68,0.1)', border: '1px solid rgba(239,68,68,0.3)', borderRadius: 2, color: '#f87171' }}
                      >
                        {voteErrors[site.id]}
                      </div>
                    )}
                  </div>
                ))}
              </div>

              {/* How it works */}
              <div className="max-w-3xl mx-auto mt-12">
                <div className="stone-panel" style={{ borderRadius: 2 }}>
                  <div className="panel-header">
                    <span className="font-fantasy text-xs tracking-widest" style={{ color: '#d4af37' }}>
                      ❓&nbsp; HOW VOTING WORKS
                    </span>
                  </div>
                  <div className="p-6 grid grid-cols-1 sm:grid-cols-3 gap-6">
                    {[
                      { step: '1', title: 'Click VOTE',      body: 'The topsite opens in a new tab. Cast your vote on the external site — takes about 10 seconds.' },
                      { step: '2', title: 'Vote Registers',  body: 'Your vote is recorded immediately. The card turns green when it\'s ready to claim in-game.' },
                      { step: '3', title: '::claimvote',     body: 'Log in to Zelus and type ::claimvote to receive your Vote Points, Donator Points, and tomes.' },
                    ].map(({ step, title, body }) => (
                      <div key={step} className="flex gap-4">
                        <span
                          className="font-fantasy text-2xl font-bold shrink-0 w-8 text-center"
                          style={{ color: 'rgba(212,175,55,0.4)' }}
                        >
                          {step}
                        </span>
                        <div>
                          <p className="font-fantasy text-xs tracking-widest text-white mb-1">{title}</p>
                          <p className="font-sans text-xs leading-relaxed" style={{ color: '#9a8f80' }}>{body}</p>
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              </div>
            </>
          )}
        </>
      )}
    </main>
  );
}
