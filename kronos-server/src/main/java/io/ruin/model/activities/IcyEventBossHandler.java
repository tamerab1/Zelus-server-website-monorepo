package io.ruin.model.activities;

import io.ruin.cache.NPCType;
import io.ruin.model.World;
import io.ruin.model.activities.bosses.eventboss.IcyEventBoss;
import io.ruin.model.entity.npc.NPC;
import io.ruin.model.entity.player.Player;
import io.ruin.model.map.Position;

/**
 * Manages the Icy Event Boss — always-present shared boss.
 * Spawns on server startup and respawns 3 minutes after death.
 *
 * Spawn:    (2082, 4466, 0)
 * Teleport: (2082, 4466, 0)
 * Command:  ::icy / ::icyboss
 */
public class IcyEventBossHandler {

    public static NPC boss;
    public static final Position teleportPosition = new Position(2082, 4466, 0);
    private static final Position spawnPosition   = new Position(2082, 4466, 0);

    /** Called once at server startup from StaticInit. */
    public static void init() {
        // Without this, NPC.setCombat() falls back to default BasicCombat and Icy's
        // whole custom attack pattern silently never ran.
        NPCType.registerCombat(IcyEventBoss.class, 17042);
        spawnBoss();
    }

    private static void spawnBoss() {
        boss = new NPC(17042).spawn(spawnPosition);
        broadcastAll("<col=00ffff>[Icy Event Boss]</col> has appeared! Type ::icy to fight it.");
    }

    /**
     * Called by the Icy Event Boss's death listener.
     * Clears the boss reference and schedules a 3-minute respawn.
     */
    public static void onDeath() {
        boss = null;
        broadcastAll("<col=00ffff>[Icy Event Boss]</col> has been slain! It will return in 3 minutes...");
        World.startEvent(e -> {
            e.delay(300); // 300 ticks = 3 minutes
            spawnBoss();
        });
    }

    private static void broadcastAll(String message) {
        for (Player p : World.players())
            p.sendMessage(message);
    }
}
