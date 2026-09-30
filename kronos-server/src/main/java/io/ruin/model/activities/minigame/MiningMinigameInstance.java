package io.ruin.model.activities.minigame;

import io.ruin.model.entity.npc.NPC;
import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.dialogue.OptionsDialogue;
import io.ruin.model.inter.utils.Option;
import io.ruin.model.map.Bounds;
import io.ruin.model.map.Direction;
import io.ruin.model.map.MapListener;
import io.ruin.model.map.Position;
import io.ruin.model.map.Tile;
import io.ruin.model.map.dynamic.DynamicMap;
import io.ruin.model.map.object.GameObject;
import io.ruin.model.map.object.actions.ObjectAction;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

// Solo, per-player instanced copy of region 13364 (the mining minigame arena David teleports players into),
// so players entering can't see each other -- each gets their own fresh DynamicMap copy of the region.
@Slf4j
public class MiningMinigameInstance {

	private static final int REGION_ID = 13364;
	private static final Bounds BOUNDS = Bounds.fromRegion(REGION_ID);
	private static final Position ENTRY_POSITION = new Position(3346, 3347, 0);
	private static final Position DRACO_ROCK_POSITION = new Position(3351, 3342, 0);
	private static final Position EXIT_PORTAL_POSITION = new Position(3349, 3333, 0);
	private static final Position PRISMATIC_CHEST_POSITION = new Position(3349, 3335, 0);
	private static final Position DRACO_BOSS_POSITION = new Position(3361, 3336, 0);
	private static final Position REAL_WORLD_EXIT_POSITION = new Position(3337, 3348, 0);
	private static final int DRACO_ROCK_ID = 62390;
	private static final int EXIT_PORTAL_ID = 27096;
	private static final int PRISMATIC_CHEST_ID = 62389;
	private static final int DRACO_BOSS_ID = 30560; // see Draco.java's DRACO_IDLE comment -- moved off the >65535 id range
	private static final int DIRECTION_EAST = 3;
	private static final int DRACODUST_ID = 60389;
	private static final int BOSS_ROOM_DOOR_ID = 8958;
	private static final Position BOSS_ROOM_ENTRY_POSITION = new Position(3361, 3339, 0);

	private static final Map<Integer, MiningMinigameInstance> activeInstances = new HashMap<>();

	public static void register() {
		ObjectAction.register(EXIT_PORTAL_ID, 1, (player, obj) -> exit(player));
		// NOTE: object 8958 is a shared, real, already-in-use vanilla door id (Waterbirth Dungeon's
		// two-person door, WaterbirthDungeon.java) -- registering it GLOBALLY by id here would
		// silently hijack/break that unrelated door for every player on the server. Each player's
		// own dynamic-map copy of this door is registered individually, per-GameObject, instead --
		// see the scan in the constructor below.
	}

	private void registerBossRoomDoor() {
		int baseX = map.swRegion.baseX;
		int baseY = map.swRegion.baseY;
		for (int x = 0; x < 64; x++) {
			for (int y = 0; y < 64; y++) {
				GameObject obj = Tile.getObject(BOSS_ROOM_DOOR_ID, baseX + x, baseY + y, 0, 10, -1);
				if (obj == null)
					continue;
				ObjectAction.register(obj, 1, (player, o) -> {
					if (enteredBossRoom) // already paid and inside -- pressing the door again does nothing
						return;
					if (player.getInventory().getAmount(DRACODUST_ID) < BOSS_ROOM_DRACODUST_COST) {
						player.sendMessage("You need " + BOSS_ROOM_DRACODUST_COST + " Dracodust to open this door.");
						return;
					}
					player.dialogue(new OptionsDialogue("Pay " + BOSS_ROOM_DRACODUST_COST
							+ " Dracodust and enter the fight?",
							new Option("Yes.", () -> enterBossRoom(player)),
							new Option("No.")));
				});
				return;
			}
		}
		// If this fires, object 8958 isn't actually placed anywhere in region 13364's map data --
		// it needs to be GameObject.spawn()'d explicitly instead (like DRACO_ROCK_ID etc. above),
		// which requires a real in-map coordinate for it.
		log.warn("MiningMinigameInstance: boss room door (object " + BOSS_ROOM_DOOR_ID
				+ ") not found anywhere in this instance -- gate not wired up.");
	}

	// Re-checks the Dracodust amount (it may have changed while the confirm dialogue was open) --
	// only pays and teleports if still eligible, and marks the gate as used so pressing the door
	// again afterwards (e.g. on the way back out) does nothing rather than re-charging.
	private void enterBossRoom(Player player) {
		if (enteredBossRoom)
			return;
		if (player.getInventory().getAmount(DRACODUST_ID) < BOSS_ROOM_DRACODUST_COST) {
			player.sendMessage("You need " + BOSS_ROOM_DRACODUST_COST + " Dracodust to open this door.");
			return;
		}
		player.getInventory().remove(DRACODUST_ID, BOSS_ROOM_DRACODUST_COST);
		enteredBossRoom = true;
		player.getMovement().teleport(convertPosition(BOSS_ROOM_ENTRY_POSITION));
	}

	// Returns false if no instance could be built (caller refunds any entry fee).
	public static boolean enter(Player player) {
		try {
			new MiningMinigameInstance(player);
			return true;
		} catch (DynamicMap.DynamicMapBuildException e) {
			player.sendMessage("The minigame is currently full, please try again shortly.");
			return false;
		}
	}

	public static void exit(Player player) {
		player.getMovement().teleport(REAL_WORLD_EXIT_POSITION);
	}

	private final int ownerId;
	private final DynamicMap map;
	private boolean destroyed;
	private int dracodustMined = 0;
	private int whelpsSpawned = 0;
	private boolean enteredBossRoom = false;
	private static final int[] WHELP_THRESHOLDS = {14, 28, 41};
	private static final int BOSS_ROOM_DRACODUST_COST = 60;

	// Called by DracoRock.java each time a mining action grants Dracodust -- tracks a running total
	// for this player's instance and returns true (exactly once per threshold crossed, up to 3
	// times total) the moment a Draco Whelp should spawn: at 14, then 28, then 41 total mined.
	public static boolean recordDracodustAndCheckWhelp(Player player, int amount) {
		MiningMinigameInstance instance = activeInstances.get(player.getUserId());
		if (instance == null)
			return false;
		instance.dracodustMined += amount;
		if (instance.whelpsSpawned < WHELP_THRESHOLDS.length
				&& instance.dracodustMined >= WHELP_THRESHOLDS[instance.whelpsSpawned]) {
			instance.whelpsSpawned++;
			return true;
		}
		return false;
	}

	private MiningMinigameInstance(Player player) throws DynamicMap.DynamicMapBuildException {
		this.ownerId = player.getUserId();
		this.map = new DynamicMap();
		map.build(BOUNDS);
		// Multi-combat so the player can still fight Draco while the Draco Whelps he summons during
		// his smithing phase are attacking them (single-combat would block the attack on Draco).
		map.makeDynamicMapMultiCombat();
		GameObject.spawn(DRACO_ROCK_ID, convertPosition(DRACO_ROCK_POSITION), 10, 0);
		GameObject.spawn(EXIT_PORTAL_ID, convertPosition(EXIT_PORTAL_POSITION), 10, 0);
		GameObject.spawn(PRISMATIC_CHEST_ID, convertPosition(PRISMATIC_CHEST_POSITION), 10, DIRECTION_EAST);
		NPC draco = new NPC(DRACO_BOSS_ID).spawn(convertPosition(DRACO_BOSS_POSITION), Direction.NORTH);
		map.addNpc(draco);
		registerBossRoomDoor();
		MapListener mapListener = map.toListener().onExit(this::onExit);
		activeInstances.put(ownerId, this);
		player.currentDynamicMap = map;
		player.inDynamicMap = true;
		player.registerMapListener(mapListener);
		player.getMovement().teleport(convertPosition(ENTRY_POSITION));
	}

	private Position convertPosition(Position pos) {
		int localX = pos.getX() - BOUNDS.swX;
		int localY = pos.getY() - BOUNDS.swY;
		return new Position(localX + map.swRegion.baseX, localY + map.swRegion.baseY, pos.getZ());
	}

	private void onExit(Player player, boolean logout) {
		player.currentDynamicMap = null;
		player.inDynamicMap = false;
		// Draco's boss HP bar only closed on his death / the player's death -- leaving mid-fight
		// (exit portal, teleport, logout) left it stuck on screen.
		if (player.getHealthHud().isOpened())
			player.getHealthHud().close();
		if (logout)
			// player disconnected without walking/teleporting out first -- their saved position must not be left
			// pointing at a dynamic-map region, since that region gets freed and reused for someone else's instance.
			player.getMovement().teleport(REAL_WORLD_EXIT_POSITION);
		destroy();
	}

	private void destroy() {
		if (destroyed)
			return;
		destroyed = true;
		map.destroy();
		activeInstances.remove(ownerId);
	}
}
