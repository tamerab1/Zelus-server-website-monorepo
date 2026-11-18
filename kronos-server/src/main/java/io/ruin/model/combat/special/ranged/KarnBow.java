package io.ruin.model.combat.special.ranged;

import io.ruin.cache.ObjType;
import io.ruin.model.combat.AttackStyle;
import io.ruin.model.combat.AttackType;
import io.ruin.model.combat.Hit;
import io.ruin.model.combat.HitType;
import io.ruin.model.combat.special.Special;
import io.ruin.model.entity.Entity;
import io.ruin.model.entity.player.Player;
import io.ruin.model.map.Projectile;

import static io.ruin.model.entity.player.PlayerCombat.getBowHitDelay;

/**
 * Crimson Volley (55%), the Karn bow (user 2026-11-15): two crimson arrows (the red crystal arrow graphics), each with
 * +50% accuracy and +25% damage; every arrow that lands heals you for 10% of what it dealt. No ammunition needed.
 */
public class KarnBow implements Special {

	public static final int KARN_BOW = 40076;

	private static final Projectile[] ARROWS = {
		new Projectile(1922, 40, 36, 41, 52, 6, 5, 11),
		new Projectile(1922, 40, 36, 51, 65, 10, 25, 11),
	};

	@Override
	public boolean accept(ObjType def, String name) {
		return def.id == KARN_BOW;
	}

	@Override
	public boolean handle(Player player, Entity target, AttackStyle style, AttackType type, int maxDamage) {
		player.animate(426);
		player.graphics(1923, 96, 0);
		Hit[] hits = new Hit[ARROWS.length];
		int distance = Math.max(Math.abs(player.getAbsX() - target.getAbsX()), Math.abs(player.getAbsY() - target.getAbsY()));
		for (int i = 0; i < ARROWS.length; i++) {
			ARROWS[i].send(player, target);
			Hit hit = new Hit(player, style, type)
				.randDamage(maxDamage)
				.boostDamage(0.25)
				.boostAttack(0.5)
				.delay(getBowHitDelay(distance) + i)
				.setAttackWeapon(ObjType.get(KARN_BOW));
			hit.postDamage(t -> {
				t.graphics(1925, 96, 0);
				if (hit.type == HitType.DAMAGE && hit.damage > 0)
					player.incrementHp(Math.max(1, hit.damage / 10));
			});
			hits[i] = hit;
		}
		target.hit(hits);
		return true;
	}

	@Override
	public int getDrainAmount() {
		return 55;
	}
}
