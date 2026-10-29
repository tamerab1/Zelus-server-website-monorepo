package io.ruin.model.activities.raids.tfa.bosses.guardian;

import io.ruin.model.entity.npc.NPC;
import io.ruin.model.map.Position;

/**
 * One of the four inert guardians around the Archive Guardian. Charging one strips a chunk of the boss's shield.
 * The wiki says some take melee, some ranged or earth spells, and one only the laser (reported as the north-east
 * one); which of the other three takes which isn't documented.
 */
public class Battery {

	public enum Charge {
		MELEE, RANGED_OR_EARTH, BEAM
	}

	public enum State {
		INACTIVE, ACTIVE, DESTROYED
	}

	public final int index;
	public final Charge charge;
	public final Position tile;
	public NPC npc;
	public State state = State.INACTIVE;

	public Battery(int index, Charge charge, Position tile, NPC npc) {
		this.index = index;
		this.charge = charge;
		this.tile = tile;
		this.npc = npc;
	}

	/** True if the 2x2 footprint covers the tile. */
	public boolean covers(int x, int y) {
		return x >= tile.getX() && x <= tile.getX() + 1 && y >= tile.getY() && y <= tile.getY() + 1;
	}
}
