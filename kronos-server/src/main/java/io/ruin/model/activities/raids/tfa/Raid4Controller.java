package io.ruin.model.activities.raids.tfa;

import io.ruin.model.World;
import io.ruin.model.activities.raids.tfa.rooms.ArchiveGuardianRoom;
import io.ruin.model.combat.Hit;
import io.ruin.model.entity.npc.NPC;
import io.ruin.model.entity.player.GameMode;
import io.ruin.model.entity.player.Player;
import io.ruin.model.map.MapListener;
import io.ruin.model.map.Position;
import io.ruin.model.map.dynamic.DynamicMap;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One run of the raid: the party's instance, the room state machine, and every member's death, logout and
 * leave handling.
 * <p>
 * Flow: {@code NEXUS} (Archive Guardian) -> {@code NEXUS_CLEARED} (path doors) -> slot 2 -> slot 3 -> slot 4
 * -> Ocellus -> Vault. Each boss room returns the party to the cleared Nexus to pick the next door.
 * Only the Archive Guardian is implemented so far; the doors refuse paths that have no room yet.
 * <p>
 * A wipe (every member in the raid dead at once) ends the run, as in Theatre of Blood. Deaths aren't safe deaths
 * on OSRS, so hardcore ironmen lose their status; items follow the server's normal PvM death rules.
 */
@Slf4j
public class Raid4Controller {

	public enum State {
		ACTIVE, ENDED
	}

	@Getter
	private final Raid4Party party;
	@Getter
	private final Raid4Instance instance = new Raid4Instance();
	@Getter
	private final Raid4Points points = new Raid4Points();

	@Getter
	private Raid4Room room;
	@Getter
	private State state = State.ACTIVE;
	/** The next path slot the Nexus doors open, 2-4; 5 means Ocellus is next. */
	@Getter
	private int nextSlot = 2;

	private final Map<String, MapListener> listeners = new HashMap<>();
	/** Members teleporting into the raid; the map listener counts them as inside until they land. */
	private final Set<String> arriving = new HashSet<>();
	private long ticks;

	Raid4Controller(Raid4Party party) {
		this.party = party;
	}

	/**
	 * Builds the Nexus, spawns the Guardian and brings in every online member. Returns false (with the instance
	 * already released) if the map couldn't be built.
	 */
	boolean start() {
		try {
			enterRoom(new ArchiveGuardianRoom());
		} catch (DynamicMap.DynamicMapBuildException e) {
			log.error("Raid 4: failed to build the Nexus for {}", party, e);
			instance.dispose();
			return false;
		}
		World.startEvent(event -> {
			while (state == State.ACTIVE) {
				try {
					tick();
				} catch (Exception e) {
					log.error("Raid 4: tick failed for {}", party, e);
				}
				event.delay(1);
			}
		});
		return true;
	}

	private void enterRoom(Raid4Room next) throws DynamicMap.DynamicMapBuildException {
		Raid4Room previous = room;
		DynamicMap map = instance.build(next.type());
		next.init(this, map);
		room = next;
		reviveDead();
		if (previous == null) { // the raid's first room: the lobby's own fade has already played
			for (Player player : party.online())
				moveInto(player, next.entryPosition());
			return;
		}
		for (Player player : party.online())
			fadeInto(player, next);
		// the old room goes once everyone has left it (the move is at O+2)
		World.startEvent(e -> {
			e.delay(4);
			previous.cleanup();
			if (previous.type() != next.type() && room != null && room.type() != previous.type())
				instance.release(previous.type());
		});
	}

	/**
	 * The move between rooms as recorded (nexus_plan 3): O the fade overlay (174) over the HUD, fading out (script 948
	 * 0,255,0,0,50); O+1 busy; O+2 the teleport; O+3 fading in (948 0,0,0,255,50); O+5 the overlay closed, the raid HUD
	 * back.
	 */
	private void fadeInto(Player player, Raid4Room next) {
		String key = Raid4Party.key(player);
		arriving.add(key); // the move is two ticks off: still a raider in between
		fading.add(key);
		player.lock();
		player.getPacketSender().sendInterface(FADE_OVERLAY, io.ruin.model.inter.ToplevelComponent.OVERLAY);
		player.getPacketSender().sendClientScript(SCRIPT_FADE, "iiiii", 0, 255, 0, 0, 50);
		player.addEvent(e -> {
			e.delay(2);
			if (state != State.ACTIVE || room != next) {
				fading.remove(key);
				player.unlock();
				return;
			}
			moveInto(player, next.entryPosition());
			e.delay(1);
			player.getPacketSender().sendClientScript(SCRIPT_FADE, "iiiii", 0, 0, 0, 255, 50);
			e.delay(2);
			fading.remove(key);
			player.unlock();
			if (player.isVisibleInterface(FADE_OVERLAY))
				player.closeInterface(io.ruin.model.inter.ToplevelComponent.OVERLAY);
			Raid4Hud.openRaidHud(player, party);
		});
	}

	private static final int FADE_OVERLAY = 174, SCRIPT_FADE = 948;

	private void moveInto(Player player, Position destination) {
		String key = Raid4Party.key(player);
		arriving.add(key);
		player.getCombat().reset(); // an attack queued in the old room would land from 1000+ tiles away
		player.getMovement().teleport(destination);
		player.teleportListener = p -> {
			p.sendMessage("The Archive's magic stops you from teleporting.");
			return false;
		};
		player.deathEndListener = (entity, killer, killHit) -> onPlayerDeath(player, killHit);
		listeners.computeIfAbsent(key, k -> {
			MapListener listener = new MapListener(p -> arriving.contains(k) || instance.roomAt(p.getPosition()) != null)
					.onExit((p, logout) -> onMemberExit(p, logout));
			player.registerMapListener(listener);
			return listener;
		});
		if (room != null)
			room.onEnter(player);
		if (!fading.contains(key))
			Raid4Hud.openRaidHud(player, party);
	}

	/** Raiders mid-transition (the fade overlay holds the HUD's slot until O+5). */
	private final java.util.Set<String> fading = new java.util.HashSet<>();

	boolean isFading(Player player) {
		return fading.contains(Raid4Party.key(player));
	}

	/**
	 * A member who logged out mid-raid logs back in: straight back into the party's current room (their party seat
	 * was kept), the HUD and its clock carrying on.
	 */
	public void rejoin(Player player) {
		if (state != State.ACTIVE || room == null || !party.isMember(player))
			return;
		moveInto(player, room.entryPosition());
		player.sendMessage("You return to your party in the Archive.");
	}

	private void tick() {
		ticks++;
		if (instance.isDisposed()) {
			end(null);
			return;
		}
		for (Player player : party.online()) {
			if (arriving.contains(Raid4Party.key(player)) && instance.roomAt(player.getPosition()) != null)
				arriving.remove(Raid4Party.key(player));
		}
		if (room != null)
			room.tick();
		Raid4Hud.tick(this, party, raidStartTick < 0 ? 0 : ticks - raidStartTick, raidStartTick >= 0);
	}

	public long ticks() {
		return ticks;
	}

	/** Ticks since the Guardian's challenge started the raid's clock (0 before). */
	public long raidTicks() {
		return raidStartTick < 0 ? 0 : ticks - raidStartTick;
	}

	/** Set once the party reaches the vault: an end after that is no failure. */
	private boolean completed, wiped;

	void markCompleted() {
		completed = true;
	}

	/** The raid's clock starts at the Guardian's challenge (RAID_PLAN: the HUD timer counts from the Touch). */
	private long raidStartTick = -1, challengeStartTick = -1;

	/** "Challenge started: X." and the room's clock. */
	public void challengeStarted(String name) {
		if (raidStartTick < 0) {
			raidStartTick = ticks;
			Raid4Stats.count(party.online(), Raid4Stats.OVERALL, Raid4Stats.ATTEMPTS);
		}
		if (room != null)
			Raid4Stats.count(party.online(), Raid4Stats.section(room.type()), Raid4Stats.ATTEMPTS);
		challengeStartTick = ticks;
		points.startRoom();
		party.message("Challenge started: " + name + ".");
	}

	/** "Challenge complete: X. Duration: m:ss. Total: m:ss" (RAID_PLAN, the clocks the raid carries). */
	public void challengeComplete(String name) {
		long start = challengeStartTick < 0 ? ticks : challengeStartTick;
		long raid = raidStartTick < 0 ? start : raidStartTick;
		party.message("Challenge complete: " + name + ". Duration: <col=ef1020>" + clock(ticks - start)
				+ "</col>. Total: <col=ef1020>" + clock(ticks - raid) + "</col>");
		points.finishRoom();
		if (room != null) {
			List<Player> online = party.online();
			int section = Raid4Stats.section(room.type());
			Raid4Stats.count(online, section, Raid4Stats.COMPLETIONS);
			Raid4Stats.time(online, section, online.size(), ticks - start);
		}
	}

	private static String clock(long ticks) {
		long ms = ticks * 600;
		long secs = ms / 1000;
		return (secs / 60) + ":" + String.format("%02d", secs % 60);
	}

	/* Room callbacks */

	/** Called by a room when its boss dies. */
	public void roomComplete(Raid4Room completed) {
		if (completed != room || state != State.ACTIVE)
			return;
		log.info("Raid 4: {} complete for {}", completed.type(), party);
		reviveDead();
		int slot = completed.type().slot;
		if (slot >= 2 && slot <= 4)
			nextSlot = slot + 1;
	}

	/** "Travel" on The Blade after Ocellus (16683): the party goes on to the vault. */
	public void travelToVault(Player player) {
		if (state != State.ACTIVE || room == null || room.type() != Raid4RoomType.OCELLUS || !room.isComplete())
			return;
		try {
			enterRoom(new Raid4VaultRoom());
		} catch (DynamicMap.DynamicMapBuildException e) {
			log.error("Raid 4: failed to build the vault for {}", party, e);
			end("The vault failed to open; your loot was lost to the void. Please report this.");
		}
	}

	private void reviveDead() {
		for (Player player : party.online()) {
			if (party.isDead(player))
				revive(player);
		}
		party.clearDead();
	}

	/** Called by the Guardian room once the death animation has played, to swap in the cleared Nexus. */
	public void openNexus(Raid4Room completed) {
		if (completed != room || state != State.ACTIVE) {
			log.warn("Raid 4: openNexus skipped (room {}, state {}) for {}", room == null ? null : room.type(), state, party);
			return;
		}
		Raid4Room cleared = new Raid4NexusRoom();
		try {
			Raid4Room previous = room;
			DynamicMap map = instance.build(cleared.type());
			cleared.init(this, map);
			room = cleared;
			log.info("Raid 4: opening the cleared Nexus (region {}) for {}", map.swRegion.id, party);
			// Same local tile in the cleared copy, so the swap reads as the hall changing around them.
			for (Player player : party.online()) {
				int lx = instance.localX(previous.type(), player.getPosition());
				int ly = instance.localY(previous.type(), player.getPosition());
				Position dest = lx >= 0 && lx < 64 && ly >= 0 && ly < 64
						? instance.walkable(cleared.type(), lx, ly, player.getPosition().getZ(), 3)
						: cleared.entryPosition();
				log.info("Raid 4: {} {} (local {},{}) -> {}", player.getName(), player.getPosition(), lx, ly, dest);
				moveInto(player, dest);
				player.sendMessage("The Guardian's hold on the Nexus breaks, and the hall around you settles.");
			}
			previous.cleanup();
			instance.release(previous.type());
		} catch (DynamicMap.DynamicMapBuildException e) {
			log.error("Raid 4: failed to build the cleared Nexus for {}", party, e);
			party.message("The Archive failed to open the way forward. You have been returned to the lobby.");
			end(null);
		}
	}

	/** "Exit" in a cleared boss room: the whole party goes back to the cleared Nexus for the next door. */
	public void returnToNexus(Player player) {
		if (room == null || room instanceof Raid4NexusRoom || room.type().slot < 2) {
			return;
		}
		if (!room.isComplete()) {
			player.sendMessage("The way out is sealed until " + room.type().displayName + " falls.");
			return;
		}
		try {
			// every stage 2..4 leads back to the atrium (nexus_plan 4: A1..A3); after stage 4 its Ocellus door is open
			if (room.type().slot <= 4)
				enterRoom(new Raid4NexusRoom(room.type().side)); // landing by that side's door (nexus_plan 6)
		} catch (DynamicMap.DynamicMapBuildException e) {
			log.error("Raid 4: failed to rebuild the cleared Nexus for {}", party, e);
			player.sendMessage("The exit won't budge. Try again in a moment.");
		}
	}

	/** Testing ({@code ::raid4 goto}): straight into any room. */
	public boolean debugEnter(Raid4RoomType type) {
		Raid4Room next = type == Raid4RoomType.VAULT ? new Raid4VaultRoom() : Raid4Rooms.create(type);
		if (next == null)
			return false;
		try {
			if (type.slot >= 2 && type.slot <= 4)
				nextSlot = type.slot;
			enterRoom(next);
			return true;
		} catch (DynamicMap.DynamicMapBuildException e) {
			log.error("Raid 4: debug enter {} failed", type, e);
			return false;
		}
	}

	/** A Nexus door: {@code slot} 2-4, west or east. */
	public void choosePath(Player player, int slot, Raid4RoomType.Side side) {
		boolean clearedHall = room instanceof io.ruin.model.activities.raids.tfa.rooms.ArchiveGuardianRoom && room.isComplete();
		if (!(room instanceof Raid4NexusRoom) && !clearedHall) {
			player.sendMessage("The door won't open while the Guardian stands.");
			return;
		}
		if (slot != nextSlot) {
			player.sendMessage(slot < nextSlot ? "You've already been down this hall." : "This door is sealed for now.");
			return;
		}
		if (!party.isLeader(player)) {
			player.sendMessage("Only the party leader can choose the path.");
			return;
		}
		Raid4RoomType type = Raid4RoomType.forChoice(slot, side);
		Raid4Room next = Raid4Rooms.create(type);
		if (next == null) {
			player.sendMessage(capitalise(type.displayName) + "'s chamber isn't open yet.");
			return;
		}
		try {
			Raid4NexusDoors.open(instance, room.type(), slot, side);
			enterRoom(next);
		} catch (DynamicMap.DynamicMapBuildException e) {
			log.error("Raid 4: failed to build {} for {}", type, party, e);
			player.sendMessage("The door won't budge. Try again in a moment.");
		}
	}

	/** The atrium's Ocellus door, open after stage 4: the party goes on to Ocellus (4301 [867..876]). */
	public void enterOcellus(Player player) {
		if (!(room instanceof Raid4NexusRoom) || nextSlot != 5)
			return;
		if (!party.isLeader(player)) {
			player.sendMessage("Only the party leader can choose the path.");
			return;
		}
		try {
			enterRoom(Raid4Rooms.create(Raid4RoomType.OCELLUS));
		} catch (DynamicMap.DynamicMapBuildException e) {
			log.error("Raid 4: failed to build Ocellus for {}", party, e);
			player.sendMessage("The door won't budge. Try again in a moment.");
		}
	}

	/* Deaths */

	private void onPlayerDeath(Player player, Hit killHit) {
		if (player.getGameMode().isHardcoreIronman())
			GameMode.hardcoreDeath(player, killHit);
		points.death(player);
		party.markDead(player);
		Raid4Stats.count(List.of(player), Raid4Stats.OVERALL, Raid4Stats.DEATHS);
		if (room != null)
			Raid4Stats.count(List.of(player), Raid4Stats.section(room.type()), Raid4Stats.DEATHS);
		Raid4Rental.release(player, "Your rented gear returns to the Quartermaster.");
		// guardian_plan 12: D+7 "You have died. Total deaths: n.", prayers off, moved out of the fight to wait.
		// Deaths here are safe on Zelus (no gravestone), so nothing is lost.
		player.sendMessage("You have died. Total deaths: " + points.totalDeaths() + ".");
		player.getPrayer().deactivateAll();
		if (room != null)
			player.getMovement().teleport(room.spectatePosition());
		player.setInvincible(true);

		if (party.alive().isEmpty())
			wipe();
	}

	/**
	 * guardian_plan 12: the whole team down. D+10 "You failed to survive the Fractured Archive.", fade out, jingle 90;
	 * D+13 back in the lobby (4130,5559) facing south (our death is already at its end, so the count runs from here).
	 */
	private void wipe() {
		log.info("Raid 4: wipe for {}", party);
		wiped = true;
		Raid4Stats.count(party.online(), Raid4Stats.OVERALL, Raid4Stats.WIPES);
		World.startEvent(e -> {
			e.delay(3);
			for (Player p : party.online()) {
				if (!isInside(p))
					continue;
				p.sendMessage("You failed to survive the Fractured Archive.");
				p.lock();
				p.getPacketSender().sendInterface(FADE_OVERLAY, io.ruin.model.inter.ToplevelComponent.OVERLAY);
				p.getPacketSender().sendClientScript(SCRIPT_FADE, "iiiii", 0, 255, 0, 0, 50);
				p.getPacketSender().sendJingle(Raid4Ids.JINGLE_RAID_FAILED);
			}
			e.delay(3);
			List<Player> back = new java.util.ArrayList<>();
			for (Player p : party.online())
				if (isInside(p))
					back.add(p);
			end(null);
			for (Player p : back) {
				p.face(io.ruin.model.map.Direction.SOUTH);
				p.getPacketSender().sendClientScript(SCRIPT_FADE, "iiiii", 0, 0, 0, 255, 50);
				p.unlock();
				World.startEvent(f -> {
					f.delay(2);
					if (p.isOnline() && p.isVisibleInterface(FADE_OVERLAY))
						p.closeInterface(io.ruin.model.inter.ToplevelComponent.OVERLAY);
				});
			}
		});
	}

	private void revive(Player player) {
		player.setInvincible(false);
		player.getCombat().restore();
	}

	/* Leaving */

	private void onMemberExit(Player player, boolean logout) {
		String key = Raid4Party.key(player);
		log.info("Raid 4: {} left the raid (logout={}, state={}) at {}", player.getName(), logout, state, player.getPosition());
		listeners.remove(key);
		arriving.remove(key);
		boolean wasDead = party.isDead(player);
		clearPlayer(player);
		Raid4Rental.release(player, logout ? null : "Your rented gear returns to the Quartermaster.");
		if (wasDead)
			revive(player);
		if (state != State.ACTIVE)
			return; // end() already moved them out
		// A logout has no destination of its own; anything else (an exit, a staff teleport) already does.
		if (logout) {
			// keep their seat: logging back in returns them to the party's room (Raid4 login listener -> rejoin)
			player.getMovement().teleport(Raid4Ids.LOBBY_RETURN_X, Raid4Ids.LOBBY_RETURN_Y, 0);
		} else
			Raid4.leaveParty(player);
		if (listeners.isEmpty())
			end(null);
		else if (party.alive().isEmpty())
			wipe();
	}

	/** Voluntary exit, for the exit portal and {@code ::raid4 leave}. */
	public void leave(Player player) {
		player.getMovement().teleport(Raid4Ids.LOBBY_RETURN_X, Raid4Ids.LOBBY_RETURN_Y, 0);
		player.sendMessage("You leave the Archive.");
	}

	private void clearPlayer(Player player) {
		Raid4Hud.closeRaidHud(player);
		player.teleportListener = null;
		player.deathEndListener = null;
		player.setInvincible(false);
	}

	/**
	 * Ends the run: sends everyone still inside to the lobby and releases the instance. Safe to call more than
	 * once.
	 */
	public void end(String message) {
		if (state == State.ENDED)
			return;
		state = State.ENDED;
		log.info("Raid 4: ended in {} for {}", room == null ? null : room.type(), party);
		List<Player> online = party.online();
		if (!completed && !wiped && raidStartTick >= 0)
			Raid4Stats.count(online, Raid4Stats.OVERALL, Raid4Stats.FAILURES);
		for (Player player : online) {
			if (listeners.remove(Raid4Party.key(player)) != null || arriving.remove(Raid4Party.key(player))) {
				clearPlayer(player);
				player.getMovement().teleport(Raid4Ids.LOBBY_RETURN_X, Raid4Ids.LOBBY_RETURN_Y, 0);
				revive(player);
				Raid4Rental.release(player, "Your rented gear returns to the Quartermaster.");
				if (message != null)
					player.sendMessage(message);
			}
		}
		arriving.clear();
		if (room != null) {
			try {
				room.cleanup();
			} catch (Exception e) {
				log.error("Raid 4: room cleanup failed", e);
			}
		}
		instance.dispose();
		Raid4Quartermaster.clear(this);
		Raid4.finished(this);
	}

	public boolean isInside(Player player) {
		return listeners.containsKey(Raid4Party.key(player));
	}

	public void onNpcDeath(NPC npc) {
		if (room != null)
			room.onNpcDeath(npc);
	}

	static String capitalise(String text) {
		return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
	}
}
