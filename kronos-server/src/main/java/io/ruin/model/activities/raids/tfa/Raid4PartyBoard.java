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
 * capture of the board's traffic exists, so every click is logged for checking against the real client):
 * <ul>
 * <li>977 tfa_partylist: its onload (10087) builds the frame; 10094(row, party) fills a row (0..44 = components
 * 15..59, "" clears it). A party is 13 "|" fields: leader, seven more member names, size, preferred size, preferred
 * completions ("0" shows "-"), mode, age in game ticks. "View Party" on a row comes back as that row's component;
 * Refresh 61, Make Party / My Party 62.</li>
 * <li>976 tfa_partydetails: its onload (10049) builds the frame; 10053(role, member) adds a member row (eight are
 * always sent, "" for empty seats: applicants are numbered from row 8), 10058(applicant) an applicant, 10060(role,
 * preferred completions, mode, preferred size, spectating, tab) the header and the action button. A member / applicant
 * is 11 fields: name, combat, attack, strength, ranged, magic, defence, hitpoints, prayer, completions, practice.
 * Roles: 0 may apply, 1 member (Leave), 2 leader (Disband; Kick = op 10 on child 14 * row of component 35), 3
 * applicant (Withdraw), 4 can't apply. Applicants: Accept op 1 / Reject op 10 on child 22 * row of component 52.
 * Back 4, Refresh 55, the action button 56.</li>
 * </ul>
 */
@Slf4j
public final class Raid4PartyBoard {

	public static final int LIST = 977, DETAILS = 976;
	private static final int LIST_FIRST_ROW = 15, LIST_ROWS = 45, LIST_REFRESH = 61, LIST_MY_PARTY = 62, LIST_FILTER = 63;
	private static final int DETAILS_BACK = 4, DETAILS_MEMBERS = 35, DETAILS_APPLICANTS = 52, DETAILS_REFRESH = 55,
			DETAILS_ACTION = 56;
	private static final int SCRIPT_LIST_ROW = 10094, SCRIPT_MEMBER = 10053, SCRIPT_APPLICANT = 10058, SCRIPT_HEADER = 10060;
	private static final int ROLE_APPLY = 0, ROLE_MEMBER = 1, ROLE_LEADER = 2, ROLE_APPLICANT = 3, ROLE_BLOCKED = 4;
	private static final int MEMBER_ROW_CHILDREN = 14, APPLICANT_ROW_CHILDREN = 22, OPS = 0x7FE;

	/** Per viewer: the parties in the list's rows, and the party open in the details. */
	private static final Map<Player, List<Raid4Party>> listed = new HashMap<>();
	private static final Map<Player, Raid4Party> viewing = new HashMap<>();

	private Raid4PartyBoard() {
	}

	public static void register() {
		InterfaceHandler.register(LIST, h -> {
			for (int row = 0; row < LIST_ROWS; row++) {
				int r = row;
				h.actions[LIST_FIRST_ROW + row] = (DefaultAction) (p, option, slot, item) -> {
					log.info("Raid 4 board: {} list row {} op {} slot {}", p.getName(), r, option, slot);
					List<Raid4Party> parties = listed.get(p);
					if (parties != null && r < parties.size())
						openDetails(p, parties.get(r));
				};
			}
			h.actions[LIST_REFRESH] = (DefaultAction) (p, option, slot, item) -> openList(p);
			h.actions[LIST_MY_PARTY] = (DefaultAction) (p, option, slot, item) -> {
				log.info("Raid 4 board: {} my party op {}", p.getName(), option);
				Raid4Party party = Raid4.partyOf(p);
				if (party == null) {
					if (Raid4.controllerOf(p) != null)
						return;
					party = Raid4.createParty(p);
				}
				openDetails(p, party);
			};
			h.actions[LIST_FILTER] = (DefaultAction) (p, option, slot, item) -> log.info("Raid 4 board: {} filter op {}", p.getName(), option);
			h.closedAction = (p, i) -> listed.remove(p);
		});
		InterfaceHandler.register(DETAILS, h -> {
			h.actions[DETAILS_BACK] = (DefaultAction) (p, option, slot, item) -> openList(p);
			h.actions[DETAILS_REFRESH] = (DefaultAction) (p, option, slot, item) -> refresh(p);
			h.actions[DETAILS_ACTION] = (DefaultAction) (p, option, slot, item) -> {
				log.info("Raid 4 board: {} action op {}", p.getName(), option);
				action(p);
			};
			h.actions[DETAILS_MEMBERS] = (DefaultAction) (p, option, slot, item) -> {
				log.info("Raid 4 board: {} member child {} op {}", p.getName(), slot, option);
				kick(p, slot / MEMBER_ROW_CHILDREN);
			};
			h.actions[DETAILS_APPLICANTS] = (DefaultAction) (p, option, slot, item) -> {
				log.info("Raid 4 board: {} applicant child {} op {}", p.getName(), slot, option);
				answer(p, slot / APPLICANT_ROW_CHILDREN, option == 1);
			};
			h.closedAction = (p, i) -> viewing.remove(p);
		});
	}

	/* ---------------------------------------------------------------- the list */

	public static void openList(Player player) {
		viewing.remove(player);
		player.openInterface(ToplevelComponent.MAINMODAL, LIST);
		var ps = player.getPacketSender();
		List<Raid4Party> parties = new ArrayList<>();
		for (Raid4Party party : Raid4.allParties())
			if (!party.hasStarted() && party.leaderPlayer() != null)
				parties.add(party);
		parties = parties.subList(0, Math.min(LIST_ROWS, parties.size()));
		listed.put(player, new ArrayList<>(parties));
		for (int row = 0; row < LIST_ROWS; row++)
			ps.sendClientScript(SCRIPT_LIST_ROW, "is", row, row < parties.size() ? listEntry(parties.get(row)) : "");
		for (int row = 0; row < LIST_ROWS; row++)
			ps.sendIfEvents(LIST, LIST_FIRST_ROW + row, -1, -1, OPS);
		for (int c : new int[] { LIST_REFRESH, LIST_MY_PARTY, LIST_FILTER })
			ps.sendIfEvents(LIST, c, -1, -1, OPS);
	}

	private static String listEntry(Raid4Party party) {
		StringBuilder b = new StringBuilder();
		List<String> names = party.getMembers();
		Player leader = party.leaderPlayer();
		b.append(leader == null ? party.getLeader() : leader.getName()).append('|');
		int added = 0;
		for (String name : names) {
			if (name.equals(party.getLeader()))
				continue;
			Player p = World.getPlayer(name);
			b.append(p == null ? name : p.getName()).append('|');
			added++;
		}
		for (; added < 7; added++)
			b.append('|');
		long ageTicks = (System.currentTimeMillis() - party.createdAt) / 600;
		b.append(party.size()).append('|').append(Raid4Party.MAX_SIZE).append('|').append(0).append('|').append("Normal").append('|')
				.append(ageTicks);
		return b.toString();
	}

	/* ---------------------------------------------------------------- the details */

	public static void openDetails(Player player, Raid4Party party) {
		viewing.put(player, party);
		player.openInterface(ToplevelComponent.MAINMODAL, DETAILS);
		var ps = player.getPacketSender();
		int role = role(player, party);
		List<String> members = new ArrayList<>(party.getMembers());
		members.remove(party.getLeader());
		members.add(0, party.getLeader());
		for (int row = 0; row < Raid4Party.MAX_SIZE; row++)
			ps.sendClientScript(SCRIPT_MEMBER, "is", role, row < members.size() ? playerEntry(members.get(row)) : "");
		for (String name : party.applicants)
			ps.sendClientScript(SCRIPT_APPLICANT, "s", playerEntry(name));
		ps.sendClientScript(SCRIPT_HEADER, "iiiiii", role, 0, 0, Raid4Party.MAX_SIZE, 0, 0);
		ps.sendIfEvents(DETAILS, DETAILS_MEMBERS, 0, MEMBER_ROW_CHILDREN * Raid4Party.MAX_SIZE, OPS);
		ps.sendIfEvents(DETAILS, DETAILS_APPLICANTS, 0, APPLICANT_ROW_CHILDREN * 32, OPS);
		for (int c : new int[] { DETAILS_BACK, DETAILS_REFRESH, DETAILS_ACTION })
			ps.sendIfEvents(DETAILS, c, -1, -1, OPS);
	}

	private static void refresh(Player player) {
		Raid4Party party = viewing.get(player);
		if (party != null && Raid4.allParties().contains(party))
			openDetails(player, party);
		else
			openList(player);
	}

	/** Refreshes the board for everyone looking at this party (a join, a leave, an application). */
	private static void refreshViewers(Raid4Party party) {
		for (var e : new ArrayList<>(viewing.entrySet()))
			if (e.getValue() == party && e.getKey().isVisibleInterface(DETAILS))
				openDetails(e.getKey(), party);
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
		if (own != null || party.isFull() || party.hasStarted())
			return ROLE_BLOCKED;
		return ROLE_APPLY;
	}

	/** The action button: Apply / Withdraw / Leave / Disband, by the viewer's role. */
	private static void action(Player player) {
		Raid4Party party = viewing.get(player);
		if (party == null)
			return;
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
				player.sendMessage("You can't apply to this party.");
				return;
			}
		}
		refreshViewers(party);
		if (!player.isVisibleInterface(DETAILS))
			openDetails(player, party);
	}

	/** Kick (the leader, on another member's row) or Quit Party (anyone, on their own). */
	private static void kick(Player player, int row) {
		Raid4Party party = viewing.get(player);
		if (party == null || Raid4.partyOf(player) != party)
			return;
		List<String> members = new ArrayList<>(party.getMembers());
		members.remove(party.getLeader());
		members.add(0, party.getLeader());
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
		openDetails(player, party);
	}

	/** The leader's Accept / Reject on an applicant. */
	private static void answer(Player player, int row, boolean accept) {
		Raid4Party party = viewing.get(player);
		if (party == null || !party.isLeader(player))
			return;
		List<String> applicants = new ArrayList<>(party.applicants);
		if (row < 0 || row >= applicants.size())
			return;
		String name = applicants.get(row);
		party.applicants.remove(name);
		Player p = World.getPlayer(name);
		if (accept && p != null && Raid4.partyOf(p) == null) {
			party.invite(p);
			Raid4.join(p, player);
		} else if (p != null)
			p.sendMessage(accept ? "You couldn't join that party." : "Your application was declined.");
		refreshViewers(party);
	}
}
