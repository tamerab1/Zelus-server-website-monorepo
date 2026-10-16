package io.ruin.model.activities.bosses.variants;

import io.ruin.model.combat.AttackStyle;
import io.ruin.model.combat.Hit;
import io.ruin.model.entity.player.Player;

/**
 * Thunderborn Warlord (Chaotic death spawn (melee) clone, storm gold). Slash melee that strikes twice per attack.
 * Special "Stormcleave": lightning strikes everyone within two tiles of it, stuns them and turns off their prayers.
 */
public class ThunderbornWarlord extends BossVariant {

	private static final int GFX_LIGHTNING = 4288;
	private static final int ANIM_ATTACK = 1540;
	private static final int CLEAVE_RADIUS = 2;

	@Override
	protected int attackRange() {
		return 1;
	}

	@Override
	protected int specialEvery() {
		return 6;
	}

	@Override
	protected boolean normalAttack() {
		if (!inMeleeRange())
			return false;
		int perStrike = Math.max(1, info.max_damage * 2 / 3);
		basicAttack(ANIM_ATTACK, AttackStyle.SLASH, perStrike);
		target.hit(new Hit(npc, AttackStyle.SLASH).randDamage(perStrike).delay(1));
		return true;
	}

	@Override
	protected boolean specialAttack() {
		var victims = playersWithin(CLEAVE_RADIUS);
		if (victims.isEmpty())
			return false;
		npc.animate(ANIM_ATTACK);
		npc.forceText("Feel the storm!");
		for (Player p : victims) {
			p.graphics(GFX_LIGHTNING, 0, 0);
			p.hit(new Hit(npc).randDamage(15, 30).delay(1));
			p.stun(2, true);
			p.getPrayer().deactivateAll();
			p.sendMessage("<col=8A0707>The storm shatters your prayers!</col>");
		}
		return true;
	}
}
