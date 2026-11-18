package io.ruin.model.skills.sailing;

import io.ruin.model.map.Position;

/**
 * Every OSRS Sailing port with a gangplank, read from the rev 241 cache (2026-11-16):
 * <ul>
 * <li>sailing_dock db table 194: dock id (= ordinal), name, Sailing level (column 4). Db row = 8587 + id, which is
 * what port_task (197) uses for its start / cargo / end ports.</li>
 * <li>Gangplank loc = 59835 + id (gameval sailing_gangplank_&lt;port&gt;); its tile and rotation are from the cache
 * map. The rotation tells which side the sea is: 0 south, 1 west, 2 north, 3 east (Port Sarim, rot 3, has the sea
 * to the east - the dock verified in game).</li>
 * <li>Port task board (60287+) and ledger table (60322+) loc ids per port (gameval port_task_board_&lt;port&gt;,
 * dock_loading_bay_ledger_table_&lt;port&gt;), -1 where the port has none (wiki mooring points table agrees).</li>
 * </ul>
 * Islands without a gangplank (dock ids 32+) aren't listed: they're moored at by anchoring, not docking.
 */
public enum Port {

	//                  name                    lvl  plank x/y   rot  board  ledger
	PORT_SARIM("Port Sarim", 1, 3051, 3193, 3, 60287, 60322),
	THE_PANDEMONIUM("The Pandemonium", 1, 3070, 2987, 3, 60288, 60324),
	LANDS_END("Land's End", 5, 1507, 3403, 3, 60295, 60335),
	MUSA_POINT("Musa Point", 10, 2961, 3146, 3, 60296, 60323),
	HOSIDIUS("Hosidius", 5, 1726, 3452, 0, -1, 60336),
	RIMMINGTON("Rimmington", 18, 2906, 3225, 1, -1, -1),
	CATHERBY("Catherby", 20, 2796, 3412, 0, 60289, 60325),
	PORT_PISCARILIUS("Port Piscarilius", 15, 1845, 3687, 0, 60293, 60332),
	BRIMHAVEN("Brimhaven", 25, 2758, 3230, 1, 60298, 60328),
	ARDOUGNE("Ardougne", 28, 2671, 3265, 0, 60297, 60327),
	PORT_KHAZARD("Port Khazard", 30, 2686, 3162, 3, 60290, 60329),
	WITCHAVEN("Witchaven", 34, 2747, 3305, 3, -1, -1),
	ENTRANA("Entrana", 36, 2879, 3336, 1, -1, 60326),
	CIVITAS_ILLA_FORTIS("Civitas illa Fortis", 38, 1775, 3142, 1, 60294, 60333),
	CORSAIR_COVE("Corsair Cove", 40, 2580, 2844, 3, 60299, 60330),
	CAIRN_ISLE("Cairn Isle", 42, 2750, 2952, 1, -1, 60337),
	SUNSET_COAST("Sunset Coast", 44, 1512, 2974, 1, -1, 60338),
	THE_SUMMER_SHORE("The Summer Shore", 45, 3174, 2367, 0, 60300, 60339),
	ALDARIN("Aldarin", 46, 1452, 2970, 2, 60301, 60340),
	RUINS_OF_UNKAH("Ruins of Unkah", 48, 3144, 2825, 1, 60291, 60331),
	VOID_KNIGHTS_OUTPOST("Void Knights' Outpost", 50, 2651, 2678, 2, 60302, 60341),
	PORT_ROBERTS("Port Roberts", 50, 1861, 3307, 1, 60303, 60342),
	RED_ROCK("Red Rock", 52, 2809, 2509, 3, 60304, 60343),
	RELLEKKA("Rellekka", 62, 2630, 3705, 0, 60292, 60334),
	ETCETERIA("Etceteria", 65, 2613, 3840, 0, 60305, 60344),
	PORT_TYRAS("Port Tyras", 66, 2144, 3120, 0, 60306, 60347),
	DEEPFIN_POINT("Deepfin Point", 67, 1923, 2758, 0, 60307, 60348),
	JATIZSO("Jatizso", 68, 2412, 3780, 0, -1, 60346),
	NEITIZNOT("Neitiznot", 68, 2309, 3782, 1, -1, 60345),
	PRIFDDINAS("Prifddinas", 70, 2158, 3324, 0, 60308, 60349),
	PISCATORIS("Piscatoris", 75, 2304, 3689, 1, -1, 60350),
	LUNAR_ISLE("Lunar Isle", 76, 2152, 3881, 3, 60309, 60351);

	public static final int GANGPLANK_BASE = 59835, DOCK_ROW_BASE = 8587;
	public static final Port[] VALUES = values();

	public final String title;
	public final int level, plankX, plankY, rotation, board, ledger;

	Port(String name, int level, int plankX, int plankY, int rotation, int board, int ledger) {
		this.title = name;
		this.level = level;
		this.plankX = plankX;
		this.plankY = plankY;
		this.rotation = rotation;
		this.board = board;
		this.ledger = ledger;
	}

	public int gangplank() {
		return GANGPLANK_BASE + ordinal();
	}

	/** this port's sailing_dock db row (port_task's start / cargo / end columns) */
	public int dockRow() {
		return DOCK_ROW_BASE + ordinal();
	}

	/** the sea side of the gangplank, {dx, dy} */
	public int[] seaDir() {
		return switch (rotation) {
			case 0 -> new int[] { 0, -1 };
			case 1 -> new int[] { -1, 0 };
			case 2 -> new int[] { 0, 1 };
			default -> new int[] { 1, 0 };
		};
	}

	/** the dock tile a player lands on when they disembark (the plank for the two verified docks, else the tile behind it) */
	public Position disembark() {
		if (this == PORT_SARIM)
			return new Position(3051, 3193, 0);
		if (this == THE_PANDEMONIUM)
			return new Position(3069, 2987, 0);
		int[] d = seaDir();
		return new Position(plankX - d[0], plankY - d[1], 0);
	}

	/** the sea tile the boat's centre sits on when moored here: alongside the plank, hull parallel to the dock */
	public Position mooring(BoatType type) {
		int[] d = seaDir();
		int off = 1 + type.hullWidth;
		return new Position(plankX + d[0] * off, plankY + d[1] * off, 0);
	}

	/** boat angle at the mooring: 0 lies north-south (docks facing east / west), 512 east-west */
	public int mooringAngle() {
		return seaDir()[0] != 0 ? 0 : 512;
	}

	public static Port forGangplank(int id) {
		int i = id - GANGPLANK_BASE;
		return i >= 0 && i < VALUES.length ? VALUES[i] : null;
	}

	public static Port forDockRow(int row) {
		int i = row - DOCK_ROW_BASE;
		return i >= 0 && i < VALUES.length ? VALUES[i] : null;
	}

	public static Port forBoard(int locId) {
		for (Port p : VALUES)
			if (p.board == locId)
				return p;
		return null;
	}

	public static Port forLedger(int locId) {
		for (Port p : VALUES)
			if (p.ledger == locId)
				return p;
		return null;
	}

	/** the port whose mooring is nearest a sea tile, within {@code range} tiles (Chebyshev) */
	public static Port nearest(Position sea, int range) {
		Port best = null;
		int bestDist = Integer.MAX_VALUE;
		for (Port p : VALUES) {
			int dist = Math.max(Math.abs(p.plankX - sea.getX()), Math.abs(p.plankY - sea.getY()));
			if (dist <= range && dist < bestDist) {
				best = p;
				bestDist = dist;
			}
		}
		return best;
	}
}
