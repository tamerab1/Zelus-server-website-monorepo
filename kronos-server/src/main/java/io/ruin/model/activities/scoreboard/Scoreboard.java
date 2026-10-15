package io.ruin.model.activities.scoreboard;

import com.google.gson.annotations.Expose;
import io.ruin.api.utils.JsonUtils;
import io.ruin.api.utils.ServerWrapper;
import io.ruin.cache.Icon;
import io.ruin.cache.ObjType;
import io.ruin.model.World;
import io.ruin.model.activities.wilderness.EdgevillePvpRewards;
import io.ruin.model.entity.player.Player;
import io.ruin.model.entity.shared.listeners.LoginListener;
import io.ruin.model.inter.InterfaceHandler;
import io.ruin.model.inter.ToplevelComponent;
import io.ruin.model.inter.actions.SimpleAction;
import io.ruin.model.item.Item;
import io.ruin.model.stat.StatType;
import io.ruin.network.PacketSender;
import io.ruin.utility.Broadcast;

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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * The Zelus Scoreboard (interface 1163, built by .dev/cache-restore-tool/src/BuildScoreboardInterface.java; ::scoreboard).
 * Daily and weekly player boards for every {@link Stat} (no clan boards: Zelus has no clans), fed by hooks in the game code (PvP kills
 * that passed Killer's anti-farm checks, boss kill counts, NPC drops, skilling XP, votes, wilderness chests, tournament
 * wins and donations). When a day / week ends (server time; the day ends at midnight, the week on Sunday night) the top 3
 * players of every paid stat get PK points ({@link #DAILY_REWARD} / {@link #WEEKLY_REWARD}); offline winners are paid on
 * their next login. Saved to runtime/saves/scoreboard.json. Game thread only.
 */
public final class Scoreboard {

	public enum Category {
		PVP("PvP Score", Icon.PK_SWORDS), PVM("PvM Score", Icon.WANTED_SKULL), SKILLING("Skilling Score", Icon.SKILL_STAR),
		MISC("Misc Score", Icon.COLLECTION_LOG);

		final String label;
		final Icon icon;

		Category(String label, Icon icon) {
			this.label = label;
			this.icon = icon;
		}
	}

	public enum Stat {
		PVP_KILLS(Category.PVP, "Overall Kills", true, 12746, "Kills"),
		SINGLE_KILLS(Category.PVP, "Single Kills", true, 4587, "Kills"),
		MULTI_KILLS(Category.PVP, "Multi Kills", true, 13652, "Kills"),
		EDGE_KILLS(Category.PVP, "Edgeville Kills", true, 24329, "Kills"),
		PURE_KILLS(Category.PVP, "Pure Kills", true, 1052, "Kills"),
		ZERKER_KILLS(Category.PVP, "Zerker Kills", true, 3751, "Kills"),
		DEEP_WILD_KILLS(Category.PVP, "Deep Wild Kills", true, 9013, "Kills"),
		PVM_LOOT(Category.PVM, "PvM Loot Value", true, 995, "Loot (gp)"),
		COX(Category.PVM, "Chambers of Xeric", true, 20851, "Runs"),
		TOB(Category.PVM, "Theatre of Blood", true, 22473, "Runs"),
		ZULRAH(Category.PVM, "Zulrah", true, 12921, "Kills"),
		CORP(Category.PVM, "Corporeal Beast", true, 12816, "Kills"),
		NEX(Category.PVM, "Nex", true, 26348, "Kills"),
		REVENANTS(Category.PVM, "Revenants", true, 21820, "Kills"),
		SKILL_XP(Category.SKILLING, "Skilling XP", true, 13280, "XP"),
		RESOURCES(Category.SKILLING, "Resources Gathered", true, 1511, "Gathered"),
		VOTES(Category.MISC, "Votes", true, 6199, "Votes"),
		WILDY_CHESTS(Category.MISC, "Wilderness Chests", true, 23490, "Chests"),
		TOURNAMENT_WINS(Category.MISC, "Tournament Wins", true, 21439, "Wins"),
		DONATED(Category.MISC, "Donations", false, 13190, "Donated");

		final Category category;
		final String label;
		/** Pays PK points to its top 3 (donations never do). */
		final boolean paid;
		/** The sidebar icon (an item sprite) and the score column's header. */
		final int iconItem;
		final String unit;

		Stat(Category category, String label, boolean paid, int iconItem, String unit) {
			this.category = category;
			this.label = label;
			this.paid = paid;
			this.iconItem = iconItem;
			this.unit = unit;
		}
	}

	public static final int INTERFACE = 1163;
	/** The Zelus Scoreboard board in the world (.dev/cache-restore-tool BuildChampionshipBoard, variant "scoreboard"). */
	public static final int BOARD_OBJECT_ID = 62391;
	private static final int CLOSE = 2, TITLE = 3;
	/** Tabs, toggles and stat entries are OSRS bezel buttons: button (click), active parts +1..+2, label +3. */
	private static final int FIRST_TAB = 4, TAB_STRIDE = 4, ACTIVE = 1, ACTIVE_PARTS = 2, LABEL = 3;
	private static final int DAILY = 20, WEEKLY = 24, SEGMENT_LABEL = 3;
	/** Sidebar entries: bezel button (4 comps) + item icon +4. */
	private static final int BOARD_TITLE = 28, FIRST_STAT = 34, STAT_STRIDE = 5, STAT_ICON = 4, STAT_BUTTONS = 7;
	private static final int SCORE_HEAD = 77;
	/** Rows: fill, rank, name, score, reward. */
	private static final int FIRST_ROW = 79, ROW_STRIDE = 5, ROWS = 10;
	private static final int PERSONAL = 132, COUNTDOWN = 133, PAYOUT = 134;
	private static final java.time.format.DateTimeFormatter DATE = java.time.format.DateTimeFormatter.ofPattern("d. MMMM yyyy", Locale.ENGLISH);

	/** PK points for #1 / #2 / #3 of each paid stat. */
	public static final int[] DAILY_REWARD = {50, 30, 15};
	public static final int[] WEEKLY_REWARD = {250, 150, 75};
	private static final ZoneId ZONE = ZoneId.systemDefault();
	private static final int DAY = 0, WEEK = 1;

	private static final String ORANGE = "<col=ff981f>", GOLD = "<col=ffc35a>", WHITE = "<col=ffffff>", GREY = "<col=9a9a9a>",
		GREEN = "<col=00ff00>", YELLOW = "<col=ffff00>", TAN = "<col=d8d0c0>", VACANT = "<col=6f6f6f>", END = "</col>";
	private static final String TAG = "<col=ff981f><shad=000000>[Scoreboard]</shad></col> ";
	/** Classic hiscore colours: gold, silver (white), bronze (orange). */
	private static final String[] RANK_COLOURS = {"<col=ffff00>", "<col=ffffff>", "<col=ff981f>"};


	/* ---------------- persisted state ---------------- */

	private static final class Board {
		/** stat -> uid -> score */
		@Expose Map<String, Map<String, Long>> users = new HashMap<>();
	}

	private static final class SaveState {
		@Expose String day;
		@Expose String week;
		@Expose Board daily = new Board();
		@Expose Board weekly = new Board();
		@Expose Map<String, String> names = new HashMap<>();
		@Expose Map<String, Integer> pendingPkp = new HashMap<>();
	}

	private static SaveState state = new SaveState();
	private static boolean dirty;

	/** Per-viewer view: category, stat, weekly. */
	private static final class View {
		Category category = Category.PVP;
		Stat stat = Stat.PVP_KILLS;
		boolean weekly;
	}

	private static final Map<String, View> views = new HashMap<>();

	private Scoreboard() {
	}

	public static void register() {
		load();
		roll();
		InterfaceHandler.register(INTERFACE, h -> {
			h.actions[CLOSE] = (SimpleAction) Player::closeInterfaces;
			Category[] cats = Category.values();
			for (int t = 0; t < cats.length; t++) {
				Category category = cats[t];
				h.actions[FIRST_TAB + t * TAB_STRIDE] = (SimpleAction) p -> {
					View v = view(p);
					v.category = category;
					v.stat = stats(category).get(0);
					draw(p);
				};
			}
			for (int s = 0; s < STAT_BUTTONS; s++) {
				int index = s;
				h.actions[FIRST_STAT + s * STAT_STRIDE] = (SimpleAction) p -> {
					View v = view(p);
					List<Stat> list = stats(v.category);
					if (index < list.size()) {
						v.stat = list.get(index);
						draw(p);
					}
				};
			}
			h.actions[DAILY] = (SimpleAction) p -> { view(p).weekly = false; draw(p); };
			h.actions[WEEKLY] = (SimpleAction) p -> { view(p).weekly = true; draw(p); };
		});
		World.startEvent(e -> {
			int ticks = 0;
			while (true) {
				e.delay(10);
				roll();
				if (++ticks % 10 == 0 && dirty) {
					save();
				}
				if (ticks % 5 == 0) {
					for (Player p : World.players()) {
						if (p.isVisibleInterface(INTERFACE)) {
							sendCountdown(p.getPacketSender());
						}
					}
				}
			}
		});
		LoginListener.register(Scoreboard::payPending);
		io.ruin.model.map.object.actions.ObjectAction.register(BOARD_OBJECT_ID, "View", (player, obj) -> open(player));
	}

	/* ---------------- recording (hooks) ---------------- */

	public static void record(Player player, Stat stat, long amount) {
		if (player == null || amount <= 0) {
			return;
		}
		roll();
		String uid = player.uuid();
		state.names.put(uid, player.getName());
		add(state.daily.users, stat, uid, amount);
		add(state.weekly.users, stat, uid, amount);
		dirty = true;
	}

	/** A player kill that passed Killer.reward's anti-farm checks. */
	public static void onPvpKill(Player killer, Player victim) {
		record(killer, Stat.PVP_KILLS, 1);
		record(killer, victim.inMulti() ? Stat.MULTI_KILLS : Stat.SINGLE_KILLS, 1);
		if (victim.getPosition().inBounds(EdgevillePvpRewards.HOTSPOT))
			record(killer, Stat.EDGE_KILLS, 1);
		int defence = killer.getStats().get(StatType.Defence).fixedLevel;
		if (defence <= 1)
			record(killer, Stat.PURE_KILLS, 1);
		else if (defence >= 40 && defence <= 45)
			record(killer, Stat.ZERKER_KILLS, 1);
		if (victim.wildernessLevel >= 30)
			record(killer, Stat.DEEP_WILD_KILLS, 1);
	}

	/** KillCounter.increment: boss kill counts by their KillCounter name. */
	public static void onBossKill(Player player, String name) {
		if (name == null)
			return;
		String n = name.toLowerCase(Locale.ROOT);
		Stat stat = n.startsWith("chambers of xeric") ? Stat.COX
			: n.startsWith("theatre of blood") ? Stat.TOB
			: n.equals("zulrah") ? Stat.ZULRAH
			: n.equals("corporeal beast") ? Stat.CORP
			: n.equals("nex") ? Stat.NEX
			: n.contains("revenant") ? Stat.REVENANTS : null;
		if (stat != null)
			record(player, stat, 1);
	}

	/** NPC drops: their cache value (spawned / attribute items don't come from drops). */
	public static void onNpcDrop(Player player, List<Item> items) {
		if (player == null || items == null)
			return;
		long value = 0;
		for (Item item : items) {
			if (item == null)
				continue;
			ObjType def = ObjType.get(ObjType.unnotedId(item.getId()));
			if (def != null)
				value += (long) Math.max(0, def.value) * item.getAmount();
		}
		record(player, Stat.PVM_LOOT, value);
	}

	/** StatList.addXp: non-combat XP; gathering skills also count one resource per XP drop. */
	public static void onXp(Player player, StatType type, double gained, boolean normalSource) {
		if (gained <= 0 || !normalSource)
			return;
		switch (type) {
			case Attack, Strength, Defence, Hitpoints, Ranged, Magic, Prayer -> {
				return;
			}
			default -> {
			}
		}
		record(player, Stat.SKILL_XP, (long) gained);
		switch (type) {
			case Woodcutting, Mining, Fishing, Hunter, Farming -> record(player, Stat.RESOURCES, 1);
			default -> {
			}
		}
	}

	private static void add(Map<String, Map<String, Long>> board, Stat stat, String key, long amount) {
		board.computeIfAbsent(stat.name(), k -> new HashMap<>()).merge(key, amount, Long::sum);
	}

	/* ---------------- periods + payouts ---------------- */

	private static String today() {
		return LocalDate.now(ZONE).toString();
	}

	private static String thisWeek() {
		return LocalDate.now(ZONE).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString();
	}

	/** Pays and resets a board whose day / week is over. */
	private static void roll() {
		String day = today(), week = thisWeek();
		boolean changed = false;
		if (!day.equals(state.day)) {
			if (state.day != null)
				payout(state.daily, DAILY_REWARD, "daily");
			state.daily = new Board();
			state.day = day;
			changed = true;
		}
		if (!week.equals(state.week)) {
			if (state.week != null)
				payout(state.weekly, WEEKLY_REWARD, "weekly");
			state.weekly = new Board();
			state.week = week;
			changed = true;
		}
		if (changed) {
			state.names.keySet().retainAll(namesInUse());
			save();
		}
	}

	private static java.util.Set<String> namesInUse() {
		java.util.Set<String> used = new java.util.HashSet<>(state.pendingPkp.keySet());
		for (Board b : new Board[]{state.daily, state.weekly})
			for (Map<String, Long> m : b.users.values())
				used.addAll(m.keySet());
		return used;
	}

	private static void payout(Board board, int[] rewards, String period) {
		List<String> headlines = new ArrayList<>();
		for (Stat stat : Stat.values()) {
			if (!stat.paid)
				continue;
			List<Map.Entry<String, Long>> top = top(board.users, stat);
			for (int r = 0; r < Math.min(rewards.length, top.size()); r++) {
				String uid = top.get(r).getKey();
				int pkp = rewards[r];
				Player p = World.getPlayer(uid);
				if (p != null && p.isOnline())
					pay(p, pkp, "#" + (r + 1) + " " + period + " " + stat.label);
				else
					state.pendingPkp.merge(uid, pkp, Integer::sum);
				if (r == 0 && stat == Stat.PVP_KILLS || r == 0 && stat == Stat.PVM_LOOT || r == 0 && stat == Stat.SKILL_XP)
					headlines.add(stat.label + ": " + state.names.getOrDefault(uid, uid));
			}
		}
		if (!headlines.isEmpty())
			Broadcast.GLOBAL.sendNews(Icon.PIT_TROPHY, "Scoreboard", "The " + period + " scoreboard has been paid! "
				+ String.join(" · ", headlines));
	}

	private static void pay(Player player, int pkp, String why) {
		player.updatePKPoints(pkp);
		player.sendMessage(TAG + "You earn " + GREEN + pkp + " PKP" + END + " for " + why + ".");
	}

	private static void payPending(Player player) {
		Integer owed = state.pendingPkp.remove(player.uuid());
		if (owed != null && owed > 0) {
			dirty = true;
			pay(player, owed, "your scoreboard placings while you were away");
		}
	}

	private static List<Map.Entry<String, Long>> top(Map<String, Map<String, Long>> board, Stat stat) {
		Map<String, Long> scores = board.getOrDefault(stat.name(), Map.of());
		List<Map.Entry<String, Long>> list = new ArrayList<>(scores.entrySet());
		list.removeIf(e -> e.getValue() <= 0);
		list.sort(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()));
		return list;
	}

	/* ---------------- dashboard ---------------- */

	public static void open(Player player) {
		roll();
		player.openInterface(ToplevelComponent.MAINMODAL, INTERFACE);
		PacketSender ps = player.getPacketSender();
		ps.sendString(INTERFACE, TITLE, ORANGE + "Zelus Scoreboard" + END);
		ps.sendString(INTERFACE, PAYOUT, "Top 3 of each board earn " + DAILY_REWARD[0] + " / " + DAILY_REWARD[1] + " / "
			+ DAILY_REWARD[2] + " PKP daily and " + WEEKLY_REWARD[0] + " / " + WEEKLY_REWARD[1] + " / " + WEEKLY_REWARD[2]
			+ " PKP weekly.");
		draw(player);
	}

	private static View view(Player player) {
		return views.computeIfAbsent(player.uuid(), k -> new View());
	}

	private static List<Stat> stats(Category category) {
		List<Stat> list = new ArrayList<>();
		for (Stat s : Stat.values())
			if (s.category == category)
				list.add(s);
		return list;
	}

	private static void draw(Player player) {
		View v = view(player);
		PacketSender ps = player.getPacketSender();
		Category[] cats = Category.values();
		for (int t = 0; t < cats.length; t++)
			button(ps, FIRST_TAB + t * TAB_STRIDE, cats[t] == v.category, cats[t].icon.tag() + " " + cats[t].label);
		segment(ps, DAILY, !v.weekly, Icon.VOTE_STAR.tag() + " Daily");
		segment(ps, WEEKLY, v.weekly, Icon.BOND.tag() + " Weekly");
		List<Stat> list = stats(v.category);
		for (int s = 0; s < STAT_BUTTONS; s++) {
			int comp = FIRST_STAT + s * STAT_STRIDE;
			boolean present = s < list.size();
			ps.setHidden(INTERFACE, comp, !present);
			ps.setHidden(INTERFACE, comp + LABEL, !present);
			ps.setHidden(INTERFACE, comp + STAT_ICON, !present);
			if (present) {
				button(ps, comp, list.get(s) == v.stat, list.get(s).label);
				ps.sendItem(INTERFACE, comp + STAT_ICON, list.get(s).iconItem, 1); // amount 1: no stack number over the icon
			} else
				for (int k = 0; k < ACTIVE_PARTS; k++)
					ps.setHidden(INTERFACE, comp + ACTIVE + k, true);
		}
		LocalDate date = v.weekly ? LocalDate.now(ZONE).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) : LocalDate.now(ZONE);
		// two lines, right-aligned: the board, then its date (never runs into the toggles on the left)
		ps.sendString(INTERFACE, BOARD_TITLE, v.category.icon.tag() + " " + ORANGE + v.stat.label + END + "<br>"
			+ YELLOW + (v.weekly ? "Week of " : "") + DATE.format(date) + END);
		ps.sendString(INTERFACE, SCORE_HEAD, v.stat.unit);

		Board board = v.weekly ? state.weekly : state.daily;
		List<Map.Entry<String, Long>> top = top(board.users, v.stat);
		int[] rewards = v.weekly ? WEEKLY_REWARD : DAILY_REWARD;
		for (int r = 0; r < ROWS; r++) {
			int row = FIRST_ROW + r * ROW_STRIDE;
			Map.Entry<String, Long> e = r < top.size() ? top.get(r) : null;
			String colour = e == null ? VACANT : r < 3 ? RANK_COLOURS[r] : TAN;
			ps.sendString(INTERFACE, row + 1, colour + (r + 1) + "." + END);
			String name = e == null ? null : state.names.getOrDefault(e.getKey(), e.getKey());
			ps.sendString(INTERFACE, row + 2, e == null ? VACANT + "-- Vacant --" + END : colour + name + END);
			ps.sendString(INTERFACE, row + 3, e == null ? "" : format(v.stat, e.getValue()));
			ps.sendString(INTERFACE, row + 4, v.stat.paid && r < rewards.length
				? (e == null ? VACANT : GREEN) + rewards[r] + " PKP" + END : "");
		}

		// personal pane: the viewer on this board
		String key = player.uuid();
		{
			int rank = 0;
			long score = 0;
			for (int i = 0; i < top.size(); i++) {
				if (top.get(i).getKey().equalsIgnoreCase(key)) {
					rank = i + 1;
					score = top.get(i).getValue();
					break;
				}
			}
			String reward = v.stat.paid && rank >= 1 && rank <= rewards.length ? GREEN + rewards[rank - 1] + " PKP" + END
				: GREY + (!v.stat.paid ? "n/a" : "Top 3 only") + END;
			ps.sendString(INTERFACE, PERSONAL, TAN + "Your " + (v.weekly ? "Weekly" : "Daily") + " Rank: " + END
				+ (rank > 0 ? WHITE + "#" + rank + END : GREY + "Unranked" + END)
				+ TAN + "  |  Score: " + END + WHITE + format(v.stat, score) + END
				+ TAN + "  |  Reward: " + END + reward);
		}
		sendCountdown(ps);
	}

	private static void sendCountdown(PacketSender ps) {
		LocalDateTime now = LocalDateTime.now(ZONE);
		LocalDateTime dayEnd = now.toLocalDate().plusDays(1).atStartOfDay();
		LocalDateTime weekEnd = now.toLocalDate().with(TemporalAdjusters.next(DayOfWeek.MONDAY)).atStartOfDay();
		Duration d = Duration.between(now, dayEnd), w = Duration.between(now, weekEnd);
		ps.sendString(INTERFACE, COUNTDOWN, YELLOW + "Day ends in: " + d.toHours() + "h " + String.format("%02d", d.toMinutesPart()) + "m"
			+ END + GREY + "  |  " + END + YELLOW + "Week ends in: " + w.toDays() + "d " + String.format("%02d", w.toHoursPart()) + "h" + END);
	}

	private static void button(PacketSender ps, int comp, boolean active, String label) {
		for (int k = 0; k < ACTIVE_PARTS; k++)
			ps.setHidden(INTERFACE, comp + ACTIVE + k, !active);
		ps.sendString(INTERFACE, comp + LABEL, (active ? GOLD : TAN) + label + END);
	}

	private static void segment(PacketSender ps, int comp, boolean active, String label) {
		for (int k = 1; k <= 2; k++)
			ps.setHidden(INTERFACE, comp + k, !active);
		ps.sendString(INTERFACE, comp + SEGMENT_LABEL, (active ? GOLD : TAN) + label + END);
	}

	private static String format(Stat stat, long value) {
		if (stat == Stat.DONATED)
			return "$" + String.format(Locale.ROOT, "%,d", value);
		if (stat == Stat.PVM_LOOT || stat == Stat.SKILL_XP) {
			if (value >= 1_000_000_000L)
				return String.format(Locale.ROOT, "%.1fB", value / 1e9);
			if (value >= 1_000_000L)
				return String.format(Locale.ROOT, "%.1fM", value / 1e6);
			if (value >= 100_000L)
				return (value / 1_000) + "K";
		}
		return String.format(Locale.ROOT, "%,d", value);
	}

	/* ---------------- storage ---------------- */

	private static File file() {
		return new File(ServerWrapper.dataFolder, "runtime/saves/scoreboard.json");
	}

	private static void load() {
		File file = file();
		if (!file.exists())
			return;
		try (FileReader reader = new FileReader(file)) {
			SaveState loaded = JsonUtils.GSON.fromJson(reader, SaveState.class);
			if (loaded != null) {
				if (loaded.daily == null) loaded.daily = new Board();
				if (loaded.weekly == null) loaded.weekly = new Board();
				for (Board b : new Board[]{loaded.daily, loaded.weekly}) {
					if (b.users == null) b.users = new HashMap<>();
				}
				if (loaded.names == null) loaded.names = new HashMap<>();
				if (loaded.pendingPkp == null) loaded.pendingPkp = new HashMap<>();
				state = loaded;
			}
		} catch (Exception e) {
			ServerWrapper.logError("Unable to load the scoreboard", e);
		}
	}

	private static void save() {
		dirty = false;
		File file = file();
		file.getParentFile().mkdirs();
		try (FileWriter writer = new FileWriter(file)) {
			JsonUtils.GSON.toJson(state, writer);
		} catch (Exception e) {
			ServerWrapper.logError("Unable to save the scoreboard", e);
		}
	}
}
