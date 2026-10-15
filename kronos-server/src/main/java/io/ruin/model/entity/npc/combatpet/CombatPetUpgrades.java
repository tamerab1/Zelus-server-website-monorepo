package io.ruin.model.entity.npc.combatpet;

import io.ruin.cache.ObjType;
import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.dialogue.MessageDialogue;
import io.ruin.model.inter.dialogue.OptionsDialogue;
import io.ruin.model.inter.utils.Option;
import io.ruin.model.item.Item;
import io.ruin.model.item.actions.ItemItemAction;
import io.ruin.model.item.actions.impl.pet.Pet;

/**
 * Ascending tiered combat pets with Astral Spores (item 31748, stackable cosmic Jute seed --
 * .dev/cache-restore-tool/bestiary_item.txt): use spores on a pet item in the inventory. The item is swapped
 * in place for the next tier (the cache item already carries that tier's look).
 */
public final class CombatPetUpgrades {

	public static final int ASTRAL_SPORE = 31748;

	/** Spores needed for T1 -> T2 and T2 -> T3. */
	static final int[] SPORES = { 1, 3 };

	private static final int ASCEND_ANIM = 811; // arms-raised cast

	private CombatPetUpgrades() {
	}

	public static void register() {
		for (Pet pet : CombatPets.tieredPets())
			ItemItemAction.register(pet.itemId, ASTRAL_SPORE, (player, petItem, spores) -> offer(player, pet));
	}

	/** Checks and asks first; nothing is consumed until the player confirms. */
	private static void offer(Player player, Pet pet) {
		Pet next = CombatPets.nextTier(pet);
		PetTier tier = CombatPets.tierOf(pet);
		if (tier == null)
			return;
		if (next == null) {
			player.sendMessage("This pet has already reached its ultimate Tier 3 form.");
			return;
		}
		int cost = SPORES[tier.ordinal()];
		int have = player.getInventory().getAmount(ASTRAL_SPORE);
		if (have < cost) {
			player.sendMessage("Ascending to Tier " + tier.next().number() + " needs " + cost + " Astral Spore"
					+ (cost == 1 ? "" : "s") + " (you have " + have + ").");
			return;
		}
		String petName = ObjType.get(pet.itemId).name.replaceAll("\\s*\\(T\\d\\)$", "");
		player.dialogue(
				new MessageDialogue("Are you sure you want to ascend your " + petName + " to Tier " + tier.next().number()
						+ " for " + cost + " Astral Spore" + (cost == 1 ? "" : "s") + "?"),
				new OptionsDialogue("Ascend your pet?",
						new Option("Yes, ascend my pet.", () -> ascend(player, pet)),
						new Option("Cancel.", () -> {})));
	}

	private static void ascend(Player player, Pet pet) {
		// re-checked: the inventory may have changed while the dialogue was open
		Item petItem = player.getInventory().findItem(pet.itemId);
		if (petItem == null)
			return;
		Pet next = CombatPets.nextTier(pet);
		PetTier tier = CombatPets.tierOf(pet);
		if (tier == null)
			return;
		if (next == null) {
			player.sendMessage("This pet has already reached its ultimate Tier 3 form.");
			return;
		}
		int cost = SPORES[tier.ordinal()];
		int have = player.getInventory().getAmount(ASTRAL_SPORE);
		if (have < cost) {
			player.sendMessage("Ascending to Tier " + tier.next().number() + " needs " + cost + " Astral Spore"
					+ (cost == 1 ? "" : "s") + " (you have " + have + ").");
			return;
		}
		if (player.getInventory().remove(ASTRAL_SPORE, cost) != cost)
			return;
		petItem.setId(next.itemId);
		player.petItemIdsObtained.add(next.itemId);

		PetTier reached = tier.next();
		player.animate(ASCEND_ANIM);
		// the tier's own burst: electric (T2) or cosmic (T3) shadow-barrage copy
		player.graphics(TierEffects.of(383, reached), 0, 0);
		player.sendMessage(reached == PetTier.T3
				? "<col=a8160f>Your pet reaches its supreme form and ascends to Tier 3!</col>"
				: "<col=0090d8>Your pet absorbs the astral energy and ascends to Tier 2!</col>");
	}

}
