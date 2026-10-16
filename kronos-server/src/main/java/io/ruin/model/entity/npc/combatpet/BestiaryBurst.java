package io.ruin.model.entity.npc.combatpet;

import io.ruin.cache.ObjType;
import io.ruin.model.World;
import io.ruin.model.entity.npc.PetNPC;
import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.dialogue.OptionsDialogue;
import io.ruin.model.inter.handlers.OptionScroll;
import io.ruin.model.inter.utils.Option;
import io.ruin.model.item.Item;
import io.ruin.model.item.actions.ItemAction;
import io.ruin.model.item.actions.impl.pet.Pet;
import io.ruin.model.var.VarPlayerRepository;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * "Spawn" on a charged Ascended Bestiary: one combat pet from the inventory fights for {@link #DURATION_TICKS}
 * (60 seconds) for {@link #COST} charges, then goes back to the inventory (or bank).
 * <ul>
 * <li>One pet item in the inventory spawns straight away; several open a pick list.</li>
 * <li>Only one pet can be out at a time (the normal pet slot).</li>
 * <li>The burst runs its full minute whether or not the owner is fighting; it only ends early when the pet is
 * picked up, the owner enters the Wilderness or another no-pet area, dies, or logs out (the pet item is handed
 * back on login).</li>
 * <li>Chat reminders at 30 and 10 seconds. The client's "Zelus Combat Pet Timer" plugin turns the [Bestiary]
 * start / return messages into a countdown infobox, so keep their wording in sync with it.</li>
 * </ul>
 */
public final class BestiaryBurst {

	public static final int COST = 10;
	public static final int DURATION_TICKS = 100; // 60 seconds

	/** Chat reminders at these many ticks left (30 and 10 seconds). */
	private static final int REMINDER_30S = 50, REMINDER_10S = 16;

	private static final int RETURN_GFX = 86; // teleport puff

	private BestiaryBurst() {
	}

	public static void register() {
		ItemAction.registerInventory(AscendedBestiary.ID, "spawn", BestiaryBurst::spawnOption);
	}

	private static void spawnOption(Player player, Item book) {
		String blocked = blockReason(player, book);
		if (blocked != null) {
			player.sendMessage(blocked);
			return;
		}
		List<Pet> pets = carriedPets(player);
		if (pets.isEmpty()) {
			player.sendMessage(BestiaryCharges.TAG + "You need a combat pet item in your inventory to spawn.");
			return;
		}
		int slot = book.getSlot();
		if (pets.size() == 1) {
			spawn(player, slot, book, pets.get(0));
			return;
		}
		List<Option> options = new ArrayList<>();
		for (Pet pet : pets)
			options.add(new Option(ObjType.get(pet.itemId).name, () -> spawn(player, slot, book, pet)));
		if (options.size() <= 5)
			player.dialogue(new OptionsDialogue("Spawn which combat pet? (" + COST + " charges)", options));
		else
			OptionScroll.open(player, "Spawn which combat pet? (" + COST + " charges)", options);
	}

	/** Distinct combat pets in the inventory, in inventory order. */
	private static List<Pet> carriedPets(Player player) {
		Set<Pet> pets = new LinkedHashSet<>();
		for (Item item : player.getInventory().getItems()) {
			if (item == null)
				continue;
			Pet pet = Pet.getByItemId(item.getId());
			if (CombatPets.isCombatPet(pet))
				pets.add(pet);
		}
		return new ArrayList<>(pets);
	}

	private static String blockReason(Player player, Item book) {
		if (player.pet != null)
			return "You already have a pet out. Pick it up first.";
		if (player.getCombat().isDead())
			return "You can't do that right now.";
		if (BestiaryCharges.charges(book) < COST)
			return BestiaryCharges.TAG + "You need at least " + COST + " charges in your Bestiary to spawn a combat pet.";
		return Pet.summonBlockReason(player);
	}

	/** Everything is re-checked: the inventory may have changed while the pick list was open. */
	private static void spawn(Player player, int bookSlot, Item book, Pet pet) {
		if (player.getInventory().get(bookSlot) != book || book.getId() != AscendedBestiary.ID)
			return;
		String blocked = blockReason(player, book);
		if (blocked != null) {
			player.sendMessage(blocked);
			return;
		}
		if (player.getInventory().remove(pet.itemId, 1) != 1)
			return;
		int left = BestiaryCharges.charges(book) - COST;
		int change = BestiaryCharges.setCharges(book, left);

		PetNPC npc = pet.spawn(player);
		CombatPet combat = npc.combatPet;
		if (combat != null) {
			combat.burstTicksLeft = DURATION_TICKS;
			combat.burstTicksElapsed = 0;
		}
		PetTier tier = CombatPets.tierOf(pet);
		npc.graphics(TierEffects.of(383, tier == null ? PetTier.T1 : tier), 0, 0);
		player.animate(811);
		player.sendMessage(BestiaryCharges.TAG + "Your " + ObjType.get(pet.itemId).name + " answers the call for "
				+ seconds(DURATION_TICKS) + " seconds. Charges: " + left + "/" + BestiaryCharges.MAX_CHARGES + ".");
		if (change == -1)
			player.sendMessage(BestiaryCharges.TAG + "Your Bestiary has run out of charges and becomes empty.");
	}

	/**
	 * Runs every tick of a bursting pet (from {@link CombatPet#process()}).
	 *
	 * @return true when the burst ended and the pet was sent back
	 */
	static boolean tick(CombatPet combat) {
		Player owner = combat.owner;
		PetNPC npc = combat.pet;
		if (owner == null || !owner.isOnline() || npc.isRemoved())
			return true; // the follow loop removes the npc; the pet item comes back on login
		combat.burstTicksElapsed++;
		String reason = null;
		if (--combat.burstTicksLeft <= 0)
			reason = "Your combat pet returns to the Bestiary.";
		else if (owner.getCombat().isDead())
			reason = "Your combat pet returns to the Bestiary as you fall.";
		else if (Pet.summonBlockReason(owner) != null)
			reason = "Your combat pet can't follow you here and returns to the Bestiary.";
		if (reason == null) {
			countdown(combat);
			return false;
		}
		end(combat, reason);
		return true;
	}

	private static void countdown(CombatPet combat) {
		int left = combat.burstTicksLeft;
		if (left == REMINDER_30S || left == REMINDER_10S)
			combat.owner.sendMessage(BestiaryCharges.TAG + "Your combat pet has " + seconds(left) + " seconds left.");
	}

	/** Ticks to whole seconds, rounded up (a tick is 0.6s). */
	static int seconds(int ticks) {
		return (ticks * 3 + 4) / 5;
	}

	private static void end(CombatPet combat, String message) {
		Player owner = combat.owner;
		PetNPC npc = combat.pet;
		combat.burstTicksLeft = 0;
		combat.disengage();
		World.sendGraphics(RETURN_GFX, 0, 0, npc.getPosition());
		npc.remove();
		if (owner.pet == npc.petType)
			owner.pet = null;
		if (owner.familiarNPC == npc)
			owner.familiarNPC = null;
		VarPlayerRepository.PET_NPC_INDEX.set(owner, -1);
		// while dying, straight to the bank so the pet can't land in an inventory that is about to be dropped
		if (!owner.getCombat().isDead() || owner.getBank().add(npc.petType.itemId, 1) != 1)
			owner.getInventory().addOrSendToBank(npc.petType.itemId, 1);
		owner.sendMessage(BestiaryCharges.TAG + message);
	}

}
