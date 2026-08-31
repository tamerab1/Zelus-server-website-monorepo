package tradepost.notify;

import java.awt.Color;
import java.math.BigInteger;

import discord.comp.impl.Embed;
import discord.comp.impl.Field;
import discord.comp.impl.Message;
import discord.comp.impl.Thumbnail;
import discord.webhooks.Webhook;
import io.ruin.api.utils.NumberUtils;
import io.ruin.cache.ObjType;
import properties.ServerProperties;
import lombok.extern.slf4j.Slf4j;
import tradepost.*;

/** Posts new Trade Post listings and completed exchanges to the #trade-post Discord channel,
 * using the same async webhook dispatcher as the rare-drop/collection-log hooks. Cancellation
 * intentionally does not post here -- only listing creation and finalized trades. */
@Slf4j
public class TradePostNotifyDiscord {

	private static final String DISCORD_HOOK = "trade_post_discord_hook";
	private static final Color BRAND_COLOR = new Color(8917522);

	public static void register() {
		TradePost.hooks.registerSilentAll(ctx -> {
			if (ctx instanceof TradePost.Hook.ExchangedOffers exchanged) {
				notifyExchange(exchanged.buyer(), exchanged.seller(), exchanged.item(),
						exchanged.totalAmountTransacted(), exchanged.priceEach(),
						exchanged.totalCoinsTransacted(), exchanged.buyerRemainder());
			} else if (ctx instanceof TradePost.Hook.OfferPlaced placed) {
				notifyListing(placed.offer());
			}
		});
	}

	private static void notifyListing(TradePostOffer offer) {
		var hook = ServerProperties.get(DISCORD_HOOK, "");
		if (hook.isEmpty())
			return;

		try {
			var itemName = ObjType.unnoted(offer.getItemId()).name;
			var action = offer.isBuy() ? "buy" : "sell";

			var embed = new Embed();
			embed.setTitle("New Trade Post Listing");
			embed.setDescription("`%s` wants to %s `%s`.".formatted(offer.getOwner(), action, itemName));
			embed.setColor(BRAND_COLOR);

			embed.setThumbnail(itemThumbnail(offer.getItemId()));
			embed.setFields(
					new Field("Item Name", itemName, true),
					new Field("Type", offer.isBuy() ? "Buy" : "Sell", true),
					new Field("Amount", NumberUtils.formatNumber(offer.getAmount()), true),
					new Field("Price each", NumberUtils.formatCoins(offer.priceEach()) + " coins", true));

			var message = new Message();
			message.setEmbeds(embed);
			Webhook.send(hook, message);
		} catch (Exception e) {
			log.error("Failed to send Trade Post 'new listing' webhook", e);
		}
	}

	private static Thumbnail itemThumbnail(int unnotedItemId) {
		var thumbnail = new Thumbnail();
		thumbnail.setUrl("https://static.runelite.net/cache/item/icon/" + unnotedItemId + ".png");
		return thumbnail;
	}

	private static void notifyExchange(String buyer, String seller, int item, BigInteger amount,
			BigInteger priceEach, BigInteger finalCoins, BigInteger buyerRemainder) {
		var hook = ServerProperties.get(DISCORD_HOOK, "");
		if (hook.isEmpty())
			return;

		try {
			var itemName = ObjType.unnoted(item).name;

			var embed = new Embed();
			embed.setTitle("Trade Post Exchange");
			embed.setDescription("`%s` sold `%s` to `%s`.".formatted(seller, itemName, buyer));
			embed.setColor(BRAND_COLOR);

			embed.setThumbnail(itemThumbnail(item));
			embed.setFields(
					new Field("Item Name", itemName, true),
					new Field("Amount", NumberUtils.formatNumber(amount), true),
					new Field("Buyer", buyer, true),
					new Field("Seller", seller, true),
					new Field("Price - tax", NumberUtils.formatCoins(finalCoins) + " coins", true),
					new Field("Price each", NumberUtils.formatCoins(priceEach) + " coins", true),
					new Field("Remainder", NumberUtils.formatCoins(buyerRemainder) + " coins", true));

			var message = new Message();
			message.setEmbeds(embed);
			Webhook.send(hook, message);
		} catch (Exception e) {
			log.error("Failed to send Trade Post 'exchange' webhook", e);
		}
	}
}
