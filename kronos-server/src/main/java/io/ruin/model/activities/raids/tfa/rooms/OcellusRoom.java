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
import io.ruin.model.map.Tile;
import io.ruin.model.map.object.GameObject;
import io.ruin.model.skills.prayer.Prayer;
import io.ruin.model.stat.Stat;
import io.ruin.model.stat.StatType;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.ruin.model.activities.raids.tfa.Raid4Ids.*;

/**
 * The fifth room: Ocellus (region 16472, plane 1), rebuilt 2026-10-28 from the RSProx captures (index 4301, 4308,
 * 4309, 4312, 4315, 4316, 4318-4320; docs/rsprox-analysis/guthixian_archive/ocellus_plan.md) and the eight RuneLite
 * fight logs (Julian-Dasilva/ArchiveOcellusTrainer docs/fight-logs). Section numbers (§) refer to the plan. Offsets are
 * from the arena's middle (4127,5673,1) = local (31,41); his 3x3 is placed by its south-west tile.
 * <p>
 * Built approximations (the plan names no rule): the chasm is the 3-wide cross between the quadrants and a stone's
 * Jump carries a raider straight over it; the void rifts' empowerment, the deflects' reflected damage and the
 * explosions' private telegraph timings beyond the plan.
 */
@Slf4j
public class OcellusRoom extends Raid4BossRoom {

	/* §1 the room */
	static final int MX = 31, MY = 41, PLANE = 1;
	static final int ENTRY_X = 31, ENTRY_Y = 4; // the raiders arrive 33 tiles south (30..32, 3..5)
	/** Corner south-west offsets, clockwise: NE, SE, SW, NW. */
	static final int[][] CORNERS = { { 5, 5 }, { 5, -7 }, { -7, -7 }, { -7, 5 } };
	/** Censers (quadrant NE, SE, SW, NW): Bandosian melee, Saradomin heal, Armadylean ranged, Zarosian spec. */
	static final int[][] CENSERS = { { 11, 11, LOC_CENSER_MELEE }, { 11, -11, LOC_CENSER_HEAL }, { -11, -11, LOC_CENSER_RANGED },
			{ -11, 11, LOC_CENSER_SPEC } };
	static final int[] CENSER_GLOW = { 4342, 4343, 4344, 4341 }; // sp_attackglow bandos, saradomin, armadyl, zaros

	/* Phase 1 */
	static final int VOLLEY_EVERY = 5, LINE_LEN = 9, BEAT_LINE_LEN = 6, METER_MAX = 96;
	static final String[] CORNER_SHOUTS = { "Pointless...", "You... fail...", "Unworthy..." };

	/* Phase 2: (N/S gap, E/W gap), 1..6 out; orange, red, green */
	static final int[][][] SEQUENCES = {
			{ { 2, 2 }, { 4, 2 }, { 2, 4 }, { 2, 2 }, { 3, 3 }, { 5, 2 } },
			{ { 3, 3 }, { 3, 4 }, { 2, 5 }, { 3, 3 }, { 3, 5 }, { 4, 4 } },
			{ { 2, 1 }, { 2, 4 }, { 2, 6 }, { 4, 4 }, { 3, 3 }, { 4, 1 } } };

	/* Ids */
	static final int SEQ_P1_PROJ_START = 14806, SEQ_P1_PROJ_END = 14807, SEQ_P1_CHARGE = 14809, SEQ_P1_EXPEND = 14810,
			SEQ_P1_EXPLODE = 14811, SEQ_P1_TELE_OUT = 14812, SEQ_P1_TELE_IN = 14813, SEQ_P1_TRANSFORM = 14814,
			SEQ_P2_TRANSFORM = 14837, SEQ_P2_TO_P3_1 = 14878, SEQ_P2_TO_P3_2 = 14881, SEQ_MELEE = 14842, SEQ_SLASH = 14846,
			SEQ_SUPERIOR = 14848, SEQ_VOLLEY = 14851, SEQ_PRAYER_IN = 14869, SEQ_PRAYER_LOOP = 14870, SEQ_PRAYER_OUT = 14871,
			SEQ_UNLEASH_IN = 14857, SEQ_UNLEASH_LOOP = 14858, SEQ_UNLEASH_OUT = 14861, SEQ_DESTROY_IN = 14859,
			SEQ_DESTROY_LOOP = 14860, SEQ_DESTROY_OUT = 14862, SEQ_DEFLECT_IN = 14863, SEQ_DEFLECT_LOOP = 14864,
			SEQ_DEFLECT_OUT = 14865, SEQ_SOUL_START = 14855, SEQ_SOUL_END = 14856, SEQ_HYBRID = 14844, SEQ_DEATH = 14872,
			SEQ_JUMP = 12196;
	static final int SPOT_REPAIR = 4304, SPOT_FIRE_CAST = 4231, SPOT_FIRE_PROJ = 4232, SPOT_WATER_CAST = 4228,
			SPOT_WATER_PROJ = 4229, SPOT_CHARGE = 4260, SPOT_EXPLODE_NPC = 4261, SPOT_EXPLODE_FLOOR = 4262,
			SPOT_TELE_OUT = 4266, SPOT_TELE_IN = 4267, SPOT_TO_P2 = 4268, SPOT_PORTAL_PROJ = 4269, SPOT_POOL = 4270,
			SPOT_SAFE_SPAWN = 4271, SPOT_SAFE_IDLE = 4272, SPOT_SAFE_DESPAWN = 4273, SPOT_STRIKE_PROJ = 4303,
			SPOT_STRIKE_HEAL = 4307, SPOT_DISCHARGE = 4305, SPOT_SWORD_DROP = 4280, SPOT_ROOT_FIRE = 4281, SPOT_ARM = 4282,
			SPOT_ARM_GLOW = 4283, SPOT_DROP_EXPLOSION = 4388, SPOT_COLLAPSE_1 = 4390, SPOT_MAGIC_CAST = 4294,
			SPOT_MAGIC_PROJ = 4297, SPOT_MAGIC_HIT = 4293, SPOT_SLASH = 4335, SPOT_SUPERIOR = 4336, SPOT_PRAYER_IN = 4325,
			SPOT_PRAYER = 4326, SPOT_PRAYER_OUT = 4327, SPOT_UNLEASH = 4321, SPOT_UNLEASH_ARENA = 4317,
			SPOT_COLLAPSE_IN = 4331, SPOT_COLLAPSE_LOOP = 4332, SPOT_COLLAPSE_OUT_1 = 4333, SPOT_COLLAPSE_OUT_2 = 4334,
			SPOT_BOMB_IDLE = 4299, SPOT_BOMB_IMPACT = 4300, SPOT_BOMB_DROP = 4301, SPOT_ENTANGLE_START = 4290,
			SPOT_ENTANGLE = 4291, SPOT_ENTANGLE_END = 4292, SPOT_FRAGMENT_LEVITATE = 4316, SPOT_FRAGMENT_IDLE = 4315,
			SPOT_PLAYER_FRAGMENT = 4313, SPOT_CHAIN_TELEGRAPH = 4340, SPOT_CHAIN_EXPLOSION = 4339, SPOT_ORB = 4310,
			SPOT_ORB_REGAIN = 4314, SPOT_HYBRID = 4328, SPOT_HYBRID_MAGIC = 4329, SPOT_HYBRID_RANGED = 4330,
			SPOT_DEATH = 4274, SPOT_DEATH_SKIRT = 4275, SPOT_DEATH_CRACK = 4276;
	static final int LOC_PORTAL = 62684, LOC_PORTAL_ACTIVE = 62685, LOC_BLADE_IDLE0 = 62707, LOC_BLADE_IDLE2 = 62709,
			LOC_STONE_MIDDLE = 62686, LOC_STONE_NORTH = 62687, LOC_BOMB = 62701, LOC_BARRIER_1 = 63000, LOC_BARRIER_2 = 63001,
			LOC_ENTRANCE_CLOSED = 62952, LOC_ENTRANCE_CLOSING = 62953, SEQ_PORTAL_DESPAWN = 14831;

	/*
	 * The arena's break, as the fight logs record it (region-local tiles, the loc's south-west corner):
	 * the four arm bridges (map locs) become tfa_wallkit_archive03_straight_collapse_01..04 at P2 T+127, crack
	 * (dungeonkit_guthixian03_cracking_collapsing_01..04) at T+132 and are left _empty at T+135; each quadrant's two
	 * wall slices and brazier flame go at its fall (D+13) for its three tfa_wallkit_archive03_slice_collapse_<q> locs.
	 */
	/** {map loc, x, y, collapse loc}: south, west, north, east arms. */
	static final int[][] ARM_BRIDGES = { { 62950, 30, 28, 62929 }, { 62928, 18, 40, 62930 }, { 62927, 30, 44, 62931 },
			{ 62928, 34, 40, 62932 } };
	static final int LOC_BRIDGE_SOUTH_END = 62954, BRIDGE_END_X = 30, BRIDGE_END_Y = 26, LOC_CENTER = 62955, CENTER_X = 30,
			CENTER_Y = 40, SEQ_CRACKING = 15042, SEQ_STONE_DESPAWN = 15052;
	/** Per quadrant (NE, SE, SW, NW): the map locs that go {loc, x, y}. */
	static final int[][][] SLICES = {
			{ { 62926, 33, 44 }, { 62925, 33, 43 }, { 62895, 40, 50 } },
			{ { 62924, 33, 31 }, { 62925, 33, 28 }, { 62895, 40, 32 } },
			{ { 62924, 21, 28 }, { 62925, 18, 31 }, { 62895, 22, 32 } },
			{ { 62924, 18, 43 }, { 62925, 21, 43 }, { 62895, 22, 50 } } };
	/** Per quadrant: its slice_collapse locs {loc, x, y} (rot 0; SE mirrored from NW, never recorded). */
	static final int[][][] SLICE_COLLAPSE = {
			{ { 62943, 33, 49 }, { 62944, 39, 43 }, { 62945, 32, 42 } },
			{ { 62946, 39, 31 }, { 62947, 33, 28 }, { 62948, 32, 33 } },
			{ { 62937, 21, 28 }, { 62938, 18, 32 }, { 62939, 23, 33 } },
			{ { 62940, 18, 43 }, { 62941, 22, 49 }, { 62942, 23, 42 } } };
	static final int NPC_INTRO = 16672, NPC_HEAL_RIFT = 16679;

	enum Phase { INTRO, P1, P2, P3, P4, DEAD }

	private Phase phase = Phase.INTRO;
	private NPC boss, blade;
	private Raid4Puppet body;
	private int p1Max, p2Max;
	private final Set<Player> hud = new HashSet<>();
	private int shownHp = -1, shownMeter = -1;
	private final List<GameObject> locs = new ArrayList<>();
	private final List<Tile> blocked = new ArrayList<>();

	/* phase 1 */
	private int corner, meter, element, linesThisCharge, firstTendril, nextCorner;
	private boolean walking, atMiddle, middle66Done, middle25Done, p1Ending, prayMelee;
	private long nextVolley = Long.MAX_VALUE, chargeAt = Long.MAX_VALUE;

	/* phase 2 */
	private int sequence = -1;
	private final GameObject[][] portals = new GameObject[4][6]; // arm (N, E, S, W) x k-1

	/* phase 3 */
	private int segment, quadrant, rotation, riftKind = -1, quadrantsFallen;
	private boolean spinning, unleashing, falling, vineDone, spunThisSegment, insolenceNext;
	private long nextP3Attack = Long.MAX_VALUE;
	private final boolean[] fallen = new boolean[4];
	private final boolean[] censerUsed = new boolean[4];
	private final Map<Long, GameObject> patches = new HashMap<>();
	private final Map<Player, Long> patchDue = new HashMap<>();
	private Player target;
	private final Map<Player, int[]> censerBuff = new HashMap<>(); // 1 melee max, 2 ranged max

	/* phase 4 */
	private long d0 = Long.MAX_VALUE;
	private int cycles, headicon = -1;
	private final Map<Player, int[]> orbs = new HashMap<>(); // {x, y, ready tick, wind hit}
	private final Set<Long> voidRing = new HashSet<>();

	@Override
	public Raid4RoomType type() {
		return Raid4RoomType.OCELLUS;
	}

	@Override
	protected int plane() {
		return PLANE;
	}

	private Position o(int dx, int dy) {
		return local(MX + dx, MY + dy);
	}

	private int ox(Position p) {
		return lx(p) - MX;
	}

	private int oy(Position p) {
		return ly(p) - MY;
	}

	/* ---------------------------------------------------------------- §1 the room */

	@Override
	protected void spawnFixtures() {
		boss = spawn(NPC_INTRO, o(-1, -4), Direction.NORTH);
		body = (Raid4Puppet) boss.getCombat();
		body.bind(this);
		body.setPreDamage(this::bossPreDamage);
		body.setPostDamage(h -> afterBossHit());
		boss.face(o(0, 2));
		block3x3(o(-1, -4));
		for (int[] c : CENSERS)
			locs.add(instance.spawnObject(c[2], o(c[0], c[1]), 10, 0));
		repairParticles();
	}

	private void repairParticles() {
		if (phase != Phase.INTRO && phase != Phase.P2)
			return;
		Raid4Fx.mapAnim(SPOT_REPAIR, o(0, 0), 0, 0);
		schedule.after(3, this::repairParticles);
	}

	@Override
	protected int[] arenaBounds() {
		return new int[] { MX - 12, MY - 12, MX + 12, MY + 12 };
	}

	@Override
	protected int[] arenaEntry() {
		return new int[] { ENTRY_X, ENTRY_Y };
	}

	@Override
	protected int[] outside() {
		return arenaEntry();
	}

	@Override
	protected boolean usesNavigator() {
		return false;
	}

	@Override
	protected String challengeName() {
		return "Ocellus";
	}

	/** The fight starts on Talk-to / Proceed (the Hooded Figure), not by walking in. */
	@Override
	public boolean inArena(Position p) {
		return phase != Phase.INTRO && super.inArena(p);
	}

	/** Talk-to / Proceed on the Hooded Figure. */
	public void talk(Player player) {
		if (phase != Phase.INTRO || complete)
			return;
		phase = Phase.P1;
		super.start(); // begin() with the first raider inside
		if (!started)
			onStartForced(player);
	}

	private void onStartForced(Player first) {
		started = true;
		scale = Raid4Scaling.scale(controller.getParty().alive().size());
		controller.challengeStarted(challengeName());
		onStart(first);
	}

	@Override
	protected void onStart(Player first) {
		p1Max = Raid4Scaling.tableHp(2500, scale);
		p2Max = Raid4Scaling.tableHp(5250, scale);
		boss.forceText("Now... ascension...");
		boss.transform(NPC_OCELLUS_P1);
		setHp(p1Max);
		unblockAll();
		// the way in is shut live once everyone is up; here nobody is shut out: raiders still on the stairs are
		// brought onto the platform instead
		for (Player p : fighters())
			if (oy(p.getPosition()) < -11)
				p.getMovement().teleport(o(Random.get(-1, 1), -10));
		// the way in shuts behind them: tfa_wallkit_archive03_entrance02_closing (0,-13) shape 22, +3 _closed, and the
		// stairs' head (-1..1, -13/-14) blocked (4301 [913..916], 4308 [1500..1503])
		GameObject closing = instance.spawnObject(LOC_ENTRANCE_CLOSING, o(0, -13), 22, 0);
		schedule.after(3, () -> {
			instance.removeObject(closing);
			instance.spawnObject(LOC_ENTRANCE_CLOSED, o(0, -13), 22, 0);
		});
		for (int x = -1; x <= 1; x++)
			for (int y = -14; y <= -13; y++) {
				Position at = o(x, y);
				Tile.get(at.getX(), at.getY(), at.getZ(), true).flagUnmovable();
			}
		schedule.after(1, this::updateHud);
		int[] first3 = { 1, 1, 1, 3, 3, 3, 2 }; // SE, NW, SW as recorded
		nextCorner = first3[Random.get(first3.length - 1)];
		schedule.after(4, () -> walkTo(nextCorner));
		log.info("Raid 4: Ocellus started by {} (scale {}, p1 {}, p2 {})", first.getName(), scale, p1Max, p2Max);
	}

	private void setHp(int hp) {
		Stat s = boss.getCombat().getStat(StatType.Hitpoints);
		s.fixedLevel = hp;
		s.currentLevel = hp;
	}

	/* Damage to him: phases end on health, never by his death until phase 4 */

	private void bossPreDamage(Hit hit) {
		if (phase == Phase.INTRO || phase == Phase.DEAD) {
			hit.damage = 0;
			return;
		}
		boolean melee = hit.attackStyle != null && hit.attackStyle.isMelee();
		if (melee && (prayMelee || headicon == 15))
			hit.damage = 0; // Protect from Melee (phase 1 walks, phase 3 untether) / Deflect Melee
		if (!melee && hit.attackStyle != null && headicon == 16)
			hit.damage = 0; // Deflect Missiles and Magic
		if (phase == Phase.P2)
			hit.damage = 0; // phase 2's hits go to The Blade
		if (phase != Phase.P4 && hit.damage >= boss.getHp())
			hit.damage = Math.max(0, boss.getHp() - 1);
		Player p = hit.attacker != null ? hit.attacker.player : null;
		if (p != null && censerBuff.containsKey(p) && hit.attackStyle != null) {
			int[] b = censerBuff.get(p);
			if ((b[0] == 1 && melee) || (b[0] == 2 && hit.attackStyle.isRanged())) {
				hit.damage = Math.max(hit.damage, hit.maxDamage);
				censerBuff.remove(p);
			}
		}
	}

	private void afterBossHit() {
		updateHud();
		if (phase == Phase.P1 && !p1Ending && boss.getHp() <= 1)
			endPhase1();
	}

	/**
	 * Nobody stays inside his (or The Blade's) footprint: those tiles are blocked, so a raider left there (a teleport,
	 * a jump, a slide) could never step out ("I can't reach that!"). They are put on the nearest open tile.
	 */
	private void pushOut(NPC npc) {
		if (npc == null || npc.isRemoved())
			return;
		Position sw = npc.getPosition();
		int size = npc.getSize();
		for (Player p : fighters()) {
			Position at = p.getPosition();
			if (at.getZ() != sw.getZ() || at.getX() < sw.getX() || at.getX() >= sw.getX() + size || at.getY() < sw.getY()
					|| at.getY() >= sw.getY() + size)
				continue;
			Tile here = Tile.get(at.getX(), at.getY(), at.getZ(), false);
			if (here != null && here.clipping == 0 && !p.getMovement().isAtDestination())
				continue; // already walking out of an open footprint
			// the nearest open tile on the rings just outside the footprint
			Position best = null;
			int bestD = Integer.MAX_VALUE;
			for (int ring = 1; ring <= 3 && best == null; ring++)
				for (int x = sw.getX() - ring; x < sw.getX() + size + ring; x++)
					for (int y = sw.getY() - ring; y < sw.getY() + size + ring; y++) {
						boolean edge = x == sw.getX() - ring || x == sw.getX() + size + ring - 1 || y == sw.getY() - ring
								|| y == sw.getY() + size + ring - 1;
						Tile t = Tile.get(x, y, at.getZ(), false);
						if (!edge || t == null || t.clipping != 0)
							continue;
						int d = Math.abs(x - at.getX()) + Math.abs(y - at.getY());
						if (d < bestD) {
							bestD = d;
							best = new Position(x, y, at.getZ());
						}
					}
			if (best == null)
				continue;
			p.getMovement().reset();
			Raid4Fx.teleportNow(p, best);
		}
	}

	/* ---------------------------------------------------------------- per tick */

	@Override
	protected void onTick() {
		long now = schedule.now();
		pushOut(boss);
		pushOut(blade);
		updateHud();
		tickPatches(now);
		switch (phase) {
			case P1 -> tickP1(now);
			case P3 -> {
				sweepFallen();
				tickP3(now);
			}
			case P4 -> {
				sweepFallen();
				tickP4(now);
			}
			default -> {
			}
		}
	}

	/* ---------------------------------------------------------------- §2 phase 1 */

	private Position cornerTile(int c) {
		return o(CORNERS[c][0], CORNERS[c][1]);
	}

	private void walkTo(int c) {
		if (phase != Phase.P1 || p1Ending)
			return;
		corner = c;
		walking = true;
		atMiddle = false;
		prayMelee = true;
		setHeadicon(0);
		element = Random.get(1);
		Position to = cornerTile(c);
		boss.stepAbs(to.getX(), to.getY(), StepType.NORMAL);
		nextVolley = schedule.now(); // a volley every 5 from his first step
	}

	private void tickP1(long now) {
		if (p1Ending)
			return;
		if (walking) {
			Position to = cornerTile(corner);
			boolean arrived = boss.getPosition().getX() == to.getX() && boss.getPosition().getY() == to.getY();
			if (now >= nextVolley) {
				nextVolley = now + VOLLEY_EVERY;
				if (checkThreshold())
					return;
				if (arrived) {
					walking = false;
					volley(SEQ_P1_PROJ_END);
					arriveCorner(now + 6);
				} else {
					volley(SEQ_P1_PROJ_START);
					if (Random.get(64) < 14)
						element ^= 1;
					if (lx(boss.getPosition()) != lx(to) || ly(boss.getPosition()) != ly(to))
						boss.stepAbs(to.getX(), to.getY(), StepType.NORMAL);
				}
			}
		}
	}

	/** A shot at every raider, one element: fire (orange, ranged) or water (blue, magic). */
	private void volley(int seq) {
		boss.animate(seq);
		boolean fire = element == 0;
		Raid4Fx.spot(boss, 0, fire ? SPOT_FIRE_CAST : SPOT_WATER_CAST);
		Position mid = boss.getPosition().relative(1, 1);
		for (Player p : fighters()) {
			int dist = distance(boss.getPosition(), 3, p.getPosition());
			int end = dist <= 4 ? 95 : 125;
			Raid4Fx.projectile(fighters(), boss, fire ? SPOT_FIRE_PROJ : SPOT_WATER_PROJ, mid, p, p.getPosition(), 578, 124, 30, end, 40, 0);
			schedule.after((end + 15) / 30, () -> {
				if (!fighters().contains(p))
					return;
				boolean prayed = p.getPrayer().isActive(fire ? Prayer.PROTECT_FROM_MISSILES : Prayer.PROTECT_FROM_MAGIC);
				int dmg = prayed || Random.get(12) < 5 ? 0 : Random.get(25);
				hitPlayer(p, new Hit(boss, fire ? AttackStyle.RANGED : AttackStyle.MAGIC).fixedDamage(dmg).ignoreDefence().ignorePrayer());
			});
		}
	}

	private void arriveCorner(long c) {
		boss.face(o(0, 0));
		setHeadicon(-1);
		prayMelee = false;
		chargeAt = c;
		schedule.after((int) (c - schedule.now()), this::charge);
	}

	/** C: the charge, seven lines two apart (three tendrils in turn), the shout at C+15, the explosion C+18. */
	private void charge() {
		if (phase != Phase.P1 || p1Ending)
			return;
		boss.animate(SEQ_P1_CHARGE);
		int cx = CORNERS[corner][0], cy = CORNERS[corner][1];
		int sx = Integer.signum(-cx - 1) == 0 ? 1 : Integer.signum(-cx - 1), sy = Integer.signum(-cy - 1) == 0 ? 1 : Integer.signum(-cy - 1);
		// first line: the z face at NE and SW, the x face at SE and NW; then the corner and the other face either order
		boolean zFirst = corner == 0 || corner == 2;
		int[] order = Random.get(1) == 0 ? new int[] { zFirst ? 1 : 0, 2, zFirst ? 0 : 1 } : new int[] { zFirst ? 1 : 0, zFirst ? 0 : 1, 2 };
		// seven lines two apart from C itself: C, C+2 .. C+12 (ocellus_plan 2)
		cornerLine(order[0], sx, sy);
		for (int i = 1; i < 7; i++) {
			int tendril = order[i % 3];
			schedule.after(2 * i, () -> cornerLine(tendril, sx, sy));
		}
		for (int t = 5; t < 18; t += 2)
			schedule.after(t, () -> {
				if (phase == Phase.P1 && !p1Ending)
					Raid4Fx.spot(boss, 0, SPOT_CHARGE);
			});
		schedule.after(15, () -> {
			if (phase == Phase.P1 && !p1Ending)
				boss.forceText(CORNER_SHOUTS[Random.get(2)]);
		});
		schedule.after(18, this::explode);
		schedule.after(21, () -> {
			if (phase != Phase.P1 || p1Ending || atMiddle)
				return;
			if (checkThreshold())
				return;
			int r = Random.get(18);
			nextCorner = r < 10 ? (corner + 3) % 4 : r < 13 ? (corner + 1) % 4 : (corner + 2) % 4;
			walkTo(nextCorner);
		});
	}

	/** A tendril's line from his 3x3 (sx, sy: toward the middle). 0 the x face, 1 the z face, 2 his inner corner. */
	private void cornerLine(int tendril, int sx, int sy) {
		if (phase != Phase.P1 || p1Ending)
			return;
		Position sw = boss.getPosition();
		int fx, fy, dx, dy;
		boolean straight = tendril == 2 ? Random.get(41) >= 20 : Random.get(106) < 62;
		if (tendril == 0) { // the middle tile of his face toward the middle's x
			fx = sx > 0 ? 3 : -1;
			fy = 1;
			dx = sx;
			dy = straight ? 0 : sy;
		} else if (tendril == 1) {
			fx = 1;
			fy = sy > 0 ? 3 : -1;
			dx = straight ? 0 : sx;
			dy = sy;
		} else {
			fx = sx > 0 ? 3 : -1;
			fy = sy > 0 ? 3 : -1;
			int r = Random.get(2);
			dx = r == 1 ? 0 : sx;
			dy = r == 0 ? 0 : sy;
		}
		List<Position> tiles = new ArrayList<>();
		for (int i = 0; i < LINE_LEN; i++)
			tiles.add(sw.relative(fx + dx * i, fy + dy * i));
		crackLine(tiles, -dx, -dy, 0, 5);
	}

	/**
	 * A crack line grows from its far end toward him: the start piece at delay 60 + extra, each tile a cycle later;
	 * it closes {@code closeAfter} ticks on with its end pieces. A close adds 2 to the meter and heals him 2 unless
	 * every raider stands on it; everyone on it takes 3..5.
	 */
	private void crackLine(List<Position> tiles, int gx, int gy, int extraDelay, int closeAfter) {
		int[] ids = crackIds(gx, gy); // {start, piece, end}
		int n = tiles.size();
		List<Position> open = new ArrayList<>();
		for (int i = n - 1, k = 0; i >= 0; i--, k++) {
			Position t = tiles.get(i);
			if (Tile.get(t.getX(), t.getY(), t.getZ(), true).clipping != 0)
				continue;
			open.add(t);
			Raid4Fx.mapAnim(k == 0 ? ids[0] : ids[1], t, 60 + extraDelay + k, 0);
		}
		schedule.after(closeAfter + extraDelay / 30, () -> {
			if (phase != Phase.P1)
				return;
			int k = 0;
			for (Position t : open)
				Raid4Fx.mapAnim(ids[2], t, k++, 0);
			Set<Long> keys = new HashSet<>();
			for (Position t : open)
				keys.add(key(t));
			List<Player> all = fighters();
			int on = 0;
			for (Player p : all)
				if (keys.contains(key(p.getPosition()))) {
					on++;
					hitPlayer(p, new Hit().randDamage(3, 5).ignoreDefence().ignorePrayer());
				}
			if (!open.isEmpty())
				Raid4Fx.sound(all, Random.get(1) == 0 ? 12512 : 12513, open.get(open.size() - 1), 0, 20);
			if (all.isEmpty() || on < all.size()) {
				meter = Math.min(METER_MAX, meter + 2);
				if (boss.getHp() < p1Max)
					boss.hit(new Hit(HitType.HEAL).fixedDamage(2));
			}
		});
	}

	/** Crack piece ids by the way the line grows. */
	private static int[] crackIds(int gx, int gy) {
		if (gx == 0 || gy == 0) {
			int r = gy > 0 ? 0 : gx > 0 ? 1 : gy < 0 ? 2 : 3; // N, E(_90), S(_180), W(_270)
			return new int[] { 4236 + r, 4240 + 2 * r, 4241 + 2 * r };
		}
		int r = gx > 0 && gy > 0 ? 0 : gx > 0 ? 1 : gy < 0 ? 2 : 3; // NE, SE(_90), SW(_180), NW(_270)
		return new int[] { 4248 + r, 4252 + 2 * r, 4253 + 2 * r };
	}

	/** The explosion (E) and the blast on E+1: within 2 of his 3x3 on E and still there, 28..30. */
	private void explode() {
		if (phase != Phase.P1 || p1Ending)
			return;
		boss.animate(SEQ_P1_EXPLODE);
		Raid4Fx.spot(boss, 0, SPOT_EXPLODE_NPC);
		Position sw = boss.getPosition().copy();
		Set<Player> near = new HashSet<>();
		for (Player p : fighters())
			if (distance(sw, 3, p.getPosition()) <= 2)
				near.add(p);
		schedule.after(1, () -> {
			for (int ring = 1; ring <= 2; ring++)
				for (int x = -ring; x < 3 + ring; x++)
					for (int y = -ring; y < 3 + ring; y++) {
						Position t = sw.relative(x, y);
						if (distance(sw, 3, t) != ring || Tile.get(t.getX(), t.getY(), t.getZ(), true).clipping != 0)
							continue;
						Raid4Fx.mapAnim(SPOT_EXPLODE_FLOOR + Random.get(3), t, ring == 1 ? 0 : 2, 0);
					}
			for (Player p : near)
				if (fighters().contains(p) && distance(sw, 3, p.getPosition()) <= 2)
					hitPlayer(p, new Hit(boss).randDamage(28, 30).ignoreDefence().ignorePrayer()); // recorded 28..30
		});
	}

	/** 66% and 25%: checked after an explosion and on each volley's tick while he walks. */
	private boolean checkThreshold() {
		double frac = boss.getHp() / (double) p1Max;
		if (!middle66Done && frac <= 0.66) {
			middle66Done = true;
			toMiddle();
			return true;
		}
		if (!middle25Done && frac <= 0.25) {
			middle25Done = true;
			toMiddle();
			return true;
		}
		return false;
	}

	/** §2a: out, +4 in at the middle (sw (-1,-1)), +5 the blockwalk, then four beats 8 apart. */
	private void toMiddle() {
		walking = false;
		atMiddle = true;
		boss.getMovement().reset();
		boss.animate(SEQ_P1_TELE_OUT);
		Raid4Fx.spot(boss, 2, SPOT_TELE_OUT);
		Raid4Fx.fade(boss, 30, 45, 0, 255);
		setHeadicon(-1);
		prayMelee = false;
		int startFace = Random.get(3); // east, south, west, north
		schedule.after(4, () -> {
			Raid4Fx.teleportNow(boss, o(-1, -1));
			boss.animate(SEQ_P1_TELE_IN);
			Raid4Fx.fade(boss, 0, 20, 255, 0);
			int[][] faceDir = { { 1, 0 }, { 0, -1 }, { -1, 0 }, { 0, 1 } };
			Raid4Fx.faceAngle(boss, Raid4Fx.angle(0, 0, faceDir[startFace][0], faceDir[startFace][1]), true);
		});
		schedule.after(5, () -> block3x3(o(-1, -1)));
		for (int b = 0; b < 4; b++) {
			int face = (startFace + b) % 4;
			schedule.after(8 + 8 * b, () -> beat(face));
		}
		schedule.after(8 + 8 * 3 + 6 + 2, this::strike);
	}

	/** A beat at the middle: two six-long diagonals on one face, the first at delay 0, the second at 60. */
	private void beat(int face) {
		if (phase != Phase.P1 || p1Ending)
			return;
		boss.animate(SEQ_P1_CHARGE);
		Position sw = boss.getPosition();
		int[][][] ends = { { { 3, 2, 1, 1 }, { 3, 0, 1, -1 } }, { { 2, -1, 1, -1 }, { 0, -1, -1, -1 } },
				{ { -1, 0, -1, -1 }, { -1, 2, -1, 1 } }, { { 0, 3, -1, 1 }, { 2, 3, 1, 1 } } };
		for (int l = 0; l < 2; l++) {
			int[] e = ends[face][l];
			List<Position> tiles = new ArrayList<>();
			for (int i = 0; i < BEAT_LINE_LEN; i++)
				tiles.add(sw.relative(e[0] + e[2] * i, e[1] + e[3] * i));
			crackLine(tiles, -e[2], -e[3], l == 0 ? 0 : 60, l == 0 ? 4 : 6);
		}
		schedule.after(4, () -> Raid4Fx.spot(boss, 0, SPOT_CHARGE));
		schedule.after(6, () -> {
			Raid4Fx.spot(boss, 0, SPOT_CHARGE);
			explode();
		});
	}

	/** The strike: "Death... awaits..." (more than one shot: "Embrace... annihilation..."), the expend +2, a shot a
	 * raider for every 24 of the meter plus one, 17..19 each. */
	private void strike() {
		if (phase != Phase.P1 || p1Ending)
			return;
		int shots = 1 + meter / 24;
		boss.forceText(shots > 1 ? "Embrace... annihilation..." : "Death... awaits...");
		meter = 0;
		unblockAll();
		schedule.after(2, () -> {
			if (phase != Phase.P1 || p1Ending)
				return;
			boss.animate(SEQ_P1_EXPEND);
			Position mid = boss.getPosition().relative(1, 1);
			for (Player p : fighters()) {
				boolean close = distance(boss.getPosition(), 3, p.getPosition()) <= 4;
				int end = close ? 39 : 54;
				for (int s = 0; s < shots; s++) {
					Raid4Fx.projectile(fighters(), boss, SPOT_STRIKE_PROJ, mid, p, p.getPosition(), 160, 140, 2 + 8 * s, end + 8 * s, 10, 0);
					Raid4Fx.spot(p, 0, SPOT_STRIKE_HEAL, end, 0);
					schedule.after(close ? 1 : 2, () -> {
						if (fighters().contains(p))
							hitPlayer(p, new Hit(boss).randDamage(17, 19).ignoreDefence().ignorePrayer());
					});
				}
			}
			schedule.after(1, () -> setHeadicon(0));
		});
		schedule.after(10, () -> {
			if (phase == Phase.P1 && !p1Ending)
				walkTo(nextCorner == corner ? (corner + 2) % 4 : nextCorner);
		});
	}

	/** Nothing left: "You... will disappear...", to the middle, the transformation, +7 phase 2. */
	private void endPhase1() {
		p1Ending = true;
		walking = false;
		boss.getMovement().reset();
		boss.forceText("You... will disappear...");
		setHeadicon(-1);
		prayMelee = false;
		int wait = 0;
		if (!atMiddle) {
			schedule.after(2, () -> {
				boss.animate(SEQ_P1_TELE_OUT);
				Raid4Fx.spot(boss, 2, SPOT_TELE_OUT);
				Raid4Fx.fade(boss, 30, 45, 0, 255);
			});
			schedule.after(4, () -> boss.getMovement().teleport(o(-1, -1)));
			schedule.after(5, () -> {
				boss.animate(SEQ_P1_TELE_IN);
				Raid4Fx.fade(boss, 0, 20, 255, 0);
			});
			wait = 5;
		}
		schedule.after(wait + 3, () -> boss.animate(SEQ_P1_TRANSFORM));
		schedule.after(wait + 7, this::phase2);
	}

	/* ---------------------------------------------------------------- §3 phase 2 */

	private void phase2() {
		phase = Phase.P2;
		long t = schedule.now();
		boss.animate(SEQ_P2_TRANSFORM);
		Raid4Fx.spot(boss, 2, SPOT_TO_P2);
		boss.transform(NPC_OCELLUS_P2);
		if (boss.avatar() != null)
			boss.avatarExtended().setAllOpsInvisible();
		setHp(p2Max);
		shownMeter = -2;
		repairParticles();
		schedule.after(2, () -> {
			for (int k = 1; k <= 6; k++) {
				portals[0][k - 1] = instance.spawnObject(LOC_PORTAL, o(0, 2 * k - 1), 10, 3);
				portals[1][k - 1] = instance.spawnObject(LOC_PORTAL, o(2 * k - 1, 0), 10, 0);
				portals[2][k - 1] = instance.spawnObject(LOC_PORTAL, o(0, -2 * k), 10, 1);
				portals[3][k - 1] = instance.spawnObject(LOC_PORTAL, o(-2 * k, 0), 10, 2);
			}
			block3x3(o(-1, -1));
		});
		schedule.after(7, () -> {
			boss.forceText("Your time... comes...");
			locs.add(instance.spawnObject(LOC_BLADE_IDLE0, o(0, 0), 10, 0));
			blade = spawn(NPC_OCELLUS_BLADE, o(-1, -1), Direction.SOUTH);
			Raid4Puppet b = (Raid4Puppet) blade.getCombat();
			b.bind(this);
			b.setPreDamage(hit -> { // its damage is his
				int dmg = Math.min(hit.damage, boss.getHp() - 1);
				if (dmg > 0)
					boss.hit(new Hit(HitType.DAMAGE_OTHER).fixedDamage(dmg).ignoreDefence().ignorePrayer());
				Stat hp = blade.getCombat().getStat(StatType.Hitpoints);
				hp.currentLevel = hp.fixedLevel + hit.damage; // never dies
			});
		});
		int q = quadrantOfParty();
		for (int r = 0; r < 3; r++) {
			int quad = (q + r) % 4;
			int round = r;
			schedule.after(11 + 42 * r, () -> round(quad, round));
		}
		schedule.after(123, this::p2ToP3);
	}

	private int quadrantOfParty() {
		int[] votes = new int[4];
		for (Player p : fighters()) {
			int qd = quadrantOf(p.getPosition());
			if (qd >= 0)
				votes[qd]++;
		}
		int best = Random.get(3);
		for (int i = 0; i < 4; i++)
			if (votes[i] > votes[best])
				best = i;
		return best;
	}

	/** 0 NE, 1 SE, 2 SW, 3 NW; -1 on the cross. */
	private int quadrantOf(Position p) {
		int x = ox(p), y = oy(p);
		if (Math.abs(x) < 2 || Math.abs(y) < 2)
			return -1;
		if (x > 0)
			return y > 0 ? 0 : 1;
		return y < 0 ? 2 : 3;
	}

	private static int qsx(int q) {
		return q == 0 || q == 1 ? 1 : -1;
	}

	private static int qsy(int q) {
		return q == 0 || q == 3 ? 1 : -1;
	}

	/** A round on quadrant q: six volleys through ten of its twelve portals, the pools, the safe portal, the fire. */
	private void round(int q, int roundNo) {
		if (phase != Phase.P2)
			return;
		sequence = sequence < 0 ? Random.get(2) : (sequence + 1) % 3;
		int[][] seq = SEQUENCES[sequence];
		int sx = qsx(q), sy = qsy(q);
		int nsArm = sy > 0 ? 0 : 2, ewArm = sx > 0 ? 1 : 3;
		for (int v = 0; v < 6; v++) {
			int[] gap = seq[v];
			schedule.after(5 * v, () -> lightPortals(nsArm, ewArm, gap));
			schedule.after(5 * v + 4, () -> firePortals(q, nsArm, ewArm, gap));
		}
		List<Position> pools = new ArrayList<>();
		schedule.after(28, () -> {
			for (int i = 0; i < 13; i++) {
				Position t = quadrantTile(q, 2, 10);
				pools.add(t);
				Raid4Fx.mapAnim(SPOT_POOL, t, 0, 0);
			}
		});
		schedule.after(31, () -> lightPortals(nsArm, ewArm, new int[] { 0, 0 }));
		Position[] safe = new Position[1];
		schedule.after(33, () -> {
			if (pools.isEmpty())
				return;
			safe[0] = pools.get(Random.get(pools.size() - 1));
			Raid4Fx.mapAnim(SPOT_SAFE_SPAWN, safe[0], 0, 0);
		});
		for (int t = 34; t <= 39; t++) {
			int tick = t;
			schedule.after(t, () -> {
				if (safe[0] == null || phase != Phase.P2)
					return;
				Raid4Fx.mapAnim(SPOT_SAFE_IDLE, safe[0], 0, 0);
				if (tick == 34)
					Raid4Fx.spot(boss, 0, SPOT_DISCHARGE, 30, 0, false);
				for (Player p : fighters())
					if (p.getPosition().getX() == safe[0].getX() && p.getPosition().getY() == safe[0].getY()) {
						int nq = (q + 1) % 4;
						Raid4Fx.teleportNow(p, quadrantTile(nq, 3, 9));
					}
			});
		}
		for (int t = 28; t <= 40; t++)
			schedule.after(t, () -> {
				for (Player p : fighters())
					for (Position pool : pools)
						if (p.getPosition().getX() == pool.getX() && p.getPosition().getY() == pool.getY()) {
							hitPlayer(p, new Hit().randDamage(3, 5).ignoreDefence().ignorePrayer());
							break;
						}
			});
		schedule.after(40, () -> {
			firePortals(q, nsArm, ewArm, new int[] { 0, 0 });
			if (safe[0] != null)
				Raid4Fx.mapAnim(SPOT_SAFE_DESPAWN, safe[0], 0, 0);
		});
		if (roundNo == 0)
			schedule.after(28, () -> locs.add(instance.spawnObject(LOC_BLADE_IDLE2, o(0, 0), 10, 0)));
	}

	private void setPortal(int arm, int k, boolean active) {
		GameObject old = portals[arm][k];
		if (old == null)
			return;
		Position at = old.getPosition().copy();
		int rot = old.getDirection();
		instance.removeObject(old);
		portals[arm][k] = instance.spawnObject(active ? LOC_PORTAL_ACTIVE : LOC_PORTAL, at, 10, rot);
	}

	/** Lit: every portal of the two arms but the gap (a gap of 0 lights all twelve). */
	private void lightPortals(int nsArm, int ewArm, int[] gap) {
		if (phase != Phase.P2)
			return;
		for (int k = 0; k < 6; k++) {
			setPortal(nsArm, k, k + 1 != gap[0]);
			setPortal(ewArm, k, k + 1 != gap[1]);
		}
	}

	/** Each lit portal fires across the quadrant along its two rows (or columns); a raider in a line takes 19..25. */
	private void firePortals(int q, int nsArm, int ewArm, int[] gap) {
		if (phase != Phase.P2)
			return;
		int sx = qsx(q), sy = qsy(q);
		Set<Integer> rows = new HashSet<>(), cols = new HashSet<>();
		for (int k = 1; k <= 6; k++) {
			if (k != gap[0]) {
				int a = sy > 0 ? 2 * k - 1 : -2 * k, b = a + 1;
				rows.add(a);
				rows.add(b);
				for (int r : new int[] { a, b })
					Raid4Fx.projectile(fighters(), null, SPOT_PORTAL_PROJ, o(0, r), null, o(sx * 14, r), 100, 100, 0,
							sx > 0 ? 6 * 14 + 6 : 6 * 14 - 6, 127, sx > 0 ? 100 : 250);
			}
			if (k != gap[1]) {
				int a = sx > 0 ? 2 * k - 1 : -2 * k, b = a + 1;
				cols.add(a);
				cols.add(b);
				for (int c : new int[] { a, b })
					Raid4Fx.projectile(fighters(), null, SPOT_PORTAL_PROJ, o(c, 0), null, o(c, sy * 14), 100, 100, 0,
							sy > 0 ? 6 * 14 + 6 : 6 * 14 - 6, 127, sy > 0 ? 100 : 250);
			}
		}
		for (Player p : fighters()) {
			if (quadrantOf(p.getPosition()) != q)
				continue;
			int px = ox(p.getPosition()), py = oy(p.getPosition());
			if (rows.contains(py))
				lineHit(p, (6 * Math.abs(px) + 6 + 29) / 30, true, py);
			if (cols.contains(px))
				lineHit(p, (6 * Math.abs(py) + 6 + 29) / 30, false, px);
		}
		lightPortals(nsArm, ewArm, new int[] { 7, 7 }); // dark
		for (int k = 0; k < 6; k++) {
			setPortal(nsArm, k, false);
			setPortal(ewArm, k, false);
		}
	}

	/** A fired line reaches the raider: still on that row (or column), 19..25. */
	private void lineHit(Player p, int ticks, boolean row, int line) {
		schedule.after(Math.max(1, ticks), () -> {
			if (phase != Phase.P2 || !fighters().contains(p))
				return;
			if ((row ? oy(p.getPosition()) : ox(p.getPosition())) == line)
				hitPlayer(p, new Hit(boss).randDamage(19, 25).ignoreDefence().ignorePrayer());
		});
	}

	/** A random walkable floor tile of quadrant q, {@code min}..{@code max} out each way from the middle. */
	private Position quadrantTile(int q, int min, int max) {
		for (int tries = 0; tries < 40; tries++) {
			Position t = o(qsx(q) * Random.get(min, max), qsy(q) * Random.get(min, max));
			Tile tile = Tile.get(t.getX(), t.getY(), t.getZ(), false);
			if (tile != null && tile.clipping == 0)
				return t;
		}
		return walkable(new int[] { MX + qsx(q) * 5, MY + qsy(q) * 5 }, 3);
	}

	/** T+123: "This... is enough..."; the arena breaks; T+131 phase 3's form; T+137 the first jump. */
	private void p2ToP3() {
		if (phase != Phase.P2)
			return;
		boss.forceText("This... is enough...");
		boss.animate(SEQ_P2_TO_P3_1);
		Raid4Fx.spot(boss, 1, SPOT_ARM_GLOW, 150, 0, false);
		Raid4Fx.spot(boss, 5, SPOT_ROOT_FIRE);
		Raid4Fx.spot(boss, 2, SPOT_ARM, 150, 0, false);
		removeNpc(blade);
		blade = null;
		Raid4Fx.mapAnim(SPOT_SWORD_DROP, o(0, 0), 0, 0);
		schedule.after(1, () -> Raid4Fx.mapAnim(SPOT_DROP_EXPLOSION, o(0, 0), 0, 0));
		schedule.after(4, this::armsCollapse);
		schedule.after(9, () -> {
			for (int i = 0; i < 4; i++)
				if (armCollapse[i] != null)
					armCollapse[i].animate(SEQ_CRACKING + i);
			removeMapLoc(LOC_CENTER, CENTER_X, CENTER_Y);
		});
		schedule.after(12, () -> {
			for (int i = 0; i < 4; i++)
				if (armCollapse[i] != null)
					armCollapse[i].setId(ARM_BRIDGES[i][3] + 4); // _empty
		});
		schedule.after(8, () -> {
			boss.animate(SEQ_P2_TO_P3_2);
			int hpLeft = boss.getHp();
			boss.transform(NPC_OCELLUS_P3);
			Stat st = boss.getCombat().getStat(StatType.Hitpoints);
			st.fixedLevel = p2Max;
			st.currentLevel = hpLeft;
			setHeadicon(-1);
		});
		// T+125: all 24 portals play vfx_ocellus_p2_portal_despawn, gone with the arena's break at T+132
		schedule.after(2, () -> {
			for (GameObject[] arm : portals)
				for (GameObject portal : arm)
					if (portal != null)
						portal.animate(SEQ_PORTAL_DESPAWN);
		});
		schedule.after(9, () -> {
			for (GameObject[] arm : portals)
				for (int k = 0; k < 6; k++)
					if (arm[k] != null) {
						instance.removeObject(arm[k]);
						arm[k] = null;
					}
			Raid4Fx.mapAnim(SPOT_COLLAPSE_1, o(0, 0), 0, 0);
			int[][] dirs = { { 0, 1 }, { 1, 0 }, { 0, -1 }, { -1, 0 } };
			for (int[] d : dirs)
				for (int k = 1; k <= 4; k++)
					Raid4Fx.mapAnim(4390 + k % 4, o(d[0] * 3 * k, d[1] * 3 * k), 15 * (k - 1), 0);
			chasm();
		});
		schedule.after(12, () -> Raid4Fx.fade(boss, 0, 28, 0, 255));
		schedule.after(14, () -> {
			phase = Phase.P3;
			segment = 0;
			quadrant = quadrantOfParty();
			startSegment();
		});
	}

	/** The cross between the quadrants becomes a chasm (2..12 out, three wide); stones at 4, 7, 10 on each arm. */
	private final GameObject[] armCollapse = new GameObject[4];
	/** The stepping stones by arm (N, E, S, W) and distance (4, 7, 10). */
	private final GameObject[][] stones = new GameObject[4][3];

	/** A map loc of this room by its region-local south-west tile; removed for every player in the region. */
	private void removeMapLoc(int id, int x, int y) {
		Position t = local(x, y);
		GameObject.forObj(id, t.getX(), t.getY(), t.getZ(), GameObject::remove);
	}

	/** T+127: the four arm bridges give way to their collapse locs; the bridge's south end goes. */
	private void armsCollapse() {
		for (int i = 0; i < 4; i++) {
			int[] b = ARM_BRIDGES[i];
			removeMapLoc(b[0], b[1], b[2]);
			armCollapse[i] = instance.spawnObject(b[3], local(b[1], b[2]), 10, 0);
			locs.add(armCollapse[i]);
		}
		removeMapLoc(LOC_BRIDGE_SOUTH_END, BRIDGE_END_X, BRIDGE_END_Y);
	}

	/** D+13: quadrant q's wall slices and brazier fall away for its collapse locs. */
	private void sliceCollapse(int q) {
		for (int[] s : SLICES[q])
			removeMapLoc(s[0], s[1], s[2]);
		for (int[] c : SLICE_COLLAPSE[q])
			locs.add(instance.spawnObject(c[0], local(c[1], c[2]), 10, 0));
	}

	/** E+0: the arm clockwise of the last quadrant loses its stones (south/west 7, 4, 10 out; east/north 10, 7, 4). */
	private void stonesDespawn(int lastQuadrant) {
		int arm = (lastQuadrant + 1) % 4;
		int[] order = arm == 2 || arm == 3 ? new int[] { 1, 0, 2 } : new int[] { 2, 1, 0 };
		for (int n = 0; n < 3; n++)
			if (stones[arm][order[n]] != null)
				stones[arm][order[n]].animate(SEQ_STONE_DESPAWN + n);
		schedule.after(2, () -> {
			for (int k = 0; k < 3; k++) {
				GameObject st = stones[arm][k];
				if (st == null)
					continue;
				Position at = st.getPosition().copy();
				instance.removeObject(st);
				locs.remove(st);
				stones[arm][k] = null;
				block(at);
			}
		});
	}

	private boolean isStone(GameObject obj) {
		for (GameObject[] arm : stones)
			for (GameObject st : arm)
				if (st == obj)
					return true;
		return false;
	}

	private void chasm() {
		for (int d = -12; d <= 12; d++)
			for (int w = -1; w <= 1; w++) {
				block(o(w, d));
				block(o(d, w));
			}
		int[][] dirs = { { 0, 1 }, { 1, 0 }, { 0, -1 }, { -1, 0 } };
		for (int a = 0; a < 4; a++)
			for (int k : new int[] { 4, 7, 10 })
				// shape 22 (ground decoration): the stones' locs only carry a model for that shape, so shape 10 drew nothing
				locs.add(stones[a][k / 3 - 1] = instance.spawnObject(k == 7 ? LOC_STONE_MIDDLE : LOC_STONE_NORTH + a,
						o(dirs[a][0] * k, dirs[a][1] * k), 22, 0));
		// the chasm's tiles are blocked, so "I can't reach that!": the jump is taken from the bank beside the stone
		for (GameObject[] arm : stones)
			for (GameObject st : arm)
				if (st != null) {
					Position at = st.getPosition().copy();
					st.skipReachCheck = pos -> Math.max(Math.abs(pos.getX() - at.getX()), Math.abs(pos.getY() - at.getY())) <= 2;
				}
	}

	/** Jump (op1) on a stone: over the chasm to the far bank, human_yama_shortjump01 and a 27-cycle exact move. */
	public void jumpStone(Player player, GameObject stone) {
		if (phase == Phase.INTRO || phase == Phase.DEAD || !isStone(stone))
			return;
		int sx = ox(stone.getPosition()), sy = oy(stone.getPosition());
		int px = ox(player.getPosition()), py = oy(player.getPosition());
		Position to;
		if (sx == 0) // a north/south arm: across in x
			to = o(px < 0 ? 2 : -2, sy);
		else
			to = o(sx, py < 0 ? 2 : -2);
		int q = quadrantOf(to);
		if (q >= 0 && fallen[q]) {
			player.sendMessage("There is nothing left to land on.");
			return;
		}
		player.animate(SEQ_JUMP);
		Raid4Fx.slide(player, to, 27, Raid4Fx.angle(player.getAbsX(), player.getAbsY(), to.getX(), to.getY()));
	}

	/* ---------------------------------------------------------------- §4 phase 3 */

	private int quarterTarget() {
		return 75 - 25 * quadrantsFallen;
	}

	private void startSegment() {
		long s = schedule.now();
		int[] c = CORNERS[quadrant];
		Position to = o(c[0], c[1]);
		Raid4Fx.teleportNow(boss, to);
		boss.face(o(0, 0));
		Raid4Fx.fade(boss, 0, 28, 255, 0);
		setHeadicon(-1);
		spunThisSegment = false;
		vineDone = false;
		pickTarget();
		// the barriers
		int[][] base = { { 8, 1, 1 }, { 1, 7, 2 } };
		for (int[] b : base) {
			int x = b[0], y = b[1], r = b[2];
			for (int t = 0; t < quadrant; t++) { // a quarter clockwise each quadrant
				int nx = y, ny = -x;
				x = nx;
				y = ny;
				r = (r + 1) % 4;
			}
			locs.add(instance.spawnObject(b == base[0] ? LOC_BARRIER_1 : LOC_BARRIER_2, o(x, y), 10, r));
		}
		schedule.after(2, () -> {
			if (boss.avatar() != null)
				boss.avatarExtended().setAllOpsVisible();
		});
		schedule.after(6, this::voidBombs);
		if (segment == 0)
			schedule.after(9, this::voidBombs);
		if (segment > 0) {
			riftKind = riftKind < 0 ? Random.get(2) : (riftKind + 1) % 3;
			for (int t : new int[] { 3, 51, 99 })
				schedule.after(t, () -> {
					if (phase == Phase.P3 && !falling)
						spawnRifts(riftKind, Math.max(1, fighters().size() / 2), 2, 9);
				});
		}
		nextP3Attack = s + 4;
	}

	private void pickTarget() {
		List<Player> pool = fighters();
		if (pool.isEmpty()) {
			target = null;
			return;
		}
		target = pool.get(Random.get(pool.size() - 1));
	}

	/** Void bombs, one on each standing quadrant; the patch stays and hurts 6..7 every 2 ticks. */
	private void voidBombs() {
		if (phase != Phase.P3 && phase != Phase.P4)
			return;
		for (int q = 0; q < 4; q++) {
			if (fallen[q])
				continue;
			Position t = o(qsx(q) * Random.get(3, 11), qsy(q) * Random.get(3, 11));
			Raid4Fx.mapAnim(SPOT_BOMB_IDLE, t, 0, 255);
			Raid4Fx.mapAnim(SPOT_BOMB_IDLE, t, 30, 255);
			schedule.after(2, () -> {
				Raid4Fx.mapAnim(SPOT_BOMB_DROP, t, 0, 0);
				Raid4Fx.mapAnim(SPOT_BOMB_IMPACT, t, 0, 0);
				for (Player p : fighters())
					if (p.getPosition().getX() == t.getX() && p.getPosition().getY() == t.getY()) {
						hitPlayer(p, new Hit().randDamage(6, 11).ignoreDefence().ignorePrayer());
						patchDue.put(p, schedule.now() + 2);
					}
				long k = key(t);
				if (!patches.containsKey(k))
					patches.put(k, instance.spawnObject(LOC_BOMB, t, 22, Random.get(3)));
			});
		}
	}

	private void tickPatches(long now) {
		if (patches.isEmpty())
			return;
		for (Player p : fighters()) {
			if (!patches.containsKey(key(p.getPosition()))) {
				continue;
			}
			Long due = patchDue.get(p);
			if (due == null || now >= due) {
				hitPlayer(p, new Hit().randDamage(6, 7).ignoreDefence().ignorePrayer());
				patchDue.put(p, now + 2);
			}
		}
	}

	/** Void rifts: 0 Augmenter, 1 Void Rift, 2 healing rift; on floor tiles {@code min}..{@code max} from his 3x3. */
	private void spawnRifts(int kind, int count, int min, int max) {
		int id = kind == 0 ? NPC_VOID_AUGMENTER : kind == 1 ? NPC_VOID_RIFT : NPC_HEAL_RIFT;
		for (int i = 0; i < count; i++) {
			Position t = null;
			for (int tries = 0; tries < 30 && t == null; tries++) {
				Position c = boss.getPosition().relative(Random.get(-max, max + 2), Random.get(-max, max + 2));
				int d = distance(boss.getPosition(), 3, c);
				if (d >= min && d <= max && quadrantOf(c) >= 0 && Tile.get(c.getX(), c.getY(), c.getZ(), true).clipping == 0)
					t = c;
			}
			if (t == null)
				continue;
			NPC rift = spawnTemp(id, t, Direction.SOUTH);
			((Raid4Puppet) rift.getCombat()).bind(this);
			Stat hp = rift.getCombat().getStat(StatType.Hitpoints);
			hp.fixedLevel = 44;
			hp.currentLevel = 44;
			if (kind == 2)
				healRift(rift);
		}
	}

	/** The healing rift: every 4 ticks a shot at his middle, a heal of 12 or 14, unless a raider stands on the line. */
	private void healRift(NPC rift) {
		schedule.after(4, () -> {
			if (rift.isRemoved() || rift.getHp() <= 0 || phase == Phase.DEAD)
				return;
			Position mid = boss.getPosition().relative(1, 1);
			Raid4Fx.projectile(fighters(), rift, SPOT_STRIKE_PROJ, rift.getPosition(), boss, mid, 160, 140, 0, 30, 2, 0);
			boolean blocked = false;
			for (Player p : fighters())
				if (onLine(rift.getPosition(), mid, p.getPosition()))
					blocked = true;
			if (!blocked)
				boss.hit(new Hit(HitType.HEAL).fixedDamage(Random.get(1) == 0 ? 12 : 14));
			healRift(rift);
		});
	}

	private static boolean onLine(Position a, Position b, Position p) {
		int steps = Math.max(Math.abs(b.getX() - a.getX()), Math.abs(b.getY() - a.getY()));
		for (int i = 1; i < steps; i++) {
			int x = a.getX() + Math.round((b.getX() - a.getX()) * i / (float) steps);
			int y = a.getY() + Math.round((b.getY() - a.getY()) * i / (float) steps);
			if (x == p.getX() && y == p.getY())
				return true;
		}
		return false;
	}

	private void tickP3(long now) {
		if (falling)
			return;
		double h = 100.0 * boss.getHp() / p2Max;
		int q = quarterTarget();
		// the fall at Q + 1, at once unless spinning or unleashing
		if (h <= q + 1 && !spinning && !unleashing) {
			fall();
			return;
		}
		if (now < nextP3Attack || spinning || unleashing)
			return;
		if (target == null || !fighters().contains(target))
			pickTarget();
		if (target == null)
			return;
		int slot = rotation % 3;
		rotation++;
		if (slot < 2) {
			if (spunThisSegment && !vineDone && h > q + 8)
				vine();
			triple(now);
		} else if (insolenceNext) {
			insolenceNext = false;
			if (h <= q + 8)
				untether(now);
			else
				insolence(now);
		} else {
			insolenceNext = true;
			spin(now);
		}
	}

	/** The triple: basic +0, +3, the slash +6 (a short line); the volley first when his target is not beside him. */
	private void triple(long now) {
		boss.face(target);
		if (distance(boss.getPosition(), 3, target.getPosition()) > 1) {
			boss.animate(SEQ_VOLLEY);
			Raid4Fx.spot(boss, 4, SPOT_MAGIC_CAST, 30, 0, false);
			Position mid = boss.getPosition().relative(1, 1);
			for (Player p : fighters()) {
				Raid4Fx.projectile(fighters(), boss, SPOT_MAGIC_PROJ, mid, p, p.getPosition(), 400, 100, 30, 90, 20, 0);
				schedule.after(3, () -> {
					if (!fighters().contains(p))
						return;
					Raid4Fx.spot(p, 2, SPOT_MAGIC_HIT, 0, 0);
					int dmg = p.getPrayer().isActive(Prayer.PROTECT_FROM_MAGIC) ? 0 : Random.get(25);
					hitPlayer(p, new Hit(boss, AttackStyle.MAGIC).fixedDamage(dmg).ignoreDefence().ignorePrayer());
				});
			}
			nextP3Attack = now + 3;
			rotation--; // the triple follows 3 later
			return;
		}
		Player t = target;
		for (int i = 0; i < 2; i++)
			schedule.after(Math.max(1, 3 * i), () -> swing(t));
		schedule.after(6, () -> slash(t));
		nextP3Attack = now + 10;
	}

	private void swing(Player t) {
		if (phase != Phase.P3 || falling || !fighters().contains(t))
			return;
		boss.animate(SEQ_MELEE);
		boss.face(t);
		boolean prayed = t.getPrayer().isActive(Prayer.PROTECT_FROM_MELEE);
		int dmg = prayed ? Random.get(1, 7) : Random.get(28);
		hitPlayer(t, new Hit(boss, AttackStyle.SLASH).fixedDamage(dmg).ignoreDefence().ignorePrayer());
	}

	private void slash(Player t) {
		if (phase != Phase.P3 || falling)
			return;
		boss.animate(SEQ_SLASH);
		Position mid = boss.getPosition().relative(1, 1);
		Position tt = t.getPosition();
		int dx = Integer.signum(tt.getX() - mid.getX()), dy = Integer.signum(tt.getY() - mid.getY());
		Set<Long> line = new HashSet<>();
		for (int i = 2; i <= 5; i++) {
			Position p = mid.relative(dx * i, dy * i);
			line.add(key(p));
			Raid4Fx.mapAnim(SPOT_SLASH, p, 0, 0);
		}
		for (Player p : fighters())
			if (line.contains(key(p.getPosition())))
				hitPlayer(p, new Hit(boss, AttackStyle.SLASH).randDamage(25, 28).ignoreDefence().ignorePrayer());
	}

	/** The spin: prayer_in, a loop 3 apart for each quadrant fallen, the out; every saw blocked by Protect from Melee,
	 * 15..27 through it; leaving Protect from Melee up between saws injures the raider. */
	private void spin(long now) {
		spinning = true;
		spunThisSegment = true;
		boss.animate(SEQ_PRAYER_IN);
		Raid4Fx.spot(boss, 4, SPOT_PRAYER_IN);
		int loops = quadrantsFallen + 1;
		for (int i = 0; i < loops; i++) {
			int at = 2 + 3 * i;
			schedule.after(at, () -> {
				if (phase != Phase.P3)
					return;
				boss.animate(SEQ_PRAYER_LOOP);
				Raid4Fx.spot(boss, 4, SPOT_PRAYER);
				saw();
			});
		}
		int out = 5 + 3 * (loops - 1);
		schedule.after(out, () -> {
			if (phase != Phase.P3)
				return;
			boss.animate(SEQ_PRAYER_OUT);
			Raid4Fx.spot(boss, 4, SPOT_PRAYER_OUT);
			saw();
		});
		schedule.after(out + 1, () -> spinning = false);
		nextP3Attack = now + out + 4;
	}

	private void saw() {
		for (Player p : fighters()) {
			if (!p.getPrayer().isActive(Prayer.PROTECT_FROM_MELEE))
				hitPlayer(p, new Hit(boss, AttackStyle.SLASH).randDamage(15, 27).ignoreDefence().ignorePrayer());
		}
	}

	/** Insolence: the line, a reset, three superior slashes at +2, +7, +12 on its target's tile; 39..41. */
	private void insolence(long now) {
		boss.forceText("Insolence...");
		Raid4Fx.resetFace(boss);
		for (int i = 0; i < 3; i++) {
			schedule.after(2 + 5 * i, () -> {
				if (phase != Phase.P3 || falling)
					return;
				if (target == null || !fighters().contains(target))
					pickTarget();
				if (target == null)
					return;
				Position tt = target.getPosition().copy();
				boss.face(target);
				boss.animate(SEQ_SUPERIOR, 30);
				Position mid = boss.getPosition().relative(1, 1);
				int dx = Integer.signum(tt.getX() - mid.getX()), dy = Integer.signum(tt.getY() - mid.getY());
				Set<Long> line = new HashSet<>();
				for (int k = 2; k <= 12; k++) {
					Position p = mid.relative(dx * k, dy * k);
					line.add(key(p));
					Raid4Fx.mapAnim(SPOT_SUPERIOR, p, 30, 0);
				}
				schedule.after(1, () -> {
					for (Player p : fighters())
						if (line.contains(key(p.getPosition())))
							hitPlayer(p, new Hit(boss, AttackStyle.SLASH).randDamage(39, 41).ignoreDefence().ignorePrayer());
				});
			});
		}
		nextP3Attack = now + 20;
	}

	/** Untether (within 8% of the next quarter): pools, the unleash, the slam at U+29 (34..39, then 18..19). */
	private void untether(long now) {
		unleashing = true;
		boss.forceText("Untether...");
		int[] c = CORNERS[quadrant];
		Position to = o(c[0], c[1]);
		Raid4Fx.slide(boss, to, 90, Raid4Fx.angle(boss.getAbsX(), boss.getAbsY(), to.getX(), to.getY()));
		boss.animate(SEQ_UNLEASH_IN);
		Raid4Fx.walkStance(boss, SEQ_UNLEASH_LOOP);
		prayMelee = true;
		setHeadicon(0);
		int q = quadrant;
		for (int t : new int[] { 6, 10, 14, 18 }) {
			schedule.after(t, () -> {
				if (phase != Phase.P3)
					return;
				int n = Random.get(10, 15);
				List<Position> pools = new ArrayList<>();
				for (int i = 0; i < n; i++) {
					Position p = o(qsx(q) * Random.get(2, 12), qsy(q) * Random.get(2, 12));
					pools.add(p);
					Raid4Fx.mapAnim(SPOT_POOL, p, 0, 0);
				}
				schedule.after(2, () -> {
					for (Player p : fighters())
						for (Position pool : pools)
							if (p.getPosition().getX() == pool.getX() && p.getPosition().getY() == pool.getY()) {
								int dmg = p.getPrayer().isActive(Prayer.PROTECT_FROM_MAGIC) ? 0 : Random.get(14, 29);
								hitPlayer(p, new Hit(boss, AttackStyle.MAGIC).fixedDamage(dmg).ignoreDefence().ignorePrayer());
								break;
							}
				});
			});
		}
		schedule.after(18, () -> Raid4Fx.spot(boss, 0, SPOT_UNLEASH, 360, 0, false));
		schedule.after(25, () -> Raid4Fx.mapAnim(SPOT_UNLEASH_ARENA + (q == 0 ? 2 : q == 3 ? 1 : 0), o(qsx(q) * 7, qsy(q) * 7), 0, 0));
		Set<Player> slammed = new HashSet<>();
		schedule.after(29, () -> {
			if (phase != Phase.P3)
				return;
			boss.animate(SEQ_UNLEASH_OUT);
			for (Player p : fighters())
				if (quadrantOf(p.getPosition()) == q && distance(boss.getPosition(), 3, p.getPosition()) > 1) {
					slammed.add(p);
					hitPlayer(p, new Hit(boss).randDamage(34, 39).ignoreDefence().ignorePrayer());
				}
		});
		schedule.after(30, () -> {
			Raid4Fx.resetStance(boss);
			Raid4Fx.resetFace(boss);
			for (Player p : slammed)
				if (fighters().contains(p))
					hitPlayer(p, new Hit(boss).randDamage(18, 19).ignoreDefence().ignorePrayer());
			unleashing = false;
		});
		schedule.after(48, () -> {
			prayMelee = false;
			if (phase == Phase.P3)
				setHeadicon(-1);
		});
		nextP3Attack = now + 37;
	}

	/** The vine: its line as the triple starts, the vine 3 ticks on, a raider rooted; 50 at +12 unless cut. */
	private void vine() {
		vineDone = true;
		String[] lines = { "The void... will take you...", "Your panic... is futile...", "Await... the void..." };
		boss.forceText(lines[Random.get(2)]);
		List<Player> pool = fighters();
		if (pool.isEmpty())
			return;
		Player victim = pool.get(Random.get(pool.size() - 1));
		schedule.after(3, () -> {
			if (phase != Phase.P3 || !fighters().contains(victim))
				return;
			NPC vine = spawnTemp(NPC_VOID_VINE, victim.getPosition().copy(), Direction.SOUTH);
			((Raid4Puppet) vine.getCombat()).bind(this);
			Raid4Fx.spot(victim, 2, SPOT_ENTANGLE_START, 0, 0);
			victim.freezeDelay.delay(12);
			victim.getMovement().reset();
			schedule.after(12, () -> {
				if (vine.isRemoved() || vine.getHp() <= 0) {
					victim.freezeDelay.reset();
					return;
				}
				Raid4Fx.spot(victim, 2, SPOT_ENTANGLE_END, 0, 0);
				if (fighters().contains(victim))
					hitPlayer(victim, new Hit(boss).fixedDamage(50).ignoreDefence().ignorePrayer());
				removeNpc(vine);
			});
		});
	}

	/** The fall: "Obliteration..."; D+11 the out; D+13 the quadrant gone (anyone left on it dies); D+17 the next. */
	private void fall() {
		falling = true;
		int q = quadrant;
		boss.forceText(Random.get(1) == 0 ? "Obliteration..." : "Run... It matters not...");
		boss.animate(SEQ_DESTROY_IN);
		Raid4Fx.spot(boss, 2, SPOT_COLLAPSE_IN);
		Raid4Fx.walkStance(boss, SEQ_DESTROY_LOOP);
		Raid4Fx.resetFace(boss);
		schedule.after(2, () -> Raid4Fx.spot(boss, 2, SPOT_COLLAPSE_LOOP, 0, 0, true));
		schedule.after(11, () -> {
			String[] lines = { "You can't... escape... the end...", "This end... comes...", "Everything... ends..." };
			boss.forceText(lines[Random.get(2)]);
			boss.animate(SEQ_DESTROY_OUT);
			Raid4Fx.spot(boss, 2, SPOT_COLLAPSE_OUT_1);
			Raid4Fx.spot(boss, 4, SPOT_COLLAPSE_OUT_2);
			Raid4Fx.resetStance(boss);
		});
		schedule.after(12, () -> Raid4Fx.fade(boss, 0, 30, 0, 255));
		// our warning on top of the recording (2026-10-29, testers missed the quadrant going): the floor rumbles under
		// everyone for the last four ticks, and dust breaks out along the quadrant's edges as it goes
		schedule.after(9, () -> {
			for (Player p : fighters())
				p.getPacketSender().shakeCamera(2, 3);
		});
		schedule.after(13, () -> {
			for (int x = 2; x <= 12; x++)
				for (int y = 2; y <= 12; y++)
					if (x == 2 || y == 2 || x == 12 || y == 12)
						Raid4Fx.mapAnim(BorkRoom.SPOT_DUST, o(qsx(q) * x, qsy(q) * y), Math.min(x, y) * 4, 0);
		});
		schedule.after(15, () -> {
			for (Player p : fighters())
				p.getPacketSender().resetCamera();
		});
		schedule.after(13, () -> {
			fallen[q] = true;
			quadrantsFallen++;
			sliceCollapse(q);
			for (Player p : fighters())
				if (quadrantOf(p.getPosition()) == q)
					voidFall(p);
			for (int x = 2; x <= 12; x++)
				for (int y = 2; y <= 12; y++)
					block(o(qsx(q) * x, qsy(q) * y));
		});
		schedule.after(17, () -> {
			falling = false;
			segment++;
			if (segment >= 3) {
				quadrant = (q + 1) % 4; // the last quadrant
				for (int k = 0; k < 4; k++)
					if (!fallen[(q + 1 + k) % 4]) {
						quadrant = (q + 1 + k) % 4;
						break;
					}
				phase4Jump();
				return;
			}
			int next = (q + 3) % 4; // counter-clockwise
			for (int k = 0; k < 4 && fallen[next]; k++)
				next = (next + 3) % 4;
			quadrant = next;
			startSegment();
		});
	}

	/**
	 * A raider left on a fallen quadrant: 60..80 from the void, and they are pulled back onto a standing quadrant (the
	 * nearest one, clockwise), never left floating in the blocked void.
	 */
	private void voidFall(Player p) {
		int from = quadrantOf(p.getPosition());
		int to = -1;
		for (int k = 1; k <= 4 && to < 0; k++) {
			int q = Math.floorMod((from < 0 ? 0 : from) + k, 4);
			if (!fallen[q])
				to = q;
		}
		if (to < 0)
			return;
		p.getMovement().reset();
		hitPlayer(p, new Hit(boss).randDamage(60, 80).ignoreDefence().ignorePrayer());
		p.sendMessage("The void drags at you as the floor gives way!");
		Raid4Fx.teleportNow(p, quadrantTile(to, 3, 8));
		p.graphics(409);
	}

	/** Every tick in phases 3 and 4: nobody stays on a fallen quadrant. */
	private void sweepFallen() {
		for (Player p : fighters()) {
			int q = quadrantOf(p.getPosition());
			if (q >= 0 && fallen[q])
				voidFall(p);
		}
	}

	/* The censers (phase 3, once each, while the raiders hold its quadrant) */

	public void useCenser(Player player, GameObject obj) {
		int idx = -1;
		for (int i = 0; i < 4; i++)
			if (CENSERS[i][2] == obj.getId())
				idx = i;
		if (idx < 0 || censerUsed[idx]) {
			player.sendMessage("The censer has burnt out for now.");
			return;
		}
		if (phase != Phase.P3 || quadrant != idx) {
			player.sendMessage("The censer is cold.");
			return;
		}
		censerUsed[idx] = true;
		obj.animate(SEQ_CENSER_ACTIVATE);
		int i = idx;
		schedule.after(1, () -> {
			for (Player p : fighters()) {
				Raid4Fx.spot(p, 1, CENSER_GLOW[i], 0, 0);
				switch (i) {
					case 0 -> censerBuff.put(p, new int[] { 1 });
					case 2 -> censerBuff.put(p, new int[] { 2 });
					case 3 -> p.getCombat().restoreSpecial(50);
					default -> {
						p.incrementHp(50);
						p.getStats().get(StatType.Prayer).restore(50, 0);
					}
				}
			}
		});
		schedule.after(3, () -> {
			Position at = obj.getPosition().copy();
			int rot = obj.getDirection();
			instance.removeObject(obj);
			locs.remove(obj);
			locs.add(instance.spawnObject(CENSERS[i][2] + 1, at, 10, rot));
		});
	}

	/* ---------------------------------------------------------------- §5 phase 4 */

	private void phase4Jump() {
		int[] c = CORNERS[quadrant];
		Raid4Fx.teleportNow(boss, o(c[0], c[1]));
		boss.face(o(0, 0));
		Raid4Fx.fade(boss, 0, 28, 255, 0);
		setHeadicon(-1);
		schedule.after(1, () -> {
			phase = Phase.P4;
			boss.forceText("End...");
			block3x3(boss.getPosition().copy());
			stonesDespawn(quadrant);
			d0 = schedule.now() + 3;
		});
	}

	private void tickP4(long now) {
		if (now < d0)
			return;
		long d = now - d0;
		if (d == 0) {
			cycles++;
			pickTarget();
			boss.animate(SEQ_DEFLECT_IN);
			setHeadicon(15);
			Raid4Fx.walkStance(boss, SEQ_DEFLECT_LOOP);
			if (target != null)
				boss.face(target);
			voidRing.clear();
			Position sw = boss.getPosition();
			for (int x = -1; x <= 3; x++)
				for (int y = -1; y <= 3; y++)
					voidRing.add(key(sw.relative(x, y)));
		}
		if (d == 3 || d == 34) {
			boss.animate(SEQ_SOUL_START);
			for (Player p : fighters()) {
				Raid4Fx.spot(p, 2, SPOT_PLAYER_FRAGMENT, 0, 0);
				p.animate(p.getCombat().getDefendAnimation(), 30);
			}
			if (d == 34)
				Raid4Fx.spot(boss, 2, SPOT_FRAGMENT_IDLE, 0, 0, true);
		}
		if (d == 4 || d == 35) {
			if (d == 4)
				Raid4Fx.spot(boss, 2, SPOT_FRAGMENT_LEVITATE, 0, 0, true);
			wind(now, d == 4);
		}
		if (d >= 9 && d <= 19 && (d - 9) % 2 == 0 || d >= 40 && d <= 50 && (d - 40) % 2 == 0)
			explosionStart();
		if (d == 21) {
			boss.animate(SEQ_DEFLECT_OUT);
			Raid4Fx.clearSpot(boss, 2);
			Raid4Fx.resetStance(boss);
			Raid4Fx.resetFace(boss);
		}
		if (d == 24 || d == 52)
			hybrid();
		if (d == 25 || d == 53)
			setHeadicon(11);
		if (d == 30 || d == 59)
			setHeadicon(-1);
		if (d == 31)
			setHeadicon(16);
		if (d == 50) {
			boss.animate(SEQ_SOUL_END);
			Raid4Fx.clearSpot(boss, 2);
		}
		if (d == 59)
			spawnRifts(1, Math.min(4, cycles + 1), 2, 5);
		// the void floor: his ring D+4..21, the rest of the quadrant D+35..50: 10 a tick
		for (Player p : fighters()) {
			boolean inRing = distance(boss.getPosition(), 3, p.getPosition()) <= 1;
			if ((d >= 4 && d <= 21 && inRing) || (d >= 35 && d <= 50 && !inRing))
				hitPlayer(p, new Hit().fixedDamage(10).ignoreDefence().ignorePrayer());
		}
		tickOrbs(now);
		if (d >= 59)
			d0 += 60;
	}

	/** The wind: every raider hit half their health; an orb for each at W+12 (2..5 from him) or W+6 (1 from him). */
	private void wind(long now, boolean first) {
		for (Player p : fighters()) {
			int hit = p.getHp() / 2;
			hitPlayer(p, new Hit(boss).fixedDamage(hit).ignoreDefence().ignorePrayer());
			int ready = first ? 12 : 6;
			Position t = null;
			for (int tries = 0; tries < 30 && t == null; tries++) {
				Position c = boss.getPosition().relative(Random.get(-5, 7), Random.get(-5, 7));
				int dd = distance(boss.getPosition(), 3, c);
				if ((first ? dd >= 2 && dd <= 5 : dd == 1) && quadrantOf(c) == quadrant)
					t = c;
			}
			if (t == null)
				t = boss.getPosition().relative(-1, 1);
			Position orb = t;
			orbs.put(p, new int[] { orb.getX(), orb.getY(), (int) (now + ready), hit });
			Raid4Fx.mapAnim(first ? 4311 : 4309, orb, 0, 0);
			schedule.after(ready, () -> Raid4Fx.mapAnim(SPOT_ORB, orb, 0, 0));
			int unclaimed = first ? 25 : 19;
			schedule.after(unclaimed, () -> {
				int[] o = orbs.remove(p);
				if (o != null && phase == Phase.P4) {
					boss.hit(new Hit(HitType.HEAL).fixedDamage(o[3]));
					Raid4Fx.sound(fighters(), 5281, boss.getPosition().relative(1, 1), 0, 9);
				}
			});
		}
	}

	private void tickOrbs(long now) {
		for (var it = orbs.entrySet().iterator(); it.hasNext();) {
			var e = it.next();
			Player p = e.getKey();
			int[] o = e.getValue();
			if (now < o[2] || !fighters().contains(p))
				continue;
			if (p.getAbsX() == o[0] && p.getAbsY() == o[1]) {
				p.incrementHp(o[3] + (now == o[2] ? 8 : 0));
				Raid4Fx.spot(p, 2, SPOT_ORB_REGAIN, 0, 0);
				it.remove();
			}
		}
	}

	/** An explosion starts on each raider's tile and lands the next tick on whoever stayed: 14..16. */
	private void explosionStart() {
		for (Player p : fighters()) {
			Position t = p.getPosition().copy();
			Raid4Fx.spot(p, 2, SPOT_CHAIN_TELEGRAPH, 0, 0);
			Raid4Fx.mapAnim(SPOT_CHAIN_EXPLOSION, t, 30, 0);
			schedule.after(1, () -> {
				if (fighters().contains(p) && p.getAbsX() == t.getX() && p.getAbsY() == t.getY()) {
					p.animate(p.getCombat().getDefendAnimation());
					hitPlayer(p, new Hit(boss).randDamage(14, 16).ignoreDefence().ignorePrayer());
				}
			});
		}
	}

	/** The hybrid: one roll for the party, 5..8 under the matching prayer (20..30 without, ours). */
	private void hybrid() {
		int kind = Random.get(2); // 0 magic, 1 ranged, 2 melee
		boss.animate(SEQ_HYBRID);
		Raid4Fx.spot(boss, 2, kind == 0 ? SPOT_HYBRID_MAGIC : kind == 1 ? SPOT_HYBRID_RANGED : SPOT_HYBRID);
		int roll = 5 + Random.get(1) + Random.get(1) + Random.get(1);
		int unprayed = Random.get(20, 30);
		schedule.after(5, () -> {
			for (Player p : fighters()) {
				Prayer need = kind == 0 ? Prayer.PROTECT_FROM_MAGIC : kind == 1 ? Prayer.PROTECT_FROM_MISSILES : Prayer.PROTECT_FROM_MELEE;
				p.animate(p.getCombat().getDefendAnimation());
				hitPlayer(p, new Hit(boss).fixedDamage(p.getPrayer().isActive(need) ? roll : unprayed).ignoreDefence().ignorePrayer());
			}
		});
	}

	/* ---------------------------------------------------------------- §6 death */

	@Override
	public void onNpcDying(NPC npc) {
		if (npc != boss || phase == Phase.DEAD)
			return;
		phase = Phase.DEAD;
		for (GameObject patch : patches.values())
			instance.removeObject(patch);
		patches.clear();
		setHeadicon(-1);
		Raid4Fx.spot(boss, 0, SPOT_DEATH_SKIRT, 1, 0, false);
		Raid4Fx.spot(boss, 5, SPOT_DEATH, 1, 0, false);
		Raid4Fx.spot(boss, 3, SPOT_DEATH_CRACK, 1, 0, false);
		if (boss.avatar() != null)
			boss.avatarExtended().setAllOpsInvisible();
		controller.getParty().message("The Fractured Archive: Total completion time: <col=ef1020>" + clock(controller.ticks()) + "</col>");
		Position tile = boss.getPosition().copy();
		schedule.after(14, () -> spawn(NPC_BLADE_VAULT_ENTRY, tile, Direction.SOUTH));
	}

	private static String clock(long ticks) {
		long s = ticks * 600 / 1000;
		return (s / 60) + ":" + String.format("%02d", s % 60);
	}

	@Override
	public void onNpcDeath(NPC npc) {
		if (npc != boss || complete)
			return;
		finish("Ocellus dead");
		schedule.after(5, () -> {
			for (Player p : hud)
				p.getHealthHud().close();
			hud.clear();
		});
	}

	/** No navigator; The Blade (16683) already stands on his tile (K+14). */
	@Override
	protected void afterClear() {
	}

	/* ---------------------------------------------------------------- helpers */

	/** His overhead, sprite group 440: 0 Protect from Melee, 11 Soul Split, 15 Deflect Melee, 16 Deflect Missiles. */
	private void setHeadicon(int index) {
		headicon = index;
		if (boss.avatar() == null)
			return;
		if (index < 0)
			boss.avatarExtended().resetHeadIcons();
		else
			boss.avatarExtended().setHeadIconChange(0, 440, index);
	}

	private void updateHud() {
		if (!started || phase == Phase.INTRO)
			return;
		int max = phase == Phase.P1 ? p1Max : p2Max;
		int hp = phase == Phase.DEAD ? 0 : Math.max(0, boss.getHp());
		for (Player p : fighters())
			if (hud.add(p) || !p.getHealthHud().isOpened()) {
				p.getHealthHud().open(true, boss.getId(), max);
				shownHp = -1;
				shownMeter = -1;
			}
		if (hp != shownHp) {
			shownHp = hp;
			for (Player p : hud) {
				p.getHealthHud().updateMaxValue(max);
				p.getHealthHud().updateValue(hp);
			}
		}
		int m = phase == Phase.P1 ? meter : -1;
		if (m != shownMeter) {
			shownMeter = m;
			for (Player p : hud)
				p.getHealthHud().extraBar(Math.max(0, m), phase == Phase.P1 ? METER_MAX : 0);
		}
	}

	private void block3x3(Position sw) {
		for (int x = 0; x < 3; x++)
			for (int y = 0; y < 3; y++)
				block(sw.relative(x, y));
	}

	private void block(Position p) {
		Tile tile = Tile.get(p.getX(), p.getY(), p.getZ(), true);
		if ((tile.clipping & 0x200000) == 0) {
			tile.flagUnmovable();
			blocked.add(tile);
		}
	}

	private void unblockAll() {
		for (Tile t : blocked)
			t.unflagUnmovable();
		blocked.clear();
	}

	@Override
	protected void clearHazards() {
		super.clearHazards();
		for (GameObject patch : patches.values())
			instance.removeObject(patch);
		patches.clear();
	}

	@Override
	public void cleanup() {
		super.cleanup();
		for (GameObject g : locs)
			instance.removeObject(g);
		locs.clear();
		for (GameObject[] arm : portals)
			for (GameObject g : arm)
				if (g != null)
					instance.removeObject(g);
		unblockAll();
		for (Player p : hud)
			p.getHealthHud().close();
		hud.clear();
	}

	@Override
	public Position entryPosition() {
		return walkable(arenaEntry(), 1);
	}
}
