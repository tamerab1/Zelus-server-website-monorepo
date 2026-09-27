package io.ruin.model.item.actions.impl;

import io.ruin.cache.ObjType;
import io.ruin.model.inter.dialogue.ItemDialogue;
import io.ruin.model.inter.dialogue.YesNoDialogue;
import io.ruin.model.item.actions.ItemItemAction;

// Using a Draconic visage (11286) on a Draco-set item consumes the visage and upgrades the item in
// place (setId keeps its slot and any attributes, e.g. perks). Works in either use-order --
// ItemItemAction.register(primary, secondary) binds both sides.
public class DraconicVisageUpgrades {

	private static final int DRACONIC_VISAGE = 11286;

	// { from, to }
	private static final int[][] UPGRADES = {
		{60338, 60333}, // Draconies Hood      -> Draconic Helmet
		{60339, 60335}, // Draconies Platebody -> Draconic Platebody
		{60340, 60336}, // Draconies Platelegs -> Draconic Platelegs
		{60342, 60337}, // Draconies Wings     -> Draconic Wings
		{60405, 60400}, // Draco Pickaxe       -> Draconic Pickaxe
	};

	public static void register() {
		for (int[] upgrade : UPGRADES) {
			int from = upgrade[0], to = upgrade[1];
			ItemItemAction.register(DRACONIC_VISAGE, from, (player, visage, item) -> {
				String toName = ObjType.get(to).name;
				player.dialogue(new YesNoDialogue("Are you sure you want to do this?",
					"Attach the Draconic visage to create a " + toName + "? The visage will be consumed.", item, () -> {
						if (visage.getId() != DRACONIC_VISAGE || item.getId() != from)
							return; // items moved/changed while the dialogue was open
						visage.remove();
						item.setId(to);
						player.dialogue(new ItemDialogue().one(to, "You fuse the Draconic visage into it, creating a " + toName + "."));
					}));
			});
		}
	}
}
