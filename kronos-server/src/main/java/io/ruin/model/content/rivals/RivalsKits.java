package io.ruin.model.content.rivals;

import io.ruin.model.inter.questtab.presets.Preset;
import io.ruin.model.item.Item;
import io.ruin.model.item.containers.Equipment;
import io.ruin.model.skills.magic.SpellBook;

/**
 * The four Division Rivals brackets' kits (2026-10-15), handed out as phantom items for the match only
 * (RivalsSnapshot). Item ids checked against the cache by name (.dev/cache-restore-tool ItemNamesById). Runes go in the
 * inventory: the snapshot empties the rune pouch for the match.
 */
final class RivalsKits {

	private RivalsKits() {
	}

	// gear
	private static final int TORVA_PLATEBODY = 26384, TORVA_PLATELEGS = 26386, ANCESTRAL_HAT = 21018, ANCESTRAL_TOP = 21021,
		ANCESTRAL_BOTTOM = 21024, MASORI_BODY_F = 27238, MASORI_CHAPS_F = 27241, VOIDWAKER = 27690, ZARYTE_CROSSBOW = 26374,
		RUBY_BOLTS_E = 21944, KODAI_WAND = 21006, ELIDINIS_WARD_F = 27251, AVERNIC_DEFENDER = 22322, DHAROK_HELM = 4716,
		DHAROK_GREATAXE = 4718, DHAROK_PLATEBODY = 4720, DHAROK_PLATELEGS = 4722, MYSTIC_TOP = 4091, MYSTIC_BOTTOM = 4093,
		AHRIM_HOOD = 4708, ANCIENT_STAFF = 4675, TORAG_PLATEBODY = 4749, TORAG_PLATELEGS = 4751, KARIL_TOP = 4736,
		DRAGON_CLAWS = 13652, HEAVY_BALLISTA = 19481, DRAGON_JAVELIN = 19484, ELDER_HOOD = 20595, ELDER_TOP = 20517,
		ELDER_ROBE = 20520, MYSTIC_SMOKE_STAFF = 12000, AGS = 11802, MAGIC_SHORTBOW_I = 12788,
		BLACK_DHIDE_CHAPS = 2497, MAGES_BOOK = 6889, ABYSSAL_WHIP = 4151, DDS_PPP = 5698, DRAGON_DEFENDER = 12954,
		ABYSSAL_TENTACLE = 12006, AMETHYST_ARROW = 21326;
	// accessories
	private static final int INFERNAL_CAPE = 21295, IMBUED_SARA_CAPE = 21791, IMBUED_ZAMMY_CAPE = 21795, OCCULT = 12002,
		TORTURE = 19553, FURY = 6585, TORMENTED = 19544, FEROCIOUS = 22981, BARROWS_GLOVES = 7462, MITHRIL_GLOVES = 7458,
		ETERNAL_BOOTS = 13235, DRAGON_BOOTS = 11840, CLIMBING_BOOTS = 3105, BERSERKER_I = 11773;
	// supplies
	private static final int SUPER_COMBAT = 12695, RANGING = 2444, RESTORE = 3024, BREW = 6685, ANGLERFISH = 13441,
		DARK_CRAB = 11936, BLOOD = 565, DEATH = 560, WATER = 555, ASTRAL = 9075, EARTH = 557, WRATH = 21880;

	/**
	 * Fills the inventory from slot {@code from} with {@code id}, leaving the LAST slot empty: every kit is 27 items, so
	 * swapping a 1-handed weapon + shield for a 2-hander always has room for the shield.
	 */
	private static void fill(Item[] inv, int from, int id) {
		for (int i = from; i < inv.length - 1; i++)
			if (inv[i] == null)
				inv[i] = new Item(id, 1);
	}

	/** Torva / Ancestral / Masori, Voidwaker and the Zaryte crossbow; Kodai + Ancients for magic. */
	static final class EndgamePrismatic extends Preset {
		EndgamePrismatic() {
			name = "Endgame Prismatic";
			attack = level(99);
			strength = level(99);
			defence = level(99);
			ranged = level(99);
			prayer = level(99);
			magic = level(99);
			hitpoints = level(99);
			spellBook = SpellBook.ANCIENT;
			equipItems[Equipment.SLOT_HAT] = new Item(ANCESTRAL_HAT, 1);
			equipItems[Equipment.SLOT_CAPE] = new Item(IMBUED_SARA_CAPE, 1);
			equipItems[Equipment.SLOT_AMULET] = new Item(OCCULT, 1);
			equipItems[Equipment.SLOT_WEAPON] = new Item(KODAI_WAND, 1);
			equipItems[Equipment.SLOT_CHEST] = new Item(ANCESTRAL_TOP, 1);
			equipItems[Equipment.SLOT_SHIELD] = new Item(ELIDINIS_WARD_F, 1);
			equipItems[Equipment.SLOT_LEGS] = new Item(ANCESTRAL_BOTTOM, 1);
			equipItems[Equipment.SLOT_HANDS] = new Item(TORMENTED, 1);
			equipItems[Equipment.SLOT_FEET] = new Item(ETERNAL_BOOTS, 1);
			equipItems[Equipment.SLOT_RING] = new Item(BERSERKER_I, 1);
			equipItems[Equipment.SLOT_AMMO] = new Item(RUBY_BOLTS_E, 100);
			int[] inv = {ZARYTE_CROSSBOW, MASORI_BODY_F, MASORI_CHAPS_F, VOIDWAKER, TORVA_PLATEBODY, TORVA_PLATELEGS,
				AVERNIC_DEFENDER, FEROCIOUS, SUPER_COMBAT, RANGING, RESTORE, RESTORE, RESTORE, BREW, BREW, BREW, BREW};
			for (int i = 0; i < inv.length; i++)
				invItems[i] = new Item(inv[i], 1);
			invItems[17] = new Item(BLOOD, 500);
			invItems[18] = new Item(DEATH, 1000);
			invItems[19] = new Item(WATER, 1500);
			fill(invItems, 20, ANGLERFISH);
		}
	}

	/**
	 * Full Dharok's, Vengeance (Lunar), and NO overhead prayers (RivalsArenaInstance enforces it). Whip + defender as the
	 * main weapon, the greataxe as the low-hitpoints finisher and a DDS (p++) for quick specs.
	 */
	static final class DharokRisk extends Preset {
		DharokRisk() {
			name = "Dharok Risk-Fight";
			attack = level(99);
			strength = level(99);
			defence = level(99);
			ranged = level(99);
			prayer = level(99);
			magic = level(94);
			hitpoints = level(99);
			spellBook = SpellBook.LUNAR;
			equipItems[Equipment.SLOT_HAT] = new Item(DHAROK_HELM, 1);
			equipItems[Equipment.SLOT_CAPE] = new Item(INFERNAL_CAPE, 1);
			equipItems[Equipment.SLOT_AMULET] = new Item(TORTURE, 1);
			equipItems[Equipment.SLOT_WEAPON] = new Item(ABYSSAL_WHIP, 1);
			equipItems[Equipment.SLOT_SHIELD] = new Item(DRAGON_DEFENDER, 1);
			equipItems[Equipment.SLOT_CHEST] = new Item(DHAROK_PLATEBODY, 1);
			equipItems[Equipment.SLOT_LEGS] = new Item(DHAROK_PLATELEGS, 1);
			equipItems[Equipment.SLOT_HANDS] = new Item(FEROCIOUS, 1);
			equipItems[Equipment.SLOT_FEET] = new Item(DRAGON_BOOTS, 1);
			equipItems[Equipment.SLOT_RING] = new Item(BERSERKER_I, 1);
			int[] inv = {DHAROK_GREATAXE, DDS_PPP, SUPER_COMBAT, SUPER_COMBAT, RESTORE, RESTORE, RESTORE, RESTORE, BREW, BREW, BREW, BREW};
			for (int i = 0; i < inv.length; i++)
				invItems[i] = new Item(inv[i], 1);
			invItems[12] = new Item(ASTRAL, 200);
			invItems[13] = new Item(DEATH, 400);
			invItems[14] = new Item(EARTH, 1000);
			fill(invItems, 15, ANGLERFISH);
		}
	}

	/** Barrows and Mystics with Ancients, an Abyssal tentacle (main melee), Dragon claws and a Heavy ballista. */
	static final class WildernessOutlaw extends Preset {
		WildernessOutlaw() {
			name = "Wilderness Outlaw";
			attack = level(99);
			strength = level(99);
			defence = level(99);
			ranged = level(99);
			prayer = level(99);
			magic = level(99);
			hitpoints = level(99);
			spellBook = SpellBook.ANCIENT;
			equipItems[Equipment.SLOT_HAT] = new Item(AHRIM_HOOD, 1);
			equipItems[Equipment.SLOT_CAPE] = new Item(IMBUED_ZAMMY_CAPE, 1);
			equipItems[Equipment.SLOT_AMULET] = new Item(OCCULT, 1);
			equipItems[Equipment.SLOT_WEAPON] = new Item(ANCIENT_STAFF, 1);
			equipItems[Equipment.SLOT_CHEST] = new Item(MYSTIC_TOP, 1);
			equipItems[Equipment.SLOT_SHIELD] = new Item(MAGES_BOOK, 1);
			equipItems[Equipment.SLOT_LEGS] = new Item(MYSTIC_BOTTOM, 1);
			equipItems[Equipment.SLOT_HANDS] = new Item(BARROWS_GLOVES, 1);
			equipItems[Equipment.SLOT_FEET] = new Item(CLIMBING_BOOTS, 1);
			equipItems[Equipment.SLOT_RING] = new Item(BERSERKER_I, 1);
			equipItems[Equipment.SLOT_AMMO] = new Item(DRAGON_JAVELIN, 100);
			int[] inv = {ABYSSAL_TENTACLE, DRAGON_CLAWS, HEAVY_BALLISTA, TORAG_PLATEBODY, TORAG_PLATELEGS, KARIL_TOP, FURY,
				SUPER_COMBAT, RANGING, RESTORE, RESTORE, RESTORE, BREW, BREW, BREW, BREW};
			for (int i = 0; i < inv.length; i++)
				invItems[i] = new Item(inv[i], 1);
			invItems[16] = new Item(BLOOD, 500);
			invItems[17] = new Item(DEATH, 1000);
			invItems[18] = new Item(WATER, 1500);
			fill(invItems, 19, DARK_CRAB);
		}
	}

	/**
	 * 75 Attack / 1 Defence: Elder chaos robes, Fire Surge (smoke staff), AGS and a Magic shortbow (i) with Amethyst
	 * arrows (Dragon arrows are above the bow's tier: "Your weapon is not strong enough to fire that ammo").
	 */
	static final class MaxedPure extends Preset {
		MaxedPure() {
			name = "Maxed Pure";
			attack = level(75);
			strength = level(99);
			defence = level(1);
			ranged = level(99);
			prayer = level(52);
			magic = level(99);
			hitpoints = level(99);
			spellBook = SpellBook.MODERN;
			equipItems[Equipment.SLOT_HAT] = new Item(ELDER_HOOD, 1);
			equipItems[Equipment.SLOT_CAPE] = new Item(IMBUED_ZAMMY_CAPE, 1);
			equipItems[Equipment.SLOT_AMULET] = new Item(OCCULT, 1);
			equipItems[Equipment.SLOT_WEAPON] = new Item(MYSTIC_SMOKE_STAFF, 1);
			equipItems[Equipment.SLOT_CHEST] = new Item(ELDER_TOP, 1);
			equipItems[Equipment.SLOT_SHIELD] = new Item(MAGES_BOOK, 1);
			equipItems[Equipment.SLOT_LEGS] = new Item(ELDER_ROBE, 1);
			equipItems[Equipment.SLOT_HANDS] = new Item(MITHRIL_GLOVES, 1);
			equipItems[Equipment.SLOT_FEET] = new Item(CLIMBING_BOOTS, 1);
			equipItems[Equipment.SLOT_RING] = new Item(BERSERKER_I, 1);
			equipItems[Equipment.SLOT_AMMO] = new Item(AMETHYST_ARROW, 100);
			int[] inv = {AGS, MAGIC_SHORTBOW_I, BLACK_DHIDE_CHAPS, FURY, SUPER_COMBAT, RANGING, RESTORE, RESTORE, RESTORE, BREW, BREW, BREW};
			for (int i = 0; i < inv.length; i++)
				invItems[i] = new Item(inv[i], 1);
			invItems[12] = new Item(WRATH, 300);
			fill(invItems, 13, ANGLERFISH);
		}
	}
}
