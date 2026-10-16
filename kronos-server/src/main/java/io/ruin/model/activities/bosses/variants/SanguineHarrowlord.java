package io.ruin.model.activities.bosses.variants;

import io.ruin.model.combat.AttackStyle;
import io.ruin.model.combat.Hit;
import io.ruin.model.entity.player.Player;

/**
 * Sanguine Harrowlord (Blood Reaver clone, crimson). Vampiric mage: every blood bolt heals it for a quarter of the
 * damage dealt. Special "Crimson Tithe": a blood burst on every player around it that heals it for all of it.
 */
public class SanguineHarrowlord extends BossVariant {

	/** Sanctum main boss: calls one of its wing's guardians below half health. */
	@Override
	protected int reinforcementId() {
		return 30599;
	}

	/** Sanctum main boss: protection prayer always up. */
	@Override
	protected io.ruin.model.entity.npc.NPC.DefaultHeadIconIndex protectionPrayer() {
		return io.ruin.model.entity.npc.NPC.DefaultHeadIconIndex.ProtectFromMelee;
	}


	private static final int GFX_BOLT = 4275, GFX_BOLT_HIT = 4276, GFX_TITHE = 4277;
	private static final int ANIM_CAST = 9194;

	@Override
	protected int attackRange() {
		return 8;
	}

	@Override
	protected int specialEvery() {
		return 5;
	}

	@Override
	protected boolean normalAttack() {
		if (!withinDistance(8))
			return false;
		Hit hit = cast(GFX_BOLT, GFX_BOLT_HIT, ANIM_CAST, AttackStyle.MAGIC, info.max_damage);
		hit.postDamage(e -> npc.incrementHp(hit.damage / 4));
		return true;
	}

	@Override
	protected boolean specialAttack() {
		var victims = playersWithin(8);
		if (victims.isEmpty())
			return false;
		npc.animate(ANIM_CAST);
		npc.forceText("Your blood is owed to me!");
		announce("The Sanguine Harrowlord demands a Crimson Tithe!");
		for (Player p : victims) {
			p.graphics(GFX_TITHE, 0, 40);
			Hit hit = new Hit(npc, AttackStyle.MAGIC).randDamage(10, 28).delay(2);
			hit.postDamage(e -> npc.incrementHp(hit.damage));
			p.hit(hit);
		}
		return true;
	}
}
