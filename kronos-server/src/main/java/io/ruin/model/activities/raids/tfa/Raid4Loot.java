package io.ruin.model.activities.raids.tfa;

import io.ruin.api.utils.Random;
import io.ruin.cache.ObjType;
import io.ruin.model.entity.player.Player;
import io.ruin.model.item.Item;

import java.util.ArrayList;
import java.util.List;

/**
 * The vault's loot, from the OSRS wiki's "Chest (The Fractured Archive)" page (Drop Rate Project, 157k samples):
 * <ul>
 * <li>a unique replaces the standard loot; once a unique is earned the special chest holds one of Rondache 5/15,
 * Zorya's tome 4/15, Horn of Bran 3/15, Ascension crossbows 2/15, Obligator 1/15;</li>
 * <li>otherwise three rolls on the standard table (weights out of 330), each with a 1/51 chance of x5;</li>
 * <li>tertiaries: Archivist's folio ~1/16 (only if not owned), each elemental fragment 1/80, elite clue ~1/12;</li>
 * <li>poor performance gives cabbage, more likely with each death.</li>
 * </ul>
 * Not published, so Zelus values (marked): the chance a raid earns a unique, how it is shared out, the pet rate
 * and the cabbage threshold.
 */
public final class Raid4Loot {

	private Raid4Loot() {
	}

	/** Chance per completed raid that the team earns a unique; est. (ToB's deathless 1/9.1 as a stand-in). */
	public static final double TEAM_UNIQUE_CHANCE = 1 / 9.1;
	/** Orion per completed raid; est. */
	public static final int PET_RATE = 650;
	/** Each death adds this much to the cabbage chance; est. */
	public static final double CABBAGE_PER_DEATH = 0.15;

	public static final int RONDACHE = 34719, ZORYAS_TOME = 34727, HORN_OF_BRAN = 34679, ASCENSION_CROSSBOWS = 34681,
			OBLIGATOR = 34717, FOLIO = 34722, ORION = 34646, SHARDS = 34669, CABBAGE = 1965, ELITE_CLUE = 12074;
	public static final int[] FRAGMENTS = { 34675, 34673, 34671, 34677 }; // wind, water, earth, fire

	private static final int[][] UNIQUES = { { RONDACHE, 5 }, { ZORYAS_TOME, 4 }, { HORN_OF_BRAN, 3 }, { ASCENSION_CROSSBOWS, 2 }, { OBLIGATOR, 1 } };

	/** {item, min, max, weight /330}. */
	private static final int[][] STANDARD = {
			{ 1391, 34, 37, 11 }, { 4087, 1, 1, 11 }, { 4585, 1, 1, 11 }, { 1201, 5, 7, 11 }, // battlestaff, d legs/skirt, rune kite
			{ 561, 301, 385, 11 }, { 9075, 301, 385, 11 }, { 565, 301, 385, 11 }, { 566, 301, 380, 11 }, { 557, 3009, 3662, 11 },
			{ 205, 60, 66, 7 }, { 207, 18, 20, 7 }, { 3049, 24, 28, 7 }, // grimy harralander, ranarr, toadflax
			{ 5300, 3, 4, 7 }, { 5299, 7, 8, 7 }, { 22879, 7, 8, 7 }, { 5315, 4, 5, 7 }, // snapdragon, kwuarm, snape grass, yew seeds
			{ 1607, 24, 27, 11 }, { 1605, 24, 26, 11 }, { 1603, 23, 27, 11 }, { 1601, 24, 26, 11 }, // sapphire, emerald, ruby, diamond
			{ 440, 851, 915, 6 }, { 449, 60, 72, 6 }, { 451, 25, 26, 6 }, // iron, adamantite, runite ore
			{ SHARDS, 75, 82, 55 }, { 9194, 50, 58, 11 }, { 575, 68, 75, 11 }, { 245, 32, 36, 7 }, // shards, onyx tips, earth orb, wine
			{ 30640, 30, 36, 6 }, { 30771, 180, 191, 6 }, { 7936, 1404, 1632, 6 }, { 6332, 140, 152, 6 }, // pages, catalyst, ess, mahogany
			{ 11237, 350, 369, 6 }, { 383, 80, 92, 6 }, // dragon arrowtips, raw shark
	};
	private static final int STANDARD_TOTAL = 330;

	public static class Reward {
		public final List<Item> items = new ArrayList<>();
		public int unique = -1;
		public boolean pet;
	}

	/**
	 * Rolls every member's chest at once: whether the team earned a unique (and for whom, weighted by points), then
	 * standard loot and tertiaries for everyone else.
	 */
	public static java.util.Map<Player, Reward> roll(List<Player> players, Raid4Points points) {
		java.util.Map<Player, Reward> rewards = new java.util.LinkedHashMap<>();
		Player uniqueWinner = null;
		if (!players.isEmpty() && Math.random() < TEAM_UNIQUE_CHANCE) {
			int total = Math.max(1, points.totalPoints(players));
			int pick = Random.get(total - 1);
			for (Player player : players) {
				pick -= points.points(player);
				if (pick < 0) {
					uniqueWinner = player;
					break;
				}
			}
			if (uniqueWinner == null)
				uniqueWinner = players.get(0);
		}
		for (Player player : players) {
			Reward reward = new Reward();
			Raid4Points.Entry entry = points.of(player);
			if (player == uniqueWinner) {
				reward.unique = rollUnique();
				reward.items.add(new Item(reward.unique, 1));
			} else if (Math.random() < entry.deaths * CABBAGE_PER_DEATH || points.points(player) <= 0) {
				reward.items.add(new Item(CABBAGE, 1));
			} else {
				for (int i = 0; i < 3; i++)
					reward.items.add(rollStandard());
			}
			// tertiaries
			if (Random.get(15) == 0 && !owns(player, FOLIO))
				reward.items.add(new Item(FOLIO, 1));
			for (int fragment : FRAGMENTS) {
				if (Random.get(79) == 0)
					reward.items.add(new Item(fragment, 1));
			}
			if (Random.get(11) == 0)
				reward.items.add(new Item(ELITE_CLUE, 1));
			if (Random.get(PET_RATE - 1) == 0) {
				reward.pet = true;
				reward.items.add(new Item(ORION, 1));
			}
			rewards.put(player, reward);
		}
		return rewards;
	}

	private static int rollUnique() {
		int roll = Random.get(14);
		for (int[] u : UNIQUES) {
			roll -= u[1];
			if (roll < 0)
				return u[0];
		}
		return RONDACHE;
	}

	private static Item rollStandard() {
		int roll = Random.get(STANDARD_TOTAL - 1);
		int[] pick = STANDARD[0];
		for (int[] entry : STANDARD) {
			roll -= entry[3];
			if (roll < 0) {
				pick = entry;
				break;
			}
		}
		int amount = Random.get(pick[1], pick[2]);
		if (Random.get(50) == 0)
			amount *= 5;
		int id = pick[0];
		ObjType def = ObjType.get(id);
		if (def != null && !def.stackable && amount > 1 && def.notedId > 0)
			id = def.notedId;
		return new Item(id, amount);
	}

	private static boolean owns(Player player, int id) {
		return player.getInventory().contains(id, 1) || player.getBank().contains(id, 1) || player.getEquipment().contains(id, 1);
	}

	public static boolean isUnique(int id) {
		for (int[] u : UNIQUES) {
			if (u[0] == id)
				return true;
		}
		return id == ORION;
	}
}
