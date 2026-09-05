package io.ruin.model.shop;


import io.ruin.cache.ObjType;
import io.ruin.model.entity.player.Player;
import io.ruin.model.item.Item;
import lombok.Getter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

@Slf4j

public abstract class CurrencyHandler {

	public static final int NOT_ENOUGH_SPACE = -2;

	@Accessors(fluent = true)
	@Getter
	protected String name, pluralName;

	public CurrencyHandler(String name, String pluralName) {
		this.name = name;
		this.pluralName = pluralName;
	}

	public CurrencyHandler(String name) {
		this.name = name;
		this.pluralName = name;
	}

	public abstract int getCurrencyCount(Player player);

	public int getPossibleBuyAmount(Player player, ShopItem shopItem, int amount) {
		int pricePer = shopItem.getPrice();
		int currencyAmount = getCurrencyCount(player);
		if (currencyAmount <= 0) {
			return 0;
		}
		long requestedCost = (long) pricePer * (long) amount;
		if (requestedCost > Integer.MAX_VALUE) {
			amount = Integer.MAX_VALUE / pricePer;
			requestedCost = (long) pricePer * (long) amount;
			log.debug("total was over max int, set to {}", amount);
		}
		log.debug("buy amt request for {} {}", requestedCost, currencyAmount);
		if (requestedCost > currencyAmount) {
			int newAmount = currencyAmount / pricePer;
			int remaining = currencyAmount - newAmount;

			log.debug("{} {}", newAmount, remaining);
			amount = newAmount;
		}
		if (shopItem.getAdditionalItems() != null && !shopItem.getAdditionalItems().isEmpty()) {
			if (player.getInventory().getFreeSlots() <= 1) {
				return NOT_ENOUGH_SPACE;
			}
			int slotsPerPurchase = 1;
			for (Item additional : shopItem.getAdditionalItems()) {
				ObjType def = additional.getDef();
				if (def.stackable) {
					slotsPerPurchase++;
				} else {
					slotsPerPurchase += additional.getAmount();
				}
			}

			log.debug("{} {}", player.getInventory().getFreeSlots(), slotsPerPurchase);


			int possibleAmount = Math.floorDiv(player.getInventory().getFreeSlots(), slotsPerPurchase);

			log.debug("{} {} {}", player.getInventory().getFreeSlots(), slotsPerPurchase, possibleAmount);
			if (possibleAmount <= 0) {
				return NOT_ENOUGH_SPACE;
			}

			amount = Math.min(amount, possibleAmount);
		}
		if (!player.getInventory().hasRoomFor(shopItem.getId(), amount)) {
			if (currencyAmount == pricePer) {
				return 1;
			}
			int capacity = player.getInventory().getCapacityFor(shopItem.getId());
			return capacity == 0 ? NOT_ENOUGH_SPACE : capacity;
		}
		return amount;
	}

	public abstract int removeCurrency(Player player, int amount);

	public abstract int addCurrency(Player player, int amount);

}
