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
import io.ruin.model.skills.prayer.Prayer;
import io.ruin.model.stat.Stat;
import io.ruin.model.stat.StatType;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static io.ruin.model.activities.raids.tfa.Raid4Ids.*;

/**
 * Slot 4 west: the Dagon'hai (region 16215), rebuilt 2026-10-28 from the RSProx captures of one kill and three wipes
 * (docs/rsprox-analysis/guthixian_archive/dagonhai_plan.md). Section numbers (§) refer to that plan. Local coordinates =
 * absolute - (4032, 5568) (template square (63,87)). Monk 0 is the Elder, 1 and 2 the monks; the cauldron is
 * "Ritual Stability".
 */
@Slf4j
public class DagonhaiRoom extends Raid4BossRoom {

	/* §1 */
	static final int ARRIVE_X = 51, ARRIVE_Y = 31; // (4082..4084, 5598..5600)
	static final int TRANSPORTER_SIDE_X = 48, TRANSPORTER_SIDE_Y = 31; // (4080,5599), beside the transporter (4079,5599)
	static final int LANDING_X = 37, LANDING_Y = 31; // ten across (4069,5599)
	static final int NAVIGATOR_X = 41, NAVIGATOR_Y = 31; // (4073,5599)
	static final int[][] MONKS = { { 25, 31 }, { 26, 32 }, { 26, 30 } }; // Elder (4057,5599), monk 1 (4058,5600), monk 2 (4058,5598)
	static final int CAULDRON_X = 32, CAULDRON_Y = 30; // (4064,5598) facing south
	/** The cauldron's loop, a corner a cycle, a tile each 2 ticks: (4063,5599) (4066,5599) (4066,5597) (4063,5597). */
	static final int[][] LOOP = { { 31, 31 }, { 34, 31 }, { 34, 29 }, { 31, 29 } };
	/** The book monks' shelves (monk 1 north and east, monk 2 south and west; tiles est.). */
	static final int[][][] SHELVES = { { { 33, 38 }, { 39, 35 } }, { { 32, 24 }, { 26, 27 } } };
	static final int[][] RIFT_SITES = { { 36, 34 }, { 37, 30 } }; // north-east, east of the cauldron (est.)
	static final int BOOK_X = 17, BOOK_Y = 29; // the Book of Life and Death (4049,5597)
	static final int ARENA_MIN_X = 20, ARENA_MAX_X = 42, ARENA_MIN_Y = 21, ARENA_MAX_Y = 41;

	/* §2-§8 */
	static final int CYCLE = 50, CHANNEL_TICKS = 18, STUN_TICKS = 11, ELDER_EVERY = 5;
	static final int STABILITY = 1000, STABILITY_STEP = 200, BREAKS = 5, RITUAL_STEP = 25;
	static final String[] ELEMENTS = { "fire", "water", "air", "earth" };
	static final String[] ELEMENT_LINES = { "I'll feed the flames!", "I'll guide the flow!", "I'll focus the winds!", "I'll strengthen the bond!" };
	static final String[] ELEMENT_SPELLS = { "Fire", "Water", "Wind", "Earth" };

	/* Damage (§Damage) */
	static final int ELDER_MAX = 30, ZAMORAK_MIN = 20, ZAMORAK_MAX = 27, ZAMORAK_POOL = 25, BANDOS_CATCH = 51, BANDOS_MIN = 23,
			BANDOS_MAX = 29, SARA_MAX = 13, SARA_HEAL = 13, IMP_MAX = 39, ENRAGE_MIN = 15, ENRAGE_MAX = 22;

	/* Ids */
	static final int[] SEQ_CHANNEL = { 14607, 14612, 14617 }, SEQ_CHANNEL_END = { 14609, 14614, 14619 },
			SEQ_CHANNEL_INTERRUPTED = { 14611, 14616, 14621 }, SEQ_DEATH = { 14652, 14653, 14654 };
	static final int[] SEQ_SEARCH_IDLE = { 14622, 14630 }, SEQ_SEARCH_END = { 14623, 14631 };
	static final int[][] SEQ_THROW = { { 14624, 14625, 14626 }, { 14632, 14633, 14634 } }; // Z, B, S
	static final int[][] SPOT_THROW = { { 4107, 4109, 4108 }, { 4110, 4112, 4111 } }; // Z, B, S
	static final int[] SPOT_BOOK_PROJ = { 4113, 4115, 4114 }, SPOT_BOOK_IMPACT = { 4116, 4118, 4117 };
	static final int[] SEQ_CAULDRON_GOD = { 14657, 14661, 14659 }, SPOT_CAULDRON_GOD = { 4143, 4155, 4150 };
	static final String[] GOD_LINES = { "Zamorak fuels us!", "Bandosian strength, feed the flame!", "Saradominist zeal, feed the flame!" };
	static final int SPOT_LANE = 4127; // vfx_dagonhai_throw_book_floor_saradomin_<way>_01 (every god): N NE E SE S SW W NW
	static final int SEQ_ELDER_RANGED = 14593, SEQ_ELDER_MAGIC = 14597, SEQ_ELDER_GUTHIX = 14604, SEQ_MONK_MAGIC_2 = 14599,
			SEQ_MONK_MAGIC_3 = 14601, SEQ_CAULDRON_GUTHIX_1 = 14678, SEQ_CAULDRON_GUTHIX_2 = 14679, SEQ_IMP_ATTACK = 14529,
			SEQ_IMP_DESPAWN = 14530, SEQ_BOOK_OPEN = 14682, SEQ_HEAL_CASTING = 652;
	static final int SPOT_ELDER_RANGED = 4070, SPOT_RANGED_PROJ = 4071, SPOT_RANGED_HIT = 4072, SPOT_ELDER_MAGIC = 4073,
			SPOT_SURGE = 1459, SPOT_STUNNED = 3993, SPOT_HEAL_CASTING = 84, SPOT_ZAMORAK_BOLT = 4144, SPOT_ZAMORAK_POOL = 4145,
			SPOT_ZAMORAK_POOL_2 = 4147, SPOT_ZAMORAK_AOE = 4149, SPOT_BANDOS_RING = 4156, SPOT_SARA_TELEGRAPH = 4063,
			SPOT_SARA_LIGHTNING = 4068, SPOT_SARA_HEAL_TELEGRAPH = 4065, SPOT_SARA_HEAL_LIGHTNING = 4069, SPOT_DEMON_BUFF = 4162,
			SPOT_DEATH_3 = 4163, SPOT_DUST_EXPLOSION = 4161, SPOT_GUTHIX_2 = 4160, SPOT_UNLOCK_BOOK = 4166,
			SPOT_ELDER_GUTHIX = 4076, SPOT_GUTHIX_ENERGY = 4077, SPOT_MONK_MAGIC_2 = 4074, SPOT_MONK_MAGIC_3 = 4075,
			SPOT_IMP_DESPAWN = 3736;
	static final int[] NPC_MONK = { NPC_DAGONHAI_ELDER, NPC_DAGONHAI_MONK_1, NPC_DAGONHAI_MONK_2 };
	static final int[] NPC_MONK_WEAK = { NPC_DAGONHAI_ELDER_WEAK, NPC_DAGONHAI_MONK_1_WEAK, NPC_DAGONHAI_MONK_2_WEAK };

	private final class Monk {
		final int index;
		NPC npc;
		int element = -1;
		long channelEnd, stunnedUntil;
		boolean dead;

		Monk(int index, NPC npc) {
			this.index = index;
			this.npc = npc;
		}

		boolean channelling() {
			return element >= 0;
		}
	}

	private final Monk[] trio = new Monk[3];
	private NPC cauldron;
	private int shieldMax, shield, stability = STABILITY, breaks, ritual, god, cycleNo, loopCorner;
	private long t0 = Long.MAX_VALUE, nextElder;
	private boolean killed, enraged, wiped;
	private final List<NPC> imps = new ArrayList<>();
	private final java.util.Map<NPC, Integer> impSwings = new java.util.HashMap<>();
	private final Set<Player> hud = new HashSet<>();
	private int shownStability = -1;

	@Override
	public Raid4RoomType type() {
		return Raid4RoomType.DAGONHAI;
	}

	@Override
	protected void spawnFixtures() {
		for (int i = 0; i < 3; i++) {
			NPC n = spawn(NPC_MONK[i], local(MONKS[i][0], MONKS[i][1]), Direction.WEST);
			Raid4Puppet body = (Raid4Puppet) n.getCombat();
			body.bind(this);
			trio[i] = new Monk(i, n);
			final int idx = i;
			body.setPreDamage(hit -> shieldHit(trio[idx], hit));
		}
		cauldron = spawn(NPC_DAGONHAI_CAULDRON, local(CAULDRON_X, CAULDRON_Y), Direction.SOUTH);
		((Raid4Puppet) cauldron.getCombat()).bind(this);
		// §1 the opening lines before anyone crosses
		schedule.after(14, () -> trio[0].npc.forceText("Ah, the Book of Life and Death."));
		schedule.after(20, () -> trio[1].npc.forceText("What does it do?"));
		schedule.after(27, () -> trio[2].npc.forceText("Gives us power, of course!"));
	}

	@Override
	protected int[] arenaBounds() {
		return new int[] { ARENA_MIN_X, ARENA_MIN_Y, ARENA_MAX_X, ARENA_MAX_Y };
	}

	@Override
	public boolean inArena(Position p) {
		int x = lx(p), y = ly(p);
		return x >= ARENA_MIN_X && x <= LANDING_X + 1 && y >= ARENA_MIN_Y && y <= ARENA_MAX_Y;
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
	protected String challengeName() {
		return "The Dagon'hai";
	}

	/* ---------------------------------------------------------------- the engage */

	@Override
	protected void onStart(Player first) {
		shieldMax = Raid4Scaling.tableHp(640, scale);
		shield = shieldMax;
		long now = schedule.now();
		t0 = now + 20;
		nextElder = now + 5;
		updateHud();
		log.info("Raid 4: Dagon'hai engaged by {} (scale {}, shield {})", first.getName(), scale, shieldMax);
	}

	@Override
	protected void onTick() {
		long now = schedule.now();
		updateHud();
		if (wiped)
			return;
		if (!killed && !enraged && now >= t0) {
			cycle(now - t0);
			if (now - t0 >= CYCLE - 1)
				t0 += CYCLE;
		}
		for (Monk m : trio) {
			if (m.dead || !m.channelling())
				continue;
			m.npc.face(cauldron.getPosition().relative(1, 1));
			if (now >= m.channelEnd)
				completeChannel(m);
		}
		// the book monks search their shelves between throws
		for (int i = 1; i <= 2; i++) {
			Monk m = trio[i];
			if (!m.dead && !m.channelling() && now >= m.stunnedUntil && !killed && !enraged)
				m.npc.animate(SEQ_SEARCH_IDLE[i - 1]);
		}
		if (now >= nextElder) {
			nextElder = now + ELDER_EVERY;
			if (enraged)
				enrageVolley();
			else
				elderAttack(now);
		}
		tickImps(now);
	}

	/* §3 the cycle: 0 Elder channels, 13 search end, 16 throw, 20 the book flies, 21 its attack, 23 the cauldron moves,
	 * 25 the other monk channels */
	private void cycle(long t) {
		int bookMonk = cycleNo % 2 == 0 ? 1 : 2;
		if (t == 0) {
			cycleNo++;
			bookMonk = cycleNo % 2 == 0 ? 1 : 2;
			channel(trio[0]);
			// the book monk walks to a shelf
			int[] shelf = SHELVES[bookMonk - 1][Random.get(1)];
			Monk m = trio[bookMonk];
			if (!m.dead)
				m.npc.stepAbs(local(shelf[0], shelf[1]).getX(), local(shelf[0], shelf[1]).getY(), StepType.NORMAL);
		} else if (t == 13) {
			Monk m = trio[bookMonk];
			if (!m.dead) {
				m.npc.animate(SEQ_SEARCH_END[bookMonk - 1]);
				m.npc.forceText("What have we here?");
			}
		} else if (t == 16) {
			throwBook(trio[bookMonk], bookMonk);
		} else if (t == 23) {
			moveCauldron();
		} else if (t == 25) {
			channel(trio[bookMonk == 1 ? 2 : 1]);
		}
	}

	/* §4 channels */

	private void channel(Monk m) {
		if (m.dead || killed || enraged || schedule.now() < m.stunnedUntil)
			return;
		m.element = Random.get(3);
		m.channelEnd = schedule.now() + CHANNEL_TICKS;
		m.npc.animate(SEQ_CHANNEL[m.index]);
		Raid4Fx.spot(m.npc, 2, 4079 + 8 * m.index + 2 * m.element, 0, 0, true);
		m.npc.forceText(ELEMENT_LINES[m.element]);
	}

	private void completeChannel(Monk m) {
		m.npc.animate(SEQ_CHANNEL_END[m.index]);
		Raid4Fx.spot(m.npc, 2, 4079 + 8 * m.index + 2 * m.element + 1);
		m.npc.forceText("Ever closer to ultimate power!");
		m.element = -1;
		addRitual();
	}

	/** A spell of the channel's element landing on the channeller interrupts it: stunned 11 ticks. */
	private void interrupt(Monk m) {
		m.element = -1;
		m.npc.animate(SEQ_CHANNEL_INTERRUPTED[m.index]);
		m.npc.forceText("Argh!");
		long now = schedule.now();
		m.stunnedUntil = now + STUN_TICKS;
		int height = m.index == 0 ? 150 : 124;
		for (int t = 0; t < STUN_TICKS; t += 2)
			schedule.after(Math.max(1, t), () -> {
				if (!m.dead)
					Raid4Fx.spot(m.npc, 2, SPOT_STUNNED, 0, height, false);
			});
	}

	/* §2 the shared shield */

	private void shieldHit(Monk m, Hit hit) {
		if (killed || enraged)
			return; // the unshielded forms take hits normally
		if (m.channelling() && hit.attackSpell != null && hit.attackSpell.getClass().getSimpleName().startsWith(ELEMENT_SPELLS[m.element]))
			interrupt(m);
		int dmg = Math.min(hit.damage, shield);
		shield -= dmg;
		hit.type(HitType.DAMAGE_SHIELD_ME);
		// the pool takes it; the monk's own health stays full
		schedule.after(1, () -> {
			Stat hp = m.npc.getCombat().getStat(StatType.Hitpoints);
			hp.currentLevel = hp.fixedLevel;
		});
		if (shield <= 0)
			shieldBroken();
	}

	private void shieldBroken() {
		breaks++;
		if (breaks >= BREAKS) {
			kill();
			return;
		}
		stability = Math.max(0, stability - STABILITY_STEP);
		cauldron.hit(new Hit(HitType.DAMAGE).fixedDamage(20).ignoreDefence().ignorePrayer());
		for (Monk m : trio) {
			if (m.dead)
				continue;
			m.npc.animate(SEQ_HEAL_CASTING);
			Raid4Fx.spot(m.npc, 0, SPOT_HEAL_CASTING, 0, 120, false);
		}
		trio[Random.get(2)].npc.forceText("The power of Zamorak protects us!");
		shield = shieldMax;
		rifts();
	}

	/** Two rifts at a site facing the cauldron; imps two at a time at +8, +11, +14; the rifts fade at +17, gone +19. */
	private void rifts() {
		int[] site = RIFT_SITES[Random.get(1)];
		NPC[] r = new NPC[2];
		for (int i = 0; i < 2; i++) {
			r[i] = spawnTemp(NPC_DAGONHAI_RIFT, local(site[0], site[1] + 2 * i), Direction.WEST);
			((Raid4Puppet) r[i].getCombat()).bind(this);
			r[i].face(cauldron.getPosition().relative(1, 1));
		}
		for (int t : new int[] { 8, 11, 14 })
			schedule.after(t, () -> {
				for (NPC rift : r)
					if (!rift.isRemoved() && !killed)
						spawnImp(rift.getPosition());
			});
		schedule.after(17, () -> {
			for (NPC rift : r)
				Raid4Fx.fade(rift, 0, 30, 0, 255);
		});
		schedule.after(19, () -> {
			for (NPC rift : r)
				removeNpc(rift);
		});
	}

	private void spawnImp(Position at) {
		NPC imp = spawnTemp(NPC_DAGONHAI_IMP, at, Direction.WEST);
		((Raid4Puppet) imp.getCombat()).bind(this);
		imp.forceText(Random.get(1) == 0 ? "For the masters!" : "Chaos reign!");
		Raid4Fx.sound(fighters(), 314, at, 0, 10);
		imps.add(imp);
		impSwings.put(imp, 0);
	}

	private void tickImps(long now) {
		for (NPC imp : new ArrayList<>(imps)) {
			if (imp.isRemoved() || imp.getHp() <= 0) {
				imps.remove(imp);
				continue;
			}
			Player target = nearest(imp.getPosition());
			if (target == null)
				continue;
			imp.face(target);
			if (imp.getPosition().distance(target.getPosition()) > 1) {
				imp.stepAbs(target.getAbsX(), target.getAbsY(), StepType.NORMAL);
				continue;
			}
			if (now % 4 != 0)
				continue;
			int swings = impSwings.getOrDefault(imp, 0);
			imp.animate(SEQ_IMP_ATTACK);
			int dmg = target.getPrayer().isActive(Prayer.PROTECT_FROM_MELEE) ? 0 : Random.get(IMP_MAX);
			hitPlayer(target, new Hit(imp, AttackStyle.STAB).fixedDamage(dmg).ignoreDefence().ignorePrayer());
			impSwings.put(imp, ++swings);
			if (swings >= 2) { // after two swings it despawns
				imps.remove(imp);
				Raid4Fx.resetFace(imp);
				if (imp.avatar() != null)
					imp.avatarExtended().setAllOpsInvisible();
				Raid4Fx.spot(imp, 0, SPOT_IMP_DESPAWN);
				imp.animate(SEQ_IMP_DESPAWN);
				schedule.after(5, () -> removeNpc(imp));
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

	/* The ritual and the stability */

	private void addRitual() {
		ritual += RITUAL_STEP;
		cauldron.hit(new Hit(HitType.DAMAGE).fixedDamage(RITUAL_STEP).ignoreDefence().ignorePrayer());
		if (ritual >= 100)
			enrage();
	}

	/* §5 the books */

	private void throwBook(Monk m, int bookMonk) {
		if (m.dead || killed || enraged)
			return;
		int g = god % 3;
		god++;
		Position mid = cauldron.getPosition().relative(1, 1);
		m.npc.face(mid);
		m.npc.animate(SEQ_THROW[bookMonk - 1][g]);
		Raid4Fx.spot(m.npc, 2, SPOT_THROW[bookMonk - 1][g]);
		m.npc.forceText(GOD_LINES[g]);
		// the lane: every tile from the monk to the cauldron's middle, both corners of each diagonal step
		Position from = m.npc.getPosition();
		List<Position> lane = laneTiles(from, mid);
		int way = way(mid.getX() - from.getX(), mid.getY() - from.getY());
		for (Position t : lane)
			Raid4Fx.mapAnim(SPOT_LANE + way, t, 0, 0);
		schedule.after(4, () -> bookFlies(m, g, lane, from, mid));
	}

	private List<Position> laneTiles(Position from, Position to) {
		Set<Long> keys = new LinkedHashSet<>();
		List<Position> out = new ArrayList<>();
		int x = from.getX(), y = from.getY();
		while (x != to.getX() || y != to.getY()) {
			int sx = Integer.signum(to.getX() - x), sy = Integer.signum(to.getY() - y);
			if (sx != 0 && sy != 0) {
				Position c1 = new Position(x + sx, y, from.getZ()), c2 = new Position(x, y + sy, from.getZ());
				if (keys.add(key(c1)))
					out.add(c1);
				if (keys.add(key(c2)))
					out.add(c2);
			}
			x += sx;
			y += sy;
			Position t = new Position(x, y, from.getZ());
			if (keys.add(key(t)))
				out.add(t);
		}
		return out;
	}

	/** The lane's way, N NE E SE S SW W NW; pure when one axis is over 3x the other. */
	private static int way(int dx, int dy) {
		if (Math.abs(dx) > 3 * Math.abs(dy))
			return dx > 0 ? 2 : 6;
		if (Math.abs(dy) > 3 * Math.abs(dx))
			return dy > 0 ? 0 : 4;
		if (dx > 0)
			return dy > 0 ? 1 : 3;
		return dy > 0 ? 7 : 5;
	}

	/** t0+20: a raider on the lane catches it; else it goes into the cauldron. +21 the god's attack. */
	private void bookFlies(Monk m, int g, List<Position> lane, Position from, Position mid) {
		if (killed || enraged)
			return;
		Player catcher = null;
		for (Player p : fighters())
			for (Position t : lane)
				if (p.getPosition().getX() == t.getX() && p.getPosition().getY() == t.getY()) {
					catcher = p;
					break;
				}
		Position origin;
		if (catcher != null) {
			Raid4Fx.projectile(fighters(), m.npc, SPOT_BOOK_PROJ[g], from, catcher, catcher.getPosition(), 160, 140, 0, 30, 16, 64);
			Raid4Fx.spot(catcher, 2, SPOT_BOOK_IMPACT[g], 30, 0);
			m.npc.forceText("Curse you!");
			origin = catcher.getPosition().copy();
		} else {
			Raid4Fx.projectile(fighters(), m.npc, SPOT_BOOK_PROJ[g], from, null, mid, 160, 50, 0, 30, 16, 64);
			cauldron.animate(SEQ_CAULDRON_GOD[g]);
			Raid4Fx.spot(cauldron, 0, SPOT_CAULDRON_GOD[g], 30, 0, false);
			addRitual();
			origin = mid;
		}
		final Player c = catcher;
		schedule.after(1, () -> godAttack(g, c, origin));
	}

	/* §6 the god attacks */

	private void godAttack(int g, Player catcher, Position origin) {
		if (killed || enraged)
			return;
		switch (g) {
			case 0 -> zamorak(catcher, origin);
			case 1 -> bandos(catcher, origin);
			default -> saradomin(catcher, origin);
		}
	}

	private void zamorak(Player catcher, Position origin) {
		if (catcher != null) {
			Raid4Fx.spot(catcher, 2, SPOT_ZAMORAK_AOE, 0, 0);
			hitPlayer(catcher, new Hit().randDamage(ZAMORAK_MIN, ZAMORAK_MAX).ignoreDefence().ignorePrayer());
			for (int i = 1; i <= 5; i++)
				schedule.after(4 * i, () -> {
					if (fighters().contains(catcher))
						hitPlayer(catcher, new Hit(HitType.DAMAGE).fixedDamage(3).ignoreDefence().ignorePrayer());
				});
		}
		int[][] n8 = { { 0, 1 }, { 1, 1 }, { 1, 0 }, { 1, -1 }, { 0, -1 }, { -1, -1 }, { -1, 0 }, { -1, 1 } };
		for (Player p : fighters()) {
			for (Position t : new Position[] { p.getPosition().copy(), p.getPosition().relative(n8[Random.get(7)][0], n8[Random.get(7)][1]) }) {
				int end = origin.distance(t) > 6 ? 150 : 90;
				Raid4Fx.projectile(fighters(), null, SPOT_ZAMORAK_BOLT, origin, null, t, 140, 0, 0, end, 45, 0);
				Raid4Fx.mapAnim(Random.get(1) == 0 ? SPOT_ZAMORAK_POOL : SPOT_ZAMORAK_POOL_2, t, 0, 0);
				schedule.after(end / 30, () -> {
					Raid4Fx.mapAnim(SPOT_ZAMORAK_AOE, t, 0, 0);
					Raid4Fx.sound(fighters(), 9206, t, 0, 3);
					for (Player q : fighters())
						if (q.getPosition().getX() == t.getX() && q.getPosition().getY() == t.getY())
							hitPlayer(q, new Hit().randDamage(ZAMORAK_POOL).ignoreDefence().ignorePrayer());
				});
			}
		}
	}

	private void bandos(Player catcher, Position origin) {
		if (catcher != null) {
			Raid4Fx.spot(catcher, 4, SPOT_BANDOS_RING, 0, 0);
			hitPlayer(catcher, new Hit().fixedDamage(BANDOS_CATCH).ignoreDefence().ignorePrayer());
		}
		Set<Player> struck = new HashSet<>();
		if (catcher != null)
			struck.add(catcher);
		for (int k = 1; k <= 8; k++) {
			int ring = k;
			for (int dx = -k; dx <= k; dx++)
				for (int dy = -k; dy <= k; dy++)
					if (Math.max(Math.abs(dx), Math.abs(dy)) == k)
						Raid4Fx.mapAnim(SPOT_BANDOS_RING + (k - 1) % 4, origin.relative(dx, dy), 30 * k, 0);
			schedule.after(k, () -> {
				for (Player p : fighters()) {
					if (struck.contains(p))
						continue;
					int d = Math.max(Math.abs(p.getAbsX() - origin.getX()), Math.abs(p.getAbsY() - origin.getY()));
					if (d == ring) {
						struck.add(p);
						hitPlayer(p, new Hit().randDamage(BANDOS_MIN, BANDOS_MAX).ignoreDefence().ignorePrayer());
					}
				}
			});
		}
	}

	private void saradomin(Player catcher, Position origin) {
		List<Position> strike = new ArrayList<>();
		for (Player p : fighters())
			strike.add(p.getPosition().copy());
		while (strike.size() < 27)
			strike.add(randomArenaTile());
		for (Position t : strike) {
			int d = Random.get(121, 126);
			Raid4Fx.mapAnim(SPOT_SARA_TELEGRAPH, t, 0, 0);
			Raid4Fx.mapAnim(SPOT_SARA_LIGHTNING, t, d, 0);
		}
		schedule.after(4, () -> {
			for (Player p : fighters())
				for (Position t : strike)
					if (p.getPosition().getX() == t.getX() && p.getPosition().getY() == t.getY()) {
						hitPlayer(p, new Hit().randDamage(SARA_MAX).ignoreDefence().ignorePrayer());
						break;
					}
		});
		List<Position> heal = new ArrayList<>();
		for (int i = 0; i < 6; i++)
			heal.add(randomArenaTile());
		for (Position t : heal) {
			Raid4Fx.mapAnim(SPOT_SARA_HEAL_TELEGRAPH, t, 0, 0);
			Raid4Fx.mapAnim(SPOT_SARA_HEAL_LIGHTNING, t, Random.get(151, 156), 0);
		}
		schedule.after(5, () -> {
			for (Player p : fighters())
				for (Position t : heal)
					if (p.getPosition().getX() == t.getX() && p.getPosition().getY() == t.getY()) {
						p.incrementHp(SARA_HEAL);
						break;
					}
		});
		if (catcher != null) {
			Raid4Fx.spot(catcher, 2, SPOT_SARA_HEAL_LIGHTNING, 0, 0);
			catcher.incrementHp(SARA_HEAL);
		}
	}

	/* The cauldron's loop: a corner a cycle, a tile each 2 ticks */
	private void moveCauldron() {
		loopCorner = (loopCorner + 1) % LOOP.length;
		Position to = local(LOOP[loopCorner][0], LOOP[loopCorner][1]);
		cauldron.stepAbs(to.getX(), to.getY(), StepType.CRAWL);
	}

	/* §7 the Elder: every 5 ticks while neither channelling nor stunned, a raider and any beside them */
	private void elderAttack(long now) {
		Monk elder = trio[0];
		if (elder.dead || elder.channelling() || now < elder.stunnedUntil || killed)
			return;
		List<Player> pool = fighters();
		pool.removeIf(p -> !inArena(p.getPosition()));
		if (pool.isEmpty())
			return;
		Player target = pool.get(Random.get(pool.size() - 1));
		boolean ranged = Random.get(144) < 75;
		elder.npc.face(target);
		elder.npc.animate(ranged ? SEQ_ELDER_RANGED : SEQ_ELDER_MAGIC);
		Raid4Fx.spot(elder.npc, 2, ranged ? SPOT_ELDER_RANGED : SPOT_ELDER_MAGIC);
		Position from = elder.npc.getPosition().relative(1, 1);
		List<Player> hit = new ArrayList<>();
		for (Player p : pool)
			if (p == target || p.getPosition().distance(target.getPosition()) <= 1)
				hit.add(p);
		for (Player p : hit) {
			Raid4Fx.projectile(fighters(), elder.npc, ranged ? SPOT_RANGED_PROJ : SPOT_SURGE, from, p, p.getPosition(), 188, 124, 60, 150, 0, 80);
			schedule.after(5, () -> {
				if (!fighters().contains(p))
					return;
				if (ranged)
					Raid4Fx.spot(p, 2, SPOT_RANGED_HIT, 0, 0);
				boolean prayed = p.getPrayer().isActive(ranged ? Prayer.PROTECT_FROM_MISSILES : Prayer.PROTECT_FROM_MAGIC);
				hitPlayer(p, new Hit(elder.npc, ranged ? AttackStyle.RANGED : AttackStyle.MAGIC)
						.fixedDamage(prayed ? 0 : Random.get(ELDER_MAX)).ignoreDefence().ignorePrayer());
			});
		}
	}

	/* §8 the kill (the fifth break) and the enrage (the ritual at 100) */

	private void unshield(String elderLine) {
		for (Monk m : trio) {
			if (m.dead)
				continue;
			m.element = -1;
			m.npc.animate(SEQ_HEAL_CASTING);
			m.npc.transform(NPC_MONK_WEAK[m.index]);
			if (m.npc.avatar() != null)
				m.npc.avatarExtended().setAllOpsVisible();
			Stat hp = m.npc.getCombat().getStat(StatType.Hitpoints);
			hp.fixedLevel = scale >= 3 ? 10 : 7;
			hp.currentLevel = hp.fixedLevel;
		}
	}

	private void kill() {
		killed = true;
		unshield(null);
		trio[1].npc.forceText("AAARRGGH!");
		trio[2].npc.forceText("AAARRGGH!");
		trio[0].npc.forceText("You incompetent fools!");
		cauldron.animate(SEQ_CAULDRON_GOD[0]);
		Raid4Fx.spot(cauldron, 0, SPOT_CAULDRON_GOD[0]);
		Raid4Fx.spot(cauldron, 1, SPOT_DUST_EXPLOSION, 25, 0, false);
		Raid4Fx.fade(cauldron, 30, 50, 0, 255);
		schedule.after(3, () -> removeNpc(cauldron));
		schedule.after(1, () -> {
			if (!trio[0].dead)
				trio[0].npc.forceText("You cannot stop the will of Zamorak!");
		});
		schedule.after(8, () -> {
			if (!trio[0].dead)
				trio[0].npc.forceText("We cannot fail!");
		});
		for (NPC imp : new ArrayList<>(imps))
			removeNpc(imp);
		imps.clear();
	}

	private void enrage() {
		if (enraged || killed)
			return;
		enraged = true;
		unshield(null);
		cauldron.animate(SEQ_CAULDRON_GUTHIX_1);
		schedule.after(1, () -> {
			trio[0].npc.forceText("Life and death, at our command!");
			trio[1].npc.forceText("Guthix's power, now Zamorak's!");
			trio[2].npc.forceText("The tome's power is ours!");
			cauldron.animate(SEQ_CAULDRON_GUTHIX_2);
			Raid4Fx.spot(cauldron, 0, SPOT_GUTHIX_2);
			Position mid = cauldron.getPosition().relative(1, 1);
			for (int i = 1; i <= mid.getX() - local(BOOK_X, BOOK_Y).getX(); i++)
				Raid4Fx.mapAnim(SPOT_UNLOCK_BOOK, mid.relative(-i, 0), 4 * i, 0);
		});
		schedule.after(2, () -> bandos(null, cauldron.getPosition().relative(1, 1)));
		schedule.after(4, () -> trio[0].npc.forceText("No survivors!"));
		nextElder = schedule.now() + 6;
	}

	private void enrageVolley() {
		Monk elder = trio[0];
		if (!elder.dead) {
			elder.npc.animate(SEQ_ELDER_GUTHIX);
			Raid4Fx.spot(elder.npc, 0, SPOT_ELDER_GUTHIX);
			Raid4Fx.spot(elder.npc, 1, SPOT_GUTHIX_ENERGY);
			Raid4Fx.sound(fighters(), 12327, elder.npc.getPosition(), 0, 10);
		}
		schedule.after(1, () -> {
			if (!trio[1].dead) {
				trio[1].npc.animate(SEQ_MONK_MAGIC_2);
				Raid4Fx.spot(trio[1].npc, 0, SPOT_MONK_MAGIC_2);
			}
			if (!trio[2].dead) {
				trio[2].npc.animate(SEQ_MONK_MAGIC_3);
				Raid4Fx.spot(trio[2].npc, 0, SPOT_MONK_MAGIC_3);
			}
		});
		for (Player p : fighters())
			for (int b = 0; b < 2; b++)
				schedule.after(2 + b, () -> {
					if (fighters().contains(p))
						hitPlayer(p, new Hit(trio[0].npc, AttackStyle.MAGIC).randDamage(ENRAGE_MIN, ENRAGE_MAX).ignoreDefence().ignorePrayer());
				});
	}

	@Override
	public void onNpcDying(NPC npc) {
		for (Monk m : trio) {
			if (m.npc == npc && !m.dead) {
				m.dead = true;
				m.element = -1;
				if (npc.avatar() != null)
					npc.avatarExtended().setAllOpsInvisible();
				if (m.index == 2)
					Raid4Fx.spot(npc, 0, SPOT_DEATH_3);
			}
		}
		if (npc.getId() == NPC_DAGONHAI_IMP) {
			imps.remove(npc);
			Player killer = nearest(npc.getPosition());
			if (killer != null)
				Raid4Fx.spot(killer, 1, SPOT_DEMON_BUFF, 0, 0);
		}
	}

	@Override
	public void onNpcDeath(NPC npc) {
		if (complete)
			return;
		boolean allDead = true;
		for (Monk m : trio)
			allDead &= m.dead;
		if (allDead) {
			for (NPC imp : new ArrayList<>(imps))
				removeNpc(imp);
			imps.clear();
			message("The demonic energy fades...");
			finish("Dagon'hai dead");
			schedule.after(8, () -> {
				for (Player p : hud)
					p.getHealthHud().close();
				hud.clear();
			});
		}
	}

	/** The HUD: the cauldron's bar ("Ritual Stability", its cache name), 1000 down by 200 a break. */
	private void updateHud() {
		if (!started)
			return;
		for (Player p : fighters())
			if (hud.add(p) || !p.getHealthHud().isOpened()) {
				p.getHealthHud().open(true, NPC_DAGONHAI_CAULDRON, STABILITY);
				p.getHealthHud().updateValue(stability);
			}
		if (stability != shownStability) {
			shownStability = stability;
			for (Player p : hud)
				p.getHealthHud().updateValue(stability);
		}
	}

	@Override
	protected void clearHazards() {
		super.clearHazards();
		for (NPC imp : new ArrayList<>(imps))
			removeNpc(imp);
		imps.clear();
	}

	@Override
	public void cleanup() {
		super.cleanup();
		for (Player p : hud)
			p.getHealthHud().close();
		hud.clear();
	}
}
