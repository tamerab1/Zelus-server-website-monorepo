package io.ruin.model.activities.bosses.solarmane;

import io.ruin.HooksV2;
import io.ruin.cache.NPCType;
import io.ruin.model.World;
import io.ruin.model.entity.npc.NPC;
import io.ruin.model.entity.player.Player;
import io.ruin.model.map.Direction;
import io.ruin.model.map.Position;
import io.ruin.model.map.Tile;
import io.ruin.model.map.object.GameObject;
import io.ruin.model.map.dynamic.DynamicMap;
import io.ruin.network.incoming.handlers.CommandHandler;
import lombok.extern.slf4j.Slf4j;

/**
 * Solarmane, the sun-lion boss (2026-10-31): npc 30611 (cache stage 12 PatchSolarmane: the Sabre-toothed kyatt with the
 * sunfire gold / royal crimson textures 478 / 479). Each fight is a private instance of region 12100 (entry 3019,4408 facing south;
 * the boss at 3018,4397 facing north), entered with ::solarmane and torn down when the player leaves, dies, teleports or logs out
 * (the customs27 pattern). Mechanics: {@link SolarmaneCombat}; stats npcs/combat/solarmane.json; loot
 * npcs/drops/newDrops/30611.json; kill count: KillCounter. The Solarmane cub pet (item 60407, npc 30612) is a
 * 1/500 unique in the drop table.
 */
@Slf4j
public final class Solarmane {

	private Solarmane() {
	}

	public static final int BOSS = 30611;
	// 2026-11-04: moved from the Vorkath island (region 9023) to region 12100, the spots the user picked
	private static final int REGION = 12100, RESPAWN_TICKS = 10;
	private static final Position BOSS_SPAWN = new Position(3018, 4397, 0), PLAYER_ENTRY = new Position(3019, 4408, 0);
	private static final Direction BOSS_FACING = Direction.NORTH, PLAYER_FACING = Direction.SOUTH;
	private static final Position OUTSIDE = new Position(3087, 3496, 0); // home, for logouts inside

	public static void register() {
		NPCType.registerCombat(SolarmaneCombat.class, BOSS);
		// the healer cubs walk through each other and players (a body in the way froze them on the spot)
		NPCType.get(SolarmaneCombat.CUB).ignoreOccupiedTiles = true;
		CommandHandler.hooks.register(CommandHandler.Hook.Handle.class, Solarmane::command);
	}

	private static HooksV2.Result command(CommandHandler.Hook.Handle cmd) {
		if (!cmd.command().equals("solarmane"))
			return HooksV2.Result.Pass;
		enter(cmd.player());
		return HooksV2.Result.Return;
	}

	/** The boss in its arena; a kill rolls the pet and brings the next one {@link #RESPAWN_TICKS} later. */
	private static NPC spawnBoss(DynamicMap map) {
		NPC boss = new NPC(BOSS).spawn(map.convertX(BOSS_SPAWN.getX()), map.convertY(BOSS_SPAWN.getY()), 0, BOSS_FACING, 0);
		boss.getCombat().setAllowRespawn(false);
		map.addNpc(boss);
		((SolarmaneCombat) boss.getCombat()).map = map;
		// 2026-11-04: the cub is a drop-table unique now (newDrops/30611.json, 1/500), no separate roll
		boss.deathEndListener = (entity, killer, killHit) -> {
			World.startEvent(e -> {
				e.delay(RESPAWN_TICKS);
				if (map.swRegion == null) // the player left: the arena is gone
					return;
				spawnBoss(map).graphics(1039);
			});
		};
		return boss;
	}

	/**
	 * An instance is built from the cache map only, so the objects the server spawns into region 12100 at boot
	 * (data/objects/spawns, e.g. dragon_eggs.json) would be missing; copies each one into the arena (2026-11-04).
	 */
	private static void copySpawnedObjects(DynamicMap map) {
		int baseX = (REGION >> 8) << 6, baseY = (REGION & 0xff) << 6;
		for (int z = 0; z < 4; z++)
			for (int x = baseX; x < baseX + 64; x++)
				for (int y = baseY; y < baseY + 64; y++) {
					Tile tile = Tile.get(x, y, z, false);
					if (tile == null || tile.gameObjects == null)
						continue;
					for (GameObject o : new java.util.ArrayList<>(tile.gameObjects))
						if (o.isSpawned() && o.getId() != -1 && !fillsGap(o, x, y))
							GameObject.spawn(o.getId(), map.convertX(x), map.convertY(y), z, o.getType(), o.getDirection());
				}
	}

	private static final int DRAGON_EGG = 12257;

	/** The eggs that stood in the room's two openings, closed by map walls since 2026-11-08 (cache stage 24). */
	private static boolean fillsGap(GameObject o, int x, int y) {
		return o.getId() == DRAGON_EGG && ((y == 4391 && x >= 3018 && x <= 3020) || (x == 3027 && y >= 4396 && y <= 4403));
	}

	/** Builds the player's private arena and brings them in. */
	public static void enter(Player player) {
		if (player.wildernessLevel > 0 || player.isLocked()) {
			player.sendMessage("You can't do that from here.");
			return;
		}
		if (player.isInstanced && player.currentDynamicMap != null) {
			player.sendMessage("You are already inside an instance.");
			return;
		}
		player.isInstanced = false;
		DynamicMap map;
		try {
			map = new DynamicMap().build(REGION, 1);
		} catch (DynamicMap.DynamicMapBuildException e) {
			log.error("solarmane: couldn't build the arena", e);
			player.sendMessage("The arena can't be opened right now. Try again in a moment.");
			return;
		}
		copySpawnedObjects(map);
		spawnBoss(map);
		player.getMovement().startTeleport(event -> {
			player.animate(3864);
			player.graphics(1039);
			event.delay(2);
			player.isInstanced = true;
			player.currentDynamicMap = map;
			player.inDynamicMap = true;
			player.getMovement().teleport(map.convertX(PLAYER_ENTRY.getX()), map.convertY(PLAYER_ENTRY.getY()), 0);
			player.face(PLAYER_FACING);
			player.sendMessage("<col=7f0000>Solarmane, the sun-lion, awaits you.</col> Leave by teleporting out.");
			map.assignListener(player).onExit((p, logout) -> {
				if (logout)
					p.getMovement().teleport(OUTSIDE);
				p.currentDynamicMap = null;
				p.inDynamicMap = false;
				p.isInstanced = false;
				map.destroy();
			});
		});
	}
}
