package io.ruin.model.activities.raids.tfa;

import io.ruin.cache.ObjType;
import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.ToplevelComponent;
import io.ruin.model.inter.dialogue.MessageDialogue;
import io.ruin.model.item.Item;
import io.ruin.model.var.VarPlayerRepository;

import java.util.HashMap;
import java.util.Map;

/**
 * The Quartermaster (TFA_MIDRAID_SUPPLIES_NPC 16761), who reaches the atrium once the third boss falls. As live, his
 * shop is the rev 241 omnishop (interfaces omnishop_main 819 / omnishop_side 806) driven by the cache's own rows:
 * shop TFA_MIDRAID_SUPPLIES_SHOP (dbrow 17150, "Alliance Supplies", inv 1030), currency
 * OMNISHOP_CURRENCY_TFA_SUPPLY_POINTS (dbrow 17149, read by the client from varbit tfa_supply_points 16381), and its
 * stock rows with their recorded prices. The client sends a buy as an IF_SCRIPT_TRIGGER on omnishop_main:trigger_buy
 * (dbrow, stock index, quantity code), the code mapped through enum_5844 (1->1, 5->2, 6->3, 2->5).
 */
public final class Raid4Quartermaster {

	private Raid4Quartermaster() {
	}

	public static final int NPC_QUARTERMASTER = 16761;
	public static final int LOCAL_X = 33, LOCAL_Y = 41; // RSProx nexus_plan 4: (4129,5609) facing south, the atrium after stage 3
	/** Supply points each member gets per raid; est. (not on the wire). */
	public static final int POINTS = 30;

	public static final int OMNISHOP_MAIN = 819, OMNISHOP_SIDE = 806;
	public static final int TRIGGER_EXAMINE = 3, TRIGGER_BUY = 4;
	static final int SHOP_ROW = 17150, SHOP_INV = 1030;
	static final int SCRIPT_MAIN_INIT = 7140, SCRIPT_SIDE_INIT = 7246;
	static final VarPlayerRepository SUPPLY_POINTS = VarPlayerRepository.varpbit(16381, false);

	/** The shop row's stock, in its order (col 5): {item, cost}; the Alliance log (17152) is view-only. */
	private static final int[][] STOCK = {
			{ 34729, -1 }, // Alliance log (view only)
			{ 6685, 3 }, // Saradomin brew(4)
			{ 3024, 3 }, // super restore(4)
			{ 2434, 2 }, // prayer potion(4)
			{ 391, 2 }, // manta ray
			{ 32336, 2 }, // halibut
			{ 32312, 1 }, // giant krill
			{ 141, 1 }, // prayer potion(2)
			{ 171, 2 }, // ranging potion(2)
			{ 12699, 2 }, // super combat potion(2)
	};
	/** enum_5844: the buy buttons' quantity codes. */
	private static final Map<Integer, Integer> QUANTITY = Map.of(0, 0, 1, 1, 5, 2, 6, 3, 2, 5, 3, 10, 4, 50);

	private static final Map<Raid4Controller, Map<String, Integer>> SPENT = new HashMap<>();

	/** The points this raider has left in their raid (0 outside one). */
	public static int pointsLeft(Player player) {
		Raid4Controller raid = Raid4.controllerOf(player);
		return raid == null ? 0 : pointsLeft(raid, player);
	}

	public static int pointsLeft(Raid4Controller raid, Player player) {
		return POINTS - SPENT.computeIfAbsent(raid, r -> new HashMap<>()).getOrDefault(Raid4Party.key(player), 0);
	}

	/** Takes {@code amount} supply points; false (nothing taken) if the raider hasn't got them. */
	public static boolean spend(Player player, int amount) {
		Raid4Controller raid = Raid4.controllerOf(player);
		if (raid == null || amount < 0 || pointsLeft(raid, player) < amount)
			return false;
		SPENT.computeIfAbsent(raid, r -> new HashMap<>()).merge(Raid4Party.key(player), amount, Integer::sum);
		return true;
	}

	/** Resupply / Talk-to in the atrium: the omnishop. */
	public static void open(Player player, int page) {
		Raid4Controller raid = Raid4.controllerOf(player);
		if (raid == null || !raid.isInside(player)) {
			player.dialogue(new MessageDialogue("The Quartermaster only supplies parties inside the Archive."));
			return;
		}
		SUPPLY_POINTS.set(player, pointsLeft(raid, player));
		Item[] stock = new Item[STOCK.length];
		for (int i = 0; i < STOCK.length; i++)
			stock[i] = new Item(STOCK[i][0], STOCK[i][1] < 0 ? 1 : 100);
		player.getPacketSender().sendItems(SHOP_INV, stock);
		player.openInterface(ToplevelComponent.MAINMODAL, OMNISHOP_MAIN);
		player.openInterface(ToplevelComponent.SIDEMODAL, OMNISHOP_SIDE);
		player.getPacketSender().sendClientScript(SCRIPT_MAIN_INIT, SHOP_ROW, SHOP_INV, SHOP_ROW);
		player.getPacketSender().sendClientScript(SCRIPT_SIDE_INIT, SHOP_ROW);
		// the scripts' triggers (examine, buy, request info) and the side panel's items
		player.getPacketSender().sendIfEvents(OMNISHOP_MAIN, TRIGGER_EXAMINE, -1, -1, 0x7FFFFFFE);
		player.getPacketSender().sendIfEvents(OMNISHOP_MAIN, TRIGGER_BUY, -1, -1, 0x7FFFFFFE);
		player.getPacketSender().sendIfEvents(OMNISHOP_MAIN, 5, -1, -1, 0x7FFFFFFE);
		player.getPacketSender().sendIfEvents(OMNISHOP_SIDE, 0, 0, 27, 0x7FE);
	}

	/** omnishop_main:trigger_buy (dbrow, stock index, quantity code). */
	public static void buy(Player player, int row, int index, int code) {
		if (row != SHOP_ROW || !player.isVisibleInterface(OMNISHOP_MAIN) || index < 0 || index >= STOCK.length)
			return;
		int[] s = STOCK[index];
		if (s[1] < 0)
			return;
		int quantity = QUANTITY.getOrDefault(code, 1);
		int bought = 0;
		for (int i = 0; i < quantity; i++) {
			if (!player.getInventory().hasRoomFor(s[0])) {
				player.sendMessage("You don't have enough inventory space.");
				break;
			}
			if (!spend(player, s[1])) {
				player.sendMessage("You don't have enough supply points.");
				break;
			}
			player.getInventory().add(s[0], 1);
			bought++;
		}
		SUPPLY_POINTS.set(player, pointsLeft(player));
		if (bought == 0)
			return;
	}

	/** omnishop_main:trigger_examine (obj). */
	public static void examine(Player player, int obj) {
		ObjType def = ObjType.get(obj);
		if (def != null)
			player.sendMessage(def.examine == null ? def.name : def.examine);
	}

	/** Forget a finished raid's spending. */
	public static void clear(Raid4Controller raid) {
		SPENT.remove(raid);
	}
}
