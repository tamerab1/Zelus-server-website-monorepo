package io.ruin.model.map.object.actions.impl.petperksarea;

import io.ruin.cache.ObjectID;
import io.ruin.model.map.object.actions.ObjectAction;
import io.ruin.utility.TeleportConstants;

/// New custom event/boss area for the pet-perk pets (2026-09-04). Registrations grow here as
/// the area is built out.
public class PetPerksArea {

	public static void register() {
		// Exit portal -> Mage Bank.
		ObjectAction.register(ObjectID.PORTAL_33037, "Enter",
				(player, obj) -> player.getMovement().teleport(TeleportConstants.MAGE_BANK));
	}
}
