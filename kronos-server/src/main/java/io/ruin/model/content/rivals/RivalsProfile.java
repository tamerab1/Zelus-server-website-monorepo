package io.ruin.model.content.rivals;

import io.ruin.api.utils.JsonUtils;
import io.ruin.api.utils.ServerWrapper;
import io.ruin.model.entity.player.Player;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Ranked progression for Division Rivals, stored on the player (PlayerAttributes.rivals*).
 * <ul>
 * <li>Skill Rating: Elo with K = 32, starting at 1,000. Used for matchmaking and the Elite leaderboard.</li>
 * <li>Divisions 10 (Iron) .. 1 (Champion), 5 steps each. A win is +1 step; the 5th step promotes (D1 promotes to Elite).
 * Reaching step 3 secures a checkpoint: losses then never drop progress below it. A loss is -1 step above the
 * checkpoint; there is no demotion.</li>
 * <li>Weekly wins (reset Monday 00:00 server time): the tier 1 chest at 4 (+75 points, 1 Rivals mystery box), the tier 2
 * chest at 8 (+150 points, 2 boxes, 5% chance of the "Weekly Warlord" title); each claimable once a week.</li>
 * <li>Rivals Points (2026-10-15 economy tuning): 20 per win, 5 per loss. Wins add a donator rank bonus (Regular 5%,
 * Super 10%, Elite / Noble 15%, Gold / Platinum / Legendary 20%, Supreme 25%) and +15% while the player owns the Rivals
 * Champion cape, both on the base 20 and stacking; the winner gets a breakdown line. No streak bonus any more.</li>
 * <li>Anti-boosting: the same two players meeting again within 30 minutes play a "friendly" - it counts as a match
 * but changes no rating, steps or points.</li>
 * </ul>
 */
public final class RivalsProfile {

	public static final int STEPS = 5, CHECKPOINT = 3, K = 32, START_RATING = 1000;
	public static final int WIN_POINTS = 20, LOSS_POINTS = 5, CAPE_BONUS_PERCENT = 15;
	public static final int TIER1_POINTS = 75, TIER1_BOXES = 1, TIER2_POINTS = 150, TIER2_BOXES = 2, WEEKLY_TITLE = 30223;
	private static final int WEEKLY_TITLE_PERCENT = 5, FANFARE = 101;
	public static final int TIER1_WINS = 4, TIER2_WINS = 8;
	private static final long REMATCH_COOLDOWN_MS = 30 * 60_000L;
	private static final ZoneId ZONE = ZoneId.systemDefault();

	/** Last time each unordered pair of players met (anti-boosting). */
	private static final Map<String, Long> lastPairing = new HashMap<>();

	private RivalsProfile() {
	}

	/* ---------------- results ---------------- */

	/** Applies a finished match. Returns a one-line summary per player: {winnerLine, loserLine}. */
	static String[] applyResult(Player winner, Player loser, boolean technical) {
		return applyResult(winner, loser, technical, false);
	}

	/** {@code forced}: a developer ::rivalsforce match -- the 30-minute rematch rule doesn't apply (testing bypass). */
	static String[] applyResult(Player winner, Player loser, boolean technical, boolean forced) {
		rollWeek(winner);
		rollWeek(loser);
		String pair = pairKey(winner, loser);
		long now = System.currentTimeMillis();
		Long last = lastPairing.put(pair, now);
		boolean friendly = !forced && last != null && now - last < REMATCH_COOLDOWN_MS;

		winner.rivalsWins++;
		loser.rivalsLosses++;
		if (friendly) {
			return new String[]{"Rematch within 30 minutes - no rating or progress change.",
				"Rematch within 30 minutes - no rating or progress change."};
		}

		double expected = 1.0 / (1.0 + Math.pow(10, (loser.rivalsRating - winner.rivalsRating) / 400.0));
		int delta = Math.max(1, (int) Math.round(K * (1 - expected)));
		winner.rivalsRating += delta;
		loser.rivalsRating = Math.max(0, loser.rivalsRating - delta);

		winner.rivalsStreak++;
		winner.rivalsBestStreak = Math.max(winner.rivalsBestStreak, winner.rivalsStreak);
		loser.rivalsStreak = 0;
		winner.rivalsWeeklyWins++;

		int donatorBonus = (int) Math.round(WIN_POINTS * donatorBonusPercent(winner) / 100.0);
		boolean cape = RivalsCape.ownsChampionCape(winner);
		int capeBonus = cape ? (int) Math.round(WIN_POINTS * CAPE_BONUS_PERCENT / 100.0) : 0;
		int earned = WIN_POINTS + donatorBonus + capeBonus;
		winner.rivalsPoints += earned;
		loser.rivalsPoints += LOSS_POINTS;
		if (donatorBonus > 0 || capeBonus > 0)
			winner.sendMessage("<col=990000>[Rivals]</col> You received " + WIN_POINTS + " points ("
				+ (donatorBonus > 0 ? "+" + donatorBonus + " Donator Bonus" : "")
				+ (donatorBonus > 0 && capeBonus > 0 ? ", " : "")
				+ (capeBonus > 0 ? "+" + capeBonus + " Cape Bonus" : "") + ")!");

		String promo = stepUp(winner);
		stepDown(loser);
		RivalsLadder.update(winner);
		RivalsLadder.update(loser);
		return new String[]{
			"+" + delta + " SR (" + winner.rivalsRating + "), +" + earned + " Rivals Points"
				+ (technical ? " (opponent left)" : "") + promo,
			"-" + delta + " SR (" + loser.rivalsRating + "), +" + LOSS_POINTS + " Rivals Points"};
	}

	/** Donator rank bonus on won Rivals points, by total donated (Player.isRegularDonator ... isSupremeDonator). */
	static int donatorBonusPercent(Player p) {
		int donated = p.totalDonated;
		if (donated >= 1750) return 25; // Supreme
		if (donated >= 400) return 20;  // Gold, Platinum, Legendary
		if (donated >= 100) return 15;  // Elite, Noble
		if (donated >= 50) return 10;   // Super
		if (donated >= 10) return 5;    // Regular
		return 0;
	}

	private static String stepUp(Player p) {
		if (p.rivalsDivision == 0)
			return "";
		p.rivalsProgress++;
		if (p.rivalsProgress >= CHECKPOINT)
			p.rivalsCheckpoint = Math.max(p.rivalsCheckpoint, CHECKPOINT);
		if (p.rivalsProgress < STEPS)
			return "";
		p.rivalsDivision--;
		p.rivalsProgress = 0;
		p.rivalsCheckpoint = 0;
		return p.rivalsDivision == 0 ? " - PROMOTED TO ELITE!" : " - promoted to Division " + p.rivalsDivision + " (" + name(p.rivalsDivision) + ")!";
	}

	private static void stepDown(Player p) {
		if (p.rivalsDivision == 0)
			return;
		if (p.rivalsProgress > p.rivalsCheckpoint)
			p.rivalsProgress--;
	}

	private static String pairKey(Player a, Player b) {
		String x = a.getName().toLowerCase(Locale.ROOT), y = b.getName().toLowerCase(Locale.ROOT);
		return x.compareTo(y) < 0 ? x + "|" + y : y + "|" + x;
	}

	/* ---------------- weekly ---------------- */

	static String currentWeek() {
		return LocalDate.now(ZONE).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString();
	}

	static void rollWeek(Player p) {
		String week = currentWeek();
		if (!week.equals(p.rivalsWeek)) {
			p.rivalsWeek = week;
			p.rivalsWeeklyWins = 0;
			p.rivalsWeeklyClaimed = false;
			p.rivalsWeeklyClaimedT1 = false;
		}
	}

	static Duration untilWeeklyReset() {
		LocalDateTime next = LocalDate.now(ZONE).with(TemporalAdjusters.next(DayOfWeek.MONDAY)).atStartOfDay();
		return Duration.between(LocalDateTime.now(ZONE), next);
	}

	static boolean claimableT1(Player p) {
		rollWeek(p);
		return p.rivalsWeeklyWins >= TIER1_WINS && !p.rivalsWeeklyClaimedT1;
	}

	static boolean claimableT2(Player p) {
		rollWeek(p);
		return p.rivalsWeeklyWins >= TIER2_WINS && !p.rivalsWeeklyClaimed;
	}

	/** Any weekly chest unlocked and not yet claimed. */
	static boolean claimable(Player p) {
		return claimableT1(p) || claimableT2(p);
	}

	/**
	 * "Claim rewards" (always clickable): claims every unlocked chest at once -- points first, then the boxes (inventory,
	 * or the bank when it's full) -- with the fanfare; otherwise explains that nothing is ready.
	 */
	static void claimWeekly(Player p) {
		String tag = "<col=990000><shad=000000>[Rivals]</shad></col> ";
		if (RivalsSnapshot.active(p) || RivalsQueueManager.inMatch(p)) { // the kit inventory is wiped on restore
			p.sendMessage(tag + "Claim your weekly rewards after the match.");
			return;
		}
		if (!claimable(p)) {
			p.sendMessage(tag + "You have no unclaimed weekly rewards. Win more ranked matches to unlock your chests! ("
				+ p.rivalsWeeklyWins + " win" + (p.rivalsWeeklyWins == 1 ? "" : "s") + " this week: tier 1 at " + TIER1_WINS
				+ ", tier 2 at " + TIER2_WINS + ")");
			return;
		}
		if (claimableT1(p)) {
			p.rivalsWeeklyClaimedT1 = true; // flags first: a second click can't pay twice
			p.rivalsPoints += TIER1_POINTS;
			giveBoxes(p, TIER1_BOXES);
			p.sendMessage(tag + "You opened your Tier 1 Weekly Chest and received " + TIER1_POINTS + " Rivals Points and a Rivals Mystery Box!");
		}
		if (claimableT2(p)) {
			p.rivalsWeeklyClaimed = true;
			p.rivalsPoints += TIER2_POINTS;
			giveBoxes(p, TIER2_BOXES);
			p.sendMessage(tag + "You opened your Tier 2 Weekly Chest and received " + TIER2_POINTS + " Rivals Points and "
				+ TIER2_BOXES + " Rivals Mystery Boxes!");
			io.ruin.model.content.loyaltytitles.LoyaltyTitle title = io.ruin.model.content.loyaltytitles.LoyaltyTitle.get(WEEKLY_TITLE);
			if (title != null && !io.ruin.model.content.loyaltytitles.LoyaltyTitleManager.isUnlocked(p, title)
				&& io.ruin.utility.Random.rollPercent(WEEKLY_TITLE_PERCENT)) {
				io.ruin.model.content.loyaltytitles.LoyaltyTitleManager.unlock(p, title);
				p.sendMessage(tag + "<col=ffd700>Rare reward!</col> You unlocked the exclusive <col=8a0303>Weekly Warlord</col> title.");
			}
		}
		p.privateSound(FANFARE);
	}

	private static void giveBoxes(Player p, int count) {
		if (p.getInventory().hasRoomFor(RivalsShop.MYSTERY_BOX, count)) {
			p.getInventory().add(RivalsShop.MYSTERY_BOX, count);
		} else {
			p.getBank().add(RivalsShop.MYSTERY_BOX, count);
			p.sendMessage("Your inventory was full, so the box" + (count > 1 ? "es were" : " was") + " sent to your bank.");
		}
	}

	/** PK tab "Division" row: "Gold (D4) - 1,040 SR", or "Elite #2 - 1,812 SR". */
	public static String dashboardLine(Player p) {
		if (p.rivalsDivision == 0)
			return "<col=ffd700>Elite #" + RivalsLadder.eliteRank(p) + "</col>";
		return "<col=ffffff>" + name(p.rivalsDivision) + " (D" + p.rivalsDivision + ")</col> <col=9a9a9a>" + String.format("%,d", p.rivalsRating) + " SR</col>";
	}

	static String name(int division) {
		return DivisionRivalsDemoState.divisionName(division);
	}

	/* ---------------- the global ladder (Elite rank) ---------------- */

	/** name -> rating / division, saved to runtime/saves/rivals_ladder.json so offline players keep their rank. */
	static final class RivalsLadder {
		static final class Entry {
			String name;
			int rating, division, wins, losses;
		}

		/** The best {@code n} by rating. */
		static synchronized List<Entry> top(int n) {
			load();
			List<Entry> list = new ArrayList<>(entries.values());
			list.sort((a, b) -> b.rating != a.rating ? Integer.compare(b.rating, a.rating) : a.name.compareToIgnoreCase(b.name));
			return list.subList(0, Math.min(n, list.size()));
		}

		private static Map<String, Entry> entries;

		static synchronized void update(Player p) {
			load();
			Entry e = entries.computeIfAbsent(p.getName().toLowerCase(Locale.ROOT), k -> new Entry());
			e.name = p.getName();
			e.rating = p.rivalsRating;
			e.division = p.rivalsDivision;
			e.wins = p.rivalsWins;
			e.losses = p.rivalsLosses;
			save();
		}

		/** 1-based rank among Elite players by rating (the player counts as Elite here). */
		static synchronized int eliteRank(Player p) {
			load();
			int rank = 1;
			for (Entry e : entries.values())
				if (e.division == 0 && e.rating > p.rivalsRating && !e.name.equalsIgnoreCase(p.getName()))
					rank++;
			return rank;
		}

		private static File file() {
			return new File(ServerWrapper.dataFolder, "runtime/saves/rivals_ladder.json");
		}

		private static void load() {
			if (entries != null)
				return;
			entries = new HashMap<>();
			File f = file();
			if (!f.exists())
				return;
			try (FileReader r = new FileReader(f)) {
				Entry[] loaded = JsonUtils.GSON.fromJson(r, Entry[].class);
				if (loaded != null)
					for (Entry e : loaded)
						if (e != null && e.name != null)
							entries.put(e.name.toLowerCase(Locale.ROOT), e);
			} catch (Exception e) {
				ServerWrapper.logError("Unable to load the Rivals ladder", e);
			}
		}

		private static void save() {
			File f = file();
			f.getParentFile().mkdirs();
			try (FileWriter w = new FileWriter(f)) {
				List<Entry> list = new ArrayList<>(entries.values());
				JsonUtils.GSON.toJson(list.toArray(new Entry[0]), w);
			} catch (Exception e) {
				ServerWrapper.logError("Unable to save the Rivals ladder", e);
			}
		}
	}
}
