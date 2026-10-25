package io.ruin.model.content;

import io.ruin.model.item.actions.ItemAction;

/**
 * Zaryte Stormpiercer (item 31856, customs19 2026-10-19): the upgraded Zaryte bow. Its rip came with a worn model only,
 * so the inventory model is the worn mesh, which isn't made to lie on the ground: dropping it is refused. The client
 * still lists "Drop" (an item record without op4 gets the client's default), so the refusal lives here.
 */
public final class ZaryteStormpiercer {
	public static final int ITEM = 42656;

	private ZaryteStormpiercer() {
	}

	public static void register() {
		// option 5 = the Drop slot, by index: the record has no op4 string for a name lookup to find
		ItemAction.registerInventory(ITEM, 5, (player, item) -> player.sendMessage("The Stormpiercer can't be dropped."));
	}
}
