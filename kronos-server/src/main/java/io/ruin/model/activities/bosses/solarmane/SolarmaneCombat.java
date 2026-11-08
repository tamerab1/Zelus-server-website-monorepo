package io.ruin.model.activities.bosses.solarmane;

import io.ruin.api.utils.Random;
import io.ruin.model.World;
import io.ruin.model.combat.AttackStyle;
import io.ruin.model.combat.Hit;
import io.ruin.model.entity.npc.NPC;
import io.ruin.model.entity.npc.NPCCombat;
import io.ruin.model.entity.shared.listeners.DeathListener;
import io.ruin.model.entity.player.Player;
import io.ruin.model.map.Position;
import io.ruin.model.map.Region;
import io.ruin.model.map.Tile;
import io.ruin.model.map.dynamic.DynamicMap;
import io.ruin.model.map.route.routes.DumbRoute;

import java.util.ArrayList;
import java.util.List;

/**
 * Solarmane (npc 30611, 2026-10-31): the sun-lion, a re-textured Sabre-toothed kyatt that keeps the kyatt's animations
 * (5225-5231). An aggressive melee boss:
 * <ul>
 * <li>Auto attack: a fast bite (5228) every 4 ticks, slash, max {@link #BITE_MAX}.</li>
 * <li>Solar Pounce: every {@link #POUNCE_EVERY}th attack it leaps (5231) onto open ground beside its target, bursting
 * into a lava splash; the target and anyone next to the landing take up to {@link #POUNCE_MAX}, and anyone it lands on
 * is thrown clear.</li>
 * <li>Falling Sun: every {@link #ROAR_EVERY}th attack it roars (5229) and shadows (1447, Olm's falling-crystal shadow)
 * spread across the arena: one under every nearby player plus {@link #ROAR_EXTRA_SHADOWS} more on open ground. After
 * {@link #ROAR_SHADOW_TICKS} ticks a beam of sunfire (2698, Sol Heredit's) falls onto each shadow and strikes
 * {@link #ROAR_FUSE} ticks later for {@link #ROAR_MIN}-{@link #ROAR_MAX} to anyone standing on one, through prayer.</li>
 * <li>Pride's Call: at {@link #CUB_CALL_PERCENT}% hitpoints, once per boss, {@link #CUB_COUNT} Solarmane cubs (npc
 * {@link #CUB}) run in and heal it for {@link #CUB_HEAL} every {@link #CUB_HEAL_TICKS} ticks each until killed.</li>
 * </ul>
 * Stats: npcs/combat/solarmane.json. Arena, pet and command: {@link Solarmane}.
 */
public class SolarmaneCombat extends NPCCombat {

	static final int SEQ_BITE = 5228, SEQ_ROAR = 5229, SEQ_LEAP = 5231;
	// 2026-11-04: the pounce's lava splash (69) was a small orange bubble played under the lion's own body; the landing
	// is now a hydra-style ground shockwave and every player it tramples is hit by a fire-wave burst
	static final int GFX_POUNCE_SLAM = 1666, GFX_POUNCE_HIT = 157, GFX_SUNFIRE_BEAM = 2698;
	/** The leap must cover at least this many tiles, or it isn't a pounce (it bites instead). */
	static final int POUNCE_MIN_LEAP = 2;
	static final int BITE_MAX = 34, POUNCE_MAX = 42, ROAR_MIN = 33, ROAR_MAX = 48;
	static final int POUNCE_EVERY = 6, ROAR_EVERY = 9, ROAR_FUSE = 3, ROAR_RANGE = 10;
	static final int GFX_SKY_SHADOW = 1447, ROAR_EXTRA_SHADOWS = 22, ROAR_SHADOW_TICKS = 2;
	// 2026-11-04: the healer cubs, npc 30613 (cache stage 12): the pet's model with Attack and a combat level
	static final int CUB = 30613, CUB_COUNT = 3, CUB_CALL_PERCENT = 40, CUB_HEAL = 20, CUB_HEAL_TICKS = 2, CUB_SPAWN_GAP = 4, REACH_RANGE = 14;
	static final int SEQ_CUB_HEAL = 5228, GFX_HEAL = 444, GFX_CUB_ARRIVE = 1039;

	/** The arena, set by {@link Solarmane} so the cubs are torn down with it. */
	DynamicMap map;
	private int attacks;
	private boolean cubsCalled;

	@Override
	public void init() {
	}

	@Override
	public void process() {
		if (!cubsCalled && !isDead() && npc.getHp() > 0 && npc.getHp() <= npc.getMaxHp() * CUB_CALL_PERCENT / 100) {
			cubsCalled = true;
			callCubs();
		}
	}

	/** {@link #CUB_COUNT} cubs come in a few tiles out and run to the lion, healing it until they're killed. */
	private void callCubs() {
		mapReach();
		npc.animate(SEQ_ROAR);
		npc.forceText("To me, my pride!");
		for (Player p : npc.localPlayers())
			p.sendMessage("<col=7f0000>Solarmane calls its cubs! Kill them before they heal it.</col>");
		for (int i = 0; i < CUB_COUNT; i++) {
			Position at = cubSpawn(i);
			NPC cub = new NPC(CUB).spawn(at.getX(), at.getY(), at.getZ());
			cub.getCombat().setAllowRespawn(false);
			cub.walkBounds = null; // no random walk back to its spawn tile: it only walks to the lion
			cub.graphics(GFX_CUB_ARRIVE);
			if (map != null)
				map.addNpc(cub);
			cub.deathEndListener = (DeathListener.Simple) () -> {
				if (map != null)
					map.removeNpc(cub);
			};
			World.startEvent(e -> {
				int t = 0;
				while (!cub.isRemoved() && !cub.getCombat().isDead()) {
					if (npc.isRemoved() || isDead()) { // the lion is gone: its cubs flee
						if (map != null)
							map.removeNpc(cub);
						cub.remove();
						return;
					}
					cub.resetFreeze(); // nothing stops a cub: hits, freezes and binds are ignored on the way in
					cub.face(npc);
					boolean touching = gap(cub, npc) <= 1;
					if (!touching)
						walkTo(cub, npc);
					if (++t >= CUB_HEAL_TICKS && touching) {
						t = 0;
						cub.face(npc);
						cub.animate(SEQ_CUB_HEAL);
						npc.graphics(GFX_HEAL, 250, 0);
						npc.incrementHp(CUB_HEAL);
					}
					e.delay(1);
				}
			});
		}
	}

	/**
	 * Where cub {@code i} comes in: {@link #CUB_SPAWN_GAP} tiles clear of the lion's body on its north, east and west
	 * sides (south, then any open spot, if that side is walled), so the players have to run each one down.
	 */
	private Position cubSpawn(int i) {
		Position c = npc.getPosition();
		int s = npc.getSize(), cs = 2, mid = (s - cs) / 2;
		int[][] sides = { { mid, s + CUB_SPAWN_GAP }, { s + CUB_SPAWN_GAP, mid }, { -CUB_SPAWN_GAP - cs, mid },
				{ mid, -CUB_SPAWN_GAP - cs } }; // north, east, west, south
		for (int k = 0; k < sides.length; k++) {
			int[] d = sides[(i + k) % sides.length];
			for (int shrink = 0; shrink <= 2; shrink++) { // walled? try a tile or two closer
				int dx = d[0], dy = d[1];
				if (dy > s) dy -= shrink; else if (dy < 0) dy += shrink;
				if (dx > s) dx -= shrink; else if (dx < 0) dx += shrink;
				if (open(c.getX() + dx, c.getY() + dy, c.getZ(), cs))
					return new Position(c.getX() + dx, c.getY() + dy, c.getZ());
			}
		}
		Position any = openAround(3, 6, cs);
		return any != null ? any : c.relative(0, s);
	}

	/** Tiles between two bodies' edges (0 = overlapping, 1 = touching). */
	private static int gap(NPC a, NPC b) {
		return Math.max(axisGap(a.getAbsX(), a.getSize(), b.getAbsX(), b.getSize()),
				axisGap(a.getAbsY(), a.getSize(), b.getAbsY(), b.getSize()));
	}

	private static int axisGap(int a, int as, int b, int bs) {
		if (a + as - 1 < b)
			return b - (a + as - 1);
		if (a > b + bs - 1)
			return a - (b + bs - 1);
		return 0;
	}

	/**
	 * One step toward the nearest spot touching the lion (2026-11-06: DumbRoute.step(cub, lion, 1) never moved them:
	 * its withinDistance(1) is true from any distance when no route entity is set, so the cubs healed from where they
	 * spawned). Pathed with the route finder each tick; a dumb step (diagonal, then straight) if it finds nothing.
	 */
	private static void walkTo(NPC cub, NPC lion) {
		int cs = cub.getSize(), ls = lion.getSize();
		int dx = Math.max(lion.getAbsX() - cs, Math.min(cub.getAbsX(), lion.getAbsX() + ls));
		int dy = Math.max(lion.getAbsY() - cs, Math.min(cub.getAbsY(), lion.getAbsY() + ls));
		// a fresh path every tick: queued steps never clear on their own, and the step queue holds only 50
		cub.getMovement().reset();
		cub.getRouteFinder().routeAbsolute(dx, dy);
		if (!cub.getMovement().isAtDestination())
			return;
		if (DumbRoute.step(cub, dx, dy))
			return;
		if (dx != cub.getAbsX() && DumbRoute.step(cub, dx, cub.getAbsY()))
			return;
		if (dy != cub.getAbsY())
			DumbRoute.step(cub, cub.getAbsX(), dy);
	}

	/**
	 * The tiles a player can walk to from the lion (2026-11-06): a flood fill from its body through the map's clipping,
	 * {@link #REACH_RANGE} tiles out. Spawns, shadows and landings must be inside it: open() alone let a cub spawn on
	 * empty ground behind the arena wall.
	 */
	private java.util.Set<Long> reach = new java.util.HashSet<>();

	private void mapReach() {
		java.util.Set<Long> seen = new java.util.HashSet<>();
		java.util.ArrayDeque<int[]> queue = new java.util.ArrayDeque<>();
		int bx = npc.getAbsX(), by = npc.getAbsY(), z = npc.getHeight(), s = npc.getSize();
		for (int x = bx; x < bx + s; x++)
			for (int y = by; y < by + s; y++)
				if (seen.add(key(x, y)))
					queue.add(new int[] { x, y });
		io.ruin.model.map.Direction[] dirs = { io.ruin.model.map.Direction.NORTH, io.ruin.model.map.Direction.SOUTH,
				io.ruin.model.map.Direction.EAST, io.ruin.model.map.Direction.WEST };
		while (!queue.isEmpty()) {
			int[] c = queue.poll();
			for (io.ruin.model.map.Direction d : dirs) {
				int nx = c[0] + d.deltaX, ny = c[1] + d.deltaY;
				if (Math.max(Math.abs(nx - bx - s / 2), Math.abs(ny - by - s / 2)) > REACH_RANGE || seen.contains(key(nx, ny)))
					continue;
				if (!DumbRoute.allowEntrance(io.ruin.model.map.ClipUtils.REGULAR, c[0], c[1], z, 1, d))
					continue;
				seen.add(key(nx, ny));
				queue.add(new int[] { nx, ny });
			}
		}
		reach = seen;
	}

	private static long key(int x, int y) {
		return ((long) x << 32) | (y & 0xffffffffL);
	}

	/** Position has no equals(Object), so List.contains would compare identity. */
	private static boolean has(List<Position> marks, Position p) {
		for (Position m : marks)
			if (m.equals(p))
				return true;
		return false;
	}

	/** A random open spot for a body of {@code size}, {@code min}-{@code max} tiles out from the lion; null if none. */
	private Position openAround(int min, int max, int size) {
		Position c = npc.getPosition();
		for (int tries = 0; tries < 60; tries++) {
			int dx = Random.get(-max, max + npc.getSize() - 1), dy = Random.get(-max, max + npc.getSize() - 1);
			int d = Math.max(Math.max(-dx, dx - npc.getSize() + 1), Math.max(-dy, dy - npc.getSize() + 1));
			if (d < min)
				continue;
			if (open(c.getX() + dx, c.getY() + dy, c.getZ(), size))
				return new Position(c.getX() + dx, c.getY() + dy, c.getZ());
		}
		return null;
	}

	@Override
	public void follow() {
		follow(1);
	}

	@Override
	public boolean attack() {
		if (!(target instanceof Player player))
			return false;
		attacks++;
		if (attacks % ROAR_EVERY == 0) {
			roar();
			return true;
		}
		if (attacks % POUNCE_EVERY == 0 && pounce(player))
			return true;
		if (!withinDistance(1))
			return false;
		npc.face(player);
		npc.animate(SEQ_BITE);
		player.hit(new Hit(npc, AttackStyle.SLASH).randDamage(BITE_MAX).delay(1));
		return true;
	}

	/**
	 * Falling Sun: shadows under every player in range and on {@link #ROAR_EXTRA_SHADOWS} more open tiles, then a sunfire
	 * beam falls onto each one and strikes {@link #ROAR_FUSE} ticks later.
	 */
	private void roar() {
		mapReach();
		npc.animate(SEQ_ROAR);
		npc.forceText("The sun is falling!");
		List<Position> marks = new ArrayList<>();
		for (Player p : npc.localPlayers())
			if (p.getPosition().isWithinDistance(npc.getPosition(), ROAR_RANGE) && !has(marks, p.getPosition()))
				marks.add(p.getPosition().copy());
		int want = marks.size() + ROAR_EXTRA_SHADOWS;
		for (int tries = 0; marks.size() < want && tries < 200; tries++) {
			Position m = openAround(1, ROAR_RANGE, 1);
			if (m != null && !has(marks, m))
				marks.add(m);
		}
		for (Position m : marks)
			World.sendGraphics(GFX_SKY_SHADOW, 0, 0, m);
		World.startEvent(e -> {
			e.delay(1);
			for (Position m : marks) // refreshed so the shadow is still down when the beam arrives
				World.sendGraphics(GFX_SKY_SHADOW, 0, 0, m);
			e.delay(ROAR_SHADOW_TICKS - 1);
			if (npc.isRemoved() || npc.getCombat().isDead())
				return;
			for (Position m : marks)
				World.sendGraphics(GFX_SUNFIRE_BEAM, 0, 0, m); // the beam comes down and strikes on its own
			e.delay(ROAR_FUSE);
			if (npc.isRemoved() || npc.getCombat().isDead())
				return;
			for (Player p : npc.localPlayers())
				for (Position m : marks)
					if (p.getPosition().equals(m)) {
						p.hit(new Hit(npc).randDamage(ROAR_MIN, ROAR_MAX).ignoreDefence().ignorePrayer());
						break;
					}
		});
	}

	/**
	 * Leaps beside its target with a 30-cycle exact move onto open ground (never on top of them); the landing tramples
	 * the target and anyone next to it, and throws clear anyone it would land on. False if there's nowhere to land.
	 */
	private boolean pounce(Player player) {
		mapReach();
		Position land = landingBeside(player);
		if (land == null)
			return false;
		npc.face(player);
		npc.animate(SEQ_LEAP);
		npc.forceText("*Solarmane pounces!*");
		Position from = npc.getPosition().copy();
		io.ruin.model.activities.raids.tfa.Raid4Fx.slide(npc, land, 30,
				io.ruin.model.activities.raids.tfa.Raid4Fx.angle(from.getX(), from.getY(), land.getX(), land.getY()));
		World.startEvent(e -> {
			e.delay(1);
			if (npc.isRemoved() || npc.getCombat().isDead())
				return;
			int size = npc.getSize();
			World.sendGraphics(GFX_POUNCE_SLAM, 0, 0, land.relative(size / 2, size / 2));
			for (Player p : npc.localPlayers()) {
				int dx = p.getAbsX() - land.getX(), dy = p.getAbsY() - land.getY();
				boolean under = dx >= 0 && dx < size && dy >= 0 && dy < size;
				boolean beside = dx >= -1 && dx <= size && dy >= -1 && dy <= size;
				if (!beside)
					continue;
				if (under)
					knockBack(p, land, size);
				p.graphics(GFX_POUNCE_HIT);
				p.hit(new Hit(npc, AttackStyle.CRUSH).randDamage(POUNCE_MAX).delay(1));
			}
		});
		return true;
	}

	/**
	 * Open ground for the whole body touching the target's tile, FARTHEST from the lion, so it leaps over / past them.
	 * Null when the best spot is under {@link #POUNCE_MIN_LEAP} tiles away. It used to pick the NEAREST spot, which in
	 * melee was the lion's own tile: a zero-tile "pounce" with a splash under itself (2026-11-04).
	 */
	private Position landingBeside(Player player) {
		int size = npc.getSize();
		Position t = player.getPosition();
		Position best = null;
		int bestDist = -1;
		for (int x = t.getX() - size; x <= t.getX() + 1; x++)
			for (int y = t.getY() - size; y <= t.getY() + 1; y++) {
				boolean covers = t.getX() >= x && t.getX() < x + size && t.getY() >= y && t.getY() < y + size;
				if (covers || !open(x, y, t.getZ(), size))
					continue;
				int d = Math.max(Math.abs(x - npc.getAbsX()), Math.abs(y - npc.getAbsY()));
				if (d > bestDist) {
					bestDist = d;
					best = new Position(x, y, t.getZ());
				}
			}
		return bestDist >= POUNCE_MIN_LEAP ? best : null;
	}

	private boolean open(int x, int y, int z, int size) {
		for (int dx = 0; dx < size; dx++)
			for (int dy = 0; dy < size; dy++) {
				if (!reach.contains(key(x + dx, y + dy)))
					return false;
				Tile tile = Tile.get(x + dx, y + dy, z, false);
				if (Region.get(x + dx, y + dy) == null)
					return false;
				if (tile != null && !tile.isTileFreeCheckDecor()) // a null tile was never clipped: open ground
					return false;
			}
		return true;
	}

	/** Throws a player out from under the landing, away from its middle, as a stagger (not a teleport). */
	private void knockBack(Player p, Position land, int size) {
		int cx = land.getX() * 2 + size - 1, cy = land.getY() * 2 + size - 1; // doubled centre
		int sx = Integer.signum(p.getAbsX() * 2 - cx), sy = Integer.signum(p.getAbsY() * 2 - cy);
		if (sx == 0 && sy == 0)
			sx = Random.get(1) == 0 ? -1 : 1;
		for (int dist = size; dist >= 1; dist--) {
			Position to = p.getPosition().relative(sx * dist, sy * dist);
			if (open(to.getX(), to.getY(), to.getZ(), 1)) {
				p.getMovement().reset();
				io.ruin.model.activities.raids.tfa.Raid4Fx.slide(p, to, 30,
						io.ruin.model.activities.raids.tfa.Raid4Fx.angle(to.getX(), to.getY(), p.getAbsX(), p.getAbsY()));
				p.animate(424);
				return;
			}
		}
	}
}
