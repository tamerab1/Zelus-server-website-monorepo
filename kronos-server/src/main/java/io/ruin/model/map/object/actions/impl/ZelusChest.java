package io.ruin.model.map.object.actions.impl;

import io.ruin.api.utils.Random;
import io.ruin.cache.Icon;
import io.ruin.cache.ItemID;
import io.ruin.model.entity.player.Player;
import io.ruin.model.item.Item;
import io.ruin.model.item.actions.ItemAction;
import io.ruin.model.map.object.actions.ObjectAction;
import io.ruin.utility.Broadcast;

public class ZelusChest {

	private static final int ZELUS_CHEST_ID = 60012;
	private static final int Z_GOLDEN_KEY = 59960;

	// Same tiers/odds as the Enhanced crystal key chest (io.ruin.model.map.object.actions.impl.
	// edgeville.EnhancedCrystalKeyChest) -- duplicated rather than shared since that class's tables
	// are private and this chest additionally needs the SUPER_RARE_LOOT tier below it doesn't have.
	private static final Item[] COMMON_LOOT = {
		new Item(1632, 50), //Uncut dragon stones
		new Item(21905, 100), //Dragon bolts
		new Item(11230, 125), //Dragon dart
		new Item(23962, 30), //Crystal shards
		new Item(23951, 2), //Enhanced crystal keys
		new Item(5315, 10), //Yew seeds
		new Item(5316, 5), //Magic seeds
		new Item(537, 100), //Dragon bones
		new Item(2364, 75), //Runite bars
		new Item(5300, 20), //Snapdragon seeds
		new Item(5295, 20), //Ranarr seeds
		new Item(220, 30), //Grimy Torstols
		new Item(8783, 200), //Mahogany planks
		new Item(1514, 250), //Magic logs
		new Item(384, 300), //Raw shark
		new Item(13442, 40), //Anglerfish
		new Item(12914, 5), //Anti venom+
		new Item(30596, 2), //Vote Buff Streak Box
		new Item(20545, 3), //Medium clue reward casket
	};
	private static final Item[][] RUNE_LOOTS = {
		new Item[]{
			new Item(560, 1500), //Death runes
			new Item(565, 1500), //Blood Runes
			new Item(566, 1500), //Soul Runes
			new Item(21880, 1000) //Wrath runes
		}
	};
	private static final Item[][] POTION_LOOTS = {
		new Item[]{
			new Item(6686, 30), //Saradomin Brew
			new Item(3025, 30), //Super restore
		}
	};
	private static final Item[][] GEM_LOOTS = {
		new Item[]{
			new Item(1622, 150), //Uncut Emerald
			new Item(1620, 150), //Uncut Ruby
			new Item(1618, 75), //Uncut Diamond
		}
	};
	private static final Item[] UNCOMMON_LOOT = {
		new Item(59979, 1), //Solarflare pet
		new Item(13440, 250), //Raw anglerfish
		new Item(11840, 1), //Dragon boots
		new Item(11944, 75), //Lava dragon bones
		new Item(6739, 1), //Dragon axe
		new Item(12914, 20), //Anti venom+
		new Item(23962, 69), //Crystal shard
		new Item(ItemID.DRAGON_PLATELEGS + 1, 6), //Dragon platelegs
		new Item(3205, 5), //Dragon Halberd
		new Item(9193, 750), //Dragonstone bolt tips
		new Item(19484, 1150), //Dragon javelins
		new Item(20544, 3), //Hard clue reward casket
		new Item(12831, 1), //Blessed spirit shield
		new Item(2510, 250), //Black dragon leather
		new Item(6571, 1), //Uncut onyx
		new Item(2577, 1), //Ranger boots
		new Item(12598, 1), //Holy sandals
		new Item(6735, 1), //Warrior ring
		new Item(6737, 1), //Berserker ring
		new Item(6731, 1), //Seers ring
		new Item(6733, 1), //Archers ring
		new Item(22125, 75), //Superior dragon bones
		new Item(11230, 500), //Dragon dart
		new Item(21905, 375) //Dragon bolts
	};
	private static final Item[] RARE_LOOT = {
		new Item(19836, 4), //Master clue reward casket
		new Item(11335, 1), //Dragon full helm
		new Item(11286, 1), //Draconic visage
		new Item(12004, 1), //Kraken tentacle
		new Item(20543, 4), //Elite clue reward casket
		new Item(30582, 1), //GWD Mystery Box
		new Item(59973, 1), //Blossom pet
	};
	private static final Item[] VERY_RARE_LOOT = {
		new Item(24034, 1), //Dragonstone full helm
		new Item(24037, 1), //Dragonstone platebody
		new Item(24040, 1), //Dragonstone platelegs
		new Item(24043, 1), //Dragonstone boots
		new Item(24046, 1), //Dragonstone gauntlets
		new Item(23911, 1), //Crystal crown
		new Item(23962, 200), //Crystal shard
	};

	// Lowest-rarity tier -- custom set/weapon items, rolled before (and rarer than) VERY_RARE_LOOT.
	private static final Item[] SUPER_RARE_LOOT = {
		new Item(24539, 1), //Summer sword
		new Item(59568, 1), //Tempest Bow
		new Item(59620, 1), //Gilded Helix bow
		new Item(59621, 1), //Gilded Helix rapier
		new Item(59622, 1), //Gilded Helix staff
		new Item(59604, 1), //Imperial staff
		new Item(59605, 1), //Imperial bow
		new Item(59606, 1), //Imperial top
		new Item(59607, 1), //Imperial robe
		new Item(59608, 1), //Imperial boots
		new Item(59588, 1), //Morveth Fullhelm
		new Item(59589, 1), //Morveth Platebody
		new Item(59590, 1), //Morveth Platelegs
	};

	public static void register() {
		ObjectAction.register(ZELUS_CHEST_ID, "Open", (player, obj) -> {
			Item key = player.getInventory().findItem(Z_GOLDEN_KEY);
			if (key == null) {
				player.sendFilteredMessage("You need a Z Golden key to open this chest.");
				return;
			}
			player.getInventory().remove(Z_GOLDEN_KEY, 1);
			player.sendFilteredMessage("You unlock the Zelus Chest with your Z Golden key.");
			player.zelusChestsOpened++;
			openChest(player);
		});
		ObjectAction.register(ZELUS_CHEST_ID, "Check", (player, obj) -> {
			player.sendMessage("You have opened " + player.zelusChestsOpened + " Zelus Chests.");
		});
		ItemAction.registerInventory(Z_GOLDEN_KEY, "Teleport", (player, item) -> {
			player.getMovement().startTeleport(e -> {
				player.animate(714);
				player.graphics(111, 92, 0);
				player.publicSound(200);
				e.delay(3);
				player.getMovement().teleport(3083, 3486, 0);
			});
		});
	}

	private static void openChest(Player player) {
		int amount = Random.get(300000, 500000);
		player.getInventory().addOrDrop(995, amount);
		if (Random.rollDie(750, 1)) {
			/**
			 * Super rare loot
			 */
			Item loot = SUPER_RARE_LOOT[Random.get(SUPER_RARE_LOOT.length - 1)];
			player.getInventory().addOrDrop(loot.getId(), loot.getAmount());
			player.addToCollectionLog(loot);
			Broadcast.GLOBAL.sendNewsDropMessage(player, Icon.ADMINISTRATOR, player.getName(), " just received " + loot.getDef().descriptiveName + " from the Zelus Chest on [Chest #" + player.zelusChestsOpened + "]!");

		} else if (Random.rollDie(225, 1)) {
			/**
			 * Very rare loot
			 */
			Item loot = VERY_RARE_LOOT[Random.get(VERY_RARE_LOOT.length - 1)];
			player.getInventory().addOrDrop(loot.getId(), loot.getAmount());
			player.addToCollectionLog(loot);
			Broadcast.GLOBAL.sendNewsDropMessage(player, Icon.ADMINISTRATOR, player.getName(), " just received " + loot.getDef().descriptiveName + " from the Zelus Chest on [Chest #" + player.zelusChestsOpened + "]!");

		} else if (Random.rollDie(100, 1)) {
			/**
			 * Rare loot
			 */
			Item loot = RARE_LOOT[Random.get(RARE_LOOT.length - 1)];
			player.getInventory().addOrDrop(loot.getId(), loot.getAmount());

		} else if (Random.rollDie(25, 1)) {
			/**
			 * Uncommon loot
			 */
			Item loot = UNCOMMON_LOOT[Random.get(UNCOMMON_LOOT.length - 1)];
			player.getInventory().addOrDrop(loot.getId(), loot.getAmount());

		} else if (Random.rollDie(10, 1)) {
			/**
			 * Rune loot
			 */
			Item[] loot = RUNE_LOOTS[0];
			for (Item item : loot)
				player.getInventory().addOrDrop(item.getId(), item.getAmount());

		} else if (Random.rollDie(10, 1)) {
			/**
			 * Potion loot
			 */
			Item[] loot = POTION_LOOTS[0];
			for (Item item : loot)
				player.getInventory().addOrDrop(item.getId(), item.getAmount());

		} else if (Random.rollDie(10, 1)) {
			/**
			 * Gem loots
			 */
			Item[] loot = GEM_LOOTS[0];
			for (Item item : loot)
				player.getInventory().addOrDrop(item.getId(), item.getAmount());

		} else {
			/**
			 * Common loot
			 */
			Item loot = COMMON_LOOT[Random.get(COMMON_LOOT.length - 1)];
			player.getInventory().addOrDrop(loot.getId(), loot.getAmount());
		}
	}
}
