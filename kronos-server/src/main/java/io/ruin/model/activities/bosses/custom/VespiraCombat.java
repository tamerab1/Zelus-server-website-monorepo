package io.ruin.model.activities.bosses.custom;

import io.ruin.model.World;
import io.ruin.model.combat.AttackStyle;
import io.ruin.model.combat.Hit;
import io.ruin.model.combat.HitType;
import io.ruin.model.entity.player.Player;
import io.ruin.model.map.Position;
import io.ruin.model.map.Projectile;
import io.ruin.model.map.object.GameObject;

/**
 * Vespira the Hive Queen (npc 21946), a Colosseum of Trials melee boss (2026-11-08). <b>Hive Venom</b> (every
 * {@link #SPIT_EVERY}th attack): she spits green poison at everyone in the arena from up to {@link #SPIT_RANGE} tiles,
 * Zulrah-style: the spit envenoms whoever it reaches, hit or miss, and leaves a toxic cloud (object 11700, 3x3) that
 * burns anyone in it for {@link #CLOUD_TICKS} ticks.
 */
public class VespiraCombat extends CustomBossCombat {

	static final int SPIT_EVERY = 4, SPIT_RANGE = 10, VENOM_DAMAGE = 6, CLOUD_TICKS = 25;
	static final int GFX_SPIT = 1044, CLOUD = 11700;

	private int attacks;

	@Override
	public boolean attack() {
		if (target == null)
			return false;
		if (withinDistance(SPIT_RANGE) && (attacks + 1) % SPIT_EVERY == 0) {
			attacks++;
			hiveVenom();
			return true;
		}
		if (withinDistance(1))
			attacks++;
		return super.attack();
	}

	private void hiveVenom() {
		npc.animate(info.attack_animation);
		faceTarget();
		npc.forceText("Drown in venom!");
		for (Player p : players())
			spit(p);
	}

	private void spit(Player victim) {
		Projectile spit = new Projectile(GFX_SPIT, 70, 31, 20, 56, 10, 16, 64);
		spit.tileOffset(npc.getSize() / 2);
		int delay = Math.max(1, (spit.send(npc, victim) + 15) / 30);
		victim.hit(new Hit(npc, AttackStyle.RANGED, null).randDamage(info.max_damage * 9 / 10).delay(delay));
		Position at = victim.getPosition().copy();
		World.startEvent(e -> {
			e.delay(delay);
			if (victim.isOnline() && !victim.getCombat().isDead())
				victim.envenom(VENOM_DAMAGE);
			if (npc.isRemoved() || isDead())
				return;
			int x = at.getX() - 1, y = at.getY() - 1;
			GameObject cloud = GameObject.spawn(CLOUD, x, y, at.getZ(), 10, 0);
			for (int t = 0; t < CLOUD_TICKS && !cloud.isRemoved() && !npc.isRemoved() && !isDead(); t++) {
				for (Player p : players())
					if (p.getAbsX() >= x && p.getAbsX() <= x + 2 && p.getAbsY() >= y && p.getAbsY() <= y + 2) {
						p.hit(new Hit(HitType.VENOM).randDamage(1, 4).ignoreDefence().ignorePrayer());
						p.envenom(VENOM_DAMAGE);
					}
				e.delay(1);
			}
			if (!cloud.isRemoved())
				cloud.remove();
		});
	}
}
