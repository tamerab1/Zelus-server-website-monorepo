package io.ruin.model.activities.raids.tfa;

import io.ruin.model.World;
import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.dialogue.MessageDialogue;
import io.ruin.model.inter.dialogue.OptionsDialogue;
import io.ruin.model.inter.utils.Option;

import java.util.ArrayList;
import java.util.List;

/**
 * The lobby's Grouping Board (loc 62767): make, find, join and run a party without commands. It works through
 * dialogue menus; the vanilla party interfaces (975/976) are client scripts driven by server data we don't have.
 */
public final class Raid4Board {

	private Raid4Board() {
	}

	public static void open(Player player) {
		Raid4Party party = Raid4.partyOf(player);
		if (party == null) {
			player.dialogue(new OptionsDialogue("The Fractured Archive",
					new Option("Create a party", () -> {
						Raid4.createParty(player);
						open(player);
					}),
					new Option("Browse open parties", () -> browse(player, 0)),
					new Option("Join a party by leader name", () -> player.stringInput("Enter the party leader's name:", name -> {
						Player leader = World.getPlayer(name);
						if (leader == null || Raid4.partyOf(leader) == null) {
							player.sendMessage("No Archive party is led by \"" + name + "\".");
							return;
						}
						joinOpen(player, Raid4.partyOf(leader));
					})),
					new Option("Unclaimed loot", () -> claim(player)),
					new Option("Close", player::closeDialogue)));
			return;
		}
		if (party.isLeader(player)) {
			player.dialogue(new OptionsDialogue("Your party (" + party.size() + "/" + Raid4Party.MAX_SIZE + ")",
					new Option("View members", () -> members(player, party)),
					new Option("Invite a player", () -> player.stringInput("Invite which player?", name -> {
						Player target = World.getPlayer(name);
						if (target == null)
							player.sendMessage(name + " isn't online.");
						else
							Raid4.invite(player, target);
					})),
					new Option("Start the raid", () -> {
						player.closeDialogue();
						Raid4.enter(player);
					}),
					new Option("Leave the party", () -> {
						Raid4.leaveParty(player);
						player.closeDialogue();
					}),
					new Option("Close", player::closeDialogue)));
		} else {
			player.dialogue(new OptionsDialogue("Your party (" + party.size() + "/" + Raid4Party.MAX_SIZE + ")",
					new Option("View members", () -> members(player, party)),
					new Option("Leave the party", () -> {
						Raid4.leaveParty(player);
						player.closeDialogue();
					}),
					new Option("Close", player::closeDialogue)));
		}
	}

	/** Four open parties a page: leader name and size. */
	private static void browse(Player player, int page) {
		List<Raid4Party> open = Raid4.openParties();
		if (open.isEmpty()) {
			player.dialogue(new MessageDialogue("There are no open parties right now. Create one, or ask a leader to invite you."));
			return;
		}
		List<Option> options = new ArrayList<>();
		int from = page * 3;
		for (int i = from; i < Math.min(open.size(), from + 3); i++) {
			Raid4Party party = open.get(i);
			Player leader = party.leaderPlayer();
			String label = (leader == null ? "?" : leader.getName()) + " (" + party.size() + "/" + Raid4Party.MAX_SIZE + ")";
			options.add(new Option(label, () -> joinOpen(player, party)));
		}
		if (open.size() > from + 3)
			options.add(new Option("More...", () -> browse(player, page + 1)));
		options.add(new Option("Back", () -> open(player)));
		player.dialogue(new OptionsDialogue("Open parties", options));
	}

	private static void joinOpen(Player player, Raid4Party party) {
		if (party.hasStarted() || party.isFull()) {
			player.sendMessage("That party is full or already inside.");
			return;
		}
		Player leader = party.leaderPlayer();
		if (leader == null) {
			player.sendMessage("That party's leader is offline.");
			return;
		}
		party.invite(player); // the board is a public listing
		Raid4.join(player, leader);
		player.closeDialogue();
	}

	private static void members(Player player, Raid4Party party) {
		StringBuilder sb = new StringBuilder();
		for (String name : party.getMembers()) {
			Player p = World.getPlayer(name);
			if (sb.length() > 0)
				sb.append(", ");
			sb.append(p == null ? name : p.getName()).append(p != null && party.isLeader(p) ? " (leader)" : "");
		}
		player.dialogue(new MessageDialogue("Members: " + sb));
	}

	private static void claim(Player player) {
		Raid4Loot.Reward reward = Raid4.unclaimed.remove(Raid4Party.key(player));
		if (reward == null)
			player.dialogue(new MessageDialogue("You have no unclaimed Fractured Archive loot."));
		else {
			player.closeDialogue();
			Raid4VaultRoom.claim(player, reward);
		}
	}
}
