package io.ruin.model.activities.fiveseals;

import io.ruin.cache.Color;
import io.ruin.model.World;
import io.ruin.model.entity.npc.NPC;
import io.ruin.model.entity.npc.NPCAction;
import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.InterfaceHandler;
import io.ruin.model.inter.ToplevelComponent;
import io.ruin.model.inter.actions.SimpleAction;
import io.ruin.model.map.Direction;
import io.ruin.model.map.Position;
import io.ruin.model.map.object.GameObject;
import io.ruin.model.map.object.actions.ObjectAction;
import io.ruin.model.shop.ShopManager;
import io.ruin.network.PacketSender;
import lombok.extern.slf4j.Slf4j;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

/**
 * The Five Seals lobby: the roped hall at home (user 2026-11-16; entrance gap in its west wall at 3112,3500, between
 * the Karn banners at 3111,3496 / 3111,3503). Read from the home map (region 12342): walls x 3112-3119 / y 3493-3506,
 * the gold carpet x 3113-3117 / y 3497-3502, the reception desk (GE booth locs) along x 3117-3118 / y 3497-3502, roped
 * wings at the south (y 3494-3496) and north (y 3503-3505) ends.
 * <ul>
 * <li>Keeper of the Seals (npc 30003, cache stage 50; "Minigame Manager" until 2026-11-17) behind the desk at 3118,3499 facing the entrance (west):
 * Start-Solo / Party-Lobby / Reward-Shop (the Hall of Seals is read at its board).</li>
 * <li>South wing: a bank chest (4483) and the Karn pool of Rejuvenation (29241). North wing: the Hall of Seals
 * board (scoreboard 55198, Read).</li>
 * <li>Runs end - exit, cash-out, wipe, death - on the carpet ({@link #CARPET}).</li>
 * </ul>
 * Windows: the Party Board 1190 and the Hall of Seals 1191 (BuildKarnHub.fiveSealsParty / fiveSealsHall, stage 42).
 */
@Slf4j
public final class FiveSealsLobby {

	private FiveSealsLobby() {
	}

	public static final Position CARPET = new Position(3115, 3500, 0);
	static final int CARPET_X0 = 3113, CARPET_Y0 = 3497, CARPET_X1 = 3117, CARPET_Y1 = 3502;
	static final int MANAGER = 30003, BANK_CHEST = 4483, POOL = 29241, BOARD = 63318; // the Hall of Seals board (cache stage 54)
	static final String SHOP_UUID = "f1ve5ea1-5t0k-en5h-0p00-karn20261116";

	/* Party Board 1190 */
	static final int PARTY = 1190, P_CLOSE = 5, P_FIRST_MEMBER = 9, P_MEMBER_STRIDE = 5, P_INFO = 34, P_INVITE = 35, P_LEAVE = 37,
		P_START = 39;
	/* Hall of Seals 1191 */
	static final int HALL = 1191, H_CLOSE = 5, H_FIRST_SOLO = 9, H_FIRST_GROUP = 24, H_ROW_STRIDE = 3, H_RESET = 39, H_BEST = 40;

	static boolean onCarpet(Player p) {
		int x = p.getAbsX(), y = p.getAbsY();
		return p.getHeight() == 0 && x >= CARPET_X0 && x <= CARPET_X1 && y >= CARPET_Y0 && y <= CARPET_Y1;
	}

	static void register() {
		// NPC options run inside the "walk to the npc" route, which stays set on the player (the Manager has combat
		// stats, so it even runs the option at once): clear it and start next tick, or the route keeps dragging the
		// player toward the lobby npc from inside the instance (stuck, run never ticks)
		NPCAction.register(MANAGER, "Start-Solo", (p, n) -> {
			io.ruin.model.map.route.routes.TargetRoute.reset(p);
			confirmSolo(p);
		});
		NPCAction.register(MANAGER, "Party-Lobby", (p, n) -> openParty(p));
		NPCAction.register(MANAGER, "Reward-Shop", (p, n) -> openShop(p));
		InterfaceHandler.register(PARTY, h -> {
			h.actions[P_CLOSE] = (SimpleAction) Player::closeInterfaces;
			h.actions[P_INVITE] = (SimpleAction) p -> p.nameInput("Invite who to your Five Seals party?", name -> {
				FiveSeals.invite(p, name);
				refreshParty(p);
			});
			h.actions[P_LEAVE] = (SimpleAction) p -> {
				if (FiveSeals.partyOf(p) == null)
					FiveSeals.createParty(p);
				else
					FiveSeals.leaveParty(p);
				refreshParty(p);
			};
			h.actions[P_START] = (SimpleAction) p -> {
				if (FiveSeals.partyOf(p) == null)
					confirmSolo(p);
				else
					FiveSeals.startFromBoard(p);
			};
			for (int r = 1; r < 5; r++) {
				int slot = r;
				h.actions[P_FIRST_MEMBER + r * P_MEMBER_STRIDE + 3] = (SimpleAction) p -> {
					FiveSeals.kick(p, slot);
					refreshParty(p);
				};
			}
		});
		InterfaceHandler.register(HALL, h -> h.actions[H_CLOSE] = (SimpleAction) Player::closeInterfaces);
		ObjectAction.register(BOARD, "View", (p, obj) -> openHall(p));
		// the lobby's furniture, once the home map is in
		World.startEvent(e -> {
			e.delay(5);
			try {
				new NPC(MANAGER).spawn(3118, 3499, 0, Direction.WEST, 0);
				GameObject.spawn(BANK_CHEST, 3116, 3494, 0, 10, 0); // user 2026-11-17: moved from 3116,3496 and turned 180
				GameObject.spawn(POOL, 3113, 3494, 0, 10, 0);
				GameObject.spawn(BOARD, 3114, 3504, 0, 10, 1); // 2x3, faces south over the carpet
			} catch (Exception ex) {
				log.error("Unable to furnish the Five Seals lobby", ex);
			}
		});
	}

	/** "are you sure?" before a solo run teleports the player away */
	static void confirmSolo(Player p) {
		p.dialogue(new io.ruin.model.inter.dialogue.OptionsDialogue("Start a solo run of The Five Seals?",
			new io.ruin.model.inter.utils.Option("Yes, enter the seals alone.", () -> {
				io.ruin.model.map.route.routes.TargetRoute.reset(p);
				p.closeInterfaces();
				World.startEvent(e -> {
					e.delay(1); // out of the dialogue / npc route first
					io.ruin.model.map.route.routes.TargetRoute.reset(p);
					if (p.isOnline())
						FiveSeals.startSolo(p);
				});
			}),
			new io.ruin.model.inter.utils.Option("No, not yet.")));
	}

	static void openShop(Player p) {
		FiveSealsShop.open(p); // the Karn Token Shop window 1192 (the old OSRS shop yaml is gone)
	}

	/* ------------------------------------------------------------------ the Party Board */

	static void openParty(Player p) {
		p.openInterface(ToplevelComponent.MAINMODAL, PARTY);
		refreshParty(p);
	}

	/** refreshes the board for everyone in the party who has it open */
	static void refreshParty(Player p) {
		List<Player> party = FiveSeals.partyOf(p);
		if (party == null) {
			send(p, List.of(p), true);
			return;
		}
		for (Player m : party)
			if (m.isVisibleInterface(PARTY))
				send(m, party, false);
		if (!party.contains(p) && p.isVisibleInterface(PARTY))
			send(p, List.of(p), true);
	}

	private static void send(Player viewer, List<Player> party, boolean alone) {
		if (!viewer.isVisibleInterface(PARTY))
			return;
		PacketSender ps = viewer.getPacketSender();
		boolean leader = party.get(0) == viewer;
		for (int r = 0; r < 5; r++) {
			int base = P_FIRST_MEMBER + r * P_MEMBER_STRIDE;
			Player m = r < party.size() ? party.get(r) : null;
			ps.sendString(PARTY, base + 1, m == null ? "" : m.getName() + (r == 0 ? (alone ? "" : " (leader)") : ""));
			ps.sendString(PARTY, base + 2, m == null ? "" : "Combat " + m.getCombat().getLevel() + " - "
				+ (!m.isOnline() ? "offline" : onCarpet(m) ? Color.DARK_GREEN.wrap("on the carpet") : "away"));
			boolean kick = m != null && r > 0 && leader && !alone;
			ps.setHidden(PARTY, base + 3, !kick);
			ps.setHidden(PARTY, base + 4, !kick);
		}
		int n = party.size();
		ps.sendString(PARTY, P_LEAVE + 1, alone ? "Create Party" : "Leave party");
		ps.sendString(PARTY, P_START + 1, alone ? "Start Solo" : leader ? "Start Run" : "Leader starts");
		ps.sendString(PARTY, P_INFO, "Party size: " + n + " / " + FiveSeals.MAX_PARTY + "<br><br>"
			+ "Scaling for " + (n == 1 ? "solo" : n + " players") + ":<br>"
			+ "- Seal 1 needs " + 100 * n + " energy" + (n == 1 ? " (2x gathering)" : "") + "<br>"
			+ "- " + Math.min(12, 2 + 2 * n) + " enemies a wave<br>"
			+ "- Bosses x" + String.format("%.2f", 1 + 0.85 * (n - 1)) + " HP<br>"
			+ "- Conduit window " + (n == 1 ? 25 : 10) + "s<br>"
			+ "- " + (1 + n / 2) + " shadow stalker" + (1 + n / 2 == 1 ? "" : "s") + "<br>"
			+ (n > 1 ? "- +10% rare drops<br>" : "")
			+ "<br>" + (alone ? "Create a party (or just invite someone), or start solo." : leader ? "You lead: start when ready." : "Only the leader starts."));
	}

	/* ------------------------------------------------------------------ the Hall of Seals */

	static void openHall(Player p) {
		p.openInterface(ToplevelComponent.MAINMODAL, HALL);
		PacketSender ps = p.getPacketSender();
		FiveSeals.Board board = FiveSeals.board();
		for (int g = 0; g < 2; g++) {
			List<FiveSeals.Entry> list = g == 0 ? board.solo : board.group;
			int first = g == 0 ? H_FIRST_SOLO : H_FIRST_GROUP;
			for (int r = 0; r < 5; r++) {
				FiveSeals.Entry e = r < list.size() ? list.get(r) : null;
				ps.sendString(HALL, first + r * H_ROW_STRIDE + 1, e == null ? (r == 0 ? "No clears yet this week" : "") : FiveSeals.time(e.ticks));
				ps.sendString(HALL, first + r * H_ROW_STRIDE + 2, e == null ? "" : e.names);
			}
		}
		ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
		ZonedDateTime reset = now.toLocalDate().with(TemporalAdjusters.next(DayOfWeek.MONDAY)).atStartOfDay(ZoneOffset.UTC);
		Duration left = Duration.between(now, reset);
		ps.sendString(HALL, H_RESET, "Weekly reset in: " + left.toDays() + "d " + left.toHoursPart() + "h");
		FiveSeals.Entry best = null;
		for (List<FiveSeals.Entry> list : List.of(board.solo, board.group))
			for (FiveSeals.Entry e : list)
				if (e.names.contains(p.getName()) && (best == null || e.ticks < best.ticks))
					best = e;
		ps.sendString(HALL, H_BEST, best == null ? "" : "Your best: " + FiveSeals.time(best.ticks));
	}
}
