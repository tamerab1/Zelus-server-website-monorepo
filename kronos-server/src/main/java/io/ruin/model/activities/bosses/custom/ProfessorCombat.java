package io.ruin.model.activities.bosses.custom;

import io.ruin.model.World;
import io.ruin.model.combat.Hit;
import io.ruin.model.entity.player.Player;
import io.ruin.model.map.Position;
import io.ruin.model.map.Projectile;
import io.ruin.model.stat.StatType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Professor Malakor (npc 21944), 2026-11-08. <b>Unstable Concoction</b> every {@link #FLASKS_EVERY}th attack: shadows
 * under each player and on {@link #EXTRA_FLASKS} more tiles, then flasks smash onto each. Anyone on one takes up to
 * {@link #FLASK_MAX} through prayer and loses {@link #WEAKEN_PERCENT}% Attack, Strength and Defence.
 */
public class ProfessorCombat extends CustomBossCombat {

	static final int FLASKS_EVERY = 4, EXTRA_FLASKS = 10, FLASK_MAX = 34, WEAKEN_PERCENT = 12, RANGE = 9, SHADOW_TICKS = 2;
	static final int GFX_FLASK = 1044, GFX_SPLASH = 1045;

	private int attacks;

	@Override
	public boolean attack() {
		if (target != null && withinDistance(RANGE) && ++attacks % FLASKS_EVERY == 0) {
			concoction();
			return true;
		}
		return super.attack();
	}

	private void concoction() {
		npc.animate(info.attack_animation);
		faceTarget();
		npc.forceText("Let us test this batch!");
		List<Position> marked = new ArrayList<>();
		for (Player p : players())
			marked.add(p.getPosition().copy());
		List<Position> open = openTiles();
		Collections.shuffle(open);
		marked.addAll(open.subList(0, Math.min(EXTRA_FLASKS, open.size())));
		for (Position at : marked)
			World.sendGraphics(GFX_SKY_SHADOW, 0, 0, at);
		npc.addEvent(e -> {
			e.delay(SHADOW_TICKS);
			int flight = 0;
			for (Position at : marked)
				flight = Math.max(flight, new Projectile(GFX_FLASK, 70, 0, 0, 40, 5, 28, 64).send(npc, at));
			e.delay(Math.max(1, (flight + 15) / 30));
			if (isDead())
				return;
			for (Position at : marked)
				World.sendGraphics(GFX_SPLASH, 0, 0, at);
			for (Player p : players())
				for (Position at : marked)
					if (p.getPosition().equals(at.getX(), at.getY(), at.getZ())) {
						p.hit(new Hit(npc).randDamage(FLASK_MAX).ignoreDefence().ignorePrayer());
						for (StatType s : new StatType[] { StatType.Attack, StatType.Strength, StatType.Defence })
							p.getStats().get(s).drain(WEAKEN_PERCENT / 100D);
						p.sendMessage("<col=9b0000>The concoction saps your strength!</col>");
						break;
					}
		});
	}
}
