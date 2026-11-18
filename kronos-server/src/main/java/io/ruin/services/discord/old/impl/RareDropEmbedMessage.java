// Beta audit 2026-11-18: the hard-coded Discord webhook URLs (with tokens) were removed from this legacy class.
package io.ruin.services.discord.old.impl;

import io.ruin.api.utils.NumberUtils;
import io.ruin.api.utils.ServerWrapper;
import io.ruin.model.World;
import io.ruin.model.entity.npc.NPC;
import io.ruin.model.entity.player.Player;
import io.ruin.model.item.Item;
import io.ruin.model.map.Position;
import io.ruin.services.discord.old.Webhook;
import io.ruin.services.discord.old.util.*;
import io.ruin.utility.Utils;

import java.util.Arrays;
import java.util.List;

@Deprecated(forRemoval = true, since = "Aug 22nd, 2025")
public class RareDropEmbedMessage {

	public static void sendDiscordMessage(Player player, String discordMessage,
	                                      String npcDescriptiveName, int itemId, int killcount) {
		if (!World.isLive()) {
			return;
		}

		try {
			Webhook webhook = new Webhook(
				"");
			Message message = new Message();

			Embed embedMessage = new Embed();
			String mode = "";
			switch (player.getGameMode()) {
				case IRONMAN:
					mode = "[Ironman] ";
					break;
				case HARDCORE_GROUP_IRONMAN:
					mode = "[Hardcore Group Ironman] ";
					break;
				case GROUP_IRONMAN:
					mode = "[Group Ironman] ";
					break;
				case HARDCORE_IRONMAN:
					mode = "[Hardcore Ironman] ";
					break;
				case ULTIMATE_IRONMAN:
					mode = "[Ultimate Ironman] ";
					break;
			}
			embedMessage.setTitle(mode + "Rare drop received!");
			String npcdesc = npcDescriptiveName == null ? "" : " from " + npcDescriptiveName;
			embedMessage.setDescription(discordMessage + npcdesc + "!");
			embedMessage.setColor(8917522);

			Thumbnail thumbnail = new Thumbnail();
			thumbnail.setUrl("https://static.runelite.net/cache/item/icon/" + itemId + ".png");
			embedMessage.setThumbnail(thumbnail);

			Footer footer = new Footer();
			if (killcount == -1)
				footer.setText("");
			else
				footer.setText(player.getDifficulty().Name + " mode" + " - KC: " + killcount + ".");
			embedMessage.setFooter(footer);

			message.setEmbeds(embedMessage);
			webhook.sendMessage(message.toJson());
		} catch (Exception e) {
			ServerWrapper.logError("Failed to send discord embed", e);
		}

	}

	public static void openWildernessKeyLog(Player player, List<Item> lootedItems) {
		if (!World.isLive()) {
			return;
		}

		try {
			Webhook webhook = new Webhook("");
			Message message = new Message();

			Embed embedMessage = new Embed();
			StringBuilder sb = new StringBuilder();
			for (Item item : lootedItems) {
				sb.append(item.getAmount()).append(" x ").append(item.getDef().name).append("\n");
			}

			embedMessage.setDescription(player.getName() + " just received " + sb + " from a Wilderness key!");
			embedMessage.setColor(8917522);


			message.setEmbeds(embedMessage);
			webhook.sendMessage(message.toJson());
		} catch (Exception e) {
			ServerWrapper.logError("Failed to send discord embed", e);
		}
	}

	@SuppressWarnings("preview")
	public static void sendReceivedKeyLog(Player player, Player from, List<Item> lootedItems, boolean destroyed) {
		if (!World.isLive()) {
			return;
		}

		try {
			Webhook webhook = new Webhook("");
			Message message = new Message();

			Embed embedMessage = new Embed();
			StringBuilder sb = new StringBuilder();
			for (Item item : lootedItems) {
				System.out.println("item: " + item.getDef().name);
				sb.append(item.getAmount()).append(" x ").append(item.getDef().name).append("\n");
			}
			embedMessage.setTitle("Wilderness Key Received");
			embedMessage.setDescription("%s just %s a wilderness key containing: %s!".formatted(player.getName(), destroyed ? "destroyed" : "received", sb));
			embedMessage.setColor(8917522);
			embedMessage.setFields(
				new Field("Killed Player", from.getName(), true),
				new Field("Death Location", getMapLocation(from.getPosition()), true)
			);

			message.setEmbeds(embedMessage);
			webhook.sendMessage(message.toJson());
		} catch (Exception e) {
			ServerWrapper.logError("Failed to send discord embed", e);
		}


	}

	public static void sendPvpDeathWebhook(Player player, Player from, List<Item> lootedItems) {
		if (!World.isLive()) return;
		try {
			var webhook = new Webhook("");
			var message = new Message();
			var embedMessage = new Embed();
			var sb = new StringBuilder();

			for (Item item : lootedItems)
				sb.append(item.getAmount()).append(" x ").append(item.getDef().name).append("\n");

			embedMessage.setTitle("Wilderness PvP Drop");
			embedMessage.setDescription(sb.toString());
			embedMessage.setColor(8917522);
			embedMessage.setFields(
				new Field("Killer", player.getName(), true),
				new Field("Killed Player", from.getName(), true),
				new Field("Death Location", getMapLocation(from.getPosition()), true)
			);

			message.setEmbeds(embedMessage);
			webhook.sendMessage(message.toJson());
		}
		catch (Exception e) {
			ServerWrapper.logError("Failed to send discord embed", e);
		}


	}

	public static void sendBoxDiscordMessage(Player player, String discordMessage,
	                                         Item box, int itemId) {

		if (!World.isLive()) {
			return;
		}

		try {
			Webhook webhook = new Webhook(
				"");
			Message message = new Message();

			Embed embedMessage = new Embed();

			embedMessage.setTitle("Rare pull received!");
			String npcdesc = box == null ? "" : " from a " + box.getDef().name;
			embedMessage.setDescription(discordMessage + npcdesc + "!");
			embedMessage.setColor(8917522);

			Thumbnail thumbnail = new Thumbnail();
			thumbnail.setUrl("https://static.runelite.net/cache/item/icon/" + itemId + ".png");
			embedMessage.setThumbnail(thumbnail);

			Footer footer = new Footer();
			footer.setText("");
			embedMessage.setFooter(footer);

			message.setEmbeds(embedMessage);
			webhook.sendMessage(message.toJson());
		} catch (Exception e) {
			ServerWrapper.logError("Failed to send discord embed", e);
		}

	}

	public static void sendGlobalSpawnedMessage(NPC npc) {

		if (!World.isLive()) {
			return;
		}

		try {
			Webhook webhook = new Webhook(
				"");
			Message message = new Message();

			Embed embedMessage = new Embed();

			embedMessage.setTitle(npc.getDef().name + " has just spawned!");
			embedMessage.setColor(8917522);

			Thumbnail thumbnail = new Thumbnail();
			embedMessage.setThumbnail(thumbnail);

			Footer footer = new Footer();
			footer.setText("");
			embedMessage.setFooter(footer);

			message.setEmbeds(embedMessage);
			webhook.sendMessage(message.toJson());
		} catch (Exception e) {
			ServerWrapper.logError("Failed to send discord embed", e);
		}

	}

	public static void sendItemBreakMessage(Player player, Item item) {

		if (!World.isLive()) {
			return;
		}

		try {
			Webhook webhook = new Webhook(
				"");
			Message message = new Message();

			Embed embedMessage = new Embed();

			embedMessage.setTitle(player.getName() + " has broke down an item: " + item.getDef().name);
			embedMessage.setColor(8917522);

			Thumbnail thumbnail = new Thumbnail();
			embedMessage.setThumbnail(thumbnail);

			Footer footer = new Footer();
			footer.setText("");
			embedMessage.setFooter(footer);

			message.setEmbeds(embedMessage);
			webhook.sendMessage(message.toJson());
		} catch (Exception e) {
			ServerWrapper.logError("Failed to send discord embed", e);
		}

	}

	public static void sendWellMessage(String desc) {
		if (!World.isLive()) {
			return;
		}

		try {
			Webhook webhook = new Webhook(
				"");
			Message message = new Message();

			Embed embedMessage = new Embed();

			embedMessage.setTitle("[Camel Statue] Boost has been enabled!");
			embedMessage.setColor(8917522);
			embedMessage.setDescription(desc);

			Thumbnail thumbnail = new Thumbnail();
			embedMessage.setThumbnail(thumbnail);

			Footer footer = new Footer();
			footer.setText("");
			embedMessage.setFooter(footer);

			message.setEmbeds(embedMessage);
			webhook.sendMessage(message.toJson());
		} catch (Exception e) {
			ServerWrapper.logError("Failed to send discord embed", e);
		}

	}

	public static void sendShopLogsToDiscord(Player player, String shopInfo, boolean buy) {
		if (!World.isLive()) {
			return;
		}

		try {
			Webhook webhook = new Webhook(
				"");
			Message message = new Message();

			Embed embedMessage = new Embed();
			String buyorsell = buy ? "bought" : "sold";

			embedMessage.setTitle(player.getName() + " just " + buyorsell + " an item.");

			embedMessage.setDescription(shopInfo);
			embedMessage.setColor(8917522);

			Footer footer = new Footer();
			footer.setText("");
			embedMessage.setFooter(footer);

			message.setEmbeds(embedMessage);
			webhook.sendMessage(message.toJson());
		} catch (Exception e) {
			ServerWrapper.logError("Failed to send discord embed", e);
		}
	}

	public static void sendAdminDBLogsToDiscord(Player player) {
		if (!World.isLive()) {
			return;
		}

		try {
			Webhook webhook = new Webhook(
				"");
			Message message = new Message();

			Embed embedMessage = new Embed();

			embedMessage.setTitle(player.getName() + " just spawned a donation boss.");

			embedMessage.setColor(8917522);

			Footer footer = new Footer();
			footer.setText("");
			embedMessage.setFooter(footer);

			message.setEmbeds(embedMessage);
			webhook.sendMessage(message.toJson());
		} catch (Exception e) {
			ServerWrapper.logError("Failed to send discord embed", e);
		}
	}

	public static void sendAdminDonorDealAmountLogsToDiscord(Player player, int amount, Player p2) {
		if (!World.isLive()) {
			return;
		}

		try {
			Webhook webhook = new Webhook(
				"");
			Message message = new Message();

			Embed embedMessage = new Embed();

			embedMessage.setTitle(player.getName() + " has just given " + amount + " store credits to " + p2.getName() + ".");

			embedMessage.setColor(8917522);

			Footer footer = new Footer();
			footer.setText("");
			embedMessage.setFooter(footer);

			message.setEmbeds(embedMessage);
			webhook.sendMessage(message.toJson());
		} catch (Exception e) {
			ServerWrapper.logError("Failed to send discord embed", e);
		}
	}

	public static void sendAdminShopLogsToDiscord(Player player, String shopInfo, boolean buy) {
		if (!World.isLive()) {
			return;
		}

		try {
			Webhook webhook = new Webhook(
				"");
			Message message = new Message();

			Embed embedMessage = new Embed();
			String buyorsell = buy ? "bought" : "sold";

			embedMessage.setTitle(player.getName() + " just " + buyorsell + " an item.");

			embedMessage.setDescription(shopInfo);
			embedMessage.setColor(8917522);

			Footer footer = new Footer();
			footer.setText("");
			embedMessage.setFooter(footer);

			message.setEmbeds(embedMessage);
			webhook.sendMessage(message.toJson());
		} catch (Exception e) {
			ServerWrapper.logError("Failed to send discord embed", e);
		}
	}

	public static void sendPMLogsToDiscord(Player player, String messageTo, String privateMessage) {
		if (!World.isLive()) {
			return;
		}

		try {
			Webhook webhook = new Webhook(
				"");
			Message message = new Message();

			Embed embedMessage = new Embed();

			embedMessage.setTitle(player.getName() + " sent a PM to " + messageTo + ".");

			embedMessage.setDescription(privateMessage);
			embedMessage.setColor(8917522);

			Footer footer = new Footer();
			footer.setText("");
			embedMessage.setFooter(footer);

			message.setEmbeds(embedMessage);
			webhook.sendMessage(message.toJson());
		} catch (Exception e) {
			ServerWrapper.logError("Failed to send discord embed", e);
		}
	}

	public static void sendTradeLogsToDiscord(Player player, String trading, String itemsTraded) {
		if (!World.isLive()) {
			return;
		}

		try {
			Webhook webhook = new Webhook(
				"");
			Message message = new Message();

			Embed embedMessage = new Embed();

			embedMessage.setTitle(player.getName() + " traded items to " + trading + ".");

			embedMessage.setDescription(itemsTraded);
			embedMessage.setColor(8917522);

			Footer footer = new Footer();
			footer.setText("");
			embedMessage.setFooter(footer);

			message.setEmbeds(embedMessage);
			webhook.sendMessage(message.toJson());
		} catch (Exception e) {
			ServerWrapper.logError("Failed to send discord embed", e);
		}
	}

	public static void sendTradeLogsToDiscordForOwner(Player player, String trading, String itemsTraded) {
		if (!World.isLive()) {
			return;
		}

		try {
			Webhook webhook = new Webhook(
				"");
			Message message = new Message();

			Embed embedMessage = new Embed();

			embedMessage.setTitle(player.getName() + " traded items to " + trading + ".");

			embedMessage.setDescription(itemsTraded);
			embedMessage.setColor(8917522);

			Footer footer = new Footer();
			footer.setText("");
			embedMessage.setFooter(footer);

			message.setEmbeds(embedMessage);
			webhook.sendMessage(message.toJson());
		} catch (Exception e) {
			ServerWrapper.logError("Failed to send discord embed", e);
		}
	}

	public static void sendRefClaimLogToDiscord(Player player) {
		if (!World.isLive()) {
			return;
		}

		try {
			Webhook webhook = new Webhook(
				"");
			Message message = new Message();

			Embed embedMessage = new Embed();

			embedMessage.setTitle(player.getName() + " claimed a referral code.");

			embedMessage.setColor(8917522);

			Footer footer = new Footer();
			footer.setText("");
			embedMessage.setFooter(footer);

			message.setEmbeds(embedMessage);
			webhook.sendMessage(message.toJson());
		} catch (Exception e) {
			ServerWrapper.logError("Failed to send discord embed", e);
		}
	}


	public static void sendCommandLogsToDiscordForOwner(Player player, String trading, String commandName,
	                                                    String[] args) {
		if (!World.isLive()) {
			return;
		}

		try {
			Webhook webhook = new Webhook(
				"");
			Message message = new Message();

			Embed embedMessage = new Embed();

			embedMessage.setTitle(player.getName() + " used the command " + commandName + ".");

			embedMessage.setDescription("The arguments were: " + Arrays.toString(args));
			embedMessage.setColor(8917522);

			Footer footer = new Footer();
			footer.setText("");
			embedMessage.setFooter(footer);

			message.setEmbeds(embedMessage);
			webhook.sendMessage(message.toJson());
		} catch (Exception e) {
			ServerWrapper.logError("Failed to send discord embed", e);
		}
	}

	public static void sendGambleLogsToDiscord(String gambleItemsWon, String winnerName, String loserName) {
		if (!World.isLive()) {
			return;
		}

		try {
			Webhook webhook = new Webhook(
				"");
			Message message = new Message();

			Embed embedMessage = new Embed();

			embedMessage.setTitle(winnerName + " won a gamble against " + loserName + ".");

			embedMessage.setDescription("Total pot won: " + gambleItemsWon);

			embedMessage.setColor(8917522);

			Footer footer = new Footer();
			footer.setText("");
			embedMessage.setFooter(footer);

			message.setEmbeds(embedMessage);
			webhook.sendMessage(message.toJson());
		} catch (Exception e) {
			ServerWrapper.logError("Failed to send discord embed", e);
		}
	}

	public static void sendRemoveFromTradepostCofferToDiscord(Player player, long amountRemoved) {
		if (!World.isLive()) {
			return;
		}

		try {
			Webhook webhook = new Webhook(
				"");
			Message message = new Message();

			Embed embedMessage = new Embed();

			embedMessage.setTitle(player.getName() + " removed " + NumberUtils.formatNumber(amountRemoved)
				+ "gp from their trade post coffer.");

			embedMessage.setDescription("Trade post coffer removal");
			embedMessage.setColor(8917522);

			Footer footer = new Footer();
			footer.setText("");
			embedMessage.setFooter(footer);

			message.setEmbeds(embedMessage);
			webhook.sendMessage(message.toJson());
		} catch (Exception e) {
			ServerWrapper.logError("Failed to send discord embed", e);
		}
	}

	public static void sendBondGiftToDiscord(String playerName, String targetName, Item bond) {
		if (!World.isLive()) {
			return;
		}

		try {
			Webhook webhook = new Webhook(
				"");
			Message message = new Message();

			Embed embedMessage = new Embed();

			embedMessage.setTitle(playerName + " has gifted a " + bond.getDef().name + " to " + targetName + ".");

			embedMessage.setDescription("Bond gift");
			embedMessage.setColor(8917522);

			Footer footer = new Footer();
			footer.setText("");
			embedMessage.setFooter(footer);

			Thumbnail thumbnail = new Thumbnail();
			thumbnail.setUrl("https://static.runelite.net/cache/item/icon/" + bond.getId() + ".png");
			embedMessage.setThumbnail(thumbnail);

			message.setEmbeds(embedMessage);
			webhook.sendMessage(message.toJson());
		} catch (Exception e) {
			ServerWrapper.logError("Failed to send discord embed", e);
		}
	}

	private static String getMapLocation(Position position) {
		return "[%s](https://explv.github.io/?centreX=%d&centreY=%d&centreZ=%d&zoom=10)"
			.formatted(position.toString(), position.getX(), position.getY(), position.getZ());
	}

	public static void sendPickupLogsToDiscord(Player player, Item item, boolean manuallyDropped) {
		if (!World.isLive()) {
			return;
		}

		try {
			Webhook webhook = new Webhook(
				"");
			Message message = new Message();

			Embed embedMessage = new Embed();

			embedMessage.setTitle("%s Picked up an item: %s x %s".formatted(
				player.getName(),
				Utils.formatMoneyString(item.getAmount()),
				item.getDef().name
			));
			embedMessage.setFields(
				new Field("Position:", getMapLocation(player.getPosition()), true)
			);

			embedMessage.setDescription(manuallyDropped ? "Item another player dropped picked up." : "Item picked up.");
			embedMessage.setColor(8917522);

			Footer footer = new Footer();
			footer.setText("");
			embedMessage.setFooter(footer);

			message.setEmbeds(embedMessage);
			webhook.sendMessage(message.toJson());
		} catch (Exception e) {
			ServerWrapper.logError("Failed to send discord embed", e);
		}
	}

	public static void sendDropItemToDiscord(Player player, Item item) {
		if (!World.isLive()) {
			return;
		}

		try {
			Webhook webhook = new Webhook(
				"");
			Message message = new Message();

			Embed embedMessage = new Embed();

			embedMessage.setTitle(
				"%s dropped an item: `%s`x %s".formatted(
					player.getName(),
					item.getDef().name,
					Utils.formatMoneyString(item.getAmount())
			));
			embedMessage.setFields(
				new Field("Position:", getMapLocation(player.getPosition()), true)
			);

			embedMessage.setDescription("Item dropped.");
			embedMessage.setColor(8917522);

			Footer footer = new Footer();
			footer.setText("");
			embedMessage.setFooter(footer);

			message.setEmbeds(embedMessage);
			webhook.sendMessage(message.toJson());
		} catch (Exception e) {
			ServerWrapper.logError("Failed to send discord embed", e);
		}
	}

	public static void sendClanLogsToDiscord(Player player, String clanMessage) {
		if (!World.isLive()) {
			return;
		}

		try {
			Webhook webhook = new Webhook(
				"");
			Message message = new Message();

			Embed embedMessage = new Embed();

			embedMessage.setTitle(player.getName() + " sent a message in a friends chat.");

			embedMessage.setDescription(clanMessage);
			embedMessage.setColor(8917522);

			Footer footer = new Footer();
			footer.setText("");
			embedMessage.setFooter(footer);

			message.setEmbeds(embedMessage);
			webhook.sendMessage(message.toJson());
		} catch (Exception e) {
			ServerWrapper.logError("Failed to send discord embed", e);
		}
	}

	public static void sendDonatorLogsToDiscord(Player player, String donationLog) {
		if (!World.isLive()) {
			return;
		}

		try {
			Webhook webhook = new Webhook(
				"");
			Message message = new Message();

			Embed embedMessage = new Embed();

			embedMessage.setTitle(player.getName() + " has just claimed a donation!");

			embedMessage.setDescription(donationLog);
			embedMessage.setColor(8917522);

			Footer footer = new Footer();
			footer.setText("");
			embedMessage.setFooter(footer);

			message.setEmbeds(embedMessage);
			webhook.sendMessage(message.toJson());
		} catch (Exception e) {
			ServerWrapper.logError("Failed to send discord embed", e);
		}
	}

	public static void sendTradePostAbortedListingToDiscord(Player player, String listingInfo, int itemId) {

		if (!World.isLive()) {
			return;
		}

		try {
			Webhook webhook = new Webhook("");
			Message message = new Message();

			Embed embedMessage = new Embed();

			embedMessage.setTitle(player.getName() + " has aborted the offer:  " + listingInfo + " on the trading post.");

			embedMessage.setDescription("Trade post listing");
			embedMessage.setColor(8917522);

			Thumbnail thumbnail = new Thumbnail();
			thumbnail.setUrl("https://static.runelite.net/cache/item/icon/" + itemId + ".png");
			embedMessage.setThumbnail(thumbnail);

			Footer footer = new Footer();
			footer.setText("");
			embedMessage.setFooter(footer);


			message.setEmbeds(embedMessage);
			webhook.sendMessage(message.toJson());
		} catch (Exception e) {
			ServerWrapper.logError("Failed to send discord embed", e);
		}
	}
}
