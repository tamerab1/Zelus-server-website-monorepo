package io.ruin.model.activities.raids.tfa;

/**
 * Creates the room for a path choice. Returns null for encounters that aren't implemented yet, which the Nexus
 * doors report as sealed.
 */
final class Raid4Rooms {

	private Raid4Rooms() {
	}

	static Raid4Room create(Raid4RoomType type) {
		if (type == null)
			return null;
		return switch (type) {
			case EZEKIEL -> new io.ruin.model.activities.raids.tfa.rooms.EzekielRoom();
			case BORK -> new io.ruin.model.activities.raids.tfa.rooms.BorkRoom();
			case MOIA -> new io.ruin.model.activities.raids.tfa.rooms.MoiaRoom();
			case DREADNOTH -> new io.ruin.model.activities.raids.tfa.rooms.DreadnothRoom();
			case DAGONHAI -> new io.ruin.model.activities.raids.tfa.rooms.DagonhaiRoom();
			case XARTH -> new io.ruin.model.activities.raids.tfa.rooms.XarthRoom();
			case OCELLUS -> new io.ruin.model.activities.raids.tfa.rooms.OcellusRoom();
			case VAULT -> new Raid4VaultRoom();
			default -> null;
		};
	}
}
