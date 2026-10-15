/**
 * donatorBenefits.js — the Donator rank benefits matrix shown in the store.
 *
 * Every value below was read from the live kronos-server source (2026-10-15), NOT from older marketing copy.
 * Zelus has EIGHT donator ranks (SecondaryGroup / Player.is*Donator, by lifetime Total Donated). Each perk lists
 * its source so it can be re-checked whenever the server changes.
 *
 * Deliberately NOT listed: the "double drop chance" (SecondaryGroup.doubleDropChance 2.50-7.00%) — every use of it
 * in DoubleDrops.java is commented out, so it does nothing in-game. The real drop perk is the extra loot roll.
 *
 * Values are per rank, in rank order: [Donator, Super, Elite, Noble, Gold, Platinum, Legendary, Supreme].
 * `base` is what a non-donator gets. `null` = no benefit at that rank.
 */

export const DONATOR_RANKS = [
  { id: 'donator',   name: 'Donator',   full: 'Donator',           spend: 10,   color: '#4ade80', badge: '/assets/ranks/rank_donator.png' },
  { id: 'super',     name: 'Super',     full: 'Super Donator',     spend: 50,   color: '#60a5fa', badge: '/assets/ranks/rank_super_donator.png' },
  { id: 'elite',     name: 'Elite',     full: 'Elite Donator',     spend: 100,  color: '#c084fc', badge: '/assets/ranks/rank_elite_donator.png' },
  { id: 'noble',     name: 'Noble',     full: 'Noble Donator',     spend: 250,  color: '#fb923c', badge: '/assets/ranks/rank_noble_donator.png' },
  { id: 'gold',      name: 'Gold',      full: 'Gold Donator',      spend: 400,  color: '#f59e0b', badge: '/assets/ranks/rank_gold_donator.png' },
  { id: 'platinum',  name: 'Platinum',  full: 'Platinum Donator',  spend: 700,  color: '#22d3ee', badge: '/assets/ranks/rank_platinum_donator.png' },
  { id: 'legendary', name: 'Legendary', full: 'Legendary Donator', spend: 1000, color: '#fbbf24', badge: '/assets/ranks/rank_legendary_donator.png' },
  { id: 'supreme',   name: 'Supreme',   full: 'Supreme Donator',   spend: 1750, color: '#d946ef', badge: '/assets/ranks/rank_supreme_donator.png' },
];

const yes = Array(8).fill('✓');

export const BENEFIT_CATEGORIES = [
  {
    id: 'combat',
    label: 'Combat, PvP & Rivals',
    accent: '#e2384d',
    perks: [
      { label: 'Extra loot roll on kills', unit: 'chance', base: '—',
        values: ['2%', '3%', '4%', '5%', '6%', '7%', '8%', '10%'], src: 'NPCCombat.rollExtraDonatorDrop' },
      { label: 'Boss bonus coins (2-5M)', unit: 'chance per kill', base: '1 in 50',
        values: ['1 in 45', '1 in 40', '1 in 37', '1 in 35', '1 in 32', '1 in 30', '1 in 28', '1 in 25'], src: 'NPCCombat (boss coin drop)' },
      { label: 'Rare boss coins (25-50M)', unit: 'chance per kill', base: '1 in 499',
        values: ['1 in 450', '1 in 430', '1 in 390', '1 in 370', '1 in 350', '1 in 330', '1 in 310', '1 in 290'], src: 'NPCCombat (boss coin drop)' },
      { label: 'Boss coins go straight to inventory', base: '—',
        values: [null, null, null, '✓', '✓', '✓', '✓', '✓'], src: 'NPCCombat (totalDonated ≥ $250)' },
      { label: 'PK points per kill', base: '4',
        values: ['5', '6', '7', '7', '8', '8', '9', '10'], src: 'Killer.reward' },
      { label: 'Blood money bonus', base: '—',
        values: [null, null, '+5%', '+10%', '+15%', '+20%', '+25%', '+30%'], src: 'Killer.donatorBonus' },
      // the box's 1-in-N is multiplied by 0.95 ... 0.70, so it drops 1/x more often
      { label: 'Wilderness reward box drop rate', base: '—',
        values: ['+5%', '+8%', '+11%', '+15%', '+18%', '+25%', '+33%', '+43%'], src: 'WildernessRewardBox.donatorBonus (×0.95 … ×0.70)' },
      { label: 'Division Rivals points on wins', base: '—',
        values: ['+5%', '+10%', '+15%', '+15%', '+20%', '+20%', '+20%', '+25%'], src: 'RivalsProfile.donatorBonusPercent' },
      { label: 'Fight Caves starting wave', base: '45',
        values: ['52', '54', '56', '58', '60', '61', '62', '63'], src: 'FightCaves.getStartingWave' },
      { label: 'Inferno starting wave', base: '50',
        values: ['57', '61', '63', '64', '65', '66', '67', '68'], src: 'Inferno.getStartingWave' },
      { label: 'God Wars kill count needed', base: '40',
        values: ['35', '30', '20', '10', '0', '0', '0', '0'], src: 'GodwarsBossEntrance.kcRequirement' },
    ],
  },
  {
    id: 'skilling',
    label: 'Skilling & Utility',
    accent: '#d4af37',
    perks: [
      { label: 'Upgrade forge success bonus', base: '—',
        values: ['+1%', '+2%', '+3%', '+4%', '+5%', '+7%', '+9%', '+11%'], src: 'UpgradeItems.getOddsForPlayer' },
      { label: 'Woodcutting: logs noted', unit: 'chance', base: '—',
        values: [null, '5%', '10%', '15%', '20%', '25%', '30%', '35%'], src: 'Woodcutting.getDonatorNoteChance' },
      // pet 1-in-N multiplied by 0.98 ... 0.90
      { label: 'Woodcutting & clue pet rate', base: '—',
        values: [null, '+2%', '+4%', '+6%', '+8%', '+9%', '+10%', '+11%'], src: 'Woodcutting / ClueType.getPetDonatorBoost (×0.98 … ×0.90)' },
      { label: 'Clue casket saved on open', base: '—',
        values: ['1 in 20', '1 in 20', '1 in 10', '1 in 10', '1 in 10', '1 in 8', '1 in 8', '1 in 8'], src: 'ClueType (casket open)' },
      { label: 'Extra marks of grace', base: '—',
        values: ['+1', '+2', '+3', '+4', '+5', '+6', '+7', '+8'], src: 'MarkOfGrace.markOfGraceDonatorIncrease' },
      { label: 'Extra Pest Control points', base: '—',
        values: ['+1', '+2', '+2', '+2', '+3', '+4', '+5', '+7'], src: 'PestControlGame.pestPoints' },
      { label: 'Dwarf cannon capacity', base: '30',
        values: ['35', '40', '50', '60', '70', '80', '90', '100'], src: 'DwarfCannon.fill' },
      { label: 'Cannon decay time (min)', base: '25',
        values: ['30', '35', '40', '45', '50', '55', '60', '65'], src: 'DwarfCannon.getDecayTime' },
      { label: 'Cannon auto-refill (≤30 tiles)', base: '—',
        values: [null, null, null, '✓', '✓', '✓', '✓', '✓'], src: 'DwarfCannon (totalDonated ≥ $250)' },
      { label: 'Smelt with noted coal', base: '—',
        values: [null, null, null, '✓', '✓', '✓', '✓', '✓'], src: 'SmeltBar (totalDonated ≥ $250)' },
      { label: 'Crystal of memories cooldown', base: '180s',
        values: ['150s', '120s', '100s', '80s', '45s', '30s', '15s', 'None'], src: 'CrystalMemories.getMemoryCooldown' },
    ],
  },
  {
    id: 'social',
    label: 'Perks & Social',
    accent: '#a855f7',
    perks: [
      { label: 'Donator Zone (::dzone)', base: '—', values: yes, src: 'CommandHandlerRegular "dzone"' },
      { label: 'Teleport command (::tp)', base: '—', values: yes, src: 'CommandHandlerRegular "teleport"' },
      { label: 'Yell with rank badge (::yell)', base: '—', values: yes, src: 'CommandHandlerRegular "yell"' },
      { label: 'Unlimited donator tablet', base: '—', values: yes, src: 'CommandHandlerRegular "claimdonortablet"' },
      { label: 'Bank anywhere (::bank)', base: '—',
        values: [null, null, null, null, null, null, '✓', '✓'], src: 'CommandHandlerRegular "bank"' },
      { label: 'Gear preset slots', base: '3',
        values: ['4', '5', '5', '7', '7', '7', '7', '7'], src: 'GearPresetHandler.maxPresets' },
      { label: 'Trading Post offer slots', base: '3',
        values: ['4', '5', '10', '10', '10', '10', '10', '10'], src: 'TradePost.maxOfferSlots' },
      { label: 'Rank title unlocked', base: '—', values: yes, src: 'LoyaltyTitleManager (30200-30207)' },
    ],
  },
];

/** The rank a lifetime Total Donated (USD) qualifies for: index into DONATOR_RANKS, or -1. */
export const rankIndexFor = (spent) => {
  let idx = -1;
  DONATOR_RANKS.forEach((r, i) => { if (spent >= r.spend) idx = i; });
  return idx;
};
