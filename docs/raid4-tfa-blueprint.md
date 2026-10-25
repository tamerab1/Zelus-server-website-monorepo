# Raid 4 ("TFA") on rev 241: implementation blueprint

Status 2026-10-25: assets audited from the vanilla rev 241 cache and RuneLite's rev 241 gameval names
(runelite@009555a90). Nothing is implemented server-side yet. "TFA" is Jagex's internal prefix; the public
name isn't in the data.

## 1. What the 241 cache already gives us

| Asset | Count | Ids | Notes |
|---|---|---|---|
| NPCs | 97 | 16631-16770 | bosses, phases, minions, lobby/story NPCs, chests (the vault chests are NPCs) |
| Interfaces | 7 | 973-979 | TfaChests, TfaHud, TfaLobby, TfaPartydetails, TfaPartylist, TfaSpectateHudInv, TfaStats |
| Varbits | 64 | 16326-16576 | party slots P0-P7, path, spectate, skip-phase mode, vault chest states, motes, capes, supply points |
| Items | 20 | 34647-34730 | capes T1-T5, motes (air/earth/fire/water), records room key, midraid supplies, Zeal reward |
| Animations | 405 | 14553-15148 | |
| Spotanims | 267 | 4042-4422 | telegraphs, projectiles, impacts |

None of these collide with Zelus content: the migration moved our customs out of every one of these ranges.

### Map: the raid's 11 regions (x 4032-4223, y 5504-5823, plane 0)

| Region | Base | Objects (by count) | Likely use |
|---|---|---|---|
| 16471 | 4096,5568 | 12k locs: barricades, pillars, doors, corpses | central hub / lobby side |
| 16215 | 4032,5568 | books, bookshelves, Book of Life and Death, Archive transporter | Archive Guardian / records room |
| 16216 | 4032,5632 | bones, bushes, Archive transporter | encounter room |
| 16726 | 4160,5504 | plants, corpses, bubbles, Archive transporter | encounter room (jelly?) |
| 16728 | 4160,5632 | candles x53, corpses, Archive transporter | encounter room (Dagon'hai?) |
| 16214 | 4032,5504 | weapon racks, dummies, flames | encounter room |
| 16727 | 4160,5568 | rubble, fire, cannonballs, hammers | encounter room (Bork/Smorks?) |
| 16470 | 4096,5504 | ascension rocks, barricades | encounter room |
| 16472 | 4096,5632 | plinths, "The Blade", Fragment, lantern | vault (TFA_BLADE_NPC_VAULT_ENTRY) |
| 16474 | 4096,5760 | stars x144 | Ocellus void arena (final) |
| 16473 | 4096,5696 | (no locs) | filler / transition |

The "likely use" column is inferred from object names, not confirmed. NPC spawn positions are NOT in the
cache (they're server data), so each room's spawn tiles must be set by hand or taken from wiki maps.

### Encounters (from the NPC names)

| Encounter | NPCs (gameval) | Notes in the data |
|---|---|---|
| Ocellus (final, 1197) | TFA_OCELLUS_INTRO*, _P1, _P2 (+ sword hitbox), _P3 (+ bomb portals basic/buff/heal, void vines) | 3 phases |
| Ezekiel (984) | TFA_EZEKIEL, _SWORD, _DEATH_SWORD, _FINALE_VFX | |
| Moia (1004) | TFA_MOIA, TFA_MOIA_MINION; varbit TFA_MOIA_SHADOW_SHIELD_AMOUNT | shield mechanic |
| Archive Guardian (984) | TFA_GUARDIAN, _START, _UNSHIELDED, _DEAD, battery active/inactive/destroyed | destroy batteries to unshield |
| Dreadnoth the Jellied (888) | TFA_JELLY_BOSS, TFA_JELLY_MINION | |
| Bork (871) + The Smorks (640) | TFA_BORK, TFA_BORK_SMORK_HP_UI_HANDLER | one shared HP bar = one encounter |
| Dagon'hai | TFA_DAGONHAI_LEADER (+unshielded), _DEMON, _FOCUS, _MINION_1/2 (+unshielded), _RIFT | |

Support NPCs: TFA_LOBBY_BANKER, TFA_MIDRAID_SUPPLIES_NPC, TFA_RECORD_KEEPER, TFA_MESSENGER, story NPCs
(Ivy Sophista, Eva Cashien, Armacus Selles), vault chests TFA_REWARDS_CHEST_VAULT_1..n (closed/open).

## 2. What the server already has to build on

- **Tombs of Amascut** (`model/activities/raids/toa`, 48 files, ~11.1k lines) is the template: party
  (`ToAParty`), lobby/party/applicants/members/invocations/supplies interfaces, a room base class (`ToARoom`)
  with one class per room under `rooms/`, boss classes under `bosses/`, a reward room.
- **Theatre of Blood** and **Chambers of Xeric** (~8k lines each) for a second reference on party UI and
  scaling.
- **`DynamicMap`** (`model/map/dynamic`): builds instanced copies of regions (`build(regionId, maxHeight)`,
  the 2x2 `buildSw/Nw/Se/Ne`, chunk lists), owns the instance's NPCs, spawned objects and events, and
  `destroy()` tears all of it down. Mad Angel uses it the same way.

## 3. Architecture

```
model/activities/raids/tfa/
  Tfa.java                 entry points: lobby object/NPC handlers, start, rejoin, spectate
  TfaParty.java            members (max 8, varbits TFA_CLIENT_P0..P7), leader, mode, path, supply points
  TfaInstance.java         one DynamicMap holding all 11 regions; room table; disposal
  TfaRoom.java             base: enter(), start(), process() per tick, onWipe(), complete(), spawns()
  rooms/                   one class per encounter + Lobby, Supplies, Vault, RecordsRoom
  bosses/                  NPCCombat per boss, one class per phase controller
  scaling/TfaScaling.java  party-size and mode multipliers
  rewards/TfaPoints.java, TfaVault.java, TfaLoot.java
  inter/                   lobby, party list/details, HUD, stats, chests, spectate (interfaces 973-979)
```

### Instance and state machine
- One `DynamicMap` per party, built from the 11 regions in a single pass with `build(List<DynamicChunk>)`,
  keeping their relative layout so transporters/passageways line up.
- `TfaInstance` states: `LOBBY -> ROOM(n) -> SUPPLIES -> ROOM(n+1) ... -> FINAL -> VAULT -> DISPOSED`.
  `TFA_CLIENT_CURRENT_PATH` holds the chosen route; each room's `complete()` opens its transporter.
- Wipe: every living member dead in the same room = wipe; teleport out, keep or forfeit points per OSRS rules,
  then `destroy()`. Logout or disconnect: members are removed from the party; the instance is disposed when
  it's empty (a `DynamicMapGuard`-style timeout for rejoin).

### Party scaling
- `TfaScaling.hp(base, partySize, mode)`, `.damage(...)`, and per-mechanic counts (minions, portals,
  batteries), all in one place so every boss reads the same table.
- The real OSRS numbers must come from the wiki (HP per party size, mode modifiers). Nothing in the cache
  carries them.

### Boss controllers
- Each boss is an `NPCCombat` plus a phase enum. `process()` runs once per tick and drives a small schedule of
  `(tick, action)` entries, so a telegraph and its hit are always N ticks apart:
  1. on tick T: play the cast animation + ground graphic on the telegraphed tiles (`World.sendGraphics`
     with a client-cycle delay);
  2. on tick T+N: resolve damage for players still on those tiles, check the protection prayer at that tick.
- Phase transitions at HP thresholds swap the NPC (e.g. TFA_OCELLUS_P1 -> P2 -> P3) or transform it, as the
  NPC list suggests.
- Use the 241 animation/spotanim ids by gameval name, not hand-picked placeholders (the lesson from Mad
  Angel: the authentic assets render fine on the 241 client).

### Points, contribution and rewards
- Per player: points for damage dealt, mechanics completed, deaths subtracted; party total feeds the
  unique roll, and each player's share of points weights who gets it, as ToA's purple roll does.
- Vault: one chest per member (TFA_VAULT_CHEST_0..7_STATE), own-chest varbit, records room key and
  capes T1-T5 (completion count tiers), collection-log varbits COLLECTION_ITEM_TFA_*.
- The exact OSRS formula and loot tables are not in the cache; they need the wiki.

## 4. Build order
1. **Asset smoke test (no mechanics).** Build the 11-region instance, walk every room, spawn every TFA NPC
   with `::npc`, and play each animation/spotanim with `::anim`/`::gfx`. Watch the client log for packet
   errors and the server for clipping problems. This verifies the assets and the map in one pass.
2. Lobby, party and instance lifecycle (create, enter, wipe, leave, dispose) with empty rooms.
3. One boss end to end (the simplest, e.g. Archive Guardian: batteries then unshielded) to settle the
   controller, telegraph and scaling patterns.
4. The remaining encounters, then Ocellus's three phases.
5. Supplies, points, vault, records room, capes, collection log.
6. Spectate, skip-phase mode, path choice.

## 5. What has to come from outside the cache
- Mechanics: attack patterns, timings, damage, phase thresholds, minion behaviour.
- Scaling and mode numbers, the points formula, and loot tables.
- Spawn tiles in each room.

The data names every asset but none of the rules. Each boss needs a mechanics write-up (wiki pages, videos)
before its controller is coded; otherwise "1:1 with OSRS" isn't checkable.
