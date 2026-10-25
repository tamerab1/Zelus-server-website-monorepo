package io.ruin.model.activities.bosses;

import io.ruin.model.World;
import io.ruin.model.entity.npc.NPC;
import io.ruin.model.entity.player.Player;
import io.ruin.model.map.Bounds;
import io.ruin.model.map.Position;
import io.ruin.model.map.dynamic.DynamicMap;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * The global / vote / donation boss cavern (2026-10-20): the big cave across regions 10061 and 10317 (the four Blue
 * Fires that stood at 2559-2560, 4959-4960 removed, its floor levelled).
 * <p>
 * Each kind of event boss fights in its own copy of it ({@link Arena}): the global boss in the real cavern, the vote
 * and donation bosses each in a persistent instance of it, as they had their own instanced arena before the cavern.
 * When all three spawned on the same centre tile of the one cavern, a global boss could appear on top of a live vote
 * or donation boss (and vice versa).
 * <p>
 * The cavern spans two regions, so rewards must not go by "players in the boss's region" (a fighter standing across
 * the 2560 line would get nothing): {@link #players} is everyone inside the boss's arena.
 */
@Slf4j
public final class EventBossArea {

	/** The real cavern's teleport tile. */
	public static final Position TELEPORT = new Position(2547, 4957, 0);
	/** The middle of the cavern: the corner where the four Blue Fires (2559-2560, 4959-4960) met. */
	public static final int CENTRE_X = 2560, CENTRE_Y = 4960;
	/** Both regions (10061 + 10317), plane 0. */
	public static final Bounds BOUNDS = new Bounds(2496, 4928, 2623, 4991, 0);
	public static final int[] REGIONS = {10061, 10317};

	/** One isolated cavern per kind of event boss. */
	public enum Arena {
		/** ::gb -- the real cavern */
		GLOBAL,
		/** ::vb -- an instance of the cavern */
		VOTE,
		/** ::db -- an instance of the cavern */
		DONATION;

		private DynamicMap map;

		private Position convert(Position p) {
			return map == null ? p : map.convertPosition(p);
		}

		/** Where ::gb / ::vb / ::db land. */
		public Position teleport() {
			return convert(TELEPORT);
		}

		/** A boss's spawn: its tiles centred on the cavern's middle for its size. */
		public Position spawnFor(int npcId) {
			int size = Math.max(1, io.ruin.cache.NPCType.get(npcId).size);
			return convert(new Position(CENTRE_X - (size + 1) / 2, CENTRE_Y - (size + 1) / 2, 0));
		}

		public Bounds bounds() {
			if (map == null)
				return BOUNDS;
			Position sw = convert(new Position(BOUNDS.swX, BOUNDS.swY, 0)), ne = convert(new Position(BOUNDS.neX, BOUNDS.neY, 0));
			return new Bounds(sw.getX(), sw.getY(), ne.getX(), ne.getY(), 0);
		}

		public boolean contains(Position p) {
			return p != null && p.inBounds(bounds());
		}
	}

	private EventBossArea() {
	}

	/** Builds the vote and donation instances; call once at startup, before any event boss spawns. */
	public static void init() throws DynamicMap.DynamicMapBuildException {
		for (Arena a : new Arena[]{Arena.VOTE, Arena.DONATION}) {
			if (a.map == null)
				a.map = new DynamicMap().build(BOUNDS).persistent(true);
		}
		log.info("Event boss arenas: global {}, vote {}, donation {}", Arena.GLOBAL.teleport(), Arena.VOTE.teleport(), Arena.DONATION.teleport());
	}

	/** The global boss's spawn (the real cavern). */
	public static Position spawnFor(int npcId) {
		return Arena.GLOBAL.spawnFor(npcId);
	}

	/** In any of the event boss arenas (cannon, pet, clue and gamemode rules apply to all three). */
	public static boolean isIn(Position p) {
		for (Arena a : Arena.values())
			if (a.contains(p))
				return true;
		return false;
	}

	public static boolean isIn(Player player) {
		return isIn(player.getPosition());
	}

	/** The arena a boss is in (GLOBAL if it's in none). */
	public static Arena arenaOf(NPC npc) {
		for (Arena a : Arena.values())
			if (a.contains(npc.getPosition()))
				return a;
		return Arena.GLOBAL;
	}

	/** Players in the boss's arena with it (its rewards / attacks go to them). */
	public static List<Player> players(NPC npc) {
		Arena arena = arenaOf(npc);
		List<Player> list = new ArrayList<>();
		for (Player p : World.players())
			if (p != null && p.isOnline() && arena.contains(p.getPosition()) && p.getHeight() == npc.getHeight())
				list.add(p);
		return list;
	}
}
