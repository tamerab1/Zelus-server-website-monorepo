package io.ruin.model.entity.npc.actions.minigame;

import io.ruin.model.activities.minigame.MiningMinigameInstance;
import io.ruin.model.entity.npc.NPC;
import io.ruin.model.entity.npc.NPCAction;
import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.dialogue.MessageDialogue;
import io.ruin.model.inter.dialogue.NPCDialogue;
import io.ruin.model.inter.dialogue.OptionsDialogue;
import io.ruin.model.inter.dialogue.PlayerDialogue;
import io.ruin.model.inter.utils.Option;
import io.ruin.model.stat.StatType;

// Entry manager for the new mining minigame (name/mechanics TBD) -- gate is level 82 Mining + 1M gp,
// which teleports the player into their own solo instance of the minigame area (see MiningMinigameInstance).
public class David {

	private static final int REQUIRED_MINING_LEVEL = 82;
	private static final int ENTRY_FEE = 1_000_000;

	public static void register() {
		NPCAction.register(7791, "talk-to", (player, npc) -> {
			player.dialogue(
				new NPCDialogue(npc, "Welcome! Fancy trying your luck in the minigame? It'll cost " + ENTRY_FEE + " coins to enter, and you'll need a Mining level of " + REQUIRED_MINING_LEVEL + "."),
				new OptionsDialogue(
					new Option("Yes, sign me up!", () -> enter(player, npc)),
					new Option("No, thanks.", player::closeDialogue)
				)
			);
		});
	}

	private static void enter(Player player, NPC npc) {
		if (player.getStats().get(StatType.Mining).currentLevel < REQUIRED_MINING_LEVEL) {
			player.dialogue(
				new PlayerDialogue("Yes, sign me up!"),
				new NPCDialogue(npc, "Sorry, you'll need a Mining level of " + REQUIRED_MINING_LEVEL + " before I can let you in."));
			return;
		}
		if (!player.getInventory().hasItem(995, ENTRY_FEE)) {
			player.dialogue(
				new PlayerDialogue("Yes, sign me up!"),
				new NPCDialogue(npc, "Sorry, you'll need " + ENTRY_FEE + " coins to enter."));
			return;
		}
		player.getInventory().remove(995, ENTRY_FEE);
		player.dialogue(
			new PlayerDialogue("Yes, sign me up!"),
			new NPCDialogue(npc, "Good luck! Off you go."));
		player.getMovement().startTeleport(event -> {
			player.getPacketSender().fadeOut();
			event.delay(1);
			boolean entered = MiningMinigameInstance.enter(player);
			player.getPacketSender().clearFade();
			if (!entered) {
				// fee was taken before the instance build -- give it back if the build failed
				player.getInventory().addOrDrop(995, ENTRY_FEE);
				return;
			}
			player.sendMessage("You pay David " + ENTRY_FEE + " coins and enter the minigame.");
		});
	}
}
