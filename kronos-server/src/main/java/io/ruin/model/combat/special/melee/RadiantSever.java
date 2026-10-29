package io.ruin.model.combat.special.melee;

import io.ruin.cache.ObjType;
import io.ruin.model.combat.AttackStyle;
import io.ruin.model.combat.AttackType;
import io.ruin.model.combat.Hit;
import io.ruin.model.combat.special.Special;
import io.ruin.model.entity.Entity;
import io.ruin.model.entity.player.Player;
import io.ruin.model.stat.StatType;

//Radiant Sever (50%): the Radiant Soulblade 42762 (tier 15, SoulWeapons; 2026-10-28 rework into a one-handed blade, was
//Radiant Judgement on a godsword stance). A swift lunging slash (the longsword lunge 7515) trailing an ethereal cyan blade
//arc (6831), the target struck by phantom slash marks (6832) and a scatter of soul sparks (6830). 25% more accuracy and
//20% more damage; a quarter of the damage dealt returns as Prayer points. PvM only, like every Soulbound power; spec
//bar: enum 906 / 1739 (PatchCustoms27).
public class RadiantSever implements Special {

	private static final int ANIM_LUNGE = 7515, GFX_BLADE_ARC = 6831, GFX_PHANTOM_SLASH = 6832,
			SOUND_SWOOSH = 2529, SOUND_IMPACT = 2537;

	@Override
	public boolean accept(ObjType def, String name) {
		return def.id == 42762;
	}

	@Override
	public boolean handle(Player player, Entity target, AttackStyle attackStyle, AttackType attackType, int maxDamage) {
		if (target.player != null) {
			player.sendMessage("Radiant Sever can only be used against monsters.");
			return false; // no energy spent
		}
		player.animate(ANIM_LUNGE);
		player.graphics(GFX_BLADE_ARC, 96, 0);
		player.publicSound(SOUND_SWOOSH);
		Hit hit = new Hit(player, attackStyle, attackType).randDamage(maxDamage).boostAttack(0.25).boostDamage(0.20);
		hit.postDamage(t -> {
			t.graphics(GFX_PHANTOM_SLASH, 80, 0);
			t.publicSound(SOUND_IMPACT);
			if (hit.damage > 0)
				player.getStats().get(StatType.Prayer).restore(Math.max(1, hit.damage / 4));
		});
		target.hit(hit);
		return true;
	}

	@Override
	public int getDrainAmount() {
		return 50;
	}
}
