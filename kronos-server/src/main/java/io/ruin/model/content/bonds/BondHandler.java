package io.ruin.model.content.bonds;

import io.ruin.cache.Color;
import io.ruin.model.content.bonds.BondType.Variant;
import io.ruin.model.entity.player.Player;
import io.ruin.model.entity.shared.listeners.LoginListener;
import io.ruin.model.inter.dialogue.ItemDialogue;
import io.ruin.model.inter.dialogue.OptionsDialogue;
import io.ruin.model.inter.utils.Option;
import io.ruin.model.item.Item;
import io.ruin.model.item.actions.ItemAction;
import io.ruin.model.item.containers.Equipment;

/**
 * Actions for every Custom Bond variant. Option slots come from the cache:
 * 1 = "Attune" (unattuned ids) / "Sever" (attuned ids), 2 = "Upgrade", 3 = "Inspect", 4 = "Overview".
 * Attuning moves the bond into the bond slot (swapping out whichever bond is there) under its attuned id; removing
 * it from the slot severs it back into the inventory. The bond is never deleted. Upgrading additionally consumes
 * unattuned T1 bonds of the same type.
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
		LoginListener.register(BondHandler::severLegacyAttuned);
	}

	/**
	 * Before the bond slot, an attuned bond worked from the inventory. Copies still held there or in the bank go back to
	 * their unattuned ids at login, so every bond is attuned the same way from now on.
	 */
	private static void severLegacyAttuned(Player player) {
		boolean severed = false;
		for (Item item : player.getInventory().getItems()) {
			Variant v = item == null ? null : BondType.variant(item.getId());
			if (v != null && v.attuned()) {
				item.setId(v.type().itemId(v.tier(), false));
				severed = true;
			}
		}
		for (Item item : player.getBank().getItems().clone()) {
			Variant v = item == null ? null : BondType.variant(item.getId());
			if (v != null && v.attuned()) {
				int amount = item.getAmount();
				if (player.getBank().remove(item.getId(), amount) == amount)
					player.getBank().add(v.type().itemId(v.tier(), false), amount);
				severed = true;
			}
		}
		if (severed)
			player.sendMessage(Color.DARK_GREEN.wrap("Bonds now work from the bond slot, left of your helmet. Attune the one you want active."));
	}

	private static void registerId(int id) {
		ItemAction.registerInventory(id, ATTUNE_OR_SEVER, BondHandler::attuneOrSever);
		ItemAction.registerInventory(id, UPGRADE, BondHandler::upgrade);
		// Inspect and Overview open the Bonds & Auras window on this bond
		ItemAction.registerInventory(id, INSPECT, (player, item) -> {
			Variant v = BondType.variant(item.getId());
			if (v != null)
				BondInterface.open(player, BondInterface.PERKS, v.type());
		});
		ItemAction.registerInventory(id, OVERVIEW, (player, item) -> {
			Variant v = BondType.variant(item.getId());
			if (v != null)
				BondInterface.open(player, BondInterface.OVERVIEW, v.type());
		});
	}

	static void attuneOrSever(Player player, Item item) {
		Variant v = BondType.variant(item.getId());
		if (v == null)
			return;
		BondType type = v.type();
		if (v.attuned()) { // an attuned copy in the inventory has no perks: this just turns it back
			item.setId(type.itemId(v.tier(), false));
			player.sendMessage("You sever your connection with the " + type.displayName(v.tier()) + ".");
			BondInterface.refreshIfOpen(player);
			return;
		}
		Equipment equipment = player.getEquipment();
		if (equipment.getItems().length <= Equipment.SLOT_BOND) {
			player.sendMessage("You can't attune a bond here.");
			return;
		}
		int attunedId = type.itemId(v.tier(), true);
		Item worn = equipment.get(Equipment.SLOT_BOND);
		Variant replaced = worn == null ? null : BondType.variant(worn.getId());
		if (item.getAmount() > 1) { // a stack: only one bond goes in the slot
			if (worn != null && player.getInventory().freeSlot() == -1) {
				player.sendMessage("You need a free inventory space to swap bonds.");
				return;
			}
			item.incrementAmount(-1);
			if (worn != null)
				player.getInventory().add(severedId(worn), 1);
			equipment.set(Equipment.SLOT_BOND, new Item(attunedId, 1));
		} else if (worn != null) {
			item.setId(severedId(worn));
			worn.setId(attunedId);
		} else {
			item.remove();
			equipment.set(Equipment.SLOT_BOND, new Item(attunedId, 1));
		}
		player.sendMessage(Color.DARK_GREEN.wrap("You attune to the " + type.displayName(v.tier()) + ". Its power flows through you."));
		if (replaced != null)
			player.sendMessage("Your " + replaced.type().displayName(replaced.tier()) + " returns to your inventory.");
		BondInterface.refreshIfOpen(player);
	}

	/** The unattuned id of a bond taken out of the slot. */
	private static int severedId(Item worn) {
		Variant v = BondType.variant(worn.getId());
		return v == null ? worn.getId() : v.type().itemId(v.tier(), false);
	}

	/** The bond slot's menu: op 1 "Remove" severs the bond into the inventory, op 2 "Overview" opens the Bonds & Auras window. */
	public static void slotOption(Player player, int option, Item worn) {
		Variant v = BondType.variant(worn.getId());
		if (option == 2 && v != null)
			BondInterface.open(player, BondInterface.OVERVIEW, v.type());
		else if (option == 1)
			sever(player);
	}

	/** Takes the bond out of the slot, unattuned, into the inventory. */
	static void sever(Player player) {
		Equipment equipment = player.getEquipment();
		Item worn = equipment.getItems().length <= Equipment.SLOT_BOND ? null : equipment.get(Equipment.SLOT_BOND);
		if (worn == null)
			return;
		if (player.getInventory().freeSlot() == -1) {
			player.sendMessage("You need a free inventory space to remove your bond.");
			return;
		}
		Variant v = BondType.variant(worn.getId());
		int id = severedId(worn);
		worn.remove();
		player.getInventory().add(id, 1);
		if (v != null)
			player.sendMessage("You sever your connection with the " + v.type().displayName(v.tier()) + ".");
		BondInterface.refreshIfOpen(player);
	}

	/** After a death, the kept bond goes from the inventory straight back into the slot. */
	public static void reseatAfterDeath(Player player) {
		Equipment equipment = player.getEquipment();
		if (equipment.getItems().length <= Equipment.SLOT_BOND || equipment.get(Equipment.SLOT_BOND) != null)
			return;
		for (Item item : player.getInventory().getItems()) {
			Variant v = item == null ? null : BondType.variant(item.getId());
			if (v != null && v.attuned()) {
				int id = item.getId();
				item.remove();
				equipment.set(Equipment.SLOT_BOND, new Item(id, 1));
				return;
			}
		}
	}

	/** Unattuned T1 bonds of this type in the inventory, not counting {@code exclude} (the clicked item). */
	static int spareBaseBonds(Player player, BondType type, Item exclude) {
		int count = 0;
		for (Item i : player.getInventory().getItems()) {
			if (i == null || i == exclude || i.hasAttributes())
				continue;
			if (i.getId() == type.baseItemId || i.getId() == type.baseProxyItemId)
				count += i.getAmount();
		}
		return count;
	}

	static void upgrade(Player player, Item item) {
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
		boolean worn = player.getEquipment().getItems().length > Equipment.SLOT_BOND && player.getEquipment().get(Equipment.SLOT_BOND) == item;
		int slot = worn ? -1 : item.getSlot();
		int id = item.getId();
		player.dialogue(
				new ItemDialogue().one(id, BondType.tierLabel(next) + ": " + type.perks(next)),
				new OptionsDialogue(
						Color.DARK_RED.wrap("Consume " + cost + " " + type.displayName(1) + (cost == 1 ? "" : "s") + " to ascend?"),
						new Option("Yes, ascend the bond.", () -> confirmUpgrade(player, slot, id)),
						new Option("No.", Player::closeDialogue)));
	}

	/** slot: the bond's inventory slot, or -1 for the one in the bond slot. */
	private static void confirmUpgrade(Player player, int slot, int expectedId) {
		// Re-check: the dialogue can sit open while the inventory changes.
		Item item = slot >= 0 ? player.getInventory().get(slot)
				: player.getEquipment().getItems().length > Equipment.SLOT_BOND ? player.getEquipment().get(Equipment.SLOT_BOND) : null;
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
		BondInterface.refreshIfOpen(player);
	}
}
