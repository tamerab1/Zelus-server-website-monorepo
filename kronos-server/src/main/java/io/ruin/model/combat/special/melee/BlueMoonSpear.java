package io.ruin.model.combat.special.melee;

import io.ruin.cache.ObjType;
import io.ruin.model.combat.AttackStyle;
import io.ruin.model.combat.AttackType;
import io.ruin.model.combat.Hit;
import io.ruin.model.combat.RewardItems;
import io.ruin.model.combat.special.Special;
import io.ruin.model.entity.Entity;
import io.ruin.model.entity.player.Player;

/**
 * Break Shackles (50%, full Blue Moon set): frees the target of any bind/freeze; each tick of binding removed gives
 * +1.5% accuracy and +1.5% damage (damage capped at +112.5%). Uses the Fend animation, crush.
 */
public class BlueMoonSpear implements Special {

	@Override
	public boolean accept(ObjType def, String name) {
		return def.id == RewardItems.BLUE_MOON_SPEAR;
	}

	@Override
	public boolean handle(Player player, Entity victim, AttackStyle attackStyle, AttackType attackType, int maxDamage) {
		if (!RewardItems.blueMoonSet(player)) {
			player.sendMessage("You need to be wearing the full Blue Moon set to use this special attack.");
			return false;
		}
		int ticks = Math.max(0, victim.freezeDelay.remaining());
		if (ticks > 0)
			victim.resetFreeze();
		player.animate(429);
		player.graphics(2793, 0, 0); // special_spear_spotanim
		Hit hit = new Hit(player, AttackStyle.CRUSH, attackType).boostAttack(0.015 * ticks)
				.boostDamage(Math.min(1.125, 0.015 * ticks)).randDamage(maxDamage)
				.setAttackWeapon(ObjType.get(RewardItems.BLUE_MOON_SPEAR));
		victim.hit(hit);
		return true;
	}

	@Override
	public int getDrainAmount() {
		return 50;
	}
}
