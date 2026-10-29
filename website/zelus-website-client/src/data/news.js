/**
 * news.js — Latest Updates cards on the home page (NewsSpotlight).
 *
 * Static for now (there is no news API yet): newest first. Each post has a short `snippet` for the card and
 * `body` paragraphs for the Read More modal. `cover` is the fallback gradient banner; set `image` (a file under
 * /public/assets/news/, e.g. '/assets/news/rivals.jpg') to show a picture instead -- 1280x720 JPG/WebP, keep the
 * subject in the centre ~60% (cards crop to roughly 3:2, the bottom gets a dark fade for the title).
 * Only describe content that is LIVE in game.
 */

export const NEWS_AUTHOR = { name: 'Zelus Team', role: 'Developer' };

export const NEWS = [
  {
    id: 'rev-241-fractured-archive',
    title: 'Revision 241 & Raid 4: The Fractured Archive',
    tag: 'Major Update',
    date: '2026-10-29',
    accent: '#8b5cf6',
    cover: 'radial-gradient(120% 90% at 15% 0%, rgba(139,92,246,0.55), transparent 60%), radial-gradient(90% 80% at 90% 100%, rgba(212,175,55,0.4), transparent 65%), linear-gradient(135deg, #0f0a1c, #0b0c10)',
    snippet: 'Zelus moves to OSRS revision 241 and opens its fourth raid: the Archive Guardian, three branching paths and Ocellus waiting in the vault.',
    body: [
      'Our biggest update yet: the whole game now runs on OSRS revision 241, with a new client built on the latest RuneLite and an updated 117HD.',
      { heading: 'Before you play' },
      'Close your client and open it again through the launcher: it updates itself to the new client. On your first login the game cache downloads once, which can take a few minutes. Your account, bank, items and progress all come with you; a handful of in-game settings may be back at their defaults.',
      { heading: 'Raid 4: The Fractured Archive' },
      'Defeat the Archive Guardian in the Nexus, then choose your path at each set of doors: Ezekiel or Bork, Moia or Dreadnoth, the Dagon\'hai or Xarth. The road ends in the vault with Ocellus, and his fall opens the reward chests.',
      'Form a party at the grouping board in the lobby, follow your team on the party HUD, and track your clears, times and MVPs in the Alliance Log. Learning the raid? The Quartermaster outside the lobby rents full best-in-slot setups for PvM points, and the gear leaves you when the raid ends.',
      'Find it under Teleports, Minigames: The Fractured Archive.',
      { heading: 'Also in this update' },
      'Tormented Demons have been reworked to the modern OSRS fight, vote and donation bosses now each have their own cavern, and the Security PIN is optional (with its login softlock fixed).',
    ],
  },
  {
    id: 'radiant-soul-weapons-elite-void',
    title: 'Radiant Soul Weapons & the Elite Void Island',
    tag: 'Content',
    date: '2026-10-29',
    accent: '#facc15',
    cover: 'radial-gradient(120% 90% at 20% 10%, rgba(250,204,21,0.45), transparent 60%), radial-gradient(90% 80% at 85% 90%, rgba(34,211,238,0.35), transparent 65%), linear-gradient(135deg, #14110a, #0b0c10)',
    snippet: 'Soulbound weapons now grow to Tier 15 and take their Radiant form. Elite Void Knights hold a new island and drop the Elite Void set.',
    body: [
      { heading: 'Soulbound weapons: Tier 15' },
      'Soulbound weapons now grow past Tier 10 all the way to Tier 15, where they take their Radiant form: the Radiant Soulblade, Radiant Soulbow and Radiant Soulspire, each with a new special attack. Your weapon keeps its tier, Soul XP and talents, so players at Tier 10 can keep climbing straight away.',
      'The Soul Tree has a new look with Weapon and Talents tabs and three new talents: Soul Precision, Soul Harvest and Soul Resonance. Your first reset is still free; after that a reset costs 500 PvM points instead of 5M coins.',
      { heading: 'The Elite Void Island' },
      'Elite Void Knights (melee, ranged and magic) have taken over an island of their own. Every knight can drop the Elite Void top and robe, Void knight gloves and all three Void helms, and the island has its own collection log page.',
      'Teleport there from Teleports, Monsters: Elite Void Island, or type ::elitevoid. Pest Control and its Elite Void exchange are unchanged.',
    ],
  },
  {
    id: 'rev-241-quality-of-life',
    title: 'Quality of Life: Autocast, Perks & More',
    tag: 'Patch Notes',
    date: '2026-10-29',
    accent: '#22c55e',
    cover: 'radial-gradient(110% 90% at 80% 0%, rgba(34,197,94,0.45), transparent 60%), radial-gradient(80% 70% at 10% 100%, rgba(59,130,246,0.4), transparent 65%), linear-gradient(135deg, #0a140f, #0b0c10)',
    snippet: 'Autocast from the spellbook, god spell autocasting, far more perk task XP, perk overwriting, the PvM Ticket and world boss timers.',
    body: [
      { heading: 'Magic' },
      'Right-click any combat spell in your spellbook and choose Autocast. The god staves can now autocast their god spell (Saradomin Strike, Claws of Guthix and Flames of Zamorak), and Magic Dart needs a Slayer\'s staff, Staff of the dead or Staff of light, as in OSRS.',
      { heading: 'Perks' },
      'Perk tasks now pay far more experience at high levels: an elite skilling task at perk level 120 gives about 12k instead of 4k. Type ::perktask to see your current task, and attach a new Workbench perk to an item whose perk slots are full to choose which perk it replaces.',
      { heading: 'World bosses' },
      'Server Info now shows the world bosses: votes towards the vote boss, the time until the next global boss and the donation boss total. Global bosses hit harder for small groups, so a few players can take one down without waiting for a crowd.',
      { heading: 'And more' },
      'A new PvM Ticket in the PvM shop (a tradeable 1 PvM point token, like the vote and PK tickets), Scratch cards that teleport you to the Scratch Machine, and a Presets button in your bank.',
    ],
  },
  {
    id: 'soulbound-weapons',
    image: '/assets/news/soulbound-selector.webp',
    title: 'Soulbound Living Weapons',
    tag: 'Major Feature',
    date: '2026-10-18',
    accent: '#22d3ee',
    cover: 'radial-gradient(120% 90% at 20% 10%, rgba(34,211,238,0.45), transparent 60%), radial-gradient(90% 80% at 85% 90%, rgba(212,175,55,0.4), transparent 65%), linear-gradient(135deg, #07141a, #0b0c10)',
    snippet: 'Progression reimagined: pick a weapon that grows with you from Tier 1 to Tier 10, evolving, awakening talents and unlocking auras as you fight.',
    body: [
      'Welcome to our biggest progression system yet. Your starter weapon is no longer something you throw away after twenty minutes: a Soulbound weapon grows with you from Tier 1 to Tier 10 across all of your PvM, changing its look, awakening talents and unlocking auras along the way.',
      { heading: 'Choose your archetype' },
      { image: '/assets/news/soulbound-selector.webp', alt: 'The Choose Your Soulbound Weapon window', caption: 'The choice is permanent, so choose wisely.' },
      'Soulbound Blade (melee): fast aggression, crushing crits and splash hits. Talents: Devastation, Cleave and Lifesteal. Ascended special: Soul Rend, a glacial cleave that leaves frost burning in the wound.',
      'Soulbound Recurve (ranged): high damage and multi-target pressure. Talents: Tempest, Ricochet and Executioner. Ascended special: Soul Volley.',
      'Soulbound Spire (magic): area damage and soul leech. Talents: Soul Siphon, Arcane Surge and Death Ward. Ascended special: Soul Nova.',
      { heading: 'Tiers, talents and the Soul Tree' },
      { image: '/assets/news/soulbound-soul-tree.webp', alt: 'The Soul Tree window', caption: 'Spend Soul Points on talents and turn on auras as your weapon grows.' },
      'Every kill feeds your weapon Soul XP, scaled to the monster, and bosses grant a big bonus on top (+50 to +250). Your weapon takes a new form at Tier 4 (Empowered) and Tier 8 (Ascended), and the Preview button lets you look at the next form before you reach it.',
      'Each tier earns Soul Points to rank up your three talents, and auras unlock at Tiers 5, 8 and 10. The first reset of your tree is free.',
      'Soulbound powers are made for PvM: every talent and special is disabled in PvP and in Division Rivals.',
      { heading: 'The Soul Sanctuary' },
      'Visit the Reliquary of Souls in its new sanctuary at home: commune with it to open your Soul Tree, choose your weapon, or recover a lost one with all of its progress. Type ::soultree to teleport straight to it, or ::soulweapon to choose your weapon from anywhere. Players with existing accounts can claim one too.',
      { heading: 'Fixes and polish' },
      'Custom capes and wings (including the KBD and YouTube capes), the Draconic set and other custom armour now layer correctly in GPU mode: no more linings, letters or weapons showing through your character. Custom animated textures now move in standard GPU mode with the new client.',
    ],
  },
  {
    id: 'obsidian-reliquary',
    image: '/assets/news/obsidian-reliquary.webp',
    title: 'The Obsidian Reliquary',
    tag: 'New Boss Area',
    date: '2026-10-16',
    accent: '#e2384d',
    cover: 'radial-gradient(120% 90% at 20% 10%, rgba(226,56,77,0.55), transparent 60%), radial-gradient(90% 80% at 85% 90%, rgba(124,16,32,0.7), transparent 65%), linear-gradient(135deg, #1a0a0f, #0b0c10)',
    snippet: 'Three guarded wings, three new bosses and pets that fight beside you in short bursts. The Wilderness Forge goes obsidian.',
    body: [
      'Take the new Obsidian Reliquary teleport from the Bosses list. Each wing is held by ten guardians: break through them to reach Harrowlord, Gloomfang or Venomtongue, each with their own protection prayer to respect.',
      'The Ascended Bestiary now works on charges. Spawn your combat pet for a 60-second burst whenever you need the extra pressure, and top it up with Combat Pet Shards.',
      'Every Wilderness Forge upgrade has been re-forged in matte obsidian with molten crimson edges, and the Nightfall bow can now take the celestial look.',
    ],
  },
  {
    id: 'division-rivals',
    image: '/assets/news/division-rivals.webp',
    title: 'Division Rivals: Ranked 1v1',
    tag: 'PvP',
    date: '2026-10-16',
    accent: '#d4af37',
    cover: 'radial-gradient(110% 90% at 80% 0%, rgba(212,175,55,0.5), transparent 60%), radial-gradient(80% 70% at 10% 100%, rgba(153,0,0,0.55), transparent 65%), linear-gradient(135deg, #15100a, #0b0c10)',
    snippet: 'Queue for ranked duels with standardised kits, climb from Division 10 to Elite and earn points for exclusive capes.',
    body: [
      'Talk to the Rivals Overseer (type ::rivals) and pick a bracket: Endgame, Dharok, Outlaw or Pure. Every match uses the same kit for both players, so nothing you own is ever at risk.',
      'Wins climb you through ten divisions and into the Elite ladder. Weekly win goals unlock reward chests with Rivals points and mystery boxes.',
      'Spend your points in the Rivals shop on the Challenger and Champion capes, the Celestial ornament kit and PvP titles.',
    ],
  },
  {
    id: 'chamber-of-ascension',
    image: '/assets/news/chamber-of-ascension.webp',
    title: 'Chamber of Ascension & Custom Bonds',
    tag: 'Content',
    date: '2026-09-30',
    accent: '#a855f7',
    cover: 'radial-gradient(110% 90% at 15% 0%, rgba(168,85,247,0.5), transparent 60%), radial-gradient(90% 80% at 90% 100%, rgba(56,189,248,0.35), transparent 65%), linear-gradient(135deg, #120c1c, #0b0c10)',
    snippet: 'Tiered Custom Bonds with attunable perks, new armour sets and the Chamber of Ascension.',
    body: [
      'Custom Bonds now come in tiers. Attune one in your inventory and its perks switch on: melee, ranged, magic and slayer each have their own path. Check ::bonds for the full overview.',
      'Alongside the bonds came new armour sets and the Chamber of Ascension.',
    ],
  },
];

/** "2026-10-16" -> "Oct 16, 2026" (UTC, so the date never shifts a day). */
export const formatNewsDate = (iso) =>
  new Date(`${iso}T00:00:00Z`).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric', timeZone: 'UTC' });
