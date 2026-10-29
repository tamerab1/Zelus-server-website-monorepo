package io.ruin.model.item.actions.impl.scratchcard;

import io.ruin.model.item.actions.ItemAction;
import io.ruin.model.map.Position;
import io.ruin.model.skills.magic.spells.modern.ModernTeleport;

public class ScratchCard {
	private static final int SCRATCH_CARD_ID = 10600;
	/** The Scratch Machine (65058) at home, 3111,3495: a card's Scratch takes its holder there, like the Z Golden key. */
	private static final Position MACHINE = new Position(3111, 3495, 0), BESIDE_MACHINE = new Position(3111, 3494, 0);

	public static void register() {
		ItemAction.registerInventory(SCRATCH_CARD_ID, "scratch", (player, item) -> {
			if (player.getPosition().isWithinDistance(MACHINE, 3)) {
				player.sendFilteredMessage("Use the card on the Scratch Machine.");
				return;
			}
			ModernTeleport.teleport(player, BESIDE_MACHINE); // the usual teleport rules (wilderness, combat, jails) apply
		});
	}
}
