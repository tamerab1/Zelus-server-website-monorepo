package io.ruin.model.map.object.actions.impl;

import io.ruin.cache.Icon;
import io.ruin.model.item.Item;
import io.ruin.model.item.loot.LootItem;
import io.ruin.model.item.loot.LootTable;
import io.ruin.model.map.object.actions.ObjectAction;
import io.ruin.utility.Broadcast;

// Draco Chest (object 62389, the former NewCustoms "Prismatic Chest"): opened with a Draco Key
// (item 3460, renamed from "Black key red"), which is consumed. Examining the chest opens this
// table in the loots viewer (LootsTables.DRACO_CHEST, see ObjectActionHandler.handleExamine).
//
// Rates are independent "1 in X" chances per open. LootTable is weight-based, so each item gets
// weight ceil(SCALE / X) inside one rewards table, and an empty table takes the remaining weight --
// that makes every item's real chance (and the viewer's displayed 1/X) match the configured rate.
public class DracoChest {

	public static final int DRACO_CHEST_ID = 62389;
	public static final int DRACO_KEY = 3460;

	private static final int SCALE = 10_000_000;

	// { itemId, 1-in-X rate }
	private static final int[][] REWARDS = {
		{60335, 599}, // Draconic platebody
		{60336, 599}, // Draconic platelegs
		{60333, 529}, // Draconic helmet
		{26219, 469}, // Osmumten's fang
		{21006, 412}, // Kodai wand
		{30514, 399}, // Corrupted offhand
		{30380, 379}, // Superior overload heart
		{21003, 329}, // Elder maul
		{12821, 319}, // Spectral spirit shield
		{22954, 312}, // Devout boots
		{22981, 289}, // Ferocious gloves
		{13652, 279}, // Dragon claws
		{11802, 269}, // Armadyl godsword
		{13243, 190}, // Infernal pickaxe
		{59960, 180}, // Z Golden key
	};

	public static final LootTable DRACO_CHEST_TABLE = buildTable();

	private static LootTable buildTable() {
		LootItem[] items = new LootItem[REWARDS.length];
		int rewardsWeight = 0;
		for (int i = 0; i < REWARDS.length; i++) {
			int weight = (SCALE + REWARDS[i][1] - 1) / REWARDS[i][1];
			items[i] = new LootItem(REWARDS[i][0], 1, weight).broadcast(Broadcast.GLOBAL);
			rewardsWeight += weight;
		}
		// Common table: whatever weight the rares don't use -- one of these on every non-rare open.
		return new LootTable()
			.addTable(rewardsWeight, items)
			.addTable(SCALE - rewardsWeight,
				new LootItem(995, 50_000, 150_000, 1), // Coins
				new LootItem(386, 5, 10, 1));          // Shark (noted)
	}

	public static void register() {
		ObjectAction.register(DRACO_CHEST_ID, "Open", (player, obj) -> {
			if (!player.getInventory().contains(DRACO_KEY, 1)) {
				player.sendMessage("You need a Draco Key to open this chest.");
				return;
			}
			player.getInventory().remove(DRACO_KEY, 1);
			Item loot = DRACO_CHEST_TABLE.rollItem();
			if (loot == null)
				return;
			player.getInventory().addOrDrop(loot.getId(), loot.getAmount());
			player.addToCollectionLog(loot);
			boolean common = loot.getId() == 995 || loot.getId() == 386;
			player.sendMessage("You unlock the Draco Chest and find " + (common ? loot.getAmount() + " x " + loot.getDef().name : loot.getDef().descriptiveName) + ".");
			if (!common)
				Broadcast.GLOBAL.sendNewsDropMessage(player, Icon.ADMINISTRATOR, player.getName(),
					" just received " + loot.getDef().descriptiveName + " from the Draco Chest!");
		});
	}
}
