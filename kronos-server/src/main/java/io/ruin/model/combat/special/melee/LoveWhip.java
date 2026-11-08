package io.ruin.model.combat.special.melee;

import io.ruin.cache.ObjType;
import io.ruin.model.combat.AttackStyle;
import io.ruin.model.combat.AttackType;
import io.ruin.model.combat.Hit;
import io.ruin.model.combat.special.Special;
import io.ruin.model.entity.Entity;
import io.ruin.model.entity.player.Player;
import io.ruin.model.stat.StatType;

//Heartbreaker (50%): the Love whip (40015, model bank pack 2026-11-06). A lash with 30% more accuracy and 10% more damage
//that bursts into hearts (spotanim 1702, the Blow Kiss emote's): if it lands, the love flows back -- you heal for half
//the damage dealt (max 25) and restore 5 Prayer points -- and the target is lovestruck: a monster is stunned for its next
//attack (2 seconds), a player is held in place for 3 ticks. Spec bar: enum 906 / 1739 (love_whip_spec_enums.txt).
public class LoveWhip implements Special {

	private static final int ANIM_WHIP_SPEC = 1658, GFX_HEARTS = 1702, SOUND_LASH = 2713, HEAL_CAP = 25, PRAYER = 5;

	@Override
	public boolean accept(ObjType def, String name) {
		return name.equals("love whip");
	}

	@Override
	public boolean handle(Player player, Entity target, AttackStyle attackStyle, AttackType attackType, int maxDamage) {
		player.animate(ANIM_WHIP_SPEC);
		player.publicSound(SOUND_LASH);
		Hit hit = new Hit(player, attackStyle, attackType).randDamage(maxDamage).boostAttack(0.30).boostDamage(0.10);
		hit.postDamage(t -> {
			t.graphics(GFX_HEARTS, 96, 0);
			if (hit.damage <= 0)
				return;
			player.incrementHp(Math.min(HEAL_CAP, Math.max(1, hit.damage / 2)));
			player.getStats().get(StatType.Prayer).restore(PRAYER, 0);
			player.graphics(GFX_HEARTS, 96, 0);
			if (t.player != null) {
				t.root(3, true);
				t.player.sendMessage("<col=ff4fa3>You've been lovestruck and can't move!</col>");
			} else {
				t.stun(2, true);
			}
		});
		target.hit(hit);
		return true;
	}

	@Override
	public int getDrainAmount() {
		return 50;
	}
}
