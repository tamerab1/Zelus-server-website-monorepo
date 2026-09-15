package io.ruin.model.activities.raids.toa.bosses.akkha;

import io.ruin.model.combat.AttackStyle;
import io.ruin.model.combat.Hit;
import io.ruin.model.entity.npc.NPCCombat;
import io.ruin.model.map.Projectile;
import io.ruin.model.skills.prayer.Prayer;

/**
 * Previously had no handler (JSON "handler": ""), so the shadows fell back to
 * BasicCombat, whose attack() only fires within melee distance(1) -- these guard
 * their quadrant corners and never approach the player, so they never attacked
 * regardless of prayer/style.
 */
public class AkkhaShadowCombat extends NPCCombat {

	private static final Projectile MAGIC_PROJECTILE = new Projectile(1304, 60, 31, 25, 35, 10, 0, 32);

	@Override
	public void init() {
	}

	@Override
	public void follow() {
	}

	@Override
	public boolean attack() {
		AkkhaShadow shadow = (AkkhaShadow) npc;
		if (!shadow.isCanAttack() || target == null || !target.isPlayer())
			return false;
		if (!target.getPosition().isWithinDistance(npc.getPosition(), 10))
			return false;
		npc.animate(info.attack_animation);
		int delay = MAGIC_PROJECTILE.send(npc, target);
		int maxDamage = info.max_damage;
		if (target.player.getPrayer().isActive(Prayer.PROTECT_FROM_MAGIC))
			maxDamage /= 3;
		target.hit(new Hit(npc, AttackStyle.MAGIC).randDamage(maxDamage).clientDelay(delay));
		return true;
	}

	@Override
	public void process() {
	}
}
