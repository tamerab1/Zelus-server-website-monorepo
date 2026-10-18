package io.ruin.model.content.soulweapons;

/**
 * The Soul Tree talents (2026-10-18): three per line, {@link #MAX_RANK} ranks each, one Soul Point per rank. A weapon
 * earns a point per tier (9 by tier 10) against 15 ranks per line, so every build is a real choice; the tree can be
 * reset (SoulTreeInterface). Effects live in {@link SoulTalents} and are PvM only: they need the player's own line
 * wielded, a monster on the other end and no Rivals match.
 */
public enum SoulTalent {
	DEVASTATION(SoulWeaponType.MELEE, "Devastation", "Critical strikes"),
	CLEAVE(SoulWeaponType.MELEE, "Cleave", "Splash damage (multi)"),
	LIFESTEAL(SoulWeaponType.MELEE, "Lifesteal", "Heal on hit"),
	TEMPEST(SoulWeaponType.RANGED, "Tempest", "Attack speed"),
	RICOCHET(SoulWeaponType.RANGED, "Ricochet", "Chain shots (multi)"),
	EXECUTIONER(SoulWeaponType.RANGED, "Executioner", "Finisher"),
	SOUL_SIPHON(SoulWeaponType.MAGIC, "Soul Siphon", "HP + Prayer leech"),
	ARCANE_SURGE(SoulWeaponType.MAGIC, "Arcane Surge", "Kill blasts (multi)"),
	DEATH_WARD(SoulWeaponType.MAGIC, "Death Ward", "Cheat death");

	public static final int MAX_RANK = 5;

	public final SoulWeaponType line;
	public final String name, tagline;

	SoulTalent(SoulWeaponType line, String name, String tagline) {
		this.line = line;
		this.name = name;
		this.tagline = tagline;
	}

	/** The line's three talents, in column order. */
	public static SoulTalent[] of(SoulWeaponType line) {
		return switch (line) {
			case MELEE -> new SoulTalent[]{DEVASTATION, CLEAVE, LIFESTEAL};
			case RANGED -> new SoulTalent[]{TEMPEST, RICOCHET, EXECUTIONER};
			case MAGIC -> new SoulTalent[]{SOUL_SIPHON, ARCANE_SURGE, DEATH_WARD};
		};
	}

	/** What a rank does in a few words, for the single line under a Soul Tree node ("Rank 3: ..." / "Next Rank: ..."). */
	public String brief(int rank) {
		return switch (this) {
			case DEVASTATION -> SoulTalents.critChance(rank) + "% crit, 130% dmg";
			case CLEAVE -> SoulTalents.cleavePercent(rank) + "% splash, 3 foes";
			case LIFESTEAL -> "Heal " + SoulTalents.lifestealPercent(rank) + "% of dmg";
			case TEMPEST -> SoulTalents.tempestChance(rank) + "% faster shots";
			case RICOCHET -> SoulTalents.ricochetChance(rank) + "% chain, 50% dmg";
			case EXECUTIONER -> "+" + SoulTalents.executionerPercent(rank) + "% dmg <35% HP";
			case SOUL_SIPHON -> "Heal " + SoulTalents.siphonHpPercent(rank) + "%, +" + rank + "% Prayer";
			case ARCANE_SURGE -> SoulTalents.surgeChance(rank) + "% kill blast";
			case DEATH_WARD -> SoulTalents.wardHpPercent(rank) + "% HP, " + SoulTalents.wardCooldownMinutes(rank) + "m cd";
		};
	}

	/** The hover tooltip's extra line: where and how the talent works. */
	public String note() {
		return switch (this) {
			case DEVASTATION -> "Melee hits on monsters.";
			case CLEAVE -> "Splashes monsters next to your target. Multi-combat only.";
			case LIFESTEAL -> "Every melee hit that lands on a monster.";
			case TEMPEST -> "Each shot at a monster may come a tick sooner.";
			case RICOCHET -> "Bounces to a monster within 3 tiles. Multi-combat only.";
			case EXECUTIONER -> "Ranged hits on monsters below 35% hitpoints.";
			case SOUL_SIPHON -> "Every spell that lands on a monster.";
			case ARCANE_SURGE -> "Blasts up to 4 monsters around your kill. Multi-combat only.";
			case DEATH_WARD -> "A monster's killing blow leaves you alive instead.";
		};
	}

	/** What a rank does, in full (the tooltip and the chat line when a rank is bought). */
	public String describe(int rank) {
		return switch (this) {
			case DEVASTATION -> SoulTalents.critChance(rank) + "% chance to crit for 130% damage";
			case CLEAVE -> SoulTalents.cleavePercent(rank) + "% of each hit splashes 3 nearby foes";
			case LIFESTEAL -> "Heal " + SoulTalents.lifestealPercent(rank) + "% of the damage you deal";
			case TEMPEST -> SoulTalents.tempestChance(rank) + "% chance to shoot a tick faster";
			case RICOCHET -> SoulTalents.ricochetChance(rank) + "% chance to chain for 50% damage";
			case EXECUTIONER -> "+" + SoulTalents.executionerPercent(rank) + "% damage to foes under 35% HP";
			case SOUL_SIPHON -> "Heal " + SoulTalents.siphonHpPercent(rank) + "% of damage, +" + rank + "% as Prayer";
			case ARCANE_SURGE -> SoulTalents.surgeChance(rank) + "% chance a kill blasts nearby foes";
			case DEATH_WARD -> "Survive a fatal hit at " + SoulTalents.wardHpPercent(rank) + "% HP; "
					+ SoulTalents.wardCooldownMinutes(rank) + "m cooldown";
		};
	}
}
