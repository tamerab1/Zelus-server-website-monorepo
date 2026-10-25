package io.ruin.model.combat.special.melee;

import io.ruin.cache.ObjType;
import io.ruin.model.combat.AttackStyle;
import io.ruin.model.combat.AttackType;
import io.ruin.model.combat.Hit;
import io.ruin.model.combat.special.Special;
import io.ruin.model.entity.Entity;
import io.ruin.model.entity.npc.NPC;
import io.ruin.model.entity.player.Player;
import io.ruin.model.stat.StatType;

//Emerald Smash: Deal an attack with 25% more damage and 30% more accuracy. On a hit, lowers your
//target's Defence by 30% and envenoms them. (50%)
// Emerald Draconies Warhammer 31774 (2026-10-17). Spec bar: cache enums 906/1739, .dev/cache-restore-tool
// emerald_warhammer_spec_enums.txt.
public class EmeraldDraconiesWarhammer implements Special {

	private static final int WARHAMMER = 42574;
	private static final int ANIM_SMASH = 1378, GFX_SMASH = 1292, GFX_TOXIC_IMPACT = 752, SOUND_SMASH = 2541;
	private static final int VENOM_DAMAGE = 6;

	@Override
	public boolean accept(ObjType def, String name) {
		return def.id == WARHAMMER;
	}

	@Override
	public boolean handle(Player player, Entity target, AttackStyle attackStyle, AttackType attackType, int maxDamage) {
		player.animate(ANIM_SMASH);
		player.graphics(GFX_SMASH);
		player.publicSound(SOUND_SMASH);
		Hit hit = new Hit(player, attackStyle, attackType).randDamage(maxDamage).boostDamage(0.25).boostAttack(0.30);
		// the drain and venom only land with the blow, like the dragon warhammer's "on a successful hit"
		hit.postDamage(t -> {
			if (hit.damage <= 0)
				return;
			t.graphics(GFX_TOXIC_IMPACT);
			if (t.player != null)
				t.player.getStats().get(StatType.Defence).drain(0.30);
			else if (t.npc != null)
				t.npc.getCombat().getStat(StatType.Defence).drain(0.30);
			t.envenom(VENOM_DAMAGE);
		});
		int damage = target.hit(hit);
		if (target instanceof NPC)
			target.npc.addWeakenedDamage(player, damage);
		return true;
	}

	@Override
	public int getDrainAmount() {
		return 50;
	}
}
