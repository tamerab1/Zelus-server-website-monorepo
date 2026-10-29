package io.ruin.model.activities.raids.tfa;

import io.ruin.model.World;
import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.ToplevelComponent;
import io.ruin.model.map.Region;
import io.ruin.model.var.VarPlayerRepository;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The Fractured Archive's own overlays, driven the way the live game drives them (RSProx guardian_plan / nexus_plan /
 * RAID_PLAN; scripts read from cache 241):
 * <ul>
 * <li>Lobby: {@code tfa_lobby} (975) on overlay_hud (161:8, as recorded) while in the lobby. Its header is script 10047 on varbit
 * tfa_client_partystatus (16386): 0 "No Party", 1 "Party", 2 "Step Inside Now!" (the party's raid is running); the names
 * box (975:5) lists the party.</li>
 * <li>Raid: {@code tfa_hud} (974) on overlay_atmosphere inside the raid, with events on 974:5 / 974:1. Script 10042 takes
 * the eight member names (party order, "" for an empty slot); varbits tfa_client_p0..p7 (16387-16394) are each member's
 * orb (script 10039): 0 nobody, 1..27 the ring filled (value - 1) of 26 (1 at 0 hp), 30 dead (red); tfa_client_partyslot
 * (16395) is the viewer's own slot, 1-based (a solo raider has 1): that slot reads "Me" and its ring follows the
 * client's own hitpoints (script 10041). Script 10036
 * (ticks, flag) is the stopwatch, sent every tick: (0, 1) until the Guardian's Touch starts the clock, then (raid ticks,
 * 0); it never resets between rooms.</li>
 * </ul>
 */
public final class Raid4Hud {

	private Raid4Hud() {
	}

	public static final int TFA_HUD = 974, TFA_LOBBY = 975;
	private static final int LOBBY_HEADER = 2, LOBBY_NAMES = 5, HUD_TRIGGER = 1, HUD_NAMES = 5;
	private static final int SCRIPT_NAMES = 10042, SCRIPT_TIMER = 10036;
	private static final int RING = 26, DEAD = 30;
	private static final VarPlayerRepository PARTY_STATUS = VarPlayerRepository.varpbit(16386, false),
			PARTY_SLOT = VarPlayerRepository.varpbit(16395, false);
	private static final VarPlayerRepository[] ORBS = new VarPlayerRepository[8];

	static {
		for (int i = 0; i < 8; i++)
			ORBS[i] = VarPlayerRepository.varpbit(16387 + i, false);
	}

	/** Players currently shown the lobby overlay. */
	private static final Set<Player> inLobby = new HashSet<>();

	/** Every 2 ticks: the lobby overlay follows who stands in the lobby. */
	static void register() {
		World.startEvent(e -> {
			while (true) {
				e.delay(2);
				try {
					lobbyPass();
				} catch (Exception ex) {
					ex.printStackTrace();
				}
			}
		});
	}

	private static void lobbyPass() {
		Region lobby = Region.get(Raid4Ids.LOBBY_REGION);
		Set<Player> here = new HashSet<>();
		for (Player p : lobby.players)
			if (p != null && p.isOnline())
				here.add(p);
		for (Player p : new HashSet<>(inLobby))
			if (!here.contains(p)) {
				inLobby.remove(p);
				if (p.isOnline() && p.isVisibleInterface(TFA_LOBBY))
					p.closeInterface(ToplevelComponent.PC);
			}
		for (Player p : here) {
			if (!p.isVisibleInterface(TFA_LOBBY)) // the packet only: openInterface would close the chatbox (dialogues)
				p.getPacketSender().sendInterface(TFA_LOBBY, ToplevelComponent.PC);
			inLobby.add(p);
			drawLobby(p);
		}
	}

	private static void drawLobby(Player p) {
		Raid4Party party = Raid4.partyOf(p);
		int status = party == null ? 0 : party.getController() != null ? 2 : 1;
		PARTY_STATUS.set(p, status);
		StringBuilder names = new StringBuilder();
		if (party != null)
			for (String m : party.getMembers()) {
				Player member = World.getPlayer(m);
				String shown = member != null ? member.getName() : m;
				names.append(names.length() == 0 ? "" : "<br>").append(party.getLeader().equals(m) ? "<col=ffc35a>" + shown + "</col>" : shown);
			}
		p.getPacketSender().sendString(TFA_LOBBY, LOBBY_NAMES, names.toString());
	}

	/** On entering the raid (and every room move): the HUD and its names. */
	static void openRaidHud(Player p, Raid4Party party) {
		inLobby.remove(p);
		// the lobby overlay sits on overlay_hud (161:8), not under the raid HUD: it closes on the way in (guardian_plan 1)
		if (p.isVisibleInterface(TFA_LOBBY))
			p.closeInterface(ToplevelComponent.PC);
		if (!p.isVisibleInterface(TFA_HUD)) {
			p.getPacketSender().sendInterface(TFA_HUD, ToplevelComponent.OVERLAY);
			p.getPacketSender().sendIfEvents(TFA_HUD, HUD_NAMES, -1, -1, 0x3FE);
			p.getPacketSender().sendIfEvents(TFA_HUD, HUD_TRIGGER, -1, -1, 0x3FE);
		}
		sendNames(p, party);
	}

	static void sendNames(Player p, Raid4Party party) {
		List<String> members = party.getMembers();
		Object[] names = new Object[8];
		for (int i = 0; i < 8; i++) {
			if (i < members.size()) {
				Player member = World.getPlayer(members.get(i));
				names[i] = member != null ? member.getName() : members.get(i);
			} else
				names[i] = "";
		}
		p.getPacketSender().sendClientScript(SCRIPT_NAMES, "ssssssss", names);
		PARTY_SLOT.set(p, members.indexOf(Raid4Party.key(p)) + 1);
	}

	/** Every raid tick, for each member inside: the eight orbs and the stopwatch. */
	static void tick(Raid4Controller raid, Raid4Party party, long raidTicks, boolean started) {
		List<String> members = party.getMembers();
		int[] codes = new int[8];
		for (int i = 0; i < members.size() && i < 8; i++) {
			Player m = World.getPlayer(members.get(i));
			if (m == null || !raid.isInside(m))
				codes[i] = 0;
			else if (party.isDead(m)) // at 0 hp, before the death ends, the ring reads 1 (empty), as recorded
				codes[i] = DEAD;
			else
				codes[i] = 1 + Math.min(RING, RING * Math.max(0, m.getHp()) / Math.max(1, m.getMaxHp()));
		}
		for (Player p : party.online()) {
			if (!raid.isInside(p) || raid.isFading(p))
				continue;
			if (!p.isVisibleInterface(TFA_HUD))
				openRaidHud(p, party);
			for (int i = 0; i < 8; i++)
				if (ORBS[i].get(p) != codes[i])
					ORBS[i].set(p, codes[i]);
			p.getPacketSender().sendClientScript(SCRIPT_TIMER, "ii", started ? (int) raidTicks : 0, started ? 0 : 1);
		}
	}

	/** Leaving the raid: the HUD goes (the lobby pass reopens the lobby overlay when they arrive there). */
	static void closeRaidHud(Player p) {
		if (p.isOnline() && p.isVisibleInterface(TFA_HUD))
			p.closeInterface(ToplevelComponent.OVERLAY);
		for (VarPlayerRepository orb : ORBS)
			orb.set(p, 0);
		PARTY_SLOT.set(p, 0);
	}
}
