package io.ruin.model.activities.bosses.custom;

import io.ruin.api.utils.Random;
import io.ruin.model.combat.AttackStyle;
import io.ruin.model.combat.Hit;
import io.ruin.model.entity.Entity;
import io.ruin.model.entity.player.Player;
import io.ruin.model.map.Position;
import io.ruin.model.map.Projectile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Terrax the Earth Titan (npc 21934), a Colosseum of Trials melee boss (2026-11-08). <b>Falling Fires</b> (every
 * {@link #FIRES_EVERY}th attack): shadows open under each player and on {@link #EXTRA_FIRES} tiles across the arena, then
 * fireballs fall onto every shadow (CustomBossCombat.skyBombs). About one normal attack in three is <b>Maw Fire</b>: a
 * magic blast of orange flame from his mouth, also how he reaches a player out of melee range.
 */
public class TerraxCombat extends CustomBossCombat {

	static final int FIRES_EVERY = 4, EXTRA_FIRES = 38, RANGE = 10, MAW_RANGE = 9, SHADOW_TICKS = 2;
	static final int GFX_MAW = 1465, GFX_MAW_HIT = 1466;

	private int attacks;

	@Override
	public boolean attack() {
		if (target == null)
			return false;
		if (withinDistance(RANGE) && (attacks + 1) % FIRES_EVERY == 0) {
			attacks++;
			fallingFires();
			return true;
		}
		boolean adjacent = withinDistance(1);
		if (adjacent)
			attacks++;
		if (withinDistance(MAW_RANGE) && (!adjacent || Random.rollDie(3))) {
			mawFire();
			return true;
		}
		return super.attack();
	}

	private void fallingFires() {
		npc.animate(info.attack_animation);
		faceTarget();
		npc.forceText("The sky burns!");
		List<Position> marked = new ArrayList<>();
		for (Player p : players())
			marked.add(p.getPosition().copy());
		List<Position> open = openTiles();
		Collections.shuffle(open);
		marked.addAll(open.subList(0, Math.min(EXTRA_FIRES, open.size())));
		skyBombs(marked, SHADOW_TICKS, 1, info.max_damage);
	}

	private void mawFire() {
		npc.animate(info.attack_animation);
		faceTarget();
		Entity victim = target;
		Projectile flame = new Projectile(GFX_MAW, 100, 31, 20, 40, 8, 14, 64);
		flame.tileOffset(npc.getSize() / 2);
		int delay = Math.max(1, (flame.send(npc, victim) + 15) / 30);
		Hit hit = new Hit(npc, AttackStyle.MAGIC, null).randDamage(info.max_damage * 9 / 10).delay(delay);
		hit.afterPostDamage(h -> victim.graphics(GFX_MAW_HIT, 92, 0));
		victim.hit(hit);
		hitPartnerToo(info.max_damage * 9 / 10);
	}
}
