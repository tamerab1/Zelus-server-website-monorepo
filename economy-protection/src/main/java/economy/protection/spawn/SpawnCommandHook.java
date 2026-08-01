package economy.protection.spawn;

import economy.protection.classification.ClassifiedItem;
import economy.protection.classification.ItemClassification;
import io.ruin.HooksV2.Result;
import io.ruin.api.utils.NumberUtils;
import io.ruin.cache.ObjType;
import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.notify.NotificationInterface;
import io.ruin.model.item.Item;
import io.ruin.network.incoming.handlers.CommandHandler;

/**
 * Registers ::spawn, gated by {@link ItemClassification}'s SPAWNABLE whitelist.
 *
 * <ul>
 *   <li>{@code ::spawn} — opens the client's native item-search dialog (the same one the
 *       Trading Post's "Buy" button opens, via {@link Player#itemSearch}), so players can
 *       search by name and see icons without knowing any ids. Picking a non-whitelisted
 *       item is rejected the same way the Trading Post rejects untradeable items.</li>
 *   <li>{@code ::spawn list} — plain-text list of every spawnable item, for quick reference</li>
 *   <li>{@code ::spawn <item_id> <amount>} — direct id + amount for power users</li>
 * </ul>
 */
public class SpawnCommandHook {

	public static void register() {
		CommandHandler.hooks.register(CommandHandler.Hook.Handle.class, SpawnCommandHook::handle);
	}

	static Result handle(CommandHandler.Hook.Handle ctx) {
		var player = ctx.player();
		var command = ctx.command();
		var args = ctx.args();

		if (!command.equalsIgnoreCase("spawn"))
			return Result.Pass;

		if (!player.isPvpMode()) {
			player.sendMessage("The ::spawn command is exclusively for PVP Mode accounts!");
			return Result.Return;
		}

		if (args.length == 1 && args[0].equalsIgnoreCase("list")) {
			sendList(player);
			return Result.Return;
		}

		if (args.length == 0) {
			openSearch(player);
			return Result.Return;
		}

		Integer id = tryParseInt(args[0]);
		if (id == null) {
			player.sendMessage("Usage: ::spawn, ::spawn list, or ::spawn <item_id> <amount>");
			return Result.Return;
		}
		int amount = args.length > 1 ? Math.max(1, NumberUtils.intValue(args[1])) : 1;
		trySpawn(player, id, amount);
		return Result.Return;
	}

	private static final String DEFAULT_PROMPT = "Enter the name of the item you wish to spawn: ";

	private static void openSearch(Player player) {
		openSearch(player, DEFAULT_PROMPT);
	}

	private static void openSearch(Player player, String prompt) {
		player.itemSearch(prompt, false, id -> {
			// Cancel (Escape) arrives as 65535 (0xFFFF, the unsigned-short encoding of -1).
			// id 0 is never a real item either, so treat any non-positive id or 65535 as "no selection".
			if (id <= 0 || id == 65535) {
				return;
			}
			if (!isSpawnAllowed(id)) {
				String itemName = new Item(id).getDef().getName();
				showError(player, "That item cannot be spawned.");
				openSearch(player, "\"" + itemName + "\" cannot be spawned — search for another item: ");
				return;
			}
			player.integerInput("How many would you like to spawn?", amt -> {
				if (amt < 1) {
					player.sendMessage("You must spawn at least 1.");
					openSearch(player);
					return;
				}
				trySpawn(player, id, amt);
				openSearch(player);
			});
		});
	}

	private static void sendList(Player player) {
		var items = ItemClassification.spawnableItems();
		if (items.isEmpty()) {
			player.sendMessage("No items are currently spawnable.");
			return;
		}
		player.sendMessage("Spawnable items:");
		for (ClassifiedItem item : items) {
			if (ItemClassification.isHighValue(item.id)) continue;
			ObjType def = ObjType.get(item.id);
			if (def == null) continue;
			String cap = item.dailySpawnCap > 0 ? (item.dailySpawnCap + "/day") : "unlimited";
			player.sendMessage("- " + def.getName() + " (" + cap + ")");
		}
	}

	private static void trySpawn(Player player, int id, int amount) {
		if (!isSpawnAllowed(id)) {
			showError(player, "That item cannot be spawned.");
			return;
		}

		var data = Attributes.semiSpawnData(player);
		int cap = ItemClassification.dailyCapOf(id);
		if (cap > 0) {
			int already = data.amountSpawnedToday(id);
			if (already >= cap) {
				showError(player, "You've reached your daily spawn limit for this item.");
				return;
			}
			amount = Math.min(amount, cap - already);
		}

		int spawnId = amount > 1 ? ObjType.notedId(id) : id;
		Item item = new Item(spawnId, amount);
		player.getInventory().addOrDrop(item);
		data.recordSpawn(id, amount);
		player.sendMessage("Spawned " + amount + "x " + item.getDef().getName() + ".");
	}

	/**
	 * PVP Mode's ::spawn is limited to whitelisted basic items — anything flagged HIGH_VALUE
	 * in {@link ItemClassification} (rare/high-tier gear) is never spawnable, regardless of
	 * the SPAWNABLE flag.
	 */
	static boolean isSpawnAllowed(int id) {
		return ItemClassification.isSpawnable(id) && !ItemClassification.isHighValue(id);
	}

	/** Red fading notification banner (~3s) at the top of the screen. */
	private static void showError(Player player, String text) {
		NotificationInterface.open(player, "Cannot Spawn", text, 0xFF0000, 5);
	}

	private static Integer tryParseInt(String s) {
		try {
			return Integer.parseInt(s.trim());
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
