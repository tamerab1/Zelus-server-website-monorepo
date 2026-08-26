package io.ruin.model.activities.perktree.perksets;

import io.ruin.model.activities.perktree.PlayerPerkSet;

public class MeleeResistance extends PlayerPerkSet {
	@Override
	public String getPerkSetName() {
		return "Melee Resistance";
	}

	public double getMeleeDamageReduction(int level) {
		double calc = getMeleeDamageReductionPercent(level) / 100;
		return 1 - calc;
	}

	@Override
	public String getPerkSetDescription() {
		return "Melee Damage Reduction";
	}

	private double getMeleeDamageReductionPercent(int level) {
		return 7.5 + (level * 3.5);
	}

	@Override
	public String getPerkSetEffect() {
		return "When this perk set is active you will take less damage from melee attacks.<br><br>" +
			"At level 1 you will take " + getMeleeDamageReductionPercent(1) + "% less damage from melee attacks.<br><br>" +
			"At level 2 you will take " + getMeleeDamageReductionPercent(2) + "% less damage from melee attacks.<br><br>" +
			"At level 3 you will take " + getMeleeDamageReductionPercent(3) + "% less damage from melee attacks.<br><br>" +
			"At level 4 you will take " + getMeleeDamageReductionPercent(4) + "% less damage from melee attacks.<br><br>" +
			"At level 5 you will take " + getMeleeDamageReductionPercent(5) + "% less damage from melee attacks.<br><br>"
			;
	}
}
