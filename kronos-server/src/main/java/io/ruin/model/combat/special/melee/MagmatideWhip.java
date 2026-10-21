package io.ruin.model.combat.special.melee;

import io.ruin.api.utils.Random;
import io.ruin.cache.ObjType;
import io.ruin.model.combat.AttackStyle;
import io.ruin.model.combat.AttackType;
import io.ruin.model.combat.Hit;
import io.ruin.model.combat.special.Special;
import io.ruin.model.entity.Entity;
import io.ruin.model.entity.player.Player;

import static core.task.api.API.queue;
import static core.task.api.API.sleep;

//Magma Brand: A 25% more accurate lash that sets the target ablaze, burning for up to 6 damage 3 more times. (50%)
// Magmatide whip 31960 (Scoreboard Exchange, 2026-10-21; spec bar in enum 906 / 1739 via sbx2_enums.txt).
public class MagmatideWhip implements Special {

	private static final int BURNS = 3, BURN_MAX = 6, BURN_INTERVAL = 2;

	@Override
	public boolean accept(ObjType def, String name) {
		return name.equals("magmatide whip");
	}

	@Override
	public boolean handle(Player player, Entity target, AttackStyle attackStyle, AttackType attackType, int maxDamage) {
		player.animate(1658);
		player.publicSound(2713);
		target.graphics(78, 0, 0); // Flames of Zamorak
		Hit lash = new Hit(player, attackStyle, attackType).randDamage(maxDamage).boostAttack(0.25);
		target.hit(lash);
		if (lash.damage > 0) {
			queue(() -> {
				for (int i = 0; i < BURNS; i++) {
					sleep(BURN_INTERVAL);
					if (target.getHp() <= 0)
						return;
					target.hit(new Hit(player, attackStyle, attackType).fixedDamage(Random.get(1, BURN_MAX)).ignoreDefence());
				}
			});
		}
		return true;
	}

	@Override
	public int getDrainAmount() {
		return 50;
	}
}
