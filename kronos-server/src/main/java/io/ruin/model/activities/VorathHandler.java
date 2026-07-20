package io.ruin.model.activities;

import io.ruin.model.World;
import io.ruin.model.entity.npc.NPC;
import io.ruin.model.entity.player.Player;
import io.ruin.model.map.Position;

/**
 * Manages Vorath's lair — always-present shared boss.
 * Vorath spawns on server startup and respawns 3 minutes after death.
 *
 * Spawn:    (1972, 5048, 0)
 * Teleport: (1972, 5038, 0)
 * Command:  ::vor / ::vorath
 */
public class VorathHandler {

    public static NPC boss;
    public static final Position teleportPosition = new Position(1972, 5038, 0);
    private static final Position spawnPosition   = new Position(1972, 5048, 0);

    /** Called once at server startup from StaticInit. */
    public static void init() {
        spawnBoss();
    }

    private static void spawnBoss() {
        boss = new NPC(17033).spawn(spawnPosition);
        broadcastAll("<col=ff0000>[Vorath]</col> has appeared in its lair! Type ::vor to fight it.");
    }

    /**
     * Called by Vorath's death listener.
     * Clears the boss reference and schedules a 3-minute respawn.
     */
    public static void onDeath() {
        boss = null;
        broadcastAll("<col=ff0000>[Vorath]</col> has been slain! It will return in 3 minutes...");
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
