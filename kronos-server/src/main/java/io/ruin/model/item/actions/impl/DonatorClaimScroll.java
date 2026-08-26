package io.ruin.model.item.actions.impl;

import io.ruin.model.activities.dailytasks.DailyTasks;
import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.dialogue.MessageDialogue;
import io.ruin.model.inter.dialogue.OptionsDialogue;
import io.ruin.model.inter.utils.Option;
import io.ruin.model.item.actions.ItemAction;
import io.ruin.model.var.VarPlayerRepository;

import java.util.function.BiConsumer;

public class DonatorClaimScroll {

	private static void registerClaimTicket(int itemId, String rewardLabel, BiConsumer<Player, Integer> grant) {
		ItemAction.registerInventory(itemId, "claim", (player, item) -> {
			int held = player.getInventory().getAmount(itemId);
			if (held <= 0) {
				return;
			}
			player.integerInput("How many tickets do you want to redeem?", amount -> {
				int toRedeem = Math.min(amount, player.getInventory().getAmount(itemId));
				if (toRedeem <= 0) {
					return;
				}
				player.getInventory().remove(itemId, toRedeem);
				grant.accept(player, toRedeem);
				player.sendMessage("You have claimed " + toRedeem + " " + rewardLabel + (toRedeem == 1 ? "" : "s") + "!");
			});
		});
	}
	public static void register() {
		ItemAction.registerInventory(30576, "read", (player, item) -> {
			player.dialogue(new MessageDialogue("This will redeem $25 donated on your account"));
			player.dialogue(new OptionsDialogue("Are you sure you want to claim this?",
				new Option("Yes, I want claim it!", () -> {
					item.remove();
					player.updateTotalDonated(25);
					DonatorBond.checkDonatorStatus(player);
					player.sendMessage("You now have a total of " + player.getTotalDonated() + "$ donated!");
				}),
				new Option("Nevermind.")));
		});
		ItemAction.registerInventory(32001, "claim", (player, item) -> {
			if (player.hasFreePerkUnlock) {
				player.sendMessage("You are unable to claim another max perk unlock until you redeem your current unlock.");
				return;
			}
			player.startEvent(e -> {
				player.dialogue(new MessageDialogue("This will allow you to purchase a free level 5 perk from the perk master."));
				e.waitForDialogue(player);
				player.dialogue(new OptionsDialogue("Are you sure you want to claim this?",
					new Option("Yes, I want claim it!", () -> {
						item.remove(1);
						player.hasFreePerkUnlock = true;
						player.sendMessage("You now have a free max perk unlock available at the perk master!");
					}),
					new Option("Nevermind.")));
			});
		});
		registerClaimTicket(59599, "PK point", (player, amount) -> player.pkPoints += amount);
		registerClaimTicket(59600, "donator point", (player, amount) -> {
			player.donatorPoints += amount;
			DonatorBond.checkDonatorStatus(player);
		});
		// Fixed: this item's own cache name is "Bounty Hunter Ticket", but it was previously
		// wired to claim Blood Money -- a pre-existing mismatch. Now claims BH points, matching
		// the item's actual name (and the shop entry that uses this same item id as its ticket).
		registerClaimTicket(59601, "BH point", (player, amount) -> player.UpdateBountyPoints(amount));
		registerClaimTicket(59602, "Vote Point", (player, amount) -> player.updateVotePoints(amount));

		ItemAction.registerInventory(59599, "check", (player, item) ->
			player.sendMessage("You currently have " + player.getPKPoints() + " PK points."));
		ItemAction.registerInventory(59600, "check", (player, item) ->
			player.sendMessage("You currently have " + player.getDonatorPoints() + " donator points."));
		ItemAction.registerInventory(59601, "check", (player, item) ->
			player.sendMessage("You currently have " + player.GetBountyPoints() + " BH points."));
		ItemAction.registerInventory(59602, "check", (player, item) ->
			player.sendMessage("You currently have " + player.getVotePoints() + " vote points."));
		ItemAction.registerInventory(30575, "read", (player, item) -> {
			player.dialogue(new MessageDialogue("This will redeem $10 donated on your account"));
			player.dialogue(new OptionsDialogue("Are you sure you want to claim this?",
				new Option("Yes, I want claim it!", () -> {
					item.remove();
					player.updateTotalDonated(10);
					DonatorBond.checkDonatorStatus(player);
					player.sendMessage("You now have a total of " + player.getTotalDonated() + "$ donated!");
				}),
				new Option("Nevermind.")));
		});
		ItemAction.registerInventory(30573, "read", (player, item) -> {
			player.dialogue(new MessageDialogue("This will redeem $50 donated on your account"));
			player.dialogue(new OptionsDialogue("Are you sure you want to claim this?",
				new Option("Yes, I want claim it!", () -> {
					item.remove();
					player.updateTotalDonated(50);
					DonatorBond.checkDonatorStatus(player);
					player.sendMessage("You now have a total of " + player.getTotalDonated() + "$ donated!");
				}),
				new Option("Nevermind.")));
		});
		ItemAction.registerInventory(30574, "read", (player, item) -> {
			player.dialogue(new MessageDialogue("This will redeem $100 donated on your account"));
			player.dialogue(new OptionsDialogue("Are you sure you want to claim this?",
				new Option("Yes, I want claim it!", () -> {
					item.remove();
					player.updateTotalDonated(100);
					DonatorBond.checkDonatorStatus(player);
					player.sendMessage("You now have a total of " + player.getTotalDonated() + "$ donated!");
				}),
				new Option("Nevermind.")));
		});

	}
}
