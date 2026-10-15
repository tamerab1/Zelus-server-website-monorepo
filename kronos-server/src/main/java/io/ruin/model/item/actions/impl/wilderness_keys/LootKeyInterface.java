package io.ruin.model.item.actions.impl.wilderness_keys;

import discord.webhooks.logs.WildernessKeyHook;
import io.ruin.api.utils.NumberUtils;
import io.ruin.cache.ObjType;
import io.ruin.model.entity.player.GameMode;
import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.InterfaceHandler;
import io.ruin.model.inter.ToplevelComponent;
import io.ruin.model.inter.actions.SimpleAction;
import io.ruin.model.item.Item;
import io.ruin.model.item.attributes.AttributeExtensions;
import io.ruin.model.item.attributes.AttributeTypes;
import io.ruin.network.PacketSender;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * The Loot Key window (interface 1159, built by .dev/cache-restore-tool/src/BuildLootKeyInterface.java): what a
 * wilderness key holds and its total value, with Bank All Loot / Take to Inventory when opened at a Loot Chest
 * (preview only from the key's Check option).
 * <p>
 * Dupe safety: a claim re-checks everything server side (the window, the key still in the inventory with the same
 * index, the chest flag, the game mode, combat and Wilderness), then takes the key's contents off the account
 * <b>before</b> granting them; whatever doesn't fit is written back to the key (the key stays), never dropped.
 * Item attributes (forge upgrades etc.) are kept.
 */
public final class LootKeyInterface {

	public static final int INTERFACE = 1159;
	private static final int CLOSE = 2, TITLE = 3, SUB_HEADER = 4;
	/** 50 cells x (fill, border, item). */
	private static final int FIRST_CELL = 10, CELL_STRIDE = 3, CELL_ITEM = 2, CELLS = 50;
	private static final int INFO = 160, BANK_ALL = 161, TAKE = 163;
	/** Clientscript 10752 set_opbase(component, name): the cell's hover names the item. */
	private static final int SET_OPBASE = 10752;
	/** No claiming within this many ticks of combat (about 10 seconds). */
	private static final int COMBAT_TICKS = 16;

	private static final String INDEX_KEY = "lootkey_index", CLAIM_KEY = "lootkey_claim";
	private static final String ORANGE = "<col=ff981f>", GREEN = "<col=00ff00>", GREY = "<col=9a9a9a>", END = "</col>";

	private LootKeyInterface() {
	}

	static void register() {
		InterfaceHandler.register(INTERFACE, h -> {
			h.actions[CLOSE] = (SimpleAction) p -> p.closeInterface(ToplevelComponent.MAINMODAL);
			h.actions[BANK_ALL] = (SimpleAction) p -> claim(p, true);
			h.actions[TAKE] = (SimpleAction) p -> claim(p, false);
			for (int c = 0; c < CELLS; c++) {
				int cell = c;
				h.actions[FIRST_CELL + c * CELL_STRIDE + CELL_ITEM] = (SimpleAction) p -> {
					Item[] items = contents(p);
					if (items != null && cell < items.length && items[cell] != null) {
						ObjType def = items[cell].getDef();
						p.sendMessage("<col=000080>" + def.name + "</col>: <col=000000>" + def.examineText() + "</col>");
					}
				};
			}
		});
	}

	/** Shows a key's contents; claim = opened at a Loot Chest (the buttons work), else a preview. */
	static void open(Player player, int keyIndex, boolean claim) {
		if (!validIndex(player, keyIndex)) {
			player.sendMessage("This key doesn't hold any loot.");
			return;
		}
		player.set(INDEX_KEY, keyIndex);
		player.set(CLAIM_KEY, claim);
		player.openInterface(ToplevelComponent.MAINMODAL, INTERFACE);
		player.getPacketSender().sendString(INTERFACE, TITLE, ORANGE + "WILDERNESS LOOT KEY" + END);
		refresh(player);
	}

	private static void refresh(Player player) {
		PacketSender ps = player.getPacketSender();
		Item[] items = contents(player);
		boolean claim = player.<Boolean>get(CLAIM_KEY, false);
		long total = 0;
		int shown = 0;
		for (int c = 0; c < CELLS; c++) {
			int cell = FIRST_CELL + c * CELL_STRIDE;
			Item item = items != null && c < items.length ? items[c] : null;
			for (int part = 0; part < CELL_STRIDE; part++)
				ps.setHidden(INTERFACE, cell + part, item == null);
			if (item == null)
				continue;
			shown++;
			total += (long) Math.max(0, item.getDef().value) * item.getAmount();
			ps.sendItem(INTERFACE, cell + CELL_ITEM, item.getId(), item.getAmount());
			ps.sendClientScript(SET_OPBASE, "Is", INTERFACE << 16 | (cell + CELL_ITEM),
				ORANGE + item.getDef().name + END + (item.getAmount() > 1 ? " x" + NumberUtils.formatNumber(item.getAmount()) : ""));
		}
		ps.sendString(INTERFACE, SUB_HEADER, ORANGE + "Total Loot Value:" + END + " " + GREEN + NumberUtils.formatNumber(total) + " gp" + END
			+ GREY + "  ·  " + shown + " item" + (shown == 1 ? "" : "s") + END);
		ps.sendString(INTERFACE, INFO, claim ? "Claim outside the Wilderness and out of combat. Anything that doesn't fit stays on the key."
			: "Preview: use this key on a Loot Chest to claim it.");
		for (int b : new int[]{BANK_ALL, TAKE}) {
			ps.setHidden(INTERFACE, b, !claim);
			ps.setHidden(INTERFACE, b + 1, !claim);
		}
	}

	/** Bank All Loot (toBank) / Take to Inventory: everything re-validated, contents taken before they're granted. */
	private static void claim(Player player, boolean toBank) {
		if (!player.isVisibleInterface(INTERFACE))
			return;
		Integer keyIndex = player.get(INDEX_KEY);
		if (keyIndex == null || !player.<Boolean>get(CLAIM_KEY, false)) {
			player.sendMessage("Use the key on a Loot Chest to claim its loot.");
			return;
		}
		if (!canClaim(player))
			return;
		Item key = findKey(player, keyIndex);
		if (key == null || !validIndex(player, keyIndex)) {
			player.closeInterface(ToplevelComponent.MAINMODAL);
			return;
		}
		Item[] items = player.wildernessKeyItems[keyIndex];
		player.wildernessKeyItems[keyIndex] = null; // take ownership first: nothing below can grant twice

		List<Item> granted = new ArrayList<>(), left = new ArrayList<>();
		for (Item item : items) {
			if (item == null || item.getAmount() <= 0)
				continue;
			int added = toBank
				? player.getBank().add(item.getId(), item.getAmount(), item.copyOfAttributes())
				: player.getInventory().add(item.getId(), item.getAmount(), item.copyOfAttributes());
			added = Math.max(0, Math.min(added, item.getAmount()));
			if (added > 0) {
				Item got = item.copy();
				got.setAmount(added);
				granted.add(got);
			}
			if (added < item.getAmount()) {
				Item rest = item.copy();
				rest.setAmount(item.getAmount() - added);
				left.add(rest);
			}
		}
		if (left.isEmpty()) {
			key.remove();
		} else {
			player.wildernessKeyItems[keyIndex] = left.toArray(new Item[0]);
		}
		WildernessKeyHandler.setSkull(player);
		log(player, granted, toBank);

		String where = toBank ? "your bank" : "your inventory";
		if (left.isEmpty()) {
			player.sendMessage(io.ruin.cache.Icon.YELLOW_KEY.tag() + " <col=000080>All the loot was moved to " + where + ".</col>");
			player.closeInterface(ToplevelComponent.MAINMODAL);
		} else {
			player.sendMessage("<col=000080>" + granted.size() + " stack" + (granted.size() == 1 ? "" : "s") + " moved to " + where
				+ "; </col><col=7f0000>" + left.size() + " didn't fit and stay on the key.</col>");
			refresh(player);
		}
	}

	private static boolean canClaim(Player player) {
		if (player.getGameMode() != GameMode.STANDARD) {
			player.sendMessage("You can't open wilderness keys on this game mode.");
			return false;
		}
		if (player.wildernessLevel > 0) {
			player.sendMessage("You can't claim a loot key inside the Wilderness.");
			return false;
		}
		if (player.getCombat().isDefending(COMBAT_TICKS) || player.getCombat().isAttacking(COMBAT_TICKS)) {
			player.sendMessage("You can't claim a loot key while in combat.");
			return false;
		}
		return true;
	}

	static boolean validIndex(Player player, int keyIndex) {
		return keyIndex >= 0 && keyIndex < player.wildernessKeyItems.length && player.wildernessKeyItems[keyIndex] != null
			&& player.wildernessKeyItems[keyIndex].length > 0;
	}

	private static Item[] contents(Player player) {
		Integer keyIndex = player.get(INDEX_KEY);
		return keyIndex == null || !validIndex(player, keyIndex) ? null : player.wildernessKeyItems[keyIndex];
	}

	/** The inventory key with this index (the key must still be held). */
	private static Item findKey(Player player, int keyIndex) {
		for (Item item : player.getInventory().getItems())
			if (item != null && item.getId() == WildernessKeyHandler.KEY_ID
				&& AttributeExtensions.getCharges(AttributeTypes.WILDERNESS_KEY, item) == keyIndex)
				return item;
		return null;
	}

	private static void log(Player player, List<Item> granted, boolean toBank) {
		var items = new JSONArray();
		for (Item item : granted) {
			var itemJson = new JSONObject();
			itemJson.put("id", item.getId());
			itemJson.put("name", item.getDef().getName());
			itemJson.put("amount", item.getAmount());
			items.put(itemJson);
		}
		var json = new JSONObject();
		json.put("player", player.getName());
		json.put("destination", toBank ? "bank" : "inventory");
		json.put("looted_items", items);
		WildernessKeyHook.openWildernessKeyLog(json);
	}
}
