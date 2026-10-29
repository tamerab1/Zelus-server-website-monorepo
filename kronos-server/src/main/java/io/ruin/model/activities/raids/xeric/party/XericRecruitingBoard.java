package io.ruin.model.activities.raids.xeric.party;

import io.ruin.Server;
import io.ruin.model.World;
import io.ruin.model.activities.raids.chambersrework.CustomXericRaid;
import io.ruin.model.activities.raids.chambersrework.XericParty;
import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.InterfaceHandler;
import io.ruin.model.inter.ToplevelComponent;
import io.ruin.model.inter.actions.DefaultAction;
import io.ruin.model.map.object.actions.ObjectAction;
import io.ruin.model.stat.StatType;
import io.ruin.model.var.VarPlayerRepository;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.ruin.model.stat.StatType.CLIENT_ORDER;

/**
 * The Chambers of Xeric recruiting board (object 29776, 2026-10-29) on the party the entrance and the Invite option use
 * ({@code player.party}), decoded from the rev 241 cache's own scripts.
 * <p>
 * Every button is a pause button: its op script (1568 on the board, 1534 on a party) sends RESUME_PAUSEBUTTON on
 * a container with the button's index as the sub.
 * <ul>
 * <li>499 raids board: onload 1563; 1566(row, party) fills a row of 40 ("" clears it): name|size|preferred size|
 * preferred combat|preferred skill total|age in ticks|. Container 3, sub = enum 1538: 0 Refresh, 1 Make party /
 * My party. The rows answer on their own container with sub = row.</li>
 * <li>507 raiding party: onload 1516; 1517(row, member) a member: name|combat|total level|then each stat in client
 * order|; 1524(title, role 0 none / 1 member / 2 leader, advertised, adverts left) the header. The preferences are
 * read from varps 1432 / 5426 / 5427. Container 3, sub = enum 1539: 0 Join / Leave, 1 Advertise, 2 Disband,
 * 3 Refresh, 4 Back, 5 preferred party size, 6 preferred combat level, 7 preferred skill total, 8 Scaling,
 * 9 Challenge mode, 10 Map Layout.</li>
 * </ul>
 */
@Slf4j
public final class XericRecruitingBoard {

	public static final int BOARD_OBJECT = 29776, BOARD = 499, PARTY = 507;
	private static final int BUTTONS = 3, ROWS = 40, MAX_MEMBERS = 100, ADVERTS = 99;
	private static final int B_REFRESH = 0, B_MY_PARTY = 1;
	private static final int P_JOIN_LEAVE = 0, P_ADVERTISE = 1, P_DISBAND = 2, P_REFRESH = 3, P_BACK = 4, P_SIZE = 5,
			P_COMBAT = 6, P_SKILL = 7, P_SCALING = 8, P_CHALLENGE = 9, P_LAYOUT = 10;
	private static final int SCRIPT_ROW = 1566, SCRIPT_MEMBER = 1517, SCRIPT_HEADER = 1524;

	/** Advertised parties, oldest first; per viewer, the board's rows and the party open. */
	private static final List<XericParty> advertised = new ArrayList<>();
	private static final Map<Player, List<XericParty>> listed = new HashMap<>();
	private static final Map<Player, XericParty> viewing = new HashMap<>();

	private XericRecruitingBoard() {
	}

	public static void register() {
		ObjectAction.register(BOARD_OBJECT, 1, (player, obj) -> openBoard(player));
		InterfaceHandler.register(BOARD, h -> {
			h.actions[BUTTONS] = (DefaultAction) (p, option, slot, item) -> boardButton(p, slot);
			for (int c = 4; c < 60; c++) {
				int child = c;
				if (child == BUTTONS)
					continue;
				h.actions[child] = (DefaultAction) (p, option, slot, item) -> boardRow(p, child, slot);
			}
			h.closedAction = (p, i) -> listed.remove(p);
		});
		InterfaceHandler.register(PARTY, h -> {
			h.actions[BUTTONS] = (DefaultAction) (p, option, slot, item) -> partyButton(p, slot);
			h.closedAction = (p, i) -> viewing.remove(p);
		});
	}

	/* ---------------------------------------------------------------- the board */

	public static void openBoard(Player player) {
		player.openInterface(ToplevelComponent.MAINMODAL, BOARD);
		viewing.remove(player);
		advertised.removeIf(party -> !party.advertised || leader(party) == null || party.isStarted());
		List<XericParty> rows = new ArrayList<>(advertised.subList(0, Math.min(ROWS, advertised.size())));
		listed.put(player, rows);
		var ps = player.getPacketSender();
		for (int i = 0; i < ROWS; i++)
			ps.sendClientScript(SCRIPT_ROW, "is", i, i < rows.size() ? row(rows.get(i)) : "");
	}

	private static String row(XericParty party) {
		Player leader = leader(party);
		return "<col=ffffff>" + (leader == null ? party.getLeader() : leader.getName()) + "</col>|" + party.getMembers().size()
				+ '|' + party.preferredSize + '|' + party.preferredCombat + '|' + party.preferredSkillTotal + '|'
				+ (Server.currentTick() - party.advertisedAt) + '|';
	}

	private static void boardButton(Player player, int sub) {
		log.info("CoX board: {} board button {}", player.getName(), sub);
		if (sub == B_REFRESH) {
			openBoard(player);
		} else if (sub == B_MY_PARTY) {
			if (player.party == null) {
				player.party = new XericParty(player.getName());
				player.setCustomXericRaid(new CustomXericRaid());
				VarPlayerRepository.RAIDS_PARTY.set(player, player.getIndex());
				player.sendMessage("You have created a party.");
			}
			openParty(player, player.party);
		}
	}

	private static void boardRow(Player player, int child, int row) {
		log.info("CoX board: {} row {} (child {})", player.getName(), row, child);
		List<XericParty> rows = listed.get(player);
		if (rows != null && row >= 0 && row < rows.size())
			openParty(player, rows.get(row));
	}

	/* ---------------------------------------------------------------- a party */

	private static Player leader(XericParty party) {
		return World.getPlayer(party.getLeader());
	}

	private static boolean isLeader(Player player, XericParty party) {
		return player.party == party && party.getLeader().equalsIgnoreCase(player.getName());
	}

	public static void openParty(Player player, XericParty party) {
		player.openInterface(ToplevelComponent.MAINMODAL, PARTY);
		viewing.put(player, party);
		VarPlayerRepository.RAIDS_PREFERRED_PARTY_SIZE.set(player, party.preferredSize);
		VarPlayerRepository.RAIDS_PREFERRED_COMBAT_LEVEL.set(player, party.preferredCombat);
		VarPlayerRepository.RAIDS_PREFERRED_SKILL_TOTAL.set(player, party.preferredSkillTotal);
		var ps = player.getPacketSender();
		List<String> order = new ArrayList<>(party.getMembers());
		order.removeIf(n -> n.equalsIgnoreCase(party.getLeader()));
		order.add(0, party.getLeader());
		for (int i = 0; i < order.size(); i++) {
			Player m = World.getPlayer(order.get(i));
			if (m == null)
				continue;
			StringBuilder b = new StringBuilder();
			b.append(i == 0 ? "<col=ffffff>" : "<col=9f9f9f>").append(m.getName()).append("</col>|");
			b.append(m.getCombat().getLevel()).append('|').append(m.getStats().totalLevel).append('|');
			for (StatType stat : CLIENT_ORDER)
				b.append(m.getStats().get(stat).fixedLevel).append('|');
			ps.sendClientScript(SCRIPT_MEMBER, "is", i, b.toString());
		}
		Player leader = leader(party);
		int role = isLeader(player, party) ? 2 : player.party == party ? 1 : 0;
		ps.sendClientScript(SCRIPT_HEADER, "siii",
				"Raiding Party of " + (leader == null ? party.getLeader() : leader.getName()) + " (" + party.getMembers().size() + ")",
				role, party.advertised ? 1 : 0, ADVERTS);
	}

	private static void refreshViewers(XericParty party) {
		for (var e : new ArrayList<>(viewing.entrySet()))
			if (e.getValue() == party && e.getKey().isVisibleInterface(PARTY))
				openParty(e.getKey(), party);
	}

	private static void partyButton(Player player, int sub) {
		XericParty party = viewing.get(player);
		log.info("CoX board: {} party button {} ({})", player.getName(), sub, party == null ? null : party.getLeader());
		if (party == null) {
			openBoard(player);
			return;
		}
		boolean leader = isLeader(player, party);
		switch (sub) {
			case P_JOIN_LEAVE -> {
				if (player.party == party)
					leave(player, party);
				else
					join(player, party);
			}
			case P_ADVERTISE -> {
				if (!leader)
					return;
				party.advertised = !party.advertised;
				if (party.advertised) {
					party.advertisedAt = Server.currentTick();
					if (!advertised.contains(party))
						advertised.add(party);
				} else
					advertised.remove(party);
				refreshViewers(party);
			}
			case P_DISBAND -> {
				if (leader)
					disband(party);
				openBoard(player);
			}
			case P_REFRESH -> openParty(player, party);
			case P_BACK -> openBoard(player);
			case P_SIZE -> preference(player, party, "party size", 1, MAX_MEMBERS, v -> party.preferredSize = v);
			case P_COMBAT -> preference(player, party, "combat level", 3, 126, v -> party.preferredCombat = v);
			case P_SKILL -> preference(player, party, "skill total", 32, 2376, v -> party.preferredSkillTotal = v);
			case P_SCALING, P_CHALLENGE, P_LAYOUT -> {
				player.sendMessage(sub == P_CHALLENGE ? "Challenge mode is not currently available."
						: sub == P_LAYOUT ? "Map layouts are not currently available." : "Raid scaling is set automatically by your party size.");
				openParty(player, party);
			}
			default -> openParty(player, party);
		}
	}

	private static void preference(Player player, XericParty party, String name, int min, int max, java.util.function.IntConsumer set) {
		if (!isLeader(player, party))
			return;
		player.integerInput("Set a preferred " + name + " (or 0 to clear it):", value -> {
			set.accept(value < min ? 0 : Math.min(max, value));
			openParty(player, party);
			refreshViewers(party);
		});
	}

	private static void join(Player player, XericParty party) {
		if (player.party != null) {
			player.sendMessage("You are already in a party.");
			return;
		}
		if (party.isStarted() || leader(party) == null) {
			player.sendMessage("No-one may join the party after the raid begins.");
			openBoard(player);
			return;
		}
		if (party.getMembers().size() >= MAX_MEMBERS) {
			player.sendMessage("This party is full.");
			return;
		}
		party.getMembers().add(player.getName());
		player.party = party;
		VarPlayerRepository.RAIDS_PARTY.set(player, leader(party).getIndex());
		player.sendMessage("You've joined " + leader(party).getName() + "'s raid party.");
		for (String name : party.getMembers()) {
			Player m = World.getPlayer(name);
			if (m != null && m != player)
				m.sendMessage(player.getName() + " has joined your raid party.");
		}
		refreshViewers(party);
		if (!player.isVisibleInterface(PARTY))
			openParty(player, party);
	}

	private static void leave(Player player, XericParty party) {
		if (isLeader(player, party)) {
			disband(party);
			openBoard(player);
			return;
		}
		party.removeMember(player.getName());
		player.party = null;
		VarPlayerRepository.RAIDS_PARTY.set(player, -1);
		player.sendMessage("You leave " + party.getLeader() + "'s party.");
		refreshViewers(party);
		openBoard(player);
	}

	/** Ends a party: every member leaves and it comes off the board. */
	public static void disband(XericParty party) {
		advertised.remove(party);
		party.advertised = false;
		for (String name : new ArrayList<>(party.getMembers())) {
			Player m = World.getPlayer(name);
			if (m != null && m.party == party) {
				m.party = null;
				VarPlayerRepository.RAIDS_PARTY.set(m, -1);
				m.sendMessage("Your party has been disbanded.");
				if (m.isVisibleInterface(PARTY))
					openBoard(m);
			}
		}
		party.getMembers().clear();
	}
}
