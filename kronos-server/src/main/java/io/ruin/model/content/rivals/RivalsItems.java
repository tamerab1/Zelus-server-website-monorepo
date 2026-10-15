package io.ruin.model.content.rivals;

import io.ruin.cache.ObjType;
import io.ruin.model.entity.player.Player;
import io.ruin.model.item.Item;
import io.ruin.model.item.actions.ItemAction;
import io.ruin.model.item.actions.ItemItemAction;
import io.ruin.model.item.loot.LootItem;
import io.ruin.model.item.loot.LootTable;
import io.ruin.utility.Broadcast;

/**
 * Division Rivals shop items (2026-10-15; cache: .dev/cache-restore-tool rivals_items.txt).
 * <ul>
 * <li>Celestial ornament kit 31790: used on a Twisted bow, Armadyl godsword, Voidwaker or Harmonised nightmare staff it
 * turns it into the celestial version (navy and gold, the client's stardust sparkles) after a confirmation. The binding
 * is PERMANENT (no Dismantle: the celestial items have no such option), so kits can't be recycled. The weapon keeps
 * its attributes (charges, upgrades, perks): the item changes id in place.</li>
 * <li>Rivals mystery box 31792: opened on the mystery spinner (MysteryBoxSpinner, interface 1154); Examine shows the
 * table in the loot viewer (LootsTables.RIVALS_MYSTERY_BOX). PK supplies (common), high-tier seeds (uncommon), the
 * ornament kit or a Challenger cape (rare).</li>
 * </ul>
 */
public final class RivalsItems {

	/** {base weapon, celestial version}. */
	private static final int[][] CELESTIAL = {
		{20997, 31778},   // Twisted bow
		{11802, 31784},   // Armadyl godsword
		{27690, 31786},   // Voidwaker
		{24423, 31788},   // Harmonised nightmare staff
	};

	public static final LootTable BOX = new LootTable()
		.addTable(60,
			new LootItem(6686, 10, 20, 15),    // Saradomin brew(4) (noted)
			new LootItem(3025, 10, 20, 15),    // Super restore(4) (noted)
			new LootItem(3145, 20, 40, 15),    // Cooked karambwan (noted)
			new LootItem(13442, 15, 30, 15))   // Anglerfish (noted)
		.addTable(30,
			new LootItem(5295, 3, 6, 10),      // Ranarr seed
			new LootItem(5300, 2, 4, 8),       // Snapdragon seed
			new LootItem(5304, 1, 3, 7),       // Torstol seed
			new LootItem(5316, 1, 2, 5))       // Magic seed
		.addTable(3,
			new LootItem(RivalsShop.ORNAMENT_KIT, 1, 2),
			new LootItem(RivalsShop.CHALLENGER_CAPE, 1, 1));

	private RivalsItems() {
	}

	static void register() {
		for (int[] pair : CELESTIAL) {
			int base = pair[0], celestial = pair[1];
			ItemItemAction.register(RivalsShop.ORNAMENT_KIT, base, (player, kit, weapon) -> player.dialogue(
				new io.ruin.model.inter.dialogue.MessageDialogue("Are you sure you want to apply the Celestial Ornament Kit?<br>"
					+ "This will <col=990000>permanently</col> bind the ornament and <col=990000>CANNOT</col> be dismantled."),
				new io.ruin.model.inter.dialogue.OptionsDialogue("Bind the kit to your " + ObjType.get(base).name + "?",
					new io.ruin.model.inter.utils.Option("<col=00c000>Yes, bind permanently</col>", () -> bind(player, kit, weapon, base, celestial)),
					new io.ruin.model.inter.utils.Option("Cancel", player::closeDialogue))));
		}
		io.ruin.model.item.actions.impl.boxes.mystery.MysteryBoxSpinner.registerBox(RivalsShop.MYSTERY_BOX, "Rivals Mystery Box", BOX);
	}

	/** Re-checks both items are still there (the dialogue gives time to move them), then binds for good. */
	private static void bind(Player player, Item kit, Item weapon, int base, int celestial) {
		player.closeDialogue();
		if (kit.getId() != RivalsShop.ORNAMENT_KIT || weapon.getId() != base || !player.getInventory().contains(kit)
			|| !player.getInventory().contains(weapon)) {
			player.sendMessage("Keep the kit and the weapon in your inventory to bind them.");
			return;
		}
		kit.remove();
		weapon.setId(celestial); // in place: keeps every attribute the weapon carries
		player.getInventory().sendUpdates();
		player.sendMessage("<col=990000><shad=000000>[Rivals]</shad></col> Starlight settles over your "
			+ ObjType.get(base).name + ": it is now " + ObjType.get(celestial).name + ", for good.");
	}
}
