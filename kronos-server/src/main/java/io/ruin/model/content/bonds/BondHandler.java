package io.ruin.model.content.bonds;

import io.ruin.cache.Color;
import io.ruin.model.content.bonds.BondType.Variant;
import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.dialogue.ItemDialogue;
import io.ruin.model.inter.dialogue.OptionsDialogue;
import io.ruin.model.inter.utils.Option;
import io.ruin.model.item.Item;
import io.ruin.model.item.actions.ItemAction;

/**
 * Inventory actions for every Custom Bond variant. Option slots come from the cache:
 * 1 = "Attune" (unattuned ids) / "Sever" (attuned ids), 2 = "Upgrade", 3 = "Inspect", 4 = "Overview".
 * Attuning, severing and upgrading only ever swap the clicked item's id -- the bond is never
 * deleted. Upgrading additionally consumes unattuned T1 bonds of the same type.
 */
public class BondHandler {

	private static final int ATTUNE_OR_SEVER = 1;
	private static final int UPGRADE = 2;
	private static final int INSPECT = 3;
	private static final int OVERVIEW = 4;

	public static void register() {
		for (BondType type : BondType.VALUES) {
			registerId(type.baseProxyItemId);
			for (int tier = 1; tier <= BondType.MAX_TIER; tier++) {
				registerId(type.itemId(tier, false));
				registerId(type.itemId(tier, true));
			}
		}
	}

	private static void registerId(int id) {
		ItemAction.registerInventory(id, ATTUNE_OR_SEVER, BondHandler::attuneOrSever);
		ItemAction.registerInventory(id, UPGRADE, BondHandler::upgrade);
		ItemAction.registerInventory(id, INSPECT, BondHandler::inspect);
		ItemAction.registerInventory(id, OVERVIEW, (player, item) -> {
			Variant v = BondType.variant(item.getId());
			if (v != null)
				BondGuide.openDetail(player, v.type());
		});
	}

	private static void attuneOrSever(Player player, Item item) {
		Variant v = BondType.variant(item.getId());
		if (v == null)
			return;
		BondType type = v.type();
		if (v.attuned()) {
			item.setId(type.itemId(v.tier(), false));
			player.sendMessage("You sever your connection with the " + type.displayName(v.tier()) + ".");
			return;
		}
		int active = type.tier(player);
		if (active > 0) {
			player.sendMessage("You are already attuned to a " + type.displayName(active) + ". Sever it first.");
			return;
		}
		item.setId(type.itemId(v.tier(), true));
		player.sendMessage(Color.DARK_GREEN.wrap("You attune to the " + type.displayName(v.tier()) + ". Its power flows through you."));
	}

	/** Unattuned T1 bonds of this type in the inventory, not counting {@code exclude} (the clicked item). */
	private static int spareBaseBonds(Player player, BondType type, Item exclude) {
		int count = 0;
		for (Item i : player.getInventory().getItems()) {
			if (i == null || i == exclude || i.hasAttributes())
				continue;
			if (i.getId() == type.baseItemId || i.getId() == type.baseProxyItemId)
				count += i.getAmount();
		}
		return count;
	}

	private static void upgrade(Player player, Item item) {
		Variant v = BondType.variant(item.getId());
		if (v == null)
			return;
		BondType type = v.type();
		if (v.tier() >= BondType.MAX_TIER) {
			player.dialogue(new ItemDialogue().one(item.getId(), "Your " + type.bondName + " has reached maximum ascension (MAX) and cannot be upgraded further."));
			return;
		}
		int next = v.tier() + 1;
		int cost = BondType.upgradeCost(v.tier());
		int have = spareBaseBonds(player, type, item);
		if (have < cost) {
			player.dialogue(new ItemDialogue().one(item.getId(), "Ascending to " + BondType.tierLabel(next) + " needs " + cost + " more "
					+ type.displayName(1) + (cost == 1 ? "" : "s") + " in your inventory. You have " + have + "."));
			return;
		}
		int slot = item.getSlot();
		int id = item.getId();
		player.dialogue(
				new ItemDialogue().one(id, BondType.tierLabel(next) + ": " + type.perks(next)),
				new OptionsDialogue(
						Color.DARK_RED.wrap("Consume " + cost + " " + type.displayName(1) + (cost == 1 ? "" : "s") + " to ascend?"),
						new Option("Yes, ascend the bond.", () -> confirmUpgrade(player, slot, id)),
						new Option("No.", Player::closeDialogue)));
	}

	private static void confirmUpgrade(Player player, int slot, int expectedId) {
		// Re-check: the dialogue can sit open while the inventory changes.
		Item item = player.getInventory().get(slot);
		Variant v = BondType.variant(expectedId);
		if (item == null || item.getId() != expectedId || v == null || v.tier() >= BondType.MAX_TIER) {
			player.sendMessage("Nothing happens -- your bond has moved. Try upgrading again.");
			player.closeDialogue();
			return;
		}
		BondType type = v.type();
		int cost = BondType.upgradeCost(v.tier());
		if (spareBaseBonds(player, type, item) < cost) {
			player.sendMessage("You no longer have enough " + type.displayName(1) + "s to ascend.");
			player.closeDialogue();
			return;
		}
		int remaining = cost;
		Item[] items = player.getInventory().getItems();
		for (int s = 0; s < items.length && remaining > 0; s++) {
			Item i = items[s];
			if (i == null || s == slot || i.hasAttributes())
				continue;
			if (i.getId() == type.baseItemId || i.getId() == type.baseProxyItemId) {
				int take = Math.min(remaining, i.getAmount());
				i.incrementAmount(-take);
				remaining -= take;
			}
		}
		int next = v.tier() + 1;
		item.setId(type.itemId(next, v.attuned()));
		player.closeDialogue();
		player.sendMessage(Color.DARK_GREEN.wrap("Your " + type.bondName + " has ascended to "
				+ (next >= BondType.MAX_TIER ? "MAX" : "Tier " + next) + "!"));
	}

	private static void inspect(Player player, Item item) {
		Variant v = BondType.variant(item.getId());
		if (v == null)
			return;
		BondType type = v.type();
		String state = v.attuned() ? "attuned -- its perks are active while it's in your inventory"
				: "not attuned -- its perks are inactive";
		String current = "Your " + type.bondName + " is currently " + (v.tier() >= BondType.MAX_TIER ? "MAX" : "Tier " + v.tier() + "/" + BondType.MAX_TIER)
				+ " (" + state + ").<br>"
				+ type.perks(v.tier());
		String next;
		if (v.tier() >= BondType.MAX_TIER) {
			next = "This bond has reached maximum ascension and cannot be upgraded further.";
		} else {
			int cost = BondType.upgradeCost(v.tier());
			next = BondType.tierLabel(v.tier() + 1) + " needs " + cost + " more " + type.displayName(1) + (cost == 1 ? "" : "s")
					+ " (you have " + spareBaseBonds(player, type, item) + "): " + type.perks(v.tier() + 1);
		}
		player.dialogue(new ItemDialogue().one(item.getId(), current), new ItemDialogue().one(item.getId(), next));
	}
}
