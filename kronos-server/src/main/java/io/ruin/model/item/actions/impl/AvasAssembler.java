package io.ruin.model.item.actions.impl;

import io.ruin.model.item.actions.ItemAction;
import io.ruin.model.item.actions.ItemItemAction;

public class AvasAssembler {

	private static final int AVAS_ASSEMBLER = 22109;
	private static final int AVAS_ACCUMULATOR = 10499;
	private static final int[] VORKATH_HEADS = {21907, 2425, 21912};
	/** Ava's of Karn (2026-11-14): the top Ava's device, keeps every arrow, bolt and dart (PlayerCombat.hasKarnAva). */
	private static final int AVAS_OF_KARN = 42767;

	public static void register() {
		ItemAction commune = (player, item) -> player.sendMessage(
				"<col=b81d24>Ava's of Karn</col> hums in the Karn colours: it keeps every arrow, bolt and dart you fire.");
		ItemAction.registerInventory(AVAS_OF_KARN, "Commune", commune);
		ItemAction.registerEquipment(AVAS_OF_KARN, "Commune", commune);
		for (int VORKATH_HEAD : VORKATH_HEADS) {
			ItemItemAction.register(AVAS_ACCUMULATOR, VORKATH_HEAD, (player, primary, secondary) -> {
				primary.setId(AVAS_ASSEMBLER);
				secondary.remove();
				player.sendMessage("You use your Vorkath head on the Accumulator and create an Assembler.");
			});
		}
	}

}
