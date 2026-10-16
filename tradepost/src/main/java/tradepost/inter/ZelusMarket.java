package tradepost.inter;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import core.task.Continuations;
import io.ruin.cache.ObjType;
import io.ruin.model.ScrollbarClientScript;
import io.ruin.model.World;
import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.InterfaceHandler;
import io.ruin.model.inter.ToplevelComponent;
import io.ruin.model.inter.actions.SimpleAction;
import io.ruin.model.inter.dialogue.OptionsDialogue;
import io.ruin.model.inter.dialogue.YesNoDialogue;
import io.ruin.model.inter.handlers.ItemStatsViewer;
import io.ruin.model.inter.utils.Option;
import io.ruin.network.PacketSender;
import lombok.experimental.ExtensionMethod;
import tradepost.TradePost;
import tradepost.TradePostCache;
import tradepost.TradePostCoffer;
import tradepost.TradePostFeatured;
import tradepost.TradePostHandler;
import tradepost.TradePostHistory;
import tradepost.TradePostOffer;
import tradepost.db.TradePostCoffersDb;
import tradepost.hook.Attributes;
import tradepost.module.Module;

/**
 * The Zelus Market (interface 1161, built by .dev/cache-restore-tool/src/BuildMarketInterface.java), the player
 * exchange that replaced the Reason trade post screens 1131/1132. It only draws and dispatches; every trade goes
 * through the {@link TradePost} engine (atomic instant buys, offer matching, the collection box).
 * <ul>
 *     <li>Browse: every live sell listing (search by item or seller, sort, pages of 40);</li>
 *     <li>Featured: promoted listings (gold framed);</li>
 *     <li>My Shop: your offer slots (cancel / feature) and collection box (coins + items), Sell item / Buy offer;</li>
 *     <li>History: your recent buys and sales;</li>
 *     <li>Lookup: every seller with a shop, then one seller's listings.</li>
 * </ul>
 * The 1133 inventory side panel stays open beside it: clicking an inventory item lists it for sale.
 */
@ExtensionMethod(Attributes.class)
public final class ZelusMarket {

	public static final int INTERFACE = 1161;
	private static final int SIDE_INVENTORY = 1133;
	public static final String TAG = "<col=ff981f><shad=000000>[Zelus Market]</shad></col> ";

	private static final int CLOSE = 2, TITLE = 3, COFFER = 4, STATS = 5;
	/**
	 * Tabs, toggles, sort and pager are OSRS bezel buttons: button (click), active parts (dark recess + gold trim) at
	 * +1..+2, label +3.
	 */
	private static final int FIRST_TAB = 6, TAB_STRIDE = 4, TAB_ACTIVE = 1, TAB_ACTIVE_PARTS = 2, TAB_LABEL = 3;
	private static final int FILTER_BAR = 26, SEARCH = 27, SEARCH_TEXT = 28, SEARCH_CLEAR = 31;
	private static final int TOGGLE_ITEMS = 33, TOGGLE_PLAYERS = 37, SEGMENT_LABEL = 3, SORT = 41, BUTTON_LABEL = 3;
	private static final int ACTION_BAR = 45, ACTION_SELL = 46, ACTION_BUY = 48, ACTION_COINS = 50, ACTION_ITEMS = 52;
	private static final int SCROLL = 68, SCROLLBAR = 69, LIST_HEIGHT = 176;
	private static final int SHOWING = 872, PREV = 873, PAGE = 877, NEXT = 878;
	/** Pager arrow sprites over the buttons (BuildBrandIcons): gold when the page can turn, dim otherwise. */
	private static final int PREV_ARROW = 901, NEXT_ARROW = 902, ARROW_LEFT = 24528, ARROW_RIGHT = 24529,
		ARROW_LEFT_DIM = 24530, ARROW_RIGHT_DIM = 24531;
	/**
	 * Rows (46px): layer, box, border, featured trim + stripe, item, name, seller line, then the stock bar (track, fill,
	 * fill highlight, border, text over it), price, total, button + label.
	 */
	private static final int FIRST_ROW = 70, ROW_STRIDE = 20, ROWS = 40, ROW_PITCH = 48;
	/** +5/+6: the sunken item slot (well + rim, 2026-10-15) sits under the item. */
	private static final int R_BOX = 1, R_GOLD = 3, R_ITEM = 7, R_NAME = 8, R_INFO = 9, R_TRACK = 10, R_FILL = 11,
		R_SHINE = 12, R_DEPTH = 13, R_EDGE = 14, R_BAR_TEXT = 15, R_PRICE = 16, R_TOTAL = 17, R_BUTTON = 18, BAR_WIDTH = 66;
	private static final int EMPTY = 870;
	private static final int MODAL = 882, MODAL_ITEM = 888, MODAL_TITLE = 889, MODAL_INFO = 890, MODAL_BUY_1 = 891,
		MODAL_BUY_5 = 893, MODAL_BUY_X = 895, MODAL_BUY_ALL = 897, MODAL_CANCEL = 899;

	/**
	 * Clientscript 10754: set the stock bar fill's width at the slim bar's 6px height (a copy of 10606, which forces an
	 * 18px height and stretched the old bar over the "(total)" line). Clientscript 45: set a component's colour.
	 */
	private static final int SET_FILL_WIDTH = 10754, SET_COLOUR = 45;
	/** Muted green while healthy, dark gold when half or less is left (was neon green / orange). */
	private static final int BAR_GREEN = 0x2e7d32, BAR_LOW = 0xc69e38;

	private static final int BROWSE = 0, FEATURED = 1, MY_SHOP = 2, HISTORY = 3, LOOKUP = 4;
	private static final String[] TABS = {"Browse", "Featured", "My Shop", "History", "Lookup"};
	private static final String[] SORTS = {"Newest first", "Price: Low to High", "Price: High to Low"};

	private static final String FEATURED_COL = "<col=c8a050>";
	private static final String ORANGE = "<col=ff981f>", GOLD = "<col=ffc35a>", YELLOW = "<col=ffff00>",
		GREY = "<col=9a9a9a>", WHITE = "<col=ffffff>", GREEN = "<col=00ff00>", RED = "<col=ff0000>",
		DIM = "<col=9e9e9e>", PLAYER = "<col=e0e0e0>", TAN = "<col=d8d0c0>", DARK = "<col=5a5045>", VACANT = "<col=6f6f6f>", END = "</col>";

	/** Per-player view state (lives on TradePostPlayer). */
	public static final class State {
		int tab = BROWSE;
		String search;
		boolean searchPlayers;
		int sort;
		int page;
		/** Lookup tab: whose shop is being viewed (null = the seller list). */
		String lookup;
		final List<Row> rows = new ArrayList<>();
		int pages = 1;
		/** Rows on all pages of the current view. */
		int total;
		int sentRows = -1;
		/** The listing in the buy modal (null = closed). */
		TradePostOffer modalOffer;
	}

	/** One list row: exactly one of offer / entry / history / seller is set. */
	record Row(TradePostOffer offer, TradePostCoffersDb.CofferEntry entry, TradePostHistory history, String seller,
			int count) {
	}

	private ZelusMarket() {
	}

	public static void register() {
		InterfaceHandler.register(INTERFACE, h -> {
			h.actions[CLOSE] = (SimpleAction) Player::closeInterfaces;
			for (int t = 0; t < TABS.length; t++) {
				int tab = t;
				h.actions[FIRST_TAB + t * TAB_STRIDE] = (SimpleAction) p -> {
					var s = state(p);
					s.tab = tab;
					s.page = 0;
					s.lookup = null;
					s.search = null;
					s.modalOffer = null;
					draw(p, true);
				};
			}
			h.actions[SEARCH] = (SimpleAction) ZelusMarket::promptSearch;
			h.actions[SEARCH_CLEAR] = (SimpleAction) p -> {
				var s = state(p);
				if (s.search != null) {
					s.search = null;
					s.page = 0;
					draw(p, true);
				}
			};
			h.actions[TOGGLE_ITEMS] = (SimpleAction) p -> setSearchPlayers(p, false);
			h.actions[TOGGLE_PLAYERS] = (SimpleAction) p -> setSearchPlayers(p, true);
			h.actions[SORT] = (SimpleAction) p -> {
				var s = state(p);
				s.sort = (s.sort + 1) % SORTS.length;
				s.page = 0;
				draw(p, true);
			};
			h.actions[PREV] = (SimpleAction) p -> turnPage(p, -1);
			h.actions[NEXT] = (SimpleAction) p -> turnPage(p, 1);
			h.actions[ACTION_SELL] = (SimpleAction) p -> p.sendMessage(TAG
					+ "Click an item in your inventory to list it for sale.");
			h.actions[ACTION_BUY] = (SimpleAction) TradePostHandler::promptBuyOffer;
			h.actions[ACTION_COINS] = (SimpleAction) TradePostCoffer::collectCoins;
			h.actions[ACTION_ITEMS] = (SimpleAction) TradePostCoffer::collectAll;
			for (int r = 0; r < ROWS; r++) {
				int index = r;
				int row = FIRST_ROW + r * ROW_STRIDE;
				h.actions[row + R_BOX] = (SimpleAction) p -> select(p, index, false);
				h.actions[row + R_BUTTON] = (SimpleAction) p -> select(p, index, true);
				h.actions[row + R_ITEM] = (SimpleAction) p -> {
					var item = rowItem(row(p, index));
					if (item > 0) {
						examine(p, item);
					}
				};
			}
			h.actions[MODAL_ITEM] = (SimpleAction) p -> {
				var offer = state(p).modalOffer;
				if (offer != null) {
					examine(p, offer.getItemId());
				}
			};
			h.actions[MODAL_BUY_1] = (SimpleAction) p -> buy(p, 1);
			h.actions[MODAL_BUY_5] = (SimpleAction) p -> buy(p, 5);
			h.actions[MODAL_BUY_ALL] = (SimpleAction) p -> buy(p, Integer.MAX_VALUE);
			h.actions[MODAL_BUY_X] = (SimpleAction) p -> {
				var offer = state(p).modalOffer;
				if (offer == null) {
					return;
				}
				p.integerInput("How many would you like to buy? (" + offer.amount + " available)", amount -> {
					if (state(p).modalOffer == offer) {
						buy(p, amount);
					}
					reopen(p);
				});
			};
			h.actions[MODAL_CANCEL] = (SimpleAction) p -> {
				state(p).modalOffer = null;
				draw(p, false);
			};
			h.closedAction = (p, i) -> {
				state(p).modalOffer = null;
				if (p.isVisibleInterface(SIDE_INVENTORY)) {
					p.closeInterface(ToplevelComponent.INVENTORY_TAB_AREA);
				}
			};
		});
	}

	private static State state(Player player) {
		return player.tradePost().market;
	}

	/* ---------------- opening ---------------- */

	public static void open(Player player) {
		open(player, -1);
	}

	/** Opens the market (on a given tab, or -1 for the last one); refuses ironmen and while disabled. */
	public static void open(Player player, int tab) {
		if (!Module.ENABLED) {
			player.sendMessage("The Zelus Market is closed for maintenance; please try again later.");
			return;
		}
		if (player.getGameMode().isIronMan()) {
			player.sendMessage("Your game mode prevents you from using the Zelus Market.");
			return;
		}
		var s = state(player);
		if (tab >= 0) {
			s.tab = tab;
			s.page = 0;
			s.lookup = null;
			s.search = null;
		}
		s.modalOffer = null;
		player.openInterface(ToplevelComponent.MAINMODAL, INTERFACE);
		if (!player.isVisibleInterface(SIDE_INVENTORY)) {
			player.openInterface(ToplevelComponent.INVENTORY_TAB_AREA, SIDE_INVENTORY);
			player.getPacketSender().sendIfEvents(SIDE_INVENTORY, 0, 0, 27, 1086);
		}
		var ps = player.getPacketSender();
		ps.sendString(INTERFACE, TITLE, ORANGE + "Zelus Market" + END);
		draw(player, true);
	}

	public static int myShopTab() {
		return MY_SHOP;
	}

	/** After an input box (which closes the screen): reopen on the same view. */
	private static void reopen(Player player) {
		if (player.isOnline()) {
			open(player, -1);
		}
	}

	/** Engine callback: redraw if the market is open (keeps the scroll position unless the row count changed). */
	public static void refresh(Player player) {
		if (player != null && player.isVisibleInterface(INTERFACE)) {
			draw(player, false);
		}
	}

	/* ---------------- drawing ---------------- */

	private static void draw(Player player, boolean resetScroll) {
		var s = state(player);
		var ps = player.getPacketSender();
		var pTradePost = player.tradePost();

		// header
		ps.sendString(INTERFACE, COFFER, DIM + "Coffer:" + END + " " + GREEN + coins(pTradePost.cofferCoins) + " gp" + END
				+ (pTradePost.coffer.isEmpty() ? "" : DIM + "  +  " + END + YELLOW + pTradePost.coffer.size() + " item"
						+ (pTradePost.coffer.size() == 1 ? "" : "s") + END));
		var live = TradePostCache.offers().filter(o -> o.isSell() && !o.isEmpty()).count();
		ps.sendString(INTERFACE, STATS, s.tab == LOOKUP && s.lookup != null
				? DIM + "Viewing: " + END + RED + display(s.lookup) + END + DIM + "'s shop" + END
				: DIM + "Listings:" + END + " " + WHITE + live + " active" + END);

		// tabs
		for (int t = 0; t < TABS.length; t++) {
			int tab = FIRST_TAB + t * TAB_STRIDE;
			boolean active = t == s.tab;
			for (int k = 0; k < TAB_ACTIVE_PARTS; k++) {
				ps.setHidden(INTERFACE, tab + TAB_ACTIVE + k, !active);
			}
			ps.sendString(INTERFACE, tab + TAB_LABEL, (active ? GOLD : TAN) + TABS[t] + END);
		}

		// filter bar / My Shop action bar
		boolean shop = s.tab == MY_SHOP;
		ps.setHidden(INTERFACE, FILTER_BAR, shop);
		ps.setHidden(INTERFACE, ACTION_BAR, !shop);
		buildRows(player);
		if (shop) {
			ps.sendString(INTERFACE, ACTION_SELL + 1, ORANGE + "Sell item" + END);
			ps.sendString(INTERFACE, ACTION_BUY + 1, ORANGE + "Buy offer" + END);

			boolean coins = pTradePost.cofferCoins.signum() > 0;
			ps.sendString(INTERFACE, ACTION_COINS + 1, coins ? GREEN + "Collect (" + coins(pTradePost.cofferCoins) + " gp)" + END
					: VACANT + "Collect (0 gp)" + END);
			ps.sendString(INTERFACE, ACTION_ITEMS + 1, pTradePost.coffer.isEmpty() ? VACANT + "Collect items (0)" + END
					: GREEN + "Collect items (" + pTradePost.coffer.size() + ")" + END);
		} else {
			boolean lookupList = s.tab == LOOKUP && s.lookup == null;
			String placeholder = lookupList ? "Search players..." : "Search items or players...";
			ps.sendString(INTERFACE, SEARCH_TEXT, s.search != null ? WHITE + s.search + END : GREY + placeholder + END);
			ps.sendString(INTERFACE, SEARCH_CLEAR + 1, s.search != null ? ORANGE + "Clear" + END : GREY + "Clear" + END);
			boolean players = s.searchPlayers || lookupList;
			segment(ps, TOGGLE_ITEMS, !players, "Items");
			segment(ps, TOGGLE_PLAYERS, players, "Players");
			ps.sendString(INTERFACE, SORT + BUTTON_LABEL, TAN + SORTS[s.sort] + END);
		}
		// footer: range + pager (all tabs)
		int first = s.page * ROWS;
		ps.sendString(INTERFACE, SHOWING, s.total == 0 ? "" : "Showing " + (first + 1) + "-" + (first + s.rows.size())
				+ " of " + s.total);
		ps.sendString(INTERFACE, PAGE, (s.page + 1) + " / " + s.pages);
		ps.sendString(INTERFACE, PREV + BUTTON_LABEL, "");
		ps.sendString(INTERFACE, NEXT + BUTTON_LABEL, "");
		ps.setGraphic(INTERFACE, PREV_ARROW, s.page > 0 ? ARROW_LEFT : ARROW_LEFT_DIM);
		ps.setGraphic(INTERFACE, NEXT_ARROW, s.page + 1 < s.pages ? ARROW_RIGHT : ARROW_RIGHT_DIM);

		// rows
		for (int r = 0; r < ROWS; r++) {
			int row = FIRST_ROW + r * ROW_STRIDE;
			Row data = r < s.rows.size() ? s.rows.get(r) : null;
			ps.setHidden(INTERFACE, row, data == null);
			if (data != null) {
				drawRow(player, ps, row, data);
			}
		}
		ps.setHidden(INTERFACE, EMPTY, !s.rows.isEmpty());
		if (s.rows.isEmpty()) {
			ps.sendString(INTERFACE, EMPTY, emptyText(s));
		}
		if (resetScroll || s.sentRows != s.rows.size()) {
			int contentHeight = s.rows.size() * ROW_PITCH - 2;
			int count = contentHeight <= LIST_HEIGHT ? 0 : (int) Math.ceil((contentHeight - LIST_HEIGHT / 3D) / 15D);
			ScrollbarClientScript.create().interfaceId(INTERFACE).containerId(SCROLL).scrollbarChildId(SCROLLBAR)
					.childrenCount(count).withDarkGraphics().build().send(player);
			s.sentRows = s.rows.size();
		}

		drawModal(player, ps);
	}

	private static String emptyText(State s) {
		return switch (s.tab) {
			case FEATURED -> "No featured listings right now.<br>" + GREY + "Feature one of yours from My Shop." + END;
			case MY_SHOP -> "You have no offers or items waiting.<br>" + GREY
					+ "Click an inventory item to sell it, or use Buy offer." + END;
			case HISTORY -> "No trades yet.";
			case LOOKUP -> s.lookup != null ? display(s.lookup) + " has nothing listed." : "No sellers found.";
			default -> s.search != null ? "Nothing matches \"" + s.search + "\"." : "The market is empty.";
		};
	}

	/** A toggle button: recessed + gold trim when selected. */
	private static void segment(PacketSender ps, int component, boolean selected, String label) {
		for (int k = 1; k <= 2; k++) {
			ps.setHidden(INTERFACE, component + k, !selected);
		}
		ps.sendString(INTERFACE, component + SEGMENT_LABEL, (selected ? GOLD : TAN) + label + END);
	}

	private static void drawRow(Player player, PacketSender ps, int row, Row data) {
		boolean featured = false;
		boolean bar = false;
		// My Shop: the bar shows how much of YOUR offer has traded (0 sold = an empty track); listings show stock left
		boolean progress = false;
		String button = null;
		int itemId = rowItem(data);
		int itemAmount = 1;
		String name, info, price = "", total = "";

		if (data.offer() != null && data.offer().owner.equalsIgnoreCase(player.uuid())) {
			// My Shop: one of my offers
			var o = data.offer();
			featured = o.isSell() && TradePostFeatured.isFeatured(o);
			itemAmount = o.getAmount();
			name = ORANGE + itemName(itemId) + END + (o.isSell() ? GREEN + " (Selling)" + END : YELLOW + " (Buying)" + END)
					+ (featured ? FEATURED_COL + "  Featured " + hours(TradePostFeatured.secondsLeft(o)) + END : "");
			var done = o.getStartAmount() - o.getAmount();
			info = (o.isSell() ? done + " sold" : done + " bought") + " · " + ago(o.getTimeStamp());
			price = colouredCoins(o.priceEach());
			total = DIM + "(" + coins(o.priceEach().multiply(o.amount)) + " left)" + END;
			bar = true;
			progress = true;
			button = "Cancel";
		} else if (data.offer() != null) {
			// a listing (Browse / Featured / a seller's shop)
			var o = data.offer();
			featured = TradePostFeatured.isFeatured(o);
			itemAmount = o.getAmount();
			name = ORANGE + itemName(itemId) + END + (featured ? FEATURED_COL + "  Featured" + END : "");
			// short enough for the column: the amount is already on the item icon and in the Stock column
			info = "from " + PLAYER + display(o.owner) + END + " · " + ago(o.getTimeStamp());
			price = colouredCoins(o.priceEach());
			total = DIM + "(" + coins(o.priceEach().multiply(o.amount)) + " total)" + END;
			bar = true;
			button = "Buy";
		} else if (data.entry() != null) {
			var e = data.entry();
			itemAmount = e.itemAmount();
			name = ORANGE + itemName(itemId) + END;
			info = "Waiting in your collection box";
			button = "Collect";
		} else if (data.history() != null) {
			var h = data.history();
			itemAmount = h.getItem().getAmount();
			name = ORANGE + itemName(itemId) + END + (itemAmount > 1 ? WHITE + " x " + itemAmount + END : "");
			info = (h.getOfferType() == 1 ? "Sold to " : "Bought from ") + PLAYER + display(h.getSeller()) + END
					+ " · " + ago(h.getAge());
			price = colouredCoins(h.getPrice());
			total = DIM + "(" + coins(h.getPrice().multiply(BigInteger.valueOf(itemAmount))) + " total)" + END;
		} else {
			// Lookup: a seller
			name = ORANGE + display(data.seller()) + END;
			info = data.count() + " listing" + (data.count() == 1 ? "" : "s");
			var worth = TradePostCache.offers().filter(o -> o.isSell() && !o.isEmpty() && o.owner.equalsIgnoreCase(data.seller()))
					.map(o -> o.priceEach().multiply(o.amount)).reduce(BigInteger.ZERO, BigInteger::add);
			total = DIM + "(" + coins(worth) + " listed)" + END;
			button = "View";
		}

		ps.setHidden(INTERFACE, row + R_GOLD, !featured);
		ps.setHidden(INTERFACE, row + R_GOLD + 1, !featured);
		ps.sendItem(INTERFACE, row + R_ITEM, itemId, Math.max(1, itemAmount));
		ps.sendString(INTERFACE, row + R_NAME, name);
		ps.sendString(INTERFACE, row + R_INFO, info);
		for (int part = R_TRACK; part <= R_BAR_TEXT; part++) {
			// the old highlight / shade lines stay hidden: the slim bar is a flat fill
			ps.setHidden(INTERFACE, row + part, !bar || part == R_SHINE || part == R_DEPTH);
		}
		if (bar) {
			var o = data.offer();
			int start = Math.max(0, o.getStartAmount()), traded = Math.max(0, start - o.getAmount());
			double ratio = start <= 0 ? 0 : (progress ? (double) traded : o.getAmount()) / start;
			int filled = progress ? traded : o.getAmount();
			int width = filled <= 0 ? 0 : Math.max(2, (int) Math.round(BAR_WIDTH * ratio));
			boolean healthy = progress || ratio > 0.5;
			ps.sendClientScript(SET_FILL_WIDTH, "Ii", INTERFACE << 16 | (row + R_FILL), width);
			ps.sendClientScript(SET_COLOUR, "Ii", INTERFACE << 16 | (row + R_FILL), healthy ? BAR_GREEN : BAR_LOW);
			String what = progress ? traded + " / " + start + (o.isSell() ? " sold" : " bought") : o.getAmount() + " / " + start + " left";
			ps.sendString(INTERFACE, row + R_BAR_TEXT, WHITE + "<shad=000000>" + what + "</shad>" + END);
		}
		ps.sendString(INTERFACE, row + R_PRICE, price);
		ps.sendString(INTERFACE, row + R_TOTAL, total);
		ps.setHidden(INTERFACE, row + R_BUTTON, button == null);
		ps.setHidden(INTERFACE, row + R_BUTTON + 1, button == null);
		if (button != null) {
			ps.sendString(INTERFACE, row + R_BUTTON + 1, ORANGE + "<shad=000000>" + button + "</shad>" + END);
		}
	}

	private static void drawModal(Player player, PacketSender ps) {
		var s = state(player);
		var offer = s.modalOffer;
		if (offer != null && (offer.isEmpty() || !TradePostCache.contains(offer))) {
			s.modalOffer = offer = null;
		}
		ps.setHidden(INTERFACE, MODAL, offer == null);
		// the modal sits over the list: hide the rows while it is up (no click-through)
		ps.setHidden(INTERFACE, SCROLL, offer != null);
		ps.setHidden(INTERFACE, SCROLLBAR, offer != null);
		if (offer == null) {
			return;
		}
		var itemId = offer.getItemId();
		ps.sendItem(INTERFACE, MODAL_ITEM, itemId, offer.getAmount());
		ps.sendString(INTERFACE, MODAL_TITLE, itemName(itemId));
		var wallet = BigInteger.valueOf(player.getInventory().getAmount(995))
				.add(BigInteger.valueOf(player.getInventory().getAmount(13204)).multiply(BigInteger.valueOf(1000)));
		ps.sendString(INTERFACE, MODAL_INFO, "Price: " + colouredCoins(offer.priceEach()) + " each<br>"
				+ "Available: " + WHITE + offer.getAmount() + END + "<br>"
				+ "Seller: " + RED + display(offer.owner) + END + "<br>"
				+ "Your coins: " + colouredCoins(wallet));
		ps.sendString(INTERFACE, MODAL_BUY_1 + 1, "Buy 1");
		ps.sendString(INTERFACE, MODAL_BUY_5 + 1, "Buy 5");
		ps.sendString(INTERFACE, MODAL_BUY_X + 1, "Buy X");
		ps.sendString(INTERFACE, MODAL_BUY_ALL + 1, "Buy All");
		ps.sendString(INTERFACE, MODAL_CANCEL + 1, "Cancel");
	}

	/* ---------------- rows per tab ---------------- */

	private static void buildRows(Player player) {
		var s = state(player);
		var all = new ArrayList<Row>();
		var me = player.uuid();
		switch (s.tab) {
			case MY_SHOP -> {
				for (var offer : player.tradePost().offers) {
					if (offer != null && !offer.isEmpty()) {
						all.add(new Row(offer, null, null, null, 0));
					}
				}
				for (var entry : player.tradePost().coffer) {
					all.add(new Row(null, entry, null, null, 0));
				}
			}
			case HISTORY -> {
				var list = new ArrayList<>(player.tradePost().history);
				list.removeIf(h -> s.search != null && !(s.searchPlayers
						? h.getSeller().toLowerCase(Locale.ROOT).contains(s.search)
						: itemName(h.getItem().getId()).toLowerCase(Locale.ROOT).contains(s.search)));
				Comparator<TradePostHistory> order = switch (s.sort) {
					case 1 -> Comparator.comparing(TradePostHistory::getPrice);
					case 2 -> Comparator.comparing(TradePostHistory::getPrice).reversed();
					default -> Comparator.comparingLong(TradePostHistory::getAge).reversed();
				};
				list.sort(order);
				list.forEach(h -> all.add(new Row(null, null, h, null, 0)));
			}
			case LOOKUP -> {
				if (s.lookup != null) {
					listings(s, o -> o.owner.equalsIgnoreCase(s.lookup)).forEach(o -> all.add(new Row(o, null, null, null, 0)));
				} else {
					Map<String, Integer> sellers = new LinkedHashMap<>();
					TradePostCache.offers()
							.filter(o -> o.isSell() && !o.isEmpty())
							.sorted(Comparator.comparingLong(TradePostOffer::getTimeStamp).reversed())
							.forEach(o -> sellers.merge(o.owner.toLowerCase(Locale.ROOT), 1, Integer::sum));
					sellers.forEach((seller, count) -> {
						if (s.search == null || seller.contains(s.search)) {
							all.add(new Row(null, null, null, seller, count));
						}
					});
				}
			}
			case FEATURED -> listings(s, o -> !o.owner.equalsIgnoreCase(me) && TradePostFeatured.isFeatured(o))
					.forEach(o -> all.add(new Row(o, null, null, null, 0)));
			default -> {
				// featured listings first, then the rest in the chosen order
				var list = listings(s, o -> !o.owner.equalsIgnoreCase(me));
				var featured = list.stream().filter(TradePostFeatured::isFeatured).toList();
				featured.forEach(o -> all.add(new Row(o, null, null, null, 0)));
				list.stream().filter(o -> !TradePostFeatured.isFeatured(o)).forEach(o -> all.add(new Row(o, null, null, null, 0)));
			}
		}
		s.total = all.size();
		s.pages = Math.max(1, (all.size() + ROWS - 1) / ROWS);
		s.page = Math.max(0, Math.min(s.page, s.pages - 1));
		s.rows.clear();
		s.rows.addAll(all.subList(s.page * ROWS, Math.min(all.size(), (s.page + 1) * ROWS)));
	}

	/** Live sell listings matching the filter and the search box, in the chosen sort order. */
	private static List<TradePostOffer> listings(State s, java.util.function.Predicate<TradePostOffer> filter) {
		Comparator<TradePostOffer> order = switch (s.sort) {
			case 1 -> Comparator.comparing(TradePostOffer::priceEach);
			case 2 -> Comparator.comparing(TradePostOffer::priceEach).reversed();
			default -> Comparator.comparingLong(TradePostOffer::getTimeStamp).reversed();
		};
		return TradePostCache.offers()
				.filter(o -> o.isSell() && !o.isEmpty())
				.filter(filter)
				.filter(o -> s.search == null || (s.searchPlayers && s.tab != LOOKUP
						? o.owner.toLowerCase(Locale.ROOT).contains(s.search)
						: itemName(o.getItemId()).toLowerCase(Locale.ROOT).contains(s.search)))
				.sorted(order)
				.collect(Collectors.toList());
	}

	private static Row row(Player player, int index) {
		var rows = state(player).rows;
		return index < rows.size() ? rows.get(index) : null;
	}

	private static int rowItem(Row row) {
		if (row == null) {
			return -1;
		}
		if (row.offer() != null) {
			return row.offer().getItemId();
		}
		if (row.entry() != null) {
			return ObjType.unnotedId(row.entry().itemId());
		}
		if (row.history() != null) {
			return row.history().getItem().getId();
		}
		return TradePostCache.offers().filter(o -> o.isSell() && !o.isEmpty() && o.owner.equalsIgnoreCase(row.seller()))
				.max(Comparator.comparingLong(TradePostOffer::getTimeStamp)).map(TradePostOffer::getItemId).orElse(995);
	}

	/* ---------------- actions ---------------- */

	private static void select(Player player, int index, boolean button) {
		var s = state(player);
		var row = row(player, index);
		if (row == null || s.modalOffer != null) {
			return;
		}
		if (row.offer() != null && row.offer().owner.equalsIgnoreCase(player.uuid())) {
			manageOffer(player, row.offer(), button);
		} else if (row.offer() != null) {
			var offer = row.offer();
			if (offer.isEmpty() || !TradePostCache.contains(offer)) {
				player.sendMessage(TAG + "That listing has just sold out.");
				draw(player, false);
				return;
			}
			s.modalOffer = offer;
			draw(player, false);
		} else if (row.entry() != null) {
			var entry = row.entry();
			Continuations.schedule(() -> {
				TradePostCoffer.collect(entry, player).await();
				TradePost.refreshViews(player);
			});
		} else if (row.seller() != null) {
			s.lookup = row.seller();
			s.search = null;
			s.page = 0;
			draw(player, true);
		}
	}

	/** My Shop: the button cancels (with a confirm); the row offers Feature / Cancel. */
	private static void manageOffer(Player player, TradePostOffer offer, boolean button) {
		var name = itemName(offer.getItemId());
		Runnable cancel = () -> player.dialogue(new YesNoDialogue("Cancel this offer?",
				(offer.isSell() ? "Your unsold " + name + " will be returned." : "Your unspent coins will be returned."),
				offer.getItemId(), Math.max(1, offer.getAmount()), () -> Continuations.schedule(() -> {
					TradePost.abort(player, offer).await();
					reopen(player);
				})));
		if (button || offer.isBuy()) {
			cancel.run();
			return;
		}
		var left = TradePostFeatured.secondsLeft(offer);
		player.dialogue(new OptionsDialogue(name + (left > 0 ? " (featured " + hours(left) + ")" : ""),
				new Option("Feature it for 24 hours (" + coins(BigInteger.valueOf(TradePostFeatured.FEE)) + " coins)",
						() -> feature(player, offer)),
				new Option("Cancel this listing", cancel),
				new Option("Never mind", () -> reopen(player))));
	}

	private static void feature(Player player, TradePostOffer offer) {
		if (offer.isEmpty() || !TradePostCache.contains(offer) || !offer.owner.equalsIgnoreCase(player.uuid())) {
			player.sendMessage(TAG + "That listing is no longer active.");
			reopen(player);
			return;
		}
		if (!player.getInventory().removeCoinsOrPlatTokens(TradePostFeatured.FEE)) {
			player.sendMessage(TAG + "You need " + coins(BigInteger.valueOf(TradePostFeatured.FEE))
					+ " coins in your inventory to feature a listing.");
			reopen(player);
			return;
		}
		TradePostFeatured.feature(offer);
		player.sendMessage(TAG + "Your " + itemName(offer.getItemId()) + " is now featured for "
				+ hours(TradePostFeatured.secondsLeft(offer)) + ".");
		reopen(player);
	}

	private static void buy(Player player, int amount) {
		var s = state(player);
		var offer = s.modalOffer;
		if (offer == null) {
			return;
		}
		// close the modal before buying: a second click in the same tick finds no listing
		s.modalOffer = null;
		if (amount == Integer.MAX_VALUE) {
			amount = offer.isEmpty() ? 0 : offer.getAmount();
		}
		TradePost.buyNow(player, offer, amount);
		if (player.isVisibleInterface(INTERFACE)) {
			draw(player, false);
		}
	}

	private static void promptSearch(Player player) {
		var s = state(player);
		var lookupList = s.tab == LOOKUP && s.lookup == null;
		player.stringInput(lookupList ? "Search sellers:" : s.searchPlayers ? "Search sellers:" : "Search items:", text -> {
			var query = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
			s.search = query.isEmpty() ? null : query;
			s.page = 0;
			if (s.tab == MY_SHOP) {
				s.tab = BROWSE;
			}
			reopen(player);
		});
	}

	private static void setSearchPlayers(Player player, boolean players) {
		var s = state(player);
		if (s.searchPlayers != players) {
			s.searchPlayers = players;
			s.page = 0;
			draw(player, true);
		}
	}

	private static void turnPage(Player player, int delta) {
		var s = state(player);
		int page = s.page + delta;
		if (page < 0 || page >= s.pages) {
			return;
		}
		s.page = page;
		draw(player, true);
	}

	private static void examine(Player player, int itemId) {
		var def = ObjType.get(itemId);
		if (def == null) {
			return;
		}
		player.sendMessage("<col=000080>" + def.name + "</col>: <col=000000>" + def.examineText() + "</col>");
		ItemStatsViewer.open(player, itemId, () -> reopen(player));
	}

	/* ---------------- formatting ---------------- */

	private static String itemName(int itemId) {
		var def = ObjType.get(itemId);
		return def == null ? "Unknown item" : def.name;
	}

	/** A player name for display: the online player's own spelling, else the stored name capitalised. */
	private static String display(String uid) {
		if (uid == null || uid.isEmpty()) {
			return "Unknown";
		}
		var online = World.getPlayer(uid);
		if (online != null) {
			return online.getName();
		}
		var parts = uid.split(" ");
		var out = new StringBuilder();
		for (var part : parts) {
			if (out.length() > 0) {
				out.append(' ');
			}
			if (!part.isEmpty()) {
				out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
			}
		}
		return out.toString();
	}

	/** 1,500 / 200K / 19.6M / 1.5B. */
	public static String coins(BigInteger amount) {
		var value = amount.max(BigInteger.ZERO);
		if (value.compareTo(BigInteger.valueOf(100_000)) < 0) {
			return String.format(Locale.ROOT, "%,d", value.longValue());
		}
		if (value.compareTo(BigInteger.valueOf(1_000_000)) < 0) {
			return value.divide(BigInteger.valueOf(1_000)) + "K";
		}
		if (value.compareTo(BigInteger.valueOf(1_000_000_000)) < 0) {
			return decimal(value, 1_000_000) + "M";
		}
		return decimal(value, 1_000_000_000) + "B";
	}

	private static String decimal(BigInteger value, long unit) {
		var scaled = new BigDecimal(value).divide(BigDecimal.valueOf(unit), 1, RoundingMode.DOWN).stripTrailingZeros();
		return scaled.toPlainString();
	}

	/** Coins in a colour by size: green below 1M, white in millions, yellow in billions. */
	private static String colouredCoins(BigInteger amount) {
		// one bright price colour (2026-10-15 market polish); the total sits beside it in grey
		return GREEN + coins(amount) + END;
	}

	private static String ago(long epochSeconds) {
		if (epochSeconds <= 0) {
			return "a while ago";
		}
		long seconds = Math.max(0, Instant.now().getEpochSecond() - epochSeconds);
		if (seconds < 60) {
			return "just now";
		}
		if (seconds < 3600) {
			return (seconds / 60) + "m ago";
		}
		if (seconds < 86400) {
			return (seconds / 3600) + "h ago";
		}
		return (seconds / 86400) + "d ago";
	}

	private static String hours(long seconds) {
		if (seconds >= 3600) {
			return (seconds / 3600) + "h";
		}
		return Math.max(1, seconds / 60) + "m";
	}
}
