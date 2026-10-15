package io.ruin.model.entity.npc.combatpet;

import io.ruin.Server;
import io.ruin.api.utils.Random;
import io.ruin.model.combat.AttackStyle;
import io.ruin.model.combat.Hit;
import io.ruin.model.entity.npc.NPC;
import io.ruin.model.entity.npc.PetNPC;
import io.ruin.model.entity.player.Player;
import io.ruin.model.map.Projectile;
import io.ruin.model.stat.Stat;
import io.ruin.model.stat.StatType;

/**
 * Stateless attack definition for one combat pet type (shared by every follower of that type).
 * Per-follower state lives in {@link CombatPet}. Register new profiles in {@link CombatPets}.
 *
 * Animations must come from the pet's own skeleton (the boss-sized npc it was shrunk from), or the
 * client plays nothing.
 */
public abstract class CombatPetProfile {

	public final AttackStyle style;
	public final int attackTicks;
	public final int maxHit;
	public final int attackRange;
	/** (effective level) * (bonus + 64), compared against the target's defence roll. */
	public final int accuracyRoll;
	/** Chance per attack to use the special instead (random-proc pets). */
	public final double specialChance;
	/** When > 0 the special is guaranteed every this many engaged ticks instead (tiered pets). */
	public final int specialIntervalTicks;

	protected CombatPetProfile(AttackStyle style, int attackTicks, int maxHit, int attackRange, int accuracyRoll,
			double specialChance) {
		this(style, attackTicks, maxHit, attackRange, accuracyRoll, specialChance, 0);
	}

	protected CombatPetProfile(AttackStyle style, int attackTicks, int maxHit, int attackRange, int accuracyRoll,
			double specialChance, int specialIntervalTicks) {
		this.style = style;
		this.attackTicks = attackTicks;
		this.maxHit = maxHit;
		this.attackRange = attackRange;
		this.accuracyRoll = accuracyRoll;
		this.specialChance = specialChance;
		this.specialIntervalTicks = specialIntervalTicks;
	}

	public abstract void basicAttack(PetNPC pet, Player owner, NPC target);

	public abstract void specialAttack(PetNPC pet, Player owner, NPC target);

	/** Special attack name and a one-line effect, shown in the Ascended Bestiary. */
	public abstract String specialName();

	public abstract String specialEffect();

	/* helpers shared by profiles */

	protected static int accuracy(int level, int bonus) {
		return (level + 8) * (bonus + 64);
	}

	protected Hit roll(Player owner, NPC target) {
		return roll(owner, target, style, maxHit);
	}

	protected Hit roll(Player owner, NPC target, AttackStyle style, int maxHit) {
		return CombatPetCombat.roll(owner, target, style, maxHit, accuracyRoll, 1D);
	}

	/** Roll against a reduced defence roll, e.g. 0.5 = ignores half the target's defence. */
	protected Hit rollPiercing(Player owner, NPC target, AttackStyle style, int maxHit, double defenceMultiplier) {
		return CombatPetCombat.roll(owner, target, style, maxHit, accuracyRoll, defenceMultiplier);
	}

	/** A hit that always lands for {@code min..max}. */
	protected Hit guaranteed(Player owner, NPC target, AttackStyle style, int min, int max) {
		if (target == null)
			return null;
		return CombatPetCombat.fixed(owner, style, Random.get(Math.min(min, max), max));
	}

	/** Melee-timed hit (lands next hit cycle). */
	protected int hitNow(NPC target, Hit hit) {
		return CombatPetCombat.apply(target, hit);
	}

	/** Sends the projectile and delays the hit until it lands. Returns the flight time in client cycles. */
	protected int hitWithProjectile(PetNPC pet, NPC target, Projectile projectile, Hit hit) {
		int duration = projectile.send(pet, target);
		if (hit != null)
			CombatPetCombat.apply(target, hit.clientDelay(duration));
		return duration;
	}

	/**
	 * Gives {@code amount} back to the owner: Prayer when it is proportionally lower than Hitpoints
	 * (and not full), otherwise Hitpoints.
	 */
	protected static void sustain(Player owner, int amount, int gfx) {
		if (amount <= 0 || !owner.isOnline() || owner.getCombat().isDead())
			return;
		Stat prayer = owner.getStats().get(StatType.Prayer);
		double prayerPct = prayer.fixedLevel == 0 ? 1 : (double) prayer.currentLevel / prayer.fixedLevel;
		double hpPct = (double) owner.getHp() / Math.max(1, owner.getMaxHp());
		if (prayerPct < hpPct && prayer.currentLevel < prayer.fixedLevel)
			prayer.restore(amount);
		else
			owner.incrementHp(amount);
		if (gfx >= 0)
			owner.graphics(gfx, 0, 0);
	}

	/**
	 * Runs {@code action} on up to {@code maxTargets} other NPCs touching {@code target} that the owner could
	 * attack themselves (multi-combat only, never players or pets). Returns how many were hit.
	 */
	protected static int splashAdjacent(Player owner, NPC target, int maxTargets, java.util.function.Consumer<NPC> action) {
		if (!owner.inMulti())
			return 0;
		int splashed = 0;
		for (NPC npc : target.localNpcs()) {
			if (splashed >= maxTargets)
				break;
			if (npc == target || npc instanceof PetNPC || npc.getHeight() != target.getHeight())
				continue;
			if (CombatPetCombat.gap(npc, target) > 1 || !owner.getCombat().canAttack(npc, false))
				continue;
			action.accept(npc);
			splashed++;
		}
		return splashed;
	}

	/**
	 * Drains {@code percent} of a target stat for {@code ticks}, then gives back exactly what was taken.
	 * Does not stack: while a debuff under {@code key} is active, further applications are ignored.
	 */
	protected static void timedDrain(NPC target, StatType statType, double percent, int ticks, String key) {
		long now = Server.currentTick();
		Long activeUntil = target.get(key);
		if (activeUntil != null && activeUntil > now)
			return;
		Stat stat = target.getCombat().getStat(statType);
		if (stat == null)
			return;
		int drained = stat.drain(percent);
		if (drained <= 0)
			return;
		target.set(key, now + ticks);
		target.addEvent(e -> {
			e.delay(ticks);
			target.remove(key);
			if (!target.isRemoved())
				stat.restore(drained); // capped at the base level
		});
	}

	/**
	 * Damage over time credited to the owner: {@code damage} every {@code interval} ticks, {@code pulses} times.
	 * Does not stack under the same {@code key}.
	 */
	protected static void damageOverTime(Player owner, NPC target, AttackStyle style, int damage, int interval,
			int pulses, String key) {
		long now = Server.currentTick();
		Long activeUntil = target.get(key);
		if (activeUntil != null && activeUntil > now)
			return;
		target.set(key, now + (long) interval * pulses);
		target.addEvent(e -> {
			for (int i = 0; i < pulses; i++) {
				e.delay(interval);
				if (!owner.isOnline() || target.isRemoved() || target.getCombat().isDead())
					break;
				CombatPetCombat.apply(target, CombatPetCombat.fixed(owner, style, damage));
			}
			target.remove(key);
		});
	}

}
