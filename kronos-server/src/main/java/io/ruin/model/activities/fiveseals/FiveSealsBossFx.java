package io.ruin.model.activities.fiveseals;

import io.ruin.api.utils.Random;
import io.ruin.model.World;
import io.ruin.model.combat.AttackStyle;
import io.ruin.model.combat.Hit;
import io.ruin.model.entity.npc.NPC;
import io.ruin.model.entity.player.Player;
import io.ruin.model.skills.prayer.Prayer;

/** Shared by The Five Seals' bosses. */
final class FiveSealsBossFx {

	private FiveSealsBossFx() {
	}

	/**
	 * An attack judged when it LANDS, {@code ticks} after its windup: the protection prayer the player has on at impact
	 * blocks it completely (so a player who reads the telegraph and switches in time is safe). {@code impactGfx} < 0 = none.
	 */
	static void strike(NPC npc, Player p, int ticks, AttackStyle style, Prayer protect, int max, int impactGfx) {
		World.startEvent(e -> {
			e.delay(Math.max(1, ticks));
			if (npc.isRemoved() || npc.getCombat() == null || npc.getCombat().isDead() || !p.isOnline() || p.getCombat().isDead())
				return;
			boolean prayed = p.getPrayer().isActive(protect);
			Hit hit = new Hit(npc, style).ignorePrayer();
			if (prayed)
				hit.block();
			else
				hit.randDamage(max);
			p.hit(hit);
			if (impactGfx >= 0)
				p.graphics(impactGfx, 0, 0);
		});
	}

	static boolean coin() {
		return Random.get(1) == 0;
	}
}
