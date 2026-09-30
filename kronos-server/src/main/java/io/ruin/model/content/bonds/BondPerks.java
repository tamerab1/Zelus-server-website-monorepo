package io.ruin.model.content.bonds;

import io.ruin.api.utils.Random;
import io.ruin.cache.ObjType;
import io.ruin.model.World;
import io.ruin.model.combat.Hit;
import io.ruin.model.entity.Entity;
import io.ruin.model.entity.npc.NPC;
import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.handlers.EquipmentStats;
import io.ruin.model.item.Item;
import io.ruin.model.skills.magic.rune.Rune;
import io.ruin.model.skills.slayer.Slayer;
import io.ruin.model.stat.Stat;
import io.ruin.model.stat.StatType;

/**
 * Every Custom Bond perk effect. Game systems call into here from a single line at their hook
 * point; everything checks {@link Player#getBondTier} (an ATTUNED bond in the inventory) first,
 * so banking, dropping or severing a bond switches its perks off immediately.
 *
 * <p>Combat perks only apply against NPCs (like the Soulsplit perk), so bonds don't affect PvP.</p>
 */
public final class BondPerks {

	private BondPerks() {
	}

	/* ------------------------------------------------------------------ */
	/* Melee -- Bond of the Blood Titan                                   */
	/* ------------------------------------------------------------------ */

	/** Largest HP leech heal from a single hit. */
	private static final int MAX_LEECH_PER_HIT = 10;

	/** Flat melee strength bonus: +2 at T2-T3, +4 at T4-T5 (vs NPCs). */
	public static int meleeStrengthBonus(Player player, Entity target) {
		if (target == null || !target.isNpc())
			return 0;
		int tier = player.getBondTier(BondType.MELEE);
		return tier >= 4 ? 4 : tier >= 2 ? 2 : 0;
	}

	/** 2% x tier chance for a special attack to cost no energy (vs NPCs). */
	public static boolean saveSpecialEnergy(Player player, Entity target) {
		if (target == null || !target.isNpc())
			return false;
		int tier = player.getBondTier(BondType.MELEE);
		if (tier > 0 && Random.get() < 0.02 * tier) {
			player.sendFilteredMessage("Your Bond of the Blood Titan preserves your special attack energy.");
			return true;
		}
		return false;
	}

	/** T5: NPC stat drains are halved. Use for every drain an NPC attack applies to a player. */
	public static int npcDrain(Player player, Stat stat, int amount) {
		if (player != null && amount > 0 && player.getBondTier(BondType.MELEE) >= 5) {
			// Halve, rounding a leftover half up or down at random so small drains still average out.
			amount = amount / 2 + (amount % 2 == 1 && Random.rollPercent(50) ? 1 : 0);
		}
		return stat.drain(amount);
	}

	/** Percentage overload of {@link #npcDrain(Player, Stat, int)}, matching Stat.drain(double). */
	public static int npcDrain(Player player, Stat stat, double percent) {
		return npcDrain(player, stat, (int) (stat.fixedLevel * percent));
	}

	/* ------------------------------------------------------------------ */
	/* Ranged -- Bond of the Void Deadeye                                 */
	/* ------------------------------------------------------------------ */

	/** Flat ranged strength bonus: +2 from T2 (vs NPCs). */
	public static int rangedStrengthBonus(Player player, Entity target) {
		if (target == null || !target.isNpc())
			return 0;
		return player.getBondTier(BondType.RANGED) >= 2 ? 2 : 0;
	}

	/** 20% x tier chance (100% at T5) that one ammo is not consumed. */
	public static boolean saveAmmo(Player player, Entity target) {
		if (target == null || !target.isNpc())
			return false;
		int tier = player.getBondTier(BondType.RANGED);
		return tier > 0 && (tier >= 5 || Random.get() < 0.20 * tier);
	}

	/* ------------------------------------------------------------------ */
	/* Magic -- Bond of the Astral Archmage                               */
	/* ------------------------------------------------------------------ */

	/** Elemental runes the bond supplies for free: Air (T1), +Water (T2), +Earth (T3), +Fire (T4+). */
	public static boolean freeRune(Player player, Rune rune) {
		int tier = player.getBondTier(BondType.MAGIC);
		if (tier <= 0 || rune == null)
			return false;
		return switch (rune) {
			case AIR -> true;
			case WATER -> tier >= 2;
			case EARTH -> tier >= 3;
			case FIRE -> tier >= 4;
			default -> false;
		};
	}

	/* ------------------------------------------------------------------ */
	/* Combat pipeline hooks (PlayerCombat)                               */
	/* ------------------------------------------------------------------ */

	/** Extra equipment bonus for PlayerCombat.getBonus (strength bonuses feed the max hit formula). */
	public static int equipmentBonus(Player player, Entity target, int bonusType) {
		if (bonusType == EquipmentStats.MELEE_STRENGTH)
			return meleeStrengthBonus(player, target);
		if (bonusType == EquipmentStats.RANGED_STRENGTH)
			return rangedStrengthBonus(player, target);
		return 0;
	}

	/** Called from PlayerCombat.preTargetDefend, before accuracy and damage are rolled. */
	public static void preTargetDefend(Player player, Hit hit, Entity target) {
		if (target == null || !target.isNpc() || hit.attackStyle == null)
			return;
		// Slayer Mastery (Slayer King T4+): +5% accuracy and damage against the current task, any style.
		if (player.getBondTier(BondType.SLAYER) >= 4 && Slayer.isTask(player, target.npc)) {
			hit.boostAttack(0.05);
			hit.boostDamage(0.05);
		}
		if (hit.attackStyle.isRanged()) {
			int tier = player.getBondTier(BondType.RANGED);
			if (tier > 0) {
				hit.boostDefence(-0.03 * tier); // ignore 3% x tier of the target's defence
				if (tier >= 3 && player.bondRangedStreak >= 2) {
					hit.boostAttack(tier >= 5 ? 0.15 : tier == 4 ? 0.10 : 0.05);
					if (tier >= 4 && hit.maxDamage > 0)
						hit.maxDamage += tier >= 5 ? 2 : 1;
				}
			}
		} else if (hit.attackStyle.isMagic()) {
			int tier = player.getBondTier(BondType.MAGIC);
			if (tier >= 2)
				hit.boostDamage(0.02 * (tier - 1)); // +2% at T2 ... +8% at T5
		}
	}

	/** Called at the end of PlayerCombat.postTargetDamage, after the hit has landed. */
	public static void postTargetDamage(Player player, Hit hit, Entity target) {
		if (target == null || !target.isNpc() || hit.attackStyle == null)
			return;
		if (hit.attackStyle.isMelee()) {
			int tier = player.getBondTier(BondType.MELEE);
			if (tier > 0 && hit.damage > 0) {
				leech(player, hit.damage * 0.005 * tier);
				if (tier >= 3)
					cleave(player, target.npc, hit.damage * (tier >= 5 ? 0.20 : tier == 4 ? 0.10 : 0.05));
			}
		} else if (hit.attackStyle.isRanged()) {
			if (player.getBondTier(BondType.RANGED) > 0)
				player.bondRangedStreak = hit.damage > 0 ? player.bondRangedStreak + 1 : 0;
		} else if (hit.attackStyle.isMagic()) {
			int tier = player.getBondTier(BondType.MAGIC);
			if (tier > 0 && hit.damage > 0) {
				if (Random.get() < 0.03 * tier)
					spellEcho(player, target, hit.damage / 2);
				if (tier >= 5 && hit.damage >= 40)
					player.getStats().get(StatType.Prayer).restore(2);
			}
		}
	}

	/** Heals a fractional amount, rounding the fraction up by chance so small hits still average out. */
	private static void leech(Player player, double amount) {
		int heal = (int) amount;
		if (Random.get() < amount - heal)
			heal++;
		heal = Math.min(heal, MAX_LEECH_PER_HIT);
		if (heal > 0 && player.getHp() > 0)
			player.incrementHp(heal);
	}

	/** Splashes {@code damage} onto up to 2 other attackable NPCs next to the target (multi-combat only). */
	private static void cleave(Player player, NPC target, double damage) {
		int dmg = (int) Math.round(damage);
		if (target == null || dmg <= 0 || !target.inMulti())
			return;
		int reach = Math.max(1, target.getSize());
		int hits = 0;
		for (NPC npc : target.localNpcs()) {
			if (hits >= 2)
				break;
			if (npc == null || npc == target || npc.dead() || npc.getCombat() == null)
				continue;
			if (!npc.getPosition().isWithinDistance(target.getPosition(), reach))
				continue;
			if (!player.getCombat().canAttack(npc, false))
				continue;
			npc.hit(new Hit(player).fixedDamage(dmg)); // credited to the player, no style so it can't cleave again
			hits++;
		}
	}

	/** A second, delayed hit for half the damage, credited to the player. It has no attack style, so it can't echo itself. */
	private static void spellEcho(Player player, Entity target, int damage) {
		if (damage <= 0)
			return;
		World.startEvent(e -> {
			e.delay(1);
			if (!target.dead())
				target.hit(new Hit(player).fixedDamage(damage));
		});
	}

	/* ------------------------------------------------------------------ */
	/* Utility -- Bond of the Sovereign Monarch                           */
	/* ------------------------------------------------------------------ */

	/** T1+: run energy never drains. */
	public static boolean infiniteRun(Player player) {
		return player.getBondTier(BondType.UTILITY) >= 1;
	}

	/** Near a bank or in the Edgeville home area. */
	public static boolean inSafeZone(Player player) {
		return player.wildernessLevel <= 0 && (player.isNearBank() || Player.EDGEVILLE.inBounds(player));
	}

	/** T2+: HP regenerates twice as fast in safe zones (same effect as Rapid Heal). */
	public static boolean fastHpRegen(Player player) {
		return player.getBondTier(BondType.UTILITY) >= 2 && inSafeZone(player);
	}

	/** T2+: special attack energy regenerates twice as fast in safe zones. */
	public static int specialRegenTicks(Player player, int ticks) {
		return player.getBondTier(BondType.UTILITY) >= 2 && inSafeZone(player) ? Math.max(1, ticks / 2) : ticks;
	}

	/** Pocket Bank (T4+) cooldown. */
	private static final long POCKET_BANK_COOLDOWN_MS = 10 * 60 * 1000L;

	/**
	 * Pocket Bank (T4+): ::bank once every 10 minutes outside the Wilderness / PvP zones.
	 * Returns null when the bank may open (and starts the cooldown), otherwise the reason it can't.
	 */
	public static String usePocketBank(Player player) {
		if (player.getBondTier(BondType.UTILITY) < 4)
			return "::bank needs an attuned Bond of the Sovereign Monarch at Tier 4 or higher.";
		if (player.wildernessLevel > 0 || player.pvpAttackZone)
			return "Your Pocket Bank can't be used in the Wilderness or PvP areas.";
		long wait = player.bondPocketBankAt + POCKET_BANK_COOLDOWN_MS - System.currentTimeMillis();
		if (wait > 0)
			return "Your Pocket Bank is recharging -- " + (wait / 60000 + 1) + " minute(s) left.";
		player.bondPocketBankAt = System.currentTimeMillis();
		return null;
	}

	/** Preservation Field (T4+): potion and food stat boosts wear off 10% slower. */
	public static double boostDurationMultiplier(Player player) {
		return player.getBondTier(BondType.UTILITY) >= 4 ? 1.1 : 1;
	}

	/** Preservation Field (T4+): timed potions (divine, overload) re-boost for 10% more cycles, decided when drunk. */
	public static int boostCycles(Player player, int cycles) {
		return player.getBondTier(BondType.UTILITY) >= 4 ? (int) Math.round(cycles * 1.1) : cycles;
	}

	/**
	 * T3+: an NPC drop goes straight into the owner's inventory when it fits; at T5 (with vault
	 * routing on, ::vaultloot) anything that doesn't fit goes to the bank instead. Returns true when
	 * the drop was delivered and must NOT be spawned on the ground.
	 */
	public static boolean autoLoot(Player owner, int id, int amount) {
		if (owner == null || amount <= 0)
			return false;
		int tier = owner.getBondTier(BondType.UTILITY);
		if (tier < 3)
			return false;
		if (owner.getInventory().hasRoomFor(id, amount)) {
			owner.getInventory().add(id, amount);
			return true;
		}
		if (tier >= 5 && owner.bondVaultRouting) {
			ObjType def = ObjType.get(id);
			int bankId = ObjType.unnotedId(id);
			if (owner.getBank().add(bankId, amount) > 0) {
				owner.sendFilteredMessage("Your Bond of the Sovereign Monarch sends " + amount + " x "
						+ (def == null ? "item" : def.name) + " to your bank.");
				return true;
			}
		}
		return false;
	}

	/** Item overload -- items carrying attributes (charges, perks) always drop normally. */
	public static boolean autoLoot(Player owner, Item item) {
		return item != null && !item.hasAttributes() && autoLoot(owner, item.getId(), item.getAmount());
	}

	/* ------------------------------------------------------------------ */
	/* Skilling -- Bond of the Artisan                                    */
	/* ------------------------------------------------------------------ */

	/** Non-combat skill XP multiplier: +5% (T1-T4), +15% (T5). */
	public static double xpMultiplier(Player player, StatType type) {
		if (type.isCombat())
			return 1;
		int tier = player.getBondTier(BondType.SKILLING);
		return tier >= 5 ? 1.15 : tier >= 1 ? 1.05 : 1;
	}

	/** T3+: 15% chance to keep production materials (bars, leather, herbs). */
	public static boolean saveMaterials(Player player) {
		if (player.getBondTier(BondType.SKILLING) >= 3 && Random.rollPercent(15)) {
			player.sendFilteredMessage("Your Bond of the Artisan saves your materials.");
			return true;
		}
		return false;
	}

	/**
	 * Delivers a gathered resource (Mining, Woodcutting, Fishing) -- replaces the skill's own
	 * inventory add. T2+: double resources (10%, 35% at T5). T5: 10% chance the resource is
	 * transmuted into its value in coins. T4+: 25% chance it goes straight to the bank.
	 */
	public static void gatherResource(Player player, int id, int amount, boolean noted) {
		int tier = player.getBondTier(BondType.SKILLING);
		if (tier >= 2 && Random.rollPercent(tier >= 5 ? 35 : 10)) {
			amount *= 2;
			player.sendFilteredMessage("Your Bond of the Artisan doubles your resources.");
		}
		if (tier >= 5 && Random.rollPercent(10)) {
			ObjType def = ObjType.get(id);
			long coins = (long) Math.max(1, def == null ? 1 : def.value) * amount;
			player.getInventory().addOrDrop(995, (int) Math.min(Integer.MAX_VALUE, coins));
			player.sendFilteredMessage("Your Bond of the Artisan transmutes your resources into " + coins + " coins.");
			return;
		}
		if (tier >= 4 && Random.rollPercent(25) && player.getBank().add(id, amount) > 0) {
			player.sendFilteredMessage("Your Bond of the Artisan sends your resources to the bank.");
			return;
		}
		player.getInventory().add(noted ? id + 1 : id, amount);
	}

	/* ------------------------------------------------------------------ */
	/* Slayer -- Bond of the Slayer King                                  */
	/* ------------------------------------------------------------------ */

	/** Bonus Slayer points on task completion: +10% (T1-T2), +15% (T3), +20% (T4+). Returns the bonus (and announces it). */
	public static int slayerBonusPoints(Player player, int points) {
		int tier = player.getBondTier(BondType.SLAYER);
		if (tier < 1 || points <= 0)
			return 0;
		double pct = tier >= 4 ? 0.20 : tier == 3 ? 0.15 : 0.10;
		int bonus = Math.max(1, (int) Math.round(points * pct));
		player.sendMessage("<col=7F00FF>Your Bond of the Slayer King grants " + bonus + " bonus Slayer points.");
		return bonus;
	}

	/** T2+: Slayer task skip/cancel and block costs are halved. */
	public static int slayerCost(Player player, int cost) {
		return player.getBondTier(BondType.SLAYER) >= 2 ? (cost + 1) / 2 : cost;
	}

	/** Superior Surge (T3+): superior Slayer monsters spawn 25% more often (the odds denominator shrinks). */
	public static int superiorOdds(Player player, int odds) {
		return player.getBondTier(BondType.SLAYER) >= 3 ? Math.max(2, (int) (odds / 1.25)) : odds;
	}

	/** Drops rarer than 1 in 100 count as rare uniques for the T5 drop-rate perk. */
	private static final int RARE_DROP_RATE = 100;

	/** T5: rare drops are 5% more likely (the drop rate denominator shrinks by 5%). */
	public static int rareDropRate(Player player, int baseRate, int currentRate) {
		if (baseRate < RARE_DROP_RATE || player.getBondTier(BondType.SLAYER) < 5)
			return currentRate;
		return Math.max(1, (int) (currentRate / 1.05));
	}
}
