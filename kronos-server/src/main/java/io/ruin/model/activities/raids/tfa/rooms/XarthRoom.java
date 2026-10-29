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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.ruin.model.activities.raids.tfa.Raid4Ids.*;

/**
 * Slot 4 east: Xarth (region 16726), rebuilt 2026-10-28 from the RSProx captures (index 4301/4302/4304/4308,
 * docs/rsprox-analysis/guthixian_archive/xarth_plan.md). Section numbers (§) refer to that plan. Local coordinates =
 * absolute - (4160, 5504) (template square (65,86)). He is 3x3 at (30,30) and never moves.
 */
@Slf4j
public class XarthRoom extends Raid4BossRoom {

	/* §1 */
	static final int SW_X = 30, SW_Y = 30, MID_X = 31, MID_Y = 31;
	static final int ARRIVE_X = 12, ARRIVE_Y = 31; // (4172,5535)
	static final int TRANSPORTER_SIDE_X = 17, TRANSPORTER_SIDE_Y = 31; // beside (4178,5535)
	static final int LANDING_X = 24, LANDING_Y = 31; // (4184,5535)
	static final int NAVIGATOR_X = 23, NAVIGATOR_Y = 31; // (4183,5535) facing east
	static final int ARENA_MIN = 19, ARENA_MAX = 43;
	/** The three pillars, relative to his south-west tile: {dx, dy, loc}; the map's own rubble (36,32), (33,36), (33,26). */
	static final int[][] PILLARS = { { 6, 2, 62711 }, { 3, 6, 62712 }, { 3, -4, 62712 } };
	/** The map's eight corpse piles {local x, y, pile loc}; at D each becomes its fire form (pile id + 1) in place. */
	static final int[][] PILES = { { 31, 41, 62747 }, { 41, 31, 62749 }, { 31, 20, 62751 }, { 21, 31, 62753 }, { 20, 40, 62755 },
			{ 40, 40, 62757 }, { 40, 20, 62759 }, { 20, 20, 62761 } };

	/* Timing */
	static final int FIRST_ATTACK = 6, CYCLE = 31, VOLLEY_EVERY = 21, FIRE_RING = 7;

	/* Damage (§ damage audit) */
	static final int FIREBALL_MAX = 27, FIREBALL_PRAYED_MIN = 1, FIREBALL_PRAYED_MAX = 8, ARROW_MIN = 11, ARROW_MAX = 29,
			MELEE_MAX = 30, AOE_MIN = 15, AOE_MAX = 40, BLAST_MIN = 30, BLAST_MAX = 60, WAVE_MIN = 40, WAVE_MAX = 50;

	/* Ids */
	static final int SEQ_MELEE = 14983, SEQ_MAGIC = 14984, SEQ_RANGED = 14985, SEQ_BLAST = 14989, SEQ_AOE = 14992,
			SEQ_RUBBLE = 14997, SEQ_CORPSE_ABSORB = 15005;
	static final int SPOT_FIREBALL = 4034, SPOT_FIREBALL_IMPACT = 2854, SPOT_MELEE = 2851, SPOT_RANGED_CAST = 4373,
			SPOT_RANGED_PROJ = 4374, SPOT_RANGED_HIT = 4375, SPOT_AOE_STUN = 4377, SPOT_AOE_PROJ = 4378, SPOT_AOE_HIT = 4379,
			SPOT_BLAST = 4380, SPOT_DEATH = 4381, SPOT_RUBBLE = 4382, SPOT_CORPSE_FIRE = 4383, SPOT_CORPSE_DOUSED = 4384,
			SPOT_CORPSE_DEATH = 4385, SPOT_CORPSE_ABSORBED = 4386, SPOT_ABSORB = 4387, SPOT_SHADOW_90 = 1446,
			SPOT_FIRE_WAVE = 3310;
	static final int NPC_RUBBLE_DESTROYED = 16686;

	private NPC xarth;
	private int maxHp;
	private boolean dead, phase2;
	private long c; // the current pillar cycle's start
	private int cycles;
	private final NPC[] pillars = new NPC[3];
	private final GameObject[] pillarLocs = new GameObject[3];
	private final List<NPC> corpses = new ArrayList<>();
	private final Map<Long, GameObject> fires = new HashMap<>();
	private final List<GameObject> pileFires = new ArrayList<>();
	/** Burns: player -> {stack 1..5, beats left, next tick}. */
	private final Map<Player, long[]> burns = new HashMap<>();
	private final Set<Player> hud = new HashSet<>();
	private int shownHp = -1;

	@Override
	public Raid4RoomType type() {
		return Raid4RoomType.XARTH;
	}

	@Override
	protected void spawnFixtures() {
		xarth = spawn(NPC_XARTH, local(SW_X, SW_Y), Direction.WEST);
		((Raid4Puppet) xarth.getCombat()).bind(this);
		xarth.face(local(13, 31));
		for (int x = SW_X; x < SW_X + 3; x++)
			for (int y = SW_Y; y < SW_Y + 3; y++)
				tile(x, y).flagUnmovable();
		for (int i = 0; i < 3; i++) {
			int[] p = PILLARS[i];
			Position at = local(SW_X + p[0], SW_Y + p[1]);
			pillars[i] = spawn(NPC_XARTH_PILLAR, at, Direction.SOUTH); // the rubble loc itself is the map's
		}
	}

	private Tile tile(int x, int y) {
		Position p = local(x, y);
		return Tile.get(p.getX(), p.getY(), p.getZ(), true);
	}

	@Override
	protected int[] arenaBounds() {
		return new int[] { ARENA_MIN, ARENA_MIN, ARENA_MAX, ARENA_MAX };
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
		return "Xarth";
	}

	@Override
	public boolean inArena(Position p) {
		int x = lx(p), y = ly(p);
		return x >= LANDING_X && x <= ARENA_MAX && y >= ARENA_MIN && y <= ARENA_MAX;
	}

	/* ---------------------------------------------------------------- engage */

	@Override
	protected void onStart(Player first) {
		maxHp = Raid4Scaling.tableHp(2600, scale);
		Stat hp = xarth.getCombat().getStat(StatType.Hitpoints);
		hp.fixedLevel = maxHp;
		hp.currentLevel = maxHp;
		xarth.face(first);
		updateHud();
		startCycle(schedule.now() + FIRST_ATTACK);
		log.info("Raid 4: Xarth engaged by {} (scale {}, hp {})", first.getName(), scale, maxHp);
	}

	@Override
	protected void onTick() {
		updateHud();
		tickBurns();
		tickCorpses();
		tickFireTiles();
	}

	/* ---------------------------------------------------------------- §2 phase 1: the pillars */

	private void startCycle(long at) {
		c = at;
		cycles++;
		int delay = (int) Math.max(1, at - schedule.now());
		for (int k = 0; k < 5; k++)
			schedule.after(delay + 4 * k, () -> attackSlot(true));
		schedule.after(delay + 20, this::aoe);
		schedule.after(delay + 23, this::blastTell);
		schedule.after(delay + 24, this::blastHits);
		schedule.after(delay + 26, this::pillarFalls);
	}

	/** A raider beside him is struck in whichever attack slot comes next; otherwise the slot's own attack. */
	private boolean meleeIfBeside() {
		for (Player p : fighters()) {
			if (distance(xarth.getPosition(), 3, p.getPosition()) == 1) {
				xarth.animate(SEQ_MELEE);
				xarth.face(p);
				Raid4Fx.spot(p, 2, SPOT_MELEE, 0, 0);
				hitPlayer(p, new Hit(xarth, AttackStyle.CRUSH).randDamage(p.getPrayer().isActive(Prayer.PROTECT_FROM_MELEE) ? 0 : MELEE_MAX)
						.ignoreDefence().ignorePrayer());
				return true;
			}
		}
		return false;
	}

	private void attackSlot(boolean fireball) {
		if (dead)
			return;
		if (meleeIfBeside())
			return;
		List<Player> pool = fighters();
		pool.removeIf(p -> !inArena(p.getPosition()));
		if (pool.isEmpty())
			return;
		Player p = pool.get(Random.get(pool.size() - 1));
		xarth.face(p);
		Position mid = local(MID_X, MID_Y);
		if (fireball) {
			xarth.animate(SEQ_MAGIC);
			Raid4Fx.projectile(fighters(), xarth, SPOT_FIREBALL, mid, p, p.getPosition(), 470, 120, 30, 90, 0, 160);
			schedule.after(3, () -> {
				if (!fighters().contains(p))
					return;
				Raid4Fx.spot(p, 2, SPOT_FIREBALL_IMPACT, 0, 124);
				boolean prayed = p.getPrayer().isActive(Prayer.PROTECT_FROM_MAGIC);
				int dmg = prayed ? Random.get(FIREBALL_PRAYED_MIN, FIREBALL_PRAYED_MAX) : Random.get(FIREBALL_MAX);
				hitPlayer(p, new Hit(xarth, AttackStyle.MAGIC).fixedDamage(dmg).ignoreDefence().ignorePrayer());
				if (dmg > 0)
					burn(p);
			});
		} else {
			xarth.animate(SEQ_RANGED);
			Raid4Fx.spot(xarth, 2, SPOT_RANGED_CAST);
			Raid4Fx.projectile(fighters(), xarth, SPOT_RANGED_PROJ, mid, p, p.getPosition(), 470, 120, 60, 120, 10, 200);
			schedule.after(4, () -> {
				if (!fighters().contains(p))
					return;
				Raid4Fx.spot(p, 2, SPOT_RANGED_HIT, 0, 0);
				int dmg = p.getPrayer().isActive(Prayer.PROTECT_FROM_MISSILES) ? 0 : Random.get(ARROW_MIN, ARROW_MAX);
				hitPlayer(p, new Hit(xarth, AttackStyle.RANGED).fixedDamage(dmg).ignoreDefence().ignorePrayer());
			});
		}
	}

	/** C+20: every raider rooted 3 ticks; each raider's tile and a random neighbour are struck at C+24. */
	private void aoe() {
		if (dead)
			return;
		xarth.animate(SEQ_AOE);
		Position mid = local(MID_X, MID_Y);
		Set<Long> tiles = new LinkedHashSet<>();
		int[][] n8 = { { 0, 1 }, { 1, 1 }, { 1, 0 }, { 1, -1 }, { 0, -1 }, { -1, -1 }, { -1, 0 }, { -1, 1 } };
		for (Player p : fighters()) {
			Raid4Fx.spot(p, 0, SPOT_AOE_STUN, 0, 0);
			p.freezeDelay.delay(3); // bound 3 ticks (walk again at C+23)
			p.getMovement().reset();
			tiles.add(key(p.getPosition()));
			int[] d = n8[Random.get(7)];
			tiles.add(key(p.getPosition().relative(d[0], d[1])));
		}
		int z = xarth.getPosition().getZ();
		List<Position> struck = new ArrayList<>();
		for (long k : tiles) {
			Position t = new Position((int) (k >> 32), (int) k, z);
			struck.add(t);
			Raid4Fx.projectile(fighters(), xarth, SPOT_AOE_PROJ, mid, null, t, 470, 0, 60, 120, 30, 128);
			Raid4Fx.mapAnim(SPOT_SHADOW_90, t, 30, 0);
			Raid4Fx.mapAnim(SPOT_AOE_HIT, t, 120, 0);
		}
		Raid4Fx.sound(fighters(), 9221, mid, 33, 15);
		Raid4Fx.sound(fighters(), 9335, mid, 3, 15);
		Raid4Fx.sound(fighters(), 9215, mid, 36, 15);
		schedule.after(4, () -> {
			if (dead)
				return;
			for (Position t : struck)
				Raid4Fx.sound(fighters(), 9240, t, 0, 4);
			for (Player p : fighters())
				for (Position t : struck)
					if (p.getPosition().getX() == t.getX() && p.getPosition().getY() == t.getY()) {
						hitPlayer(p, new Hit(xarth).randDamage(AOE_MIN, AOE_MAX).ignoreDefence().ignorePrayer());
						break;
					}
		});
	}

	/** C+23: the flame blast tell. */
	private void blastTell() {
		if (dead)
			return;
		xarth.animate(SEQ_BLAST, 30);
		Raid4Fx.spot(xarth, 0, SPOT_BLAST, 30, 0, false);
		Position mid = local(MID_X, MID_Y);
		Raid4Fx.sound(fighters(), 9224, mid, 30, 15);
		Raid4Fx.sound(fighters(), 9210, mid, 3, 15);
		Raid4Fx.sound(fighters(), 9215, mid, 36, 15);
		Raid4Fx.sound(fighters(), 9259, mid, 39, 15);
	}

	private int hidingPillar = -1;

	/** The blast: every raider not hidden behind a standing pillar, hard, with a burn (the wiki). */
	private void blastHits() {
		if (dead)
			return;
		int[] votes = new int[3];
		for (Player p : fighters()) {
			int hid = hiddenBehind(p.getPosition());
			if (hid >= 0) {
				votes[hid]++;
				continue;
			}
			hitPlayer(p, new Hit(xarth, AttackStyle.MAGIC).randDamage(BLAST_MIN, BLAST_MAX).ignoreDefence().ignorePrayer());
			burn(p);
		}
		hidingPillar = -1;
		int best = 0;
		for (int i = 0; i < 3; i++)
			if (pillars[i] != null && votes[i] > best) {
				best = votes[i];
				hidingPillar = i;
			}
	}

	/** The standing pillar between this tile and his middle, or -1. */
	private int hiddenBehind(Position p) {
		Position mid = local(MID_X, MID_Y);
		double fx = mid.getX() + 0.5, fy = mid.getY() + 0.5, tx = p.getX() + 0.5, ty = p.getY() + 0.5;
		int steps = (int) Math.ceil(Math.max(Math.abs(tx - fx), Math.abs(ty - fy)) * 3);
		for (int i = 0; i < 3; i++) {
			if (pillars[i] == null)
				continue;
			Position sw = pillars[i].getPosition();
			for (int s = 1; s < steps; s++) {
				double x = fx + (tx - fx) * s / steps, y = fy + (ty - fy) * s / steps;
				if (x >= sw.getX() && x < sw.getX() + 2 && y >= sw.getY() && y < sw.getY() + 2)
					return i;
			}
		}
		return -1;
	}

	/** C+26: the pillar the raiders hid behind falls (one still standing at random if nobody hid). */
	private void pillarFalls() {
		if (dead)
			return;
		int i = hidingPillar;
		if (i < 0 || pillars[i] == null) {
			List<Integer> standing = new ArrayList<>();
			for (int k = 0; k < 3; k++)
				if (pillars[k] != null)
					standing.add(k);
			if (standing.isEmpty())
				return;
			i = standing.get(Random.get(standing.size() - 1));
		}
		NPC pillar = pillars[i];
		Position at = pillar.getPosition().copy();
		if (cycles < 3)
			fireRingVisual(); // the first two falls burn the whole floor too (the third's ring also hurts, phase2)
		removeNpc(pillar);
		pillars[i] = null;
		NPC rubble = spawn(NPC_RUBBLE_DESTROYED, at, Direction.SOUTH);
		Raid4Fx.faceAngle(rubble, 0, true);
		rubble.animate(SEQ_RUBBLE);
		Raid4Fx.spot(rubble, 0, SPOT_RUBBLE);
		Raid4Fx.sound(fighters(), 10579, local(MID_X, MID_Y), 0, 15);
		// the map's rubble loc goes (its tiles open up)
		GameObject.forObj(PILLARS[i][2], at.getX(), at.getY(), at.getZ(), GameObject::remove);
		schedule.after(3, () -> removeNpc(rubble));
		if (cycles < 3)
			startCycle(c + CYCLE);
		else
			phase2(schedule.now());
	}

	/** The fire wave over the floor around him, outward by distance, as the third fall's (no damage here). */
	private void fireRingVisual() {
		int z = xarth.getPosition().getZ();
		for (int x = SW_X - FIRE_RING; x < SW_X + 3 + FIRE_RING; x++)
			for (int y = SW_Y - FIRE_RING; y < SW_Y + 3 + FIRE_RING; y++) {
				Position t = local(x, y);
				int r = distance(xarth.getPosition(), 3, t);
				if (r == 0 || Tile.get(t.getX(), t.getY(), z, true).clipping != 0)
					continue;
				Raid4Fx.mapAnim(SPOT_FIRE_WAVE, t, r, 0);
			}
	}

	/**
	 * The blast's fire rolling over the ground from his middle to the pillar it breaks (2026-10-28): the same fire wave
	 * the third fall's ring uses, one tile at a time outward, so the first two falls read as burnt down too.
	 */
	private void fireTrail(Position pillarSw) {
		Position mid = local(MID_X, MID_Y);
		double fx = mid.getX() + 0.5, fy = mid.getY() + 0.5, tx = pillarSw.getX() + 1.0, ty = pillarSw.getY() + 1.0;
		int steps = (int) Math.ceil(Math.max(Math.abs(tx - fx), Math.abs(ty - fy)));
		java.util.Set<Long> done = new java.util.HashSet<>();
		for (int s = 2; s <= steps; s++) {
			int x = (int) Math.floor(fx + (tx - fx) * s / steps), y = (int) Math.floor(fy + (ty - fy) * s / steps);
			Position t = new Position(x, y, mid.getZ());
			if (done.add(key(t)))
				Raid4Fx.mapAnim(SPOT_FIRE_WAVE, t, s * 6, 0);
		}
	}

	/* ---------------------------------------------------------------- §3 phase 2 */

	private void phase2(long d) {
		phase2 = true;
		int z = xarth.getPosition().getZ();
		List<Position> ring = new ArrayList<>();
		for (int x = SW_X - FIRE_RING; x < SW_X + 3 + FIRE_RING; x++)
			for (int y = SW_Y - FIRE_RING; y < SW_Y + 3 + FIRE_RING; y++) {
				Position t = local(x, y);
				int r = distance(xarth.getPosition(), 3, t);
				if (r == 0 || Tile.get(t.getX(), t.getY(), z, true).clipping != 0)
					continue;
				ring.add(t);
				Raid4Fx.mapAnim(SPOT_FIRE_WAVE, t, r, 0);
			}
		for (Player p : fighters()) {
			if (distance(xarth.getPosition(), 3, p.getPosition()) <= FIRE_RING) {
				hitPlayer(p, new Hit(xarth).randDamage(WAVE_MIN, WAVE_MAX).ignoreDefence().ignorePrayer());
				burn(p);
			}
		}
		arenaFires(ring, 8);
		for (int[] pile : PILES) {
			Position t = local(pile[0], pile[1]);
			GameObject.forObj(pile[2], t.getX(), t.getY(), t.getZ(), obj -> obj.setId(pile[2] + 1));
		}
		schedule.after(7, () -> attackSlot(false));
		schedule.after(11, () -> attackSlot(false));
		schedule.after(15, () -> volley(ring));
	}

	private void arenaFires(List<Position> ring, int n) {
		for (int i = 0; i < n && !ring.isEmpty(); i++) {
			Position t = ring.get(Random.get(ring.size() - 1));
			long k = key(t);
			if (!fires.containsKey(k))
				fires.put(k, instance.spawnObject(LOC_XARTH_ARENA_FIRE, t, 10, Random.get(3)));
		}
	}

	/** M: n fireballs 2 apart at tiles 8 from his middle (a corpse rises at each +3), then four arrows, every 21. */
	private void volley(List<Position> ring) {
		if (dead)
			return;
		int living = fighters().size();
		int n = Math.max(Math.min(living, 2), (living + 1) / 2);
		Position mid = local(MID_X, MID_Y);
		for (int i = 0; i < n; i++) {
			schedule.after(1 + 2 * i, () -> {
				if (dead)
					return;
				double a = Random.get() * Math.PI * 2;
				Position t = mid.relative((int) Math.round(Math.cos(a) * 8), (int) Math.round(Math.sin(a) * 8));
				xarth.animate(SEQ_MAGIC);
				xarth.face(t);
				Raid4Fx.projectile(fighters(), xarth, SPOT_FIREBALL, mid, null, t, 470, 50, 30, 90, 0, 160);
				schedule.after(3, () -> raiseCorpse(t));
			});
		}
		int lastFireball = 1 + 2 * (n - 1);
		for (int k = 0; k < 4; k++)
			schedule.after(lastFireball + 3 + 4 * k, () -> attackSlot(false));
		schedule.after(12, () -> arenaFires(ring, 3));
		schedule.after(VOLLEY_EVERY, () -> volley(ring));
	}

	/* §4 the corpses */

	private void raiseCorpse(Position t) {
		if (dead)
			return;
		NPC corpse = spawn(NPC_XARTH_CORPSE, t, Direction.SOUTH);
		((Raid4Puppet) corpse.getCombat()).bind(this);
		Raid4Fx.spot(corpse, 2, SPOT_CORPSE_FIRE, 0, 0, true);
		corpse.face(local(MID_X, MID_Y));
		Raid4Fx.fade(corpse, 0, 30, 255, 0);
		spawned.add(corpse);
		corpses.add(corpse);
		schedule.after(1, () -> {
			if (!corpse.isRemoved())
				corpse.face(xarth);
		});
	}

	private void tickCorpses() {
		for (NPC corpse : new ArrayList<>(corpses)) {
			if (corpse.isRemoved() || corpse.getHp() <= 0 || dead)
				continue;
			if (distance(xarth.getPosition(), 3, corpse.getPosition()) <= 1) {
				absorb(corpse);
				continue;
			}
			if (corpse.isFrozen())
				continue;
			int dx = Integer.signum(MID_X - lx(corpse.getPosition())), dy = Integer.signum(MID_Y - ly(corpse.getPosition()));
			corpse.getMovement().reset();
			corpse.step(dx, dy, StepType.NORMAL);
		}
	}

	/** A corpse reaching him is absorbed: a heal of all its health on him. */
	private void absorb(NPC corpse) {
		corpses.remove(corpse);
		Raid4Fx.spot(xarth, 0, SPOT_ABSORB);
		int heal = Math.min(corpse.getHp(), maxHp - xarth.getHp());
		if (heal > 0)
			xarth.hit(new Hit(HitType.HEAL).fixedDamage(heal));
		Raid4Fx.spot(corpse, 2, SPOT_CORPSE_ABSORBED);
		corpse.animate(SEQ_CORPSE_ABSORB);
		if (corpse.avatar() != null)
			corpse.avatarExtended().setAllOpsInvisible();
		schedule.after(2, () -> removeNpc(corpse));
	}

	/* §5 burns: +1 a fireball that hits, never past 5, six beats 4 apart, then back to 1 */

	private void burn(Player p) {
		long[] b = burns.get(p);
		if (b == null)
			burns.put(p, new long[] { 1, 6, schedule.now() + 1 });
		else {
			b[0] = Math.min(5, b[0] + 1);
			b[1] = 6;
		}
	}

	private void tickBurns() {
		long now = schedule.now();
		for (var it = burns.entrySet().iterator(); it.hasNext();) {
			var e = it.next();
			Player p = e.getKey();
			long[] b = e.getValue();
			if (!fighters().contains(p)) {
				it.remove();
				continue;
			}
			if (now < b[2])
				continue;
			b[2] = now + 4;
			hitPlayer(p, new Hit(HitType.DAMAGE).fixedDamage((int) b[0]).ignoreDefence().ignorePrayer());
			if (--b[1] <= 0)
				it.remove();
		}
	}

	/** The wiki: a raider who walks on an arena fire is set alight. */
	private void tickFireTiles() {
		if (fires.isEmpty())
			return;
		for (Player p : fighters())
			if (fires.containsKey(key(p.getPosition())))
				burn(p);
	}

	/* ---------------------------------------------------------------- §6 death */

	@Override
	public void onNpcDying(NPC npc) {
		if (npc == xarth && !dead) {
			dead = true;
			Raid4Fx.spot(xarth, 0, SPOT_DEATH);
			Raid4Fx.resetFace(xarth);
			if (xarth.avatar() != null)
				xarth.avatarExtended().setAllOpsInvisible();
		} else if (npc.getId() == NPC_XARTH_CORPSE) {
			corpses.remove(npc);
			Raid4Fx.spot(npc, 0, SPOT_CORPSE_DEATH);
			Raid4Fx.spot(npc, 2, SPOT_CORPSE_DOUSED);
			Raid4Fx.resetFace(npc);
		}
	}

	@Override
	public void onNpcDeath(NPC npc) {
		if (npc != xarth || complete)
			return;
		for (NPC corpse : new ArrayList<>(corpses))
			removeNpc(corpse);
		corpses.clear();
		for (int x = SW_X; x < SW_X + 3; x++)
			for (int y = SW_Y; y < SW_Y + 3; y++)
				tile(x, y).unflagUnmovable();
		finish("Xarth dead");
		schedule.after(8, () -> {
			for (Player p : hud)
				p.getHealthHud().close();
			hud.clear();
		});
	}

	private void updateHud() {
		if (!started || maxHp == 0)
			return;
		int hp = dead ? 0 : Math.max(0, xarth.getHp());
		for (Player p : fighters())
			if (hud.add(p) || !p.getHealthHud().isOpened())
				p.getHealthHud().open(true, NPC_XARTH, maxHp);
		if (hp != shownHp) {
			shownHp = hp;
			for (Player p : hud)
				p.getHealthHud().updateValue(hp);
		}
	}

	@Override
	protected void clearHazards() {
		super.clearHazards();
		for (GameObject f : fires.values())
			instance.removeObject(f);
		fires.clear();
		for (GameObject f : pileFires)
			instance.removeObject(f);
		pileFires.clear();
		burns.clear();
	}

	@Override
	public void cleanup() {
		super.cleanup();
		for (Player p : hud)
			p.getHealthHud().close();
		hud.clear();

	}
}
