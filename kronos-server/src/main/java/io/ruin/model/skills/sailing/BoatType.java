package io.ruin.model.skills.sailing;

import java.util.List;

/**
 * Boat hulls. Every placement here is read from the rev 241 cache's Sailing db tables (dumped 2026-11-14):
 * <ul>
 * <li>sailing_boat (166) row 8110 Raft: hotspot coordgrid 331405626 = (3843,6458,1), the single facility slot
 * (options row 8526: cannons, salvaging hooks, cargo holds, ...).</li>
 * <li>sailing_boat_steering (181) row 8320 Bronze helm: loc 59554 at (3843,6460,1) rotation 0 shape 10.</li>
 * <li>sailing_boat_sail (179) row 8299 Wooden mast and linen sails: loc 59530 at (3843,6459,1).</li>
 * <li>sailing_boat_sail_pattern (230) row 16257 Plain sail: canvas loc 29506 at (3843,6461,1).</li>
 * <li>sailing_boat_hull (178) row 8264 Wooden base: hull loc 59494 (placed in the showroom map at 3843,6458,
 * length 3), facility stats row 8161: base speed 192, speed cap 320.</li>
 * </ul>
 * The deck instance is a copy of the showroom zone {@code (helm tile) >> 3}; offsets below are from the helm tile.
 */
public enum BoatType {

	RAFT(1, 3843, 6460, 1, 0, 1, 3, 192, List.of(
		new Part(Role.HELM, 0, 0, -1, 59554, 0, 0b1111),          // bronze helm (steering row 8320)
		new Part(Role.SAIL, 0, -1, -1, 59530, 0, 0),              // wooden mast (sail row 8299)
		new Part(Role.SAIL_CANVAS, 0, 1, -1, 29506, 0, 0b10010),  // plain sail canvas (pattern row 16257): Set / Un-set
		new Part(Role.FACILITY, 0, -2, -1, 60483, 0, 0b1111))),   // facility hotspot (3843,6458); default bronze hook

	SKIFF(3, 3843, 6453, 1, -1, 2, 5, 192, List.of(
		new Part(Role.HELM, 1, 1, -1, 59576, 0, 0b1111),          // bronze helm (steering row 8327, 331422006)
		new Part(Role.SAIL, 1, -1, -1, 59537, 0, 0b10),           // wooden mast (sail row 8306, 331422004)
		new Part(Role.SAIL_CANVAS, 1, 0, -1, 29516, 0, 0b10010),  // plain sail canvas (pattern row 16289, 331422005)
		new Part(Role.FACILITY, 0, -3, -1, 60490, 1, 0b1111)));   // hook hotspot (sailing_boat row 8111); bronze 2x5 hook

	public enum Role { HELM, SAIL, SAIL_CANVAS, FACILITY }

	public record Part(Role role, int offX, int offY, int level, int locId, int rotation, int opFlags) {
	}

	public final int worldEntityType, sourceZoneX, sourceZoneY, anchorX, anchorY, deckLevel, helmStandOffY, hullWidth, hullLength;
	/** fine units per tick at full sail (sailing_boat_facility_stats boat_basespeed of the base hull) */
	public final int baseSpeed;
	public final List<Part> parts;

	BoatType(int worldEntityType, int helmX, int helmY, int deckLevel, int helmStandOffY, int hullWidth, int hullLength,
			 int baseSpeed, List<Part> parts) {
		this.worldEntityType = worldEntityType;
		this.sourceZoneX = helmX >> 3;
		this.sourceZoneY = helmY >> 3;
		this.anchorX = helmX & 7;
		this.anchorY = helmY & 7;
		this.deckLevel = deckLevel;
		this.helmStandOffY = helmStandOffY;
		this.hullWidth = hullWidth;
		this.hullLength = hullLength;
		this.baseSpeed = baseSpeed;
		this.parts = parts;
	}

	public Part part(Role role) {
		for (Part p : parts)
			if (p.role == role)
				return p;
		return null;
	}
}
