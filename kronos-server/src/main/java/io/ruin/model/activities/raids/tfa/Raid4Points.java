package io.ruin.model.activities.raids.tfa;

import io.ruin.model.entity.player.Player;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-player contribution for the run: damage dealt, damage taken, mechanics completed and deaths.
 * <p>
 * The wiki doesn't publish the raid's points formula, so {@link #points} is a Zelus weighting and the raw
 * counters are kept separately: when the real formula is known only {@link #points} changes.
 */
public class Raid4Points {

	public static final int POINTS_PER_MECHANIC = 50;
	public static final double DEATH_PENALTY = 0.2;

	public static class Entry {
		public int damageDealt;
		public int damageTaken;
		public int mechanics;
		public int deaths;
		/** Bosses on which this raider dealt the most damage (the Alliance Log's "Number of Bosses as MVP"). */
		public int mvps;
		/** Damage dealt since the current challenge started. */
		transient int roomDamage;
	}

	private final Map<String, Entry> entries = new HashMap<>();

	public Entry of(Player player) {
		return entries.computeIfAbsent(Raid4Party.key(player), k -> new Entry());
	}

	public void damageDealt(Player player, int damage) {
		if (damage > 0) {
			Entry e = of(player);
			e.damageDealt += damage;
			e.roomDamage += damage;
		}
	}

	public void damageTaken(Player player, int damage) {
		if (damage > 0)
			of(player).damageTaken += damage;
	}

	/** A challenge started: its damage count starts at zero for everyone. */
	public void startRoom() {
		for (Entry e : entries.values())
			e.roomDamage = 0;
	}

	/** A challenge is complete: the raider with the most damage in it is its MVP. */
	public void finishRoom() {
		Entry best = null;
		for (Entry e : entries.values())
			if (e.roomDamage > 0 && (best == null || e.roomDamage > best.roomDamage))
				best = e;
		if (best != null)
			best.mvps++;
	}

	public void mechanic(Player player) {
		of(player).mechanics++;
	}

	public void death(Player player) {
		of(player).deaths++;
	}

	/** The party's deaths this raid, for "You have died. Total deaths: n." */
	public int totalDeaths() {
		int n = 0;
		for (Entry e : entries.values())
			n += e.deaths;
		return n;
	}

	public int points(Player player) {
		Entry e = of(player);
		double points = e.damageDealt + e.mechanics * POINTS_PER_MECHANIC;
		points *= Math.max(0, 1 - e.deaths * DEATH_PENALTY);
		return (int) points;
	}

	public int totalPoints(Iterable<Player> players) {
		int total = 0;
		for (Player player : players)
			total += points(player);
		return total;
	}
}
