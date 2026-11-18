package io.ruin.model.combat.special.ranged;

import io.ruin.cache.ObjType;
import io.ruin.model.combat.AttackStyle;
import io.ruin.model.combat.AttackType;
import io.ruin.model.combat.Hit;
import io.ruin.model.combat.RewardItems;
import io.ruin.model.combat.special.Special;
import io.ruin.model.entity.Entity;
import io.ruin.model.entity.player.Player;

/**
 * Eclipse (50%, full Eclipse Moon set): a magic-based strike with +50% accuracy that consumes the target's burns,
 * adding the damage left in them to the max hit and half of it to the min hit. No darts needed.
 */
public class EclipseAtlatl implements Special {

	@Override
	public boolean accept(ObjType def, String name) {
		return def.id == RewardItems.ECLIPSE_ATLATL;
	}

	@Override
	public boolean handle(Player player, Entity victim, AttackStyle attackStyle, AttackType attackType, int maxDamage) {
		if (!RewardItems.eclipseMoonSet(player)) {
			player.sendMessage("You need to be wearing the full Eclipse Moon set to use this special attack.");
			return false;
		}
		int burn = Math.min(50, RewardItems.consumeBurns(victim)); // wiki: the burn bonus is capped at 50
		player.animate(11060);
		player.graphics(2797, 0, 0);
		io.ruin.model.activities.raids.tfa.Raid4Fx.spot(player, 1, 2794, 0, 0); // special_atlatl_spotanim, own slot beside the cast
		Hit hit = new Hit(player, AttackStyle.MAGIC, attackType).boostAttack(0.5)
				.randDamage(burn / 2, maxDamage + burn).setAttackWeapon(ObjType.get(RewardItems.ECLIPSE_ATLATL));
		hit.postDamage(t -> t.graphics(2798, 0, 0));
		victim.hit(hit);
		player.getCombat().updateLastAttack(4);
		return true;
	}

	@Override
	public int getDrainAmount() {
		return 50;
	}
}
