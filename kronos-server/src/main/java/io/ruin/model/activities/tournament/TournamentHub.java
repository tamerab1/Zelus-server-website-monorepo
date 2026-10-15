package io.ruin.model.activities.tournament;

import io.ruin.cache.ItemID;
import io.ruin.cache.ObjType;
import io.ruin.model.ScrollbarClientScript;
import io.ruin.model.World;
import io.ruin.model.activities.tournament.TournamentHandler.Reward;
import io.ruin.model.activities.tournament.TournamentHandler.State;
import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.InterfaceHandler;
import io.ruin.model.inter.ToplevelComponent;
import io.ruin.model.inter.actions.SimpleAction;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * The Pit Championships hub (interface 1148, built by .dev/cache-restore-tool/src/BuildTournamentInterface.java):
 * a live status line, Tournament / Schedule & rules / Hall of Fame tabs, a "Your record" box, and on the Tournament
 * tab the format banner, entry rules, prize showcase and the Register / Teleport buttons. Opened by the Pit
 * Championship Board, the Champion's Reward Trophy (Hall of Fame) and ::pit.
 */
public final class TournamentHub {

	private static final int INTERFACE = 1148;
	private static final int CLOSE = 2, TITLE = 3, STATUS = 4;
	private static final int MAIN_LAYER = 9, HEADING = 10, BADGE = 11, INFO_1 = 12, INFO_2 = 13;
	private static final int FIRST_REQUIREMENT = 16, REQUIREMENT_ROWS = 5;
	private static final int FIRST_PRIZE_MODEL = 23, FIRST_PRIZE_TEXT = 26;
	private static final int REGISTER_BUTTON = 35, REGISTER_LABEL = 36, TELEPORT_BUTTON = 37, TELEPORT_LABEL = 38;
	private static final int LIST_LAYER = 39, SCROLL = 40, SCROLLBAR = 41, FIRST_LIST_ROW = 42, LIST_ROWS = 60;
	private static final int[] TAB_BUTTONS = {102, 104, 106}, TAB_LABELS = {103, 105, 107};
	private static final int FIRST_RECORD_ROW = 112, RECORD_ROWS = 7;
	private static final int PREVIOUS_FORMAT = 119, NEXT_FORMAT = 121;
	private static final int SCROLL_HEIGHT = 240, ROW_HEIGHT = 12;

	public static final int TOURNAMENT = 0, SCHEDULE = 1, HALL_OF_FAME = 2;
	private static final String[] TAB_NAMES = {"Tournament", "Schedule & rules", "Hall of Fame"};

	/** Prize showcase: the item shown for each of the three podium rewards. */
	/** The prize sprite slot is 22px tall (an inventory icon is 32): zoom the model out to fit inside it. */
	private static final double PRIZE_ZOOM_SCALE = 1.45;

	private static final Reward[] PODIUM = {TournamentHandler.CHAMPION, TournamentHandler.RUNNER_UP, TournamentHandler.SEMIFINALIST};

	private static final String TAB_KEY = "pit_hub_tab", LOOP_KEY = "pit_hub_loop", BROWSE_KEY = "pit_hub_format";

	private static final String GOLD = "<col=ffc35a>", GREY = "<col=9a9a9a>", WHITE = "<col=ffffff>",
		GREEN = "<col=50e050>", RED = "<col=ff5050>", ORANGE = "<col=ff981f>", END = "</col>";
	private static final String BANNER_LABEL = "<col=ff981f>", BANNER_GREEN = "<col=00ff00>", YELLOW = "<col=ffff00>";
	private static final String BULLET = GOLD + "»" + END + " ";

	private TournamentHub() {
	}

	static void register() {
		InterfaceHandler.register(INTERFACE, h -> {
			h.actions[CLOSE] = (SimpleAction) player -> player.closeInterface(ToplevelComponent.MAINMODAL);
			for (int t = 0; t < TAB_BUTTONS.length; t++) {
				int tab = t;
				h.actions[TAB_BUTTONS[t]] = (SimpleAction) player -> show(player, tab);
			}
			h.actions[REGISTER_BUTTON] = (SimpleAction) player -> {
				if (!TournamentHandler.isQueued(player) && browsed(player) != TournamentHandler.format()) {
					player.sendMessage(browsed(player).name + " isn't open yet: registration opens " + TournamentHandler.REGISTRATION_MINUTES
						+ " minutes before it starts.");
					return;
				}
				if (TournamentHandler.isQueued(player))
					TournamentHandler.leave(player);
				else
					TournamentHandler.join(player);
				show(player, TOURNAMENT);
			};
			h.actions[PREVIOUS_FORMAT] = (SimpleAction) player -> browse(player, -1);
			h.actions[NEXT_FORMAT] = (SimpleAction) player -> browse(player, 1);
			h.actions[TELEPORT_BUTTON] = (SimpleAction) player -> {
				player.closeInterface(ToplevelComponent.MAINMODAL);
				TournamentHandler.teleportToLobby(player);
			};
		});
	}

	public static void open(Player player) {
		open(player, TOURNAMENT);
	}

	public static void open(Player player, int tab) {
		player.set(BROWSE_KEY, TournamentHandler.format().ordinal());
		player.openInterface(ToplevelComponent.MAINMODAL, INTERFACE);
		player.getPacketSender().sendString(INTERFACE, TITLE, "THE PIT CHAMPIONSHIPS");
		show(player, tab);
		startLoop(player);
	}

	/** The format shown on the Tournament tab (the arrows browse the rotation). */
	private static TournamentFormat browsed(Player player) {
		return TournamentFormat.VALUES[player.<Integer>get(BROWSE_KEY, TournamentHandler.format().ordinal())];
	}

	private static void browse(Player player, int step) {
		int n = TournamentFormat.VALUES.length;
		player.set(BROWSE_KEY, (browsed(player).ordinal() + step + n) % n);
		show(player, TOURNAMENT);
	}

	/** Re-renders the hub for everyone looking at it (registration / bracket changes). */
	static void refreshViewers() {
		for (Player p : World.players()) {
			if (p != null && p.isOnline() && p.isVisibleInterface(INTERFACE))
				show(p, p.<Integer>get(TAB_KEY, TOURNAMENT));
		}
	}

	/** Ticks the status line every tick and the Tournament tab (countdowns) every 5 ticks while the hub is open. */
	private static void startLoop(Player player) {
		int token = player.<Integer>get(LOOP_KEY, 0) + 1;
		player.set(LOOP_KEY, token);
		player.addEvent(e -> {
			int ticks = 0;
			while (true) {
				e.delay(1);
				if (!player.isOnline() || !player.isVisibleInterface(INTERFACE) || player.<Integer>get(LOOP_KEY, 0) != token)
					return;
				player.getPacketSender().sendString(INTERFACE, STATUS, statusLine());
				if (++ticks % 5 == 0 && player.<Integer>get(TAB_KEY, TOURNAMENT) == TOURNAMENT)
					showTournament(player);
			}
		});
	}

	private static void show(Player player, int tab) {
		player.set(TAB_KEY, tab);
		var ps = player.getPacketSender();
		for (int t = 0; t < TAB_LABELS.length; t++)
			ps.sendString(INTERFACE, TAB_LABELS[t], t == tab ? WHITE + TAB_NAMES[t] + END : TAB_NAMES[t]);
		ps.sendString(INTERFACE, STATUS, statusLine());
		ps.setHidden(INTERFACE, MAIN_LAYER, tab != TOURNAMENT);
		ps.setHidden(INTERFACE, LIST_LAYER, tab == TOURNAMENT);

		if (tab == TOURNAMENT) {
			showTournament(player);
			showPrizes(player);
		} else {
			List<String> rows = tab == SCHEDULE ? schedule() : hallOfFame();
			for (int r = 0; r < LIST_ROWS; r++)
				ps.sendString(INTERFACE, FIRST_LIST_ROW + r, r < rows.size() ? rows.get(r) : "");
			updateScrollbar(player, Math.min(rows.size(), LIST_ROWS));
		}

		List<String> record = record(player);
		for (int r = 0; r < RECORD_ROWS; r++)
			ps.sendString(INTERFACE, FIRST_RECORD_ROW + r, r < record.size() ? record.get(r) : "");
	}

	/* ---------------- Tournament tab ---------------- */

	private static void showTournament(Player player) {
		var ps = player.getPacketSender();
		TournamentFormat format = browsed(player);
		boolean current = format == TournamentHandler.format();
		State state = current ? TournamentHandler.state() : State.SCHEDULED;
		boolean full = TournamentHandler.queueSize() >= TournamentHandler.bracketSize();

		ps.sendString(INTERFACE, HEADING, ORANGE + format.name + END);
		ps.sendString(INTERFACE, BADGE, switch (state) {
			case OPEN -> full ? ORANGE + "[ Bracket Full ]" + END : BANNER_GREEN + "[ Open for Entry ]" + END;
			case RUNNING -> RED + "[ In Progress ]" + END;
			default -> YELLOW + "[ Scheduled ]" + END;
		});
		switch (state) {
			case OPEN -> {
				ps.sendString(INTERFACE, INFO_1, "Style: " + WHITE + format.style + END);
				ps.sendString(INTERFACE, INFO_2, "Starts: " + WHITE + "in " + duration(TournamentHandler.millisUntilStart()) + END
					+ GREY + "  -  " + END + "Registered: " + WHITE + TournamentHandler.queueSize() + " / " + TournamentHandler.bracketSize() + END
					+ GREY + " (min " + TournamentHandler.minPlayers(TournamentHandler.format()) + ")" + END);
			}
			case RUNNING -> {
				ps.sendString(INTERFACE, INFO_1, "Round " + WHITE + TournamentHandler.round() + END + "  -  " + WHITE
					+ TournamentHandler.playersLeft() + END + " of " + TournamentHandler.entrants() + " players left");
				String match = TournamentHandler.currentMatch();
				ps.sendString(INTERFACE, INFO_2, match == null ? GREY + "Next match starting..." + END : "Now fighting: " + WHITE + match + END);
			}
			default -> {
				long until = current ? TournamentHandler.millisUntilStart()
					: TournamentHandler.nextStartOf(format) - System.currentTimeMillis();
				ps.sendString(INTERFACE, INFO_1, "Style: " + WHITE + format.style + END);
				ps.sendString(INTERFACE, INFO_2, "Starts: " + WHITE + "in " + duration(until) + END + (current
					? GREY + "  (sign-ups open in " + duration(TournamentHandler.millisUntilRegistration()) + ")" + END : ""));
			}
		}

		String[] rules = format.boxing ? new String[]{
			"Gloves only: other worn gear is taken off.",
			"No gloves? Pick a corner for free red/blue ones.",
			"No prayer, magic, food or drinks.",
			"Entry: " + GREEN + "Free" + END + "  -  needs 30+ minutes of playtime.",
			"Be in the pit lobby at the start. Logging out = forfeit."
		} : new String[]{
			"Own setup: worn gear over the caps is taken off.",
			"Level caps: " + WHITE + format.caps + END,
			"Weapons: " + WHITE + (format.weaponRule == null ? "anything you can wield" : format.weaponRule) + END,
			"Entry: " + GREEN + "Free" + END + "  -  needs 30+ minutes of playtime.",
			"Be in the pit lobby at the start. Logging out = forfeit."
		};
		for (int r = 0; r < REQUIREMENT_ROWS; r++)
			ps.sendString(INTERFACE, FIRST_REQUIREMENT + r, BULLET + rules[r]);

		String register;
		if (TournamentHandler.inBracket(player))
			register = GREY + "You're in the bracket" + END;
		else if (TournamentHandler.isQueued(player))
			register = RED + "Leave queue" + END;
		else if (!current)
			register = GREY + "Not open yet" + END;
		else if (state != State.OPEN)
			register = GREY + "Registration closed" + END;
		else if (full)
			register = GREY + "Bracket full" + END;
		else
			register = GREEN + "Register (Free Entry)" + END;
		ps.sendString(INTERFACE, REGISTER_LABEL, register);
		ps.sendString(INTERFACE, TELEPORT_LABEL, "Enter Lobby");
	}

	private static void showPrizes(Player player) {
		var ps = player.getPacketSender();
		for (int p = 0; p < PODIUM.length; p++) {
			Reward reward = PODIUM[p];
			// a stack of reward tokens standing in for the points
			int itemId = ItemID.REWARD_TOKEN, amount = reward.points;
			ObjType def = ObjType.get(itemId);
			ps.sendItem(INTERFACE, FIRST_PRIZE_MODEL + p, itemId, amount);
			ps.sendModelInformation(INTERFACE, FIRST_PRIZE_MODEL + p, (int) (def.zoom2d * PRIZE_ZOOM_SCALE), def.xan2d, def.yan2d);

			String line1 = reward.points + " Point" + (reward.points == 1 ? "" : "s");
			String line2 = reward == TournamentHandler.CHAMPION ? "+ Champion title" : "";
			ps.sendString(INTERFACE, FIRST_PRIZE_TEXT + 3 * p, reward.place);
			ps.sendString(INTERFACE, FIRST_PRIZE_TEXT + 3 * p + 1, line1);
			ps.sendString(INTERFACE, FIRST_PRIZE_TEXT + 3 * p + 2, line2);
		}
	}

	/* ---------------- Schedule & rules tab ---------------- */

	private static List<String> schedule() {
		List<String> r = new ArrayList<>();
		r.add(GOLD + "Upcoming rotation" + END);
		for (int k = 0; k < 6; k++) {
			TournamentFormat format = TournamentHandler.upcomingFormat(k);
			long start = TournamentHandler.upcomingStart(k);
			String when = start < 0 ? GREEN + "Now" + END : "in " + duration(start - System.currentTimeMillis());
			r.add(WHITE + format.name + END + GREY + "  -  " + END + when);
			r.add(GREY + "   " + format.style + END);
		}
		r.add("");
		r.add(GOLD + "How it works" + END);
		r.add(BULLET + "A tournament starts every " + TournamentHandler.INTERVAL_MINUTES + " minutes.");
		r.add(BULLET + "Registration opens " + TournamentHandler.REGISTRATION_MINUTES + " minutes before the start.");
		r.add(BULLET + "Up to " + TournamentHandler.BRACKET_SIZE + " players (Boxing " + TournamentHandler.BOXING_BRACKET_SIZE + "), single elimination.");
		r.add(BULLET + "An odd player out gets a bye to the next round.");
		r.add(BULLET + "Matches are 1v1 in the pit, one at a time.");
		r.add(BULLET + "Combat stats are set to the format's levels.");
		r.add("   Your real stats return when you're out.");
		r.add(BULLET + "Worn gear over the caps is taken off at the start.");
		r.add(BULLET + (TournamentMatch.TIME_LIMIT_TICKS * 6 / 600) + "-minute match limit: more hitpoints wins.");
		r.add(BULLET + "No teleporting out. Logging out = forfeit.");
		r.add("");
		r.add(GOLD + "Rewards" + END);
		for (Reward reward : PODIUM) {
			r.add(BULLET + WHITE + reward.place + END + ": " + reward.points + " Tournament Point" + (reward.points == 1 ? "" : "s"));
		}
		r.add("   " + GREY + "1st also unlocks the Champion title." + END);
		r.add(BULLET + "At least 30 minutes of playtime to enter.");
		r.add("");
		r.add(GOLD + "Formats" + END);
		for (TournamentFormat format : TournamentFormat.VALUES) {
			r.add(WHITE + format.name + END + ": " + format.style);
			if (format.weaponRule != null)
				r.add("   " + GREY + format.weaponRule + END);
		}
		return r;
	}

	/* ---------------- Hall of Fame tab ---------------- */

	private static List<String> hallOfFame() {
		List<String> r = new ArrayList<>();
		List<TournamentRecord> records = TournamentHandler.hallOfFame();
		if (records.isEmpty()) {
			r.add(GOLD + "Hall of Fame" + END);
			r.add("No champion has been crowned yet.");
			r.add(GREY + "Win a Pit Championship to be the first!" + END);
			return r;
		}
		SimpleDateFormat date = new SimpleDateFormat("yyyy-MM-dd HH:mm");
		for (int i = 0; i < records.size() && r.size() < LIST_ROWS; i++) {
			TournamentRecord rec = records.get(i);
			if (i > 0)
				r.add("");
			r.add(GOLD + date.format(new Date(rec.finishedAt)) + END + "  " + WHITE + rec.format + END + GREY + "  (" + rec.entrants + " players)" + END);
			r.add("Champion: " + ORANGE + rec.champion + END);
			if (rec.runnerUp != null)
				r.add("Runner-up: " + WHITE + rec.runnerUp + END);
			if (!rec.semifinalists.isEmpty())
				r.add("Semi-finalists: " + WHITE + String.join(", ", rec.semifinalists) + END);
			if (i == 0) { // full bracket for the latest tournament
				for (int round = 0; round < rec.rounds.size(); round++) {
					boolean last = round == rec.rounds.size() - 1;
					r.add(GREY + (last ? "Final" : "Round " + (round + 1)) + END);
					for (String result : rec.rounds.get(round))
						r.add("   " + result);
				}
			}
		}
		return r.size() > LIST_ROWS ? r.subList(0, LIST_ROWS) : r;
	}

	/* ---------------- shared ---------------- */

	private static List<String> record(Player player) {
		List<String> r = new ArrayList<>();
		r.add(GOLD + "Your record" + END);
		r.add("Championships: " + WHITE + player.tournamentWins + END);
		r.add("Tournament pts: " + WHITE + player.tournamentPoints + END);
		r.add("");
		r.add(GOLD + "Your status" + END);
		if (TournamentHandler.inBracket(player))
			r.add(ORANGE + "In the bracket" + END);
		else if (TournamentHandler.isQueued(player))
			r.add(GREEN + "Registered" + END);
		else
			r.add(GREY + "Not registered" + END);
		r.add(TournamentHandler.isQueued(player) && !TournamentHandler.inLobby(player)
			? RED + "Not in the lobby!" + END : "");
		return r;
	}

	static String statusLine() {
		String name = WHITE + TournamentHandler.format().name + END;
		return switch (TournamentHandler.state()) {
			case OPEN -> BANNER_LABEL + "Registration Open:" + END + " " + name + " " + BANNER_GREEN + "(Starts in "
				+ duration(TournamentHandler.millisUntilStart()) + ")" + END;
			case RUNNING -> BANNER_LABEL + "Live Now:" + END + " " + name + " " + RED + "(Round " + TournamentHandler.round() + ")" + END;
			default -> BANNER_LABEL + "Next Event:" + END + " " + name + " " + BANNER_GREEN + "(Starts in "
				+ duration(TournamentHandler.millisUntilStart()) + ")" + END;
		};
	}

	private static String duration(long ms) {
		long s = Math.max(0, ms / 1000);
		if (s >= 3600)
			return (s / 3600) + "h " + String.format("%02d", (s % 3600) / 60) + "m";
		return (s / 60) + "m " + String.format("%02d", s % 60) + "s";
	}

	/** Sizes the scroll area to the rows in use (clientscript 10532: count * 15 + height / 3). */
	private static void updateScrollbar(Player player, int rowsUsed) {
		int contentHeight = rowsUsed * ROW_HEIGHT + 4;
		int count = contentHeight <= SCROLL_HEIGHT ? 0 : (int) Math.ceil((contentHeight - SCROLL_HEIGHT / 3D) / 15D);
		ScrollbarClientScript.create()
			.interfaceId(INTERFACE)
			.containerId(SCROLL)
			.scrollbarChildId(SCROLLBAR)
			.childrenCount(count)
			.withDarkGraphics()
			.build()
			.send(player);
	}
}
