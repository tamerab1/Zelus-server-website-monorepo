package io.ruin.model.content.rivals;

import io.ruin.cache.ItemID;
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
 * <li>Celestial ornament kit 31790: used on a Twisted bow, Armadyl godsword, Voidwaker, Harmonised nightmare staff or Nightfall bow it
 * turns it into the celestial version (navy and gold, the client's stardust sparkles) after a confirmation. The binding
 * is PERMANENT (no Dismantle: the celestial items have no such option), so kits can't be recycled. The weapon keeps
 * its attributes (charges, upgrades, perks): the item changes id in place.</li>
 * <li>Rivals mystery box 31792: opened on the mystery spinner (MysteryBoxSpinner, interface 1154); Examine shows the
 * table in the loot viewer (LootsTables.RIVALS_MYSTERY_BOX). Tiers: see {@link #BOX}.</li>
 * </ul>
 */
public final class RivalsItems {

	/** {base weapon, celestial version}. */
	private static final int[][] CELESTIAL = {
		{20997, 42578},   // Twisted bow
		{11802, 42584},   // Armadyl godsword
		{27690, 42586},   // Voidwaker
		{24423, 42588},   // Harmonised nightmare staff
		{59626, 42618},   // Nightfall bow -> Nightfall bow (celestial): own look, attack animation, arrows and special
	};

	/**
	 * Rivals mystery box (rebalanced 2026-10-17): PvP / Blood Money / Rivals only, four tiers out of 100. The Challenger
	 * cape is no longer in it -- Rivals capes come from rank (the shop's division gates) only.
	 */
	public static final LootTable BOX = new LootTable()
		.addTable("Common", 70,
			new LootItem(ItemID.BLOOD_MONEY, 5_000, 15_000, 25),
			new LootItem(12696, 5, 10, 15),    // Super combat potion(4) (noted)
			new LootItem(24621, 50, 100, 15),  // Blighted vengeance sack
			new LootItem(24599, 10, 20, 15),   // Blighted super restore(4) (noted)
			new LootItem(13442, 20, 40, 15),   // Anglerfish (noted)
			new LootItem(3145, 20, 40, 15))    // Cooked karambwan (noted)
		.addTable("Uncommon", 22,
			new LootItem(ItemID.BLOOD_MONEY, 50_000, 100_000, 30),
			new LootItem(24607, 50, 100, 15),  // Blighted ancient ice sack
			new LootItem(24590, 30, 60, 15),   // Blighted manta ray (noted)
			new LootItem(12849, 1, 15),        // Granite clamp
			new LootItem(12786, 1, 15))        // Magic shortbow scroll
		.addTable("Rare", 7,
			new LootItem(ItemID.BLOOD_MONEY, 250_000, 500_000, 20),
			new LootItem(24229, 1, 15),        // Ornate maul handle
			new LootItem(RivalsShop.ORNAMENT_KIT, 1, 10),
			new LootItem(12757, 1, 5),         // Blue dark bow paint
			new LootItem(12759, 1, 5),         // Green dark bow paint
			new LootItem(12761, 1, 5),         // Yellow dark bow paint
			new LootItem(12763, 1, 5),         // White dark bow paint
			new LootItem(21813, 1, 10),        // Ancient statuette
			new LootItem(22613, 1, 5),         // Vesta's longsword
			new LootItem(22622, 1, 5),         // Statius's warhammer
			new LootItem(22647, 1, 5),         // Zuriel's staff
			new LootItem(22636, 50, 100, 5))   // Morrigan's javelin
		.addTable("Unique", 1,
			new LootItem(30221, 1, 40),        // Title: the Rival
			new LootItem(ItemID.BLOOD_MONEY, 1_000_000, 30),
			new LootItem(22305, 1, 30));       // Ancient relic

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
