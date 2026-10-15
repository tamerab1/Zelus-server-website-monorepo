package io.ruin.model.item.actions.impl.storage;

import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.Interface;
import io.ruin.model.inter.InterfaceAction;
import io.ruin.model.inter.InterfaceHandler;
import io.ruin.model.inter.ToplevelComponent;
import io.ruin.model.inter.actions.DefaultAction;
import io.ruin.model.inter.dialogue.YesNoDialogue;
import io.ruin.model.var.VarPlayerRepository;
import io.ruin.model.item.Item;
import io.ruin.model.item.ItemContainer;
import io.ruin.model.item.actions.ItemAction;
import io.ruin.model.item.actions.ItemItemAction;
import io.ruin.model.map.ground.GroundItem;
import io.ruin.model.skills.magic.rune.Rune;

import static io.ruin.model.var.VarPlayerRepository.RUNE_POUCH_ADD_AMOUNT;

public class RunePouch extends ItemContainer {

	public static final int RUNE_POUCH = 12791;

	public static void register() {
		ItemAction.registerInventory(RUNE_POUCH, "open", (player, item) -> player.getRunePouch().open());
		ItemAction.registerInventory(RUNE_POUCH, "empty", (player, item) -> player.getRunePouch().empty(false));
		ItemAction.registerInventory(RUNE_POUCH, "destroy", RunePouch::destroy);

		for (Rune rune : Rune.values()) {
			ItemItemAction.register(RUNE_POUCH, rune.getId(),
					(player, pouchItem, runeItem) -> player.getRunePouch().deposit(runeItem, runeItem.getAmount()));
		}

		// whichever pouch is carried owns the shared display varbits from login on
		io.ruin.model.entity.shared.listeners.LoginListener.register(RunePouch::refreshDisplay);

		InterfaceHandler.register(Interface.RUNE_POUCH, h -> {
			h.actions[11] = (DefaultAction) (p, option, slot, itemId) -> RUNE_POUCH_ADD_AMOUNT.set(p, 0); // Make 1
			h.actions[12] = (DefaultAction) (p, option, slot, itemId) -> RUNE_POUCH_ADD_AMOUNT.set(p, 1); // Make 5
			h.actions[13] = (DefaultAction) (p, option, slot, itemId) -> RUNE_POUCH_ADD_AMOUNT.set(p, 3); // Make X
			h.actions[14] = (DefaultAction) (p, option, slot, itemId) -> RUNE_POUCH_ADD_AMOUNT.set(p, 2); // Make ALL

			h.actions[7] = (DefaultAction) (p, option, slot, itemId) -> {
				Item item = p.getInventory().get(slot, itemId);
				if (item == null)
					return;
				int amount = -1;
				if (RUNE_POUCH_ADD_AMOUNT.get(p) > 0) {
					switch (RUNE_POUCH_ADD_AMOUNT.get(p)) {
						case 1 -> amount = 5;
						case 2 -> amount = item.getAmount();
						case 3 -> {
							p.integerInput("Enter amount:", amt -> deposit(p, item, amt));
							return;
						}
					}
				} else if (option == 1)
					amount = 1;
				else if (option == 3)
					amount = 5;
				else if (option == 2) // All changed
					amount = item.getAmount();
				else if (option == 4) {
					p.integerInput("Enter amount:", amt -> deposit(p, item, amt));
					return;
				} else {
					item.examine(p);
					return;
				}
				if (amount > 0)
					deposit(p, item, amount);
			};
			h.actions[4] = new InterfaceAction() {

				@Override
				public void handleClick(Player p, int option, int slot, int itemId) {
					ItemContainer pouch = openPouch(p);
					Item item = pouch.get(slot, itemId);
					if (item == null)
						return;
					int amount = -1;
					if (RUNE_POUCH_ADD_AMOUNT.get(p) > 0) {
						switch (RUNE_POUCH_ADD_AMOUNT.get(p)) {
							case 1 -> amount = 5;
							case 2 -> amount = item.getAmount();
							case 3 -> {
								p.integerInput("Enter amount:", amt -> withdraw(p, item, amt));
								return;
							}
						}
					} else if (option == 1)
						amount = 1;
					else if (option == 3)
						amount = 5;
					else if (option == 2) // All Changed
						amount = item.getAmount();
					else if (option == 4) {
						p.integerInput("Enter amount:", amt -> withdraw(p, item, amt));
						return;
					} else {
						item.examine(p);
						return;
					}
					if (amount > 0)
						withdraw(p, item, amount);
				}

				@Override
				public void handleDrag(Player player, int fromSlot, int fromItemId, int toInterfaceId, int toChildId,
						int toSlot, int toItemId) {
					ItemContainer pouch = openPouch(player);

					Item from = pouch.get(fromSlot);
					Item to = pouch.get(toSlot);

					if (from == null || to == null) {
						return;
					}

					pouch.set(fromSlot, to);
					pouch.set(toSlot, from);
				}

			};
		});
	}

	/** Key of the pouch the open window (190) belongs to: the divine pouch uses the same window. */
	static final String DIVINE_OPEN_KEY = "rune_pouch_divine_open";

	static ItemContainer openPouch(Player p) {
		return p.<Boolean>get(DIVINE_OPEN_KEY, false) ? p.DivinerunePouch : p.getRunePouch();
	}

	private static void deposit(Player p, Item item, int amount) {
		if (p.<Boolean>get(DIVINE_OPEN_KEY, false))
			p.DivinerunePouch.deposit(item, amount);
		else
			p.getRunePouch().deposit(item, amount);
	}

	private static void withdraw(Player p, Item item, int amount) {
		if (p.<Boolean>get(DIVINE_OPEN_KEY, false))
			p.DivinerunePouch.withdraw(item, amount);
		else
			p.getRunePouch().withdraw(item, amount);
	}

	/**
	 * Both pouches share the client's pouch varbits (types 29/1622/1623/14285, amounts 1624/1625/1626/14286), which
	 * the pouch window and the spellbook's rune check read: the carried divine pouch owns them, else the rune pouch.
	 */
	public static void refreshDisplay(Player p) {
		if (p.getInventory().hasId(DivineRunePouch.RUNE_POUCH))
			p.DivinerunePouch.forceDisplay();
		else
			p.getRunePouch().forceDisplay();
	}

	public static void destroy(Player player, Item item) {
		player.dialogue(
				new YesNoDialogue("Are you sure you want to do this?",
						"This Rune Pouch will be destroyed and all stored items will be dropped.", item, () -> {
							item.remove();
							player.sendMessage("You destroy your Rune Pouch!");
							for (Item i : player.getRunePouch().getItems()) {
								if (i != null) {
									new GroundItem(i).owner(player).droppedBy(player).position(player.getPosition()).spawnPrivate();
									i.remove();
								}
							}
						}));
	}

	public void deposit(Item item, int amount) {
		if (amount <= 0) {
			return;
		}
		if (io.ruin.model.content.pvppreset.SpawnedItems.refuse(player, item, "stored in a rune pouch")) {
			return;
		}

		if (amount > item.getAmount()) {
			amount = item.getAmount();
		}

		Rune rune = item.getDef().rune;

		if (rune == null) {
			player.sendMessage("You can't store this item in your pouch.");
			return;
		}

		int freeSlot = -1;
		Item stack = null;
		for (int i = 0; i < items.length; i++) {
			Item stored = items[i];
			if (stored == null) {
				if (freeSlot == -1)
					freeSlot = i;
				continue;
			}
			if (item.getId() == stored.getId()) {
				stack = stored;
				break;
			}
		}

		if (stack != null) {
			amount = Math.min(amount, 16000 - stack.getAmount());
			stack.incrementAmount(amount);
			item.incrementAmount(-amount);
			return;
		}

		if (freeSlot != -1) {
			amount = Math.min(amount, 16000);
			set(freeSlot, new Item(item.getId(), amount));
			item.incrementAmount(-amount);
			return;
		}

		player.sendMessage("Not enough space in your pouch.");
	}

	public void empty(boolean bank) {
		var count = getCount();
		if (count == 0) {
			if (!bank)
				player.sendMessage("Your rune pouch is already empty.");
			return;
		}
		for (Item item : getItems()) {
			if (item != null)
				item.move(item.getId(), item.getAmount(), bank ? player.getBank() : player.getInventory());
		}
		// (the old check re-tested the count taken before moving, so it always said there was no space)
		if (getCount() == 0) {
			if (!bank)
				player.sendMessage("You empty your rune pouch.");
			return;
		}
		player.sendMessage(bank ? "Not enough space in your bank." : "Not enough space in your inventory.");
	}

	@Override
	public boolean sendUpdates() {
		if (updatedCount == 0) {
			return false;
		}
		// a carried divine pouch owns the shared varbits
		if (player.getInventory().hasId(DivineRunePouch.RUNE_POUCH)) {
			java.util.Arrays.fill(updatedSlots, false);
			updatedCount = 0;
			return true;
		}

		if (updatedSlots[0]) {
			update(0, VarPlayerRepository.RUNE_POUCH_LEFT_TYPE, VarPlayerRepository.RUNE_POUCH_LEFT_AMOUNT);
		}

		if (updatedSlots[1]) {
			update(1, VarPlayerRepository.RUNE_POUCH_MIDDLE_TYPE, VarPlayerRepository.RUNE_POUCH_MIDDLE_AMOUNT);
		}

		if (updatedSlots[2]) {
			update(2, VarPlayerRepository.RUNE_POUCH_RIGHT_TYPE, VarPlayerRepository.RUNE_POUCH_RIGHT_AMOUNT);
		}

		updatedCount = 0;
		return true;
	}

	private void open() {
		if (player.getCombat().isDefending(6) || player.getCombat().isAttacking(6)) {
			player.sendFilteredMessage("You can't do this in combat.");
			return;
		}

		player.set(DIVINE_OPEN_KEY, false);
		forceDisplay();
		VarPlayerRepository.VARP_261.set(player, 3);
		player.getPacketSender().sendIfEvents(Interface.RUNE_POUCH, 4, 0, 2, 1054 | 1 << 17 | 1 << 20);
		player.getPacketSender().sendIfEvents(Interface.RUNE_POUCH, 7, 0, 27, 1054);
		player.openInterface(ToplevelComponent.MAINMODAL, Interface.RUNE_POUCH);
	}

	/** Writes all 3 slots to the shared varbits (and clears the divine pouch's 4th). */
	void forceDisplay() {
		update(0, VarPlayerRepository.RUNE_POUCH_LEFT_TYPE, VarPlayerRepository.RUNE_POUCH_LEFT_AMOUNT);
		update(1, VarPlayerRepository.RUNE_POUCH_MIDDLE_TYPE, VarPlayerRepository.RUNE_POUCH_MIDDLE_AMOUNT);
		update(2, VarPlayerRepository.RUNE_POUCH_RIGHT_TYPE, VarPlayerRepository.RUNE_POUCH_RIGHT_AMOUNT);
		VarPlayerRepository.RUNE_POUCH_RUNE4.set(player, 0);
		VarPlayerRepository.RUNE_POUCH_AMOUNT4.set(player, 0);
	}

	void withdraw(Item item, int amount) {
		if (item.move(item.getId(), amount, player.getInventory()) > 0)
			return;
		player.sendMessage("Not enough space in your inventory.");
	}

	private void update(int slot, VarPlayerRepository typeConfig, VarPlayerRepository amountConfig) {
		Item item = items[slot];
		if (item == null) {
			typeConfig.set(player, 0);
			amountConfig.set(player, 0);
		} else {
			typeConfig.set(player, item.getDef().rune.ordinal() + 1);
			amountConfig.set(player, item.getAmount());
		}
		updatedSlots[slot] = false;
	}

}
