package io.ruin.model.item.actions.impl.pet.perk;

/// Per-pet perk values. Values are fractions (0.20 = 20%) except DROP_RATE, whose value1 is a
/// whole percentage-point addition matching Player#calculateDropRate's own unit.
///
/// Field meaning depends on type:
///  - MELEE:      value1=accuracy, value2=damage
///  - MAGE:       value1=accuracy, value2=damage, value3=defence
///  - RANGED:     value1=accuracy, value2=damage, value3=defence
///  - UTILITY:    value1=special regen speedup, value2=prayer drain reduction
///  - DROP_RATE:  value1=drop rate addition (whole percentage points, e.g. 10 = +10%)
public final class PetPerk {

	public final PerkType type;
	public final double value1, value2, value3;

	private PetPerk(PerkType type, double value1, double value2, double value3) {
		this.type = type;
		this.value1 = value1;
		this.value2 = value2;
		this.value3 = value3;
	}

	public static PetPerk melee(double accuracy, double damage) {
		return new PetPerk(PerkType.PET_MELEE_BOOST, accuracy, damage, 0);
	}

	/// Uses the shared default mage defence bonus (PetPerkBonuses.MAGE_DEFENCE_BOOST).
	public static PetPerk mage(double accuracy, double damage) {
		return mage(accuracy, damage, PetPerkBonuses.MAGE_DEFENCE_BOOST);
	}

	public static PetPerk mage(double accuracy, double damage, double defence) {
		return new PetPerk(PerkType.PET_MAGE_BOOST, accuracy, damage, defence);
	}

	public static PetPerk ranged(double accuracy, double damage, double defence) {
		return new PetPerk(PerkType.PET_RANGED_BOOST, accuracy, damage, defence);
	}

	public static PetPerk utility(double specialRegenBoost, double prayerDrainReduction) {
		return new PetPerk(PerkType.PET_UTILITY_BOOST, specialRegenBoost, prayerDrainReduction, 0);
	}

	public static PetPerk dropRate(double additionPercent) {
		return new PetPerk(PerkType.PET_DROP_RATE_BOOST, additionPercent, 0, 0);
	}

	/// Human-readable summary, used by ::petperks and baked into each pet item's examine text.
	public String describe() {
		return switch (type) {
			case PET_MELEE_BOOST -> "+" + pct(value1) + "% melee accuracy, +" + pct(value2) + "% melee damage";
			case PET_MAGE_BOOST -> "+" + pct(value1) + "% magic accuracy, +" + pct(value2) + "% magic damage, +"
					+ pct(value3) + "% defence";
			case PET_RANGED_BOOST -> "+" + pct(value1) + "% ranged accuracy, +" + pct(value2) + "% ranged damage, +"
					+ pct(value3) + "% defence";
			case PET_UTILITY_BOOST -> pct(value1) + "% faster special attack regen, -" + pct(value2)
					+ "% prayer drain";
			case PET_DROP_RATE_BOOST -> "+" + (int) value1 + "% drop rate";
			default -> "";
		};
	}

	private static String pct(double fraction) {
		return String.valueOf((int) Math.round(fraction * 100));
	}
}
