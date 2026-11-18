package io.ruin.model.activities.bosses;

import io.ruin.api.utils.Random;
import io.ruin.model.combat.Hit;
import io.ruin.model.entity.player.Player;
import io.ruin.model.item.Item;
import io.ruin.model.item.actions.ItemAction;
import io.ruin.model.item.actions.impl.Consumable;
import io.ruin.model.item.actions.impl.pet.Pet;
import io.ruin.model.stat.StatType;

/**
 * The inventory options of the Maggot King's loot, per the OSRS wiki (Dull ancient medal, Stymphike tartare, Maggot egg,
 * Blood sac, Elder venator fang, Tarnished items; fetched 2026-11-14). Ids are the rev 241 cache's.
 */
public final class MaggotKingItems {

	private MaggotKingItems() {
	}

	static final int DULL_MEDAL = 33627, TARTARE = 33628, ROTTEN_TARTARE = 33629, BLOOD_SAC = 33623, VIAL = 229, VIAL_OF_BLOOD = 22446,
		ELDER_FANG = 33634, ETCHED_FANG = 33636, CHISEL = 1755;
	/* Maggot egg, sickly, warm, pulsating, wriggling, writhing: pet chance on Pop (1 in n) */
	static final int[][] EGGS = { { 33665, 3000 }, { 33667, 2500 }, { 33669, 1250 }, { 33671, 500 }, { 33673, 10 }, { 33675, 2 } };
	/* tarnished item -> { {result, weight /10} ... } */
	static final int[][][] TARNISHED = {
		{ { 33679 }, { 1247, 7 }, { 1245, 2 }, { 1243, 1 } },  // spear: rune / adamant / mithril
		{ { 33687 }, { 1641, 7 }, { 21087, 2 }, { 1637, 1 } }, // ring: ruby / topaz / sapphire
		{ { 33689 }, { 11085, 5 }, { 11092, 4 }, { 21123, 1 } }, // bracelet: ruby / diamond / topaz
		{ { 33691 }, { 1660, 5 }, { 1662, 4 }, { 21093, 1 } }, // necklace: ruby / diamond / jade
		{ { 33693 }, { 1679, 6 }, { 1675, 3 }, { 1681, 1 } },  // amulet (u): ruby / sapphire / diamond
		{ { 33683 }, { 1373, 5 }, { 1371, 4 }, { 1377, 1 } },  // battleaxe: rune / adamant / dragon
		{ { 33677 }, { 1303, 5 }, { 1301, 4 }, { 1305, 1 } },  // longsword: rune / adamant / dragon
		{ { 33685 }, { 3202, 5 }, { 3200, 4 }, { 3204, 1 } },  // halberd: rune / adamant / dragon
		{ { 33681 }, { 1319, 5 }, { 1317, 4 }, { 1315, 1 } },  // 2h sword: rune / adamant / mithril
	};
	private static final String SAC_COUNT = "MAGGOT_BLOOD_SAC_COUNT", MEDAL_ACTIVE = "MAGGOT_MEDAL_ACTIVE";

	public static void register() {
		ItemAction.registerInventory(DULL_MEDAL, "Polish", (p, item) -> polishMedal(p, item));
		ItemAction.registerInventory(TARTARE, "Eat", (p, item) -> eatTartare(p, item));
		for (int[] egg : EGGS)
			ItemAction.registerInventory(egg[0], "Pop", (p, item) -> popEgg(p, item, egg[1]));
		ItemAction.registerInventory(BLOOD_SAC, "Squeeze", (p, item) -> squeeze(p, item));
		ItemAction.registerInventory(ELDER_FANG, "Etch", (p, item) -> etch(p));
		for (int[][] t : TARNISHED)
			ItemAction.registerInventory(t[0][0], "Polish", (p, item) -> polishTarnished(p, item, t));
	}

	/** The Maggot King's lair (and its approach): the wiki's "inside Vampyrium". */
	static boolean inVampyrium(Player p) {
		if (MaggotKing.inLair(p))
			return true;
		int x = p.getAbsX(), y = p.getAbsY();
		return x >= 2560 && x <= 2751 && y >= 7744 && y <= 7935; // the regions around the lair entrance (2677,7851)
	}

	/**
	 * Polish (inside Vampyrium only): 8 prayer points every 6 ticks for 120 ticks; a 3/7 chance it also gives up to +20
	 * Attack / Strength / Defence, +14 Ranged and +11 Magic, re-applied over 4m30s, for 1 damage. Outside, it falls apart.
	 */
	private static void polishMedal(Player p, Item item) {
		if (!inVampyrium(p)) {
			item.remove();
			p.sendMessage("The medal falls apart as you polish it.");
			return;
		}
		item.remove();
		boolean combat = Random.get(1, 7) <= 3;
		if (combat) {
			p.sendMessage("The medal quivers with magic and breaks apart!");
			p.hit(new Hit().fixedDamage(1));
		} else {
			p.sendMessage("The medal releases a trace of ancient magic as it falls apart.");
		}
		p.set(MEDAL_ACTIVE, true);
		p.addEvent(e -> {
			for (int t = 0; t < 120; t += 6) {
				if (!inVampyrium(p))
					break;
				p.getStats().get(StatType.Prayer).restore(8, 0);
				e.delay(6);
			}
			p.sendMessage("The prayer restoration blessing dissipates.");
		});
		if (combat) {
			p.addEvent(e -> {
				for (int t = 0; t < 450; t += 25) {
					if (!inVampyrium(p))
						break;
					p.getStats().get(StatType.Attack).boost(20, 0);
					p.getStats().get(StatType.Strength).boost(20, 0);
					p.getStats().get(StatType.Defence).boost(20, 0);
					p.getStats().get(StatType.Ranged).boost(14, 0);
					p.getStats().get(StatType.Magic).boost(11, 0);
					e.delay(25);
				}
				p.sendMessage("You feel the power start to diffuse.");
				p.remove(MEDAL_ACTIVE);
			});
		}
	}

	/** Eat: heals 26 and poisons for 1; outside Vampyrium the meat has rotted. */
	private static void eatTartare(Player p, Item item) {
		if (p.isLocked() || p.isStunned() || p.eatDelay.isDelayed())
			return;
		if (!inVampyrium(p)) {
			item.setId(ROTTEN_TARTARE);
			p.sendMessage("On closer inspection, it looks like that meat is rotten.");
			return;
		}
		item.remove();
		Consumable.animEat(p);
		p.incrementHp(26);
		p.poison(1);
		p.eatDelay.delay(1);
		p.getCombat().delayAttack(4);
	}

	/** Pop: each egg has its own chance to hatch the maggot marquess. */
	private static void popEgg(Player p, Item item, int petChance) {
		item.remove(1);
		if (Random.get(1, petChance) == 1)
			Pet.MAGGOT_MARQUESS.unlock(p, 0);
	}

	/** Squeeze into an empty vial: 50 blood sacs fill a vial of blood. Without a vial it empties onto the floor. */
	private static void squeeze(Player p, Item item) {
		item.remove(1);
		if (!p.getInventory().hasId(VIAL)) {
			p.sendMessage("You squeeze a sac. It empties onto the floor.");
			return;
		}
		int count = (Integer) p.get(SAC_COUNT, 0) + 1;
		if (count >= 50) {
			p.getInventory().remove(VIAL, 1);
			p.getInventory().addOrDrop(VIAL_OF_BLOOD, 1);
			count = 0;
		}
		p.set(SAC_COUNT, count);
	}

	/** Etch (84 Crafting, a chisel): the etched elder venator fang, 500 Crafting xp. */
	private static void etch(Player p) {
		if (p.getStats().get(StatType.Crafting).currentLevel < 84) {
			p.sendMessage("You need a Crafting level of 84 to etch the elder venator fang.");
			return;
		}
		if (!p.getInventory().hasId(CHISEL)) {
			p.sendMessage("You need a chisel to etch the elder venator fang.");
			return;
		}
		p.getInventory().remove(ELDER_FANG, 1);
		p.getInventory().addOrDrop(ETCHED_FANG, 1);
		p.getStats().addXp(StatType.Crafting, 500, true);
	}

	/**
	 * Polish (64 Crafting): a random item of the matching kind. NOTE: the wiki gives "200-250 Crafting XP" across the
	 * tarnished items without saying which gets what; jewellery 200, weapons 250 here.
	 */
	private static void polishTarnished(Player p, Item item, int[][] table) {
		if (p.getStats().get(StatType.Crafting).currentLevel < 64) {
			p.sendMessage("You need a Crafting level of 64 to polish this.");
			return;
		}
		int roll = Random.get(1, 10);
		int result = table[1][0];
		for (int i = 1; i < table.length; i++) {
			roll -= table[i][1];
			if (roll <= 0) {
				result = table[i][0];
				break;
			}
		}
		String name = item.getDef().name.toLowerCase();
		item.setId(result);
		boolean jewellery = name.contains("ring") || name.contains("bracelet") || name.contains("necklace") || name.contains("amulet");
		p.getStats().addXp(StatType.Crafting, jewellery ? 200 : 250, true);
		p.sendMessage("You rub the " + name + " on your clothes and are surprised to find a shine underneath the grime.");
	}
}
