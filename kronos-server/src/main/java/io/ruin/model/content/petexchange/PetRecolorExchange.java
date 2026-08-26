package io.ruin.model.content.petexchange;

import io.ruin.data.impl.pets.pet_recolor_exchange;
import io.ruin.model.entity.npc.NPC;
import io.ruin.model.entity.npc.NPCAction;
import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.dialogue.MessageDialogue;
import io.ruin.model.inter.dialogue.NPCDialogue;
import io.ruin.model.inter.dialogue.OptionsDialogue;
import io.ruin.model.inter.dialogue.YesNoDialogue;
import io.ruin.model.inter.utils.Option;
import io.ruin.model.item.Item;
import io.ruin.model.item.actions.ItemNPCAction;
import io.ruin.model.shop.Currency;

import java.util.ArrayList;
import java.util.List;

/**
 * Dialogue-driven exchange: trade a base boss pet + Donator/Vote points for one of its
 * Shadow/Golden/Blood recolor variants. Base pet -> variant mapping and costs come entirely
 * from {@link pet_recolor_exchange}'s JSON config, so adding a future pet needs no Java changes.
 */
public class PetRecolorExchange {

	public static final int BROKER_NPC_ID = 30068;

	public static void register() {
		NPCAction.register(BROKER_NPC_ID, "talk-to", (player, npc) -> player.dialogue(
			new NPCDialogue(npc, "Bring me one of your boss pets and I can recolor it into a Shadow, " +
				"Golden, or Blood variant -- for a price in donator points or vote points."),
			new NPCDialogue(npc, "Just use the pet on me to get started. Fair warning: the original pet " +
				"doesn't survive the process.")
		));

		for (Integer baseItemId : pet_recolor_exchange.BY_BASE_ITEM_ID.keySet()) {
			ItemNPCAction.register(baseItemId, BROKER_NPC_ID, (player, item, npc) -> openTierChoice(player, npc, item));
		}
	}

	private static void openTierChoice(Player player, NPC npc, Item baseItem) {
		List<pet_recolor_exchange.Variant> variants = pet_recolor_exchange.BY_BASE_ITEM_ID.get(baseItem.getId());
		if (variants == null || variants.isEmpty())
			return;
		List<Option> options = new ArrayList<>();
		for (pet_recolor_exchange.Variant variant : variants) {
			options.add(new Option(variant.name + " (" + variant.dpCost + " DP, " + variant.vpCost + " VP)",
				() -> confirmExchange(player, baseItem, variant)));
		}
		options.add(new Option("Nevermind"));
		player.dialogue(
			new NPCDialogue(npc, "Which variant would you like?"),
			new OptionsDialogue(options)
		);
	}

	private static void confirmExchange(Player player, Item baseItem, pet_recolor_exchange.Variant variant) {
		player.dialogue(new YesNoDialogue(
			"Confirm exchange",
			"Trade your " + baseItem.getDef().name + " and " + variant.dpCost + " DP / " + variant.vpCost +
				" VP for a " + variant.name + "? This cannot be undone.",
			baseItem.getId(), 1,
			() -> performExchange(player, baseItem, variant)
		));
	}

	private static void performExchange(Player player, Item baseItem, pet_recolor_exchange.Variant variant) {
		Item currentPet = player.getInventory().findItem(baseItem.getId());
		if (currentPet == null) {
			player.sendMessage("You no longer have that pet.");
			return;
		}
		if (Currency.DONATOR.currencyHandler.getCurrencyCount(player) < variant.dpCost) {
			player.dialogue(new MessageDialogue("You don't have enough donator points for that."));
			return;
		}
		if (Currency.VOTE.currencyHandler.getCurrencyCount(player) < variant.vpCost) {
			player.dialogue(new MessageDialogue("You don't have enough vote points for that."));
			return;
		}

		currentPet.remove(1);
		if (variant.dpCost > 0)
			Currency.DONATOR.currencyHandler.removeCurrency(player, variant.dpCost);
		if (variant.vpCost > 0)
			Currency.VOTE.currencyHandler.removeCurrency(player, variant.vpCost);

		grantVariant(player, variant);
	}

	private static void grantVariant(Player player, pet_recolor_exchange.Variant variant) {
		if (player.getInventory().add(variant.itemId, 1) == 1) {
			player.sendMessage("<col=FF0000>The broker hands you a " + variant.name + ".");
		} else if (player.getBank().add(variant.itemId, 1) == 1) {
			player.sendMessage("<col=FF0000>Your new " + variant.name + " didn't fit in your inventory, so it's been sent to your bank.");
		} else {
			player.sendMessage("You don't have enough space for your new pet.");
		}
		if (!player.petItemIdsObtained.contains(variant.itemId))
			player.petItemIdsObtained.add(variant.itemId);
		player.addToCollectionLog(new Item(variant.itemId, 1));
	}

}
