package io.ruin.model.entity.npc.actions;

import io.ruin.model.entity.npc.NPCAction;
import io.ruin.model.inter.dialogue.NPCDialogue;
import io.ruin.model.inter.dialogue.OptionsDialogue;
import io.ruin.model.inter.utils.Option;
import io.ruin.model.shop.ShopManager;

/**
 * Allanna (npc 8531, spawned at the Farming Guild -- data/npcs/spawns/Hosidius.json)
 * had no shop wiring at all before this: her shop data (Allanna's_Farming_Shop.yaml)
 * existed but nothing ever called ShopManager.openIfExists() for it, so the Farming
 * Guild's only nearby shopkeeper was unreachable and, even once reached, sold no
 * seeds -- tools only. Both fixed together: this wiring, and real seeds added to
 * her shop's defaultStock.
 */
public class AllannasFarmingShop {
	public static void register() {
		NPCAction.register(8531, "talk-to", (player, npc) -> player.dialogue(new NPCDialogue(npc, "Hello, " + player.getName() + ". How can I be of assistance?"),
			new OptionsDialogue(
				new Option("Open shop", () -> {
					ShopManager.openIfExists(player, "hYUme7woHuBVr6pmwd2mdKKh45qLWF");
				}),
				new Option("Nevermind"
				))));
		NPCAction.register(8531, "trade", (player, npc) -> {
			ShopManager.openIfExists(player, "hYUme7woHuBVr6pmwd2mdKKh45qLWF");
		});
	}
}
