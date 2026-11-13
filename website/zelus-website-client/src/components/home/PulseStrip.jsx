import { Skull, Activity, Crown, Swords, Flame, Gem, TrendingUp, Trophy, Target } from 'lucide-react';
import { useApp } from '../../context/AppContext.jsx';
import { timeAgo } from '../../hooks/useLiveFeed.js';

/** Live feed event types pushed by the game server (io.ruin.services.LiveFeed). */
const EVENT_STYLE = {
  pvp_kill:       { Icon: Swords,     color: '#DC2626', label: 'PvP kill'  },
  killstreak:     { Icon: Flame,      color: '#F97316', label: 'Streak'    },
  rare_drop:      { Icon: Gem,        color: '#E5B84B', label: 'Rare drop' },
  level_up:       { Icon: TrendingUp, color: '#4ADE80', label: 'Level up'  },
  tournament_win: { Icon: Trophy,     color: '#E5B84B', label: 'Champion'  },
  bounty_claim:   { Icon: Target,     color: '#DC2626', label: 'Bounty'    },
};

function CardHead({ icon, title, right }) {
  return (
    <div className="flex items-center justify-between gap-3 mb-4">
      <span className="flex items-center gap-2.5">
        <span className="grid place-items-center w-8 h-8 rounded border border-karn-gold/35 text-karn-gold-hi bg-karn-gold/[0.07]">
          {icon}
        </span>
        <span className="font-ui font-bold uppercase tracking-[0.2em] text-[0.8rem] text-karn-text">{title}</span>
      </span>
      {right}
    </div>
  );
}

/* 1 ── World event: the global boss cycle (GlobalBossHandler.SPAWN_EVERY = 4900 ticks ≈ 49 min). */
function WorldEventCard() {
  return (
    <div className="k-panel k-panel--hover k-cap p-5 flex flex-col">
      <CardHead icon={<Skull size={16} />} title="World Event" right={<span className="k-chip">Global boss</span>} />
      <div className="flex items-center gap-4">
        <div className="relative grid place-items-center w-[74px] h-[74px] shrink-0">
          <svg viewBox="0 0 74 74" className="absolute inset-0" aria-hidden="true">
            {/* Decorative full ring: the site has no live boss timer, so nothing here should read as progress. */}
            <circle cx="37" cy="37" r="33" fill="none" stroke="url(#k-ring)" strokeWidth="3" />
            <circle cx="37" cy="37" r="27" fill="none" stroke="#2A2226" strokeWidth="1" strokeDasharray="2 4" />
            <defs>
              <linearGradient id="k-ring" x1="0" y1="0" x2="1" y2="1">
                <stop offset="0%" stopColor="#E5B84B" /><stop offset="100%" stopColor="#B81D24" />
              </linearGradient>
            </defs>
          </svg>
          <span className="text-center leading-none">
            <span className="block font-ui font-bold text-2xl text-white tabular">49</span>
            <span className="block font-ui font-bold text-[0.6rem] tracking-[0.2em] text-karn-muted mt-0.5">MIN</span>
          </span>
        </div>
        <div className="min-w-0">
          <p className="font-fantasy font-bold text-lg text-white leading-snug">A world boss rises every 49 minutes</p>
          <p className="text-sm text-karn-muted mt-1 leading-relaxed">It hits harder for small groups, so a few friends can take it down.</p>
        </div>
      </div>
      <p className="mt-auto pt-4 text-[0.8rem] text-karn-dim">
        Live timer in game: <span className="font-ui font-bold text-karn-gold-hi tracking-wider">::gb</span> or Server Info
      </p>
    </div>
  );
}

/* 2 ── Live activity ticker from /livefeed. */
function ActivityCard({ feed }) {
  const { events, connected, freshIds } = feed;
  const rows = events.slice(0, 4);
  return (
    <div className="k-panel k-panel--hover k-cap p-5 flex flex-col">
      <CardHead icon={<Activity size={16} />} title="Live Activity" right={
        <span className="flex items-center gap-1.5 font-ui font-bold text-[0.7rem] tracking-[0.2em] uppercase"
              style={{ color: connected ? '#4ADE80' : '#6E655D' }}>
          <span className={`w-1.5 h-1.5 rounded-full ${connected ? 'k-live-dot' : ''}`} style={{ background: connected ? '#4ADE80' : '#6E655D' }} />
          {connected ? 'Live' : 'Offline'}
        </span>
      } />
      {rows.length === 0 ? (
        <div className="flex-1 grid place-items-center text-center py-4">
          <p className="text-sm text-karn-muted leading-relaxed max-w-[260px]">
            {connected ? 'The realm is quiet. Rare drops, kills and 99s appear here the moment they happen.'
                       : 'The live feed is reconnecting.'}
          </p>
        </div>
      ) : (
        <ul className="space-y-2.5">
          {rows.map(ev => {
            const cfg = EVENT_STYLE[ev.type] ?? EVENT_STYLE.pvp_kill;
            return (
              <li key={ev.id} className="flex items-start gap-2.5"
                  style={freshIds.has(ev.id) ? { animation: 'k-feed-in .35s ease-out' } : undefined}>
                <cfg.Icon size={15} className="mt-0.5 shrink-0" style={{ color: cfg.color }} />
                <span className="min-w-0 flex-1 text-[0.86rem] leading-snug text-karn-text/90 line-clamp-2">{ev.message}</span>
                <span className="shrink-0 font-ui font-semibold text-[0.72rem] text-karn-dim tabular mt-0.5">{timeAgo(ev.timestamp)}</span>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}

/*
 * 3 ── Champion spotlight. Uses only real data, in this order:
 *   the latest tournament winner from the live feed, then the top killer on the hiscores
 *   (kills are only known when the API reads character files), then the total-level leader.
 */
function pickChampion(events, hiscores) {
  const win = events.find(e => e.type === 'tournament_win');
  if (win) return { kind: 'Tournament Champion', name: win.username || '—', stat: win.message, when: timeAgo(win.timestamp) };
  const topPk = [...hiscores].sort((a, b) => (b.kills ?? 0) - (a.kills ?? 0))[0];
  if (topPk && (topPk.kills ?? 0) > 0) return { kind: 'Top PKer', name: topPk.username, stat: `${topPk.kills.toLocaleString()} player kills` };
  const top = hiscores[0];
  if (top) return { kind: 'Realm Leader', name: top.username, stat: `Total level ${Number(top.total_level).toLocaleString()}` };
  return null;
}

function ChampionCard({ feed, hiscores, loading }) {
  const { setCurrentView } = useApp();
  const champ = pickChampion(feed.events, hiscores);
  return (
    <div className="k-panel k-panel--hover k-cap p-5 flex flex-col overflow-hidden">
      <div aria-hidden="true" className="absolute -right-10 -top-10 w-44 h-44 rounded-full"
           style={{ background: 'radial-gradient(circle, rgba(212,175,55,0.16), transparent 70%)' }} />
      <CardHead icon={<Crown size={16} />} title="Champion" right={champ && <span className="k-chip">{champ.kind}</span>} />
      {champ ? (
        <>
          <p className="font-fantasy font-black text-[1.7rem] leading-tight k-gold-text truncate" title={champ.name}>{champ.name}</p>
          <p className="text-sm text-karn-muted mt-1.5 leading-relaxed line-clamp-2">{champ.stat}{champ.when ? ` · ${champ.when}` : ''}</p>
        </>
      ) : (
        <p className="text-sm text-karn-muted">{loading ? 'Reading the hiscores…' : 'No champion crowned yet. It could be you.'}</p>
      )}
      <button type="button" onClick={() => setCurrentView('hiscores')} className="k-link mt-auto pt-4 self-start">
        View highscores →
      </button>
    </div>
  );
}

export default function PulseStrip({ feed, hiscores, loading }) {
  return (
    <section aria-label="Server pulse" className="relative z-10 max-w-7xl mx-auto px-4 sm:px-6 -mt-[120px] sm:-mt-[130px]">
      <div className="grid gap-4 md:grid-cols-3">
        <WorldEventCard />
        <ActivityCard feed={feed} />
        <ChampionCard feed={feed} hiscores={hiscores} loading={loading} />
      </div>
    </section>
  );
}
