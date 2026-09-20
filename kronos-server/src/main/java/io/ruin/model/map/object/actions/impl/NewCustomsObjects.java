package io.ruin.model.map.object.actions.impl;

import io.ruin.model.map.object.actions.ObjectAction;

// Kulyx Box, Prismatic Chest, and Suprise -- added 2026-09-18 as part of the NewCustoms batch.
// "Open" is wired up but intentionally has no loot yet; a real drop table is a follow-up.
public class NewCustomsObjects {

	private static final int KULYX_BOX_ID = 62388;
	// Remapped from source id 139780: that id sat 77392 away from its nearest sorted neighbor in
	// the OBJECT config archive, which silently overflowed IndexData's 16-bit delta encoding on
	// write and corrupted the id to 74244 (content was fine, just mislabeled) -- fixed by moving
	// it next to the other custom objects instead, well within a safe delta.
	private static final int PRISMATIC_CHEST_ID = 62389;
	private static final int SUPRISE_ID = 72000;

	public static void register() {
		for (int id : new int[]{KULYX_BOX_ID, PRISMATIC_CHEST_ID, SUPRISE_ID}) {
			ObjectAction.register(id, "Open", (player, obj) -> {
				player.sendMessage("You open it, but there's nothing inside... yet.");
			});
		}
	}
}
