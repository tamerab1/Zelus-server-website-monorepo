package io.ruin.model.item.actions.impl;

import io.ruin.model.entity.player.Player;
import io.ruin.model.item.Item;
import io.ruin.model.item.actions.ItemAction;

public class WildernessSword {

	private static boolean teleport(Player player, Item item) {
		return player.getMovement().startTeleport(e -> {
			player.animate(714);
			player.graphics(111, 92, 0);
			player.publicSound(200);
			e.delay(3);
			player.getMovement().teleport(3359, 3892, 0);
		});
	}

	public static void register() {
		ItemAction.registerInventory(13111, "teleport", WildernessSword::teleport);
		ItemAction.registerEquipment(13111, "teleport", WildernessSword::teleport);
		// Wildfang blade (Wilderness Forge upgrade of the wilderness sword 4)
		ItemAction.registerInventory(42442, "teleport", WildernessSword::teleport);
		ItemAction.registerEquipment(42442, "teleport", WildernessSword::teleport);
	}

}
