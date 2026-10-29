package io.ruin.model.activities.raids.tfa.rooms;

import io.ruin.api.utils.Random;
import io.ruin.model.activities.raids.tfa.Raid4Fx;
import io.ruin.model.activities.raids.tfa.Raid4RoomType;
import io.ruin.model.activities.raids.tfa.Raid4Scaling;
import io.ruin.model.activities.raids.tfa.bosses.Raid4Puppet;
import io.ruin.model.combat.AttackStyle;
import io.ruin.model.combat.Hit;
import io.ruin.model.combat.HitType;
import io.ruin.model.entity.npc.NPC;
import io.ruin.model.entity.player.Player;
import io.ruin.model.entity.shared.StepType;
import io.ruin.model.map.Direction;
import io.ruin.model.map.Position;
import io.ruin.model.map.object.GameObject;
import io.ruin.model.skills.prayer.Prayer;
import io.ruin.model.stat.Stat;
import io.ruin.model.stat.StatType;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.ruin.model.activities.raids.tfa.Raid4Ids.*;

/**
 * Slot 3 east: Dreadnoth the Jellied (region 16728), rebuilt 2026-10-28 from the RSProx captures of thirteen fights
 * (docs/rsprox-analysis/guthixian_archive/dreadnoth_plan.md). Section numbers (§) refer to that plan. Local
 * coordinates = absolute - (4160, 5632) (template square (65,88)). He is 5x5.
 */
@Slf4j
public class DreadnothRoom extends Raid4BossRoom {

	/* §1 */
	static final int SPAWN_X = 29, SPAWN_Y = 29; // (4189,5661) west, face (4181,5663)
	static final int ARRIVE_X = 12, ARRIVE_Y = 30; // (4172,5662)
	static final int LANDING_X = 25, LANDING_Y = 31; // eight tiles east of the transporter (4185,5663)
	static final int TRANSPORTER_SIDE_X = 16, TRANSPORTER_SIDE_Y = 31; // beside (4177,5663)
	static final int NAVIGATOR_X = 24, NAVIGATOR_Y = 31; // (4184,5663) facing east
	static final int SW_MIN = 24, SW_MAX = 34; // his south-west tile stays within x 4184..4194, z 5656..5666
	static final int FLOOR_MIN = 24, FLOOR_MAX = 38; // x 4184..4198, z 5656..5670
	static final int SIZE = 5, ENGAGE_RANGE = 4;

	/* §2-§6 */
	static final double[] PHASES = { 0.85, 0.65, 0.45 };
	static final double ENRAGE_AT = 0.25;
	static final int CRAWL_EVERY = 2, SWING_EVERY = 4, SLAM_GAP = 6, LEAP_EVERY = 8, JELLY_SWING_EVERY = 3;

	/* Damage (§ damage audits) */
	static final int SLAM_MIN = 22, SLAM_MAX = 44, SHOT_MAX = 33, MELEE_PRAYED = 30, MELEE_MAX = 45,
			LANDING_MIN = 16, LANDING_MAX = 38, CRUSH_MIN = 25, CRUSH_MAX = 44, PROJ2_MIN = 19, PROJ2_MAX = 41,
			JELLY_MIN = 5, JELLY_MAX = 10, BLOOD_MIN = 7, BLOOD_MAX = 8;

	/* Ids */
	static final int SEQ_JUMP = 14777, SEQ_MELEE = 14779, SEQ_LEAP = 14780, SEQ_JELLY_JUMP = 14774, SEQ_JELLY_SPAWN = 15162,
			SEQ_JELLY_ATTACK = 1586;
	static final int SPOT_ROCKFALL = 60, SPOT_JUMP_5X5 = 4209, SPOT_SPAWNING = 4210, SPOT_PROJ1 = 4211, SPOT_PROJ2 = 4212,
			SPOT_BLOOD_SPLAT = 1576;
	static final int LOC_BLOOD = 62609;

	private NPC boss;
	private Raid4Puppet body;
	private int maxHp;
	private Player target;
	private boolean dead, enraged, glubPending;
	private int phasesDone, landings;
	private long holdUntil, nextCrawl, nextSwing, nextLeap = Long.MAX_VALUE, frozenUntil;
	private boolean steppedThisTick;

	private final List<Jelly> jellies = new ArrayList<>();
	private final Set<Long> blobShown = new LinkedHashSet<>();
	/** Blood: tile key -> loc; the tick it went down (hurts from the next). */
	private final Map<Long, GameObject> blood = new HashMap<>();
	private final Map<Long, Long> bloodSince = new HashMap<>();
	/** Per raider: the tick their next blood hit is due while they start ticks on blood. */
	private final Map<Player, Long> bloodDue = new HashMap<>();
	private final Map<Player, Position> startTiles = new HashMap<>();
	private final Set<Player> hud = new HashSet<>();
	private int shownHp = -1;

	private final class Jelly {
		final NPC npc;
		final long hatch;
		Player target;
		long nextSwing;

		Jelly(NPC npc, long hatch) {
			this.npc = npc;
			this.hatch = hatch;
			this.nextSwing = hatch + 2;
		}
	}

	@Override
	public Raid4RoomType type() {
		return Raid4RoomType.DREADNOTH;
	}

	@Override
	protected void spawnFixtures() {
		boss = spawn(NPC_DREADNOTH, local(SPAWN_X, SPAWN_Y), Direction.WEST);
		body = (Raid4Puppet) boss.getCombat();
		body.bind(this);
		boss.face(local(21, 31));
	}

	@Override
	protected int[] arenaBounds() {
		return new int[] { FLOOR_MIN, FLOOR_MIN, FLOOR_MAX, FLOOR_MAX };
	}

	@Override
	protected int[] arenaEntry() {
		return new int[] { LANDING_X, LANDING_Y };
	}

	@Override
	protected int[] outside() {
		return new int[] { TRANSPORTER_SIDE_X, TRANSPORTER_SIDE_Y };
	}

	@Override
	protected int[] entry() {
		return new int[] { ARRIVE_X, ARRIVE_Y };
	}

	@Override
	protected int[] navigatorSpot() {
		return new int[] { NAVIGATOR_X, NAVIGATOR_Y };
	}

	@Override
	protected Direction navigatorFacing() {
		return Direction.EAST;
	}

	@Override
	protected String challengeName() {
		return "Dreadnoth the Jellied";
	}

	/* ---------------------------------------------------------------- engage (the landing tile is 4 from him: §1) */

	@Override
	protected void onStart(Player first) {
		maxHp = Raid4Scaling.tableHp(2100, scale);
		Stat hp = boss.getCombat().getStat(StatType.Hitpoints);
		hp.fixedLevel = maxHp;
		hp.currentLevel = maxHp;
		target = first;
		boss.face(first);
		long now = schedule.now();
		nextCrawl = now + 2;
		nextSwing = now + 1;
		frozenUntil = now + 5; // the engage counts as a thaw (immune 5 ticks)
		schedule.after(1, this::updateHud);
		log.info("Raid 4: Dreadnoth engaged by {} (scale {}, hp {})", first.getName(), scale, maxHp);
	}

	/* ---------------------------------------------------------------- per tick */

	@Override
	protected void onTick() {
		long now = schedule.now();
		startTiles.clear();
		for (Player p : fighters())
			startTiles.put(p, p.getPosition().copy());
		updateHud();
		tickBlood(now);
		tickBlob();
		tickJellies(now);
		if (dead)
			return;
		if (target == null || !fighters().contains(target))
			switchTarget(false);
		if (target == null)
			return;
		double frac = boss.getHp() / (double) maxHp;
		if (now >= holdUntil) {
			if (glubPending) {
				glubPending = false;
				enrage(now);
			}
			if (!enraged && phasesDone < PHASES.length && frac <= PHASES[phasesDone]) {
				slamPhase(++phasesDone, now);
				return;
			}
			if (!enraged && frac <= ENRAGE_AT) {
				enrage(now);
			}
		} else if (!enraged && frac <= ENRAGE_AT) {
			glubPending = true; // waits for the resume
		}
		if (now < holdUntil)
			return;
		if (enraged && now >= nextLeap) {
			leap(now);
			nextLeap = now + (Random.get(5) == 0 ? Random.get(9, 14) : LEAP_EVERY);
			return;
		}
		// about 1 in 64 chase ticks he turns to another raider
		if (!enraged && Random.get(63) == 0)
			switchTarget(false);
		steppedThisTick = false;
		if (!enraged)
			crawl(now);
		swing(now);
	}

	/* §2 crawl: one tile every 2 ticks on the rule */
	private void crawl(long now) {
		if (now < nextCrawl || now < frozenUntil || boss.isFrozen())
			return;
		nextCrawl = now + CRAWL_EVERY;
		Position sw = boss.getPosition();
		Position t = startTiles.getOrDefault(target, target.getPosition());
		int sx = lx(sw), sy = ly(sw), tx = lx(t), ty = ly(t);
		if (under(sw, SIZE, t)) { // his target under him: a cardinal step at random
			int[][] c = { { 0, 1 }, { 1, 0 }, { 0, -1 }, { -1, 0 } };
			int[] d = c[Random.get(3)];
			moveTo(sx + d[0], sy + d[1]);
			return;
		}
		boolean onSide = (tx >= sx && tx < sx + SIZE && (ty == sy - 1 || ty == sy + SIZE))
				|| (ty >= sy && ty < sy + SIZE && (tx == sx - 1 || tx == sx + SIZE));
		if (onSide)
			return;
		int dx = Integer.signum(tx - sx), dy = Integer.signum(ty - sy);
		// target diagonal to his box: step along x to put it on a side
		boolean diagonalToBox = (tx < sx || tx >= sx + SIZE) && (ty < sy || ty >= sy + SIZE)
				&& distance(sw, SIZE, t) == 1;
		if (diagonalToBox)
			dy = 0;
		int nx = sx + dx, ny = sy + dy;
		// never a step that puts his 5x5 over the target
		if (under(local(nx, ny), SIZE, t)) {
			if (!under(local(sx + dx, sy), SIZE, t) && dx != 0)
				ny = sy;
			else if (!under(local(sx, sy + dy), SIZE, t) && dy != 0)
				nx = sx;
			else
				return;
		}
		moveTo(nx, ny);
	}

	private void moveTo(int x, int y) {
		x = Math.max(SW_MIN, Math.min(SW_MAX, x));
		y = Math.max(SW_MIN, Math.min(SW_MAX, y));
		Position cur = boss.getPosition();
		int dx = x - lx(cur), dy = y - ly(cur);
		if (dx == 0 && dy == 0)
			return;
		boss.getMovement().reset();
		boss.step(dx, dy, StepType.CRAWL);
		steppedThisTick = true;
	}

	/* §2 the swing: every 4 ticks when any raider is within a tile of his 5x5, never on a step tick */
	private void swing(long now) {
		if (now < nextSwing || steppedThisTick)
			return;
		List<Player> near = new ArrayList<>();
		for (Player p : fighters()) {
			Position start = startTiles.getOrDefault(p, p.getPosition());
			if (distance(boss.getPosition(), SIZE, start) <= 1)
				near.add(p);
		}
		if (near.isEmpty())
			return;
		nextSwing = now + SWING_EVERY;
		boss.animate(SEQ_MELEE);
		for (Player p : near) {
			boolean prayed = p.getPrayer().isActive(Prayer.PROTECT_FROM_MELEE);
			int dmg = Random.get(25) < 4 ? 0 : Random.get(prayed ? MELEE_PRAYED : MELEE_MAX);
			hitPlayer(p, new Hit(boss, AttackStyle.CRUSH).fixedDamage(dmg).ignoreDefence().ignorePrayer());
		}
	}

	private void switchTarget(boolean announceTurn) {
		List<Player> pool = fighters();
		pool.removeIf(p -> !inArena(p.getPosition()));
		if (pool.size() > 1 && target != null)
			pool.remove(target);
		if (pool.isEmpty())
			return;
		target = pool.get(Random.get(pool.size() - 1));
		boss.face(target);
	}

	/* ---------------------------------------------------------------- §3 slam phases */

	private void slamPhase(int k, long j) {
		int raiders = Math.max(1, fighters().size());
		int total = Math.min(12, 3 * raiders * k);
		int per = Math.max(1, total / k);
		holdUntil = j + SLAM_GAP * (k - 1) + 10;
		boss.getMovement().reset();
		for (int i = 0; i < k; i++) {
			int quota = i == k - 1 ? total - per * (k - 1) : per;
			boolean last = i == k - 1;
			schedule.after(1 + SLAM_GAP * i, () -> slam(quota, last));
		}
		// a resume: half the time he turns to another raider
		schedule.after(SLAM_GAP * (k - 1) + 10, () -> {
			if (!dead && Random.get(1) == 0)
				switchTarget(true);
		});
	}

	private void slam(int quota, boolean last) {
		if (dead)
			return;
		boss.animate(SEQ_JUMP);
		schedule.after(1, () -> { // J+1
			if (dead)
				return;
			Position middle = boss.getPosition().relative(2, 2);
			Raid4Fx.sound(fighters(), 12361, middle, 0, 20);
			for (Player p : fighters()) {
				Raid4Fx.spot(p, 2, SPOT_ROCKFALL, 0, 0);
				int dmg = p.getPrayer().isActive(Prayer.PROTECT_FROM_MELEE) ? 0 : Random.get(SLAM_MIN, SLAM_MAX);
				hitPlayer(p, new Hit(boss).fixedDamage(dmg).ignoreDefence().ignorePrayer());
				for (int s = 0; s < 3; s++)
					shot(p, 10 + 2 * s, 90 + 30 * s, 3 + s);
			}
			bloodBurst(3);
		});
		schedule.after(4, () -> seedBlob(quota));
		if (last)
			schedule.after(8, this::hatch);
	}

	/** A proj01 at a raider: 0 under protect from missiles, else 1..33. */
	private void shot(Player p, int start, int end, int landTicks) {
		Raid4Fx.projectile(fighters(), boss, SPOT_PROJ1, boss.getPosition().relative(2, 2), p, p.getPosition(), 450, 30, start, end, 25, 200);
		schedule.after(landTicks, () -> {
			if (!fighters().contains(p))
				return;
			int dmg = p.getPrayer().isActive(Prayer.PROTECT_FROM_MISSILES) ? 0 : Random.get(1, SHOT_MAX);
			hitPlayer(p, new Hit(boss, AttackStyle.RANGED).fixedDamage(dmg).ignoreDefence().ignorePrayer());
		});
	}

	/** Up to {@code tries} blood tiles round his south-west tile, -1..5, on the floor, never over blood. */
	private void bloodBurst(int tries) {
		Position sw = boss.getPosition();
		for (int i = 0; i < tries; i++) {
			Position t = sw.relative(Random.get(-1, 5), Random.get(-1, 5));
			bloodTry(t);
		}
	}

	private void bloodTry(Position t) {
		int x = lx(t), y = ly(t);
		if (x < FLOOR_MIN || x > FLOOR_MAX || y < FLOOR_MIN || y > FLOOR_MAX)
			return;
		long k = key(t);
		if (blood.containsKey(k))
			return;
		blood.put(k, instance.spawnObject(LOC_BLOOD, t, 22, Random.get(3)));
		bloodSince.put(k, schedule.now());
		Raid4Fx.mapAnim(SPOT_BLOOD_SPLAT, t, 0, 0);
		Raid4Fx.sound(fighters(), 12387, t, 0, 10);
	}

	/* The blob: a seed on every raider's tile, three tiles a tick, until the slam's quota */

	private final List<int[]> blobQueue = new ArrayList<>(); // {x, y, seedX, seedY}
	private int blobQuota;

	private void seedBlob(int quota) {
		if (dead)
			return;
		Set<Long> seeds = new LinkedHashSet<>();
		for (Player p : fighters())
			seeds.add(key(p.getPosition()));
		blobQuota += quota;
		for (long s : seeds) {
			if (blobShown.size() >= blobQuota)
				break;
			Position t = new Position((int) (s >> 32), (int) s, boss.getPosition().getZ());
			if (blobShown.add(s)) {
				Raid4Fx.mapAnim(SPOT_SPAWNING, t, 0, 0);
				Raid4Fx.mapAnim(SPOT_BLOOD_SPLAT, t, 0, 0);
			}
			blobQueue.add(new int[] { t.getX(), t.getY() });
		}
	}

	private void tickBlob() {
		if (blobShown.isEmpty())
			return;
		int z = boss.getPosition().getZ();
		int grown = 0;
		List<int[]> seeds = new ArrayList<>(blobQueue);
		outer:
		for (int[] s : seeds) {
			for (int r = 1; r <= 3 && grown < 3; r++) {
				for (int dx = -r; dx <= r && grown < 3; dx++) {
					for (int dy = -r; dy <= r && grown < 3; dy++) {
						if (blobShown.size() >= blobQuota)
							break outer;
						Position t = new Position(s[0] + dx, s[1] + dy, z);
						long k = key(t);
						if (blobShown.contains(k) || !inArena(t))
							continue;
						blobShown.add(k);
						grown++;
						Position seed = new Position(s[0], s[1], z);
						Raid4Fx.projectile(fighters(), null, SPOT_SPAWNING, seed, null, t, 0, 0, 0, 30, 0, 0);
						Raid4Fx.mapAnim(SPOT_SPAWNING, t, 30, 0);
						Raid4Fx.mapAnim(SPOT_BLOOD_SPLAT, t, 30, 0);
					}
				}
			}
		}
		// every tile already shown is sent again until they hatch
		for (long k : blobShown)
			Raid4Fx.mapAnim(SPOT_SPAWNING, new Position((int) (k >> 32), (int) k, z), 0, 0);
	}

	private void hatch() {
		long now = schedule.now();
		int z = boss.getPosition().getZ();
		for (long k : blobShown) {
			NPC j = spawn(NPC_DREADNOTH_JELLY, new Position((int) (k >> 32), (int) k, z), Direction.SOUTH);
			((Raid4Puppet) j.getCombat()).bind(this);
			j.animate(SEQ_JELLY_SPAWN);
			spawned.add(j);
			jellies.add(new Jelly(j, now));
		}
		blobShown.clear();
		blobQueue.clear();
		blobQuota = 0;
	}

	/* §4 the jellies */

	private void tickJellies(long now) {
		for (Jelly j : new ArrayList<>(jellies)) {
			if (j.npc.isRemoved() || j.npc.getHp() <= 0)
				continue;
			if (now < j.hatch + 2)
				continue;
			Player near = nearest(j.npc.getPosition());
			if (near == null)
				continue;
			if (j.target == null || !fighters().contains(j.target)
					|| near.getPosition().distance(j.npc.getPosition()) < j.target.getPosition().distance(j.npc.getPosition())) {
				j.target = near;
				j.npc.face(near);
			}
			Position me = j.npc.getPosition(), t = j.target.getPosition();
			int dx = t.getX() - me.getX(), dy = t.getY() - me.getY();
			boolean side = Math.abs(dx) + Math.abs(dy) == 1;
			if (!side) {
				int sx = Integer.signum(dx), sy = Integer.signum(dy);
				if (dx == 0 && dy == 0) { // on their tile: step off
					int[][] c = { { 0, 1 }, { 1, 0 }, { 0, -1 }, { -1, 0 } };
					int[] d = c[Random.get(3)];
					sx = d[0];
					sy = d[1];
				} else if (Math.abs(dx) == 1 && Math.abs(dy) == 1) {
					sy = 0; // a diagonal: to a side
				}
				j.npc.getMovement().reset();
				j.npc.step(sx, sy, StepType.NORMAL);
				me = me.relative(sx, sy);
				side = Math.abs(t.getX() - me.getX()) + Math.abs(t.getY() - me.getY()) == 1;
			}
			if (side && now >= j.nextSwing) {
				j.nextSwing = now + JELLY_SWING_EVERY;
				j.npc.animate(SEQ_JELLY_ATTACK);
				Player victim = j.target;
				int dmg;
				if (victim.getPrayer().isActive(Prayer.PROTECT_FROM_MELEE)) {
					dmg = 0;
					victim.getStats().get(StatType.Prayer).drain((int) 4); // the wiki: drains prayer through protect (unverified)
				} else
					dmg = Random.get(27) < 8 ? 0 : Random.get(JELLY_MIN, JELLY_MAX);
				hitPlayer(victim, new Hit(j.npc, AttackStyle.CRUSH).fixedDamage(dmg).ignoreDefence().ignorePrayer());
			}
		}
	}

	private Player nearest(Position from) {
		Player best = null;
		int bd = Integer.MAX_VALUE;
		for (Player p : fighters()) {
			int d = p.getPosition().distance(from);
			if (d < bd) {
				bd = d;
				best = p;
			}
		}
		return best;
	}

	/* §5 blood: 7 or 8 every 2 ticks, from the first tick a raider starts on it (new blood hurts from the next) */

	private void tickBlood(long now) {
		for (Player p : fighters()) {
			Position start = startTiles.getOrDefault(p, p.getPosition());
			long k = key(start);
			Long since = bloodSince.get(k);
			boolean on = since != null && since < now;
			Long due = bloodDue.get(p);
			if (on) {
				if (due == null || now >= due) {
					hitPlayer(p, new Hit().randDamage(BLOOD_MIN, BLOOD_MAX).ignoreDefence().ignorePrayer());
					bloodDue.put(p, now + 2);
				}
			} else if (due != null) {
				if (now >= due) // once more the tick after stepping off when due
					hitPlayer(p, new Hit().randDamage(BLOOD_MIN, BLOOD_MAX).ignoreDefence().ignorePrayer());
				bloodDue.remove(p);
			}
		}
	}

	/* §6 the enrage */

	private void enrage(long now) {
		if (enraged)
			return;
		enraged = true;
		boss.forceText("*glub glub*");
		nextLeap = now + 5;
	}

	private void leap(long now) {
		boss.animate(SEQ_LEAP);
		Position t = startTiles.getOrDefault(target, target.getPosition());
		Position sw = boss.getPosition();
		int cx = lx(t) - 2, cy = ly(t) - 2; // centred on them
		int dx = cx - lx(sw), dy = cy - ly(sw);
		if (Math.abs(dx) > 5 || Math.abs(dy) > 5) { // exactly 5 tiles in the nearest of the eight directions
			double a = Math.atan2(dy, dx);
			int oct = (int) Math.round(a / (Math.PI / 4));
			int[][] dirs = { { 1, 0 }, { 1, 1 }, { 0, 1 }, { -1, 1 }, { -1, 0 }, { -1, -1 }, { 0, -1 }, { 1, -1 } };
			int[] d = dirs[((oct % 8) + 8) % 8];
			cx = lx(sw) + 5 * d[0];
			cy = ly(sw) + 5 * d[1];
		}
		cx = Math.max(SW_MIN, Math.min(SW_MAX, cx));
		cy = Math.max(SW_MIN, Math.min(SW_MAX, cy));
		Position to = local(cx, cy);
		int angle = Raid4Fx.angle(sw.getX(), sw.getY(), to.getX(), to.getY());
		Raid4Fx.slide(boss, to, 90, angle);
		Raid4Fx.mapAnim(SPOT_JUMP_5X5, to.relative(2, 2), 0, 0);
		landings++;
		boolean retarget = landings % 3 == 0;
		schedule.after(3, () -> land(retarget));
	}

	private void land(boolean retarget) {
		if (dead && boss.isRemoved())
			return;
		Position sw = boss.getPosition();
		Position middle = sw.relative(2, 2);
		Raid4Fx.sound(fighters(), 12361, middle, 0, 20);
		int healed = 0;
		for (Player p : fighters()) {
			Raid4Fx.spot(p, 2, SPOT_ROCKFALL, 0, 0);
			Position start = startTiles.getOrDefault(p, p.getPosition());
			if (under(sw, SIZE, start)) {
				int dmg = Random.get(CRUSH_MIN, CRUSH_MAX);
				hitPlayer(p, new Hit(boss, AttackStyle.CRUSH).fixedDamage(dmg).ignoreDefence().ignorePrayer());
				healed += dmg;
			} else {
				int dmg = p.getPrayer().isActive(Prayer.PROTECT_FROM_MELEE) ? 0 : Random.get(LANDING_MIN, LANDING_MAX);
				hitPlayer(p, new Hit(boss).fixedDamage(dmg).ignoreDefence().ignorePrayer());
			}
			shot(p, 10, 90, 3);
			Position tile = p.getPosition().copy();
			Raid4Fx.projectile(fighters(), boss, SPOT_PROJ2, middle, null, tile, 650, 60, 10, 180, 30, 0);
			Raid4Fx.sound(fighters(), 12362, tile, 0, 15);
			schedule.after(6, () -> {
				if (dead)
					return; // no proj02 lands after his death
				bloodTry(tile);
				for (Player q : fighters())
					if (q.getPosition().getX() == tile.getX() && q.getPosition().getY() == tile.getY())
						hitPlayer(q, new Hit(boss).randDamage(PROJ2_MIN, PROJ2_MAX).ignoreDefence().ignorePrayer());
			});
		}
		// jellies under him are crushed into him: deleted, he heals their health left
		for (Jelly j : new ArrayList<>(jellies)) {
			if (!j.npc.isRemoved() && under(sw, SIZE, j.npc.getPosition())) {
				healed += Math.max(0, j.npc.getHp());
				jellies.remove(j);
				removeNpc(j.npc);
			}
		}
		bloodBurst(5);
		if (healed > 0 && !dead)
			boss.hit(new Hit(HitType.HEAL).fixedDamage(Math.min(healed, maxHp - boss.getHp())));
		if (retarget)
			switchTarget(true);
	}

	/* ---------------------------------------------------------------- §7 death */

	@Override
	public void onNpcDying(NPC npc) {
		if (npc == boss && !dead) {
			dead = true;
			Position sw = boss.getPosition().copy();
			Position middle = sw.relative(2, 2);
			schedule.after(3, () -> { // K+3: seven jellies out of him, seven different tiles of his 5x5, never the middle
				List<int[]> tiles = new ArrayList<>();
				for (int x = 0; x < SIZE; x++)
					for (int y = 0; y < SIZE; y++)
						if (!(x == 2 && y == 2))
							tiles.add(new int[] { x, y });
				Collections.shuffle(tiles);
				long now = schedule.now();
				for (int i = 0; i < 7; i++) {
					int[] o = tiles.get(i);
					Position from = sw.relative(o[0], o[1]);
					NPC j = spawn(NPC_DREADNOTH_JELLY, from, Direction.NORTH);
					((Raid4Puppet) j.getCombat()).bind(this);
					spawned.add(j);
					j.animate(SEQ_JELLY_JUMP);
					Position to = middle.relative(2 * (o[0] - 2), 2 * (o[1] - 2));
					int fx = Math.max(FLOOR_MIN, Math.min(FLOOR_MAX, lx(to))), fy = Math.max(FLOOR_MIN, Math.min(FLOOR_MAX, ly(to)));
					Raid4Fx.slide(j, local(fx, fy), 28 + Random.get(2), 1024);
					jellies.add(new Jelly(j, now));
				}
			});
		} else if (npc.getId() == NPC_DREADNOTH_JELLY) {
			// a blood tile where it died, then one floor neighbour at random; both skipped on blood
			Position t = npc.getPosition().copy();
			bloodTry(t);
			int[][] n = { { 0, 1 }, { 1, 0 }, { 0, -1 }, { -1, 0 }, { 1, 1 }, { 1, -1 }, { -1, 1 }, { -1, -1 } };
			int[] d = n[Random.get(7)];
			if (Random.get(2) != 0)
				bloodTry(t.relative(d[0], d[1]));
		}
	}

	@Override
	public void onNpcDeath(NPC npc) {
		if (npc.getId() == NPC_DREADNOTH_JELLY) {
			jellies.removeIf(j -> j.npc == npc);
			spawned.remove(npc);
		}
		if (dead && jellies.isEmpty() && !complete && boss.isRemoved()) {
			schedule.after(3, () -> {
				if (!complete && jellies.isEmpty())
					win();
			});
		}
	}

	private void win() {
		finish("Dreadnoth and his jellies dead");
		for (GameObject obj : blood.values())
			instance.removeObject(obj);
		blood.clear();
		bloodSince.clear();
		schedule.after(8, () -> {
			for (Player p : hud)
				p.getHealthHud().close();
			hud.clear();
		});
	}

	private void updateHud() {
		if (!started || maxHp == 0)
			return;
		int hp = dead ? 0 : Math.max(0, boss.getHp());
		for (Player p : fighters())
			if (hud.add(p) || !p.getHealthHud().isOpened())
				p.getHealthHud().open(true, NPC_DREADNOTH, maxHp);
		if (hp != shownHp) {
			shownHp = hp;
			for (Player p : hud)
				p.getHealthHud().updateValue(hp);
		}
	}

	@Override
	protected void clearHazards() {
		super.clearHazards();
		for (Jelly j : jellies)
			removeNpc(j.npc);
		jellies.clear();
		for (GameObject obj : blood.values())
			instance.removeObject(obj);
		blood.clear();
		bloodSince.clear();
	}

	@Override
	public void cleanup() {
		super.cleanup();
		for (Player p : hud)
			p.getHealthHud().close();
		hud.clear();
	}
}
