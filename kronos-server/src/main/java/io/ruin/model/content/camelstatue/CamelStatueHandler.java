package io.ruin.model.content.camelstatue;

import discord.webhooks.notifications.GlobalBroadcastHook;
import io.ruin.api.utils.ExecutorUtils;
import io.ruin.api.utils.NumberUtils;
import io.ruin.cache.Icon;
import io.ruin.cache.ItemID;
import io.ruin.model.World;
import io.ruin.model.entity.player.Player;
import io.ruin.model.item.Item;
import io.ruin.model.map.object.GameObject;
import io.ruin.utility.Broadcast;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
public class CamelStatueHandler {
	public static final AtomicLong totalDonated = new AtomicLong(0);
	public static final AtomicLong amountSinceLastUpdate = new AtomicLong(0);

	private static final Map<CamelStatueRewards, AtomicLong> donatedPerReward = new EnumMap<>(CamelStatueRewards.class);
	static {
		for (CamelStatueRewards reward : CamelStatueRewards.values())
			donatedPerReward.put(reward, new AtomicLong(0));
	}

	@Getter
	static List<CamelStatueRewards> activeRewards = new ArrayList<>();

	public static boolean isRewardActive(CamelStatueRewards reward) {
		return activeRewards.contains(reward);
	}

	public static long getDonated(CamelStatueRewards reward) {
		return donatedPerReward.get(reward).get();
	}

	public static final ScheduledExecutorService scheduler =
			Executors.newSingleThreadScheduledExecutor(r -> {
				Thread thread = new Thread(r, "CamelStatue-Scheduler");
				thread.setDaemon(true);
				return thread;
			});

	public static void donateToWell(Player player) {
		player.integerInput("How much would you like to donate?", amount -> {
			try {
				if (amount < 1) {
					player.sendMessage("You must donate at least 1 coin.");
					return;
				}
				if (!player.getInventory().contains(995)) {
					player.sendMessage("You do not have any coins to donate.");
					return;
				}
				if (amount > player.getInventory().getAmount(995)) {
					amount = player.getInventory().getAmount(995);
				}
				player.getInventory().remove(995, amount);
				CamelStatueRewards reward = player.getCamelStatueInterface().getLastSection();
				addGoldPieceValueToStatue(player, amount, reward, "");
				player.getCamelStatueInterface().update(player);
				checkRewardUnlock(reward);
			} catch (Exception e) {
				log.error("Error processing donation from player: %s".formatted(player.getName()), e);
				player.sendMessage("There was an error processing your donation. Please try again.");
			}
		});
	}

	public static void donatePlatinumTokens(Player player, Item platinumToken, GameObject statue) {
		player.integerInput("How much would you like to donate?", amount -> {
			try {
				if (amount < 1) {
					player.sendMessage("You must donate at least 1 token.");
					return;
				}
				if (!player.getInventory().contains(ItemID.PLATINUM_TOKEN)) {
					player.sendMessage("You do not have any tokens to donate.");
					return;
				}
				if (amount > player.getInventory().getAmount(ItemID.PLATINUM_TOKEN)) {
					amount = player.getInventory().getAmount(ItemID.PLATINUM_TOKEN);
				}
				player.getInventory().remove(ItemID.PLATINUM_TOKEN, amount);

				var goldValue = amount * 1_000L;

				CamelStatueRewards reward = player.getCamelStatueInterface().getLastSection();
				addGoldPieceValueToStatue(player, goldValue, reward, "");
				player.getCamelStatueInterface().update(player);
				checkRewardUnlock(reward);
			} catch (Exception e) {
				log.error("Error processing donation from player: %s".formatted(player.getName()), e);
				player.sendMessage("There was an error processing your donation. Please try again.");
			}
		});
	}

	private static void addGoldPieceValueToStatue(Player player, long amount, CamelStatueRewards reward, String broadcastPrefix) {
		totalDonated.addAndGet(amount);
		amountSinceLastUpdate.addAndGet(amount);
		donatedPerReward.get(reward).addAndGet(amount);

		player.sendMessage("You have donated %s coins towards %s."
				.formatted(NumberUtils.formatNumber(amount), reward.getName()));
		player.sendMessage("There's been a total of %s coins donated to the statue."
				.formatted(NumberUtils.formatNumber(totalDonated.get())));

		if (amount > 50_000_000)
			Broadcast.GLOBAL.sendNews(Icon.ADMINISTRATOR, "Camel Statue",
					broadcastPrefix + "%s has donated %s coins to the Camel Statue at home!"
							.formatted(player.getName(), NumberUtils.formatNumber(amount)));

		if (amountSinceLastUpdate.get() >= 250_000_000) {
			amountSinceLastUpdate.set(0);
			Broadcast.GLOBAL.sendNews(Icon.ADMINISTRATOR, "Camel Statue",
					"Another 250,000,000 coins have been donated to the Camel Statue at home!");
		}
	}

	public static void adminDonateToWell(Player player, int amount) {
		try {
			CamelStatueRewards reward = player.getCamelStatueInterface().getLastSection();
			addGoldPieceValueToStatue(player, amount, reward, "[Admin Fill] ");
			player.getCamelStatueInterface().update(player);
			checkRewardUnlock(reward);
		} catch (Exception e) {
			log.error("Error processing admin donation from player: " + player.getName(), e);
			player.sendMessage("There was an error processing your donation.");
		}
	}

	public static void scheduleWellClearing() {
		scheduler.scheduleAtFixedRate(() -> {
			try {
				totalDonated.set(0);
				amountSinceLastUpdate.set(0);
				donatedPerReward.values().forEach(donated -> donated.set(0));
				activeRewards.clear();
				log.info("Well cleared at midnight.");
				Broadcast.GLOBAL.sendNews(Icon.ADMINISTRATOR, "Camel Statue",
						"A new day has started and the camel statue has been wiped!");
			} catch (Exception e) {
				log.error("Error clearing well", e);
			}
		}, getDelayUntilMidnight(), 24, TimeUnit.HOURS);
	}

	private static long getDelayUntilMidnight() {
		long now = System.currentTimeMillis();
		long midnight = now - (now % 86400000) + 86400000; // Next midnight in milliseconds
		return midnight - now;
	}

	private static void checkRewardUnlock(CamelStatueRewards reward) {
		try {
			if (activeRewards.contains(reward))
				return;
			if (donatedPerReward.get(reward).get() < reward.getUnlockAmount())
				return;

			activeRewards.add(reward);
			Broadcast.GLOBAL.sendNews(Icon.ADMINISTRATOR, "Camel Statue",
				"The Camel Statue at home has been upgraded and is now giving " +
					reward.name().toLowerCase().replace("_", " ") + "!");

			var dtoArray = new JSONArray();
			dtoArray.put(new JSONObject()
				.put("perk_name", reward.getName())
				.put("perk_description", reward.getDescription())
			);
			var dto = new JSONObject();
			dto.put("enabled_perks", dtoArray);
			GlobalBroadcastHook.sendWellMessage(dto);

			if (reward == CamelStatueRewards.DOUBLE_SLAYER_POINTS && !World.doubleSlayer)
				World.doubleSlayer = true;
		} catch (Exception e) {
			log.error("Error updating well", e);
		}
	}

	public static void shutdown() {
		ExecutorUtils.shutdown(scheduler);
	}
}
