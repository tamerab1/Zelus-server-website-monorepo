package io.ruin.model.skills.sailing;

import io.ruin.model.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * What sorting one salvage gives, per type. Every table is the OSRS wiki's (Small/Fishy/Barracuda/Large/Plundered/
 * Martial/Fremennik/Opulent salvage, fetched 2026-11-14), weights exactly as listed ({@code n/total}); item ids are the
 * rev 241 cache's.
 * <ul>
 * <li>Pre-roll: each rolled independently before the main table; a hit is received instead of the main-table item.</li>
 * <li>Main: exactly one weighted pick.</li>
 * <li>Tertiary: rolled independently on top.</li>
 * </ul>
 * "Never" on Sailors' amulet (inert): it isn't rolled while the player already owns one.
 */
public final class SalvageLoot {

	private SalvageLoot() {
	}

	static final int SAILORS_AMULET_INERT = 32398, SOUP = 31283;

	record Drop(int id, int min, int max, double weight) {
	}

	/** {@code chance} is 1/denominator */
	record Roll(int id, int min, int max, double denominator) {
	}

	/** a sub-table rolled from a main-table slot; entries are weighted by their overall wiki rate */
	record Sub(double weight, List<Drop> entries) {
	}

	static final class Table {
		final List<Roll> pre = new ArrayList<>(), tertiary = new ArrayList<>();
		final List<Drop> main = new ArrayList<>();
		final List<Sub> subs = new ArrayList<>();

		Table pre(int id, int min, int max, double denom) {
			pre.add(new Roll(id, min, max, denom));
			return this;
		}

		Table main(int id, int min, int max, double weight) {
			main.add(new Drop(id, min, max, weight));
			return this;
		}

		Table main(int id, double weight) {
			return main(id, 1, 1, weight);
		}

		Table sub(double weight, List<Drop> entries) {
			subs.add(new Sub(weight, entries));
			return this;
		}

		Table tertiary(int id, int min, int max, double denom) {
			tertiary.add(new Roll(id, min, max, denom));
			return this;
		}

		List<int[]> roll(Player player) {
			ThreadLocalRandom r = ThreadLocalRandom.current();
			List<int[]> out = new ArrayList<>(3);
			boolean preHit = false;
			for (Roll p : pre) {
				if (p.id == SAILORS_AMULET_INERT && owns(player, p.id))
					continue;
				if (r.nextDouble() * p.denominator < 1.0) {
					out.add(new int[]{p.id, amount(r, p.min, p.max)});
					preHit = true;
					break;
				}
			}
			if (!preHit) {
				double total = 0;
				for (Drop d : main)
					total += d.weight;
				for (Sub s : subs)
					total += s.weight;
				double pick = r.nextDouble() * total;
				Drop chosen = null;
				for (Drop d : main) {
					if (pick < d.weight) {
						chosen = d;
						break;
					}
					pick -= d.weight;
				}
				if (chosen == null) {
					for (Sub s : subs) {
						if (pick < s.weight) {
							chosen = pickWeighted(r, s.entries);
							break;
						}
						pick -= s.weight;
					}
				}
				if (chosen != null)
					out.add(new int[]{chosen.id, amount(r, chosen.min, chosen.max)});
			}
			for (Roll t : tertiary)
				if (r.nextDouble() * t.denominator < 1.0)
					out.add(new int[]{t.id, amount(r, t.min, t.max)});
			return out;
		}
	}

	private static Drop pickWeighted(ThreadLocalRandom r, List<Drop> entries) {
		double total = 0;
		for (Drop d : entries)
			total += d.weight;
		double pick = r.nextDouble() * total;
		for (Drop d : entries) {
			if (pick < d.weight)
				return d;
			pick -= d.weight;
		}
		return entries.get(entries.size() - 1);
	}

	private static int amount(ThreadLocalRandom r, int min, int max) {
		return max > min ? r.nextInt(min, max + 1) : min;
	}

	private static boolean owns(Player p, int id) {
		return p.getInventory().hasId(id) || p.getBank().hasId(id) || p.getEquipment().hasId(id);
	}

	/** an entry given as an overall "1/x" rate inside a sub-table: weight 1/x */
	private static Drop r(int id, int amount, double denom) {
		return new Drop(id, amount, amount, 1.0 / denom);
	}

	// clue scrolls (Kronos ClueType ids), common items
	private static final int CLUE_BEGINNER = 23182, CLUE_EASY = 2677, CLUE_MEDIUM = 2801, CLUE_HARD = 2722;
	private static final int BOAT_BOTTLE = 31989, FACILITY_BOTTLE = 33074;

	static final Table SMALL = new Table()
		.pre(BOAT_BOTTLE, 1, 1, 750).pre(SAILORS_AMULET_INERT, 1, 1, 2000).pre(32863, 1, 1, 3000) // rusty locket
		// shipbuilding
		.main(2349, 200).main(4819, 1, 5, 200).main(1511, 200).main(4820, 1, 5, 100).main(960, 100).main(1521, 100)
		.main(32083, 100).main(2351, 50).main(8778, 50).main(32085, 50).main(2353, 1)
		// runes and ammunition
		.main(556, 1, 10, 100).main(555, 1, 10, 100).main(31906, 2, 7, 100).main(31908, 2, 7, 50).main(886, 1, 5, 20)
		// other
		.main(995, 1, 200, 100).main(526, 100)
		.tertiary(CLUE_BEGINNER, 1, 1, 300).tertiary(SOUP, 1, 1, 800_000);

	static final Table FISHY = new Table()
		.pre(CLUE_EASY, 1, 1, 300).pre(BOAT_BOTTLE, 1, 1, 750).pre(SAILORS_AMULET_INERT, 1, 1, 1500).pre(32864, 1, 1, 3000) // mouldy block
		// shipbuilding
		.main(1521, 40).main(8778, 20).main(32085, 20).main(1511, 20).main(2351, 20).main(4820, 1, 5, 20).main(1539, 1, 5, 10)
		.main(960, 10).main(2353, 1)
		// fishing
		.main(314, 15, 200, 40).main(313, 20, 100, 40).main(377, 40).main(353, 40).main(371, 40).main(307, 2).main(311, 2).main(301, 2)
		// other
		.main(401, 40).main(31906, 2, 7, 40).main(31908, 1, 5, 20)
		.tertiary(SOUP, 1, 1, 800_000);

	static final Table BARRACUDA = new Table()
		.pre(BOAT_BOTTLE, 1, 1, 500).pre(32865, 1, 1, 3000).pre(SAILORS_AMULET_INERT, 1, 1, 1000) // dull knife
		// seeds
		.main(31541, 1, 3, 500).main(31543, 1, 3, 125)
		// shipbuilding
		.main(1941, 1, 10, 5000).main(1521, 5000).main(6333, 3750).main(1539, 1, 5, 2500).main(31970, 2500).main(954, 1250)
		.main(8778, 1250).main(436, 500).main(8780, 250).main(31716, 25)
		// fish
		.main(371, 500).main(383, 250).main(32349, 1)
		// other
		.main(28896, 2500).main(1963, 1250).main(2, 2, 7, 1250)
		.tertiary(CLUE_MEDIUM, 1, 1, 300).tertiary(SOUP, 1, 1, 800_000);

	static final Table LARGE = new Table()
		.pre(SAILORS_AMULET_INERT, 1, 1, 500).pre(FACILITY_BOTTLE, 1, 1, 1000).pre(32866, 1, 1, 3000) // broken compass
		// shipbuilding
		.main(1539, 1, 5, 200).main(6333, 200).main(8778, 50).main(4822, 1, 5, 30)
		// seeds and frags
		.main(31511, 1, 5, 10).main(31543, 1, 5, 10).main(31545, 1, 5, 5).main(31513, 1, 5, 2).main(31547, 1)
		// jewellery
		.main(1635, 300).main(1637, 300).main(1639, 300).main(1643, 200).main(411, 200).main(413, 200).main(405, 100)
		// other
		.main(21504, 50).main(31910, 2, 5, 50).main(31912, 1, 7, 10)
		.tertiary(SOUP, 1, 1, 800_000);

	static final Table PLUNDERED = new Table()
		.pre(SAILORS_AMULET_INERT, 1, 1, 450).pre(32867, 1, 1, 3000).pre(31916, 2, 3, 3000).pre(FACILITY_BOTTLE, 1, 1, 900) // rusty coin, dragon cannonball
		// sailing supplies
		.main(28896, 100).main(31910, 2, 5, 50).main(31912, 2, 5, 25).main(31973, 25).main(31914, 2, 4, 10)
		// weapons
		.main(1329, 100).main(1333, 1)
		// jewellery
		.main(413, 100).main(1635, 200).main(1637, 100).main(11076, 100).main(11085, 75).main(1639, 50).main(405, 50)
		.main(11092, 25).main(1643, 25)
		.tertiary(SOUP, 1, 1, 800_000);

	static final Table MARTIAL = new Table()
		.pre(SAILORS_AMULET_INERT, 1, 1, 400).pre(CLUE_HARD, 1, 1, 500).pre(FACILITY_BOTTLE, 1, 1, 700).pre(32868, 1, 1, 3000) // broken sextant
		.pre(31916, 2, 3, 3000).pre(32099, 1, 1, 24000) // dragon cannonball, salvor's paint
		// weapons and armour
		.main(1299, 300).main(1301, 200).main(1317, 100).main(1135, 100).main(1303, 10)
		// ammunition and materials
		.main(43, 1, 5, 300).main(822, 1, 5, 300).main(823, 1, 3, 200).main(890, 1, 3, 200).main(9380, 1, 3, 100).main(892, 1, 3, 100)
		.main(31912, 2, 5, 100).main(31914, 2, 4, 50)
		// seeds and frags
		.main(31515, 10).main(31547, 10).main(31549, 1)
		// logs
		.main(6332, 100).main(32904, 6).main(32907, 3)
		// other
		.main(1731, 200).main(4823, 1, 3, 50)
		.tertiary(SOUP, 1, 1, 800_000);

	static final Table FREMENNIK = new Table()
		.pre(SAILORS_AMULET_INERT, 1, 1, 350).pre(FACILITY_BOTTLE, 1, 1, 500).pre(32870, 1, 1, 3000) // smashed mirror
		// fish
		.main(331, 400).main(359, 400).main(377, 400).main(7944, 400)
		// helmets
		.main(3749, 8).main(3751, 8).main(3755, 8).main(3753, 8).main(3748, 8)
		// materials
		.main(447, 300).main(10810, 200).main(4823, 1, 3, 100).main(4824, 1, 3, 50).main(8782, 50).main(32907, 6).main(32910, 3)
		// seeds and frags
		.main(31545, 1, 3, 10).main(31515, 5).main(31549, 5).main(31551, 1)
		// other
		.main(9075, 1, 3, 200).main(13484, 50).main(31914, 2, 5, 50)
		.tertiary(SOUP, 1, 1, 800_000);

	static final Table OPULENT = new Table()
		.pre(SAILORS_AMULET_INERT, 1, 1, 300).pre(FACILITY_BOTTLE, 1, 1, 300).pre(31406, 1, 3, 2000).pre(32869, 1, 1, 3000) // dragon nails, mouldy doll
		.pre(32115, 1, 1, 20000) // dragon cannon barrel
		// sailing resources
		.main(31914, 2, 5, 3).main(11334, 1, 10, 30).main(31979, 10).main(31916, 2, 5, 1)
		// rare seed drop table (10/424)
		.sub(10, List.of(r(5296, 1, 214), r(5297, 1, 312.3), r(5281, 1, 323.2), r(5106, 1, 448.7), r(5298, 1, 457.6),
			r(5280, 1, 481.4), r(22873, 1, 660.2), r(5299, 1, 669.8), r(5300, 1, 1004.7), r(5301, 1, 1444.2),
			r(5302, 1, 2009.4), r(22879, 3, 2310.8), r(5303, 1, 3301.1), r(5304, 1, 5135.1)))
		// useful herb drop table (10/424)
		.sub(10, List.of(r(211, 1, 135.68), r(207, 1, 169.6), r(3051, 1, 169.6), r(219, 1, 226.13)))
		// combat herb drop table (10/424)
		.sub(10, List.of(r(213, 1, 135.7), r(217, 1, 169.6), r(215, 1, 169.6), r(2485, 1, 226.1)))
		// gem drop table (10/424), rates without a ring of wealth
		.sub(10, List.of(r(995, 1, 84.96), r(1623, 1, 169.6), r(1621, 1, 339.2), r(1619, 1, 678.4), r(1462, 1, 1809.07),
			r(1617, 1, 2713.6), r(830, 5, 5427.2), r(987, 1, 5427.2), r(985, 1, 5427.2), r(1247, 1, 86835.2),
			r(2366, 1, 173670.4), r(1249, 1, 231560.53)))
		// other
		.main(1625, 30).main(1627, 30).main(1629, 30).main(442, 30).main(2355, 30).main(5525, 30).main(1993, 30).main(2007, 30)
		.main(958, 30).main(950, 30).main(995, 1000, 5000, 20).main(13204, 1, 5, 20)
		.tertiary(SOUP, 1, 1, 800_000);
}
