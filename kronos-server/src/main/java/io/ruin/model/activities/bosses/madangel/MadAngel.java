package io.ruin.model.activities.bosses.madangel;

import io.ruin.Server;
import io.ruin.api.utils.Random;
import io.ruin.cache.NPCType;
import io.ruin.model.World;
import io.ruin.model.combat.AttackStyle;
import io.ruin.model.combat.Hit;
import io.ruin.model.entity.npc.NPC;
import io.ruin.model.entity.npc.NPCAction;
import io.ruin.model.entity.npc.NPCCombat;
import io.ruin.model.entity.player.Player;
import io.ruin.model.entity.shared.listeners.HitListener;
import io.ruin.model.map.Direction;
import io.ruin.model.map.Position;
import io.ruin.model.map.Projectile;
import io.ruin.model.map.Tile;
import io.ruin.model.map.dynamic.DynamicMap;
import io.ruin.model.map.object.actions.ObjectAction;
import io.ruin.model.map.route.routes.ProjectileRoute;
import io.ruin.model.skills.prayer.Prayer;
import lombok.extern.slf4j.Slf4j;

import static io.ruin.model.activities.bosses.madangel.MadAngelIds.*;

/**
 * Mad Angel -- the boss of the Fallen Cathedral in Wyrmscraig, fought in a private
 * {@link DynamicMap} instance. Ported from RS-Realm-Server-Package's {@code MadAngel.kt}
 * (rsmod engine), which itself was modelled on the wiki's Strategies page plus one packet capture
 * of a real kill -- see that project's {@code CAPTURE.md} for exactly which numbers below are
 * measured versus chosen.
 * <p>
 * <b>The client crash, root-caused and fixed (2026-09-12):</b> every attempt at her bomb special
 * ("Exploding Light") crashed the client within about a second, always, regardless of the attack
 * loop's architecture -- an extensive in-game bisection ruled out {@code npc.lock()}, {@code
 * npc.addEvent} scheduling, every VFX call, {@code Projectile.send}'s coordinate-targeted overload,
 * {@code World.sendGraphics} to a bare tile, and {@code scatterTile()}'s collision search, one at a
 * time, before the real cause turned up in the CACHE, not the code: her bomb spotanims (4014
 * {@code GFX_BOMB_THROW}, 4015 {@code GFX_BOMB_TRAVEL}, 4017 {@code GFX_BOMB_HIT}/{@code
 * GFX_BOMB_REFLECT}) each carry an internal animation reference (spotanim opcode 2, decoded via
 * {@code DumpSpotAnimV2.java} in {@code .dev/cache-restore-tool}) pointing at sequences
 * 14444-14447 -- which the original import simply never copied, because it only ever imported
 * sequences this class calls directly via {@code animate()}, not ones referenced from INSIDE a
 * spotanim's own definition. The client crashes instantly trying to render a spotanim whose
 * required sub-animation doesn't exist. Confirmed missing via {@code CheckSeqOpcode.java} against
 * Zelus's cache (NOT FOUND) and present, byte-identical across all 4 of the source project's own
 * cache copies, via the same tool (see {@code ImportMissingBombSeqs.java}, which copied them in).
 * Smite and cleave never hit this because every ONE of their own spotanims' internal animation
 * references happens to already exist in Zelus's cache (confirmed the same way) -- not because
 * they are architecturally different from bomb in any way that matters here.
 * <p>
 * <b>Architecture, matched to the source:</b> the source's rsmod engine runs an always-ticking
 * "watchdog" (its {@code onAiTimer}) alongside the normal ap-interaction attack cycle, and every
 * special resolves off plain tick-number comparisons ({@code bombLandsAt}, {@code smiteLandsAt},
 * {@code cleaveLandsAt}) polled from that watchdog every tick -- there is no engine-level "lock"
 * anywhere in it. An earlier revision of this port used {@code npc.lock()} + {@code npc.addEvent}
 * to fake the same "she's mid-special, don't interrupt her" effect instead. That turned out NOT to
 * be the cause of the crash above (both architectures crash identically against the missing
 * sequences), but it was still a needless divergence from the source worth fixing on its own
 * merits: this revision removes {@code npc.lock()}/{@code unlock()} entirely and instead mirrors
 * the source directly. Zelus's own {@link NPCCombat#process()} already runs unconditionally every
 * tick regardless of lock state (confirmed by reading {@code NPC.java}'s own tick method), so it
 * plays the same role as the source's watchdog -- polling {@code smiteLandsAt}/{@code bombLandsAt
 * (via bombTile)}/{@code cleaveLandsAt} and resolving whichever is due. {@link #attack()} keeps its
 * normal cooldown-gated role (the framework's {@code attack0()} already won't call it again until
 * {@code info.attack_ticks} has elapsed), and simply declines to start anything new
 * ({@code return false}, consuming no cooldown) while {@link #isBusy()} -- exactly the source's
 * {@code if (state.busy) { actionDelay = ...; return }} guard, just split across the two hooks
 * Zelus actually offers instead of the source's single ap-tick.
 * <p>
 * <b>What this port changes versus the source, deliberately:</b>
 * <ul>
 *     <li><b>Accuracy delegation.</b> The source hand-rolls accuracy for her basic attack (rsmod
 *     has no other option) but delegates telegraphed specials to pure position/prayer checks. Here,
 *     basic attacks are simply handed to Zelus's own {@link Hit} accuracy roll (as every other
 *     boss on this server already does), while telegraphed specials call {@code
 *     Hit#ignoreDefence()} to keep them unavoidable-except-by-mechanic, matching the source's
 *     intent without re-deriving Zelus's combat formulas.</li>
 *     <li><b>Guaranteed-hit rewards.</b> The source computes the reward off a dedicated
 *     crush-melee-max-hit formula. This port reads it off the reward-earning hit's own already
 *     -rolled {@code maxDamage} instead (see {@link #onIncomingHitPre}) -- equivalent in spirit,
 *     cheaper, and doesn't require re-deriving Zelus's max-hit formulas for a style the player may
 *     not even be using.</li>
 *     <li><b>Reachability checks.</b> The source uses a proper line-of-walk test so the bomb
 *     shadow/detonation/cleave wave never paint through a wall. This port uses
 *     {@code Tile#clipping == 0}, the same primitive {@code NPCCombat} itself already uses for
 *     drop-position clipping. It is a per-tile check, not a line test, so (unlike the source) it
 *     can paint through a single-tile-thin wall -- acceptable given no line-of-walk utility exists
 *     elsewhere in this codebase; flagged here rather than silently accepted.</li>
 *     <li><b>Loot.</b> The source rolls a hand-built always/main/tertiary wiki table with its own
 *     probabilities. This port instead feeds {@link #onDeath} into
 *     {@code NPCCombat#handleNewDrop}, i.e. the SAME generic {@code newDrops/16305.json}-driven
 *     system every other boss on Zelus uses (see that file for the converted rates). One
 *     consequence worth knowing: unlike the source's table (which has a real chance of rolling
 *     nothing beyond the always-drops), Zelus's generic roller always awards at least one
 *     "closest-to-passing" item from the main pool per roll -- this makes Mad Angel slightly more
 *     generous than a literal wiki-rate translation, consistently with how every other boss here
 *     already behaves.</li>
 * </ul>
 * <p>
 * <b>Cache status (as of the 2026-09-11 import):</b> her npc definitions, models, skeleton, frame
 * archives, spotanims, idle/walk animations, AND the Fallen Cathedral mapsquare (terrain + locs)
 * are imported into Zelus's cache -- see {@link MadAngelIds}'s javadoc for exactly which ids were
 * remapped due to collisions with unrelated pre-existing content.
 * <p>
 * <b>The pew, entrance/exit flow (wired 2026-09-13, revised same day):</b> source's own
 * {@code CATHEDRAL_TEMPLATE} copies exactly one 8x8-zone mapsquare, the same single mapsquare this
 * port already builds -- so unlike an earlier version of this class claimed, that was never
 * actually a gap. The pew itself, though, genuinely was missing: entry used to be admin-only
 * ({@code ::madangel} building the instance directly).
 * <p>
 * <b>The exit pew: two swap attempts tried and abandoned, now a permanent single object.</b> Object
 * 62251 (the real "Exit"/"Quick-exit" pew) was imported (see {@code ImportExitPew.java}, verified
 * byte-identical on read-back -- the import itself is fine and still in the cache) with the intent
 * of swapping it in over 62250 inside the instance, mirroring {@code MadAngelEntrance.kt}'s own loc
 * swap exactly. Attempt 1: {@code GameObject.remove()}/{@code spawn()} done BEFORE the player's own
 * teleport into the newly-built map -- rendered invisible. Root cause, found by reading
 * {@code GameObject.java} directly: {@code spawn()}'s own source is
 * {@code for (Player player : tile.region.players) send(player);} -- it only notifies players
 * ALREADY in that region at the moment it runs, and nobody was yet. Attempt 2: moved the same swap
 * to AFTER the teleport, plus explicit {@code .send(player)} calls on both the removal and the
 * replacement (confirmed {@code GameObject.send(Player)} is a real public method that targets one
 * specific player directly) -- STILL rendered invisible in-game, meaning the viewer-timing theory,
 * while plausible and grounded in real code, was not the complete explanation, and there is no
 * available tooling here to inspect the actual packet stream to find the rest of it. Given two
 * verified-plausible fixes both failed against a mechanism (dynamic loc replacement inside a
 * player's own newly-built instance) that has no other working precedent anywhere else in this
 * codebase to compare against, continuing to iterate on it risks a third invisible-pew regression --
 * so it's abandoned outright, per explicit instruction, rather than attempted a third time.
 * <p>
 * <b>Current, final approach:</b> 62250 is never removed or replaced -- one permanent object,
 * real-world placement and every instance's copy alike. {@link #register} wires ONE handler to its
 * "Climb" option; {@link #handlePewClimb} branches enter vs. exit on the player's own
 * {@code isInstanced} state. The menu says "Climb Church pew" on both sides, not the authentic
 * "Exit"/"Quick-exit" -- accepted explicitly as the right trade-off: a working exit that says the
 * wrong thing beats a correctly-worded one that doesn't render and traps the player. The 62251
 * import is left in the cache, unused.
 * <p>
 * The 5 brand-new drop-table items are still un-created (see {@link MadAngelIds}'s own TODO). The
 * four mapsquares surrounding the Cathedral's own are now imported too (see {@link MadAngelIds}'s
 * javadoc) -- the real-world pew tile is no longer an isolated island.
 */
@Slf4j
public class MadAngel extends NPCCombat {

    // =========================================================================================
    // Registration / bootstrap
    // =========================================================================================

    /**
     * NPC_MAD_ANGEL is her real, now-imported cache entry, so this registers cleanly.
     * {@code NPCType.registerCombat} itself already no-ops safely if its npc id doesn't exist, so
     * this needs no defensive guard either way.
     * <p>
     * The dormant form's own real click-to-start flow IS wired up here (her cache data already
     * carries it -- op1 "Wake" on {@link MadAngelIds#NPC_DORMANT}, confirmed via cache decode):
     * {@link #createAndEnter} does not auto-wake her the instant a player arrives, it just drops
     * them next to a dormant, waiting angel, matching the real fight's own pacing.
     * <p>
     * The Climb pew (62250) is registered globally, ONE handler for every copy of it everywhere --
     * its real-world placement AND every instance's copy alike, since it is now NEVER swapped or
     * removed (see the class javadoc: two separate attempts at swapping in the real "Exit" pew,
     * 62251, both rendered invisible in-game despite a targeted fix between them, and a third
     * attempt isn't worth the risk of trapping a player again). {@link #handlePewClimb} decides
     * enter vs. exit by the clicking player's own {@code isInstanced} state. The menu reads "Climb
     * Church pew" on both sides -- not authentic to source's "Exit"/"Quick-exit" text -- a
     * deliberate, explicit trade-off: a working exit that says the wrong thing beats a correctly-
     * worded one that doesn't render.
     */
    public static void register() {
        NPCType.registerCombat(MadAngel.class, NPC_MAD_ANGEL);
        NPCAction.register(NPC_DORMANT, "Wake", MadAngel::wakeAngel);
        ObjectAction.register(REAL_OBJ_PEW_CLIMB, "Climb", (player, obj) -> handlePewClimb(player));
    }

    /**
     * The one "Climb" handler for pew 62250, wherever it's clicked -- branches on the PLAYER's own
     * {@code isInstanced} state, not the object's coordinate (a real-world tile and an
     * instance-space tile for "at the pew" aren't comparable numbers anyway).
     */
    private static void handlePewClimb(Player player) {
        if (player.isInstanced) {
            if (player.currentDynamicMap == null) {
                // Same stale-flag self-heal as createAndEnter's own guard -- see its javadoc.
                player.isInstanced = false;
                player.inDynamicMap = false;
                return;
            }
            exitCathedral(player, player.currentDynamicMap);
        } else {
            createAndEnter(player);
        }
    }

    // =========================================================================================
    // Entrance (mirrors Vorkath's createAndEnter / exit pattern)
    // =========================================================================================

    /**
     * Builds a private instance from {@link MadAngelIds#CATHEDRAL_REGION_ID} and teleports the
     * caller in next to her dormant form. She does not wake automatically on arrival; the player
     * has to click "Wake" on her (see {@link #register} and {@link #wakeAngel}).
     * <p>
     * <b>Any departure from the instance ends the encounter, unconditionally.</b> The source
     * ({@code MadAngelInstances.releaseIfDeparted}/{@code releaseDepartedOwners}) does the same --
     * dying, teleporting away, logging out, or otherwise leaving without using the exit pew all
     * just tear the region down, with no attempt to drag the player back into the fight. An
     * earlier revision of this port instead force-teleported the player BACK into the arena
     * whenever they left it (reachable via ordinary play: this import's incomplete arena walls --
     * see the class javadoc -- let the bomb's scatter mechanic push a kiting player straight
     * through where a wall should be). That diverged from the source on purpose-built behaviour it
     * doesn't have, so it is removed outright rather than merely disabled -- NOT because it caused
     * the client crash (it didn't; see the class javadoc for the real cause, a missing-sequence
     * cache gap). Given the incomplete arena, treating an exit as "return them to real Gielinor"
     * (as logout already did) is also the only SAFE option until the arena's walls are actually
     * imported -- leaving them wherever they wandered risks stranding them in genuinely unimported
     * void space.
     */
    public static void createAndEnter(Player player) {
        if (player.isInstanced) {
            // Self-heal an orphaned flag: isInstanced can only mean anything while
            // currentDynamicMap actually tracks a live instance for this player. A null map here
            // means they left some OTHER way than the tracked exits below (a quick-travel/admin
            // teleport straight out, most likely, which never runs through this instance's own
            // onExit listener) -- the flag is stale, not a real "still inside" state, so clear it
            // and let them back in rather than leaving them stuck until a server restart.
            if (player.currentDynamicMap == null) {
                player.isInstanced = false;
                player.inDynamicMap = false;
            } else {
                player.sendMessage("You are already inside an instance.");
                return;
            }
        }

        DynamicMap map;
        try {
            // BUG FIXED 2026-09-13: maxHeight was 1 (planes 0-1 only). Decoded straight from the
            // source cache's own loc list (.dev/cache-restore-tool, DecodeCathedralHeights.java):
            // 64 real objects near the anchor sit on plane 2 -- upper wall/pillar/ceiling pieces --
            // and were never being copied into the instance at all, which is exactly what
            // "half-rendered rock walls, cutoff edges" was: not a black-void-radius problem (the
            // source's own CATHEDRAL_TEMPLATE copies this same single 64x64 mapsquare, confirmed in
            // MadAngelEntrance.kt's own comment -- ours already matches), but a missing PLANE.
            // 0 locs were found on plane 3, but building it too is free if it's empty and matches
            // how other full-height Zelus rooms (Tempoross, NightmareEvent) build all 4 planes.
            map = new DynamicMap().build(CATHEDRAL_REGION_ID, 3);
        } catch (DynamicMap.DynamicMapBuildException e) {
            player.sendMessage("Unable to create the instance right now. Try again in a moment.");
            return;
        }

        NPC dormant = new NPC(NPC_DORMANT)
                .spawn(map.convertX(ARENA_ANCHOR_X), map.convertY(ARENA_ANCHOR_Y), 0, Direction.EAST, 0);
        map.addNpc(dormant);

        // Land the player OVER the pew: CLIMB_THROUGH_TILES west of wherever they stood to click
        // it (not a fixed anchor), matching MadAngelEntrance.kt's own
        // `region.copyOf(returnTile.translate(-CLIMB_THROUGH_TILES, 0))`. Landing on the tile they
        // left would read as the click having done nothing. Also no longer drops them next to the
        // (dormant, non-hostile) angel -- they now arrive at the barrier and have to walk in.
        int enterX = map.convertX(player.getPosition().getX()) - CLIMB_THROUGH_TILES;
        int enterY = map.convertY(player.getPosition().getY());

        // No lock() here, deliberately: this is a brief 2-tick hurdle, not a cinematic, and there is
        // no reason to freeze the player's whole movement pipeline for it.
        player.animate(SEQ_CLIMB_REACH);
        player.startEvent(event -> {
            event.delay(2);
            player.isInstanced = true;
            player.currentDynamicMap = map;
            player.inDynamicMap = true;
            player.getMovement().teleport(enterX, enterY, 0);

            map.assignListener(player).onExit((p, logout) -> {
                // BUG FIXED 2026-09-13: this used to call teleport() unconditionally, regardless of
                // `logout`. MapListener.process() runs every tick for every player and fires this
                // the instant `lastRegion` no longer matches the instance -- which includes the tick
                // right after ANY teleport away (::home, a teleport spell, dying), not just logout.
                // Unconditionally re-teleporting here silently overwrote wherever that teleport had
                // just sent the player, snapping them back to the pew every time -- the "infinite
                // loop" symptom. Vorkath's own onExit (Vorkath.java) already gets this right: it
                // only forces a teleport `if (logout)`, since a disconnected client has no pending
                // destination to protect and needs somewhere safe for their next login. Any other
                // exit reason already has a real destination in flight (their own teleport, or
                // exitCathedral's own explicit one) -- cleanup only, no override.
                if (logout) {
                    p.getMovement().teleport(PEW_OUTSIDE_X, PEW_OUTSIDE_Y, 0);
                }
                p.currentDynamicMap = null;
                p.inDynamicMap = false;
                p.isInstanced = false;
                map.destroy();
            });
        });
    }

    /**
     * The same pew click, from inside the instance (see {@link #handlePewClimb}). Restores the
     * player to the fixed {@link MadAngelIds#PEW_OUTSIDE_X}/{@link MadAngelIds#PEW_OUTSIDE_Y} spot
     * -- not stored per-player, since it's always the same tile regardless of where they originally
     * climbed in from.
     */
    private static void exitCathedral(Player player, DynamicMap map) {
        if (player.currentDynamicMap != map) {
            return;
        }
        player.animate(SEQ_CLIMB_REACH);
        player.startEvent(event -> {
            event.delay(2);
            player.getMovement().teleport(PEW_OUTSIDE_X, PEW_OUTSIDE_Y, 0);
            player.currentDynamicMap = null;
            player.inDynamicMap = false;
            player.isInstanced = false;
            map.destroy();
        });
    }

    /**
     * Wake -> 3-tick rise -> replace with the fighting form -> fight.
     * <p>
     * Replaced rather than transmogged: a transmog keeps the dormant type's stats/combat handler,
     * so the fighting form's 755 hp and this class's {@code NPCCombat} registration would never
     * take effect. The opening delay before her first swing is handled entirely inside
     * {@link #attack()} now (see {@link #started}/{@link #nextDecisionTick}), not by locking her
     * here -- see the class javadoc for why {@code npc.lock()} is gone from this whole class.
     */
    private static void wakeAngel(Player player, NPC dormant) {
        if (dormant.isLocked()) {
            return;
        }
        dormant.lock();
        player.animate(SEQ_CLIMB_REACH);
        dormant.animate(SEQ_SPAWN);
        dormant.graphics(GFX_SPAWN);
        dormant.transform(NPC_RISING);
        player.sendMessage("The angel stirs...");

        Position coords = dormant.getPosition().copy();
        DynamicMap map = player.currentDynamicMap;

        dormant.addEvent(event -> {
            event.delay(SPAWN_RISE_TICKS);
            dormant.remove();

            NPC angel = new NPC(NPC_MAD_ANGEL).spawn(coords.getX(), coords.getY(), coords.getZ(), Direction.WEST, 0);
            if (map != null) {
                map.addNpc(angel);
            }
            // The real hall floor, measured (not guessed) from the source cache's own loc list for
            // mapsquare 39,34 -- decoded with .dev/cache-restore-tool/src/DecodeCathedralWalls.java,
            // which reads the exact same wall-piece bytes ImportCathedralMap.java copied into Zelus
            // byte-for-byte. Two earlier guesses (a tight 8-tile circle, then a loose rectangle -12
            // west/+15 north-south) were both wrong for the same reason: neither was checked against
            // where the real walls actually are. The decode found the south wall at y=2210, north
            // wall at y=2221, and east wall (behind the entrance pews) at x=2541, all centred on her
            // spawn anchor (2532,2215) -- giving a real interior floor of x[2530,2540] y[2211,2220].
            // (There is also a small west throne alcove past x=2528 that this box deliberately
            // excludes -- her spawn and every attack tile are inside the main hall, she has no
            // reason to ever go there, and the user asked for her confined to "the main central
            // cathedral hall" specifically.) Beyond this box, in every direction, is NOT this room --
            // it's whatever real, unrelated map content DynamicMap stitches in around a single
            // imported mapsquare for rendering padding, which is what "the small back storage room"
            // in the 2026-09-13 bug report actually was: confirmed by the fact that no such room
            // exists anywhere in the source's own loc data near this hall. Used both as the leash
            // (below, and in {@link #process()}) and to pre-empt every movement step in {@link
            // #follow()} so she can no longer reach that boundary in the first place, not just get
            // caught after crossing it.
            //
            // BUG FIXED 2026-09-13: those measured numbers are REAL-WORLD tile coordinates -- they
            // must go through map.convertX/convertY, exactly like this same method's spawn call
            // three lines up (map.convertX(ARENA_ANCHOR_X)), before they mean anything inside this
            // DynamicMap instance. The first version of this fix skipped that conversion and
            // compared the player's actual (translated, instance-space) position against raw
            // real-world numbers nowhere near it -- true on literally every tick, which is why the
            // leash fired the instant she woke, not because the leash concept itself is wrong.
            io.ruin.model.map.Bounds bounds = new io.ruin.model.map.Bounds(
                    map.convertX(2530), map.convertY(2211),
                    map.convertX(2540), map.convertY(2220),
                    angel.getPosition().getZ());
            angel.attackBounds = bounds;
            ((MadAngel) angel.getCombat()).arenaBounds = bounds;
            angel.getCombat().setTarget(player);
            // The source's ap-interaction re-faces her at her target every tick from the moment she
            // wakes; Zelus's own generic NPCCombat#basicAttack() helper does the same
            // (faceTarget() -> npc.face(target)) but this class overrides basicAttack() with its
            // own version that never calls it (see basicAttack() below), so without this she just
            // sits at her spawn Direction until something else happens to turn her -- confirmed
            // in-game 2026-09-12 ("she starts fighting facing the wrong way").
            angel.face(player);
        });
    }

    // =========================================================================================
    // Per-fight state (one MadAngel instance per spawned npc -- no shared/static state needed)
    // =========================================================================================

    private boolean enraged = false;
    private int attackCount = 0;
    /** Cycles bomb -> cleave -> smite, matching the order a capture observed after the opening attacks. */
    private int nextSpecial = 0;
    /** Specials still owed back-to-back; the enrage opener queues all three. */
    private int burstRemaining = 0;

    /** Set on the first {@link #attack()} call after waking; see {@link #nextDecisionTick}. */
    private boolean started = false;
    /**
     * The tick her next attack-loop decision may run. Set once, on the first {@link #attack()}
     * call, to {@code now + OPENING_DELAY} so she doesn't swing on the tick she rose -- the
     * source's {@code state.started}/{@code actionDelay} pair, done without a lock.
     */
    private long nextDecisionTick = -1;

    /** Whether Protect from Magic was active the tick BEFORE a smite strike resolves -- see {@link #resolveSmiteStrike}. */
    private boolean prayedLastTick = false;
    /** True only while the bounced-bomb hit against her is being queued, so the reward hooks ignore it. */
    private boolean reflecting = false;

    /** Tick a charging smite lands, or -1. Polled from {@link #process()}. */
    private long smiteLandsAt = -1;
    /** Enraged follow-up strikes still owed after the current one resolves. */
    private int smiteFollowUps = 0;

    /** The airborne bomb's fixed destination tile, or null. Polled from {@link #process()}. */
    private Position bombTile;
    /** Tick the airborne bomb lands, meaningful only while {@link #bombTile} is non-null. */
    private long bombLandsAt = -1;
    private int bombThrowsLeft;

    /** Tick the current cleave swing's wave lands, or -1. Polled from {@link #process()}. */
    private long cleaveLandsAt = -1;
    private CleaveZone cleaveSword = CleaveZone.RIGHT;
    private CleaveZone cleaveMarked;
    private int cleaveFacingX, cleaveFacingZ;
    private int cleaveSwingsLeft;

    /** Set when the player blocks a smite (flicked prayer) or dodges a cleave to a side. */
    private boolean guaranteedMaxHit = false;
    private boolean guaranteedStrongHit = false;

    private enum CleaveZone {LEFT, RIGHT, FRONT, BEHIND}

    /**
     * The room's real shape, set once in {@link #wakeAngel}. Tight toward the ~6-tile-distant real
     * doorway, generous everywhere else -- see {@link #process()} for why a uniform circle doesn't
     * fit a rectangular hall with an asymmetric exit.
     */
    private io.ruin.model.map.Bounds arenaBounds;

    /** True while any multi-tick special is still running; she starts nothing else until it is done. */
    private boolean isBusy() {
        return bombTile != null || smiteLandsAt >= 0 || cleaveLandsAt >= 0;
    }

    @Override
    public void init() {
        // NPC_MAD_ANGEL (16305) is now her real, imported cache entry -- her real stats load
        // automatically from data/npcs/combat/mad_angel.json via the normal npc_combat pipeline,
        // same as every other boss.

        npc.hitListener = new HitListener()
                .preDefend(this::onIncomingHitPre)
                .postDefend(this::onIncomingHitPost)
                .postDamage(this::checkEnrage);

        npc.deathEndListener = (entity, killer, killHit) -> onDeath(killer == null ? null : killer.player);
    }

    @Override
    public boolean isAggressive() {
        // She never auto-aggros; wakeAngel() assigns her target directly the moment she wakes.
        return false;
    }

    /**
     * The always-ticking half of the fight -- runs every tick regardless of lock state (confirmed
     * against {@code NPC.java}'s own tick method: {@code combat.process()} is called unconditionally,
     * before the {@code npc.isLocked()}-gated {@code follow0()}/{@code attack0()}), exactly the role
     * the source's watchdog timer plays. Every telegraphed special resolves off a plain tick-number
     * comparison here, independent of {@link #attack()}'s own cooldown -- see the class javadoc.
     */
    @Override
    public void process() {
        if (target == null || target.player == null) {
            return;
        }
        Player t = target.player;

        // No player leash. Deleted 2026-09-13 per explicit instruction: a player in a private
        // instance must never be auto-teleported out mid-fight for wandering, full stop -- not
        // replaced with a wider check, not "driven every tick" some other way.

        // Zero roaming, unconditionally, every tick, regardless of what put a step in her queue --
        // not just "don't add new steps" (follow() already does that) but actively erase anything
        // that's there. This is the hard guarantee: whatever she's carrying in her movement buffer,
        // gone before the world tick can act on it.
        npc.resetSteps();

        long now = Server.currentTick();

        if (bombTile != null && now >= bombLandsAt) {
            resolveBomb(t, bombTile);
        }
        if (smiteLandsAt >= 0 && now >= smiteLandsAt) {
            resolveSmiteStrike(t);
        }
        if (cleaveLandsAt >= 0 && now >= cleaveLandsAt) {
            resolveCleave(t);
        }

        // Sampled AFTER any resolution above, so a same-tick resolveSmiteStrike still reads the
        // PREVIOUS tick's value when deciding held-vs-flicked -- matching the source exactly.
        prayedLastTick = t.getPrayer().isActive(Prayer.PROTECT_FROM_MAGIC);
    }

    /**
     * DELIBERATE DEVIATION from source (2026-09-13): she no longer walks AT ALL, ever. The source's
     * {@code chaseTarget} does step her toward the target every tick she isn't busy, and two earlier
     * attempts here tried to port that faithfully while fencing her into the real hall -- first a
     * circular leash, then a measured rectangular one with a pre-emptive per-step clamp in this very
     * method (checking each queued step against the hall's real, cache-measured bounds before the
     * tick could apply it). Both were still observed walking her into the same unrelated back room
     * behind the entrance pews. Rather than chase a third theory for why a provably-correct-looking
     * clamp wasn't holding, this trades the chase for a guarantee: a boss that never issues a single
     * movement step cannot path anywhere, into a back room or otherwise, no matter what this room's
     * collision turns out to be doing. {@link #basicAttack} already deals magic damage at range when
     * not adjacent (also straight from source, which has the same non-mover fallback baked into her
     * basic attack for exactly this reason: "she never moves and cannot close the gap herself"), so
     * she remains a real threat instead of a target dummy -- just one the player closes the distance
     * on, rather than the reverse. She still turns to track the player from wherever she's standing.
     */
    @Override
    public void follow() {
        if (isBusy()) {
            return;
        }
        if (target != null && target.player != null) {
            npc.face(target);
        }
    }

    // =========================================================================================
    // Main attack loop
    // =========================================================================================

    @Override
    public boolean attack() {
        if (target == null || target.player == null) {
            return false;
        }
        if (!withinDistance(MAX_ENGAGE_RANGE)) {
            return false;
        }
        Player t = target.player;

        // A special in progress owns her until process() resolves it; decline without consuming
        // the attack cooldown so this is retried again next tick.
        if (isBusy()) {
            return false;
        }

        // First tick after aggro: arm the opening delay so she doesn't swing on the tick she rose.
        if (!started) {
            started = true;
            nextDecisionTick = Server.currentTick() + OPENING_DELAY;
            return false;
        }
        if (Server.currentTick() < nextDecisionTick) {
            return false;
        }

        if (burstRemaining > 0) {
            burstRemaining--;
            useNextSpecial(t);
            return true;
        }
        if (attackCount >= ATTACKS_PER_SPECIAL) {
            attackCount = 0;
            useNextSpecial(t);
            return true;
        }
        basicAttack(t);
        attackCount++;
        return true;
    }

    /**
     * Below half hitpoints she enrages once and opens the phase with all three specials
     * back-to-back. Checked on every hit she takes (not just once per attack cycle) so it can
     * never be missed mid-special.
     */
    private void checkEnrage(Hit hit) {
        if (enraged || npc.getHp() <= 0) {
            return;
        }
        if (npc.getHp() * 2 > npc.getMaxHp()) {
            return;
        }
        enraged = true;
        burstRemaining = SPECIAL_COUNT;
        npc.forceText("...!");
        if (target != null && target.player != null) {
            target.player.sendMessage("The Mad Angel's fury boils over!");
        }
    }

    /**
     * Rewards for blocking a smite (perfect flick) or dodging a cleave to a side, paid out on the
     * player's next landed hit against her. Runs BEFORE the hit is rolled, so this is where
     * accuracy/damage get forced; {@link #onIncomingHitPost} runs after and applies the floor.
     */
    private void onIncomingHitPre(Hit hit) {
        if (reflecting || hit.attacker == null || hit.attacker.player == null) {
            return;
        }
        if (guaranteedMaxHit) {
            hit.min(hit.maxDamage); // forces the roll to its own ceiling
            hit.ignoreDefence();    // and guarantees it isn't blocked outright
        } else if (guaranteedStrongHit) {
            hit.ignoreDefence();    // "100% accurate"; the floor is applied after the roll, below
        }
    }

    private void onIncomingHitPost(Hit hit) {
        if (reflecting || hit.attacker == null || hit.attacker.player == null) {
            return;
        }
        if (guaranteedMaxHit) {
            guaranteedMaxHit = false;
            hit.attacker.player.sendMessage("Your prayer holds — the blow lands true.");
        } else if (guaranteedStrongHit) {
            guaranteedStrongHit = false;
            int floor = hit.maxDamage / 2;
            if (hit.damage < floor) {
                hit.damage = floor;
            }
        }
    }

    // =========================================================================================
    // Basic attacks
    // =========================================================================================

    /**
     * Animation 4589 either way. Melee when adjacent, magic otherwise -- she never moves to close
     * the gap herself once a special has rooted her, so this is what lets her still threaten a
     * kiting player between specials.
     */
    private void basicAttack(Player t) {
        npc.animate(SEQ_MELEE);
        npc.face(t);
        if (npc.getPosition().isWithinDistance(t.getPosition(), 1)) {
            t.hit(new Hit(npc, AttackStyle.SLASH).randDamage(0, MAX_HIT).delay(1));
        } else {
            t.hit(new Hit(npc, AttackStyle.MAGIC).randDamage(0, MAX_HIT).delay(2));
            t.graphics(GFX_SMITE_HIT, 0, 30);
        }
    }

    private void useNextSpecial(Player t) {
        switch (nextSpecial) {
            case 0 -> startBomb(t);
            case 1 -> startCleave(t);
            default -> startSmite(t);
        }
        nextSpecial = (nextSpecial + 1) % SPECIAL_COUNT;
    }

    // =========================================================================================
    // Lightning Smite
    // =========================================================================================

    private void startSmite(Player t) {
        boolean wasEnraged = enraged;
        npc.animate(wasEnraged ? SEQ_SMITE_ENRAGED : SEQ_SMITE);
        // TEMP DISABLED 2026-09-13: GFX_SMITE_CHARGE/_ENRAGED (spotanim 4011/4012, model 60639)
        // reported as a screen-filling deformed mesh under RS117HD specifically. Not yet known
        // whether the model itself is bad or 117HD's shader pipeline is mishandling a custom,
        // never-classified model id -- untested with 117HD off. Animation (the telegraph itself,
        // SEQ_SMITE/_ENRAGED above) is unaffected and still plays; only the sky-beam visual is
        // pulled pending that test, rather than swapping in an unverified substitute id blind.
        // npc.graphics(wasEnraged ? GFX_SMITE_CHARGE_ENRAGED : GFX_SMITE_CHARGE);
        smiteLandsAt = Server.currentTick() + SMITE_CHARGE_TICKS;
        smiteFollowUps = wasEnraged ? 2 : 0;
        t.sendMessage("The Mad Angel's eyes glow blue — protect from Magic!");
    }

    /** Called from {@link #process()} once {@link #smiteLandsAt} is due. */
    private void resolveSmiteStrike(Player t) {
        smiteLandsAt = -1;
        if (npc.getHp() <= 0) {
            return;
        }
        boolean prayingNow = t.getPrayer().isActive(Prayer.PROTECT_FROM_MAGIC);
        boolean flicked = prayingNow && !prayedLastTick;
        int rolled = Random.get(SMITE_MIN, SMITE_MAX);
        int damage;
        if (flicked) {
            damage = 0;
        } else if (prayingNow) {
            damage = rolled * (100 - SMITE_PRAYER_REDUCTION) / 100;
        } else {
            damage = rolled;
        }

        t.hit(new Hit(npc, AttackStyle.MAGIC).fixedDamage(damage).delay(HIT_NOW).ignoreDefence());
        t.graphics(GFX_SMITE_HIT, 0, 0);

        if (flicked) {
            guaranteedMaxHit = true;
            t.sendMessage("You block the strike perfectly!");
        }

        // Enraged: three strikes in all, the second SMITE_ENRAGED_DELAY_1 ticks after the first
        // and the third SMITE_ENRAGED_DELAY_2 ticks after the second.
        if (smiteFollowUps > 0) {
            int gap = smiteFollowUps == 2 ? SMITE_ENRAGED_DELAY_1 : SMITE_ENRAGED_DELAY_2;
            smiteFollowUps--;
            smiteLandsAt = Server.currentTick() + gap;
        }
    }

    // =========================================================================================
    // Exploding Light
    // =========================================================================================

    /**
     * PERMANENTLY OFF the authentic id as of 2026-09-13. Full history: spotanim 4015
     * ({@link MadAngelIds#GFX_BOMB_TRAVEL}, model 60632 -- shared by every bomb GFX id, 4014-4017)
     * is the id source itself sends for both legs of the throw, and was tried three separate times
     * across this and an earlier session -- confirmed BOTH times to render as a giant, room-filling
     * broken yellow mesh as a MOVING projectile (fine as a static point display via
     * {@code npc.graphics()}/{@code World.sendGraphics()}; root cause never found, but reproduced
     * consistently enough -- twice, independently -- to stop treating it as a fluke). Do not put
     * 4015 (or any of 4014/4016/4017) back into a Projectile/ProjAnim call. Also tried and rejected:
     * Vorkath's MAGIC_PROJECTILE (1479, thematically wrong), Bree's ranged gfx (1190, a flying
     * arrow), Nex's holy/wrath orb (2007, still reported wrong). <b>Now locked to 160</b> (a
     * standard, pre-existing spotanim -- decoded from Zelus's own cache: model 3116, ordinary
     * 128/128 scale, nothing shared with the broken 60632 mesh) per explicit instruction, as the
     * one that visibly does not stretch.
     */
    private static final Projectile PROJ_BOMB_OUT =
            new Projectile(160, 220, 30, 30, 120, 0, 40, 0);
    /** The 1-tick return leg. Same id/history as {@link #PROJ_BOMB_OUT} above -- see its javadoc. */
    private static final Projectile PROJ_BOMB_BACK =
            new Projectile(160, 30, 220, 0, 30, 0, 17, 0);

    private void startBomb(Player t) {
        bombThrowsLeft = BOMB_BOUNCES;
        t.sendMessage("A ball of light arcs into the room — stand in its shadow to send it back!");
        throwBomb(t);
    }

    private void throwBomb(Player t) {
        npc.animate(SEQ_BOMB);
        // TEMP SWAPPED 2026-09-13: was GFX_BOMB_THROW (spotanim 4014, model 60632) -- same model,
        // same reasoning as PROJ_BOMB_OUT above. Same substitute (gfx 76) already used for
        // detonate()/the reflect flash, for consistency.
        npc.graphics(76);

        Position tile = scatterTile(t);
        bombTile = tile;
        bombLandsAt = Server.currentTick() + BOMB_OUT_TICKS;
        bombThrowsLeft--;
        PROJ_BOMB_OUT.send(npc, tile.getX(), tile.getY());
        World.sendGraphics(GFX_BOMB_SHADOW, 0, CLIENT_CYCLES_PER_TICK, tile);
    }

    /** Called from {@link #process()} once {@link #bombLandsAt} is due. */
    private void resolveBomb(Player t, Position landing) {
        bombTile = null;
        bombLandsAt = -1;

        if (samePosition(t.getPosition(), landing)) {
            PROJ_BOMB_BACK.send(landing, npc);
            int reflected = Random.get(REFLECT_MIN, REFLECT_MAX);
            reflecting = true;
            npc.hit(new Hit(t, AttackStyle.MAGIC).fixedDamage(reflected).delay(BOMB_BACK_TICKS).ignoreDefence());
            reflecting = false;
            // TEMP SWAPPED 2026-09-13: was GFX_BOMB_REFLECT (spotanim 4017, model 60632) -- see
            // PROJ_BOMB_BACK's javadoc above for why this exact moment is the isolated culprit.
            // gfx 76 is Saradomin Strike's own hit splash (SaradominStrike.java), standard content.
            npc.graphics(76, 0, BOMB_BACK_TICKS * CLIENT_CYCLES_PER_TICK);
            t.sendMessage("You bounce the light back at the Mad Angel!");

            if (npc.getHp() <= 0) {
                bombThrowsLeft = 0;
                return;
            }
            if (bombThrowsLeft > 0) {
                throwBomb(t);
            }
        } else {
            detonate(landing, t);
            bombThrowsLeft = 0;
        }
    }

    /**
     * TEMP DISABLED 2026-09-13: the per-tile GFX_BOMB_HIT wash loop is removed (was hundreds of
     * overlapping copies of model 60632 across the room). Then reported STILL showing "a tight
     * circle of vertical light pillars completely filling the room" from just the single remaining
     * call below -- consistent with model 60632 itself being authored as a multi-pillar cluster,
     * not a simple column, the same suspicion that already justified swapping PROJ_BOMB_BACK and
     * the reflect flash (see their javadocs). Same swap applied here for consistency: gfx 76,
     * Saradomin Strike's hit splash (SaradominStrike.java), standard content, single dispatch on
     * the landing tile. Damage is unaffected either way -- t.hit() below was never tied to either
     * the loop or the specific graphic id.
     */
    private void detonate(Position landing, Player t) {
        World.sendGraphics(76, 0, 0, landing);
        int damage = Random.get(DETONATE_MIN, DETONATE_MAX);
        t.hit(new Hit(npc, AttackStyle.MAGIC).fixedDamage(damage).delay(HIT_NOW).ignoreDefence());
        t.graphics(76, 0, 0);
        t.sendMessage("The light detonates across the cathedral!");
    }

    /**
     * A random reachable tile within {@link #BOMB_SCATTER} of the PLAYER (not of her -- scattering
     * around her footprint put marks behind pews and past the arena edge).
     */
    /**
     * Widens the search if the tight radius keeps failing, rather than immediately giving up and
     * marking the player's own tile. Confirmed in-game (2026-09-12, {@code ::madangelcollision})
     * that this room's collision is NOT broken -- the tiles right around the player are a real,
     * legitimate mix of open floor and blocked furniture (pew rows), not void. But when fighting
     * her at melee range, her own {@link #ANGEL_SIZE} 3x3 footprint plus a pew or two can cover
     * most of a tight {@link #BOMB_SCATTER}=3 search area, leaving few or no valid candidates
     * within it specifically -- not because the room is broken, just because 3 tiles is a small
     * radius to search around two separate obstacles. Widening out (still capped well inside the
     * arena, nowhere near the ~6-tile-distant doorway) finds a real nearby tile far more reliably
     * before this ever needs to fall back to the player's own position.
     */
    private Position scatterTile(Player t) {
        // BUG FIXED 2026-09-13, the real root cause of this entire investigation: `origin` here IS
        // the player's own live position object (getPosition() does not return a copy). translate()
        // mutates in place and returns itself -- so every rejected candidate on every failed
        // attempt below was PERMANENTLY relocating the actual player by that attempt's random
        // (dx,dy), silently, with no bounds check, before the next attempt shifted them again from
        // wherever that left them. Across up to 60 attempts per bomb throw, compounded over every
        // bomb throw in the fight, this alone explains the player (and, via the identical mistake
        // in drawSword/resolveCleave/paintCleave below, the boss) appearing to teleport/wander/end
        // up outside the room across this entire investigation -- none of it was pathfinding,
        // leashing, or movement-queue behaviour. translated() returns a new Position and never
        // touches the original.
        // BUG FIXED 2026-09-13, checked against the source's own scatterTile (MadAngel.kt): a plain
        // isOpen()/BLOCK_WALK check on the CANDIDATE tile alone is not enough, and the source's own
        // comment on this exact method says so -- "walls live on the tile EDGES, not on the tiles
        // themselves, so a tile one room over reads as perfectly walkable. Marks were landing inside
        // the side chapels and out past the cathedral wall." Source's fix was `reachableFrom`, a
        // line-of-walk check between the player and the candidate. Zelus's equivalent utility is
        // ProjectileRoute.allow() -- same idea, walking each step's tile-edge clipping via
        // Bresenham (already used by DumbRoute.withinDistance() for the same kind of question) --
        // so a candidate across a wall or in an unconnected side room now correctly fails even when
        // its own tile happens to read as open.
        Position origin = t.getPosition();
        for (int radius = BOMB_SCATTER; radius <= BOMB_SCATTER_MAX; radius += BOMB_SCATTER) {
            for (int i = 0; i < 20; i++) {
                Position candidate = origin.translated(Random.get(-radius, radius), Random.get(-radius, radius));
                if (isOpen(candidate) && !underAngel(candidate)
                        && ProjectileRoute.allow(origin.getX(), origin.getY(), origin.getZ(), candidate.getX(), candidate.getY())) {
                    return candidate;
                }
            }
        }
        return origin;
    }

    private boolean underAngel(Position p) {
        Position sw = npc.getPosition();
        return p.getX() >= sw.getX() && p.getX() < sw.getX() + ANGEL_SIZE
                && p.getY() >= sw.getY() && p.getY() < sw.getY() + ANGEL_SIZE;
    }

    // =========================================================================================
    // Sword Cleave
    // =========================================================================================

    private void startCleave(Player t) {
        cleaveSwingsLeft = enraged ? CLEAVE_SWINGS_ENRAGED : CLEAVE_SWINGS;
        drawSword(t, true);
    }

    /** Commits one swing: the side, the facing, and the zone the player occupies right now. */
    private void drawSword(Player t, boolean opening) {
        // BUG FIXED 2026-09-13: npc.getPosition() is her REAL, live position object, not a copy.
        // translate() mutates in place and returns itself, so this line used to permanently shift
        // her actual position by (ANGEL_SIZE/2, ANGEL_SIZE/2) -- every single swing, opening and
        // every follow-up (drawSword is called again from resolveCleave for each remaining swing).
        // translated() computes the same centre point without moving her.
        Position centre = npc.getPosition().translated(ANGEL_SIZE / 2, ANGEL_SIZE / 2);
        int dx = t.getPosition().getX() - centre.getX();
        int dz = t.getPosition().getY() - centre.getY();
        if (Math.abs(dx) >= Math.abs(dz)) {
            cleaveFacingX = dx >= 0 ? 1 : -1;
            cleaveFacingZ = 0;
        } else {
            cleaveFacingX = 0;
            cleaveFacingZ = dz >= 0 ? 1 : -1;
        }
        // Committed facing, locked for the whole telegraph -- matches the source's lockFacing,
        // pointing FACE_LOCK_DISTANCE tiles out in the committed direction rather than at the
        // player, so the zones cannot rotate under them mid-telegraph.
        //
        // BUG FIXED 2026-09-13: same translate()-mutates-itself mistake, this time on `centre`
        // directly -- which used to matter even more here because `centre` is read AGAIN below
        // (cleaveMarked = cleaveZoneOf(t.getPosition(), centre)) after this call, so that zone was
        // being computed from a centre already flung FACE_LOCK_DISTANCE (6) tiles away, not the
        // real one. translated() leaves `centre` itself untouched for that later read.
        npc.face(centre.translated(cleaveFacingX * FACE_LOCK_DISTANCE, cleaveFacingZ * FACE_LOCK_DISTANCE));

        CleaveZone previous = cleaveSword;
        cleaveSword = Random.get(2) == 0 ? CleaveZone.LEFT : CleaveZone.RIGHT;
        cleaveMarked = cleaveZoneOf(t.getPosition(), centre);

        int newSide = cleaveSword == CleaveZone.LEFT ? 0 : 1;
        int seq;
        if (opening) {
            seq = (enraged ? SEQ_SWEEP_OPEN_ENRAGED : SEQ_SWEEP_OPEN)[newSide];
        } else if (previous == cleaveSword) {
            seq = (enraged ? SEQ_SWEEP_AGAIN_ENRAGED : SEQ_SWEEP_AGAIN)[newSide];
        } else {
            int fromSide = previous == CleaveZone.LEFT ? 0 : 1;
            seq = (enraged ? SEQ_SWEEP_CROSS_ENRAGED : SEQ_SWEEP_CROSS)[fromSide];
        }
        npc.animate(seq);

        int telegraph = enraged ? CLEAVE_TELEGRAPH_ENRAGED : CLEAVE_TELEGRAPH;
        cleaveLandsAt = Server.currentTick() + telegraph;

        t.sendMessage("The Mad Angel draws her sword back to the " + (cleaveSword == CleaveZone.LEFT ? "left" : "right") + "!");
    }

    /** Called from {@link #process()} once {@link #cleaveLandsAt} is due. */
    private void resolveCleave(Player t) {
        cleaveLandsAt = -1;
        // BUG FIXED 2026-09-13: same mutating-translate() mistake as drawSword, compounded by
        // paintCleave below ALSO mutating this same object (by reference) up to (2*CLEAVE_RADIUS+1)^2
        // = 169 more times per call -- together, every single cleave resolution was flinging her
        // real position across the room, which is exactly what image_9d8166/9d2f6c/9d814a showed.
        Position centre = npc.getPosition().translated(ANGEL_SIZE / 2, ANGEL_SIZE / 2);
        paintCleave(centre);

        CleaveZone zone = cleaveZoneOf(t.getPosition(), centre);
        boolean swept = zone == cleaveSword || zone == cleaveMarked || zone == CleaveZone.FRONT;

        if (swept) {
            t.hit(new Hit(npc, AttackStyle.SLASH).randDamage(CLEAVE_MIN, CLEAVE_MAX).delay(HIT_NOW).ignoreDefence());
        } else if (zone == CleaveZone.BEHIND) {
            t.sendMessage("You duck behind the Mad Angel.");
        } else {
            guaranteedStrongHit = true;
            t.sendMessage("You slip past the shockwave.");
        }

        cleaveSwingsLeft--;
        if (cleaveSwingsLeft > 0) {
            drawSword(t, false);
            return;
        }

        // The run's last wave -- the closing animation carries it, it is not a cosmetic return-to-rest.
        npc.animate(SEQ_SWEEP_RESET[cleaveSword == CleaveZone.LEFT ? 0 : 1]);
        // Run over. Drop the committed facing and look at the player again.
        npc.face(t);
    }

    /** Every tile the sweep actually covers, painted so what the player sees is what hits them. */
    private void paintCleave(Position centre) {
        // BUG FIXED 2026-09-13: centre is passed by reference from resolveCleave's own `centre` --
        // mutating it here (the old .translate()) mutated the CALLER's variable too, on every one
        // of this loop's up to 169 iterations, which was then read again by resolveCleave right
        // after this call returns. translated() never touches centre itself.
        for (int dx = -CLEAVE_RADIUS; dx <= CLEAVE_RADIUS; dx++) {
            for (int dz = -CLEAVE_RADIUS; dz <= CLEAVE_RADIUS; dz++) {
                Position tile = centre.translated(dx, dz);
                if (underAngel(tile)) {
                    continue;
                }
                CleaveZone zone = cleaveZoneOf(tile, centre);
                boolean swept = zone == cleaveSword || zone == cleaveMarked || zone == CleaveZone.FRONT;
                if (!swept || !isOpen(tile)) {
                    continue;
                }
                int ring = Math.max(Math.abs(dx), Math.abs(dz));
                World.sendGraphics(GFX_CLEAVE_WAVE, 0, ring * CLEAVE_SPREAD_PER_RING, tile);
            }
        }
    }

    /** Forward/back from the dot product with her committed facing; left/right from the perpendicular. */
    private CleaveZone cleaveZoneOf(Position coord, Position centre) {
        int ox = coord.getX() - centre.getX();
        int oz = coord.getY() - centre.getY();
        int forward = ox * cleaveFacingX + oz * cleaveFacingZ;
        if (forward < 0) {
            return CleaveZone.BEHIND;
        }
        int side = ox * cleaveFacingZ - oz * cleaveFacingX;
        if (side > 0) {
            return CleaveZone.RIGHT;
        }
        if (side < 0) {
            return CleaveZone.LEFT;
        }
        return CleaveZone.FRONT;
    }

    // =========================================================================================
    // Death
    // =========================================================================================

    private void onDeath(Player killer) {
        Position respawnCoords = npc.spawnPosition != null ? npc.spawnPosition.copy() : npc.getPosition().copy();
        // BUG FIXED 2026-09-13: same translate()-mutates-itself mistake -- respawnCoords is read
        // again below for the dormant NPC's actual spawn tile, so this used to respawn her 1 tile
        // off from her true spot every single kill. translated() leaves respawnCoords alone.
        Position dropCoords = respawnCoords.translated(1, -1);
        DynamicMap map = killer != null ? killer.currentDynamicMap : null;

        npc.transform(NPC_DEAD);
        npc.addEvent(event -> {
            event.delay(DEATH_BODY_TICKS);
            npc.remove();
            NPC dormant = new NPC(NPC_DORMANT).spawn(respawnCoords.getX(), respawnCoords.getY(), respawnCoords.getZ(), Direction.EAST, 0);
            if (map != null) {
                map.addNpc(dormant);
            }
        });

        if (killer != null) {
            // Same generic, JSON-driven drop system every other boss uses -- see
            // data/npcs/drops/newDrops/16305.json (converted from the wiki table) and the class
            // javadoc above for how that differs in behaviour from the source's own roller.
            handleNewDrop(killer, npc.getId(), dropCoords);
        }
    }

    // =========================================================================================
    // Shared helpers
    // =========================================================================================

    private boolean isOpen(Position p) {
        Tile tile = Tile.get(p, true);
        return tile != null && tile.clipping == 0;
    }

    private boolean samePosition(Position a, Position b) {
        return a.getX() == b.getX() && a.getY() == b.getY() && a.getZ() == b.getZ();
    }

    // =========================================================================================
    // Constants -- values and their source noted individually; see the source's CAPTURE.md for
    // which of these are measured from a real packet log versus chosen (never silently swapped).
    // =========================================================================================

    /** Wiki: "6 ticks (3.6 seconds)". */
    private static final int ATTACK_RATE = 6;
    /** Wiki infobox max hit; every basic/cleave attack here is clamped to it via randDamage's own ceiling. */
    private static final int MAX_HIT = 28;
    /**
     * Ticks between being aggroed and her first swing. Chosen, not measured; bumped 2->4 on
     * explicit instruction ("zero special attacks on tick 1", "delay special attacks by at least
     * 4 ticks") -- though {@link #ATTACKS_PER_SPECIAL} (3 basics minimum before any special) was
     * already guaranteeing that structurally regardless of this value.
     */
    private static final int OPENING_DELAY = 4;
    /** Measured: seq 3321 runs 23 frames / 90 client cycles = exactly 3 ticks. */
    private static final int SPAWN_RISE_TICKS = 3;
    /** Measured: death animation at capture-tick 305854, corpse transform at 305861. */
    private static final int DEATH_BODY_TICKS = 7;
    /** The smallest legal "lands this tick" delay. */
    private static final int HIT_NOW = 1;
    /** Smite, bomb, cleave. */
    private static final int SPECIAL_COUNT = 3;
    /** Measured: three ordinary attacks precede each special, not two. */
    private static final int ATTACKS_PER_SPECIAL = 3;
    /** attackRange 16 / maxRange 30 in the source npc editor -- how far she keeps firing the magic auto. */
    private static final int MAX_ENGAGE_RANGE = 30;

    // Lightning Smite
    /** Strike resolves 5 ticks after the charge -- see the source's note on why not the measured 6. */
    private static final int SMITE_CHARGE_TICKS = 5;
    private static final int SMITE_PRAYER_REDUCTION = 50;
    private static final int SMITE_MIN = 18;
    private static final int SMITE_MAX = 28;
    private static final int SMITE_ENRAGED_DELAY_1 = 4;
    private static final int SMITE_ENRAGED_DELAY_2 = 2;

    // Exploding Light
    private static final int BOMB_BOUNCES = 3;
    private static final int BOMB_SCATTER = 3;
    /** How far {@link #scatterTile} widens out if the tight radius above can't find a candidate. */
    private static final int BOMB_SCATTER_MAX = 9;
    /**
     * BUG FIXED 2026-09-13: was 4, matching only the TRAVEL portion of the throw. Traced against
     * PROJ_BOMB_OUT's own fields (Zelus's Projectile(gfxId, startHeight, endHeight, delay,
     * durationStart, durationIncrement, curve, idk) -- confirmed by reading Projectile.java
     * directly): delay=30 cycles (1 tick) BEFORE the ball starts moving, THEN durationStart=120
     * cycles (4 ticks) of travel -- a 5-tick total visual span from throw to impact, matching
     * MadAngel.kt's own comment on the real capture ("starttime=30 endtime=150 ... four ticks in
     * the air, ONE TICK BEFORE IT STARTS"). Scheduling the server's own resolution
     * (resolveBomb()/detonate()) at 4 ticks instead of 5 fired it a full tick before the ball had
     * visually finished arriving -- landing tile marked, detonation/reflect message and hit already
     * resolved, while the ball sprite was still one tick from the floor. Reads as "the ball travels
     * way too fast" because the mechanic concludes before its own visual does, not because the
     * throw's travel time itself is actually short.
     */
    private static final int BOMB_OUT_TICKS = 5;
    private static final int BOMB_BACK_TICKS = 1;
    private static final int ANGEL_SIZE = 3;
    private static final int DETONATE_MIN = 30;
    private static final int DETONATE_MAX = 40;
    private static final int REFLECT_MIN = 18;
    private static final int REFLECT_MAX = 22;
    // BLAST_RADIUS / BLAST_SPREAD_CYCLES removed 2026-09-13 along with detonate()'s per-tile loop
    // (see detonate()'s javadoc) -- no longer referenced.
    private static final int CLIENT_CYCLES_PER_TICK = 30;

    // Sword Cleave
    private static final int CLEAVE_SWINGS = 4;
    private static final int CLEAVE_SWINGS_ENRAGED = 6;
    private static final int CLEAVE_TELEGRAPH = 6;
    private static final int CLEAVE_TELEGRAPH_ENRAGED = 6;
    private static final int CLEAVE_MIN = 12;
    private static final int CLEAVE_MAX = 28;
    private static final int CLEAVE_RADIUS = 6;
    private static final int CLEAVE_SPREAD_PER_RING = 2;
    /** How far ahead the locked cleave facing points. Far enough that the angle reads cleanly. */
    private static final int FACE_LOCK_DISTANCE = 6;
}
