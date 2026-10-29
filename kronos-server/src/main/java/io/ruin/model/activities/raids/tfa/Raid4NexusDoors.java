package io.ruin.model.activities.raids.tfa;

import io.ruin.model.map.Position;
import io.ruin.model.map.Tile;
import io.ruin.model.map.object.GameObject;

/**
 * The six Nexus path doors. The map places every one as its open frame, so the server has to set each door's
 * state: closed (63094, no options), ajar (the "_slightly" loc, Enter) or opening (the "_slightly_opened" loc).
 * They're plain loc swaps on the copied map object, not multilocs.
 */
public final class Raid4NexusDoors {

	private Raid4NexusDoors() {
	}

	public static final int LOC_CLOSED = 63094;

	/** {slot, localX, localY, open frame, ajar, opening}, from the Nexus loc list (planes 0 and 1). */
	static final int[][] DOORS = {
			{ 2, 17, 16, 63088, 63089, 63090 }, { 2, 45, 16, 63091, 63092, 63093 },
			{ 3, 17, 29, 63096, 63097, 63098 }, { 3, 45, 29, 63099, 63100, 63101 },
			{ 4, 17, 42, 63102, 63103, 63104 }, { 4, 45, 42, 63105, 63106, 63107 },
	};
	private static final int PLANES = 2;

	/** Every ajar and opening variant, so the click handlers can be registered on them too. */
	public static int[][] variants() {
		return DOORS;
	}

	/** All doors shut: the Guardian still stands. */
	public static void sealAll(Raid4Instance instance, Raid4RoomType room) {
		for (int[] door : DOORS)
			set(instance, room, door, LOC_CLOSED);
	}

	/** The next path's two doors ajar, every other door shut. */
	public static void showChoice(Raid4Instance instance, Raid4RoomType room, int nextSlot) {
		for (int[] door : DOORS)
			set(instance, room, door, door[0] == nextSlot ? door[4] : LOC_CLOSED);
	}

	/** Both doors of the next path swing open (the "_slightly_opened" locs), as after the Guardian's fall. */
	public static void openChoice(Raid4Instance instance, Raid4RoomType room, int nextSlot) {
		for (int[] door : DOORS)
			if (door[0] == nextSlot)
				set(instance, room, door, door[5]);
	}

	/** Plays the chosen door's opening animation. */
	public static void open(Raid4Instance instance, Raid4RoomType room, int slot, Raid4RoomType.Side side) {
		for (int[] door : DOORS) {
			if (door[0] == slot && (door[1] < 32) == (side == Raid4RoomType.Side.WEST))
				set(instance, room, door, door[5]);
		}
	}

	private static void set(Raid4Instance instance, Raid4RoomType room, int[] door, int id) {
		for (int plane = 0; plane < PLANES; plane++) {
			Position pos = instance.local(room, door[1], door[2], plane);
			Tile tile = Tile.get(pos.getX(), pos.getY(), plane, false);
			if (tile == null || tile.gameObjects == null)
				continue;
			for (GameObject obj : tile.gameObjects) {
				if (obj.originalId == door[3]) {
					if (obj.getId() != id)
						obj.setId(id);
					break;
				}
			}
		}
	}
}
