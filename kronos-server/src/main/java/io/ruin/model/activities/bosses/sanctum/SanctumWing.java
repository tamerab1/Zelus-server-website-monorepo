package io.ruin.model.activities.bosses.sanctum;

/**
 * The Sanctum's wings, in God Wars Dungeon coordinates (plane 2) -- {@link SanctumInstance} converts them into the
 * player's private copy. Tiles were picked from the cache's walkable floor (flood fill from each boss door).
 * The Saradomin wing (plane 0) is reserved for a future boss.
 */
enum SanctumWing {

	BANDOS("Thunderborn", "Sanguine Harrowlord", 30599, 30594, 26503,
			new int[]{2863, 5354}, new int[]{2862, 5354}, new int[]{2864, 5354}, new int[]{2870, 5360},
			new int[]{2864, 5350, 2877, 5369},
			new int[]{2845, 5347},
			new int[][]{{2829, 5340}, {2833, 5354}, {2837, 5344}, {2837, 5348}, {2839, 5357}, {2843, 5338}, {2847, 5343},
					{2848, 5351}, {2855, 5355}, {2857, 5351}}),

	ZAMORAK("Rimeweaver", "Gloomfang Devourer", 30598, 30596, 26505,
			new int[]{2925, 5332}, new int[]{2925, 5333}, new int[]{2925, 5331}, new int[]{2926, 5324},
			new int[]{2917, 5318, 2935, 5331},
			new int[]{2913, 5345},
			new int[][]{{2905, 5348}, {2904, 5356}, {2911, 5354}, {2915, 5340}, {2916, 5350}, {2923, 5339}, {2922, 5356},
					{2924, 5349}, {2927, 5339}, {2928, 5347}}),

	ARMADYL("Jadeshot", "Venomtongue Abomination", 30600, 30597, 26502,
			new int[]{2839, 5295}, new int[]{2839, 5294}, new int[]{2839, 5296}, new int[]{2833, 5302},
			new int[]{2824, 5296, 2842, 5308},
			new int[]{2848, 5279},
			new int[][]{{2832, 5284}, {2834, 5291}, {2837, 5272}, {2840, 5262}, {2840, 5278}, {2844, 5266}, {2841, 5288},
					{2847, 5260}, {2844, 5282}, {2850, 5270}});

	static final int PLANE = 2;

	/** Overlay label (God Wars overlay row) and the boss shown in messages. */
	final String guardianName, bossName;
	final int guardianId, bossId, doorId;
	/** roomCentre: the boss stands centred on it; room: x1, y1, x2, y2 (the boss only fights players inside). */
	final int[] door, outside, inside, roomCentre, room, arrival;
	final int[][] guardianSpawns;

	SanctumWing(String guardianName, String bossName, int guardianId, int bossId, int doorId, int[] door, int[] outside, int[] inside,
				int[] roomCentre, int[] room, int[] arrival, int[][] guardianSpawns) {
		this.guardianName = guardianName;
		this.bossName = bossName;
		this.guardianId = guardianId;
		this.bossId = bossId;
		this.doorId = doorId;
		this.door = door;
		this.outside = outside;
		this.inside = inside;
		this.roomCentre = roomCentre;
		this.room = room;
		this.arrival = arrival;
		this.guardianSpawns = guardianSpawns;
	}

}
