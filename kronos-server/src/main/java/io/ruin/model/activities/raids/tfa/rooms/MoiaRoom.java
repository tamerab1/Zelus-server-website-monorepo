package io.ruin.model.activities.raids.tfa.rooms;

import io.ruin.api.utils.Random;
import io.ruin.model.activities.raids.tfa.Raid4Fx;
import io.ruin.model.activities.raids.tfa.Raid4RoomType;
import io.ruin.model.activities.raids.tfa.Raid4Scaling;
import io.ruin.model.activities.raids.tfa.bosses.Raid4Puppet;
import io.ruin.model.combat.AttackStyle;
import io.ruin.model.combat.Hit;
import io.ruin.model.combat.HitType;
import io.ruin.model.entity.Entity;
import io.ruin.model.entity.npc.NPC;
import io.ruin.model.entity.player.Player;
import io.ruin.model.entity.shared.StepType;
import io.ruin.model.map.Direction;
import io.ruin.model.map.Position;
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
 * Slot 3 west: Moia (region 16216), rebuilt 2026-10-28 from the RSProx captures of four kills
 * (docs/rsprox-analysis/guthixian_archive/moia_plan.md). Section numbers (§) refer to that plan. Local coordinates =
 * absolute - (4032, 5632) (template square (63,88)); C = the ring's middle tile (31,31). Moia is 3x3.
 */
@Slf4j
public class MoiaRoom extends Raid4BossRoom {

	/* §1 */
	static final int C_X = 31, C_Y = 31;
	static final int ARRIVE_X = 49, ARRIVE_Y = 31; // (4080..4082, 5662..5664)
	static final int TRANSPORTER_SIDE_X = 43, TRANSPORTER_SIDE_Y = 31; // beside (4074,5663)
	static final int LANDING_X = 34, LANDING_Y = 31; // (4066,5663)
	static final int NAVIGATOR_X = 35, NAVIGATOR_Y = 31; // (4067,5663) facing west
	static final int PLAZA_MIN = 27, PLAZA_MAX = 35;
	/** Her four points (south-west tiles): W, N, E, S. */
	static final int[][] POINTS = { { 23, 30 }, { 30, 37 }, { 37, 30 }, { 30, 23 } };
	static final int RETURN_X = 30, RETURN_Y = 30;
	/** The binders' track, relative to C (§6). */
	static final int[][] TRACK = { { 4, 4 }, { 4, 0 }, { 4, -4 }, { 0, -2 }, { -4, -4 }, { -4, 0 }, { 0, 0 }, { -4, 4 }, { 0, 2 } };
	static final int[] REFILL_ORDER = { 7, 8, 0, 6, 1, 2, 3, 4, 5 };
	/** The phase-2 ring, clockwise, relative to C (§7). */
	static final int[][] RING = { { 0, 3 }, { 2, 2 }, { 4, 0 }, { 2, -2 }, { 0, -3 }, { -2, -2 }, { -4, 0 }, { -2, 2 } };

	/* Timing */
	static final int SLOT = 5, LEG_EVERY = 15, REFILL_EVERY = 30, COUGH_EVERY = 8, COUGHS = 4;
	static final double CHANGE_AT = 0.34;

	/* Damage (§8) */
	static final int DAGGER_MIN = 5, DAGGER_MAX = 32, MAGIC_MIN = 11, MAGIC_MAX = 44, BINDER_MIN = 4, BINDER_MAX = 22,
			BLAST_EACH = 25, COUGH = 2, BINDER_COUGH = 20;

	/* Ids */
	static final int SEQ_MAGIC = 14785, SEQ_DAGGER = 14786, SEQ_CHANT = 14787, SEQ_TELEPORT = 14788, SEQ_BINDER_SWING = 14791,
			SEQ_PUSHED = 14531;
	static final int SPOT_DAGGER_MAGIC = 4213, SPOT_DAGGER = 4214, SPOT_MAGIC_IMPACT = 4216, SPOT_INFECT = 4217,
			SPOT_INFECT_PROJ = 4218, SPOT_TELE_OUT = 4219, SPOT_TELE_IN = 4220, SPOT_TELE_DEATH = 4221, SPOT_EMPOWER_OUT = 4222,
			SPOT_EMPOWER = 4223, SPOT_EMPOWER_IN = 4224, SPOT_CHANT_EXPLODE = 4225, SPOT_BINDER_ADD = 2421;
	static final int HEADBAR_COUNTDOWN = 77, BINDER_BODY = 62568;

	private NPC moia;
	private int maxHp;
	private boolean phase2, changing, dead, added;
	private int point; // 0..3, phase 1
	private long nextSlot, nextEvent;
	private int eventIndex;
	private boolean eventGap25;
	private Player face;
	/** Binders: npc -> its track / ring index, and its swing clock. */
	private final Map<NPC, int[]> binders = new HashMap<>(); // {index, nextSwing, empowered}
	private long nextLeg, nextRefill;
	/** Coughs: entity -> {coughs left, next cough tick}. */
	private final Map<Entity, long[]> coughs = new HashMap<>();
	/* phase 2 */
	private long nextChant = Long.MAX_VALUE, chantTick;
	private int chants, blast, ringWay;
	private int phase2Slot;
	private int magicEvery;
	private final Set<Player> hud = new HashSet<>();
	private int shownHp = -1;

	@Override
	public Raid4RoomType type() {
		return Raid4RoomType.MOIA;
	}

	@Override
	protected void spawnFixtures() {
	}

	@Override
	protected int[] arenaBounds() {
		return new int[] { PLAZA_MIN, PLAZA_MIN, PLAZA_MAX, PLAZA_MAX };
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
		return Direction.WEST;
	}

	@Override
	protected int navigatorDelay() {
		return 3; // K+6 line, K+9 the navigator
	}

	@Override
	protected String challengeName() {
		return "Moia";
	}

	/* ---------------------------------------------------------------- §1 the engage */

	@Override
	protected void onStart(Player first) {
		maxHp = Raid4Scaling.tableHp(1900, scale);
		schedule.after(3, this::addMoia);
		schedule.after(6, () -> {
			for (int i = 0; i < TRACK.length; i++)
				if (i != 5)
					addBinder(i, false);
		});
		long e = schedule.now();
		nextLeg = e + 13;
		nextRefill = e + 23;
		log.info("Raid 4: Moia engaged by {} (scale {}, hp {})", first.getName(), scale, maxHp);
	}

	private void addMoia() {
		point = 0;
		moia = spawn(NPC_MOIA, local(POINTS[0][0], POINTS[0][1]), Direction.EAST);
		((Raid4Puppet) moia.getCombat()).bind(this);
		Stat hp = moia.getCombat().getStat(StatType.Hitpoints);
		hp.fixedLevel = maxHp;
		hp.currentLevel = maxHp;
		moia.face(local(49, 31));
		Raid4Fx.spot(moia, 0, SPOT_TELE_IN);
		Raid4Fx.fade(moia, 0, 15, 255, 0);
		added = true;
		long now = schedule.now();
		nextSlot = now + SLOT;
		nextEvent = now + 25;
		eventIndex = 0;
		updateHud();
	}

	private Position middle() {
		return moia.getPosition().relative(1, 1);
	}

	/* ---------------------------------------------------------------- per tick */

	@Override
	protected void onTick() {
		long now = schedule.now();
		updateHud();
		tickCoughs(now);
		tickBinders(now);
		if (!added || dead || moia == null || moia.isRemoved() || changing)
			return;
		defenceFloor();
		if (!phase2) {
			// a random raider every tick
			List<Player> pool = fighters();
			if (!pool.isEmpty()) {
				Player next = pool.get(Random.get(pool.size() - 1));
				if (next != face) {
					face = next;
					moia.face(next);
				}
			}
		}
		if (phase2 && now >= nextChant)
			chant(now);
		if (now >= nextSlot) {
			nextSlot = now + SLOT;
			slot(now);
		}
	}

	/** Defence drained no lower than 60% of its base (wiki). */
	private void defenceFloor() {
		Stat def = moia.getCombat().getStat(StatType.Defence);
		int floor = (int) Math.ceil(def.fixedLevel * 0.6);
		if (def.currentLevel < floor)
			def.currentLevel = floor;
	}

	/* §2/§3 the slot clock */

	private void slot(long now) {
		if (!phase2 && moia.getHp() <= maxHp * CHANGE_AT) {
			change();
			return;
		}
		if (!phase2 && now >= nextEvent) {
			int kind = eventIndex % 3; // T, T, I
			eventIndex++;
			eventGap25 = !eventGap25;
			nextEvent = now + (Random.get(1) == 0 ? 20 : 25);
			if (kind < 2)
				blink();
			else
				infect();
			return;
		}
		if (phase2) {
			phase2Slot++;
			if (phase2Slot == magicEvery || (magicEvery > 0 && phase2Slot > magicEvery && (phase2Slot - magicEvery) % 4 == 0)) {
				magicVolley(210, 7);
				return;
			}
		}
		daggers();
	}

	/** One dagger at every raider from her middle (st 10, et 30, angle 135, progress 20); lands next tick. */
	private void daggers() {
		moia.animate(SEQ_MAGIC);
		Position mid = middle();
		for (Player p : fighters()) {
			Raid4Fx.projectile(fighters(), moia, SPOT_DAGGER, mid, p, p.getPosition(), 340, 95, 10, 30, 135, 20);
			schedule.after(1, () -> {
				if (!fighters().contains(p) || dead)
					return;
				int dmg = p.getPrayer().isActive(Prayer.PROTECT_FROM_MISSILES) ? 0 : Random.get(DAGGER_MIN, DAGGER_MAX);
				hitPlayer(p, new Hit(moia, AttackStyle.RANGED).fixedDamage(dmg).ignoreDefence().ignorePrayer());
			});
		}
	}

	/** A magic dagger at every raider (st 15, et {@code end}), each landing {@code land} ticks on. */
	private void magicVolley(int end, int land) {
		moia.animate(SEQ_MAGIC);
		Position mid = middle();
		for (Player p : fighters()) {
			Raid4Fx.projectile(fighters(), moia, SPOT_DAGGER_MAGIC, mid, p, p.getPosition(), 340, 95, 15, end, 150, 100);
			Raid4Fx.sound(fighters(), 12472, mid, 0, 30);
			schedule.after(land, () -> {
				if (!fighters().contains(p) || dead)
					return;
				Raid4Fx.spot(p, 2, SPOT_MAGIC_IMPACT, 0, 80);
				int dmg = p.getPrayer().isActive(Prayer.PROTECT_FROM_MAGIC) ? 0 : Random.get(MAGIC_MIN, MAGIC_MAX);
				hitPlayer(p, new Hit(moia, AttackStyle.MAGIC).fixedDamage(dmg).ignoreDefence().ignorePrayer());
			});
		}
	}

	/** T: S the blink out; S+1 in at one of the other three points with a magic dagger at every raider. */
	private void blink() {
		Raid4Fx.spot(moia, 0, SPOT_TELE_OUT);
		moia.animate(SEQ_TELEPORT);
		schedule.after(1, () -> {
			if (dead || changing)
				return;
			int next = (point + 1 + Random.get(2)) % 4;
			point = next;
			Raid4Fx.teleportNow(moia, local(POINTS[next][0], POINTS[next][1]));
			Raid4Fx.spot(moia, 0, SPOT_TELE_IN);
			Raid4Fx.fade(moia, 0, 15, 255, 0);
			if (moia.avatar() != null)
				moia.avatarExtended().setAllOpsInvisible();
			List<Player> pool = fighters();
			if (!pool.isEmpty()) {
				face = pool.get(Random.get(pool.size() - 1));
				moia.face(face);
			}
			magicVolley(180, 6);
			schedule.after(1, () -> {
				if (moia.avatar() != null && !dead)
					moia.avatarExtended().setAllOpsVisible();
			});
		});
	}

	/** I: an infection at any raider; it lands at +4 and they start coughing. */
	private void infect() {
		List<Player> pool = fighters();
		if (pool.isEmpty())
			return;
		Player p = pool.get(Random.get(pool.size() - 1));
		moia.animate(SEQ_DAGGER);
		Raid4Fx.spot(moia, 0, SPOT_INFECT);
		Raid4Fx.sound(fighters(), 150, middle(), 0, 15);
		Raid4Fx.projectile(fighters(), moia, SPOT_INFECT_PROJ, middle(), p, p.getPosition(), 255, 124, 20, 120, 127, 0);
		schedule.after(4, () -> {
			if (fighters().contains(p))
				startCough(p, true);
		});
	}

	/* §4 the cough */

	private void startCough(Entity e, boolean landing) {
		if (coughs.containsKey(e))
			return;
		long now = schedule.now();
		coughs.put(e, new long[] { e instanceof NPC ? Integer.MAX_VALUE : COUGHS, landing ? now : now });
		cough(e, landing);
	}

	private void cough(Entity e, boolean delayedSplat) {
		e.forceText("*Cough*");
		if (e instanceof Player p) {
			if (delayedSplat)
				schedule.after(1, () -> {
					if (fighters().contains(p))
						hitPlayer(p, new Hit(HitType.POISON).fixedDamage(COUGH).ignoreDefence().ignorePrayer());
				});
			else
				hitPlayer(p, new Hit(HitType.POISON).fixedDamage(COUGH).ignoreDefence().ignorePrayer());
			// a raider's cough catches every raider and binder within 2 not coughing yet
			for (Player q : fighters())
				if (q != p && !coughs.containsKey(q) && q.getPosition().distance(p.getPosition()) <= 2)
					startCough(q, false);
			for (NPC b : binders.keySet())
				if (!coughs.containsKey(b) && b.getPosition().distance(p.getPosition()) <= 2)
					startCough(b, false);
		} else if (e instanceof NPC b && !b.isRemoved()) {
			b.hit(new Hit(HitType.POISON).fixedDamage(BINDER_COUGH).ignoreDefence().ignorePrayer());
		}
	}

	private void tickCoughs(long now) {
		for (var it = coughs.entrySet().iterator(); it.hasNext();) {
			var en = it.next();
			Entity e = en.getKey();
			long[] c = en.getValue();
			if ((e instanceof Player p && !fighters().contains(p)) || (e instanceof NPC n && (n.isRemoved() || n.getHp() <= 0))) {
				it.remove();
				continue;
			}
			if (now - c[1] >= COUGH_EVERY) {
				c[1] = now;
				if (--c[0] <= 0) {
					it.remove();
					continue;
				}
				cough(e, true);
			}
		}
	}

	/* §6 the binders */

	private NPC addBinder(int index, boolean ring) {
		int[] o = ring ? RING[index] : TRACK[index];
		Position t = local(C_X + o[0], C_Y + o[1]);
		NPC b = spawn(NPC_MOIA_BINDER, t, Direction.SOUTH);
		((Raid4Puppet) b.getCombat()).bind(this);
		Stat hp = b.getCombat().getStat(StatType.Hitpoints);
		hp.fixedLevel = 95;
		hp.currentLevel = 95;
		if (Random.get(1) == 0)
			Raid4Fx.bodyModel(b, BINDER_BODY);
		Raid4Fx.mapAnim(SPOT_BINDER_ADD, t, 0, 0);
		Raid4Fx.sound(fighters(), 9140, t, 0, 10);
		spawned.add(b);
		binders.put(b, new int[] { index, (int) (schedule.now() + 7), 0 });
		return b;
	}

	private boolean occupied(int index, boolean ring) {
		for (int[] s : binders.values())
			if (s[0] == index)
				return true;
		return false;
	}

	private void tickBinders(long now) {
		binders.keySet().removeIf(b -> b.isRemoved() || b.getHp() <= 0);
		if (!phase2 && !dead && !changing) {
			if (now >= nextLeg) {
				nextLeg = now + LEG_EVERY;
				for (Map.Entry<NPC, int[]> e : binders.entrySet()) {
					int[] s = e.getValue();
					s[0] = (s[0] + 1) % TRACK.length;
					walk(e.getKey(), local(C_X + TRACK[s[0]][0], C_Y + TRACK[s[0]][1]));
					s[1] = (int) (now + 7);
				}
			}
			if (now >= nextRefill) {
				nextRefill = now + REFILL_EVERY;
				int added = 0;
				for (int idx : REFILL_ORDER) {
					if (binders.size() >= 6 && added > 0)
						break;
					if (!occupied(idx, false)) {
						addBinder(idx, false);
						added++;
					}
				}
			}
		}
		// swings: a raider within 2, its own tile included, from the raider's tile before it moves
		for (Map.Entry<NPC, int[]> e : binders.entrySet()) {
			NPC b = e.getKey();
			int[] s = e.getValue();
			if (now < s[1] || dead)
				continue;
			Player target = null;
			for (Player p : fighters())
				if (p.getPosition().distance(b.getPosition()) <= 2) {
					target = p;
					break;
				}
			if (target == null)
				continue;
			s[1] = (int) (now + 5);
			b.animate(SEQ_BINDER_SWING);
			b.face(target);
			boolean onTile = target.getPosition().getX() == b.getPosition().getX() && target.getPosition().getY() == b.getPosition().getY();
			int dmg = target.getPrayer().isActive(Prayer.PROTECT_FROM_MELEE) ? 0 : Random.get(31) == 0 ? 0 : Random.get(BINDER_MIN, BINDER_MAX);
			hitPlayer(target, new Hit(b, AttackStyle.CRUSH).fixedDamage(dmg).ignoreDefence().ignorePrayer());
			if (onTile) {
				b.forceText("Grah!");
				for (Player p : fighters()) {
					if (p.getPosition().getX() != b.getPosition().getX() || p.getPosition().getY() != b.getPosition().getY())
						continue;
					Position to = p.getPosition().relative(-1, -1);
					if (lx(to) < PLAZA_MIN || ly(to) < PLAZA_MIN)
						to = p.getPosition().relative(0, -1);
					p.animate(SEQ_PUSHED);
					Raid4Fx.slide(p, to, 29, 1536);
				}
			}
		}
	}

	/** A leg: a tile a tick, diagonals first, through each other (WALK steps skip the clipping check). */
	private void walk(NPC b, Position to) {
		b.getMovement().reset();
		b.stepAbs(to.getX(), to.getY(), StepType.WALK);
	}

	/* §5 the change */

	private void change() {
		changing = true;
		moia.forceText("Cease this futile resistance!");
		schedule.after(1, () -> { // P+1
			if (dead)
				return;
			for (NPC b : binders.keySet()) {
				int heal = b.getMaxHp() - b.getHp();
				if (heal > 0)
					b.hit(new Hit(HitType.HEAL).fixedDamage(heal));
			}
			Raid4Fx.mapAnim(SPOT_TELE_OUT, middle(), 0, 0);
			moia.setHidden(true);
			for (int i = 0; i < RING.length; i++)
				if (binders.size() < TRACK.length && !occupied(i, false)) {
					addBinder(i, false);
					break;
				}
		});
		schedule.after(5, () -> { // P+5 the binders walk to the ring
			int i = 0;
			for (Map.Entry<NPC, int[]> e : binders.entrySet()) {
				if (i >= RING.length)
					break;
				e.getValue()[0] = i;
				walk(e.getKey(), local(C_X + RING[i][0], C_Y + RING[i][1]));
				i++;
			}
		});
		schedule.after(14, this::returnToMiddle);
	}

	private void returnToMiddle() {
		if (dead)
			return;
		Raid4Fx.teleportNow(moia, local(RETURN_X, RETURN_Y));
		moia.setHidden(false);
		List<Player> pool = fighters();
		if (!pool.isEmpty()) {
			face = pool.get(Random.get(pool.size() - 1));
			moia.face(face);
		}
		Raid4Fx.spot(moia, 0, SPOT_TELE_IN);
		Raid4Fx.fade(moia, 0, 15, 255, 0);
		int heal = Math.min(215 * scaleFactor() / 100, 65 * binders.size());
		heal = Math.min(heal, maxHp - moia.getHp());
		if (heal > 0)
			moia.hit(new Hit(HitType.HEAL).fixedDamage(heal));
		changing = false;
		phase2 = true;
		chants = 0;
		nextChant = schedule.now() + 2;
		nextSlot = Long.MAX_VALUE;
	}

	private int scaleFactor() {
		return Raid4Scaling.tableHp(100, scale);
	}

	/* §7 the chants */

	private void chant(long now) {
		chants++;
		int gap = Math.max(31, 43 - 2 * chants); // C2 = C1+41, +39, +37, +35, ...
		blast = gap - 5;
		chantTick = now;
		nextChant = now + gap;
		moia.animate(SEQ_CHANT);
		moia.forceText(chants == 1 ? "Useless corpses... Give me your strength!" : "Again!");
		// headbar 77 (the countdown) draws one of our custom card sprites in this cache: left out
		if (chants > 1) // "Again!" fills the ring to six
			for (int i = 0; i < RING.length && binders.size() < 6; i++)
				if (!occupied(i, true))
					addBinder(i, true);
		for (Map.Entry<NPC, int[]> e : binders.entrySet()) {
			NPC b = e.getKey();
			e.getValue()[2] = 1;
			schedule.after(1, () -> Raid4Fx.spot(b, 5, SPOT_EMPOWER_IN, 5, 0, false));
			schedule.after(4, () -> Raid4Fx.spot(b, 5, SPOT_EMPOWER, 0, 0, true));
		}
		ringWay = Random.get(10) < 7 ? -1 : 1; // anticlockwise 7 of 11
		int b = blast;
		if (chants == 1)
			schedule.after(2, this::ringMove);
		schedule.after(2 + (b + 2) / 3, this::ringMove);
		schedule.after(2 + (2 * b + 2) / 3, this::ringMove);
		schedule.after(b, this::burst);
		// daggers every 5 from the chant; a magic volley the 5th after the first, after an "Again!" the 1st..3rd then every 4th
		phase2Slot = 0;
		magicEvery = chants == 1 ? 5 : Random.get(1, 3);
		nextSlot = now + SLOT;
	}

	private void ringMove() {
		if (dead)
			return;
		for (Map.Entry<NPC, int[]> e : binders.entrySet()) {
			int[] s = e.getValue();
			s[0] = ((s[0] + ringWay) % RING.length + RING.length) % RING.length;
			walk(e.getKey(), local(C_X + RING[s[0]][0], C_Y + RING[s[0]][1]));
		}
	}

	/** C'-5: every empowered binder standing bursts; 25 to every raider for each, the tick after. */
	private void burst() {
		if (dead)
			return;
		moia.animate(SEQ_MAGIC);
		int survivors = 0;
		for (Map.Entry<NPC, int[]> e : binders.entrySet()) {
			if (e.getValue()[2] == 1 && !e.getKey().isRemoved() && e.getKey().getHp() > 0) {
				survivors++;
				Raid4Fx.spot(e.getKey(), 5, SPOT_EMPOWER_OUT);
				e.getValue()[2] = 0;
				NPC b = e.getKey();
				schedule.after(2, () -> Raid4Fx.clearSpot(b, 5));
			}
		}
		for (Player p : fighters())
			Raid4Fx.spot(p, 5, SPOT_CHANT_EXPLODE, 60, 0);
		int dmg = BLAST_EACH * survivors;
		if (dmg > 0)
			schedule.after(1, () -> {
				for (Player p : fighters())
					hitPlayer(p, new Hit(moia).fixedDamage(dmg).ignoreDefence().ignorePrayer());
			});
	}

	/* §9 death */

	@Override
	public void onNpcDying(NPC npc) {
		if (npc == moia && !dead) {
			dead = true;
			schedule.after(1, () -> {
				Raid4Fx.spot(moia, 0, SPOT_TELE_DEATH);
				Raid4Fx.resetFace(moia);
				moia.forceText("Hmph!");
				if (moia.avatar() != null)
					moia.avatarExtended().setAllOpsInvisible();
				for (NPC b : new ArrayList<>(binders.keySet())) {
					b.animate(14792);
					if (b.avatar() != null)
						b.avatarExtended().setAllOpsInvisible();
				}
			});
		} else if (npc.getId() == NPC_MOIA_BINDER) {
			binders.remove(npc);
			coughs.remove(npc);
			if (npc.avatar() != null)
				npc.avatarExtended().setAllOpsInvisible();
		}
	}

	@Override
	public void onNpcDeath(NPC npc) {
		if (npc != moia || complete)
			return;
		for (NPC b : new ArrayList<>(binders.keySet()))
			removeNpc(b);
		binders.clear();
		finish("Moia dead");
		schedule.after(8, () -> {
			for (Player p : hud)
				p.getHealthHud().close();
			hud.clear();
		});
	}

	@Override
	protected boolean usesNavigator() {
		return true;
	}

	private void updateHud() {
		if (!added || maxHp == 0)
			return;
		int hp = dead ? 0 : Math.max(0, moia.getHp());
		for (Player p : fighters())
			if (hud.add(p) || !p.getHealthHud().isOpened())
				p.getHealthHud().open(true, NPC_MOIA, maxHp);
		if (hp != shownHp) {
			shownHp = hp;
			for (Player p : hud)
				p.getHealthHud().updateValue(hp);
		}
	}

	@Override
	protected void clearHazards() {
		super.clearHazards();
		for (NPC b : new ArrayList<>(binders.keySet()))
			removeNpc(b);
		binders.clear();
		coughs.clear();
	}

	@Override
	public void cleanup() {
		super.cleanup();
		for (Player p : hud)
			p.getHealthHud().close();
		hud.clear();
	}
}
