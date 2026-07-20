import { useState, useEffect, useMemo, useCallback } from 'react';
import { fetchHiscores } from '../../services/gameService.js';
import { SKILLS, xpToLevel, formatXp } from '../../utils/hiscores.js';

// ── Category definitions ────────────────────────────────────────────────────
const WIKI = 'https://oldschool.runescape.wiki/images';
const PVP_CATS = [
  { id: 'kills',      label: 'Kills',       sortKey: 'kills',      cols: ['Kills','Deaths','KDR'],    icon: `${WIKI}/Rune_scimitar.png`,   iconStyle: {} },
  { id: 'deaths',     label: 'Deaths',      sortKey: 'deaths',     cols: ['Deaths','Kills','KDR'],    icon: `${WIKI}/Skull.png`,            iconStyle: {} },
  { id: 'kdr',        label: 'KDR',         sortKey: 'kdr',        cols: ['KDR','Kills','Deaths'],    icon: `${WIKI}/Skull.png`, iconStyle: { filter: 'hue-rotate(310deg) saturate(4) brightness(0.9)' } },
  { id: 'killstreak', label: 'Kill Streak', sortKey: 'killstreak', cols: ['Streak','Kills','Deaths'], icon: `${WIKI}/Skull_(TzHaar_Fight_Pit)_icon.png`, iconStyle: {} },
];

const SKILL_CAT = { id: 'overall', label: 'Overall', sortKey: 'total_level', icon: 'https://oldschool.runescape.wiki/images/Stats_icon.png' };

const RANK_COLORS = { 1: '#ffd700', 2: '#c0c0c0', 3: '#cd7f32' };

// ── Helpers ─────────────────────────────────────────────────────────────────
function fmt(n) { return Number(n ?? 0).toLocaleString(); }
function fmtKdr(n) { return Number(n ?? 0).toFixed(2); }

function getPvpCols(cat, player) {
  switch (cat.id) {
    case 'kills':      return [fmt(player.kills),      fmt(player.deaths), fmtKdr(player.kdr)];
    case 'deaths':     return [fmt(player.deaths),     fmt(player.kills),  fmtKdr(player.kdr)];
    case 'kdr':        return [fmtKdr(player.kdr),     fmt(player.kills),  fmt(player.deaths)];
    case 'killstreak': return [fmt(player.killstreak), fmt(player.kills),  fmt(player.deaths)];
    default:           return ['—','—','—'];
  }
}

// ── Sub-components ───────────────────────────────────────────────────────────
function RankBadge({ rank }) {
  const color = RANK_COLORS[rank];
  return (
    <span className="font-fantasy text-sm font-bold" style={{ color: color || '#555' }}>
      {rank <= 3 ? ['🥇','🥈','🥉'][rank - 1] : `#${rank}`}
    </span>
  );
}

function PlayerName({ username, rank }) {
  const color = RANK_COLORS[rank] || '#c8b89a';
  return (
    <div className="flex items-center gap-2">
      {rank <= 3 && (
        <span className="w-1 h-5 rounded-full shrink-0"
              style={{ background: RANK_COLORS[rank] }} />
      )}
      <span className="font-fantasy text-sm font-semibold tracking-wide"
            style={{ color }}>
        {username}
      </span>
    </div>
  );
}

function TableHeader({ cols }) {
  return (
    <thead>
      <tr style={{ borderBottom: '1px solid rgba(212,175,55,0.2)', background: 'rgba(212,175,55,0.04)' }}>
        <th className="px-4 py-3 w-16 text-center font-fantasy text-sm tracking-widest" style={{ color: '#8a8070' }}>RANK</th>
        <th className="px-4 py-3 text-left   font-fantasy text-sm tracking-widest" style={{ color: '#8a8070' }}>PLAYER</th>
        {cols.map(c => (
          <th key={c} className="px-4 py-3 text-right font-fantasy text-sm tracking-widest pr-6" style={{ color: '#8a8070' }}>
            {c.toUpperCase()}
          </th>
        ))}
      </tr>
    </thead>
  );
}

function TableRow({ player, rank, renderCells, isEven }) {
  const [hovered, setHovered] = useState(false);
  return (
    <tr
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
      style={{
        background: hovered ? 'rgba(212,175,55,0.06)' : isEven ? 'rgba(255,255,255,0.015)' : 'transparent',
        borderBottom: '1px solid rgba(30,26,20,0.6)',
        transition: 'background 0.12s',
      }}
    >
      <td className="px-4 py-2.5 w-16 text-center"><RankBadge rank={rank} /></td>
      <td className="px-4 py-2.5"><PlayerName username={player.username} rank={rank} /></td>
      {renderCells(player)}
    </tr>
  );
}

// ── Search bar ───────────────────────────────────────────────────────────────
function SearchBar({ value, onChange, onClear }) {
  return (
    <form onSubmit={e => e.preventDefault()} className="flex items-center gap-2">
      <input
        type="text" value={value} onChange={e => onChange(e.target.value)}
        placeholder="Search player..."
        className="rpg-input font-fantasy text-xs tracking-wide px-3 py-1.5 w-44"
        style={{ fontSize: '11px' }}
      />
      {value && (
        <button type="button" onClick={onClear}
          className="font-fantasy text-xs transition-colors"
          style={{ color: '#8a8070' }}
          onMouseOver={e => e.currentTarget.style.color = '#f87171'}
          onMouseOut={e  => e.currentTarget.style.color = '#555'}>✕</button>
      )}
    </form>
  );
}

// ── Main view ────────────────────────────────────────────────────────────────
export default function HiscoresView() {
  const [data,     setData]     = useState([]);
  const [loading,  setLoading]  = useState(true);
  const [error,    setError]    = useState(null);
  const [section,  setSection]  = useState('pvp');   // 'pvp' | 'skills'
  const [pvpCat,   setPvpCat]   = useState(PVP_CATS[0]);
  const [skillCat, setSkillCat] = useState(SKILL_CAT);
  const [search,   setSearch]   = useState('');

  const load = useCallback((sortKey) => {
    setLoading(true);
    setError(null);
    fetchHiscores(sortKey, 100)
      .then(d  => { setData(d); setLoading(false); })
      .catch(e => { setError(e.message); setLoading(false); });
  }, []);

  useEffect(() => {
    const key = section === 'pvp' ? pvpCat.sortKey : skillCat.sortKey;
    load(key);
  }, [section, pvpCat, skillCat, load]);

  const ranked = useMemo(() => {
    let list = [...data];
    if (search) list = list.filter(p => p.username.toLowerCase().includes(search.toLowerCase()));
    return list.map((p, i) => ({ ...p, rank: i + 1 }));
  }, [data, search]);

  // ── Column renderers ────────────────────────────────────────────────────────
  const renderPvpCells = (cat) => (player) => {
    const vals = getPvpCols(cat, player);
    const isFirst = true;
    return vals.map((v, i) => (
      <td key={i} className="px-4 py-2.5 text-right pr-6">
        <span className={`font-mono text-sm ${i === 0 ? 'font-bold' : ''}`}
              style={{ color: i === 0 ? '#e2d9c8' : '#555' }}>
          {v}
        </span>
      </td>
    ));
  };

  const renderSkillCells = (cat) => (player) => {
    const isOverall = cat.id === 'overall';
    const level = isOverall ? player.total_level : xpToLevel(player[cat.sortKey]);
    const xp    = isOverall ? player.total_experience : (player[cat.sortKey] ?? 0);
    return (
      <>
        <td className="px-4 py-2.5 text-right">
          <span className="font-fantasy text-sm font-bold"
                style={{ color: level >= 99 ? '#ffd700' : '#e2d9c8' }}>
            {level}
          </span>
        </td>
        <td className="px-4 py-2.5 text-right pr-6">
          <span className="font-mono text-xs" style={{ color: '#8a8070' }}>
            {formatXp(xp)}
          </span>
        </td>
      </>
    );
  };

  const activeCat  = section === 'pvp' ? pvpCat  : skillCat;
  const tableCols  = section === 'pvp'
    ? pvpCat.cols
    : (skillCat.id === 'overall' ? ['Level', 'Experience'] : ['Level', 'XP']);
  const renderRow  = section === 'pvp'
    ? renderPvpCells(pvpCat)
    : renderSkillCells(skillCat);

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 py-10">

      {/* Page heading */}
      <div className="mb-6">
        <h1 className="font-fantasy text-2xl font-bold tracking-widest" style={{ color: '#d4af37' }}>
          ⚔ HISCORES
        </h1>
        <div className="gold-divider mt-2" />
      </div>

      <div className="flex gap-5 items-start flex-col lg:flex-row">

        {/* ── LEFT SIDEBAR ────────────────────────────────────────── */}
        <aside className="w-full lg:w-52 shrink-0 space-y-3">

          {/* PvP section */}
          <div className="stone-panel overflow-hidden">
            <button
              onClick={() => setSection('pvp')}
              className="w-full panel-header flex items-center gap-2 transition-all"
              style={{ background: section === 'pvp' ? 'rgba(212,175,55,0.1)' : undefined }}
            >
              <span style={{ color: '#e05252', fontSize: 13 }}>⚔</span>
              <span className="font-fantasy text-sm tracking-widest font-bold"
                    style={{ color: section === 'pvp' ? '#d4af37' : '#b0a898' }}>
                PVP STATS
              </span>
            </button>
            {section === 'pvp' && (
              <ul>
                {PVP_CATS.map(cat => (
                  <li key={cat.id}>
                    <button
                      onClick={() => setPvpCat(cat)}
                      className="w-full flex items-center gap-3 px-4 py-2 text-left transition-all"
                      style={{
                        background: pvpCat.id === cat.id ? 'rgba(212,175,55,0.08)' : 'transparent',
                        borderLeft: pvpCat.id === cat.id ? '2px solid #d4af37' : '2px solid transparent',
                        borderBottom: '1px solid rgba(30,26,20,0.5)',
                      }}
                    >
                      <img
                        src={cat.icon}
                        alt={cat.label}
                        className="w-4 h-4 shrink-0 object-contain"
                        style={{ imageRendering: 'pixelated', opacity: pvpCat.id === cat.id ? 1 : 0.45, ...cat.iconStyle }}
                      />
                      <span className="font-fantasy text-sm tracking-wide"
                            style={{ color: pvpCat.id === cat.id ? '#d4af37' : '#b0a898' }}>
                        {cat.label}
                      </span>
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </div>

          {/* Skills section */}
          <div className="stone-panel overflow-hidden">
            <button
              onClick={() => setSection('skills')}
              className="w-full panel-header flex items-center gap-2 transition-all"
              style={{ background: section === 'skills' ? 'rgba(212,175,55,0.1)' : undefined }}
            >
              <img src={`${WIKI}/Stats_icon.png`} alt="Skills"
                   className="w-4 h-4 shrink-0 object-contain"
                   style={{ imageRendering: 'pixelated', opacity: section === 'skills' ? 1 : 0.45 }} />
              <span className="font-fantasy text-sm tracking-widest font-bold"
                    style={{ color: section === 'skills' ? '#d4af37' : '#b0a898' }}>
                SKILLS
              </span>
            </button>
            {section === 'skills' && (
              <ul>
                {SKILLS.map(skill => {
                  const isActive = skillCat.id === skill.id;
                  const sortKey  = skill.id === 'overall' ? 'total_level' : skill.key;
                  return (
                    <li key={skill.id}>
                      <button
                        onClick={() => setSkillCat({ id: skill.id, label: skill.label, sortKey, icon: skill.icon })}
                        className="w-full flex items-center gap-3 px-4 py-2 text-left transition-all"
                        style={{
                          background: isActive ? 'rgba(212,175,55,0.08)' : 'transparent',
                          borderLeft: isActive ? `2px solid ${skill.color}` : '2px solid transparent',
                          borderBottom: '1px solid rgba(30,26,20,0.4)',
                        }}
                      >
                        <img
                          src={skill.icon}
                          alt={skill.label}
                          className="w-4 h-4 shrink-0 object-contain"
                          style={{ imageRendering: 'pixelated', opacity: isActive ? 1 : 0.5 }}
                        />
                        <span className="font-fantasy text-sm tracking-wide"
                              style={{ color: isActive ? '#d4af37' : '#b0a898' }}>
                          {skill.label}
                        </span>
                      </button>
                    </li>
                  );
                })}
              </ul>
            )}
          </div>
        </aside>

        {/* ── MAIN TABLE PANEL ────────────────────────────────────── */}
        <div className="flex-1 min-w-0">
          <div className="stone-panel overflow-hidden">

            {/* Header */}
            <div className="panel-header flex items-center justify-between gap-4 flex-wrap">
              <div className="flex items-center gap-3">
                {activeCat.icon && (
                  <img src={activeCat.icon} alt={activeCat.label}
                       className="w-5 h-5 object-contain shrink-0"
                       style={{ imageRendering: 'pixelated', ...(section === 'pvp' ? pvpCat.iconStyle : {}) }} />
                )}
                <span className="font-fantasy text-sm tracking-widest font-bold" style={{ color: '#d4af37' }}>
                  {activeCat.label.toUpperCase()} HISCORES
                </span>
              </div>
              <SearchBar value={search} onChange={setSearch} onClear={() => setSearch('')} />
            </div>

            {/* States */}
            {loading && (
              <div className="py-20 text-center">
                <span className="font-fantasy text-sm tracking-widest animate-pulse" style={{ color: '#8a8070' }}>
                  LOADING HISCORES...
                </span>
              </div>
            )}
            {!loading && error && (
              <div className="py-16 text-center space-y-2">
                <p className="font-fantasy text-sm tracking-widest" style={{ color: '#f87171' }}>
                  COULD NOT REACH THE GAME DATABASE
                </p>
                <p className="font-fantasy text-xs" style={{ color: '#6a6058' }}>{error}</p>
              </div>
            )}
            {!loading && !error && ranked.length === 0 && (
              <div className="py-20 text-center">
                <p className="font-fantasy text-sm tracking-widest" style={{ color: '#6a6058' }}>
                  {search ? `No players found matching "${search}"` : 'No data yet.'}
                </p>
              </div>
            )}

            {/* Table */}
            {!loading && !error && ranked.length > 0 && (
              <div className="overflow-x-auto">
                <table className="w-full border-collapse">
                  <TableHeader cols={tableCols} />
                  <tbody>
                    {ranked.map((player, i) => (
                      <TableRow
                        key={player.username}
                        player={player}
                        rank={player.rank}
                        isEven={i % 2 === 0}
                        renderCells={renderRow}
                      />
                    ))}
                  </tbody>
                </table>
              </div>
            )}

            {/* Footer */}
            {!loading && !error && (
              <div className="px-5 py-3 flex items-center justify-between"
                   style={{ borderTop: '1px solid rgba(30,26,20,0.6)', background: 'rgba(0,0,0,0.2)' }}>
                <span className="font-fantasy text-xs" style={{ color: '#6a6058' }}>
                  {search
                    ? `${ranked.length} result${ranked.length !== 1 ? 's' : ''} for "${search}"`
                    : `${ranked.length} player${ranked.length !== 1 ? 's' : ''} ranked`}
                </span>
                <span className="font-fantasy text-xs" style={{ color: '#2a2420' }}>ZELUS HISCORES</span>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
