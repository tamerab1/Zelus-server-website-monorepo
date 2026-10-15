package io.ruin.model.activities.tournament;

import io.ruin.api.utils.JsonUtils;
import io.ruin.api.utils.ServerWrapper;
import io.ruin.model.World;
import io.ruin.model.entity.player.Player;
import io.ruin.model.entity.shared.listeners.LoginListener;
import io.ruin.model.inter.InterfaceHandler;
import io.ruin.model.inter.ToplevelComponent;
import io.ruin.model.inter.actions.SimpleAction;
import io.ruin.model.map.Position;
import io.ruin.network.PacketSender;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The all-time Pit Championship ladder (2026-10-15): lifetime Tournament Points earned (the rank), wins (1st place
 * finishes) and matches played per player, saved to runtime/saves/pit_ladder.json.
 * <ul>
 * <li>Interface 1169 (.dev/cache-restore-tool BuildChampionLadderInterface), opened by the Championship Board's "View":
 * the top 20 in two columns, the viewer's own rank, the next-tournament countdown and a button to the Tournament Hub.</li>
 * <li>The live board: the top 5 names and points are written into interface 1170 (never opened, so the client keeps the
 * text) for players near the Pit; the client's Championship Board plugin draws them onto the board in the world.</li>
 * </ul>
 * Points are the lifetime total earned, not the spendable balance. A player already on the ladder before it existed is
 * added at their next login with their wins, and their current balance as a lower bound for points.
 */
public final class ChampionLadder {

	public static final int INTERFACE = 1169, DATA_INTERFACE = 1170;
	private static final int CLOSE = 2, TITLE = 3, SUBTITLE = 4;
	private static final int[] FIRST_ROW = {17, 99};
	private static final int ROW_STRIDE = 7, RANK = 1, NAME = 2, POINTS = 3, WINS = 4, PLAYED = 5, ROWS_PER_PANEL = 10;
	private static final int PERSONAL = 172, COUNTDOWN = 173, HUB_BUTTON = 174;
	private static final int LIVE_ROWS = 5, DATA_POINTS = 5;
	/** Players this close to the Pit lobby get the live top 5 for the board. */
	private static final int LIVE_RANGE = 40;

	private static final String END = "</col>", GOLD = "<col=ffc35a>", WHITE = "<col=ffffff>", GREEN = "<col=40ff40>",
		GREY = "<col=9a9a9a>";
	private static final String[] MEDAL = {"<col=ffd700>", "<col=e0e0e0>", "<col=cd7f32>"};
	private static final String MUTED = "<col=808080>", SEP = "<col=5a4d3a>  |  </col>";

	/** One ladder entry (a plain class so Gson fills it). */
	static final class Entry {
		String name;
		long points;
		int wins, matches;
	}

	private static Map<String, Entry> entries = new HashMap<>();
	private static List<Entry> ranked = new ArrayList<>();
	private static boolean dirty;
	/** Bumped on every change; each player near the Pit is re-sent the live rows when theirs is behind. */
	private static int version;
	private static final Map<Player, Integer> sentVersion = new WeakHashMap<>();

	private ChampionLadder() {
	}

	public static void register() {
		load();
		InterfaceHandler.register(INTERFACE, h -> {
			h.actions[CLOSE] = (SimpleAction) Player::closeInterfaces;
			h.actions[HUB_BUTTON] = (SimpleAction) TournamentHub::open;
		});
		LoginListener.register(ChampionLadder::seed);
		World.startEvent(e -> {
			int ticks = 0;
			while (true) {
				e.delay(10);
				if (++ticks % 6 == 0 && dirty)
					save();
				for (Player p : World.players()) {
					if (!nearPit(p))
						continue;
					Integer sent = sentVersion.get(p);
					if (sent == null || sent != version)
						sendLive(p);
					if (p.isVisibleInterface(INTERFACE))
						p.getPacketSender().sendString(INTERFACE, COUNTDOWN, TournamentHub.statusLine());
				}
			}
		});
	}

	/* ---------------- recording ---------------- */

	static void recordPoints(Player player, int points) {
		if (points <= 0)
			return;
		entry(player).points += points;
		changed();
	}

	static void recordWin(Player player) {
		entry(player).wins++;
		changed();
	}

	static void recordMatch(Player player) {
		entry(player).matches++;
		changed();
	}

	private static Entry entry(Player player) {
		Entry e = entries.computeIfAbsent(key(player.getName()), k -> new Entry());
		e.name = player.getName();
		return e;
	}

	/** Puts players who already played before the ladder existed on it, from what their save has. */
	private static void seed(Player player) {
		if (entries.containsKey(key(player.getName())) || (player.tournamentWins <= 0 && player.tournamentPoints <= 0))
			return;
		Entry e = entry(player);
		e.wins = player.tournamentWins;
		e.points = player.tournamentPoints;
		e.matches = player.tournamentWins;
		changed();
	}

	private static void changed() {
		dirty = true;
		version++;
		ranked = new ArrayList<>(entries.values());
		ranked.sort(Comparator.comparingLong((Entry e) -> -e.points).thenComparingInt(e -> -e.wins)
			.thenComparingInt(e -> e.matches).thenComparing(e -> e.name.toLowerCase(Locale.ROOT)));
	}

	private static String key(String name) {
		return name.toLowerCase(Locale.ROOT).replace(' ', '_');
	}

	/**
	 * Staff: ::pitladder name points wins played  (sets an entry; underscores for spaces), ::pitladder remove name.
	 * Fails loud on anything it can't parse instead of guessing.
	 */
	public static String staffCommand(String[] args) {
		if (args == null)
			args = new String[0];
		if (args.length == 2 && args[0].equalsIgnoreCase("remove")) {
			Entry removed = entries.remove(key(args[1].replace('_', ' ')));
			if (removed == null)
				return "No ladder entry named " + args[1].replace('_', ' ') + ".";
			changed();
			return "Removed " + removed.name + " from the Pit Championship ladder.";
		}
		if (args.length != 4)
			return "Usage: ::pitladder name points wins played   |   ::pitladder remove name   (underscores for spaces)";
		long points;
		int wins, played;
		try {
			points = Long.parseLong(args[1]);
			wins = Integer.parseInt(args[2]);
			played = Integer.parseInt(args[3]);
		} catch (NumberFormatException e) {
			return "Points, wins and played must be whole numbers.";
		}
		if (points < 0 || wins < 0 || played < 0 || wins > played)
			return "Values must be 0 or more, and wins can't exceed matches played.";
		String name = args[0].replace('_', ' ');
		Entry e = entries.computeIfAbsent(key(name), k -> new Entry());
		if (e.name == null)
			e.name = name;
		e.points = points;
		e.wins = wins;
		e.matches = played;
		changed();
		return "Ladder: " + e.name + " now has " + points + " pts, " + wins + " wins, " + played + " played (rank #"
			+ (ranked.indexOf(e) + 1) + ").";
	}

	/* ---------------- interface ---------------- */

	public static void open(Player player) {
		player.openInterface(ToplevelComponent.MAINMODAL, INTERFACE);
		PacketSender ps = player.getPacketSender();
		ps.sendString(INTERFACE, TITLE, "<col=ff981f>Pit Championship Ladder" + END);
		ps.sendString(INTERFACE, SUBTITLE, "Ranked by lifetime Tournament Points  -  Wins are 1st place finishes");
		for (int[] h : new int[][]{{14, 15, 16}, {96, 97, 98}}) { // RivalsLeaderboard reuses this window with other labels
			ps.sendString(INTERFACE, h[0], "Pts");
			ps.sendString(INTERFACE, h[1], "Wins");
			ps.sendString(INTERFACE, h[2], "Played");
		}
		ps.sendString(INTERFACE, HUB_BUTTON + 2, ""); // the centred footer (Division Rivals only)
		ps.setHidden(INTERFACE, HUB_BUTTON, false);
		ps.setHidden(INTERFACE, HUB_BUTTON + 1, false);
		for (int i = 0; i < ROWS_PER_PANEL * 2; i++) {
			int row = FIRST_ROW[i / ROWS_PER_PANEL] + (i % ROWS_PER_PANEL) * ROW_STRIDE;
			if (i >= ranked.size()) {
				ps.sendString(INTERFACE, row + RANK, GREY + (i + 1) + END);
				ps.sendString(INTERFACE, row + NAME, GREY + "-" + END);
				for (int k : new int[]{POINTS, WINS, PLAYED})
					ps.sendString(INTERFACE, row + k, "");
				continue;
			}
			Entry e = ranked.get(i);
			boolean self = e.name.equalsIgnoreCase(player.getName());
			ps.sendString(INTERFACE, row + RANK, (i < 3 ? MEDAL[i] : GOLD) + (i + 1) + END);
			ps.sendString(INTERFACE, row + NAME, (self ? GREEN : i < 3 ? MEDAL[i] : WHITE) + e.name + END);
			ps.sendString(INTERFACE, row + POINTS, String.valueOf(e.points));
			ps.sendString(INTERFACE, row + WINS, String.valueOf(e.wins));
			ps.sendString(INTERFACE, row + PLAYED, String.valueOf(e.matches));
		}
		ps.sendString(INTERFACE, PERSONAL, personal(player));
		ps.sendString(INTERFACE, COUNTDOWN, TournamentHub.statusLine());
	}

	private static String personal(Player player) {
		for (int i = 0; i < ranked.size(); i++) {
			Entry e = ranked.get(i);
			if (e.name.equalsIgnoreCase(player.getName()))
				return GOLD + "Your Rank:" + END + " " + WHITE + "#" + (i + 1) + END + MUTED + " of " + END + WHITE + ranked.size() + END
					+ SEP + GOLD + "Points:" + END + " " + "<col=00ff00>" + e.points + END
					+ SEP + GOLD + "Wins:" + END + " " + WHITE + e.wins + END
					+ SEP + GOLD + "Played:" + END + " " + WHITE + e.matches + END;
		}
		return GREY + "You are not ranked yet - enter a Pit Championship to join the ladder." + END;
	}

	/* ---------------- the live board ---------------- */

	private static boolean nearPit(Player player) {
		Position at = player.getPosition();
		Position pit = TournamentHandler.SIGNUP_POSITION;
		return at.getZ() == pit.getZ() && Math.abs(at.getX() - pit.getX()) <= LIVE_RANGE && Math.abs(at.getY() - pit.getY()) <= LIVE_RANGE;
	}

	private static void sendLive(Player player) {
		PacketSender ps = player.getPacketSender();
		for (int i = 0; i < LIVE_ROWS; i++) {
			Entry e = i < ranked.size() ? ranked.get(i) : null;
			ps.sendString(DATA_INTERFACE, i, e == null ? "" : e.name);
			ps.sendString(DATA_INTERFACE, DATA_POINTS + i, e == null ? "" : String.valueOf(e.points));
		}
		sentVersion.put(player, version);
	}

	/* ---------------- saving ---------------- */

	private static File file() {
		return new File(ServerWrapper.dataFolder, "runtime/saves/pit_ladder.json");
	}

	private static void load() {
		File file = file();
		if (file.exists()) {
			try (FileReader reader = new FileReader(file)) {
				Entry[] loaded = JsonUtils.GSON.fromJson(reader, Entry[].class);
				if (loaded != null)
					for (Entry e : loaded)
						if (e != null && e.name != null)
							entries.put(key(e.name), e);
			} catch (Exception e) {
				ServerWrapper.logError("Unable to load the Pit Championship ladder", e);
			}
		}
		changed();
		dirty = false;
	}

	private static void save() {
		dirty = false;
		File file = file();
		file.getParentFile().mkdirs();
		try (FileWriter writer = new FileWriter(file)) {
			JsonUtils.GSON.toJson(ranked.toArray(new Entry[0]), writer);
		} catch (Exception e) {
			ServerWrapper.logError("Unable to save the Pit Championship ladder", e);
		}
	}
}
