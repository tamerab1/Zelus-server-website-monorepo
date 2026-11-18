package io.ruin.model.activities.moonsofperil.bosses;

import io.ruin.model.combat.Hit;
import io.ruin.model.entity.npc.NPC;
import io.ruin.model.entity.shared.listeners.HitListener;
import io.ruin.model.map.Position;
import io.ruin.model.map.object.GameObject;
import io.ruin.model.stat.StatType;

import java.util.*;

/**
 * Blood Moon (npc 13016, 500 HP, flat armour 2).
 * <ul>
 * <li>Vampiric standard attack: the first two hits heal her 1x and 2x their damage, the third 3x (30-50).</li>
 * <li>Blood Jaguar: eight jaguars ring the room; only the one behind Eyatlalli's glyph can be attacked, and hitting it
 * heals you 20% of the damage (20 per phase). Every 6 ticks a square of blood pools forms around the Moon: stand on a
 * pool too early and it bites, stay off the pools when the jaguars lunge and they bite. Either heals her 3x.</li>
 * <li>Blood Rain: she disappears and blood rains across the room, forming short-lived pools. Keep off them.</li>
 * </ul>
 */
public class BloodMoon extends MoonBoss {

	public static final int ID = 13016; // pmoon_boss_blood_moon (multivar -> 13011 vis)
	public static final int SPAWN_X = 1390, SPAWN_Y = 9630, ENTRY_X = 1395, ENTRY_Y = 9633;

	private static final int[][] SIGILS = { { 1393, 9635 }, { 1395, 9633 }, { 1395, 9630 }, { 1390, 9628 }, { 1388, 9630 },
		{ 1388, 9633 } };

	private static final int COMBO_ANIM = 11004, MAGIC_ANIM = 11000, DISAPPEAR_ANIM = 11003, APPEAR_ANIM = 11002;
	public static final int JAGUAR = 13021;
	private static final int JAGUAR_SPAWN_ANIM = 9094, JAGUAR_IDLE_ANIM = 9091, JAGUAR_ATTACK_ANIM = 12491, JAGUAR_DEATH_ANIM = 12492;
	private static final int POOL = 51046, POOL_SPAWN_ANIM = 10966, POOL_ATTACK_ANIM = 10969, POOL_DESPAWN_ANIM = 10968,
		RAIN_DESPAWN_ANIM = 10967;
	/* jaguar ring (dx, dy from the Moon's SW tile) + its moon-phase overlay */
	private static final int[][] JAGUAR_RING = { { 3, -4, 52980 }, { 7, 0, 52990 }, { 3, 7, 52988 }, { 7, 3, 52989 }, { -4, 3, 52986 },
		{ 0, 7, 52987 }, { 0, -4, 52982 }, { -4, 0, 52984 } };

	/* jaguar phase */
	private boolean jaguars;
	private int jaguarTicks, poolIn, lungeIn, despawnIn, healedThisPhase;
	private final List<NPC> pack = new ArrayList<>();
	private NPC glyphJaguar, jaguarGlyph;
	private final List<GameObject> ring = new ArrayList<>();
	private final Set<Position> ringTiles = new HashSet<>();
	private final List<GameObject> overlays = new ArrayList<>();

	/* blood rain */
	private boolean rain;
	private int rainTicks;
	private final Map<GameObject, Integer> rainPools = new HashMap<>(); // pool -> ticks left

	@Override
	protected int[][] sigils() {
		return SIGILS;
	}

	@Override
	protected int comboAnim() {
		return COMBO_ANIM;
	}

	@Override
	protected int flatArmour() {
		return -2; // wiki: flat armour -2, i.e. every hit lands 2 higher
	}

	@Override
	protected void onComboHit(int index, int damage) {
		if (damage <= 0)
			return;
		healMoon(index == 2 ? rand(30, 50) : damage * (index + 1)); // wiki: 1x, 2x, then 30-50 regardless of the damage
	}

	@Override
	protected void startSpecial(boolean a) {
		if (a)
			startJaguars();
		else
			startRain();
	}

	@Override
	protected boolean specialTick() {
		if (jaguars)
			return jaguarTick();
		if (rain)
			return rainTick();
		return false;
	}

	/* ------------------------------------------------------------------ blood jaguars */

	private int bx() {
		return SPAWN_X;
	}

	private int by() {
		return SPAWN_Y;
	}

	private void startJaguars() {
		jaguars = true;
		jaguarTicks = 30;
		poolIn = 5;
		lungeIn = -1;
		despawnIn = -1;
		healedThisPhase = 0;
		npc.animate(MAGIC_ANIM);
		lunges = 0;
		rings = 0;
		// wiki: shaping the jaguars from your blood deals up to 15 (and she heals from it)
		int drained = rand(0, 15);
		player.hit(new Hit(npc).fixedDamage(drained).ignorePrayer().ignoreDefence());
		healMoon(drained);
		int chosen = rand(0, JAGUAR_RING.length - 1);
		Position centre = pos(bx() + 2, by() + 2);
		for (int i = 0; i < JAGUAR_RING.length; i++) {
			int[] r = JAGUAR_RING[i];
			NPC jag = spawnAdd(JAGUAR, bx() + r[0], by() + r[1]);
			jag.animate(JAGUAR_SPAWN_ANIM);
			jag.face(centre);
			final boolean isGlyph = i == chosen;
			jag.hitListener = new HitListener().preDamage(h -> jaguarHit(isGlyph, h)).postDamage(h -> jag.incrementHp(h.damage));
			pack.add(jag);
			overlays.add(spawnObject(r[2], bx() + r[0], by() + r[1], 22, 0));
			if (isGlyph) {
				glyphJaguar = jag;
				// Eyatlalli's glyph, two tiles from the jaguar toward the Moon
				int gx = bx() + r[0] + Integer.signum(2 - r[0]) * 2, gy = by() + r[1] + Integer.signum(2 - r[1]) * 2;
				jaguarGlyph = spawnAdd(GLYPH, gx, gy);
				jaguarGlyph.animate(10981);
			}
		}
		npc.addEvent(e -> {
			e.delay(2);
			for (NPC j : pack)
				j.animate(JAGUAR_IDLE_ANIM);
		});
	}

	private void jaguarHit(boolean isGlyph, Hit hit) {
		if (hit.attacker == null || hit.attacker.player == null || !isGlyph) {
			hit.block();
			return;
		}
		int heal = Math.min(25 - healedThisPhase, hit.damage / 5); // wiki: up to 25 back per phase
		if (heal > 0) {
			healedThisPhase += heal;
			player.incrementHp(heal); // reclaim your blood
		}
	}

	private boolean jaguarTick() {
		// wiki: seven sets of blood pools, the jaguars attacking after the first six (the last set is pools only)
		if (--poolIn == 0 && rings < 7) {
			spawnRing();
			rings++;
			lungeIn = 3;
			poolIn = 6;
		}
		boolean onPool = ringTiles.contains(new Position(player.getAbsX(), player.getAbsY(), 0));
		if (lungeIn == 2 && onPool)
			bite(6, 2); // stepped on the pool too early: up to 6, heals her 2x
		if (lungeIn > 0 && --lungeIn == 0) {
			for (GameObject o : ring)
				o.animate(POOL_ATTACK_ANIM);
			if (lunges < 6) {
				for (NPC j : pack)
					j.animate(JAGUAR_ATTACK_ANIM);
				if (!onPool)
					bite(9, 3); // caught in the jaguars' cleave: up to 9, heals her 3x
				lunges++;
			}
			despawnIn = 2;
		}
		if (despawnIn > 0 && --despawnIn == 0)
			clearRing();
		if (rings >= 7 && lungeIn <= 0 && despawnIn <= 0) { // wiki: seven pool sets, the jaguars attack six times
			endJaguars();
			return false;
		}
		return true;
	}

	private void bite(int max, int healFactor) {
		int dmg = rand(0, max);
		player.hit(new Hit(npc).fixedDamage(dmg).ignorePrayer().ignoreDefence());
		healMoon(dmg * healFactor);
	}

	private int lunges, rings;

	private void spawnRing() {
		clearRing();
		for (int dx = -1; dx <= 5; dx++) {
			for (int dy = -1; dy <= 5; dy++) {
				if (dx != -1 && dx != 5 && dy != -1 && dy != 5)
					continue;
				GameObject pool = spawnObject(POOL, bx() + dx, by() + dy, 10, 0);
				pool.animate(POOL_SPAWN_ANIM);
				ring.add(pool);
				ringTiles.add(new Position(mx(bx() + dx), my(by() + dy), 0));
			}
		}
	}

	private void clearRing() {
		for (GameObject o : ring) {
			o.animate(POOL_DESPAWN_ANIM);
			removeObject(o);
		}
		ring.clear();
		ringTiles.clear();
	}

	private void endJaguars() {
		jaguars = false;
		clearRing();
		for (NPC j : pack)
			j.animate(JAGUAR_DEATH_ANIM);
		List<NPC> dying = new ArrayList<>(pack);
		pack.clear();
		glyphJaguar = null;
		npc.addEvent(e -> {
			e.delay(3);
			for (NPC j : dying)
				removeAdd(j);
		});
		for (GameObject o : overlays)
			removeObject(o);
		overlays.clear();
		removeAdd(jaguarGlyph); // the jaguar's glyph marker
		jaguarGlyph = null;
	}

	/* ------------------------------------------------------------------ blood rain */

	private void startRain() {
		rain = true;
		rainTicks = 19;
		npc.animate(DISAPPEAR_ANIM);
		npc.addEvent(e -> {
			e.delay(2);
			if (rain)
				hideMoon(true);
		});
	}

	private boolean hitByRain;

	private boolean rainTick() {
		// existing pools: armed from their second tick, then fade
		for (Iterator<Map.Entry<GameObject, Integer>> it = rainPools.entrySet().iterator(); it.hasNext();) {
			Map.Entry<GameObject, Integer> e = it.next();
			int left = e.getValue() - 1;
			GameObject pool = e.getKey();
			if (left <= 0) {
				removeObject(pool);
				it.remove();
				continue;
			}
			e.setValue(left);
			if (left == 2)
				pool.animate(RAIN_DESPAWN_ANIM);
			if (left < 8 && player.getAbsX() == pool.getPosition().getX() && player.getAbsY() == pool.getPosition().getY()) {
				int dmg = rand(0, 9); // wiki: up to 9, heals her 2x
				player.hit(new Hit(npc).fixedDamage(dmg).ignorePrayer().ignoreDefence());
				healMoon(dmg * 2);
				hitByRain = true;
			}
		}
		// wiki: keeping off the pools chips at her (NOTE: "minor" damage; 1 a tick here), never below 1 hp
		if (!hitByRain && npc.getHp() > 1)
			npc.hit(new Hit().fixedDamage(1));
		hitByRain = false;
		if (rainTicks > 3) {
			Set<Position> used = new HashSet<>();
			for (GameObject g : rainPools.keySet())
				used.add(g.getPosition());
			for (int i = 0; i < 14; i++) {
				int x = bx() + rand(-5, 9), y = by() + rand(-5, 9);
				boolean underMoon = x >= bx() && x < bx() + 5 && y >= by() && y < by() + 5;
				Position p = pos(x, y);
				if (underMoon || used.contains(p))
					continue;
				used.add(p);
				GameObject pool = spawnObject(POOL, x, y, 10, 0);
				pool.animate(POOL_SPAWN_ANIM);
				rainPools.put(pool, 8);
			}
		}
		if (--rainTicks <= 0) {
			endRain();
			return false;
		}
		return true;
	}

	private void endRain() {
		rain = false;
		for (GameObject g : new ArrayList<>(rainPools.keySet()))
			removeObject(g);
		rainPools.clear();
		hideMoon(false);
		npc.animate(APPEAR_ANIM);
	}

	@Override
	protected void cleanupSpecial() {
		if (jaguars)
			endJaguars();
		if (rain)
			endRain();
	}
}
