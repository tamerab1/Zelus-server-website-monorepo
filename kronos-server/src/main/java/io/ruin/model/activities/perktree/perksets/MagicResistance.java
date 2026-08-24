package io.ruin.model.activities.perktree.perksets;

import io.ruin.model.activities.perktree.PlayerPerkSet;

public class MagicResistance extends PlayerPerkSet {
	@Override
	public String getPerkSetName() {
		return "Magic Resistance";
	}

	@Override
	public String getPerkSetDescription() {
		return "Magic Damage Reduction";
	}

	@Override
	public String getPerkSetEffect() {
		return "When this perk set is active you will take less damage from magic attacks.<br><br>" +
			"At level 1 you will take " + getMagicDamageReductionPercent(1) + "% less damage from magic attacks.<br><br>" +
			"At level 2 you will take " + getMagicDamageReductionPercent(2) + "% less damage from magic attacks.<br><br>" +
			"At level 3 you will take " + getMagicDamageReductionPercent(3) + "% less damage from magic attacks.<br><br>" +
			"At level 4 you will take " + getMagicDamageReductionPercent(4) + "% less damage from magic attacks.<br><br>" +
			"At level 5 you will take " + getMagicDamageReductionPercent(5) + "% less damage from magic attacks.<br><br>"
			;
	}

	private double getMagicDamageReductionPercent(int level) {
		return 7.5 + (level * 3.5);
	}

	public double getMagicDamageReduction(int level) {
		double calc = getMagicDamageReductionPercent(level) / 100;
		return 1 - calc;
	}
}
