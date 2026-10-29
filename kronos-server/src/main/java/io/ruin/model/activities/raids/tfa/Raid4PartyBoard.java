package io.ruin.model.activities.raids.tfa;

import io.ruin.model.World;
import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.InterfaceHandler;
import io.ruin.model.inter.ToplevelComponent;
import io.ruin.model.inter.actions.DefaultAction;
import io.ruin.model.stat.StatType;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The grouping board, 1:1 with the cache's own party interfaces (2026-10-29), decoded from their clientscripts (no
 * capture of the board's traffic exists, so every click is logged for checking against the real client).
 * <p>
 * Every button on both boards is a pause button: its op script (10054, 10056, 10065, 10070, 10073, 10084, 10097) finds
 * a child of a container and sends RESUME_PAUSEBUTTON with that child's index as the sub. The server enables no
 * IF_SETEVENTS on either board (op scripts work without them; enabling them made buttons fire twice).
 * <ul>
 * <li>977 tfa_partylist: onload 10087 builds the frame; 10094(row, party) fills a row (0..44, "" clears it). A party
 * is 13 "|" fields: leader, seven more member names, size, preferred size, preferred completions ("0" shows "-"),
 * mode, age in game ticks. Container 1, sub = enum 6059: 0 Refresh, 1 Make Party / My Party, 2 Filter. Container
 * 14, sub = row: View Party.</li>
 * <li>976 tfa_partydetails: onload 10049 builds the frame; 10053(role, member) adds a member row (eight are always
 * sent, "" for empty seats), 10058(applicant) an applicant, then 10060(role, preferred completions, mode, preferred
 * size, spectating, tab) the header, the action button and the tabs. A member / applicant is 11 fields: name,
 * combat, attack, strength, ranged, magic, defence, hitpoints, prayer, completions, practice. Roles: 0 may apply,
 * 1 member, 2 leader, 3 applicant, 4 can't apply. Tabs: 0 members, 1 applicants.
 * Container 1; with N = enum 6057's size (10), sub 0..N-1 = enum 6057: 0 Back, 1 Refresh, 2 Unblock, 3 Set preferred
 * size, 4 Set preferred completions, 5 mode (display only), 6 Apply / Withdraw / Leave / Disband, 7 Members tab,
 * 8 Applicants tab, 9 Enable / Disable spectating; N+row Kick / Quit Party (op 10), N+8+row Move up, N+16+row Move
 * down, N+24+row Accept, N+31+row Reject.</li>
 * </ul>
 */
@Slf4j
public final class Raid4PartyBoard {

	public static final int LIST = 977, DETAILS = 976;
	private static final int PAUSE = 1, LIST_ROWS_PAUSE = 14, LIST_ROWS = 45;
	private static final int LIST_REFRESH = 0, LIST_MY_PARTY = 1, LIST_FILTER = 2;
	private static final int BACK = 0, REFRESH = 1, UNBLOCK = 2, SET_SIZE = 3, SET_COMPLETIONS = 4, MODE = 5, ACTION = 6,
			TAB_MEMBERS_BUTTON = 7, TAB_APPLICANTS_BUTTON = 8, SPECTATING = 9;
	private static final int NAMED = 10, KICK = NAMED, MOVE_UP = NAMED + 8, MOVE_DOWN = NAMED + 16, ACCEPT = NAMED + 24,
			REJECT = NAMED + 31, END = NAMED + 38;
	private static final int SCRIPT_LIST_ROW = 10094, SCRIPT_MEMBER = 10053, SCRIPT_APPLICANT = 10058, SCRIPT_HEADER = 10060;
	private static final int ROLE_APPLY = 0, ROLE_MEMBER = 1, ROLE_LEADER = 2, ROLE_APPLICANT = 3, ROLE_BLOCKED = 4;
	private static final int TAB_MEMBERS = 0, TAB_APPLICANTS = 1, MAX_APPLICANTS = 7;

	/** Per viewer: the parties in the list's rows, the party open in the details, and the details tab. */
	private static final Map<Player, List<Raid4Party>> listed = new HashMap<>();
	private static final Map<Player, Raid4Party> viewing = new HashMap<>();
	private static final Map<Player, Integer> tabs = new HashMap<>();

	private Raid4PartyBoard() {
	}

	public static void register() {
		InterfaceHandler.register(LIST, h -> {
			h.actions[PAUSE] = (DefaultAction) (p, option, slot, item) -> listButton(p, slot);
			h.actions[LIST_ROWS_PAUSE] = (DefaultAction) (p, option, slot, item) -> {
				log.info("Raid 4 board: {} list row {}", p.getName(), slot);
				List<Raid4Party> parties = listed.get(p);
				if (parties != null && slot >= 0 && slot < parties.size())
					openDetails(p, parties.get(slot));
			};
			h.closedAction = (p, i) -> listed.remove(p);
		});
		InterfaceHandler.register(DETAILS, h -> {
			h.actions[PAUSE] = (DefaultAction) (p, option, slot, item) -> detailsButton(p, slot);
			h.closedAction = (p, i) -> {
				viewing.remove(p);
				tabs.remove(p);
			};
		});
	}

	/* ---------------------------------------------------------------- the list */

	private static void listButton(Player player, int sub) {
		log.info("Raid 4 board: {} list button {}", player.getName(), sub);
		switch (sub) {
			case LIST_REFRESH -> openList(player);
			case LIST_MY_PARTY -> {
				Raid4Party party = Raid4.partyOf(player);
				if (party == null) {
					if (Raid4.controllerOf(player) != null)
						return;
					party = Raid4.createParty(player);
				}
				openDetails(player, party);
			}
			case LIST_FILTER -> openList(player);
			default -> {
			}
		}
	}

	public static void openList(Player player) {
		player.openInterface(ToplevelComponent.MAINMODAL, LIST);
		viewing.remove(player);
		tabs.remove(player);
		var ps = player.getPacketSender();
		List<Raid4Party> parties = new ArrayList<>();
		for (Raid4Party party : Raid4.allParties())
			if (!party.hasStarted() && party.leaderPlayer() != null)
				parties.add(party);
		parties = parties.subList(0, Math.min(LIST_ROWS, parties.size()));
		listed.put(player, new ArrayList<>(parties));
		for (int row = 0; row < LIST_ROWS; row++)
			ps.sendClientScript(SCRIPT_LIST_ROW, "is", row, row < parties.size() ? listEntry(parties.get(row)) : "");
	}

	private static String listEntry(Raid4Party party) {
		StringBuilder b = new StringBuilder();
		Player leader = party.leaderPlayer();
		b.append(leader == null ? party.getLeader() : leader.getName()).append('|');
		int added = 0;
		for (String name : party.getMembers()) {
			if (name.equals(party.getLeader()))
				continue;
			Player p = World.getPlayer(name);
			b.append(p == null ? name : p.getName()).append('|');
			added++;
		}
		for (; added < 7; added++)
			b.append('|');
		long ageTicks = (System.currentTimeMillis() - party.createdAt) / 600;
		b.append(party.size()).append('|').append(party.preferredSize).append('|').append(party.preferredCompletions)
				.append('|').append("Normal").append('|').append(ageTicks);
		return b.toString();
	}

	/* ---------------------------------------------------------------- the details */

	/** The member rows in board order: the leader first. */
	private static List<String> rows(Raid4Party party) {
		List<String> members = new ArrayList<>(party.getMembers());
		members.remove(party.getLeader());
		members.add(0, party.getLeader());
		return members;
	}

	public static void openDetails(Player player, Raid4Party party) {
		// reopening closes the shown copy first, and its closedAction forgets the viewer: read the tab before
		int tab = viewing.get(player) == party ? tabs.getOrDefault(player, TAB_MEMBERS) : TAB_MEMBERS;
		player.openInterface(ToplevelComponent.MAINMODAL, DETAILS);
		viewing.put(player, party);
		tabs.put(player, tab);
		var ps = player.getPacketSender();
		int role = role(player, party);
		List<String> members = rows(party);
		for (int row = 0; row < Raid4Party.MAX_SIZE; row++)
			ps.sendClientScript(SCRIPT_MEMBER, "is", role, row < members.size() ? playerEntry(members.get(row)) : "");
		int shown = 0;
		for (String name : party.applicants)
			if (shown++ < MAX_APPLICANTS)
				ps.sendClientScript(SCRIPT_APPLICANT, "s", playerEntry(name));
		ps.sendClientScript(SCRIPT_HEADER, "iiiiii", role, party.preferredCompletions, 0, party.preferredSize,
				party.spectating ? 1 : 0, tab);
	}

	/** Reopens the details for everyone looking at this party (a join, a leave, an application, a setting). */
	private static void refreshViewers(Raid4Party party) {
		for (var e : new ArrayList<>(viewing.entrySet()))
			if (e.getValue() == party && e.getKey().isVisibleInterface(DETAILS))
				openDetails(e.getKey(), party);
	}

	private static void detailsButton(Player player, int sub) {
		Raid4Party party = viewing.get(player);
		log.info("Raid 4 board: {} details button {} ({})", player.getName(), sub, party);
		if (party == null || !Raid4.allParties().contains(party)) {
			openList(player);
			return;
		}
		boolean leader = Raid4.partyOf(player) == party && party.isLeader(player);
		if (sub >= ACCEPT && sub < END) {
			answer(player, party, sub < REJECT ? sub - ACCEPT : sub - REJECT, sub < REJECT);
			return;
		}
		if (sub >= MOVE_UP && sub < ACCEPT) {
			boolean up = sub < MOVE_DOWN;
			List<String> members = rows(party);
			int row = up ? sub - MOVE_UP : sub - MOVE_DOWN;
			if (leader && row > 0 && row < members.size())
				party.move(members.get(row), up ? -1 : 1);
			refreshViewers(party);
			return;
		}
		if (sub >= KICK && sub < MOVE_UP) {
			kick(player, party, sub - KICK);
			return;
		}
		switch (sub) {
			case BACK -> openList(player);
			case REFRESH -> openDetails(player, party);
			case UNBLOCK -> {
				if (leader)
					party.blocked.clear();
				openDetails(player, party);
			}
			case SET_SIZE -> {
				if (!leader)
					return;
				player.integerInput("Enter your preferred party size (1-" + Raid4Party.MAX_SIZE + "):", value -> {
					party.preferredSize = Math.max(1, Math.min(Raid4Party.MAX_SIZE, value));
					refreshViewers(party);
				});
			}
			case SET_COMPLETIONS -> {
				if (!leader)
					return;
				player.integerInput("Enter the preferred number of completions:", value -> {
					party.preferredCompletions = Math.max(0, value);
					refreshViewers(party);
				});
			}
			case SPECTATING -> {
				if (leader)
					party.spectating = !party.spectating;
				refreshViewers(party);
			}
			case ACTION -> action(player, party);
			case TAB_MEMBERS_BUTTON -> showTab(player, party, TAB_MEMBERS);
			case TAB_APPLICANTS_BUTTON -> showTab(player, party, TAB_APPLICANTS);
			default -> openDetails(player, party);
		}
	}

	private static void showTab(Player player, Raid4Party party, int tab) {
		tabs.put(player, tab);
		openDetails(player, party);
	}

	private static String playerEntry(String name) {
		Player p = World.getPlayer(name);
		if (p == null)
			return name + "|-|-|-|-|-|-|-|-|-|-";
		var st = p.getStats();
		return p.getName() + '|' + p.getCombat().getLevel() + '|' + st.get(StatType.Attack).fixedLevel + '|'
				+ st.get(StatType.Strength).fixedLevel + '|' + st.get(StatType.Ranged).fixedLevel + '|'
				+ st.get(StatType.Magic).fixedLevel + '|' + st.get(StatType.Defence).fixedLevel + '|'
				+ st.get(StatType.Hitpoints).fixedLevel + '|' + st.get(StatType.Prayer).fixedLevel + '|'
				+ p.fracturedArchiveKills.getKills() + "|0";
	}

	private static int role(Player player, Raid4Party party) {
		Raid4Party own = Raid4.partyOf(player);
		if (own == party)
			return party.isLeader(player) ? ROLE_LEADER : ROLE_MEMBER;
		if (party.applicants.contains(Raid4Party.key(player)))
			return ROLE_APPLICANT;
		if (own != null || party.isFull() || party.hasStarted() || party.blocked.contains(Raid4Party.key(player))
				|| party.applicants.size() >= MAX_APPLICANTS)
			return ROLE_BLOCKED;
		return ROLE_APPLY;
	}

	/** The action button: Apply / Withdraw / Leave / Disband, by the viewer's role. */
	private static void action(Player player, Raid4Party party) {
		switch (role(player, party)) {
			case ROLE_APPLY -> {
				party.applicants.add(Raid4Party.key(player));
				Player leader = party.leaderPlayer();
				if (leader != null)
					leader.sendMessage(player.getName() + " has applied to join your Archive party.");
			}
			case ROLE_APPLICANT -> party.applicants.remove(Raid4Party.key(player));
			case ROLE_MEMBER -> Raid4.leaveParty(player);
			case ROLE_LEADER -> {
				for (Player p : party.online())
					if (p != player)
						Raid4.leaveParty(p);
				Raid4.leaveParty(player);
				party.applicants.clear();
				openList(player);
				return;
			}
			default -> {
				openDetails(player, party);
				return;
			}
		}
		refreshViewers(party);
		if (!player.isVisibleInterface(DETAILS))
			openDetails(player, party);
	}

	/** Kick (the leader, on another member's row) or Quit Party (anyone, on their own). */
	private static void kick(Player player, Raid4Party party, int row) {
		if (Raid4.partyOf(player) != party)
			return;
		List<String> members = rows(party);
		if (row < 0 || row >= members.size())
			return;
		String target = members.get(row);
		if (target.equals(Raid4Party.key(player))) {
			Raid4.leaveParty(player);
		} else if (party.isLeader(player)) {
			Player p = World.getPlayer(target);
			if (p != null) {
				Raid4.leaveParty(p);
				p.sendMessage("You have been removed from the party.");
			}
		}
		refreshViewers(party);
		if (Raid4.allParties().contains(party))
			openDetails(player, party);
		else
			openList(player);
	}

	/** The leader's Accept / Reject on an applicant (a rejected player is blocked until Unblock). */
	private static void answer(Player player, Raid4Party party, int row, boolean accept) {
		if (!party.isLeader(player) || Raid4.partyOf(player) != party)
			return;
		List<String> applicants = new ArrayList<>(party.applicants);
		if (row < 0 || row >= applicants.size())
			return;
		String name = applicants.get(row);
		party.applicants.remove(name);
		Player p = World.getPlayer(name);
		if (accept && p != null && Raid4.partyOf(p) == null && !party.isFull()) {
			party.invite(p);
			Raid4.join(p, player);
		} else if (accept) {
			if (p != null)
				p.sendMessage("You couldn't join that party.");
		} else {
			party.blocked.add(name);
			if (p != null)
				p.sendMessage("Your application was declined.");
		}
		refreshViewers(party);
	}
}
