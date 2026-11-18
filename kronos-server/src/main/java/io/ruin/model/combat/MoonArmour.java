package io.ruin.model.combat;

import io.ruin.cache.ObjType;
import io.ruin.model.World;
import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.dialogue.MessageDialogue;
import io.ruin.model.inter.dialogue.YesNoDialogue;
import io.ruin.model.item.Item;
import io.ruin.model.item.actions.ItemAction;
import io.ruin.model.item.attributes.AttributeExtensions;
import io.ruin.model.item.attributes.AttributeTypes;
import io.ruin.model.item.containers.Equipment;
import io.ruin.model.stat.StatType;
import io.ruin.api.utils.NumberUtils;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Perilous Moons armour degradation (OSRS wiki): a fresh piece turns into its untradeable degraded form with 3,000
 * charges the first time it is worn in combat and loses one charge per 54 seconds (90 ticks) of combat, 45 hours in
 * all. At 0 it breaks (the broken form can't be worn). Bob/Dunstan repair a broken piece for 1,500,000 coins; an
 * armour stand costs 7,500 coins less per Smithing level. Repair gives back the fresh, tradeable piece.
 */
public class MoonArmour {

	/** fresh, degraded, broken */
	public static final int[][] PIECES = {
		{ 29022, 29043, 29067 }, { 29025, 29045, 29070 }, { 29028, 29047, 29073 }, // Blood Moon
		{ 29013, 29037, 29058 }, { 29016, 29039, 29061 }, { 29019, 29041, 29064 }, // Blue Moon
		{ 29004, 29031, 29049 }, { 29007, 29033, 29052 }, { 29010, 29035, 29055 } }; // Eclipse Moon

	public static final int MAX_CHARGES = 3000, TICKS_PER_CHARGE = 90, REPAIR_COST = 1_500_000, STAND_DISCOUNT = 7_500;

	private static final Map<Player, Integer> COMBAT_TICKS = new WeakHashMap<>();

	public static int[] piece(int id) {
		for (int[] p : PIECES)
			if (p[0] == id || p[1] == id || p[2] == id)
				return p;
		return null;
	}

	/** True if the player wears the fresh or degraded form of this fresh piece id. */
	public static boolean wears(Player p, int freshId) {
		int[] pc = piece(freshId);
		return p.getEquipment().hasId(freshId) || (pc != null && p.getEquipment().hasId(pc[1]));
	}

	public static void register() {
		for (int[] pc : PIECES) {
			ItemAction.registerInventory(pc[1], "check", (p, item) -> check(p, item));
			ItemAction.registerEquipment(pc[1], "check", (p, item) -> check(p, item));
		}
		World.startEvent(e -> {
			while (true) {
				e.delay(1);
				for (Player p : World.players())
					tick(p);
			}
		});
	}

	private static void check(Player p, Item item) {
		int charges = AttributeExtensions.getCharges(item);
		double hours = charges * 54 / 3600.0;
		p.sendMessage("Your " + item.getDef().name + " has " + NumberUtils.formatNumber(charges) + " charges left ("
			+ String.format("%.1f", hours) + " hours of combat).");
	}

	private static void tick(Player p) {
		if (p == null || !p.isOnline())
			return;
		boolean any = false;
		for (int slot : new int[] { Equipment.SLOT_HAT, Equipment.SLOT_CHEST, Equipment.SLOT_LEGS }) {
			Item it = p.getEquipment().get(slot);
			if (it != null && piece(it.getId()) != null) {
				any = true;
				break;
			}
		}
		if (!any || !(p.getCombat().isAttacking(10) || p.getCombat().isDefending(10)))
			return;
		int t = COMBAT_TICKS.getOrDefault(p, 0) + 1;
		boolean drain = t >= TICKS_PER_CHARGE;
		COMBAT_TICKS.put(p, drain ? 0 : t);
		for (int slot : new int[] { Equipment.SLOT_HAT, Equipment.SLOT_CHEST, Equipment.SLOT_LEGS }) {
			Item it = p.getEquipment().get(slot);
			if (it == null)
				continue;
			int[] pc = piece(it.getId());
			if (pc == null || it.getId() == pc[2])
				continue;
			if (it.getId() == pc[0]) { // first time in combat: becomes the degraded, untradeable form
				it.setId(pc[1]);
				it.putAttribute(AttributeTypes.CHARGES, MAX_CHARGES);
			}
			if (!drain)
				continue;
			int charges = AttributeExtensions.getCharges(it) - 1;
			if (charges > 0) {
				it.putAttribute(AttributeTypes.CHARGES, charges);
				if (charges == 100 || charges == 10)
					p.sendMessage("<col=ef1020>Your " + it.getDef().name + " is close to breaking.");
				continue;
			}
			it.clearAttribute(AttributeTypes.CHARGES);
			it.setId(pc[2]);
			p.sendMessage("<col=ef1020>Your " + ObjType.get(pc[0]).name + " has broken!");
			if (!p.getEquipment().unequip(it)) { // no space: it stays broken but can't stay worn
				it.remove();
				new io.ruin.model.map.ground.GroundItem(new Item(pc[2], 1)).owner(p).position(p.getPosition()).spawn();
				p.sendMessage("It falls to the ground.");
			}
		}
	}

	/** Repair a broken piece; returns false if the item isn't broken moon armour. */
	public static boolean repair(Player p, Item item, boolean armourStand) {
		int[] pc = piece(item.getId());
		if (pc == null || item.getId() != pc[2])
			return false;
		int price = REPAIR_COST;
		if (armourStand)
			price = Math.max(0, price - STAND_DISCOUNT * p.getStats().get(StatType.Smithing).currentLevel);
		final int cost = price;
		p.dialogue(new YesNoDialogue("Repair the item?", "Repairing this item will cost " + NumberUtils.formatNumber(cost) + " coins. Continue?",
			pc[0], 1, () -> {
				if (!p.getInventory().contains(995, cost)) {
					p.dialogue(new MessageDialogue("Not enough coins in your inventory."));
					return;
				}
				if (item.getId() != pc[2])
					return;
				p.getInventory().remove(995, cost);
				item.setId(pc[0]);
			}));
		return true;
	}
}
