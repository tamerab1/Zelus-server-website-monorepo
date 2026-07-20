package io.ruin.model.activities.pkbots;

import io.ruin.model.World;
import io.ruin.model.entity.player.Player;
import io.ruin.model.map.Position;
import io.ruin.process.event.EventWorker;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ZelusBotManager — manages all active {@link ZelusBotPlayer} instances.
 *
 * <h3>Usage</h3>
 * <pre>
 *   // Spawn a bot for a player (max 3 per player)
 *   ZelusBotManager.spawnForPlayer(player, player.getPosition());
 *
 *   // Remove all bots owned by a player
 *   ZelusBotManager.despawnForPlayer(player);
 * </pre>
 *
 * <h3>World tick integration</h3>
 * Call {@link #register()} once during server startup (in {@code StaticInit}).
 * This registers a persistent world event that drives all bot AIs every tick.
 */
public class ZelusBotManager {

    /** Maximum concurrent bots each player may have at once. */
    public static final int MAX_BOTS_PER_PLAYER = 3;

    /** Counter used to generate unique bot names. */
    private static final AtomicInteger botCounter = new AtomicInteger(1);

    /** All currently-active bots, keyed by their username. */
    private static final Map<String, ZelusBotPlayer> activeBots = new ConcurrentHashMap<>();

    /** Maps owner userId → list of bot usernames they own. */
    private static final Map<Integer, List<String>> ownerBots = new ConcurrentHashMap<>();

    // -----------------------------------------------------------------------
    // Registration (call once on server start)
    // -----------------------------------------------------------------------

    /**
     * Registers the per-tick AI processing loop.
     * Must be called during server startup (e.g. from {@code StaticInit}).
     */
    public static void register() {
        EventWorker.startEvent(e -> {
            while (true) {
                e.delay(1);
                processBots();
            }
        });
    }

    // -----------------------------------------------------------------------
    // Spawn / despawn
    // -----------------------------------------------------------------------

    /**
     * Spawns a bot for the given player at the given position (or next to them).
     *
     * @param owner       the player who requested the bot
     * @param spawnPos    where the bot should appear
     * @return the spawned bot, or null if the player is at the limit
     */
    public static ZelusBotPlayer spawnForPlayer(Player owner, Position spawnPos) {
        int ownerId    = owner.getUserId();
        List<String> myBots = ownerBots.computeIfAbsent(ownerId, k -> new ArrayList<>());

        // Prune dead/removed bots before checking the limit
        myBots.removeIf(name -> {
            ZelusBotPlayer b = activeBots.get(name);
            return b == null;
        });

        if (myBots.size() >= MAX_BOTS_PER_PLAYER) {
            owner.sendMessage("<col=ff4444>[PK Bot]</col> You already have "
                    + MAX_BOTS_PER_PLAYER + " active bots. Use <col=ffd700>::despawnbots</col> to remove them.");
            return null;
        }

        // Pick a spawn tile adjacent to the player
        Position pos = nearbyTile(spawnPos);

        String username = "PK_Bot_" + botCounter.getAndIncrement();

        ZelusBotPlayer bot;
        try {
            bot = new ZelusBotPlayer(username, pos);
        } catch (IllegalStateException ex) {
            owner.sendMessage("<col=ff4444>[PK Bot]</col> Could not spawn a bot — no free player slot.");
            return null;
        }

        bot.ownerUserId = ownerId;
        activeBots.put(username, bot);
        myBots.add(username);

        owner.sendMessage("<col=ffd700>[PK Bot]</col> A PK bot (<col=00ff00>" + username + "</col>) has been spawned."
                + " Use <col=ffd700>::despawnbots</col> to remove it.");
        return bot;
    }

    /**
     * Removes all bots belonging to the given player.
     */
    public static void despawnForPlayer(Player owner) {
        int ownerId    = owner.getUserId();
        List<String> myBots = ownerBots.remove(ownerId);
        if (myBots == null || myBots.isEmpty()) {
            owner.sendMessage("<col=ffd700>[PK Bot]</col> You have no active bots.");
            return;
        }

        int removed = 0;
        for (String name : myBots) {
            ZelusBotPlayer bot = activeBots.remove(name);
            if (bot != null) {
                removeBot(bot);
                removed++;
            }
        }
        owner.sendMessage("<col=ffd700>[PK Bot]</col> Removed " + removed + " PK bot(s).");
    }

    /**
     * Removes all active bots (admin/shutdown use).
     */
    public static void despawnAll() {
        for (ZelusBotPlayer bot : activeBots.values()) {
            removeBot(bot);
        }
        activeBots.clear();
        ownerBots.clear();
    }

    /** Cleanly removes a single bot from the world (deallocates RSProt info). */
    private static void removeBot(ZelusBotPlayer bot) {
        bot.online = false;
        bot.deallocateRSProt();
        World.removePlayer(bot);
    }

    // -----------------------------------------------------------------------
    // Per-tick processing
    // -----------------------------------------------------------------------

    static void processBots() {
        for (ZelusBotPlayer bot : activeBots.values()) {
            try {
                bot.ai.process();
            } catch (Exception ex) {
                // Swallow per-bot exceptions so one bad bot can't break others
            }
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Counts how many active bots the given player owns.
     */
    public static int countBotsForPlayer(Player owner) {
        List<String> myBots = ownerBots.getOrDefault(owner.getUserId(), List.of());
        myBots.removeIf(name -> !activeBots.containsKey(name));
        return myBots.size();
    }

    /**
     * Returns an adjacent tile next to {@code center}, trying all 4 cardinal
     * directions. Falls back to the center tile itself if all are invalid.
     */
    private static Position nearbyTile(Position center) {
        int[][] offsets = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] off : offsets) {
            int nx = center.getX() + off[0];
            int ny = center.getY() + off[1];
            if (nx > 0 && ny > 0) {
                return new Position(nx, ny, center.getZ());
            }
        }
        return center.copy();
    }
}
