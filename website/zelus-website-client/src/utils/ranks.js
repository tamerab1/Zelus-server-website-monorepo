/**
 * ranks.js — Privilege and donor-rank utilities for Zelus.
 *
 * Aligned with NR 288 Kronos server:
 *   PlayerGroup  enum → isAdmin(), STAFF_LABELS
 *   SecondaryGroup enum → getDonorRank()
 *   GameMode     enum → GAME_MODE_LABELS, getGameModeLabel()
 *
 * All threshold values mirror store_catalog.py pricing exactly so that the
 * displayed rank always matches what the server awards on purchase.
 */

// ── Podium icons (hiscores table) ─────────────────────────────────────────────
export const rankIcon = (i) => {
  if (i === 0) return '🥇';
  if (i === 1) return '🥈';
  if (i === 2) return '🥉';
  return `#${i + 1}`;
};

// ── Admin / staff privilege check ─────────────────────────────────────────────
// Matches NR 288 PlayerGroup enum — updated to include COMMUNITY_ADMIN,
// HEAD_ADMIN, and OWNER; removed old TRUE_DEVELOPER and HIDDEN_ADMINISTRATOR.
const ADMIN_GROUPS = new Set([
  'COMMUNITY_ADMIN',
  'ADMINISTRATOR',
  'HEAD_ADMIN',
  'DEVELOPER',
  'OWNER',
]);

export const isAdmin = (privilege) => ADMIN_GROUPS.has(privilege);

// All staff groups (used for coloured name tags, staff list, etc.)
const STAFF_GROUPS = new Set([
  'SUPPORT',
  'FORUM_MODERATOR',
  'MODERATOR',
  'COMMUNITY_ADMIN',
  'ADMINISTRATOR',
  'HEAD_ADMIN',
  'DEVELOPER',
  'OWNER',
]);

export const isStaff = (privilege) => STAFF_GROUPS.has(privilege);

/**
 * Returns a human-readable label and Tailwind colour class for a given
 * ApiPrivilege string.  Defaults to the "Player" label for REGISTERED or
 * anything unknown.
 */
export const getPrivilegeLabel = (privilege) => {
  const map = {
    REGISTERED:      { label: 'Player',            color: 'text-gray-400'   },
    BETA_TESTER:     { label: 'Beta Tester',        color: 'text-cyan-400'   },
    YOUTUBER:        { label: 'YouTuber',            color: 'text-red-400'    },
    FORUM_MODERATOR: { label: 'Forum Moderator',    color: 'text-teal-400'   },
    SUPPORT:         { label: 'Support',             color: 'text-green-400'  },
    MODERATOR:       { label: 'Moderator',           color: 'text-blue-400'   },
    COMMUNITY_ADMIN: { label: 'Community Admin',    color: 'text-indigo-400' },
    ADMINISTRATOR:   { label: 'Administrator',       color: 'text-purple-400' },
    HEAD_ADMIN:      { label: 'Head Admin',          color: 'text-rose-400'   },
    DEVELOPER:       { label: 'Developer',           color: 'text-orange-400' },
    OWNER:           { label: 'Owner',               color: 'text-yellow-300' },
    BANNED:          { label: 'Banned',              color: 'text-red-600'    },
  };
  return map[privilege] ?? { label: 'Player', color: 'text-gray-400' };
};

// ── Donor ranks — NR 288 SecondaryGroup ───────────────────────────────────────
// Thresholds match store_catalog.py prices exactly (which equal the kronos-server
// totalDonated threshold in Player.java / DonatorBond.java — no bridge math).
// getDonorRank() is called with the user's `total_spent` (USD float) and
// returns the display name + Tailwind colour for their current tier.
//
// SecondaryGroup  | Price threshold | Drop boost
// ─────────────── | ─────────────── | ──────────
// DONATOR         | $10             | +2.50 %
// SUPER_DONATOR   | $50             | +2.75 %
// ELITE_DONATOR   | $100            | +3.00 %
// NOBLE_DONATOR   | $250            | +3.20 %
// GOLD_DONATOR    | $400            | +3.50 %
// PLATINUM_DONATOR| $700            | +4.00 %
// LEGENDARY_DONATOR| $1,000         | +5.00 %
// SUPREME_DONATOR | $1,750          | +7.00 %

export const getDonorRank = (spent) => {
  if (spent >= 1750) return { name: 'Supreme Donator',   color: 'text-fuchsia-400' };
  if (spent >= 1000) return { name: 'Legendary Donator', color: 'text-yellow-300'  };
  if (spent >= 700)  return { name: 'Platinum Donator',  color: 'text-cyan-300'    };
  if (spent >= 400)  return { name: 'Gold Donator',      color: 'text-amber-400'   };
  if (spent >= 250)  return { name: 'Noble Donator',     color: 'text-orange-400'  };
  if (spent >= 100)  return { name: 'Elite Donator',     color: 'text-purple-400'  };
  if (spent >= 50)   return { name: 'Super Donator',     color: 'text-blue-400'    };
  if (spent >= 10)   return { name: 'Donator',           color: 'text-emerald-400' };
  return                     { name: 'Player',            color: 'text-gray-400'    };
};

/**
 * Returns whether a given total_spent qualifies for any donor rank at all.
 * Useful for conditional rendering of donor-only UI elements.
 */
export const isDonor = (spent) => spent >= 10;

// ── Game modes — NR 288 GameMode enum ────────────────────────────────────────
// STANDARD replaces the old NORMAL value.
// GROUP_IRONMAN and HARDCORE_GROUP_IRONMAN are new additions.
export const GAME_MODE_LABELS = {
  STANDARD:               { label: 'Standard',              icon: '⚔️'  },
  IRONMAN:                { label: 'Ironman',                icon: '🛡️'  },
  HARDCORE_IRONMAN:       { label: 'Hardcore Ironman',       icon: '💀'  },
  ULTIMATE_IRONMAN:       { label: 'Ultimate Ironman',       icon: '🔥'  },
  GROUP_IRONMAN:          { label: 'Group Ironman',          icon: '👥'  },
  HARDCORE_GROUP_IRONMAN: { label: 'Hardcore Group Ironman', icon: '☠️'  },
  // Legacy value — kept so any pre-migration accounts don't display raw "NORMAL"
  NORMAL:                 { label: 'Standard',              icon: '⚔️'  },
};

export const getGameModeLabel = (mode) =>
  GAME_MODE_LABELS[mode] ?? { label: mode ?? 'Standard', icon: '⚔️' };
