package io.ruin.model.activities.colosseum;

import io.ruin.api.utils.Random;
import io.ruin.model.World;
import io.ruin.model.combat.Hit;
import io.ruin.model.entity.Entity;
import io.ruin.model.entity.player.Player;
import io.ruin.model.item.Item;
import io.ruin.model.item.containers.Equipment;
import io.ruin.model.skills.slayer.Slayer;
import io.ruin.model.stat.StatType;

import java.util.HashMap;
import java.util.Map;

/**
 * What the egg-slot icons do (2026-11-08): each design has one passive that grows with its tier, and only the worn icon
 * counts. Combat effects apply against NPCs only, and everything is off in a Division Rivals match.
 */
public final class EggEffects {

	private EggEffects() {
	}

	// the designs, in EggForge.TIERS order
	private static final int BEAST = 0, SHADE = 1, EASTER = 2, AMBERGLOW = 3, INFERNOGLINT = 4, CHALLENGE = 5, MIDNIGHT = 6, BONECROWN = 7,
			SOULFLAME = 8, FROST = 9, VOID_EYE = 10, SUNBURST = 11;
	/** the percentage at T1; each tier adds that much again (Sunburst has its own 1 to 4 scale, see {@link #percent(int, int)}) */
	private static final int[] PER_TIER = { 1, 1, 2, 2, 3, 2, 3, 2, 4, 3, 1, 0 };
	private static final String[] TEXT = { "%s damage on your Slayer task", "%s chance a monster's attack misses you", "%s Zelus points from kills",
			"%s PvM points from kills", "%s chance your hit burns the target", "%s Trial Marks from the Colosseum", "Prayer drains %s slower",
			"%s damage to undead", "%s chance a kill restores 5 Prayer", "%s chance your hit delays the target's next attack",
			"%s accuracy in all styles", "%s damage to everything" };
	private static final boolean[] SIGNED = { true, false, true, true, false, true, false, true, false, false, true, true };
	private static final int BURN_DAMAGE = 3, BURN_HITS = 3, BURN_TICKS = 2, SOUL_PRAYER = 5, GFX_BURN = 1466;

	/** item id -> design * 5 + tier (0 = T1) */
	private static final Map<Integer, Integer> BY_ID = new HashMap<>();

	static {
		for (int d = 0; d < EggForge.TIERS.length; d++)
			for (int t = 0; t < 5; t++)
				BY_ID.put(EggForge.TIERS[d][t], d * 5 + t);
	}

	private static double percent(int design, int tier) {
		return design == SUNBURST ? 1 + 0.75 * tier : PER_TIER[design] * (tier + 1);
	}

	/** This design's percentage for the player, or 0 when they aren't wearing it. */
	private static double worn(Player player, int design) {
		if (player.inRivalsFight())
			return 0;
		Item[] slots = player.getEquipment().getItems();
		if (slots.length <= Equipment.SLOT_EGG || slots[Equipment.SLOT_EGG] == null)
			return 0;
		Integer code = BY_ID.get(slots[Equipment.SLOT_EGG].getId());
		return code == null || code / 5 != design ? 0 : percent(design, code % 5);
	}

	private static boolean roll(Player player, int design) {
		double chance = worn(player, design);
		return chance > 0 && Random.get() * 100 < chance;
	}

	/** "+1% damage on your Slayer task" and so on, or null if the item isn't an icon. */
	public static String describe(int itemId) {
		Integer code = BY_ID.get(itemId);
		if (code == null)
			return null;
		int design = code / 5;
		String value = (SIGNED[design] ? "+" : "") + format(percent(design, code % 5)) + "%";
		return String.format(TEXT[design], value);
	}

	/** the effect at T5, for the shop */
	public static String describeMax(int itemId) {
		Integer code = BY_ID.get(itemId);
		return code == null ? null : (SIGNED[code / 5] ? "+" : "") + format(percent(code / 5, 4)) + "% at T5";
	}

	private static String format(double v) {
		return v == Math.rint(v) ? String.valueOf((int) v) : String.valueOf(v);
	}

	/* ---------------- combat hooks (PlayerCombat) ---------------- */

	public static void preTargetDefend(Player player, Hit hit, Entity target) {
		if (target == null || target.npc == null)
			return;
		double damage = worn(player, SUNBURST);
		if (Slayer.isTask(player, target.npc))
			damage += worn(player, BEAST);
		if (target.npc.getDef().undead)
			damage += worn(player, BONECROWN);
		if (damage > 0)
			hit.boostDamage(damage / 100);
		double accuracy = worn(player, VOID_EYE);
		if (accuracy > 0)
			hit.boostAttack(accuracy / 100);
	}

	public static void postTargetDamage(Player player, Hit hit, Entity target) {
		if (target == null || target.npc == null || hit.attackStyle == null || hit.damage <= 0)
			return;
		if (roll(player, INFERNOGLINT)) {
			target.graphics(GFX_BURN, 60, 0);
			World.startEvent(e -> {
				for (int i = 0; i < BURN_HITS; i++) {
					e.delay(BURN_TICKS);
					if (target.getHp() < 1 || target.npc.isRemoved())
						return;
					target.hit(new Hit(player).fixedDamage(BURN_DAMAGE));
				}
			});
		}
		if (roll(player, FROST))
			target.npc.getCombat().delayAttack(1);
	}

	/** Called on every hit the player takes, before its accuracy roll. */
	public static void preDefend(Player player, Hit hit) {
		if (hit.attacker != null && hit.attacker.npc != null && hit.attackStyle != null && roll(player, SHADE))
			hit.block();
	}

	public static double prayerDrainFactor(Player player) {
		return 1 + worn(player, MIDNIGHT) / 100;
	}

	/* ---------------- rewards ---------------- */

	private static int boosted(int amount, double percent) {
		return percent <= 0 || amount <= 0 ? amount : (int) Math.ceil(amount * (1 + percent / 100));
	}

	public static int zelusPoints(Player player, int points) {
		return boosted(points, worn(player, EASTER));
	}

	public static int pvmPoints(Player player, int points) {
		return boosted(points, worn(player, AMBERGLOW));
	}

	public static int trialMarks(Player player, int marks) {
		int boosted = boosted(marks, worn(player, CHALLENGE));
		return player.pet == io.ruin.model.item.actions.impl.pet.Pet.DRAKTHOR_HATCHLING ? (int) Math.round(boosted * 1.10) : boosted;
	}

	public static void onKill(Player player) {
		if (roll(player, SOULFLAME))
			player.getStats().get(StatType.Prayer).restore(SOUL_PRAYER);
	}
}
