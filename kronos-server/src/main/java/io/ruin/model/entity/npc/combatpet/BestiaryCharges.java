package io.ruin.model.entity.npc.combatpet;

import io.ruin.cache.ObjType;
import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.dialogue.MessageDialogue;
import io.ruin.model.inter.dialogue.OptionsDialogue;
import io.ruin.model.inter.utils.Option;
import io.ruin.model.item.Item;
import io.ruin.model.item.actions.ItemAction;
import io.ruin.model.item.actions.ItemItemAction;
import io.ruin.model.item.actions.impl.pet.Pet;
import io.ruin.model.item.attributes.AttributeExtensions;

/**
 * Ascended Bestiary charges, stored on the book item as the "bestiary_charges" attribute.
 * <ul>
 * <li>Combat Pet Shards (31816, cosmic crystal shard -- .dev/cache-restore-tool/bestiary_charge_items.txt) used on
 * either book add 1 charge each, up to {@link #MAX_CHARGES}: the player is asked how many, then to confirm.</li>
 * <li>The empty book (31814) becomes the active book (31746) at {@link #ACTIVATE_AT} charges; the active book
 * falls back to empty when it reaches 0.</li>
 * <li>Active books from before charges existed carry no attribute and count as {@link #LEGACY_CHARGES}.</li>
 * <li>A duplicate combat pet used on a book dismantles into {@link #DISMANTLE_SHARDS} shards.</li>
 * </ul>
 * Every charge value is clamped to [0, MAX_CHARGES] and every amount is computed against the space left, so
 * nothing can overflow or go negative.
 */
public final class BestiaryCharges {

	public static final int EMPTY_ID = 42614;
	public static final int SHARD = 42616;

	public static final int MAX_CHARGES = 2000;
	public static final int ACTIVATE_AT = 100;
	public static final int LEGACY_CHARGES = 100;
	public static final int DISMANTLE_SHARDS = 50;

	static final String KEY = "bestiary_charges";
	public static final String TAG = "<col=8A0707>[Bestiary]</col> ";

	private BestiaryCharges() {
	}

	public static void register() {
		for (int book : new int[]{AscendedBestiary.ID, EMPTY_ID}) {
			ItemItemAction.register(book, SHARD, (player, bookItem, shards) -> askAmount(player, bookItem));
			ItemAction.registerInventory(book, "check", (player, item) -> player.sendMessage(TAG + describe(item) + "."));
			for (Pet pet : CombatPets.allPets())
				ItemItemAction.register(book, pet.itemId, (player, bookItem, petItem) -> offerDismantle(player, pet));
		}
	}

	public static boolean isBook(int id) {
		return id == AscendedBestiary.ID || id == EMPTY_ID;
	}

	public static int charges(Item book) {
		int stored;
		try {
			stored = book.getAttributeInt(KEY, -1);
		} catch (NumberFormatException e) {
			stored = -1;
		}
		if (stored < 0) // never charged: pre-charge active books get their starter charges
			return book.getId() == AscendedBestiary.ID ? LEGACY_CHARGES : 0;
		return Math.min(stored, MAX_CHARGES);
	}

	/**
	 * Stores the charges (clamped) and swaps the book between its empty and active forms.
	 *
	 * @return the book's new state: 1 = just activated, -1 = just ran out, 0 = unchanged
	 */
	static int setCharges(Item book, int charges) {
		charges = Math.max(0, Math.min(charges, MAX_CHARGES));
		int change = 0;
		if (book.getId() == EMPTY_ID && charges >= ACTIVATE_AT) {
			book.setId(AscendedBestiary.ID);
			change = 1;
		} else if (book.getId() == AscendedBestiary.ID && charges == 0) {
			book.setId(EMPTY_ID);
			change = -1;
		}
		// always written (even 0) so an empty book never reads as a legacy active one
		book.putAttribute(KEY, charges);
		book.update();
		return change;
	}

	static String describe(Item book) {
		return ObjType.get(book.getId()).name + " (Charges: " + charges(book) + "/" + MAX_CHARGES + ")";
	}

	/** Examine hook: the first book in the inventory, or null. */
	static String describeCarried(Player player, int bookId) {
		Item book = player.getInventory().findItemExact(bookId);
		return book == null ? null : describe(book);
	}

	/** Step 1: how many shards (capped by the shards carried and the space left in the book). */
	private static void askAmount(Player player, Item book) {
		int space = MAX_CHARGES - charges(book);
		if (space <= 0) {
			player.sendMessage(TAG + "Your Bestiary is already fully charged (" + MAX_CHARGES + "/" + MAX_CHARGES + ").");
			return;
		}
		int carried = player.getInventory().getAmount(SHARD);
		int most = Math.min(carried, space);
		int slot = book.getSlot();
		player.integerInput("How many shards would you like to charge? (1-" + most + ")",
				amount -> confirm(player, slot, book, amount));
	}

	/** Step 2: confirm. */
	private static void confirm(Player player, int slot, Item book, int amount) {
		if (!sameBook(player, slot, book))
			return;
		int have = charges(book);
		int add = Math.min(Math.min(amount, player.getInventory().getAmount(SHARD)), MAX_CHARGES - have);
		if (add <= 0) {
			player.sendMessage(TAG + "You didn't add any shards.");
			return;
		}
		player.dialogue(
				new MessageDialogue("Add " + add + " Combat Pet Shard" + (add == 1 ? "" : "s") + " to your Bestiary?<br>Charges: "
						+ have + " -> " + (have + add) + " / " + MAX_CHARGES),
				new OptionsDialogue("Charge your Bestiary?",
						new Option("Yes, add " + add + " shard" + (add == 1 ? "" : "s") + ".", () -> charge(player, slot, book, add)),
						new Option("Cancel.", () -> {})));
	}

	/** Step 3: everything re-checked, since the inventory may have changed while the dialogues were open. */
	private static void charge(Player player, int slot, Item book, int wanted) {
		if (!sameBook(player, slot, book))
			return;
		int have = charges(book);
		int add = Math.min(Math.min(wanted, player.getInventory().getAmount(SHARD)), MAX_CHARGES - have);
		if (add <= 0)
			return;
		int removed = player.getInventory().remove(SHARD, add);
		if (removed <= 0)
			return;
		int now = have + removed;
		player.sendMessage(TAG + "You add " + removed + " shard" + (removed == 1 ? "" : "s") + " to your Bestiary. Charges: "
				+ now + "/" + MAX_CHARGES + ".");
		if (setCharges(book, now) == 1) {
			player.animate(811);
			player.graphics(TierEffects.of(383, PetTier.T3), 0, 0);
			player.sendMessage(TAG + "Your Bestiary glows with power! You can now spawn Combat Pets.");
		} else if (book.getId() == EMPTY_ID) {
			player.sendMessage(TAG + "It needs " + (ACTIVATE_AT - now) + " more charges to awaken.");
		}
	}

	private static boolean sameBook(Player player, int slot, Item book) {
		return player.getInventory().get(slot) == book && isBook(book.getId());
	}

	/* ---------------- dismantling duplicate pets ---------------- */

	private static void offerDismantle(Player player, Pet pet) {
		if (!isDuplicate(player, pet)) {
			player.sendMessage(TAG + "You can only dismantle a duplicate: keep another " + ObjType.get(pet.itemId).name
					+ " in your inventory or bank first.");
			return;
		}
		player.dialogue(
				new MessageDialogue("Dismantle this duplicate " + ObjType.get(pet.itemId).name + " into "
						+ DISMANTLE_SHARDS + " Combat Pet Shards? The pet is destroyed."),
				new OptionsDialogue("Dismantle the duplicate pet?",
						new Option("Yes, dismantle it.", () -> dismantle(player, pet)),
						new Option("Cancel.", () -> {})));
	}

	private static void dismantle(Player player, Pet pet) {
		// re-checked: the inventory may have changed while the dialogue was open
		if (!isDuplicate(player, pet) || player.getInventory().remove(pet.itemId, 1) != 1)
			return;
		player.getInventory().addOrSendToBank(SHARD, DISMANTLE_SHARDS);
		player.animate(811);
		player.sendMessage(TAG + "You dismantle the duplicate pet into " + DISMANTLE_SHARDS + " Combat Pet Shards.");
	}

	/** True when the player keeps another copy after giving one up from the inventory. */
	private static boolean isDuplicate(Player player, Pet pet) {
		int inventory = player.getInventory().getAmount(pet.itemId);
		if (inventory <= 0)
			return false;
		return inventory >= 2 || player.getBank().getAmount(pet.itemId) > 0 || player.pet == pet;
	}

}
