package io.ruin.model.activities.bosses.custom;

import io.ruin.model.World;
import io.ruin.model.combat.Hit;
import io.ruin.model.entity.player.Player;
import io.ruin.model.map.Position;

import java.util.ArrayList;
import java.util.List;

/**
 * Volcanus the Earthshaker (npc 21926), 2026-11-08. <b>Fault Lines</b> every {@link #FAULT_EVERY}th attack: shadows run
 * across the arena in a cross through him, then magma erupts along them; anyone on a line is burned through prayer.
 * Stepping off the lines is safe.
 */
public class VolcanusCombat extends CustomBossCombat {

	static final int FAULT_EVERY = 4, SHADOW_TICKS = 3, RANGE = 10, GFX_ERUPTION = 157, GFX_QUAKE = 1028;

	private int attacks;

	@Override
	public boolean attack() {
		if (target != null && withinDistance(RANGE) && ++attacks % FAULT_EVERY == 0) {
			faultLines();
			return true;
		}
		return super.attack();
	}

	private void faultLines() {
		npc.animate(info.attack_animation);
		npc.forceText("The earth splits!");
		Position c = npc.getCentrePosition();
		List<Position> lines = new ArrayList<>();
		for (Position at : openTiles())
			if (Math.abs(at.getX() - c.getX()) <= 1 || Math.abs(at.getY() - c.getY()) <= 1)
				lines.add(at);
		for (Position at : lines)
			World.sendGraphics(GFX_SKY_SHADOW, 0, 0, at);
		npc.addEvent(e -> {
			for (int t = 1; t <= SHADOW_TICKS; t++) {
				e.delay(1);
				if (t % 2 == 1 && t < SHADOW_TICKS)
					for (Position at : lines)
						World.sendGraphics(GFX_SKY_SHADOW, 0, 0, at);
			}
			if (isDead())
				return;
			npc.animate(info.attack_animation);
			World.sendGraphics(GFX_QUAKE, 0, 0, c);
			for (Position at : lines)
				World.sendGraphics(GFX_ERUPTION, 0, 0, at);
			for (Player p : players())
				for (Position at : lines)
					if (p.getPosition().equals(at.getX(), at.getY(), at.getZ())) {
						p.hit(new Hit(npc).randDamage(info.max_damage * 11 / 10).ignoreDefence().ignorePrayer());
						break;
					}
		});
	}
}
