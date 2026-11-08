package io.ruin.model.activities.bosses.custom;

import io.ruin.model.combat.Hit;
import io.ruin.model.entity.Entity;

/**
 * Xanthos the Void Fiend (npc 21943), 2026-11-08. <b>Void Shackle</b> every {@link #SHACKLE_EVERY}th attack: a strike
 * through protection prayers that, if it does damage, holds the player in place for {@link #SHACKLE_SECONDS} seconds.
 */
public class XanthosCombat extends CustomBossCombat {

	static final int SHACKLE_EVERY = 5, SHACKLE_SECONDS = 10, GFX_SHACKLE = 369;

	private int attacks;

	@Override
	public boolean attack() {
		if (target != null && withinDistance(1) && ++attacks % SHACKLE_EVERY == 0) {
			npc.animate(info.attack_animation);
			faceTarget();
			npc.forceText("Bound to the void!");
			Entity victim = target;
			Hit hit = new Hit(npc, info.attack_style, null).randDamage(info.max_damage).ignorePrayer().delay(1);
			hit.postDamage(t -> {
				if (hit.damage > 0) {
					victim.freeze(SHACKLE_SECONDS, npc);
					victim.graphics(GFX_SHACKLE);
					if (victim.player != null)
						victim.player.sendMessage("<col=9b0000>The void shackles you in place!</col>");
				}
			});
			victim.hit(hit);
			return true;
		}
		return super.attack();
	}
}
