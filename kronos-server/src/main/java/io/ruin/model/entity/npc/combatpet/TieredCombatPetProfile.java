package io.ruin.model.entity.npc.combatpet;

import io.ruin.model.combat.AttackStyle;

/**
 * Base for pets that come in T1/T2/T3 versions: the tier sets max hit, accuracy, how often the guaranteed
 * special fires and how strong its effects are.
 */
public abstract class TieredCombatPetProfile extends CombatPetProfile {

	public final PetTier tier;

	/** @param maxHits max hit for T1, T2, T3 */
	protected TieredCombatPetProfile(PetTier tier, AttackStyle style, int attackTicks, int attackRange, int... maxHits) {
		super(style, attackTicks, maxHits[tier.ordinal()], attackRange, tier.accuracyRoll, 0, tier.specialIntervalTicks);
		this.tier = tier;
	}

	/** The tier-tinted copy of a spotanim (bronze / electric / cosmic, see {@link TierEffects}). */
	protected int fx(int baseGfx) {
		return TierEffects.of(baseGfx, tier);
	}

	protected io.ruin.model.map.Projectile fx(io.ruin.model.map.Projectile base) {
		return TierEffects.of(base, tier);
	}

	/** {@code base} scaled by the tier's effect strength. */
	protected double scaled(double base) {
		return base * tier.effectScale;
	}

	/** {@code base} scaled by the tier, as a percentage string ("7.5%"). */
	protected String percent(double base) {
		double v = scaled(base) * 100;
		return (v == Math.rint(v) ? String.valueOf((int) v) : String.valueOf(Math.round(v * 10) / 10D)) + "%";
	}

	/** Special-attack max hit: {@code multiplier} x the normal max hit, still under the global cap. */
	protected int specialMax(double multiplier) {
		return Math.min(CombatPetCombat.MAX_PET_HIT, (int) Math.round(maxHit * multiplier));
	}

}
