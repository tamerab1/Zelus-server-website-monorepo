package io.ruin.model.activities.bosses.variants;

import io.ruin.model.combat.AttackStyle;
import io.ruin.model.combat.Hit;
import io.ruin.model.entity.Entity;
import io.ruin.model.entity.player.Player;

/**
 * Jadeshot Deadeye (Chaotic death spawn (ranged) clone, jade). Ranged that fires at every player within range at
 * once. Special "Barbed Volley": three rapid jade arrows at the target that pierce Protect from Missiles.
 */
public class JadeshotDeadeye extends BossVariant {

	private static final int GFX_ARROW = 6696, GFX_VOLLEY_HIT = 6697;
	private static final int ANIM_ATTACK = 1540;
	private static final int RANGE = 8;

	@Override
	protected int attackRange() {
		return RANGE;
	}

	@Override
	protected int specialEvery() {
		return 8; // Sanctum guardian nerf 2026-10-20 (players: "so aggressive"): was 5, volley 3 x 22 through prayer
	}

	@Override
	protected boolean normalAttack() {
		if (!withinDistance(RANGE))
			return false;
		npc.animate(ANIM_ATTACK);
		faceTarget();
		var victims = playersWithin(RANGE);
		if (target.player == null || !victims.contains(target.player))
			shoot(target, info.max_damage, false);
		for (Player p : victims)
			shoot(p, info.max_damage, false);
		return true;
	}

	@Override
	protected boolean specialAttack() {
		if (!withinDistance(RANGE))
			return false;
		npc.animate(ANIM_ATTACK);
		faceTarget();
		npc.forceText("Three arrows, three wounds.");
		for (int i = 0; i < 3; i++) {
			int wait = i;
			npc.addEvent(e -> {
				if (wait > 0)
					e.delay(wait);
				if (target != null && !isDead())
					shoot(target, 12, true);
			});
		}
		target.graphics(GFX_VOLLEY_HIT, 0, 90);
		return true;
	}

	private Hit shoot(Entity victim, int max, boolean piercing) {
		projectile(GFX_ARROW).send(npc, victim);
		Hit hit = new Hit(npc, AttackStyle.RANGED).randDamage(max).delay(distanceTo(victim) <= 4 ? 2 : 3);
		if (piercing)
			hit.ignorePrayer();
		victim.hit(hit);
		return hit;
	}
}
