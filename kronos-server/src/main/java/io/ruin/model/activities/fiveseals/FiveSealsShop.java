package io.ruin.model.activities.fiveseals;

import io.ruin.cache.Color;
import io.ruin.cache.ObjType;
import io.ruin.model.content.loyaltytitles.LoyaltyTitle;
import io.ruin.model.content.loyaltytitles.LoyaltyTitleManager;
import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.InterfaceHandler;
import io.ruin.model.inter.ToplevelComponent;
import io.ruin.model.inter.actions.DefaultAction;
import io.ruin.model.inter.actions.SimpleAction;
import io.ruin.model.inter.dialogue.OptionsDialogue;
import io.ruin.model.inter.utils.Option;
import io.ruin.model.item.Item;
import io.ruin.network.PacketSender;

/**
 * The Five Seals Token Shop (window 1192, BuildKarnHub.fiveSealsShop, user 2026-11-16: "a new design shop interface ...
 * unique, match Karn"). Paid in Ascension shards (34669) from the inventory.
 * <ul>
 * <li>Rewards tab (reworked 2026-11-18: only things no other content gives): Seal supply packs and Vault Favours
 * (account counters), the three Five Seals titles, the Duskwarden / Hellfang ornament kits ({@link FiveSealsOrnaments}),
 * the Sealbreaker cape, Gilded Lil' Vorgath (owners of the pet only) and the Ascension sigil (card 8: buys tier 1, then
 * upgrades it a tier per purchase - {@link FiveSealsSigil}). Buy 1 / 5 / 10 / X on the cards that sell more than one.</li>
 * <li>Ascension tab: three account upgrades that only work inside The Five Seals: Starting Supplies (tiers 1-3, raid
 * restoratives at the start of every run), Altar Conduit (+20% seal charge in rooms 1 and 2) and Sovereign Greed (a full
 * clear pays x6.0 instead of x5.0).</li>
 * </ul>
 */
public final class FiveSealsShop {

	private FiveSealsShop() {
	}

	static final int SHARDS = 34669;
	static final int GROUP = 1192, CLOSE = 5, TAB_REWARDS = 7, TAB_ASCENSION = 10, PURSE_ITEM = 13, PURSE = 14, REWARDS = 15,
		FIRST_REWARD = 16, REWARD_STRIDE = 6, UPGRADES = 64, FIRST_UPGRADE = 65, UPGRADE_STRIDE = 8, INFO = 89;
	/**
	 * Which tab each viewer has open. Sovereign Greed's Unlock button sits over card 8's Buy button (the other tab), and
	 * a Greed purchase handed out card 8's Karn partyhat (user 2026-11-17): every click is now checked against the open tab.
	 */
	private static final java.util.Map<Player, Boolean> ASCENSION_TAB = new java.util.WeakHashMap<>();

	/** fake inventories for the item slots (the Battle Pass uses 3100+) */
	private static final int INV_BASE = 3200;

	/*
	 * The Rewards tab (user 2026-11-18: "sell items without breaking the eco and not losing items from another source"):
	 * everything here exists only in The Five Seals. Consumables are account counters (never items, so never traded), the
	 * cosmetics are untradeable, and nothing hands out supplies, upgrade materials or perk progress that other content pays.
	 */
	static final int CARD_SUPPLY = 0, CARD_FAVOUR = 1, CARD_TITLE = 2, CARD_DUSK_KIT = 3, CARD_HELL_KIT = 4, CARD_CAPE = 5,
		CARD_PET = 6, SIGIL_CARD = 7, CARDS = 8;
	static final int DUSK_KIT = 42803, HELL_KIT = 42804, CAPE = 42805, GILDED_PET = 42806;
	/** card icons (the counters and the title have no item of their own) */
	static final int[] ICON = { 20972, 23071, 10830, DUSK_KIT, HELL_KIT, CAPE, GILDED_PET, FiveSealsSigil.FIRST };
	static final int[] PRICE = { 250, 500, 0, 2500, 2500, 3000, 10000, 0 };
	static final String[] NAME = { "Seal supply pack", "Vault Favour", "", "Duskwarden ornament kit", "Hellfang ornament kit",
		"Sealbreaker cape", "Gilded Lil' Vorgath", "" };
	static final String[] INFO_TEXT = {
		"Seal supply pack - your next run starts with 2 extra Xeric's aid (+) and 2 Prayer enhance (+), on top of Starting Supplies. Run-only: taken back when the run ends.",
		"Vault Favour - your next full clear's table loot is doubled (not the uniques or the pet).",
		"",
		"Duskwarden ornament kit - use it on any Duskwarden piece to forge it in Karn crimson and gold. Dismantle the piece to get the kit back.",
		"Hellfang ornament kit - use it on any Hellfang piece to gild it. Dismantle the piece to get the kit back.",
		"Sealbreaker cape - worn by those who break the Five Seals.",
		"Gilded Lil' Vorgath - Lil' Vorgath in gold. Only for players who already own Lil' Vorgath.",
		"",
	};
	/** The Five Seals titles (LoyaltyTitle 30224-30226), bought in this order */
	static final int[] TITLES = { 30224, 30225, 30226 };
	static final int[] TITLE_PRICES = { 2000, 4000, 7500 };
	/** shards for tier 1, 2, 3, 4, 5 */
	static final int[] SIGIL_PRICES = { 3000, 5000, 8000, 12000, 18000 };
	/** extra restoratives from one Seal supply pack: Xeric's aid (+)(4) and Prayer enhance (+)(4) */
	static final int PACK_AID = 2, PACK_ENHANCE = 2;

	/* the ascension upgrades: Starting Supplies (3 tiers), Altar Conduit, Sovereign Greed */
	static final int[] SUPPLY_PRICES = { 1500, 3000, 6000 };
	static final int CONDUIT_PRICE = 4000, GREED_PRICE = 10000;
	/** card icons: Xeric's aid (+), Karnwood logs (the seals' offering), a coin pile (Greed: no item reward, so no item icon) */
	private static final int[] UPGRADE_ICON = { 20984, 42775, 2572 }; // Greed = Ring of wealth (user 2026-11-18: more loot, not coins)

	static void register() {
		FiveSealsSigil.register();
		InterfaceHandler.register(GROUP, h -> {
			h.actions[CLOSE] = (SimpleAction) Player::closeInterfaces;
			h.actions[TAB_REWARDS] = (SimpleAction) p -> tab(p, false);
			h.actions[TAB_ASCENSION] = (SimpleAction) p -> tab(p, true);
			for (int i = 0; i < CARDS; i++) {
				int card = i;
				h.actions[FIRST_REWARD + i * REWARD_STRIDE + 4] = (DefaultAction) (p, option, slot, itemId) -> {
					if (card == SIGIL_CARD) {
						buySigil(p, false);
						return;
					}
					if (!multi(card)) {
						buy(p, card, 1, false);
						return;
					}
					switch (option) {
						case 1 -> buy(p, card, 1, false);
						case 2 -> buy(p, card, 5, false);
						case 3 -> buy(p, card, 10, false);
						default -> p.integerInput("How many " + NAME[card] + " would you like to buy?", n -> buy(p, card, n, false));
					}
				};
				h.actions[FIRST_REWARD + i * REWARD_STRIDE + 1] = (DefaultAction) (p, option, slot, itemId) -> info(p, card);
			}
			for (int u = 0; u < 3; u++) {
				int upgrade = u;
				h.actions[FIRST_UPGRADE + u * UPGRADE_STRIDE + 6] = (SimpleAction) p -> unlock(p, upgrade, false);
			}
		});
	}

	static void open(Player p) {
		p.openInterface(ToplevelComponent.MAINMODAL, GROUP);
		PacketSender ps = p.getPacketSender();
		slot(p, PURSE_ITEM, INV_BASE + 15, new Item(SHARDS, 1), "");
		for (int i = 0; i < CARDS; i++) {
			// the cache's clickMask enables only op1; turn on Buy 1 / 5 / 10 / X (ops 1-4) where a card sells more than one
			ps.sendIfEvents(GROUP, FIRST_REWARD + i * REWARD_STRIDE + 4, -1, -1, multi(i) ? 2 | 4 | 8 | 16 : 2);
		}
		for (int u = 0; u < 3; u++)
			slot(p, FIRST_UPGRADE + u * UPGRADE_STRIDE + 1, INV_BASE + 8 + u, new Item(UPGRADE_ICON[u], UPGRADE_ICON[u] == 995 ? 10_000 : 1), "");
		tab(p, false);
	}

	private static void slot(Player p, int child, int inv, Item item, String op) {
		PacketSender ps = p.getPacketSender();
		ps.sendClientScript(149, "IviiiIsssss", GROUP << 16 | child, inv, 2, 2, 0, -1, op, "", "", "", "");
		ps.sendIfEvents(GROUP, child, 0, 0, op.isEmpty() ? 1024 : 1026);
		ps.sendItems(-1, child, inv, item);
	}

	private static void tab(Player p, boolean ascension) {
		ASCENSION_TAB.put(p, ascension);
		PacketSender ps = p.getPacketSender();
		ps.setHidden(GROUP, REWARDS, ascension);
		ps.setHidden(GROUP, UPGRADES, !ascension);
		ps.setHidden(GROUP, TAB_REWARDS + 1, ascension);
		ps.setHidden(GROUP, TAB_ASCENSION + 1, !ascension);
		refresh(p);
		ps.sendString(GROUP, INFO, ascension
			? "Ascension upgrades belong to your account and work only inside The Five Seals."
			: "Spend the Ascension shards you earn in The Five Seals. Right-click Buy for 1 / 5 / 10 / X.");
	}

	/** the purse and the upgrade cards (their state changes with every purchase) */
	private static void refresh(Player p) {
		PacketSender ps = p.getPacketSender();
		ps.sendString(GROUP, PURSE, String.format("%,d Ascension shards", p.getInventory().getAmount(SHARDS)));
		for (int i = 0; i < CARDS; i++)
			if (i != SIGIL_CARD)
				rewardCard(p, i);
		sigilCard(p);
		int tier = p.fiveSealsSupplies;
		upgradeCard(p, 0, "Starting Supplies",
			"Begin every run with raid restoratives: Xeric's aid (+) and Prayer enhance (+), more each tier; tier 3 adds Revitalisation (+).",
			tier == 0 ? "Not owned" : "Tier " + tier + " of 3 owned",
			tier >= 3 ? "Maxed" : String.format("%s: %,d shards", tier == 0 ? "Tier 1" : "Next tier", SUPPLY_PRICES[tier]),
			tier >= 3 ? "Maxed" : tier == 0 ? "Unlock" : "Upgrade");
		upgradeCard(p, 1, "Altar Conduit",
			"Your offerings and kills charge the Seals of Awakening and Carnage 20% faster.",
			p.fiveSealsConduit ? "Owned" : "Not owned",
			p.fiveSealsConduit ? "-" : String.format("%,d shards", CONDUIT_PRICE),
			p.fiveSealsConduit ? "Owned" : "Unlock");
		upgradeCard(p, 2, "Sovereign Greed",
			"A full clear of the Sovereign Vault pays x6.0 instead of x5.0.",
			p.fiveSealsGreed ? "Owned" : "Not owned",
			p.fiveSealsGreed ? "-" : String.format("%,d shards", GREED_PRICE),
			p.fiveSealsGreed ? "Owned" : "Unlock");
	}

	private static void upgradeCard(Player p, int u, String name, String what, String status, String price, String button) {
		PacketSender ps = p.getPacketSender();
		int base = FIRST_UPGRADE + u * UPGRADE_STRIDE;
		ps.sendString(GROUP, base + 2, name);
		ps.sendString(GROUP, base + 3, what);
		ps.sendString(GROUP, base + 4, status);
		ps.sendString(GROUP, base + 5, price);
		ps.sendString(GROUP, base + 7, button);
	}

	/** card 8: the next tier's sigil and price, or the tier 5 sigil marked maxed */
	private static void sigilCard(Player p) {
		int tier = FiveSealsSigil.tier(p), base = FIRST_REWARD + SIGIL_CARD * REWARD_STRIDE;
		int shown = Math.min(FiveSealsSigil.TIERS, tier + 1);
		slot(p, base + 1, INV_BASE + SIGIL_CARD, new Item(FiveSealsSigil.item(shown), 1), "Info");
		PacketSender ps = p.getPacketSender();
		ps.sendString(GROUP, base + 2, tier >= FiveSealsSigil.TIERS ? "Ascension sigil (maxed)"
			: tier == 0 ? "Ascension sigil" : "Sigil: tier " + tier + " -> " + (tier + 1));
		ps.sendString(GROUP, base + 3, tier >= FiveSealsSigil.TIERS ? "Maxed" : String.format("%,d shards", SIGIL_PRICES[tier]));
	}

	private static String sigilInfo(Player p) {
		int tier = FiveSealsSigil.tier(p);
		String what = "Ascension sigil - every tier adds +" + FiveSealsSigil.percent(1) + "% to all Five Seals pay (max tier "
			+ FiveSealsSigil.TIERS + ": +" + FiveSealsSigil.percent(FiveSealsSigil.TIERS) + "%). ";
		if (tier >= FiveSealsSigil.TIERS)
			return what + "Yours is radiant: fully upgraded.";
		return what + (tier == 0 ? "Buy tier 1" : "Your tier " + tier + " (+" + FiveSealsSigil.percent(tier) + "%) upgrades to tier " + (tier + 1))
			+ String.format(" for %,d shards.", SIGIL_PRICES[tier]);
	}

	private static void buySigil(Player p, boolean confirmed) {
		if (!p.isVisibleInterface(GROUP) || ASCENSION_TAB.getOrDefault(p, false))
			return;
		int tier = FiveSealsSigil.tier(p);
		if (tier >= FiveSealsSigil.TIERS) {
			p.sendMessage("Your Ascension sigil is already at its highest tier.");
			return;
		}
		int price = SIGIL_PRICES[tier];
		if (p.getInventory().getAmount(SHARDS) < price) {
			p.sendMessage("You need " + String.format("%,d", price) + " Ascension shards for that.");
			return;
		}
		if (!confirmed) {
			confirm(p, (tier == 0 ? "Buy an Ascension sigil" : "Upgrade your sigil to tier " + (tier + 1)) + " for "
				+ String.format("%,d", price) + " shards?", () -> buySigil(p, true));
			return;
		}
		p.getInventory().remove(SHARDS, price);
		int now = FiveSealsSigil.upgrade(p);
		if (now < 0) { // the old sigil vanished between the check and the swap: refund
			p.getInventory().addOrDrop(SHARDS, price);
			p.sendMessage("Your Ascension sigil could not be found; your shards were returned.");
			return;
		}
		p.sendMessage(Color.DARK_GREEN.wrap((now == 1 ? "You buy an Ascension sigil" : "Your Ascension sigil is now tier " + now)
			+ " for " + String.format("%,d", price) + " Ascension shards: +" + FiveSealsSigil.percent(now) + "% Five Seals pay."));
		refresh(p);
		p.getPacketSender().sendString(GROUP, INFO, sigilInfo(p));
	}

	/** cards that sell more than one at a time (Buy 1 / 5 / 10 / X) */
	static boolean multi(int card) {
		return card == CARD_SUPPLY || card == CARD_FAVOUR || card == CARD_DUSK_KIT || card == CARD_HELL_KIT;
	}

	/** the next Five Seals title this player can buy (index into TITLES), or -1 when they own all three */
	static int nextTitle(Player p) {
		for (int i = 0; i < TITLES.length; i++)
			if (!LoyaltyTitleManager.isUnlocked(p, LoyaltyTitle.get(TITLES[i])))
				return i;
		return -1;
	}

	private static int price(Player p, int card) {
		if (card == CARD_TITLE) {
			int t = nextTitle(p);
			return t < 0 ? 0 : TITLE_PRICES[t];
		}
		return PRICE[card];
	}

	/** draws one Rewards card: icon, name (with what you own) and price */
	private static void rewardCard(Player p, int card) {
		PacketSender ps = p.getPacketSender();
		int base = FIRST_REWARD + card * REWARD_STRIDE;
		slot(p, base + 1, INV_BASE + card, new Item(ICON[card], 1), "Info");
		String name = NAME[card], cost;
		switch (card) {
			case CARD_SUPPLY -> name += p.fiveSealsSupplyPacks > 0 ? " (" + p.fiveSealsSupplyPacks + ")" : "";
			case CARD_FAVOUR -> name += p.fiveSealsFavours > 0 ? " (" + p.fiveSealsFavours + ")" : "";
			case CARD_TITLE -> {
				int t = nextTitle(p);
				name = t < 0 ? "Titles (all owned)" : "Title: " + plain(LoyaltyTitle.get(TITLES[t]));
			}
			default -> { }
		}
		int price = price(p, card);
		cost = card == CARD_TITLE && nextTitle(p) < 0 ? "Owned" : String.format("%,d shards", price);
		ps.sendString(GROUP, base + 2, name);
		ps.sendString(GROUP, base + 3, cost);
	}

	private static String plain(LoyaltyTitle t) {
		return t.text.replaceAll("<[^>]+>", "").replaceFirst("^,", "").trim();
	}

	private static void info(Player p, int card) {
		PacketSender ps = p.getPacketSender();
		if (card == SIGIL_CARD) {
			ps.sendString(GROUP, INFO, sigilInfo(p));
			return;
		}
		if (card == CARD_TITLE) {
			int t = nextTitle(p);
			ps.sendString(GROUP, INFO, t < 0 ? "You own every Five Seals title: Sealbreaker, the Ascended and Sovereign. Wear them from the Titles window."
				: "Five Seals titles, bought in order: Sealbreaker, the Ascended, Sovereign. Next: " + plain(LoyaltyTitle.get(TITLES[t]))
				+ String.format(" for %,d shards.", TITLE_PRICES[t]));
			return;
		}
		ps.sendString(GROUP, INFO, INFO_TEXT[card] + String.format(" %,d shards%s.", PRICE[card], multi(card) ? " each" : ""));
	}

	/**
	 * Every purchase asks first (user 2026-11-18: "add a confirming message"). The question is a chatbox dialogue that leaves
	 * the shop open (unsafeDialogue); "Yes" runs the purchase again from the top with {@code confirmed}, so shards, tab and
	 * window are all checked afresh at that moment.
	 */
	private static void confirm(Player p, String question, Runnable yes) {
		p.unsafeDialogue(new OptionsDialogue(question,
			new Option("Yes, buy it.", yes),
			new Option("No.")));
	}

	private static void buy(Player p, int card, int amount, boolean confirmed) {
		if (!p.isVisibleInterface(GROUP) || amount <= 0 || ASCENSION_TAB.getOrDefault(p, false))
			return;
		int price = price(p, card);
		int have = p.getInventory().getAmount(SHARDS);
		if (card == CARD_TITLE) {
			int t = nextTitle(p);
			if (t < 0) {
				p.sendMessage("You already own every Five Seals title.");
				return;
			}
			if (have < price) {
				p.sendMessage("You need " + String.format("%,d", price) + " Ascension shards for that.");
				return;
			}
			LoyaltyTitle title = LoyaltyTitle.get(TITLES[t]);
			if (!confirmed) {
				confirm(p, "Buy the title " + plain(title) + " for " + String.format("%,d", price) + " shards?", () -> buy(p, card, 1, true));
				return;
			}
			p.getInventory().remove(SHARDS, price);
			LoyaltyTitleManager.unlock(p, title);
			p.sendMessage(Color.DARK_GREEN.wrap("You unlock the title " + plain(title) + " for " + String.format("%,d", price)
				+ " Ascension shards. Wear it from the Titles window."));
			refresh(p);
			info(p, card);
			return;
		}
		if (card == CARD_PET && !p.petItemIdsObtained.contains(io.ruin.model.item.actions.impl.pet.Pet.LIL_VORGATH.itemId)) {
			p.sendMessage("Only players who own Lil' Vorgath can buy his gilded look.");
			return;
		}
		if (!multi(card))
			amount = 1;
		amount = (int) Math.min(amount, have / price);
		if (amount <= 0) {
			p.sendMessage("You need " + String.format("%,d", price) + " Ascension shards for that.");
			return;
		}
		boolean counter = card == CARD_SUPPLY || card == CARD_FAVOUR;
		ObjType def = counter ? null : ObjType.get(ICON[card]);
		if (def != null && !def.stackable) {
			int room = p.getInventory().getFreeSlots() + (have == amount * price ? 1 : 0);
			amount = Math.min(amount, room);
			if (amount <= 0) {
				p.sendMessage("You don't have enough inventory space.");
				return;
			}
		}
		String what = amount + " x " + (counter ? NAME[card] : def.name);
		if (!confirmed) {
			int n = amount;
			confirm(p, "Buy " + what + " for " + String.format("%,d", amount * price) + " shards?", () -> buy(p, card, n, true));
			return;
		}
		p.getInventory().remove(SHARDS, amount * price);
		if (card == CARD_SUPPLY)
			p.fiveSealsSupplyPacks += amount;
		else if (card == CARD_FAVOUR)
			p.fiveSealsFavours += amount;
		else {
			p.getInventory().add(ICON[card], amount);
			if (card == CARD_PET)
				p.petItemIdsObtained.add(GILDED_PET);
		}
		p.sendMessage(Color.DARK_GREEN.wrap("You buy " + what + " for " + String.format("%,d", amount * price) + " Ascension shards."));
		refresh(p);
	}

	private static void unlock(Player p, int upgrade, boolean confirmed) {
		if (!p.isVisibleInterface(GROUP) || !ASCENSION_TAB.getOrDefault(p, false))
			return;
		int price;
		String name;
		switch (upgrade) {
			case 0 -> {
				if (p.fiveSealsSupplies >= 3) {
					p.sendMessage("Your Starting Supplies are already at their highest tier.");
					return;
				}
				price = SUPPLY_PRICES[p.fiveSealsSupplies];
				name = "Starting Supplies tier " + (p.fiveSealsSupplies + 1);
			}
			case 1 -> {
				if (p.fiveSealsConduit) {
					p.sendMessage("You already own the Altar Conduit.");
					return;
				}
				price = CONDUIT_PRICE;
				name = "the Altar Conduit";
			}
			default -> {
				if (p.fiveSealsGreed) {
					p.sendMessage("You already own Sovereign Greed.");
					return;
				}
				price = GREED_PRICE;
				name = "Sovereign Greed";
			}
		}
		if (p.getInventory().getAmount(SHARDS) < price) {
			p.sendMessage("You need " + String.format("%,d", price) + " Ascension shards for " + name + ".");
			return;
		}
		if (!confirmed) {
			confirm(p, "Unlock " + name + " for " + String.format("%,d", price) + " shards?", () -> unlock(p, upgrade, true));
			return;
		}
		p.getInventory().remove(SHARDS, price);
		switch (upgrade) {
			case 0 -> p.fiveSealsSupplies++;
			case 1 -> p.fiveSealsConduit = true;
			default -> p.fiveSealsGreed = true;
		}
		p.sendMessage(Color.DARK_GREEN.wrap("You unlock " + name + " for " + String.format("%,d", price) + " Ascension shards."));
		refresh(p);
	}

	/* ------------------------------------------------------------------ Starting Supplies, inside a run */

	/** Xeric's aid (+) / Prayer enhance (+) / Revitalisation (+), every dose: raid-only items, never taken out of a run */
	static final int[] SUPPLY_ITEMS = { 20981, 20982, 20983, 20984, 20969, 20970, 20971, 20972, 20957, 20958, 20959, 20960 };

	/** the run's opening supplies for this player's tier */
	static void giveSupplies(Player p) {
		int tier = p.fiveSealsSupplies;
		if (tier > 0) {
			p.getInventory().addOrDrop(20984, tier);      // Xeric's aid (+)(4)
			p.getInventory().addOrDrop(20972, tier);      // Prayer enhance (+)(4)
			if (tier >= 3)
				p.getInventory().addOrDrop(20960, 1);     // Revitalisation (+)(4)
			p.sendMessage(Color.PURPLE.wrap("Your Starting Supplies (tier " + tier + ") are in your pack."));
		}
		if (p.fiveSealsSupplyPacks > 0) {                  // one Seal supply pack per run (Rewards tab)
			p.fiveSealsSupplyPacks--;
			p.getInventory().addOrDrop(20984, PACK_AID);
			p.getInventory().addOrDrop(20972, PACK_ENHANCE);
			p.sendMessage(Color.PURPLE.wrap("You open a Seal supply pack (" + p.fiveSealsSupplyPacks + " left)."));
		}
	}

	static void removeSupplies(Player p) {
		for (int id : SUPPLY_ITEMS) {
			int n = p.getInventory().getAmount(id);
			if (n > 0)
				p.getInventory().remove(id, n);
		}
	}
}
