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
import core.task.Continuations;
import lombok.experimental.ExtensionMethod;
import tradepost.TradePost;
import tradepost.TradePostCache;
import tradepost.TradePostCoffer;
import tradepost.TradePostFeatured;
import tradepost.TradePostHandler;
import tradepost.TradePostHistory;
import tradepost.TradePostOffer;
import tradepost.hook.Attributes;
import tradepost.module.Module;

/**
 * The Zelus Trading Post (interface 1161, built by .dev/cache-restore-tool/src/BuildTradingPostInterface.java after
 * the classic OSRS Trading Post layout, 2026-10-20; it replaced the tabbed Zelus Market of the same group). It only
 * draws and dispatches; every trade goes through the {@link TradePost} engine (atomic instant buys, offer matching,
 * the collection box).
 * <ul>
 *     <li>left: Search for Item / Player, Recent Listings, View History; the collection box (Collect Items /
 *     Collect Earnings, with live counts);</li>
 *     <li>right: My Offers (used / max slots, cancel or feature), or the chosen view (listings, search results, a
 *     seller's shop, the seller list, history) with Sort and Back; Buy Offer / Sell Offer underneath.</li>
 * </ul>
 * The 1133 inventory side panel stays open beside it: clicking an inventory item lists it for sale.
 */
@ExtensionMethod(Attributes.class)
public final class ZelusMarket {

	public static final int INTERFACE = 1161;
	private static final int SIDE_INVENTORY = 1133;
	public static final String TAG = "<col=ff981f><shad=000000>[Trading Post]</shad></col> ";

	private static final int CLOSE = 2, TITLE = 3;
	/** Stone buttons (IfKit.stoneButton): fill (click), +9 pressed layer, +19 label. */
	private static final int PRESSED = 9, LABEL = 19;
	private static final int SEARCH_ITEM = 5, SEARCH_PLAYER = 25, RECENT = 45, HISTORY = 65;
	private static final int COLLECT_ITEMS = 86, COLLECT_EARNINGS = 107, COLLECT_ITEMS_LABEL = 1430, COLLECT_EARNINGS_LABEL = 1431;
	private static final int HEADING = 128, COUNT = 129, SORT = 130, SORT_BUTTON = 131, BACK = 151, BACK_BUTTON = 152;
	private static final int SCROLL = 176, SCROLLBAR = 177, LIST_HEIGHT = 150;
	/** Rows (40px on a 42px pitch): +1 fill (click), +3 featured trim, +6 item, +7 name, +8 detail, +9 button layer, +10 button. */
	private static final int FIRST_ROW = 178, ROW_STRIDE = 30, ROWS = 40, ROW_PITCH = 42;
	private static final int R_FILL = 1, R_GOLD = 3, R_ITEM = 6, R_NAME = 7, R_INFO = 8, R_BUTTON_LAYER = 9, R_BUTTON = 10;
	private static final int EMPTY = 1378, FOOTER = 1381, PAGER = 1382, PREV = 1383, PAGE = 1384, NEXT = 1385;
	private static final int ARROW_LEFT = 24528, ARROW_RIGHT = 24529, ARROW_LEFT_DIM = 24530, ARROW_RIGHT_DIM = 24531;
	private static final int BUY_OFFER = 1386, SELL_OFFER = 1408;
	private static final int MODAL = 1432, MODAL_ITEM = 1438, MODAL_TITLE = 1439, MODAL_INFO = 1440, MODAL_BUY_1 = 1441,
		MODAL_BUY_5 = 1461, MODAL_BUY_X = 1481, MODAL_BUY_ALL = 1501, MODAL_CANCEL = 1521;

	/** The right panel's views. */
	private static final int MY_OFFERS = 0, LISTINGS = 1, ITEM_SEARCH = 2, SELLERS = 3, SHOP = 4, HISTORY_VIEW = 5;
	private static final String[] SORTS = {"Newest first", "Price: Low-High", "Price: High-Low"};

	private static final String ORANGE = "<col=ff981f>", GOLD = "<col=ffc35a>", WHITE = "<col=ffffff>", GREEN = "<col=00ff00>",
		YELLOW = "<col=ffff00>", RED = "<col=ff4040>", SOFT = "<col=c8b896>", DIM = "<col=9e9e9e>", PLAYER = "<col=e0e0e0>", END = "</col>";

	/** Per-player view state (lives on TradePostPlayer). */
	public static final class State {
		int view = MY_OFFERS;
		/** The view Back returns to (the seller list when a shop was opened from it). */
		int backTo = MY_OFFERS;
		/** Item / player search text (lower case), or the seller whose shop is open. */
		String search;
		String shop;
		int sort;
		int page;
		final List<Row> rows = new ArrayList<>();
		int pages = 1;
		int total;
		int sentRows = -1;
		/** The listing in the buy modal (null = closed). */
		TradePostOffer modalOffer;
	}

	/** One list row: exactly one of offer / history / seller is set. */
	record Row(TradePostOffer offer, TradePostHistory history, String seller, int count) {
	}

	private ZelusMarket() {
	}

	public static void register() {
		InterfaceHandler.register(INTERFACE, h -> {
			h.actions[CLOSE] = (SimpleAction) Player::closeInterfaces;
			h.actions[SEARCH_ITEM] = (SimpleAction) p -> promptSearch(p, false);
			h.actions[SEARCH_PLAYER] = (SimpleAction) p -> promptSearch(p, true);
			h.actions[RECENT] = (SimpleAction) p -> show(p, LISTINGS, MY_OFFERS);
			h.actions[HISTORY] = (SimpleAction) p -> show(p, HISTORY_VIEW, MY_OFFERS);
			h.actions[COLLECT_ITEMS] = (SimpleAction) TradePostCoffer::collectAll;
			h.actions[COLLECT_EARNINGS] = (SimpleAction) TradePostCoffer::collectCoins;
			h.actions[SORT_BUTTON] = (SimpleAction) p -> {
				var s = state(p);
				s.sort = (s.sort + 1) % SORTS.length;
				s.page = 0;
				draw(p, true);
			};
			h.actions[BACK_BUTTON] = (SimpleAction) p -> {
				var s = state(p);
				show(p, s.view == SHOP ? s.backTo : MY_OFFERS, MY_OFFERS);
			};
			h.actions[PREV] = (SimpleAction) p -> turnPage(p, -1);
			h.actions[NEXT] = (SimpleAction) p -> turnPage(p, 1);
			h.actions[BUY_OFFER] = (SimpleAction) TradePostHandler::promptBuyOffer;
			h.actions[SELL_OFFER] = (SimpleAction) p -> p.sendMessage(TAG + "Click an item in your inventory to list it for sale.");
			for (int r = 0; r < ROWS; r++) {
				int index = r;
				int row = FIRST_ROW + r * ROW_STRIDE;
				h.actions[row + R_FILL] = (SimpleAction) p -> select(p, index, false);
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

	/**
	 * True (with a message) while the player carries a temporary loadout: a Rivals match / layout kit or a PvP preset.
	 * Whatever they hold then is borrowed and wiped afterwards, so nothing may be listed (2026-10-17 ammo dupe audit).
	 */
	public static boolean blockedByLoadout(Player player) {
		if (io.ruin.model.content.rivals.RivalsSnapshot.active(player) || player.pvpPresetActive) {
			player.sendMessage("You can't use the Trading Post while using a Rivals kit or a PvP preset.");
			return true;
		}
		return false;
	}

	/** Opens the Trading Post (on a view, or -1 for the last one); refuses ironmen and while disabled. */
	public static void open(Player player, int view) {
		if (!Module.ENABLED) {
			player.sendMessage("The Trading Post is closed for maintenance; please try again later.");
			return;
		}
		if (player.getGameMode().isIronMan()) {
			player.sendMessage("Your game mode prevents you from using the Trading Post.");
			return;
		}
		if (blockedByLoadout(player))
			return;
		var s = state(player);
		if (view >= 0) {
			s.view = view;
			s.backTo = MY_OFFERS;
			s.page = 0;
			s.search = null;
			s.shop = null;
		}
		s.modalOffer = null;
		player.openInterface(ToplevelComponent.MAINMODAL, INTERFACE);
		if (!player.isVisibleInterface(SIDE_INVENTORY)) {
			player.openInterface(ToplevelComponent.INVENTORY_TAB_AREA, SIDE_INVENTORY);
			player.getPacketSender().sendIfEvents(SIDE_INVENTORY, 0, 0, 27, 1086);
		}
		player.getPacketSender().sendString(INTERFACE, TITLE, "Trading Post");
		draw(player, true);
	}

	/** The view opened after placing an offer (the player's own offers). */
	public static int myShopTab() {
		return MY_OFFERS;
	}

	/** After an input box (which closes the screen): reopen on the same view. */
	private static void reopen(Player player) {
		if (player.isOnline()) {
			open(player, -1);
		}
	}

	/** Engine callback: redraw if the Trading Post is open (keeps the scroll position unless the row count changed). */
	public static void refresh(Player player) {
		if (player != null && player.isVisibleInterface(INTERFACE)) {
			draw(player, false);
		}
	}

	private static void show(Player player, int view, int backTo) {
		var s = state(player);
		s.view = view;
		s.backTo = backTo;
		s.page = 0;
		s.modalOffer = null;
		if (view == MY_OFFERS || view == LISTINGS || view == HISTORY_VIEW) {
			s.search = null;
			s.shop = null;
		}
		draw(player, true);
	}

	/* ---------------- drawing ---------------- */

	private static void draw(Player player, boolean resetScroll) {
		var s = state(player);
		var ps = player.getPacketSender();
		var pTradePost = player.tradePost();

		// left column: the active view's button sits pressed; the collection box shows what is waiting
		pressed(ps, SEARCH_ITEM, s.view == ITEM_SEARCH);
		pressed(ps, SEARCH_PLAYER, s.view == SELLERS || s.view == SHOP);
		pressed(ps, RECENT, s.view == LISTINGS);
		pressed(ps, HISTORY, s.view == HISTORY_VIEW);
		int waiting = pTradePost.coffer.size();
		ps.sendString(INTERFACE, COLLECT_ITEMS_LABEL, "Collect Items<br>" + (waiting > 0 ? GREEN : SOFT)
				+ "(" + waiting + " item" + (waiting == 1 ? "" : "s") + ")" + END);
		boolean earned = pTradePost.cofferCoins.signum() > 0;
		ps.sendString(INTERFACE, COLLECT_EARNINGS_LABEL, "Collect Earnings<br>" + (earned ? GREEN : SOFT)
				+ "(" + coins(pTradePost.cofferCoins) + " coins)" + END);

		// heading: My Offers + slots, or the view's title with Sort and Back
		buildRows(player);
		boolean mine = s.view == MY_OFFERS;
		ps.sendString(INTERFACE, HEADING, heading(s));
		ps.sendString(INTERFACE, COUNT, mine ? "(" + usedSlots(player) + "/" + TradePost.maxOfferSlots(player) + ")" : "");
		ps.setHidden(INTERFACE, SORT, mine || s.view == SELLERS);
		ps.sendString(INTERFACE, SORT_BUTTON + LABEL, SORTS[s.sort]);
		ps.setHidden(INTERFACE, BACK, mine);

		// footer: a hint on My Offers, else the range and (with more than one page) the pager
		int first = s.page * ROWS;
		ps.sendString(INTERFACE, FOOTER, mine ? DIM + "Click an inventory item to sell it." + END
				: s.total == 0 ? "" : DIM + "Showing " + (first + 1) + "-" + (first + s.rows.size()) + " of " + s.total + END);
		boolean paged = !mine && s.pages > 1;
		ps.setHidden(INTERFACE, PAGER, !paged);
		if (paged) {
			ps.sendString(INTERFACE, PAGE, (s.page + 1) + " / " + s.pages);
			ps.setGraphic(INTERFACE, PREV, s.page > 0 ? ARROW_LEFT : ARROW_LEFT_DIM);
			ps.setGraphic(INTERFACE, NEXT, s.page + 1 < s.pages ? ARROW_RIGHT : ARROW_RIGHT_DIM);
		}

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

	private static void pressed(PacketSender ps, int button, boolean on) {
		ps.setHidden(INTERFACE, button + PRESSED, !on);
	}

	private static int usedSlots(Player player) {
		int used = 0;
		for (var offer : player.tradePost().offers) {
			if (offer != null && !offer.isEmpty()) {
				used++;
			}
		}
		return used;
	}

	/** The heading fits 110px of bold text: long search terms are shortened. */
	private static String heading(State s) {
		return switch (s.view) {
			case LISTINGS -> "Recent Listings";
			case ITEM_SEARCH -> "\"" + clip(s.search, 12) + "\"";
			case SELLERS -> s.search == null ? "Sellers" : "\"" + clip(s.search, 12) + "\"";
			case SHOP -> clip(display(s.shop), 14);
			case HISTORY_VIEW -> "History";
			default -> "My Offers";
		};
	}

	private static String clip(String text, int max) {
		if (text == null) {
			return "";
		}
		return text.length() <= max ? text : text.substring(0, max - 1) + "..";
	}

	private static String emptyText(State s) {
		return switch (s.view) {
			case LISTINGS -> "Nothing is listed right now.";
			case ITEM_SEARCH -> "No listings match \"" + clip(s.search, 20) + "\".";
			case SELLERS -> "No sellers match that name.";
			case SHOP -> display(s.shop) + " has nothing listed.";
			case HISTORY_VIEW -> "You haven't traded yet.";
			default -> "You have no offers.";
		};
	}

	private static void drawRow(Player player, PacketSender ps, int row, Row data) {
		boolean featured = false;
		String button = null;
		int itemId = rowItem(data);
		int itemAmount = 1;
		String name, info;

		if (data.offer() != null && data.offer().owner.equalsIgnoreCase(player.uuid())) {
			// My Offers: what has traded so far, the price, and Cancel
			var o = data.offer();
			featured = o.isSell() && TradePostFeatured.isFeatured(o);
			itemAmount = o.getAmount();
			var done = o.getStartAmount() - o.getAmount();
			// featured rows get the gold trim (the name stays one line); the row's menu shows the time left
			name = itemName(itemId);
			info = (o.isSell() ? GREEN + "Selling" : YELLOW + "Buying") + END + " " + done + "/" + o.getStartAmount()
					+ " · " + GREEN + coins(o.priceEach()) + END + " ea";
			button = "Cancel";
		} else if (data.offer() != null) {
			// a listing (Recent Listings / a search / a seller's shop)
			var o = data.offer();
			featured = TradePostFeatured.isFeatured(o);
			itemAmount = o.getAmount();
			name = itemName(itemId);
			info = GREEN + coins(o.priceEach()) + END + " ea · x" + o.getAmount() + " · " + PLAYER + display(o.owner) + END;
			button = "Buy";
		} else if (data.history() != null) {
			var h = data.history();
			itemAmount = h.getItem().getAmount();
			name = itemName(itemId) + (itemAmount > 1 ? WHITE + " x " + itemAmount + END : "");
			info = (h.getOfferType() == 1 ? RED + "Sold to " : GREEN + "Bought from ") + END + PLAYER + display(h.getSeller()) + END
					+ " · " + coins(h.getPrice()) + " · " + ago(h.getAge());
		} else {
			// a seller
			name = display(data.seller());
			var worth = TradePostCache.offers().filter(o -> o.isSell() && !o.isEmpty() && o.owner.equalsIgnoreCase(data.seller()))
					.map(o -> o.priceEach().multiply(o.amount)).reduce(BigInteger.ZERO, BigInteger::add);
			info = data.count() + " listing" + (data.count() == 1 ? "" : "s") + " · " + GREEN + coins(worth) + END + " listed";
			button = "View";
		}

		ps.setHidden(INTERFACE, row + R_GOLD, !featured);
		ps.sendItem(INTERFACE, row + R_ITEM, itemId, Math.max(1, itemAmount));
		ps.sendString(INTERFACE, row + R_NAME, name);
		ps.sendString(INTERFACE, row + R_INFO, info);
		ps.setHidden(INTERFACE, row + R_BUTTON_LAYER, button == null);
		if (button != null) {
			ps.sendString(INTERFACE, row + R_BUTTON + LABEL, button);
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
		ps.sendString(INTERFACE, MODAL_INFO, "Price: " + GREEN + coins(offer.priceEach()) + END + " each<br>"
				+ "Available: " + WHITE + offer.getAmount() + END + "<br>"
				+ "Seller: " + PLAYER + display(offer.owner) + END + "<br>"
				+ "Your coins: " + GREEN + coins(wallet) + END);
	}

	/* ---------------- rows per view ---------------- */

	private static void buildRows(Player player) {
		var s = state(player);
		var all = new ArrayList<Row>();
		var me = player.uuid();
		switch (s.view) {
			case MY_OFFERS -> {
				for (var offer : player.tradePost().offers) {
					if (offer != null && !offer.isEmpty()) {
						all.add(new Row(offer, null, null, 0));
					}
				}
			}
			case HISTORY_VIEW -> {
				var list = new ArrayList<>(player.tradePost().history);
				Comparator<TradePostHistory> order = switch (s.sort) {
					case 1 -> Comparator.comparing(TradePostHistory::getPrice);
					case 2 -> Comparator.comparing(TradePostHistory::getPrice).reversed();
					default -> Comparator.comparingLong(TradePostHistory::getAge).reversed();
				};
				list.sort(order);
				list.forEach(h -> all.add(new Row(null, h, null, 0)));
			}
			case SHOP -> listings(s, o -> o.owner.equalsIgnoreCase(s.shop)).forEach(o -> all.add(new Row(o, null, null, 0)));
			case SELLERS -> {
				Map<String, Integer> sellers = new LinkedHashMap<>();
				TradePostCache.offers()
						.filter(o -> o.isSell() && !o.isEmpty())
						.sorted(Comparator.comparingLong(TradePostOffer::getTimeStamp).reversed())
						.forEach(o -> sellers.merge(o.owner.toLowerCase(Locale.ROOT), 1, Integer::sum));
				sellers.forEach((seller, count) -> {
					if (s.search == null || seller.contains(s.search)) {
						all.add(new Row(null, null, seller, count));
					}
				});
			}
			case ITEM_SEARCH -> listings(s, o -> !o.owner.equalsIgnoreCase(me)
					&& itemName(o.getItemId()).toLowerCase(Locale.ROOT).contains(s.search == null ? "" : s.search))
					.forEach(o -> all.add(new Row(o, null, null, 0)));
			default -> {
				// featured listings first, then the rest in the chosen order
				var list = listings(s, o -> !o.owner.equalsIgnoreCase(me));
				list.stream().filter(TradePostFeatured::isFeatured).forEach(o -> all.add(new Row(o, null, null, 0)));
				list.stream().filter(o -> !TradePostFeatured.isFeatured(o)).forEach(o -> all.add(new Row(o, null, null, 0)));
			}
		}
		s.total = all.size();
		s.pages = Math.max(1, (all.size() + ROWS - 1) / ROWS);
		s.page = Math.max(0, Math.min(s.page, s.pages - 1));
		s.rows.clear();
		s.rows.addAll(all.subList(s.page * ROWS, Math.min(all.size(), (s.page + 1) * ROWS)));
	}

	/** Live sell listings matching the filter, in the chosen sort order. */
	private static List<TradePostOffer> listings(State s, java.util.function.Predicate<TradePostOffer> filter) {
		Comparator<TradePostOffer> order = switch (s.sort) {
			case 1 -> Comparator.comparing(TradePostOffer::priceEach);
			case 2 -> Comparator.comparing(TradePostOffer::priceEach).reversed();
			default -> Comparator.comparingLong(TradePostOffer::getTimeStamp).reversed();
		};
		return TradePostCache.offers()
				.filter(o -> o.isSell() && !o.isEmpty())
				.filter(filter)
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
		} else if (row.seller() != null) {
			s.shop = row.seller();
			show(player, SHOP, SELLERS);
		}
	}

	/** My Offers: the button cancels (with a confirm); the row offers Feature / Cancel. */
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

	/** Item: listings whose name contains the text. Player: the seller's shop on an exact match, else matching sellers. */
	private static void promptSearch(Player player, boolean players) {
		player.stringInput(players ? "Search for a player:" : "Search for an item:", text -> {
			var query = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
			var s = state(player);
			s.page = 0;
			s.modalOffer = null;
			if (query.isEmpty()) {
				reopen(player);
				return;
			}
			s.search = query;
			s.shop = null;
			if (!players) {
				s.view = ITEM_SEARCH;
				s.backTo = MY_OFFERS;
			} else if (TradePostCache.offers().anyMatch(o -> o.isSell() && !o.isEmpty() && o.owner.equalsIgnoreCase(query))) {
				s.shop = query;
				s.view = SHOP;
				s.backTo = MY_OFFERS;
			} else {
				s.view = SELLERS;
				s.backTo = MY_OFFERS;
			}
			reopen(player);
		});
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
