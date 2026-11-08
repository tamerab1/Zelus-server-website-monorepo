package io.ruin.model.combat.special.melee;

import io.ruin.cache.ObjType;
import io.ruin.model.combat.AttackStyle;
import io.ruin.model.combat.AttackType;
import io.ruin.model.combat.Hit;
import io.ruin.model.combat.special.Special;
import io.ruin.model.entity.Entity;
import io.ruin.model.entity.player.Player;
import io.ruin.model.stat.StatType;
import io.ruin.utility.Misc;

//Shatterquake (55%): the Ethereal elder maul (40077, model bank pack 2026-11-06), an upgraded Elder maul. The maul's
//crushing swing (7516) lands with 30% more accuracy and 25% more damage and, as Pulverize, cracks the target's Defence by
//35%. Two ticks later the ethereal aftershock follows (the earthquake burst, 1028): an echo of 40% of the blow that ignores
//Defence, and in multi-combat it also shakes every monster standing next to the target. Spec bar: PatchSpecBars.
public class EtherealElderMaul implements Special {

	private static final int ANIM_SWING = 7516, GFX_SMASH = 1292, GFX_QUAKE = 1028, SOUND_SMASH = 2541, AFTERSHOCK_DELAY = 2;
	private static final double ACCURACY = 0.30, DAMAGE = 0.25, DEFENCE_DRAIN = 0.35, ECHO = 0.40;

	@Override
	public boolean accept(ObjType def, String name) {
		return name.equals("ethereal elder maul");
	}

	@Override
	public boolean handle(Player player, Entity target, AttackStyle attackStyle, AttackType attackType, int maxDamage) {
		player.animate(ANIM_SWING);
		player.publicSound(SOUND_SMASH);
		target.graphics(GFX_SMASH, 0, 0);
		Hit hit = new Hit(player, AttackStyle.CRUSH, attackType).randDamage(maxDamage).boostAttack(ACCURACY).boostDamage(DAMAGE);
		hit.postDamage(t -> {
			if (hit.damage <= 0)
				return;
			if (t.player != null)
				t.player.getStats().get(StatType.Defence).drain(DEFENCE_DRAIN);
			else if (t.npc != null)
				t.npc.getCombat().getStat(StatType.Defence).drain(DEFENCE_DRAIN);
			int echo = Math.max(1, (int) (hit.damage * ECHO));
			t.hit(aftershock(player, attackType, echo));
			if (t.inMulti() && t.isNpc())
				t.forLocalEntity(e -> {
					if (e == t || e.player != null || Misc.getDistance(e.getPosition(), t.getPosition()) > 1
							|| !player.getCombat().canAttack(e, false))
						return;
					e.hit(aftershock(player, attackType, echo));
				});
		});
		target.hit(hit);
		return true;
	}

	private static Hit aftershock(Player player, AttackType attackType, int damage) {
		Hit h = new Hit(player, AttackStyle.CRUSH, attackType).fixedDamage(damage).ignoreDefence().delay(AFTERSHOCK_DELAY);
		h.preDamage(e -> e.graphics(GFX_QUAKE, 0, 0));
		return h;
	}

	@Override
	public int getDrainAmount() {
		return 55;
	}
}
