package io.ruin.model.activities.tournament;

import io.ruin.cache.Icon;
import io.ruin.cache.ItemID;
import io.ruin.cache.ObjType;
import io.ruin.model.inter.handlers.LootsTables;
import io.ruin.model.item.Item;
import io.ruin.model.item.actions.ItemAction;
import io.ruin.model.item.loot.LootItem;
import io.ruin.model.item.loot.LootTable;
import io.ruin.utility.Broadcast;

/**
 * Tournament Chest (31758, a retextured clone of the Advanced donator mystery chest): awarded to the champion
 * and runner-up of a Boxing Pit Championship. One roll per chest (weights out of 10,000):
 * <ul>
 *     <li>85% common: 2-5M coins, 250-750 blood money or 1-3 Astral spores</li>
 *     <li>10% Brawler's Golden Gloves (T2)</li>
 *     <li>2.75% Obsidian Heavy Gloves (T3), 1.25% Infernal Fury Gloves (T4)</li>
 *     <li>1% Cosmic Champion Gloves (T5)</li>
 * </ul>
 * Examine and "Look-in" open this table in the loot viewer (LootsTables.BOXING_MYSTERY_CHEST).
 */
public final class BoxingMysteryChest {

	public static final int ID = 31758;
	private static final int ASTRAL_SPORE = 31748;
	private static final int GOLDEN_GLOVES = 31750, OBSIDIAN_GLOVES = 31752, INFERNAL_GLOVES = 31754, COSMIC_GLOVES = 31756;

	public static final LootTable TABLE = new LootTable()
		.addTable(1_500,
			new LootItem(COSMIC_GLOVES, 1, 100),
			new LootItem(INFERNAL_GLOVES, 1, 125),
			new LootItem(OBSIDIAN_GLOVES, 1, 275),
			new LootItem(GOLDEN_GLOVES, 1, 1_000))
		.addTable(8_500,
			new LootItem(ItemID.COINS_995, 2_000_000, 5_000_000, 1),
			new LootItem(ItemID.BLOOD_MONEY, 250, 750, 1),
			new LootItem(ASTRAL_SPORE, 1, 3, 1));

	private BoxingMysteryChest() {
	}

	static void register() {
		ItemAction.registerInventory(ID, 1, (player, item) -> {
			if (player.getInventory().getFreeSlots() < 1) {
				player.sendMessage("You need a free inventory slot to open the chest.");
				return;
			}
			Item reward = TABLE.rollItem();
			if (reward == null)
				return;
			item.remove(1);
			player.getInventory().add(reward.getId(), reward.getAmount());
			String name = ObjType.get(reward.getId()).name;
			player.sendMessage("You open the Tournament Chest and find " + (reward.getAmount() > 1
				? String.format("%,d x ", reward.getAmount()) : "") + name + ".");
			if (reward.getId() == INFERNAL_GLOVES || reward.getId() == COSMIC_GLOVES)
				Broadcast.GLOBAL.sendNews(Icon.YELLOW_KEY, "The Pit", player.getName() + " found " + name + " in a Tournament Chest!");
		});
		ItemAction.registerInventory(ID, 2, (player, item) ->
			player.getLootsViewer().updateInterface(player, LootsTables.BOXING_MYSTERY_CHEST, "Tournament Chest"));
	}
}
