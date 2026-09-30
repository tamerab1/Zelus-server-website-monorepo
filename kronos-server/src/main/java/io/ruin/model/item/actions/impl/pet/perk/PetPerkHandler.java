package io.ruin.model.item.actions.impl.pet.perk;

import io.ruin.cache.ObjType;
import io.ruin.model.combat.AttackStyle;
import io.ruin.model.entity.Entity;
import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.handlers.OptionScroll;
import io.ruin.model.inter.utils.Option;
import io.ruin.model.item.actions.impl.pet.Pet;

import java.util.ArrayList;
import java.util.List;

/// Central lookup for the pet combat-perk system. A perk is active only while its owning
/// pet is the player's currently-summoned follower (Player#pet, set/cleared by Pet#spawn and
/// Pet#pickup) -- pets can't be killed/removed any other way in this codebase, so there is no
/// separate "alive" check needed beyond "is it still the active pet". Each pet carries its own
/// tuned PetPerk values -- see Pet.java's enum declarations.
public final class PetPerkHandler {

	private PetPerkHandler() {
	}

	public static PetPerk getActivePetPerk(Player player) {
		if (player == null || player.pet == null) {
			return null;
		}
		return player.pet.perk;
	}

	/// entity is the ATTACKER.
	public static double getAccuracyBoost(Entity entity, AttackStyle style) {
		if (entity == null || entity.player == null || style == null) {
			return 0;
		}
		PetPerk perk = getActivePetPerk(entity.player);
		if (perk == null) {
			return 0;
		}
		if (perk.type == PerkType.PET_MELEE_BOOST && style.isMelee()) {
			return perk.value1;
		}
		if (perk.type == PerkType.PET_RANGED_BOOST && style.isRanged()) {
			return perk.value1;
		}
		if (perk.type == PerkType.PET_MAGE_BOOST && style.isMagic()) {
			return perk.value1;
		}
		return 0;
	}

	/// entity is the ATTACKER.
	public static double getDamageBoost(Entity entity, AttackStyle style) {
		if (entity == null || entity.player == null || style == null) {
			return 0;
		}
		PetPerk perk = getActivePetPerk(entity.player);
		if (perk == null) {
			return 0;
		}
		if (perk.type == PerkType.PET_MELEE_BOOST && style.isMelee()) {
			return perk.value2;
		}
		if (perk.type == PerkType.PET_RANGED_BOOST && style.isRanged()) {
			return perk.value2;
		}
		if (perk.type == PerkType.PET_MAGE_BOOST && style.isMagic()) {
			return perk.value2;
		}
		return 0;
	}

	/// entity is the DEFENDER. Mage/Ranged pets grant a flat defence roll bonus regardless
	/// of the incoming attack style.
	public static double getDefenceBoost(Entity entity) {
		if (entity == null || entity.player == null) {
			return 0;
		}
		PetPerk perk = getActivePetPerk(entity.player);
		if (perk == null) {
			return 0;
		}
		if (perk.type == PerkType.PET_MAGE_BOOST || perk.type == PerkType.PET_RANGED_BOOST) {
			return perk.value3;
		}
		return 0;
	}

	/// Multiplies the special-attack regen tick interval; a lower result regens faster.
	public static int applySpecialRegenBoost(Player player, int baseTicks) {
		PetPerk perk = getActivePetPerk(player);
		if (perk == null || perk.type != PerkType.PET_UTILITY_BOOST) {
			return baseTicks;
		}
		return Math.max(1, (int) Math.round(baseTicks * (1D - perk.value1)));
	}

	public static double getPrayerDrainReduction(Player player) {
		PetPerk perk = getActivePetPerk(player);
		return (perk != null && perk.type == PerkType.PET_UTILITY_BOOST) ? perk.value2 : 0;
	}

	/// Whole percentage-point addition, matching Player#calculateDropRate's own unit.
	public static int getDropRateAddition(Player player) {
		PetPerk perk = getActivePetPerk(player);
		return (perk != null && perk.type == PerkType.PET_DROP_RATE_BOOST) ? (int) perk.value1 : 0;
	}

	// ::petperks guide -- same look as the ::bonds guide: a clickable category list, then a
	// scroll page per category. Dark colours only (parchment background); lines stay short
	// (~42 chars) because the scroll interface doesn't wrap text.
	private static final String HEADER = "<col=800000>", CATEGORY = "<col=4a2600>", PET = "<col=000080>",
			TEXT = "<col=1a1a1a>", ACTIVE = "<col=006600>", NONE = "<col=333333>";

	private static final Object[][] CATEGORIES = {
			{"Melee Pets", PerkType.PET_MELEE_BOOST},
			{"Mage Pets", PerkType.PET_MAGE_BOOST},
			{"Ranged Pets", PerkType.PET_RANGED_BOOST},
			{"Utility Pets", PerkType.PET_UTILITY_BOOST},
			{"Drop Rate Pets", PerkType.PET_DROP_RATE_BOOST},
	};

	/// ::petperks -- the player's active pet perk plus a clickable list of pet categories.
	public static void openInterface(Player player) {
		List<Option> options = new ArrayList<>();
		Pet active = player.pet;
		// The status row is fully colour-tagged so it does NOT light up on mouse-over (it isn't
		// clickable); the category rows below have no tags, so clientscript 218's hover colour shows.
		options.add(Option.info(active != null && active.perk != null
				? ACTIVE + petName(active) + " (active): " + active.perk.describe() + "</col>"
				: NONE + "No perk pet summoned right now</col>"));
		for (Object[] category : CATEGORIES) {
			String title = (String) category[0];
			PerkType type = (PerkType) category[1];
			int count = petsOf(type).size();
			options.add(Option.info(" ")); // spacer row
			options.add(new Option(title + " (" + count + " pet" + (count == 1 ? "" : "s") + ")",
					p -> openCategory(p, title, type)));
		}
		// keepOpen, so clicking the status/spacer rows does nothing instead of closing the list.
		OptionScroll.openKeepOpen(player, "Pet Perks", options);
	}

	private static void openCategory(Player player, String title, PerkType type) {
		List<String> lines = new ArrayList<>();
		lines.add("");
		lines.add(TEXT + "A pet's perk works while it's summoned.");
		for (Pet pet : petsOf(type)) {
			lines.add("");
			boolean isActive = player.pet == pet;
			lines.add(PET + petName(pet) + "</col>" + (isActive ? ACTIVE + " (active)</col>" : ""));
			for (String stat : pet.perk.describeLines())
				lines.add(TEXT + io.ruin.model.content.bonds.BondGuide.BULLET + stat);
		}
		if (petsOf(type).isEmpty()) {
			lines.add("");
			lines.add(NONE + "(no pets in this category yet)");
		}
		io.ruin.model.content.bonds.BondGuide.openRows(player, title, lines,
				io.ruin.model.content.bonds.BondGuide.BACK_PREFIX + "Back to all categories", PetPerkHandler::openInterface);
	}

	private static List<Pet> petsOf(PerkType type) {
		List<Pet> pets = new ArrayList<>();
		for (Pet pet : Pet.VALUES) {
			if (pet.perk != null && pet.perk.type == type)
				pets.add(pet);
		}
		return pets;
	}

	private static String petName(Pet pet) {
		ObjType def = ObjType.get(pet.itemId);
		return def != null && def.name != null ? def.name : pet.name();
	}
}
