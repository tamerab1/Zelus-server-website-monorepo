package io.ruin.model.activities.colosseum;

import io.ruin.model.entity.npc.NPC;
import io.ruin.model.entity.npc.NPCAction;
import io.ruin.model.map.Direction;
import io.ruin.model.map.Position;

import java.util.*;

/**
 * The trial hosts in the Colosseum hall (2026-11-07): each trial's headline boss stands around the room as a talk-only
 * copy (cache stage 21, npcs 21950-21957: Talk-to, no Attack, no combat level). Talk-to opens the trial picker
 * (TrialSelectInterface) on that host's trial: the briefing and Enter solo / Form duo live there, never in the chatbox.
 * Each host has its own spot ({@link #SPOT}), facing into the hall.
 */
public final class TrialHosts {

	private TrialHosts() {
	}

	/** host npc per trial, in Trial's order (PatchColosseum.HOSTS) */
	static final Map<Trial, Integer> HOST = new EnumMap<>(Map.of(
			Trial.MORVATH, 21950, Trial.TERRAX, 21951, Trial.VESPIRA, 21952, Trial.TITANS, 21953,
			Trial.VOID_PACT, 21954, Trial.ARCANE, 21955, Trial.CHAMPIONS, 21956, Trial.BALAROK, 21957));
	private static final Position CENTRE = new Position(3231, 5855, 0);

	static void register() {
		for (var e : HOST.entrySet()) {
			Trial t = e.getKey();
			// no chatbox: Talk-to opens the trial picker on this host's trial (2026-11-07)
			NPCAction.register(e.getValue(), 1, (player, npc) -> TrialSelectInterface.open(player, t));
		}
	}

	/**
	 * Where each host stands (user, 2026-11-07; round 5 fine-tuned, the Void Pact's Professor Malakor on the west wall).
	 * The pillars near these spots were removed by cache stage 21b.
	 */
	static final Map<Trial, Position> SPOT = new EnumMap<>(Map.of(
			Trial.TERRAX, new Position(3251, 5860, 0), Trial.TITANS, new Position(3251, 5848, 0),
			Trial.CHAMPIONS, new Position(3238, 5841, 0), Trial.ARCANE, new Position(3247, 5841, 0),
			Trial.VESPIRA, new Position(3223, 5842, 0), Trial.MORVATH, new Position(3215, 5842, 0),
			Trial.BALAROK, new Position(3209, 5854, 0), Trial.VOID_PACT, new Position(3211, 5847, 0)));

	/** Every host on its own spot, facing into the hall. */
	static void spawn() {
		for (Trial t : Trial.values()) {
			Position at = SPOT.get(t);
			new NPC(HOST.get(t)).spawn(at.getX(), at.getY(), at.getZ(), facing(at), 0);
		}
	}

	/** away from the wall it stands at: the south wall looks north, the west wall east, the east wall west */
	private static Direction facing(Position at) {
		if (at.getY() <= 5843)
			return Direction.NORTH;
		return at.getX() < CENTRE.getX() ? Direction.EAST : Direction.WEST;
	}
}
