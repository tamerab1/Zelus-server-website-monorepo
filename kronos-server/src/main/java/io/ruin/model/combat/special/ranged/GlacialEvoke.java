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

//Glacial Evoke: A frost-charged shot with Zaryte precision that bursts into ice for 20% more damage and freezes the
//target. (75%) -- the Zaryte Stormpiercer 31856 (customs19, 2026-10-19). The Zaryte crossbow's Evoke ignores defence and
//forces its bolt's effect; the Stormpiercer needs no ammo, so its forced effect is its own frost burst instead. The Evoke
//bolt is recoloured ice (spotanim 4428, .dev/cache-restore-tool/stormpiercer_gfx.txt) and lands with Ice Barrage's
//splash; spec bar via cache enums 906 / 1739 (stormpiercer_spec_enums.txt).
public class GlacialEvoke implements Special {

	private static final Projectile ICE_BOLT = new Projectile(6835, 38, 36, 41, 51, 5, 5, 11);
	private static final int ICE_SPLASH = 369, FREEZE_SECONDS = 5;

	@Override
	public boolean accept(ObjType def, String name) {
		return def.id == 42656;
	}

	@Override
	public boolean handle(Player player, Entity target, AttackStyle style, AttackType type, int maxDamage) {
		player.animate(9168); // the Zaryte crossbow's own Evoke animation
		ICE_BOLT.send(player, target);
		int delayTicks = PlayerCombat.getBowHitDelay(PlayerCombat.getChebyshevDistance(player, target));
		Hit hit = new Hit(player, style, type).randDamage(maxDamage).boostDamage(0.20).ignoreDefence().delay(delayTicks);
		hit.afterPostDamage(t -> {
			if (hit.isBlocked())
				return;
			t.graphics(ICE_SPLASH, 0, 0);
			t.freeze(FREEZE_SECONDS, player);
		});
		target.hit(hit);
		return true;
	}

	@Override
	public int getDrainAmount() {
		return 75;
	}
}
