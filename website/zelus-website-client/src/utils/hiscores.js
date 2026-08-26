// ── XP → Level (standard OSRS formula) ─────────────────────────────────────
const XP_TABLE = (() => {
  const t = [0, 0]; // index 0 unused, level 1 = 0 xp
  let total = 0;
  for (let i = 1; i <= 98; i++) {
    total += Math.floor(i + 300 * Math.pow(2, i / 7));
    t[i + 1] = Math.floor(total / 4);
  }
  return t;
})();

export function xpToLevel(xp) {
  if (!xp || xp <= 0) return 1;
  for (let lvl = 99; lvl >= 2; lvl--) {
    if (xp >= XP_TABLE[lvl]) return lvl;
  }
  return 1;
}

export function formatXp(xp) {
  if (!xp && xp !== 0) return '—';
  return Number(xp).toLocaleString();
}

const WIKI = 'https://oldschool.runescape.wiki/images';

// ── Skill definitions (OSRS sidebar order) ──────────────────────────────────
export const SKILLS = [
  { id: 'overall',      label: 'Overall',      key: null,              color: '#d4af37', icon: `${WIKI}/Combat_icon.png`        },
  { id: 'attack',       label: 'Attack',        key: 'attack_xp',      color: '#e05252', icon: `${WIKI}/Attack_icon.png`        },
  { id: 'hitpoints',    label: 'Hitpoints',     key: 'hitpoints_xp',   color: '#b91c1c', icon: `${WIKI}/Hitpoints_icon.png`     },
  { id: 'mining',       label: 'Mining',        key: 'mining_xp',      color: '#94a3b8', icon: `${WIKI}/Mining_icon.png`        },
  { id: 'strength',     label: 'Strength',      key: 'strength_xp',    color: '#d97706', icon: `${WIKI}/Strength_icon.png`      },
  { id: 'agility',      label: 'Agility',       key: 'agility_xp',     color: '#3b82f6', icon: `${WIKI}/Agility_icon.png`       },
  { id: 'smithing',     label: 'Smithing',      key: 'smithing_xp',    color: '#a16207', icon: `${WIKI}/Smithing_icon.png`      },
  { id: 'defence',      label: 'Defence',       key: 'defence_xp',     color: '#6366f1', icon: `${WIKI}/Defence_icon.png`       },
  { id: 'herblore',     label: 'Herblore',      key: 'herblore_xp',    color: '#16a34a', icon: `${WIKI}/Herblore_icon.png`      },
  { id: 'fishing',      label: 'Fishing',       key: 'fishing_xp',     color: '#2563eb', icon: `${WIKI}/Fishing_icon.png`       },
  { id: 'ranged',       label: 'Ranged',        key: 'ranged_xp',      color: '#15803d', icon: `${WIKI}/Ranged_icon.png`        },
  { id: 'thieving',     label: 'Thieving',      key: 'thieving_xp',    color: '#7c3aed', icon: `${WIKI}/Thieving_icon.png`      },
  { id: 'cooking',      label: 'Cooking',       key: 'cooking_xp',     color: '#dc2626', icon: `${WIKI}/Cooking_icon.png`       },
  { id: 'prayer',       label: 'Prayer',        key: 'prayer_xp',      color: '#ca8a04', icon: `${WIKI}/Prayer_icon.png`        },
  { id: 'crafting',     label: 'Crafting',      key: 'crafting_xp',    color: '#b45309', icon: `${WIKI}/Crafting_icon.png`      },
  { id: 'firemaking',   label: 'Firemaking',    key: 'firemaking_xp',  color: '#ea580c', icon: `${WIKI}/Firemaking_icon.png`    },
  { id: 'magic',        label: 'Magic',         key: 'magic_xp',       color: '#9333ea', icon: `${WIKI}/Magic_icon.png`         },
  { id: 'fletching',    label: 'Fletching',     key: 'fletching_xp',   color: '#4d7c0f', icon: `${WIKI}/Fletching_icon.png`     },
  { id: 'woodcutting',  label: 'Woodcutting',   key: 'woodcutting_xp', color: '#65a30d', icon: `${WIKI}/Woodcutting_icon.png`   },
  { id: 'runecrafting', label: 'Runecrafting',  key: 'runecrafting_xp',color: '#8b5cf6', icon: `${WIKI}/Runecraft_icon.png`     },
  { id: 'slayer',       label: 'Slayer',        key: 'slayer_xp',      color: '#991b1b', icon: `${WIKI}/Slayer_icon.png`        },
  { id: 'farming',      label: 'Farming',       key: 'farming_xp',     color: '#166534', icon: `${WIKI}/Farming_icon.png`       },
  { id: 'construction', label: 'Construction',  key: 'construction_xp',color: '#92400e', icon: `${WIKI}/Construction_icon.png`  },
  { id: 'hunter',       label: 'Hunter',        key: 'hunter_xp',      color: '#78350f', icon: `${WIKI}/Hunter_icon.png`        },
];
