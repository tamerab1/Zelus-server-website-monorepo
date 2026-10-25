package io.ruin.model.item.actions.impl.boxes.mystery;

import discord.webhooks.notifications.RareBoxOpenHook;
import io.ruin.api.utils.Random;
import io.ruin.cache.Icon;
import io.ruin.cache.ObjType;
import io.ruin.model.ScrollbarClientScript;
import io.ruin.model.entity.player.Player;
import io.ruin.model.entity.shared.listeners.LoginListener;
import io.ruin.model.inter.InterfaceHandler;
import io.ruin.model.inter.ToplevelComponent;
import io.ruin.model.inter.actions.SimpleAction;
import io.ruin.model.item.Item;
import io.ruin.model.item.actions.ItemAction;
import io.ruin.model.item.loot.LootItem;
import io.ruin.model.item.loot.LootTable;
import io.ruin.network.PacketSender;
import io.ruin.utility.Broadcast;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The "Zelus Spinner" (interface 1154, built by .dev/cache-restore-tool/src/BuildSpinnerInterface.java): opening one of
 * the main mystery boxes shows a roulette instead of handing the reward over straight away.
 * <p>
 * Spin! removes the box and rolls the reward on the server first (each box keeps its own rules: the donator box's
 * no-repeat list and holiday-item save), stores it on the account (pendingSpin*, so a disconnect can't lose it), then
 * sends a 40-slot strip and starts a client-side clientscript (10751 -> 10750 on the track's onTimer, every 20ms
 * client cycle) that scrolls it with an ease-out until the pre-rolled reward stops under the needle; only then is
 * it granted, with the box's original messages, broadcasts and collection log. Quick open skips the animation.
 * The bottom panels show every possible reward (rarity border + chance) and recent pulls.
 */
public final class MysteryBoxSpinner {

	public static final int INTERFACE = 1154;
	private static final int CLOSE = 2, TITLE = 3, SUB_HEADER = 4;
	/** Track: 9 x (fill, border, item); the middle (index 4) is the winning slot. */
	/** Track: a scrollable layer (9 slots visible) holding 40 x (fill, border, item); the needle is over visible slot 4. */
	private static final int TRACK_LAYER = 10, FIRST_TRACK = 11, TRACK_STRIDE = 3, TRACK_BORDER = 1, TRACK_ITEM = 2,
		TRACK = 40, MIDDLE = 4, SLOT_PITCH = 50;
	/** Client scripts (AddSpinnerScripts): start(track, target scroll x) attaches the per-cycle easing tick (10750). */
	private static final int SCRIPT_SPIN_START = 10751;
	/** The client's ease-out takes about 3.5 seconds; pay out on the tick after it lands. */
	private static final int SPIN_TICKS = 7;
	private static final int BOX_COUNT = 135, SPIN = 136, QUICK = 138;
	/** Reward grid: 60 x (fill, border, item, chance tag), 6 per row. */
	private static final int GRID_SCROLL = 146, GRID_SCROLLBAR = 147, FIRST_CELL = 148, CELL_STRIDE = 4, CELL_BORDER = 1,
		CELL_ITEM = 2, CELL_TAG = 3, CELLS = 60, GRID_COLS = 6, GRID_ROW_PITCH = 44, GRID_HEIGHT = 113;
	private static final int FIRST_FEED = 394, FEED = 8;

	private static final String GOLD = "<col=ffc35a>", GREY = "<col=9a9a9a>", WHITE = "<col=ffffff>", GREEN = "<col=00ff00>",
		ORANGE = "<col=ff981f>", END = "</col>";
	private static final int BORDER = 0x5a4d3a;

	/** Rarity tiers by chance (percent): border colour and label. */
	private enum Rarity {
		MYTHIC(0.25, 0xff8c00, 0xdc2626, "Mythic"), LEGENDARY(1, 0xc86fff, 0x9333ea, "Legendary"), RARE(3, 0x4fa3ff, 0x0284c7, "Rare"),
		UNCOMMON(10, 0x3cb043, 0x15803d, "Uncommon"), COMMON(101, 0x9a9a9a, 0x4b5563, "Common");
		final double below;
		/** Bright border colour on the dark spinner panels. */
		final int colour;
		/** Dark, shadowed tag colour for the parchment chatbox (the bright ones wash out there). */
		final int chatColour;
		final String label;

		Rarity(double below, int colour, int chatColour, String label) {
			this.below = below;
			this.colour = colour;
			this.chatColour = chatColour;
			this.label = label;
		}

		static Rarity of(double percent) {
			for (Rarity rarity : values())
				if (percent < rarity.below)
					return rarity;
			return COMMON;
		}
	}

	/**
	 * A box the spinner opens: its loot table and the original box's rules -- alwaysBroadcast (every pull is announced,
	 * as the 3rd age box did), bonus (an extra roll the box made on open, e.g. the slayer casket's points).
	 */
	private record SpinBox(String name, LootTable table, boolean donator, boolean collectionLog, boolean alwaysBroadcast,
						   java.util.function.Consumer<Player> bonus, boolean holidaySave) {
		SpinBox(String name, LootTable table, boolean donator, boolean collectionLog) {
			this(name, table, donator, collectionLog, false, null, false);
		}

		SpinBox(String name, LootTable table, boolean donator, boolean collectionLog, boolean alwaysBroadcast,
				java.util.function.Consumer<Player> bonus) {
			this(name, table, donator, collectionLog, alwaysBroadcast, bonus, false);
		}
	}

	private static final Map<Integer, SpinBox> BOXES = new LinkedHashMap<>();

	/** Per-player spinner state (the player object is recreated on every login). */
	private static final class State {
		int boxId;
		boolean spinning, quick;
		final LinkedList<String> recent = new LinkedList<>();
	}

	private static final Map<Player, State> STATES = new WeakHashMap<>();
	/** Server-wide feed of broadcast-worthy pulls (newest first). */
	private static final LinkedList<String> SERVER_FEED = new LinkedList<>();

	private MysteryBoxSpinner() {
	}

	private static State state(Player player) {
		return STATES.computeIfAbsent(player, p -> new State());
	}

	/**
	 * The rest of the reward boxes, taken over after their own classes registered (StaticInit calls this after the
	 * last box class; the Open option goes to the last registration). Each keeps its table and its open-time rules.
	 */
	public static void registerMoreBoxes() {
		java.util.Map<Integer, SpinBox> more = new LinkedHashMap<>();
		more.put(290, new SpinBox("Super Mystery Box", SuperMysteryBox.ECO_MYSTERY_BOX_TABLE, false, true));
		more.put(30426, new SpinBox("Limited Mystery Box", LimitedMysteryBox.ECO_MYSTERY_BOX_TABLE, false, true));
		more.put(30185, new SpinBox("Summer Mystery Box", SummerMysteryBox.summerMboxLoot, false, true));
		more.put(43817, new SpinBox("Echo Orb Mystery Box", EchoOrbMysteryBox.summerMboxLoot, false, false));
		more.put(6831, new SpinBox("3rd Age Mystery Box", ThirdAgeMysteryBox.THIRD_AGE_BOX_TABLE, false, true, true, null));
		more.put(22330, new SpinBox("PvP Armour Mystery Box", PVPArmourMysteryBox.PVP_ARMOUR_BOX_TABLE, false, false));
		more.put(6829, new SpinBox("Vote Mystery Box", VoteMysteryBox.ECO_VOTING_BOX_TABLE, false, false));
		more.put(30430, new SpinBox("Ultra Mystery Box", MysteryBox.ULTRA_MYSTERY_BOX, false, true, false, null, true));
		more.put(30528, new SpinBox("Gauntlet Mystery Box", MysteryBox.GAUNTLET_MYSTERY_BOX_TABLE, false, false));
		more.put(23071, new SpinBox("Slayer Casket", SlayerCasket.SLAYER_CASKET_LOOT, false, false, false, player -> {
			if (Random.rollDie(20, 1)) {
				io.ruin.model.var.VarPlayerRepository.SLAYER_POINTS.set(player, 25 + io.ruin.model.var.VarPlayerRepository.SLAYER_POINTS.get(player));
				player.sendMessage("You recieved 25 slayer points!");
			}
		}));
		BOXES.putAll(more);
		takeOver(more.keySet());
	}

	/** Adds a box from elsewhere (e.g. the Rivals mystery box): Open goes to the spinner with this table. */
	public static void registerBox(int boxId, String name, LootTable table) {
		BOXES.put(boxId, new SpinBox(name, table, false, false));
		takeOver(java.util.List.of(boxId));
	}

	private static void takeOver(java.util.Collection<Integer> boxIds) {
		for (int boxId : boxIds) {
			ItemAction open = (player, item) -> {
				// an unclaimed reward on the old vote wheel (702) is finished there first
				if (item.getId() == 6829 && !player.claimedBox) {
					VoteMysteryBox.open(player, item);
					return;
				}
				open(player, item.getId());
			};
			if (!ItemAction.registerInventory(boxId, "Open", open))
				ItemAction.registerInventory(boxId, "open", open);
		}
	}

	/** Called at the end of MysteryBox.register(): takes over the boxes' Open option (last registration wins). */
	static void register() {
		BOXES.put(6199, new SpinBox("Mystery Box", MysteryBox.MYSTERY_BOX_TABLE, false, true));
		BOXES.put(42418, new SpinBox("Arcane Mystery Box", MysteryBox.MYSTERY_BOX_TABLE, false, true));
		BOXES.put(30461, new SpinBox("Donator Mystery Box", MysteryBox.ADVANCED_MYSTERY_BOX_COMMON_TABLE, true, false));
		BOXES.put(30444, new SpinBox("Donator Mystery Box", MysteryBox.ADVANCED_MYSTERY_BOX_COMMON_TABLE, true, false));
		BOXES.put(30529, new SpinBox("ToB Mystery Box", MysteryBox.TOB_MYSTERY_BOX_TABLE, false, false));
		BOXES.put(30530, new SpinBox("CoX Mystery Box", MysteryBox.COX_MYSTERY_BOX_TABLE, false, false));
		BOXES.put(30578, new SpinBox("Wilderness Mystery Box", MysteryBox.WILDERNESS_MYSTERY_BOX_TABLE, false, false));
		takeOver(BOXES.keySet());

		// a spin interrupted by a disconnect is paid out on the next login
		LoginListener.register(player -> {
			if (player.pendingSpinItemId > 0) {
				int id = player.pendingSpinItemId, amount = Math.max(1, player.pendingSpinAmount);
				player.pendingSpinItemId = 0;
				player.pendingSpinAmount = 0;
				player.getInventory().addOrDrop(id, amount);
				player.sendMessage("<col=e39b00><shad=000000>[Zelus Spinner]</shad></col> Your interrupted spin paid out: <col=e39b00><shad=000000>"
					+ amount + " x " + ObjType.get(id).name + "</shad></col>.");
			}
		});

		InterfaceHandler.register(INTERFACE, h -> {
			h.actions[CLOSE] = (SimpleAction) p -> p.closeInterface(ToplevelComponent.MAINMODAL);
			h.actions[SPIN] = (SimpleAction) MysteryBoxSpinner::spin;
			h.actions[QUICK] = (SimpleAction) p -> {
				State s = state(p);
				s.quick = !s.quick;
				sendControls(p);
			};
			for (int c = 0; c < CELLS; c++) {
				int cell = c;
				h.actions[FIRST_CELL + c * CELL_STRIDE + CELL_ITEM] = (SimpleAction) p -> examineCell(p, cell);
			}
		});
	}

	/* ---------------- screen ---------------- */

	public static void open(Player player, int boxId) {
		SpinBox box = BOXES.get(boxId);
		if (box == null)
			return;
		State s = state(player);
		if (s.spinning) {
			player.sendMessage("Your spinner is still rolling!");
			return;
		}
		s.boxId = boxId;
		player.openInterface(ToplevelComponent.MAINMODAL, INTERFACE);
		PacketSender ps = player.getPacketSender();
		ps.sendString(INTERFACE, TITLE, ORANGE + box.name() + " · Zelus Vault" + END);
		ps.sendString(INTERFACE, SUB_HEADER, "Press " + GOLD + "Spin!" + END + " to open a box. The reward is rolled the moment you spin.");
		// a resting strip of possible rewards
		List<Item> strip = new ArrayList<>();
		for (int k = 0; k < TRACK; k++)
			strip.add(preview(box));
		sendStrip(player, box, strip);
		scrollTo(player, 0);
		sendGrid(player, box);
		sendControls(player);
		sendFeed(player);
	}

	private static void sendControls(Player player) {
		State s = state(player);
		PacketSender ps = player.getPacketSender();
		int boxes = player.getInventory().getAmount(s.boxId);
		ps.sendString(INTERFACE, BOX_COUNT, WHITE + "Boxes: " + END + (boxes > 0 ? GREEN : "<col=ff5050>") + boxes + END);
		ps.sendString(INTERFACE, SPIN + 1, s.spinning ? GREY + "Spinning..." + END : "Spin!");
		ps.sendString(INTERFACE, QUICK + 1, "Quick open: " + (s.quick ? GREEN + "On" + END : GREY + "Off" + END));
	}

	/** Sends the whole strip (TRACK items) with rarity borders. */
	private static void sendStrip(Player player, SpinBox box, List<Item> strip) {
		PacketSender ps = player.getPacketSender();
		Map<Integer, Double> chances = chances(box.table());
		for (int k = 0; k < TRACK; k++) {
			Item item = strip.get(k);
			int slot = FIRST_TRACK + k * TRACK_STRIDE;
			ps.sendItem(INTERFACE, slot + TRACK_ITEM, item.getId(), item.getAmount());
			setColour(player, slot + TRACK_BORDER, Rarity.of(chances.getOrDefault(item.getId(), 100D)).colour);
		}
	}

	/**
	 * Starts the client-side scroll of the strip to {@code target} px (0 = rest): clientscript 10751 resets the scroll
	 * and attaches 10750 as the track's onTimer, which eases it in every client cycle and removes itself on arrival.
	 */
	private static void scrollTo(Player player, int target) {
		player.getPacketSender().sendClientScript(SCRIPT_SPIN_START, "Ii", INTERFACE << 16 | TRACK_LAYER, target);
	}

	private static void sendGrid(Player player, SpinBox box) {
		PacketSender ps = player.getPacketSender();
		Map<Integer, Double> chances = chances(box.table());
		List<Map.Entry<Integer, Double>> rewards = new ArrayList<>(chances.entrySet());
		rewards.sort(Map.Entry.comparingByValue()); // rarest first
		for (int c = 0; c < CELLS; c++) {
			int cell = FIRST_CELL + c * CELL_STRIDE;
			boolean used = c < rewards.size();
			for (int part = 0; part < CELL_STRIDE; part++)
				ps.setHidden(INTERFACE, cell + part, !used);
			if (!used)
				continue;
			Map.Entry<Integer, Double> reward = rewards.get(c);
			Rarity rarity = Rarity.of(reward.getValue());
			ps.sendItem(INTERFACE, cell + CELL_ITEM, reward.getKey(), 1);
			setColour(player, cell + CELL_BORDER, rarity.colour);
			ps.sendString(INTERFACE, cell + CELL_TAG, "<col=" + Integer.toHexString(rarity.colour) + ">" + percent(reward.getValue()) + END);
		}
		int rows = (Math.min(CELLS, rewards.size()) + GRID_COLS - 1) / GRID_COLS;
		int contentHeight = rows * GRID_ROW_PITCH - 2;
		int count = contentHeight <= GRID_HEIGHT ? 0 : (int) Math.ceil((contentHeight - GRID_HEIGHT / 3D) / 15D);
		ScrollbarClientScript.create().interfaceId(INTERFACE).containerId(GRID_SCROLL).scrollbarChildId(GRID_SCROLLBAR)
			.childrenCount(count).withDarkGraphics().build().send(player);
	}

	private static void sendFeed(Player player) {
		State s = state(player);
		PacketSender ps = player.getPacketSender();
		List<String> lines = new ArrayList<>();
		for (String line : s.recent)
			if (lines.size() < FEED)
				lines.add(line);
		if (lines.size() < FEED && !SERVER_FEED.isEmpty()) {
			lines.add(GOLD + "Server jackpots" + END);
			for (String line : SERVER_FEED)
				if (lines.size() < FEED)
					lines.add(line);
		}
		if (lines.isEmpty())
			lines.add(GREY + "No pulls yet: spin a box!" + END);
		for (int l = 0; l < FEED; l++)
			ps.sendString(INTERFACE, FIRST_FEED + l, l < lines.size() ? lines.get(l) : "");
	}

	/* ---------------- spinning ---------------- */

	private static void spin(Player player) {
		State s = state(player);
		SpinBox box = BOXES.get(s.boxId);
		if (box == null || s.spinning)
			return;
		if (player.pendingSpinItemId > 0) {
			player.sendMessage("Your previous spin hasn't paid out yet.");
			return;
		}
		Item boxItem = player.getInventory().findItem(s.boxId);
		if (boxItem == null) {
			player.sendMessage("You don't have any more of these boxes.");
			return;
		}

		// roll first, then take the box (each box's own rules), and keep the reward on the account until it's paid
		Item reward = roll(player, box);
		consumeBox(player, box, boxItem);
		if (box.bonus() != null)
			box.bonus().accept(player);
		player.pendingSpinItemId = reward.getId();
		player.pendingSpinAmount = reward.getAmount();

		if (s.quick) {
			List<Item> strip = new ArrayList<>();
			for (int k = 0; k < TRACK; k++)
				strip.add(k == MIDDLE ? reward : preview(box));
			sendStrip(player, box, strip);
			scrollTo(player, 0);
			pay(player, box, reward);
			return;
		}

		// strip: random previews with the reward placed 5 slots from the end, so at the end of the scroll (the last 9
		// slots visible) it sits under the needle (visible slot 4)
		List<Item> strip = new ArrayList<>();
		for (int i = 0; i < TRACK; i++)
			strip.add(preview(box));
		int winner = TRACK - 1 - (8 - MIDDLE);
		strip.set(winner, reward);
		int target = (winner - MIDDLE) * SLOT_PITCH;
		sendStrip(player, box, strip);
		scrollTo(player, target); // the client animates the whole spin (20ms cycles, ease-out)
		s.spinning = true;
		sendControls(player);
		player.startEvent(e -> {
			try {
				e.delay(SPIN_TICKS);
			} finally {
				s.spinning = false;
			}
			pay(player, box, reward);
		});
	}

	/** The box's roll: the donator box never repeats one of its last 20 rewards (as before). */
	private static Item roll(Player player, SpinBox box) {
		if (!box.donator())
			return box.table().rollItem();
		Item reward;
		while (true) {
			reward = box.table().rollItem();
			if (!player.recentMysteryBoxRewards.contains(reward.getId())) {
				if (player.recentMysteryBoxRewards.size() >= 20)
					player.recentMysteryBoxRewards.remove(0);
				player.recentMysteryBoxRewards.add(reward.getId());
				return reward;
			}
		}
	}

	/** Removes one box; a worn holiday item may save the donator box (as before). */
	private static void consumeBox(Player player, SpinBox box, Item boxItem) {
		if (box.donator() || box.holidaySave()) {
			int holidayBoost = MysteryBox.wearingChristmasItem(player);
			if (holidayBoost > 0 && Random.get(1, 100) <= holidayBoost) {
				player.sendMessage("Your holiday item saves you from losing the box!");
				return;
			}
		}
		boxItem.remove(1);
	}

	/** Pays the pre-rolled reward with the box's original messages, broadcast and collection log. */
	private static void pay(Player player, SpinBox box, Item reward) {
		if (player.pendingSpinItemId != reward.getId())
			return; // already paid (e.g. on a relog)
		player.pendingSpinItemId = 0;
		player.pendingSpinAmount = 0;
		player.getInventory().addOrDrop(reward);
		String name = reward.getDef().name;
		player.sendMessage(Icon.YELLOW_KEY.tag() + " <col=e39b00><shad=000000>[Zelus Spinner]</shad></col> You have received <col=e39b00><shad=000000>"
			+ (reward.getAmount() > 1 ? reward.getAmount() + " x " : "") + name + "</shad></col> from the " + box.name() + "!");
		if (box.collectionLog())
			player.addToCollectionLog(reward);
		if (reward.lootBroadcast != null || box.alwaysBroadcast()) {
			Broadcast.WORLD.sendNewsDropMessage(player, Icon.YELLOW_KEY, "<col=000000>" + player.getName(), " received <shad=D80808>"
				+ reward.getAmount() + "x " + name.toLowerCase() + "</shad> reward from a " + box.name() + "!");
			if (box.donator()) {
				var json = new JSONObject();
				json.put("player", player.getName().replaceAll("<[^>]*>", ""));
				json.put("player_x", player.getPosition().getX());
				json.put("player_y", player.getPosition().getY());
				json.put("player_z", player.getPosition().getZ());
				json.put("item", name);
				json.put("item_id", reward.getId());
				json.put("source", box.name());
				RareBoxOpenHook.sendBoxDiscordMessage(json);
			}
			SERVER_FEED.addFirst(GOLD + player.getName() + END + ": " + shorten(name, 16));
			while (SERVER_FEED.size() > FEED)
				SERVER_FEED.removeLast();
		}
		State s = state(player);
		Rarity rarity = Rarity.of(chances(box.table()).getOrDefault(reward.getId(), 100D));
		s.recent.addFirst("<col=" + Integer.toHexString(rarity.colour) + ">" + shorten(name, 20) + END
			+ (reward.getAmount() > 1 ? GREY + " x" + reward.getAmount() + END : ""));
		while (s.recent.size() > FEED)
			s.recent.removeLast();
		if (player.isVisibleInterface(INTERFACE)) {
			sendControls(player);
			sendFeed(player);
		}
	}

	/* ---------------- helpers ---------------- */

	private static Item preview(SpinBox box) {
		Item item = box.table().rollItem();
		return item == null ? new Item(995, 1) : item;
	}

	/** Each item's chance (percent) of being the reward: its table's share times its weight in that table. */
	private static Map<Integer, Double> chances(LootTable table) {
		Map<Integer, Double> chances = new LinkedHashMap<>();
		if (table.tables == null)
			return chances;
		double tablesWeight = 0;
		for (LootTable.ItemsTable t : table.tables)
			tablesWeight += t.weight;
		for (LootTable.ItemsTable t : table.tables) {
			if (t.items == null || tablesWeight <= 0)
				continue;
			double itemsWeight = 0;
			for (LootItem item : t.items)
				itemsWeight += item.weight;
			for (LootItem item : t.items) {
				if (itemsWeight <= 0)
					continue;
				double percent = (t.weight / tablesWeight) * (item.weight / itemsWeight) * 100D;
				chances.merge(item.id, percent, Double::sum);
			}
		}
		return chances;
	}

	private static String percent(double percent) {
		if (percent >= 10)
			return Math.round(percent) + "%";
		if (percent >= 1)
			return String.format("%.1f%%", percent);
		return String.format("%.2f%%", percent);
	}

	private static void setColour(Player player, int component, int colour) {
		player.getPacketSender().sendClientScript(45, "", INTERFACE << 16 | component, colour);
	}

	private static void examineCell(Player player, int cell) {
		SpinBox box = BOXES.get(state(player).boxId);
		if (box == null)
			return;
		List<Map.Entry<Integer, Double>> rewards = new ArrayList<>(chances(box.table()).entrySet());
		rewards.sort(Map.Entry.comparingByValue());
		if (cell >= rewards.size())
			return;
		Map.Entry<Integer, Double> reward = rewards.get(cell);
		Rarity rarity = Rarity.of(reward.getValue());
		// chatbox is parchment: dark shadowed tag, black name, dark charcoal chance
		player.sendMessage("<col=" + String.format("%06x", rarity.chatColour) + "><shad=000000>[" + rarity.label + "]</shad>" + END
			+ " <col=000000>" + ObjType.get(reward.getKey()).name + END
			+ " <col=1f2937>- " + String.format(java.util.Locale.ROOT, "%.2f", reward.getValue()) + "% chance" + END);
	}

	private static String shorten(String text, int max) {
		return text.length() <= max ? text : text.substring(0, max - 3).trim() + "...";
	}
}
