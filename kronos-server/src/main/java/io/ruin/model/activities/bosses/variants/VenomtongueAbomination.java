package io.ruin.model.activities.bosses.variants;

import io.ruin.model.combat.AttackStyle;
import io.ruin.model.combat.Hit;
import io.ruin.model.stat.StatType;

/**
 * Venomtongue Abomination (Bloodthirsty mutated Bloodveld clone, toxic). Stab attacks with its long tongue, which
 * reaches two tiles. Special "Corrosive Lash": envenoms the target and corrodes a fifth of their Defence.
 */
public class VenomtongueAbomination extends BossVariant {

	/** Sanctum main boss: calls one of its wing's guardians below half health. */
	@Override
	protected int reinforcementId() {
		return 30600;
	}

	/** Sanctum main boss: protection prayer always up. */
	@Override
	protected io.ruin.model.entity.npc.NPC.DefaultHeadIconIndex protectionPrayer() {
		return io.ruin.model.entity.npc.NPC.DefaultHeadIconIndex.ProtectFromRanged;
	}


	private static final int GFX_LASH = 6690;
	private static final int ANIM_ATTACK = 1552;
	private static final int REACH = 2;

	@Override
	protected int attackRange() {
		return REACH;
	}

	@Override
	protected int specialEvery() {
		return 4;
	}

	@Override
	protected boolean normalAttack() {
		if (distanceTo(target) > REACH)
			return false;
		basicAttack(ANIM_ATTACK, AttackStyle.STAB, info.max_damage);
		return true;
	}

	@Override
	protected boolean specialAttack() {
		if (distanceTo(target) > REACH)
			return false;
		npc.animate(ANIM_ATTACK);
		faceTarget();
		target.graphics(GFX_LASH, 0, 0);
		target.hit(new Hit(npc, AttackStyle.STAB).randDamage(15, 30));
		target.envenom(6);
		if (target.player != null) {
			target.player.getStats().get(StatType.Defence).drain(0.2);
			target.player.sendMessage("<col=1e7b00>The Corrosive Lash eats through your defences!</col>");
		}
		return true;
	}
}
