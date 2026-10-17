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
