package io.ruin.model.activities.perktree.perks;

import io.ruin.model.activities.perktree.PlayerPerk;

public class ArcaneEnhancement extends PlayerPerk {
	@Override
	public int getPerkMaxLevel() {
		return 5;
	}

	public double getDamageBoost() {
		return (10 + (perkLevel * 3.75)) / 100;
	}


	@Override
	public void activatePerk() {
		this.isActive = true;
	}

	@Override
	public String getPerkName() {
		return "Arcane Enhancement";
	}

	@Override
	public String getPerkDescription() {
		return "Attack faster with magic spells";
	}

	@Override
	public String getPerkEffect() {
		return "When using magic your damage will be boosted by " + getDamageBoost() * 100 + " %, and you will attack every " + getAttackSpeed(perkLevel) + " ticks.";
	}

	private double getDamageBoost(int level) {
		return 10 + (level * 3.75);
	}

	/**
	 * Mirrors the attackTicks switch in PlayerCombat's magic-spell handling.
	 * Normal (unperked) spell attack speed is 5 ticks.
	 */
	private int getAttackSpeed(int level) {
		return switch (level) {
			case 1, 2 -> 4;
			case 3, 4 -> 3;
			case 5 -> 2;
			default -> 5;
		};
	}

	@Override
	public String getRepositoryDescription() {
		return "When using magic with this perk active your max damage will be increased and you will attack faster.<br><br>" +
			"At level 1 your max hit will be boosted by " + getDamageBoost(1) + "% and you will attack every " + getAttackSpeed(1) + " ticks.<br><br>" +
			"At level 2 your max hit will be boosted by " + getDamageBoost(2) + "% and you will attack every " + getAttackSpeed(2) + " ticks.<br><br>" +
			"At level 3 your max hit will be boosted by " + getDamageBoost(3) + "% and you will attack every " + getAttackSpeed(3) + " ticks.<br><br>" +
			"At level 4 your max hit will be boosted by " + getDamageBoost(4) + "% and you will attack every " + getAttackSpeed(4) + " ticks.<br><br>" +
			"At level 5 your max hit will be boosted by " + getDamageBoost(5) + "% and you will attack every " + getAttackSpeed(5) + " ticks.";
	}
}
