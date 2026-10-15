package io.ruin.model.activities.lottery;

import com.google.gson.annotations.Expose;
import io.ruin.api.utils.JsonUtils;
import io.ruin.api.utils.ServerWrapper;
import io.ruin.cache.Icon;
import io.ruin.model.World;
import io.ruin.model.entity.npc.NPC;
import io.ruin.model.entity.npc.NPCAction;
import io.ruin.model.entity.player.Player;
import io.ruin.model.entity.shared.listeners.LoginListener;
import io.ruin.model.inter.InterfaceHandler;
import io.ruin.model.inter.ToplevelComponent;
import io.ruin.model.inter.actions.SimpleAction;
import io.ruin.network.PacketSender;
import io.ruin.utility.Broadcast;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The Zelus Lottery (interface 1164, built by .dev/cache-restore-tool/src/BuildLotteryInterface.java; ::lottery).
 * Points only: players deposit PK points or PvM points through the Deposit window. PK points are harder to earn, so
 * they carry {@link #PKP_WEIGHT}x the winning weight of PvM points: weight = PKP * 2 + PvMP, and a player's win chance
 * is their weight over the total weight. Every {@link #ROUND_MINUTES} minutes (clock aligned) a {@link SecureRandom} weighted draw picks the
 * winner if at least {@link #MIN_ENTRANTS} players entered, otherwise the pot rolls over. The winner receives the pot
 * back in the same currencies (the PKP part as PKP, the PvMP part as PvM points); offline winners are paid on login.
 * Points are taken and added to the pot in the same tick, and the pot is saved at once (runtime/saves/lottery.json).
 * The announcer NPC ({@link #ANNOUNCER_NPC}, Jackpot Jarvis) shows the pot overhead for 24s of every 30-40s. Game thread only.
 */
public final class Lottery {

	public static final int INTERFACE = 1164;
	private static final int CLOSE = 2, TITLE = 3, LEFT = 4, RULES = 10, POT = 15, POT_LABEL = 16, POT_ITEMS = 17,
		CHANCE = 18, TIMER = 19, DEPOSIT_PKP = 20, DEPOSIT_PVMP = 22, NOTE = 24, LAST_WINNER = 25, RIGHT = 26,
		FIRST_ROW = 35, ROW_STRIDE = 4, ROWS = 8, YOUR_ENTRY = 71;
	/** Deposit window. Amount buttons: box, border, selected parts +2..+5, label +6. */
	private static final int MODAL = 72, MODAL_TITLE = 77, MODAL_BALANCE = 78, FIRST_AMOUNT = 79, AMOUNT_STRIDE = 7,
		MODAL_CHOSEN = 114, MODAL_STATS = 118, CONFIRM = 119, CANCEL = 121, MODAL_NOTE = 123;
	private static final int[] QUICK_AMOUNTS = {1_000, 5_000, 25_000, 100_000};
	/** Winning weight of 1 PK point (1 PvM point = 1). */
	public static final int PKP_WEIGHT = 2;
	/** The right panel's 2nd column header (BuildLotteryInterface: RIGHT panel + heading + Name, then this). */
	private static final int WEIGHT_HEADER = RIGHT + 7;

	public static final int ROUND_MINUTES = 15, MIN_ENTRANTS = 2;
	/** The Town Crier by the lottery cage at home: shouts the pot and opens the lottery on Talk-to. */
	public static final int ANNOUNCER_NPC = 280;
	/**
	 * Jarvis's shout stays up for SHOUT_VISIBLE_TICKS (24s): an OSRS overhead message only lasts ~3 seconds on the
	 * client, so it is re-sent every SHOUT_REFRESH_TICKS (1.2s) to keep one steady message. Then a quiet gap of
	 * 10-26 ticks, so a new announcement starts every 30-40 seconds.
	 */
	public static final int SHOUT_VISIBLE_TICKS = 40, SHOUT_REFRESH_TICKS = 2, SHOUT_GAP_MIN_TICKS = 10, SHOUT_GAP_MAX_TICKS = 26;
	private static final int COMBAT_LOCK_TICKS = 17;
	private static final SecureRandom RANDOM = new SecureRandom();

	private static final String ORANGE = "<col=ff981f>", WHITE = "<col=ffffff>", GREY = "<col=9a9a9a>", GREEN = "<col=00ff00>",
		YELLOW = "<col=ffff00>", RED = "<col=ff3333>", END = "</col>";
	/** Chat: gold-star badge + high-contrast prefix (readable on the parchment chatbox). */
	private static final String TAG = Icon.COLLECTION_LOG.tag() + " <col=b85900><shad=000000>[Zelus Lottery]:</shad></col> ";
	private static final String CHAT_POINTS = "<col=006600><b>", CHAT_PCT = "<col=000080><shad=000000><b>", CHAT_END = "</b></col>",
		CHAT_PCT_END = "</b></shad></col>", CHAT_ERROR = "<col=7f0000>";

	enum Currency {
		PKP("PKP", "PK Points"), PVMP("PvMP", "PvM Points");

		final String shortName, longName;

		Currency(String shortName, String longName) {
			this.shortName = shortName;
			this.longName = longName;
		}

		int balance(Player p) {
			return this == PKP ? p.getPKPoints() : p.PvmPoints;
		}

		void take(Player p, int amount) {
			if (this == PKP)
				p.updatePKPoints(-amount);
			else
				p.PvmPoints -= amount;
		}

		void give(Player p, int amount) {
			if (this == PKP)
				p.updatePKPoints(amount);
			else
				p.PvmPoints += amount;
		}
	}

	/* ---------------- persisted state ---------------- */

	private static final class Entry {
		@Expose String uid;
		@Expose String name;
		@Expose long pkp;
		@Expose long pvmp;

		/** Winning weight: PK points count {@link #PKP_WEIGHT}x. */
		long weight() {
			return pkp * PKP_WEIGHT + pvmp;
		}
	}

	private static final class SaveState {
		@Expose long round;
		@Expose Map<String, Entry> entries = new HashMap<>();
		@Expose Map<String, Long> pendingPkp = new HashMap<>();
		@Expose Map<String, Long> pendingPvmp = new HashMap<>();
		@Expose String lastWinner;
	}

	private static SaveState state = new SaveState();

	/** Per-viewer deposit window state. */
	private static final class Deposit {
		Currency currency;
		int amount;
	}

	private static final Map<String, Deposit> deposits = new HashMap<>();

	private Lottery() {
	}

	public static void register() {
		load();
		if (state.round == 0) {
			state.round = currentRound();
			save();
		}
		InterfaceHandler.register(INTERFACE, h -> {
			h.actions[CLOSE] = (SimpleAction) Player::closeInterfaces;
			h.actions[DEPOSIT_PKP] = (SimpleAction) p -> openDeposit(p, Currency.PKP);
			h.actions[DEPOSIT_PVMP] = (SimpleAction) p -> openDeposit(p, Currency.PVMP);
			for (int k = 0; k < 5; k++) {
				int index = k;
				h.actions[FIRST_AMOUNT + k * AMOUNT_STRIDE] = (SimpleAction) p -> chooseAmount(p, index);
			}
			h.actions[CONFIRM] = (SimpleAction) Lottery::confirm;
			h.actions[CANCEL] = (SimpleAction) p -> {
				deposits.remove(p.uuid());
				draw(p);
			};
			// i = the interface replacing this one: re-opening the lottery itself (a redraw) must keep the deposit window
			h.closedAction = (p, i) -> {
				if (i != INTERFACE)
					deposits.remove(p.uuid());
			};
		});
		// Jackpot Jarvis: Talk-to explains the lottery, Lottery opens it (cache ops 0 / 1)
		NPCAction.register(ANNOUNCER_NPC, 1, (player, npc) -> talk(player, npc));
		NPCAction.register(ANNOUNCER_NPC, 2, (player, npc) -> open(player));
		World.startEvent(e -> {
			while (true) {
				e.delay(10);
				if (currentRound() != state.round) {
					draw();
				}
				for (Player p : World.players()) {
					if (p.isVisibleInterface(INTERFACE)) {
						sendTimer(p.getPacketSender());
						if (deposits.containsKey(p.uuid()))
							drawDeposit(p);
					}
				}
			}
		});
		World.startEvent(e -> {
			while (true) {
				e.delay(io.ruin.api.utils.Random.get(SHOUT_GAP_MIN_TICKS, SHOUT_GAP_MAX_TICKS));
				for (int t = 0; t < SHOUT_VISIBLE_TICKS; t += SHOUT_REFRESH_TICKS) {
					shout();
					e.delay(SHOUT_REFRESH_TICKS);
				}
			}
		});
		LoginListener.register(Lottery::payPending);
	}

	/** Jackpot Jarvis's "Talk-to": the lottery in four lines, then an offer to open it. */
	private static void talk(Player player, NPC npc) {
		player.dialogue(
			new io.ruin.model.inter.dialogue.NPCDialogue(npc, "Welcome to the Zelus Lottery! Players pool together PK Points and "
				+ "PvM Points, and one lucky entrant wins the whole grand pot."),
			new io.ruin.model.inter.dialogue.NPCDialogue(npc, "To keep things fair, PK Points are weighted at " + PKP_WEIGHT
				+ "x the value of PvM Points: 1 PKP counts as " + PKP_WEIGHT + " tickets, 1 PvMP as 1 ticket."),
			new io.ruin.model.inter.dialogue.NPCDialogue(npc, "Draws happen automatically every " + ROUND_MINUTES + " minutes. If fewer than "
				+ MIN_ENTRANTS + " players enter, the pot safely rolls over to the next round!"),
			new io.ruin.model.inter.dialogue.NPCDialogue(npc, "The winner is paid in the same currencies that went in, even if "
				+ "they're offline when the draw happens."),
			new io.ruin.model.inter.dialogue.OptionsDialogue("Open the lottery?",
				new io.ruin.model.inter.utils.Option("Yes, show me the pot.", () -> open(player)),
				new io.ruin.model.inter.utils.Option("Not right now.")));
	}

	private static long currentRound() {
		return System.currentTimeMillis() / 60_000L / ROUND_MINUTES;
	}

	private static long drawAtMillis() {
		return (currentRound() + 1) * ROUND_MINUTES * 60_000L;
	}

	/** The announcer's overhead shout (plain text: overhead chat is yellow already). */
	private static void shout() {
		long minutes = Math.max(1, (drawAtMillis() - System.currentTimeMillis() + 59_999) / 60_000L);
		long pkp = 0, pvmp = 0;
		for (Entry e : state.entries.values()) {
			pkp += e.pkp;
			pvmp += e.pvmp;
		}
		String text = "[" + minutes + " MIN] - The Lottery is sitting at " + potText(pkp, pvmp) + " (PKP counts 2x)!";
		for (NPC npc : World.npcsSlots()) {
			if (npc.getId() == ANNOUNCER_NPC)
				npc.forceText(text);
		}
	}

	/* ---------------- depositing ---------------- */

	private static boolean canEnter(Player player) {
		if (player.getGameMode().isIronMan()) {
			player.sendMessage(TAG + CHAT_ERROR + "Ironmen can't enter the lottery." + END);
			return false;
		}
		if (player.wildernessLevel > 0 || player.getCombat().isDefending(COMBAT_LOCK_TICKS)
				|| player.getCombat().isAttacking(COMBAT_LOCK_TICKS)) {
			player.sendMessage(TAG + CHAT_ERROR + "You can't enter the lottery in the Wilderness or during combat." + END);
			return false;
		}
		return true;
	}

	private static void openDeposit(Player player, Currency currency) {
		if (!canEnter(player))
			return;
		Deposit d = new Deposit();
		d.currency = currency;
		deposits.put(player.uuid(), d);
		draw(player);
	}

	private static void chooseAmount(Player player, int index) {
		Deposit d = deposits.get(player.uuid());
		if (d == null)
			return;
		if (index < QUICK_AMOUNTS.length) {
			d.amount = QUICK_AMOUNTS[index];
			drawDeposit(player);
			return;
		}
		// The chatbox prompt leaves the lottery screen open. Root cause of the old bug: the callback re-opened the
		// interface, whose closedAction wiped the deposit window. Now the typed amount is stored on this window (d) and
		// the screen is redrawn in place (re-opened only if the player closed it meanwhile).
		player.integerInput("How many " + d.currency.longName + " do you want to deposit? (You have "
			+ fmt(d.currency.balance(player)) + ")", amount -> {
			int balance = d.currency.balance(player);
			if (amount < 1) {
				player.sendMessage(TAG + CHAT_ERROR + "Enter at least 1." + END);
			} else if (amount > balance) {
				player.sendMessage(TAG + CHAT_ERROR + "You only have " + fmt(balance) + " " + d.currency.longName + "." + END);
				d.amount = balance;
			} else {
				d.amount = amount;
			}
			deposits.put(player.uuid(), d);
			if (player.isVisibleInterface(INTERFACE))
				draw(player);
			else
				reopen(player);
		});
	}

	/** Confirm: re-validates everything, takes the points and credits the pot in one tick. */
	private static void confirm(Player player) {
		Deposit d = deposits.remove(player.uuid());
		if (d == null)
			return;
		if (!canEnter(player)) {
			draw(player);
			return;
		}
		int amount = d.amount;
		if (amount < 1) {
			player.sendMessage(TAG + CHAT_ERROR + "Choose an amount to deposit first." + END);
			deposits.put(player.uuid(), d);
			drawDeposit(player);
			return;
		}
		int balance = d.currency.balance(player);
		if (amount > balance) {
			player.sendMessage(TAG + CHAT_ERROR + "You only have " + fmt(balance) + " " + d.currency.longName + "." + END);
			deposits.put(player.uuid(), d);
			drawDeposit(player);
			return;
		}
		d.currency.take(player, amount);
		Entry e = entry(player);
		if (d.currency == Currency.PKP)
			e.pkp += amount;
		else
			e.pvmp += amount;
		save();
		player.sendMessage(TAG + "You deposited " + CHAT_POINTS + fmt(amount) + " " + d.currency.shortName + CHAT_END
			+ " into the pot. Win chance: " + CHAT_PCT + pct2(e.weight()) + "%" + CHAT_PCT_END + ".");
		refreshViewers();
	}

	private static Entry entry(Player player) {
		return state.entries.computeIfAbsent(player.uuid(), k -> {
			Entry e = new Entry();
			e.uid = player.uuid();
			e.name = player.getName();
			return e;
		});
	}

	/* ---------------- the draw ---------------- */

	private static void draw() {
		state.round = currentRound();
		List<Entry> entrants = new ArrayList<>(state.entries.values());
		entrants.removeIf(e -> e.weight() <= 0);
		if (entrants.size() < MIN_ENTRANTS) {
			for (Entry e : entrants) {
				Player p = World.getPlayer(e.uid);
				if (p != null)
					p.sendMessage(TAG + "Not enough entrants this round - your deposit rolls over to the next draw.");
			}
			save();
			refreshViewers();
			return;
		}
		long total = total();
		entrants.sort(Comparator.comparing((Entry e) -> e.uid));
		long ticket = (long) (RANDOM.nextDouble() * total);
		Entry winner = entrants.get(entrants.size() - 1);
		long running = 0;
		for (Entry e : entrants) {
			running += e.weight();
			if (ticket < running) {
				winner = e;
				break;
			}
		}
		double chance = winner.weight() * 100.0 / total;
		long pkpPot = 0, pvmpPot = 0;
		for (Entry e : entrants) {
			pkpPot += e.pkp;
			pvmpPot += e.pvmp;
		}

		// reset the round before paying, so nothing can be paid twice
		state.entries.clear();
		state.lastWinner = winner.name + " won " + potText(pkpPot, pvmpPot) + " (" + String.format(Locale.ROOT, "%.3f", chance) + "%)";
		Player p = World.getPlayer(winner.uid);
		if (p != null && p.isOnline()) {
			pay(p, pkpPot, pvmpPot);
		} else {
			if (pkpPot > 0)
				state.pendingPkp.merge(winner.uid, pkpPot, Long::sum);
			if (pvmpPot > 0)
				state.pendingPvmp.merge(winner.uid, pvmpPot, Long::sum);
		}
		save();
		Broadcast.GLOBAL.sendNews(Icon.COLLECTION_LOG, "Zelus Lottery", winner.name + " has won the lottery pot of "
			+ potText(pkpPot, pvmpPot) + " with a win chance of " + String.format(Locale.ROOT, "%.3f", chance) + "%!");
		refreshViewers();
	}

	private static void pay(Player player, long pkp, long pvmp) {
		io.ruin.model.content.cosmetic.Stardust.celebrate(player);
		long paidPkp = payCapped(player, Currency.PKP, pkp, state.pendingPkp);
		long paidPvmp = payCapped(player, Currency.PVMP, pvmp, state.pendingPvmp);
		player.sendMessage(TAG + "You won the lottery: " + CHAT_POINTS + fmt(pkp) + " PKP" + CHAT_END
			+ (pvmp > 0 ? " and " + CHAT_POINTS + fmt(pvmp) + " PvMP" + CHAT_END : "") + "!");
		if (paidPkp < pkp || paidPvmp < pvmp) {
			save();
			player.sendMessage(TAG + "Your balance is at the maximum: the rest of your winnings is kept for you and paid when "
				+ "you have room (on your next login).");
		}
	}

	/**
	 * Pays what fits under Integer.MAX_VALUE (the balances are ints: an unchecked add wrapped them negative) and keeps
	 * the remainder owed in {@code pending}. Returns the amount paid.
	 */
	private static long payCapped(Player player, Currency currency, long amount, Map<String, Long> pending) {
		if (amount <= 0)
			return 0;
		long room = Math.max(0, (long) Integer.MAX_VALUE - Math.max(0, currency.balance(player)));
		long now = Math.min(amount, room);
		if (now > 0)
			currency.give(player, (int) now);
		if (amount > now)
			pending.merge(player.uuid(), amount - now, Long::sum);
		return now;
	}

	private static void payPending(Player player) {
		Long pkp = state.pendingPkp.remove(player.uuid());
		Long pvmp = state.pendingPvmp.remove(player.uuid());
		if (pkp == null && pvmp == null)
			return;
		save();
		pay(player, pkp == null ? 0 : pkp, pvmp == null ? 0 : pvmp);
	}

	/* ---------------- dashboard ---------------- */

	public static void open(Player player) {
		player.openInterface(ToplevelComponent.MAINMODAL, INTERFACE);
		PacketSender ps = player.getPacketSender();
		ps.sendString(INTERFACE, TITLE, ORANGE + "Zelus Lottery" + END);
		ps.sendString(INTERFACE, RULES, "Add PK Points or PvM Points to the lotto. Your win chance is your weight"
			+ " divided by the total weight.");
		ps.sendString(INTERFACE, POT_LABEL, "- TOTAL POT SIZE -");
		ps.sendString(INTERFACE, DEPOSIT_PKP + 1, GREEN + "+" + END + " Deposit PKP");
		ps.sendString(INTERFACE, DEPOSIT_PVMP + 1, GREEN + "+" + END + " Deposit PvMP");
		ps.sendString(INTERFACE, NOTE, "PK Points provide " + PKP_WEIGHT + "x winning weight compared to PvM Points. Needs "
			+ MIN_ENTRANTS + "+ entrants, otherwise the pot rolls over.");
		ps.sendString(INTERFACE, WEIGHT_HEADER, "Weight");
		ps.sendString(INTERFACE, MODAL_NOTE, "Notice: Points deposited into the lottery pot are final and non-refundable.");
		ps.sendString(INTERFACE, CONFIRM + 1, GREEN + "+" + END + " Confirm Deposit");
		ps.sendString(INTERFACE, CANCEL + 1, RED + "x" + END + " Cancel");
		for (int k = 0; k < 5; k++)
			ps.sendString(INTERFACE, FIRST_AMOUNT + k * AMOUNT_STRIDE + 6, k < QUICK_AMOUNTS.length ? fmt(QUICK_AMOUNTS[k]) : "Enter X");
		draw(player);
	}

	private static void reopen(Player player) {
		if (player.isOnline())
			open(player);
	}

	private static void refreshViewers() {
		for (Player p : World.players())
			if (p.isVisibleInterface(INTERFACE))
				draw(p);
	}

	private static void draw(Player player) {
		PacketSender ps = player.getPacketSender();
		long pkp = 0, pvmp = 0;
		for (Entry e : state.entries.values()) {
			pkp += e.pkp;
			pvmp += e.pvmp;
		}
		ps.sendString(INTERFACE, POT, fmt(pkp) + " PKP");
		ps.sendString(INTERFACE, POT_ITEMS, (pvmp > 0 ? "+ " + fmt(pvmp) + " PvMP  |  " : "") + "Total weight: " + fmt(total()));
		Entry mine = state.entries.get(player.uuid());
		long portion = mine == null ? 0 : mine.weight();
		ps.sendString(INTERFACE, CHANCE, "Your Win Chance: " + ORANGE + pct(portion) + "%" + END + "<br>Your Weight: "
			+ GREEN + fmt(portion) + END + GREY + " (PKP x" + PKP_WEIGHT + " + PvMP)" + END);
		ps.sendString(INTERFACE, LAST_WINNER, state.lastWinner == null ? GREY + "No draws yet." + END
			: "Last winner:<br>" + WHITE + state.lastWinner + END);
		sendTimer(ps);

		List<Entry> top = new ArrayList<>(state.entries.values());
		top.removeIf(e -> e.weight() <= 0);
		top.sort(Comparator.comparingLong(Entry::weight).reversed());
		for (int r = 0; r < ROWS; r++) {
			int row = FIRST_ROW + r * ROW_STRIDE;
			Entry e = r < top.size() ? top.get(r) : null;
			ps.sendString(INTERFACE, row + 1, e == null ? "<col=6f6f6f>-- Open --" + END : (r + 1) + ". " + e.name);
			ps.sendString(INTERFACE, row + 2, e == null ? "" : fmt(e.weight()));
			ps.sendString(INTERFACE, row + 3, e == null ? "" : pct(e.weight()) + "%");
		}
		int rank = mine == null ? 0 : top.indexOf(mine) + 1;
		ps.sendString(INTERFACE, YOUR_ENTRY, mine == null ? GREY + "You haven't entered this round.<br>Deposit PKP or PvMP to play." + END
			: "Rank: " + WHITE + "#" + rank + " of " + top.size() + END
			+ "<br>PKP: " + GREEN + fmt(mine.pkp) + END + "   PvMP: " + GREEN + fmt(mine.pvmp) + END
			+ "<br>Win chance: " + ORANGE + pct(mine.weight()) + "%" + END + GREY + " (weight " + fmt(mine.weight()) + ")" + END);

		boolean modal = deposits.containsKey(player.uuid());
		ps.setHidden(INTERFACE, MODAL, !modal);
		ps.setHidden(INTERFACE, LEFT, modal);
		ps.setHidden(INTERFACE, RIGHT, modal);
		if (modal)
			drawDeposit(player);
	}

	private static void drawDeposit(Player player) {
		Deposit d = deposits.get(player.uuid());
		if (d == null)
			return;
		PacketSender ps = player.getPacketSender();
		ps.sendString(INTERFACE, MODAL_TITLE, ORANGE + "Zelus Lottery · Deposit " + d.currency.longName + END);
		ps.sendString(INTERFACE, MODAL_BALANCE, "Available: " + GREEN + fmt(d.currency.balance(player)) + " " + d.currency.shortName + END);
		for (int k = 0; k < 5; k++) {
			int comp = FIRST_AMOUNT + k * AMOUNT_STRIDE;
			boolean selected = k < QUICK_AMOUNTS.length ? d.amount == QUICK_AMOUNTS[k]
				: d.amount > 0 && !isQuick(d.amount);
			for (int part = 2; part <= 5; part++)
				ps.setHidden(INTERFACE, comp + part, !selected);
		}
		ps.sendString(INTERFACE, MODAL_CHOSEN, d.amount < 1 ? GREY + "Choose an amount" + END
			: "Deposit: " + YELLOW + fmt(d.amount) + " " + d.currency.shortName + END
			+ (d.amount > d.currency.balance(player) ? RED + "  (not enough)" + END : ""));
		Entry mine = state.entries.get(player.uuid());
		long added = (long) Math.max(0, d.amount) * (d.currency == Currency.PKP ? PKP_WEIGHT : 1);
		long after = (mine == null ? 0 : mine.weight()) + added;
		long pot = total() + added;
		String chance = pot <= 0 ? "0.000" : String.format(Locale.ROOT, "%.3f", after * 100.0 / pot);
		long seconds = Math.max(0, (drawAtMillis() - System.currentTimeMillis()) / 1000);
		ps.sendString(INTERFACE, MODAL_STATS, ORANGE + "TOTAL WEIGHT: " + END + GREEN + fmt(total()) + END
			+ "  -  " + ORANGE + "WIN CHANCE: " + END + RED + chance + "%" + END
			+ "<br>" + ORANGE + "TIME LEFT: " + END + YELLOW + (seconds / 60) + "M, " + String.format("%02d", seconds % 60) + "S" + END);
	}

	private static boolean isQuick(int amount) {
		for (int q : QUICK_AMOUNTS)
			if (q == amount)
				return true;
		return false;
	}

	private static void sendTimer(PacketSender ps) {
		long seconds = Math.max(0, (drawAtMillis() - System.currentTimeMillis()) / 1000);
		ps.sendString(INTERFACE, TIMER, ORANGE + "Next draw in: " + END + WHITE + (seconds / 60) + "m "
			+ String.format("%02d", seconds % 60) + "s" + END);
	}

	/** "12,000 PKP + 3,000 PvMP" (the PvMP part only when there is one). */
	private static String potText(long pkp, long pvmp) {
		return fmt(pkp) + " PKP" + (pvmp > 0 ? " + " + fmt(pvmp) + " PvMP" : "");
	}

	/** Total winning weight of every entry (PKP x {@link #PKP_WEIGHT} + PvMP). */
	private static long total() {
		long total = 0;
		for (Entry e : state.entries.values())
			total += e.weight();
		return total;
	}

	private static String pct(long weight) {
		long total = total();
		return total <= 0 ? "0.000" : String.format(Locale.ROOT, "%.3f", weight * 100.0 / total);
	}

	private static String pct2(long weight) {
		long total = total();
		return total <= 0 ? "0.00" : String.format(Locale.ROOT, "%.2f", weight * 100.0 / total);
	}

	private static String fmt(long value) {
		return String.format(Locale.ROOT, "%,d", value);
	}

	/* ---------------- storage ---------------- */

	private static File file() {
		return new File(ServerWrapper.dataFolder, "runtime/saves/lottery.json");
	}

	private static void load() {
		File file = file();
		if (!file.exists())
			return;
		try (FileReader reader = new FileReader(file)) {
			SaveState loaded = JsonUtils.GSON.fromJson(reader, SaveState.class);
			if (loaded != null) {
				if (loaded.entries == null) loaded.entries = new HashMap<>();
				if (loaded.pendingPkp == null) loaded.pendingPkp = new HashMap<>();
				if (loaded.pendingPvmp == null) loaded.pendingPvmp = new HashMap<>();
				state = loaded;
			}
		} catch (Exception e) {
			ServerWrapper.logError("Unable to load the lottery", e);
		}
	}

	private static void save() {
		File file = file();
		file.getParentFile().mkdirs();
		try (FileWriter writer = new FileWriter(file)) {
			JsonUtils.GSON_PRETTY.toJson(state, writer);
		} catch (Exception e) {
			ServerWrapper.logError("Unable to save the lottery", e);
		}
	}
}
