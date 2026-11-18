package io.ruin.model.content.welcome;

import com.google.gson.reflect.TypeToken;
import io.ruin.api.utils.JsonUtils;
import io.ruin.api.utils.NumberUtils;
import io.ruin.api.utils.ServerWrapper;
import io.ruin.model.World;
import io.ruin.model.activities.dailytasks.DailyTasks;
import io.ruin.model.activities.dailytasks.DailyTasksInterface;
import io.ruin.model.entity.player.Player;
import io.ruin.model.entity.player.SecurityPin;
import io.ruin.model.entity.shared.listeners.LoginListener;
import io.ruin.model.inter.InterfaceHandler;
import io.ruin.model.inter.ToplevelComponent;
import io.ruin.model.inter.actions.SimpleAction;
import io.ruin.network.PacketSender;

import java.io.File;
import java.io.FileReader;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

/**
 * The Karn welcome screen (user 2026-11-15: "Karn crimson and gold, not too dark, show every login"; interface 1185
 * from the user's mock-up, .dev BuildKarnHub + cache stage 42). Opens on every login (not for brand-new players, during
 * the tutorial or while the security PIN holds them):
 * <ul>
 * <li>Current Events: the global / vote boss, the Hotspot and any double-rate events (View opens the Server dashboard);</li>
 * <li>Daily Tasks: three of today's tasks with their progress (Open = ::dailies);</li>
 * <li>Discord &amp; Voting: the invite and the vote page;</li>
 * <li>the Noticeboard: News and Updates from data/noticeboard.json (reloaded when it changes), Feed = the latest world
 * news (rare drops, 99s, killstreaks: {@link io.ruin.services.LiveFeed#push}); 3 posts a page, Read shows a whole post;</li>
 * <li>Enter World closes it.</li>
 * </ul>
 * ::welcome reopens it.
 */
public final class WelcomeScreen {

	private WelcomeScreen() {
	}

	public static final int INTERFACE = 1185;
	private static final int CLOSE = 5, SUBTITLE = 7, ONLINE = 8, EVENTS_BUTTON = 10, EVENTS_TEXT = 12, DAILY_BUTTON = 14,
			FIRST_TASK = 16, TASK_STRIDE = 22, DISCORD = 83, VOTE = 85, FIRST_TAB = 88, TAB_STRIDE = 3, FIRST_POST = 97,
			POST_STRIDE = 7, PREV = 118, PAGE = 119, NEXT = 120, READ_MORE = 121, ENTER = 123, PER_PAGE = 3;
	private static final String TAB_KEY = "welcome_tab", PAGE_KEY = "welcome_page";
	private static final String DISCORD_URL = "https://discord.gg/sVrcatBdyG";
	private static final String GREEN = "<col=2c7a34>", GREY = "<col=6a5a4c>", RED = "<col=9e1b24>", END = "</col>";

	// ------------------------------------------------------------------ posts

	public static final class Post {
		public String title, date, body;

		Post() {
		}

		Post(String title, String date, String body) {
			this.title = title;
			this.date = date;
			this.body = body;
		}
	}

	private static Map<String, List<Post>> board = Map.of();
	private static long loadedModified = -1;
	private static final Deque<Post> FEED = new ArrayDeque<>();
	private static final int FEED_SIZE = 30;

	private static List<Post> posts(int tab) {
		if (tab == 2)
			synchronized (FEED) {
				return new ArrayList<>(FEED);
			}
		File file = new File(ServerWrapper.dataFolder, "noticeboard.json");
		long modified = file.exists() ? file.lastModified() : 0;
		if (modified != loadedModified) {
			loadedModified = modified;
			Map<String, List<Post>> loaded = Map.of();
			if (file.exists()) {
				// read the two arrays only: the file's "_comment" string made a whole-map parse fail (an empty board)
				try (FileReader reader = new FileReader(file)) {
					com.google.gson.JsonObject root = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
					java.util.Map<String, List<Post>> read = new java.util.HashMap<>();
					for (String key : new String[]{"news", "updates"})
						if (root.has(key) && root.get(key).isJsonArray())
							read.put(key, JsonUtils.GSON.fromJson(root.get(key), new TypeToken<List<Post>>() {}.getType()));
					loaded = read;
				} catch (Exception e) {
					ServerWrapper.logError("Unable to load noticeboard.json", e);
				}
			}
			board = loaded;
		}
		return board.getOrDefault(tab == 0 ? "news" : "updates", List.of());
	}

	/** a world news line (LiveFeed.push): kept for the Feed tab, newest first */
	public static void feed(String type, String text) {
		if (text == null || text.isBlank())
			return;
		String title = switch (type == null ? "" : type) {
			case "level_up" -> "Level up";
			case "killstreak" -> "Killstreak";
			case "tournament_win" -> "Tournament win";
			case "pvp_kill" -> "PvP kill";
			case "bounty_claim" -> "Bounty claimed";
			default -> "Rare drop";
		};
		String time = LocalTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("HH:mm"));
		synchronized (FEED) {
			FEED.addFirst(new Post(title, time, text));
			while (FEED.size() > FEED_SIZE)
				FEED.removeLast();
		}
	}

	// ------------------------------------------------------------------ registration

	public static void register() {
		InterfaceHandler.register(INTERFACE, h -> {
			h.actions[CLOSE] = (SimpleAction) WelcomeScreen::close;
			h.actions[ENTER] = (SimpleAction) WelcomeScreen::close;
			h.actions[EVENTS_BUTTON] = (SimpleAction) p -> {
				p.closeInterfaces();
				io.ruin.model.inter.questtab.dashboard.QuestDashboards.show(p, io.ruin.model.inter.questtab.dashboard.DashboardLayout.SERVER);
			};
			h.actions[DAILY_BUTTON] = (SimpleAction) io.ruin.model.activities.dailytasks.DailyTasksHub::open;
			h.actions[DISCORD] = (SimpleAction) p -> p.openUrl(World.type.getWorldName() + " Discord", DISCORD_URL);
			h.actions[VOTE] = (SimpleAction) p -> p.openUrl(World.type.getWorldName() + " Vote",
					World.type.getWebsiteUrl() + "/vote/" + p.getName().replace(" ", "-"));
			h.actions[READ_MORE] = (SimpleAction) p -> p.openUrl(World.type.getWorldName() + " News", World.type.getWebsiteUrl() + "/news");
			h.actions[PREV] = (SimpleAction) p -> page(p, -1);
			h.actions[NEXT] = (SimpleAction) p -> page(p, 1);
			for (int t = 0; t < 3; t++) {
				int tab = t;
				h.actions[FIRST_TAB + t * TAB_STRIDE] = (SimpleAction) p -> {
					p.set(TAB_KEY, tab);
					p.set(PAGE_KEY, 0);
					sendBoard(p);
				};
			}
			for (int r = 0; r < PER_PAGE; r++) {
				int row = r;
				h.actions[FIRST_POST + r * POST_STRIDE + 6] = (SimpleAction) p -> read(p, row);
			}
		});
		LoginListener.register(p -> p.addEvent(e -> {
			e.delay(2); // after the login's own windows (starter, PIN hold)
			if (!p.isOnline() || p.newPlayer || p.inTutorial || SecurityPin.isHeld(p) || p.isVisibleInterface(INTERFACE))
				return;
			open(p);
		}));
	}

	/**
	 * Closes the welcome screen, then shows the Daily Login Streak if today's reward is still unclaimed: both open on
	 * login in the same modal slot, and the welcome screen (2 ticks later) used to replace the streak for good.
	 */
	private static void close(Player p) {
		p.closeInterfaces();
		if (p.canClaimLoginReward && !p.claimedLoginToday)
			p.getDailyLogin().open();
	}

	public static void open(Player p) {
		p.set(TAB_KEY, 0);
		p.set(PAGE_KEY, 0);
		p.openInterface(ToplevelComponent.MAINMODAL, INTERFACE);
		PacketSender ps = p.getPacketSender();
		ps.sendString(INTERFACE, SUBTITLE, "Welcome back, " + p.getName() + ".");
		ps.sendString(INTERFACE, ONLINE, "Online: " + NumberUtils.formatNumber(World.playerCount()));
		ps.sendString(INTERFACE, EVENTS_TEXT, events());
		sendTasks(p);
		sendBoard(p);
	}

	private static String plain(String s) {
		return s == null ? "" : s.replaceAll("<[^>]*>", "").trim();
	}

	private static String events() {
		List<String> lines = new ArrayList<>();
		lines.add("Global boss: " + plain(io.ruin.model.activities.bosses.globalboss.GlobalBossHandler.status()));
		lines.add("Vote boss: " + plain(io.ruin.model.activities.VoteBossHandler.status()));
		List<String> bonus = new ArrayList<>();
		if (World.doubleExpActive)
			bonus.add("Double XP");
		if (World.doubleSlayer)
			bonus.add("Double Slayer");
		if (World.doublePkp)
			bonus.add("Double PKP");
		lines.add(bonus.isEmpty() ? "Hotspot: " + io.ruin.model.activities.wilderness.Hotspot.ACTIVE.name
				: RED + String.join(", ", bonus) + " active!" + END);
		return String.join("<br>", lines);
	}

	private static void sendTasks(Player p) {
		PacketSender ps = p.getPacketSender();
		// unfinished tasks first, then the finished ones
		List<Integer> order = new ArrayList<>();
		DailyTasks[] tasks = p.currentDailyTasks;
		for (int pass = 0; pass < 2; pass++)
			for (int i = 0; tasks != null && i < tasks.length; i++)
				if (tasks[i] != null && (p.currentDailyTaskAmounts[i] > 0) == (pass == 0))
					order.add(i);
		for (int r = 0; r < 3; r++) {
			int base = FIRST_TASK + r * TASK_STRIDE;
			if (r >= order.size()) {
				ps.sendString(INTERFACE, base, r == 0 ? GREY + "No tasks today." + END : "");
				ps.sendString(INTERFACE, base + 1, "");
				for (int s = 0; s < 20; s++)
					ps.setHidden(INTERFACE, base + 2 + s, true);
				continue;
			}
			int i = order.get(r);
			int total = Math.max(1, p.startingDailyTaskAmounts[i]);
			int done = Math.max(0, total - p.currentDailyTaskAmounts[i]);
			String name = tasks[i].getName();
			ps.sendString(INTERFACE, base, name.length() > 24 ? name.substring(0, 22).trim() + ".." : name);
			ps.sendString(INTERFACE, base + 1, done >= total ? GREEN + "Done" + END : done + " / " + total);
			int fill = (int) Math.floor(20.0 * done / total);
			for (int s = 0; s < 20; s++)
				ps.setHidden(INTERFACE, base + 2 + s, s >= fill);
		}
	}

	private static void page(Player p, int delta) {
		int count = posts(p.<Integer>get(TAB_KEY, 0)).size();
		int pages = Math.max(1, (count + PER_PAGE - 1) / PER_PAGE);
		p.set(PAGE_KEY, Math.max(0, Math.min(pages - 1, p.<Integer>get(PAGE_KEY, 0) + delta)));
		sendBoard(p);
	}

	/** a body cut to two p12 lines of the 204px column (~30 characters a line) */
	private static String twoLines(String text) {
		String t = plain(text).replaceAll("\\s+", " ");
		StringBuilder out = new StringBuilder();
		int line = 0, len = 0;
		for (String w : t.split(" ")) {
			if (len > 0 && len + 1 + w.length() > 30) {
				if (++line == 2) {
					out.append("..");
					return out.toString();
				}
				out.append("<br>");
				len = 0;
			} else if (len > 0) {
				out.append(' ');
				len++;
			}
			out.append(w);
			len += w.length();
		}
		return out.toString();
	}

	private static void sendBoard(Player p) {
		if (!p.isVisibleInterface(INTERFACE))
			return;
		PacketSender ps = p.getPacketSender();
		int tab = p.<Integer>get(TAB_KEY, 0), page = p.<Integer>get(PAGE_KEY, 0);
		for (int t = 0; t < 3; t++)
			ps.setHidden(INTERFACE, FIRST_TAB + t * TAB_STRIDE + 1, t != tab);
		List<Post> list = posts(tab);
		int pages = Math.max(1, (list.size() + PER_PAGE - 1) / PER_PAGE);
		ps.sendString(INTERFACE, PAGE, "Page " + (page + 1) + " of " + pages);
		for (int r = 0; r < PER_PAGE; r++) {
			int base = FIRST_POST + r * POST_STRIDE, i = page * PER_PAGE + r;
			Post post = i < list.size() ? list.get(i) : null;
			for (int icon = 0; icon < 3; icon++)
				ps.setHidden(INTERFACE, base + icon, post == null || icon != tab);
			if (post == null) {
				ps.sendString(INTERFACE, base + 3, r == 0 ? (tab == 2 ? "Nothing yet" : "No posts yet") : "");
				ps.sendString(INTERFACE, base + 4, "");
				ps.sendString(INTERFACE, base + 5, r == 0 && tab == 2 ? "Rare drops, 99s and killstreaks show up here." : "");
				continue;
			}
			String title = plain(post.title);
			ps.sendString(INTERFACE, base + 3, title.length() > 24 ? title.substring(0, 22).trim() + ".." : title);
			ps.sendString(INTERFACE, base + 4, post.date == null ? "" : post.date);
			ps.sendString(INTERFACE, base + 5, twoLines(post.body));
		}
	}

	private static void read(Player p, int row) {
		int tab = p.<Integer>get(TAB_KEY, 0), i = p.<Integer>get(PAGE_KEY, 0) * PER_PAGE + row;
		List<Post> list = posts(tab);
		if (i >= list.size())
			return;
		Post post = list.get(i);
		List<String> lines = new ArrayList<>();
		if (post.date != null && !post.date.isEmpty())
			lines.add("<col=7f0000>" + post.date + "</col>");
		lines.add("");
		StringBuilder line = new StringBuilder();
		for (String w : plain(post.body).split("\\s+")) {
			if (line.length() + w.length() + 1 > 60) {
				lines.add(line.toString());
				line.setLength(0);
			}
			if (line.length() > 0)
				line.append(' ');
			line.append(w);
		}
		if (line.length() > 0)
			lines.add(line.toString());
		p.sendScroll(plain(post.title), lines.toArray(new String[0]));
	}
}
