package io.ruin.model.content.fortune;

import io.ruin.api.utils.NumberUtils;
import io.ruin.cache.ItemID;
import io.ruin.model.World;
import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.InterfaceHandler;
import io.ruin.model.inter.ToplevelComponent;
import io.ruin.model.inter.actions.SimpleAction;
import io.ruin.network.PacketSender;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Statue of Fortune window, interface 1173 (.dev/cache-restore-tool BuildFortuneInterface). Everything is drawn from
 * {@link StatueOfFortune}'s state; open windows are redrawn on every change and every 2 ticks for the timers.
 */
public class FortuneInterface {
	public static final int INTERFACE = 1173;
	private static final int CLOSE = 2, TITLE = 3, ACTIVE = 4, NEXT_EXPIRY = 5, SCROLL = 11, SCROLLBAR = 12,
			FIRST_ROW = 13, ROW_STRIDE = 8, NAME = 82, MODEL = 87, EFFECT = 88, FIRST_SEG = 89, SEG_STRIDE = 8, TOTALS = 113,
			TIME = 114, EXTEND = 115, TAB_POOL = 121, TAB_ALL = 125, FIRST_BOARD = 129, YOU_POOL = 141, YOU_ALL = 142,
			YOU_NEXT = 143, FIRST_QUICK = 144, BROADCAST = 154;
	/** Bar segment parts (offsets from its first component): fill base / highlight / shade strips, the sliding cover, label. */
	private static final int SEG_BASE = 2, SEG_HIGHLIGHT = 3, SEG_SHADE = 4, SEG_COVER = 5, SEG_LABEL = 7;
	/** {base, highlight, shade}: completed tiers green, the tier being filled amber. */
	private static final int[] DONE = {0x3a9a3a, 0x7ad67a, 0x226622}, FILLING = {0xd8861c, 0xffc463, 0x9a5a12};
	/** 8 rows of 40px in a 204px scroll container: clientscript 10532 sizes it to count * 15 + height / 3. */
	private static final int SCROLL_ROWS = (int) Math.ceil((Blessing.VALUES.length * 40 - 204 / 3D) / 15D);
	/** Client scripts: set a component's width / colour. */
	private static final int SET_COLOUR = 45, SEG_WIDTH = 46;
	private static final String GOLD = "<col=ffb83f>", GREEN = "<col=3ddc5a>", AMBER = "<col=ff9a1f>", MUTED = "<col=8f8573>",
			CRIMSON = "<col=e0402a>", END = "</col>";
	private static final long[] QUICK = {1_000_000L, 5_000_000L, 25_000_000L};
	private static final String[] QUICK_LABELS = {"1M", "5M", "25M", "Custom", "All cash"};
	/** Statue state model + idle seq per loc (BuildFortuneStatue). */
	private static final Map<Integer, int[]> MODELS = Map.of(
			StatueOfFortune.DORMANT, new int[]{65277, 16203},
			StatueOfFortune.CHARGED, new int[]{65278, 16204},
			StatueOfFortune.BLESSED, new int[]{65279, 16205});
	private static final int MODEL_ZOOM = 3360, MODEL_PITCH = 110, MODEL_YAW = 0;

	private static final class View {
		Blessing selected = Blessing.ZELUS_POINTS;
		boolean allTime;
	}

	private static final Map<String, View> views = new HashMap<>();

	private static View view(Player p) {
		return views.computeIfAbsent(p.uuid(), k -> new View());
	}

	static void register() {
		InterfaceHandler.register(INTERFACE, h -> {
			h.actions[CLOSE] = (SimpleAction) Player::closeInterfaces;
			for (int r = 0; r < Blessing.VALUES.length; r++) {
				Blessing b = Blessing.VALUES[r];
				h.actions[FIRST_ROW + r * ROW_STRIDE] = (SimpleAction) p -> {
					view(p).selected = b;
					draw(p);
				};
			}
			h.actions[TAB_POOL] = (SimpleAction) p -> { view(p).allTime = false; draw(p); };
			h.actions[TAB_ALL] = (SimpleAction) p -> { view(p).allTime = true; draw(p); };
			for (int q = 0; q < QUICK.length; q++) {
				long amount = QUICK[q];
				h.actions[FIRST_QUICK + q * 2] = (SimpleAction) p -> give(p, amount);
			}
			h.actions[FIRST_QUICK + 3 * 2] = (SimpleAction) FortuneInterface::customPrompt;
			h.actions[FIRST_QUICK + 4 * 2] = (SimpleAction) FortuneInterface::sacrificeStack;
			h.actions[BROADCAST] = (SimpleAction) p -> {
				p.fortuneBroadcast = !p.fortuneBroadcast;
				draw(p);
			};
		});
	}

	/** Opens the window (on `b` when given); `prompt` asks for a custom amount right away (the statue's Sacrifice op). */
	public static void open(Player p, Blessing b, boolean prompt) {
		if (b != null)
			view(p).selected = b;
		p.openInterface(ToplevelComponent.MAINMODAL, INTERFACE);
		io.ruin.model.ScrollbarClientScript.create().interfaceId(INTERFACE).containerId(SCROLL).scrollbarChildId(SCROLLBAR)
				.childrenCount(SCROLL_ROWS).withDarkGraphics().build().send(p);
		draw(p);
		if (prompt)
			customPrompt(p);
	}

	public static void openBenefactors(Player p) {
		view(p).allTime = true;
		open(p, null, false);
	}

	/** Asks first ("Sacrifice 25,000,000 coins to Pet Rates?"); the coins are only taken, and re-checked, on Yes. */
	static void give(Player p, long amount) {
		Blessing b = view(p).selected;
		boolean broadcast = p.fortuneBroadcast;
		p.unsafeDialogue(new io.ruin.model.inter.dialogue.OptionsDialogue(
				"Sacrifice " + NumberUtils.formatNumber(amount) + " coins to " + b.title + "?",
				new io.ruin.model.inter.utils.Option("Yes, sacrifice them.", () -> {
					p.closeDialogue();
					if (StatueOfFortune.sacrifice(p, b, amount, broadcast) && p.isVisibleInterface(INTERFACE))
						draw(p);
				}),
				new io.ruin.model.inter.utils.Option("No.", p::closeDialogue)));
	}

	static void customPrompt(Player p) {
		p.integerInput("How many coins will you sacrifice to " + view(p).selected.title + "?", amount -> {
			if (amount > 0)
				give(p, amount);
		});
	}

	/** Sacrifices every coin and platinum token in the inventory (also: coins used on the statue). */
	public static void sacrificeStack(Player p) {
		long all = p.getInventory().getAmount(995) + p.getInventory().getAmount(ItemID.PLATINUM_TOKEN) * 1000L;
		if (all <= 0) {
			p.sendMessage("You have no coins to sacrifice.");
			return;
		}
		give(p, all);
	}

	static void refreshAll() {
		for (Player p : World.players())
			if (p.isVisibleInterface(INTERFACE))
				draw(p);
	}

	static void refreshTimers() {
		if (StatueOfFortune.activeCount() > 0)
			refreshAll();
	}

	static void draw(Player p) {
		PacketSender ps = p.getPacketSender();
		View v = view(p);
		Blessing sel = v.selected;
		ps.sendString(INTERFACE, TITLE, "Statue of Fortune");

		// header
		int active = StatueOfFortune.activeCount();
		ps.sendString(INTERFACE, ACTIVE, active == 0 ? MUTED + "No blessings active" + END
				: GREEN + active + (active == 1 ? " Blessing" : " Blessings") + " Active" + END);
		long next = Long.MAX_VALUE;
		for (Blessing b : Blessing.VALUES)
			if (StatueOfFortune.isActive(b))
				next = Math.min(next, StatueOfFortune.timeLeft(b));
		int loc = StatueOfFortune.desiredLoc();
		ps.sendString(INTERFACE, NEXT_EXPIRY, next != Long.MAX_VALUE ? "Next fades in " + GOLD + StatueOfFortune.clock(next) + END
				: loc == StatueOfFortune.CHARGED ? AMBER + "The statue stirs..." + END : MUTED + "The statue is dormant" + END);

		// left: blessings
		for (int r = 0; r < Blessing.VALUES.length; r++) {
			Blessing b = Blessing.VALUES[r];
			int row = FIRST_ROW + r * ROW_STRIDE;
			boolean selected = b == sel;
			for (int part = 2; part <= 5; part++)
				ps.setHidden(INTERFACE, row + part, !selected);
			ps.sendString(INTERFACE, row + 6, (selected ? GOLD : "") + b.title + (selected ? END : ""));
			ps.sendString(INTERFACE, row + 7, badge(b));
		}

		// centre: selected blessing
		int tier = StatueOfFortune.tier(sel);
		ps.sendString(INTERFACE, NAME, sel.title + (tier > 0 ? " - Tier " + StatueOfFortune.TIER[tier] : ""));
		int[] model = MODELS.get(loc);
		ps.sendModel(INTERFACE, MODEL, model[0]);
		ps.sendModelInformation(INTERFACE, MODEL, MODEL_ZOOM, MODEL_PITCH, MODEL_YAW);
		ps.animateInterface(INTERFACE, MODEL, model[1]);
		String effect = tier > 0
				? GREEN + sel.effect(tier) + END + "<br>" + (tier < 3 ? MUTED + "Next: " + sel.effect(tier + 1) + END : GOLD + "Maximum tier" + END)
				: MUTED + "Not active" + END + "<br>Tier I: " + sel.effect(1);
		ps.sendString(INTERFACE, EFFECT, effect);
		long pool = StatueOfFortune.poolOf(sel);
		for (int s = 0; s < 3; s++) {
			int seg = FIRST_SEG + s * SEG_STRIDE;
			long lo = s == 0 ? 0 : sel.cost(s), hi = sel.cost(s + 1);
			double fill = tier > s ? 1 : Math.max(0, Math.min(1, (double) (pool - lo) / (hi - lo)));
			int[] colours = tier > s ? DONE : FILLING;
			for (int part = 0; part < 3; part++)
				ps.sendClientScript(SET_COLOUR, "Ii", INTERFACE << 16 | (seg + SEG_BASE + part), colours[part]);
			ps.setAlignment(INTERFACE, seg + SEG_COVER, (int) Math.round(fill * SEG_WIDTH), 0);
			ps.sendString(INTERFACE, seg + SEG_LABEL, (tier > s ? GREEN : MUTED) + StatueOfFortune.shortGp(hi) + END);
		}
		if (tier < 3) {
			long goal = sel.cost(tier + 1);
			ps.sendString(INTERFACE, TOTALS, StatueOfFortune.shortGp(pool) + " / " + StatueOfFortune.shortGp(goal)
					+ " (" + (int) (pool * 100 / goal) + "%)");
		} else {
			ps.sendString(INTERFACE, TOTALS, GOLD + "Tier III reached" + END + " - Reserve " + StatueOfFortune.shortGp(StatueOfFortune.reserveOf(sel)));
		}
		ps.sendString(INTERFACE, TIME, tier > 0 ? "Time left " + StatueOfFortune.clock(StatueOfFortune.timeLeft(sel)) : "");
		String extend;
		if (tier == 3)
			extend = "+1 min per " + StatueOfFortune.shortGp(sel.extendRate()) + " (max 3h)";
		else if (tier > 0)
			extend = "Tier " + StatueOfFortune.TIER[tier + 1] + " resets to 60:00";
		else
			extend = "Tier I: a 60-min blessing";
		ps.sendString(INTERFACE, EXTEND, extend);

		// right: benefactors
		drawTab(ps, TAB_POOL, !v.allTime, "This pool");
		drawTab(ps, TAB_ALL, v.allTime, "All-time");
		List<Map.Entry<String, Long>> top = StatueOfFortune.top(v.allTime ? null : sel, 5);
		for (int i = 0; i < 5; i++) {
			int row = FIRST_BOARD + i * 2;
			if (i >= top.size()) {
				ps.sendString(INTERFACE, row, i == 0 ? MUTED + "No sacrifices yet" + END : "");
				ps.sendString(INTERFACE, row + 1, "");
				continue;
			}
			Map.Entry<String, Long> e = top.get(i);
			String[] rank = StatueOfFortune.rank(StatueOfFortune.state.allTime.getOrDefault(e.getKey(), 0L));
			String name = StatueOfFortune.displayName(e.getKey());
			ps.sendString(INTERFACE, row, (i + 1) + ". " + (rank != null ? "<col=" + rank[1] + ">" + name + END : name));
			ps.sendString(INTERFACE, row + 1, StatueOfFortune.shortGp(e.getValue()));
		}
		long mine = StatueOfFortune.contributionOf(p, sel), mineAll = StatueOfFortune.allTimeOf(p);
		ps.sendString(INTERFACE, YOU_POOL, "This pool: " + GOLD + StatueOfFortune.shortGp(mine) + END);
		ps.sendString(INTERFACE, YOU_ALL, "All-time: " + GOLD + StatueOfFortune.shortGp(mineAll) + END);
		String[] rank = StatueOfFortune.rank(mineAll);
		Object[] up = StatueOfFortune.nextRank(mineAll);
		ps.sendString(INTERFACE, YOU_NEXT, (rank != null ? "Rank: <col=" + rank[1] + ">" + rank[0] + END + "<br>" : "")
				+ (up != null ? "Next rank: " + up[1] + "<br>at " + StatueOfFortune.shortGp((long) up[0]) + " all-time" : GOLD + "Highest rank reached" + END));

		// action bar
		for (int q = 0; q < QUICK_LABELS.length; q++)
			ps.sendString(INTERFACE, FIRST_QUICK + q * 2 + 1, QUICK_LABELS[q]);
		ps.sendString(INTERFACE, BROADCAST + 1, "Broadcast: " + (p.fortuneBroadcast ? GREEN + "On" : CRIMSON + "Off") + END);
	}

	private static void drawTab(PacketSender ps, int tab, boolean selected, String label) {
		ps.setHidden(INTERFACE, tab + 1, !selected);
		ps.setHidden(INTERFACE, tab + 2, !selected);
		ps.sendString(INTERFACE, tab + 3, selected ? GOLD + label + END : label);
	}

	private static String badge(Blessing b) {
		int tier = StatueOfFortune.tier(b);
		if (tier > 0)
			return GREEN + "T" + StatueOfFortune.TIER[tier] + " " + StatueOfFortune.clock(StatueOfFortune.timeLeft(b)) + END;
		long pool = StatueOfFortune.poolOf(b);
		if (pool <= 0)
			return MUTED + "Not started" + END;
		return AMBER + (int) (pool * 100 / b.cost(1)) + "% to Tier I" + END;
	}
}
