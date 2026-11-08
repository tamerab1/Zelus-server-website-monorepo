package io.ruin.model.activities.colosseum;

/**
 * The Colosseum of Trials' fights (2026-11-07). A trial is one boss, or a gauntlet of bosses fought in waves (the next
 * spawns when the last falls). Tiers: 1 = 15 marks, 2 = 40, 3 = 100 ({@link #points}), x1.25 under {@link #parTicks()},
 * x1.5 under three quarters of it; a duo splits them by damage. Declared in the picker's order (tier, then list order).
 * Bosses: npcs/combat/Custom_bosses*.json (CustomBossCombat; Balarok: BalarokCombat).
 */
public enum Trial {
	MORVATH(1, "Morvath the Shadowblade", "Morvath the Shadowblade", "Ranged", "Melee",
			"A shadow archer who never misses twice.", 21940),
	TERRAX(1, "Terrax the Earth Titan", "Terrax the Earth Titan", "Melee", "Magic", "The earth itself, risen.", 21934),
	VESPIRA(1, "Vespira the Hive Queen", "Vespira the Hive Queen", "Melee", "Magic", "The queen of the hive.", 21946),
	TITANS(2, "Trial of the Titans", "Trial of the Titans", "Melee", "Magic", "Three brutes who hit like falling stone.",
			21928, 21933, 21926),
	VOID_PACT(2, "The Void Pact", "The Void Pact", "Melee / Ranged", "Magic", "The Void Fiend, then the Professor.",
			21943, 21944),
	ARCANE(3, "Trial of the Arcane", "Trial of the Arcane", "Magic", "Ranged", "Five archmages, one after another.",
			21941, 21923, 21924, 21921, 21925),
	CHAMPIONS(3, "Trial of Champions", "Trial of Champions", "Mixed", "Varies", "Four champions, each with their own style.",
			21939, 21929, 21938, 21942),
	BALAROK(3, "Balarok the Horned Behemoth", "Balarok the Behemoth", "Melee", "Magic",
			"Hellfire, charges and a roar that breaks prayers.", 21945);

	public final int tier, points;
	public final String title, listName, style, weakness, blurb;
	public final int[] waves;

	Trial(int tier, String title, String listName, String style, String weakness, String blurb, int... waves) {
		this.tier = tier;
		this.points = tier == 1 ? 15 : tier == 2 ? 40 : 100;
		this.title = title;
		this.listName = listName;
		this.style = style;
		this.weakness = weakness;
		this.blurb = blurb;
		this.waves = waves;
	}

	/** 60 seconds a boss (100 ticks) */
	public int parTicks() {
		return 100 * waves.length;
	}

	/** the clear's multiplier: x1.5 under 3/4 of par, x1.25 under par */
	public double speedMultiplier(int ticks) {
		if (ticks < parTicks() * 3 / 4)
			return 1.5;
		if (ticks < parTicks())
			return 1.25;
		return 1.0;
	}

	public String bestKey(boolean duo) {
		return name() + (duo ? "_DUO" : "_SOLO");
	}
}
