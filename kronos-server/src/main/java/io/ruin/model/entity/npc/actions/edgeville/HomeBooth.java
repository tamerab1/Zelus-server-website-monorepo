package io.ruin.model.entity.npc.actions.edgeville;

import io.ruin.model.map.Tile;

/**
 * The home Grand Exchange booth (user, 2026-11-14): its loc 10060 is breakroutefinding, so LocType.load drops its clipping
 * and the four diagonal corner pieces (type 9) were walkable: talking to a banker from the south stood the player inside
 * the counter at 3095,3496. The middle is blocked by the map's floor flags; this blocks the corners too, so the route to a
 * banker / clerk ends outside the counter.
 */
public final class HomeBooth {

	private HomeBooth() {
	}

	private static final int[][] CORNERS = { { 3095, 3496 }, { 3095, 3499 }, { 3098, 3496 }, { 3098, 3499 } };

	public static void register() {
		for (int[] c : CORNERS)
			Tile.get(c[0], c[1], 0, true).flagUnmovable();
	}
}
