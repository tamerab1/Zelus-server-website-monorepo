package io.ruin.model.activities.perktree.perks;

import io.ruin.model.activities.perktree.PlayerPerk;

import java.text.DecimalFormat;

public class EnhancedMagicalWeapons extends PlayerPerk {
	@Override
	public int getPerkMaxLevel() {
		return 5;
	}

	public double getAccuracyBoost() {
		return (15.0 + (perkLevel * 4.5)) / 100;
	}

	public double getDamageBoost() {
		return (10 + (perkLevel * 2.5)) / 100;
	}

	@Override
	public void activatePerk() {
		this.isActive = true;
	}

	@Override
	public String getPerkName() {
		return "Enhanced Magical Weapons";
	}

	@Override
	public String getPerkDescription() {
		return "Attack faster with autocast magic weapons.";
	}

	@Override
	public String getPerkEffect() {
		DecimalFormat decimalFormat = new DecimalFormat("#.##");
		return "Autocast weapons such as trident will receive a " + Double.parseDouble(decimalFormat.format(getDamageBoost() * 100)) + "% damage boost and<br>" +
			"a " + getAccuracyBoost() * 100 + "% accuracy boost, and will attack every " + getAttackSpeed(perkLevel) + " ticks.";
	}

	private double getAccuracyBoost(int level) {
		DecimalFormat decimalFormat = new DecimalFormat("#.##");
		return Double.parseDouble(decimalFormat.format(15 + (level * 4.5)));
	}

	private double getDamageBoost(int level) {
		DecimalFormat decimalFormat = new DecimalFormat("#.##");
		return Double.parseDouble(decimalFormat.format(10 + (level * 2.5)));
	}

	/**
	 * Mirrors the baseAttackSpeed switch in PlayerCombat's autocast-magic handling.
	 * Normal (unperked) autocast attack speed is 4 ticks.
	 */
	private int getAttackSpeed(int level) {
		return switch (level) {
			case 1 -> 4;
			case 2, 3 -> 3;
			case 4, 5 -> 2;
			default -> 4;
		};
	}

	@Override
	public String getRepositoryDescription() {
		return "When this perk is active autocast magic weapons such as trident will have an accuracy and damage boost, and will attack faster at higher levels.<br><br>" +
			"At level 1 you will receive a " + getDamageBoost(1) + "% damage boost and a " + getAccuracyBoost(1) +
			"% accuracy boost using autocast magic weapons, attacking every " + getAttackSpeed(1) + " ticks (no change from normal speed).<br><br>" +
			"At level 2 you will receive a " + getDamageBoost(2) + "% damage boost and a " + getAccuracyBoost(2) +
			"% accuracy boost using autocast magic weapons, attacking every " + getAttackSpeed(2) + " ticks.<br><br>" +
			"At level 3 you will receive a " + getDamageBoost(3) + "% damage boost and a " + getAccuracyBoost(3) +
			"% accuracy boost using autocast magic weapons, attacking every " + getAttackSpeed(3) + " ticks.<br><br>" +
			"At level 4 you will receive a " + getDamageBoost(4) + "% damage boost and a " + getAccuracyBoost(4) +
			"% accuracy boost using autocast magic weapons, attacking every " + getAttackSpeed(4) + " ticks.<br><br>" +
			"At level 5 you will receive a " + getDamageBoost(5) + "% damage boost and a " + getAccuracyBoost(5) +
			"% accuracy boost using autocast magic weapons, attacking every " + getAttackSpeed(5) + " ticks."
			;
	}
}
