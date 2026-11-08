package io.ruin.model.activities.colosseum;

import io.ruin.model.ScrollbarClientScript;
import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.InterfaceHandler;
import io.ruin.model.inter.ToplevelComponent;
import io.ruin.model.inter.actions.SimpleAction;
import io.ruin.model.inter.handlers.ItemStatsViewer;
import io.ruin.network.PacketSender;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Arbiter's Reliquary, interface 1183 (.dev/cache-restore-tool BuildReliquaryInterface, art assets/colosseum/MakeColosseumArt,
 * written by cache stage 21): the Colosseum of Trials' reward shop, opened by the Reliquary object's Trade. Icons and Gear are
 * scrolling grids of item cards with a Buy button; a card's Examine opens the item's stats (an icon's effect goes to chat).
 * The third tab opens the Ember Crucible. Prices are Trial Marks, stock is unlimited; the catalogue is {@link #CATALOGUE}.
 */
public final class ReliquaryInterface {

	private ReliquaryInterface() {
	}

	public static final int INTERFACE = 1183;
	// component ids, as BuildReliquaryInterface prints them
	private static final int CLOSE = 6, BALANCE = 8, FIRST_TAB = 9, TAB_STRIDE = 3, TAB_ACTIVE = 1, TAB_LABEL = 2, SCROLL = 18, BAR = 19, FIRST_CARD = 20,
			CARD_STRIDE = 7, CARD_BG = 1, CARD_ITEM = 2, CARD_NAME = 3, CARD_PRICE = 4, CARD_BUY = 6, CARDS = 40, PITCH = 54, CARD_H = 48, VIEW_H = 208;
	private static final String GOLD = "<col=f4dc9a>", WHITE = "<col=ffffff>", PALE = "<col=e8e0f0>", RED = "<col=e0402a>", DIM = "<col=8a8078>", END = "</col>";
	/** item names in chat: the chatbox is parchment, where gold is unreadable */
	private static final String CHAT_NAME = "<col=7f0000>", CHAT_RED = "<col=9b0000>";

	public enum Category {
		ICONS("Icons"),
		GEAR("Gear"),
		FORGE("Egg Forge"); // opens the Ember Crucible instead of a list

		final String label;

		Category(String label) {
			this.label = label;
		}
	}

	private static final int DRAKTHOR_PET = 60411;

	record Reward(Category category, int itemId, String name, int price) {}

	static final List<Reward> CATALOGUE = List.of(
			icon(40228, "Bonecrown", 2000), icon(40233, "Soulflame", 2000), icon(40238, "Frost sigil", 1800), icon(40243, "Void eye", 2200),
			icon(40248, "Sunburst", 3000), icon(40080, "Beast", 650), icon(40203, "Shade", 650), icon(40087, "Easter", 500),
			icon(40090, "Amberglow", 1000), icon(40091, "Infernoglint", 1250), icon(40093, "Challenge", 1500), icon(40094, "Midnight spring", 1000),
			gear(40052, "Tempest helm", 1500), gear(40053, "Tempest platebody", 3000), gear(40054, "Tempest legs", 2500),
			gear(40055, "Archaeology master cape", 1500),
			gear(40060, "Nightrix hood", 1500), gear(40058, "Nightrix robe top", 3000), gear(40059, "Nightrix robe bottom", 2500),
			gear(40070, "Tuska helm", 1500), gear(40071, "Tuska cuirass", 3000), gear(40072, "Tuska robe legs", 2500), gear(40073, "Tuska gauntlets", 1000),
			gear(40082, "Ethereal mask", 1500), gear(40089, "Ethereal torso", 3000), gear(40084, "Ethereal legs", 2500), gear(40085, "Ethereal gloves", 1000),
			gear(40083, "Ethereal cape", 1500), gear(40079, "Ethereal dinh's bulwark", 4000), gear(40077, "Ethereal elder maul", 4000),
			gear(40066, "Valkyrie helm", 1500), gear(40067, "Valkyrie body", 3000), gear(40068, "Valkyrie legs", 2500), gear(40074, "Valkyrie wings", 2000),
			gear(40025, "Cloud Ankou mask", 800), gear(40024, "Cloud Ankou top", 800), gear(40023, "Cloud Ankou leggings", 800),
			gear(40021, "Cloud Ankou gloves", 800), gear(40022, "Cloud Ankou socks", 800),
			gear(40015, "Love whip", 1200),
			gear(DRAKTHOR_PET, "Drak'thor hatchling", 12000));

	private static Reward icon(int id, String name, int price) {
		return new Reward(Category.ICONS, id, name + " (T1)", price);
	}

	private static Reward gear(int id, String name, int price) {
		return new Reward(Category.GEAR, id, name, price);
	}

	/** per player: the open tab, and whether its scrollbar is set up (sending it again would jump back to the top) */
	private static final class View {
		Category tab = Category.ICONS;
		boolean scrollSet;
	}

	private static final Map<Integer, View> VIEWS = new HashMap<>();

	static void register() {
		InterfaceHandler.register(INTERFACE, h -> {
			h.actions[CLOSE] = (SimpleAction) Player::closeInterfaces;
			Category[] tabs = Category.values();
			for (int t = 0; t < tabs.length; t++) {
				Category c = tabs[t];
				h.actions[FIRST_TAB + t * TAB_STRIDE + TAB_LABEL] = c == Category.FORGE ? (SimpleAction) EggForge::open : (SimpleAction) p -> {
					View v = VIEWS.get(p.getUserId());
					if (v != null && v.tab != c) {
						v.tab = c;
						v.scrollSet = false;
						draw(p);
					}
				};
			}
			for (int i = 0; i < CARDS; i++) {
				int index = i, card = FIRST_CARD + i * CARD_STRIDE;
				SimpleAction examine = p -> examine(p, index);
				h.actions[card + CARD_BG] = examine;
				h.actions[card + CARD_ITEM] = examine;
				h.actions[card + CARD_BUY] = (SimpleAction) p -> buy(p, index);
			}
			h.closedAction = (p, i) -> VIEWS.remove(p.getUserId());
		});
	}

	public static void open(Player player) {
		open(player, Category.ICONS);
	}

	private static void open(Player player, Category tab) {
		View v = new View();
		v.tab = tab;
		VIEWS.put(player.getUserId(), v);
		player.openInterface(ToplevelComponent.MAINMODAL, INTERFACE);
		draw(player);
	}

	/** An item's price in the catalogue (the Ember Crucible's upgrades are priced from it). */
	static int price(int itemId) {
		for (Reward r : CATALOGUE)
			if (r.itemId == itemId)
				return r.price;
		throw new IllegalArgumentException("not in the Reliquary: " + itemId);
	}

	static List<Reward> list(Category c) {
		List<Reward> l = new ArrayList<>();
		for (Reward r : CATALOGUE)
			if (r.category == c)
				l.add(r);
		return l;
	}

	private static void examine(Player player, int index) {
		View v = VIEWS.get(player.getUserId());
		if (v == null)
			return;
		List<Reward> l = list(v.tab);
		if (index >= l.size())
			return;
		Reward r = l.get(index);
		String effect = EggEffects.describe(r.itemId);
		if (effect != null) {
			player.sendMessage(CHAT_NAME + r.name + END + ": " + effect + " (" + EggEffects.describeMax(r.itemId) + "). Only the worn icon counts.");
			return;
		}
		if (r.itemId == DRAKTHOR_PET) {
			player.sendMessage(CHAT_NAME + r.name + END + ": a follower that earns you 10% more Trial Marks while it is out.");
			return;
		}
		Category tab = v.tab;
		ItemStatsViewer.open(player, r.itemId, () -> open(player, tab));
	}

	/** Spends the marks and hands the item over, with the window left open. */
	private static void buy(Player player, int index) {
		View v = VIEWS.get(player.getUserId());
		if (v == null || !player.isVisibleInterface(INTERFACE))
			return;
		List<Reward> l = list(v.tab);
		if (index < 0 || index >= l.size())
			return;
		Reward r = l.get(index);
		if (player.trialMarks < r.price) {
			player.sendMessage(CHAT_RED + "You do not have enough Trial Marks for this." + END + " (" + player.trialMarks + " / " + r.price + ")");
			return;
		}
		if (!player.getInventory().hasRoomFor(r.itemId)) {
			player.sendMessage("You need a free inventory slot to buy this.");
			return;
		}
		player.trialMarks -= r.price;
		player.getInventory().add(r.itemId, 1);
		player.sendMessage("You buy " + CHAT_NAME + r.name + END + " for " + r.price + " Trial Marks. You have " + player.trialMarks + " left.");
		draw(player);
	}

	private static void draw(Player player) {
		View v = VIEWS.get(player.getUserId());
		if (v == null)
			return;
		PacketSender ps = player.getPacketSender();
		ps.sendString(INTERFACE, BALANCE, String.valueOf(player.trialMarks));
		Category[] tabs = Category.values();
		for (int t = 0; t < tabs.length; t++) {
			int tab = FIRST_TAB + t * TAB_STRIDE;
			ps.setHidden(INTERFACE, tab + TAB_ACTIVE, tabs[t] != v.tab);
			ps.sendString(INTERFACE, tab + TAB_LABEL, (tabs[t] == v.tab ? WHITE : PALE) + tabs[t].label + END);
		}
		List<Reward> l = list(v.tab);
		for (int i = 0; i < CARDS; i++) {
			int card = FIRST_CARD + i * CARD_STRIDE;
			ps.setHidden(INTERFACE, card, i >= l.size());
			if (i >= l.size())
				continue;
			Reward r = l.get(i);
			boolean afford = player.trialMarks >= r.price;
			ps.sendItem(INTERFACE, card + CARD_ITEM, r.itemId, 1);
			ps.sendString(INTERFACE, card + CARD_NAME, r.name);
			ps.sendString(INTERFACE, card + CARD_PRICE, (afford ? GOLD : RED) + r.price + " Marks" + END);
			ps.sendString(INTERFACE, card + CARD_BUY, afford ? "Buy" : DIM + "Buy" + END);
		}
		if (!v.scrollSet) {
			v.scrollSet = true;
			int rows = (l.size() + 1) / 2, content = (rows - 1) * PITCH + CARD_H;
			int count = content <= VIEW_H ? 0 : (int) Math.ceil((content - VIEW_H / 3D) / 15D); // clientscript 10532: count * 15 + height / 3
			ScrollbarClientScript.create().interfaceId(INTERFACE).containerId(SCROLL).scrollbarChildId(BAR).childrenCount(count).withDarkGraphics().build().send(player);
		}
	}
}
