package io.ruin.model.inter.questtab.main;

import io.ruin.cache.Color;
import io.ruin.model.World;
import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.dialogue.MessageDialogue;
import io.ruin.model.inter.dialogue.OptionsDialogue;
import io.ruin.model.inter.questtab.QuestTabEntry;
import io.ruin.model.inter.utils.Option;

public class TotalSpent extends QuestTabEntry {

	public static final TotalSpent INSTANCE = new TotalSpent();

	@Override
	public void send(Player player) {
		send(player, "Total Spent", "$" + player.storeAmountSpent, Color.GREEN);
	}

	@Override
	public void select(Player player) {
		player.dialogue(
			new MessageDialogue("You have spent a total of " + Color.COOL_BLUE.wrap("$" + player.storeAmountSpent) +
				" inside the store. You can view a list of donator benefits by going to "
				+ Color.COOL_BLUE.wrap(World.type.getWebsiteUrl() + "/store") + "."),
			new OptionsDialogue("View the " + World.type.getWorldName() + " Store?",
				new Option("Yes", () -> player.openUrl(World.type.getWorldName() + " Store", World.type.getWebsiteUrl() + "/store")),
				new Option("No", player::closeDialogue)));
	}

}