package io.ruin.model.activities.bosses.custom;

import io.ruin.model.combat.AttackStyle;
import io.ruin.model.combat.Hit;
import io.ruin.model.entity.player.Player;
import io.ruin.model.map.Projectile;

/**
 * Malakor, the Soulreaver (npc 21925), the last of the Trial of the Arcane (2026-11-08). Everything the other mages do,
 * plus <b>Soul Harvest</b> every {@link #HARVEST_EVERY}th attack: he tears the soul out of everyone in the arena, for
 * {@link #HARVEST_MIN}-{@link #HARVEST_MAX} through prayer, and heals {@link #HEAL_RATE}x what he took.
 */
public class SoulreaverCombat extends ArcaneCombat {

	static final int HARVEST_EVERY = 7, HARVEST_MIN = 22, HARVEST_MAX = 48, HEAL_RATE = 2, GFX_SOUL = 1046, GFX_HEAL = 444;

	private int attacks;

	@Override
	public boolean attack() {
		if (target != null && withinDistance(RANGE) && ++attacks % HARVEST_EVERY == 0) {
			soulHarvest();
			return true;
		}
		return super.attack();
	}

	private void soulHarvest() {
		npc.animate(info.attack_animation);
		faceTarget();
		npc.forceText("Your soul is mine!");
		for (Player p : players()) {
			// the soul flies from the player to him
			int delay = Math.max(1, (new Projectile(GFX_SOUL, 40, 80, 0, 45, 8, 12, 64).send(p, npc) + 15) / 30);
			Hit hit = new Hit(npc, AttackStyle.MAGIC, null).randDamage(HARVEST_MIN, HARVEST_MAX).ignoreDefence().ignorePrayer().delay(delay);
			hit.postDamage(t -> {
				if (hit.damage > 0 && !isDead()) {
					npc.incrementHp(hit.damage * HEAL_RATE);
					npc.graphics(GFX_HEAL, 120, 0);
				}
			});
			p.hit(hit);
		}
	}
}
