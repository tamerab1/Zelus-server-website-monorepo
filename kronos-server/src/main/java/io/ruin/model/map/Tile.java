package io.ruin.model.map;

import io.ruin.api.utils.NumberUtils;
import io.ruin.cache.ObjType;
import io.ruin.model.entity.Entity;
import io.ruin.model.entity.player.Player;
import io.ruin.model.map.ground.GroundItem;
import io.ruin.model.map.object.GameObject;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static io.ruin.model.map.ClipConstants.*;

@Slf4j
public class Tile {

	/**
	 * Creation
	 */

	public final Region region;

	public Tile(Region region) {
		this.region = region;
	}

	public static Tile get(int x, int y, int z) {
		return get(x, y, z, false);
	}

	public static Tile get(Position position) {
		return get(position.getX(), position.getY(), position.getZ(), false);
	}

	public static Tile get(Position position, boolean create) {
		return Region.get(position.getX(), position.getY()).getTile(position.getX(), position.getY(), position.getZ(),
				create);
	}

	public static Tile get(int x, int y, int z, boolean create) {
		var region = Region.get(x, y);
		if (region == null) {
			return null;
		}
		return region.getTile(x, y, z, create);
	}

	/**
	 * Clipping
	 */

	public int clipping;

	/**
	 * happy to have this memory footprint instead of re-reading cache map files to load original clipping whenevery a
	 * DynamicMap is created, which needs to copy the original data from somewhere
	 */
	public int defaultClipping;

	public int projectileClipping; // I know I didn't want to do this either :[

	public void flagUnmovable() {
		clipping |= 0x200000;
	}

	public void unflagUnmovable() {
		clipping &= ~0x200000;
	}

	public void flagDecoration() {
		clipping |= 0x40000;
	}

	public void unflagDecoration() {
		clipping &= ~0x40000;
	}

	public boolean allowEntrance(int mask) {
		return (clipping & mask) == 0;
	}

	public boolean isTileFree() {
		return isFloorFree() && isWallsFree();
	}

	public boolean isTileFreeCheckDecor() {
		return isFloorFree() && isWallsFree() && isFloorFreeCheckDecor();
	}

	public boolean isFloorFreeCheckDecor() {
		return (clipping & (UNMOVABLE_MASK | DECORATION_MASK | OBJECT_MASK)) == 0;
	}

	public boolean isWildySpawnFree() {
		boolean movable = (clipping & UNMOVABLE_MASK) == 0;
		boolean object = (clipping & OBJECT_MASK) == 0;
		boolean east = (clipping & EAST_MASK) == 0;
		boolean south = (clipping & SOUTH_MASK) == 0;
		return movable;
	}

	public boolean isFloorFree() {
		boolean movable = (clipping & UNMOVABLE_MASK) == 0;
		boolean object = (clipping & OBJECT_MASK) == 0;
		return movable && object;
	}

	public boolean isWallsFree() {
		boolean north = (clipping & NORTH_MASK) == 0;
		boolean east = (clipping & EAST_MASK) == 0;
		boolean south = (clipping & SOUTH_MASK) == 0;
		boolean west = (clipping & WEST_MASK) == 0;
		boolean north_east = (clipping & NORTH_EAST_MASK) == 0;
		boolean south_east = (clipping & SOUTH_EAST_MASK) == 0;
		boolean north_west = (clipping & NORTH_WEST_MASK) == 0;
		boolean south_west = (clipping & SOUTH_WEST_MASK) == 0;
		return north && east && south && west && north_east && south_east && north_west && south_west;
	}

	public boolean allowStandardEntrance() {
		return allowEntrance(WEST_MASK)
				|| allowEntrance(EAST_MASK)
				|| allowEntrance(SOUTH_MASK)
				|| allowEntrance(NORTH_MASK);
	}

	/**
	 * Active
	 */

	private boolean active;

	public void checkActive() {
		boolean active = false;
		if (groundItems != null && groundItems.size() > 0)
			active = true;
		else if (gameObjects != null && gameObjects.size() > 0)
			active = gameObjects.stream().anyMatch(GameObject::isCustom);
		if (this.active == active) {
			/* same active state */
			return;
		}
		if ((this.active = active))
			region.activeTiles.add(this);
		else
			region.activeTiles.remove(this);
	}

	/**
	 * Game objects
	 */

	public List<GameObject> gameObjects;

	public void addObject(GameObject gameObject) {
		if (gameObjects == null)
			gameObjects = new ObjectArrayList<>(1);
		gameObject.tile = this;
		gameObject.clip(false);
		gameObjects.add(gameObject);
		checkActive();
	}

	public void removeObject(GameObject gameObject) {
		if (gameObjects == null) {
			/* this tile has been destroyed */
			return;
		}
		gameObject.clip(true);
		gameObject.tile = null;
		gameObjects.remove(gameObject);
		checkActive();
	}

	public static GameObject getObject(int id, int x, int y, int z) {
		return getObject(id, x, y, z, -1, -1);
	}

	public static GameObject getObject(int id, int x, int y, int z, int type, int direction) {
		Tile tile = get(x, y, z, false);
		return tile == null ? null : tile.getObject(id, type, direction);
	}

	public GameObject getObject(int id, int type, int direction) {
		if (gameObjects != null) {
			for (GameObject gameObject : gameObjects) {
				if (gameObject != null
						&& (id == -1 || gameObject.getId() == id)
						&& (type == -1 || gameObject.getType() == type)
						&& (direction == -1 || gameObject.getDirection() == direction))
					return gameObject;
			}
		}
		return null;
	}

	/**
	 * Ground items
	 */

	public List<GroundItem> groundItems;

	public void addItem(GroundItem groundItem) {
		if (ObjType.get(groundItem.id).stackable) {
			GroundItem stack = getItem(groundItem.id, groundItem.activeOwner);
			if (stack != null) {
				var previousAmount = stack.amount;
				stack.amount = NumberUtils.intSum(stack.amount, groundItem.amount);
				stack.sendUpdate(previousAmount);
				return;
			}
			if (groundItems == null)
				groundItems = new ObjectArrayList<>(8);
		} else {
			if (groundItems == null)
				groundItems = new ObjectArrayList<>(8);
			if (groundItem.amount > 1) {
				var len = (groundItem.amount - 1);
				if (len > 100) {
					log.error("Tried to drop 100x items at once. " + groundItem.id, new IllegalStateException());
					return;
				}
				for (int i = 0; i < len; i++) {
					GroundItem newItem = new GroundItem(groundItem.id, 1)
							.owner(groundItem.originalOwner)
							.position(groundItem.getX(), groundItem.getY(), groundItem.getZ());
					newItem.tile = this;
					groundItems.add(newItem);
					addOrQueueVisibility(newItem);
				}
				groundItem.amount = 1;
			}
		}
		groundItem.tile = this;
		groundItems.add(groundItem);
		addOrQueueVisibility(groundItem);
		checkActive();
	}

	/**
	 * OSRS only ever shows one ground-item slot per (displayId, tile, owner) to a given client.
	 * If one is already visible here, this copy stays queued (added to the list but never sent)
	 * instead of getting its own ObjAdd -- otherwise picking up either one would send an ObjDel
	 * that the client can't tell apart from the other, silently vanishing both. See
	 * GroundItem.visible and revealNextHidden().
	 */
	private void addOrQueueVisibility(GroundItem groundItem) {
		if (hasVisibleItem(groundItem.displayId(), groundItem.activeOwner))
			return;
		groundItem.sendAdd();
	}

	private boolean hasVisibleItem(int displayId, String ownerId) {
		if (groundItems == null)
			return false;
		for (GroundItem groundItem : groundItems) {
			if (groundItem.visible && groundItem.displayId() == displayId && sameOwner(groundItem.activeOwner, ownerId))
				return true;
		}
		return false;
	}

	/** Reveals the next same-id/owner copy left queued by addOrQueueVisibility(), if any. */
	private void revealNextHidden(int displayId, String ownerId) {
		if (groundItems == null)
			return;
		for (GroundItem groundItem : groundItems) {
			if (!groundItem.visible && groundItem.displayId() == displayId && sameOwner(groundItem.activeOwner, ownerId)) {
				groundItem.sendAdd();
				return;
			}
		}
	}

	private static boolean sameOwner(String a, String b) {
		boolean aPublic = a == null || a.isEmpty();
		boolean bPublic = b == null || b.isEmpty();
		if (aPublic || bPublic)
			return aPublic && bPublic;
		return a.equalsIgnoreCase(b);
	}

	public void removeItem(GroundItem groundItem) {
		if (groundItems == null) {
			/* tile has been destroyed */
			return;
		}
		boolean wasVisible = groundItem.visible;
		if (wasVisible)
			groundItem.sendRemove();
		groundItem.tile = null;
		groundItems.remove(groundItem);
		if (wasVisible)
			revealNextHidden(groundItem.displayId(), groundItem.activeOwner);
		checkActive();
	}

	/**
	 * Removes a ground item for a specific player picking it up.
	 * Removes from the groundItems list FIRST so no subsequent zone sync can re-add it,
	 * then sends ObjDel directly to the picking player, then updates active state.
	 */
	/**
	 * Removes a ground item for a specific player picking it up.
	 * Removes from the groundItems list FIRST so no subsequent zone sync can re-add it,
	 * then sends ObjDel directly to the picking player and all other visible players.
	 */
	public void pickupRemoveItem(GroundItem groundItem, io.ruin.model.entity.player.Player picker) {
		if (groundItems == null) {
			return;
		}
		boolean wasVisible = groundItem.visible;
		// Remove from list first - prevents any concurrent/subsequent Region.update
		// from sending an ObjAdd that would create a ghost ground item visual.
		groundItems.remove(groundItem);
		if (wasVisible) {
			// Send ObjDel before nulling tile (sendRemove needs tile.region.players)
			picker.getPacketSender().sendRemoveGroundItem(groundItem);
			groundItem.sendRemove();
		}
		groundItem.tile = null;
		if (wasVisible)
			// A same-id/owner copy may have been queued invisibly behind this one (see
			// addOrQueueVisibility) -- reveal it now instead of leaving it stranded.
			revealNextHidden(groundItem.displayId(), groundItem.activeOwner);
		checkActive();
	}

	public GroundItem getItem(int id, @Nullable String ownerId) {
		if (groundItems == null)
			return null;
		for (GroundItem groundItem : groundItems) {
			if (groundItem.id == id
					&& ((ownerId == null && groundItem.activeOwner == null)
							|| (groundItem.activeOwner != null && groundItem.activeOwner.equalsIgnoreCase(ownerId))))
				return groundItem;
		}
		return null;
	}

	public GroundItem getPickupItem(int id, String ownerId) {
		if (groundItems == null)
			return null;
		// Prefer the visible copy -- if several same-id items share this tile, only one is ever
		// actually shown to the client (see addOrQueueVisibility); the rest are queued and
		// invisible, so the click the client sent could only ever have targeted the visible one.
		GroundItem fallback = null;
		for (GroundItem groundItem : groundItems) {
			// id may be either the real item id or its ground display proxy id (see
			// GroundItem.displayId()) -- the client reports back whatever id it saw rendered.
			if ((groundItem.id == id || groundItem.displayId() == id)
					&& (groundItem.activeOwner == null || groundItem.activeOwner.isEmpty()
					|| groundItem.activeOwner.equalsIgnoreCase(ownerId))) {
				if (groundItem.visible)
					return groundItem;
				if (fallback == null)
					fallback = groundItem;
			}
		}
		return fallback;
	}

	/**
	 * Updating
	 */

	public void update(Player player) {
		if (gameObjects != null) {
			for (GameObject gameObject : gameObjects) {
				if (gameObject.isCustom())
					gameObject.send(player);
			}
		}
		if (groundItems != null) {
			for (GroundItem groundItem : groundItems) {
				// Skip copies still queued behind another same-id item on this tile (see
				// addOrQueueVisibility) -- they were never shown, so don't leak them here either.
				if (groundItem.visible && (groundItem.activeOwner == null || groundItem.activeOwner.isEmpty()
						|| groundItem.activeOwner.equalsIgnoreCase(player.getName()))) {
					// This only runs as part of a region reload (see Entity.updateRegion()),
					// which the client processes by rebuilding its own local zone state from
					// scratch -- so it can never already have this item rendered, and the
					// sendRemove() that used to run first here was a guaranteed-redundant
					// packet on every single ground item in every newly (re)loaded region.
					// Cut for the teleport-freeze fix: a teleport can reveal up to 9 regions
					// at once (vs. 1-3 while walking across a border), and this halved the
					// synchronous per-tick packet burst that caused it.
					groundItem.sendAdd(player);
				}
			}
		}
	}

	/**
	 * Destroy
	 */

	public void destroy() {
		if (gameObjects != null) {
			/*
			 * for (GameObject gameObject : gameObjects) { gameObject.tile = null; // dereference }
			 */
			gameObjects.clear();
			gameObjects = null;
		}
		if (groundItems != null) {
			/*
			 * for (GroundItem groundItem : groundItems) { groundItem.tile = null; // dereference }
			 */
			groundItems.clear();
			groundItems = null;
		}
		clipping = 0;
	}

	/**
	 * Misc vars
	 */

	public Consumer<Player> digAction;

	public boolean multi;

	public boolean roofExists;

	public boolean nearBank;

	// public boolean allowDrop = true;

	public int playerCount, npcCount;

	public byte wildernessLevel;

	// public boolean safePVPInstance = false;

	private List<Consumer<Entity>> triggers;

	public static void occupy(Entity entity) {
		if (entity.occupyingTiles) {
			fill(entity, entity.getLastPosition(), -1);
			entity.occupyingTiles = false;
		}
		if (!entity.isHidden()) {
			if (entity.npc != null && entity.npc.getDef() != null && !entity.npc.getDef().occupyTiles)
				return;
			fill(entity, entity.getPosition(), 1);
			entity.occupyingTiles = true;
		}
	}

	private static void fill(Entity entity, Position pos, int increment) {
		int size = entity.getSize();
		int absX = pos.getX();
		int absY = pos.getY();
		int z = pos.getZ();
		for (int x = absX; x < (absX + size); x++) {
			for (int y = absY; y < (absY + size); y++) {
				Tile tile = Tile.get(x, y, z, true);
				if (tile == null) {
					continue;
				}
				if (entity.player != null)
					tile.playerCount += increment;
				else
					tile.npcCount += increment;
			}
		}
	}

	public static boolean isOccupied(Entity entity, int stepX, int stepY) {
		int size = entity.getSize();
		int absX = entity.getAbsX();
		int absY = entity.getAbsY();
		int z = entity.getHeight();
		int eastMostX = absX + (size - 1);
		int northMostY = absY + (size - 1);
		for (int x = stepX; x < (stepX + size); x++) {
			for (int y = stepY; y < (stepY + size); y++) {
				if (x >= absX && x <= eastMostX && y >= absY && y <= northMostY) {
					/* stepping within itself, allow it */
					continue;
				}
				Tile tile = Tile.get(x, y, z, true);
				if (tile.playerCount > 0 || tile.npcCount > 0)
					return true;
			}
		}
		return false;
	}

	/**
	 * Misc
	 */

	public static boolean allowObjectPlacement(Position position) {
		return allowObjectPlacement(position.getX(), position.getY(), position.getZ());
	}

	public static boolean allowObjectPlacement(int x, int y, int z) {
		Tile tile = get(x, y, z);
		if (tile == null)
			return true;
		if (tile.roofExists)
			return false;
		if (tile.gameObjects != null) {
			for (GameObject obj : tile.gameObjects) {
				if (obj.getType() == 10 || obj.getType() == 11)
					return false;
			}
		}
		return true;
	}

	public void addTrigger(Consumer<Entity> trigger) {
		if (triggers == null)
			triggers = new ArrayList<>();
		triggers.add(trigger);
	}

	public void addPlayerTrigger(Consumer<Player> trigger) {
		addTrigger((e) -> {
			if (e instanceof Player)
				trigger.accept(e.player);
		});
	}

	public void checkTriggers(Entity entity) {
		if (triggers == null)
			return;
		triggers.forEach(t -> t.accept(entity));
	}

	public void removeTrigger(Consumer<Entity> trigger) {
		if (triggers == null)
			return;
		triggers.remove(trigger);
	}

	public void clearTriggers() {
		if (triggers == null)
			return;
		triggers.clear();

	}

	public boolean isActive() {
		return active;
	}

}
