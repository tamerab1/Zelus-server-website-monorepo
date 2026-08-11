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
 *   category    : 'dp' | 'ranks'
 *   price       : number       — USD (display only; authoritative price is server-side)
 *   badge       : string       — hex accent colour
 *   color       : string       — Tailwind gradient classes for card banner
 *   icon_path   : string       — served from /public/assets/items/ (NR 288 sprite)
 *                               dp    → pixelated inventory sprite (OSRS wiki equivalent)
 *                               ranks → /public/assets/ranks/rank_*.png
 *   iconFallback: string       — emoji shown while img loads / on error
 *   description : string
 *   // category-specific optional fields:
 *   points      : number       — dp & ranks: Donator Points awarded on purchase
 *   bonus       : string       — dp: e.g. "+20% bonus"
 *   quantity    : number       — dp denomination display
 *   benefits    : string[]     — ranks: perk list
 *   badge_label : string       — ribbon ("POPULAR", "BEST VALUE", …)
 * }
 *
 * RANK SLUGS — aligned with NR 288 SecondaryGroup enum & store_catalog.py:
 *   donator → super_donator → elite_donator → noble_donator →
 *   gold_donator → platinum_donator → legendary_donator → supreme_donator
 */

// ── Category manifest ─────────────────────────────────────────────────────────
export const CATEGORIES = [
  { id: 'dp',       label: 'Donator Points', icon: '💠', desc: 'Top up your Donator Points balance' },
  { id: 'ranks',    label: 'Ranks',          icon: '👑', desc: 'Unlocked automatically as your Total Donated grows — not purchased directly' },
];

// ── Items ─────────────────────────────────────────────────────────────────────
const storeItems = [

  // ─── DONATOR POINTS ────────────────────────────────────────────────────────
  // Amounts confirmed live in-game 2026-08-11 after a server-side DP rebalance.
  // slug/id are kept stable (they're the payment package_id contract with the
  // backend) even though they no longer match the displayed quantity.
  {
    id:           'dp_500',
    slug:         'dp_500',
    name:         '$5 Donator Bond',
    category:     'dp',
    price:        5,
    badge:        '#38bdf8',
    color:        'from-sky-800 to-sky-950',

    // NR 288 item 30464 ($5 Donator bond) — real icon rendered from the live cache model
    icon_path:    '/assets/items/bond_5.png',
    iconFallback: '💠',
    description:  'A small reserve of Donator Points — spend them in the in-game donor shop.',
    quantity:     350,
  },
  {
    id:           'dp_1200',
    slug:         'dp_1200',
    name:         '$10 Donator Bond',
    category:     'dp',
    price:        10,
    badge:        '#38bdf8',
    color:        'from-sky-700 to-sky-950',

    // NR 288 item 30497 ($10 Donator bond) — real icon rendered from the live cache model
    icon_path:    '/assets/items/bond_10.png',
    iconFallback: '💠',
    description:  'Solid value for new players looking to unlock exclusive rewards early.',
    quantity:     770,
    bonus:        '+10% bonus',
  },
  {
    id:           'dp_3000',
    slug:         'dp_3000',
    name:         '$25 Donator Bond',
    category:     'dp',
    price:        25,
    badge:        '#7dd3fc',
    color:        'from-sky-600 to-blue-950',

    // NR 288 item 30466 ($25 Donator bond) — real icon rendered from the live cache model
    icon_path:    '/assets/items/bond_25.png',
    iconFallback: '💠',
    description:  'The most popular top-up — enough for gear, scrolls and a few mystery boxes.',
    quantity:     2180,
    bonus:        '+25% bonus',
    badge_label:  'POPULAR',
  },
  {
    id:           'dp_8000',
    slug:         'dp_8000',
    name:         '$50 Donator Bond',
    category:     'dp',
    price:        50,
    badge:        '#bae6fd',
    color:        'from-blue-500 to-blue-950',

    // NR 288 item 30467 ($50 Donator bond) — real icon rendered from the live cache model
    icon_path:    '/assets/items/bond_50.png',
    iconFallback: '💠',
    description:  'Serious stock for serious players. Unlock high-tier donor shop items with ease.',
    quantity:     4350,
    bonus:        '+24% bonus',
  },
  {
    id:           'dp_20000',
    slug:         'dp_20000',
    name:         '$100 Donator Bond',
    category:     'dp',
    price:        100,
    badge:        '#e0f2fe',
    color:        'from-sky-400 to-blue-900',

    // NR 288 item 30468 ($100 Donator bond) — real icon rendered from the live cache model
    icon_path:    '/assets/items/bond_100.png',
    iconFallback: '💠',
    description:  'The ultimate Donator Points vault — maximum value, maximum power.',
    quantity:     9450,
    bonus:        '+35% bonus',
    badge_label:  'BEST VALUE',
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

];

export default storeItems;
