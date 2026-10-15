package io.ruin.model.activities.wilderness;

import com.google.gson.annotations.Expose;
import io.ruin.api.utils.JsonUtils;
import io.ruin.api.utils.Random;
import io.ruin.api.utils.ServerWrapper;
import io.ruin.cache.Icon;
import io.ruin.cache.ItemID;
import io.ruin.model.World;
import io.ruin.model.entity.player.Player;
import io.ruin.model.entity.shared.listeners.LoginListener;
import io.ruin.model.inter.InterfaceHandler;
import io.ruin.model.inter.ToplevelComponent;
import io.ruin.model.inter.actions.SimpleAction;
import io.ruin.model.item.Item;
import io.ruin.model.item.actions.ItemAction;
import io.ruin.model.map.Bounds;
import io.ruin.model.stat.StatType;
import io.ruin.model.var.VarPlayerRepository;
import io.ruin.network.PacketSender;
import io.ruin.utility.Broadcast;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Edgeville PvP Rewards (interface 1162, built by .dev/cache-restore-tool/src/BuildPvpRewardsInterface.java): timed
 * rounds that score ASSIGNED Bounty Hunter target kills made inside the Edgeville hotspot.
 * <ul>
 *     <li>Rounds are {@link #ROUND_MINUTES} long and aligned to the clock, so they survive restarts.</li>
 *     <li>Two divisions: Mains, and Pures (1 Defence, by the killer's base Defence at the time of the kill).</li>
 *     <li>Each valid kill scores {@link #SCORE_PER_KILL} + a streak bonus and grows its division's Blood Money pot;
 *     at the round's end the top 3 of each division split the pot 50 / 30 / 20 (offline winners are paid on login).</li>
 *     <li>Personal round milestones (5 / 10 / 20 kills) are claimed from the dashboard for Bounty crates; anything
 *     left unclaimed when the round ends is paid automatically.</li>
 *     <li>Anti-boost: kills between the same IP or computer, friends / clan-mates, PvP-preset kills and the same
 *     victim twice in one round never count (Bounty Hunter already refuses to pair most of these as targets).</li>
 * </ul>
 * Tabs: Overview (leaderboards + milestone) and Full Leaderboard (top 10 per division). Clicking a ranked row shows
 * that player's PvP profile in chat; the crate can be inspected for its loot. State is saved to
 * runtime/saves/edgeville_pvp_rewards.json after every change. Game thread only.
 */
public final class EdgevillePvpRewards {

	public static final int INTERFACE = 1162;
	private static final int CLOSE = 2, TITLE = 3, SUB_HEADER = 4, TIMER = 5;
	/** Sub-tabs: box (click + hover), 2px orange underline (active), label. */
	private static final int TAB_OVERVIEW = 6, TAB_LEADERBOARD = 9, TAB_UNDERLINE = 1, TAB_LABEL = 2;
	private static final int OVERVIEW = 13, FULL = 112;
	/**
	 * Overview columns: heading +5, sub +6, rows at +7 + 7r (box, border, rank, name, kills, BM icon, prize),
	 * pot text +30 (in a framed strip).
	 */
	private static final int MAINS = 14, PURES = 45, HEADING = 5, SUB = 6, FIRST_RANK = 7, RANK_STRIDE = 7, POT = 30;
	private static final int R_RANK = 2, R_NAME = 3, R_KILLS = 4, R_BM = 5, R_PRIZE = 6;
	private static final int MILESTONE_NAME = 82, MILESTONE_ITEM = 88, BAR_FILL = 94, BAR_SHINE = 95, BAR_TEXT = 96,
		PROGRESS = 97, RULE = 98, CLAIM = 99, CLAIM_BUTTON = 102, NEXT_TIER = 104, LOOT = 105, LOOT_TEXT = 109,
		LOOT_BACK = 110;
	/** Full standings columns: heading +5, rows at +7 + 5r (box, rank, name, kills, score). */
	private static final int FULL_MAINS = 113, FULL_PURES = 170, FULL_ROWS = 10, FULL_STRIDE = 5;
	private static final int STATUS = 230, FOOTNOTE = 231;
	private static final int BAR_WIDTH = 130, SET_WIDTH = 10606, SET_COLOUR = 45, SET_OPBASE = 10752;
	private static final int BAR_ORANGE = 0xe0a030, BAR_ORANGE_SHINE = 0xffc860, BAR_GREEN = 0x2fa83a, BAR_GREEN_SHINE = 0x5ccf5c;
	private static final int BLOOD_MONEY_ICON_AMOUNT = 1000;

	/** The Edgeville hotspot: Wilderness levels 1 - ~10 north of Edgeville (same bounds as BH's "Edgeville Only"). */
	public static final Bounds HOTSPOT = new Bounds(2993, 3523, 3124, 3597, -1);

	public static final int ROUND_MINUTES = 90;
	public static final int SCORE_PER_KILL = 10, MAX_STREAK_BONUS = 10;
	public static final int BASE_POT = 5_000, POT_PER_KILL = 500;
	private static final int[] POT_SHARES = {50, 30, 20};
	/** Milestones: kills needed (this round) -> Bounty crates paid. */
	private static final int[] MILESTONE_KILLS = {5, 10, 20};
	private static final int[] MILESTONE_CRATES = {1, 2, 3};
	public static final int BOUNTY_CRATE = 24329;

	private static final String[] DIVISIONS = {"Mains", "Pures"};
	private static final String ORANGE = "<col=ff981f>", GOLD = "<col=ffc35a>", WHITE = "<col=ffffff>",
		GREY = "<col=9a9a9a>", GREEN = "<col=00ff00>", RED = "<col=ff3333>", VACANT = "<col=6f6f6f>",
		TAN = "<col=d8d0c0>", END = "</col>";
	private static final String TAG = "<col=ff981f><shad=000000>[Edgeville PvP]</shad></col> ";
	/** Classic hiscore gold / silver / bronze. */
	private static final String[] RANK_COLOURS = {"<col=ffff00>", "<col=ffffff>", "<col=ff981f>"};
	private static final String SUBTLE = "<col=8f8f8f>";

	/** What a Bounty crate holds (shown by Inspect; must match {@link #registerCrate}). */
	private static final String LOOT_TABLE = GOLD + "Always:" + END + "<br>"
		+ "250 - 750 Blood Money<br>"
		+ GOLD + "Plus one of:" + END + "<br>"
		+ "40%  15 Sharks, 4 Super restores,<br>      4 Saradomin brews<br>"
		+ "30%  3 Super combats, 10 Anglerfish<br>"
		+ "20%  +500 bonus Blood Money<br>"
		+ "10%  Antique emblem (tier 1)";

	/* ---------------- persisted state ---------------- */

	private static final class Entry {
		@Expose String uid;
		@Expose String name;
		@Expose int division;
		@Expose int kills;
		@Expose int score;
		/** Milestones claimed this round. */
		@Expose int milestones;
		@Expose long lastKill;
	}

	private static final class SaveState {
		@Expose long round;
		@Expose Map<String, Entry> entries = new HashMap<>();
		@Expose Set<String> killedPairs = new HashSet<>();
		@Expose Map<String, Integer> pendingBloodMoney = new HashMap<>();
		@Expose Map<String, Integer> pendingCrates = new HashMap<>();
	}

	private static SaveState state = new SaveState();

	/** Per-viewer UI state (not saved): the open tab and whether the crate loot overlay is up. */
	private static final Map<String, Integer> viewTab = new HashMap<>();
	private static final Set<String> lootOpen = new HashSet<>();

	private EdgevillePvpRewards() {
	}

	public static void register() {
		load();
		rollRound();
		InterfaceHandler.register(INTERFACE, h -> {
			h.actions[CLOSE] = (SimpleAction) Player::closeInterfaces;
			h.actions[TAB_OVERVIEW] = (SimpleAction) p -> setTab(p, 0);
			h.actions[TAB_LEADERBOARD] = (SimpleAction) p -> setTab(p, 1);
			h.actions[MILESTONE_ITEM] = (SimpleAction) p -> {
				lootOpen.add(p.uuid());
				p.sendMessage(TAG + "Bounty crate: 250-750 Blood Money, plus one of: PK supplies (sharks, restores, brews),"
					+ " combat supplies (super combats, anglerfish), +500 bonus Blood Money, or an Antique emblem (tier 1).");
				draw(p);
			};
			h.actions[LOOT_BACK] = (SimpleAction) p -> {
				lootOpen.remove(p.uuid());
				draw(p);
			};
			h.actions[CLAIM_BUTTON] = (SimpleAction) EdgevillePvpRewards::claim;
			for (int r = 0; r < 3; r++) {
				int rank = r;
				h.actions[MAINS + FIRST_RANK + r * RANK_STRIDE] = (SimpleAction) p -> profile(p, 0, rank);
				h.actions[PURES + FIRST_RANK + r * RANK_STRIDE] = (SimpleAction) p -> profile(p, 1, rank);
			}
			for (int r = 0; r < FULL_ROWS; r++) {
				int rank = r;
				h.actions[FULL_MAINS + 7 + r * FULL_STRIDE] = (SimpleAction) p -> profile(p, 0, rank);
				h.actions[FULL_PURES + 7 + r * FULL_STRIDE] = (SimpleAction) p -> profile(p, 1, rank);
			}
			h.closedAction = (p, i) -> lootOpen.remove(p.uuid());
		});
		World.startEvent(e -> {
			int ticks = 0;
			while (true) {
				e.delay(10);
				rollRound();
				if (++ticks % 5 == 0) {
					// the countdown (minutes) for anyone watching
					for (Player p : World.players()) {
						if (p.isVisibleInterface(INTERFACE)) {
							sendTimer(p.getPacketSender());
						}
					}
				}
			}
		});
		LoginListener.register(EdgevillePvpRewards::payPending);
		registerCrate();
	}

	/* ---------------- rounds ---------------- */

	private static long currentRound() {
		return System.currentTimeMillis() / 60_000L / ROUND_MINUTES;
	}

	private static long roundEndsAtMillis() {
		return (currentRound() + 1) * ROUND_MINUTES * 60_000L;
	}

	/** Ends the saved round (paying winners and unclaimed milestones) once the clock has moved past it. */
	private static void rollRound() {
		long now = currentRound();
		if (state.round == now) {
			return;
		}
		if (state.round != 0 && !state.entries.isEmpty()) {
			payWinners();
			payUnclaimedMilestones();
		}
		state.round = now;
		state.entries.clear();
		state.killedPairs.clear();
		save();
		refreshViewers();
	}

	private static void payWinners() {
		for (int division = 0; division < DIVISIONS.length; division++) {
			List<Entry> top = leaderboard(division);
			int pot = pot(division);
			List<String> winners = new ArrayList<>();
			for (int r = 0; r < Math.min(3, top.size()); r++) {
				Entry e = top.get(r);
				int share = pot * POT_SHARES[r] / 100;
				winners.add("#" + (r + 1) + " " + e.name + " (" + share + " BM)");
				Player p = World.getPlayer(e.uid);
				if (p != null && p.isOnline()) {
					giveBloodMoney(p, share, "placing #" + (r + 1) + " in the " + DIVISIONS[division] + " round");
				} else {
					state.pendingBloodMoney.merge(e.uid, share, Integer::sum);
				}
			}
			if (!winners.isEmpty()) {
				Broadcast.GLOBAL.sendNews(Icon.WILDY_SKULL, "Edgeville PvP",
						DIVISIONS[division] + " round over! " + String.join(", ", winners));
			}
		}
	}

	private static void payUnclaimedMilestones() {
		for (Entry e : state.entries.values()) {
			int crates = 0;
			while (e.milestones < MILESTONE_KILLS.length && e.kills >= MILESTONE_KILLS[e.milestones]) {
				crates += MILESTONE_CRATES[e.milestones];
				e.milestones++;
			}
			if (crates == 0) {
				continue;
			}
			Player p = World.getPlayer(e.uid);
			if (p != null && p.isOnline()) {
				giveCrates(p, crates, "your unclaimed round milestones");
			} else {
				state.pendingCrates.merge(e.uid, crates, Integer::sum);
			}
		}
	}

	private static void payPending(Player player) {
		Integer owed = state.pendingBloodMoney.remove(player.uuid());
		Integer crates = state.pendingCrates.remove(player.uuid());
		if ((owed == null || owed <= 0) && (crates == null || crates <= 0)) {
			return;
		}
		save();
		if (owed != null && owed > 0) {
			giveBloodMoney(player, owed, "your Edgeville PvP round placing");
		}
		if (crates != null && crates > 0) {
			giveCrates(player, crates, "your unclaimed Edgeville PvP milestones");
		}
	}

	private static void giveBloodMoney(Player player, int amount, String why) {
		int banked = player.getBank().deposit(ItemID.BLOOD_MONEY, amount);
		if (banked < amount) {
			player.getInventory().addOrSendToBank(ItemID.BLOOD_MONEY, amount - banked);
		}
		player.sendMessage(TAG + "You receive " + GREEN + String.format(Locale.ROOT, "%,d", amount) + END
				+ " Blood Money for " + why + " (sent to your bank).");
	}

	private static void giveCrates(Player player, int crates, String why) {
		int added = player.getInventory().add(BOUNTY_CRATE, crates);
		if (added < crates) {
			player.getBank().deposit(BOUNTY_CRATE, crates - added);
		}
		player.sendMessage(TAG + GOLD + crates + " Bounty crate" + (crates == 1 ? "" : "s") + END + " for " + why
				+ (added < crates ? " (some went to your bank)." : "."));
	}

	/* ---------------- kills ---------------- */

	/** Called from PlayerCombat when a player is killed by their assigned Bounty Hunter target. */
	public static void onTargetKill(Player killer, Player victim) {
		if (killer == null || victim == null || killer == victim) {
			return;
		}
		if (!victim.getPosition().inBounds(HOTSPOT) || victim.wildernessLevel <= 0) {
			return;
		}
		rollRound();
		if (killer.pvpPresetActive || victim.pvpPresetActive) {
			killer.sendMessage(TAG + GREY + "PvP preset kills don't count towards the hotspot round." + END);
			return;
		}
		if (killer.getIpInt() == victim.getIpInt()
				|| (killer.hwid != null && !killer.hwid.isEmpty() && killer.hwid.equals(victim.hwid))) {
			killer.sendMessage(TAG + RED + "Same-address kills don't count towards the hotspot round." + END);
			return;
		}
		if (BountyHunter.friendLink.test(killer, victim) || BountyHunter.friendLink.test(victim, killer)
				|| BountyHunter.clanLink.test(killer, victim)) {
			killer.sendMessage(TAG + RED + "Kills on friends or clan-mates don't count towards the hotspot round." + END);
			return;
		}
		String pair = killer.uuid() + "|" + victim.uuid();
		if (!state.killedPairs.add(pair)) {
			killer.sendMessage(TAG + GREY + "You've already scored " + victim.getName()
					+ " this round; repeat targets don't count." + END);
			return;
		}

		int division = division(killer);
		Entry e = state.entries.computeIfAbsent(key(killer.uuid(), division), k -> {
			Entry fresh = new Entry();
			fresh.uid = killer.uuid();
			fresh.division = division;
			return fresh;
		});
		e.name = killer.getName();
		e.kills++;
		int gained = SCORE_PER_KILL + Math.min(MAX_STREAK_BONUS, Math.max(0, killer.bhKillstreak - 1) * 2);
		e.score += gained;
		e.lastKill = System.currentTimeMillis();
		save();

		int rank = rank(e);
		killer.sendMessage(TAG + "Hotspot target kill! " + GREEN + "+" + gained + END + " score  ("
				+ e.kills + " kill" + (e.kills == 1 ? "" : "s") + ", " + e.score + " score, rank #" + rank + " "
				+ DIVISIONS[division] + "). ::pvprewards");
		if (claimable(e)) {
			killer.sendMessage(TAG + GOLD + "Milestone reached: Bounty Crate (tier " + (e.milestones + 1)
					+ ") is ready to claim in ::pvprewards." + END);
		}
		refreshViewers();
	}

	private static boolean claimable(Entry e) {
		return e != null && e.milestones < MILESTONE_KILLS.length && e.kills >= MILESTONE_KILLS[e.milestones];
	}

	/** The Claim button: pays the next reached milestone into the inventory (bank if full). */
	private static void claim(Player player) {
		rollRound();
		Entry e = state.entries.get(key(player.uuid(), division(player)));
		if (!claimable(e)) {
			player.sendMessage(TAG + "You have no milestone ready to claim yet.");
			draw(player);
			return;
		}
		int tier = e.milestones + 1;
		int crates = MILESTONE_CRATES[e.milestones];
		e.milestones++; // marked claimed before paying: a double click cannot claim twice
		save();
		giveCrates(player, crates, "claiming Bounty Crate (tier " + tier + ")");
		refreshViewers();
	}

	/** Pures = base Defence 1. */
	private static int division(Player player) {
		return player.getStats().get(StatType.Defence).fixedLevel <= 1 ? 1 : 0;
	}

	private static String key(String uid, int division) {
		return uid + "|" + division;
	}

	private static List<Entry> leaderboard(int division) {
		List<Entry> list = new ArrayList<>();
		for (Entry e : state.entries.values()) {
			if (e.division == division && e.kills > 0) {
				list.add(e);
			}
		}
		list.sort(Comparator.comparingInt((Entry e) -> e.score).reversed()
				.thenComparing(Comparator.comparingInt((Entry e) -> e.kills).reversed())
				.thenComparingLong(e -> e.lastKill));
		return list;
	}

	private static int rank(Entry entry) {
		return leaderboard(entry.division).indexOf(entry) + 1;
	}

	private static int pot(int division) {
		int kills = 0;
		for (Entry e : state.entries.values()) {
			if (e.division == division) {
				kills += e.kills;
			}
		}
		return BASE_POT + POT_PER_KILL * kills;
	}

	/** A ranked row was clicked: this round's standing plus lifetime PvP stats (if they are online) in chat. */
	private static void profile(Player viewer, int division, int rank) {
		List<Entry> top = leaderboard(division);
		if (rank >= top.size()) {
			return;
		}
		Entry e = top.get(rank);
		viewer.sendMessage(TAG + WHITE + e.name + END + " - " + DIVISIONS[division] + " #" + (rank + 1) + ": "
				+ e.kills + " hotspot kill" + (e.kills == 1 ? "" : "s") + ", " + e.score + " score this round.");
		Player target = World.getPlayer(e.uid);
		if (target != null && target.isOnline()) {
			int kills = VarPlayerRepository.PVP_KILLS.get(target);
			int deaths = VarPlayerRepository.PVP_DEATHS.get(target);
			String kdr = deaths == 0 ? String.valueOf(kills) : String.format(Locale.ROOT, "%.2f", kills / (double) deaths);
			viewer.sendMessage(TAG + "Lifetime: " + kills + " kills / " + deaths + " deaths (KDR " + kdr + "), "
					+ VarPlayerRepository.BOUNTY_HUNTER_TARGET_KILLS.get(target) + " BH target kills, combat "
					+ target.getCombat().getLevel() + ". " + GREEN + "Online" + END);
		} else {
			viewer.sendMessage(TAG + GREY + e.name + " is offline; lifetime stats show while they are online." + END);
		}
	}

	/* ---------------- dashboard ---------------- */

	public static void open(Player player) {
		rollRound();
		lootOpen.remove(player.uuid());
		player.openInterface(ToplevelComponent.MAINMODAL, INTERFACE);
		PacketSender ps = player.getPacketSender();
		ps.sendString(INTERFACE, TITLE, ORANGE + "Edgeville PvP Rewards" + END);
		ps.sendString(INTERFACE, SUB_HEADER, SUBTLE + "Assigned Bounty Hunter target kills inside the Edgeville hotspot count." + END);
		ps.sendString(INTERFACE, FOOTNOTE, "Repeat targets and same-address kills do not count.");
		ps.sendString(INTERFACE, MAINS + HEADING, "Mains");
		ps.sendString(INTERFACE, MAINS + SUB, "Top target killers");
		ps.sendString(INTERFACE, PURES + HEADING, "Pures (1 Defence)");
		ps.sendString(INTERFACE, PURES + SUB, "Top pure target killers");
		ps.sendString(INTERFACE, FULL_MAINS + 5, "Mains - top " + FULL_ROWS);
		ps.sendString(INTERFACE, FULL_PURES + 5, "Pures - top " + FULL_ROWS);
		ps.sendString(INTERFACE, LOOT_TEXT, LOOT_TABLE);
		ps.sendString(INTERFACE, LOOT_BACK + 1, "Back");
		draw(player);
	}

	private static void setTab(Player player, int tab) {
		viewTab.put(player.uuid(), tab);
		lootOpen.remove(player.uuid());
		draw(player);
	}

	private static void refreshViewers() {
		for (Player p : World.players()) {
			if (p.isVisibleInterface(INTERFACE)) {
				draw(p);
			}
		}
	}

	private static void draw(Player player) {
		PacketSender ps = player.getPacketSender();
		sendTimer(ps);
		int tab = viewTab.getOrDefault(player.uuid(), 0);
		drawTab(ps, TAB_OVERVIEW, tab == 0, "Overview");
		drawTab(ps, TAB_LEADERBOARD, tab == 1, "Full Leaderboard");
		ps.setHidden(INTERFACE, OVERVIEW, tab != 0);
		ps.setHidden(INTERFACE, FULL, tab != 1);
		if (tab == 0) {
			drawLeaderboard(ps, MAINS, 0);
			drawLeaderboard(ps, PURES, 1);
			drawMilestone(player, ps);
		} else {
			drawFullBoard(ps, FULL_MAINS, 0);
			drawFullBoard(ps, FULL_PURES, 1);
		}

		int division = division(player);
		Entry mine = state.entries.get(key(player.uuid(), division));
		int rank = mine == null ? 0 : rank(mine);
		ps.sendString(INTERFACE, STATUS, GREY + "Division: " + END + GOLD + DIVISIONS[division] + END
				+ GREY + "   |   Score: " + END + WHITE + (mine == null ? 0 : mine.score) + END
				+ GREY + "   |   Kills: " + END + WHITE + (mine == null ? 0 : mine.kills) + END
				+ GREY + "   |   Rank: " + END + (rank > 0 ? GREEN + "#" + rank + END : TAN + "Unranked" + END));
	}

	/** Active: flat stone + the orange underline + white label; inactive: soft grey label (the box brightens on hover). */
	private static void drawTab(PacketSender ps, int tab, boolean active, String label) {
		ps.setHidden(INTERFACE, tab + TAB_UNDERLINE, !active);
		ps.sendString(INTERFACE, tab + TAB_LABEL, (active ? WHITE : SUBTLE) + label + END);
	}

	private static void drawMilestone(Player player, PacketSender ps) {
		Entry mine = state.entries.get(key(player.uuid(), division(player)));
		int kills = mine == null ? 0 : mine.kills;
		int claimed = mine == null ? 0 : mine.milestones;
		boolean loot = lootOpen.contains(player.uuid());
		ps.setHidden(INTERFACE, LOOT, !loot);

		int need, crates;
		if (claimed >= MILESTONE_KILLS.length) {
			need = MILESTONE_KILLS[MILESTONE_KILLS.length - 1];
			crates = MILESTONE_CRATES[MILESTONE_CRATES.length - 1];
			ps.sendString(INTERFACE, MILESTONE_NAME, "All milestones claimed!");
		} else {
			need = MILESTONE_KILLS[claimed];
			crates = MILESTONE_CRATES[claimed];
			ps.sendString(INTERFACE, MILESTONE_NAME, "Bounty Crate (tier " + (claimed + 1) + ")");
		}
		ps.sendItem(INTERFACE, MILESTONE_ITEM, BOUNTY_CRATE, crates);
		// right-click / hover: "Inspect Bounty Crate (tier N) rewards"
		ps.sendClientScript(SET_OPBASE, "Is", INTERFACE << 16 | MILESTONE_ITEM, ORANGE + "Bounty Crate (tier "
			+ Math.min(claimed + 1, MILESTONE_KILLS.length) + ") rewards" + END);

		// inset bar: fill = kills / need, orange while in progress, green when reached
		int shown = Math.min(kills, need);
		int pct = need <= 0 ? 0 : shown * 100 / need;
		int width = (int) Math.round(BAR_WIDTH * pct / 100.0);
		ps.setHidden(INTERFACE, BAR_FILL, width <= 0);
		ps.setHidden(INTERFACE, BAR_SHINE, width <= 0);
		if (width > 0) {
			ps.sendClientScript(SET_WIDTH, "Ii", INTERFACE << 16 | BAR_FILL, width);
			ps.sendClientScript(SET_WIDTH, "Ii", INTERFACE << 16 | BAR_SHINE, width);
			ps.sendClientScript(SET_COLOUR, "Ii", INTERFACE << 16 | BAR_FILL, pct >= 100 ? BAR_GREEN : BAR_ORANGE);
			ps.sendClientScript(SET_COLOUR, "Ii", INTERFACE << 16 | BAR_SHINE, pct >= 100 ? BAR_GREEN_SHINE : BAR_ORANGE_SHINE);
		}
		ps.sendString(INTERFACE, BAR_TEXT, WHITE + shown + " / " + need + " (" + pct + "%)" + END);
		ps.sendString(INTERFACE, PROGRESS, WHITE + kills + " / " + need + END + " valid target kills<br>inside the hotspot");

		boolean ready = claimable(mine);
		ps.setHidden(INTERFACE, CLAIM, !ready);
		ps.setHidden(INTERFACE, RULE, ready);
		if (ready) {
			ps.sendString(INTERFACE, CLAIM_BUTTON + 1, GOLD + "Claim Tier " + (claimed + 1) + " Crate" + END);
		} else if (claimed >= MILESTONE_KILLS.length) {
			ps.sendString(INTERFACE, RULE, "Keep climbing the leaderboard for a share of the pot.");
		} else {
			int left = need - kills;
			ps.sendString(INTERFACE, RULE, "Defeat " + ORANGE + left + END + " more eligible target" + (left == 1 ? "" : "s")
					+ " to earn this reward.");
		}
		ps.sendString(INTERFACE, NEXT_TIER, claimed + 1 < MILESTONE_KILLS.length
				? ORANGE + "Next: Tier " + (claimed + 2) + " at " + MILESTONE_KILLS[claimed + 1] + " kills" + END
				: claimed + 1 == MILESTONE_KILLS.length ? "Final milestone" : "");
	}

	private static void sendTimer(PacketSender ps) {
		long minutes = Math.max(0, (roundEndsAtMillis() - System.currentTimeMillis()) / 60_000L);
		String left = minutes >= 60 ? (minutes / 60) + "h " + (minutes % 60) + "m" : Math.max(1, minutes) + "m";
		ps.sendString(INTERFACE, TIMER, ORANGE + "Round ends in: " + END + WHITE + left + END);
	}

	private static void drawLeaderboard(PacketSender ps, int panel, int division) {
		List<Entry> top = leaderboard(division);
		int pot = pot(division);
		for (int r = 0; r < 3; r++) {
			int row = panel + FIRST_RANK + r * RANK_STRIDE;
			Entry e = r < top.size() ? top.get(r) : null;
			ps.sendString(INTERFACE, row + R_RANK, (e == null ? VACANT : RANK_COLOURS[r]) + "#" + (r + 1) + END);
			ps.sendString(INTERFACE, row + R_NAME, e == null ? VACANT + "-- Vacant --" + END : RANK_COLOURS[r] + e.name + END);
			ps.sendString(INTERFACE, row + R_KILLS, e == null ? "" : e.kills + " kill" + (e.kills == 1 ? "" : "s") + " · " + e.score + " pts");
			ps.sendItem(INTERFACE, row + R_BM, ItemID.BLOOD_MONEY, BLOOD_MONEY_ICON_AMOUNT);
			ps.sendString(INTERFACE, row + R_PRIZE, (e == null ? VACANT : GREEN) + "Prize: "
					+ String.format(Locale.ROOT, "%,d", pot * POT_SHARES[r] / 100) + " BM" + END);
		}
		ps.sendString(INTERFACE, panel + POT, ORANGE + "Total Pot: " + END + GREEN + String.format(Locale.ROOT, "%,d", pot) + " BM" + END);
	}

	private static void drawFullBoard(PacketSender ps, int panel, int division) {
		List<Entry> top = leaderboard(division);
		for (int r = 0; r < FULL_ROWS; r++) {
			int row = panel + 7 + r * FULL_STRIDE;
			Entry e = r < top.size() ? top.get(r) : null;
			ps.sendString(INTERFACE, row + 1, (e == null ? VACANT : r < 3 ? RANK_COLOURS[r] : GOLD) + (r + 1) + END);
			ps.sendString(INTERFACE, row + 2, e == null ? VACANT + "-- Vacant --" + END : e.name);
			ps.sendString(INTERFACE, row + 3, e == null ? "" : String.valueOf(e.kills));
			ps.sendString(INTERFACE, row + 4, e == null ? "" : String.valueOf(e.score));
		}
	}

	/* ---------------- Bounty crate ---------------- */

	private static void registerCrate() {
		ItemAction open = (player, item) -> {
			if (player.getInventory().getFreeSlots() < 3) {
				player.sendMessage("You need at least 3 free inventory slots to open a Bounty crate.");
				return;
			}
			item.remove(1);
			int bm = Random.get(250, 750);
			player.getInventory().addOrSendToBank(ItemID.BLOOD_MONEY, bm);
			List<String> got = new ArrayList<>();
			got.add(String.format(Locale.ROOT, "%,d", bm) + " Blood Money");
			int roll = Random.get(99);
			if (roll < 40) {
				give(player, got, 386, 15);    // sharks (noted)
				give(player, got, 3025, 4);    // super restores (noted)
				give(player, got, 6686, 4);    // saradomin brews (noted)
			} else if (roll < 70) {
				give(player, got, 12696, 3);   // super combat potions (noted)
				give(player, got, 13442, 10);  // anglerfish (noted)
			} else if (roll < 90) {
				int extra = 500;
				player.getInventory().addOrSendToBank(ItemID.BLOOD_MONEY, extra);
				got.add(extra + " bonus Blood Money");
			} else {
				give(player, got, 24565, 1);   // antique emblem (tier 1)
			}
			player.sendMessage(TAG + "Your Bounty crate contained: " + String.join(", ", got) + ".");
		};
		if (!ItemAction.registerInventory(BOUNTY_CRATE, "Open", open)) {
			ItemAction.registerInventory(BOUNTY_CRATE, 1, open);
		}
	}

	private static void give(Player player, List<String> got, int itemId, int amount) {
		player.getInventory().addOrSendToBank(itemId, amount);
		got.add(amount + " x " + new Item(itemId).getDef().name);
	}

	/* ---------------- storage ---------------- */

	private static File file() {
		return new File(ServerWrapper.dataFolder, "runtime/saves/edgeville_pvp_rewards.json");
	}

	private static void load() {
		File file = file();
		if (!file.exists()) {
			return;
		}
		try (FileReader reader = new FileReader(file)) {
			SaveState loaded = JsonUtils.GSON.fromJson(reader, SaveState.class);
			if (loaded != null) {
				if (loaded.entries == null) loaded.entries = new HashMap<>();
				if (loaded.killedPairs == null) loaded.killedPairs = new HashSet<>();
				if (loaded.pendingBloodMoney == null) loaded.pendingBloodMoney = new HashMap<>();
				if (loaded.pendingCrates == null) loaded.pendingCrates = new HashMap<>();
				state = loaded;
			}
		} catch (Exception e) {
			ServerWrapper.logError("Unable to load Edgeville PvP rewards", e);
		}
	}

	private static void save() {
		File file = file();
		file.getParentFile().mkdirs();
		try (FileWriter writer = new FileWriter(file)) {
			JsonUtils.GSON_PRETTY.toJson(state, writer);
		} catch (Exception e) {
			ServerWrapper.logError("Unable to save Edgeville PvP rewards", e);
		}
	}
}
