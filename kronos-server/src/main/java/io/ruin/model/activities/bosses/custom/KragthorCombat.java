package io.ruin.model.activities.bosses.custom;

import io.ruin.model.combat.Hit;

/** Krag'thor the Bloodbound (npc 21942): swings with both hands, so every melee attack lands two hits at once (2026-11-08). */
public class KragthorCombat extends CustomBossCombat {

	@Override
	public boolean attack() {
		if (target == null || !withinDistance(1))
			return false;
		npc.animate(info.attack_animation);
		faceTarget();
		int max = info.max_damage * 3 / 4;
		hitPartnerToo(max);
		target.hit(new Hit(npc, info.attack_style, null).randDamage(max).delay(1), new Hit(npc, info.attack_style, null).randDamage(max).delay(1));
		return true;
	}
}
