package io.ruin.model.activities.raids.tfa;

import io.ruin.model.activities.raids.tfa.bosses.guardian.ArchiveGuardian;
import io.ruin.model.activities.raids.tfa.bosses.guardian.InertGuardian;
import io.ruin.model.activities.raids.tfa.rooms.ArchiveGuardianRoom;
import io.ruin.model.entity.npc.NPCAction;
import io.ruin.model.entity.player.Player;
import io.ruin.model.map.object.actions.ObjectAction;
import io.ruin.cache.NPCType;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.ruin.model.activities.raids.tfa.Raid4Ids.*;

/**
 * Entry points and bookkeeping for The Fractured Archive: parties, running raids, and the lobby / Nexus
 * handlers. Party management is command-driven until the lobby interfaces (973-979) are wired.
 */
@Slf4j
public final class Raid4 {

	private Raid4() {
	}

	/** Lower-case member name -> party. */
	private static final Map<String, Raid4Party> PARTIES = new HashMap<>();
	private static final List<Raid4Controller> ACTIVE = new ArrayList<>();
	/** Vault loot of members who were offline when their raid ended, by party key. */
	public static final Map<String, Raid4Loot.Reward> unclaimed = new HashMap<>();

	public static void register() {
		Raid4Npcs.register();
		Raid4Lobby.register();
		Raid4Hud.register();
		Raid4Stats.register();
		Raid4PartyBoard.register();
		// the vault's Merit scroll (tfa_vault_scoreboard): the Alliance Log
		for (int option = 1; option <= 5; option++) {
			ObjectAction.register(LOC_MERIT_SCROLL, option, (player, obj) -> Raid4Stats.open(player));
			// the Nexus's scoreboard: the same Alliance Log, on the party's stats
			ObjectAction.register(LOC_IN_RAID_SCOREBOARD, option, (player, obj) -> Raid4Stats.open(player));
			ObjectAction.register(LOC_IN_RAID_SCOREBOARD_BROKEN, option, (player, obj) -> Raid4Stats.open(player));
			// the loot wheelbarrow keeps loot that wasn't taken from the vault
			ObjectAction.register(LOC_LOOT_WHEELBARROW, option, (player, obj) -> Raid4Lobby.claimUnclaimed(player));
		}
		// a raider who logged out mid-raid comes back into their party's room
		io.ruin.model.entity.shared.listeners.LoginListener.register(p -> {
			Raid4Party party = partyOf(p);
			if (party != null && party.getController() != null)
				party.getController().rejoin(p);
		});
		NPCType.registerCombat(ArchiveGuardian.class, NPC_GUARDIAN, NPC_GUARDIAN_UNSHIELDED);
		NPCType.registerCombat(InertGuardian.class, NPC_BATTERY_ACTIVE);
		NPCType.registerCombat(io.ruin.model.activities.raids.tfa.bosses.ezekiel.Ezekiel.class, NPC_EZEKIEL);
		NPCType.registerCombat(io.ruin.model.activities.raids.tfa.bosses.ezekiel.AnimatedSword.class, NPC_EZEKIEL_SWORD);
		NPCType.registerCombat(io.ruin.model.activities.raids.tfa.bosses.bork.Bork.class, NPC_BORK);
		NPCType.registerCombat(io.ruin.model.activities.raids.tfa.bosses.bork.Smorks.class, NPC_SMORKS);
		NPCType.registerCombat(io.ruin.model.activities.raids.tfa.bosses.Raid4Puppet.class, NPC_MOIA, NPC_MOIA_BINDER,
				NPC_DREADNOTH, NPC_DREADNOTH_JELLY,
				NPC_DAGONHAI_ELDER, NPC_DAGONHAI_ELDER_WEAK, NPC_DAGONHAI_MONK_1, NPC_DAGONHAI_MONK_1_WEAK, NPC_DAGONHAI_MONK_2,
				NPC_DAGONHAI_MONK_2_WEAK, NPC_DAGONHAI_CAULDRON, NPC_DAGONHAI_IMP, NPC_DAGONHAI_RIFT,
				NPC_XARTH, NPC_XARTH_PILLAR, NPC_XARTH_CORPSE, NPC_XARTH_CORPSE_DOUSED,
				NPC_OCELLUS_P1, NPC_OCELLUS_P2, NPC_OCELLUS_BLADE, NPC_OCELLUS_P3, NPC_VOID_RIFT, NPC_VOID_SIPHON, NPC_VOID_AUGMENTER,
				NPC_VOID_VINE, 16672, 16673);

		// Ocellus' censers (op 1; the inactive forms answer too, with "burnt out")
		for (int id = LOC_CENSER_MELEE; id <= LOC_CENSER_HEAL + 1; id++) {
			for (int option = 1; option <= 2; option++) {
				ObjectAction.register(id, option, (player, obj) -> {
					Raid4Controller raid = controllerOf(player);
					if (raid != null && raid.getRoom() instanceof io.ruin.model.activities.raids.tfa.rooms.OcellusRoom room)
						room.useCenser(player, obj);
				});
			}
		}
		for (int option = 1; option <= 3; option++)
			NPCAction.register(Raid4Quartermaster.NPC_QUARTERMASTER, option, (player, npc) -> {
				// one npc id, two jobs: gear rental in the lobby, supply points in the Nexus
				if (player.getPosition().getRegion().id == Raid4Ids.LOBBY_REGION)
					Raid4Rental.talk(player);
				else
					Raid4Quartermaster.open(player, 0);
			});
		Raid4Rental.register();
		Raid4LobbyNpcs.register();
		new io.ruin.model.entity.npc.NPC(Raid4Rental.NPC).spawn(Raid4Rental.LOBBY_X, Raid4Rental.LOBBY_Y, 0,
				io.ruin.model.map.Direction.WEST, 0);
		// The vault: member chests, the special chest, and the portal out
		for (int id = NPC_VAULT_CHEST_BIG; id <= NPC_VAULT_CHEST_FIRST + 15; id++) {
			for (int option = 1; option <= 3; option++) {
				NPCAction.register(id, option, (player, npc) -> {
					Raid4Controller raid = controllerOf(player);
					if (raid != null && raid.getRoom() instanceof Raid4VaultRoom vault)
						vault.openChest(player, npc);
				});
			}
		}
		for (int option = 1; option <= 2; option++) {
			for (int loc : new int[] { LOC_VAULT_EXIT_PORTAL, LOC_VAULT_EXIT })
				ObjectAction.register(loc, option, (player, obj) -> {
					Raid4Controller raid = controllerOf(player);
					if (raid != null)
						raid.leave(player);
				});
			NPCAction.register(NPC_VAULT_EXIT_SMALL, option, (player, npc) -> {
				Raid4Controller raid = controllerOf(player);
				if (raid != null)
					raid.leave(player);
			});
		}
		Raid4VaultRoom.register();

		// tfa_exit_large (62766), the hall's way out: leave the raid, forfeiting it (confirmed first)
		for (int option = 1; option <= 3; option++)
			ObjectAction.register(LOC_EXIT_LARGE, option, (player, obj) -> confirmForfeit(player));

		// Ivy before the Ocellus door (tfa_ivy_sophista, Talk-to). Her Talk-to text isn't recorded or on the wiki yet:
		// a stand-in with her own recorded lines from that visit (nexus_plan 4, A3)
		NPCAction.register(Raid4NexusRoom.IVY, 1, (player, npc) -> player.dialogue(
				new io.ruin.model.inter.dialogue.NPCDialogue(npc, "The situation is almost under control!"),
				new io.ruin.model.inter.dialogue.NPCDialogue(npc, "Check the vault for danger and let's end this!")));
		NPCAction.register(NPC_GUARDIAN_START, 1, (player, npc) -> {
			Raid4Controller raid = controllerOf(player);
			if (raid != null && raid.getRoom() instanceof ArchiveGuardianRoom room)
				room.touch(player, npc);
		});

		// Moia's and Dreadnoth's rooms have their own teleporter locs; the other four share TFA_BOSS_TELEPORTER.
		for (int loc : new int[] { LOC_BOSS_TELEPORTER, LOC_TELEPORTER_MOIA, LOC_TELEPORTER_JELLY }) {
			for (int option = 1; option <= 2; option++) {
				ObjectAction.register(loc, option, (player, obj) -> {
					Raid4Controller raid = controllerOf(player);
					if (raid != null && raid.getRoom() != null)
						raid.getRoom().useTeleporter(player);
					else
						player.sendMessage("Nothing interesting happens.");
				});
			}
		}
		// Ocellus: the Hooded Figure's Talk-to / Proceed starts the fight; the stones' Jump crosses the chasm
		for (int intro : new int[] { 16672, 16673 })
			for (int option = 1; option <= 3; option++)
				NPCAction.register(intro, option, (player, npc) -> {
					Raid4Controller raid = controllerOf(player);
					if (raid != null && raid.getRoom() instanceof io.ruin.model.activities.raids.tfa.rooms.OcellusRoom room)
						room.talk(player);
				});
		for (int stone = 62686; stone <= 62690; stone++)
			ObjectAction.register(stone, 1, (player, obj) -> {
				Raid4Controller raid = controllerOf(player);
				if (raid != null && raid.getRoom() instanceof io.ruin.model.activities.raids.tfa.rooms.OcellusRoom room)
					room.jumpStone(player, obj);
			});
		// Alliance Navigator: Follow (op1) / Quick-follow (op3) lead the party back to the Nexus
		for (int option : new int[] { 1, 3 }) {
			NPCAction.register(NPC_ALLIANCE_NAVIGATOR, option, (player, npc) -> {
				Raid4Controller raid = controllerOf(player);
				if (raid != null)
					raid.returnToNexus(player);
			});
		}
		// The Blade after Ocellus: Commune (op1), Travel (op3) -> "Are you ready to proceed?"
		NPCAction.register(NPC_BLADE_VAULT_ENTRY, 1, (player, npc) -> player.dialogue(
				new io.ruin.model.inter.dialogue.MessageDialogue("The Blade hums quietly. The way to the vault lies through it.")));
		NPCAction.register(NPC_BLADE_VAULT_ENTRY, 3, (player, npc) -> player.dialogue(
				new io.ruin.model.inter.dialogue.OptionsDialogue("Are you ready to proceed?",
						new io.ruin.model.inter.utils.Option("Yes.", () -> {
							player.closeDialogue();
							Raid4Controller raid = controllerOf(player);
							if (raid != null)
								raid.travelToVault(player);
						}),
						new io.ruin.model.inter.utils.Option("No.", player::closeDialogue))));
		ObjectAction.register(LOC_ROOM_EXIT, 1, (player, obj) -> {
			Raid4Controller raid = controllerOf(player);
			if (raid != null)
				raid.returnToNexus(player);
		});

		for (int option = 1; option <= 3; option++)
			ObjectAction.register(LOC_LOBBY_RAID_ENTRY, option, (player, obj) -> enter(player));
		for (int option = 1; option <= 5; option++)
			// the grouping board: its first op is the real party list (977 / 976), the rest the old menu (unclaimed loot)
			if (option == 1)
				ObjectAction.register(LOC_LOBBY_GROUPING_BOARD, option, (player, obj) -> Raid4PartyBoard.openList(player));
			else
				ObjectAction.register(LOC_LOBBY_GROUPING_BOARD, option, (player, obj) -> Raid4Board.open(player));
		// "Enter" / "Quick-enter" on every state of each door (open frame, ajar, opening).
		for (int[] door : Raid4NexusDoors.variants()) {
			Raid4RoomType.Side side = door[1] < 32 ? Raid4RoomType.Side.WEST : Raid4RoomType.Side.EAST;
			for (int option = 1; option <= 2; option++) {
				for (int id = door[3]; id <= door[5]; id++)
					registerDoor(id, option, door[0], side);
			}
		}
		// the Ocellus door (4124,5621): open only in the atrium after stage 4 (nexus_plan 2, 4)
		for (int option = 1; option <= 2; option++)
			ObjectAction.register(LOC_NEXUS_OCELLUS_DOOR_OPEN, option, (player, obj) -> {
				Raid4Controller raid = controllerOf(player);
				if (raid != null)
					raid.enterOcellus(player);
			});
	}

	private static void registerDoor(int locId, int option, int slot, Raid4RoomType.Side side) {
		ObjectAction.register(locId, option, (player, obj) -> {
			Raid4Controller raid = controllerOf(player);
			if (raid != null)
				raid.choosePath(player, slot, side);
		});
	}

	/* Parties */

	public static Raid4Party partyOf(Player player) {
		return PARTIES.get(Raid4Party.key(player));
	}

	public static Raid4Controller controllerOf(Player player) {
		Raid4Party party = partyOf(player);
		return party == null ? null : party.getController();
	}

	public static Raid4Party createParty(Player player) {
		Raid4Party existing = partyOf(player);
		if (existing != null)
			return existing;
		Raid4Party party = new Raid4Party(player);
		PARTIES.put(Raid4Party.key(player), party);
		return party;
	}

	public static void invite(Player leader, Player target) {
		Raid4Party party = partyOf(leader);
		if (party == null || !party.isLeader(leader)) {
			leader.sendMessage("You need to lead a party to invite players.");
			return;
		}
		if (partyOf(target) != null) {
			leader.sendMessage(target.getName() + " is already in a party.");
			return;
		}
		if (party.isFull() || party.hasStarted()) {
			leader.sendMessage("Your party can't take any more members.");
			return;
		}
		party.invite(target);
		leader.sendMessage("You invite " + target.getName() + " to your Archive party.");
		target.sendMessage(leader.getName() + " has invited you to their Archive party. (::raid4 join " + leader.getName() + ")");
	}

	public static void join(Player player, Player leader) {
		Raid4Party party = partyOf(leader);
		if (partyOf(player) != null) {
			player.sendMessage("You're already in a party.");
			return;
		}
		if (party == null || !party.accept(player)) {
			player.sendMessage("You can't join that party.");
			return;
		}
		PARTIES.put(Raid4Party.key(player), party);
		party.message(player.getName() + " has joined the party (" + party.size() + "/" + Raid4Party.MAX_SIZE + ").");
	}

	public static final int LOC_EXIT_LARGE = 62766;

	/** The exit and ::leaveraid: forfeit the run (the party carries on without them). */
	public static void confirmForfeit(Player player) {
		Raid4Controller raid = controllerOf(player);
		if (raid == null || !raid.isInside(player)) {
			player.sendMessage("You aren't in the Fractured Archive.");
			return;
		}
		player.dialogue(new io.ruin.model.inter.dialogue.OptionsDialogue("Leave the Archive? You forfeit this raid and its loot.",
				new io.ruin.model.inter.utils.Option("Yes, leave the raid.", () -> {
					player.closeDialogue();
					leaveParty(player);
				}),
				new io.ruin.model.inter.utils.Option("No, stay.", player::closeDialogue)));
	}

	/** Removes the player from their party. A raid in progress notices through its map listener. */
	public static void leaveParty(Player player) {
		Raid4Party party = partyOf(player);
		if (party == null)
			return;
		Raid4Controller raid = party.getController();
		if (raid != null && raid.isInside(player)) {
			raid.leave(player);
			return; // the controller's exit handler calls back in here once they're out
		}
		PARTIES.remove(Raid4Party.key(player));
		if (party.remove(Raid4Party.key(player))) {
			if (raid != null)
				raid.end(null);
			return;
		}
		party.message(player.getName() + " has left the party.");
	}

	/* Raids */

	/** The lobby barrier: the leader starts the raid, members rejoin a running one. */
	public static void enter(Player player) {
		Raid4Party party = partyOf(player);
		if (party == null) {
			// guardian_plan 1 (4297 [327..341]): the chatmenu, then the party list (977) on the first option; the two
			// spectate lines are struck through in the live game too
			player.dialogue(new io.ruin.model.inter.dialogue.OptionsDialogue("You are not currently in a raiding party.",
					new io.ruin.model.inter.utils.Option("Form or join a party.", () -> Raid4PartyBoard.openList(player)),
					new io.ruin.model.inter.utils.Option("<str>Spectate a specific party.</str>", () -> {
					}),
					new io.ruin.model.inter.utils.Option("<str>Spectate a recent party.</str>", () -> {
					})));
			return;
		}
		if (party.hasStarted()) {
			player.sendMessage("Your party is already inside.");
			return;
		}
		if (!party.isLeader(player)) {
			player.sendMessage("Only the party leader can lead the way in.");
			return;
		}
		start(party);
	}

	/** Every party, once each. */
	public static List<Raid4Party> allParties() {
		List<Raid4Party> list = new ArrayList<>();
		for (Raid4Party party : PARTIES.values())
			if (!list.contains(party))
				list.add(party);
		return list;
	}

	/** Parties still forming in the lobby with room to spare. */
	public static List<Raid4Party> openParties() {
		List<Raid4Party> list = new ArrayList<>();
		for (Raid4Party party : PARTIES.values()) {
			if (!list.contains(party) && !party.hasStarted() && !party.isFull() && party.leaderPlayer() != null)
				list.add(party);
		}
		return list;
	}

	public static boolean start(Raid4Party party) {
		// only members waiting in the lobby go in; anyone elsewhere (or offline) is dropped from the party
		for (String name : new ArrayList<>(party.getMembers())) {
			Player member = io.ruin.model.World.getPlayer(name);
			if (member == null || member.getPosition().getRegion().id != LOBBY_REGION) {
				PARTIES.remove(name);
				party.remove(name);
				if (member != null)
					member.sendMessage("Your party entered the Archive without you: you have to be in the lobby.");
			}
		}
		if (party.size() == 0)
			return false;
		Raid4Controller raid = new Raid4Controller(party);
		party.setController(raid);
		if (!raid.start()) {
			party.setController(null);
			party.message("The Archive can't be opened right now. Try again in a moment.");
			return false;
		}
		ACTIVE.add(raid);
		for (Player member : party.online())
			member.sendMessage("You enter the Archive..."); // guardian_plan 1
		log.info("Raid 4 started: {}", party);
		return true;
	}

	static void finished(Raid4Controller raid) {
		ACTIVE.remove(raid);
		Raid4Party party = raid.getParty();
		// Members who left mid-run were already dropped; anyone still listed stays partied in the lobby.
		if (party.getController() == raid)
			party.setController(null);
	}

	public static List<Raid4Controller> active() {
		return ACTIVE;
	}
}
