package io.ruin.model.combat.special.magic;

import io.ruin.cache.ObjType;
import io.ruin.model.combat.AttackStyle;
import io.ruin.model.combat.AttackType;
import io.ruin.model.combat.Hit;
import io.ruin.model.combat.special.Special;
import io.ruin.model.content.soulweapons.SoulTalents;
import io.ruin.model.entity.Entity;
import io.ruin.model.entity.npc.NPC;
import io.ruin.model.entity.player.Player;
import io.ruin.model.entity.player.PlayerCombat;
import io.ruin.model.map.Projectile;

//Arcane Cataclysm: a violet starburst that hits up to 60% harder with doubled accuracy and, in multi-combat, splashes half
//its damage onto up to four monsters around the target (50%) -- the Radiant Soulspire 42764 (tier 15, SoulWeapons,
//customs27 2026-10-27). Fired from PlayerCombat's Spire block, which passes the spell's max hit. The void-violet bolt of the
//Radiant look (Ocellus' magic: cast 4294, projectile 4297) bursting in his void explosion (4339). PvM only; spec bar: enum
//906 / 1739 (PatchCustoms27).
public class ArcaneCataclysm implements Special {

	private static final int SPLASH_RANGE = 1, SPLASH_TARGETS = 4, GFX_CAST = 4294, GFX_BOLT = 4297, GFX_BURST = 4339;

	@Override
	public boolean accept(ObjType def, String name) {
		return def.id == 42764;
	}

	@Override
	public boolean handle(Player player, Entity target, AttackStyle attackStyle, AttackType attackType, int maxDamage) {
		if (target.player != null) {
			player.sendMessage("Arcane Cataclysm can only be used against monsters.");
			return false; // no energy spent
		}
		player.animate(1167);
		player.graphics(GFX_CAST, 92, 0);
		int duration = new Projectile(GFX_BOLT, 62, 31, 56, 56, 10, 32, 11).send(player, target);
		int delay = PlayerCombat.getMagicHitDelay(PlayerCombat.getChebyshevDistance(player, target));
		Hit hit = new Hit(player, AttackStyle.MAGIC, attackType).randDamage(Math.max(1, (int) (maxDamage * 1.6)))
			.boostAttack(1.0).delay(delay);
		hit.postDamage(t -> {
			if (hit.damage <= 0 || target.npc == null || !target.npc.inMulti())
				return;
			int splash = Math.max(1, hit.damage / 2);
			int n = 0;
			for (NPC other : SoulTalents.nearby(player, target.npc, SPLASH_RANGE)) {
				Hit s = new Hit(player, AttackStyle.MAGIC, attackType).fixedDamage(splash).ignoreDefence();
				s.soulEcho = true; // never triggers talents itself
				s.postDamage(x -> x.graphics(GFX_BURST));
				other.hit(s);
				if (++n >= SPLASH_TARGETS)
					break;
			}
		});
		target.hit(hit);
		target.graphics(GFX_BURST, 0, duration);
		return true;
	}

	@Override
	public int getDrainAmount() {
		return 50;
	}
}
