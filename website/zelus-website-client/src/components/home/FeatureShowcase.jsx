import { useRef, useState } from 'react';
import { Skull, Castle, Swords, Hammer, Download } from 'lucide-react';
import { useApp } from '../../context/AppContext.jsx';

/**
 * Tabbed feature showcase. Only describe content that is LIVE in game (same rule as data/news.js).
 * Images live in /public/assets/news/. A tab without `image` draws the Colosseum crest instead.
 */
const FEATURES = [
  {
    id: 'pvm',
    tab: 'PvM & Raids',
    short: 'PvM & Raids',
    Icon: Skull,
    image: '/assets/news/fractured-archive-ocellus.webp',
    caption: 'Ocellus waits at the end of The Fractured Archive.',
    title: 'Raids, world bosses & living weapons',
    blurb: 'Four raids from Chambers of Xeric to our own Fractured Archive, global bosses for the whole server and a Soulbound weapon that levels up with every kill.',
    points: [
      'The Fractured Archive: branching paths, eight bosses and Ocellus in the vault',
      'Soulbound weapons grow from Tier 1 to their Radiant form at Tier 15',
      'Global bosses every 49 minutes, scaled so small groups can win',
      'The Obsidian Reliquary: three guarded wings, three new bosses',
    ],
    stats: [['4', 'Raids'], ['T15', 'Soul weapons'], ['49m', 'Boss cycle']],
  },
  {
    id: 'colosseum',
    tab: 'Colosseum of Trials',
    short: 'Colosseum',
    Icon: Castle,
    title: 'Prove yourself in the Colosseum',
    blurb: 'Boss trials built for champions, solo or with a partner. Every clear pays Trial Marks, and marks buy things you can only earn here.',
    points: [
      'Solo and duo boss trials, each with its own special attacks',
      '15 to 100 Trial Marks per clear, depending on the trial',
      'Spend marks in the Reliquary on icons and armour',
      'Upgrade icons in the Ember Crucible and hatch the Drak\'thor pet',
    ],
    stats: [['Solo', '& duo'], ['100', 'Max marks / clear'], ['12', 'Icon passives']],
  },
  {
    id: 'pvp',
    tab: 'High-Stakes PvP',
    short: 'PvP',
    Icon: Swords,
    image: '/assets/news/division-rivals.webp',
    caption: 'Division Rivals: ranked 1v1 with standard kits.',
    title: 'Climb the ranked ladder',
    blurb: 'Ranked Division Rivals duels, live tournaments and the Wilderness. Every kill and streak lands on the live feed for the whole server to see.',
    points: [
      'Division Rivals: ranked 1v1 from Division 10 up to the Elite ladder',
      'Four brackets (Endgame, Dharok, Outlaw, Pure) with identical kits',
      'Weekly win goals and the Rivals shop: capes, ornament kit, titles',
      'Wilderness Forge upgrades in matte obsidian',
    ],
    stats: [['10', 'Divisions'], ['4', 'Brackets'], ['Live', 'Kill feed']],
  },
  {
    id: 'economy',
    tab: 'Earned-Gear Economy',
    short: 'Economy',
    Icon: Hammer,
    image: '/assets/news/soulbound-soul-tree.webp',
    caption: 'The Soul Tree: talents and auras for your Soulbound weapon.',
    title: 'Gear you earned keeps its worth',
    blurb: 'Progress lives in what you have done: your weapon\'s tier and talents, the perks you forge onto gear and the points you bank from PvM, votes and trials.',
    points: [
      'Your Soulbound weapon keeps its tier, Soul XP and talents for good',
      'Workbench perks on your gear, levelled through perk tasks',
      'PvM, Vote, PK and Trial point shops, with tradeable tickets',
      'Raid rentals: best-in-slot setups for PvM points that leave when the raid ends',
    ],
    stats: [['3', 'Talents / weapon'], ['4', 'Point shops'], ['BiS', 'Raid rentals']],
  },
];

/** Stand-in visual for tabs without a screenshot: a crest over a crimson arena glow. */
function ColosseumCrest() {
  return (
    <div className="absolute inset-0 grid place-items-center"
         style={{ background: 'radial-gradient(60% 55% at 50% 55%, rgba(184,29,36,0.45), transparent 70%), radial-gradient(90% 70% at 50% 120%, rgba(212,175,55,0.25), transparent 70%), linear-gradient(180deg, #160E12, #0B090C)' }}>
      <svg viewBox="0 0 240 240" className="w-[58%] max-w-[300px] opacity-90" aria-hidden="true">
        <defs>
          <linearGradient id="k-crest" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="#F6DC8C" /><stop offset="55%" stopColor="#C59B27" /><stop offset="100%" stopColor="#6B4E12" />
          </linearGradient>
        </defs>
        <circle cx="120" cy="120" r="104" fill="none" stroke="url(#k-crest)" strokeWidth="3" />
        <circle cx="120" cy="120" r="92" fill="none" stroke="#B81D24" strokeWidth="1.5" strokeDasharray="3 6" />
        {Array.from({ length: 12 }, (_, i) => {
          const a = (i / 12) * Math.PI * 2;
          return <rect key={i} x={120 + Math.cos(a) * 78 - 5} y={120 + Math.sin(a) * 78 - 9} width="10" height="18" rx="2"
                       fill="#1C1418" stroke="url(#k-crest)" strokeWidth="1.2"
                       transform={`rotate(${(a * 180) / Math.PI + 90} ${120 + Math.cos(a) * 78} ${120 + Math.sin(a) * 78})`} />;
        })}
        <path d="M84 150 L156 78 M156 150 L84 78" stroke="url(#k-crest)" strokeWidth="7" strokeLinecap="round" />
        <path d="M78 156 l12 -6 -6 12 z M162 156 l-12 -6 6 12 z" fill="#E5B84B" />
        <circle cx="120" cy="114" r="14" fill="#B81D24" stroke="url(#k-crest)" strokeWidth="2.5" />
      </svg>
    </div>
  );
}

export default function FeatureShowcase() {
  const { setPlayOpen } = useApp();
  const [active, setActive] = useState(0);
  const tabRefs = useRef([]);
  const f = FEATURES[active];

  const onKeyDown = (e) => {
    const dir = e.key === 'ArrowRight' ? 1 : e.key === 'ArrowLeft' ? -1 : 0;
    if (!dir) return;
    e.preventDefault();
    const next = (active + dir + FEATURES.length) % FEATURES.length;
    setActive(next);
    tabRefs.current[next]?.focus();
  };

  return (
    <section className="max-w-7xl mx-auto px-4 sm:px-6 mt-24" aria-labelledby="features-title">
      <div className="text-center max-w-2xl mx-auto">
        <p className="k-eyebrow">What awaits</p>
        <h2 id="features-title" className="k-heading k-gold-text text-3xl sm:text-[2.6rem] mt-3">Forge your legend</h2>
        <p className="text-karn-muted mt-4 leading-relaxed">
          Every system on Karn feeds the same goal: power you earned yourself.
        </p>
      </div>

      {/* Tabs */}
      <div role="tablist" aria-label="Features" onKeyDown={onKeyDown}
           className="mt-10 grid grid-cols-2 lg:grid-cols-4 gap-2 p-1.5 rounded-md border border-karn-border bg-karn-surface/70">
        {FEATURES.map((t, i) => {
          const on = i === active;
          return (
            <button key={t.id} type="button" role="tab" id={`tab-${t.id}`} aria-selected={on} aria-controls={`panel-${t.id}`}
                    tabIndex={on ? 0 : -1} ref={el => (tabRefs.current[i] = el)} onClick={() => setActive(i)}
                    className={`relative flex items-center justify-center gap-2 h-12 px-3 rounded font-ui font-bold uppercase tracking-[0.12em] text-[0.82rem] sm:text-[0.88rem] transition-all ${on ? 'text-white' : 'text-karn-muted hover:text-karn-text hover:bg-white/[0.03]'}`}
                    style={on ? { background: 'linear-gradient(180deg, rgba(184,29,36,0.32), rgba(184,29,36,0.12))', boxShadow: 'inset 0 0 0 1px rgba(212,175,55,0.45)' } : undefined}>
              <t.Icon size={16} className={on ? 'text-karn-gold-hi' : ''} />
              <span className="truncate sm:hidden">{t.short}</span>
              <span className="truncate hidden sm:inline">{t.tab}</span>
            </button>
          );
        })}
      </div>

      {/* Panel */}
      <div key={f.id} role="tabpanel" id={`panel-${f.id}`} aria-labelledby={`tab-${f.id}`}
           className="k-panel k-cap mt-4 grid lg:grid-cols-[1.15fr_1fr] overflow-hidden" style={{ animation: 'k-fade-in .35s ease' }}>
        <div className="relative min-h-[240px] sm:min-h-[340px] lg:min-h-[440px] bg-black">
          {f.image
            ? <img src={f.image} alt={f.caption} loading="lazy" className="absolute inset-0 w-full h-full object-cover" />
            : <ColosseumCrest />}
          <div aria-hidden="true" className="absolute inset-0"
               style={{ background: 'linear-gradient(90deg, transparent 55%, rgba(18,16,20,0.95) 100%), linear-gradient(180deg, transparent 60%, rgba(9,8,10,0.85) 100%)' }} />
          {f.caption && (
            <p className="absolute left-4 bottom-3 right-4 text-xs text-karn-text/75 font-medium">{f.caption}</p>
          )}
        </div>

        <div className="p-6 sm:p-8 lg:p-10 flex flex-col">
          <span className="k-chip self-start"><f.Icon size={13} /> {f.tab}</span>
          <h3 className="k-heading text-white text-2xl sm:text-3xl mt-4">{f.title}</h3>
          <p className="text-karn-muted mt-3 leading-relaxed">{f.blurb}</p>

          <ul className="mt-6 space-y-3">
            {f.points.map(p => (
              <li key={p} className="flex gap-3 text-[0.94rem] text-karn-text/90 leading-snug">
                <span aria-hidden="true" className="mt-[7px] w-2 h-2 rotate-45 shrink-0 bg-karn-crimson border border-karn-gold" />
                {p}
              </li>
            ))}
          </ul>

          <div className="mt-7 grid grid-cols-3 gap-2">
            {f.stats.map(([v, l]) => (
              <div key={l} className="rounded border border-karn-border bg-black/30 px-3 py-3 text-center">
                <div className="font-ui font-bold text-xl text-karn-gold-hi tabular leading-none">{v}</div>
                <div className="font-ui font-semibold text-[0.68rem] uppercase tracking-[0.16em] text-karn-dim mt-1.5">{l}</div>
              </div>
            ))}
          </div>

          <button type="button" onClick={() => setPlayOpen(true)} className="k-btn k-btn-crimson h-12 px-7 text-[0.88rem] mt-8 self-start">
            <Download size={16} /> Play Now
          </button>
        </div>
      </div>
    </section>
  );
}
