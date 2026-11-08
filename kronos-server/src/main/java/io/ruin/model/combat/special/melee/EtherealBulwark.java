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

import java.util.concurrent.atomic.AtomicInteger;

//Phantom Rampart (50%): the Ethereal dinh's bulwark (40079, model bank pack 2026-11-06), an upgraded Dinh's bulwark. Its
//slam (Dinh's 7511 / 1336) hits with 25% more accuracy and 20% more damage -- in multi-combat it also strikes up to 11
//monsters within 5 tiles of the target, as Dinh's does -- and every hit that lands breaks that target's guard (Defence
//-10%). The wall gives back: you heal 10% of all the damage dealt (max 30). Spec bar: PatchSpecBars (enums 906 / 1739).
public class EtherealBulwark implements Special {

	private static final int ANIM_SLAM = 7511, GFX_SLAM = 1336, SOUND_SLAM = 3869, MAX_TARGETS = 11, HEAL_CAP = 30;
	private static final double DAMAGE = 0.20, ACCURACY = 0.25, DEFENCE_DRAIN = 0.10, HEAL = 0.10;

	@Override
	public boolean accept(ObjType def, String name) {
		return name.equals("ethereal dinh's bulwark");
	}

	@Override
	public boolean handle(Player player, Entity target, AttackStyle attackStyle, AttackType attackType, int maxDamage) {
		player.animate(ANIM_SLAM);
		player.graphics(GFX_SLAM);
		player.publicSound(SOUND_SLAM);
		AtomicInteger dealt = new AtomicInteger();
		if (target.inMulti() && target.isNpc()) {
			AtomicInteger hit = new AtomicInteger();
			target.forLocalEntity(e -> {
				if (e == target || e.player != null || hit.get() >= MAX_TARGETS)
					return;
				if (Misc.getDistance(e.getPosition(), target.getPosition()) > 5 || !player.getCombat().canAttack(e, false))
					return;
				e.hit(slam(player, attackStyle, attackType, maxDamage, dealt));
				hit.incrementAndGet();
			});
		}
		target.hit(slam(player, attackStyle, attackType, maxDamage, dealt));
		return true;
	}

	/** one slam: on a landed hit, the target's Defence drops and the wielder heals its share */
	private static Hit slam(Player player, AttackStyle style, AttackType type, int maxDamage, AtomicInteger dealt) {
		Hit hit = new Hit(player, style, type).randDamage(maxDamage).boostDamage(DAMAGE).boostAttack(ACCURACY);
		hit.postDamage(t -> {
			if (hit.damage <= 0)
				return;
			if (t.player != null)
				t.player.getStats().get(StatType.Defence).drain(DEFENCE_DRAIN);
			else if (t.npc != null)
				t.npc.getCombat().getStat(StatType.Defence).drain(DEFENCE_DRAIN);
			// heal the share of this hit, keeping the whole special under the cap
			int before = dealt.getAndAdd(hit.damage);
			int healed = Math.min(HEAL_CAP, (int) ((before + hit.damage) * HEAL)) - Math.min(HEAL_CAP, (int) (before * HEAL));
			if (healed > 0)
				player.incrementHp(healed);
		});
		return hit;
	}

	@Override
	public int getDrainAmount() {
		return 50;
	}
}
