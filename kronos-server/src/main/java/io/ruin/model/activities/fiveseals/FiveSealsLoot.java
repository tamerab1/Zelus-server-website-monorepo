package io.ruin.model.activities.fiveseals;

import io.ruin.api.utils.Random;
import io.ruin.cache.Color;
import io.ruin.cache.Icon;
import io.ruin.cache.ObjType;
import io.ruin.model.entity.player.Player;
import io.ruin.model.item.Item;
import io.ruin.model.item.actions.impl.pet.Pet;
import io.ruin.model.item.loot.LootItem;
import io.ruin.model.item.loot.LootTable;
import io.ruin.utility.Broadcast;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The Five Seals' drop table, raid style (user 2026-11-17: "make a drop table for the chest like the raids ... add
 * common/uncommon/rare rewards too"). Every payout keeps its fixed pay (Ascension shards, coins, perk points, a perk lamp
 * from seal 3, all in FiveSealsRun.payout) and adds {@link #rolls} rolls on {@link #TABLE}; a full clear also rolls for a
 * purple ({@link #UNIQUES}) and the pet.
 * <ul>
 * <li>{@link #TABLE}: common (70) noted supplies and resources, uncommon (25) gems / seeds / herbs / ammo, rare (5) the
 * Item Upgrader materials, perk progression and bonus shards. Common and uncommon amounts grow
 * with the run's multiplier.</li>
 * <li>Purple: 1 in {@link #UNIQUE_RATE} full clears (+10% in a group), then {@link #UNIQUES} by weight (28 in all). The
 * user wanted these rarer than ToA's Masori pieces and Tumeken's shadow (ToA 300: purple 1/27, Masori piece ~1/279,
 * shadow ~1/837): Duskwarden / Hellfang helm ~1/350, body / legs / kiteshield ~1/467, blade / crossbow ~1/700,
 * Decimation ~1/1400.</li>
 * <li>Pet: Lil' Vorgath, 1 in {@link #PET_RATE} full clears (straight to the player, Pet.unlock).</li>
 * </ul>
 * The vault window (inv 1029) holds six stacks: shards, coins, lamp + up to three rolls; a purple takes one roll's place.
 */
final class FiveSealsLoot {

	private FiveSealsLoot() {
	}

	static final int UNIQUE_RATE = 50, PET_RATE = 1500, VORGATH_NPC = 20000;

	static final LootTable UNIQUES = new LootTable().addTable(1,
		new LootItem(42696, 1, 4),   // Duskwarden helm
		new LootItem(42698, 1, 3),   // Duskwarden platebody
		new LootItem(42700, 1, 3),   // Duskwarden platelegs
		new LootItem(42704, 1, 3),   // Duskwarden kiteshield
		new LootItem(42702, 1, 2),   // Duskwarden blade
		new LootItem(42706, 1, 4),   // Hellfang helm
		new LootItem(42708, 1, 3),   // Hellfang platebody
		new LootItem(42710, 1, 3),   // Hellfang platelegs
		new LootItem(42712, 1, 2),   // Hellfang crossbow
		new LootItem(40063, 1, 1));  // Decimation

	/** every purple, for the collection log page (CollectionLogData.THE_FIVE_SEALS) and broadcasts */
	static final Set<Integer> UNIQUE_IDS = Set.of(42696, 42698, 42700, 42702, 42704, 42706, 42708, 42710, 42712, 40063);

	/** rare-table items: fixed amounts (not scaled by the multiplier) */
	private static final Set<Integer> FIXED = Set.of(43832, 30549, 43820, 7968, 34669, 30570);

	static final LootTable TABLE = new LootTable()
		.addTable("common", 70,
			new LootItem(385, 20, 40, 1),      // Shark
			new LootItem(6685, 6, 12, 1),      // Saradomin brew(4)
			new LootItem(3024, 6, 12, 1),      // Super restore(4)
			new LootItem(2434, 8, 15, 1),      // Prayer potion(4)
			new LootItem(451, 8, 20, 1),       // Runite ore
			new LootItem(1513, 25, 60, 1),     // Magic logs
			new LootItem(536, 15, 35, 1),      // Dragon bones
			new LootItem(453, 60, 150, 1),     // Coal
			new LootItem(1391, 8, 20, 1))      // Battlestaff
		.addTable("uncommon", 25,
			new LootItem(1631, 4, 10, 1),      // Uncut dragonstone
			new LootItem(11230, 80, 180, 1),   // Dragon dart
			new LootItem(5295, 4, 10, 1),      // Ranarr seed
			new LootItem(5304, 2, 6, 1),       // Torstol seed
			new LootItem(207, 15, 35, 1),      // Grimy ranarr weed
			new LootItem(219, 10, 25, 1),      // Grimy torstol
			new LootItem(9244, 40, 100, 1),    // Dragonstone bolts (e)
			new LootItem(21880, 60, 150, 1))   // Wrath rune
		.addTable("rare", 5,
			new LootItem(43832, 3, 8, 4),      // Ethereal essence (Item Upgrader)
			new LootItem(30549, 1, 3, 4),      // Dagannoth tooth (Item Upgrader)
			new LootItem(43820, 1, 1, 3),      // Perk exp lamp
			new LootItem(7968, 1, 1, 3),       // Perk task skip scroll
			new LootItem(34669, 200, 500, 3),  // Ascension shards
			new LootItem(30570, 1, 2, 2));     // Perk point scroll (as ToA's table; not the stacking +5% drop rate scroll)

	/** 1 roll for seals 1-2, 2 for seals 3-4, 3 for a full clear */
	static int rolls(int seals) {
		return seals >= 5 ? 3 : seals >= 3 ? 2 : 1;
	}

	/**
	 * Adds the table rolls (and on a full clear the purple and pet rolls) to {@code loot}. {@code pm} = the player's final
	 * pay multiplier; common / uncommon amounts are x max(1, pm / 2).
	 */
	static void roll(Player p, List<Item> loot, int seals, double pm, boolean fullClear, boolean group) {
		int rolls = rolls(seals);
		if (fullClear) {
			double chance = (group ? 1.10 : 1.0) / UNIQUE_RATE;
			if (Random.get() < chance) {
				Item unique = UNIQUES.rollItem();
				merge(loot, unique.getId(), 1);
				p.addToCollectionLog(new Item(unique.getId(), 1));
				announce(p, unique.getId());
				rolls--;
			}
			if (Random.get() < (group ? 1.10 : 1.0) / PET_RATE)
				Pet.LIL_VORGATH.unlock(p, VORGATH_NPC);
		}
		double scale = Math.max(1.0, pm / 2.0);
		int favour = 1;
		if (fullClear && p.fiveSealsFavours > 0) {         // Vault Favour (Rewards tab): this clear's table loot x2
			p.fiveSealsFavours--;
			favour = 2;
			p.sendMessage(Color.RAID_PURPLE.wrap("Your Vault Favour doubles the vault's table loot (" + p.fiveSealsFavours + " left)."));
		}
		for (int i = 0; i < rolls; i++) {
			Item item = TABLE.rollItem();
			if (item == null)
				continue;
			int id = item.getId(), amount = item.getAmount();
			if (!FIXED.contains(id))
				amount = (int) Math.round(amount * scale);
			amount *= favour;
			ObjType def = ObjType.get(id);
			if (def != null && !def.stackable && def.notedId > 0)
				id = def.notedId; // supplies come noted
			merge(loot, id, amount);
		}
	}

	static void merge(List<Item> loot, int id, int amount) {
		if (amount <= 0)
			return;
		for (Item item : loot)
			if (item.getId() == id) {
				item.setAmount(item.getAmount() + amount);
				return;
			}
		loot.add(new Item(id, amount));
	}

	private static void announce(Player p, int id) {
		ObjType def = ObjType.get(id);
		String name = def == null ? "a unique" : def.descriptiveName != null ? def.descriptiveName : def.name;
		p.sendMessage(Color.RAID_PURPLE.wrap("Something special shines in the Sovereign Vault: " + (def == null ? "?" : def.name) + "!"));
		Broadcast.WORLD.sendNewsDropMessage(p, Icon.RARE_DROP, "<col=000000>" + p.getName(), " just received " + name + " from The Five Seals!");
	}

	/** the full-clear completion count (collection log page "The Five Seals") */
	static void countClear(Player p) {
		p.fiveSealsClears.increment(p);
	}

	static List<Item> newLoot() {
		return new ArrayList<>();
	}
}
