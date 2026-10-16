package io.ruin.model.combat.special.ranged;

import io.ruin.cache.ObjType;
import io.ruin.model.combat.AttackStyle;
import io.ruin.model.combat.AttackType;
import io.ruin.model.combat.Hit;
import io.ruin.model.combat.HitType;
import io.ruin.model.combat.special.Special;
import io.ruin.model.entity.Entity;
import io.ruin.model.entity.player.Player;
import io.ruin.model.item.Item;
import io.ruin.model.item.containers.Equipment;
import io.ruin.model.map.Projectile;

import static io.ruin.model.entity.player.PlayerCombat.getBowHitDelay;
import static io.ruin.model.entity.player.PlayerCombat.getChebyshevDistance;

/**
 * Nightfall bow (celestial) 31818 -- "Starfall Volley" (55%): two starlit arrows (navy/gold dragon heads, spotanims
 * 4418/4419 -- .dev/cache-restore-tool/celestial_nightfall_gfx.txt), each up to 35% more damage and never less than 8,
 * and every arrow that lands heals the archer for a fifth of its damage in a shower of stardust (4293).
 */
public class StarfallVolley implements Special {

	public static final int CELESTIAL_NIGHTFALL_BOW = 31818;

	/** The Venator bow's quick draw (the celestial bow's own attack animation, weapon type CELESTIAL_NIGHTFALL_BOW). */
	public static final int ATTACK_ANIM = 9858;
	private static final int STAR_DRAWBACK = 4416, STAR_HEAD = 4418, STAR_IMPACT = 4419, STARDUST = 4293;
	private static final Projectile[] VOLLEY = {
		new Projectile(STAR_HEAD, 40, 36, 41, 52, 6, 5, 11),
		new Projectile(STAR_HEAD, 40, 36, 41, 65, 10, 25, 11),
	};
	private static final int MIN_DAMAGE = 8;
	private static final double DAMAGE_BOOST = 0.35, HEAL_SHARE = 0.20;

	@Override
	public boolean accept(ObjType def, String name) {
		return def.id == CELESTIAL_NIGHTFALL_BOW;
	}

	@Override
	public boolean handle(Player player, Entity target, AttackStyle style, AttackType type, int maxDamage) {
		Item ammo = player.getEquipment().get(Equipment.SLOT_AMMO);
		if (ammo == null || ammo.getAmount() < 2) {
			player.sendMessage("You need at least two arrows in your quiver to use this special attack.");
			return false;
		}
		player.animate(ATTACK_ANIM);
		player.graphics(STAR_DRAWBACK, 96, 0);
		Hit[] hits = new Hit[VOLLEY.length];
		for (int i = 0; i < VOLLEY.length; i++) {
			VOLLEY[i].send(player, target);
			int delayTicks = getBowHitDelay(getChebyshevDistance(player, target));
			Hit hit = new Hit(player, style, type)
				.randDamage(maxDamage)
				.boostDamage(DAMAGE_BOOST)
				.boostAttack(0.45)
				.capDamagePvP(48, target)
				.delay(delayTicks);
			hit.postDefend(t -> {
				hit.type = HitType.DAMAGE;
				hit.damage = Math.max(MIN_DAMAGE, hit.damage);
			}).postDamage(t -> {
				t.graphics(STAR_IMPACT, 96, 0);
				t.graphics(STARDUST, 70, 0);
				int heal = (int) (hit.damage * HEAL_SHARE);
				if (heal > 0 && !player.getCombat().isDead())
					player.hit(new Hit(HitType.HEAL).fixedDamage(heal));
			});
			hits[i] = hit;
		}
		player.getCombat().removeAmmo(ammo, hits);
		target.hit(hits);
		return true;
	}

	@Override
	public int getDrainAmount() {
		return 55;
	}

}
