package io.ruin.model.activities.newshop.shops;

import io.ruin.cache.ItemID;
import io.ruin.model.activities.newshop.NewShop;
import io.ruin.model.activities.newshop.ShopCategories;
import io.ruin.model.activities.newshop.ShopItem;
import io.ruin.model.entity.player.Player;
import io.ruin.model.item.Item;

import java.util.Arrays;
import java.util.List;

public class PvmPointStore extends NewShop {

    @Override
    public List<ShopItem> getShopItemList() {
        return Arrays.asList(
            // Consumables
            new ShopItem(new Item(ItemID.SUPER_RESTORE4), 15, ShopCategories.CONSUMABLES, null, true),
            new ShopItem(new Item(ItemID.PRAYER_POTION4), 12, ShopCategories.CONSUMABLES, null, true),
            new ShopItem(new Item(ItemID.SARADOMIN_BREW4), 18, ShopCategories.CONSUMABLES, null, true),
            new ShopItem(new Item(ItemID.STAMINA_POTION4), 20, ShopCategories.CONSUMABLES, null, true),
            new ShopItem(new Item(ItemID.EXTENDED_SUPER_ANTIFIRE4), 25, ShopCategories.CONSUMABLES, null, true),
            new ShopItem(new Item(ItemID.BASTION_POTION4), 20, ShopCategories.CONSUMABLES, null, true),
            new ShopItem(new Item(ItemID.BATTLEMAGE_POTION4), 20, ShopCategories.CONSUMABLES, null, true),
            new ShopItem(new Item(ItemID.ANGLERFISH), 8, ShopCategories.CONSUMABLES, null, true),
            new ShopItem(new Item(ItemID.SHARK), 5, ShopCategories.CONSUMABLES, null, true),

            // Keys & Rewards
            new ShopItem(new Item(ItemID.CRYSTAL_KEY), 50, ShopCategories.TREASURES, null, true),
            new ShopItem(new Item(ItemID.BRIMSTONE_KEY), 40, ShopCategories.TREASURES, null, true),
            new ShopItem(new Item(30452), 400, ShopCategories.TREASURES, null, true),   // Mystery box

            // Notable equipment
            new ShopItem(new Item(ItemID.BERSERKER_RING), 500, ShopCategories.EQUIPMENT, null, true),
            new ShopItem(new Item(ItemID.ARCHERS_RING), 400, ShopCategories.EQUIPMENT, null, true),
            new ShopItem(new Item(ItemID.SEERS_RING), 400, ShopCategories.EQUIPMENT, null, true),
            new ShopItem(new Item(ItemID.WARRIOR_RING), 300, ShopCategories.EQUIPMENT, null, true),
            new ShopItem(new Item(ItemID.AMULET_OF_TORTURE), 2500, ShopCategories.EQUIPMENT, null, true),
            new ShopItem(new Item(ItemID.NECKLACE_OF_ANGUISH), 2000, ShopCategories.EQUIPMENT, null, true),
            new ShopItem(new Item(ItemID.TORMENTED_BRACELET), 2000, ShopCategories.EQUIPMENT, null, true),
            new ShopItem(new Item(ItemID.PRIMORDIAL_BOOTS), 3000, ShopCategories.EQUIPMENT, null, true),
            new ShopItem(new Item(ItemID.PEGASIAN_BOOTS), 2500, ShopCategories.EQUIPMENT, null, true),
            new ShopItem(new Item(ItemID.ETERNAL_BOOTS), 2500, ShopCategories.EQUIPMENT, null, true),

            // Raid items
            new ShopItem(new Item(ItemID.TWISTED_BUCKLER), 4000, ShopCategories.EQUIPMENT, null, true),
            new ShopItem(new Item(ItemID.KODAI_INSIGNIA), 5000, ShopCategories.EQUIPMENT, null, true),
            new ShopItem(new Item(ItemID.DRAGON_HUNTER_CROSSBOW), 5000, ShopCategories.EQUIPMENT, null, true),
            new ShopItem(new Item(ItemID.ANCESTRAL_HAT), 6000, ShopCategories.EQUIPMENT, null, true),
            new ShopItem(new Item(ItemID.ANCESTRAL_ROBE_TOP), 8000, ShopCategories.EQUIPMENT, null, true),
            new ShopItem(new Item(ItemID.ANCESTRAL_ROBE_BOTTOM), 7000, ShopCategories.EQUIPMENT, null, true),
            new ShopItem(new Item(ItemID.JUSTICIAR_FACEGUARD), 5000, ShopCategories.EQUIPMENT, null, true),
            new ShopItem(new Item(ItemID.JUSTICIAR_CHESTGUARD), 7000, ShopCategories.EQUIPMENT, null, true),
            new ShopItem(new Item(ItemID.JUSTICIAR_LEGGUARDS), 6000, ShopCategories.EQUIPMENT, null, true)
        );
    }

    /** Exclusive to PVM Mode accounts — PVP Mode accounts never earn or spend PvM Points. */
    @Override
    public boolean canOpen(Player player) {
        return player.isPvmMode();
    }

    @Override
    public boolean buy(Player player, ShopItem item, int amount) {
        if (!player.isPvmMode()) {
            player.sendMessage("The PVM Point Shop is exclusive to PVM Mode accounts.");
            return false;
        }
        int cost = item.getCost() * amount;
        if (player.PvmPoints < cost) {
            int canAfford = player.PvmPoints / item.getCost();
            if (canAfford == 0) {
                player.sendMessage("You don't have enough PVM points to buy this item.");
                return false;
            }
            amount = canAfford;
            cost = item.getCost() * amount;
            player.sendMessage("You can only afford " + amount + " of that item.");
        }
        for (int i = 0; i < amount; i++) {
            if (!player.getInventory().hasRoomFor(item.getItem().getId(), 1)) {
                player.sendMessage("Not enough inventory space.");
                return false;
            }
            player.PvmPoints -= item.getCost();
            player.getInventory().addOrDrop(item.getItem());
        }
        return true;
    }

    @Override
    public List<ShopCategories> getCategories() {
        return Arrays.asList(
            ShopCategories.CONSUMABLES,
            ShopCategories.EQUIPMENT,
            ShopCategories.TREASURES
        );
    }

    @Override
    public String getShopName() {
        return "PVM Point Shop";
    }

    @Override
    public String getPointIdentifier() {
        return "PVM Points: ";
    }

    @Override
    public void openMessage(Player player) {
        player.sendMessage("Welcome to the PVM Point Shop! You have " + player.PvmPoints + " PVM points.");
    }
}
