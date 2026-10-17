package io.ruin.model.item.actions.impl.pet.perk;

import io.ruin.model.combat.AttackStyle;
import io.ruin.model.entity.Entity;
import io.ruin.model.entity.player.Player;
import io.ruin.model.item.actions.impl.pet.Pet;


/// Central lookup for the pet combat-perk system. A perk is active only while its owning
/// pet is the player's currently-summoned follower (Player#pet, set/cleared by Pet#spawn and
/// Pet#pickup) -- pets can't be killed/removed any other way in this codebase, so there is no
/// separate "alive" check needed beyond "is it still the active pet". Each pet carries its own
/// tuned PetPerk values -- see Pet.java's enum declarations.
public final class PetPerkHandler {

	private PetPerkHandler() {
	}

	public static PetPerk getActivePetPerk(Player player) {
		if (player == null || player.pet == null || player.inRivalsFight()) { // RivalsFairPlay
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

	/// ::petperks -- the Pet Perks window (PetPerksInterface, interface 1141).
	public static void openInterface(Player player) {
		PetPerksInterface.open(player);
	}
}
