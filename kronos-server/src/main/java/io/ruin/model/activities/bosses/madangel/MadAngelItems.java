package io.ruin.model.activities.bosses.madangel;

import io.ruin.model.item.Item;
import io.ruin.model.item.actions.ItemAction;
import io.ruin.model.entity.player.Player;

// Wires the "Teleport" option on Ardeaglais teleport (34033), Mad Angel's unique teleport-scroll
// drop, to the same outside-pew entrance point used by ::madangel and the Fallen Cathedral's own
// entrance flow (MadAngelIds.PEW_OUTSIDE_X/Y). One charge is consumed per use, matching every other
// single-charge teleport scroll/talisman in this codebase (see AirTalisman.java etc.) rather than
// RS-Realm's book-of-scrolls binding, which has no equivalent UI in this client.
//
// The item's other 4 options (Wield on Hallowfell, Destroy on Sunstone crystal, Drop on Aggy) need
// no bespoke registration here: Wield is handled generically by the engine from the item's cache
// wearPos/equip-stat fields, Destroy is already wired globally for every item id in the cache by
// DestroyAction.register(), and Aggy's Drop-to-summon is handled by Pet.java once its follower npc
// is registered there. Jar of light (34030) has no interactive option in RS-Realm's own reference
// content either (collection-log trophy only) -- nothing to wire.
public class MadAngelItems {

    public static void register() {
        ItemAction.registerInventory(MadAngelIds.ITEM_ARDEAGLAIS_TELEPORT, "teleport", MadAngelItems::teleport);
    }

    private static void teleport(Player player, Item item) {
        player.getMovement().startTeleport(e -> {
            player.animate(714);
            player.graphics(111, 92, 0);
            player.publicSound(200);
            e.delay(3);
            player.getMovement().teleport(MadAngelIds.PEW_OUTSIDE_X, MadAngelIds.PEW_OUTSIDE_Y, 0);
            item.remove(1);
        });
    }
}
