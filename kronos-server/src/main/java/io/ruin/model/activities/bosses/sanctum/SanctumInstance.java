package io.ruin.model.activities.bosses.sanctum;

import io.ruin.model.World;
import io.ruin.model.activities.bosses.variants.BossVariant;
import io.ruin.model.entity.npc.NPC;
import io.ruin.model.entity.player.Player;
import io.ruin.model.entity.shared.listeners.DeathListener;
import io.ruin.model.inter.Interface;
import io.ruin.model.inter.ToplevelComponent;
import io.ruin.model.map.Direction;
import io.ruin.model.map.Position;
import io.ruin.model.map.dynamic.DynamicMap;
import io.ruin.model.map.object.GameObject;
import io.ruin.model.map.object.actions.ObjectAction;
import io.ruin.model.var.VarPlayerRepository;


/**
 * One player's private copy of the Sanctum map: the de-snowed God Wars Dungeon copy in regions 7850/7851/8106/8107
 * (.dev/cache-restore-tool/BuildSanctumMap: obsidian floors, lava instead of ice, recoloured rocks, no snow specks and
 * no wind-sound emitters). Wing coordinates stay in God Wars terms and are shifted onto it by {@link #convert}.
 * <ul>
 * <li>Built on the first wing portal use; later portal uses just move the player to that wing.</li>
 * <li>Only the Sanctum guardians live here (a map copy brings no npcs).</li>
 * <li>Guardian kills by the owner count for their wing (capped at {@link #MAX_KC}); the wing's boss door costs
 * {@link #KC_REQUIRED} of them (re-entry is free until that boss dies).</li>
 * <li>The bosses are spawned with the copy, centred in their rooms, so they can be seen from the wing. They wander at
 * most {@link #BOSS_LEASH} tiles and only fight players inside their room. One boss per entry: after a kill the next
 * one only appears once the player has left the room (at least {@link #BOSS_RESPAWN_TICKS} later), and the door costs
 * the kill count again.</li>
 * <li>Kill counts show on the God Wars overlay (interface 406, relabelled). Its timer script hides it outside the real
 * God Wars Dungeon unless varbit {@link #GWD_OVERLAY_VARBIT} is set, so that is held at 1 while inside.</li>
 * <li>Leaving the copy (teleport, death, logout) destroys it; logging out inside puts the player back at the hub.</li>
 * </ul>
 */
public final class SanctumInstance {

	static final int KC_REQUIRED = 10, MAX_KC = 40;
	private static final int BOSS_LEASH = 3, BOSS_RESPAWN_TICKS = 25;
	private static final int GWD_OVERLAY_VARBIT = 11709;

	private static final String KEY = "sanctum_instance";
	private static final int[] SOURCE_REGIONS = {7850, 7851, 8106, 8107}; // sw, nw, se, ne
	private static final int MAX_PLANE = 3;
	/** God Wars Dungeon south-west corner (where the wing coordinates come from) and the Sanctum map's. */
	private static final int GWD_BASE_X = 2816, GWD_BASE_Y = 5248, SANCTUM_BASE_X = 1920, SANCTUM_BASE_Y = 10880;
	private static final int EXIT_PORTAL = 4389;

	/* God Wars overlay (interface 406) components */
	private static final int OVERLAY_TITLE = 8;
	private static final int[] OVERLAY_NAMES = {9, 10, 11, 12, 13}, OVERLAY_COUNTS = {14, 15, 16, 17, 18};
	/** Overlay row per god: Armadyl, Bandos, Saradomin, Zamorak, Ancient. */
	private static final SanctumWing[] OVERLAY_ROWS = {SanctumWing.ARMADYL, SanctumWing.BANDOS, null, SanctumWing.ZAMORAK};

	private final Player owner;
	private final DynamicMap map;
	private final int[] kills = new int[SanctumWing.values().length];
	private final NPC[] bosses = new NPC[SanctumWing.values().length];
	/** Paid the door for the current boss (cleared when it dies). */
	private final boolean[] paid = new boolean[SanctumWing.values().length];
	private boolean destroyed;

	private SanctumInstance(Player owner, DynamicMap map) {
		this.owner = owner;
		this.map = map;
	}

	/** Hub portal: into (or across) the player's private Sanctum, at that wing. */
	static void enter(Player player, SanctumWing wing) {
		SanctumInstance current = player.get(KEY);
		if (current != null && current.map.isIn(player)) {
			current.moveTo(wing);
			return;
		}
		DynamicMap map;
		try {
			map = new DynamicMap()
					.buildSw(SOURCE_REGIONS[0], MAX_PLANE)
					.buildNw(SOURCE_REGIONS[1], MAX_PLANE)
					.buildSe(SOURCE_REGIONS[2], MAX_PLANE)
					.buildNe(SOURCE_REGIONS[3], MAX_PLANE);
		} catch (DynamicMap.DynamicMapBuildException e) {
			player.sendMessage("The Sanctum is unstable right now. Please try again in a moment.");
			return;
		}
		map.makeDynamicMapMultiCombat();
		SanctumInstance instance = new SanctumInstance(player, map);
		player.set(KEY, instance);
		instance.populate();
		instance.moveTo(wing);
		player.currentDynamicMap = map;
		player.inDynamicMap = true;
		VarPlayerRepository.varpbit(GWD_OVERLAY_VARBIT, false).set(player, 1);
		player.openInterface(ToplevelComponent.WILDERNESS_OVERLAY, Interface.GODWARS);
		instance.updateOverlay();
		// again once the teleport has landed (the overlay's load script fills in God Wars' own counts)
		player.addEvent(e -> {
			e.delay(2);
			if (!instance.destroyed)
				instance.updateOverlay();
		});
		map.assignListener(player).onExit((p, logout) -> {
			instance.destroyed = true;
			VarPlayerRepository.varpbit(GWD_OVERLAY_VARBIT, false).set(p, 0);
			if (logout)
				p.getMovement().teleport(Sanctum.HUB);
			else
				p.closeInterface(ToplevelComponent.WILDERNESS_OVERLAY);
			p.currentDynamicMap = null;
			p.inDynamicMap = false;
			if (p.get(KEY) == instance)
				p.set(KEY, null);
			map.destroy();
		});
	}

	private void moveTo(SanctumWing wing) {
		owner.getMovement().teleport(convert(wing.arrival));
		owner.sendMessage("<col=8A0707>[Sanctum]</col> Slay " + KC_REQUIRED + " " + wing.guardianName
				+ " guardians to open the " + wing.bossName + " chamber.");
	}

	/** Guardians, bosses, exit portals, boss door actions. */
	private void populate() {
		for (SanctumWing wing : SanctumWing.values()) {
			spawnBoss(wing);
			for (int[] spawn : wing.guardianSpawns) {
				Position at = convert(spawn);
				NPC guardian = new NPC(wing.guardianId).spawn(at.getX(), at.getY(), at.getZ(), Direction.SOUTH, 3);
				guardian.deathEndListener = (DeathListener.SimplePlayer) killer -> guardianSlain(killer, wing);
				map.addNpc(guardian);
			}
			// exit portal next to the arrival tile
			Position exit = convert(new int[]{wing.arrival[0] + 1, wing.arrival[1] + 2});
			GameObject portal = GameObject.spawn(EXIT_PORTAL, exit.getX(), exit.getY(), exit.getZ(), 10, 0);
			map.addSpawnedObject(portal);
			ObjectAction.register(portal, 1, (p, obj) -> p.getMovement().teleport(Sanctum.HUB));
			Position door = convert(wing.door);
			ObjectAction.register(wing.doorId, door.getX(), door.getY(), door.getZ(), 1, (p, obj) -> useDoor(p, wing));
		}
	}

	private void guardianSlain(Player killer, SanctumWing wing) {
		if (killer != owner)
			return;
		int w = wing.ordinal();
		kills[w] = Math.min(kills[w] + 1, MAX_KC);
		updateOverlay();
	}

	private void useDoor(Player player, SanctumWing wing) {
		if (player != owner)
			return;
		Position inside = convert(wing.inside), outside = convert(wing.outside);
		int w = wing.ordinal();
		NPC boss = bosses[w];
		boolean isInside = player.getPosition().distance(inside) < player.getPosition().distance(outside);
		if (isInside) {
			player.getMovement().teleport(outside);
			if (boss != null && !boss.isRemoved() && boss.getCombat().getTarget() == player)
				boss.getCombat().reset(); // the boss lets go as soon as you leave its room
			return;
		}
		if (!paid[w]) {
			if (kills[w] < KC_REQUIRED) {
				player.sendMessage("<col=ff0000>The barrier remains sealed. You must defeat " + KC_REQUIRED + " Guardians to enter ("
						+ kills[w] + "/" + KC_REQUIRED + ").</col>");
				return;
			}
			kills[w] -= KC_REQUIRED;
			paid[w] = true;
			updateOverlay();
			player.sendMessage("<col=8A0707>[Sanctum]</col> The door devours the essence of " + KC_REQUIRED + " " + wing.guardianName
					+ " guardians.");
		}
		player.getMovement().teleport(inside);
	}

	/** Centred on the room's centre tile (by the boss's footprint), leashed to it, only fighting inside the room. */
	private void spawnBoss(SanctumWing wing) {
		if (destroyed)
			return;
		Position centre = convert(wing.roomCentre);
		int size = Math.max(1, io.ruin.cache.NPCType.get(wing.bossId).size);
		int x = centre.getX() - (size - 1) / 2, y = centre.getY() - (size - 1) / 2;
		NPC boss = new NPC(wing.bossId).spawn(x, y, centre.getZ(), Direction.SOUTH, BOSS_LEASH);
		boss.getCombat().setAllowRespawn(false);
		Position low = convert(new int[]{wing.room[0], wing.room[1]}), high = convert(new int[]{wing.room[2], wing.room[3]});
		if (boss.getCombat() instanceof BossVariant variant)
			variant.leash(centre, BOSS_LEASH, e -> e.getAbsX() >= low.getX() && e.getAbsX() <= high.getX()
					&& e.getAbsY() >= low.getY() && e.getAbsY() <= high.getY() && e.getHeight() == centre.getZ());
		boss.deathEndListener = (entity, killer, hit) -> {
			paid[wing.ordinal()] = false;
			World.startEvent(e -> {
				e.delay(BOSS_RESPAWN_TICKS);
				// one boss per entry: wait until the owner has left the room
				while (!destroyed && inRoom(wing, owner))
					e.delay(2);
				spawnBoss(wing);
			});
		};
		map.addNpc(boss);
		bosses[wing.ordinal()] = boss;
	}

	private boolean inRoom(SanctumWing wing, Player player) {
		Position low = convert(new int[]{wing.room[0], wing.room[1]}), high = convert(new int[]{wing.room[2], wing.room[3]});
		return player.isOnline() && player.getAbsX() >= low.getX() && player.getAbsX() <= high.getX()
				&& player.getAbsY() >= low.getY() && player.getAbsY() <= high.getY() && player.getHeight() == low.getZ();
	}

	private void updateOverlay() {
		var ps = owner.getPacketSender();
		ps.sendString(Interface.GODWARS, OVERLAY_TITLE, "<col=ff981f>Sanctum Essence</col>");
		for (int row = 0; row < OVERLAY_NAMES.length; row++) {
			SanctumWing wing = row < OVERLAY_ROWS.length ? OVERLAY_ROWS[row] : null;
			boolean used = row < OVERLAY_ROWS.length;
			ps.setHidden(Interface.GODWARS, OVERLAY_NAMES[row], !used);
			ps.setHidden(Interface.GODWARS, OVERLAY_COUNTS[row], !used);
			if (!used)
				continue;
			if (wing == null) {
				ps.sendString(Interface.GODWARS, OVERLAY_NAMES[row], "<col=9a9a9a>Reserved</col>");
				ps.sendString(Interface.GODWARS, OVERLAY_COUNTS[row], "<col=9a9a9a>0/" + KC_REQUIRED + "</col>");
			} else {
				int kc = kills[wing.ordinal()];
				String colour = kc >= KC_REQUIRED ? "<col=00ff00>" : "<col=ffffff>";
				ps.sendString(Interface.GODWARS, OVERLAY_NAMES[row], "<col=00ff00>" + wing.guardianName + "</col>");
				ps.sendString(Interface.GODWARS, OVERLAY_COUNTS[row], colour + kc + "/" + KC_REQUIRED + "</col>");
			}
		}
	}

	/** God Wars coordinates -> the Sanctum map -> this player's copy. */
	private Position convert(int[] xy) {
		int x = xy[0] - GWD_BASE_X + SANCTUM_BASE_X, y = xy[1] - GWD_BASE_Y + SANCTUM_BASE_Y;
		return new Position(map.convertX(x), map.convertY(y), SanctumWing.PLANE);
	}

}
