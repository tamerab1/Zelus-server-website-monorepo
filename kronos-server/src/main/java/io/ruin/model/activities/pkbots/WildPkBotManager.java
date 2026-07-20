package io.ruin.model.activities.pkbots;

import io.ruin.model.World;
import io.ruin.model.entity.player.Player;
import io.ruin.process.event.EventWorker;

/**
 * WildPkBotManager — spawns and ticks the three server-wide wilderness PK bots
 * (Zerker/Pure/Main) that stand in low-level wilderness for players to fight.
 *
 * Call {@link #register()} once during server startup from StaticInit.
 * Bots respawn automatically via WildPkBotPlayer's death handler.
 */
public class WildPkBotManager {

    private static final WildPkBotPlayer[] wildBots =
            new WildPkBotPlayer[WildPkBotSetup.values().length];

    // -----------------------------------------------------------------------
    // Registration
    // -----------------------------------------------------------------------

    public static void register() {
        spawnAll();
        EventWorker.startEvent(e -> {
            while (true) {
                e.delay(1);
                tickAll();
            }
        });
    }

    // -----------------------------------------------------------------------
    // Spawn / despawn
    // -----------------------------------------------------------------------

    private static void spawnAll() {
        WildPkBotSetup[] setups = WildPkBotSetup.values();
        for (int i = 0; i < setups.length; i++) {
            if (wildBots[i] == null) {
                try {
                    wildBots[i] = new WildPkBotPlayer(setups[i]);
                    System.out.println("[WildPkBotManager] Spawned " + setups[i].username
                            + " at " + setups[i].spawnPosition);
                } catch (IllegalStateException ex) {
                    System.err.println("[WildPkBotManager] Could not spawn "
                            + setups[i].username + ": " + ex.getMessage());
                }
            }
        }
    }

    /** Despawns and respawns all wild bots (admin use). */
    public static void respawnAll(Player admin) {
        despawnAll(null);
        spawnAll();
        if (admin != null)
            admin.sendMessage("<col=ffd700>[WildBot]</col> All wilderness bots respawned.");
    }

    /** Cleanly removes all wild bots from the world. */
    public static void despawnAll(Player admin) {
        for (int i = 0; i < wildBots.length; i++) {
            WildPkBotPlayer bot = wildBots[i];
            if (bot != null) {
                removeBot(bot);
                wildBots[i] = null;
            }
        }
        if (admin != null)
            admin.sendMessage("<col=ffd700>[WildBot]</col> All wilderness bots despawned.");
    }

    private static void removeBot(WildPkBotPlayer bot) {
        bot.online = false;
        bot.deallocateRSProt();
        World.removePlayer(bot);
    }

    // -----------------------------------------------------------------------
    // Per-tick processing
    // -----------------------------------------------------------------------

    private static void tickAll() {
        for (WildPkBotPlayer bot : wildBots) {
            if (bot == null) continue;
            try {
                bot.ai.process();
            } catch (Exception ignored) {
            }
        }
    }
}
