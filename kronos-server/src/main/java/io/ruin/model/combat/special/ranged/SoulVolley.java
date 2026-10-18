package io.ruin.model.combat.special.ranged;

import io.ruin.cache.ObjType;
import io.ruin.model.combat.AttackStyle;
import io.ruin.model.combat.AttackType;
import io.ruin.model.combat.Hit;
import io.ruin.model.combat.special.Special;
import io.ruin.model.entity.Entity;
import io.ruin.model.entity.player.Player;
import io.ruin.model.entity.player.PlayerCombat;
import io.ruin.model.map.Projectile;

//Soul Volley: Loose two soul-charged arrows at once, each dealing 25% more damage. (55%) -- the Ascended soulbound
//recurve 31834 (SoulWeapons, 2026-10-18). Needs no ammo, like the bow itself. PvM only; the dark bow's two-arrow
//pattern with the white crystal arrows.
public class SoulVolley implements Special {

	private static final Projectile[] ARROWS = {
		new Projectile(1924, 40, 36, 41, 52, 6, 5, 11),
		new Projectile(1924, 40, 36, 41, 65, 10, 25, 11),
	};

	@Override
	public boolean accept(ObjType def, String name) {
		return def.id == 31834;
	}

	@Override
	public boolean handle(Player player, Entity target, AttackStyle style, AttackType type, int maxDamage) {
		if (target.player != null) {
			player.sendMessage("Soul Volley can only be used against monsters.");
			return false; // no energy spent
		}
		player.animate(426);
		player.graphics(1925, 96, 0);
		int delayTicks = PlayerCombat.getBowHitDelay(PlayerCombat.getChebyshevDistance(player, target));
		Hit[] hits = new Hit[ARROWS.length];
		for (int i = 0; i < ARROWS.length; i++) {
			ARROWS[i].send(player, target);
			hits[i] = new Hit(player, style, type).randDamage(maxDamage).boostDamage(0.25).boostAttack(0.25).delay(delayTicks);
		}
		target.hit(hits);
		return true;
	}

	@Override
	public int getDrainAmount() {
		return 55;
	}
}
