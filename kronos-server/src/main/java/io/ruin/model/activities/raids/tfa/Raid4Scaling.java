package io.ruin.model.activities.raids.tfa;

/**
 * Party-size scaling, from the OSRS wiki (October 2026).
 * <p>
 * Bosses scale with the players present when the room starts; a solo raid is scaled as two. Each player past
 * the second adds 43.63% of base HP. The wiki gives exact per-size tables for some bosses, which are used where
 * known since they don't round the same way as the general rule. The wiki doesn't document offensive stat
 * scaling, so offence is left at the cache values; mechanics that scale per player (mushrooms, laser damage)
 * scale in the boss itself.
 */
public final class Raid4Scaling {

	private Raid4Scaling() {
	}

	public static final int MIN_SCALE = 2;
	public static final double HP_PER_EXTRA_PLAYER = 0.4363;

	/** Archive Guardian, flying (the shield), team size 1-8. */
	private static final int[] GUARDIAN_SHIELD = { 550, 550, 800, 1030, 1260, 1490, 1720, 1950 };
	/** Archive Guardian, grounded (its real hp), team size 1-8. */
	private static final int[] GUARDIAN_HP = { 1100, 1100, 1600, 2060, 2520, 2980, 3440, 3910 };

	/** Ezekiel, team size 1-8. The wiki's table stops at 7; 8 follows its +1260 steps. */
	private static final int[] EZEKIEL_HP = { 3000, 3000, 4350, 5610, 6870, 8130, 9390, 10650 };

	public static int ezekielHp(int partySize) {
		return EZEKIEL_HP[scale(partySize) - 1];
	}

	/**
	 * The wiki's per-size hp tables all follow one rule: the third player adds 45% of base, every one after
	 * adds 42%, rounded to the nearest 10 (checked against Ezekiel, Bork, the Smorks, Dreadnoth, Xarth and Ocellus).
	 */
	public static int tableHp(int baseHp, int partySize) {
		int n = scale(partySize);
		double mult = n <= 2 ? 1 : 1.45 + 0.42 * (n - 3);
		return (int) (Math.round(baseHp * mult / 10.0) * 10);
	}

	public static int scale(int partySize) {
		return Math.max(MIN_SCALE, Math.min(Raid4Party.MAX_SIZE, partySize));
	}

	public static int hp(int baseHp, int partySize) {
		return (int) Math.round(baseHp * (1 + HP_PER_EXTRA_PLAYER * (scale(partySize) - MIN_SCALE)));
	}

	public static int guardianShield(int partySize) {
		return GUARDIAN_SHIELD[scale(partySize) - 1];
	}

	public static int guardianHp(int partySize) {
		return GUARDIAN_HP[scale(partySize) - 1];
	}
}
