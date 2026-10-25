package io.ruin.model.activities.scoreboard;

import io.ruin.api.utils.NumberUtils;
import io.ruin.cache.Icon;
import io.ruin.cache.ObjType;
import io.ruin.model.ScrollbarClientScript;
import io.ruin.model.entity.npc.NPCAction;
import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.InterfaceHandler;
import io.ruin.model.inter.ToplevelComponent;
import io.ruin.model.inter.actions.DefaultAction;
import io.ruin.model.inter.actions.SimpleAction;
import io.ruin.model.inter.dialogue.NPCDialogue;
import io.ruin.model.inter.dialogue.OptionsDialogue;
import io.ruin.model.inter.dialogue.YesNoDialogue;
import io.ruin.model.inter.handlers.EquipmentStats;
import io.ruin.model.inter.utils.Option;
import io.ruin.model.item.Item;
import io.ruin.model.item.containers.Equipment;
import io.ruin.network.PacketSender;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Scoreboard Exchange (interface 1179, built by .dev/cache-restore-tool/src/BuildScoreboardExchangeInterface.java):
 * the Score Points (SbP) shop of the Scoreboard Quartermaster (npc 30610, Edgeville 3085,3497). Category tabs over a
 * scrolling showcase of item cards; selecting a card fills the item panel (examine, worn stats, cost), and Buy asks for
 * confirmation before the points are taken. SbP come from {@link Scoreboard}'s daily / weekly payouts.
 */
public final class ScoreboardExchange {

	public static final int INTERFACE = 1179;
	public static final int QUARTERMASTER = 30610;
	private static final int CLOSE = 2, TITLE = 3, BALANCE = 8;
	/** Stone buttons: fill (click), +9 pressed layer, +19 label. */
	private static final int PRESSED = 9, LABEL = 19, STONE = 20;
	private static final int FIRST_TAB = 9, SCROLL = 113, SCROLLBAR = 114, GRID_HEIGHT = 226;
	/** Cards (3 a row, 110px pitch): +1 box (Select / Value / Buy / Examine), +3..+6 selected, +10 item, +11 name, +14 price. */
	private static final int FIRST_CARD = 115, CARD_STRIDE = 15, CARDS = 36, PER_ROW = 3, CARD_PITCH = 110, CARD_H = 104;
	private static final int EMPTY = 655, ITEM_NAME = 660, ITEM_SLOT = 665, ITEM_EXAMINE = 666, ITEM_STATS = 668,
		ITEM_COST = 671, BUY = 672;

	private static final String ORANGE = "<col=ff981f>", WHITE = "<col=ffffff>", GREEN = "<col=00ff00>", RED = "<col=ff3333>",
		GREY = "<col=9a9a9a>", END = "</col>";
	private static final String TAG = "<col=ff981f><shad=000000>[Scoreboard Exchange]</shad></col> ";
	/** Chat colours that read on the parchment chatbox (the interface's white / bright green / grey don't). */
	private static final String CHAT_NAVY = "<col=000080>", CHAT_GREEN = "<col=006000>", CHAT_RED = "<col=7f0000>";

	public enum Category {
		ALL("All"), WILDSTALKER("Wildstalker"), CLOWN("Clown"), ANKOU("Ankou"), RARES("Rares");

		final String label;

		Category(String label) {
			this.label = label;
		}
	}

	private record Stock(Category category, int itemId, int cost) {
	}

	private static final Stock[] STOCK = {
		new Stock(Category.WILDSTALKER, 42466, 500), // Wildstalker top
		new Stock(Category.WILDSTALKER, 42468, 500), // Wildstalker trousers
		new Stock(Category.WILDSTALKER, 42464, 350), // Wildstalker hood
		new Stock(Category.WILDSTALKER, 42470, 250), // Wildstalker boots
		new Stock(Category.CLOWN, 22689, 300),       // Clown mask
		new Stock(Category.CLOWN, 22695, 250),       // Clown gown
		new Stock(Category.CLOWN, 22698, 250),       // Clown trousers
		new Stock(Category.CLOWN, 22692, 180),       // Clown bow tie
		new Stock(Category.CLOWN, 22701, 180),       // Clown shoes
		// round 2 (2026-10-21, .dev/cache-restore-tool/sbx2_*.txt): recoloured Ankou sets, 5 pieces each
		new Stock(Category.ANKOU, 42714, 600),       // Crimson Ankou mask
		new Stock(Category.ANKOU, 42716, 600),       // Crimson Ankou top
		new Stock(Category.ANKOU, 42718, 600),       // Crimson Ankou leggings
		new Stock(Category.ANKOU, 42720, 600),       // Crimson Ankou gloves
		new Stock(Category.ANKOU, 42722, 600),       // Crimson Ankou socks
		new Stock(Category.ANKOU, 42724, 600),       // Verdant Ankou mask
		new Stock(Category.ANKOU, 42726, 600),       // Verdant Ankou top
		new Stock(Category.ANKOU, 42728, 600),       // Verdant Ankou leggings
		new Stock(Category.ANKOU, 42730, 600),       // Verdant Ankou gloves
		new Stock(Category.ANKOU, 42732, 600),       // Verdant Ankou socks
		new Stock(Category.ANKOU, 42734, 600),       // Abyssal Ankou mask
		new Stock(Category.ANKOU, 42736, 600),       // Abyssal Ankou top
		new Stock(Category.ANKOU, 42738, 600),       // Abyssal Ankou leggings
		new Stock(Category.ANKOU, 42740, 600),       // Abyssal Ankou gloves
		new Stock(Category.ANKOU, 42742, 600),       // Abyssal Ankou socks
		new Stock(Category.ANKOU, 42744, 750),       // Celestial Ankou mask
		new Stock(Category.ANKOU, 42746, 750),       // Celestial Ankou top
		new Stock(Category.ANKOU, 42748, 750),       // Celestial Ankou leggings
		new Stock(Category.ANKOU, 42750, 750),       // Celestial Ankou gloves
		new Stock(Category.ANKOU, 42752, 750),       // Celestial Ankou socks
		new Stock(Category.RARES, 42754, 2500),      // Starwoven partyhat
		new Stock(Category.RARES, 42756, 2500),      // Gildshadow partyhat
		new Stock(Category.RARES, 42758, 1500),      // Stormcoil whip
		new Stock(Category.RARES, 42760, 1500),      // Magmatide whip
		new Stock(Category.RARES, 60262, 1200),      // Souly Wings
	};

	/** Per-viewer view: tab and selected card (an index into the tab's list). */
	private static final class View {
		Category category = Category.ALL;
		int selected;
		List<Stock> list = List.of();
	}

	private static final Map<String, View> views = new HashMap<>();

	private ScoreboardExchange() {
	}

	public static void register() {
		InterfaceHandler.register(INTERFACE, h -> {
			h.actions[CLOSE] = (SimpleAction) p -> p.closeInterface(ToplevelComponent.MAINMODAL);
			Category[] cats = Category.values();
			for (int t = 0; t < cats.length; t++) {
				Category category = cats[t];
				h.actions[FIRST_TAB + t * STONE] = (SimpleAction) p -> {
					View v = view(p);
					v.category = category;
					v.selected = 0;
					draw(p, true);
				};
			}
			for (int c = 0; c < CARDS; c++) {
				int index = c;
				h.actions[FIRST_CARD + c * CARD_STRIDE + 1] = (DefaultAction) (p, option, slot, itemId) -> {
					View v = view(p);
					if (index >= v.list.size())
						return;
					Stock stock = v.list.get(index);
					switch (option) {
						case 1 -> {
							v.selected = index;
							draw(p, false);
						}
						case 2 -> p.sendMessage(TAG + name(stock) + " - Cost: " + NumberUtils.formatNumber(stock.cost) + " Score Points.");
						case 3 -> {
							v.selected = index;
							confirm(p, stock);
						}
						default -> Item.examine(p, stock.itemId);
					}
				};
			}
			h.actions[ITEM_SLOT] = (SimpleAction) p -> {
				Stock stock = selected(p);
				if (stock != null)
					Item.examine(p, stock.itemId);
			};
			h.actions[BUY] = (SimpleAction) p -> {
				Stock stock = selected(p);
				if (stock != null)
					confirm(p, stock);
			};
		});
		NPCAction.register(QUARTERMASTER, "talk-to", (player, npc) -> player.dialogue(
			new NPCDialogue(npc, "Every day and every week the Zelus Scoreboard crowns its best. Finish in the top three of "
				+ "any board and you earn Score Points."),
			new NPCDialogue(npc, "Daily boards pay " + Scoreboard.DAILY_REWARD[0] + " / " + Scoreboard.DAILY_REWARD[1] + " / "
				+ Scoreboard.DAILY_REWARD[2] + " SbP, weekly boards " + Scoreboard.WEEKLY_REWARD[0] + " / "
				+ Scoreboard.WEEKLY_REWARD[1] + " / " + Scoreboard.WEEKLY_REWARD[2] + ". You have "
				+ NumberUtils.formatNumber(player.scorePoints) + " Score Points."),
			new OptionsDialogue("Open the Scoreboard Exchange?",
				new Option("Show me your rewards.", () -> open(player)),
				new Option("Maybe later.", player::closeDialogue))));
		NPCAction.register(QUARTERMASTER, "trade", (player, npc) -> open(player));
	}

	/* ---------------- interface ---------------- */

	public static void open(Player player) {
		player.openInterface(ToplevelComponent.MAINMODAL, INTERFACE);
		PacketSender ps = player.getPacketSender();
		ps.sendString(INTERFACE, TITLE, ORANGE + "Scoreboard Exchange" + END);
		Category[] cats = Category.values();
		for (int t = 0; t < cats.length; t++)
			ps.sendString(INTERFACE, FIRST_TAB + t * STONE + LABEL, cats[t].label);
		draw(player, true);
	}

	private static void draw(Player player, boolean resetScroll) {
		View v = view(player);
		PacketSender ps = player.getPacketSender();
		v.list = stock(v.category);
		if (v.selected >= v.list.size())
			v.selected = 0;
		sendBalance(player, ps);
		Category[] cats = Category.values();
		for (int t = 0; t < cats.length; t++)
			ps.setHidden(INTERFACE, FIRST_TAB + t * STONE + PRESSED, cats[t] != v.category);

		for (int c = 0; c < CARDS; c++) {
			int card = FIRST_CARD + c * CARD_STRIDE;
			boolean present = c < v.list.size();
			ps.setHidden(INTERFACE, card, !present);
			if (!present)
				continue;
			Stock stock = v.list.get(c);
			boolean selected = c == v.selected;
			for (int k = 3; k <= 6; k++)
				ps.setHidden(INTERFACE, card + k, !selected);
			ps.sendItem(INTERFACE, card + 10, stock.itemId, 1);
			ps.sendString(INTERFACE, card + 11, name(stock));
			ps.sendString(INTERFACE, card + 14, NumberUtils.formatNumber(stock.cost) + " SbP");
		}
		ps.setHidden(INTERFACE, EMPTY, !v.list.isEmpty());
		if (v.list.isEmpty())
			ps.sendString(INTERFACE, EMPTY, "Nothing in stock here yet.");
		if (resetScroll) {
			int rows = (v.list.size() + PER_ROW - 1) / PER_ROW;
			int contentHeight = rows == 0 ? 0 : (rows - 1) * CARD_PITCH + CARD_H;
			int count = contentHeight <= GRID_HEIGHT ? 0 : (int) Math.ceil((contentHeight - GRID_HEIGHT / 3D) / 15D);
			ScrollbarClientScript.create().interfaceId(INTERFACE).containerId(SCROLL).scrollbarChildId(SCROLLBAR)
				.childrenCount(count).withDarkGraphics().build().send(player);
		}
		drawPanel(player, ps, v.list.isEmpty() ? null : v.list.get(v.selected));
	}

	private static void drawPanel(Player player, PacketSender ps, Stock stock) {
		if (stock == null) {
			ps.sendString(INTERFACE, ITEM_NAME, "");
			ps.sendItem(INTERFACE, ITEM_SLOT, -1, 0);
			ps.sendString(INTERFACE, ITEM_EXAMINE, "");
			ps.sendString(INTERFACE, ITEM_STATS, "");
			ps.sendString(INTERFACE, ITEM_COST, "");
			return;
		}
		ObjType def = ObjType.get(stock.itemId);
		ps.sendString(INTERFACE, ITEM_NAME, ORANGE + def.name + END);
		ps.sendItem(INTERFACE, ITEM_SLOT, stock.itemId, 1);
		ps.sendString(INTERFACE, ITEM_EXAMINE, def.examine == null ? "" : def.examine);
		ps.sendString(INTERFACE, ITEM_STATS, stats(def));
		boolean affordable = player.scorePoints >= stock.cost;
		ps.sendString(INTERFACE, ITEM_COST, "Cost: " + (affordable ? WHITE : RED) + NumberUtils.formatNumber(stock.cost) + END
			+ " Score Points");
	}

	/** Up to 4 short lines: the slot, then the bonuses that aren't 0 (attack, defence, strength / prayer). */
	private static String stats(ObjType def) {
		List<String> lines = new ArrayList<>();
		lines.add("Slot: " + WHITE + slotName(def.equipSlot) + END);
		int[] b = def.equipBonuses;
		if (b != null && b.length > EquipmentStats.PRAYER) {
			if (b[0] != 0 || b[1] != 0 || b[2] != 0 || b[3] != 0 || b[4] != 0)
				lines.add("Att: " + WHITE + bonus(b[0]) + " / " + bonus(b[1]) + " / " + bonus(b[2]) + END
					+ GREY + "  M " + END + WHITE + bonus(b[3]) + END + GREY + " R " + END + WHITE + bonus(b[4]) + END);
			if (b[5] != 0 || b[6] != 0 || b[7] != 0 || b[8] != 0 || b[9] != 0)
				lines.add("Def: " + WHITE + bonus(b[5]) + " / " + bonus(b[6]) + " / " + bonus(b[7]) + END
					+ GREY + "  M " + END + WHITE + bonus(b[8]) + END + GREY + " R " + END + WHITE + bonus(b[9]) + END);
			List<String> other = new ArrayList<>();
			if (b[EquipmentStats.MELEE_STRENGTH] != 0) other.add("Str " + WHITE + bonus(b[EquipmentStats.MELEE_STRENGTH]) + END);
			if (b[EquipmentStats.RANGED_STRENGTH] != 0) other.add("R.Str " + WHITE + bonus(b[EquipmentStats.RANGED_STRENGTH]) + END);
			if (b[EquipmentStats.MAGIC_DAMAGE] != 0) other.add("M.Dmg " + WHITE + bonus(b[EquipmentStats.MAGIC_DAMAGE]) + "%" + END);
			if (b[EquipmentStats.PRAYER] != 0) other.add("Pray " + WHITE + bonus(b[EquipmentStats.PRAYER]) + END);
			if (!other.isEmpty())
				lines.add(String.join("  ", other));
		}
		if (lines.size() == 1)
			lines.add(GREY + "Cosmetic - no combat bonuses." + END);
		return String.join("<br>", lines);
	}

	private static String bonus(int value) {
		return value > 0 ? "+" + value : String.valueOf(value);
	}

	private static String slotName(int slot) {
		return switch (slot) {
			case Equipment.SLOT_HAT -> "Head";
			case Equipment.SLOT_CAPE -> "Cape";
			case Equipment.SLOT_AMULET -> "Neck";
			case Equipment.SLOT_WEAPON -> "Weapon";
			case Equipment.SLOT_CHEST -> "Body";
			case Equipment.SLOT_SHIELD -> "Shield";
			case Equipment.SLOT_LEGS -> "Legs";
			case Equipment.SLOT_HANDS -> "Hands";
			case Equipment.SLOT_FEET -> "Feet";
			case Equipment.SLOT_RING -> "Ring";
			case Equipment.SLOT_AMMO -> "Ammo";
			default -> "None";
		};
	}

	/** ::sbp, and the balance plaque. */
	public static void sendBalance(Player player) {
		player.sendMessage(TAG + "You have " + CHAT_GREEN + NumberUtils.formatNumber(player.scorePoints) + " Score Points" + END
			+ ". Earn them on the Zelus Scoreboard (::scoreboard).");
	}

	private static void sendBalance(Player player, PacketSender ps) {
		ps.sendString(INTERFACE, BALANCE, Icon.PIT_TROPHY.tag() + " Score Points: " + WHITE
			+ NumberUtils.formatNumber(player.scorePoints) + END);
	}

	/* ---------------- buying ---------------- */

	private static void confirm(Player player, Stock stock) {
		if (player.scorePoints < stock.cost) {
			player.sendMessage(TAG + CHAT_RED + "You need " + NumberUtils.formatNumber(stock.cost) + " Score Points for "
				+ name(stock) + " (you have " + NumberUtils.formatNumber(player.scorePoints) + ")." + END);
			return;
		}
		if (!player.getInventory().hasRoomFor(stock.itemId, 1)) {
			player.sendMessage(TAG + "You don't have enough inventory space.");
			return;
		}
		player.dialogue(new YesNoDialogue("Buy " + name(stock) + "?", "Buy " + name(stock) + " for "
			+ NumberUtils.formatNumber(stock.cost) + " Score Points?", stock.itemId, 1, () -> {
			buy(player, stock);
			open(player);
		}));
	}

	/** Checks again (the confirmation can sit open), then takes the points and gives the item in the same tick. */
	private static void buy(Player player, Stock stock) {
		if (player.scorePoints < stock.cost) {
			player.sendMessage(TAG + CHAT_RED + "You no longer have enough Score Points." + END);
			return;
		}
		if (!player.getInventory().hasRoomFor(stock.itemId, 1)) {
			player.sendMessage(TAG + "You don't have enough inventory space.");
			return;
		}
		player.scorePoints -= stock.cost;
		player.getInventory().add(stock.itemId, 1);
		// one line, every part readable on the chatbox (the item name was white and "(X left)" grey: 2026-10-21)
		player.sendMessage(TAG + "You bought " + name(stock) + " for " + NumberUtils.formatNumber(stock.cost) + " Score Points. "
			+ CHAT_NAVY + "(" + NumberUtils.formatNumber(player.scorePoints) + " left)" + END);
	}

	/* ---------------- helpers ---------------- */

	private static View view(Player player) {
		return views.computeIfAbsent(player.uuid(), k -> new View());
	}

	private static Stock selected(Player player) {
		View v = view(player);
		return v.selected < v.list.size() ? v.list.get(v.selected) : null;
	}

	private static List<Stock> stock(Category category) {
		List<Stock> list = new ArrayList<>();
		for (Stock s : STOCK)
			if (category == Category.ALL || s.category == category)
				list.add(s);
		return list;
	}

	private static String name(Stock stock) {
		return ObjType.get(stock.itemId).name;
	}
}
