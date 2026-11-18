package io.ruin.model.activities.moonsofperil;

import io.ruin.model.item.loot.LootItem;
import io.ruin.model.item.loot.LootTable;

/**
 * Lunar Chest table for the ::loots viewer only (the chest itself rolls in {@link MoonsOfPerilHandler#lootShrine}).
 * Uniques: each subdued Moon gives a 1/56 roll at its own set, i.e. 1/224 per piece. Standard weights are /30.
 */
public class MoonsOfPerilRewards {

	public static final LootTable table = new LootTable()
		.addTable("Uniques (per Moon subdued)", 4,
			new LootItem(29028, 1, 1), new LootItem(29022, 1, 1), new LootItem(29025, 1, 1), new LootItem(28997, 1, 1),
			new LootItem(29019, 1, 1), new LootItem(29013, 1, 1), new LootItem(29016, 1, 1), new LootItem(28988, 1, 1),
			new LootItem(29010, 1, 1), new LootItem(29004, 1, 1), new LootItem(29007, 1, 1), new LootItem(29000, 1, 1))
		.addTable("Standard", 220,
			new LootItem(28991, 72, 120, 5),
			new LootItem(29381, 160, 179, 2),
			new LootItem(28900, 42, 54, 1),
			new LootItem(29379, 6, 12, 3),
			new LootItem(1939, 79, 119, 4),
			new LootItem(572, 30, 45, 2),
			new LootItem(6035, 6, 12, 3),
			new LootItem(1762, 15, 25, 3),
			new LootItem(206, 12, 18, 3),
			new LootItem(210, 12, 18, 1),
			new LootItem(5314, 1, 2, 2),
			new LootItem(5315, 1, 1, 1));
}
