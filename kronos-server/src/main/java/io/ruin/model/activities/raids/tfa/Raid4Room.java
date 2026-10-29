package io.ruin.model.activities.raids.tfa;

import io.ruin.model.entity.npc.NPC;
import io.ruin.model.entity.player.Player;
import io.ruin.model.map.Position;
import io.ruin.model.map.dynamic.DynamicMap;

/**
 * Lifecycle of one encounter, driven by {@link Raid4Controller}:
 * <ol>
 * <li>{@link #init} once the room's map is built: spawn the dormant boss and fixtures;</li>
 * <li>{@link #start} when the fight begins (a "Touch" on the boss, or entry for rooms without one);</li>
 * <li>{@link #tick} every game tick until the room is complete or failed;</li>
 * <li>{@link #cleanup} when the party leaves the room, wipes or the raid ends. Must be safe to call twice.</li>
 * </ol>
 */
public interface Raid4Room {

	Raid4RoomType type();

	void init(Raid4Controller controller, DynamicMap map);

	void start();

	void tick();

	/** Called for every NPC this room spawned when it dies. */
	void onNpcDeath(NPC npc);

	void cleanup();

	boolean isStarted();

	boolean isComplete();

	/** Where members land when they enter the room. */
	Position entryPosition();

	/** Where members who die in this room wait until it's cleared. */
	Position spectatePosition();

	/** "Use" on the room's boss teleporter (loc {@link Raid4Ids#LOC_BOSS_TELEPORTER}). */
	default void useTeleporter(Player player) {
		player.sendMessage("Nothing interesting happens.");
	}

	/** Called when a member enters the room, after the teleport. */
	default void onEnter(Player player) {
	}
}
