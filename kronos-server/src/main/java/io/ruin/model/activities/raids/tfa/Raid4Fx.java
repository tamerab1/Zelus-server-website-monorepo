package io.ruin.model.activities.raids.tfa;

import io.ruin.model.World;
import io.ruin.model.entity.npc.NPC;
import io.ruin.model.entity.player.Player;
import io.ruin.model.map.Position;

import java.util.Collection;
import java.util.List;

/**
 * The npc-info blocks the RSProx captures of the raid use (docs/rsprox-analysis/guthixian_archive), written straight
 * to the rsprot avatar: spotanims on a slot (with delay and loop), transparency fades, timed headbars, base-animation
 * (stance) overrides, body models, face angles and exact moves; plus map anims and area sounds. Times are client
 * cycles (30 to a tick) as in the captures.
 */
public final class Raid4Fx {

	private Raid4Fx() {
	}

	/** Spotanim on an npc's slot. */
	public static void spot(NPC npc, int slot, int id, int delay, int height, boolean loop) {
		if (npc == null || npc.avatar() == null)
			return;
		npc.avatarExtended().setSpotAnim(slot, id, delay, height, loop);
	}

	public static void spot(NPC npc, int slot, int id) {
		spot(npc, slot, id, 0, 0, false);
	}

	/** Clears an npc's spotanim slot. */
	public static void clearSpot(NPC npc, int slot) {
		spot(npc, slot, -1, 0, 0, false);
	}

	/** Spotanim on a player's slot. */
	public static void spot(Player player, int slot, int id, int delay, int height) {
		if (player == null || player.avatarExtended() == null)
			return;
		player.avatarExtended().setSpotAnim(slot, id, delay, height, false);
	}

	/** A map anim (a spotanim on a tile). */
	public static void mapAnim(int id, Position tile, int delay, int height) {
		World.sendGraphics(id, height, delay, tile);
	}

	/** Fades an npc from {@code from} to {@code to} transparency (0 opaque .. 255 invisible) over cycles start..end. */
	public static void fade(NPC npc, int start, int end, int from, int to) {
		if (npc == null || npc.avatar() == null)
			return;
		npc.avatarExtended().setTransparency(start, end, from, to, true);
	}

	/** A headbar of type {@code id} running from startFill to endFill over endTime cycles. */
	public static void headbar(NPC npc, int id, int startFill, int endFill, int endTime) {
		if (npc == null || npc.avatar() == null)
			return;
		npc.avatarExtended().addHeadBar(-1, id, id, startFill, endFill, 0, endTime);
	}

	public static void removeHeadbar(NPC npc, int id) {
		if (npc == null || npc.avatar() == null)
			return;
		npc.avatarExtended().removeHeadBar(id);
	}

	/** {@code [bas] turnleft, turnright, ready = seq}: holds the npc in a stance. */
	public static void stance(NPC npc, int seq) {
		if (npc == null || npc.avatar() == null)
			return;
		var info = npc.avatarExtended();
		info.setReadyAnim(seq);
		info.setTurnAnims(seq, seq);
	}

	/** {@code [bas] walk, ready = seq}. */
	public static void walkStance(NPC npc, int seq) {
		if (npc == null || npc.avatar() == null)
			return;
		var info = npc.avatarExtended();
		info.setReadyAnim(seq);
		info.setWalkAnims(seq, seq, seq, seq);
	}

	/** An empty {@code [bas]}: the type's own stances. */
	public static void resetStance(NPC npc) {
		if (npc == null || npc.avatar() == null)
			return;
		npc.avatarExtended().resetBaseAnimationSet();
	}

	/** {@code [body_customisation]} with one model; -1 hides the npc's body. */
	public static void bodyModel(NPC npc, int model) {
		if (npc == null || npc.avatar() == null)
			return;
		npc.avatarExtended().setBodyCustomisation(List.of(model), List.of(), List.of(), null);
	}

	public static void resetBody(NPC npc) {
		if (npc == null || npc.avatar() == null)
			return;
		npc.avatarExtended().resetBodyCustomisations();
	}

	/** {@code [facing] angle=} (0 south, 512 west, 1024 north, 1536 east). */
	public static void faceAngle(NPC npc, int angle, boolean instant) {
		if (npc == null || npc.avatar() == null)
			return;
		npc.avatarExtended().setFaceAngle(angle & 2047, instant);
	}

	/** {@code [facing] coord=} a tile. */
	public static void faceTile(NPC npc, Position tile) {
		npc.face(tile);
	}

	/** {@code [facing] type=reset}. */
	public static void resetFace(NPC npc) {
		if (npc == null || npc.avatar() == null)
			return;
		npc.avatarExtended().resetFacing();
	}

	/**
	 * Teleports an npc and slides it there with an exact move: the client places it at (current + delta1) at cycle 0
	 * and (current + delta2) at {@code cycles}, current being the tile it was just teleported to, so delta1 = old - new
	 * and delta2 = 0. Facing {@code angle}.
	 */
	public static void slide(NPC npc, Position to, int cycles, int angle) {
		Position from = npc.getPosition().copy();
		npc.getMovement().teleport(to);
		applyNow(npc.getMovement(), npc.getPosition());
		npc.forceMovementUpdate.set(from.getX() - to.getX(), from.getY() - to.getY(), 0, 0, 0, cycles, angle & 2047);
	}

	/** The player version (PlayerMovement.force's mask). */
	public static void slide(Player player, Position to, int cycles, int angle) {
		Position from = player.getPosition().copy();
		player.getMovement().teleport(to);
		applyNow(player.getMovement(), player.getPosition());
		player.movementModeUpdate.set(io.ruin.model.entity.shared.MovementState.WALK.getId());
		player.forceMovementUpdate.set(from.getX() - to.getX(), from.getY() - to.getY(), 0, 0, 0, cycles, angle & 2047);
	}

	/**
	 * The raid's rooms tick after the entities have moved but before rsprot sends their info: a queued teleport would
	 * go out next tick, after its exact move, which the client then measures from the old tile (the slide starts
	 * mirrored outside and snaps). Apply it now so the teleport and its exact move share one npc / player info.
	 */
	public static void applyNow(io.ruin.model.entity.shared.Movement movement, Position position) {
		movement.finishTeleport(position);
	}

	/** A teleport that lands this tick (with whatever spotanim / seq is sent alongside it). */
	public static void teleportNow(io.ruin.model.entity.Entity entity, Position to) {
		entity.getMovement().teleport(to);
		applyNow(entity.getMovement(), entity.getPosition());
	}

	/** The OSRS angle from one tile to another (0 south, 512 west, 1024 north, 1536 east), cut toward zero. */
	public static int angle(int fromX, int fromY, int toX, int toY) {
		double dx = toX - fromX, dy = toY - fromY;
		if (dx == 0 && dy == 0)
			return 0;
		// atan2 measured from south, clockwise towards west
		double rad = Math.atan2(-dx, -dy);
		int a = (int) (rad * 1024 / Math.PI);
		return a & 2047;
	}

	/**
	 * A projectile as the captures record it (raw v2 heights, start/end cycles, angle, progress), from a tile to a
	 * target entity (followed) or, with {@code target} null, to a tile. Sent to every listed player.
	 */
	public static void projectile(Collection<Player> players, io.ruin.model.entity.Entity source, int id, Position from,
	                              io.ruin.model.entity.Entity target, Position to, int startHeight, int endHeight,
	                              int start, int end, int angle, int progress) {
		for (Player p : players)
			p.getPacketSender().sendProjectile(source, target, id, from.getX(), from.getY(), to.getX(), to.getY(),
					startHeight, endHeight, start, end, angle, progress);
	}

	/** An area sound heard by every listed player. */
	public static void sound(Collection<Player> players, int id, Position at, int delay, int radius) {
		for (Player p : players)
			p.getPacketSender().sendAreaSound(id, 0, delay, at.getX(), at.getY(), radius);
	}
}
