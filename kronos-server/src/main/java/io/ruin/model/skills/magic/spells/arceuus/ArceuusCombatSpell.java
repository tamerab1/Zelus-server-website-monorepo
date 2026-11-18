package io.ruin.model.skills.magic.spells.arceuus;

import io.ruin.api.utils.Random;
import io.ruin.model.combat.Hit;
import io.ruin.model.entity.Entity;
import io.ruin.model.skills.magic.rune.Rune;
import io.ruin.model.skills.magic.spells.TargetSpell;

/**
 * The Arceuus combat spells (OSRS wiki values; cast/hit spotanims, player anims and autocast slots from the rev 241
 * cache: anims 8972 grasp / 8977 demonbane, autocast enum 1986 slots 53-58).
 * <ul>
 * <li>Grasps: on a successful hit, a chance to bind the target (Ghostly 10% 2 ticks, Skeletal 25% 3, Undead 50% 4).</li>
 * <li>Demonbanes: demons only, +20% accuracy.</li>
 * </ul>
 */
public class ArceuusCombatSpell extends TargetSpell {

	private final int bindChance, bindTicks;
	private final boolean demonbane;

	private ArceuusCombatSpell(int lvl, double xp, int max, int anim, int castGfx, int hitGfx, int autocast, int bindChance,
							   int bindTicks, boolean demonbane, io.ruin.model.item.Item... runes) {
		this.bindChance = bindChance;
		this.bindTicks = bindTicks;
		this.demonbane = demonbane;
		setLvlReq(lvl);
		setBaseXp(xp);
		setMaxDamage(max);
		setAnimationId(anim);
		setCastGfx(castGfx, 0, 0);
		setHitGfx(hitGfx, 0);
		setRunes(runes);
		setAutoCast(autocast);
		if (demonbane)
			setCastCheck((p, t) -> {
				if (t.npc != null && t.npc.getDef().demon)
					return true;
				if (p.player != null)
					p.player.sendMessage("This spell only affects demons.");
				return false;
			});
	}

	public static ArceuusCombatSpell inferiorDemonbane() {
		return new ArceuusCombatSpell(44, 27.0, 16, 8977, 1865, 1866, 53, 0, 0, true, Rune.CHAOS.toItem(1), Rune.FIRE.toItem(3));
	}

	public static ArceuusCombatSpell superiorDemonbane() {
		return new ArceuusCombatSpell(62, 36.0, 23, 8977, 1867, 1868, 54, 0, 0, true, Rune.SOUL.toItem(1), Rune.FIRE.toItem(5));
	}

	public static ArceuusCombatSpell darkDemonbane() {
		return new ArceuusCombatSpell(82, 43.5, 30, 8977, 1869, 1870, 55, 0, 0, true, Rune.SOUL.toItem(2), Rune.FIRE.toItem(7));
	}

	public static ArceuusCombatSpell ghostlyGrasp() {
		return new ArceuusCombatSpell(35, 22.5, 12, 8972, 1856, 1857, 56, 10, 2, false, Rune.CHAOS.toItem(1), Rune.AIR.toItem(4));
	}

	public static ArceuusCombatSpell skeletalGrasp() {
		return new ArceuusCombatSpell(56, 33.0, 17, 8972, 1859, 1860, 57, 25, 3, false, Rune.DEATH.toItem(1), Rune.EARTH.toItem(8));
	}

	public static ArceuusCombatSpell undeadGrasp() {
		return new ArceuusCombatSpell(79, 46.5, 24, 8972, 1862, 1863, 58, 50, 4, false, Rune.BLOOD.toItem(1), Rune.FIRE.toItem(12));
	}

	public boolean isGrasp() {
		return !demonbane;
	}

	@Override
	protected void beforeHit(Hit hit, Entity target) {
		if (demonbane)
			hit.boostAttack(0.20);
	}

	@Override
	protected void afterHit(Hit hit, Entity target) {
		if (demonbane || hit.isBlocked() || target.hasFreezeImmunity() || Random.get(0, 99) >= bindChance)
			return;
		target.freeze(1, hit.attacker);
		target.freezeDelay.delay(bindTicks);
	}
}
