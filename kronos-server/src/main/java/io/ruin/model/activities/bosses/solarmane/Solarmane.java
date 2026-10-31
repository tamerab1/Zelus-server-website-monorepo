package io.ruin.model.activities.bosses.solarmane;

import io.ruin.HooksV2;
import io.ruin.api.utils.Random;
import io.ruin.cache.NPCType;
import io.ruin.model.World;
import io.ruin.model.entity.npc.NPC;
import io.ruin.model.entity.player.Player;
import io.ruin.model.item.actions.impl.pet.Pet;
import io.ruin.model.map.Direction;
import io.ruin.model.map.Position;
import io.ruin.model.map.dynamic.DynamicMap;
import io.ruin.network.incoming.handlers.CommandHandler;
import lombok.extern.slf4j.Slf4j;

/**
 * Solarmane, the sun-lion boss (2026-10-31): npc 30611 (cache stage 12 PatchSolarmane: the Sabre-toothed kyatt with the
 * sunfire gold / royal crimson textures 478 / 479). Each fight is a private instance of the Vorkath island (region 9023,
 * the lava-lit plateau), entered with ::solarmane and torn down when the player leaves, dies, teleports or logs out
 * (the customs27 pattern). Mechanics: {@link SolarmaneCombat}; stats npcs/combat/solarmane.json; loot
 * npcs/drops/newDrops/30611.json; kill count: KillCounter. Each kill rolls the Solarmane cub pet (item 60407, npc
 * 30612) at 1 in {@link #PET_RATE}.
 */
@Slf4j
public final class Solarmane {

	private Solarmane() {
	}

	public static final int BOSS = 30611;
	static final int PET_RATE = 500;
	private static final int REGION = 9023, RESPAWN_TICKS = 10;
	private static final Position BOSS_SPAWN = new Position(2269, 4062, 0), PLAYER_ENTRY = new Position(2272, 4054, 0);
	private static final Position OUTSIDE = new Position(3087, 3496, 0); // home, for logouts inside

	public static void register() {
		NPCType.registerCombat(SolarmaneCombat.class, BOSS);
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
		NPC boss = new NPC(BOSS).spawn(map.convertX(BOSS_SPAWN.getX()), map.convertY(BOSS_SPAWN.getY()), 0, Direction.SOUTH, 0);
		boss.getCombat().setAllowRespawn(false);
		map.addNpc(boss);
		boss.deathEndListener = (entity, killer, killHit) -> {
			if (killer != null && killer.player != null && Random.get(0, PET_RATE - 1) == 0)
				Pet.SOLARMANE_CUB.unlock(killer.player, BOSS);
			World.startEvent(e -> {
				e.delay(RESPAWN_TICKS);
				if (map.swRegion == null) // the player left: the arena is gone
					return;
				spawnBoss(map).graphics(1039);
			});
		};
		return boss;
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
		spawnBoss(map);
		player.getMovement().startTeleport(event -> {
			player.animate(3864);
			player.graphics(1039);
			event.delay(2);
			player.isInstanced = true;
			player.currentDynamicMap = map;
			player.inDynamicMap = true;
			player.getMovement().teleport(map.convertX(PLAYER_ENTRY.getX()), map.convertY(PLAYER_ENTRY.getY()), 0);
			player.sendMessage("<col=ef8a10>Solarmane, the sun-lion, awaits you.</col> Leave by teleporting out.");
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
