package io.ruin.model.activities;

import io.ruin.api.utils.Random;
import io.ruin.cache.NPCType;
import io.ruin.model.World;
import io.ruin.model.activities.bosses.eventboss.Vorath;
import io.ruin.model.entity.npc.NPC;
import io.ruin.model.entity.player.Player;
import io.ruin.model.map.Position;

/**
 * Pandora, the Enigma Keeper (npc 17033, the Mystery Box boss; was "Vorath") -- an event boss (2026-10-20). It appears
 * every 2-3 hours (60 seconds' warning, then the spawn broadcast); the next timer starts when it dies. ::pandora takes
 * players to its lair while it's up and says how long until it returns otherwise; admins can force it with
 * ::spawnpandora.
 * <p>
 * The lair is the pinball room in map square 30_78, rebuilt 41x41 (.dev/cache-restore-tool RebuildPandoraArena): the
 * player lands just inside the cave exit (9293, which takes you home), Pandora (size 5) stands at the north end of the
 * green emblem.
 *
 * Spawn:    (1962, 5044, 0) -- its south-west tile, the 5x5 centred on 1964, 5046
 * Teleport: (1964, 5022, 0)
 * Command:  ::pandora / ::vor / ::vorath
 */
public class VorathHandler {

    public static NPC boss;
    public static final Position teleportPosition = new Position(1964, 5022, 0);
    private static final Position spawnPosition   = new Position(1962, 5044, 0);
    /** 2-3 hours between appearances, in ticks (100 ticks a minute). */
    private static final int MIN_DELAY = 120 * 100, MAX_DELAY = 180 * 100, WARNING = 100;

    /** Server tick the next appearance is due, or -1 while it's up / about to spawn. */
    private static long nextSpawnTick = -1;
    /** Bumped on every (re)schedule, so a forced spawn cancels the pending timer. */
    private static int schedule;

    /** Called once at server startup from StaticInit. */
    public static void init() {
        // Without this, NPC.setCombat() falls back to default BasicCombat and Vorath's
        // whole custom attack pattern + loot table silently never ran.
        NPCType.registerCombat(Vorath.class, 17033);
        // the lair's cave exit takes you home
        io.ruin.model.map.object.actions.ObjectAction.register(9293, 1, (player, obj) ->
                io.ruin.model.skills.magic.spells.modern.ModernTeleport.teleport(player, World.HOME));
        scheduleNext();
    }

    private static void scheduleNext() {
        int id = ++schedule;
        int delay = Random.get(MIN_DELAY, MAX_DELAY);
        nextSpawnTick = io.ruin.Server.currentTick() + delay;
        World.startEvent(e -> {
            e.delay(delay - WARNING);
            if (id != schedule || boss != null)
                return;
            nextSpawnTick = -1;
            announce("<col=a020f0>[Pandora]</col> The Enigma Keeper's chest is rattling... it opens in 60 seconds! Type ::pandora to join.");
            e.delay(WARNING);
            if (id == schedule && boss == null)
                spawnBoss();
        });
    }

    private static void spawnBoss() {
        nextSpawnTick = -1;
        boss = new NPC(17033).spawn(spawnPosition);
        announce("<col=a020f0>[Pandora]</col> Pandora, the Enigma Keeper has burst out of its chest! Type ::pandora to fight it.");
    }

    /** {@code ::spawnpandora} (admin): spawn it now, cancelling the pending timer. */
    public static boolean forceSpawn() {
        if (boss != null)
            return false;
        schedule++;
        spawnBoss();
        return true;
    }

    /** For ::pandora while it's away: "in 1h 23m", or null while it's up or about to spawn. */
    public static String timeUntilNext() {
        if (boss != null || nextSpawnTick < 0)
            return null;
        long minutes = Math.max(1, (nextSpawnTick - io.ruin.Server.currentTick()) / 100);
        return minutes >= 60 ? (minutes / 60) + "h " + (minutes % 60) + "m" : minutes + "m";
    }

    /** Called by Pandora's death listener: the next appearance is 2-3 hours out. */
    public static void onDeath() {
        boss = null;
        announce("<col=a020f0>[Pandora]</col> Pandora has been slain! Its chest will reform in 2-3 hours...");
        scheduleNext();
    }

    /** The top-of-screen broadcast banner only (no separate chat line; the client adds its own "Broadcast:" entry). */
    private static void announce(String message) {
        for (Player p : World.players())
            p.getPacketSender().sendBroadcast(message);
    }
}
