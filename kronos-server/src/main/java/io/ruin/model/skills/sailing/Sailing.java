package io.ruin.model.skills.sailing;

import io.ruin.model.entity.player.Player;
import io.ruin.model.map.Position;
import io.ruin.model.map.object.actions.ObjectAction;
import io.ruin.model.stat.StatType;
import io.ruin.model.var.VarPlayerRepository;
import net.rsprot.protocol.game.outgoing.camera.CamTargetV4;
import net.rsprot.protocol.game.outgoing.misc.client.SetInteractionMode;

/**
 * Sailing: boarding, the helm, the sails and docking. The boat itself is {@link Boat} (a rsprot world entity).
 * <ul>
 * <li>Gangplanks (multiloc on varbit 19104 sailing_player_is_on_player_boat): "Board" from the dock, "Disembark" from
 * the boat, at every {@link Port}. The boat stays moored where it was last left (PlayerAttributes.sailingBoatPort);
 * docking needs the port's Sailing level (not boostable).</li>
 * <li>Helm op1 Navigate / Stop-navigating (multiloc on varbit 19105 sailing_boat_facility_lockedin). While at the helm
 * the client is put in heading mode (set_interaction_mode tile 2) and sends SetHeading 0-15 on a sea click.</li>
 * <li>Sail canvas: "Set" raises the sails, "Un-set" lowers them (the brake).</li>
 * </ul>
 */
public class Sailing {

	public static final VarPlayerRepository ON_PLAYER_BOAT = VarPlayerRepository.varpbit(19104, false);
	public static final VarPlayerRepository FACILITY_LOCKEDIN = VarPlayerRepository.varpbit(19105, false);

	private static final int WORLD_DEFAULT = -2, TILE_DISABLED = 0, TILE_WALK = 1, TILE_HEADING = 2, ENTITY_ALL = 1;

	public static void register() {
		for (Port port : Port.VALUES) {
			ObjectAction.register(port.gangplank(), 1, (p, obj) -> {
				if (p.boat == null)
					board(p, port);
			});
		}
		for (Salvaging.Hook h : Salvaging.Hook.values())
			ObjectAction.register(h.raftLoc(), 5, (p, obj) -> modifyFacility(p));
		for (BoatType type : BoatType.values()) {
			BoatType.Part helm = type.part(BoatType.Role.HELM);
			ObjectAction.register(helm.locId(), 1, (p, obj) -> toggleHelm(p));
			BoatType.Part canvas = type.part(BoatType.Role.SAIL_CANVAS);
			if (canvas != null) {
				ObjectAction.register(canvas.locId(), 2, (p, obj) -> setSails(p, true));
				ObjectAction.register(canvas.locId(), 5, (p, obj) -> setSails(p, false));
				ObjectAction.register(canvas.locId(), 1, (p, obj) -> setSails(p, p.boat != null && !p.boat.sailsUp));
			}
		}
	}

	/* ------------------------------------------------------------------ boarding */

	public static Port mooredAt(Player p) {
		int i = p.sailingBoatPort;
		return i >= 0 && i < Port.VALUES.length ? Port.VALUES[i] : Port.PORT_SARIM;
	}

	public static void board(Player p, Port port) {
		if (p.boat != null)
			return;
		Port moored = mooredAt(p);
		if (moored != port) {
			p.sendMessage("Your boat is moored at " + moored.title + ".");
			return;
		}
		BoatType type = BoatType.RAFT;
		Position mooring = port.mooring(type);
		Boat boat = Boat.spawn(p, type, mooring.getX(), mooring.getY(), port.mooringAngle(), facilityLoc(p));
		if (boat == null) {
			p.sendMessage("Your boat couldn't be prepared right now. Try again in a moment.");
			return;
		}
		p.startEvent(e -> {
			p.lock();
			p.getPacketSender().fadeOut();
			e.delay(2); // let the client take the world entity before the player is put on it
			p.boat = boat;
			p.boatRebuildPending = boat.index;
			Position deck = boat.helmStand();
			p.getMovement().teleport(deck.getX(), deck.getY(), deck.getZ());
			p.updateFirstChunk();
			p.getUpdater().updateRegion = true;
			ON_PLAYER_BOAT.set(p, 1);
			p.pendingCamTarget = new CamTargetV4(new CamTargetV4.PlayerCamTarget(p.getIndex()));
			boat.map.assignListener(p).onExit((pl, logout) -> leftBoat(pl, boat, logout));
			p.getPacketSender().fadeIn();
			p.sendMessage("You board your boat.");
			p.unlock();
		});
	}

	/** the loc in the raft's one facility slot: the cargo hold, or the salvaging hook (Sailing 15+) */
	public static int facilityLoc(Player p) {
		return p.sailingRaftFacility == 1 ? Salvaging.raftHookLoc(p) : PortTasks.CARGO_HOLD_RAFT;
	}

	/**
	 * "Modify" on the raft's facility: swap the cargo hold for the salvaging hook and back. (OSRS does this at a shipyard
	 * through the customisation interface; until that exists the facility's own Modify option does it.)
	 */
	public static void modifyFacility(Player p) {
		Boat boat = p.boat;
		if (boat == null)
			return;
		if (boat.speed > 0) {
			p.sendMessage("You need to stop the boat before you can modify it.");
			return;
		}
		if (p.sailingRaftFacility == 0) {
			if (p.getStats().get(StatType.Sailing).fixedLevel < 15) {
				p.sendMessage("You need a Sailing level of 15 to fit a salvaging hook.");
				return;
			}
			if (!p.sailingCargoHold.isEmpty()) {
				p.sendMessage("You need to empty your cargo hold before you can replace it.");
				return;
			}
			p.sailingRaftFacility = 1;
		} else {
			p.sailingRaftFacility = 0;
		}
		BoatType.Part part = boat.type.part(BoatType.Role.FACILITY);
		boat.facilityLoc = facilityLoc(p);
		io.ruin.model.map.Position at = boat.tileOf(part);
		for (io.ruin.model.map.object.GameObject o : boat.deckLocs)
			if (o.getPosition().getX() == at.getX() && o.getPosition().getY() == at.getY())
				o.setId(boat.facilityLoc);
		p.sendMessage(p.sailingRaftFacility == 1 ? "You swap your cargo hold for a salvaging hook." : "You swap your salvaging hook for a cargo hold.");
	}

	/** Gangplank click from the deck. Returns true when it was handled. */
	public static boolean objectClick(Player p, int option, int id, int x, int y) {
		if (p.boat == null)
			return false;
		Port port = Port.forGangplank(id);
		if (port != null) {
			disembark(p, port);
			return true;
		}
		return false;
	}

	public static void disembark(Player p, Port port) {
		Boat boat = p.boat;
		if (boat == null)
			return;
		Port near = Port.nearest(boat.seaPosition(), 12);
		if (near != port) {
			p.sendMessage("You need to sail closer to the dock to disembark.");
			return;
		}
		if (boat.speed > 0) {
			p.sendMessage("You need to stop the boat before you can disembark.");
			return;
		}
		if (p.getStats().get(StatType.Sailing).fixedLevel < port.level) {
			p.sendMessage("You need a Sailing level of " + port.level + " to dock at " + port.title + ".");
			return;
		}
		p.sailingBoatPort = port.ordinal();
		p.startEvent(e -> {
			p.lock();
			p.getPacketSender().fadeOut();
			e.delay(1);
			p.set("SAILING_DISEMBARKING", true);
			p.getMovement().teleport(port.disembark()); // leaving the deck instance runs leftBoat
			e.delay(1);
			p.getPacketSender().fadeIn();
			p.sendMessage("You disembark.");
			p.unlock();
		});
	}

	private static void leftBoat(Player p, Boat boat, boolean logout) {
		boolean disembarked = Boolean.TRUE.equals(p.remove("SAILING_DISEMBARKING"));
		if (!logout && !disembarked)
			PortTasks.loseHoldCargo(p); // wiki: teleporting off the boat at sea loses the hold's cargo
		if (p.boat == boat) {
			stopNavigating(p, false);
			p.boat = null;
			ON_PLAYER_BOAT.set(p, 0);
			if (!logout) {
				p.pendingCamTarget = new CamTargetV4(new CamTargetV4.PlayerCamTarget(p.getIndex()));
				p.updateFirstChunk();
				p.getUpdater().updateRegion = true;
			}
		}
		if (logout) {
			// logging out at sea: the boat is brought into the nearest port the player can dock at
			Port best = Port.PORT_SARIM;
			int bestDist = Integer.MAX_VALUE, lvl = p.getStats().get(StatType.Sailing).fixedLevel;
			for (Port port : Port.VALUES) {
				int d = Math.max(Math.abs(port.plankX - boat.seaX()), Math.abs(port.plankY - boat.seaY()));
				if (port.level <= lvl && d < bestDist) {
					best = port;
					bestDist = d;
				}
			}
			p.sailingBoatPort = best.ordinal();
			p.getMovement().teleport(best.disembark());
		}
		boat.destroy();
	}

	/* ------------------------------------------------------------------ helm + sails */

	private static void toggleHelm(Player p) {
		Boat boat = p.boat;
		if (boat == null)
			return;
		if (boat.navigating) {
			stopNavigating(p, true);
			return;
		}
		boat.navigating = true;
		Position stand = boat.helmStand();
		if (!p.getPosition().equals(stand))
			p.getMovement().teleport(stand.getX(), stand.getY(), stand.getZ());
		FACILITY_LOCKEDIN.set(p, 1);
		send(p, new SetInteractionMode(WORLD_DEFAULT, TILE_HEADING, ENTITY_ALL));
		send(p, new SetInteractionMode(boat.index, TILE_DISABLED, ENTITY_ALL));
		p.sendMessage("You take the helm. Click on the sea to set a heading.");
	}

	static void stopNavigating(Player p, boolean message) {
		Boat boat = p.boat;
		if (boat == null || !boat.navigating)
			return;
		boat.navigating = false;
		boat.sailsUp = false;
		FACILITY_LOCKEDIN.set(p, 0);
		send(p, new SetInteractionMode(WORLD_DEFAULT, TILE_WALK, ENTITY_ALL));
		send(p, new SetInteractionMode(boat.index, TILE_WALK, ENTITY_ALL));
		p.resetAnimation();
		if (message)
			p.sendMessage("You step back from the helm.");
	}

	private static void setSails(Player p, boolean up) {
		Boat boat = p.boat;
		if (boat == null)
			return;
		if (!boat.navigating) {
			p.sendMessage("You need to be at the helm to control the sails.");
			return;
		}
		boat.sailsUp = up;
		p.sendFilteredMessage(up ? "You hoist the sails." : "You lower the sails.");
	}

	/** The client's set_heading packet (0-15, 22.5 degrees each). */
	public static void heading(Player p, int heading) {
		Boat boat = p.boat;
		if (boat == null || !boat.navigating || heading < 0 || heading > 15)
			return;
		boat.targetAngle = heading * 128;
		boat.sailsUp = true;
	}

	/** The boat moved under its rider: rebuild the map when it nears the edge of the loaded area. */
	static void onBoatMoved(Player p, Boat boat) {
		if (p.updateRegion())
			p.getUpdater().updateRegion = true;
	}

	private static void send(Player p, net.rsprot.protocol.message.OutgoingGameMessage msg) {
		if (p.rsprotSession != null)
			p.rsprotSession.queue(msg);
	}
}
