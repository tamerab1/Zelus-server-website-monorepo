package io.ruin.model.map.object.actions.impl;

import io.ruin.model.entity.player.Player;
import io.ruin.model.item.Item;
import io.ruin.model.item.actions.impl.scratchcard.ScratchCardManager;
import io.ruin.model.map.object.actions.ObjectAction;

public class ScratchMachine {

	private static final int SCRATCH_MACHINE_ID = 60013;
	private static final int SCRATCH_CARD_ID = 10600;

	public static void register() {
		ObjectAction.register(SCRATCH_MACHINE_ID, "Use", (player, obj) -> {
			Item scratchCard = player.getInventory().findItem(SCRATCH_CARD_ID);
			if (scratchCard == null) {
				player.sendFilteredMessage("You need a scratch card to use this.");
				return;
			}
			if (player.getScratchCard() == null || !player.getScratchCard().isRunning()) {
				player.scratchCardManager = new ScratchCardManager();
				player.getInventory().remove(SCRATCH_CARD_ID, 1);
				player.getScratchCard().open(player);
			}
		});
	}
}
