package io.ruin.model.activities.perktree.perks;

import io.ruin.model.activities.perktree.PlayerPerk;

public class TheArtOfMining extends PlayerPerk {
	@Override
	public int getPerkMaxLevel() {
		return 5;
	}

	public int chanceToSaveRockDepleting() {
		return (int) (15 + (perkLevel * 7.5));
	}

	public int getBonusOreChance() {
		return 10 + (perkLevel * 5);
	}

	@Override
	public void activatePerk() {
		isActive = true;
	}

	@Override
	public String getPerkName() {
		return "The Art Of Mining";
	}

	@Override
	public String getPerkDescription() {
		return "Mining rock depleting reduced, chance for bonus ore.";
	}

	@Override
	public String getPerkEffect() {
		return "You will receive a " + chanceToSaveRockDepleting() + "% chance for the rock not to deplete, and a "
			+ getBonusOreChance() + "% chance for an extra ore, when mining.";
	}

	private int chanceToSaveRockDepleting(int level) {
		return (int) (15 + (level * 7.5));
	}

	private int getBonusOreChance(int level) {
		return 10 + (level * 5);
	}

	@Override
	public String getRepositoryDescription() {
		return "When mining you will have a chance for the rock you are mining to skip the rock depleting and for you to continue mining instead of the rock depleting. " +
			"You will also have a chance to mine an additional ore on top of your normal one.<br><br>" +
			"At level 1 you will have a " + chanceToSaveRockDepleting(1) + "% chance for the rock not to deplete, and a " + getBonusOreChance(1) + "% chance for bonus ore.<br><br>" +
			"At level 2 you will have a " + chanceToSaveRockDepleting(2) + "% chance for the rock not to deplete, and a " + getBonusOreChance(2) + "% chance for bonus ore.<br><br>" +
			"At level 3 you will have a " + chanceToSaveRockDepleting(3) + "% chance for the rock not to deplete, and a " + getBonusOreChance(3) + "% chance for bonus ore.<br><br>" +
			"At level 4 you will have a " + chanceToSaveRockDepleting(4) + "% chance for the rock not to deplete, and a " + getBonusOreChance(4) + "% chance for bonus ore.<br><br>" +
			"At level 5 you will have a " + chanceToSaveRockDepleting(5) + "% chance for the rock not to deplete, and a " + getBonusOreChance(5) + "% chance for bonus ore."
			;
	}
}
