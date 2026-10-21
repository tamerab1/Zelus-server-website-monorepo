package io.ruin.model.skills.slayer;

import io.ruin.api.utils.NumberUtils;
import io.ruin.cache.ObjType;
import io.ruin.model.World;
import io.ruin.model.activities.newshop.NewShopHandler;
import io.ruin.model.activities.newshop.ShopCategories;
import io.ruin.model.activities.newshop.ShopItem;
import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.InterfaceHandler;
import io.ruin.model.inter.ToplevelComponent;
import io.ruin.model.inter.actions.DefaultAction;
import io.ruin.model.inter.actions.SimpleAction;
import io.ruin.model.inter.dialogue.OptionsDialogue;
import io.ruin.model.inter.utils.Option;
import io.ruin.model.ScrollbarClientScript;
import io.ruin.model.item.Item;
import io.ruin.model.var.VarPlayerRepository;
import io.ruin.network.PacketSender;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Slayer Master Codex (interface 1180, built by .dev/cache-restore-tool/src/BuildSlayerCodexInterface.java), 2026-10-21:
 * one window replacing the legacy rewards interface (426) and the split Slayer shops. Every master's Rewards and Trade
 * paths open it ({@link SlayerUnlock#openRewards} delegates here). Tabs:
 * <ul>
 * <li>Unlocks &amp; Perks: every {@link SlayerUnlock} (unlocks, then task extensions) as a card -- price, or sunken + ACTIVE;
 * "Unlock" buys it, "Disable" turns an active one off (no refund, as before, so it asks first);</li>
 * <li>Task Management: the current task, Cancel / Block (the usual costs, with bond / donator discounts) and the blocked
 * list with Unblock;</li>
 * <li>Slayer Supplies: the Slayer point supplies, the old rewards shop items and the coin-priced utility gear;</li>
 * <li>Prestige Gear: the Slayer point items costing 650+ (sigils, rare unlockables).</li>
 * </ul>
 */
public final class SlayerCodex {

	public static final int INTERFACE = 1180;
	private static final int CLOSE = 2, TITLE = 3, POINTS = 4;
	/** Stone buttons: fill (click), +9 pressed layer, +19 label. */
	private static final int PRESSED = 9, LABEL = 19, STONE = 20;
	private static final int FIRST_TAB = 5;
	private static final int UNLOCK_VIEW = 89, UNLOCK_SCROLL = 90, UNLOCK_SCROLLBAR = 91, FIRST_UNLOCK = 92, UNLOCK_STRIDE = 14,
		UNLOCK_CARDS = 60, UNLOCK_PITCH = 82, UNLOCK_H = 76;
	private static final int SHOP_VIEW = 932, SHOP_SCROLL = 933, SHOP_SCROLLBAR = 934, FIRST_SHOP = 935, SHOP_STRIDE = 10,
		SHOP_CARDS = 50, SHOP_PITCH = 104, SHOP_H = 98, SHOP_COLS = 5;
	private static final int TASK_VIEW = 1435, TASK_ITEM = 1444, TASK_NAME = 1445, TASK_LEFT = 1446, TASK_MASTER = 1447,
		CANCEL = 1448, BLOCK = 1468, BLOCK_TITLE = 1492, FIRST_BLOCKED = 1494, BLOCKED_STRIDE = 22, BLOCKED_ROWS = 7;
	private static final int VIEW_HEIGHT = 226;

	private static final String ORANGE = "<col=ff981f>", WHITE = "<col=ffffff>", GREEN = "<col=00ff00>", RED = "<col=ff3333>",
		GOLD = "<col=ffc35a>", GREY = "<col=9a9a9a>", END = "</col>";
	private static final String TAG = "<col=7f0000>[Slayer]</col> ";

	public enum Tab {
		UNLOCKS, TASK, SUPPLIES, PRESTIGE
	}

	/** One perk card: title, a short (2-line) description and an icon item. */
	private record Perk(String title, String description, int icon) {
	}

	private static final Map<SlayerUnlock, Perk> PERKS = new EnumMap<>(SlayerUnlock.class);

	static {
		perk(SlayerUnlock.GARGOYLE_SMASHER, "Gargoyle Smasher", "Gargoyles are finished off with a rock hammer.", 4162);
		perk(SlayerUnlock.SLUG_SALTER, "Slug Salter", "Rockslugs are finished off with a bag of salt.", 4161);
		perk(SlayerUnlock.REPTILE_FREEZER, "Reptile Freezer", "Desert lizards are finished off with ice coolers.", 6696);
		perk(SlayerUnlock.SHROOM_SPRAYER, "'Shroom Sprayer", "Mutated zygomites are finished off with fungicide.", 7432);
		perk(SlayerUnlock.BROADER_FLETCHING, "Broader Fletching", "Learn to fletch broad arrows and broad bolts.", 11875);
		perk(SlayerUnlock.MALEVOLENT_MASQUERADE, "Malevolent Masquerade", "Learn to combine gear into a Slayer helmet.", 11864);
		perk(SlayerUnlock.RING_BLING, "Ring Bling", "Learn to craft your own Slayer rings.", 11866);
		perk(SlayerUnlock.SEEING_RED, "Seeing Red", "Masters may assign you red dragons.", 1749);
		perk(SlayerUnlock.I_HOPE_YOU_MITH_ME, "I Hope You Mith Me!", "Masters may assign you mithril dragons.", 2359);
		perk(SlayerUnlock.WATCH_THE_BIRDIE, "Watch the Birdie", "Masters may assign you aviansies.", 314);
		perk(SlayerUnlock.HOT_STUFF, "Hot Stuff", "Masters may assign you TzHaar.", 6570);
		perk(SlayerUnlock.REPTILE_GOT_RIPPED, "Reptile Got Ripped", "Masters may assign you lizardmen.", 13391);
		perk(SlayerUnlock.LIKE_A_BOSS, "Like a Boss", "Masters may assign you boss monsters.", 11832);
		perk(SlayerUnlock.BIGGER_AND_BADDER, "Bigger and Badder", "Superior slayer creatures can appear on task.", 20724);
		perk(SlayerUnlock.KING_BLACK_BONNET, "King Black Bonnet", "Learn to make the black Slayer helmet.", 19639);
		perk(SlayerUnlock.KALPHITE_KHAT, "Kalphite Khat", "Learn to make the green Slayer helmet.", 19643);
		perk(SlayerUnlock.UNHOLY_HELMET, "Unholy Helmet", "Learn to make the red Slayer helmet.", 19647);
		perk(SlayerUnlock.DARK_MANTLE, "Dark Mantle", "Learn to make the purple Slayer helmet.", 21264);
		perk(SlayerUnlock.UNDEAD_HEAD, "Undead Head", "Learn to make the turquoise Slayer helmet.", 21888);
		perk(SlayerUnlock.USE_MORE_HEAD, "Use More Head", "Learn to make the hydra Slayer helmet.", 23073);
		perk(SlayerUnlock.DULY_NOTED, "Duly Noted", "Mithril dragons drop their mithril bars noted.", 2360);
		perk(SlayerUnlock.DOUBLE_TROUBLE, "Double Trouble", "Grotesque Guardians kills count twice on task.", 21730);
		perk(SlayerUnlock.BASILOCKED, "Basilocked", "Masters may assign you basilisks.", 24268);
		perk(SlayerUnlock.TWISTEDVISION, "Twisted Vision", "Learn to make the twisted Slayer helmet.", 24370);
		perk(SlayerUnlock.VAMPYRESLAYER, "Actual Vampyre Slayer", "Masters may assign you vampyres.", 24699);
		perk(SlayerUnlock.TASKSTORAGE, "Task Storage", "Store your task and take it back up later.", 4155);
		perk(SlayerUnlock.IWILDYMORESLAYER, "I Wildy More Slayer", "Wilderness tasks from Krystilia, for free.", 21820);
		perk(SlayerUnlock.EYE_SEE_YOU, "Eye See You", "Masters may assign you araxytes.", 11864);
		ext(SlayerUnlock.NEED_MORE_DARKNESS, "Need More Darkness", "Dark beast", 11235);
		ext(SlayerUnlock.ANKOU_VERY_MUCH, "Ankou Very Much", "Ankou", 20095);
		ext(SlayerUnlock.SUQANOTHER_ONE, "Suq-a-nother One", "Suqah", 9075);
		ext(SlayerUnlock.FIRE_AND_DARKNESS, "Fire & Darkness", "Black dragon", 1747);
		ext(SlayerUnlock.PEDAL_TO_THE_METALS, "Pedal to the Metals", "Metal dragon", 2363);
		ext(SlayerUnlock.I_REALLY_MITH_YOU, "I Really Mith You", "Mithril dragon", 2359);
		ext(SlayerUnlock.SPIRITUAL_FERVOUR, "Spiritual Fervour", "Spiritual creature", 11818);
		ext(SlayerUnlock.BIRDS_OF_A_FEATHER, "Birds of a Feather", "Aviansie", 314);
		ext(SlayerUnlock.GREATER_CHALLENGER, "Greater Challenge", "Greater demon", 7979);
		ext(SlayerUnlock.ITS_DARK_IN_HERE, "It's Dark in Here", "Black demon", 6729);
		ext(SlayerUnlock.BLEED_ME_DRY, "Bleed Me Dry", "Bloodveld", 526);
		ext(SlayerUnlock.SMELL_YA_LATER, "Smell Ya Later", "Aberrant spectre", 4168);
		ext(SlayerUnlock.HORRORFIC, "Horrorific", "Cave horror", 8901);
		ext(SlayerUnlock.TO_DUST_YOU_SHALL_RETURN, "To Dust You Shall Return", "Dust devil", 4164);
		ext(SlayerUnlock.WYVERNOTHER_ONE, "Wyver-nother One", "Skeletal wyvern", 21637);
		ext(SlayerUnlock.GET_SMASHED, "Get Smashed", "Gargoyle", 4153);
		ext(SlayerUnlock.NECHS_PLEASE, "Nechs Please", "Nechryael", 4131);
		ext(SlayerUnlock.AUGMENT_MY_ABBIES, "Augment My Abbies", "Abyssal demon", 4151);
		ext(SlayerUnlock.KRACK_ON, "Krack On", "Cave kraken", 12004);
		ext(SlayerUnlock.GET_SCABARIGHT_ON_IT, "Get Scabaright on It", "Scarab", 4155);
		ext(SlayerUnlock.WYVERNOTHER_TWO, "Wyver-nother Two", "Fossil Island wyvern", 21637);
		ext(SlayerUnlock.BASILONGER, "Basilonger", "Basilisk", 24268);
		ext(SlayerUnlock.MORE_EYES_THAN_SENSE, "More Eyes Than Sense", "Araxyte", 11864);
	}

	private static void perk(SlayerUnlock u, String title, String description, int icon) {
		PERKS.put(u, new Perk(title, description, icon));
	}

	private static void ext(SlayerUnlock u, String title, String monster, int icon) {
		PERKS.put(u, new Perk(title, monster + " tasks are extended (more kills).", icon));
	}

	/** One shop card: the item, how many one purchase gives, the price, and whether it costs coins (else points). */
	private record Ware(int itemId, int amount, int price, boolean coins) {
	}

	/** Per-viewer state. */
	private static final class View {
		Tab tab = Tab.UNLOCKS;
		/** What the scrollbar was last set up for: re-sending it scrolls back to the top, so only on a tab / size change. */
		Tab scrollTab;
		int scrollSize = -1;
		List<SlayerUnlock> unlocks = List.of();
		List<Ware> wares = List.of();
	}

	private static final Map<String, View> views = new HashMap<>();

	private SlayerCodex() {
	}

	public static void register() {
		InterfaceHandler.register(INTERFACE, h -> {
			h.actions[CLOSE] = (SimpleAction) p -> p.closeInterface(ToplevelComponent.MAINMODAL);
			Tab[] tabs = Tab.values();
			for (int t = 0; t < tabs.length; t++) {
				Tab tab = tabs[t];
				h.actions[FIRST_TAB + t * STONE] = (SimpleAction) p -> show(p, tab);
			}
			for (int c = 0; c < UNLOCK_CARDS; c++) {
				int index = c;
				h.actions[FIRST_UNLOCK + c * UNLOCK_STRIDE + 1] = (DefaultAction) (p, option, slot, itemId) -> {
					View v = view(p);
					if (index < v.unlocks.size())
						unlockAction(p, v.unlocks.get(index), option);
				};
			}
			for (int c = 0; c < SHOP_CARDS; c++) {
				int index = c;
				h.actions[FIRST_SHOP + c * SHOP_STRIDE + 1] = (DefaultAction) (p, option, slot, itemId) -> {
					View v = view(p);
					if (index < v.wares.size())
						wareAction(p, v.wares.get(index), option);
				};
			}
			h.actions[CANCEL] = (SimpleAction) p -> {
				SlayerUnlock.cancelTask(p);
				show(p, Tab.TASK);
			};
			h.actions[BLOCK] = (SimpleAction) p -> {
				SlayerUnlock.blockTask(p);
				show(p, Tab.TASK);
			};
			for (int r = 0; r < BLOCKED_ROWS; r++) {
				int row = r;
				h.actions[FIRST_BLOCKED + r * BLOCKED_STRIDE + 2] = (SimpleAction) p -> {
					if (VarPlayerRepository.BLOCKED_TASKS[row].get(p) == 0)
						return;
					SlayerUnlock.unblockTask(p, row);
					p.sendMessage(TAG + "Task unblocked.");
					show(p, Tab.TASK);
				};
			}
		});
	}

	/* ---------------- opening ---------------- */

	public static void open(Player player, Tab tab) {
		view(player).scrollTab = null; // a fresh window: set the scrollbar up again
		player.openInterface(ToplevelComponent.MAINMODAL, INTERFACE);
		player.getPacketSender().sendString(INTERFACE, TITLE, ORANGE + "Slayer Master Codex" + END);
		show(player, tab);
	}

	private static void show(Player player, Tab tab) {
		View v = view(player);
		v.tab = tab;
		PacketSender ps = player.getPacketSender();
		sendPoints(player);
		Tab[] tabs = Tab.values();
		for (int t = 0; t < tabs.length; t++)
			ps.setHidden(INTERFACE, FIRST_TAB + t * STONE + PRESSED, tabs[t] != tab);
		ps.setHidden(INTERFACE, UNLOCK_VIEW, tab != Tab.UNLOCKS);
		ps.setHidden(INTERFACE, SHOP_VIEW, tab != Tab.SUPPLIES && tab != Tab.PRESTIGE);
		ps.setHidden(INTERFACE, TASK_VIEW, tab != Tab.TASK);
		switch (tab) {
			case UNLOCKS -> drawUnlocks(player, ps, v);
			case TASK -> drawTask(player, ps);
			default -> drawShop(player, ps, v, tab == Tab.PRESTIGE);
		}
	}

	private static void sendPoints(Player player) {
		player.getPacketSender().sendString(INTERFACE, POINTS, "Slayer Points: " + WHITE
			+ NumberUtils.formatNumber(VarPlayerRepository.SLAYER_POINTS.get(player)) + END);
	}

	/* ---------------- unlocks ---------------- */

	private static void drawUnlocks(Player player, PacketSender ps, View v) {
		List<SlayerUnlock> list = new ArrayList<>();
		for (SlayerUnlock u : SlayerUnlock.values())
			if (!u.extension && PERKS.containsKey(u))
				list.add(u);
		for (SlayerUnlock u : SlayerUnlock.values())
			if (u.extension && PERKS.containsKey(u))
				list.add(u);
		v.unlocks = list;
		for (int c = 0; c < UNLOCK_CARDS; c++) {
			int card = FIRST_UNLOCK + c * UNLOCK_STRIDE;
			boolean present = c < list.size();
			ps.setHidden(INTERFACE, card, !present);
			if (!present)
				continue;
			SlayerUnlock u = list.get(c);
			Perk perk = PERKS.get(u);
			boolean active = u.config.get(player) == 1;
			for (int k = 3; k <= 5; k++)
				ps.setHidden(INTERFACE, card + k, !active);
			ps.sendItem(INTERFACE, card + 8, perk.icon, 1);
			ps.sendString(INTERFACE, card + 9, perk.title + (u.extension ? GREY + " (extend)" + END : ""));
			ps.sendString(INTERFACE, card + 10, perk.description);
			ps.sendString(INTERFACE, card + 13, active ? GREEN + "[ ACTIVE ]" + END
				: u.price == 0 ? "Free - Unlock" : "[ " + NumberUtils.formatNumber(u.price) + " Pts ] Unlock");
		}
		scrollbar(player, v, list.size(), UNLOCK_SCROLL, UNLOCK_SCROLLBAR, ((list.size() + 1) / 2 - 1) * UNLOCK_PITCH + UNLOCK_H);
	}

	/**
	 * Unlock / Disable both ask first, in the chatbox with the Codex left open (unsafeDialogue: a normal dialogue closes
	 * the window, which also lost the scroll position). The answer re-checks everything before points move.
	 */
	private static void unlockAction(Player player, SlayerUnlock u, int option) {
		Perk perk = PERKS.get(u);
		boolean active = u.config.get(player) == 1;
		if (option == 1) {
			if (active) {
				player.sendMessage(TAG + perk.title + " is already unlocked.");
				return;
			}
			if (VarPlayerRepository.SLAYER_POINTS.get(player) < u.price) {
				player.sendMessage(TAG + "You need " + NumberUtils.formatNumber(u.price) + " Slayer Points to unlock " + perk.title + ".");
				return;
			}
			player.unsafeDialogue(new OptionsDialogue("Unlock " + perk.title + " for " + NumberUtils.formatNumber(u.price) + " Slayer Points?",
				new Option("Yes, unlock it.", () -> {
					player.closeDialogue();
					if (u.config.get(player) == 1 || VarPlayerRepository.SLAYER_POINTS.get(player) < u.price) {
						player.sendMessage(TAG + "You can no longer unlock " + perk.title + ".");
					} else {
						u.toggle(player); // takes the points and sets it
						player.sendMessage(TAG + perk.title + " unlocked.");
					}
					refresh(player);
				}),
				new Option("No, cancel.", player::closeDialogue)));
			return;
		}
		if (!active) {
			player.sendMessage(TAG + perk.title + " isn't active.");
			return;
		}
		player.unsafeDialogue(new OptionsDialogue("Disable " + perk.title + "? You won't get the points back.",
			new Option("Yes, disable it.", () -> {
				player.closeDialogue();
				if (u.config.get(player) == 1)
					u.toggle(player); // active -> off, as the old rewards interface did
				refresh(player);
			}),
			new Option("No, keep it.", player::closeDialogue)));
	}

	/** Redraws the open tab in place (no scroll reset) if the Codex is still open. */
	private static void refresh(Player player) {
		if (player.isVisibleInterface(INTERFACE))
			show(player, view(player).tab);
	}

	/* ---------------- shop ---------------- */

	private static List<Ware> wares(boolean prestige) {
		List<Ware> list = new ArrayList<>();
		if (!prestige) {
			// the old rewards-interface shop (points)
			if (World.isEco()) {
				list.add(new Ware(11866, 1, 75, false));    // Slayer ring
				list.add(new Ware(11875, 250, 35, false));  // Broad bolts
				list.add(new Ware(4160, 250, 35, false));   // Broad arrows
				list.add(new Ware(13226, 1, 750, false));   // Herb sack
				list.add(new Ware(12791, 1, 750, false));   // Rune pouch
				list.add(new Ware(11941, 1, 10, false));    // Looting bag
			} else {
				list.add(new Ware(11866, 1, 75, false));
				list.add(new Ware(12791, 1, 300, false));
				list.add(new Ware(11941, 1, 100, false));
			}
		}
		for (ShopItem s : NewShopHandler.slayerStore.getShopItemList()) {
			boolean points = s.getCategory() == ShopCategories.SLAYER;
			boolean isPrestige = points && s.getCost() >= 650;
			if (isPrestige != prestige)
				continue;
			list.add(new Ware(s.getItem().getId(), Math.max(1, s.getItem().getAmount()), s.getCost(), !points));
		}
		return list;
	}

	private static void drawShop(Player player, PacketSender ps, View v, boolean prestige) {
		v.wares = wares(prestige);
		for (int c = 0; c < SHOP_CARDS; c++) {
			int card = FIRST_SHOP + c * SHOP_STRIDE;
			boolean present = c < v.wares.size();
			ps.setHidden(INTERFACE, card, !present);
			if (!present)
				continue;
			Ware w = v.wares.get(c);
			ps.sendItem(INTERFACE, card + 5, w.itemId, w.amount);
			ps.sendString(INTERFACE, card + 6, name(w));
			ps.sendString(INTERFACE, card + 9, price(w.price, w.coins));
		}
		scrollbar(player, v, v.wares.size(), SHOP_SCROLL, SHOP_SCROLLBAR, ((v.wares.size() + SHOP_COLS - 1) / SHOP_COLS - 1) * SHOP_PITCH + SHOP_H);
	}

	private static String name(Ware w) {
		ObjType def = ObjType.get(w.itemId);
		String name = def == null ? "Item " + w.itemId : def.name;
		return w.amount > 1 ? name + " x" + w.amount : name;
	}

	private static String price(int price, boolean coins) {
		return coins ? NumberUtils.formatNumber(price) + " gp" : NumberUtils.formatNumber(price) + " Pts";
	}

	private static void wareAction(Player player, Ware w, int option) {
		// menu (BuildSlayerCodexInterface), the OSRS shop order: Value (left-click) / Buy 1 / 5 / 10 / 50 / X / Examine
		switch (option) {
			case 1 -> player.sendMessage(TAG + name(w) + " costs " + (w.coins ? NumberUtils.formatNumber(w.price) + " coins."
				: NumberUtils.formatNumber(w.price) + " Slayer Points."));
			case 2 -> buy(player, w, 1);
			case 3 -> buy(player, w, 5);
			case 4 -> buy(player, w, 10);
			case 5 -> buy(player, w, 50);
			case 6 -> player.integerInput("How many would you like to buy?", amount -> buy(player, w, amount));
			default -> Item.examine(player, w.itemId);
		}
	}

	private static void buy(Player player, Ware w, int amount) {
		if (amount <= 0)
			return;
		ObjType def = ObjType.get(w.itemId);
		if (def == null)
			return;
		int balance = w.coins ? player.getInventory().getAmount(995) : VarPlayerRepository.SLAYER_POINTS.get(player);
		int affordable = (int) Math.min(amount, balance / (long) Math.max(1, w.price));
		if (affordable <= 0) {
			player.sendMessage(TAG + "You need " + price(w.price, w.coins) + " to buy " + name(w) + ".");
			return;
		}
		// space: a stackable item needs one slot (or its stack), anything else a slot each
		int canHold = def.stackable ? (player.getInventory().hasRoomFor(w.itemId, (long) affordable * w.amount > Integer.MAX_VALUE ? Integer.MAX_VALUE
			: affordable * w.amount) ? affordable : 0) : Math.min(affordable, player.getInventory().getFreeSlots() / Math.max(1, w.amount));
		if (canHold <= 0) {
			player.sendMessage(TAG + "You don't have enough inventory space.");
			return;
		}
		int bought = canHold;
		long cost = (long) bought * w.price;
		if (w.coins)
			player.getInventory().remove(995, (int) cost);
		else
			VarPlayerRepository.SLAYER_POINTS.set(player, VarPlayerRepository.SLAYER_POINTS.get(player) - (int) cost);
		player.getInventory().add(w.itemId, bought * w.amount);
		player.sendMessage(TAG + "You bought " + (bought > 1 ? bought + " x " : "") + name(w) + " for "
			+ price((int) cost, w.coins) + ".");
		if (bought < amount)
			player.sendMessage(TAG + "You could only afford / carry " + bought + ".");
		refresh(player);
	}

	/* ---------------- task ---------------- */

	private static void drawTask(Player player, PacketSender ps) {
		int taskId = VarPlayerRepository.SLAYER_TASK.get(player);
		int left = VarPlayerRepository.SLAYER_TASK_AMOUNT.get(player);
		SlayerCreature task = SlayerCreature.lookup(taskId);
		boolean boss = VarPlayerRepository.BOSS_TASK.get(player) != 0;
		boolean hasTask = (task != null || boss) && left > 0;
		ps.sendItem(INTERFACE, TASK_ITEM, hasTask ? 4155 : -1, 1); // the enchanted gem
		ps.sendString(INTERFACE, TASK_NAME, hasTask ? SlayerUnlock.taskName(player, taskId) : GREY + "No task" + END);
		ps.sendString(INTERFACE, TASK_LEFT, hasTask ? WHITE + NumberUtils.formatNumber(left) + END + " left to slay" : "Visit a master for one.");
		ps.sendString(INTERFACE, TASK_MASTER, "Assigned by: " + WHITE + masterName(VarPlayerRepository.SLAYER_MASTER.get(player)) + END
			+ "<br>Tasks completed: " + WHITE + NumberUtils.formatNumber(player.totalSlayerTasksCompleted) + END);
		ps.sendString(INTERFACE, CANCEL + LABEL, "Cancel Task (" + SlayerUnlock.cancelCost(player) + " Pts)");
		ps.sendString(INTERFACE, BLOCK + LABEL, "Block Task (" + SlayerUnlock.blockCost(player) + " Pts)");
		int blocked = 0;
		for (int r = 0; r < BLOCKED_ROWS; r++) {
			int id = VarPlayerRepository.BLOCKED_TASKS[r].get(player);
			int row = FIRST_BLOCKED + r * BLOCKED_STRIDE;
			boolean used = id != 0;
			if (used)
				blocked++;
			ps.sendString(INTERFACE, row + 1, used ? blockedName(player, id) : GREY + "- empty slot -" + END);
			for (int k = 0; k < STONE; k++)
				ps.setHidden(INTERFACE, row + 2 + k, !used && k != PRESSED);
			ps.setHidden(INTERFACE, row + 2 + PRESSED, true);
		}
		ps.sendString(INTERFACE, BLOCK_TITLE, "Blocked Tasks " + GREY + "(" + blocked + "/" + BLOCKED_ROWS + ")" + END);
	}

	private static String blockedName(Player player, int id) {
		try {
			return io.ruin.cache.EnumMap.get(693).strings().get(id);
		} catch (Exception e) {
			return "Task " + id;
		}
	}

	private static String masterName(int id) {
		return switch (id) {
			case SlayerMaster.TURAEL_ID -> "Turael";
			case SlayerMaster.MAZCHNA_ID -> "Mazchna";
			case SlayerMaster.VANNAKA_ID -> "Vannaka";
			case SlayerMaster.CHAELDAR_ID -> "Chaeldar";
			case SlayerMaster.STEVE_ID -> "Nieve";
			case SlayerMaster.DURADEL_ID -> "Duradel";
			case SlayerMaster.KRYSTILIA_ID -> "Krystilia";
			case SlayerMaster.KONAR_ID -> "Konar quo Maten";
			case SlayerMaster.NOMAD_ID -> "Nomad";
			default -> "-";
		};
	}

	/* ---------------- helpers ---------------- */

	private static void scrollbar(Player player, View v, int size, int container, int bar, int contentHeight) {
		if (v.scrollTab == v.tab && v.scrollSize == size)
			return; // already set up: re-sending would jump the list back to the top
		v.scrollTab = v.tab;
		v.scrollSize = size;
		int count = contentHeight <= VIEW_HEIGHT ? 0 : (int) Math.ceil((contentHeight - VIEW_HEIGHT / 3D) / 15D);
		ScrollbarClientScript.create().interfaceId(INTERFACE).containerId(container).scrollbarChildId(bar)
			.childrenCount(count).withDarkGraphics().build().send(player);
	}

	private static View view(Player player) {
		return views.computeIfAbsent(player.uuid(), k -> new View());
	}
}
