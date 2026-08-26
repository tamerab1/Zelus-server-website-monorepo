package io.ruin.model.activities.perktree.perksets;

import io.ruin.model.activities.perktree.PlayerPerkSet;

public class SkilledMind extends PlayerPerkSet {

	public double getChanceToDoubleExperience(int level) {
		return getChanceToDoubleExperiencePercent(level) / 100;
	}

	@Override
	public String getPerkSetName() {
		return "Skilled Mind";
	}

	@Override
	public String getPerkSetDescription() {
		return "Experience Boost";
	}

	private double getChanceToDoubleExperiencePercent(int level) {
		return 2.5 + (5.5 * level);
	}

	@Override
	public String getPerkSetEffect() {
		return "When this perk set is active you will have a chance to double incoming experience meaning you will get 2x the experience when gaining experience at any time.<br><br>" +
			"At level 1 you will have a " + getChanceToDoubleExperiencePercent(1) + "% chance to double your experience.<br><br>" +
			"At level 2 you will have a " + getChanceToDoubleExperiencePercent(2) + "% chance to double your experience.<br><br>" +
			"At level 3 you will have a " + getChanceToDoubleExperiencePercent(3) + "% chance to double your experience.<br><br>" +
			"At level 4 you will have a " + getChanceToDoubleExperiencePercent(4) + "% chance to double your experience.<br><br>" +
			"At level 5 you will have a " + getChanceToDoubleExperiencePercent(5) + "% chance to double your experience.<br><br>"
			;
	}
}
