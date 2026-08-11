import { useState, useEffect } from 'react';
import { submitVote, fetchVoteStatus, checkGameUsername } from '../../services/voteService.js';
import VOTE_SITES from '../../data/voteSites.js';
import VoteCard from './VoteCard.jsx';
import LoadingSpinner, { ServerDownBanner } from '../ui/LoadingSpinner.jsx';

/**
 * The Vote page.
 * - Player enters + verifies their in-game username (no account needed).
 * - Fetches per-site statuses once the username is verified.
 * - handleVoteClick: opens the topsite URL in a new tab, then POSTs to /vote/submit.
 * - Per-card state is tracked in `siteStates` map.
 */
export default function VoteView() {
  // { [siteId]: { state: 'idle'|'pending'|'cooldown'|'loading', secondsLeft: 0 } }
  const [siteStates,     setSiteStates]     = useState({});
  const [statusLoading,  setStatusLoading]  = useState(false);
  const [statusError,    setStatusError]    = useState(null);
  const [voteErrors,     setVoteErrors]     = useState({}); // { [siteId]: message }

  // Game username validation gate — this is the player's identity for voting
  const [gameUsername,        setGameUsername]        = useState('');
  const [gameUsernameInput,   setGameUsernameInput]   = useState('');
  const [gameUsernameError,   setGameUsernameError]   = useState('');
  const [gameUsernameLoading, setGameUsernameLoading] = useState(false);

  /* ── Load vote statuses once the username is verified ────── */
  useEffect(() => {
    if (!gameUsername) return;
    setStatusLoading(true);
    fetchVoteStatus(gameUsername)
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
  }, [gameUsername]);

  /* ── Silently poll while any site is awaiting the topsite's confirmation
     ping, so "unverified" flips to "pending" without the player refreshing. ── */
  const hasUnverified = Object.values(siteStates).some(s => s.state === 'unverified');
  useEffect(() => {
    if (!gameUsername || !hasUnverified) return;
    const id = setInterval(() => {
      fetchVoteStatus(gameUsername)
        .then(data => {
          const map = {};
          data.forEach(entry => {
            map[entry.site_name] = {
              state:       entry.state,
              secondsLeft: entry.seconds_remaining ?? 0,
            };
          });
          setSiteStates(map);
        })
        .catch(() => { /* silent -- a transient poll failure shouldn't disrupt the page */ });
    }, 10_000);
    return () => clearInterval(id);
  }, [gameUsername, hasUnverified]);

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

    // Open the topsite in a new tab with the player's username as callback
    const site = VOTE_SITES.find(s => s.id === siteId);
    if (site) {
      window.open(site.buildUrl(gameUsername), '_blank', 'noopener,noreferrer');
    }

    try {
      await submitVote(siteId, gameUsername);
      // Success → unverified until the topsite's own confirmation ping arrives
      // (polled for automatically; see the effect above).
      setSiteStates(prev => ({
        ...prev,
        [siteId]: { state: 'unverified', secondsLeft: 12 * 3600 },
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
            Vote on RuneLocus and RSPS-List every 12 hours to earn Vote Points.
            Vote on both in the same window for a bonus raffle ticket. After voting, type{' '}
            <span className="font-mono text-xs px-1.5 py-0.5"
              style={{ background: 'rgba(0,0,0,0.5)', border: '1px solid rgba(212,175,55,0.4)', borderRadius: 2, color: '#d4af37' }}>
              ::claimvote
            </span>
            {' '}in-game once each vote is confirmed to receive your rewards.
          </p>
        </div>
      </div>

      {/* Game username validation gate */}
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
        <>
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
                className="max-w-2xl mx-auto mb-6 p-5 flex flex-col sm:flex-row items-center justify-between gap-4"
                style={{ background: 'rgba(0,0,0,0.2)', border: '1px solid #2e2820', borderRadius: 2 }}
              >
                <div>
                  <p className="font-fantasy text-xs tracking-widest" style={{ color: '#d4af37' }}>
                    VOTING REWARDS
                  </p>
                  <p className="font-sans text-xs mt-1" style={{ color: '#9a8f80' }}>
                    Vote on both sites every 12 hours for the full bonus, plus a raffle ticket.
                  </p>
                </div>
                <div className="text-center sm:text-right shrink-0">
                  <p className="font-fantasy text-2xl font-bold" style={{ color: '#d4af37' }}>
                    {VOTE_SITES.reduce((sum, s) => sum + s.votePoints, 0)}
                  </p>
                  <p className="font-fantasy text-xs tracking-widest" style={{ color: '#555' }}>
                    MAX VOTE POINTS / 12H
                  </p>
                </div>
              </div>

              {/* Rewards breakdown */}
              <div className="max-w-2xl mx-auto mb-10 stone-panel" style={{ borderRadius: 2 }}>
                <div className="panel-header">
                  <span className="font-fantasy text-xs tracking-widest" style={{ color: '#d4af37' }}>
                    🎁&nbsp; WHAT YOU GET
                  </span>
                </div>
                <div className="p-5 flex flex-col gap-4">
                  {[
                    {
                      icon: '⭐',
                      title: 'Vote Points',
                      body: '2 for a RuneLocus vote, 1 for an RSPS-List vote — plus a bonus based on your donator rank, added once per claim. Credited to your balance instantly.',
                    },
                    {
                      icon: '🎟️',
                      title: 'Vote Raffle Ticket — bonus for voting both sites',
                      body: 'Vote on RuneLocus AND RSPS-List in the same window and you\'ll also receive a raffle ticket. Every 12 hours, one ticket holder is drawn to win 2 Mystery Boxes.',
                    },
                  ].map(({ icon, title, body }) => (
                    <div key={title} className="flex gap-4">
                      <span className="text-xl shrink-0">{icon}</span>
                      <div>
                        <p className="font-fantasy text-xs tracking-widest text-white mb-1">{title}</p>
                        <p className="font-sans text-xs leading-relaxed" style={{ color: '#9a8f80' }}>{body}</p>
                      </div>
                    </div>
                  ))}
                </div>
              </div>

              {/* Daily Vote Streak */}
              <div className="max-w-2xl mx-auto mb-10 stone-panel" style={{ borderRadius: 2 }}>
                <div className="panel-header">
                  <span className="font-fantasy text-xs tracking-widest" style={{ color: '#d4af37' }}>
                    🔥&nbsp; DAILY VOTE STREAK
                  </span>
                </div>
                <div className="p-5">
                  <p className="font-sans text-xs leading-relaxed" style={{ color: '#9a8f80' }}>
                    Separate from the rewards above — claiming a vote with{' '}
                    <span className="font-mono" style={{ color: '#d4af37' }}>::claimvote</span> also builds your Daily
                    Vote Streak, and opens the Daily Vote Streak interface in-game where you can claim that day's
                    prize. Rewards escalate the longer your streak runs, up to 28 days, and reset if you go more than
                    36 hours without voting.
                  </p>
                </div>
              </div>

              {/* Vote cards grid */}
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-6 max-w-3xl mx-auto">
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
                      { step: '2', title: 'Vote Confirmed',  body: 'The site verifies your vote and pings us back — the card turns amber while it waits, then green once confirmed and ready to claim.' },
                      { step: '3', title: '::claimvote',     body: 'Log into the game and type ::claimvote to receive your Vote Points instantly, and to progress your Daily Vote Streak.' },
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
