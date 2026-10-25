package io.ruin.model.activities.bosses.variants;

import io.ruin.model.combat.AttackStyle;
import io.ruin.model.combat.Hit;
import io.ruin.model.stat.StatType;

/**
 * Gloomfang Devourer (Bloodthirsty Night beast clone, void). Hybrid: savage slash bites up close, void orbs at
 * range. Special "Eclipse": snuffs out the target's protection prayer, drains half their prayer, then lands a
 * heavy void blast.
 */
public class GloomfangDevourer extends BossVariant {

	/** Sanctum main boss: calls one of its wing's guardians below half health. */
	@Override
	protected int reinforcementId() {
		return 30598;
	}

	/** Sanctum main boss: protection prayer always up. */
	@Override
	protected io.ruin.model.entity.npc.NPC.DefaultHeadIconIndex protectionPrayer() {
		return io.ruin.model.entity.npc.NPC.DefaultHeadIconIndex.ProtectFromMagic;
	}


	private static final int GFX_ORB = 6687, GFX_ORB_HIT = 6688, GFX_ECLIPSE = 6689;
	private static final int ANIM_ATTACK = 2731;

	@Override
	protected int attackRange() {
		return 1; // closes in to bite, casts while it walks
	}

	@Override
	protected int specialEvery() {
		return 5;
	}

	@Override
	protected boolean normalAttack() {
		if (inMeleeRange()) {
			basicAttack(ANIM_ATTACK, AttackStyle.SLASH, info.max_damage);
			return true;
		}
		if (distanceTo(target) > 8)
			return false;
		cast(GFX_ORB, GFX_ORB_HIT, ANIM_ATTACK, AttackStyle.MAGIC, info.max_damage - 6);
		return true;
	}

	@Override
	protected boolean specialAttack() {
		if (target.player == null || distanceTo(target) > 8)
			return false;
		npc.animate(ANIM_ATTACK);
		npc.forceText("Darkness devours all!");
		var p = target.player;
		p.getPrayer().deactivateProtectionPrayer();
		p.getStats().get(StatType.Prayer).drain(0.5);
		p.sendMessage("<col=8a2be2>The Eclipse snuffs out your prayers!</col>");
		p.graphics(GFX_ECLIPSE, 0, 0);
		p.hit(new Hit(npc, AttackStyle.MAGIC).randDamage(20, 40).delay(2));
		return true;
	}
}
