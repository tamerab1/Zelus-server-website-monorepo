package io.ruin.model.content.bonds;

import io.ruin.cache.ObjType;
import io.ruin.model.ScrollbarClientScript;
import io.ruin.model.content.bonds.BondType.Variant;
import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.InterfaceHandler;
import io.ruin.model.inter.ToplevelComponent;
import io.ruin.model.inter.actions.SimpleAction;
import io.ruin.model.item.Item;

import java.util.ArrayList;
import java.util.List;

/**
 * The Bonds & Auras window (interface 1140, built by .dev/cache-restore-tool/src/BuildBondsInterface.java)
 * for the Chamber of Ascension custom bonds: Overview / Bond perks / Upgrade / Obtaining tabs, a row of
 * the six bonds as clickable models, a rotating model of the selected bond, and Attune/Sever + Upgrade
 * buttons that run the same logic as the bond items' own options. Opened by ::bonds / ::auras and the
 * bonds' Inspect / Overview options.
 */
public final class BondInterface {

	private static final int INTERFACE = 1140;
	private static final int CLOSE = 2, TITLE = 3;
	private static final int FIRST_WELL = 8, FIRST_SELECTOR = 14, BIG_MODEL = 20, FIRST_HEADER = 21;
	/** Selector slot frames (BuildBondsInterface 88+): per bond shadow top, shadow left, border, selected trim (+3). */
	private static final int FIRST_SLOT_FRAME = 88, SLOT_FRAME_STRIDE = 4, SLOT_TRIM = 3;
	private static final int SCROLL = 26, SCROLLBAR = 27, FIRST_ROW = 28, ROWS = 40;
	private static final int ATTUNE_BUTTON = 68, ATTUNE_LABEL = 69, UPGRADE_BUTTON = 70, UPGRADE_LABEL = 71;
	private static final int[] TAB_BUTTONS = {72, 74, 76, 78}, TAB_LABELS = {73, 75, 77, 79};
	private static final int FIRST_INFO_ROW = 84, INFO_ROWS = 4;
	private static final int SCROLL_HEIGHT = 126, ROW_HEIGHT = 12;

	public static final int OVERVIEW = 0, PERKS = 1, UPGRADE = 2, OBTAINING = 3;
	private static final String[] TAB_NAMES = {"Overview", "Bond perks", "Upgrade", "Obtaining"};

	/** Item models are framed for a 36x32 icon; the boxes are bigger, so the icon zoom is scaled down. */
	private static final double BIG_ZOOM_SCALE = 1.9, SELECTOR_ZOOM_SCALE = 1.15;
	private static final int SPIN_STEP = 48;

	/** Aurelius' bond drop rates (data/npcs/drops/newDrops/30571.json), in BondType order. */
	private static final int[] DROP_RATES = {750, 650, 750, 500, 500, 750};

	private static final String TAB_KEY = "bond_ui_tab", SELECTED_KEY = "bond_ui_type", SPIN_KEY = "bond_ui_spin";

	private static final String GOLD = "<col=ffc35a>", GREY = "<col=9a9a9a>", WHITE = "<col=ffffff>",
			GREEN = "<col=50e050>", RED = "<col=ff5050>", END = "</col>";
	private static final String BULLET = GOLD + "»" + END + " ";

	private BondInterface() {
	}

	public static void register() {
		InterfaceHandler.register(INTERFACE, h -> {
			h.actions[CLOSE] = (SimpleAction) player -> player.closeInterface(ToplevelComponent.MAINMODAL);
			for (int t = 0; t < TAB_BUTTONS.length; t++) {
				int tab = t;
				h.actions[TAB_BUTTONS[t]] = (SimpleAction) player -> show(player, tab, selected(player));
			}
			for (int b = 0; b < BondType.VALUES.length; b++) {
				BondType type = BondType.VALUES[b];
				h.actions[FIRST_SELECTOR + b] = (SimpleAction) player -> {
					int tab = player.<Integer>get(TAB_KEY, OVERVIEW);
					show(player, tab == OVERVIEW || tab == OBTAINING ? PERKS : tab, type);
				};
			}
			h.actions[ATTUNE_BUTTON] = (SimpleAction) BondInterface::attuneOrSever;
			h.actions[UPGRADE_BUTTON] = (SimpleAction) BondInterface::upgrade;
		});
	}

	/** ::bonds -- opens on the overview, with the first attuned (else first owned) bond selected. */
	public static void open(Player player) {
		BondType pick = BondType.VALUES[0];
		for (BondType type : BondType.VALUES) {
			if (type.tier(player) > 0) {
				pick = type;
				break;
			}
		}
		open(player, OVERVIEW, pick);
	}

	public static void open(Player player, int tab, BondType type) {
		player.openInterface(ToplevelComponent.MAINMODAL, INTERFACE);
		player.getPacketSender().sendString(INTERFACE, TITLE, "Bonds & Auras");
		show(player, tab, type);
	}

	/** Re-renders the window after an attune/sever/upgrade, if it is still open. */
	static void refreshIfOpen(Player player) {
		if (player.isVisibleInterface(INTERFACE))
			show(player, player.<Integer>get(TAB_KEY, OVERVIEW), selected(player));
	}

	private static BondType selected(Player player) {
		return BondType.VALUES[player.<Integer>get(SELECTED_KEY, 0)];
	}

	private static void show(Player player, int tab, BondType type) {
		player.set(TAB_KEY, tab);
		player.set(SELECTED_KEY, type.ordinal());
		var ps = player.getPacketSender();
		for (int t = 0; t < TAB_LABELS.length; t++)
			ps.sendString(INTERFACE, TAB_LABELS[t], t == tab ? WHITE + TAB_NAMES[t] + END : TAB_NAMES[t]);

		// bond selector: each bond's best owned version (so its look matches), highlight on the selected one
		for (int b = 0; b < BondType.VALUES.length; b++) {
			BondType t = BondType.VALUES[b];
			int itemId = displayItem(player, t);
			ps.sendItem(INTERFACE, FIRST_SELECTOR + b, itemId, 1);
			ObjType def = ObjType.get(itemId);
			ps.sendModelInformation(INTERFACE, FIRST_SELECTOR + b, (int) (def.zoom2d / SELECTOR_ZOOM_SCALE), def.xan2d, def.yan2d);
			ps.setHidden(INTERFACE, FIRST_SLOT_FRAME + b * SLOT_FRAME_STRIDE + SLOT_TRIM, t != type);
		}
		showBigModel(player, type);

		List<String> header = header(player, type);
		for (int r = 0; r < 4; r++)
			ps.sendString(INTERFACE, FIRST_HEADER + r, r < header.size() ? header.get(r) : "");

		List<String> rows = switch (tab) {
			case PERKS -> perks(player, type);
			case UPGRADE -> upgradeRows(player, type);
			case OBTAINING -> obtaining();
			default -> overview(player);
		};
		for (int r = 0; r < ROWS; r++)
			ps.sendString(INTERFACE, FIRST_ROW + r, r < rows.size() ? rows.get(r) : "");
		updateScrollbar(player, Math.min(rows.size(), ROWS));

		boolean actions = tab == UPGRADE;
		for (int c : new int[]{ATTUNE_BUTTON, ATTUNE_LABEL, UPGRADE_BUTTON, UPGRADE_LABEL})
			ps.setHidden(INTERFACE, c, !actions);
		if (actions)
			ps.sendString(INTERFACE, ATTUNE_LABEL, type.tier(player) > 0 ? "Sever" : "Attune");

		List<String> info = summary(player);
		for (int r = 0; r < INFO_ROWS; r++)
			ps.sendString(INTERFACE, FIRST_INFO_ROW + r, r < info.size() ? info.get(r) : "");
	}

	/* ---------------- content (rows are ~45 characters wide; the header lines ~34) ---------------- */

	private static List<String> header(Player player, BondType type) {
		List<String> r = new ArrayList<>();
		r.add(type.bondName.replace("Bond of the ", ""));
		r.add("Category: " + WHITE + type.category + END);
		r.add("Status: " + status(player, type));
		Item best = bondInInventory(player, type);
		r.add(best == null ? GREY + "Not in your inventory" + END : "In your inventory");
		return r;
	}

	private static List<String> overview(Player player) {
		List<String> r = new ArrayList<>();
		r.add(GOLD + "How bonds work" + END);
		r.add(BULLET + "Attune a bond to switch its perks on.");
		r.add(BULLET + "Perks only work while the attuned bond");
		r.add("   is in your inventory.");
		r.add(BULLET + "One attuned bond of each type at a time.");
		r.add(BULLET + "Sever it to switch its perks off.");
		r.add(BULLET + "Upgrade with spare T1 bonds: T1 to MAX.");
		r.add(BULLET + "Only unattuned T1 bonds can be traded.");
		r.add("");
		r.add(GOLD + "Your bonds" + END);
		for (BondType type : BondType.VALUES)
			r.add(WHITE + type.bondName.replace("Bond of the ", "") + END + " (" + type.category + "): " + status(player, type));
		r.add("");
		r.add(GREY + "Click a bond above to see its perks." + END);
		return r;
	}

	private static List<String> perks(Player player, BondType type) {
		List<String> r = new ArrayList<>();
		int current = bestTier(player, type);
		for (int tier = 1; tier <= BondType.MAX_TIER; tier++) {
			if (tier > 1)
				r.add("");
			String cost = tier == 1 ? "the base bond" : "+" + BondType.upgradeCost(tier - 1) + " T1 bond" + (tier == 2 ? "" : "s");
			boolean yours = tier == current;
			r.add((yours ? GREEN : GOLD) + BondType.tierLabel(tier) + END + GREY + "  -  " + cost + END
					+ (yours ? GREEN + "   (your bond)" + END : ""));
			for (String perk : type.perkLines(tier))
				r.add(BULLET + perk);
		}
		return r;
	}

	/** At most 7 rows: the action buttons cover the lower part of the panel on this tab. */
	private static List<String> upgradeRows(Player player, BondType type) {
		List<String> r = new ArrayList<>();
		Item bond = bondInInventory(player, type);
		if (bond == null) {
			r.add(RED + "No " + type.bondName.replace("Bond of the ", "") + " bond in your inventory." + END);
			r.add("Take it out of your bank to attune or upgrade it.");
			r.add("");
			r.add(GREY + "Upgrades cost spare unattuned T1 bonds of the" + END);
			r.add(GREY + "same type: 1, 2, 3, then 4 for MAX (10 total)." + END);
			return r;
		}
		Variant v = BondType.variant(bond.getId());
		r.add("Your bond: " + WHITE + BondType.tierLabel(v.tier()) + END + (v.attuned() ? GREEN + ", attuned" + END : GREY + ", not attuned" + END));
		if (v.tier() >= BondType.MAX_TIER) {
			r.add(GOLD + "MAX: this bond is fully ascended." + END);
			return r;
		}
		int next = v.tier() + 1;
		int cost = BondType.upgradeCost(v.tier());
		int have = BondHandler.spareBaseBonds(player, type, bond);
		r.add("Next: " + WHITE + BondType.tierLabel(next) + END + " for " + cost + " spare T1 bond" + (cost == 1 ? "" : "s")
				+ " (you have " + (have >= cost ? GREEN : RED) + have + END + ")");
		r.add(GOLD + BondType.tierLabel(next) + " perks" + END);
		String[] lines = type.perkLines(next);
		for (int i = 0; i < Math.min(4, lines.length); i++)
			r.add(BULLET + lines[i]);
		return r;
	}

	private static List<String> obtaining() {
		List<String> r = new ArrayList<>();
		r.add(GOLD + "Chamber of Ascension" + END);
		r.add(BULLET + "Bonds drop from Aurelius, the chamber's boss.");
		r.add(BULLET + "Teleport: Minigames > Chamber of Ascension.");
		r.add("");
		r.add(GOLD + "Aurelius bond drops" + END);
		for (int b = 0; b < BondType.VALUES.length; b++) {
			BondType type = BondType.VALUES[b];
			r.add(WHITE + type.bondName.replace("Bond of the ", "") + END + " (" + type.category + ")  " + GOLD + "1/" + DROP_RATES[b] + END);
		}
		r.add("");
		r.add(GOLD + "Trading & upgrading" + END);
		r.add(BULLET + "Unattuned T1 bonds are tradeable: buy spares");
		r.add("   from other players to upgrade faster.");
		r.add(BULLET + "T1 to MAX takes 11 bonds of one type.");
		return r;
	}

	private static List<String> summary(Player player) {
		List<String> r = new ArrayList<>();
		int attuned = 0;
		for (BondType type : BondType.VALUES)
			if (type.tier(player) > 0)
				attuned++;
		r.add(GOLD + "Active bonds" + END);
		r.add((attuned > 0 ? GREEN : GREY) + attuned + " of " + BondType.VALUES.length + " attuned" + END);
		r.add(GREY + "Perks need the attuned" + END);
		r.add(GREY + "bond in your inventory." + END);
		return r;
	}

	/* ---------------- actions ---------------- */

	private static void attuneOrSever(Player player) {
		BondType type = selected(player);
		Item bond = bondInInventory(player, type);
		if (bond == null) {
			player.sendMessage("You need a " + type.bondName + " in your inventory.");
			return;
		}
		BondHandler.attuneOrSever(player, bond);
		refreshIfOpen(player);
	}

	private static void upgrade(Player player) {
		BondType type = selected(player);
		Item bond = bondInInventory(player, type);
		if (bond == null) {
			player.sendMessage("You need a " + type.bondName + " in your inventory.");
			return;
		}
		BondHandler.upgrade(player, bond); // asks for confirmation; refreshes this window when done
	}

	/* ---------------- helpers ---------------- */

	/** The bond the buttons act on: the attuned copy, else the highest-tier unattuned one in the inventory. */
	private static Item bondInInventory(Player player, BondType type) {
		Item best = null;
		int bestScore = -1;
		for (Item item : player.getInventory().getItems()) {
			if (item == null)
				continue;
			Variant v = BondType.variant(item.getId());
			if (v == null || v.type() != type)
				continue;
			int score = (v.attuned() ? 10 : 0) + v.tier();
			if (score > bestScore) {
				best = item;
				bestScore = score;
			}
		}
		return best;
	}

	/** Highest tier owned in inventory or bank (0 = none). */
	private static int bestTier(Player player, BondType type) {
		return Math.max(BondGuide.highestOwned(player.getInventory().getItems(), type),
				BondGuide.highestOwned(player.getBank().getItems(), type));
	}

	private static int displayItem(Player player, BondType type) {
		Item bond = bondInInventory(player, type);
		if (bond != null)
			return bond.getId();
		int tier = bestTier(player, type);
		return tier > 0 ? type.itemId(tier, false) : type.baseItemId;
	}

	private static String status(Player player, BondType type) {
		int attuned = type.tier(player);
		if (attuned > 0)
			return GREEN + "Attuned " + BondType.tierLabel(attuned) + END;
		int best = bestTier(player, type);
		return best > 0 ? WHITE + "Owned " + BondType.tierLabel(best) + END : RED + "Not owned" + END;
	}

	private static void showBigModel(Player player, BondType type) {
		var ps = player.getPacketSender();
		int itemId = displayItem(player, type);
		ObjType def = ObjType.get(itemId);
		int zoom = (int) (def.zoom2d / BIG_ZOOM_SCALE);
		ps.sendItem(INTERFACE, BIG_MODEL, itemId, 1);
		ps.sendModelInformation(INTERFACE, BIG_MODEL, zoom, def.xan2d, def.yan2d);
		int spin = player.<Integer>get(SPIN_KEY, 0) + 1;
		player.set(SPIN_KEY, spin);
		player.addEvent(e -> {
			int yan = def.yan2d;
			while (true) {
				e.delay(1);
				if (!player.isOnline() || !player.isVisibleInterface(INTERFACE) || player.<Integer>get(SPIN_KEY, 0) != spin)
					return;
				yan = (yan + SPIN_STEP) & 2047;
				ps.sendModelInformation(INTERFACE, BIG_MODEL, zoom, def.xan2d, yan);
			}
		});
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
