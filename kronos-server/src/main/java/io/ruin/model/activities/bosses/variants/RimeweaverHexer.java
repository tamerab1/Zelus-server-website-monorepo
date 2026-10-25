package io.ruin.model.activities.bosses.variants;

import io.ruin.model.combat.AttackStyle;
import io.ruin.model.combat.Hit;
import io.ruin.model.skills.prayer.Prayer;

/**
 * Rimeweaver Hexer (Chaotic death spawn (magic) clone, frost). Reads your prayers: it fires ice shards (ranged)
 * when you protect from magic, and frost spells otherwise. Special "Permafrost": freezes the target in a block of
 * ice and shatters it on them.
 */
public class RimeweaverHexer extends BossVariant {

	private static final int GFX_FROST = 6691, GFX_FROST_HIT = 6692, GFX_SHARD = 6693, GFX_PERMAFROST = 6694;
	private static final int ANIM_ATTACK = 1540;
	private static final int FREEZE_SECONDS = 3; // Sanctum guardian nerf 2026-10-20 (players: "so aggressive"): was 5 s, special every 5, 18-32

	@Override
	protected int attackRange() {
		return 8;
	}

	@Override
	protected int specialEvery() {
		return 8;
	}

	@Override
	protected boolean normalAttack() {
		if (!withinDistance(8))
			return false;
		boolean protectingMagic = target.player != null && target.player.getPrayer().isActive(Prayer.PROTECT_FROM_MAGIC);
		if (protectingMagic)
			cast(GFX_SHARD, -1, ANIM_ATTACK, AttackStyle.RANGED, info.max_damage);
		else
			cast(GFX_FROST, GFX_FROST_HIT, ANIM_ATTACK, AttackStyle.MAGIC, info.max_damage);
		return true;
	}

	@Override
	protected boolean specialAttack() {
		if (!withinDistance(8))
			return false;
		npc.animate(ANIM_ATTACK);
		npc.forceText("Be still... forever.");
		target.graphics(GFX_PERMAFROST, 0, 40);
		if (!target.hasFreezeImmunity()) {
			target.freeze(FREEZE_SECONDS, npc);
			if (target.player != null)
				target.player.sendMessage("<col=0055ff>Permafrost locks you in place!</col>");
		}
		target.hit(new Hit(npc, AttackStyle.MAGIC).randDamage(10, 20).delay(2));
		return true;
	}
}
