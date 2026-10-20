package io.ruin.model.activities.bosses;

import io.ruin.model.World;
import io.ruin.model.entity.npc.NPC;
import io.ruin.model.entity.player.Player;
import io.ruin.model.map.Bounds;
import io.ruin.model.map.Position;

import java.util.ArrayList;
import java.util.List;

/**
 * The global / vote / donation boss cavern (2026-10-20): the big cave across regions 10061 and 10317 (the four Blue
 * Fires that stood at 2559-2560, 4959-4960 removed, its floor levelled). ::gb, ::vb and ::db all teleport here; each
 * event boss has its own spawn on the open floor. Before, the vote and donation bosses ran in instanced copies of the
 * 2900,3616 arena and the global boss at 2912,3614.
 * <p>
 * The cavern spans two regions, so rewards must not go by "players in the boss's region" (a fighter standing across
 * the 2560 line would get nothing): {@link #players} is everyone inside {@link #BOUNDS}.
 */
public final class EventBossArea {

	public static final Position TELEPORT = new Position(2547, 4957, 0);
	/** The middle of the cavern: the corner where the four Blue Fires (2559-2560, 4959-4960) met. */
	public static final int CENTRE_X = 2560, CENTRE_Y = 4960;
	/** Both regions (10061 + 10317), plane 0. */
	public static final Bounds BOUNDS = new Bounds(2496, 4928, 2623, 4991, 0);
	public static final int[] REGIONS = {10061, 10317};

	private EventBossArea() {
	}

	/** Where an event boss spawns: its tiles centred on {@link #CENTRE_X}, {@link #CENTRE_Y} for its size. */
	public static Position spawnFor(int npcId) {
		int size = Math.max(1, io.ruin.cache.NPCType.get(npcId).size);
		return new Position(CENTRE_X - (size + 1) / 2, CENTRE_Y - (size + 1) / 2, 0);
	}

	public static boolean isIn(Position p) {
		return p != null && p.inBounds(BOUNDS);
	}

	public static boolean isIn(Player player) {
		return isIn(player.getPosition());
	}

	/** Players in the cavern with the boss (its rewards / attacks go to them). */
	public static List<Player> players(NPC npc) {
		List<Player> list = new ArrayList<>();
		for (Player p : World.players())
			if (p != null && p.isOnline() && isIn(p.getPosition()) && p.getHeight() == npc.getHeight())
				list.add(p);
		return list;
	}
}
