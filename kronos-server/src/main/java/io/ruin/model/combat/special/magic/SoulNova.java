package io.ruin.model.combat.special.magic;

import io.ruin.cache.ObjType;
import io.ruin.model.combat.AttackStyle;
import io.ruin.model.combat.AttackType;
import io.ruin.model.combat.Hit;
import io.ruin.model.combat.special.Special;
import io.ruin.model.entity.Entity;
import io.ruin.model.entity.player.Player;
import io.ruin.model.entity.player.PlayerCombat;
import io.ruin.model.map.Projectile;

//Soul Nova: Unleash a surge of soul energy that hits up to 50% harder with doubled accuracy. (50%) -- the Ascended
//soulbound spire 31840 (SoulWeapons, 2026-10-18). Fired from PlayerCombat's Spire block, which passes the spell's max
//hit. PvM only.
public class SoulNova implements Special {

	@Override
	public boolean accept(ObjType def, String name) {
		return def.id == 31840;
	}

	@Override
	public boolean handle(Player player, Entity target, AttackStyle attackStyle, AttackType attackType, int maxDamage) {
		if (target.player != null) {
			player.sendMessage("Soul Nova can only be used against monsters.");
			return false; // no energy spent
		}
		player.animate(1167); // staff held (the v2 Ascended spire uses the standard staff pose)
		player.graphics(2125, 92, 0);
		int duration = new Projectile(2126, 62, 31, 56, 56, 10, 32, 11).send(player, target);
		// standard OSRS magic hit delay; the impact is a spot-anim on the target, timed to the orb's arrival
		Hit hit = new Hit(player, AttackStyle.MAGIC, attackType).randDamage(Math.max(1, (int) (maxDamage * 1.5)))
			.boostAttack(1.0).delay(PlayerCombat.getMagicHitDelay(PlayerCombat.getChebyshevDistance(player, target)));
		target.hit(hit);
		target.graphics(2127, 92, duration);
		return true;
	}

	@Override
	public int getDrainAmount() {
		return 50;
	}
}
