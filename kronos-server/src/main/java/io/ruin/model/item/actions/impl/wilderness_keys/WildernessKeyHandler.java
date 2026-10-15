package io.ruin.model.item.actions.impl.wilderness_keys;

import discord.webhooks.logs.WildernessKeyHook;
import io.ruin.model.entity.player.GameMode;
import io.ruin.model.entity.player.Player;
import io.ruin.model.entity.shared.listeners.LoginListener;
import io.ruin.model.inter.dialogue.OptionsDialogue;
import io.ruin.model.inter.utils.Option;
import io.ruin.model.item.Item;
import io.ruin.model.item.actions.ItemAction;
import io.ruin.model.item.actions.ItemObjectAction;
import io.ruin.model.item.attributes.AttributeExtensions;
import io.ruin.model.item.attributes.AttributeTypes;
import io.ruin.model.map.ground.GroundItem;
import io.ruin.model.map.object.actions.ObjectAction;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class WildernessKeyHandler {

	static final int KEY_ID = 26651;

	public static void register() {
		ObjectAction.register(43486, 1, (player, obj) -> {
			open(player);
		});

		ItemAction.registerInventory(KEY_ID, "Check", WildernessKeyHandler::check);
		ItemAction.registerInventory(KEY_ID, "Destroy", WildernessKeyHandler::destroyKey);
		ItemObjectAction.register(43484, KEY_ID, (player, item, obj) -> open(player, item));
		// the spawned chest is the multiloc 43486 (shows as 43484 "Loot Chest"): using a key on it picks that key
		ItemObjectAction.register(43486, KEY_ID, (player, item, obj) -> open(player, item));
		// the Edgeville Loot Chest is a coffer (HomeHandler): using a key on it opens that key
		ItemObjectAction.register(29087, KEY_ID, (player, item, obj) -> open(player, item));
		LoginListener.register(WildernessKeyHandler::setSkull);
		LootKeyInterface.register();
	}

	public static boolean handlePlayerKill(Player player, List<Item> lostItems) {
		if (lostItems.isEmpty() || player.getGameMode() != GameMode.STANDARD)
			return false;
		int keyIndex = player.getInventory().findItem(KEY_ID) == null ? 0 : getFreeKeyIndex(player);
		if (keyIndex == -1 || !player.getInventory().hasRoomFor(KEY_ID, 1)) {
			player.sendMessage("You can't carry anymore keys.");
			return false;
		}
		player.wildernessKeyItems[keyIndex] = lostItems.toArray(new Item[0]);
		Item key = new Item(KEY_ID, 1);
		key.getDef().neverProtect = true;
		AttributeExtensions.setCharges(AttributeTypes.WILDERNESS_KEY, key, keyIndex);
		player.getInventory().add(key);
		player.sendMessage("You have received a wilderness key.");
		setSkull(player);
		return true;
	}

	/** ::testlootkey [1-3]: a key loaded with simulated PvP spoils, to test the Loot Key window and its claims. */
	public static void giveTestKey(Player player, int tier) {
		List<Item> spoils = new ArrayList<>();
		spoils.add(new Item(13307, tier * 50_000));     // Blood money
		spoils.add(new Item(385, 10));                  // Sharks
		spoils.add(new Item(6685, 4));                  // Saradomin brew(4)
		spoils.add(new Item(12695, 2));                 // Super combat potion(4)
		spoils.add(new Item(4151, 1));                  // Abyssal whip
		if (tier >= 2) {
			spoils.add(new Item(13652, 1));             // Dragon claws
			spoils.add(new Item(9244, 200));            // Dragon bolts (e)
			spoils.add(new Item(11828, 1));             // Armadyl chestplate
		}
		if (tier >= 3) {
			spoils.add(new Item(11802, 1));             // Armadyl godsword
			spoils.add(new Item(11830, 1));             // Armadyl chainskirt
			spoils.add(new Item(21003, 1));             // Elder maul
			spoils.add(new Item(12825, 1));             // Arcane spirit shield
		}
		if (!handlePlayerKill(player, spoils)) {
			player.sendMessage("No test key given: you need a standard game mode account, a free inventory slot and fewer than 5 keys.");
			return;
		}
		player.sendMessage("Test loot key (tier " + tier + ", " + spoils.size() + " stacks) added: Check it, or use it on a Loot Chest.");
	}

	private static void destroyKey(Player player, Item key) {
		player.dialogue(new OptionsDialogue("Are you sure you want to destroy the key and its contents?",
			new Option("Yes, destroy the key.", () -> {
				int keyIndex = AttributeExtensions.getCharges(AttributeTypes.WILDERNESS_KEY, key);
				if (!player.getInventory().contains(key))
					return;
				key.remove();
				if (!LootKeyInterface.validIndex(player, keyIndex)) {
					setSkull(player);
					return;
				}
				List<Item> itemsToLose = List.of(player.wildernessKeyItems[keyIndex]);
				player.wildernessKeyItems[keyIndex] = null;
				setSkull(player);

//				RareDropEmbedMessage.sendReceivedKeyLog(player, player, itemsToLose, true);

				var items = new JSONArray();
				for (Item item : itemsToLose) {
					var itemJson = new JSONObject();
					itemJson.put("id", item.getId());
					itemJson.put("name", item.getDef().getName());
					itemJson.put("amount", item.getAmount());
					items.put(itemJson);
				}

				var jsonObject = new JSONObject();
					jsonObject.put("player", player.getName());
					jsonObject.put("dead_player", player.getName());
					jsonObject.put("key_action", "destroyed");
					jsonObject.put("lost_items", items);

				WildernessKeyHook.sendDestroyKeyLog(jsonObject);

			}),
			new Option("Nevermind.")));
	}

	/** Check: the Loot Key window with Bank All / Take to Inventory (claims are refused in the Wilderness or combat). */
	private static void check(Player player, Item key) {
		if (player.getGameMode() != GameMode.STANDARD) {
			player.sendMessage("You can't open wilderness keys on this game mode.");
			return;
		}
		LootKeyInterface.open(player, AttributeExtensions.getCharges(AttributeTypes.WILDERNESS_KEY, key), true);
	}

	public static boolean handleDyingWithKey(Player player, Player dead, Item key) {
		if (key.getId() != KEY_ID)
			return false;
		key.remove();
		if (!LootKeyInterface.validIndex(dead, AttributeExtensions.getCharges(AttributeTypes.WILDERNESS_KEY, key)))
			return true;
		if (getFreeKeyIndex(player) == -1 || !player.getInventory().hasRoomFor(KEY_ID, 1)) { // no slot: the loot drops for the killer
			int keyIndex = AttributeExtensions.getCharges(AttributeTypes.WILDERNESS_KEY, key);
			for (Item item : dead.wildernessKeyItems[keyIndex]) {
				if (item != null)
					new GroundItem(item).owner(player).position(dead.getPosition()).spawn();
			}
			dead.wildernessKeyItems[keyIndex] = null;
			return true;
		}
		int deadIndex = AttributeExtensions.getCharges(AttributeTypes.WILDERNESS_KEY, key);
		List<Item> lostItems = new ArrayList<>();
		for (Item item : dead.wildernessKeyItems[deadIndex]) {
			if (item == null)
				continue;
			lostItems.add(item);
		}
		dead.wildernessKeyItems[deadIndex] = null;
		// the killer's key takes one of the KILLER's free slots: reusing the dead player's slot number overwrote
		// the loot of a key the killer already held in that slot (and left two keys pointing at one stash)
		int keyIndex = getFreeKeyIndex(player);
		player.wildernessKeyItems[keyIndex] = lostItems.toArray(new Item[0]);

		Item k = new Item(KEY_ID, 1);
		k.getDef().neverProtect = true;
		AttributeExtensions.setCharges(AttributeTypes.WILDERNESS_KEY, k, keyIndex);
		player.getInventory().add(k);
		player.sendMessage("You have received a wilderness key.");
		setSkull(player);

//		RareDropEmbedMessage.sendReceivedKeyLog(player, dead, lostItems, false);

		var items = new JSONArray();
		for (Item item : lostItems) {
			var itemJson = new JSONObject();
				itemJson.put("id", item.getId());
				itemJson.put("name", item.getDef().getName());
				itemJson.put("amount", item.getAmount());
			items.put(itemJson);
		}

		var jsonObject = new JSONObject();
			jsonObject.put("player", player.getName());
			jsonObject.put("death_x", dead.getPosition().getX());
			jsonObject.put("death_y", dead.getPosition().getY());
			jsonObject.put("death_z", dead.getPosition().getZ());
			jsonObject.put("dead_player", dead.getName());
			jsonObject.put("key_action", "received");
			jsonObject.put("lost_items", items);

		WildernessKeyHook.sendReceivedKeyLog(jsonObject);
		return true;
	}

	private static int countKeys(Player player) {
		int count = 0;
		for (int i = 0; i < 28; i++) {
			if (player.getInventory().get(i) == null || player.getInventory().get(i).getId() != KEY_ID)
				continue;
			count++;
		}
		return count;
	}

	private static int getFreeKeyIndex(Player player) {
		List<Integer> usedIndexes = new ArrayList<>();
		for (int i = 0; i < 28; i++) {
			if (player.getInventory().get(i) == null || player.getInventory().get(i).getId() != KEY_ID)
				continue;
			usedIndexes.add(AttributeExtensions.getCharges(AttributeTypes.WILDERNESS_KEY, player.getInventory().get(i)));
		}
		for (int i = 0; i < 5; i++)
			if (!usedIndexes.contains(i))
				return i;
		return -1;
	}

	public static boolean open(Player player, Item key) {
		if (key == null)
			return false;
		if (player.getGameMode() != GameMode.STANDARD) {
			player.sendMessage("You can't open wilderness keys on this game mode.");
			return false;
		}
		LootKeyInterface.open(player, AttributeExtensions.getCharges(AttributeTypes.WILDERNESS_KEY, key), true);
		return true;
	}

	private static Item getKey(Player player) {
		for (int i = 0; i < 28; i++) {
			Item item = player.getInventory().get(i);
			if (item == null || item.getId() != KEY_ID)
				continue;
			return item;
		}
		return null;
	}

	/**
	 * The overhead skull: loot keys first, then the combat skull (kill-spree / high-risk / Forinthry, as set by
	 * PlayerCombat), then a red skull for an active WANTED bounty. Runs every tick from depleteSkull, so it only sends an
	 * appearance update when the icon actually changes (it used to resend every tick and flatten the spree and
	 * high-risk skulls to the plain one).
	 */
	public static void setSkull(Player player) {
		int keyCount = Math.min(5, countKeys(player));
		var combat = player.getCombat();
		int icon;
		if (keyCount > 0)
			icon = 14 + keyCount;
		else if (combat.highRiskSkull || combat.skullDelay > 0)
			icon = io.ruin.model.combat.KillingSpree.overheadId(player);
		else if (combat.forinthySkullDelay > 0)
			icon = 3;
		else if (player.wantedBountyAmount > 0)
			icon = 1;
		else
			icon = -1;
		if (player.getAppearance().getSkullIcon() != icon)
			player.getAppearance().setSkullIcon(icon);
	}


	public static boolean open(Player player) {
		return open(player, getKey(player));
	}
}
