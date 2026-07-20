/**
 * storePackages.js — Donor rank packages for the Zelus Store.
 *
 * Aligned with NR 288 Kronos server SecondaryGroup enum (8 tiers).
 * slug values MUST exactly match the keys in store_catalog.py — they are sent
 * directly to the payment API as `package_id` and validated server-side.
 *
 * Tier order (ascending):
 *   donator → super_donator → elite_donator → noble_donator →
 *   gold_donator → platinum_donator → legendary_donator → supreme_donator
 *
 * Prices and token counts mirror store_catalog.py exactly.
 * Drop boost values come from SecondaryGroup.java.
 *
 * icon_path  — path to the actual in-game rank sprite PNG (extracted from game cache).
 *              Rendered by ProductCard.jsx ItemIcon; emoji icon field is the fallback.
 */

const storePackages = [
  // ── Tier 1 — DONATOR ────────────────────────────────────────────────────────
  {
    id:         1,
    slug:       'donator',
    name:       'Donator',
    price:      10,
    tokens:     100,
    color:      'from-emerald-700 to-emerald-950',
    badge:      '#4ade80',
    icon_path:  '/assets/ranks/rank_donator.png',
    icon:       '💎',
    benefits: [
      'Donator rank & [<col=4ade80>Donor</col>] yell prefix',
      'Access to ::dzone (exclusive training area)',
      '2× Slayer points per task',
      '+2.50% double drop chance boost',
      'Custom yell text colour',
      'Reduced item-on-death loss penalty',
      '100 Donator Points on purchase',
    ],
  },

  // ── Tier 2 — SUPER DONATOR ──────────────────────────────────────────────────
  {
    id:         2,
    slug:       'super_donator',
    name:       'Super Donator',
    price:      25,
    tokens:     275,
    color:      'from-blue-700 to-blue-950',
    badge:      '#60a5fa',
    icon_path:  '/assets/ranks/rank_super_donator.png',
    icon:       '🔷',
    popular:    true,
    benefits: [
      'All Donator benefits',
      'Access to ::superdzone',
      '3× Slayer points per task',
      '+2.75% double drop chance boost',
      '1.5× global XP multiplier',
      'Auto-loot from monsters (no click needed)',
      'Priority support queue',
      '275 Donator Points on purchase',
    ],
  },

  // ── Tier 3 — ELITE DONATOR ──────────────────────────────────────────────────
  {
    id:         3,
    slug:       'elite_donator',
    name:       'Elite Donator',
    price:      50,
    tokens:     600,
    color:      'from-purple-700 to-purple-950',
    badge:      '#c084fc',
    icon_path:  '/assets/ranks/rank_elite_donator.png',
    icon:       '✨',
    benefits: [
      'All Super Donator benefits',
      'Access to ::elitezone',
      '5× Slayer points per task',
      '+3.00% double drop chance boost',
      '2× global XP multiplier',
      'Exclusive cosmetic items unlocked',
      'Remote bank access from anywhere',
      '1 free weekly mystery box',
      '600 Donator Points on purchase',
    ],
  },

  // ── Tier 4 — NOBLE DONATOR ──────────────────────────────────────────────────
  {
    id:         4,
    slug:       'noble_donator',
    name:       'Noble Donator',
    price:      75,
    tokens:     950,
    color:      'from-orange-600 to-red-950',
    badge:      '#fb923c',
    icon_path:  '/assets/ranks/rank_noble_donator.png',
    icon:       '🔥',
    benefits: [
      'All Elite Donator benefits',
      'Access to ::noblezone',
      '7× Slayer points per task',
      '+3.20% double drop chance boost',
      '+1 herb per farming patch harvest',
      'Thieving success rate bonus',
      '2 free weekly mystery boxes',
      '950 Donator Points on purchase',
    ],
  },

  // ── Tier 5 — GOLD DONATOR ───────────────────────────────────────────────────
  {
    id:         5,
    slug:       'gold_donator',
    name:       'Gold Donator',
    price:      100,
    tokens:     1300,
    color:      'from-amber-500 to-amber-950',
    badge:      '#f59e0b',
    icon_path:  '/assets/ranks/rank_gold_donator.png',
    icon:       '🏅',
    benefits: [
      'All Noble Donator benefits',
      'Access to ::goldzone',
      '10× Slayer points per task',
      '+3.50% double drop chance boost',
      '+25% prayer drain resistance',
      '+2 bonus Wintertodt crate rolls',
      'Faster birdhouse trap respawn',
      '1,300 Donator Points on purchase',
    ],
  },

  // ── Tier 6 — PLATINUM DONATOR ───────────────────────────────────────────────
  {
    id:         6,
    slug:       'platinum_donator',
    name:       'Platinum Donator',
    price:      150,
    tokens:     2000,
    color:      'from-cyan-600 to-slate-950',
    badge:      '#22d3ee',
    icon_path:  '/assets/ranks/rank_platinum_donator.png',
    icon:       '🔘',
    benefits: [
      'All Gold Donator benefits',
      'Access to ::platinumzone',
      '15× Slayer points per task',
      '+4.00% double drop chance boost',
      'Auto-pickup on ground items (~87.5% chance)',
      'Divine Super Combat Pool (5-min cooldown)',
      'Fastest herb patch growth timer',
      '2,000 Donator Points on purchase',
    ],
  },

  // ── Tier 7 — LEGENDARY DONATOR ──────────────────────────────────────────────
  {
    id:         7,
    slug:       'legendary_donator',
    name:       'Legendary Donator',
    price:      200,
    tokens:     2750,
    color:      'from-yellow-500 to-amber-900',
    badge:      '#fbbf24',
    icon_path:  '/assets/ranks/rank_legendary_donator.png',
    icon:       '👑',
    exclusive:  true,
    benefits: [
      'All Platinum Donator benefits',
      'Access to ::legendaryzone',
      '20× Slayer points per task',
      '+5.00% double drop chance boost',
      '+50% prayer drain resistance',
      'Auto-pickup on ground items (100%)',
      'Exclusive Legendary cosmetic pet',
      '5 free monthly mystery boxes',
      '2,750 Donator Points on purchase',
    ],
  },

  // ── Tier 8 — SUPREME DONATOR ─────────────────────────────────────────────────
  {
    id:         8,
    slug:       'supreme_donator',
    name:       'Supreme Donator',
    price:      300,
    tokens:     4500,
    color:      'from-fuchsia-600 to-purple-950',
    badge:      '#d946ef',
    icon_path:  '/assets/ranks/rank_supreme_donator.png',
    icon:       '⚜️',
    ultimate:   true,
    benefits: [
      'All Legendary Donator benefits',
      'Access to ::supremezone',
      '30× Slayer points per task',
      '+7.00% double drop chance boost',
      'Unlimited global XP multiplier',
      'Overload Pool (500-tick buff, 21 refreshes)',
      'Exclusive Supreme cosmetic pet',
      '5 free weekly mystery boxes',
      'Custom NPC placed in-game with your name',
      'Permanent name on the Zelus Hall of Fame',
      '4,500 Donator Points on purchase',
    ],
  },
];

export default storePackages;
