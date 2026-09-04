package io.ruin.model.item.actions.impl.pet.perk;

import io.ruin.cache.ObjType;
import io.ruin.model.combat.AttackStyle;
import io.ruin.model.entity.Entity;
import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.dialogue.MessageDialogue;
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

	/// ::petperks -- a chat-dialogue "interface" listing every perk-granting pet with its own
	/// tuned values, grouped by category, plus the player's own currently active perk (if any).
	public static void openInfoDialogue(Player player) {
		List<MessageDialogue> pages = new ArrayList<>();

		Pet active = player.pet;
		String activeLine = active != null && active.perk != null
				? "Your active pet is <col=006600>" + petName(active) + "</col>, granting: <col=006600>"
						+ active.perk.describe() + "</col>."
				: "You have no perk-granting pet summoned right now.";
		pages.add(new MessageDialogue("<col=ff0000>Pet Perks</col><br><br>" + activeLine).lineHeight(20));

		pages.addAll(categoryPages("Melee Pets", PerkType.PET_MELEE_BOOST));
		pages.addAll(categoryPages("Mage Pets", PerkType.PET_MAGE_BOOST));
		pages.addAll(categoryPages("Ranged Pets", PerkType.PET_RANGED_BOOST));
		pages.addAll(categoryPages("Utility Pets", PerkType.PET_UTILITY_BOOST));
		pages.addAll(categoryPages("Drop Rate Pets", PerkType.PET_DROP_RATE_BOOST));

		player.dialogue(pages.toArray(new MessageDialogue[0]));
	}

	// At most 2 pets per page -- a single Mage/Ranged entry already wraps to 2 lines in the
	// dialogue box (name + 3 stats), so anything more than 2 per page pushed text off the
	// bottom, hidden behind the "Click here to continue" prompt (confirmed via screenshot).
	private static final int PETS_PER_PAGE = 2;

	private static List<MessageDialogue> categoryPages(String title, PerkType type) {
		List<Pet> pets = new ArrayList<>();
		for (Pet pet : Pet.VALUES) {
			if (pet.perk != null && pet.perk.type == type) {
				pets.add(pet);
			}
		}

		List<MessageDialogue> pages = new ArrayList<>();
		if (pets.isEmpty()) {
			pages.add(new MessageDialogue("<col=ff0000>" + title + "</col><br><br>(none currently)").lineHeight(18));
			return pages;
		}

		for (int start = 0; start < pets.size(); start += PETS_PER_PAGE) {
			int end = Math.min(start + PETS_PER_PAGE, pets.size());
			StringBuilder sb = new StringBuilder();
			String pageTitle = start == 0 ? title : title + " (cont.)";
			sb.append("<col=ff0000>").append(pageTitle).append("</col><br><br>");
			for (int i = start; i < end; i++) {
				if (i > start) {
					sb.append("<br>");
				}
				Pet pet = pets.get(i);
				sb.append(petName(pet)).append(": ").append(pet.perk.describe());
			}
			pages.add(new MessageDialogue(sb.toString()).lineHeight(18));
		}
		return pages;
	}

	private static String petName(Pet pet) {
		ObjType def = ObjType.get(pet.itemId);
		return def != null && def.name != null ? def.name : pet.name();
	}
}
