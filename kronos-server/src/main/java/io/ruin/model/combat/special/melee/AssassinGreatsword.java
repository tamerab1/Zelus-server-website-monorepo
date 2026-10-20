package io.ruin.model.combat.special.melee;

import io.ruin.cache.ObjType;
import io.ruin.model.combat.AttackStyle;
import io.ruin.model.combat.AttackType;
import io.ruin.model.combat.Hit;
import io.ruin.model.combat.special.Special;
import io.ruin.model.entity.Entity;
import io.ruin.model.entity.player.Player;

//Umbral Execution: A strike from the shadows with doubled accuracy and 30% more damage, or 60% against targets below
//half health. (50%) -- the Assassin greatsword 31884 (armor batch, 2026-10-20). Black copies of the AGS spec swirl
//(4429 from 1211) and the Ancient godsword burst (4430 from 2001): .dev/cache-restore-tool/armor_1020_fix_gfx.txt;
//spec bar via cache enums 906 / 1739 (armor_1020_fix_enums.txt).
public class AssassinGreatsword implements Special {

	private static final int ASSASSIN_GREATSWORD = 31884;
	// 7644 with the forced right-hand item (the regular AGS) stripped, so the wielded model shows (as the Tempest godsword)
	private static final int SPEC_ANIM = 14457;
	private static final int SHADOW_SWIRL = 4429, SHADOW_BURST = 4430;

	@Override
	public boolean accept(ObjType def, String name) {
		return def.id == ASSASSIN_GREATSWORD;
	}

	@Override
	public boolean handle(Player player, Entity target, AttackStyle attackStyle, AttackType attackType, int maxDamage) {
		player.animate(SPEC_ANIM);
		player.graphics(SHADOW_SWIRL);
		player.publicSound(3869);
		boolean wounded = target.getHp() * 2 < target.getMaxHp();
		Hit hit = new Hit(player, attackStyle, attackType)
			.randDamage(maxDamage)
			.boostDamage(wounded ? 0.60 : 0.30)
			.boostAttack(1.25)
			.capDamagePvP(80, target);
		hit.afterPostDamage(t -> {
			if (!hit.isBlocked())
				t.graphics(SHADOW_BURST);
		});
		target.hit(hit);
		return true;
	}

	@Override
	public int getDrainAmount() {
		return 50;
	}
}
