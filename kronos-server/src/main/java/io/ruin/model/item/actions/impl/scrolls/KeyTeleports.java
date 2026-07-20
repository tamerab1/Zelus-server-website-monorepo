package io.ruin.model.item.actions.impl.scrolls;

import io.ruin.model.content.HomeHandler;
import io.ruin.model.entity.player.Player;
import io.ruin.model.item.Item;
import io.ruin.model.item.actions.ItemAction;
import io.ruin.model.map.Bounds;

public enum KeyTeleports {

	CRYSTAL_KEY(989, new Bounds(1389, 3248, 1389, 3248, 0)),
	SLAYER_KEY1(25426, new Bounds(1384, 3246, 1384, 3246, 0)),
	SLAYER_KEY2(25424, new Bounds(1384, 3246, 1384, 3246, 0)),
	SLAYER_KEY3(25432, new Bounds(1384, 3246, 1384, 3246, 0)),
	SLAYER_KEY4(25430, new Bounds(1384, 3246, 1384, 3246, 0)),
	ENH_CRYSTAL_KEY(23951, new Bounds(1388, 3244, 1388, 3244, 0)),
	BRIMSTONE_KEY(23083, new Bounds(1389, 3248, 1389, 3248, 0)),
	DONATOR_TABLET(30611, new Bounds(1248, 2604, 1248, 2604, 2)),
	HOME_TABLET(30612, new Bounds(1376, 3232, 1376, 3232, 0));

	public final int id;
	public final Bounds bounds;

	KeyTeleports(int id, Bounds bounds) {
		this.id = id;
		this.bounds = bounds;
	}

	private void teleportWithKey(Player player, Item key) {
		player.getMovement().startTeleport(event -> {
			player.animate(3864);
			player.graphics(1039);
			player.privateSound(200, 0, 10);
			event.delay(2);
			player.getMovement().teleport(bounds);
		});
	}

	private void breakTeleport(Player player, Item key) {
		if (key.getId() == DONATOR_TABLET.id) {
			// Check if the player is a donator before allowing teleportation
			if (!player.isDonator()) {
				player.sendMessage("You must be a donator to use this item.");
				return;
			}
		}

		player.getMovement().startTeleport(event -> {
			player.animate(3864);
			if (player.getInventory().contains(25104)) {
				player.sendMessage("The crystal of memories stores your last location as an available teleport.");
				player.crystalMemoryPosition = player.getPosition().copy();
			}
			player.graphics(1039);
			player.privateSound(200, 0, 10);
			event.delay(2);
			player.getMovement().teleport(bounds);
			HomeHandler.drinkFromPool(player);
		});
	}

	public static void register() {
		for (KeyTeleports key : values())
			ItemAction.registerInventory(key.id, "Teleport", key::teleportWithKey);
		// Adds another loop for the breakTeleport method to replenish with the effects of the ornate pool
		for (KeyTeleports key : values()) {
			if (key == DONATOR_TABLET || key == HOME_TABLET) {
				ItemAction.registerInventory(key.id, "Break", key::breakTeleport);
			}
		}
	}
}
