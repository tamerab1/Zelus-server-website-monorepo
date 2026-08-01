package inter.ikod.hook;

import discord.webhooks.logs.WildernessKeyHook;
import discord.webhooks.logs.WildernessPvpKillHook;
import economy.protection.audit.AuditLogger;
import economy.protection.classification.ItemClassification;
import economy.protection.config.EconomyConfig;
import inter.ikod.IKOD;
import io.ruin.HooksV2.Result;
import io.ruin.cache.ObjType;
import io.ruin.model.entity.player.PlayerCombat;
import io.ruin.model.item.Item;
import io.ruin.model.item.actions.impl.wilderness_keys.WildernessKeyHandler;
import io.ruin.model.map.ground.GroundItem;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class PlayerCombatHook implements PlayerCombat.Hook {

	public static void register() {
		PlayerCombat.hooks.register(PlayerCombat.Hook.OnDeath.class, PlayerCombatHook::handle);
	}

	static Result handle(PlayerCombat.Hook.OnDeath ctx) {
		var player = ctx.player();
		var killer = ctx.killer();
		var pKiller = ctx.pKiller();
		// TODO(polish) - split the module to interface/ikod logic
		// interface should handle just interface stuff, items kept on death is pure
		// logic

		var lostItems = new ArrayList<Item>();
		var spawnValueAccrued = new AtomicLong();
		IKOD.forLostItem(player, killer, item -> {
			if(item.getId() == 30464) {
				if(item.hasAttributes()) {
					item.remove();
					return;
				}
			}
			item.clearAttributes();
			ObjType def = item.getDef();
			if (pKiller != null && pKiller.hideFreeItems && def.free) {
				return;
			}

			// Economy-protection: semi-spawnable items never hit the ground or a killer's
			// loot list — their PvP value is converted into PKP for the killer instead.
			if (ItemClassification.isSpawnable(item.getId()) || ItemClassification.isDissolvable(item.getId())) {
				spawnValueAccrued.addAndGet(ItemClassification.pvpValueOf(item.getId()) * item.getAmount());
				return;
			}

			if (pKiller == null || killer.rewardBlocked || !def.tradeable)
				new GroundItem(item).owner(player).position(player.getPosition()).spawn(60);
			else if (pKiller.getGameMode().isIronMan())
				new GroundItem(item).owner(player).position(player.getPosition()).diedToIron(player).spawn(1);
			else {
				// new GroundItem(item).owner(pKiller).position(player.getPosition()).spawn();
				lostItems.add(item);
			}
		});

		if (pKiller != null && !killer.rewardBlocked && spawnValueAccrued.get() > 0) {
			int pkp = EconomyConfig.convertToPkp(spawnValueAccrued.get());
			if (pkp > 0) {
				pKiller.updatePKPoints(pkp);
				pKiller.sendMessage("[Economy] +" + pkp + " PKP for your victim's spawned supplies (no item drop).");
			}
		}

		if (pKiller != null) {
			List<Item> itemsToLose = new ArrayList<>(lostItems);
			if (!lostItems.isEmpty()) {
				if (!WildernessKeyHandler.handlePlayerKill(pKiller, lostItems)) {
					for (Item item : lostItems) {
						if (WildernessKeyHandler.handleDyingWithKey(pKiller, player, item))
							continue;
						if (ItemClassification.isHighValue(item.getId()) && EconomyConfig.rollSink()) {
							AuditLogger.logSink(pKiller, player, item);
							continue;
						}
						new GroundItem(item).owner(pKiller).position(player.getPosition()).spawn();
					}
//					RareDropEmbedMessage.sendPvpDeathWebhook(pKiller, player, itemsToLose);

					var items = new JSONArray();
					for (Item item : itemsToLose) {
						var itemJson = new JSONObject();
							itemJson.put("id", item.getId());
							itemJson.put("name", item.getDef().getName());
							itemJson.put("amount", item.getAmount());
						items.put(itemJson);
					}
					var jsonObject = new JSONObject();
						jsonObject.put("player", pKiller.getName());
						jsonObject.put("death_x", player.getPosition().getX());
						jsonObject.put("death_y", player.getPosition().getY());
						jsonObject.put("death_z", player.getPosition().getZ());
						jsonObject.put("dead_player", player.getName());
						jsonObject.put("lost_items", items);

					WildernessPvpKillHook.sendPvpDeathWebhook(jsonObject);
				}
				else {
//					RareDropEmbedMessage.sendReceivedKeyLog(pKiller, player, itemsToLose, false);

					var items = new JSONArray();
					for (Item item : lostItems) {
						var itemJson = new JSONObject();
							itemJson.put("id", item.getId());
							itemJson.put("name", item.getDef().getName());
							itemJson.put("amount", item.getAmount());
						items.put(itemJson);
					}

					var jsonObject = new JSONObject();
						jsonObject.put("player", pKiller.getName());
						jsonObject.put("death_x", player.getPosition().getX());
						jsonObject.put("death_y", player.getPosition().getY());
						jsonObject.put("death_z", player.getPosition().getZ());
						jsonObject.put("dead_player", player.getName());
						jsonObject.put("key_action", "received");
						jsonObject.put("lost_items", items);

					WildernessKeyHook.sendReceivedKeyLog(jsonObject);
				}
			}
		}
		return Result.Pass;
	}
}
