package tradepost;



import java.math.BigInteger;
import com.google.errorprone.annotations.CheckReturnValue;
import core.task.Continuation;
import core.task.Continuations;
import io.ruin.HooksV2;
import io.ruin.api.utils.MathUtils;
import io.ruin.cache.ObjType;
import io.ruin.model.World;
import io.ruin.model.entity.player.Player;
import io.ruin.model.entity.player.SecondaryGroup;
import io.ruin.model.item.Item;
import lombok.experimental.ExtensionMethod;
import lombok.extern.slf4j.Slf4j;
import tradepost.TradePostOffer.Kind;
import tradepost.db.TradePostHistoriesDb;
import tradepost.db.TradePostOffersDb;
import tradepost.hook.Attributes;
import tradepost.inter.ZelusMarket;

import static core.task.api.API.*;

/**
 * The Zelus Market engine (formerly Reason's trade post).
 *
 * <p>Concurrency model: every method here runs on the game thread inside a continuation, which only yields at an
 * {@code await()} (a database round trip) or {@code sleep}. Each state change is therefore done in ONE synchronous
 * block with no await in between: validate, take the payment, mark the offer sold / detach it, and pay both sides.
 * The database writes are scheduled AFTER that block, so a double click, two buyers on the same listing, or a slow or
 * failed database can never let the same stock or coins be handed out twice.
 */
@Slf4j
@ExtensionMethod(Attributes.class)
public class TradePost {
	public static final HooksV2<Hook> hooks = new HooksV2<>(Hook.class);

	public sealed interface Hook {
		public record OfferPlaced(Player player, TradePostOffer offer) implements Hook {
		}

		public record OfferAborted(Player player, TradePostOffer offer, BigInteger amount,
				BigInteger priceEach) implements Hook {
		}

		public record ExchangedOffers(
				String buyer,
				String seller,
				int item,
				BigInteger priceEach,
				BigInteger totalCoinsTransacted,
				BigInteger totalAmountTransacted,
				BigInteger buyerRemainder) implements Hook {
		}
	}

	// Slots 1-3 are open to everyone; slot 4 needs Donator+, slot 5 needs Super Donator+, and
	// slots 6-10 need Elite Donator+ (equalToOrGreaterThan so higher tiers keep the perk).
	public static int maxOfferSlots(Player player) {
		var group = player.getSecondaryGroup();
		if (group.equalToOrGreaterThan(SecondaryGroup.ELITE_DONATOR)) return 10;
		if (group.equalToOrGreaterThan(SecondaryGroup.SUPER_DONATOR)) return 5;
		if (group.equalToOrGreaterThan(SecondaryGroup.DONATOR)) return 4;
		return 3;
	}

	// Loads temporary slotted attributes for a player
	public static void loadSlots(Player player) {
		TradePostCache.offersByPlayer(player.uuid()).forEach(it -> {
			var pTradePost = player.tradePost();
			pTradePost.offers[it.slot] = it;
		});
	}

	/** Redraws whichever market screen the player has open (the new one, or a legacy screen). */
	public static void refreshViews(Player player) {
		if (player == null || !player.isOnline()) {
			return;
		}
		player.tradePost().inter.refresh(player);
		ZelusMarket.refresh(player);
	}

	@CheckReturnValue
	public static Continuation<TradePostOffer> placeOffer(Player player, int _item, int _amount,
			long priceEach,
			TradePostOffer.Kind kind) {
		return placeOffer(player, _item, _amount, BigInteger.valueOf(priceEach), kind);
	}

	// tries to place an offer, may return null
	@CheckReturnValue
	public static Continuation<TradePostOffer> placeOffer(
			Player player,
			int itemId,
			int itemAmount,
			BigInteger priceEach,
			TradePostOffer.Kind kind) {

		return () -> {
			if (itemAmount < 1) {
				player.sendMessage("You must offer at least 1.");
				return null;
			}

			if (MathUtils.lt(priceEach, BigInteger.ONE)) {
				player.sendMessage("The price must be at least 1 coin.");
				return null;
			}

			if (MathUtils.gt(priceEach, BigInteger.valueOf(100_000_000_000L))) {
				player.sendMessage("Are you serious?");
				return null;
			}

			var pTradePost = player.tradePost();
			var slot = pTradePost.offersEmptySlot(maxOfferSlots(player));
			if (slot == -1) {
				player.sendMessage("You have the maximum amount of offers active.");
				return null;
			}

			var amount = BigInteger.valueOf(itemAmount);
			var item = ObjType.get(itemId);
			var itemIdUnnoted = item.unnotedId();

			if (!removeItemsForOffer(player, itemId, kind, priceEach, amount)) {
				return null;
			}

			// Reserve the slot across the insert's await: a second offer placed meanwhile must not pick the same slot
			// (two rows in one slot would share every later update and delete).
			pTradePost.reserved[slot] = true;
			TradePostOffer offer;
			try {
				var price = priceEach.multiply(amount);
				offer = insert(player, itemIdUnnoted, slot, price, player.uuid(), amount, kind).await();
			} catch (RuntimeException e) {
				log.error("Unable to insert an offer: player = {} slot = {}", player.uuid(), slot, e);
				offer = null;
			} finally {
				pTradePost.reserved[slot] = false;
			}
			if (offer == null) {
				log.warn("Unable to insert an offer: player = {} slot = {}", player.uuid(), slot);
				rollbackItemsForOffer(player, itemId, kind, priceEach, amount);
				return null;
			}

			hooks.handle(new Hook.OfferPlaced(player, offer));
			TradePostProcess.queueAdd(offer);

			pTradePost.offers[offer.slot] = offer;
			refreshViews(player);
			return offer;
		};
	}

	private static boolean removeItemsForOffer(
			Player player,
			int itemId,
			Kind kind,
			BigInteger priceEach,
			BigInteger amount) {

		var inv = player.getInventory();
		var item = ObjType.get(itemId);
		switch (kind) {

			case Buy -> {
				var totalPrice = priceEach.multiply(amount);
				if (!inv.removeCoinsOrPlatTokens(totalPrice)) {
					player.sendMessage("You do not have enough coins to buy this item.");
					return false;
				}
			}

			case Sell -> {
				if (!item.tradeable) {
					player.sendMessage("You cannot sell " + item.name + ".");
					return false;
				}

				// remove(id, amount, acceptNoted) only matches attribute-free stacks, so an upgraded copy of the same
				// item id is never taken by mistake.
				var amountInt = amount.intValueExact();
				if (inv.count(item.id, true) < amountInt) {
					player.sendMessage("You do not have enough items to place this offer.");
					return false;
				}
				var removed = inv.remove(item.id, amountInt, true);
				if (removed != amountInt) {
					if (removed > 0) {
						inv.addOrSendToBank(item.id, removed);
					}
					player.sendMessage("You do not have enough items to place this offer.");
					return false;
				}
			}

			default -> {
				player.sendMessage("Unknown offer kind");
				return false;
			}
		}

		return true;
	}

	private static void rollbackItemsForOffer(
			Player player,
			int itemId,
			Kind kind,
			BigInteger priceEach,
			BigInteger amount) {

		var inv = player.getInventory();
		var item = ObjType.get(itemId);
		switch (kind) {

			case Buy -> {
				var totalPrice = priceEach.multiply(amount);
				inv.addCoinsOrPlatTokens(totalPrice);
				player.sendMessage("The market could not take your offer; your coins have been returned.");
			}

			case Sell -> {
				var amountInt = amount.intValueExact();
				inv.addOrSendToBank(item.id, amountInt);
				player.sendMessage("The market could not take your offer; your items have been returned.");
			}

			default -> {
				return;
			}
		}

	}

	@CheckReturnValue
	public static Continuation<TradePostOffer> insert(
			Player player,
			int itemId,
			int slot,
			long price,
			String owner,
			long startAmount,
			Kind kind) {
		return insert(player, itemId, slot, BigInteger.valueOf(price), owner,
				BigInteger.valueOf(startAmount), kind);
	}

	// Inserts new offer onto trade post, along with setting temporary slotted
	// player attribute for the offer.
	@CheckReturnValue
	public static Continuation<TradePostOffer> insert(
			Player player,
			int itemId,
			int slot,
			BigInteger price,
			String owner,
			BigInteger startAmount,
			Kind kind) {

		return () -> {
			var offer = TradePostOffersDb.insert(itemId, slot, price, owner, startAmount, kind).await();
			if (offer != null) {
				TradePostCache.insert(offer);
			}
			return offer;
		};
	}

	// Abort the offer by removing it and giving back the offer's items
	@CheckReturnValue
	public static Continuation.Void abort(Player player, TradePostOffer offer) {
		return () -> {
			if (offer.isEmpty()) {
				player.sendMessage("That offer has already completed; check your collection box.");
				return;
			}
			if (!offer.owner.equalsIgnoreCase(player.uuid())) {
				player.sendMessage("That is not your offer.");
				return;
			}

			// Detach first (amount -> 0, out of the cache and the slot), then refund: a second abort or a buyer arriving
			// in the same tick sees an empty offer.
			var amount = offer.amount;
			var priceEach = offer.priceEach();
			detach(offer);

			if (offer.isBuy()) {
				player.getInventory().addCoinsOrPlatTokens(priceEach.multiply(amount));
			} else {
				giveItem(player, offer.getItemId(), amount.intValueExact(), "your cancelled listing");
			}

			hooks.handle(new Hook.OfferAborted(player, offer, amount, priceEach));
			refreshViews(player);
			TradePostOffersDb.removeOffer(offer).await();
		};
	}

	// Removes the offer from the trade post completely along with temporary
	// player slotted attribute for that offer.
	@CheckReturnValue
	public static Continuation.Void remove(TradePostOffer offer) {
		return () -> {
			detach(offer);
			TradePostOffersDb.removeOffer(offer).await();
		};
	}

	/** The synchronous half of {@link #remove}: empties the offer and takes it out of the cache and its slot. */
	private static void detach(TradePostOffer offer) {
		var player = World.getPlayer(offer.owner);
		if (player != null) {
			var pTradePost = player.tradePost();
			if (pTradePost.offers[offer.slot] == offer) {
				pTradePost.offers[offer.slot] = null;
			}
		}

		offer.remove();
		TradePostCache.remove(offer);
		TradePostFeatured.unfeature(offer);
	}

	/** Legacy screen entry point (interface 1132): buys through {@link #buyNow}. */
	@CheckReturnValue
	public static Continuation.Void exchange(Player buyer, TradePostOffer offer, int _amount) {
		return () -> buyNow(buyer, offer, _amount);
	}

	/**
	 * Instant buy from a sell listing. Runs entirely on the game thread with no await before the hand-over: the
	 * listing is re-validated, the buyer's coins are taken and the stock is marked sold BEFORE any item or coin is
	 * granted, so double clicks and simultaneous buyers can only ever buy what is really left.
	 *
	 * @return the amount bought (0 if nothing happened)
	 */
	public static int buyNow(Player buyer, TradePostOffer offer, int requested) {
		if (offer == null || offer.kind != Kind.Sell) {
			buyer.sendMessage("That listing is not for sale.");
			return 0;
		}
		if (offer.isEmpty() || !TradePostCache.contains(offer)) {
			buyer.sendMessage("That listing has already been sold or cancelled.");
			return 0;
		}
		if (offer.owner.equalsIgnoreCase(buyer.uuid())) {
			buyer.sendMessage("You cannot buy your own listing.");
			return 0;
		}
		if (requested < 1) {
			buyer.sendMessage("You must buy at least 1.");
			return 0;
		}

		var amount = MathUtils.min(BigInteger.valueOf(requested), offer.amount);
		var priceEach = offer.priceEach();
		var totalPrice = priceEach.multiply(amount);
		if (!buyer.getInventory().removeCoinsOrPlatTokens(totalPrice)) {
			buyer.sendMessage("You need " + ZelusMarket.coins(totalPrice) + " coins (or platinum tokens) in your inventory for that.");
			return 0;
		}

		// Mark the stock sold (and detach a sold-out listing) in the same tick the coins were taken.
		offer.amount = offer.amount.subtract(amount);
		var soldOut = offer.isEmpty();
		if (soldOut) {
			detach(offer);
		}

		exchanged(buyer.uuid(), offer.owner, offer.itemId, amount, BigInteger.ZERO, priceEach,
				TradePostTaxing.apply(totalPrice));

		Continuations.schedule(() -> {
			if (soldOut) {
				TradePostOffersDb.removeOffer(offer).await();
			} else {
				TradePostOffersDb.upsert(offer).await();
			}
		});
		return amount.intValueExact();
	}

	// Exchanges two valid offers with each other
	@CheckReturnValue
	public static Continuation.Void exchange(
			TradePostOffer buy,
			TradePostOffer sell,
			BigInteger amount) {

		return () -> {

			if (sell.kind != Kind.Sell) {
				return;
			}

			if (buy.kind != Kind.Buy) {
				return;
			}

			if (buy.isEmpty()) {
				return;
			}

			if (sell.isEmpty()) {
				return;
			}

			if (MathUtils.gt(sell.priceEach(), buy.priceEach())) {
				throw new IllegalStateException("Selling for more than buy offer");
			}

			var amountTransacted = amount;
			if (MathUtils.lt(sell.amount, amountTransacted)) {
				amountTransacted = sell.amount;
			}

			if (MathUtils.lt(buy.amount, amountTransacted)) {
				amountTransacted = buy.amount;
			}

			if (MathUtils.lt(amountTransacted, BigInteger.ONE)) {
				return;
			}

			var priceEach = buy.priceEach();
			if (MathUtils.lt(sell.priceEach(), buy.priceEach())) {
				priceEach = sell.priceEach();
			}

			var buyerRemainder = buy.priceEach()
					.multiply(amountTransacted)
					.subtract(priceEach.multiply(amountTransacted));

			sell.amount = sell.amount.subtract(amountTransacted);
			buy.amount = buy.amount.subtract(amountTransacted);

			// The seller is paid for what was really transacted (not the requested amount).
			var totalCoinsAmount = finalCoinsAmount(amountTransacted, priceEach);

			var buyEmpty = buy.isEmpty();
			var sellEmpty = sell.isEmpty();
			if (buyEmpty) {
				detach(buy);
			}
			if (sellEmpty) {
				detach(sell);
			}

			exchanged(
					buy.owner, sell.owner,
					sell.itemId, amountTransacted, buyerRemainder, priceEach,
					totalCoinsAmount);

			// Persist after both sides are settled in memory.
			if (buyEmpty) {
				TradePostOffersDb.removeOffer(buy).await();
			} else {
				TradePostOffersDb.upsert(buy).await();
			}
			if (sellEmpty) {
				TradePostOffersDb.removeOffer(sell).await();
			} else {
				TradePostOffersDb.upsert(sell).await();
			}
		};
	}

	// When exchange finalized
	private static void exchanged(
			String buyerUid, String sellerUid,
			int itemId, BigInteger amount, BigInteger buyerRemainder, BigInteger priceEach,
			BigInteger finalCoinsAmount) {

		giveItemsToBuyer(buyerUid, itemId, amount, buyerRemainder);
		giveItemsToSeller(sellerUid, finalCoinsAmount);

		hooks.handle(new Hook.ExchangedOffers(
				buyerUid, sellerUid, itemId, priceEach, finalCoinsAmount, amount, buyerRemainder));

		var item = new Item(itemId, amount.intValue());
		TradePostHistoriesDb.append(buyerUid, sellerUid, TradePostOffer.Kind.Sell, item, priceEach);
		TradePostHistoriesDb.append(sellerUid, buyerUid, TradePostOffer.Kind.Buy, item, priceEach);

		refreshViews(World.getPlayer(buyerUid));
		refreshViews(World.getPlayer(sellerUid));
	}

	private static BigInteger finalCoinsAmount(BigInteger itemAmount, BigInteger priceEach) {
		var totalPrice = itemAmount.multiply(priceEach);
		totalPrice = TradePostTaxing.apply(totalPrice);
		return totalPrice;
	}

	private static void giveItemsToSeller(String sellerUid, BigInteger totalCoinsAmount) {
		TradePostCoffer.addCoins(sellerUid, totalCoinsAmount);
	}

	/** Online buyers get the items straight away (inventory, then bank); anyone else gets them in the collection box. */
	private static void giveItemsToBuyer(String buyerUid, int item, BigInteger amount,
			BigInteger coinsRemainder) {
		if (MathUtils.gt(coinsRemainder, BigInteger.ZERO)) {
			TradePostCoffer.addCoins(buyerUid, coinsRemainder);
		}
		var buyer = World.getPlayer(buyerUid);
		if (buyer != null && buyer.isOnline()) {
			giveItem(buyer, item, amount.intValueExact(), "your market purchase");
		} else {
			TradePostCoffer.add(buyerUid, item, amount);
		}
	}

	/**
	 * Inventory (noted when more than one), then bank, then the persistent collection box: items are never dropped on
	 * the floor and never lost to a full inventory / bank.
	 */
	public static void giveItem(Player player, int itemId, int amount, String what) {
		var unnotedId = ObjType.unnotedId(itemId);
		var def = ObjType.get(unnotedId);
		var invId = amount > 1 && !def.stackable ? ObjType.notedId(unnotedId) : unnotedId;
		var remaining = amount;
		var toInventory = player.getInventory().add(invId, remaining);
		remaining -= toInventory;
		if (remaining > 0) {
			var banked = player.getBank().deposit(unnotedId, remaining);
			if (banked > 0) {
				player.sendMessage(banked + " x " + def.name + " from " + what + " went to your bank.");
			}
			remaining -= banked;
		}
		if (remaining > 0) {
			TradePostCoffer.add(player.uuid(), unnotedId, BigInteger.valueOf(remaining));
			player.sendMessage(ZelusMarket.TAG + remaining + " x " + def.name + " from " + what
					+ " is waiting in your collection box (My Shop).");
		}
	}
}
