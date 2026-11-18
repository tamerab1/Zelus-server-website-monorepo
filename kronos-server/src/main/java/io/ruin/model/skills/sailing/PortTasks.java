package io.ruin.model.skills.sailing;

import com.google.gson.reflect.TypeToken;
import io.ruin.api.utils.ServerWrapper;
import io.ruin.api.utils.JsonUtils;
import io.ruin.api.utils.Random;
import io.ruin.cache.ObjType;
import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.ClientInterfaceType;
import io.ruin.model.inter.InterfaceHandler;
import io.ruin.model.inter.ToplevelComponent;
import io.ruin.model.inter.actions.DefaultAction;
import io.ruin.model.inter.actions.SimpleAction;
import io.ruin.model.item.Item;
import io.ruin.model.item.actions.ItemAction;
import io.ruin.model.map.object.actions.ObjectAction;
import io.ruin.model.stat.StatType;
import io.ruin.model.var.VarPlayerRepository;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileReader;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

/**
 * Sailing port tasks (courier deliveries + bounties), OSRS 1:1 where the data exists:
 * <ul>
 * <li>Tasks: cache db table 197 port_task (606 rows: name, type, level, board / cargo / end port, cargo crate + qty,
 * bounty npc / item / amount / rarity, notice text) joined with the wiki's XP tables into data/sailing/port_tasks.json
 * by tools/sailing/build_port_tasks.py.</li>
 * <li>The notice board (loc "Inspect") opens port_task_board 941 and the client draws it with clientscript 8912(8 task
 * rows): layout 0 = 4 + 4 notices. Each notice is 6 dynamic children of 941:3 (script 8917, board path) and only the
 * first has the "Select" op, so a click on child k is notice k / 6. Selecting opens port_task_info 942 in 941:5 and
 * clientscript 8901(941:5, row) draws it: "Accept task" (or "Cancel task" when held) on 942:10, "Back" on 942:8.</li>
 * <li>Held tasks: varbits port_task_slot_N_id / _cargo_taken / _cargo_delivered (19574 + 3N), the task id (column 0;
 * script 9097 finds the row by id). Slots by Sailing level 1/7/28/56/84 -> 1..5 (wiki).</li>
 * <li>Cargo: one crate carried at a time (varbit 19134 sailing_carrying_cargo flips the ledger table multiloc between
 * Take-cargo 60320 and Deposit-cargo 60321, and the cargo hold between Open 60577 and Deposit-held 60581). The raft's
 * regular cargo hold holds 20 (sailing_boat_facility_stats row 8239).</li>
 * <li>Rewards (wiki): the task's Sailing XP; courier tasks also a port coin bag (4/5) or the destination's port reward
 * bag (1/5) sized by base XP (tiny 0-399, small -999, medium -2,499, large -5,999, huge 6,000+); 1/36 shark paint.
 * Boards refresh at 00:00 UTC and after every 8 completed tasks.</li>
 * </ul>
 * Not yet: bounty kills (needs sea combat), the cargo hold interface (943), crewmates, the captain's log.
 */
@Slf4j
public class PortTasks {

	public static final int BOARD = 941, INFO = 942;
	private static final int BOARD_CONTAINER = 3, BOARD_CLOSE = 4, BOARD_POPUP = 5, INFO_BACK = 8, INFO_SELECT = 10;
	private static final int BOARD_SCRIPT = 8912, INFO_SCRIPT = 8901, NOTICE_CHILDREN = 6, NOTICES = 8;

	private static final int SLOT_VARBIT = 19574, MAX_SLOTS = 5;
	private static final VarPlayerRepository[] SLOT_ID = new VarPlayerRepository[MAX_SLOTS], TAKEN = new VarPlayerRepository[MAX_SLOTS],
		DELIVERED = new VarPlayerRepository[MAX_SLOTS];
	private static final VarPlayerRepository CARRYING = VarPlayerRepository.varpbit(19134, false);
	private static final VarPlayerRepository COMPLETED_TODAY = VarPlayerRepository.varpbit(19591, true);
	private static final VarPlayerRepository COMPLETED_TOTAL = VarPlayerRepository.varp(5207, true);

	static {
		for (int i = 0; i < MAX_SLOTS; i++) {
			SLOT_ID[i] = VarPlayerRepository.varpbit(SLOT_VARBIT + i * 3, true);
			TAKEN[i] = VarPlayerRepository.varpbit(SLOT_VARBIT + i * 3 + 1, true);
			DELIVERED[i] = VarPlayerRepository.varpbit(SLOT_VARBIT + i * 3 + 2, true);
		}
	}

	public static final int CARGO_HOLD_RAFT = 60245, CARGO_HOLD_SIZE_RAFT = 20;
	private static final int COIN_BAG = 34377, SHARK_PAINT = 32090, COINS = 995;

	/* ------------------------------------------------------------------ data */

	public static final class Task {
		int row, id, type, level, board, cargoPort, endPort, cargoItem, cargoQty, npcAlive, npcDead, item, amount, rarity;
		Integer xp;
		String name, flavour;

		boolean courier() {
			return type == 0;
		}
	}

	private static final Map<Integer, Task> BY_ID = new HashMap<>();
	private static final Map<Integer, List<Task>> BY_BOARD = new HashMap<>();
	private static final Set<Integer> CRATES = new HashSet<>();

	private static void load() {
		File file = new File(ServerWrapper.dataFolder, "sailing/port_tasks.json");
		try (FileReader reader = new FileReader(file)) {
			List<Task> tasks = JsonUtils.GSON.fromJson(reader, new TypeToken<List<Task>>() {}.getType());
			for (Task t : tasks) {
				if (t.xp == null || t.board < 0 || t.board >= Port.VALUES.length)
					continue;
				BY_ID.put(t.id, t);
				BY_BOARD.computeIfAbsent(t.board, k -> new ArrayList<>()).add(t);
				if (t.courier() && t.cargoItem > 0)
					CRATES.add(t.cargoItem);
			}
			log.info("Loaded {} port tasks", BY_ID.size());
		} catch (Exception e) {
			log.error("Unable to load sailing/port_tasks.json", e);
		}
	}

	/* ------------------------------------------------------------------ registration */

	public static void register() {
		load();
		for (Port port : Port.VALUES) {
			if (port.board != -1)
				ObjectAction.register(port.board, 1, (p, obj) -> openBoard(p, port));
			if (port.ledger != -1)
				for (int op = 1; op <= 3; op++)
					ObjectAction.register(port.ledger, op, (p, obj) -> ledger(p, port));
		}
		ObjectAction.register(CARGO_HOLD_RAFT, 1, (p, obj) -> holdPrimary(p));
		ObjectAction.register(CARGO_HOLD_RAFT, 2, (p, obj) -> holdDepositAll(p));
		ObjectAction.register(CARGO_HOLD_RAFT, 5, (p, obj) -> Sailing.modifyFacility(p));
		InterfaceHandler.register(BOARD, h -> {
			h.actions[BOARD_CLOSE] = (SimpleAction) Player::closeInterfaces;
			h.actions[BOARD_CONTAINER] = (DefaultAction) (p, option, slot, itemId) -> selectNotice(p, slot / NOTICE_CHILDREN);
		});
		InterfaceHandler.register(INFO, h -> {
			h.actions[INFO_BACK] = (SimpleAction) p -> p.getPacketSender().removeSubInterface(BOARD, BOARD_POPUP);
			h.actions[INFO_SELECT] = (SimpleAction) PortTasks::acceptOrCancel;
		});
		for (int t = 0; t < 5; t++) {
			int tier = t;
			ItemAction.registerInventory(COIN_BAG + 2 * t, "Open", (p, item) -> openCoinBag(p, item, tier));
		}
		for (int base : LOOTSACK_BASE) {
			if (base <= 0)
				continue;
			for (int t = 0; t < 5; t++) {
				int tier = t;
				ItemAction.registerInventory(base + 2 * t, "Open", (p, item) -> openRewardBag(p, item, tier));
			}
		}
	}

	/* ------------------------------------------------------------------ slots */

	public static int slots(Player p) {
		int lvl = p.getStats().get(StatType.Sailing).fixedLevel;
		return 1 + (lvl >= 7 ? 1 : 0) + (lvl >= 28 ? 1 : 0) + (lvl >= 56 ? 1 : 0) + (lvl >= 84 ? 1 : 0);
	}

	private static Task held(Player p, int slot) {
		int id = SLOT_ID[slot].get(p);
		return id == 0 ? null : BY_ID.get(id);
	}

	private static int slotOf(Player p, Task t) {
		for (int i = 0; i < MAX_SLOTS; i++)
			if (SLOT_ID[i].get(p) == t.id)
				return i;
		return -1;
	}

	private static void clearSlot(Player p, int slot) {
		SLOT_ID[slot].set(p, 0);
		TAKEN[slot].set(p, 0);
		DELIVERED[slot].set(p, 0);
	}

	/* ------------------------------------------------------------------ the board */

	private static void refreshGeneration(Player p) {
		long today = LocalDate.now(ZoneOffset.UTC).toEpochDay();
		if (p.sailingBoardDay != today) {
			p.sailingBoardDay = today;
			p.sailingBoardGen++;
			p.sailingBoardDone.clear();
			p.sailingTasksSinceRefresh = 0;
			COMPLETED_TODAY.set(p, 0);
		}
	}

	/** this player's notices on a port's board: up to 7 courier tasks and 1 bounty, seeded per player / port / board generation */
	static List<Task> notices(Player p, Port port) {
		refreshGeneration(p);
		int lvl = p.getStats().get(StatType.Sailing).fixedLevel;
		java.util.Random rnd = new java.util.Random(Objects.hash(p.getUserId(), port.ordinal(), p.sailingBoardGen));
		List<Task> pool = BY_BOARD.getOrDefault(port.ordinal(), List.of());
		List<Task> couriers = new ArrayList<>(), locked = new ArrayList<>(), bounties = new ArrayList<>();
		for (Task t : pool) {
			if (p.sailingBoardDone.contains(t.id) && slotOf(p, t) == -1)
				continue;
			if (!t.courier())
				bounties.add(t);
			else if (t.level <= lvl)
				couriers.add(t);
			else
				locked.add(t);
		}
		Collections.shuffle(couriers, rnd);
		locked.sort(Comparator.comparingInt(t -> t.level));
		List<Task> out = new ArrayList<>(couriers.subList(0, Math.min(NOTICES - 1, couriers.size())));
		for (Task t : locked) {
			if (out.size() >= NOTICES - 1)
				break;
			out.add(t);
		}
		// held tasks from this board always stay on it (so they can be cancelled here)
		for (int s = 0; s < MAX_SLOTS; s++) {
			Task t = held(p, s);
			if (t != null && t.board == port.ordinal() && !out.contains(t) && t.courier()) {
				if (out.size() >= NOTICES - 1)
					out.remove(out.size() - 1);
				out.add(0, t);
			}
		}
		out.sort(Comparator.comparingInt(t -> t.level));
		if (!bounties.isEmpty()) {
			List<Task> open = new ArrayList<>();
			for (Task b : bounties)
				if (b.level <= lvl)
					open.add(b);
			Task b = open.isEmpty() ? bounties.stream().min(Comparator.comparingInt(t -> t.level)).get() : open.get(rnd.nextInt(open.size()));
			out.add(b);
		}
		return out;
	}

	public static void openBoard(Player p, Port port) {
		List<Task> shown = notices(p, port);
		p.set("PT_SHOWN", shown);
		p.openInterface(ToplevelComponent.MAINMODAL, BOARD);
		p.getPacketSender().sendIfEvents(BOARD, BOARD_CONTAINER, 0, NOTICES * NOTICE_CHILDREN - 1, 2);
		Object[] rows = new Object[NOTICES];
		for (int i = 0; i < NOTICES; i++)
			rows[i] = i < shown.size() ? shown.get(i).row : -1;
		p.getPacketSender().sendClientScript(BOARD_SCRIPT, rows);
	}

	private static void selectNotice(Player p, int index) {
		List<Task> shown = p.get("PT_SHOWN");
		if (shown == null || index < 0 || index >= shown.size())
			return;
		Task t = shown.get(index);
		p.set("PT_SELECTED", t);
		p.getPacketSender().openSubInterface(INFO, BOARD, BOARD_POPUP, ClientInterfaceType.MODAL);
		p.getPacketSender().sendIfEvents(INFO, INFO_SELECT, -1, -1, 2);
		p.getPacketSender().sendIfEvents(INFO, INFO_BACK, -1, -1, 2);
		p.getPacketSender().sendClientScript(INFO_SCRIPT, BOARD << 16 | BOARD_POPUP, t.row);
	}

	private static void acceptOrCancel(Player p) {
		Task t = p.get("PT_SELECTED");
		if (t == null)
			return;
		int slot = slotOf(p, t);
		if (slot != -1) {
			cancel(p, slot);
		} else {
			accept(p, t);
		}
		p.getPacketSender().removeSubInterface(BOARD, BOARD_POPUP);
	}

	private static void accept(Player p, Task t) {
		if (p.getStats().get(StatType.Sailing).fixedLevel < t.level) {
			p.sendMessage("You need a Sailing level of " + t.level + " to take on this task.");
			return;
		}
		if (!t.courier()) {
			p.sendMessage("Bounty tasks can't be taken yet: the sea creatures they hunt aren't on Karn's seas yet.");
			return;
		}
		int max = slots(p), free = -1;
		for (int i = 0; i < max; i++)
			if (SLOT_ID[i].get(p) == 0) {
				free = i;
				break;
			}
		if (free == -1) {
			p.sendMessage("You can't take on any more port tasks right now.");
			return;
		}
		SLOT_ID[free].set(p, t.id);
		TAKEN[free].set(p, 0);
		DELIVERED[free].set(p, 0);
		p.sendMessage("You take on the port task: " + t.name + ".");
		Port cargo = port(t.cargoPort), end = port(t.endPort);
		if (cargo != null && end != null)
			p.sendMessage("Collect " + t.cargoQty + " crate" + (t.cargoQty == 1 ? "" : "s") + " of cargo at " + cargo.title
				+ " and deliver " + (t.cargoQty == 1 ? "it" : "them") + " to " + end.title + ".");
	}

	private static void cancel(Player p, int slot) {
		Task t = held(p, slot);
		clearSlot(p, slot);
		// the task's crates go back: carried ones and those in the hold
		if (t != null && t.cargoItem > 0) {
			int n = p.getInventory().count(t.cargoItem);
			if (n > 0)
				p.getInventory().remove(t.cargoItem, n);
			p.sailingCargoHold.removeIf(id -> id == t.cargoItem);
			updateCarrying(p);
		}
		p.sendMessage("You cancel the port task" + (t == null ? "." : ": " + t.name + "."));
	}

	private static Port port(int ordinal) {
		return ordinal >= 0 && ordinal < Port.VALUES.length ? Port.VALUES[ordinal] : null;
	}

	/* ------------------------------------------------------------------ cargo */

	private static Integer carriedCrate(Player p) {
		for (int id : CRATES)
			if (p.getInventory().contains(id, 1))
				return id;
		return null;
	}

	/** varbit 19134: the ledger tables and cargo hold switch to their deposit options while a crate is carried */
	public static void updateCarrying(Player p) {
		CARRYING.set(p, carriedCrate(p) != null ? 1 : 0);
	}

	private static void ledger(Player p, Port port) {
		Integer crate = carriedCrate(p);
		if (crate != null) {
			depositAtLedger(p, port, crate);
			return;
		}
		for (int s = 0; s < MAX_SLOTS; s++) {
			Task t = held(p, s);
			if (t == null || !t.courier() || t.cargoPort != port.ordinal() || TAKEN[s].get(p) >= t.cargoQty)
				continue;
			if (p.getInventory().isFull()) {
				p.sendMessage("You need a free inventory space to carry the cargo.");
				return;
			}
			p.animate(832);
			p.getInventory().add(t.cargoItem, 1);
			TAKEN[s].set(p, TAKEN[s].get(p) + 1);
			updateCarrying(p);
			int left = t.cargoQty - TAKEN[s].get(p);
			p.sendMessage("You collect a crate of cargo for your " + t.name + " task." + (left > 0 ? " (" + left + " more to collect)" : ""));
			return;
		}
		p.sendMessage("There's no cargo waiting here for any of your port tasks.");
	}

	private static void depositAtLedger(Player p, Port port, int crate) {
		for (int s = 0; s < MAX_SLOTS; s++) {
			Task t = held(p, s);
			if (t == null || t.cargoItem != crate)
				continue;
			if (t.endPort == port.ordinal()) {
				p.animate(832);
				p.getInventory().remove(crate, 1);
				DELIVERED[s].set(p, DELIVERED[s].get(p) + 1);
				updateCarrying(p);
				if (DELIVERED[s].get(p) >= t.cargoQty) {
					complete(p, s, t);
				} else {
					int left = t.cargoQty - DELIVERED[s].get(p);
					p.sendMessage("You deliver a crate of cargo. " + left + " more to go.");
				}
				return;
			}
			if (t.cargoPort == port.ordinal()) {
				p.animate(832);
				p.getInventory().remove(crate, 1);
				TAKEN[s].set(p, Math.max(0, TAKEN[s].get(p) - 1));
				updateCarrying(p);
				p.sendMessage("You return the crate to the ledger.");
				return;
			}
			Port end = port(t.endPort);
			p.sendMessage("This cargo needs to go to " + (end == null ? "another port" : end.title) + ".");
			return;
		}
		p.sendMessage("That cargo isn't for any of your port tasks.");
	}

	/** cargo hold op1: Deposit-held while carrying a crate, else Open (takes the most recent crate back out) */
	private static void holdPrimary(Player p) {
		Integer crate = carriedCrate(p);
		if (crate != null) {
			if (p.sailingCargoHold.size() >= CARGO_HOLD_SIZE_RAFT) {
				p.sendMessage("Your cargo hold is full.");
				return;
			}
			p.animate(832);
			p.getInventory().remove(crate, 1);
			p.sailingCargoHold.add(crate);
			updateCarrying(p);
			p.sendMessage("You stow the crate in your cargo hold. (" + p.sailingCargoHold.size() + "/" + CARGO_HOLD_SIZE_RAFT + ")");
			return;
		}
		if (p.sailingCargoHold.isEmpty()) {
			p.sendMessage("Your cargo hold is empty.");
			return;
		}
		if (p.getInventory().isFull()) {
			p.sendMessage("You need a free inventory space to carry the cargo.");
			return;
		}
		int id = p.sailingCargoHold.remove(p.sailingCargoHold.size() - 1);
		p.animate(832);
		p.getInventory().add(id, 1);
		updateCarrying(p);
		p.sendMessage("You take a crate out of your cargo hold. (" + p.sailingCargoHold.size() + "/" + CARGO_HOLD_SIZE_RAFT + ")");
	}

	private static void holdDepositAll(Player p) {
		int moved = 0;
		for (int id : CRATES) {
			int n = p.getInventory().count(id);
			for (int i = 0; i < n && p.sailingCargoHold.size() < CARGO_HOLD_SIZE_RAFT; i++) {
				p.getInventory().remove(id, 1);
				p.sailingCargoHold.add(id);
				moved++;
			}
		}
		updateCarrying(p);
		p.sendMessage(moved == 0 ? "You have no cargo to store." : "You stow " + moved + " crate" + (moved == 1 ? "" : "s")
			+ " in your cargo hold. (" + p.sailingCargoHold.size() + "/" + CARGO_HOLD_SIZE_RAFT + ")");
	}

	/** wiki: crates in the hold are lost if the player dies at sea, the boat capsizes or they teleport off it */
	public static void loseHoldCargo(Player p) {
		if (!p.sailingCargoHold.isEmpty()) {
			p.sailingCargoHold.clear();
			p.sendMessage("The cargo in your boat's hold has been lost.");
		}
	}

	/* ------------------------------------------------------------------ completion + rewards */

	private static void complete(Player p, int slot, Task t) {
		clearSlot(p, slot);
		int xp = t.xp == null ? 0 : t.xp;
		p.getStats().addXp(StatType.Sailing, xp, true);
		COMPLETED_TOTAL.set(p, COMPLETED_TOTAL.get(p) + 1);
		COMPLETED_TODAY.set(p, COMPLETED_TODAY.get(p) + 1);
		p.sendMessage("You have completed the port task: " + t.name + ".");
		if (t.courier()) {
			int tier = xp >= 6000 ? 4 : xp >= 2500 ? 3 : xp >= 1000 ? 2 : xp >= 400 ? 1 : 0;
			Port end = port(t.endPort);
			int base = end == null ? 0 : LOOTSACK_BASE[end.ordinal()];
			if (base > 0 && Random.get(1, 5) == 1)
				p.getInventory().addOrDrop(base + 2 * tier, 1);
			else
				p.getInventory().addOrDrop(COIN_BAG + 2 * tier, 1);
		}
		if (Random.get(1, 36) == 1) {
			p.getInventory().addOrDrop(SHARK_PAINT, 1);
			p.sendMessage("You find a pot of shark paint among the reward.");
		}
		// boards refresh after every 8 completed tasks (wiki: unconfirmed but widely reported)
		refreshGeneration(p);
		p.sailingBoardDone.add(t.id);
		if (++p.sailingTasksSinceRefresh >= 8) {
			p.sailingTasksSinceRefresh = 0;
			p.sailingBoardGen++;
			p.sailingBoardDone.clear();
			p.sendMessage("The port task notice boards have new tasks for you.");
		}
	}

	/** port_task_lootsack_t0_&lt;port&gt; per Port ordinal (gameval; tiers are +2 each, the noted form +1); 0 = none */
	private static final int[] LOOTSACK_BASE = new int[Port.VALUES.length];

	static {
		Object[][] map = { { Port.CAIRN_ISLE, 34077 }, { Port.ENTRANA, 34087 }, { Port.NEITIZNOT, 34097 }, { Port.JATIZSO, 34107 },
			{ Port.HOSIDIUS, 34117 }, { Port.SUNSET_COAST, 34127 }, { Port.PISCATORIS, 34137 }, { Port.ETCETERIA, 34147 },
			{ Port.RUINS_OF_UNKAH, 34157 }, { Port.LUNAR_ISLE, 34167 }, { Port.CORSAIR_COVE, 34177 }, { Port.PRIFDDINAS, 34187 },
			{ Port.LANDS_END, 34197 }, { Port.ALDARIN, 34207 }, { Port.THE_SUMMER_SHORE, 34217 }, { Port.MUSA_POINT, 34227 },
			{ Port.RELLEKKA, 34237 }, { Port.VOID_KNIGHTS_OUTPOST, 34247 }, { Port.THE_PANDEMONIUM, 34257 }, { Port.RED_ROCK, 34267 },
			{ Port.BRIMHAVEN, 34277 }, { Port.PORT_TYRAS, 34287 }, { Port.CATHERBY, 34297 }, { Port.CIVITAS_ILLA_FORTIS, 34307 },
			{ Port.ARDOUGNE, 34317 }, { Port.PORT_KHAZARD, 34327 }, { Port.DEEPFIN_POINT, 34337 }, { Port.PORT_SARIM, 34347 },
			{ Port.PORT_PISCARILIUS, 34357 }, { Port.PORT_ROBERTS, 34367 } };
		for (Object[] m : map)
			LOOTSACK_BASE[((Port) m[0]).ordinal()] = (Integer) m[1];
	}

	/** wiki Port coin bag: coins by size */
	private static final int[][] COIN_RANGE = { { 800, 1200 }, { 1600, 2400 }, { 2400, 3600 }, { 3600, 5400 }, { 6400, 9600 } };

	private static void openCoinBag(Player p, Item item, int tier) {
		item.remove(1);
		int coins = Random.get(COIN_RANGE[tier][0], COIN_RANGE[tier][1]);
		p.getInventory().addOrDrop(COINS, coins);
		p.sendMessage("You open the bag and find " + String.format("%,d", coins) + " coins.");
	}

	/**
	 * wiki Port reward bag tables per size: {item, min, max, weight, noted}. Weights are the wiki's n/divisor
	 * (medium's half weights doubled). The port's own signature item (1-3 of the divisor) isn't listed yet: rerolled.
	 */
	private static final int[][][] REWARD_TABLE = {
		{ // tiny /96
			{ 995, 5000, 12500, 6, 0 }, { 22818, 15, 25, 4, 0 }, { 436, 10, 20, 4, 1 }, { 438, 10, 20, 4, 1 }, { 440, 10, 20, 4, 1 },
			{ 453, 10, 20, 4, 1 }, { 31906, 40, 60, 4, 0 }, { 31908, 40, 60, 4, 0 }, { 2, 40, 60, 4, 0 }, { 31910, 40, 60, 4, 0 },
			{ 4819, 20, 30, 4, 0 }, { 4820, 20, 30, 4, 0 }, { 1539, 20, 30, 4, 0 }, { 4822, 20, 30, 4, 0 }, { 32083, 10, 20, 4, 0 },
			{ 32085, 5, 15, 4, 0 }, { 960, 10, 20, 4, 1 }, { 8778, 10, 20, 4, 1 }, { 8780, 10, 20, 3, 1 }, { 2349, 10, 20, 3, 1 },
			{ 2351, 10, 20, 3, 1 }, { 2353, 10, 20, 3, 1 }, { 31964, 5, 15, 3, 1 }, { 31967, 5, 15, 3, 1 }, { 31970, 5, 15, 3, 1 } },
		{ // small /168
			{ 995, 10000, 17500, 15, 0 }, { 22818, 40, 60, 8, 0 }, { 440, 10, 20, 8, 1 }, { 453, 10, 20, 8, 1 }, { 31908, 40, 60, 8, 0 },
			{ 2, 40, 60, 8, 0 }, { 4820, 20, 30, 8, 0 }, { 1539, 20, 30, 8, 0 }, { 32085, 10, 20, 8, 0 }, { 34068, 10, 20, 8, 0 },
			{ 8778, 10, 20, 8, 1 }, { 8780, 10, 20, 8, 1 }, { 447, 10, 20, 7, 1 }, { 31910, 40, 60, 7, 0 }, { 4822, 20, 30, 7, 0 },
			{ 2351, 10, 20, 6, 1 }, { 2353, 10, 20, 6, 1 }, { 2359, 10, 20, 6, 1 }, { 8782, 5, 15, 6, 1 }, { 31967, 5, 15, 6, 1 },
			{ 31970, 5, 15, 6, 1 }, { 31973, 5, 15, 6, 1 } },
		{ // medium /160 (weights x2)
			{ 995, 15000, 25000, 28, 0 }, { 453, 10, 20, 16, 1 }, { 447, 10, 20, 16, 1 }, { 2, 40, 60, 16, 0 }, { 31910, 40, 60, 16, 0 },
			{ 1539, 20, 30, 16, 0 }, { 4822, 20, 30, 16, 0 }, { 34068, 10, 20, 16, 0 }, { 34070, 10, 20, 16, 0 }, { 8778, 10, 20, 16, 1 },
			{ 8780, 10, 20, 16, 1 }, { 8782, 5, 15, 12, 1 }, { 31967, 5, 15, 12, 1 }, { 31970, 5, 15, 12, 1 }, { 31973, 5, 15, 12, 1 },
			{ 2353, 10, 20, 12, 1 }, { 2359, 10, 20, 12, 1 }, { 449, 10, 20, 11, 1 }, { 31912, 40, 60, 11, 0 }, { 4823, 20, 30, 11, 0 },
			{ 32307, 20, 30, 9, 0 }, { 22818, 40, 60, 7, 0 }, { 2361, 10, 20, 7, 1 } },
		{ // large /155
			{ 995, 22500, 32500, 15, 0 }, { 453, 10, 20, 8, 1 }, { 449, 10, 20, 8, 1 }, { 31910, 40, 60, 8, 0 }, { 31912, 40, 60, 8, 0 },
			{ 4822, 20, 30, 8, 0 }, { 4823, 20, 30, 8, 0 }, { 34070, 10, 20, 8, 0 }, { 34072, 10, 20, 8, 0 }, { 8780, 10, 20, 8, 1 },
			{ 8782, 10, 20, 8, 1 }, { 32307, 40, 60, 7, 0 }, { 31970, 5, 15, 6, 1 }, { 31973, 5, 15, 6, 1 }, { 2359, 10, 20, 6, 1 },
			{ 2361, 10, 20, 6, 1 }, { 31432, 5, 15, 5, 1 }, { 31976, 5, 15, 5, 1 }, { 31914, 40, 60, 5, 0 }, { 4824, 20, 30, 5, 0 },
			{ 451, 10, 20, 3, 1 }, { 2363, 10, 20, 2, 1 } },
		{ // huge /225
			{ 995, 40000, 60000, 22, 0 }, { 34072, 10, 20, 13, 0 }, { 34074, 10, 20, 13, 0 }, { 32307, 40, 60, 12, 0 }, { 31912, 40, 60, 12, 0 },
			{ 4823, 20, 30, 12, 0 }, { 8782, 10, 20, 12, 1 }, { 31432, 10, 20, 12, 1 }, { 31435, 5, 15, 9, 1 }, { 31973, 5, 15, 9, 1 },
			{ 31976, 5, 15, 9, 1 }, { 31979, 5, 15, 9, 1 }, { 453, 30, 50, 9, 1 }, { 449, 10, 20, 9, 1 }, { 2359, 10, 20, 9, 1 },
			{ 2361, 10, 20, 9, 1 }, { 31914, 40, 60, 9, 0 }, { 4824, 20, 30, 9, 0 }, { 451, 5, 10, 8, 1 }, { 31916, 40, 60, 6, 0 },
			{ 2363, 5, 10, 5, 1 }, { 31406, 10, 15, 4, 0 }, { 31996, 1, 2, 1, 1 } } };

	private static void openRewardBag(Player p, Item item, int tier) {
		int[][] table = REWARD_TABLE[tier];
		int total = 0;
		for (int[] r : table)
			total += r[3];
		int roll = Random.get(total - 1);
		int[] pick = table[0];
		for (int[] r : table) {
			roll -= r[3];
			if (roll < 0) {
				pick = r;
				break;
			}
		}
		item.remove(1);
		int id = pick[0];
		if (pick[4] == 1) {
			ObjType def = ObjType.get(id);
			if (def != null && def.notedId != -1)
				id = def.notedId;
		}
		int qty = Random.get(pick[1], pick[2]);
		p.getInventory().addOrDrop(id, qty);
		ObjType def = ObjType.get(pick[0]);
		p.sendMessage("You open the bag and find " + qty + " x " + (def == null ? "something" : def.name) + ".");
	}
}
