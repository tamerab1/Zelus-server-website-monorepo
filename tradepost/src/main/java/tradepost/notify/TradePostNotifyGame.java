package tradepost.notify;

import java.math.BigInteger;
import io.ruin.api.utils.MathUtils;
import io.ruin.api.utils.NumberUtils;
import io.ruin.model.World;
import io.ruin.model.entity.player.Player;
import io.ruin.model.item.Item;
import tradepost.*;
import tradepost.inter.ZelusMarket;

public class TradePostNotifyGame {

	public static void register() {
		TradePost.hooks.registerSilentAll((ctx) -> {
			switch (ctx) {
				case TradePost.Hook.OfferPlaced placed -> {
					notifyAdded(placed.player(), placed.offer());
				}
				case TradePost.Hook.ExchangedOffers exchange -> {
					notifyBought(
							exchange.buyer(),
							exchange.item(),
							exchange.totalAmountTransacted(),
							exchange.priceEach(),
							exchange.buyerRemainder());

					notifySold(
							exchange.seller(),
							exchange.item(),
							exchange.totalAmountTransacted(),
							exchange.priceEach());
				}
				default -> {
				}
			}
		});
	}

	private static void notifySold(String ownerUid, int item, BigInteger amount, BigInteger price) {
		var owner = World.getPlayer(ownerUid);
		if (owner == null) {
			return;
		}
		var itemDef = new Item(item).getDef();
		owner.sendMessage(ZelusMarket.TAG + "Sold " + (MathUtils.gt(amount, BigInteger.ONE) ? amount + " x " : "")
				+ itemDef.name + " for " + ZelusMarket.coins(price) + " each. Collect your earnings at the Trading Post.");
	}

	private static void notifyBought(String ownerUid, int item, BigInteger amount,
			BigInteger priceEach, BigInteger remainder) {
		var owner = World.getPlayer(ownerUid);
		if (owner == null) {
			return;
		}
		var itemDef = new Item(item).getDef();
		owner.sendMessage(ZelusMarket.TAG + "Bought " + (MathUtils.gt(amount, BigInteger.ONE) ? amount + " x " : "")
				+ itemDef.name + " for " + ZelusMarket.coins(priceEach) + " each.");

		if (MathUtils.gt(remainder, BigInteger.ZERO)) {
			owner.sendMessage(ZelusMarket.TAG + "The unused " + ZelusMarket.coins(remainder)
					+ " coins from your buy offer are in your collection box.");
		}
	}

	public static void notifyAdded(Player player, TradePostOffer offer) {
		var itemDef = new Item(offer.getItemId()).getDef();
		player.sendMessage(ZelusMarket.TAG + (offer.isSell() ? "Listed " : "Placed a buy offer for ")
				+ NumberUtils.formatNumber(offer.amount) + " x " + itemDef.name + " at "
				+ ZelusMarket.coins(offer.priceEach()) + " each.");
	}
}
