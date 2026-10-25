package io.ruin.model.content.rivals;

import io.ruin.cache.ObjType;
import io.ruin.model.content.loyaltytitles.LoyaltyTitle;
import io.ruin.model.content.loyaltytitles.LoyaltyTitleManager;
import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.InterfaceHandler;
import io.ruin.model.inter.ToplevelComponent;
import io.ruin.model.inter.actions.DefaultAction;
import io.ruin.model.inter.actions.SimpleAction;
import io.ruin.model.ScrollbarClientScript;
import io.ruin.model.item.Item;
import io.ruin.api.utils.NumberUtils;

/**
 * The Division Rivals Points shop, interface 1171 (.dev/cache-restore-tool BuildRivalsShopInterface: the Tournament
 * Shop's card grid). Opened from the Rivals Overseer, the matchmaking window and the Rivals window.
 * <ul>
 * <li>Capes: Challenger (Division 5 or better), Champion (Elite only).</li>
 * <li>Celestial ornament kit: use it on a Twisted bow, Armadyl godsword, Voidwaker or Harmonised nightmare staff.</li>
 * <li>Titles: "the Rival" / "Undefeated", unlocked straight into the Titles screen (no item).</li>
 * <li>Rivals mystery box, a combat XP lamp (the skill lamp), a looting bag, blighted supplies.</li>
 * <li>The Assassin set (row 2): hood 650, garb 800, legs 800, greatsword 1,000.</li>
 * </ul>
 */
public final class RivalsShop {

	public static final int INTERFACE = 1171;
	private static final int CLOSE = 2, TITLE = 3, POINTS = 4;
	private static final int SCROLL = 9, SCROLLBAR = 10;
	private static final int FIRST_CARD = 11, CARD_SIZE = 9, CARDS = 24;
	private static final int CONTENT_HEIGHT = 6 * 115 + 5 * 4, SCROLL_HEIGHT = 235;
	private static final int MODEL = 4, NAME = 5, COST = 6, BUTTON = 7;
	private static final double MODEL_ZOOM_SCALE = 0.95;

	public static final int CHALLENGER_CAPE = 42580, CHAMPION_CAPE = 42582, ORNAMENT_KIT = 42590, MYSTERY_BOX = 42592;
	public static final int TITLE_RIVAL = 30221, TITLE_UNDEFEATED = 30222;
	private static final int SCROLL_ITEM = 2396, LAMP = 30470, LOOTING_BAG = 11941, BLIGHTED_RESTORE = 24598;
	private static final int ASSASSIN_HOOD = 42678, ASSASSIN_GARB = 42680, ASSASSIN_LEGS = 42682, ASSASSIN_GREATSWORD = 42684;

	private enum Kind { ITEM, TITLE, BLIGHTED }

	private record Stock(String name, int iconItem, int amount, int cost, Kind kind, int titleId, int minDivision) {
		/** minDivision: 10 = anyone, 5 = Division 5 or better, 0 = Elite only. */
		static Stock item(int id, int amount, int cost) {
			return new Stock(null, id, amount, cost, Kind.ITEM, 0, 10);
		}
	}

	private static final Stock[] STOCK = {
		new Stock("Champion cape", CHAMPION_CAPE, 1, 3500, Kind.ITEM, 0, 0),
		new Stock("Challenger cape", CHALLENGER_CAPE, 1, 1000, Kind.ITEM, 0, 5),
		new Stock("Ornament kit", ORNAMENT_KIT, 1, 2000, Kind.ITEM, 0, 10),
		new Stock("Mystery box", MYSTERY_BOX, 1, 250, Kind.ITEM, 0, 10),
		// row 2: the Assassin set (armor batch 2026-10-20; destroy-only)
		Stock.item(ASSASSIN_HOOD, 1, 650),
		Stock.item(ASSASSIN_GARB, 1, 800),
		Stock.item(ASSASSIN_LEGS, 1, 800),
		Stock.item(ASSASSIN_GREATSWORD, 1, 1000),
		new Stock("Title: the Rival", SCROLL_ITEM, 1, 750, Kind.TITLE, TITLE_RIVAL, 10),
		new Stock("Title: Undefeated", SCROLL_ITEM, 1, 750, Kind.TITLE, TITLE_UNDEFEATED, 10),
		new Stock("Combat XP lamp", LAMP, 1, 100, Kind.ITEM, 0, 10),
		Stock.item(LOOTING_BAG, 1, 25),
		new Stock("Blighted supplies (10)", BLIGHTED_RESTORE, 10, 25, Kind.BLIGHTED, 0, 10),
	};

	private RivalsShop() {
	}

	static void register() {
		InterfaceHandler.register(INTERFACE, h -> {
			h.actions[CLOSE] = (SimpleAction) p -> p.closeInterface(ToplevelComponent.MAINMODAL);
			for (int i = 0; i < Math.min(CARDS, STOCK.length); i++) {
				Stock stock = STOCK[i];
				int card = FIRST_CARD + i * CARD_SIZE;
				h.actions[card + MODEL] = (DefaultAction) (player, option, slot, itemId) -> {
					switch (option) {
						case 1 -> player.sendMessage(name(stock) + " costs " + NumberUtils.formatNumber(stock.cost) + " Rivals Points"
							+ requirementText(stock) + ".");
						case 2 -> buy(player, stock, 1);
						case 3 -> buy(player, stock, 5);
						default -> Item.examine(player, stock.iconItem);
					}
				};
				h.actions[card + BUTTON] = (DefaultAction) (player, option, slot, itemId) ->
					buy(player, stock, option == 3 ? 10 : option == 2 ? 5 : 1);
			}
		});
	}

	public static void open(Player player) {
		player.openInterface(ToplevelComponent.MAINMODAL, INTERFACE);
		var ps = player.getPacketSender();
		ps.sendString(INTERFACE, TITLE, "Division Rivals Shop");
		sendPoints(player);
		for (int i = 0; i < CARDS; i++) {
			int card = FIRST_CARD + i * CARD_SIZE;
			boolean used = i < STOCK.length;
			ps.setHidden(INTERFACE, card, !used);
			if (!used)
				continue;
			Stock stock = STOCK[i];
			ObjType def = ObjType.get(stock.iconItem);
			ps.sendItem(INTERFACE, card + MODEL, stock.iconItem, stock.amount);
			ps.sendModelInformation(INTERFACE, card + MODEL, (int) (def.zoom2d * MODEL_ZOOM_SCALE), def.xan2d, def.yan2d);
			// the name block holds 2 lines: the name, then (if any) the requirement; the price has its own line below
			ps.sendString(INTERFACE, card + NAME, "<col=ff981f>" + name(stock) + "</col>" + (stock.minDivision < 10
				? "<br><col=a0a0a0>Requires: " + (stock.minDivision == 0 ? "Elite" : "D" + stock.minDivision + "+") + "</col>" : ""));
			ps.sendString(INTERFACE, card + COST, "<col=00ff00>[" + NumberUtils.formatNumber(stock.cost) + " Pts]</col>");
		}
		int rows = (int) Math.ceil((CONTENT_HEIGHT - SCROLL_HEIGHT / 3D) / 15D);
		ScrollbarClientScript.create()
			.interfaceId(INTERFACE)
			.containerId(SCROLL)
			.scrollbarChildId(SCROLLBAR)
			.childrenCount(rows)
			.withDarkGraphics()
			.build()
			.send(player);
	}

	private static void sendPoints(Player player) {
		player.getPacketSender().sendString(INTERFACE, POINTS, "<col=ffff00>Rivals Points:</col> <col=00ff00>"
			+ NumberUtils.formatNumber(player.rivalsPoints) + "</col>   <col=9a9a9a>Earn them in ranked matches (::rivals)</col>");
	}

	private static String name(Stock stock) {
		if (stock.name != null)
			return stock.name;
		String name = ObjType.get(stock.iconItem).name;
		return stock.amount > 1 ? name + " (x" + stock.amount + ")" : name;
	}

	private static String requirementText(Stock stock) {
		return stock.minDivision == 10 ? "" : stock.minDivision == 0 ? " and needs Elite" : " and needs Division " + stock.minDivision + " or better";
	}

	/** Division 10 is the lowest; 0 (Elite) the highest. */
	private static boolean meets(Player player, int minDivision) {
		return minDivision == 10 || player.rivalsDivision <= minDivision;
	}

	private static void buy(Player player, Stock stock, int amount) {
		// mid-match the inventory is the kit, which is wiped on restore: the purchase would be lost
		if (RivalsSnapshot.active(player) || RivalsQueueManager.inMatch(player)) {
			player.sendMessage("You can't use the Rivals shop during a match.");
			return;
		}
		if (!meets(player, stock.minDivision)) {
			player.sendMessage("<col=ff0000>" + name(stock) + " needs " + (stock.minDivision == 0 ? "Elite" : "Division " + stock.minDivision + " or better")
				+ " in Division Rivals.</col>");
			return;
		}
		if (stock.kind == Kind.TITLE)
			amount = 1;
		int bought = 0;
		for (int i = 0; i < amount; i++) {
			if (player.rivalsPoints < stock.cost) {
				if (bought == 0)
					player.sendMessage("<col=ff0000>You do not have enough Rivals Points for this.</col>");
				break;
			}
			if (stock.kind == Kind.TITLE) {
				LoyaltyTitle title = LoyaltyTitle.get(stock.titleId);
				if (title == null) {
					player.sendMessage("That title isn't available right now.");
					return;
				}
				if (LoyaltyTitleManager.isUnlocked(player, title)) {
					player.sendMessage("You already have that title. Select it in the Titles screen.");
					return;
				}
				player.rivalsPoints -= stock.cost;
				LoyaltyTitleManager.unlock(player, title);
				bought++;
				continue;
			}
			int id = stock.iconItem, count = stock.amount;
			if (stock.kind == Kind.BLIGHTED) {
				ObjType def = ObjType.get(id);
				if (def != null && def.notedId > 0)
					id = def.notedId;
			}
			if (!player.getInventory().hasRoomFor(id, count)) {
				player.sendMessage("You don't have enough inventory space.");
				break;
			}
			player.rivalsPoints -= stock.cost;
			player.getInventory().add(id, count);
			bought++;
		}
		if (bought == 0)
			return;
		player.sendMessage("<col=990000><shad=000000>[Rivals]</shad></col> <col=ffffff>You bought " + (bought > 1 ? bought + " x " : "") + name(stock)
			+ " for " + NumberUtils.formatNumber((long) bought * stock.cost) + " Rivals Points.</col>");
		sendPoints(player);
		DivisionRivalsInterfaceManager.refreshIfOpen(player);
	}
}
