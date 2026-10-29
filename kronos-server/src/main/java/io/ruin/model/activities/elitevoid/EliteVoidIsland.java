package io.ruin.model.activities.elitevoid;

import io.ruin.HooksV2;
import io.ruin.cache.NPCType;
import io.ruin.model.World;
import io.ruin.model.entity.npc.NPC;
import io.ruin.model.map.Bounds;
import io.ruin.model.map.Direction;
import io.ruin.model.map.Position;
import io.ruin.model.map.Tile;
import io.ruin.model.map.dynamic.DynamicMap;
import io.ruin.model.skills.magic.spells.modern.ModernTeleport;
import io.ruin.network.incoming.handlers.CommandHandler;
import lombok.extern.slf4j.Slf4j;

/**
 * The Elite Void island (2026-10-29): a second way to Elite Void beside Pest Control, for when there aren't enough
 * players to start a game. A persistent copy of the Pest Control battle island (region 10536), so the real island stays
 * free for the minigame, held by Elite Void Knights (20002 melee, 20003 ranger, 20004 mage; cache stage 8
 * PatchEliteVoid) that drop the Elite Void pieces (npcs/drops/newDrops/2000x.json). Reached from the Teleports
 * interface (Monsters). Pest Control's own rewards and the Elite Void exchange are untouched.
 */
@Slf4j
public final class EliteVoidIsland {

	private EliteVoidIsland() {
	}

	public static final int MELEE = 20002, RANGER = 20003, MAGE = 20004;
	/** The Pest Control battle island, plane 0: region 10536 (2624..2687, 2560..2623). */
	private static final int REGION = 10536;
	private static final Bounds BOUNDS = new Bounds(2624, 2560, 2687, 2623, 0);
	/** The landing jetty, where the Pest Control boat lands its squads. */
	private static final Position LANDING = new Position(2657, 2610, 0);
	/** Along the paths from the jetty to the four portals and round the knight's compound: {x, y}. */
	private static final int[][] SPAWNS = {
			{ 2650, 2600 }, { 2664, 2600 }, { 2645, 2593 }, { 2668, 2593 }, { 2638, 2596 }, { 2674, 2590 },
			{ 2633, 2591 }, { 2679, 2593 }, { 2648, 2580 }, { 2665, 2579 }, { 2645, 2572 }, { 2668, 2573 },
			{ 2656, 2576 }, { 2641, 2604 }, { 2672, 2604 },
	};
	private static final int WALK_RANGE = 4;

	private static DynamicMap map;

	public static void register() {
		NPCType.registerCombat(EliteVoidKnightCombat.class, MELEE, RANGER, MAGE);
		CommandHandler.hooks.register(CommandHandler.Hook.Handle.class, EliteVoidIsland::command);
		World.startEvent(e -> {
			e.delay(5); // after the world and its maps are up
			ensure();
		});
	}

	private static HooksV2.Result command(CommandHandler.Hook.Handle cmd) {
		if (!cmd.command().equals("elitevoid"))
			return HooksV2.Result.Pass;
		ModernTeleport.teleport(cmd.player(), teleport()); // everyone; the usual teleport rules (wilderness, combat) apply
		return HooksV2.Result.Return;
	}

	/** Builds the island and its knights once. */
	private static synchronized boolean ensure() {
		if (map != null)
			return true;
		try {
			// by region, as Pest Control builds it: build(Bounds) goes through build(List), which doesn't record the
			// source base that convertPosition needs (the landing and spawns then fall outside the copy, into the void)
			map = new DynamicMap().build(REGION, 0).persistent(true);
		} catch (DynamicMap.DynamicMapBuildException e) {
			log.error("Elite Void island: couldn't build the island", e);
			return false;
		}
		int[] kinds = { MELEE, RANGER, MAGE };
		int n = 0;
		for (int i = 0; i < SPAWNS.length; i++) {
			Position at = walkable(map.convertPosition(new Position(SPAWNS[i][0], SPAWNS[i][1], 0)));
			if (at == null)
				continue;
			NPC knight = new NPC(kinds[i % kinds.length]).spawn(at.getX(), at.getY(), at.getZ(), Direction.SOUTH, WALK_RANGE);
			map.addNpc(knight);
			n++;
		}
		log.info("Elite Void island: {} knights, landing {}", n, teleport());
		return true;
	}

	/** Where the teleport lands: the jetty of the copy. */
	public static Position teleport() {
		return ensure() ? map.convertPosition(LANDING) : LANDING;
	}

	/** The nearest free tile within 3 (the island's walls and barricades cut through some spots). */
	private static Position walkable(Position p) {
		for (int r = 0; r <= 3; r++)
			for (int dx = -r; dx <= r; dx++)
				for (int dy = -r; dy <= r; dy++) {
					if (Math.max(Math.abs(dx), Math.abs(dy)) != r)
						continue;
					// no tile record = nothing on it (open floor); water and walls carry clipping, so they have one
					Tile t = Tile.get(p.getX() + dx, p.getY() + dy, p.getZ(), false);
					if (t == null || t.isTileFree())
						return new Position(p.getX() + dx, p.getY() + dy, p.getZ());
				}
		return null;
	}

	public static boolean isIn(Position p) {
		if (map == null || p == null)
			return false;
		Position sw = map.convertPosition(new Position(BOUNDS.swX, BOUNDS.swY, 0));
		Position ne = map.convertPosition(new Position(BOUNDS.neX, BOUNDS.neY, 0));
		return p.getX() >= sw.getX() && p.getX() <= ne.getX() && p.getY() >= sw.getY() && p.getY() <= ne.getY() && p.getZ() == 0;
	}
}
