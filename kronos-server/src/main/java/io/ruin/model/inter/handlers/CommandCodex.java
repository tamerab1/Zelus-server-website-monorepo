package io.ruin.model.inter.handlers;

import io.ruin.model.ScrollbarClientScript;
import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.InterfaceHandler;
import io.ruin.model.inter.ToplevelComponent;
import io.ruin.model.inter.actions.SimpleAction;
import io.ruin.network.PacketSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Zelus Commands (interface 1177, built by .dev/cache-restore-tool BuildCommandCodexInterface), opened by ::commands /
 * ::help: a READ-ONLY guide to the general and utility commands. Nothing on it runs a command -- teleports belong to the
 * Teleport menu, so there are no teleport lists here (only the feature hubs ::soultree / ::sanctum / ::draco).
 * Four tabs and a search over command and description.
 * <p>
 * Every command listed was checked against its handler (2026-10-18). Not listed because they do not exist on the server:
 * ::refer (it is ::ref / ::referrals), ::bestiary (an item), ::toggles / ::filter / ::fps / ::grounditems (client
 * plugin settings, not server commands), ::wiki (the website has no /wiki page: it lands on the home page).
 */
public final class CommandCodex {
	public static final int INTERFACE = 1177;
	private static final int TITLE = 3, CLOSE = 2, SUBTITLE = 9, COUNT = 10, FIRST_TAB = 16, TAB_STRIDE = 4, TIP = 32,
		SEARCH = 33, CAT_TITLE = 40, CAT_BLURB = 41, SCROLL = 43, SCROLLBAR = 44, EMPTY = 45, FIRST_ROW = 46, ROW_STRIDE = 3,
		ROWS = 32, ROW_HEIGHT = 26, SCROLL_HEIGHT = 189;

	/** cmd = the syntax shown in the left column, desc = the right column. */
	private record Entry(String cmd, String desc) {}

	private record Category(String tab, String title, String blurb, List<Entry> entries) {}

	private static Entry e(String cmd, String desc) {
		return new Entry("::" + cmd, desc);
	}

	private static final List<Category> CATEGORIES = List.of(
		new Category("General & Account", "General & Account", "Help, voting, the store, referrals and your account.", List.of(
			e("help", "Opens this commands guide"),
			e("claim", "Claim a store purchase"),
			e("vote", "Open the voting page"),
			e("claimvote", "Claim your vote rewards"),
			e("store", "Open the online store"),
			e("deals", "Limited-time donator deals"),
			e("ref [name]", "Name the player who referred you"),
			e("referrals", "Your referral rewards"),
			e("discord", "Join the Zelus Discord"),
			e("rules", "The server rules (on our Discord)"),
			e("players", "See who is online"),
			e("staff", "See which staff are online"),
			e("yell [message]", "Message the whole server"),
			e("changepass [new]", "Change your password"),
			e("changesecuritypin", "Set a new 4-digit bank PIN"))),
		new Category("Features & Perks", "Features & Perks", "Soulbound weapons, upgrades, pets and your progress.", List.of(
			e("soulweapon", "Choose or recover your weapon"),
			e("soulweapon info", "Your weapon's tier and Soul XP"),
			e("soultree", "Teleport to the Reliquary of Souls"),
			e("auras", "Your bonds and their auras"),
			e("petperks", "Your combat pet perks"),
			e("upgrade", "Item and equipment upgrades"),
			e("capeforge", "Forge and upgrade capes"),
			e("presets", "Gear presets (opens your bank)"),
			e("loadouts", "PvP gear loadouts (in PvP mode)"),
			e("itemstats", "Look up any item's stats"),
			e("points", "Your point balances"),
			e("dailies", "Today's daily tasks"),
			e("titles", "Choose a loyalty title"),
			e("cas", "Your combat achievements"))),
		new Category("Competitive & PvP", "Competitive & PvP", "Ranked PvP, boss dungeons, tournaments and games.", List.of(
			e("rivals", "Division Rivals ranked 1v1"),
			e("sanctum", "Teleport to the Sanctum"),
			e("draco", "Teleport to Draco's lair"),
			e("tournaments", "Pit Championships and tournaments"),
			e("highscores", "Open the online hiscores"),
			e("lottery", "The lottery"),
			e("raffle", "The vote raffle"),
			e("casino", "Casino blackjack"),
			e("scoreboard", "Event scoreboards"))),
		new Category("Settings & Utility", "Settings & Utility", "Switch account options on and off, check timers.", List.of(
			e("profanity on/off", "Turn the chat filter on or off"),
			e("breakvials", "Toggle smashing empty vials"),
			e("explock", "Lock or unlock XP gain"),
			e("vaultloot", "Toggle Monarch bond loot to bank"),
			e("pvpmode off", "Leave PvP mode"),
			e("timers", "Your active scroll and boost timers"),
			e("task", "Your current Slayer task"),
			e("bosstask", "Your current boss task")))
	);

	private static final int TOTAL = CATEGORIES.stream().mapToInt(c -> c.entries.size()).sum();
	private static final Map<Integer, Integer> tab = new ConcurrentHashMap<>();

	private CommandCodex() {
	}

	public static void register() {
		for (Category c : CATEGORIES)
			if (c.entries.size() > ROWS)
				throw new IllegalStateException("Zelus Commands: " + c.tab + " has more than " + ROWS + " rows");
		InterfaceHandler.register(INTERFACE, h -> {
			h.actions[CLOSE] = (SimpleAction) Player::closeInterfaces;
			for (int t = 0; t < CATEGORIES.size(); t++) {
				final int cat = t;
				h.actions[FIRST_TAB + t * TAB_STRIDE] = (SimpleAction) p -> showCategory(p, cat);
			}
			h.actions[SEARCH] = (SimpleAction) p -> p.stringInput("Search commands:", q -> search(p, q));
			// the rows have no actions: this guide is read-only
		});
	}

	public static void open(Player player) {
		player.openInterface(ToplevelComponent.MAINMODAL, INTERFACE);
		PacketSender ps = player.getPacketSender();
		ps.sendString(INTERFACE, TITLE, "Zelus Commands");
		ps.sendString(INTERFACE, SUBTITLE, "General directory and utility commands reference.");
		ps.sendString(INTERFACE, COUNT, TOTAL + " commands");
		for (int t = 0; t < CATEGORIES.size(); t++)
			ps.sendString(INTERFACE, FIRST_TAB + t * TAB_STRIDE + 3, CATEGORIES.get(t).tab);
		ps.sendString(INTERFACE, TIP, "Type a command in the chat box. Teleports are in the Teleport menu.");
		ps.sendString(INTERFACE, SEARCH + 1, "Search");
		showCategory(player, tab.getOrDefault(player.getUserId(), 0));
	}

	private static void showCategory(Player player, int cat) {
		Category c = CATEGORIES.get(cat);
		tab.put(player.getUserId(), cat);
		highlightTab(player, cat);
		fill(player, c.title, c.blurb, c.entries, "");
	}

	private static void search(Player player, String query) {
		String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
		if (q.startsWith("::"))
			q = q.substring(2);
		if (q.isEmpty())
			return;
		List<Entry> hits = new ArrayList<>();
		for (Category c : CATEGORIES)
			for (Entry en : c.entries)
				if ((en.cmd.toLowerCase(Locale.ROOT).contains(q) || en.desc.toLowerCase(Locale.ROOT).contains(q)) && hits.size() < ROWS)
					hits.add(en);
		highlightTab(player, -1);
		fill(player, "Search: " + query.trim(), hits.size() + " match" + (hits.size() == 1 ? "" : "es") + ". Pick a tab to go back.",
			hits, "No command matches \"" + query.trim() + "\".");
	}

	private static void highlightTab(Player player, int active) {
		PacketSender ps = player.getPacketSender();
		for (int t = 0; t < CATEGORIES.size(); t++) {
			int base = FIRST_TAB + t * TAB_STRIDE;
			ps.setHidden(INTERFACE, base + 1, t != active);
			ps.setHidden(INTERFACE, base + 2, t != active);
		}
	}

	private static void fill(Player player, String title, String blurb, List<Entry> entries, String emptyText) {
		PacketSender ps = player.getPacketSender();
		ps.sendString(INTERFACE, CAT_TITLE, title);
		ps.sendString(INTERFACE, CAT_BLURB, blurb);
		ps.setHidden(INTERFACE, EMPTY, !entries.isEmpty());
		ps.sendString(INTERFACE, EMPTY, emptyText);
		for (int r = 0; r < ROWS; r++) {
			int base = FIRST_ROW + r * ROW_STRIDE;
			boolean used = r < entries.size();
			ps.setHidden(INTERFACE, base, !used);
			ps.setHidden(INTERFACE, base + 1, !used);
			ps.setHidden(INTERFACE, base + 2, !used);
			if (!used)
				continue;
			Entry en = entries.get(r);
			ps.sendString(INTERFACE, base + 1, en.cmd);
			ps.sendString(INTERFACE, base + 2, en.desc);
		}
		updateScrollbar(player, entries.size());
	}

	/** Sizes the scroll area to the rows in use (clientscript 10532: count * 15 + height / 3) and scrolls to the top. */
	private static void updateScrollbar(Player player, int rows) {
		int contentHeight = rows * ROW_HEIGHT;
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
