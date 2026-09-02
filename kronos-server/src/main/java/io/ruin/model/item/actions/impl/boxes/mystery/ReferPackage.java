package io.ruin.model.item.actions.impl.boxes.mystery;

import io.ruin.model.item.actions.ItemAction;
import io.ruin.model.item.actions.impl.DonatorBond;
import io.ruin.cache.ItemID;

/**
 * Refer Package (item id {@link ItemID#REFER_PACKAGE}, 60235) - the referrer's reward from the
 * refer-a-friend system (see io.ruin.model.content.referral.ReferralSystem). Not sold, dropped,
 * or granted by any other content in this codebase - keep it that way so this stays the one and
 * only source, per the anti-abuse design of that system.
 *
 * A brand-new item id, cloned visually from item 290's model - NOT a repurposed existing item.
 * (An earlier attempt reused id 290 directly and collided with the live "Super Mystery Box" -
 * see SuperMysteryBox.java + ObjType.java's "if (id == 290)" override - hence going the
 * brand-new-id route instead.)
 *
 * Contents are fixed (not a loot roll) - always the same 3 items on open.
 */
public class ReferPackage {

	private static final int ITEM_ID = ItemID.REFER_PACKAGE;

	private static final int MYSTERY_BOX_ITEM_ID = ItemID.TOB_REFUND_CHEST; // id 6199, "Mystery box"
	private static final int DONATOR_TICKET_AMOUNT = 250;
	private static final int Z_GOLDEN_KEY_ITEM_ID = 59960; // see ZelusChest.Z_GOLDEN_KEY

	private static final int REQUIRED_FREE_SLOTS = 3;

	public static void register() {
		ItemAction.registerInventory(ITEM_ID, "open", (player, item) -> {
			if (player.getInventory().getFreeSlots() < REQUIRED_FREE_SLOTS) {
				player.sendMessage("You need at least " + REQUIRED_FREE_SLOTS
						+ " free inventory slots to open your Refer Package.");
				return;
			}
			item.remove();
			player.getInventory().add(MYSTERY_BOX_ITEM_ID, 1);
			player.getInventory().add(DonatorBond.DONATOR_TICKET_ID, DONATOR_TICKET_AMOUNT);
			player.getInventory().add(Z_GOLDEN_KEY_ITEM_ID, 1);
			player.sendMessage("You open your Refer Package and receive a Mystery Box, "
					+ DONATOR_TICKET_AMOUNT + " Donator Tickets and a Golden Key!");
		});
	}

	private ReferPackage() {
	}

}
