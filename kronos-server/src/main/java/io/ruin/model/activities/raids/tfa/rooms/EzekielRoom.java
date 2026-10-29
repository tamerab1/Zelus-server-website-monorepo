package io.ruin.model.activities.raids.tfa.rooms;

import io.ruin.api.utils.Random;
import io.ruin.model.activities.raids.tfa.Raid4Controller;
import io.ruin.model.activities.raids.tfa.Raid4Fx;
import io.ruin.model.activities.raids.tfa.Raid4Instance;
import io.ruin.model.activities.raids.tfa.Raid4Room;
import io.ruin.model.activities.raids.tfa.Raid4RoomType;
import io.ruin.model.activities.raids.tfa.Raid4Scaling;
import io.ruin.model.activities.raids.tfa.Raid4Schedule;
import io.ruin.model.activities.raids.tfa.bosses.ezekiel.AnimatedSword;
import io.ruin.model.activities.raids.tfa.bosses.ezekiel.Ezekiel;
import io.ruin.model.combat.AttackStyle;
import io.ruin.model.combat.Hit;
import io.ruin.model.combat.HitType;
import io.ruin.model.entity.npc.NPC;
import io.ruin.model.entity.player.Player;
import io.ruin.model.map.Direction;
import io.ruin.model.map.Position;
import io.ruin.model.map.Tile;
import io.ruin.model.map.dynamic.DynamicMap;
import io.ruin.model.skills.prayer.Prayer;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.ruin.model.activities.raids.tfa.Raid4Ids.*;

/**
 * Slot 2 west: Ezekiel (region 16214), rebuilt 2026-10-28 from the RSProx captures of eight live lives
 * (docs/rsprox-analysis/guthixian_archive/ezekiel_plan.md, index 4300/4301/4304/4314/4315). Every tick, tile, seq,
 * spotanim, sound and line below is the recorded one; section numbers (§) refer to that plan. What the captures can't
 * show (the raiders' prayers, so exact damage under prayer) is marked "unverified".
 * <p>
 * Coordinates are local to the region (template square (63,86) = absolute - (4032, 5504)).
 */
@Slf4j
public class EzekielRoom implements Raid4Room {

	/* §1 the room */
	static final int ARENA_MIN = 27, ARENA_MAX = 35; // the 9x9 (4059..4067, 5531..5539)
	static final int SPAWN_X = 32, SPAWN_Y = 32; // (4064,5536), spawnangle east
	static final int WANDER_X = 30, WANDER_Y = 30, WANDER_RANGE = 3; // §2: 3 from the middle cell
	static final int LANDING_X = 34, LANDING_Y = 31; // the transporter's landing tile (4066,5535)
	static final int NAVIGATOR_X = 35, NAVIGATOR_Y = 31; // (4067,5535) facing west
	static final int CAGE_X = 31, CAGE_Y = 31; // the cage's centre (4063,5535)
	static final int LANE_LOW = 24, LANE_HIGH = 36; // the lanes' border lines x=4056/4068, z=5528/5540
	static final int FLIGHT = 12;

	/* §3 the clock */
	static final int MELEE_GAP = 5, SPECIAL_TO_MELEE = 7, ENGAGE_TO_MELEE = 6;
	static final int WEAKEN_ATTACK = 12, WEAKEN_RECOVER = 8, WEAKEN_HOLD = 10, HEAL_TO_MELEE = 5;
	static final double FINALE_AT = 0.335;
	static final int VOLLEY_EVERY = 11;

	/* Damage (prayers aren't on the wire: the prayed reductions are ours) */
	static final int MELEE_MAX = 28; // a third under protect from melee
	static final int MAGIC_MIN = 4, MAGIC_MAX = 21; // protect from magic blocks it (ours)
	static final int STAR_MIN = 20, STAR_MAX = 34;
	static final int SWORD_MIN = 27, SWORD_MAX = 31; // + the sword's charge
	static final int VOLLEY_MIN = 15, VOLLEY_MAX = 30;
	static final int BURN = 2, BURN_HITS = 3, BURN_GAP = 5;

	/* Ids (gamevals) */
	static final int SEQ_WALK = 14684, SEQ_MELEE = 14685, SEQ_MAGIC = 14686, SEQ_SPECIAL = 14687, SEQ_HEAL = 14688,
			SEQ_TO_WEAKEN = 14691, SEQ_WEAKEN = 14692, SEQ_WEAKEN_TO_IDLE = 14693, SEQ_FINALE = 14694,
			SEQ_FINALE_LOOP = 14696, SEQ_FINALE_DEATH = 14697;
	static final int SPOT_MAGIC = 4167, SPOT_MAGIC_IMPACT = 4168, SPOT_SPECIAL_CRACK = 4169, SPOT_STAR_SPAWN = 4170,
			SPOT_STAR_MARKER = 4171, SPOT_STAR_EXPLOSION = 4172, SPOT_HEAL_GLOW = 4173, SPOT_HEAL = 4174,
			SPOT_FINALE_CAST = 4175, SPOT_FINALE_GLOW = 4177, SPOT_FINALE_GLOW_LOOP = 4178, SPOT_WAVE_DUST = 4394,
			SPOT_TELE = 409, SPOT_IMPACT_MEDIUM = 4197, SPOT_IMPACT_SMALL = 4198;
	/** The V: front / left / right (vfx_ezekiel_sword_telegraph 01/03/04, finale sword drop/idle/despawn 01/03/04). */
	static final int[] V_TELEGRAPH = { 4182, 4190, 4194 }, V_DROP = { 14710, 14718, 14722 }, V_IDLE = { -1, 14721, 14725 },
			V_DESPAWN = { 14712, 14720, 14724 }, V_MODEL = { 62639, 62641, 62642 }, V_SOUND = { 12417, 12413, 12423 };
	static final int FENCE_DROP_01 = 14731;
	static final int HEADBAR_SWORD_CLOCK = 87, HEADBAR_SWORD_CHARGE = 42, HEADBAR_WEAKEN = 85;
	static final int SOUND_MAGIC = 12432, SOUND_SPECIAL = 12454, SOUND_STAR = 12441, SOUND_AGH = 7115,
			SOUND_WEAKEN = 12455, SOUND_WEAKEN_2 = 12398, SOUND_FINALE = 12407, SOUND_V = 12400, SOUND_VOLLEY = 12416;

	static final String[] ENGAGE_LINES = { "You choose to stand against me? Fall then.",
			"To stand against good is to be evil, regardless of reason.", "Your defence of this place ends with me." };
	static final String[] SPECIAL_LINES = { "Divine consequence!", "Divine judgement!", "Your evil plots end at me!" };

	private Raid4Controller controller;
	private Raid4Instance instance;
	private DynamicMap map;
	@Getter
	private final Raid4Schedule schedule = new Raid4Schedule();

	@Getter
	private NPC ezekiel;
	private Ezekiel boss;
	@Getter
	private int scale;
	private int maxHp;

	private boolean engaged, complete, finale, dying;
	private Player tank;
	private long engageTick = -1, nextAttack, holdUntil, nextWander;
	private int cycle; // 0, 1 = melee, 2 = special
	private int specials;
	private final List<Sword> swords = new ArrayList<>();
	private final List<NPC> props = new ArrayList<>();
	private final List<Tile> blocked = new ArrayList<>();
	private final Set<Player> hudShown = new HashSet<>();
	private int shownHp = -1;

	/* the finale */
	private int sideX, sideY; // unit vector from him to the cage
	private NPC[] vee;
	private int volleyIndex;

	@Override
	public Raid4RoomType type() {
		return Raid4RoomType.EZEKIEL;
	}

	@Override
	public void init(Raid4Controller controller, DynamicMap map) {
		this.controller = controller;
		this.instance = controller.getInstance();
		this.map = map;
		ezekiel = spawn(NPC_EZEKIEL, local(SPAWN_X, SPAWN_Y), Direction.EAST);
		ezekiel.getCombat().setAllowRespawn(false);
		boss = (Ezekiel) ezekiel.getCombat();
		scale = Raid4Scaling.scale(Math.max(1, controller.getParty().alive().size()));
		maxHp = Raid4Scaling.ezekielHp(scale);
		boss.bind(this, maxHp);
		nextWander = 4;
	}

	@Override
	public void start() {
	}

	/* ---------------------------------------------------------------- tick */

	@Override
	public void tick() {
		schedule.tick();
		// who stood still at the end of this tick, and where: next tick's dodges start from there (7). The room ticks
		// after the players' own movement, so a click filed on the window's tick has already taken its first step.
		idleLastTick.clear();
		stoodAt.clear();
		for (Player p : fighters())
			if (p.getMovement().isAtDestination()) {
				idleLastTick.add(p);
				stoodAt.put(p, p.getPosition().copy());
			}
		dodgeWindow.clear();
		if (complete || dying)
			return;
		long now = schedule.now();
		if (!engaged) {
			wander(now);
			for (Player p : fighters()) {
				if (lx(p) == LANDING_X && ly(p) == LANDING_Y) {
					engage(p);
					break;
				}
			}
			return;
		}
		updateHud();
		if (finale)
			return;
		if (tank == null || !fighters().contains(tank) || !inArena(tank.getPosition()))
			newTank();
		if (tank == null)
			return;
		boolean reachBefore = edgeOn(tank.getPosition());
		// §4b: at due+1 the reach is judged before the step: still out of reach -> the magic
		if (now == nextAttack + 1 && !reachBefore && now >= holdUntil) {
			magic();
			return;
		}
		if (now >= holdUntil)
			step();
		if (now >= nextAttack && edgeOn(tank.getPosition())) {
			if (cycle < 2)
				melee();
			else
				special();
		}
	}

	/* §2 before the engage: he wanders 3 from the middle cell */
	private void wander(long now) {
		if (now < nextWander)
			return;
		nextWander = now + 8 + Random.get(8);
		int x = WANDER_X - WANDER_RANGE + Random.get(WANDER_RANGE * 2), y = WANDER_Y - WANDER_RANGE + Random.get(WANDER_RANGE * 2);
		Position to = local(x, y);
		ezekiel.getRouteFinder().routeAbsolute(to.getX(), to.getY());
	}

	/* §2 the engage: the first raider on the landing tile is the tank */
	private void engage(Player first) {
		engaged = true;
		engageTick = schedule.now();
		tank = first;
		ezekiel.getMovement().reset();
		controller.challengeStarted("Ezekiel");
		log.info("Raid 4: Ezekiel engaged by {} (scale {}, hp {})", first.getName(), scale, maxHp);
		// ezekiel_plan: a party of three sees him at level 1,043 (level_change); one or two raiders, the cache's 984.
		// Other sizes were not recorded, so they keep the cache's level.
		if (controller.getParty().alive().size() == 3 && ezekiel.avatarExtended() != null)
			ezekiel.avatarExtended().setCombatLevelChange(1043);
		schedule.after(1, () -> {
			if (tank != null)
				ezekiel.face(tank);
			updateHud();
		});
		schedule.after(2, () -> ezekiel.forceText(ENGAGE_LINES[Random.get(ENGAGE_LINES.length - 1)]));
		nextAttack = engageTick + ENGAGE_TO_MELEE;
		cycle = 0;
	}

	/** The tank died or left: the raider nearest his 3x3 is the new one, faced at once with "Face me!" (3/3). */
	private void newTank() {
		Player best = null;
		int bestDist = Integer.MAX_VALUE;
		for (Player p : fighters()) {
			if (!inArena(p.getPosition()))
				continue;
			int d = distToBox(p.getPosition());
			if (d < bestDist) {
				bestDist = d;
				best = p;
			}
		}
		boolean had = tank != null;
		tank = best;
		if (tank != null) {
			ezekiel.face(tank);
			if (had)
				ezekiel.forceText("Face me!");
		}
	}

	/* §2b movement: one tile a tick toward the tank until they are edge-on; out from under him */
	private void step() {
		Position t = tank.getPosition();
		int sx = lx(ezekiel.getPosition()), sy = ly(ezekiel.getPosition());
		int tx = lx(t), ty = ly(t);
		int dx, dy;
		if (under(t)) {
			// step out: away from the tank's side of his 3x3
			dx = tx - (sx + 1) >= 0 ? -1 : 1;
			dy = ty - (sy + 1) >= 0 ? -1 : 1;
			if (tx == sx + 1)
				dx = 0;
			if (ty == sy + 1)
				dy = 0;
			if (dx == 0 && dy == 0)
				dx = 1;
			// a step that leaves the tank on the ring
		} else if (!edgeOn(t)) {
			dx = Integer.signum(tx < sx ? tx - sx : tx > sx + 2 ? tx - (sx + 2) : 0);
			dy = Integer.signum(ty < sy ? ty - sy : ty > sy + 2 ? ty - (sy + 2) : 0);
		} else {
			return;
		}
		int nx = clamp(sx + dx, ARENA_MIN, ARENA_MAX - 2), ny = clamp(sy + dy, ARENA_MIN, ARENA_MAX - 2);
		if (nx == sx && ny == sy)
			return;
		Position to = local(nx, ny);
		ezekiel.getMovement().reset();
		ezekiel.step(to.getX() - ezekiel.getPosition().getX(), to.getY() - ezekiel.getPosition().getY(),
				io.ruin.model.entity.shared.StepType.NORMAL);
	}

	/* §4 the melee: a cleave on the 16-tile ring but the three tiles opposite the tank, and under him */
	private void melee() {
		long now = schedule.now();
		ezekiel.animate(SEQ_MELEE);
		ezekiel.face(tank);
		int sx = lx(ezekiel.getPosition()), sy = ly(ezekiel.getPosition());
		int[] tr = { lx(tank) - sx, ly(tank) - sy };
		Set<Long> safe = safeTiles(tr[0], tr[1]);
		Set<Long> ring = new HashSet<>();
		for (int x = -1; x <= 3; x++) {
			for (int y = -1; y <= 3; y++) {
				if (x >= 0 && x <= 2 && y >= 0 && y <= 2)
					continue;
				if (safe.contains(key(x, y)))
					continue;
				ring.add(key(x, y));
				Raid4Fx.mapAnim(SPOT_WAVE_DUST, local(sx + x, sy + y), 5 + Random.get(2), 0);
			}
		}
		for (Player p : fighters()) {
			int rx = lx(p) - sx, ry = ly(p) - sy;
			if (ring.contains(key(rx, ry)) || under(p.getPosition())) {
				int max = p.getPrayer().isActive(Prayer.PROTECT_FROM_MELEE) ? MELEE_MAX / 3 : MELEE_MAX;
				hitPlayer(p, new Hit(ezekiel, AttackStyle.CRUSH).randDamage(max).ignoreDefence().ignorePrayer());
			}
		}
		cycle++;
		nextAttack = now + MELEE_GAP;
	}

	/** §4: mirror the tank's ring tile through his centre (2-x, 2-y), that tile and its two neighbours on the same side. */
	private static Set<Long> safeTiles(int x, int y) {
		int mx = 2 - x, my = 2 - y;
		Set<Long> out = new HashSet<>();
		if (mx == -1 || mx == 3) {
			for (int d = -1; d <= 1; d++)
				out.add(key(mx, my + d));
		} else {
			for (int d = -1; d <= 1; d++)
				out.add(key(mx + d, my));
		}
		return out;
	}

	/* §4b the magic: the tank out of reach a tick past an attack's due tick */
	private void magic() {
		long now = schedule.now();
		Position centre = local(lx(ezekiel.getPosition()) + 1, ly(ezekiel.getPosition()) + 1);
		Raid4Fx.sound(fighters(), SOUND_MAGIC, centre, 30, 7);
		ezekiel.animate(SEQ_MAGIC);
		Raid4Fx.spot(ezekiel, 0, SPOT_MAGIC);
		List<Player> targets = fighters();
		for (Player p : targets)
			Raid4Fx.spot(p, 2, SPOT_MAGIC_IMPACT, 30, 0);
		schedule.after(2, () -> {
			if (dying || complete)
				return;
			for (Player p : targets) {
				if (!fighters().contains(p))
					continue;
				int dmg = p.getPrayer().isActive(Prayer.PROTECT_FROM_MAGIC) ? 0 : Random.get(MAGIC_MIN, MAGIC_MAX);
				hitPlayer(p, new Hit(ezekiel, AttackStyle.MAGIC).fixedDamage(dmg).ignoreDefence().ignorePrayer());
			}
		});
		cycle = 0; // the melee count starts over
		nextAttack = now + MELEE_GAP;
	}

	/* ---------------------------------------------------------------- §5 the special: the star and the swords */

	private void special() {
		long now = schedule.now();
		int sx = lx(ezekiel.getPosition()), sy = ly(ezekiel.getPosition());
		ezekiel.animate(SEQ_SPECIAL);
		Raid4Fx.spot(ezekiel, 2, SPOT_SPECIAL_CRACK);
		Raid4Fx.resetFace(ezekiel);
		ezekiel.forceText(SPECIAL_LINES[Random.get(SPECIAL_LINES.length - 1)]);
		Position centre = local(sx + 1, sy + 1);
		Raid4Fx.sound(fighters(), SOUND_SPECIAL, centre, 0, 31);
		Raid4Fx.sound(fighters(), SOUND_STAR, centre, 90, 31);
		Raid4Fx.sound(fighters(), SOUND_STAR, centre, 150, 31);

		// the star: ring at d=34, arms three wide one out at 36, the arm's middle two out at 38, three out at 40
		Map<Long, Integer> star = new HashMap<>();
		for (int x = -1; x <= 3; x++)
			for (int y = -1; y <= 3; y++)
				if (!(x >= 0 && x <= 2 && y >= 0 && y <= 2))
					star.put(key(sx + x, sy + y), 34);
		for (int w = 0; w < 3; w++) {
			star.put(key(sx - 2, sy + w), 36);
			star.put(key(sx + 4, sy + w), 36);
			star.put(key(sx + w, sy - 2), 36);
			star.put(key(sx + w, sy + 4), 36);
		}
		for (int d = 3; d <= 4; d++) {
			int delay = d == 3 ? 38 : 40;
			star.put(key(sx - d, sy + 1), delay);
			star.put(key(sx + 2 + d, sy + 1), delay);
			star.put(key(sx + 1, sy - d), delay);
			star.put(key(sx + 1, sy + 2 + d), delay);
		}
		star.keySet().removeIf(k -> !inArenaLocal(kx(k), ky(k)));
		for (Map.Entry<Long, Integer> e : star.entrySet()) {
			Position tile = local(kx(e.getKey()), ky(e.getKey()));
			int d = e.getValue();
			Raid4Fx.mapAnim(SPOT_STAR_SPAWN, tile, d, 0);
			Raid4Fx.mapAnim(SPOT_STAR_MARKER, tile, d + 30, 0);
			Raid4Fx.mapAnim(SPOT_STAR_EXPLOSION, tile, d + 60, 0);
		}
		// two beats: S+3 the star tiles; S+5 the star tiles or under his 3x3 at S
		Map<Player, Position> before = new HashMap<>();
		schedule.after(2, () -> snapshot(before));
		schedule.after(3, () -> starBeat(star, before, -1, -1));
		Map<Player, Position> before2 = new HashMap<>();
		schedule.after(4, () -> snapshot(before2));
		schedule.after(5, () -> starBeat(star, before2, sx, sy));

		spawnSwords(now);
		specials++;
		holdUntil = now + 5; // rooted S..S+4 (0/139 steps)
		cycle = 0;
		nextAttack = now + SPECIAL_TO_MELEE;
	}

	private void snapshot(Map<Player, Position> into) {
		into.clear();
		for (Player p : fighters())
			into.put(p, p.getPosition().copy());
	}

	private void starBeat(Map<Long, Integer> star, Map<Player, Position> before, int underX, int underY) {
		if (dying || complete || finale)
			return;
		for (Map.Entry<Player, Position> e : before.entrySet()) {
			Player p = e.getKey();
			if (!fighters().contains(p))
				continue;
			int x = lx(e.getValue()), y = ly(e.getValue());
			boolean onStar = star.containsKey(key(x, y));
			boolean under = underX >= 0 && x >= underX && x <= underX + 2 && y >= underY && y <= underY + 2;
			if (onStar || under)
				hitPlayer(p, new Hit(ezekiel).randDamage(STAR_MIN, STAR_MAX).ignoreDefence().ignorePrayer());
		}
	}

	/* The swords */

	private final class Sword {
		final NPC npc;
		final int sx, sy, dx, dy; // start SW (local) and flight direction
		final long flyAt;
		final boolean cross; // a cross / two-rows sword: a miss heals him

		Sword(NPC npc, int sx, int sy, int dx, int dy, long flyAt, boolean cross) {
			this.npc = npc;
			this.sx = sx;
			this.sy = sy;
			this.dx = dx;
			this.dy = dy;
			this.flyAt = flyAt;
			this.cross = cross;
		}

		int charge() {
			return npc.getCombat() instanceof AnimatedSword s ? s.charge() : 0;
		}

		/** The 3-wide band from where it starts to where it lands. */
		boolean inBand(int x, int y) {
			int ex = sx + dx * FLIGHT, ey = sy + dy * FLIGHT;
			int minX = Math.min(sx, ex), maxX = Math.max(sx, ex) + 2, minY = Math.min(sy, ey), maxY = Math.max(sy, ey) + 2;
			return x >= minX && x <= maxX && y >= minY && y <= maxY;
		}

		/** §6: a column's x or a row's z equal to his south-west tile's. */
		boolean throughHim(int ex, int ey) {
			return dx == 0 ? sx == ex : sy == ey;
		}
	}

	/** One of five layouts (§5): the first special a cross on (27,30) or (33,33), else a cross 2/5, each line 1/5. */
	private void spawnSwords(long now) {
		// cross lanes {column x, row y}: any pair but the middle (30,30)
		int[][] lanes = { { 27, 30 }, { 33, 30 }, { 33, 27 }, { 33, 33 }, { 30, 27 }, { 30, 33 }, { 27, 33 }, { 27, 27 } };
		List<int[]> layout = new ArrayList<>(); // {sx, sy, dx, dy, order}
		boolean healOnMiss;
		int roll;
		int[] crossLane;
		if (specials == 0) { // the first special: the cross on (27,30) 4/8, on (33,33) 3/8, the two rows 1/8
			int r = Random.get(7);
			roll = r == 7 ? 4 : 0;
			crossLane = r < 4 ? lanes[0] : lanes[3];
		} else { // then a cross 2/5, each other layout 1/5
			int r = Random.get(4);
			roll = r <= 1 ? 0 : r == 2 ? 4 : r == 3 ? 6 : 8;
			crossLane = lanes[Random.get(lanes.length - 1)];
		}
		if (roll < 4) { // a cross: one column and one row, all four together
			int x = crossLane[0], y = crossLane[1];
			layout.add(new int[] { x, LANE_HIGH, 0, -1, 0 });
			layout.add(new int[] { x, LANE_LOW, 0, 1, 0 });
			layout.add(new int[] { LANE_LOW, y, 1, 0, 0 });
			layout.add(new int[] { LANE_HIGH, y, -1, 0, 0 });
			healOnMiss = true;
		} else if (roll < 6) { // two rows (z 5532 / 5536), a sword from each end, together
			for (int y : new int[] { 28, 32 }) {
				layout.add(new int[] { LANE_LOW, y, 1, 0, 0 });
				layout.add(new int[] { LANE_HIGH, y, -1, 0, 0 });
			}
			healOnMiss = true;
		} else if (roll < 8) { // three rows -> east, one a tick from the north
			layout.add(new int[] { LANE_LOW, 33, 1, 0, 0 });
			layout.add(new int[] { LANE_LOW, 30, 1, 0, 1 });
			layout.add(new int[] { LANE_LOW, 27, 1, 0, 2 });
			healOnMiss = false;
		} else { // three columns -> south, one a tick from the west
			layout.add(new int[] { 27, LANE_HIGH, 0, -1, 0 });
			layout.add(new int[] { 30, LANE_HIGH, 0, -1, 1 });
			layout.add(new int[] { 33, LANE_HIGH, 0, -1, 2 });
			healOnMiss = false;
		}
		List<Sword> volley = new ArrayList<>();
		for (int[] l : layout) {
			NPC npc = spawn(NPC_EZEKIEL_SWORD, local(l[0], l[1]), Direction.SOUTH);
			npc.getCombat().setAllowRespawn(false);
			((AnimatedSword) npc.getCombat()).bind(this);
			Raid4Fx.bodyModel(npc, 62645 + Random.get(3));
			Raid4Fx.fade(npc, 0, 30, 255, 0);
			int order = l[4];
			if (order == 0)
				Raid4Fx.headbar(npc, HEADBAR_SWORD_CLOCK, 60, 0, 390);
			else
				schedule.after(order, () -> Raid4Fx.headbar(npc, HEADBAR_SWORD_CLOCK, 60, 0, 390));
			Sword s = new Sword(npc, l[0], l[1], l[2], l[3], now + 14 + order, healOnMiss);
			swords.add(s);
			volley.add(s);
		}
		// flights: one per distinct tick; a cross / two rows that all miss heals him at fly+2
		Set<Long> ticks = new HashSet<>();
		for (Sword s : volley)
			ticks.add(s.flyAt);
		boolean[] anyThrough = { false };
		for (long t : ticks) {
			List<Sword> flying = new ArrayList<>();
			for (Sword s : volley)
				if (s.flyAt == t)
					flying.add(s);
			schedule.after((int) (t - now), () -> {
				if (fly(flying))
					anyThrough[0] = true;
			});
		}
		if (healOnMiss) {
			long fly = now + 14;
			schedule.after(16, () -> {
				if (!anyThrough[0] && !finale && !dying && !complete)
					healOnMiss(fly);
			});
		}
	}

	/** §5/§6/§6c a set of swords flies: bands judged, the ones through him weaken him and hit him at fly+1. */
	private boolean fly(List<Sword> flying) {
		if (finale || dying || complete)
			return false;
		long now = schedule.now();
		int ex = lx(ezekiel.getPosition()), ey = ly(ezekiel.getPosition());
		int bossDamage = 0;
		boolean through = false;
		Map<Player, Integer> bandHits = new HashMap<>();
		// 7: a raider in a sword's path on its flight tick may roll out; the roll lands before the band is judged
		for (Sword s : flying)
			for (Player p : fighters())
				if (s.inBand(lx(p), ly(p)))
					dodge(p);
		for (Sword s : flying) {
			int charge = s.charge();
			for (Player p : fighters()) {
				if (s.inBand(lx(p), ly(p)))
					bandHits.merge(p, Random.get(SWORD_MIN, SWORD_MAX) + charge, Integer::sum);
			}
			if (s.throughHim(ex, ey)) {
				through = true;
				bossDamage += charge + Random.get(12, 15);
			}
			Position to = local(s.sx + s.dx * FLIGHT, s.sy + s.dy * FLIGHT);
			int angle = Raid4Fx.angle(0, 0, s.dx, s.dy);
			Raid4Fx.removeHeadbar(s.npc, HEADBAR_SWORD_CHARGE);
			Raid4Fx.slide(s.npc, to, 30, angle);
			Raid4Fx.faceAngle(s.npc, angle, true);
			Raid4Fx.fade(s.npc, 30, 60, 0, 255);
			if (s.npc.avatar() != null)
				s.npc.avatarExtended().setAllOpsInvisible();
			Raid4Fx.sound(fighters(), 12458 + Random.get(3), local(ex + 1, ey + 1), 0, 10);
			NPC done = s.npc;
			swords.remove(s);
			schedule.after(2, () -> removeNpc(done));
		}
		for (Map.Entry<Player, Integer> e : bandHits.entrySet()) {
			Player p = e.getKey();
			hitPlayer(p, new Hit(ezekiel).fixedDamage(e.getValue()).ignoreDefence().ignorePrayer());
			for (int b = 1; b <= BURN_HITS; b++)
				schedule.after(b * BURN_GAP, () -> {
					if (fighters().contains(p))
						hitPlayer(p, new Hit(HitType.DAMAGE).fixedDamage(BURN).ignoreDefence().ignorePrayer());
				});
		}
		if (through) {
			int dmg = bossDamage;
			schedule.after(1, () -> {
				if (!dying && !complete && !ezekiel.isRemoved())
					ezekiel.hit(new Hit(HitType.DAMAGE_OTHER).fixedDamage(dmg).ignoreDefence().ignorePrayer());
			});
			weaken(now);
		}
		return through;
	}

	/* §6 weakened */
	private void weaken(long w) {
		ezekiel.animate(SEQ_TO_WEAKEN);
		Raid4Fx.stance(ezekiel, SEQ_WEAKEN);
		Raid4Fx.headbar(ezekiel, HEADBAR_WEAKEN, 0, 0, 1);
		ezekiel.forceText("Agh!");
		Position c = local(lx(ezekiel.getPosition()) + 1, ly(ezekiel.getPosition()) + 1);
		Raid4Fx.sound(fighters(), SOUND_AGH, c, 0, 10);
		Raid4Fx.sound(fighters(), SOUND_WEAKEN, c, 0, 20);
		schedule.after(1, () -> {
			Raid4Fx.resetFace(ezekiel);
			Raid4Fx.sound(fighters(), SOUND_WEAKEN_2, c, 0, 10);
		});
		schedule.after(WEAKEN_RECOVER, () -> {
			if (finale || dying || complete)
				return;
			ezekiel.animate(SEQ_WEAKEN_TO_IDLE);
			Raid4Fx.resetStance(ezekiel);
		});
		schedule.after(9, () -> {
			if (tank != null && !finale)
				ezekiel.face(tank);
		});
		holdUntil = w + WEAKEN_HOLD;
		nextAttack = w + WEAKEN_ATTACK;
		cycle = 0;
	}

	/* §6b a cross or the two rows all missed: he heals a tenth at fly+4 */
	private void healOnMiss(long fly) {
		ezekiel.animate(SEQ_HEAL);
		Raid4Fx.spot(ezekiel, 0, SPOT_HEAL_GLOW);
		Raid4Fx.spot(ezekiel, 2, SPOT_HEAL);
		ezekiel.forceText("Saradomin, give me strength.");
		Raid4Fx.headbar(ezekiel, HEADBAR_WEAKEN, 0, 0, 1);
		schedule.after(2, () -> {
			if (dying || complete || finale)
				return;
			int heal = Math.min(maxHp / 10, maxHp - ezekiel.getHp());
			if (heal > 0)
				ezekiel.hit(new Hit(HitType.HEAL).fixedDamage(heal));
		});
		long now = schedule.now();
		cycle = 0;
		nextAttack = now + HEAL_TO_MELEE;
	}

	/* ---------------------------------------------------------------- §8 the finale */

	/** From {@link Ezekiel}: his health changed. */
	public void onBossDamaged() {
		updateHud();
		if (!finale && !dying && engaged && ezekiel.getHp() > 0 && ezekiel.getHp() <= maxHp * FINALE_AT)
			startFinale();
	}

	private void startFinale() {
		finale = true;
		int sx = lx(ezekiel.getPosition()), sy = ly(ezekiel.getPosition());
		// the side he was nearest: the cage's middle cell is (30..32, 30..32)
		int ddx = (sx + 1) - CAGE_X, ddy = (sy + 1) - CAGE_Y;
		int cellX, cellY;
		if (Math.abs(ddx) > Math.abs(ddy)) {
			cellX = ddx > 0 ? 33 : 27;
			cellY = 30;
		} else {
			cellX = 30;
			cellY = ddy >= 0 ? 33 : 27;
		}
		sideX = Integer.signum(CAGE_X - (cellX + 1));
		sideY = Integer.signum(CAGE_Y - (cellY + 1));
		final int fx = cellX, fy = cellY;
		// F-1: the tile face toward his 3x3 to come, an empty [bas], his swords fade out
		ezekiel.getMovement().reset();
		Raid4Fx.resetStance(ezekiel);
		ezekiel.face(local(fx + 1, fy + 1));
		for (Sword s : swords)
			Raid4Fx.fade(s.npc, 0, 30, 0, 255);
		schedule.after(1, () -> { // F
			if (dying)
				return;
			for (Sword s : new ArrayList<>(swords))
				removeNpc(s.npc);
			swords.clear();
			Raid4Fx.slide(ezekiel, local(fx, fy), 60, 0);
			ezekiel.animate(SEQ_WALK);
			Raid4Fx.faceAngle(ezekiel, 0, false);
			Raid4Fx.sound(fighters(), 12458, local(fx + 1, fy + 1), 0, 10);
		});
		schedule.after(3, () -> ezekiel.face(local(CAGE_X, CAGE_Y))); // F+2
		schedule.after(4, () -> { // F+3
			if (dying)
				return;
			ezekiel.animate(SEQ_FINALE);
			Raid4Fx.spot(ezekiel, 0, SPOT_FINALE_CAST);
			Raid4Fx.spot(ezekiel, 1, SPOT_FINALE_GLOW);
			Raid4Fx.stance(ezekiel, SEQ_FINALE_LOOP);
		});
		schedule.after(6, () -> { // F+5
			if (dying)
				return;
			Raid4Fx.spot(ezekiel, 5, SPOT_FINALE_GLOW_LOOP, 0, 0, true);
			Raid4Fx.sound(fighters(), SOUND_FINALE, local(fx + 1, fy + 1), 0, 20);
			for (int x = fx; x < fx + 3; x++)
				for (int y = fy; y < fy + 3; y++)
					block(x, y);
		});
		schedule.after(7, () -> { // F+6
			if (dying)
				return;
			for (int x = ARENA_MIN; x <= ARENA_MAX; x++)
				for (int y = ARENA_MIN; y <= ARENA_MAX; y++)
					if (!inCage(x, y))
						block(x, y);
			for (Player p : fighters()) {
				if (!inCage(lx(p), ly(p))) {
					Position from = p.getPosition().copy();
					Position to = local(clamp(lx(p), CAGE_X - 1, CAGE_X + 1), clamp(ly(p), CAGE_Y - 1, CAGE_Y + 1));
					Raid4Fx.slide(p, to, 29, Raid4Fx.angle(from.getX(), from.getY(), to.getX(), to.getY()));
				}
			}
			// seven fence swords on the other cells, each with its cell's drop / idle
			int[][] cellNo = { { 6, 5, 1 }, { 7, -1, 2 }, { 1, 4, 3 } }; // [cx][cy]
			for (int cx = 0; cx < 3; cx++) {
				for (int cy = 0; cy < 3; cy++) {
					if (cx == 1 && cy == 1)
						continue;
					int x = ARENA_MIN + cx * 3, y = ARENA_MIN + cy * 3;
					if (x == fx && y == fy)
						continue;
					int n = cellNo[cx][cy];
					NPC fence = spawn(NPC_EZEKIEL_FENCE_SWORD, local(x, y), Direction.SOUTH);
					int drop = FENCE_DROP_01 + 2 * (n - 1);
					fence.animate(drop);
					Raid4Fx.stance(fence, drop + 1);
					props.add(fence);
				}
			}
		});
		schedule.after(9, () -> spawnVee(fx, fy)); // F+8
		schedule.after(13, () -> volley(true)); // F+12, then every 11
	}

	/** F+8 the V: the front on his edge tile facing the cage, two one back and to the sides. */
	private void spawnVee(int fx, int fy) {
		if (dying)
			return;
		int cx = fx + 1, cy = fy + 1;
		int leftX = -sideY, leftY = sideX;
		int[][] tiles = { { cx + sideX, cy + sideY }, { cx + leftX, cy + leftY }, { cx - leftX, cy - leftY } };
		int angle = Raid4Fx.angle(0, 0, sideX, sideY);
		vee = new NPC[3];
		for (int i = 0; i < 3; i++) {
			NPC v = spawn(NPC_EZEKIEL_FINALE_SWORD, local(tiles[i][0], tiles[i][1]), Direction.SOUTH);
			Raid4Fx.faceAngle(v, angle, true);
			Raid4Fx.bodyModel(v, V_MODEL[i]);
			v.animate(V_DROP[i]);
			if (V_IDLE[i] < 0)
				Raid4Fx.resetStance(v);
			else
				Raid4Fx.walkStance(v, V_IDLE[i]);
			Raid4Fx.sound(fighters(), SOUND_V, local(tiles[i][0], tiles[i][1]), 0, 15);
			vee[i] = v;
			props.add(v);
		}
	}

	/** The fixed cycle of six (§8): B l r / B r l / r B l / l B r / B r l / B l r. */
	private static final String[] VOLLEYS = { "Blr", "Brl", "rBl", "lBr", "Brl", "Blr" };

	private void volley(boolean first) {
		if (dying || complete || vee == null)
			return;
		if (first)
			volleyIndex = Random.get(1) == 0 ? 0 : 4;
		String order = VOLLEYS[volleyIndex % VOLLEYS.length];
		volleyIndex++;
		Position cage = local(CAGE_X, CAGE_Y);
		Position his = local(CAGE_X - 3 * sideX, CAGE_Y - 3 * sideY);
		for (int k = 0; k < 3; k++) {
			char c = order.charAt(k);
			boolean[] lit = c == 'B' ? new boolean[] { false, true, true } : c == 'l' ? new boolean[] { true, true, false }
					: new boolean[] { true, false, true };
			for (int i = 0; i < 3; i++) {
				if (lit[i]) {
					Raid4Fx.spot(vee[i], k, V_TELEGRAPH[i], 30 * k, 0, false);
					Raid4Fx.sound(fighters(), V_SOUND[i], vee[i].getPosition(), 30 * k, 15);
				}
			}
			Set<Long> safe = volleySafe(c);
			List<Position> unsafe = new ArrayList<>();
			for (int row = 0; row < 3; row++) {
				for (int col = -1; col <= 1; col++) {
					if (safe.contains(key(row, col)))
						continue;
					Position t = cageTile(row, col);
					unsafe.add(t);
					Raid4Fx.mapAnim(Random.get(1) == 0 ? SPOT_IMPACT_MEDIUM : SPOT_IMPACT_SMALL, t, 124 + 30 * k + 2 * row, 0);
				}
			}
			Raid4Fx.sound(fighters(), SOUND_FINALE, cage, 120 + 30 * k, 20);
			final List<Position> hitTiles = unsafe;
			schedule.after(4 + k, () -> {
				if (dying || complete)
					return;
				for (Player p : fighters())
					if (inCage(lx(p), ly(p)))
						dodge(p); // the cage's window: the three strikes of each volley
				for (Player p : fighters()) {
					for (Position t : hitTiles) {
						if (p.getPosition().getX() == t.getX() && p.getPosition().getY() == t.getY()) {
							hitPlayer(p, new Hit(ezekiel, AttackStyle.MAGIC).randDamage(VOLLEY_MIN, VOLLEY_MAX).ignoreDefence().ignorePrayer());
							break;
						}
					}
				}
			});
		}
		Raid4Fx.sound(fighters(), SOUND_VOLLEY, his, 225, 15);
		if (!first)
			Raid4Fx.spot(ezekiel, 0, SPOT_FINALE_CAST, 225, 0, false);
		schedule.after(VOLLEY_EVERY, () -> volley(false));
	}

	/** Safe tiles as {row from his side, column (-1 left .. 1 right)}: both lit -> far row; front + one side -> the far
	 * two tiles of the other side's column. */
	private static Set<Long> volleySafe(char c) {
		Set<Long> safe = new HashSet<>();
		if (c == 'B') {
			for (int col = -1; col <= 1; col++)
				safe.add(key(2, col));
		} else {
			int col = c == 'l' ? 1 : -1; // left lit -> the right column is safe
			safe.add(key(1, col));
			safe.add(key(2, col));
		}
		return safe;
	}

	/** The cage tile {@code row} rows from his side, {@code col} to his left (-1) or right (+1). */
	private Position cageTile(int row, int col) {
		int leftX = -sideY, leftY = sideX;
		int x = CAGE_X + sideX * (row - 1) - leftX * col;
		int y = CAGE_Y + sideY * (row - 1) - leftY * col;
		return local(x, y);
	}

	/* ---------------------------------------------------------------- §9 death */

	/** K: his health reached 0 (death animation running; {@link #onNpcDeath} at K+11). */
	public void onDying() {
		if (dying)
			return;
		dying = true;
		Position tile = ezekiel.getPosition().copy();
		log.info("Raid 4: Ezekiel died at {}", tile);
		schedule.after(1, () -> { // K+1
			for (int slot : new int[] { 0, 5, 1, 6 })
				Raid4Fx.clearSpot(ezekiel, slot);
			// npc_ezekiel_finale_death01 is his death animation (Raid4Npcs)
			ezekiel.forceText("...Formidable.");
			Raid4Fx.fade(ezekiel, 210, 270, 0, 255);
			if (ezekiel.avatar() != null)
				ezekiel.avatarExtended().setAllOpsInvisible();
			if (vee != null)
				for (int i = 0; i < 3; i++)
					if (vee[i] != null)
						vee[i].animate(V_DESPAWN[i]);
			NPC death = spawn(NPC_EZEKIEL_DEATH_SWORD, tile, Direction.SOUTH);
			Raid4Fx.faceAngle(death, Raid4Fx.angle(0, 0, sideX, sideY), true);
			Raid4Fx.bodyModel(death, -1);
			props.add(death);
			schedule.after(7, () -> Raid4Fx.resetBody(death)); // K+8
		});
		schedule.after(2, () -> { // K+2
			if (vee != null)
				for (NPC v : vee)
					removeNpc(v);
			vee = null;
		});
		for (Sword s : new ArrayList<>(swords))
			removeNpc(s.npc);
		swords.clear();
	}

	@Override
	public void onNpcDeath(NPC npc) {
		if (npc != ezekiel || complete)
			return;
		complete = true; // K+11
		if (!dying)
			onDying();
		controller.challengeComplete("Ezekiel");
		controller.roomComplete(this);
		for (NPC prop : props)
			if (prop.getId() == NPC_EZEKIEL_FENCE_SWORD)
				Raid4Fx.fade(prop, 0, 30, 0, 255);
		unblockAll();
		schedule.after(1, () -> { // K+12: the fence goes
			for (NPC prop : new ArrayList<>(props))
				if (prop.getId() == NPC_EZEKIEL_FENCE_SWORD) {
					removeNpc(prop);
					props.remove(prop);
				}
		});
		schedule.after(3, () -> props.add(spawn(NPC_ALLIANCE_NAVIGATOR, local(NAVIGATOR_X, NAVIGATOR_Y), Direction.WEST))); // K+14
		schedule.after(8, () -> { // K+19
			for (Player p : new ArrayList<>(hudShown))
				p.getHealthHud().close();
			hudShown.clear();
		});
	}

	/* ---------------------------------------------------------------- 7 the raiders' dodge */

	static final int SEQ_ROLL = 14705, SEQ_STRAFE = 14706;
	private final Set<Player> idleLastTick = new HashSet<>();
	private final Map<Player, Position> stoodAt = new HashMap<>();
	private final Set<Player> dodgeWindow = new HashSet<>();

	/**
	 * A walk click handled inside a dodge window: the raider takes up to three steps of the click's route at once, as
	 * one teleport with an exact move: tfa_ezekiel_roll over 15 cycles for two or three tiles (angle the way it goes),
	 * tfa_ezekiel_strafe over 8 for one (angle the other way, plus 4: a hop backwards). The rest of the route walks on.
	 */
	private void dodge(Player p) {
		if (!dodgeWindow.add(p) || !idleLastTick.contains(p))
			return;
		Position from = stoodAt.get(p);
		if (from == null)
			return;
		var m = p.getMovement();
		// the click's destination: the route's last waypoint (the walk may already have finished this tick)
		int wx, wy;
		if (m.writeOffset > 0) {
			wx = m.getStepsX()[m.writeOffset - 1];
			wy = m.getStepsY()[m.writeOffset - 1];
		} else {
			wx = p.getPosition().getX();
			wy = p.getPosition().getY();
		}
		boolean moved = !p.getPosition().equals(from);
		if (!moved && m.isAtDestination())
			return; // no click this tick
		int x = from.getX(), y = from.getY(), steps = 0;
		while (steps < 3 && (x != wx || y != wy)) {
			int nx = x + Integer.signum(wx - x), ny = y + Integer.signum(wy - y);
			Tile t = Tile.get(nx, ny, from.getZ(), false);
			if (t != null && t.clipping != 0)
				break;
			x = nx;
			y = ny;
			steps++;
		}
		if (steps == 0)
			return;
		Position to = new Position(x, y, from.getZ());
		int travel = Raid4Fx.angle(from.getX(), from.getY(), to.getX(), to.getY());
		boolean roll = steps >= 2;
		m.reset();
		Raid4Fx.teleportNow(p, from); // undo this tick's walk step: the client only ever sees the dodge
		p.animate(roll ? SEQ_ROLL : SEQ_STRAFE);
		int angle = roll ? travel : ((travel + 1024 + 4) & 2047);
		Raid4Fx.slide(p, to, roll ? 15 : 8, angle);
		// the rest of the route walks on from the next tick
		if (x != wx || y != wy)
			p.stepAbs(wx, wy, io.ruin.model.entity.shared.StepType.NORMAL);
	}

	/* ---------------------------------------------------------------- the room */

	/** The Archive transporter (Use / Quick-use): 7 tiles across onto the landing tile, two ticks after the op. */
	@Override
	public void useTeleporter(Player player) {
		if (complete) {
			controller.returnToNexus(player);
			return;
		}
		if (finale) {
			player.sendMessage("The swords have closed around the arena. You can't get in.");
			return;
		}
		if (inArena(player.getPosition()))
			return;
		player.lock();
		player.addEvent(e -> {
			e.delay(2);
			player.unlock();
			Raid4Fx.teleportNow(player, local(LANDING_X, LANDING_Y));
			player.graphics(SPOT_TELE);
			if (player.avatar() != null)
				player.avatarExtended().resetFacing();
		});
	}

	private void updateHud() {
		if (!engaged)
			return;
		int hp = Math.max(0, ezekiel.getHp());
		for (Player p : fighters()) {
			if (hudShown.add(p) || !p.getHealthHud().isOpened())
				p.getHealthHud().open(true, NPC_EZEKIEL, maxHp);
		}
		if (hp != shownHp) {
			shownHp = hp;
			for (Player p : hudShown)
				p.getHealthHud().updateValue(hp);
		}
	}

	private void block(int x, int y) {
		Position p = local(x, y);
		Tile tile = Tile.get(p.getX(), p.getY(), p.getZ(), true);
		if ((tile.clipping & 0x200000) == 0) {
			tile.flagUnmovable();
			blocked.add(tile);
		}
	}

	private void unblockAll() {
		for (Tile tile : blocked)
			tile.unflagUnmovable();
		blocked.clear();
	}

	@Override
	public void cleanup() {
		schedule.clear();
		for (Sword s : swords)
			removeNpc(s.npc);
		swords.clear();
		for (NPC prop : props)
			removeNpc(prop);
		props.clear();
		vee = null;
		unblockAll();
		for (Player p : hudShown)
			p.getHealthHud().close();
		hudShown.clear();
		if (map.getNpcs() != null)
			for (NPC npc : new ArrayList<>(map.getNpcs()))
				removeNpc(npc);
	}

	/* Helpers shared with the swords */

	public Raid4Schedule schedule() {
		return schedule;
	}

	/** Living members standing in this room. */
	public List<Player> fighters() {
		List<Player> list = controller.getParty().alive();
		list.removeIf(p -> instance.roomAt(p.getPosition()) != type());
		return list;
	}

	public void hitPlayer(Player player, Hit hit) {
		if (player.isInvincible())
			return;
		hit.postDamage(entity -> controller.getPoints().damageTaken(player, hit.damage));
		player.hit(hit);
	}

	public void recordDamage(Player player, int damage) {
		controller.getPoints().damageDealt(player, damage);
	}

	public void recordMechanic(Player player) {
		controller.getPoints().mechanic(player);
	}

	public void message(String message) {
		controller.getParty().message(message);
	}

	/** Edge-on to his 3x3 (one of the 12 tiles sharing an edge). */
	private boolean edgeOn(Position p) {
		int sx = lx(ezekiel.getPosition()), sy = ly(ezekiel.getPosition());
		int x = lx(p) - sx, y = ly(p) - sy;
		boolean sideX = (x == -1 || x == 3) && y >= 0 && y <= 2;
		boolean sideY = (y == -1 || y == 3) && x >= 0 && x <= 2;
		return sideX || sideY;
	}

	private boolean under(Position p) {
		int sx = lx(ezekiel.getPosition()), sy = ly(ezekiel.getPosition());
		int x = lx(p) - sx, y = ly(p) - sy;
		return x >= 0 && x <= 2 && y >= 0 && y <= 2;
	}

	private int distToBox(Position p) {
		int sx = lx(ezekiel.getPosition()), sy = ly(ezekiel.getPosition());
		int x = lx(p), y = ly(p);
		int dx = x < sx ? sx - x : x > sx + 2 ? x - sx - 2 : 0;
		int dy = y < sy ? sy - y : y > sy + 2 ? y - sy - 2 : 0;
		return Math.max(dx, dy);
	}

	private boolean inArena(Position p) {
		return inArenaLocal(lx(p), ly(p));
	}

	private static boolean inArenaLocal(int x, int y) {
		return x >= ARENA_MIN && x <= ARENA_MAX && y >= ARENA_MIN && y <= ARENA_MAX;
	}

	private static boolean inCage(int x, int y) {
		return x >= CAGE_X - 1 && x <= CAGE_X + 1 && y >= CAGE_Y - 1 && y <= CAGE_Y + 1;
	}

	private int lx(Player p) {
		return lx(p.getPosition());
	}

	private int ly(Player p) {
		return ly(p.getPosition());
	}

	private int lx(Position p) {
		return instance.localX(type(), p);
	}

	private int ly(Position p) {
		return instance.localY(type(), p);
	}

	private Position local(int x, int y) {
		return instance.local(type(), x, y, 0);
	}

	private static long key(int x, int y) {
		return ((long) (x + 0x8000) << 16) | ((y + 0x8000) & 0xffff);
	}

	private static int kx(long k) {
		return (int) (k >> 16) - 0x8000;
	}

	private static int ky(long k) {
		return (int) (k & 0xffff) - 0x8000;
	}

	private static int clamp(int v, int min, int max) {
		return Math.max(min, Math.min(max, v));
	}

	private NPC spawn(int id, Position pos, Direction dir) {
		NPC npc = new NPC(id).spawn(pos.getX(), pos.getY(), pos.getZ(), dir, 0);
		map.addNpc(npc);
		return npc;
	}

	private void removeNpc(NPC npc) {
		if (npc == null)
			return;
		if (map.getNpcs() != null)
			map.getNpcs().remove(npc);
		if (!npc.isRemoved())
			npc.remove();
	}

	@Override
	public boolean isStarted() {
		return engaged;
	}

	@Override
	public boolean isComplete() {
		return complete;
	}

	@Override
	public Position entryPosition() {
		return instance.walkable(type(), EZEKIEL_ENTRY_LOCAL_X, EZEKIEL_ENTRY_LOCAL_Y, 0, 1);
	}

	@Override
	public Position spectatePosition() {
		return entryPosition();
	}
}
