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
 * Blood Infusion (25%, full Blood Moon set): costs 25% of current HP (not under 4), +25% accuracy and +25% max hit
 * (before the split) on both splats, which roll accuracy independently; a successful splat guarantees Bloodrager.
 */
public class DualMacuahuitl implements Special {

	@Override
	public boolean accept(ObjType def, String name) {
		return def.id == RewardItems.DUAL_MACUAHUITL;
	}

	@Override
	public boolean handle(Player player, Entity victim, AttackStyle attackStyle, AttackType attackType, int maxDamage) {
		if (!RewardItems.bloodMoonSet(player)) {
			player.sendMessage("You need to be wearing the full Blood Moon set to use this special attack.");
			return false;
		}
		int hp = player.getHp();
		if (hp >= 4)
			player.hit(new Hit().fixedDamage(hp / 4));
		player.animate(10989);
		player.graphics(2792, 0, 0);
		int max = (int) Math.floor(maxDamage * 1.25);
		int first = max / 2, second = max - first;
		Hit h1 = new Hit(player, attackStyle, attackType).boostAttack(0.25).randDamage(first).setAttackWeapon(ObjType.get(RewardItems.DUAL_MACUAHUITL));
		Hit h2 = new Hit(player, attackStyle, attackType).boostAttack(0.25).randDamage(second).delay(1).setAttackWeapon(ObjType.get(RewardItems.DUAL_MACUAHUITL));
		victim.hit(h1);
		victim.hit(h2);
		if (!h1.isBlocked() || !h2.isBlocked())
			player.getCombat().updateLastAttack(3); // Bloodrager guaranteed
		return true;
	}

	@Override
	public int getDrainAmount() {
		return 25;
	}
}
