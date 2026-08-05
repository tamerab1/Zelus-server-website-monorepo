/**
 * storeItems.js — Unified item catalogue for the Zelus Store.
 *
 * DATA CONTRACT
 * ─────────────
 * item {
 *   id          : string       — unique React key & icon filename root
 *   slug        : string       — sent to payment API as `package_id`
 *                               MUST match a key in store_catalog.py exactly
 *   name        : string
 *   category    : 'dp' | 'packages' | 'ranks' | 'boosters' | 'misc'
 *   price       : number       — USD (display only; authoritative price is server-side)
 *   badge       : string       — hex accent colour
 *   color       : string       — Tailwind gradient classes for card banner
 *   icon_path   : string       — served from /public/assets/items/ (NR 288 sprite or thematic render)
 *                               dp/boosters/misc → pixelated inventory sprite (OSRS wiki equivalent)
 *                               packages → smooth thematic render (boss/item detail image)
 *                               ranks → /public/assets/ranks/rank_*.png
 *   iconFallback: string       — emoji shown while img loads / on error
 *   description : string
 *   // category-specific optional fields:
 *   points      : number       — dp & ranks: Donator Points awarded on purchase
 *   bonus       : string       — dp: e.g. "+20% bonus"
 *   quantity    : number       — dp denomination display
 *   benefits    : string[]     — ranks: perk list
 *   rewards     : { label, icon }[]  — packages: item reward list
 *   duration    : string       — boosters: active duration label
 *   badge_label : string       — ribbon ("POPULAR", "BEST VALUE", …)
 * }
 *
 * RANK SLUGS — aligned with NR 288 SecondaryGroup enum & store_catalog.py:
 *   donator → super_donator → elite_donator → noble_donator →
 *   gold_donator → platinum_donator → legendary_donator → supreme_donator
 */

const WIKI = 'https://oldschool.runescape.wiki/images';

// ── Category manifest ─────────────────────────────────────────────────────────
export const CATEGORIES = [
  { id: 'dp',       label: 'Donator Points', icon: '💠', desc: 'Top up your Donator Points balance' },
  { id: 'packages', label: 'Packages',       icon: '📦', desc: 'Curated bundles for every playstyle' },
  { id: 'ranks',    label: 'Ranks',          icon: '👑', desc: 'Permanent donator ranks & perks'     },
  { id: 'boosters', label: 'Boosters',       icon: '⚡', desc: 'Timed power-ups for maximum gains'   },
  { id: 'misc',     label: 'Miscellaneous',  icon: '🎲', desc: 'Mystery boxes, titles & utilities'   },
];

// ── Items ─────────────────────────────────────────────────────────────────────
const storeItems = [

  // ─── DONATOR POINTS ────────────────────────────────────────────────────────
  {
    id:           'dp_500',
    slug:         'dp_500',
    name:         '500 Donator Points',
    category:     'dp',
    price:        5,
    badge:        '#38bdf8',
    color:        'from-sky-800 to-sky-950',

    // NR 288 item 30464 ($5 Donator bond) → OSRS bond sprite
    icon_path:    '/assets/items/dp_coin.png',
    iconFallback: '💠',
    description:  'A small reserve of Donator Points — spend them in the in-game donor shop.',
    quantity:     500,
  },
  {
    id:           'dp_1200',
    slug:         'dp_1200',
    name:         '1,200 Donator Points',
    category:     'dp',
    price:        10,
    badge:        '#38bdf8',
    color:        'from-sky-700 to-sky-950',

    icon_path:    '/assets/items/dp_coin.png',
    iconFallback: '💠',
    description:  'Solid value for new players looking to unlock exclusive rewards early.',
    quantity:     1200,
    bonus:        '+20% bonus',
  },
  {
    id:           'dp_3000',
    slug:         'dp_3000',
    name:         '3,000 Donator Points',
    category:     'dp',
    price:        20,
    badge:        '#7dd3fc',
    color:        'from-sky-600 to-blue-950',

    icon_path:    '/assets/items/dp_coin.png',
    iconFallback: '💠',
    description:  'The most popular top-up — enough for gear, scrolls and a few mystery boxes.',
    quantity:     3000,
    bonus:        '+25% bonus',
    badge_label:  'POPULAR',
  },
  {
    id:           'dp_8000',
    slug:         'dp_8000',
    name:         '8,000 Donator Points',
    category:     'dp',
    price:        50,
    badge:        '#bae6fd',
    color:        'from-blue-500 to-blue-950',

    icon_path:    '/assets/items/dp_coin.png',
    iconFallback: '💠',
    description:  'Serious stock for serious players. Unlock high-tier donor shop items with ease.',
    quantity:     8000,
    bonus:        '+33% bonus',
  },
  {
    id:           'dp_20000',
    slug:         'dp_20000',
    name:         '20,000 Donator Points',
    category:     'dp',
    price:        100,
    badge:        '#e0f2fe',
    color:        'from-sky-400 to-blue-900',

    icon_path:    '/assets/items/dp_coin.png',
    iconFallback: '💠',
    description:  'The ultimate Donator Points vault — maximum value, maximum power.',
    quantity:     20000,
    bonus:        '+43% bonus',
    badge_label:  'BEST VALUE',
  },

  // ─── PACKAGES ───────────────────────────────────────────────────────────────

  // ── Starter PvM Package — $15
  {
    id:           'pkg_starter_pvm',
    slug:         'pkg_starter_pvm',
    name:         'Starter PvM Package',
    category:     'packages',
    price:        15,
    badge:        '#2dd4bf',
    color:        'from-teal-700 to-teal-950',

    icon_path:    '/assets/items/pkg_starter_pvm.png',
    iconFallback: '📦',
    description:  'Everything a fresh account needs to walk straight into PvM and start earning. Skip the grind — arrive ready.',
    rewards: [
      { label: 'Donator rank (Tier 1)',      icon: `${WIKI}/Coins_10000.png`          },
      { label: 'Bandos Armour Set',          icon: `${WIKI}/Bandos_chestplate.png`    },
      { label: 'Whip + Defender',            icon: `${WIKI}/Abyssal_whip.png`         },
      { label: 'Full Prayer Gear (Proselyte)',icon: `${WIKI}/Proselyte_hauberk.png`   },
      { label: '500 Donator Points',         icon: `${WIKI}/Coins_10000.png`          },
      { label: 'Double XP Scroll (24h)',     icon: `${WIKI}/Experience_lamp.png`      },
    ],
  },

  // ── Ironman Head-Start — $20
  {
    id:           'pkg_ironman_headstart',
    slug:         'pkg_ironman_headstart',
    name:         'Ironman Head-Start',
    category:     'packages',
    price:        20,
    badge:        '#94a3b8',
    color:        'from-slate-600 to-slate-950',

    icon_path:    '/assets/items/pkg_ironman_headstart.png',
    iconFallback: '🛡️',
    description:  'Designed for the self-sufficient warrior. Critical early resources to accelerate your solo journey without breaking the spirit of the mode.',
    rewards: [
      { label: 'Full Graceful Outfit',       icon: `${WIKI}/Graceful_top.png`         },
      { label: 'Herb Seed Pack ×30',         icon: `${WIKI}/Ranarr_seed.png`          },
      { label: 'Noted Pure Essence ×5,000',  icon: `${WIKI}/Pure_essence.png`         },
      { label: 'Large XP Lamp ×5',           icon: `${WIKI}/Lamp.png`                 },
      { label: 'Rune Pouch',                 icon: `${WIKI}/Rune_pouch.png`           },
      { label: '750 Donator Points',         icon: `${WIKI}/Coins_10000.png`          },
    ],
    badge_label: 'IRONMAN',
  },

  // ── Ultimate Skiller Bundle — $35
  {
    id:           'pkg_ultimate_skiller',
    slug:         'pkg_ultimate_skiller',
    name:         'Ultimate Skiller Bundle',
    category:     'packages',
    price:        35,
    badge:        '#a3e635',
    color:        'from-lime-700 to-lime-950',

    icon_path:    '/assets/items/pkg_ultimate_skiller.png',
    iconFallback: '⛏️',
    description:  'Engineered for the player who wants max total level faster than anyone else. Top-tier skilling tools, outfits, and experience accelerators — all in one.',
    rewards: [
      { label: 'Full Skilling Outfit Set',   icon: `${WIKI}/Graceful_top.png`         },
      { label: 'Dragon Pickaxe + Axe',       icon: `${WIKI}/Dragon_pickaxe.png`       },
      { label: 'Large XP Lamp ×10',          icon: `${WIKI}/Lamp.png`                 },
      { label: 'Double XP Scroll (7 days)',  icon: `${WIKI}/Experience_lamp.png`      },
      { label: 'Skill Cape (choice)',         icon: `${WIKI}/Max_cape.png`             },
      { label: '1,000 Donator Points',       icon: `${WIKI}/Coins_10000.png`          },
    ],
    badge_label: 'POPULAR',
  },

  // ── Wilderness Warlord Pack — $40
  {
    id:           'pkg_wilderness_warlord',
    slug:         'pkg_wilderness_warlord',
    name:         'Wilderness Warlord Pack',
    category:     'packages',
    price:        40,
    badge:        '#f87171',
    color:        'from-red-700 to-red-950',

    icon_path:    '/assets/items/pkg_wilderness_warlord.png',
    iconFallback: '⚔️',
    description:  'Forged in blood. Step into the Wilderness as a predator — not prey. Full BIS PvP loadout, points, and scrolls to dominate from day one.',
    rewards: [
      { label: 'Full Void Knight Set',       icon: `${WIKI}/Void_knight_top.png`      },
      { label: 'Dragon Claws',               icon: `${WIKI}/Dragon_claws.png`         },
      { label: 'Armadyl Crossbow',           icon: `${WIKI}/Armadyl_crossbow.png`     },
      { label: '500 PK Points',              icon: `${WIKI}/Skull.png`                },
      { label: '2× PK Points Scroll (48h)', icon: `${WIKI}/Experience_lamp.png`      },
      { label: '1,000 Donator Points',       icon: `${WIKI}/Coins_10000.png`          },
    ],
  },

  // ── Raider's Arsenal — $60
  {
    id:           'pkg_raiders_arsenal',
    slug:         'pkg_raiders_arsenal',
    name:         "Raider's Arsenal",
    category:     'packages',
    price:        60,
    badge:        '#a78bfa',
    color:        'from-violet-700 to-violet-950',

    icon_path:    '/assets/items/pkg_raiders_arsenal.png',
    iconFallback: '🏹',
    description:  'The complete raid-ready loadout. Gear up for Chambers, Theatre, and Tombs with supplies, armour, and an edge that separates serious raiders from the rest.',
    rewards: [
      { label: 'Bandos + Armadyl Full Sets', icon: `${WIKI}/Bandos_chestplate.png`    },
      { label: 'Raid Supply Stack ×200',     icon: `${WIKI}/Super_restore(4).png`     },
      { label: 'Occult Necklace',            icon: `${WIKI}/Occult_necklace.png`      },
      { label: 'Berserker Ring (i)',          icon: `${WIKI}/Berserker_ring_(i).png`   },
      { label: 'Boss Mystery Box ×5',        icon: `${WIKI}/Coins_10000.png`          },
      { label: '2,000 Donator Points',       icon: `${WIKI}/Coins_10000.png`          },
    ],
    badge_label:  'BEST VALUE',
  },

  // ── RNG God Bundle — $90
  {
    id:           'pkg_rng_god',
    slug:         'pkg_rng_god',
    name:         'RNG God Bundle',
    category:     'packages',
    price:        90,
    badge:        '#f472b6',
    color:        'from-pink-700 to-fuchsia-950',

    icon_path:    '/assets/items/pkg_rng_god.png',
    iconFallback: '🎁',
    description:  'Pray to the RNG gods and let fate decide your riches. Stacked with mystery boxes, drop rate scrolls, and a mountain of Donator Points — this bundle is pure chaos in the best way.',
    rewards: [
      { label: 'Grand Promo Box ×3',         icon: `${WIKI}/Coins_10000.png`          },
      { label: 'Super Mystery Box ×5',       icon: `${WIKI}/Coins_10000.png`          },
      { label: 'Standard Mystery Box ×10',   icon: `${WIKI}/Coins_10000.png`          },
      { label: '+10% Drop Rate Scroll (48h)',icon: `${WIKI}/Experience_lamp.png`      },
      { label: 'Pet Bonus Scroll (24h)',      icon: `${WIKI}/Experience_lamp.png`      },
      { label: '2,500 Donator Points',       icon: `${WIKI}/Coins_10000.png`          },
    ],
  },

  // ── Supreme Endgame Bundle — $150
  {
    id:           'pkg_supreme_endgame',
    slug:         'pkg_supreme_endgame',
    name:         'Supreme Endgame Bundle',
    category:     'packages',
    price:        150,
    badge:        '#fbbf24',
    color:        'from-amber-500 to-orange-950',

    icon_path:    '/assets/items/pkg_supreme_endgame.png',
    iconFallback: '🏆',
    description:  'The single most powerful package in the Zelus store. Hand-crafted for end-game dominance — rare gear, elite boosters, and a Donator Points haul that sets you up for months.',
    badge_label:  'LEGENDARY',
    rewards: [
      { label: 'Gold Donator Rank (Tier 5)', icon: `${WIKI}/Coins_10000.png`          },
      { label: 'Grand Promo Box ×5',         icon: `${WIKI}/Coins_10000.png`          },
      { label: 'Full Twisted Kit Cosmetics', icon: `${WIKI}/Twisted_bow.png`          },
      { label: 'Double XP Scroll (30 days)', icon: `${WIKI}/Experience_lamp.png`      },
      { label: '+10% Drop Rate Scroll (7d)', icon: `${WIKI}/Experience_lamp.png`      },
      { label: 'Exclusive Supreme Bundle Pet', icon: `${WIKI}/Pet_chaos_elemental.png` },
      { label: '5,000 Donator Points',       icon: `${WIKI}/Coins_10000.png`          },
    ],
  },

  // ─── RANKS — DO NOT MODIFY ────────────────────────────────────────────────────
  // Perfectly synced with NR 288 SecondaryGroup enum & store_catalog.py.
  // slug → price → points → benefits are authoritative — touch nothing here.
  //
  // Prices equal the kronos-server totalDonated threshold exactly (Player.java /
  // DonatorBond.java). Benefits are limited to perks confirmed live in
  // DonatorBonus.java, SecondaryGroup.java, Bank.java and Title.java — no
  // aspirational/unimplemented copy.

  // Tier 1
  {
    id:           'rank_donator',
    slug:         'donator',
    name:         'Donator',
    category:     'ranks',
    price:        10,
    tokens:       100,
    badge:        '#4ade80',
    color:        'from-emerald-700 to-emerald-950',
    icon_path:    '/assets/ranks/rank_donator.png',
    iconFallback: '💎',
    description:  'Begin your donor journey and support Zelus.',
    benefits: [
      'Donator rank & "Donor" title prefix',
      'Access to the Donator Zone (::dzone)',
      '+2.50% double drop chance boost',
      '+1 Pest Control point & +1,000 coins per game',
      '+5% bonus chance vs. superior Slayer monsters',
      '+1 bonus loot roll at Wintertodt',
      '+5% chance to double Coin Casket rewards',
      '+2% chance to double Blood Money drops',
      '+1 bonus Slayer point per task',
      '100 Donator Points on purchase',
    ],
  },

  // Tier 2
  {
    id:           'rank_super_donator',
    slug:         'super_donator',
    name:         'Super Donator',
    category:     'ranks',
    price:        50,
    tokens:       275,
    badge:        '#60a5fa',
    color:        'from-blue-700 to-blue-950',
    icon_path:    '/assets/ranks/rank_super_donator.png',
    iconFallback: '🔷',
    badge_label:  'POPULAR',
    description:  'Enhanced perks for dedicated supporters.',
    benefits: [
      'Everything in Donator, plus:',
      '+2.75% double drop chance boost',
      '7 gear preset loadout slots (up from 5)',
      '+2 Pest Control points & +2,500 coins per game',
      '+7% bonus chance vs. superior Slayer monsters',
      '3% reduction to Slayer task cancel cost',
      '+10% chance to double Coin Casket rewards',
      '+5% chance to double Blood Money drops',
      '275 Donator Points on purchase',
    ],
  },

  // Tier 3
  {
    id:           'rank_elite_donator',
    slug:         'elite_donator',
    name:         'Elite Donator',
    category:     'ranks',
    price:        100,
    tokens:       600,
    badge:        '#c084fc',
    color:        'from-purple-700 to-purple-950',
    icon_path:    '/assets/ranks/rank_elite_donator.png',
    iconFallback: '✨',
    description:  'Serious advantages for power players.',
    benefits: [
      'Everything in Super Donator, plus:',
      '"Elite" title prefix',
      '+3.00% double drop chance boost',
      '9 gear preset loadout slots',
      'No fall damage in the Karuulm Dungeon',
      '810 bank slots (+10)',
      '+3 Pest Control points & +5,000 coins per game',
      '8% chance to save your Warrior Guild tokens',
      '600 Donator Points on purchase',
    ],
  },

  // Tier 4
  {
    id:           'rank_noble_donator',
    slug:         'noble_donator',
    name:         'Noble Donator',
    category:     'ranks',
    price:        250,
    tokens:       950,
    badge:        '#fb923c',
    color:        'from-orange-600 to-red-950',
    icon_path:    '/assets/ranks/rank_noble_donator.png',
    iconFallback: '🔥',
    description:  'A noble name commands respect across Zelus.',
    benefits: [
      'Everything in Elite Donator, plus:',
      '"Noble" title prefix',
      '+3.20% double drop chance boost',
      '11 gear preset loadout slots',
      '830 bank slots (+20 more)',
      '+4 Pest Control points & +7,500 coins per game',
      '2 bonus loot rolls at Wintertodt',
      '950 Donator Points on purchase',
    ],
  },

  // Tier 5
  {
    id:           'rank_gold_donator',
    slug:         'gold_donator',
    name:         'Gold Donator',
    category:     'ranks',
    price:        400,
    tokens:       1300,
    badge:        '#f59e0b',
    color:        'from-amber-500 to-amber-950',
    icon_path:    '/assets/ranks/rank_gold_donator.png',
    iconFallback: '🏅',
    description:  'Gold-tier status with powerful passive perks.',
    benefits: [
      'Everything in Noble Donator, plus:',
      '"The Gilded" title prefix',
      '+3.50% double drop chance boost',
      '13 gear preset loadout slots',
      '850 bank slots (+20 more)',
      '+5 Pest Control points & +10,000 coins per game',
      '+15% bonus chance vs. superior Slayer monsters',
      '1,300 Donator Points on purchase',
    ],
  },

  // Tier 6
  {
    id:           'rank_platinum_donator',
    slug:         'platinum_donator',
    name:         'Platinum Donator',
    category:     'ranks',
    price:        700,
    tokens:       2000,
    badge:        '#22d3ee',
    color:        'from-cyan-600 to-slate-950',
    icon_path:    '/assets/ranks/rank_platinum_donator.png',
    iconFallback: '🔘',
    description:  'Platinum-tier: near-unstoppable prestige.',
    benefits: [
      'Everything in Gold Donator, plus:',
      '"Platinum" title prefix',
      '+4.00% double drop chance boost',
      '15 gear preset loadout slots (maximum)',
      '880 bank slots (+30 more)',
      '3 bonus loot rolls at Wintertodt',
      '+18% bonus chance vs. superior Slayer monsters',
      '2,000 Donator Points on purchase',
    ],
  },

  // Tier 7
  {
    id:           'rank_legendary_donator',
    slug:         'legendary_donator',
    name:         'Legendary Donator',
    category:     'ranks',
    price:        1000,
    tokens:       2750,
    badge:        '#fbbf24',
    color:        'from-yellow-500 to-amber-900',
    icon_path:    '/assets/ranks/rank_legendary_donator.png',
    iconFallback: '👑',
    badge_label:  'EXCLUSIVE',
    description:  'Join the legends of Zelus — one of the elite few.',
    benefits: [
      'Everything in Platinum Donator, plus:',
      '"Legend" title prefix',
      '+5.00% double drop chance boost',
      '910 bank slots (+30 more)',
      '+7 Pest Control points & +25,000 coins per game',
      '+21% bonus chance vs. superior Slayer monsters',
      '2,750 Donator Points on purchase',
    ],
  },

  // Tier 8
  {
    id:           'rank_supreme_donator',
    slug:         'supreme_donator',
    name:         'Supreme Donator',
    category:     'ranks',
    price:        1750,
    tokens:       4500,
    badge:        '#d946ef',
    color:        'from-fuchsia-600 to-purple-950',
    icon_path:    '/assets/ranks/rank_supreme_donator.png',
    iconFallback: '⚜️',
    badge_label:  'ULTIMATE',
    description:  'The pinnacle of Zelus prestige — unmatched power.',
    benefits: [
      'Everything in Legendary Donator, plus:',
      '+7.00% double drop chance boost — the highest in the game',
      '950 bank slots (+40 more) — the highest in the game',
      '+8 Pest Control points & +50,000 coins per game',
      '+25% bonus chance vs. superior Slayer monsters — the highest in the game',
      '+43% chance to save your Warrior Guild tokens',
      '4,500 Donator Points on purchase',
    ],
  },

  // ─── BOOSTERS ────────────────────────────────────────────────────────────────

  // Double XP Scroll — 1 hour
  {
    id:           'scroll_xp_1h',
    slug:         'scroll_xp_1h',
    name:         'Double XP Scroll',
    category:     'boosters',
    price:        3,
    badge:        '#34d399',
    color:        'from-emerald-700 to-emerald-950',

    icon_path:    '/assets/items/scroll_xp.png',
    iconFallback: '📜',
    description:  'Activate to double all skill XP gains for one hour. Stack with server bonuses for maximum efficiency.',
    duration:     '1 hour',
  },

  // Double XP Scroll — 24 hours
  {
    id:           'scroll_xp_24h',
    slug:         'scroll_xp_24h',
    name:         'Double XP Scroll',
    category:     'boosters',
    price:        7,
    badge:        '#34d399',
    color:        'from-green-700 to-green-950',

    icon_path:    '/assets/items/scroll_xp.png',
    iconFallback: '📜',
    description:  'A full day of 2× XP across every skill. Log in, grind hard, and watch your total level climb.',
    duration:     '24 hours',
    badge_label:  'POPULAR',
  },

  // Double XP Scroll — 7 days
  {
    id:           'scroll_xp_7d',
    slug:         'scroll_xp_7d',
    name:         'Double XP Scroll',
    category:     'boosters',
    price:        20,
    badge:        '#6ee7b7',
    color:        'from-teal-600 to-green-950',

    icon_path:    '/assets/items/scroll_xp.png',
    iconFallback: '📜',
    description:  'Seven days of non-stop 2× XP. The go-to scroll for players pushing a max cape or hitting milestone combat levels.',
    duration:     '7 days',
    badge_label:  'BEST VALUE',
  },

  // Drop Rate Scroll — +50% for 1 hour
  {
    id:           'scroll_dr_1h',
    slug:         'scroll_dr_1h',
    name:         '+50% Drop Rate Scroll',
    category:     'boosters',
    price:        4,
    badge:        '#f59e0b',
    color:        'from-amber-700 to-amber-950',

    icon_path:    '/assets/items/scroll_dr.png',
    iconFallback: '⭐',
    description:  'Temporarily push your drop rate by 50% for a single hour. Perfect for a focused boss grind session.',
    duration:     '1 hour',
  },

  // Drop Rate Scroll — +50% for 24 hours
  {
    id:           'scroll_dr_24h',
    slug:         'scroll_dr_24h',
    name:         '+50% Drop Rate Scroll',
    category:     'boosters',
    price:        10,
    badge:        '#fbbf24',
    color:        'from-amber-600 to-orange-950',

    icon_path:    '/assets/items/scroll_dr.png',
    iconFallback: '⭐',
    description:  'A full 24-hour window of elevated drop luck. Chase uniques, pets, and rare rewards with the odds stacked in your favour.',
    duration:     '24 hours',
  },

  // 2× PK Points Scroll — 1 hour
  {
    id:           'scroll_pk_1h',
    slug:         'scroll_pk_1h',
    name:         '2× PK Points Scroll',
    category:     'boosters',
    price:        3,
    badge:        '#f87171',
    color:        'from-red-700 to-red-950',

    icon_path:    '/assets/items/scroll_pk.png',
    iconFallback: '💀',
    description:  'Every kill rewards double PK Points for one hour. Enter the Wilderness, slay, and stack your points at twice the normal rate.',
    duration:     '1 hour',
  },

  // 2× PK Points Scroll — 24 hours
  {
    id:           'scroll_pk_24h',
    slug:         'scroll_pk_24h',
    name:         '2× PK Points Scroll',
    category:     'boosters',
    price:        8,
    badge:        '#ef4444',
    color:        'from-red-600 to-red-950',

    icon_path:    '/assets/items/scroll_pk.png',
    iconFallback: '💀',
    description:  'Dominate the Wilderness for an entire day. Double PK Points means faster access to the best PvP rewards on the server.',
    duration:     '24 hours',
  },

  // Pet Bonus Scroll — 1 hour
  {
    id:           'scroll_pet_1h',
    slug:         'scroll_pet_1h',
    name:         'Pet Bonus Scroll',
    category:     'boosters',
    price:        5,
    badge:        '#f472b6',
    color:        'from-pink-700 to-fuchsia-950',

    icon_path:    '/assets/items/scroll_pet.png',
    iconFallback: '🐾',
    description:  'Significantly increases the pet drop rate modifier for one hour. Combine with a Drop Rate Scroll for a lethal pet-hunting session.',
    duration:     '1 hour',
  },

  // 3× Slayer Points Scroll — 24 hours
  {
    id:           'scroll_slayer_24h',
    slug:         'scroll_slayer_24h',
    name:         '3× Slayer Points Scroll',
    category:     'boosters',
    price:        8,
    badge:        '#818cf8',
    color:        'from-indigo-700 to-indigo-950',

    icon_path:    '/assets/items/scroll_slayer.png',
    iconFallback: '🗡️',
    description:  'Triple your Slayer point rewards from every completed task for 24 hours. Unlock the most powerful Slayer shop rewards in record time.',
    duration:     '24 hours',
  },

  // ─── MISCELLANEOUS ────────────────────────────────────────────────────────────

  // Standard Mystery Box
  {
    id:           'mystery_box',
    slug:         'mystery_box',
    name:         'Mystery Box',
    category:     'misc',
    price:        5,
    badge:        '#a78bfa',
    color:        'from-violet-700 to-violet-950',

    icon_path:    '/assets/items/mystery_box.png',
    iconFallback: '🎲',
    description:  'A sealed box with a guaranteed rare reward inside. Contains weapons, armour, Donator Points, scrolls, or cosmetics — you will not walk away empty-handed.',
  },

  // Super Mystery Box
  {
    id:           'super_mystery_box',
    slug:         'super_mystery_box',
    name:         'Super Mystery Box',
    category:     'misc',
    price:        15,
    badge:        '#c084fc',
    color:        'from-purple-600 to-fuchsia-950',

    icon_path:    '/assets/items/super_mystery_box.png',
    iconFallback: '🎲',
    description:  'A tier above the standard box — higher rarity rolls, exclusive items, and a meaningful chance at end-game gear or elite cosmetics. Step up your luck.',
    badge_label:  'UPGRADED',
  },

  // Grand Promo Box
  {
    id:           'grand_promo_box',
    slug:         'grand_promo_box',
    name:         'Grand Promo Box',
    category:     'misc',
    price:        30,
    badge:        '#f59e0b',
    color:        'from-amber-600 to-yellow-950',

    icon_path:    '/assets/items/grand_promo_box.png',
    iconFallback: '🎁',
    description:  "The rarest loot table in the store. Each Grand Promo Box has a chance to yield Zelus's most coveted items — exclusive pets, max-tier gear, bulk Donator Points, and more. One box can change your account.",
    badge_label:  'RARE',
  },

  // Pet Box
  {
    id:           'pet_box',
    slug:         'pet_box',
    name:         'Exclusive Pet Box',
    category:     'misc',
    price:        18,
    badge:        '#f472b6',
    color:        'from-pink-700 to-rose-950',

    icon_path:    '/assets/items/pet_box.png',
    iconFallback: '🐾',
    description:  'A curated box containing one random exclusive companion pet — unavailable through normal gameplay. Unique cosmetics that follow you across the world of Zelus.',
  },

];

export default storeItems;
