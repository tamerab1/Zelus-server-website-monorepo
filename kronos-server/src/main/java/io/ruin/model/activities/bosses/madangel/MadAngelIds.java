package io.ruin.model.activities.bosses.madangel;

/**
 * Every raw cache/world id Mad Angel depends on, gathered in one place.
 * <p>
 * <b>Status as of the 2026-09-11 cache import:</b> her real npc definitions, 7 models, skeleton,
 * 17 frame archives, and 8 spotanims have been imported into Zelus's cache from
 * RS-Realm-Server-Package's own official cache (verified byte-for-byte via read-back decode --
 * see that session's collision-check + pack tooling under {@code .dev/cache-restore-tool}). NPC
 * and spotanim ids are unchanged from the source; 4 sequence ids collided with unrelated
 * pre-existing content and were remapped (noted per-constant below); one spotanim-referenced
 * model (48289) collided and was remapped to 61848, with spotanim 4013's own bytes patched to
 * match.
 * <p>
 * <b>Idle/walk animation bug, found and fixed same day:</b> the ORIGINAL pack pass only imported
 * sequences her combat script explicitly {@code animate()}s -- it never checked the npc
 * DEFINITION's own baked-in {@code readyAnim}/{@code walkAnim} fields (cache npc opcodes 13/14,
 * not anything {@code MadAngel.java} touches). Those pointed at raw source ids 4588 (fighting/
 * rising forms) and 1991 (dormant/dead forms), both of which were OCCUPIED in Zelus by unrelated,
 * differently-sized content -- so between explicit animate() calls she visibly played whatever
 * random unrelated animation happened to already sit at those ids. Fixed by importing the real
 * sequences to {@link #SEQ_IDLE_FIGHTING}/{@link #SEQ_IDLE_DORMANT} and repointing all 4 npc
 * records' opcode-13/14 payloads at them ({@code FixMadAngelIdleAnims.java}); their own referenced
 * frame archives (14558/14559, also missed by the original pass -- free in Zelus, imported 1:1)
 * came along too.
 * <p>
 * <b>Still NOT imported / still placeholder:</b> the Fallen Cathedral mapsquare and the Church pew
 * object -- a full map import is a much larger undertaking than model/seq/npc data and wasn't
 * attempted this pass. Entry is still via the admin-only {@code ::madangel} command (see
 * {@code CommandHandlerAdmin}'s "madangel" case and {@link MadAngel#createAndEnter}), reusing
 * Vorkath's crater as the instance source region, exactly as before.
 */
public final class MadAngelIds {

    private MadAngelIds() {
    }

    // ==== NPCs (imported, unchanged ids) ======================================================

    /** Fighting form, 755 hp (via data/npcs/combat/mad_angel.json), op2 "Attack". */
    public static final int NPC_MAD_ANGEL = 16305;
    /** Dormant form, op1 "Wake". */
    public static final int NPC_DORMANT = 16306;
    /** Ops-less form worn only while she rises. */
    public static final int NPC_RISING = 16307;
    /** Corpse form. */
    public static final int NPC_DEAD = 16308;

    // ==== Locs / objects (Fallen Cathedral pew) -- both imported, only 62250 in active use =====
    // 62250 ("Climb") was already part of the Cathedral furniture pass (ImportCathedralObjects.java)
    // and physically exists both at its real-world placement and inside every instance (copied in
    // by DynamicMap.build along with the rest of the mapsquare's locs). 62251 ("Exit"/"Quick-exit")
    // has NO real-world placement -- confirmed via a direct decode of both ids from the source cache
    // (DecodeExitPew.java): same model (61680, already imported), no anim, options=[Exit,
    // Quick-exit]. Imported this session via ImportExitPew.java (verify+apply against Zelus's live
    // cache; read back byte-identical) with the intent of spawning it over the climb pew's instance
    // tile, matching MadAngelEntrance.kt's own swap -- but that swap is CURRENTLY UNUSED: it
    // rendered invisible in-game (GameObject.spawn() only notifies players already in the region at
    // the moment it's called, and nobody is yet when this ran -- see MadAngel's own class javadoc
    // for the full trace). MadAngel#handlePewClimb now reuses 62250 for both directions instead.
    // The import and PEW_SHAPE/PEW_DIRECTION below are left in place, unused, in case a
    // spawn-after-teleport variant of the swap is revisited later.

    public static final int REAL_OBJ_PEW_CLIMB = 62250;
    public static final int REAL_OBJ_PEW_EXIT = 62251;
    /** Both pews' real placement -- shape 10 (CentrepieceStraight), angle 1 (North). From `findLoc`, per MadAngelEntrance.kt. */
    public static final int PEW_SHAPE = 10;
    public static final int PEW_DIRECTION = 1;

    // ==== Animations / seqs (imported) ========================================================
    // 3321/4589/4590/8543 collided with unrelated pre-existing Zelus content (different, unknown
    // animations already at those exact ids) -- remapped to the free block right after her own
    // 14429-14448 range. The imported bytes are byte-identical to source; only the ARCHIVE SLOT
    // (this sequence's own id) changed -- frameIDs reference frame-archive numbers, which are
    // unchanged, so no content rewrite was needed for the remap itself.

    /** Remapped from source id 3321 (collided). */
    public static final int SEQ_SPAWN = 14449;
    /** Remapped from source id 4589 (collided). */
    public static final int SEQ_MELEE = 14450;
    /** Remapped from source id 4590 (collided). */
    public static final int SEQ_SMITE = 14451;
    /** Remapped from source id 8543 (collided). */
    public static final int SEQ_SMITE_ENRAGED = 14452;
    /** Unchanged from source -- was already free in Zelus. */
    public static final int SEQ_BOMB = 14443;
    /** Unchanged from source -- was already free in Zelus. */
    public static final int SEQ_DEATH = 14448;

    /** human_pickuptable -- common/shared, was already present in Zelus untouched. */
    public static final int SEQ_CLIMB_REACH = 832;

    /**
     * Her idle/hover pose+loop, baked into the npc definition itself (readyAnim=walkAnim, cache
     * npc opcodes 13/14) rather than called from {@code MadAngel.java}. NOT referenced by name
     * anywhere in the combat script -- listed here only so the id is accounted for and its
     * provenance (remapped from source 4588, which collided) is on record. Fighting (16305) and
     * rising (16307) forms both use it.
     */
    public static final int SEQ_IDLE_FIGHTING = 14453;
    /** Same as {@link #SEQ_IDLE_FIGHTING} but for the dormant (16306) and dead (16308) forms; remapped from source 1991 (collided). */
    public static final int SEQ_IDLE_DORMANT = 14454;

    /**
     * The cleave sweep is a state machine, not one animation per side -- see
     * {@link MadAngel#drawSword} for how these are picked. Side 0 = left, 1 = right. All unchanged
     * from source (14429-14442 was entirely free in Zelus).
     */
    public static final int[] SEQ_SWEEP_OPEN = {14433, 14429};
    public static final int[] SEQ_SWEEP_AGAIN = {14434, 14430};
    /** Indexed by the side swung FROM (the previous side), not the new one. */
    public static final int[] SEQ_SWEEP_CROSS = {14435, 14431};
    /** The closing beat of a run -- carries the last wave, not cosmetic. */
    public static final int[] SEQ_SWEEP_RESET = {14436, 14432};
    public static final int[] SEQ_SWEEP_OPEN_ENRAGED = {14440, 14437};
    public static final int[] SEQ_SWEEP_AGAIN_ENRAGED = {14441, 14438};
    public static final int[] SEQ_SWEEP_CROSS_ENRAGED = {14442, 14439};

    // ==== Spotanims / graphics (imported, unchanged ids) ======================================
    // Each one's internally-referenced model id was imported too (see the model block below);
    // 4013 is the one exception whose bytes were patched (its model reference was remapped).

    public static final int GFX_SPAWN = 4010;
    public static final int GFX_SMITE_CHARGE = 4011;
    public static final int GFX_SMITE_CHARGE_ENRAGED = 4012;
    public static final int GFX_SMITE_HIT = 4013;
    public static final int GFX_BOMB_THROW = 4014;
    public static final int GFX_BOMB_TRAVEL = 4015;
    // 4016 exists in the cache but a live capture never sends it -- the only impact vfx used is 4017.
    public static final int GFX_BOMB_HIT = 4017;
    public static final int GFX_BOMB_REFLECT = 4017;
    /** gargboss_debris_shadow_150 -- was ALREADY present in Zelus byte-identical; not re-imported. */
    public static final int GFX_BOMB_SHADOW = 1448;
    /** zebak_roar_wave_dust -- same as above, already present byte-identical. */
    public static final int GFX_CLEAVE_WAVE = 2184;

    // ==== Models (imported; informational -- nothing in MadAngel.java references these ids
    // ==== directly, they're reached indirectly via the npc/spotanim defs above) ================
    // 61845 (dormant/rising/dead), 61847 (fighting), 61786 (chathead), 61842 (spawn vfx),
    // 60639 (smite charge vfx), 60632 (bomb vfx) -- all unchanged from source.
    // 48289 (smite-hit vfx model) collided with unrelated existing content -> remapped to 61848,
    // and spotanim 4013 (GFX_SMITE_HIT)'s bytes were patched to reference 61848 instead of 48289.

    // ==== Items ================================================================================
    // Ordinary, long-standing OSRS items -- unrelated to this session's model/seq import, left as
    // originally ported. Still worth a cache spot-check, but not a blocker for combat mechanics.
    public static final int ITEM_GRANITE_DUST = 21726;
    public static final int ITEM_RUNE_KITESHIELD = 1201;
    public static final int ITEM_RUNE_FULL_HELM = 1163;
    public static final int ITEM_ADAMANT_LONGSWORD = 1301;
    public static final int ITEM_DRAGON_MED_HELM = 1149;
    public static final int ITEM_DRAGON_BATTLEAXE = 1377;
    public static final int ITEM_STEEL_CANNONBALL = 2;
    public static final int ITEM_LAW_RUNE = 563;
    public static final int ITEM_NATURE_RUNE = 561;
    public static final int ITEM_DEATH_RUNE = 560;
    public static final int ITEM_AIR_RUNE = 556;
    public static final int ITEM_PRAYER_POTION_4 = 2434;
    public static final int ITEM_SUPER_COMBAT_POTION_3 = 12697;
    public static final int ITEM_SHARK = 385;
    public static final int ITEM_PRAYER_POTION_2 = 141;
    public static final int ITEM_SUPER_COMBAT_POTION_1 = 12701;
    public static final int ITEM_COINS = 995;
    public static final int ITEM_RAW_MONKFISH = 7944;
    public static final int ITEM_EMERALD = 1605;
    public static final int ITEM_SAPPHIRE = 1607;
    public static final int ITEM_PAPYRUS = 970;
    public static final int ITEM_CLUE_SCROLL_HARD = 2722;
    public static final int ITEM_YELLOWFIN = 32328;
    public static final int ITEM_CUPRONICKEL_BAR = 32892;

    // TODO(cache): these 5 items are still brand-new and NOT imported -- the drop table
    // (data/npcs/drops/newDrops/16305.json) still needs them created before real drops work.
    public static final int ITEM_SUNSTONE_CRYSTAL = 34032;
    public static final int ITEM_ARDEAGLAIS_TELEPORT = 34033;
    public static final int ITEM_HALLOWFELL = 34027;
    public static final int ITEM_JAR_OF_LIGHT = 34030;
    public static final int ITEM_AGGY = 34042;

    // ==== World (imported 2026-09-11) ==========================================================
    // The real Fallen Cathedral mapsquare -- terrain (m39_34) and locs (l39_34), archives 10018/
    // 10019 in Zelus's MAPS index -- was imported this pass: extracted from the source's modern
    // unnamed-group layout (group id (39<<8)|34, sub-file 0 = terrain, sub-file 1 = locs -- see
    // GameMapDecoder.kt) and re-containered into Zelus's own classic named-archive convention
    // ("m{x}_{z}"/"l{x}_{z}", looked up by name hash in Region.java). The underlying tile-opcode
    // and loc-list-smart-encoding formats are IDENTICAL between the two caches (cross-checked
    // rsmod's MapTileDecoder.kt/MapLocListDecoder.kt against Region.java's own decode loop), so
    // this was a straight split-and-recontainer, no re-encoding -- verified byte-for-byte via
    // read-back decode (see {@code ImportCathedralMap.java} under {@code .dev/cache-restore-tool}).
    // No XTEA keys were needed: Zelus's own {@code Region.NO_KEYS = true} means the whole server
    // cache reads under one shared default key, source region encryption is irrelevant here.
    // DynamicMap.build COPIES tiles into a freshly allocated, fully isolated region per player, so
    // entering never touches this source region itself. Zero collision risk (verified free before
    // writing). Region id and anchor coordinates below are confirmed against
    // RS-Realm-Server-Package's own MadAngelEntrance.kt (CATHEDRAL_TEMPLATE = copyAllLevels(312,
    // 272) = mapsquare (39,34); ARENA_ANCHOR = CoordGrid(2532, 2215, 0)), not guessed.
    // <p>
    // RESOLVED (2026-09-13): the four adjacent mapsquares -- (38,34), (40,34), (39,33), (39,35) --
    // are now imported too (ImportWyrmscraigNeighborMaps.java, verified present via
    // CheckNeighborMaps.java's read-back), using the exact same technique as the cathedral's own
    // import above. Each source group actually has 5 sub-files, not the 2 (terrain/locs) this
    // engine reads -- confirmed the cathedral's OWN source group also has 5 (sub-files 2/3/4 are
    // 228/2/1 bytes, IDENTICAL sizes in an unrelated neighbor group, i.e. boilerplate metadata this
    // engine's Region.java has no mechanism to read anyway, not something the already-working
    // cathedral import silently lost). Destination archive ids (20001-20008) are a fresh block, not
    // a same/adjacent-to-source-id scheme, because (39,33)=10017 and (39,35)=10019 are numerically
    // adjacent to the cathedral's OWN 10018/10019 -- a naive "+1" offset would have collided with
    // l39_34 immediately. Archive ids don't matter for lookup regardless (Region.java resolves
    // purely by NAME HASH of "m{x}_{z}"/"l{x}_{z}"), so this needed no changes to the SOURCE data
    // itself, and the real-world pew tile is no longer an isolated island once a player walks far
    // enough to load these squares.
    // <p>
    // The Church pew object is wired -- MadAngel#register hangs ONE handler off 62250's real
    // "Climb" option, calling createAndEnter. Exiting uses the REAL "Exit"/"Quick-exit" pew (62251,
    // imported in the Locs/objects block above) -- see MadAngel's own class javadoc for how the
    // client-visibility bug from the first attempt at this swap was root-caused and fixed.

    /** The Fallen Cathedral's real mapsquare -- (39 &lt;&lt; 8) | 34. */
    public static final int CATHEDRAL_REGION_ID = 10018;
    /** Her real spawn tile inside that region -- the fight's own SW anchor, per source. */
    public static final int ARENA_ANCHOR_X = 2532;
    public static final int ARENA_ANCHOR_Y = 2215;

    /** The real Church pew's tile, from the source. Now wired -- see MadAngel#register. */
    public static final int REAL_PEW_X = 2538;
    public static final int REAL_PEW_Y = 2215;
    public static final int CLIMB_THROUGH_TILES = 2;

    /**
     * History: first `REAL_PEW_X-2` (2536) -- checked against this file's own measured hall bounds
     * (interior floor x[2530,2540] y[2211,2220], east wall at x=2541, the one the pew sits against)
     * and that landed WEST into the hall interior, not east through the door -- the opposite of
     * "outside". Reverted to a straight alias of the pew's own tile (2538, matching
     * {@code MadAngelEntrance.kt}'s own {@code PEW_OUTSIDE} exactly), which was correct relative to
     * source but, per direct user observation in-game, dropped the player ON the blocking
     * pew/doorway tile itself. Now 2540 -- still inside the previously-measured interior range,
     * technically, but the specific tile identified visually as clear standing ground on the
     * approach, immediately at the threshold facing the pew.
     */
    public static final int PEW_OUTSIDE_X = 2540;
    public static final int PEW_OUTSIDE_Y = REAL_PEW_Y;
}
