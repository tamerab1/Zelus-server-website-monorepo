package io.ruin.model.combat.special.ranged;

import io.ruin.cache.ObjType;
import io.ruin.model.World;
import io.ruin.model.combat.AttackStyle;
import io.ruin.model.combat.AttackType;
import io.ruin.model.combat.Hit;
import io.ruin.model.combat.HitType;
import io.ruin.model.combat.special.Special;
import io.ruin.model.entity.Entity;
import io.ruin.model.entity.player.Player;
import io.ruin.model.map.Projectile;

import static io.ruin.model.entity.player.PlayerCombat.getBowHitDelay;
import static io.ruin.model.entity.player.PlayerCombat.getChebyshevDistance;

/**
 * Hellfang Rend (55%), the Hellfang crossbow and its (or) version (user 2026-11-18: "add a special attack ... unique and
 * clean"): two fangs. A dragonfire bolt (x1.0, +25% accuracy), then a hellfire fang a tick behind it for 60% of the
 * max hit. If both land, the target is set ablaze: 5 hellfire hits of 4, one every 2 ticks. One bolt is used.
 */
public class HellfangCrossbow implements Special {

	public static final int CROSSBOW = 42712, CROSSBOW_OR = 42815;
	private static final Projectile BOLT = new Projectile(698, 38, 36, 41, 51, 5, 5, 11);
	private static final Projectile FANG = new Projectile(2807, 38, 36, 51, 51, 3, 15, 11);
	private static final int ANIM = 4230, GFX_BLAZE = 2808, BURN_HITS = 5, BURN_DAMAGE = 4, BURN_GAP = 2;

	@Override
	public boolean accept(ObjType def, String name) {
		return def.id == CROSSBOW || def.id == CROSSBOW_OR;
	}

	@Override
	public boolean handle(Player player, Entity victim, AttackStyle attackStyle, AttackType attackType, int maxDamage) {
		player.animate(ANIM);
		BOLT.send(player, victim);
		FANG.send(player, victim);
		int delay = getBowHitDelay(getChebyshevDistance(player, victim));
		ObjType weapon = ObjType.get(CROSSBOW);
		Hit bolt = new Hit(player, attackStyle, attackType).randDamage(maxDamage).boostAttack(0.25).delay(delay)
			.setRangedWeapon(weapon);
		Hit fang = new Hit(player, attackStyle, attackType).randDamage((int) (maxDamage * 0.6)).delay(delay + 1)
			.setRangedWeapon(weapon);
		fang.postDamage(t -> {
			if (bolt.type != HitType.DAMAGE || bolt.damage <= 0 || fang.type != HitType.DAMAGE || fang.damage <= 0)
				return;
			t.graphics(GFX_BLAZE, 60, 0);
			World.startEvent(e -> {
				for (int i = 0; i < BURN_HITS; i++) {
					e.delay(BURN_GAP);
					if (t.dead() || player.dead())
						return;
					Hit burn = new Hit(player, attackStyle, attackType).fixedDamage(BURN_DAMAGE).ignoreDefence().ignorePrayer();
					burn.postDamage(b -> b.graphics(GFX_BLAZE, 40, 0));
					t.hit(burn);
				}
			});
		});
		victim.hit(bolt, fang);
		return true;
	}

	@Override
	public int getDrainAmount() {
		return 55;
	}
}
