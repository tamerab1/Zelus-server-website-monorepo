package io.ruin.model.content.donatordeals;

import io.ruin.api.utils.JsonUtils;
import io.ruin.api.utils.ServerWrapper;
import io.ruin.cache.ObjType;
import io.ruin.model.World;
import io.ruin.model.entity.npc.NPCAction;
import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.InterfaceHandler;
import io.ruin.model.inter.ToplevelComponent;
import io.ruin.model.inter.actions.SimpleAction;
import io.ruin.model.inter.handlers.shopinterface.CustomShop2;
import io.ruin.network.PacketSender;
import io.ruin.services.Loggers;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.time.Instant;
import java.util.*;

/**
 * Zelus Donator Deals (interface 1166, built by .dev/cache-restore-tool BuildDonatorDealsInterface): timed flash deals
 * paid in donator points, shown as up to 4 cards with the crossed-out original price, the discount badge, the package
 * contents, a live countdown and a Purchase button, plus the donator rank progress bar.
 * <p>
 * Deals come from cfg/donator_deals.json and are HOT-RELOADED: the file is re-read within a few seconds of being
 * saved, or straight away with ::reloaddeals (admin). Fields per deal: dealId, title, originalCost, discountedCost,
 * discountPercent (0 = computed), itemIds[], amounts[], durationMinutes, startTime (ISO-8601 like
 * "2026-10-15T18:00:00Z", or epoch millis; empty = the moment the deal was first loaded), limitPerPlayer (default 1,
 * 0 = unlimited). Purchases per player are saved in runtime/saves/donator_deals.json.
 * <p>
 * Opened from the Donator Point Store (NPC 5523) "Deals" option, the "Deals" button on the Donation Shop tab of the
 * shop screen (891:130), and ::deals.
 */
public final class DonatorDeals {

	public static final int INTERFACE = 1166;
	public static final int STORE_NPC = 5523;
	private static final int CLOSE = 2, TITLE = 3, SHOP_TAB = 4, DEALS_TAB = 8, BALANCE = 12, FIRST_CARD = 13,
		CARD_STRIDE = 31, CARDS = 4, CARD_TITLE = 5, CARD_BADGE_TEXT = 10, CARD_FIRST_ITEM = 15, CARD_OLD_PRICE = 21,
		CARD_PRICE = 22, CARD_HOURGLASS = 24, CARD_TIME = 26, CARD_BUY = 27, CARD_BUY_SHADE = 29, CARD_BUY_LABEL = 30,
		NO_DEALS = 137, BAR_GREEN = 142, BAR_GOLD = 144, BAR_TEXT = 146, BAR_WIDTH = 462, MAX_ITEMS = 6;
	/** The recessed item tray inside a card (card-relative): items are centred in it, 1 row for up to 3, else 2. */
	private static final int TRAY_X = 3, TRAY_Y = 52, TRAY_W = 106, TRAY_H = 66, ITEM_W = 36, ITEM_H = 32, ITEM_PITCH_X = 35,
		ITEM_PITCH_Y = 33;
	private static final int HOURGLASS_ITEM = 5610, CONFIRM_TICKS = 10, RELOAD_CHECK_TICKS = 8;

	/** Donator ranks by total $ donated (Player.isRegularDonator ... isSupremeDonator). */
	private static final int[] RANK_DOLLARS = {10, 50, 100, 250, 400, 700, 1000, 1750};
	private static final String[] RANK_NAMES = {"Regular", "Super", "Elite", "Noble", "Gold", "Platinum", "Legendary", "Supreme"};

	private static final String WHITE = "<col=ffffff>", GREY = "<col=9a9a9a>", GREEN = "<col=00ff00>",
		GOLD = "<col=ffc35a>", RED = "<col=ff5050>", ORANGE = "<col=ff981f>", END = "</col>";
	private static final String TAG = "<col=b85900><shad=000000>[Zelus Deals]:</shad></col> ";

	/* ---------------- config ---------------- */

	static final class Deal {
		String dealId;
		String title;
		int originalCost;
		int discountedCost;
		int discountPercent;
		int[] itemIds;
		int[] amounts;
		int durationMinutes;
		String startTime;
		Integer limitPerPlayer;

		transient long startMillis, endMillis;

		int percent() {
			if (discountPercent > 0)
				return discountPercent;
			return originalCost <= 0 ? 0 : Math.round((originalCost - discountedCost) * 100f / originalCost);
		}

		int limit() {
			return limitPerPlayer == null ? 1 : limitPerPlayer;
		}
	}

	private static final class Config {
		List<Deal> deals = new ArrayList<>();
	}

	/* ---------------- saved state ---------------- */

	private static final class SaveState {
		/** dealId -> player uuid -> times bought. */
		Map<String, Map<String, Integer>> purchases = new HashMap<>();
		/** dealId -> when a deal without a startTime was first loaded (so a reload doesn't restart its clock). */
		Map<String, Long> firstSeen = new HashMap<>();
	}

	private static List<Deal> deals = new ArrayList<>();
	private static SaveState state = new SaveState();
	private static long loadedModified = -1;

	/** Per-viewer: the deal ids last drawn (a change forces a full redraw) and a pending purchase confirmation. */
	private static final class View {
		List<String> drawn = new ArrayList<>();
		String confirmDeal;
		long confirmUntilTick;
	}

	private static final Map<String, View> views = new HashMap<>();

	private DonatorDeals() {
	}

	public static void register() {
		loadState();
		reload(null);
		InterfaceHandler.register(INTERFACE, h -> {
			h.actions[CLOSE] = (SimpleAction) Player::closeInterfaces;
			h.actions[SHOP_TAB] = (SimpleAction) CustomShop2::openDonatorStoreShop;
			h.actions[DEALS_TAB] = (SimpleAction) DonatorDeals::draw;
			for (int c = 0; c < CARDS; c++) {
				int card = c;
				int base = FIRST_CARD + c * CARD_STRIDE;
				h.actions[base + CARD_BUY] = (SimpleAction) p -> purchase(p, card);
				for (int s = 0; s < MAX_ITEMS; s++) {
					int slot = s;
					h.actions[base + CARD_FIRST_ITEM + s] = (SimpleAction) p -> examine(p, card, slot);
				}
			}
			h.closedAction = (p, i) -> {
				if (i != INTERFACE)
					views.remove(p.uuid());
			};
		});
		NPCAction.register(STORE_NPC, 3, (player, npc) -> open(player));
		World.startEvent(e -> {
			int tick = 0;
			while (true) {
				e.delay(1);
				if (++tick % RELOAD_CHECK_TICKS == 0)
					reloadIfChanged();
				List<Deal> active = active();
				for (Player p : World.players()) {
					if (!p.isVisibleInterface(INTERFACE))
						continue;
					View v = view(p);
					if (!v.drawn.equals(ids(active)))
						draw(p);
					else
						sendTimers(p, active);
				}
			}
		});
	}

	public static void open(Player player) {
		// coming from the shop screen: drop its sell-side inventory panel
		player.closeInterface(ToplevelComponent.SIDEMODAL);
		player.openInterface(ToplevelComponent.MAINMODAL, INTERFACE);
		draw(player);
	}

	/* ---------------- loading ---------------- */

	private static File configFile() {
		return new File(ServerWrapper.dataFolder, "cfg/donator_deals.json");
	}

	private static void reloadIfChanged() {
		File file = configFile();
		long modified = file.exists() ? file.lastModified() : 0;
		if (modified != loadedModified)
			reload(null);
	}

	/** Re-reads cfg/donator_deals.json. Bad deals are skipped (and reported to the admin who asked, if any). */
	public static void reload(Player admin) {
		File file = configFile();
		loadedModified = file.exists() ? file.lastModified() : 0;
		List<Deal> loaded = new ArrayList<>();
		List<String> problems = new ArrayList<>();
		if (file.exists()) {
			try (FileReader reader = new FileReader(file)) {
				Config config = JsonUtils.GSON.fromJson(reader, Config.class);
				if (config != null && config.deals != null) {
					Set<String> seen = new HashSet<>();
					for (Deal d : config.deals) {
						String problem = validate(d, seen);
						if (problem != null)
							problems.add(problem);
						else
							loaded.add(d);
					}
				}
			} catch (Exception ex) {
				problems.add("could not read the file: " + ex.getMessage());
				ServerWrapper.logError("Unable to load cfg/donator_deals.json", ex);
			}
		}
		boolean stateChanged = false;
		for (Deal d : loaded) {
			if (d.startTime == null || d.startTime.isBlank()) {
				Long first = state.firstSeen.get(d.dealId);
				if (first == null) {
					first = System.currentTimeMillis();
					state.firstSeen.put(d.dealId, first);
					stateChanged = true;
				}
				d.startMillis = first;
			} else {
				d.startMillis = parseTime(d.startTime);
			}
			d.endMillis = d.startMillis + d.durationMinutes * 60_000L;
		}
		if (stateChanged)
			saveState();
		deals = loaded;
		for (String p : problems)
			System.err.println("[DonatorDeals] skipped: " + p);
		if (admin != null) {
			admin.sendMessage(TAG + "Reloaded " + loaded.size() + " deal(s), " + active().size() + " active now"
				+ (problems.isEmpty() ? "." : ", " + problems.size() + " skipped:"));
			for (String p : problems)
				admin.sendMessage(TAG + "<col=7f0000>" + p + "</col>");
		}
	}

	private static String validate(Deal d, Set<String> seen) {
		if (d == null)
			return "an empty entry";
		String id = d.dealId == null ? "(no dealId)" : d.dealId;
		if (d.dealId == null || d.dealId.isBlank())
			return "a deal has no dealId";
		if (!seen.add(d.dealId))
			return id + ": duplicate dealId";
		if (d.title == null || d.title.isBlank())
			return id + ": no title";
		if (d.itemIds == null || d.itemIds.length == 0 || d.itemIds.length > MAX_ITEMS)
			return id + ": needs 1-" + MAX_ITEMS + " itemIds";
		if (d.amounts == null || d.amounts.length != d.itemIds.length)
			return id + ": amounts must match itemIds";
		for (int i = 0; i < d.itemIds.length; i++) {
			ObjType def = ObjType.get(d.itemIds[i]);
			if (def == null || def.name == null || def.name.equalsIgnoreCase("null"))
				return id + ": item " + d.itemIds[i] + " does not exist";
			if (d.amounts[i] < 1)
				return id + ": amount for item " + d.itemIds[i] + " must be at least 1";
		}
		if (d.discountedCost < 1 || d.originalCost < d.discountedCost)
			return id + ": needs discountedCost >= 1 and originalCost >= discountedCost";
		if (d.durationMinutes < 1)
			return id + ": durationMinutes must be at least 1";
		if (d.startTime != null && !d.startTime.isBlank()) {
			try {
				parseTime(d.startTime);
			} catch (Exception ex) {
				return id + ": bad startTime \"" + d.startTime + "\" (use 2026-10-15T18:00:00Z or epoch millis)";
			}
		}
		return null;
	}

	private static long parseTime(String s) {
		s = s.trim();
		if (s.chars().allMatch(Character::isDigit))
			return Long.parseLong(s);
		return Instant.parse(s).toEpochMilli();
	}

	/** Deals running right now, ending soonest first. */
	private static List<Deal> active() {
		long now = System.currentTimeMillis();
		List<Deal> list = new ArrayList<>();
		for (Deal d : deals)
			if (now >= d.startMillis && now < d.endMillis)
				list.add(d);
		list.sort(Comparator.comparingLong(d -> d.endMillis));
		return list.size() > CARDS ? list.subList(0, CARDS) : list;
	}

	private static List<String> ids(List<Deal> list) {
		List<String> ids = new ArrayList<>(list.size());
		for (Deal d : list)
			ids.add(d.dealId);
		return ids;
	}

	/* ---------------- drawing ---------------- */

	private static View view(Player player) {
		return views.computeIfAbsent(player.uuid(), k -> new View());
	}

	private static void draw(Player player) {
		PacketSender ps = player.getPacketSender();
		View v = view(player);
		List<Deal> active = active();
		v.drawn = ids(active);
		ps.sendString(INTERFACE, TITLE, "Zelus Donator Store");
		// tabs: Deals is the active (recessed) one
		ps.setHidden(INTERFACE, SHOP_TAB + 1, true);
		ps.setHidden(INTERFACE, SHOP_TAB + 2, true);
		ps.setHidden(INTERFACE, DEALS_TAB + 1, false);
		ps.setHidden(INTERFACE, DEALS_TAB + 2, false);
		ps.sendString(INTERFACE, SHOP_TAB + 3, "Shop");
		ps.sendString(INTERFACE, DEALS_TAB + 3, "<col=ffc35a>Deals</col>");
		ps.sendString(INTERFACE, BALANCE, "Balance: " + WHITE + fmt(player.getDonatorPoints()) + END + " donator pts");

		for (int c = 0; c < CARDS; c++) {
			int base = FIRST_CARD + c * CARD_STRIDE;
			boolean used = c < active.size();
			ps.setHidden(INTERFACE, base, !used);
			if (!used)
				continue;
			Deal d = active.get(c);
			ps.sendString(INTERFACE, base + CARD_TITLE, d.title);
			ps.sendString(INTERFACE, base + CARD_BADGE_TEXT, d.percent() + "% DISCOUNT");
			int count = d.itemIds.length;
			int rows = count <= 3 ? 1 : 2;
			for (int s = 0; s < MAX_ITEMS; s++) {
				int comp = base + CARD_FIRST_ITEM + s;
				boolean has = s < count;
				ps.setHidden(INTERFACE, comp, !has);
				if (!has)
					continue;
				// centre each row in the tray: row 0 holds up to 3, row 1 the rest
				int row = s < 3 ? 0 : 1;
				int inRow = row == 0 ? Math.min(count, 3) : count - 3;
				int col = row == 0 ? s : s - 3;
				int rowWidth = (inRow - 1) * ITEM_PITCH_X + ITEM_W;
				int blockHeight = (rows - 1) * ITEM_PITCH_Y + ITEM_H;
				int x = TRAY_X + (TRAY_W - rowWidth) / 2 + col * ITEM_PITCH_X;
				int y = TRAY_Y + (TRAY_H - blockHeight) / 2 + row * ITEM_PITCH_Y;
				ps.setAlignment(INTERFACE, comp, x, y);
				ps.sendItem(INTERFACE, comp, d.itemIds[s], d.amounts[s]);
			}
			ps.sendString(INTERFACE, base + CARD_OLD_PRICE, d.originalCost > d.discountedCost
				? "<str>" + fmt(d.originalCost) + " pts</str>" : "");
			ps.sendString(INTERFACE, base + CARD_PRICE, fmt(d.discountedCost) + " pts");
			ps.sendItem(INTERFACE, base + CARD_HOURGLASS, HOURGLASS_ITEM, 1);
			boolean claimed = d.limit() > 0 && bought(player, d) >= d.limit();
			ps.setHidden(INTERFACE, base + CARD_BUY_SHADE, !claimed);
			ps.sendString(INTERFACE, base + CARD_BUY_LABEL, buyLabel(player, v, d));
		}
		ps.sendString(INTERFACE, NO_DEALS, active.isEmpty()
			? "There are no flash deals running right now.<br>Check back soon, or browse the Shop tab." : "");
		sendTimers(player, active);
		sendProgress(player);
	}

	private static String buyLabel(Player player, View v, Deal d) {
		int limit = d.limit();
		if (limit > 0 && bought(player, d) >= limit)
			return "<col=8f8f8f>Claimed</col>";
		if (d.dealId.equals(v.confirmDeal) && io.ruin.Server.currentTick() <= v.confirmUntilTick)
			return "<col=ffff00>Confirm?</col>";
		return "<col=ff981f>Purchase</col>";
	}

	private static void sendTimers(Player player, List<Deal> active) {
		PacketSender ps = player.getPacketSender();
		long now = System.currentTimeMillis();
		for (int c = 0; c < active.size() && c < CARDS; c++) {
			Deal d = active.get(c);
			long seconds = Math.max(0, (d.endMillis - now) / 1000);
			// "Time remaining:" is a fixed label above this line (1166 round 3); keep this short so it never wraps
			String left;
			if (seconds >= 3600)
				left = (seconds / 3600) + "h " + String.format("%02d", seconds / 60 % 60) + "m";
			else
				left = (seconds / 60) + "m " + String.format("%02d", seconds % 60) + "s";
			ps.sendString(INTERFACE, FIRST_CARD + c * CARD_STRIDE + CARD_TIME,
				(seconds < 300 ? "<col=ff5c4d>" : WHITE) + left + END);
		}
	}

	private static void sendProgress(Player player) {
		PacketSender ps = player.getPacketSender();
		int total = player.getTotalDonated();
		int next = -1;
		for (int r = 0; r < RANK_DOLLARS.length; r++) {
			if (total < RANK_DOLLARS[r]) {
				next = r;
				break;
			}
		}
		// green fill while climbing, gold fill at the top rank; the status text sits inside the bar
		boolean max = next < 0;
		int width;
		if (max) {
			ps.sendString(INTERFACE, BAR_TEXT, "<col=ffd36a>Supreme Donator</col><col=e0d0b0> (Max Tier)</col>");
			width = BAR_WIDTH;
		} else {
			int floor = next == 0 ? 0 : RANK_DOLLARS[next - 1];
			int ceil = RANK_DOLLARS[next];
			double progress = Math.max(0, total - floor) / (double) (ceil - floor);
			ps.sendString(INTERFACE, BAR_TEXT, WHITE + "$" + fmt(ceil - total) + END + " more to "
				+ "<col=ffd36a>" + RANK_NAMES[next] + " Donator" + END + "<col=c0c0c0>  (" + (int) (progress * 100) + "%)" + END);
			width = (int) Math.round(BAR_WIDTH * progress);
		}
		width = Math.max(1, Math.min(BAR_WIDTH, width));
		int shown = max ? BAR_GOLD : BAR_GREEN, other = max ? BAR_GREEN : BAR_GOLD;
		for (int part = 0; part < 2; part++) {
			ps.setHidden(INTERFACE, other + part, true);
			ps.setHidden(INTERFACE, shown + part, false);
			ps.sendClientScript(10606, "Ii", INTERFACE << 16 | (shown + part), width);
		}
	}

	private static void examine(Player player, int card, int slot) {
		List<Deal> active = active();
		if (card >= active.size() || slot >= active.get(card).itemIds.length)
			return;
		Deal d = active.get(card);
		ObjType def = ObjType.get(d.itemIds[slot]);
		if (def != null)
			player.sendMessage(TAG + d.amounts[slot] + " x " + def.name + ": " + def.examineText());
	}

	/* ---------------- purchasing ---------------- */

	/** First click arms the card ("Confirm?"), a second click within 6 seconds buys it. */
	private static void purchase(Player player, int card) {
		List<Deal> active = active();
		if (card >= active.size()) {
			draw(player);
			return;
		}
		Deal d = active.get(card);
		View v = view(player);
		int limit = d.limit();
		if (limit > 0 && bought(player, d) >= limit) {
			player.sendMessage(TAG + "You have already claimed this deal" + (limit > 1 ? " " + limit + " times." : "."));
			return;
		}
		if (!d.dealId.equals(v.confirmDeal) || io.ruin.Server.currentTick() > v.confirmUntilTick) {
			v.confirmDeal = d.dealId;
			v.confirmUntilTick = io.ruin.Server.currentTick() + CONFIRM_TICKS;
			player.sendMessage(TAG + "Click again to buy " + "<col=000080>" + d.title + END + " for "
				+ fmt(d.discountedCost) + " donator points.");
			draw(player);
			return;
		}
		v.confirmDeal = null;
		buy(player, d);
		draw(player);
	}

	private static void buy(Player player, Deal d) {
		if (System.currentTimeMillis() >= d.endMillis) {
			player.sendMessage(TAG + "<col=7f0000>That deal has just ended.</col>");
			return;
		}
		if (player.getDonatorPoints() < d.discountedCost) {
			player.sendMessage(TAG + "<col=7f0000>You need " + fmt(d.discountedCost) + " donator points (you have "
				+ fmt(player.getDonatorPoints()) + ").</col>");
			return;
		}
		boolean toInventory = player.getInventory().getFreeSlots() >= slotsNeeded(player, d);
		if (!toInventory) {
			if (player.getGameMode().isUltimateIronman()) {
				player.sendMessage(TAG + "<col=7f0000>Free up " + slotsNeeded(player, d) + " inventory slots first.</col>");
				return;
			}
			if (player.getBank().getFreeSlots() < d.itemIds.length) {
				player.sendMessage(TAG + "<col=7f0000>Your inventory and bank are too full for this deal.</col>");
				return;
			}
		}
		player.updateDonatorPoints(-d.discountedCost);
		for (int i = 0; i < d.itemIds.length; i++) {
			if (toInventory)
				player.getInventory().add(d.itemIds[i], d.amounts[i]);
			else
				player.getBank().add(d.itemIds[i], d.amounts[i]);
		}
		state.purchases.computeIfAbsent(d.dealId, k -> new HashMap<>()).merge(player.uuid(), 1, Integer::sum);
		saveState();
		Loggers.logBond(player.getUserId(), player.getName(), player.getIp(), "Donator deal '" + d.dealId + "' bought for "
			+ d.discountedCost + " donator points (" + player.getDonatorPoints() + " left)");
		player.sendMessage(TAG + "<col=006600>You bought " + d.title + "!" + (toInventory ? "" : " It was sent to your bank.")
			+ "</col>");
		io.ruin.model.content.cosmetic.Stardust.celebrate(player);
	}

	private static int slotsNeeded(Player player, Deal d) {
		int slots = 0;
		for (int i = 0; i < d.itemIds.length; i++) {
			ObjType def = ObjType.get(d.itemIds[i]);
			if (def != null && def.stackable)
				slots += player.getInventory().hasId(d.itemIds[i]) ? 0 : 1;
			else
				slots += d.amounts[i];
		}
		return slots;
	}

	private static int bought(Player player, Deal d) {
		Map<String, Integer> byPlayer = state.purchases.get(d.dealId);
		return byPlayer == null ? 0 : byPlayer.getOrDefault(player.uuid(), 0);
	}

	/* ---------------- storage ---------------- */

	private static File stateFile() {
		return new File(ServerWrapper.dataFolder, "runtime/saves/donator_deals.json");
	}

	private static void loadState() {
		File file = stateFile();
		if (!file.exists())
			return;
		try (FileReader reader = new FileReader(file)) {
			SaveState loaded = JsonUtils.GSON.fromJson(reader, SaveState.class);
			if (loaded != null) {
				if (loaded.purchases == null) loaded.purchases = new HashMap<>();
				if (loaded.firstSeen == null) loaded.firstSeen = new HashMap<>();
				state = loaded;
			}
		} catch (Exception e) {
			ServerWrapper.logError("Unable to load the donator deal purchases", e);
		}
	}

	private static void saveState() {
		File file = stateFile();
		file.getParentFile().mkdirs();
		try (FileWriter writer = new FileWriter(file)) {
			JsonUtils.GSON_PRETTY.toJson(state, writer);
		} catch (Exception e) {
			ServerWrapper.logError("Unable to save the donator deal purchases", e);
		}
	}

	private static String fmt(long value) {
		return String.format(Locale.ROOT, "%,d", value);
	}
}
