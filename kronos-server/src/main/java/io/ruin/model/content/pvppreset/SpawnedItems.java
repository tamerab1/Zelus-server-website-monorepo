package io.ruin.model.content.pvppreset;

import io.ruin.model.entity.player.Player;
import io.ruin.model.item.Item;
import io.ruin.model.item.ItemContainer;

/**
 * Free PvP items (Battle Presets kits, the Free Food / Potions / Veng / Barrage supply buttons) are stamped with the
 * existing phantom flag ({@link PvpPresetManager#PHANTOM_KEY}) so they behave like rental preset gear: usable in
 * combat (runes, food and potions are consumed per item object), but they can never become real wealth.
 * <ul>
 *     <li>They CAN be banked (to organise kits); they stack like the plain item (ItemContainerG.stacksWithAttributes).</li>
 *     <li>Already refused: trade (Trade), shops (Shop), ground spawns (GroundItem: they vanish), and Zelus Market
 *     listings (attribute items can't be listed).</li>
 *     <li>Refused here: dropping, staking (Duel), gambling (Gamble), alchemy, looting bag / rune pouches, and the
 *     forges (Wilderness / Relic / Cape: the forged result is a brand-new REAL item, so a spawned base or material
 *     must never go in -- {@link #refuseIfSpawned}).</li>
 *     <li>Death: {@link #strip} removes them before any drop / loot key / death hook runs, so the killer gets nothing.</li>
 * </ul>
 */
public final class SpawnedItems {

	private static final String TAG = "<col=ffd700>[Free PvP]</col> ";

	private SpawnedItems() {
	}

	public static boolean isSpawned(Item item) {
		return PvpPresetManager.isPhantomItem(item);
	}

	/** A copy of {@code item} carrying the spawned (phantom) flag. */
	public static Item stamp(Item item) {
		Item copy = item.copy();
		copy.putAttribute(PvpPresetManager.PHANTOM_KEY, "1");
		return copy;
	}

	public static Item spawned(int id, int amount) {
		Item item = new Item(id, amount);
		item.putAttribute(PvpPresetManager.PHANTOM_KEY, "1");
		return item;
	}

	/**
	 * Forges: true (and a chat message) if the inventory holds a spawned copy of any of {@code itemIds}. Their counts and
	 * removals go by id, so a spawned item could otherwise stand in for a real one and come out as a real upgrade.
	 */
	public static boolean refuseIfSpawned(Player player, String action, int... itemIds) {
		for (Item item : player.getInventory().getItems()) {
			if (item == null || !isSpawned(item))
				continue;
			for (int id : itemIds)
				if (item.getId() == id) {
					player.sendMessage(TAG + "Free spawned items can't be " + action + " (" + item.getDef().name
						+ "). Bank or drop them first.");
					return true;
				}
		}
		return false;
	}

	/** True (and a chat message) if {@code item} is spawned and so can't be used for {@code action}. */
	public static boolean refuse(Player player, Item item, String action) {
		if (!isSpawned(item)) {
			return false;
		}
		player.sendMessage(TAG + "Free spawned items can't be " + action + ".");
		return true;
	}

	/** Deletes every spawned item the player carries (inventory, equipment, looting bag). Returns how many stacks. */
	public static int strip(Player player) {
		int removed = strip(player.getInventory()) + strip(player.getEquipment()) + strip(player.getLootingBag());
		if (removed > 0) {
			player.getInventory().sendUpdates();
			player.getEquipment().sendUpdates();
		}
		return removed;
	}

	private static int strip(ItemContainer container) {
		if (container == null) {
			return 0;
		}
		int removed = 0;
		for (Item item : container.getItems()) {
			if (item != null && isSpawned(item)) {
				item.remove();
				removed++;
			}
		}
		return removed;
	}
}
