package io.ruin.model.activities.bosses.customs27;

import io.ruin.HooksV2;
import io.ruin.cache.NPCType;
import io.ruin.model.entity.npc.NPC;
import io.ruin.model.entity.player.Player;
import io.ruin.model.map.Direction;
import io.ruin.model.map.Position;
import io.ruin.model.map.dynamic.DynamicMap;
import io.ruin.network.incoming.handlers.CommandHandler;
import lombok.extern.slf4j.Slf4j;

/**
 * The customs27 bosses (2026-10-27): Vorgath the Shadowrider (20000) and Baelgor the Felreaver (20001). Each fight is a
 * private instance of the Vorkath island (region 9023, the open lava-lit plateau), entered with ::vorgath / ::baelgor and
 * torn down when the player leaves, dies, teleports or logs out (the Vorkath / Mad Angel pattern). Stats and animations:
 * npcs/combat/Customs27_bosses.json; kill counts: KillCounter (by id). No drops / collection log yet (user, 2026-10-27).
 */
@Slf4j
public final class Customs27Bosses {

	private Customs27Bosses() {
	}

	public static final int VORGATH = 20000, BAELGOR = 20001;
	private static final int REGION = 9023;
	private static final Position BOSS_SPAWN = new Position(2269, 4062, 0), PLAYER_ENTRY = new Position(2272, 4054, 0);
	private static final Position OUTSIDE = new Position(3087, 3496, 0); // home, for logouts inside

	public static void register() {
		NPCType.registerCombat(VorgathCombat.class, VORGATH);
		NPCType.registerCombat(BaelgorCombat.class, BAELGOR);
		CommandHandler.hooks.register(CommandHandler.Hook.Handle.class, Customs27Bosses::command);
	}

	private static HooksV2.Result command(CommandHandler.Hook.Handle cmd) {
		// staff only: the bosses have no drops yet (and command hooks run before any rank check)
		if (!cmd.player().isAdmin())
			return HooksV2.Result.Pass;
		switch (cmd.command()) {
			case "vorgath" -> enter(cmd.player(), VORGATH);
			case "baelgor" -> enter(cmd.player(), BAELGOR);
			case "baelgorgust" -> { // test: every attack is the Hellfire Gust (19014), to see if that anim is what breaks him
				BaelgorCombat.forceGust = !BaelgorCombat.forceGust;
				cmd.player().sendMessage("Baelgor gusts every attack: " + BaelgorCombat.forceGust);
			}
			default -> {
				return HooksV2.Result.Pass;
			}
		}
		return HooksV2.Result.Return;
	}

	/** The boss in its arena; a kill brings the next one {@link #RESPAWN_TICKS} later while the arena is still in use. */
	private static NPC spawnBoss(DynamicMap map, int bossId) {
		NPC boss = new NPC(bossId).spawn(map.convertX(BOSS_SPAWN.getX()), map.convertY(BOSS_SPAWN.getY()), 0, Direction.SOUTH, 0);
		boss.getCombat().setAllowRespawn(false);
		map.addNpc(boss);
		boss.deathEndListener = (entity, killer, killHit) -> io.ruin.model.World.startEvent(e -> {
			e.delay(RESPAWN_TICKS);
			if (map.swRegion == null) // the player left: the arena is gone
				return;
			NPC next = spawnBoss(map, bossId);
			next.graphics(1039);
		});
		return boss;
	}

	private static final int RESPAWN_TICKS = 10;

	/** Builds the boss's private arena and brings the player in. */
	public static void enter(Player player, int bossId) {
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
			log.error("customs27: couldn't build the arena for {}", bossId, e);
			player.sendMessage("The arena can't be opened right now. Try again in a moment.");
			return;
		}
		NPC boss = spawnBoss(map, bossId);
		player.getMovement().startTeleport(event -> {
			player.animate(3864);
			player.graphics(1039);
			event.delay(2);
			player.isInstanced = true;
			player.currentDynamicMap = map;
			player.inDynamicMap = true;
			player.getMovement().teleport(map.convertX(PLAYER_ENTRY.getX()), map.convertY(PLAYER_ENTRY.getY()), 0);
			player.sendMessage("<col=ef1020>" + boss.getDef().name + " awaits you.</col> Leave by teleporting out.");
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
