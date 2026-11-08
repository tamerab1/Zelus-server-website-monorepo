package io.ruin.model.activities.bosses.custom;

import io.ruin.api.utils.Random;
import io.ruin.model.combat.AttackStyle;
import io.ruin.model.combat.Hit;
import io.ruin.model.entity.player.Player;
import io.ruin.model.map.Projectile;

/**
 * The Trial of the Arcane's mages (npcs 21941, 21923, 21924, 21921, and 21925 through {@link SoulreaverCombat}), 2026-11-08.
 * <b>Prayer Sever</b>: every 5-6 attacks a different spell, a Water Surge that switches off the prayers of everyone it
 * reaches, hit or miss, so they have to be turned back on.
 */
public class ArcaneCombat extends CustomBossCombat {

	static final int RANGE = 8, GFX_SEVER = 1459, GFX_SEVER_HIT = 1460;

	private int untilSever = Random.get(5, 6);

	@Override
	public boolean attack() {
		if (target != null && withinDistance(RANGE) && --untilSever <= 0) {
			untilSever = Random.get(5, 6);
			prayerSever();
			return true;
		}
		return super.attack();
	}

	private void prayerSever() {
		npc.animate(info.attack_animation);
		faceTarget();
		npc.forceText("Your gods cannot hear you!");
		for (Player p : players()) {
			Projectile spell = new Projectile(GFX_SEVER, 60, 31, 18, 38, 10, 15, 64);
			spell.tileOffset(npc.getSize() / 2);
			int delay = Math.max(1, (spell.send(npc, p) + 15) / 30);
			p.hit(new Hit(npc, AttackStyle.MAGIC, null).randDamage(info.max_damage).delay(delay));
			npc.addEvent(e -> {
				e.delay(delay);
				if (p.getCombat().isDead())
					return;
				p.graphics(GFX_SEVER_HIT, 92, 0);
				p.getPrayer().deactivateAll();
				p.sendMessage("<col=9b0000>The spell severs you from your prayers!</col>");
			});
		}
	}
}
