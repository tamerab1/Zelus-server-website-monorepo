package io.ruin.model.item.actions.impl.pet.perk;

public enum PerkType {

	BONUS_EXP,
	BONUS_DR,
	INVENTORY_AUTO_LOOT,
	BONUS_STRENGTH,
	DRAGON_FIRE_IMMUNITY,
	BONUS_SMITE,
	BANK_AUTO_LOOT,
	KEEP_BONUS_ITEMS_ON_DEATH,
	BONUS_POINTS,
	WALKER_PERK,
	BETA_DONATORS,

	KARUULM_IMMUNITY,

	/**
	 * Combat/utility perks granted by a custom pet while it is summoned as the player's
	 * active follower (see Pet.perk + PetPerkHandler). One pet = one perk = no stacking,
	 * since only one pet can be summoned at a time.
	 */
	PET_MELEE_BOOST,
	PET_MAGE_BOOST,
	PET_RANGED_BOOST,
	PET_UTILITY_BOOST,
	PET_DROP_RATE_BOOST
}