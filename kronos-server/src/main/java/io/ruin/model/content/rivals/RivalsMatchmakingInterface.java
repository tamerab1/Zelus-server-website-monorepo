package io.ruin.model.content.rivals;

import io.ruin.cache.ObjType;
import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.InterfaceHandler;
import io.ruin.model.inter.ToplevelComponent;
import io.ruin.model.inter.actions.SimpleAction;
import io.ruin.model.item.Item;
import io.ruin.model.item.containers.Equipment;
import io.ruin.network.PacketSender;

import java.util.Locale;

/**
 * The Division Rivals matchmaking window, interface 1172 (.dev/cache-restore-tool BuildRivalsMatchmakingInterface).
 * Profile strip (division badge, SR, record, weekly, points), the four brackets as selectable cards (2 x 2) with their kit, a
 * status line and one main button (Find match / Cancel search); when a match is found the main button gives way to
 * Accept / Decline (the ready check, RivalsQueueManager). Footer links open the Rivals shop and leaderboard.
 */
public final class RivalsMatchmakingInterface {

	public static final int INTERFACE = 1172;
	private static final int CLOSE = 2, TITLE = 3;
	private static final int BADGE = 9, DIVISION = 10, LINE_RECORD = 11, LINE_WEEKLY = 12, LINE_POINTS = 13;
	private static final int[] CARD = {15, 31, 47, 63};
	private static final int CARD_CLICK = 5, CARD_RECESS = 6, CARD_TRIM = 7, CARD_NAME = 8, CARD_STATS = 9, CARD_MODELS = 10,
		CARD_SEARCHING = 15;
	private static final int STATUS = 79, MAIN_BUTTON = 80, MAIN_LABEL = 81, ACCEPT = 82, DECLINE = 84, SHOP = 86, LEADERBOARD = 87;
	private static final int BADGE_SPRITE_START = 24200, PRIMARY = 24227, SECONDARY = 24220;
	/** Kit preview: weapon, helm, body, legs, shield slot. */
	private static final int[] PREVIEW_SLOTS = {Equipment.SLOT_WEAPON, Equipment.SLOT_HAT, Equipment.SLOT_CHEST, Equipment.SLOT_LEGS,
		Equipment.SLOT_SHIELD};
	private static final String MODE_KEY = "rivals-selected-mode";

	private RivalsMatchmakingInterface() {
	}

	static void register() {
		InterfaceHandler.register(INTERFACE, h -> {
			h.actions[CLOSE] = (SimpleAction) p -> p.closeInterface(ToplevelComponent.MAINMODAL);
			for (int k = 0; k < CARD.length; k++) {
				RivalsMode mode = RivalsMode.values()[k];
				h.actions[CARD[k] + CARD_CLICK] = (io.ruin.model.inter.actions.OptionAction) (p, option) -> {
					if (option == 2) { // "Configure layout"
						if (RivalsLayout.active(p))
							RivalsLayout.reprompt(p);
						else
							RivalsLayout.start(p, mode);
						return;
					}
					if (RivalsQueueManager.isQueued(p)) {
						p.sendMessage("Cancel your search to switch queues.");
						return;
					}
					p.temp.put(MODE_KEY, mode);
					render(p);
				};
			}
			SimpleAction main = RivalsMatchmakingInterface::mainButton;
			h.actions[MAIN_BUTTON] = main;
			h.actions[MAIN_LABEL] = main;
			h.actions[ACCEPT] = (SimpleAction) RivalsQueueManager::accept;
			h.actions[ACCEPT + 1] = (SimpleAction) RivalsQueueManager::accept;
			h.actions[DECLINE] = (SimpleAction) RivalsQueueManager::decline;
			h.actions[DECLINE + 1] = (SimpleAction) RivalsQueueManager::decline;
			h.actions[SHOP] = (SimpleAction) RivalsShop::open;
			h.actions[LEADERBOARD] = (SimpleAction) RivalsLeaderboard::open;
		});
	}

	public static void open(Player p) {
		p.openInterface(ToplevelComponent.MAINMODAL, INTERFACE);
		render(p);
	}

	static RivalsMode selected(Player p) {
		Object m = p.temp.get(MODE_KEY);
		return m instanceof RivalsMode mode ? mode : RivalsMode.ENDGAME_PRISMATIC;
	}

	private static void mainButton(Player p) {
		if (RivalsQueueManager.isQueued(p)) {
			RivalsQueueManager.leave(p, "You stopped searching.");
		} else {
			RivalsQueueManager.join(p, selected(p));
		}
		refreshIfOpen(p);
	}

	static void refreshIfOpen(Player p) {
		if (p.isOnline() && p.isVisibleInterface(INTERFACE))
			render(p);
	}

	/** The live search / ready-check line only (every 2 ticks while queued). */
	static void refreshStatus(Player p) {
		if (p.isVisibleInterface(INTERFACE)) {
			String line = RivalsQueueManager.searchLine(p);
			if (line != null)
				p.getPacketSender().sendString(INTERFACE, STATUS, line);
		}
	}

	private static void render(Player p) {
		RivalsProfile.rollWeek(p);
		PacketSender ps = p.getPacketSender();
		ps.sendString(INTERFACE, TITLE, "<col=ff981f>Division Rivals - Matchmaking</col>");
		boolean elite = p.rivalsDivision == 0;
		ps.sendClientScript(44, "Ii", INTERFACE << 16 | BADGE, BADGE_SPRITE_START + (elite ? 10 : 10 - p.rivalsDivision));
		ps.sendString(INTERFACE, DIVISION, elite ? "<col=ffd700>Elite</col>  <col=9a9a9a>#" + RivalsProfile.RivalsLadder.eliteRank(p) + "</col>"
			: RivalsProfile.name(p.rivalsDivision) + "  <col=9a9a9a>Division " + p.rivalsDivision + "  -  step " + p.rivalsProgress + " / "
			+ RivalsProfile.STEPS + (p.rivalsCheckpoint > 0 ? "  (Safe)" : "") + "</col>");
		ps.sendString(INTERFACE, LINE_RECORD, String.format(Locale.ROOT,
			"<col=ffffff>%,d SR</col>   <col=40ff40>%dW</col> <col=ff6a6a>%dL</col>   Streak <col=ffffff>%d</col> (best %d)",
			p.rivalsRating, p.rivalsWins, p.rivalsLosses, p.rivalsStreak, p.rivalsBestStreak));
		ps.sendString(INTERFACE, LINE_WEEKLY, "Weekly wins <col=ffffff>" + p.rivalsWeeklyWins + "</col> / " + RivalsProfile.TIER2_WINS
			+ (RivalsProfile.claimable(p) ? "  <col=ffd56b>- chest ready to claim!</col>" : p.rivalsWeeklyClaimed ? "  <col=9ce891>- chest claimed</col>" : ""));
		ps.sendString(INTERFACE, LINE_POINTS, String.format(Locale.ROOT, "%,d Rivals Points", p.rivalsPoints));

		RivalsMode chosen = selected(p);
		boolean queued = RivalsQueueManager.isQueued(p), pending = RivalsQueueManager.awaitingAccept(p);
		for (int k = 0; k < CARD.length; k++) {
			RivalsMode mode = RivalsMode.values()[k];
			int c = CARD[k];
			boolean on = mode == chosen;
			ps.setHidden(INTERFACE, c + CARD_RECESS, !on);
			ps.setHidden(INTERFACE, c + CARD_TRIM, !on);
			ps.sendString(INTERFACE, c + CARD_NAME, (on ? "<col=ffc35a>" : "<col=c8b89a>") + mode.label + "</col>");
			ps.sendString(INTERFACE, c + CARD_STATS, mode.tagline);
			Item[] eq = mode.kit.kitEquipment();
			for (int i = 0; i < PREVIEW_SLOTS.length; i++) {
				Item item = PREVIEW_SLOTS[i] < eq.length ? eq[PREVIEW_SLOTS[i]] : null;
				int comp = c + CARD_MODELS + i;
				if (item == null) {
					ps.setHidden(INTERFACE, comp, true);
					continue;
				}
				ps.setHidden(INTERFACE, comp, false);
				ObjType def = ObjType.get(item.getId());
				ps.sendItem(INTERFACE, comp, item.getId(), 1);
				if (def != null)
					ps.sendModelInformation(INTERFACE, comp, (int) (def.zoom2d * 1.5), def.xan2d, def.yan2d); // 40 x 26 boxes
			}
			int searching = RivalsQueueManager.searching(mode);
			ps.sendString(INTERFACE, c + CARD_SEARCHING, searching == 0 ? "<col=9a9a9a>0 searching</col>"
				: searching + " searching");
		}

		String line = RivalsQueueManager.searchLine(p);
		boolean inMatch = RivalsQueueManager.inMatch(p) || RivalsSnapshot.active(p);
		ps.sendString(INTERFACE, STATUS, inMatch ? "<col=ff981f>You are in a Rivals match.</col>"
			: line != null ? line : "Select a queue, then <col=ffc35a>Find match</col>. You'll be asked to accept when an opponent is found.");
		ps.setHidden(INTERFACE, MAIN_BUTTON, pending || inMatch);
		ps.setHidden(INTERFACE, MAIN_LABEL, pending || inMatch);
		ps.sendClientScript(44, "Ii", INTERFACE << 16 | MAIN_BUTTON, queued ? SECONDARY : PRIMARY);
		ps.sendString(INTERFACE, MAIN_LABEL, queued ? "<col=ff6a6a>Cancel search</col>" : "Find match");
		for (int comp : new int[]{ACCEPT, ACCEPT + 1, DECLINE, DECLINE + 1})
			ps.setHidden(INTERFACE, comp, !pending);
	}
}
