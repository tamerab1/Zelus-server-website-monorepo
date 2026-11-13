import { Trophy, Vote, ShoppingBag, ChevronRight } from 'lucide-react';
import { useApp, getGameModeLabel } from '../../context/AppContext.jsx';
import AlertBox from '../ui/AlertBox.jsx';
import storeItems from '../../data/storeItems.js';

const RANK_COLOR = ['#F6D36B', '#D7D7DE', '#D08A4E'];

/** Featured webstore bond: the $25 "MOST POPULAR" one (labels/prices come from storeItems.js, as in the store). */
const FEATURED = storeItems.find(i => i.slug === 'dp_2180');

function RankCell({ i }) {
  if (i < 3) {
    return (
      <span className="grid place-items-center w-8 h-8 rounded-sm font-ui font-bold text-sm"
            style={{ color: '#120D02', background: `linear-gradient(180deg, ${RANK_COLOR[i]}, ${i === 0 ? '#B8892A' : i === 1 ? '#8E8E97' : '#8A5226'})` }}>
        {i + 1}
      </span>
    );
  }
  return <span className="grid place-items-center w-8 h-8 rounded-sm font-ui font-bold text-sm text-karn-muted border border-karn-border">{i + 1}</span>;
}

function TopFive({ hiscores, loading, error }) {
  const { setCurrentView } = useApp();
  const rows = hiscores.slice(0, 5);

  return (
    <div className="k-panel k-cap flex flex-col">
      <div className="flex items-center justify-between gap-3 px-5 sm:px-6 py-4 border-b border-karn-border">
        <span className="flex items-center gap-2.5">
          <Trophy size={18} className="text-karn-gold-hi" />
          <h3 className="font-fantasy font-bold text-lg text-white">Top Players</h3>
        </span>
        <span className="font-ui font-semibold text-[0.72rem] uppercase tracking-[0.2em] text-karn-dim">Total level</span>
      </div>

      <div className="flex-1">
        {loading && (
          <ul aria-busy="true" className="divide-y divide-karn-border/60">
            {Array.from({ length: 5 }, (_, i) => (
              <li key={i} className="flex items-center gap-4 px-5 sm:px-6 h-[60px]">
                <span className="w-8 h-8 rounded-sm bg-white/[0.04] animate-pulse" />
                <span className="h-3 w-40 rounded bg-white/[0.05] animate-pulse" />
              </li>
            ))}
          </ul>
        )}

        {!loading && error && (
          <p className="px-6 py-12 text-center text-sm text-karn-muted">Highscores are unavailable right now. Try again in a minute.</p>
        )}

        {!loading && !error && rows.length === 0 && (
          <p className="px-6 py-12 text-center text-sm text-karn-muted">No ranked players yet. Be the first name on the board.</p>
        )}

        {!loading && !error && rows.length > 0 && (
          <table className="w-full text-left">
            <thead className="sr-only">
              <tr><th>Rank</th><th>Player</th><th>Total level</th><th>Total XP</th></tr>
            </thead>
            <tbody className="divide-y divide-karn-border/60">
              {rows.map((p, i) => {
                const mode = getGameModeLabel(p.mode);
                return (
                  <tr key={p.username} className="h-[60px] hover:bg-white/[0.025] transition-colors">
                    <td className="pl-5 sm:pl-6 w-14"><RankCell i={i} /></td>
                    <td className="pr-3 min-w-0">
                      <span className="block font-ui font-bold text-[1.05rem] tracking-wide truncate max-w-[200px] sm:max-w-none"
                            style={{ color: i < 3 ? RANK_COLOR[i] : '#EDE6DD' }}>
                        {p.username}
                      </span>
                      {p.mode && p.mode !== 'STANDARD' && p.mode !== 'NORMAL' && (
                        <span className="block text-[0.72rem] text-karn-dim">{mode.label}</span>
                      )}
                    </td>
                    <td className="text-right pr-3 font-ui font-bold text-[1.05rem] text-karn-gold-hi tabular">
                      {Number(p.total_level).toLocaleString()}
                    </td>
                    <td className="hidden sm:table-cell text-right pr-6 font-ui font-semibold text-sm text-karn-muted tabular">
                      {Number(p.total_experience ?? 0).toLocaleString()} xp
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}
      </div>

      <div className="px-5 sm:px-6 py-3.5 border-t border-karn-border bg-black/20">
        <button type="button" onClick={() => setCurrentView('hiscores')} className="k-link">
          Full highscores <ChevronRight size={14} />
        </button>
      </div>
    </div>
  );
}

function VoteCard() {
  const { setCurrentView } = useApp();
  return (
    <div className="k-panel k-panel--hover k-cap p-6 flex gap-5 items-start">
      <span className="grid place-items-center w-12 h-12 rounded-md shrink-0 text-karn-gold-hi border border-karn-gold/40 bg-karn-gold/[0.07]">
        <Vote size={22} />
      </span>
      <div className="min-w-0 flex-1">
        <h3 className="font-fantasy font-bold text-lg text-white">Vote for Karn</h3>
        <p className="text-sm text-karn-muted mt-1.5 leading-relaxed">
          Vote on RuneLocus, RSPS-List and RuneRank every 12 hours, then type{' '}
          <span className="font-ui font-bold text-karn-gold-hi tracking-wide">::claimvote</span> in game for Vote Points and your daily streak.
        </p>
        <button type="button" onClick={() => setCurrentView('vote')} className="k-btn k-btn-gold h-10 px-5 text-[0.8rem] mt-4">
          Vote now
        </button>
      </div>
    </div>
  );
}

function StoreCard() {
  const { setCurrentView, handleCheckout, storeMessage } = useApp();
  return (
    <div className="k-panel k-panel--hover k-cap p-6">
      <div className="flex gap-5 items-start">
        <span className="grid place-items-center w-12 h-12 rounded-md shrink-0 text-white"
              style={{ background: 'linear-gradient(180deg, #CF262E, #8E141A)', boxShadow: 'inset 0 1px 0 rgba(255,255,255,.22)' }}>
          <ShoppingBag size={21} />
        </span>
        <div className="min-w-0 flex-1">
          <h3 className="font-fantasy font-bold text-lg text-white">Karn Webstore</h3>
          <p className="text-sm text-karn-muted mt-1.5 leading-relaxed">
            Donator bonds, ranks and bundles. Delivered to your character in game.
          </p>
        </div>
      </div>

      <AlertBox status={storeMessage} className="!mb-0 mt-4" />

      {FEATURED && (
        <div className="mt-5 flex items-center gap-4 rounded-md border border-karn-border bg-black/30 p-3.5">
          <img src={FEATURED.icon_path} alt="" loading="lazy" className="w-11 h-11 object-contain shrink-0" style={{ imageRendering: 'pixelated' }} />
          <div className="min-w-0 flex-1">
            <p className="font-ui font-bold text-white truncate">{FEATURED.name}</p>
            <p className="text-xs text-karn-muted">
              {FEATURED.quantity?.toLocaleString()} Donator Points{FEATURED.bonus ? ` · ${FEATURED.bonus}` : ''}
            </p>
          </div>
          <div className="text-right shrink-0">
            <p className="font-ui font-bold text-lg text-karn-gold-hi tabular leading-none">${FEATURED.price}</p>
            <button type="button" onClick={() => handleCheckout(FEATURED)} className="k-btn k-btn-crimson h-8 px-3 text-[0.72rem] mt-2">
              Buy
            </button>
          </div>
        </div>
      )}

      <button type="button" onClick={() => setCurrentView('store')} className="k-link mt-4">
        Browse the store <ChevronRight size={14} />
      </button>
    </div>
  );
}

export default function LeaderboardSection({ hiscores, loading, error }) {
  return (
    <section className="max-w-7xl mx-auto px-4 sm:px-6 mt-24" aria-labelledby="board-title">
      <div className="flex flex-col sm:flex-row sm:items-end sm:justify-between gap-3 mb-8">
        <div>
          <p className="k-eyebrow">Hall of legends</p>
          <h2 id="board-title" className="k-heading k-gold-text text-3xl sm:text-[2.4rem] mt-3">Rise through the ranks</h2>
        </div>
        <p className="text-karn-muted max-w-md sm:text-right leading-relaxed">
          Climb the highscores, vote for daily rewards and back the server you play on.
        </p>
      </div>

      <div className="grid gap-4 lg:grid-cols-[1.35fr_1fr]">
        <TopFive hiscores={hiscores} loading={loading} error={error} />
        <div className="grid gap-4 content-start">
          <VoteCard />
          <StoreCard />
        </div>
      </div>
    </section>
  );
}
