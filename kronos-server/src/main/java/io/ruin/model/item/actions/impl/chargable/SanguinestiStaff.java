package io.ruin.model.item.actions.impl.chargable;

import io.ruin.api.utils.NumberUtils;
import io.ruin.cache.Color;
import io.ruin.cache.ObjType;
import io.ruin.cache.ItemID;
import io.ruin.model.combat.Hit;
import io.ruin.model.entity.Entity;
import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.dialogue.ItemDialogue;
import io.ruin.model.inter.dialogue.OptionsDialogue;
import io.ruin.model.inter.utils.Option;
import io.ruin.model.item.Item;
import io.ruin.model.item.actions.ItemAction;
import io.ruin.model.item.actions.ItemItemAction;
import io.ruin.model.item.attributes.AttributeExtensions;

/**
 * @author ReverendDread on 5/16/2020
 * https://www.rune-server.ee/members/reverenddread/
 * @project Kronos
 */
public class SanguinestiStaff {

	public static final int CHARGED = 22323;
	public static final int UNCHARGED = 22481;
	public static final int MAX_CHARGES = 20_000;

	public static void consumeCharge(Player player, Item staff, Hit hit, Entity entity) {

		if (AttributeExtensions.getCharges(staff) > 0) {
			AttributeExtensions.deincrementCharges(staff, 1);


		} else {
			player.sendMessage(Color.RED.wrap("Your staff has ran out of charges!"));
			staff.setId(UNCHARGED);
			player.getCombat().updateWeapon(false);

		}
	}

	public static void check(Player player, Item staff) {
		player.sendMessage("Your Sanguinesti staff has " + NumberUtils.formatNumber(AttributeExtensions.getCharges(staff)) + " charges remaining.");
	}

	public static void uncharge(Player player, Item staff) {
		int charges = AttributeExtensions.getCharges(staff);
		player.dialogue(new OptionsDialogue(Color.DARK_RED.wrap("Uncharge your staff for all its charges? (regaining " + NumberUtils.formatNumber(charges * 3) + " blood runes)."),
			new Option("Proceed.", () -> {
				player.getInventory().add(ItemID.BLOOD_RUNE, charges * 3);
				AttributeExtensions.setCharges(staff, 0);
				staff.setId(UNCHARGED);
				player.dialogue(new ItemDialogue().one(CHARGED, "You uncharge your sanguindesti staff, regaining " +
					NumberUtils.formatNumber(charges * 3) + " blood runes in the process."));
			}),
			new Option("Cancel.", Player::closeDialogue)
		));
	}

	public static void charge(Player player, Item staff) {
		int currentCharges = AttributeExtensions.getCharges(staff);
		if (currentCharges >= MAX_CHARGES) {
			player.sendMessage("Your staff can't hold any more charges.");
			return;
		}
		int runesInInventory = player.getInventory().getAmount(ItemID.BLOOD_RUNE);
		if (runesInInventory == 0) {
			player.sendMessage("You require blood runes to charge your staff.");
			return;
		}
		// Asks for blood RUNES directly, not charges (1 charge = 3 runes) -- the old prompt
		// asked "how many charges?" but only capped the removal against the staff's raw
		// capacity, not the player's actual rune count matched to the x3 multiplier. A player
		// charging a near-empty staff (capacity up to 60,000 runes) who typed any charge amount
		// >= their own rune count / 3 had their ENTIRE stack silently consumed -- confirmed as
		// a real incident (30k+ blood runes eaten in one go). Working in runes throughout
		// removes the multiplier surprise and clamps input directly against what's held.
		int maxRunesUsable = Math.min(runesInInventory, (MAX_CHARGES - currentCharges) * 3);
		player.integerInput("How many blood runes do you want to use? (Up to " + NumberUtils.formatNumber(maxRunesUsable) + ", 3 runes per charge)", (input) -> {
			int runesToUse = Math.min(Math.max(input, 0), maxRunesUsable);
			int removed = player.getInventory().remove(ItemID.BLOOD_RUNE, runesToUse);
			AttributeExtensions.addCharges(staff, removed / 3);
			staff.setId(CHARGED);
			check(player, staff);
		});
	}

	public static void charge(Player player, Item staff, Item rune) {
		charge(player, staff);
	}

	private static void wield(Player player, Item item) {
		player.sendMessage("Your sanguinesti staff has no charges! You can use blood runes to power the staff.");
	}

	public static void register() {

		ItemItemAction.register(UNCHARGED, ItemID.BLOOD_RUNE, SanguinestiStaff::charge);
		ItemAction.registerInventory(UNCHARGED, "charge", SanguinestiStaff::charge);
		ItemAction.registerInventory(UNCHARGED, "wield", SanguinestiStaff::wield);

		ItemItemAction.register(CHARGED, ItemID.BLOOD_RUNE, SanguinestiStaff::charge);
		ItemAction.registerInventory(CHARGED, "charge", SanguinestiStaff::charge);
		ItemAction.registerEquipment(CHARGED, "check", SanguinestiStaff::check);
		ItemAction.registerInventory(CHARGED, "check", SanguinestiStaff::check);
		ItemAction.registerInventory(CHARGED, "uncharge", SanguinestiStaff::uncharge);
		ObjType.get(CHARGED).addPreTargetDefendListener(SanguinestiStaff::consumeCharge);

	}

}
