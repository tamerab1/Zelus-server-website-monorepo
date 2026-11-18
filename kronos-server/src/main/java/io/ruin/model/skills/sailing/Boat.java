package io.ruin.model.skills.sailing;

import io.ruin.model.entity.player.Player;
import io.ruin.model.map.Position;
import io.ruin.model.map.Region;
import io.ruin.model.map.Tile;
import io.ruin.model.map.dynamic.DynamicChunk;
import io.ruin.model.map.dynamic.DynamicMap;
import io.ruin.model.map.object.GameObject;
import io.ruin.rsprot.RSProtService;
import lombok.extern.slf4j.Slf4j;
import net.rsprot.protocol.game.outgoing.info.worldentityinfo.WorldEntityAvatar;

import java.util.ArrayList;
import java.util.List;

/**
 * A player's boat: an OSRS "world entity". The deck is a private copy of the boat's showroom zone in the cache (a
 * DynamicMap), and the client draws that copy at the boat's position on the real sea through a rsprot world entity
 * avatar. While aboard, the player stands on the copy (instance coords) and the client's map is built around the
 * boat's sea position (see {@link io.ruin.network.PacketSender#sendRegion}).
 * <p>
 * Ported from RS-Realm's rev 237 sailing (PlayerBoatInstance / SailingScript): the boat's sea anchor matches the
 * CENTRE of its instance footprint, angles are 0..2047 (heading packet * 128), speed in fine units (128 per tile),
 * full sails 192/tick, acceleration 64/tick, at most 128 angle units of turn a tick.
 */
@Slf4j
public class Boat {

	public static final int FINE = 128, ANGLE_FULL = 2048, MAX_TURN = 128, FULL_SPEED = 192, ACCELERATION = 64;

	private static final Boat[] SLOTS = new Boat[256];

	public final int index;
	public final BoatType type;
	public final Player owner;
	public final DynamicMap map;
	/** south-west tile of the deck instance */
	public final int baseX, baseY;
	/** sea position in fine units (tile * 128 + fraction); the instance centre is drawn here */
	public int fineX, fineY;
	public int level = 0;
	public int angle, targetAngle;
	public int speed;
	public boolean sailsUp, navigating;
	public WorldEntityAvatar avatar;
	public final List<GameObject> deckLocs = new ArrayList<>();
	/** the loc built in the facility hotspot (a salvaging hook tier, ...); 0 = the hull type's default */
	public int facilityLoc;
	/** loc animations to play on deck locs this tick (sent inside the boat's active-world block) */
	final List<int[]> deckAnims = new ArrayList<>();
	boolean teleport = true;

	private Boat(int index, BoatType type, Player owner, DynamicMap map, int seaX, int seaY, int angle) {
		this.index = index;
		this.type = type;
		this.owner = owner;
		this.map = map;
		Region sw = map.swRegion;
		this.baseX = sw.baseX;
		this.baseY = sw.baseY;
		this.fineX = seaX * FINE + FINE / 2;
		this.fineY = seaY * FINE + FINE / 2;
		this.angle = this.targetAngle = angle;
	}

	public static Boat get(int index) {
		return index > 0 && index < SLOTS.length ? SLOTS[index] : null;
	}

	public static Iterable<Boat> all() {
		List<Boat> list = new ArrayList<>();
		for (Boat b : SLOTS)
			if (b != null)
				list.add(b);
		return list;
	}

	/** Spawns a boat for the player at a sea tile. Returns null when no instance or slot is free. */
	public static Boat spawn(Player owner, BoatType type, int seaX, int seaY, int angle) {
		return spawn(owner, type, seaX, seaY, angle, 0);
	}

	public static Boat spawn(Player owner, BoatType type, int seaX, int seaY, int angle, int facilityLoc) {
		int slot = -1;
		for (int i = 1; i < SLOTS.length; i++)
			if (SLOTS[i] == null) {
				slot = i;
				break;
			}
		if (slot == -1)
			return null;
		DynamicMap map;
		try {
			List<DynamicChunk> chunks = new ArrayList<>();
			for (int z = 0; z < 4; z++)
				chunks.add(new DynamicChunk(type.sourceZoneX, type.sourceZoneY, z).pos(0, 0, z));
			map = new DynamicMap().build(chunks);
		} catch (Exception e) {
			log.error("Unable to build a boat instance", e);
			return null;
		}
		Boat boat = new Boat(slot, type, owner, map, seaX, seaY, angle);
		boat.facilityLoc = facilityLoc;
		SLOTS[slot] = boat;
		boat.spawnDeck();
		var factory = RSProtService.service().getWorldEntityAvatarFactory();
		boat.avatar = factory.alloc(slot, type.worldEntityType, owner.getIndex(), 1, 1, boat.baseX >> 3, boat.baseY >> 3, 0, 3,
			boat.fineX, boat.fineY, 0, type.deckLevel, angle);
		return boat;
	}

	private void spawnDeck() {
		for (BoatType.Part part : type.parts) {
			int x = baseX + type.anchorX + part.offX(), y = baseY + type.anchorY + part.offY();
			GameObject obj = GameObject.spawn(locFor(part), x, y, levelOf(part), 10, part.rotation());
			deckLocs.add(obj);
		}
		// the helmsman stands on the wheel's tile on the raft; make sure it is walkable
		Position helm = helmStand();
		Tile t = Tile.get(helm.getX(), helm.getY(), helm.getZ(), true);
		if (t != null)
			t.clipping = 0;
	}

	/** the loc actually on deck for a part (the facility hotspot carries whatever the player built there) */
	public int locFor(BoatType.Part part) {
		return part.role() == BoatType.Role.FACILITY && facilityLoc > 0 ? facilityLoc : part.locId();
	}

	public int levelOf(BoatType.Part part) {
		return part.level() < 0 ? type.deckLevel : part.level();
	}

	/** instance tile of a part */
	public Position tileOf(BoatType.Part part) {
		return new Position(baseX + type.anchorX + part.offX(), baseY + type.anchorY + part.offY(), levelOf(part));
	}

	/** Queue a loc animation on the deck loc of a part; sent to every viewer of this boat this tick. */
	public void animateDeck(BoatType.Role role, int seq) {
		BoatType.Part part = type.part(role);
		if (part != null)
			deckAnims.add(new int[]{type.anchorX + part.offX(), type.anchorY + part.offY(), levelOf(part), part.rotation(), seq});
	}

	/** Called after every player's update has been written. */
	public static void clearDeckAnims() {
		for (Boat b : SLOTS)
			if (b != null)
				b.deckAnims.clear();
	}

	/**
	 * Where an instance tile is on the sea right now (tile units, fractional): the inverse of
	 * {@link #resolveClick}, rotating the tile's offset from the instance centre by the boat's angle.
	 */
	public double[] seaPointOf(Position inst) {
		double ix = (inst.getX() + 0.5) - (baseX + 4.0), iy = (inst.getY() + 0.5) - (baseY + 4.0);
		double rad = angle * (2.0 * Math.PI / ANGLE_FULL);
		double cos = Math.cos(rad), sin = Math.sin(rad);
		double dx = ix * cos + iy * sin, dy = -ix * sin + iy * cos;
		return new double[]{fineX / (double) FINE + dx, fineY / (double) FINE + dy};
	}

	/** where the player stands at the helm (and lands when boarding) */
	public Position helmStand() {
		return new Position(baseX + type.anchorX, baseY + type.anchorY + type.helmStandOffY, type.deckLevel);
	}

	public int seaX() {
		return fineX / FINE;
	}

	public int seaY() {
		return fineY / FINE;
	}

	public Position seaPosition() {
		return new Position(seaX(), seaY(), level);
	}

	public boolean contains(Position p) {
		return p.getX() >= baseX && p.getX() < baseX + 8 && p.getY() >= baseY && p.getY() < baseY + 8;
	}

	/** Called every tick before the info protocols update. */
	public static void processAll() {
		for (Boat b : SLOTS)
			if (b != null)
				b.process();
	}

	private void process() {
		if (owner == null || !owner.isOnline()) {
			destroy();
			return;
		}
		if (angle != targetAngle) {
			angle = stepAngle(angle, targetAngle, MAX_TURN);
			avatar.updateAngle(angle);
		}
		int target = sailsUp ? type.baseSpeed : 0;
		if (speed < target)
			speed = Math.min(speed + ACCELERATION, target);
		else if (speed > target)
			speed = Math.max(speed - ACCELERATION, target);
		while (speed > 0 && blockedAhead(speed))
			speed = Math.max(0, speed - ACCELERATION);
		boolean moved = false;
		if (speed > 0) {
			double rad = angle * 2.0 * Math.PI / ANGLE_FULL;
			fineX += (int) -(Math.sin(rad) * speed);
			fineY += (int) -(Math.cos(rad) * speed);
			moved = true;
		}
		if (moved || teleport) {
			avatar.updateCoord(level, fineX, fineY, teleport);
			teleport = false;
		}
		if (moved && owner.boat == this)
			Sailing.onBoatMoved(owner, this);
	}

	/** the sea's own clipping stops the boat a little short of land and rocks (OSRS sheds speed into the shore) */
	private boolean blockedAhead(int dist) {
		int reach = dist + type.hullLength * FINE / 2 + FINE / 2;
		double rad = angle * 2.0 * Math.PI / ANGLE_FULL;
		double beam = (angle + ANGLE_FULL / 4) * 2.0 * Math.PI / ANGLE_FULL;
		int half = type.hullWidth * FINE / 2;
		for (int side = -1; side <= 1; side++) {
			int fx = fineX + (int) -(Math.sin(rad) * reach) + (int) -(Math.sin(beam) * half * side);
			int fy = fineY + (int) -(Math.cos(rad) * reach) + (int) -(Math.cos(beam) * half * side);
			Tile t = Tile.get(fx / FINE, fy / FINE, level, false);
			if (t == null)
				continue; // unloaded open sea
			if (!t.isFloorFree())
				return true;
		}
		return false;
	}

	public static int stepAngle(int current, int target, int maxStep) {
		int raw = (target - current) % ANGLE_FULL;
		int diff = raw > ANGLE_FULL / 2 ? raw - ANGLE_FULL : raw < -ANGLE_FULL / 2 ? raw + ANGLE_FULL : raw;
		if (diff > maxStep)
			return Math.floorMod(current + maxStep, ANGLE_FULL);
		if (diff < -maxStep)
			return Math.floorMod(current - maxStep, ANGLE_FULL);
		return Math.floorMod(target, ANGLE_FULL);
	}

	/**
	 * Map a click the client sent in root-world (sea) coords to the deck instance: undo the boat's position and
	 * rotation around the instance centre (RS-Realm MoveGameClickHandler.resolveDest). Clicks already on the deck pass.
	 */
	public Position resolveClick(int x, int y, int z) {
		if (x >= baseX && x < baseX + 8 && y >= baseY && y < baseY + 8)
			return new Position(x, y, z);
		double bx = fineX / (double) FINE, by = fineY / (double) FINE;
		double dx = (x + 0.5) - bx, dy = (y + 0.5) - by;
		double rad = angle * (2.0 * Math.PI / ANGLE_FULL);
		double cos = Math.cos(rad), sin = Math.sin(rad);
		double ix = dx * cos - dy * sin, iy = dx * sin + dy * cos;
		int rx = (int) Math.floor(baseX + 4.0 + ix - 0.5), ry = (int) Math.floor(baseY + 4.0 + iy - 0.5);
		rx = Math.max(baseX, Math.min(baseX + 7, rx));
		ry = Math.max(baseY, Math.min(baseY + 7, ry));
		return new Position(rx, ry, z);
	}

	public void destroy() {
		if (SLOTS[index] != this)
			return;
		SLOTS[index] = null;
		try {
			if (avatar != null)
				RSProtService.service().getWorldEntityAvatarFactory().release(avatar);
		} catch (Exception e) {
			log.error("Unable to release boat avatar", e);
		}
		avatar = null;
		for (GameObject o : deckLocs)
			o.remove();
		deckLocs.clear();
		try {
			map.destroy();
		} catch (Exception e) {
			log.error("Unable to destroy boat instance", e);
		}
	}
}
