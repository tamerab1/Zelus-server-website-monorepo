package io.ruin.model.activities.colosseum;

import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.InterfaceHandler;
import io.ruin.model.inter.ToplevelComponent;
import io.ruin.model.inter.actions.SimpleAction;
import io.ruin.network.PacketSender;

import java.util.HashMap;
import java.util.Map;

/**
 * The Colosseum of Trials' trial picker, interface 1182 (.dev/cache-restore-tool BuildTrialSelectInterface, art
 * assets/colosseum/MakeColosseumArt, written by cache stage 21). Left: the trials by tier. Right: the picked trial: its boss on
 * the arena floor, the facts (tier, style, weakness, bosses, PBs), the line-up (clicking a boss puts it on the floor) and
 * Enter solo / Form duo.
 * <p>
 * The models are the bosses' showcase copies (stage 21: bounding box centred on the origin, 63472-63489) with zooms
 * computed for each box by MakeShowcaseModels (assets/colosseum/cameras.txt), so nothing pokes out of its box.
 */
public final class TrialSelectInterface {

	private TrialSelectInterface() {
	}

	public static final int INTERFACE = 1182;
	// component ids, as BuildTrialSelectInterface prints them
	private static final int CLOSE = 6, BALANCE = 8, NAME = 36, STAGE_MODEL = 38, FACTS = 39, FIRST_THUMB = 41, THUMB_STRIDE = 4, THUMB_PICKED = 1,
			THUMB_MODEL = 3, THUMBS = 5, NOTE = 61, SOLO = 63, DUO = 65, ROW_PICKED = 1, ROW_LABEL = 2;
	private static final int[] TIERS = { 9, 19, 26 };
	private static final int[] ROWS = { 10, 13, 16, 20, 23, 27, 30, 33 };
	private static final String GOLD = "<col=ecc67a>", MUTED = "<col=b8a890>", END = "</col>";
	/** a slight showcase pitch; yaw 0 shows the boss's front */
	private static final int PITCH = 96, YAW = 0;

	/** npc -> {showcase model, stand animation (-1: base pose; Vespira's hover lifts her out of the box), stage zoom, thumbnail zoom} (cameras.txt) */
	private static final Map<Integer, int[]> LOOK = Map.ofEntries(
			Map.entry(21941, new int[] { 63472, 813, 2391, 7714 }), Map.entry(21923, new int[] { 63473, 808, 2257, 6446 }),
			Map.entry(21924, new int[] { 63474, 16242, 3188, 8861 }), Map.entry(21921, new int[] { 63475, 16234, 1571, 4887 }),
			Map.entry(21925, new int[] { 63476, 16238, 1396, 4505 }), Map.entry(21928, new int[] { 63477, 808, 2006, 5641 }),
			Map.entry(21933, new int[] { 63478, 808, 1271, 3886 }), Map.entry(21926, new int[] { 63479, 808, 1414, 4563 }),
			Map.entry(21939, new int[] { 63480, 813, 2093, 5888 }), Map.entry(21929, new int[] { 63481, 813, 1024, 2874 }),
			Map.entry(21938, new int[] { 63482, 7053, 2309, 6992 }), Map.entry(21942, new int[] { 63483, 2065, 2203, 6232 }),
			Map.entry(21943, new int[] { 63484, 808, 2449, 6966 }), Map.entry(21944, new int[] { 63485, 813, 3189, 9024 }),
			Map.entry(21940, new int[] { 63486, 808, 2343, 6513 }), Map.entry(21934, new int[] { 63487, 16246, 1735, 5314 }),
			Map.entry(21946, new int[] { 63488, -1, 2705, 8729 }), Map.entry(21945, new int[] { 63489, 808, 4380, 14133 }));

	/** per player: the picked trial and the wave on the floor */
	private record Pick(Trial trial, int wave) {}

	private static final Map<Integer, Pick> PICKED = new HashMap<>();

	static void register() {
		InterfaceHandler.register(INTERFACE, h -> {
			h.actions[CLOSE] = (SimpleAction) Player::closeInterfaces;
			Trial[] all = Trial.values();
			for (int i = 0; i < all.length; i++) {
				Trial t = all[i];
				h.actions[ROWS[i] + ROW_LABEL] = (SimpleAction) p -> pick(p, t, t.waves.length - 1);
			}
			for (int w = 0; w < THUMBS; w++) {
				int wave = w;
				h.actions[FIRST_THUMB + w * THUMB_STRIDE] = (SimpleAction) p -> {
					Pick cur = PICKED.get(p.getUserId());
					if (cur != null && wave < cur.trial.waves.length)
						pick(p, cur.trial, wave);
				};
			}
			h.actions[SOLO] = (SimpleAction) p -> start(p, false);
			h.actions[DUO] = (SimpleAction) p -> start(p, true);
			h.closedAction = (p, i) -> PICKED.remove(p.getUserId());
		});
	}

	public static void open(Player player) {
		open(player, Trial.values()[0]);
	}

	/** Opens on one trial (a host's Talk-to), its headline boss on the floor. */
	public static void open(Player player, Trial focus) {
		PICKED.put(player.getUserId(), new Pick(focus, focus.waves.length - 1));
		player.openInterface(ToplevelComponent.MAINMODAL, INTERFACE);
		drawList(player);
		drawShowcase(player);
	}

	private static void start(Player player, boolean duo) {
		Pick cur = PICKED.get(player.getUserId());
		if (cur == null)
			return;
		player.closeInterfaces();
		if (duo)
			Colosseum.invite(player, cur.trial);
		else
			Colosseum.enter(player, cur.trial);
	}

	private static void pick(Player player, Trial t, int wave) {
		PICKED.put(player.getUserId(), new Pick(t, wave));
		drawShowcase(player);
	}

	private static void drawList(Player player) {
		PacketSender ps = player.getPacketSender();
		ps.sendString(INTERFACE, BALANCE, String.valueOf(player.trialMarks));
		Trial[] all = Trial.values();
		for (int t = 0; t < TIERS.length; t++)
			for (Trial trial : all)
				if (trial.tier == t + 1) {
					ps.sendString(INTERFACE, TIERS[t], "Tier " + (t + 1) + " - " + trial.points + " Marks");
					break;
				}
		for (int i = 0; i < all.length; i++)
			ps.sendString(INTERFACE, ROWS[i] + ROW_LABEL, all[i].listName);
	}

	private static void drawShowcase(Player player) {
		Pick cur = PICKED.get(player.getUserId());
		if (cur == null)
			return;
		Trial t = cur.trial;
		PacketSender ps = player.getPacketSender();
		Trial[] all = Trial.values();
		for (int i = 0; i < all.length; i++)
			ps.setHidden(INTERFACE, ROWS[i] + ROW_PICKED, all[i] != t);
		ps.sendString(INTERFACE, NAME, t.title);
		show(ps, STAGE_MODEL, t.waves[cur.wave], 2);
		ps.sendString(INTERFACE, FACTS, GOLD + "Tier " + t.tier + END + " - " + t.points + " Marks<br>"
				+ MUTED + "Style: " + END + t.style + "<br>"
				+ MUTED + "Weak to: " + END + t.weakness + "<br>"
				+ MUTED + "Bosses: " + END + t.waves.length + "<br>"
				+ MUTED + "PB solo: " + END + Colosseum.best(player, t, false) + "<br>"
				+ MUTED + "PB duo: " + END + Colosseum.best(player, t, true));
		for (int w = 0; w < THUMBS; w++) {
			int thumb = FIRST_THUMB + w * THUMB_STRIDE;
			boolean used = w < t.waves.length;
			ps.setHidden(INTERFACE, thumb, !used);
			ps.setHidden(INTERFACE, thumb + THUMB_PICKED, !used || w != cur.wave);
			ps.setHidden(INTERFACE, thumb + THUMB_MODEL, !used);
			if (used)
				show(ps, thumb + THUMB_MODEL, t.waves[w], 3);
		}
		ps.sendString(INTERFACE, NOTE, "Duo: bosses have +60% HP, marks split by damage");
	}

	/** zoomIndex 2 = the stage, 3 = a thumbnail */
	private static void show(PacketSender ps, int child, int npc, int zoomIndex) {
		int[] look = LOOK.get(npc);
		if (look == null)
			return;
		ps.sendModel(INTERFACE, child, look[0]);
		ps.sendModelInformation(INTERFACE, child, look[zoomIndex], PITCH, YAW);
		ps.animateInterface(INTERFACE, child, look[1]);
	}
}
