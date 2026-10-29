package io.ruin.model.activities.raids.tfa.rooms;

import io.ruin.api.utils.Random;
import io.ruin.model.activities.raids.tfa.Raid4Fx;
import io.ruin.model.activities.raids.tfa.Raid4RoomType;
import io.ruin.model.activities.raids.tfa.Raid4Scaling;
import io.ruin.model.activities.raids.tfa.bosses.bork.Bork;
import io.ruin.model.activities.raids.tfa.bosses.bork.Smorks;
import io.ruin.model.combat.AttackStyle;
import io.ruin.model.combat.Hit;
import io.ruin.model.entity.npc.NPC;
import io.ruin.model.entity.player.Player;
import io.ruin.model.map.Direction;
import io.ruin.model.map.Position;
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
 * Slot 2 east: Bork and the Smorks (region 16727), rebuilt 2026-10-28 from the RSProx captures of thirteen fights
 * (docs/rsprox-analysis/guthixian_archive/bork_plan.md). Section numbers (§) refer to that plan. Local coordinates =
 * absolute - (4160, 5568) (template square (65,87)).
 */
@Slf4j
public class BorkRoom extends Raid4BossRoom {

	/* §1 the room */
	static final int ARENA_MIN_X = 24, ARENA_MAX_X = 38, ARENA_MIN_Y = 23, ARENA_MAX_Y = 39; // (4184..4198, 5591..5607)
	static final int BORK_X = 34, BORK_Y = 35; // (4194,5603) spawnangle west
	static final int CART_X = 30, CART_Y = 30; // (4190,5598)
	static final int HANDLER_X = 1, HANDLER_Y = 1; // the bar's npc (4161,5569)
	static final int LANDING_X = 27, LANDING_Y = 31; // over the wall (4187,5599)
	static final int ARRIVE_X = 11, ARRIVE_Y = 31; // (4170..4172, 5598..5600)
	static final int TRANSPORTER_SIDE_X = 19, TRANSPORTER_SIDE_Y = 31; // west of the transporter (4180,5599)
	static final int NAVIGATOR_X = 24, NAVIGATOR_Y = 31; // (4184,5599) facing east
	static final int MIDDLE_X = 29, MIDDLE_Y = 26; // (4189,5594)
	static final int BORK_SIZE = 4, CART_SIZE = 3;

	/* §2-§5 */
	static final int GONG_FIRST = 8, SMORK_FIRST = 13;
	static final int[] RATE = { 7, 6, 5, 4 };
	static final int[] SHOT_END = { 150, 120, 90, 90 };
	static final String[] BORK_SPEED = { "Bork get into it!", "Bork make fast drum!", "Bork maximum crush!" };
	static final String[] SMORK_SPEED = { "Smorks go faster!", "Smorks feeling drum beat now!", "Smorks go fastest!" };
	static final String[] NAPALM_LINES = { "Burny gloop!", "Fireeeeeeeeee!", "Hot stuff!" };
	static final String[] STUN_LINES = { "That you fault!", "Bork! We sorry! No go!", "No! Fearless leader!" };
	static final String[] MISS_LINES = { "Bork crush now!", "Bork think it stomp time now!" };
	/** The Smorks' cycle of seven slots. */
	enum Slot { SHOT, SHOT2, NAPALM, NONE, SHOT3, SHOT4, BOMB }

	/* Ids */
	static final int SEQ_GONG = 14542, SEQ_STUN = 14545, SEQ_STOMP_IN = 14549, SEQ_STOMP = 14550, SEQ_STOMP_OUT = 14551,
			SEQ_DEATH = 14541, SEQ_RAGE_IN = 14557, SEQ_RAGE_GONG = 14547, SEQ_CANNON = 14554, SEQ_BIG_CANNON = 14555;
	static final int SPOT_GONG = 4037, SPOT_STUN = 4041, SPOT_STOMP = 4039, SPOT_DEATH = 4036, SPOT_DUST = 4395,
			SPOT_NAPALM_PROJ = 4045, SPOT_NAPALM_FIRE = 4046, SPOT_NAPALM_SPLAT = 4047, SPOT_NAPALM_DESPAWN = 4051,
			SPOT_RANGE_PROJ = 4056, SPOT_MAGE_PROJ = 4057, SPOT_BIG_BALL = 4440, SPOT_SHADOW_120 = 1447, SPOT_SHADOW_150 = 1448,
			SPOT_SMORK_EXPLOSION = 4062, SPOT_SMORK_GOBLINS = 4061, SPOT_SMORK_TOWER = 4060;
	static final int LOC_RAGGED = 62544, RAGGED_TICKS = 199;
	static final int NPC_HANDLER = 16642;

	/* Damage (§10) */
	static final int BEAT1_MIN = 16, BEAT1_MAX = 28, BEAT2_MIN = 20, BEAT2_MAX = 33, SLAM_MIN = 14, SLAM_MAX = 48,
			SLAM_PRAYED_MIN = 18, SLAM_PRAYED_MAX = 33, SHOT_MAX = 32, NAPALM_MIN = 25, NAPALM_MAX = 28, FIRE = 2;

	private NPC bork, smorks, handler;
	private Bork borkBody;
	private int borkMax, smorksMax;
	private Player target;
	private int tier; // 0..3 = R 7, 6, 5, 4
	private long nextGong = Long.MAX_VALUE, nextSlot = Long.MAX_VALUE;
	private int slot;
	private boolean borkDead, smorksDead, rage;
	/** No gong / no chase until this tick (stun, miss, the stomps). */
	private long gongBlockedUntil, standUntil;
	private Position borkTileBefore;
	/** Ragged tiles: key -> {expiry tick, rotation}. */
	private final Map<Long, long[]> ragged = new HashMap<>();
	private final Map<Long, GameObject> raggedLocs = new HashMap<>();
	private final Set<Player> hud = new HashSet<>();
	private int shownBork = -1, shownSmorks = -1;

	@Override
	public Raid4RoomType type() {
		return Raid4RoomType.BORK;
	}

	@Override
	protected void spawnFixtures() {
		smorks = spawn(NPC_SMORKS, local(CART_X, CART_Y), Direction.WEST);
		((Smorks) smorks.getCombat()).bind(this);
		bork = spawn(NPC_BORK, local(BORK_X, BORK_Y), Direction.WEST);
		borkBody = (Bork) bork.getCombat();
		borkBody.bind(this);
		handler = spawn(NPC_HANDLER, local(HANDLER_X, HANDLER_Y), Direction.SOUTH);
		// the cart blocks walking
		for (int x = CART_X; x < CART_X + CART_SIZE; x++)
			for (int y = CART_Y; y < CART_Y + CART_SIZE; y++) {
				Position p = local(x, y);
				io.ruin.model.map.Tile.get(p.getX(), p.getY(), p.getZ(), true).flagUnmovable();
			}
		// §1: he wanders the whole arena until the fight starts
		bork.walkTo = null;
	}

	@Override
	protected int[] arenaBounds() {
		return new int[] { ARENA_MIN_X, ARENA_MIN_Y, ARENA_MAX_X, ARENA_MAX_Y };
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
		return "Bork";
	}

	/* ---------------------------------------------------------------- the engage */

	@Override
	protected void onStart(Player first) {
		borkMax = Raid4Scaling.tableHp(1500, scale);
		smorksMax = Raid4Scaling.tableHp(600, scale);
		setHp(bork, borkMax);
		setHp(smorks, smorksMax);
		target = first;
		long a = schedule.now();
		Raid4Fx.sound(fighters(), 12769, local(MIDDLE_X, MIDDLE_Y), 0, 30);
		Raid4Fx.resetFace(smorks);
		bork.getMovement().reset();
		schedule.after(1, () -> {
			bork.face(target);
			borkBody.chase(target);
			updateHud();
		});
		schedule.after(2, () -> smorks.face(target));
		nextGong = a + GONG_FIRST;
		nextSlot = a + SMORK_FIRST;
		log.info("Raid 4: Bork engaged by {} (scale {}, bork {}, smorks {})", first.getName(), scale, borkMax, smorksMax);
	}

	private static void setHp(NPC npc, int hp) {
		Stat stat = npc.getCombat().getStat(StatType.Hitpoints);
		stat.fixedLevel = hp;
		stat.currentLevel = hp;
	}

	private int rate() {
		return RATE[tier];
	}

	/* ---------------------------------------------------------------- per tick */

	@Override
	public void tick() {
		if (!started && !complete && bork != null && !bork.isRemoved() && schedule.now() % 9 == 0) {
			// §1 the wander, the whole arena
			Position to = randomArenaTile();
			bork.getRouteFinder().routeAbsolute(to.getX(), to.getY());
		}
		super.tick();
	}

	@Override
	protected void onTick() {
		long now = schedule.now();
		updateHud();
		tickRagged(now);
		if (!borkDead && bork != null) {
			if (target == null || !fighters().contains(target) || !inArena(target.getPosition()))
				pickTarget(false);
			if (now < standUntil || borkBody.isDazed())
				borkBody.chase(null);
			else if (target != null && !rage)
				borkBody.chase(target);
			if (now >= nextGong && now >= gongBlockedUntil) {
				if (rage)
					rageBeat();
				else
					gong();
				nextGong = now + rate();
			}
			borkTileBefore = bork.getPosition().copy();
		}
		if (!smorksDead && now >= nextSlot) {
			smorkSlot();
			nextSlot = now + (borkDead ? 2 : rate());
		}
	}

	/* §2 the drum */

	private void gong() {
		bork.animate(SEQ_GONG);
		Raid4Fx.spot(bork, 0, SPOT_GONG);
		Position sw = borkTileBefore != null ? borkTileBefore : bork.getPosition();
		int reach = 1;
		if (target != null)
			reach = Math.max(1, distance(sw, BORK_SIZE, target.getPosition()));
		final int first = reach;
		beat(sw, first, true);
		schedule.after(2, () -> {
			if (!borkDead && !rage && schedule.now() >= gongBlockedUntil)
				beat(bork.getPosition().copy(), 1, false);
		});
	}

	/** One beat: every tile round his 4x4 out to {@code reach}, delay = the Manhattan distance from the drum (2,2). */
	private void beat(Position sw, int reach, boolean firstBeat) {
		Position drum = sw.relative(2, 2);
		Set<Long> tiles = new HashSet<>();
		for (int x = sw.getX() - reach; x < sw.getX() + BORK_SIZE + reach; x++) {
			for (int y = sw.getY() - reach; y < sw.getY() + BORK_SIZE + reach; y++) {
				Position t = new Position(x, y, sw.getZ());
				if (under(sw, BORK_SIZE, t) || !inArena(t) || shadowed(drum, t))
					continue;
				tiles.add(key(t));
				Raid4Fx.mapAnim(SPOT_DUST, t, Math.abs(x - drum.getX()) + Math.abs(y - drum.getY()), 0);
			}
		}
		for (Player p : fighters()) {
			if (!tiles.contains(key(p.getPosition())))
				continue;
			int dmg = firstBeat ? Random.get(BEAT1_MIN, BEAT1_MAX) : Random.get(BEAT2_MIN, BEAT2_MAX);
			if (p.getPrayer().isActive(Prayer.PROTECT_FROM_MELEE))
				dmg /= 4;
			hitPlayer(p, new Hit(bork, AttackStyle.CRUSH).fixedDamage(dmg).ignoreDefence().ignorePrayer());
		}
	}

	/** A tile the cart hides from {@code from}: the line between them crosses the cart's 3x3. */
	private boolean shadowed(Position from, Position to) {
		if (smorksDead)
			return false;
		Position cart = smorks.getPosition();
		if (under(cart, CART_SIZE, to))
			return true;
		double fx = from.getX() + 0.5, fy = from.getY() + 0.5, tx = to.getX() + 0.5, ty = to.getY() + 0.5;
		int steps = (int) Math.ceil(Math.max(Math.abs(tx - fx), Math.abs(ty - fy)) * 2);
		for (int i = 1; i < steps; i++) {
			double x = fx + (tx - fx) * i / steps, y = fy + (ty - fy) * i / steps;
			if (x >= cart.getX() && x < cart.getX() + CART_SIZE && y >= cart.getY() && y < cart.getY() + CART_SIZE)
				return true;
		}
		return false;
	}

	/* §3 the Smorks */

	private void smorkSlot() {
		Slot s = Slot.values()[slot % 7];
		slot++;
		switch (s) {
			case SHOT, SHOT2, SHOT3, SHOT4 -> shot();
			case NAPALM -> napalm();
			case BOMB -> {
				if (borkDead)
					shot();
				else
					bigBomb();
			}
			default -> {
			}
		}
	}

	/** One projectile at every raider but Bork's target (everyone once he's dead), all of one style. */
	private void shot() {
		if (smorks.getHp() <= 0)
			return;
		smorks.animate(SEQ_CANNON);
		boolean magic = Random.get(1) == 0;
		int end = borkDead ? 90 : SHOT_END[tier];
		int land = (end + 15) / 30;
		Position from = local(CART_X + 1, CART_Y + 1);
		List<Player> raiders = fighters();
		for (Player p : raiders) {
			// the recordings (parties of 3-4) never shot at Bork's target while he lived; a solo raider IS his target,
			// and no solo fight was recorded, so alone they still get the shots (2026-10-29: solo saw only the napalm)
			if (!borkDead && p == target && raiders.size() > 1)
				continue;
			Raid4Fx.projectile(fighters(), smorks, magic ? SPOT_MAGE_PROJ : SPOT_RANGE_PROJ, from, p, p.getPosition(), 270, 100, 0, end, 20, 100);
			schedule.after(land, () -> {
				if (!fighters().contains(p))
					return;
				boolean prayed = p.getPrayer().isActive(magic ? Prayer.PROTECT_FROM_MAGIC : Prayer.PROTECT_FROM_MISSILES);
				int dmg = prayed ? Random.get(1) : Random.get(SHOT_MAX);
				hitPlayer(p, new Hit(smorks, magic ? AttackStyle.MAGIC : AttackStyle.RANGED).fixedDamage(dmg).ignoreDefence().ignorePrayer());
			});
		}
	}

	private void napalm() {
		List<Player> pool = fighters();
		if (pool.isEmpty())
			return;
		Player victim = pool.get(Random.get(pool.size() - 1));
		Position cart = local(CART_X + 1, CART_Y + 1);
		smorks.forceText(NAPALM_LINES[Random.get(NAPALM_LINES.length - 1)]);
		Raid4Fx.sound(fighters(), 12747 + Random.get(9), cart, 0, 15);
		schedule.after(1, () -> {
			if (smorksDead)
				return;
			Position tile = victim.getPosition().copy();
			smorks.animate(SEQ_CANNON);
			Raid4Fx.projectile(fighters(), smorks, SPOT_NAPALM_PROJ, cart, null, tile, 270, 0, 0, 120, 30, 0);
			Raid4Fx.mapAnim(SPOT_SHADOW_120, tile, 0, 0);
			Raid4Fx.sound(fighters(), 12240, cart, 0, 15);
			schedule.after(4, () -> napalmLands(tile));
		});
	}

	/** §9: the tile, then one streak or two opposed, 1..3 long, any of 8 ways; a step k is three tiles across. */
	private void napalmLands(Position tile) {
		Raid4Fx.sound(fighters(), 12239, tile, 0, 6);
		Map<Long, Integer> patch = new HashMap<>();
		patch.put(key(tile), 0);
		int[][] dirs = { { 0, 1 }, { 1, 1 }, { 1, 0 }, { 1, -1 }, { 0, -1 }, { -1, -1 }, { -1, 0 }, { -1, 1 } };
		int d = Random.get(7);
		int streaks = Random.get(1) + 1;
		for (int s = 0; s < streaks; s++) {
			int[] dir = dirs[(d + 4 * s) % 8];
			int len = Random.get(1, 3);
			for (int k = 1; k <= len; k++) {
				int cx = tile.getX() + dir[0] * k, cy = tile.getY() + dir[1] * k;
				List<int[]> step = new ArrayList<>();
				if (dir[0] == 0 || dir[1] == 0) { // straight: three across
					int px = dir[1], py = dir[0];
					for (int w = -1; w <= 1; w++)
						step.add(new int[] { cx + px * w, cy + py * w });
				} else { // diagonal: the corner and its sides
					step.add(new int[] { cx, cy });
					step.add(new int[] { cx - dir[0], cy });
					step.add(new int[] { cx, cy - dir[1] });
				}
				for (int[] t : step)
					patch.putIfAbsent(key(new Position(t[0], t[1], tile.getZ())), 3 * k);
			}
		}
		fire(patch, tile.getZ());
		// a raider on the landing tile; Bork under it the tick after
		for (Player p : fighters())
			if (p.getPosition().getX() == tile.getX() && p.getPosition().getY() == tile.getY())
				hitPlayer(p, new Hit(smorks).randDamage(NAPALM_MIN, NAPALM_MAX).ignoreDefence().ignorePrayer());
		if (!borkDead && under(bork.getPosition(), BORK_SIZE, tile))
			schedule.after(1, () -> hurtBork());
	}

	/** Fire + splat on each tile, then (+5) a ragged tile for 199 ticks. */
	private void fire(Map<Long, Integer> patch, int z) {
		for (Map.Entry<Long, Integer> e : patch.entrySet()) {
			Position t = new Position((int) (e.getKey() >> 32), (int) (long) e.getKey(), z);
			if (!inArena(t) || under(smorks.getPosition(), CART_SIZE, t))
				continue;
			int rot = Random.get(3);
			Raid4Fx.mapAnim(SPOT_NAPALM_FIRE, t, e.getValue(), 0);
			Raid4Fx.mapAnim(SPOT_NAPALM_SPLAT + rot, t, e.getValue(), 0);
			schedule.after(5, () -> raggedTile(t, rot));
		}
	}

	/**
	 * A ragged tile: TFA_BORK_RAGGED_TILE, spawned as shape 10 (centrepiece, non-blocking). Its loc has one model and no
	 * shape list, which the client only draws for shape 10: spawned as ground decoration (22) it drew nothing, and the
	 * black seen in game was only the napalm splat fading (2026-10-29). Shape 10 also leaves the map's grass alone.
	 */
	private void raggedTile(Position t, int rot) {
		if (complete)
			return;
		long k = key(t);
		GameObject old = raggedLocs.remove(k);
		if (old != null)
			instance.removeObject(old);
		raggedLocs.put(k, instance.spawnObject(LOC_RAGGED, t, 10, rot));
		ragged.put(k, new long[] { schedule.now() + RAGGED_TICKS, rot });
	}

	private void tickRagged(long now) {
		for (var it = ragged.entrySet().iterator(); it.hasNext();) {
			var e = it.next();
			if (now < e.getValue()[0])
				continue;
			GameObject obj = raggedLocs.remove(e.getKey());
			if (obj != null) {
				Raid4Fx.mapAnim(SPOT_NAPALM_DESPAWN + (int) e.getValue()[1], obj.getPosition(), 0, 0);
				instance.removeObject(obj);
			}
			it.remove();
		}
		// a raider on a ragged tile: 2 a tick and set alight (1..3 every 4, 5 times)
		for (Player p : fighters()) {
			if (ragged.containsKey(key(p.getPosition()))) {
				hitPlayer(p, new Hit().fixedDamage(FIRE).ignoreDefence().ignorePrayer());
				burn(p, Random.get(10) == 0 ? 3 : Random.get(1, 2));
			}
		}
	}

	private void hurtBork() {
		if (borkDead)
			return;
		bork.hit(new Hit().randDamage(25, 28).ignoreDefence().ignorePrayer());
		Raid4Fx.sound(fighters(), 12254, bork.getPosition().relative(2, 2), 0, 10);
	}

	/* The big bomb */

	private void bigBomb() {
		List<Player> pool = fighters();
		if (pool.isEmpty())
			return;
		Player victim = target != null && pool.contains(target) && Random.get(1) == 0 ? target : pool.get(Random.get(pool.size() - 1));
		Position cart = local(CART_X + 1, CART_Y + 1);
		smorks.forceText("Big bomb! Bork watch out!");
		schedule.after(2, () -> {
			if (smorksDead)
				return;
			Position tile = victim.getPosition().copy();
			smorks.animate(SEQ_BIG_CANNON);
			Raid4Fx.projectile(fighters(), smorks, SPOT_BIG_BALL, cart, null, tile, 270, 0, 15, 150, 30, 0);
			Raid4Fx.mapAnim(SPOT_SHADOW_150, tile, 0, 0);
			Raid4Fx.sound(fighters(), 12248, cart, 15, 15);
			Position[] beforeLanding = new Position[1];
			schedule.after(4, () -> beforeLanding[0] = borkDead ? null : bork.getPosition().copy());
			schedule.after(5, () -> bombLands(tile, beforeLanding[0]));
		});
	}

	private void bombLands(Position tile, Position borkBefore) {
		if (borkDead || borkBefore == null)
			return;
		// a raider on the landing tile takes the ball and its blast: two splats (bork_plan: 4 and 19, one sample)
		for (Player p : fighters())
			if (p.getPosition().getX() == tile.getX() && p.getPosition().getY() == tile.getY()) {
				hitPlayer(p, new Hit(smorks).randDamage(1, 10).ignoreDefence().ignorePrayer());
				hitPlayer(p, new Hit(smorks).randDamage(10, 25).ignoreDefence().ignorePrayer());
			}
		if (under(borkBefore, BORK_SIZE, tile))
			stun();
		else {
			if (distance(borkBefore, BORK_SIZE, tile) == 1)
				hurtBork();
			miss(tile);
		}
	}

	/* §5 stunned, then angry */

	private void stun() {
		long l = schedule.now();
		borkBody.setDazed(true);
		bork.animate(SEQ_STUN);
		Raid4Fx.spot(bork, 0, SPOT_STUN);
		if (target != null)
			bork.face(target.getPosition().copy());
		bork.forceText("Oof.");
		smorks.forceText(STUN_LINES[Random.get(STUN_LINES.length - 1)]);
		Position c = bork.getPosition().relative(2, 2);
		Raid4Fx.sound(fighters(), 12249, c, 0, 20);
		Raid4Fx.sound(fighters(), 3201, c, 0, 20);
		Raid4Fx.sound(fighters(), 12273, c, 0, 15);
		Raid4Fx.sound(fighters(), 12758, c, 0, 15);
		gongBlockedUntil = Long.MAX_VALUE;
		standUntil = Long.MAX_VALUE;
		schedule.after(14, () -> { // L+14
			if (borkDead)
				return;
			borkBody.setDazed(false);
			bork.animate(SEQ_STOMP_IN);
			bork.forceText("Bork very angry!!");
		});
		schedule.after(16, () -> {
			if (borkDead)
				return;
			bork.animate(SEQ_STOMP);
			Raid4Fx.spot(bork, 0, SPOT_STOMP);
		});
		for (int t : new int[] { 17, 20, 23, 26 })
			schedule.after(t, this::slam);
		schedule.after(22, () -> {
			if (!borkDead)
				bork.animate(SEQ_STOMP_OUT);
		});
		schedule.after(29, () -> resumeAfter(l + 29));
	}

	/** §9 the bomb missed: a fire patch, then the stomp without the stun. */
	private void miss(Position tile) {
		long l = schedule.now();
		Raid4Fx.sound(fighters(), 12249, tile, 0, 20);
		Raid4Fx.sound(fighters(), 12250, tile, 30, 20);
		Map<Long, Integer> patch = new HashMap<>();
		patch.put(key(tile), 0);
		int[][] n8 = { { 0, 1 }, { 1, 1 }, { 1, 0 }, { 1, -1 }, { 0, -1 }, { -1, -1 }, { -1, 0 }, { -1, 1 } };
		int extra = Random.get(2, 4);
		List<int[]> pool = new ArrayList<>(List.of(n8));
		java.util.Collections.shuffle(pool);
		for (int i = 0; i < extra; i++)
			patch.put(key(tile.relative(pool.get(i)[0], pool.get(i)[1])), 0);
		fire(patch, tile.getZ());
		standUntil = Long.MAX_VALUE;
		gongBlockedUntil = l + 2; // the gong due at L or L+1 sounds; then none until the resume
		schedule.after(2, () -> {
			gongBlockedUntil = Long.MAX_VALUE;
			if (borkDead)
				return;
			if (target != null)
				bork.face(target.getPosition().copy());
			bork.forceText(MISS_LINES[Random.get(MISS_LINES.length - 1)]);
		});
		schedule.after(3, () -> {
			if (!borkDead && target != null)
				bork.face(target);
		});
		schedule.after(5, () -> {
			if (borkDead)
				return;
			bork.animate(SEQ_STOMP);
			Raid4Fx.spot(bork, 0, SPOT_STOMP);
		});
		for (int t : new int[] { 6, 9, 12, 15 })
			schedule.after(t, this::slam);
		schedule.after(11, () -> {
			if (!borkDead)
				bork.animate(SEQ_STOMP_OUT);
		});
		schedule.after(18, () -> resumeAfter(l + 18));
	}

	/** A new random target; the drum again 4 ticks after a change, 2 after keeping the old one. */
	private void resumeAfter(long at) {
		if (borkDead)
			return;
		boolean changed = pickTarget(true);
		long resume = schedule.now() + (changed ? 4 : 2);
		gongBlockedUntil = resume;
		standUntil = resume;
		nextGong = resume;
	}

	/** Picks a raider at random; "Bork smash <name> now." only when it is another. Returns true on a change. */
	private boolean pickTarget(boolean announce) {
		List<Player> pool = fighters();
		pool.removeIf(p -> !inArena(p.getPosition()));
		if (pool.isEmpty())
			return false;
		Player next = pool.get(Random.get(pool.size() - 1));
		boolean changed = next != target;
		target = next;
		if (changed && announce && !borkDead)
			bork.forceText("Bork smash " + next.getName() + " now.");
		if (!borkDead)
			bork.face(next);
		return changed;
	}

	/** A slam: dust over the whole arena from the middle of the edge he faces his target across, but the cart's
	 * shadow; 14..48 off Protect from Melee, 18..33 under it. */
	private void slam() {
		if (borkDead)
			return;
		Position sw = bork.getPosition();
		Position origin = edgeMiddle(sw);
		Set<Long> tiles = new HashSet<>();
		int[] b = arenaBounds();
		for (int x = b[0]; x <= b[2]; x++)
			for (int y = b[1]; y <= b[3]; y++) {
				Position t = local(x, y);
				if (under(sw, BORK_SIZE, t) || shadowed(origin, t))
					continue;
				tiles.add(key(t));
				Raid4Fx.mapAnim(SPOT_DUST, t, Math.min(20, Math.abs(t.getX() - origin.getX()) + Math.abs(t.getY() - origin.getY())), 0);
			}
		for (Player p : fighters()) {
			if (!tiles.contains(key(p.getPosition())))
				continue;
			boolean prayed = p.getPrayer().isActive(Prayer.PROTECT_FROM_MELEE);
			int dmg = prayed ? Random.get(SLAM_PRAYED_MIN, SLAM_PRAYED_MAX) : Random.get(SLAM_MIN, SLAM_MAX);
			hitPlayer(p, new Hit(bork, AttackStyle.CRUSH).fixedDamage(dmg).ignoreDefence().ignorePrayer());
		}
	}

	private boolean slamAlt;

	/** The middle of the 4x4's edge facing his target, alternating its two middle tiles. */
	private Position edgeMiddle(Position sw) {
		slamAlt = !slamAlt;
		int m = slamAlt ? 1 : 2;
		if (target == null)
			return sw.relative(m, BORK_SIZE);
		int dx = target.getPosition().getX() - (sw.getX() + 2), dy = target.getPosition().getY() - (sw.getY() + 2);
		if (Math.abs(dx) > Math.abs(dy))
			return dx > 0 ? sw.relative(BORK_SIZE, m) : sw.relative(-1, m);
		return dy > 0 ? sw.relative(m, BORK_SIZE) : sw.relative(m, -1);
	}

	/* The speed (§4): 75 / 50 / 25 % of the Smorks */

	private void checkSpeed() {
		if (smorksDead || smorksMax <= 0)
			return;
		int hp = smorks.getHp();
		int want = hp <= smorksMax / 4 ? 3 : hp <= smorksMax / 2 ? 2 : hp <= smorksMax * 3 / 4 ? 1 : 0;
		while (tier < want) {
			tier++;
			if (!borkDead)
				bork.forceText(BORK_SPEED[tier - 1]);
			smorks.forceText(SMORK_SPEED[tier - 1]);
		}
	}

	/* The rage (Smorks first, the owner's description + the wiki; unverified past +8) */

	private void startRage() {
		rage = true;
		borkBody.chase(null);
		schedule.after(9, () -> {
			if (!borkDead)
				bork.forceText("Bork kill human for that!");
		});
		schedule.after(10, () -> {
			if (!borkDead)
				bork.animate(SEQ_RAGE_IN);
		});
		gongBlockedUntil = schedule.now() + 11;
		nextGong = schedule.now() + 11;
	}

	private void rageBeat() {
		bork.animate(SEQ_RAGE_GONG);
		Position drum = bork.getPosition().relative(2, 2);
		int[] b = arenaBounds();
		for (int x = b[0]; x <= b[2]; x++)
			for (int y = b[1]; y <= b[3]; y++) {
				Position t = local(x, y);
				if (!under(bork.getPosition(), BORK_SIZE, t))
					Raid4Fx.mapAnim(SPOT_DUST, t, Math.min(20, Math.abs(t.getX() - drum.getX()) + Math.abs(t.getY() - drum.getY())), 0);
			}
		for (Player p : fighters()) {
			if (!inArena(p.getPosition()))
				continue;
			int dmg = Random.get(BEAT1_MIN, BEAT1_MAX);
			if (p.getPrayer().isActive(Prayer.PROTECT_FROM_MELEE))
				dmg /= 4;
			hitPlayer(p, new Hit(bork, AttackStyle.CRUSH).fixedDamage(dmg).ignoreDefence().ignorePrayer());
		}
	}

	/* ---------------------------------------------------------------- §6 deaths */

	@Override
	public void onNpcDying(NPC npc) {
		if (npc == bork && !borkDead) {
			borkDead = true;
			borkBody.chase(null);
			Raid4Fx.spot(bork, 0, SPOT_DEATH);
			if (!smorksDead) {
				bork.forceText("Smorks... avenge... Bork.");
				schedule.after(7, () -> {
					if (!smorksDead)
						smorks.forceText("Human die for that!");
				});
				nextSlot = schedule.now() + 11;
			}
		} else if (npc == smorks && !smorksDead) {
			smorksDead = true;
			Raid4Fx.resetFace(smorks);
			Position cart = local(CART_X + 1, CART_Y + 1);
			if (borkDead) {
				smorks.forceText("Uh oh.");
				Raid4Fx.sound(fighters(), 12761, cart, 0, 15);
			} else {
				smorks.forceText("No, Smorks fail Bork!");
				Raid4Fx.sound(fighters(), 12757, cart, 0, 15);
				startRage();
			}
			schedule.after(2, () -> {
				Raid4Fx.spot(smorks, 0, SPOT_SMORK_EXPLOSION);
				Raid4Fx.spot(smorks, 1, SPOT_SMORK_GOBLINS);
				Raid4Fx.spot(smorks, 2, SPOT_SMORK_TOWER);
				Raid4Fx.sound(fighters(), 12251, cart, 0, 15);
			});
			schedule.after(7, () -> Raid4Fx.fade(smorks, 0, 30, 0, 255));
		}
	}

	@Override
	public void onNpcDeath(NPC npc) {
		if (npc == smorks) {
			for (int x = CART_X; x < CART_X + CART_SIZE; x++)
				for (int y = CART_Y; y < CART_Y + CART_SIZE; y++) {
					Position p = local(x, y);
					io.ruin.model.map.Tile.get(p.getX(), p.getY(), p.getZ(), true).unflagUnmovable();
				}
			removeNpc(handler);
		}
		if (borkDead && smorksDead && !complete) {
			finish("Bork and the Smorks dead");
			schedule.after(10, () -> {
				for (Player p : hud)
					p.getHealthHud().close();
				hud.clear();
			});
		}
	}

	private void updateHud() {
		if (bork == null)
			return;
		checkSpeed();
		int b = borkDead ? 0 : Math.max(0, bork.getHp()), s = smorksDead ? 0 : Math.max(0, smorks.getHp());
		for (Player p : fighters()) {
			if (hud.add(p) || !p.getHealthHud().isOpened()) {
				p.getHealthHud().open(true, NPC_HANDLER, borkMax);
				p.getHealthHud().updateValue(b);
				p.getHealthHud().extraBar(s, smorksMax);
			}
		}
		if (b != shownBork || s != shownSmorks) {
			shownBork = b;
			shownSmorks = s;
			for (Player p : hud) {
				p.getHealthHud().updateValue(b);
				p.getHealthHud().extraBar(s, smorksMax);
			}
		}
	}

	@Override
	protected void clearHazards() {
		super.clearHazards();
		for (GameObject obj : raggedLocs.values())
			instance.removeObject(obj);
		raggedLocs.clear();
		ragged.clear();
	}

	@Override
	public void cleanup() {
		super.cleanup();
		for (Player p : hud)
			p.getHealthHud().close();
		hud.clear();
	}
}
