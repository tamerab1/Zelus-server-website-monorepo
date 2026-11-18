package io.ruin.model.combat.special.melee;

import io.ruin.cache.ObjType;
import io.ruin.model.combat.AttackStyle;
import io.ruin.model.combat.AttackType;
import io.ruin.model.combat.Hit;
import io.ruin.model.combat.HitType;
import io.ruin.model.combat.special.Special;
import io.ruin.model.entity.Entity;
import io.ruin.model.entity.player.Player;

/**
 * Karn's Judgement (50%), the Karn sword (user 2026-11-15): a slash with +40% accuracy and +30% damage, then a crimson
 * follow-up a tick later for half of the first; you heal 20% of all the damage dealt.
 */
public class KarnSword implements Special {

	public static final int KARN_SWORD = 42773;

	@Override
	public boolean accept(ObjType def, String name) {
		return def.id == KARN_SWORD;
	}

	@Override
	public boolean handle(Player player, Entity victim, AttackStyle attackStyle, AttackType attackType, int maxDamage) {
		player.animate(1872);          // the scimitar's special swing
		player.graphics(2792, 0, 0);   // a crimson burst
		Hit first = new Hit(player, attackStyle, attackType).boostAttack(0.4).boostDamage(0.3).randDamage(maxDamage)
			.setAttackWeapon(ObjType.get(KARN_SWORD));
		first.postDamage(t -> {
			if (first.type != HitType.DAMAGE || first.damage <= 0)
				return;
			int follow = Math.max(1, first.damage / 2);
			Hit second = new Hit(player, attackStyle, attackType).fixedDamage(follow).ignoreDefence().delay(1)
				.setAttackWeapon(ObjType.get(KARN_SWORD));
			second.postDamage(v -> v.graphics(2792, 0, 0));
			victim.hit(second);
			player.incrementHp(Math.max(1, (first.damage + follow) / 5));
		});
		victim.hit(first);
		return true;
	}

	@Override
	public int getDrainAmount() {
		return 50;
	}
}
