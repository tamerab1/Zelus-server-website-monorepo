package io.ruin.model.activities.tournament;

import io.ruin.api.utils.NumberUtils;
import io.ruin.cache.ObjType;
import io.ruin.model.ScrollbarClientScript;
import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.InterfaceHandler;
import io.ruin.model.inter.ToplevelComponent;
import io.ruin.model.inter.actions.DefaultAction;
import io.ruin.model.inter.handlers.LootsTables;
import io.ruin.model.item.Item;

/**
 * The Pit Tournament Shop (interface 1149, built by .dev/cache-restore-tool/src/BuildTournamentShopInterface.java):
 * a scrolling grid of up to 24 item cards (4 per row) bought with tournamentPoints. Opened by the Pit Championship Board's "Rewards" option.
 * Item model ops: Value / Buy 1 / Buy 5 / Examine; Buy button ops: Buy 1 / Buy 5 / Buy 10.
 */
public final class TournamentShop {

	private static final int INTERFACE = 1149;
	private static final int CLOSE = 2, TITLE = 3, POINTS = 4;
	private static final int SCROLL = 9, SCROLLBAR = 10;
	private static final int FIRST_CARD = 11, CARD_SIZE = 9, CARDS = 24;
	/** Card grid: 6 rows of 115px with 4px gaps, in a 235px-tall scroll container. */
	private static final int CONTENT_HEIGHT = 6 * 115 + 5 * 4, SCROLL_HEIGHT = 235;
	private static final int MODEL = 4, NAME = 5, COST = 6, BUTTON = 7;
	/** Card models are 40px tall; an inventory icon is 32. */
	private static final double MODEL_ZOOM_SCALE = 0.95;

	private record Stock(int itemId, int amount, int cost) {
	}

	/** Four per row, in the order the rows are shown. */
	private static final Stock[] STOCK = {
		new Stock(31694, 1, 200),               // Frostbite partyhat
		new Stock(31692, 1, 180),               // Inferno partyhat
		new Stock(31614, 1, 160),               // Prismatic partyhat
		new Stock(31690, 1, 150),               // Toxic partyhat

		new Stock(31698, 1, 160),               // Midnight santa hat
		new Stock(31616, 1, 150),               // Festive santa hat
		new Stock(31700, 1, 140),               // Candy cane santa hat
		new Stock(31702, 1, 120),               // Golden santa hat

		new Stock(31756, 1, 450),               // Cosmic Champion Gloves
		new Stock(31754, 1, 300),               // Infernal Fury Gloves
		new Stock(31750, 1, 60),                // Brawler's Golden Gloves
		new Stock(31748, 1, 100),               // Astral Spore

		new Stock(31662, 1, 80),                // Solarflare chausses
		new Stock(31660, 1, 80),                // Solarflare cuirass
		new Stock(31658, 1, 50),                // Solarflare helm
		new Stock(31608, 1, 50),                // Soulfire sword

		new Stock(31680, 1, 80),                // Starheart platebody
		new Stock(31682, 1, 80),                // Starheart platelegs
		new Stock(31678, 1, 60),                // Starheart full helm
		new Stock(31606, 1, 50),                // Frostbrand sword

		new Stock(BoxingMysteryChest.ID, 1, 100), // Tournament Chest
		new Stock(31642, 1, 40),                // Wildfang blade
		new Stock(31650, 1, 80),                // Skyhunter bow
		new Stock(31610, 1, 70),                // Voidrend axe
	};


	private TournamentShop() {
	}

	static void register() {
		InterfaceHandler.register(INTERFACE, h -> {
			h.actions[CLOSE] = (io.ruin.model.inter.actions.SimpleAction) p -> p.closeInterface(ToplevelComponent.MAINMODAL);
			for (int i = 0; i < Math.min(CARDS, STOCK.length); i++) {
				Stock stock = STOCK[i];
				int card = FIRST_CARD + i * CARD_SIZE;
				h.actions[card + MODEL] = (DefaultAction) (player, option, slot, itemId) -> {
					switch (option) {
						case 1 -> player.sendMessage(name(stock) + " costs " + NumberUtils.formatNumber(stock.cost) + " Tournament Points.");
						case 2 -> buy(player, stock, 1);
						case 3 -> buy(player, stock, 5);
						default -> examine(player, stock);
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
		ps.sendString(INTERFACE, TITLE, "The Pit Tournament Shop");
		sendPoints(player);
		for (int i = 0; i < CARDS; i++) {
			int card = FIRST_CARD + i * CARD_SIZE;
			boolean used = i < STOCK.length;
			ps.setHidden(INTERFACE, card, !used);
			if (!used)
				continue;
			Stock stock = STOCK[i];
			ObjType def = ObjType.get(stock.itemId);
			ps.sendItem(INTERFACE, card + MODEL, stock.itemId, stock.amount);
			ps.sendModelInformation(INTERFACE, card + MODEL, (int) (def.zoom2d * MODEL_ZOOM_SCALE), def.xan2d, def.yan2d);
			ps.sendString(INTERFACE, card + NAME, "<col=ff981f>" + name(stock) + "</col>");
			ps.sendString(INTERFACE, card + COST, "<col=00ff00>[Cost: " + NumberUtils.formatNumber(stock.cost) + " Pts]</col>");
		}
		// clientscript 10532 sizes the scroll area to count * 15 + height / 3
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
		player.getPacketSender().sendString(INTERFACE, POINTS, "<col=ffff00>Tournament Points:</col> <col=00ff00>"
			+ NumberUtils.formatNumber(player.tournamentPoints) + "</col>");
	}

	private static String name(Stock stock) {
		String name = ObjType.get(stock.itemId).name;
		return stock.amount > 1 ? name + " (x" + stock.amount + ")" : name;
	}

	private static void examine(Player player, Stock stock) {
		if (stock.itemId == BoxingMysteryChest.ID) {
			player.getLootsViewer().updateInterface(player, LootsTables.BOXING_MYSTERY_CHEST, "Tournament Chest");
			return;
		}
		Item.examine(player, stock.itemId);
	}

	private static void buy(Player player, Stock stock, int amount) {
		int bought = 0;
		for (int i = 0; i < amount; i++) {
			if (player.tournamentPoints < stock.cost) {
				if (bought == 0)
					player.sendMessage("<col=ff0000>You do not have enough Tournament Points to purchase this item.</col>");
				break;
			}
			if (!player.getInventory().hasRoomFor(stock.itemId, stock.amount)) {
				player.sendMessage("You don't have enough inventory space.");
				break;
			}
			player.tournamentPoints -= stock.cost;
			player.getInventory().add(stock.itemId, stock.amount);
			bought++;
		}
		if (bought == 0)
			return;
		player.sendMessage("<col=005f9e>[Shop]</col> <col=ffffff>You bought " + (bought > 1 ? bought + " x " : "") + name(stock) + " for "
			+ NumberUtils.formatNumber((long) bought * stock.cost) + " Tournament Points.</col>");
		sendPoints(player);
	}
}
