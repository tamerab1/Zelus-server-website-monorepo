package io.ruin.model.activities.raids.tfa;

/**
 * Every instanced room of the raid and the source region it is copied from.
 * <p>
 * Five bosses per run: the Archive Guardian and Ocellus are fixed, and slots 2-4 are a west/east choice made at
 * the Nexus doors (section 1 = slot 2, south to north). Regions were matched to bosses by their loc names.
 * <p>
 * {@code sourcePlane} is the first source plane copied to instance plane 0. The Nexus keeps two states in one
 * region: planes 0-1 hold the live Guardian arena, planes 2-3 the same hall after the kill (guardian corpse,
 * supply chest, wheelbarrow).
 */
public enum Raid4RoomType {

	NEXUS("the Nexus", 16471, 0, 2, 1, null),
	NEXUS_CLEARED("the Nexus", 16471, 2, 2, 1, null),

	EZEKIEL("Ezekiel", 16214, 0, 4, 2, Side.WEST),
	BORK("Bork and the Smorks", 16727, 0, 4, 2, Side.EAST),
	MOIA("Moia", 16216, 0, 4, 3, Side.WEST),
	DREADNOTH("Dreadnoth the Jellied", 16728, 0, 4, 3, Side.EAST),
	DAGONHAI("the Dagon'hai", 16215, 0, 4, 4, Side.WEST),
	XARTH("Xarth", 16726, 0, 4, 4, Side.EAST),

	// From live-game region checks (projectile-override plugin, Oct 2026): Ocellus fights in 16472. 16474 is the
	// void reward island ("The Blade teleports you to a Void reward island"; its locs include the loot brazier).
	OCELLUS("Ocellus", 16472, 0, 4, 5, null),
	VAULT("the Vault", 16474, 0, 4, 6, null);

	public enum Side {
		WEST, EAST
	}

	public final String displayName;
	public final int regionId;
	public final int sourcePlane;
	public final int planes;
	public final int slot;
	public final Side side;

	Raid4RoomType(String displayName, int regionId, int sourcePlane, int planes, int slot, Side side) {
		this.displayName = displayName;
		this.regionId = regionId;
		this.sourcePlane = sourcePlane;
		this.planes = planes;
		this.slot = slot;
		this.side = side;
	}

	public int sourceBaseX() {
		return (regionId >> 8) << 6;
	}

	public int sourceBaseY() {
		return (regionId & 0xff) << 6;
	}

	public static Raid4RoomType forChoice(int slot, Side side) {
		for (Raid4RoomType type : values()) {
			if (type.slot == slot && type.side == side)
				return type;
		}
		return null;
	}
}
