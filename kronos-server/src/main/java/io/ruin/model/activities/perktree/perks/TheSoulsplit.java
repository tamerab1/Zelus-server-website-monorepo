package io.ruin.model.activities.perktree.perks;

import io.ruin.model.activities.perktree.PlayerPerk;

public class TheSoulsplit extends PlayerPerk {
	@Override
	public int getPerkMaxLevel() {
		return 5;
	}

	/** 5 / 10 / 15 / 20 / 25% per hit (2026-11-08: was 12-44%). */
	public int chanceToHeal() {
		return chanceToHeal(perkLevel);
	}

	/** 8 / 10 / 12 / 14 / 16% of the hit (2026-11-08: was 10-30%); a miss heals nothing. */
	public int healAmount(int damage) {
		return damage <= 0 ? 0 : Math.max(1, (int) (damage * heal(perkLevel)));
	}

	@Override
	public void activatePerk() {
		this.isActive = true;
	}

	@Override
	public String getPerkName() {
		return "The Soulsplit";
	}

	@Override
	public String getPerkDescription() {
		return "Heal from your hits.";
	}

	@Override
	public String getPerkEffect() {
		return "You will have a " + chanceToHeal() + "% chance to heal an amount based on your hit and perk level.";
	}

	public int chanceToHeal(int level) {
		return level * 5;
	}

	public double heal(int level) {
		return (6 + level * 2) / 100D;
	}

	@Override
	public String getRepositoryDescription() {
		return "When this perk is active you will have a chance to heal an amount based on the damage your hit was and what level this perk is.<br><br>" +
			"At level 1 you will have a chance of " + chanceToHeal(1) + "% to heal off your hit for the amount of 1:" + heal(1) + ".<br><br>" +
			"At level 2 you will have a chance of " + chanceToHeal(2) + "% to heal off your hit for the amount of 1:" + heal(2) + ".<br><br>" +
			"At level 3 you will have a chance of " + chanceToHeal(3) + "% to heal off your hit for the amount of 1:" + heal(3) + ".<br><br>" +
			"At level 4 you will have a chance of " + chanceToHeal(4) + "% to heal off your hit for the amount of 1:" + heal(4) + ".<br><br>" +
			"At level 5 you will have a chance of " + chanceToHeal(5) + "% to heal off your hit for the amount of 1:" + heal(5) + "."
			;
	}
}
