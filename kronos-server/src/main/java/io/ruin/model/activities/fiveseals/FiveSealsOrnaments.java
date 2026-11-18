package io.ruin.model.activities.fiveseals;

import io.ruin.cache.Color;
import io.ruin.cache.ObjType;
import io.ruin.model.entity.player.Player;
import io.ruin.model.item.Item;
import io.ruin.model.item.actions.ItemAction;
import io.ruin.model.item.actions.ItemItemAction;

/**
 * The Five Seals ornament kits (shop Rewards tab, cache stage 63, 2026-11-18): use a kit on a Duskwarden / Hellfang
 * piece to turn it into its (or) look (the piece keeps its perks and upgrades: only its id changes); Dismantle the (or)
 * piece to get the plain piece and the kit back. The (or) pieces share their base's stats (item_info.json) and specials.
 */
final class FiveSealsOrnaments {

	private FiveSealsOrnaments() {
	}

	/** {kit, base, ornamented} */
	static final int[][] PIECES = {
		{ FiveSealsShop.DUSK_KIT, 42696, 42807 }, // helm
		{ FiveSealsShop.DUSK_KIT, 42698, 42808 }, // platebody
		{ FiveSealsShop.DUSK_KIT, 42700, 42809 }, // platelegs
		{ FiveSealsShop.DUSK_KIT, 42702, 42810 }, // blade
		{ FiveSealsShop.DUSK_KIT, 42704, 42811 }, // kiteshield
		{ FiveSealsShop.HELL_KIT, 42706, 42812 }, // helm
		{ FiveSealsShop.HELL_KIT, 42708, 42813 }, // platebody
		{ FiveSealsShop.HELL_KIT, 42710, 42814 }, // platelegs
		{ FiveSealsShop.HELL_KIT, 42712, 42815 }, // crossbow
	};

	static void register() {
		for (int[] piece : PIECES) {
			int kit = piece[0], base = piece[1], or = piece[2];
			if (ObjType.get(kit) == null || ObjType.get(or) == null)
				continue; // a cache without stage 63
			ItemItemAction.register(kit, base, (player, kitItem, baseItem) -> apply(player, kitItem, baseItem, or));
			ItemAction.registerInventory(or, "dismantle", (player, item) -> dismantle(player, item, kit, base));
		}
	}

	private static void apply(Player player, Item kit, Item piece, int or) {
		if (kit.getId() == piece.getId())
			return;
		String from = piece.getDef().name;
		kit.remove();
		piece.setId(or);
		player.sendMessage(Color.DARK_GREEN.wrap("You apply the ornament kit to your " + from + "."));
	}

	private static void dismantle(Player player, Item piece, int kit, int base) {
		if (player.getInventory().getFreeSlots() < 1) {
			player.sendMessage("You need a free inventory space to dismantle that.");
			return;
		}
		piece.setId(base);
		player.getInventory().add(kit, 1);
		player.sendMessage("You take the ornament kit off your " + ObjType.get(base).name + ".");
	}
}
