package io.ruin.model.combat.special.melee;

import io.ruin.cache.ObjType;
import io.ruin.model.combat.AttackStyle;
import io.ruin.model.combat.AttackType;
import io.ruin.model.combat.Hit;
import io.ruin.model.combat.special.Special;
import io.ruin.model.entity.Entity;
import io.ruin.model.entity.player.Player;

import static core.task.api.API.queue;
import static core.task.api.API.sleep;

//Chain Surge: A 25% more accurate lash. On a hit, lightning arcs back through the target for 50% more damage. (50%)
// Stormcoil whip 31958 (Scoreboard Exchange, 2026-10-21; spec bar in enum 906 / 1739 via sbx2_enums.txt).
public class StormcoilWhip implements Special {

	@Override
	public boolean accept(ObjType def, String name) {
		return name.equals("stormcoil whip");
	}

	@Override
	public boolean handle(Player player, Entity target, AttackStyle attackStyle, AttackType attackType, int maxDamage) {
		player.animate(1658);
		player.publicSound(2713);
		target.graphics(76, 0, 0); // Saradomin strike: the bolt from above
		Hit lash = new Hit(player, attackStyle, attackType).randDamage(maxDamage).boostAttack(0.25);
		target.hit(lash);
		if (lash.damage > 0) {
			int arc = Math.max(1, lash.damage / 2);
			queue(() -> {
				sleep(1);
				if (target.getHp() <= 0)
					return;
				target.graphics(76, 0, 0);
				target.hit(new Hit(player, attackStyle, attackType).fixedDamage(arc).ignoreDefence().clientDelay(1));
			});
		}
		return true;
	}

	@Override
	public int getDrainAmount() {
		return 50;
	}
}
