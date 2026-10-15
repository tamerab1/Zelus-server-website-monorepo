package io.ruin.model.entity.npc.combatpet;

/**
 * Progression tier of a tiered combat pet (see {@link TieredCombatPetProfile}). Each tier has its own
 * cache recolour/retexture (tier_pets.txt: T1 bronze/iron, T2 cobalt/neon cyan, T3 obsidian + cosmic texture).
 */
public enum PetTier {

	T1(8, 1.0, CombatPetProfile.accuracy(60, 30)),
	T2(7, 1.0, CombatPetProfile.accuracy(75, 55)),
	T3(6, 1.5, CombatPetProfile.accuracy(90, 80));

	/** A special is guaranteed every this many engaged ticks. */
	public final int specialIntervalTicks;
	/** Multiplier for special-effect strength (debuffs, heals, freezes...). */
	public final double effectScale;
	public final int accuracyRoll;

	PetTier(int specialIntervalTicks, double effectScale, int accuracyRoll) {
		this.specialIntervalTicks = specialIntervalTicks;
		this.effectScale = effectScale;
		this.accuracyRoll = accuracyRoll;
	}

	public int number() {
		return ordinal() + 1;
	}

	public PetTier next() {
		return this == T3 ? null : values()[ordinal() + 1];
	}

}
