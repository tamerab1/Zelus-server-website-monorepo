package io.ruin.model.inter.handlers.shopinterface;

import io.ruin.api.utils.NumberUtils;
import io.ruin.cache.Color;
import io.ruin.cache.ObjType;
import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.Interface;
import io.ruin.model.inter.InterfaceHandler;
import io.ruin.model.inter.ToplevelComponent;
import io.ruin.model.inter.actions.DefaultAction;
import io.ruin.model.item.Item;
import io.ruin.model.shop.Currency;
import io.ruin.model.inter.handlers.shopinterface.CustomShop2;

import java.util.Arrays;

public class CustomShopInterface2 {
	// Component 41 on interface 891 is the buy-grid's scrollable layer, sized 456x224.
	// Root cause of the long-standing "no items visible" bug: it was being driven with
	// PacketSender.sendItem() (IfSetObject) against 60 cloned type=2 sub-widgets (52-111).
	// IfSetObject only updates type=6 "model" widgets (e.g. ONE_ITEM_DIALOGUE's OBJ_MODEL1,
	// confirmed via direct cache inspection) -- it's a no-op against type=2 container widgets.
	//
	// The real client mechanism for a live item grid (confirmed by inspecting the vanilla
	// shop's own interface 300/component 16, which already renders correctly) is: call
	// clientscript 149 ("iiiiiisssss": combinedId, containerId, cols, rows, 0, -1, then 5
	// right-click option labels matching option codes 1-5) once to bind a widget to a
	// container id + grid shape, then push contents to that same container id via a plain
	// UpdateInvFull (PacketSender.sendItems). The sell-side grid in open() below already did
	// this correctly (script 149 targeting interface 301/component 0, container 93) -- this
	// mirrors that for the buy side, targeting 891/41 + container 10005.
	// "rows" genuinely controls pixel pitch (confirmed: 6 was too sparse, 15 was too tight).
	// 10 is the current best-guess middle ground.
	private static final int BUY_GRID_COLUMNS = 10;
	private static final int BUY_GRID_ROWS = 10;

	// The shop tabs reachable from interface 891 -- search and item resolution both scan
	// across all of them, not just whichever tab happens to be active.
	private static final CustomShop2[] SEARCHABLE_SHOPS = {
		CustomShop2.PKP_SHOP, CustomShop2.BLOOD_MONEY_STORE, CustomShop2.ZELUS_POINTS_SHOP,
		CustomShop2.ACHIEVEMENT_POINTS_SHOP, CustomShop2.DONATOR_STORE, CustomShop2.VOTE_STORE,
		CustomShop2.PVM_SHOP
	};

	// Finds which shop tab a given item actually belongs to, preferring the player's currently
	// active tab (so pricing/currency stays as displayed) and falling back to a scan across all
	// searchable shops -- needed because search results can surface items from tabs other than
	// the one currently open.
	private static CustomShop2 resolveShopForItem(Player player, int itemId) {
		CustomShop2 active = CustomShop2.get(player.getShopIdentifier());
		if (active != null && active.getShopItem(itemId) != null) {
			return active;
		}
		for (CustomShop2 shop : SEARCHABLE_SHOPS) {
			if (shop.isPvmOnly() && !player.isPvmMode()) {
				continue;
			}
			if (shop.getShopItem(itemId) != null) {
				return shop;
			}
		}
		return null;
	}

	// Package-private (not private): CustomShop2.refreshShop() also needs to reassert this
	// binding, for the same reason searchShop() does -- see its call site below.
	static void configureBuyGrid(Player player) {
		player.getPacketSender().sendClientScript(149, "iiiiiisssss", (891 << 16) | 41, 10005,
			BUY_GRID_COLUMNS, BUY_GRID_ROWS, 0, -1,
			"Value<col=ff9040>", "Buy 1<col=ff9040>", "Buy 5<col=ff9040>",
			"Buy 10<col=ff9040>", "Buy X<col=ff9040>");
	}

	public static void register() {
		InterfaceHandler.register(Interface.CUSTOM_SHOP2, h -> {
			h.actions[16] = (DefaultAction) (player, option, slot, itemId) -> {
				player.getPacketSender().sendString(891, 14, "PKP Shop");
				player.setShopIdentifier(13);
				handleEnteringShop(player, CustomShop2.PKP_SHOP);
				sendBalanceMessage(player, CustomShop2.PKP_SHOP);
				open(player, CustomShop2.getItemsFromShop(player));
			};
			h.actions[18] = (DefaultAction) (player, option, slot, itemId) -> {
				player.getPacketSender().sendString(891, 14, "Bounty Hunter Shop");
				player.setShopIdentifier(6);
				handleEnteringShop(player, CustomShop2.BLOOD_MONEY_STORE);
				sendBalanceMessage(player, CustomShop2.BLOOD_MONEY_STORE);
				open(player, CustomShop2.getItemsFromShop(player));
			};
			h.actions[20] = (DefaultAction) (player, option, slot, itemId) -> {
				player.getPacketSender().sendString(891, 14, "Zelus Points Shop");
				player.setShopIdentifier(14);
				handleEnteringShop(player, CustomShop2.ZELUS_POINTS_SHOP);
				sendBalanceMessage(player, CustomShop2.ZELUS_POINTS_SHOP);
				open(player, CustomShop2.getItemsFromShop(player));
			};
			h.actions[22] = (DefaultAction) (player, option, slot, itemId) -> {
				player.getPacketSender().sendString(891, 14, "Achievement Points Shop");
				player.setShopIdentifier(15);
				handleEnteringShop(player, CustomShop2.ACHIEVEMENT_POINTS_SHOP);
				sendBalanceMessage(player, CustomShop2.ACHIEVEMENT_POINTS_SHOP);
				open(player, CustomShop2.getItemsFromShop(player));
			};
			h.actions[24] = (DefaultAction) (player, option, slot, itemId) -> {
				player.getPacketSender().sendString(891, 14, "Donation Shop");
				player.setShopIdentifier(9);
				handleEnteringShop(player, CustomShop2.DONATOR_STORE);
				sendBalanceMessage(player, CustomShop2.DONATOR_STORE);
				open(player, CustomShop2.getItemsFromShop(player));
			};
			h.actions[26] = (DefaultAction) (player, option, slot, itemId) -> {
				player.getPacketSender().sendString(891, 14, "Voting Shop");
				player.setShopIdentifier(10);
				handleEnteringShop(player, CustomShop2.VOTE_STORE);
				sendBalanceMessage(player, CustomShop2.VOTE_STORE);
				open(player, CustomShop2.getItemsFromShop(player));
			};
			// Component 28 pre-existed on interface 891 (baked label "Supplies Shop", unused
			// until now) -- repurposed here for the PVM Point Shop. PVP_MODE accounts never
			// earn/spend PVM Points, so the tab itself refuses to open for them.
			h.actions[28] = (DefaultAction) (player, option, slot, itemId) -> {
				if (!player.isPvmMode()) {
					player.sendMessage("The PVM Point Shop is exclusive to PVM Mode accounts.");
					return;
				}
				player.getPacketSender().sendString(891, 14, "PVM Point Shop");
				player.setShopIdentifier(16);
				handleEnteringShop(player, CustomShop2.PVM_SHOP);
				sendBalanceMessage(player, CustomShop2.PVM_SHOP);
				open(player, CustomShop2.getItemsFromShop(player));
			};
			// Component 30 -- new tab added to interface 891 for selling Antique emblems back for
			// BH Points. Sell-only: no buy grid, see the sellAtListedPrice guard in buy() below.
			h.actions[30] = (DefaultAction) (player, option, slot, itemId) -> {
				player.getPacketSender().sendString(891, 14, "Sell Bounty Hunter Emblems");
				player.setShopIdentifier(17);
				handleEnteringShop(player, CustomShop2.SELL_BH_EMBLEMS);
				sendBalanceMessage(player, CustomShop2.SELL_BH_EMBLEMS);
				open(player, CustomShop2.getItemsFromShop(player));
			};
			h.simpleAction(49, player -> {
				player.stringInput("Enter an item name to search for:", search -> searchShop(player, search));
			});
			h.actions[41] = (DefaultAction) (player, option, slot, itemId) -> {
				if (option == 1) {
					priceCheck(player, slot, itemId);
				} else {
					attemptBuy(player, option, slot, itemId);
				}
			};

			h.closedAction = (p, i) -> {
				if (p.getActiveCustomShop2() != null) {
					p.getActiveCustomShop2().removePlayerFromShop(p);
					p.setActiveCustomShop2(null);
					p.closeInterface(ToplevelComponent.SIDEMODAL);
				}
			};
		});

		// Was dead code (never registered) -- the sell-side inventory grid in open() above has
		// been sending real items into 301/0 since the interface-891 rework, but nothing was
		// wired to handle clicks on it until now.
		InterfaceHandler.register(Interface.PLAYER_SHOP_INVENTORY, h -> h.actions[0] = (DefaultAction) (player, option, slot, itemId) -> {
			if (player.isVisibleInterface(Interface.CUSTOM_SHOP2)) {
				attemptSell(player, option, slot, itemId);
			}
		});
	}

	public static void handleEnteringShop(Player player, CustomShop2 CustomShop2) {
		CustomShop2.addPlayerToShop(player);
		player.setActiveCustomShop2(CustomShop2);
	}

	// Matches the old newshop-framework shops' openMessage() behaviour (e.g.
	// AchievementPointStore/PvmPointStore/ReasonPointStore) -- announce the player's current
	// balance in chat every time they switch to a shop tab.
	private static void sendBalanceMessage(Player player, CustomShop2 shop) {
		Currency currency = shop.getCurrency();
		int balance = currency.getCurrencyHandler().getCurrencyCount(player);
		player.sendMessage("You have " + Color.RED.wrap(NumberUtils.formatNumber(balance)) + " " + currency.getCurrencyHandler().name() + ".");
	}

	private static void searchShop(Player player, String search) {
		String needle = search.toLowerCase();
		java.util.LinkedHashSet<Integer> matchedIds = new java.util.LinkedHashSet<>();
		for (CustomShop2 shop : SEARCHABLE_SHOPS) {
			if (shop.isPvmOnly() && !player.isPvmMode()) {
				continue;
			}
			for (ShopItem si : shop.getShopItems()) {
				ObjType def = ObjType.get(si.getItemId());
				if (def != null && def.name.toLowerCase().contains(needle)) {
					matchedIds.add(def.id);
				}
			}
		}
		Item[] filtered = matchedIds.stream().map(Item::new).toArray(Item[]::new);

		int capacity = Arrays.stream(SEARCHABLE_SHOPS).mapToInt(shop -> shop.getShopItems().length).max().orElse(0);
		Item[] slots = new Item[capacity];
		boolean[] updatedSlots = new boolean[capacity];
		for (int i = 0; i < capacity; i++) {
			slots[i] = i < filtered.length ? filtered[i] : null;
			updatedSlots[i] = true;
		}
		// Container updates sent outside the open()/configureBuyGrid() flow don't render unless
		// the grid binding is reasserted first -- see configureBuyGrid()'s call site in open().
		configureBuyGrid(player);
		player.getPacketSender().updateItems(-1, 10005, slots, updatedSlots, capacity);
	}

	private static void attemptBuy(Player player, int option, int slot, int itemId) {
		CustomShop2 CustomShop2 = resolveShopForItem(player, itemId);
		if (CustomShop2 == null) {
			return;
		}

		ShopItem shopItem = CustomShop2.getShopItem(itemId);
		if (shopItem == null) {
			return;
		}

		Item item = new Item(shopItem.getItemId());

		ObjType itemDef = item.getDef();
		if (itemDef == null) {
			return;
		}

		if (ObjType.get(shopItem.getItemId()) != null && itemId != shopItem.getItemId()
			&& itemId != (ObjType.get(shopItem.getItemId()) != null ? itemDef.placeholderMainId : 0))
			return;

		if (option == 10) {
			item.examine(player);
			return;
		}

		if (!itemInShop(itemId, CustomShop2)) {
			player.sendMessage("");
			return;
		}

		ShopItem CustomShop2Item = Arrays.stream(CustomShop2.getShopItems())
			.filter(si -> si.getItemId() == itemId)
			.findFirst()
			.orElse(null);

		if (CustomShop2Item == null) {
			return;
		}

		if (option == 2) {
			buy(player, CustomShop2Item, CustomShop2, itemId, 1);
		} else if (option == 3) {
			buy(player, CustomShop2Item, CustomShop2, itemId, 5);
		} else if (option == 4) {
			buy(player, CustomShop2Item, CustomShop2, itemId, 10);
		} else if (option == 5) {
			player.integerInput("How many do you want to buy?",
				amt -> buy(player, CustomShop2Item, CustomShop2, itemId, amt));
		}
	}

	private static void buy(Player player, ShopItem CustomShop2Item, CustomShop2 CustomShop2, int itemId, int amount) {
		if (amount <= 0 || CustomShop2 == null || CustomShop2Item == null || itemId <= 0)
			return;

		if (CustomShop2.isSellAtListedPrice()) {
			player.sendMessage("This shop only buys items from you -- it doesn't sell anything.");
			return;
		}


		/**
		 * Container check
		 */
		Item item = new Item(CustomShop2Item.getItemId());
		ObjType itemDef = item.getDef();
		if (itemDef == null) {
			return;
		}
		int freeSlots = player.getInventory().getFreeSlots();
		if (amount > freeSlots) {
			/**
			 * Attempt to note the given item.
			 */
			if (!itemDef.isNote() && itemDef.notedId != -1)
				itemDef = itemDef.fromNote();
		}
		if (itemDef.stackable) {
			/**
			 * 'Free' a slot if necessary.
			 */
			if (freeSlots == 0 && player.getInventory().findItem(itemDef.id) != null)
				freeSlots++;
		} else if (amount > freeSlots) {
			/**
			 * Set amount equal to free slots.
			 */
			amount = freeSlots;
		}
		if (freeSlots == 0) {
			player.sendMessage("Not enough space in your inventory.");
			return;
		}

		/**
		 * Currency check
		 */
		int price = CustomShop2Item.getPrice();
		Currency currency = CustomShop2.getCurrency();
		if (price != 0) {
			long buyPrice = (long) price * amount;
			int currencyAmount = currency.getCurrencyHandler().getCurrencyCount(player);
			if (currencyAmount < buyPrice) {
				if (price > currencyAmount) {
					/* not enough currency to even buy 1 */
					amount = 0;
				} else {
					amount = currencyAmount / price;
					buyPrice = (long) price * amount;
				}

				if (amount <= 0) {
					player.sendMessage("You don't have enough " + currency.getCurrencyHandler().name() + " to buy this item.");
					return;
				}
			}
			if (buyPrice <= 0L || buyPrice < price) {
				player.sendMessage("Please buy this item in a smaller quantity.");
				return;
			}
			currency.getCurrencyHandler().removeCurrency(player, (int) buyPrice);
		}

		player.getInventory().add(itemDef.id, amount);

		CustomShop2.refreshShop();
	}

	public static void attemptSell(Player player, int option, int slot, int itemId) {
		if (slot < 0 || slot > 28)
			return;

		Item item = player.getInventory().get(slot);

		if (item == null || (itemId != item.getId() && itemId != item.getDef().placeholderMainId))
			return;

		if (option == 10) {
			item.examine(player);
			return;
		}

		CustomShop2 CustomShop2 = io.ruin.model.inter.handlers.shopinterface.CustomShop2.get(player.getShopIdentifier());

		if (CustomShop2 == null) {
			return;
		}

		// Explicit-buyback shops (sellAtListedPrice) are built to sink specific non-tradeable
		// items at a fixed price -- same pattern as the Wilderness Emblem Trader's Archaic
		// emblems, which aren't GE-tradeable either. Only the general case requires tradeable.
		if (!CustomShop2.isSellAtListedPrice() && !item.getDef().tradeable) {
			player.sendMessage("You can't sell that item.");
			return;
		}

		if (!itemInShop(itemId, CustomShop2)) {
			player.sendMessage("This item is not available right now.");
			return;
		}

		ShopItem CustomShop2Item = Arrays.stream(CustomShop2.getShopItems())
			.filter(si -> si.getItemId() == itemId)
			.findFirst()
			.orElse(null);

		if (CustomShop2Item == null) {
			return;
		}

		int price = CustomShop2.isSellAtListedPrice() ? CustomShop2Item.getPrice() : (int) (CustomShop2Item.getPrice() * 0.70);

		if (option == 1) {
			if (CustomShop2Item.getPrice() <= 0) {
				player.sendMessage("You can't sell that item to this store.");
			} else {
				player.sendMessage(Color.COOL_BLUE.wrap(item.getDef().name) + " can be sold for "
					+ (Color.RED.wrap(NumberUtils.formatNumber(price))
					+ " " + CustomShop2.getCurrency().getCurrencyHandler().name()) + ".");
			}
			return;
		} else if (option == 2) {
			sell(player, CustomShop2Item, CustomShop2, price, itemId, 1);
		} else if (option == 3) {
			sell(player, CustomShop2Item, CustomShop2, price, itemId, 5);
		} else if (option == 4) {
			sell(player, CustomShop2Item, CustomShop2, price, itemId, 10);
		} else if (option == 5) {
			player.integerInput("How many do you want to sell?",
				amt -> sell(player, CustomShop2Item, CustomShop2, price, itemId, amt));
		}
	}

	public static void sell(Player player, ShopItem CustomShop2Item, CustomShop2 CustomShop2,
	                        int price, int itemId, int amount) {

		if (CustomShop2Item.getPrice() <= 0) {
			player.sendMessage("You can't sell that item to this store.");
			return;
		}

		if (amount > player.getInventory().getAmount(itemId)) {
			amount = player.getInventory().getAmount(itemId);
		}

		if (amount <= 0)
			return;

		int totalPrice = amount * price;

		CustomShop2.getCurrency().getCurrencyHandler().addCurrency(player, totalPrice);
		player.getInventory().remove(itemId, amount);
		player.sendMessage("You sell " + amount + " x " + new Item(itemId).getDef().name + " for "
			+ Color.RED.wrap(NumberUtils.formatNumber(totalPrice)) + " " + CustomShop2.getCurrency().getCurrencyHandler().name() + ".");
		CustomShop2.refreshShop();
	}

	private static boolean itemInShop(int itemId, CustomShop2 CustomShop2) {
		if (CustomShop2 != null) {
			return Arrays.stream(CustomShop2.getShopItems()).anyMatch(i -> i.getItemId() == itemId);
		}
		return false;
	}

	private static void priceCheck(Player player, int slot, int itemId) {
		int shopId = player.getShopIdentifier();
		if (shopId < 0) {
			player.sendMessage("Something is wrong with this shop. Please contact a staff member.");
			return;
		}

		CustomShop2 CustomShop2 = resolveShopForItem(player, itemId);

		if (CustomShop2 != null) {
			ShopItem selectedItem = Arrays.stream(CustomShop2.getShopItems())
				.filter(si -> si.getItemId() == itemId)
				.findFirst()
				.orElse(null);

			if (selectedItem == null) {
				return;
			}

			ObjType def = ObjType.get(selectedItem.getItemId());

			if (def != null) {
				player.sendMessage(Color.COOL_BLUE.wrap(def.name) + " costs "
					+ Color.RED.wrap(NumberUtils.formatNumber(selectedItem.getPrice()))
					+ " " + CustomShop2.getCurrency().getCurrencyHandler().name() + "."
				);
			}
		}
	}

	public static void open(Player player, Item[] shopItems) {
		if (player.getShopIdentifier() < 1) {
			player.setShopIdentifier(13);
			handleEnteringShop(player, CustomShop2.PKP_SHOP);
		}
		if (!player.isVisibleInterface(Interface.CUSTOM_SHOP2)) {
			player.openInterface(ToplevelComponent.MAINMODAL, Interface.CUSTOM_SHOP2);
			player.openInterface(ToplevelComponent.SIDEMODAL, Interface.PLAYER_SHOP_INVENTORY);
		}
		player.getPacketSender().sendClientScript(917, "ii", -1, -1);
		// script 10208 (per-tab preview item icons) intentionally not sent -- its cc_create calls
		// target IF3-format tab components, which this client's cc_create implementation can't
		// resolve (throws ArrayIndexOutOfBoundsException client-side). Cosmetic only, safe to skip.
		// script 10207 (item grid via cc_deleteall + cc_create loop) also intentionally not sent --
		// confirmed via Widget Inspector that cc_create never attaches children to the real widget
		// tree on this client build even when it doesn't throw (component 41 stayed at 0 children).
		// Replaced with the real grid-binding mechanism (script 149 + UpdateInvFull) -- see
		// configureBuyGrid() above.
		configureBuyGrid(player);
		player.getPacketSender().sendClientScript(149, "iiiiiisssss", 19726336, 93, 4, 7, 0, -1,
			"Value<col=ff9040>", "Sell 1<col=ff9040>", "Sell 5<col=ff9040>",
			"Sell 10<col=ff9040>", "Sell X<col=ff9040>");
		player.getPacketSender().sendItems(-1, 1, 93, player.getInventory().getItems());
		player.getPacketSender().sendItems(10005, shopItems);
		player.getPacketSender().sendIfEvents(891, 41, 0,
			127, 1150);
		player.getPacketSender().sendIfEvents(301, 0, 0, 27, 1086);
	}
}
