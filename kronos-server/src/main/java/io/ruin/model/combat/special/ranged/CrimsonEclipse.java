package io.ruin.model.combat.special.ranged;

import io.ruin.cache.ObjType;
import io.ruin.model.World;
import io.ruin.model.combat.AttackStyle;
import io.ruin.model.combat.AttackType;
import io.ruin.model.combat.Hit;
import io.ruin.model.combat.special.Special;
import io.ruin.model.content.soulweapons.SoulTalents;
import io.ruin.model.entity.Entity;
import io.ruin.model.entity.npc.NPC;
import io.ruin.model.entity.player.Player;
import io.ruin.model.map.Position;
import io.ruin.model.map.Projectile;

//Crimson Eclipse: loose one arrow high into the sky; two ticks later it eclipses into four burning arrows that rain down
//on the target, each landing a guaranteed-accuracy 60% hit, and the impact scorches up to three monsters next to it for a
//quarter of the total (multi-combat) (55%) -- the Radiant Soulbow 42763 (tier 15, SoulWeapons, customs27 2026-10-27,
//restyled 2026-10-28 from a dark-bow-like volley). No ammo, like the bow itself. The bow's draw (426) with the burning
//dragon arrow's launch flash (1116) shooting straight up (1115), then the four fall from the sky around the target (1115,
//from 4 corners, high arc) and burst on it (the dark bow dragon impact 1100). PvM only; spec bar: enum 906 / 1739.
public class CrimsonEclipse implements Special {

	private static final int ANIM_DRAW = 426, GFX_LAUNCH = 1116, GFX_ARROW = 1115, GFX_BURST = 1100;
	private static final int RAIN = 4, RAIN_DELAY = 2, SCORCH_RANGE = 1, SCORCH_TARGETS = 3;
	private static final double RAIN_SHARE = 0.60, SCORCH_SHARE = 0.25;
	private static final int[][] CORNERS = { { -2, -2 }, { 2, -2 }, { -2, 2 }, { 2, 2 } };

	@Override
	public boolean accept(ObjType def, String name) {
		return def.id == 42763;
	}

	@Override
	public boolean handle(Player player, Entity target, AttackStyle style, AttackType type, int maxDamage) {
		if (target.player != null) {
			player.sendMessage("Crimson Eclipse can only be used against monsters.");
			return false; // no energy spent
		}
		player.animate(ANIM_DRAW);
		player.graphics(GFX_LAUNCH, 96, 0);
		// the eclipse arrow, straight up over the player's head
		Position up = player.getPosition().copy();
		new Projectile(GFX_ARROW, 40, 250, 20, 40, 0, 0, 0).send(player, up.getX(), up.getY());
		World.startEvent(e -> {
			e.delay(RAIN_DELAY);
			if (target.getCombat() == null || target.getCombat().isDead() || player.getCombat().isDead())
				return;
			Position t = target.getPosition().copy();
			Hit[] hits = new Hit[RAIN];
			for (int i = 0; i < RAIN; i++) {
				Position from = t.relative(CORNERS[i][0], CORNERS[i][1]);
				new Projectile(GFX_ARROW, 250, 31, 0, 30, 0, 20, 0).send(from, target);
				hits[i] = new Hit(player, style, type).randDamage((int) Math.max(1, maxDamage * RAIN_SHARE)).ignoreDefence().delay(1);
			}
			hits[RAIN - 1].postDamage(x -> {
				x.graphics(GFX_BURST, 60, 0);
				if (target.npc == null || !target.npc.inMulti())
					return;
				int sum = 0;
				for (Hit h : hits)
					sum += Math.max(0, h.damage);
				int scorch = Math.max(1, (int) (sum * SCORCH_SHARE));
				int n = 0;
				for (NPC other : SoulTalents.nearby(player, target.npc, SCORCH_RANGE)) {
					Hit s = new Hit(player, style, type).fixedDamage(scorch).ignoreDefence();
					s.soulEcho = true; // never triggers talents itself
					s.postDamage(o -> o.graphics(GFX_BURST, 60, 0));
					other.hit(s);
					if (++n >= SCORCH_TARGETS)
						break;
				}
			});
			target.hit(hits);
		});
		return true;
	}

	@Override
	public int getDrainAmount() {
		return 55;
	}
}
