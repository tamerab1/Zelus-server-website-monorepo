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
