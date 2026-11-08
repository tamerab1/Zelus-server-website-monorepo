package io.ruin.model.activities.bosses.custom;

import io.ruin.model.World;
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
 * Balarok the Horned Behemoth (npc 21945), the Colosseum of Trials' hardest fight (2026-11-07, harder 2026-11-08).
 * He attacks on sight and never stands idle: out of melee reach he hurls {@link #HELLBOLT_GFX hellbolts}. Melee up close
 * (his dual claw cleave), plus:
 * <ul>
 * <li><b>Worldbreaker</b> (every {@link #WORLDBREAKER_EVERY}th attack, his signature): shadows spread over the whole
 * arena except the tiles around him, then bombs fall on every one for {@link #WORLDBREAKER_MIN}-
 * {@link #WORLDBREAKER_MAX}, through defence and prayer. The only safe place is right beside him.</li>
 * <li><b>Hellfire Rain</b> (every {@link #RAIN_EVERY}th attack): shadows under each player and on {@link #RAIN_TILES}
 * tiles around them, then a bomb on each -- anyone still on one takes up to {@link #RAIN_MAX}, through defence and
 * prayer. Move!</li>
 * <li><b>Horned Charge</b> (every {@link #CHARGE_EVERY}th attack): he lowers his horns at the player furthest away and
 * charges {@link #CHARGE_DELAY} ticks later; whoever is still within a tile of the marked spot takes up to
 * {@link #CHARGE_MAX}.</li>
 * <li><b>Infernal Roar</b> (once at half hitpoints, once at a quarter): every player takes up to {@link #ROAR_MAX} and
 * their prayers are knocked off.</li>
 * <li><b>Enraged</b>: his swings hit {@link #ENRAGE_PERCENT}% harder under half his hitpoints,
 * {@link #FURY_PERCENT}% harder under a quarter.</li>
 * </ul>
 * Every special targets everyone in the arena (CustomBossCombat's {@link #arena}), or just his target outside one.
 */
public class BalarokCombat extends CustomBossCombat {

	static final int WORLDBREAKER_EVERY = 8, WORLDBREAKER_WINDUP = 3, WORLDBREAKER_MIN = 45, WORLDBREAKER_MAX = 75;
	static final int RAIN_EVERY = 3, RAIN_TILES = 8, RAIN_SPREAD = 3, RAIN_DELAY = 2, RAIN_MAX = 45;
	static final int CHARGE_EVERY = 5, CHARGE_DELAY = 2, CHARGE_MAX = 60;
	static final int ROAR_MAX = 35, ENRAGE_PERCENT = 20, FURY_PERCENT = 35;
	static final int HELLBOLT_RANGE = 12, HELLBOLT_GFX = 1465, HELLBOLT_HIT_GFX = 1466;
	static final int CHARGE_GFX = 1028, ROAR_GFX = 1039;

	private int attacks;
	private boolean roaredHalf, roaredQuarter;

	@Override
	public boolean attack() {
		if (target == null)
			return false;
		double hp = npc.getHp() / (double) npc.getMaxHp();
		if (!roaredHalf && hp <= 0.5) {
			roaredHalf = true;
			roar();
			return true;
		}
		if (!roaredQuarter && hp <= 0.25) {
			roaredQuarter = true;
			roar();
			return true;
		}
		attacks++;
		if (attacks % WORLDBREAKER_EVERY == 0) {
			worldbreaker();
			return true;
		}
		if (attacks % CHARGE_EVERY == 0) {
			charge();
			return true;
		}
		if (attacks % RAIN_EVERY == 0) {
			hellfireRain();
			return true;
		}
		int max = info.max_damage;
		if (hp <= 0.25)
			max = max * (100 + FURY_PERCENT) / 100;
		else if (hp <= 0.5)
			max = max * (100 + ENRAGE_PERCENT) / 100;
		if (!withinDistance(1)) {
			if (!withinDistance(HELLBOLT_RANGE))
				return false;
			hellbolt(max * 4 / 5);
			hitPartnerToo(max * 4 / 5);
			return true;
		}
		basicAttack(info.attack_animation, AttackStyle.SLASH, max);
		hitPartnerToo(max);
		return true;
	}

	/** Out of reach is no escape: a bolt of hellfire, on magic. */
	private void hellbolt(int max) {
		npc.animate(info.attack_animation);
		faceTarget();
		Entity victim = target;
		Projectile bolt = new Projectile(HELLBOLT_GFX, 88, 31, 22, 38, 8, 16, 64);
		bolt.tileOffset(npc.getSize() / 2);
		int arrives = bolt.send(npc, victim);
		Hit hit = new Hit(npc, AttackStyle.MAGIC, null).randDamage(max).delay(Math.max(1, (arrives + 15) / 30));
		hit.afterPostDamage(h -> victim.graphics(HELLBOLT_HIT_GFX, 124, 0));
		victim.hit(hit);
	}

	/** Shadows on every open tile except the ones around him, then bombs on all of them: only his side is safe. */
	private void worldbreaker() {
		npc.animate(info.attack_animation);
		npc.forceText("THE WORLD BREAKS!");
		npc.graphics(ROAR_GFX);
		for (Player p : players())
			p.sendMessage("<col=9b0000>The ground darkens around Balarok - get next to him!</col>");
		Position centre = npc.getCentrePosition();
		int safe = npc.getSize() / 2 + 2;
		List<Position> marked = new ArrayList<>();
		for (Position at : openTiles())
			if (at.distance(centre) > safe)
				marked.add(at);
		skyBombs(marked, WORLDBREAKER_WINDUP, WORLDBREAKER_MIN, WORLDBREAKER_MAX);
	}

	/** Shadows under each player and on open tiles around them, then a bomb on each. */
	private void hellfireRain() {
		npc.animate(info.attack_animation);
		npc.forceText("Burn beneath my hooves!");
		List<Position> marked = new ArrayList<>();
		List<Position> open = openTiles();
		for (Player p : players()) {
			marked.add(p.getPosition().copy());
			Collections.shuffle(open);
			int added = 0;
			for (Position at : open)
				if (added < RAIN_TILES && at.distance(p.getPosition()) <= RAIN_SPREAD && !at.equals(p.getPosition())) {
					marked.add(at);
					added++;
				}
		}
		skyBombs(marked, RAIN_DELAY, 1, RAIN_MAX);
	}

	private void charge() {
		Player victim = null;
		int far = -1;
		for (Player p : players()) {
			int d = p.getPosition().distance(npc.getCentrePosition());
			if (d > far) {
				far = d;
				victim = p;
			}
		}
		if (victim == null)
			return;
		Position at = victim.getPosition().copy();
		npc.face(victim);
		npc.forceText("None escape the Behemoth!");
		for (Position tile : openTiles())
			if (tile.distance(at) <= 1)
				World.sendGraphics(GFX_SKY_SHADOW, 0, 0, tile);
		victim.sendMessage("<col=9b0000>Balarok lowers his horns at you - move!</col>");
		npc.addEvent(e -> {
			e.delay(CHARGE_DELAY);
			npc.animate(info.attack_animation);
			World.sendGraphics(CHARGE_GFX, 0, 0, at);
			boolean hitAny = false;
			for (Player p : players())
				if (p.getPosition().distance(at) <= 1) {
					p.hit(new Hit(npc).randDamage(CHARGE_MAX).ignoreDefence().ignorePrayer());
					hitAny = true;
				}
			if (!hitAny)
				npc.forceText("Rraagh!");
		});
	}

	private void roar() {
		npc.animate(info.attack_animation);
		npc.forceText("KNEEL BEFORE BALAROK!");
		npc.graphics(ROAR_GFX);
		for (Player p : players()) {
			p.hit(new Hit(npc).randDamage(ROAR_MAX).ignoreDefence().ignorePrayer().delay(1));
			p.getPrayer().deactivateAll();
			p.sendMessage("<col=9b0000>Balarok's roar shatters your prayers!</col>");
		}
	}
}
