package io.ruin.model.content.fortune;

/**
 * The Statue of Fortune's blessings. Each climbs three tiers; costs are cumulative within a cycle and a tier's value
 * replaces the lower tier's. Tier III costs what the old camel statue's single unlock cost. Values are the balanced set
 * (2026-10-17): no 2x anywhere -- Tier III tops out at +25% XP, +10% perk XP, +50% slayer points, +30% raid / +20% Zelus points, +5% drop
 * rate, 12% extra-roll chance, 15% better pet odds. Costs are first-pass placeholders to tune from donation logs.
 */
public enum Blessing {
	ZELUS_POINTS("Zelus Points", "Zelus points from every source.", Kind.MULTIPLIER, 995,
			250_000_000L, 500_000_000L, 800_000_000L, 0.05, 0.10, 0.20),
	PERK_EXPERIENCE("Perk Experience", "Perk experience from completed tasks.", Kind.MULTIPLIER, 11640,
			500_000_000L, 1_000_000_000L, 2_000_000_000L, 0.03, 0.05, 0.10),
	SLAYER_POINTS("Slayer Points", "Slayer points from completed tasks.", Kind.MULTIPLIER, 4155,
			100_000_000L, 300_000_000L, 600_000_000L, 0.15, 0.30, 0.50),
	PET_RATES("Pet Rates", "Better odds on every pet roll.", Kind.PET, 12647,
			125_000_000L, 400_000_000L, 800_000_000L, 0.05, 0.10, 0.15),
	EXTRA_DROP_ROLL("Extra Drop Roll", "A chance at a second drop roll on boss kills.", Kind.CHANCE, 405,
			150_000_000L, 500_000_000L, 1_000_000_000L, 0.05, 0.08, 0.12),
	RAID_POINTS("Raid Points", "Points in Chambers, Theatre and Tombs.", Kind.MULTIPLIER, 20997,
			250_000_000L, 750_000_000L, 1_500_000_000L, 0.05, 0.10, 0.30),
	DROP_RATE("Drop Rate", "Added drop rate on every kill.", Kind.FLAT, 2572,
			500_000_000L, 1_000_000_000L, 2_000_000_000L, 1.5, 3, 5),
	EXPERIENCE("Experience", "Experience in every skill.", Kind.MULTIPLIER, 4447,
			500_000_000L, 1_500_000_000L, 3_000_000_000L, 0.10, 0.15, 0.25);

	public enum Kind { MULTIPLIER, PET, CHANCE, FLAT }

	public static final Blessing[] VALUES = values();

	public final String title, about;
	public final Kind kind;
	/** Item shown as the blessing's icon. */
	public final int icon;
	private final long[] cost;
	private final double[] value;

	Blessing(String title, String about, Kind kind, int icon, long t1, long t2, long t3, double v1, double v2, double v3) {
		this.title = title;
		this.about = about;
		this.kind = kind;
		this.icon = icon;
		this.cost = new long[]{t1, t2, t3};
		this.value = new double[]{v1, v2, v3};
	}

	/** Cumulative cost of tier 1-3. */
	public long cost(int tier) {
		return cost[tier - 1];
	}

	/** Value at tier 1-3; 0 at tier 0. */
	public double value(int tier) {
		return tier <= 0 ? 0 : value[tier - 1];
	}

	/** The effect at a tier, as players read it. */
	public String effect(int tier) {
		double v = value(tier);
		return switch (kind) {
			case MULTIPLIER -> "+" + Math.round(v * 100) + "% " + title.toLowerCase();
			case PET -> Math.round(v * 100) + "% better pet odds";
			case CHANCE -> Math.round(v * 100) + "% extra drop roll chance";
			case FLAT -> "+" + (v == Math.floor(v) ? String.valueOf((int) v) : String.valueOf(v)) + "% drop rate";
		};
	}

	/** Tier III cost / 100: GP per extra minute once maxed. */
	public long extendRate() {
		return cost[2] / 100;
	}
}
