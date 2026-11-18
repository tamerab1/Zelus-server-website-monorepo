package io.ruin.model.activities.bosses.hueycoatl;

import io.ruin.api.utils.Random;
import io.ruin.cache.Color;
import io.ruin.cache.Icon;
import io.ruin.cache.ObjType;
import io.ruin.model.World;
import io.ruin.model.combat.Hit;
import io.ruin.model.combat.HitType;
import io.ruin.model.entity.BossHudTracker;
import io.ruin.model.entity.npc.NPC;
import io.ruin.model.entity.npc.NPCCombat;
import io.ruin.model.entity.player.Player;
import io.ruin.model.entity.shared.listeners.HitListener;
import io.ruin.model.inter.ToplevelComponent;
import io.ruin.model.item.Item;
import io.ruin.model.item.actions.impl.pet.Pet;
import io.ruin.model.map.Bounds;
import io.ruin.model.map.MapListener;
import io.ruin.model.map.Position;
import io.ruin.model.map.Projectile;
import io.ruin.model.map.Tile;
import io.ruin.model.map.object.GameObject;
import io.ruin.model.map.object.actions.ObjectAction;
import io.ruin.model.skills.prayer.Prayer;
import io.ruin.model.var.VarPlayerRepository;
import io.ruin.utility.Broadcast;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * The Hueycoatl (Hueycoatl's lair, the Darkfrost, Varlamore): one shared public encounter on the overworld map
 * (region 5939), as on OSRS. Ids are the cache's own (gameval names in brackets); tiles come from the map's blocked
 * flags and from live client data (public bot/plugin sources that record npc tiles, spotanims and tick timings).
 * <ul>
 * <li>Camp (north, y 3287+): Bank buffalo 55199, Scoreboard 55198 (interface 884), the chain 55401 (multiloc on varbit
 * huey_in_area 11362: 55202 outside with Climb/Quick-climb/Peek, 55201 inside with Climb/Quick-climb).</li>
 * <li>Body: five "Hueycoatl body" npcs (14017, 250 HP) in the five mountainside caves (locs 55243 / diagonal 55246,
 * which play the body anims 11683-11690). A broken body leaves Rubble (14018). The barrier 55206 (3x6 at 1515,3268)
 * retracts (11700, then 55207) once all five are broken.</li>
 * <li>Head (14009, 7x7 on the cliff at 1509,3290, fought from y 3289): during the body phase it is the placeholder
 * 14010 (no Attack option); it wakes (11681) when the barrier opens. Attacks: one style per volley at everyone on the
 * summit (anims 11669-11671, cast spotanim 2968/2971/2974, projectile 2969/2972/2975, impact 2970/2973/2976); the
 * matching protection prayer cuts it to 0-2, else 0-14. Glowing symbols (loc 55209) under players, lightning
 * (spotanim 3001) 2 ticks later, damage 2 ticks after that (up to 16).</li>
 * <li>Prayer pillars (magic 1504,3287 / ranged 1505,3289 / melee 1507,3290): engravings fill S1-S5 from the team's
 * protection prayers; all three full lights the braziers (+30% damage).</li>
 * <li>At 50%: the shield (14011, activation 11676) and the tail (14014, 5x5, appear 11720 / idle 11722 / disappear
 * 11721). Damage to the tail is capped at 4 (9 with a crush-highest bonus). Every switch the head plays 11676, 4 ticks
 * later the tail slams (11721) and a shockwave rolls across the summit 2 tiles a tick (spotanims 2977-2992, impacts
 * 2993-3000) from (1505,3282) eastwards or (1519,3284) westwards, two tiles thick: stay at y 3285+ or run through it.</li>
 * <li>Tail broken (14015, 11723): the head screeches (14013, 11677) and is attackable again, attacking faster, while
 * the broken tail keeps slamming. Defeat: 11679 then 14012 (knockout idle); 30 seconds later it resets.</li>
 * </ul>
 */
@Slf4j
public class Hueycoatl extends NPCCombat {

	/* npcs */
	public static final int HEAD = 14009, HEAD_PLACEHOLDER = 14010, HEAD_INVULNERABLE = 14011, HEAD_DEFEATED = 14012, HEAD_ENRAGED = 14013;
	public static final int TAIL = 14014, TAIL_BROKEN = 14015, BODY = 14017, RUBBLE = 14018;
	private static final int LOBBY_LEADER = 13996, LOBBY_GUARD = 14000;
	/** huey_seer: stands by the prayer pillars and calls the fight out (overhead text, as on OSRS) */
	private static final int SEER = 14008;
	private static final Position SEER_POS = new Position(1506, 3286, 0);
	/* locs */
	private static final int SCOREBOARD = 55198, BANK_BUFFALO = 55199, MEETING_FLAG = 55203;
	private static final int ENTRANCE = 55401, CHAIN_OUT = 55202, CHAIN_IN = 55201, SLOPE = 55234;
	private static final Position SLOPE_POS = new Position(1520, 3290, 0);
	private static final int BARRIER = 55206, BARRIER_RETRACTED = 55207, BARRIER_RESPAWN = 55208, ATTACK_TILE = 55209;
	private static final int CAVE = 55243, CAVE_DIAG = 55246;
	/* head anims */
	private static final int IDLE_ANIM = 11668, SHIELD_ACTIVATION = 11676, SCREECH = 11677, SCREECH_RESET = 11678, KNOCKOUT = 11679,
		AWAKEN = 11681, AWAKEN_RESET = 11682;
	private static final int[] ATTACK_ANIM = { 11669, 11670, 11671 }, SHIELD_ATTACK_ANIM = { 11673, 11674, 11675 };
	/* body / barrier / tail anims */
	private static final int BODY_SPAWN = 11683, BODY_DEATH = 11687, BODY_DEATH_IDLE = 11689; // +1 = the diagonal cave
	private static final int BARRIER_TRANSITION = 11700;
	private static final int TAIL_APPEAR = 11720, TAIL_DISAPPEAR = 11721, TAIL_BREAK = 11723;
	/* spotanims */
	private static final int[] CAST_GFX = { 2968, 2971, 2974 }, IMPACT_GFX = { 2970, 2973, 2976 };
	private static final Projectile[] FEATHER = {
		new Projectile(2969, 110, 31, 30, 45, 10, 16, 192),
		new Projectile(2972, 110, 31, 30, 45, 10, 16, 192),
		new Projectile(2975, 110, 31, 30, 45, 10, 16, 192) };
	private static final int LIGHTNING_GFX = 3001, LOOT_BEAM_GFX = 3002;
	/* shockwave: front / second layer / impact, for rows north, level, south of the origin row (east-moving, west-moving) */
	private static final int[] WAVE_EAST = { 2984, 2980, 2981 }, WAVE_WEST = { 2983, 2978, 2982 };
	private static final int SECOND_LAYER = 8, IMPACT = 16;

	private static final Prayer[] PRAYERS = { Prayer.PROTECT_FROM_MELEE, Prayer.PROTECT_FROM_MISSILES, Prayer.PROTECT_FROM_MAGIC };

	/* tiles */
	private static final Position HEAD_POS = new Position(1509, 3290, 0); // 7x7 on the cliff; attacked from y 3289
	private static final Position BARRIER_POS = new Position(1515, 3268, 0);
	private static final Position ENTRANCE_POS = new Position(1527, 3285, 0);
	private static final Position INSIDE = new Position(1527, 3282, 0), OUTSIDE = new Position(1527, 3289, 0);
	/* body npc tiles (the cave mouths) */
	private static final Position[] BODY_POS = { new Position(1520, 3273, 0), new Position(1524, 3270, 0), new Position(1524, 3277, 0),
		new Position(1530, 3276, 0), new Position(1527, 3273, 0) };
	/* tail 5x5 SW tiles, west / east of the summit; the waves start just inside them */
	/* the tail's SW tile, west / east: players hit it from (1509,3285) / (1515,3285) (bot data: the wave-dodge attack tiles),
	 * so it sits right beside them, centred on the shockwave origin rows */
	private static final Position[] TAIL_POS = { new Position(1504, 3280, 0), new Position(1516, 3282, 0) };
	private static final Position[] WAVE_ORIGIN = { new Position(1505, 3282, 0), new Position(1519, 3284, 0) };
	private static final int WAVE_MIN_Y = 3276, WAVE_MAX_Y = 3284, WAVE_MIN_X = 1501, WAVE_MAX_X = 1523;
	/* brazier (off; on = off - 1) and engraving (empty; S1-S5 = empty + 1..5) per style: melee, ranged, magic */
	private static final int[] BRAZIER_OFF = { 55227, 55219, 55211 }, ENGRAVING_EMPTY = { 55228, 55220, 55212 };
	private static final Position[] PILLAR_POS = { new Position(1507, 3290, 0), new Position(1505, 3289, 0), new Position(1504, 3287, 0) };

	private static final Bounds SUMMIT = new Bounds(1500, 3264, 1523, 3289, 0);
	private static final Bounds MOUNTAINSIDE = new Bounds(1524, 3264, 1535, 3284, 0);

	private static final VarPlayerRepository HUEY_IN_AREA = VarPlayerRepository.varpbit(11362, false);

	enum Phase { BODY, HEAD, TAIL, FINAL, DEAD }

	/* ------------------------------------------------------------------ encounter state (one public fight) */

	private static Phase phase = Phase.BODY;
	private static NPC head, tail;
	private static final List<BodyPart> bodies = new ArrayList<>();
	private static final List<NPC> rubble = new ArrayList<>();
	private static final Map<Integer, int[]> contribution = new HashMap<>(); // userId -> {body, headTail}
	private static final Set<Integer> diedThisFight = new HashSet<>();
	private static int respawnIn = -1, ticks = 0, startTick = 0;
	private static int tailSide = 0, tailSwitchIn = 0, shockwaveIn = 0;
	private static final int[] pillarFill = new int[3];
	private static int seerCharge = 0;
	private static boolean braziersLit = false;
	private static final Map<Position, Integer> symbols = new HashMap<>();
	private static final Map<Position, GameObject> symbolObjects = new HashMap<>();
	/* world scoreboard (since the server started) */
	private static int globalKills, globalDeaths, globalBest = -1;
	private static NPC seer;
	private static int seerTalkIn = 0;
	/** OSRS's boss health bar (the head) for everyone on the mountain once the head is up */
	private static final BossHudTracker hud = new BossHudTracker();

	private static final class BodyPart {
		final NPC npc;
		final GameObject cave;
		final boolean diag;

		BodyPart(NPC npc, GameObject cave, boolean diag) {
			this.npc = npc;
			this.cave = cave;
			this.diag = diag;
		}
	}

	private static boolean inFight(Player p) {
		return p.getHeight() == 0 && (SUMMIT.inBounds(p) || MOUNTAINSIDE.inBounds(p));
	}

	private static List<Player> fighters() {
		List<Player> list = new ArrayList<>();
		for (Player p : World.players())
			if (p != null && inFight(p) && !p.getCombat().isDead())
				list.add(p);
		return list;
	}

	/** OSRS game messages for the encounter (purple), sent to everyone on the mountain */
	private static void announce(String text) {
		for (Player p : fighters())
			p.sendMessage(Color.PURPLE.wrap(text));
	}

	private static void seerSays(String text) {
		if (seer != null && !seer.isRemoved())
			seer.forceText(text);
	}

	private static List<Player> summitPlayers() {
		List<Player> list = new ArrayList<>();
		for (Player p : World.players())
			if (p != null && p.getHeight() == 0 && SUMMIT.inBounds(p) && !p.getCombat().isDead())
				list.add(p);
		return list;
	}

	private static void track(Hit hit, boolean body) {
		if (hit.attacker == null || hit.attacker.player == null || hit.damage <= 0)
			return;
		int[] c = contribution.computeIfAbsent(hit.attacker.player.getUserId(), k -> new int[2]);
		c[body ? 0 : 1] += hit.damage;
	}

	/* ------------------------------------------------------------------ lifecycle + camp */

	public static void register() {
		// the chain: clients send the multiloc's base id (55401); its transforms are listed too
		for (int id : new int[] { ENTRANCE, CHAIN_OUT, CHAIN_IN }) {
			ObjectAction.register(id, 1, (p, obj) -> climb(p, true));
			ObjectAction.register(id, 2, (p, obj) -> climb(p, false));
		}
		ObjectAction.register(ENTRANCE, 3, (p, obj) -> peek(p));
		ObjectAction.register(CHAIN_OUT, 3, (p, obj) -> peek(p));
		// the slope (1x1 at 1520,3290) has no walkable side tile in the map - every orthogonal neighbour is flagged
		// blocked - so it is used from any adjacent tile, including the summit's edge (1519,3289) diagonal to it
		World.startEvent(e -> {
			for (int tries = 0; tries < 50; tries++) {
				GameObject slope = Tile.getObject(SLOPE, SLOPE_POS.getX(), SLOPE_POS.getY(), 0);
				if (slope != null) {
					slope.skipReachCheck = pos -> pos.getZ() == 0 && Math.abs(pos.getX() - SLOPE_POS.getX()) <= 1
						&& Math.abs(pos.getY() - SLOPE_POS.getY()) <= 1;
					return;
				}
				e.delay(1);
			}
			log.warn("Hueycoatl: the slope loc wasn't found at {}", SLOPE_POS);
		});
		ObjectAction.register(SLOPE, 1, (p, obj) -> slide(p, true));
		ObjectAction.register(SLOPE, 2, (p, obj) -> slide(p, false));
		ObjectAction.register(SCOREBOARD, 1, (p, obj) -> openScoreboard(p));
		ObjectAction.register(BANK_BUFFALO, 1, (p, obj) -> p.getBank().open());
		ObjectAction.register(MEETING_FLAG, 1, (p, obj) -> p.sendMessage("Private Hueycoatl groups aren't available on Karn yet: fight her with everyone at the summit."));
		MapListener.registerBounds(SUMMIT, MOUNTAINSIDE)
			.onEnter(p -> HUEY_IN_AREA.set(p, 1))
			.onExit((p, logout) -> HUEY_IN_AREA.set(p, 0));
		World.startEvent(e -> {
			e.delay(10);
			seer = new NPC(SEER).spawn(SEER_POS.getX(), SEER_POS.getY(), 0, io.ruin.model.map.Direction.SOUTH, 0);
			startBodyPhase();
			while (true) {
				try {
					tick();
				} catch (Exception ex) {
					log.error("Hueycoatl tick failed", ex);
				}
				e.delay(1);
			}
		});
	}

	private static String phaseName() {
		return switch (phase) {
			case BODY -> "breaking her body on the mountainside";
			case HEAD -> "fighting her head at the summit";
			case TAIL -> "fighting her tail";
			case FINAL -> "at her final stand";
			case DEAD -> "waiting for her to return";
		};
	}

	private static void climb(Player p, boolean animated) {
		boolean goingIn = p.getAbsY() >= ENTRANCE_POS.getY();
		Position dest = goingIn ? INSIDE : OUTSIDE;
		if (goingIn && (p.getCombat().getLevel() < 85 || p.getStats().get(io.ruin.model.stat.StatType.Prayer).fixedLevel < 43))
			p.sendMessage(Color.RED.wrap("Warning: the Hueycoatl is recommended for 85+ combat and 43+ Prayer."));
		p.startEvent(e -> {
			p.lock();
			if (animated) {
				p.animate(828);
				e.delay(2);
			}
			p.getMovement().teleport(dest);
			HUEY_IN_AREA.set(p, goingIn ? 1 : 0);
			p.unlock();
		});
	}

	private static void peek(Player p) {
		int n = fighters().size();
		p.sendMessage("You peek up the chain. " + (n == 0 ? "Nobody is" : n == 1 ? "1 adventurer is" : n + " adventurers are")
			+ " fighting the Hueycoatl, " + phaseName() + ".");
	}

	private static void slide(Player p, boolean animated) {
		p.startEvent(e -> {
			p.lock();
			if (animated) {
				p.animate(4767);
				e.delay(2);
			}
			p.getMovement().teleport(OUTSIDE);
			HUEY_IN_AREA.set(p, 0);
			p.unlock();
		});
	}

	/* interface 884 (huey_scoreboard): personal / global kills, deaths, best time */
	private static void openScoreboard(Player p) {
		p.openInterface(ToplevelComponent.MAINMODAL, 884);
		p.getPacketSender().sendString(884, 9, String.valueOf(p.hueycoatlKills.getKills()));
		p.getPacketSender().sendString(884, 11, String.valueOf(p.hueycoatlDeaths));
		p.getPacketSender().sendString(884, 13, time(p.hueycoatlBestTicks));
		p.getPacketSender().sendString(884, 15, String.valueOf(globalKills));
		p.getPacketSender().sendString(884, 17, String.valueOf(globalDeaths));
		p.getPacketSender().sendString(884, 19, time(globalBest));
	}

	private static String time(int ticks) {
		if (ticks <= 0)
			return "-";
		int s = (int) Math.round(ticks * 0.6);
		return s / 60 + ":" + String.format("%02d", s % 60);
	}

	private static void tick() {
		ticks++;
		trackDeaths();
		if (phase == Phase.HEAD || phase == Phase.TAIL || phase == Phase.FINAL) {
			if (head != null && !head.isRemoved())
				hud.update(fighters(), HEAD, head.getHp(), head.getMaxHp());
		} else {
			hud.closeAll();
		}
		switch (phase) {
			case BODY -> bodyTick();
			case HEAD, TAIL, FINAL -> {
				if (head == null || head.isRemoved()) {
					resetEncounter();
					return;
				}
				pillarTick();
				symbolTick();
				// the seer urges the team on while the pillars aren't charged
				if (phase == Phase.HEAD && !braziersLit && --seerTalkIn <= 0) {
					seerTalkIn = 20;
					seerSays("Keep praying to charge the pillars!");
				}
				if (phase == Phase.TAIL)
					tailTick();
				if (phase == Phase.FINAL && tail != null && --shockwaveIn <= 0) {
					shockwaveIn = 25;
					slamSequence(false);
				}
			}
			case DEAD -> {
				symbolTick();
				if (--respawnIn <= 0)
					resetEncounter();
			}
		}
	}

	private static void trackDeaths() {
		for (Player p : World.players()) {
			if (p == null || p.getHeight() != 0 || !(SUMMIT.inBounds(p) || MOUNTAINSIDE.inBounds(p)))
				continue;
			if (p.getCombat().isDead()) {
				if (diedThisFight.add(p.getUserId())) {
					p.hueycoatlDeaths++;
					globalDeaths++;
				}
			} else {
				diedThisFight.remove(p.getUserId());
			}
		}
	}

	/* ------------------------------------------------------------------ body phase */

	private static void startBodyPhase() {
		phase = Phase.BODY;
		contribution.clear();
		startTick = ticks;
		if (head == null) {
			head = new NPC(HEAD_PLACEHOLDER).spawn(HEAD_POS.getX(), HEAD_POS.getY(), 0, 0);
			head.face(new Position(HEAD_POS.getX() + 3, HEAD_POS.getY() - 5, 0));
		}
		for (Position p : BODY_POS) {
			GameObject cave = nearestCave(p);
			boolean diag = cave != null && cave.getId() == CAVE_DIAG;
			NPC body = new NPC(BODY).spawn(p.getX(), p.getY(), 0, 0);
			body.noKillPoints = true; // the body segments in the caves give no Karn / PVM points (user 2026-11-14)
			body.hitListener = new HitListener().postDamage(h -> track(h, true));
			if (cave != null)
				cave.animate(BODY_SPAWN + (diag ? 1 : 0));
			bodies.add(new BodyPart(body, cave, diag));
		}
		setBarrier(false, true);
		announce("The Hueycoatl begins to awaken...");
	}

	private static GameObject nearestCave(Position p) {
		GameObject best = null;
		int bestDist = Integer.MAX_VALUE;
		for (int dx = -3; dx <= 3; dx++)
			for (int dy = -3; dy <= 3; dy++)
				for (int id : new int[] { CAVE, CAVE_DIAG }) {
					GameObject o = Tile.getObject(id, p.getX() + dx, p.getY() + dy, 0);
					int d = Math.abs(dx) + Math.abs(dy);
					if (o != null && d < bestDist) {
						best = o;
						bestDist = d;
					}
				}
		return best;
	}

	private static void bodyTick() {
		for (Iterator<BodyPart> it = bodies.iterator(); it.hasNext();) {
			BodyPart b = it.next();
			if (!b.npc.getCombat().isDead() && !b.npc.isRemoved())
				continue;
			it.remove();
			Position at = b.npc.getPosition().copy();
			if (b.cave != null) {
				GameObject cave = b.cave;
				boolean diag = b.diag;
				cave.animate(BODY_DEATH + (diag ? 1 : 0));
				World.startEvent(e -> {
					e.delay(3);
					cave.animate(BODY_DEATH_IDLE + (diag ? 1 : 0));
				});
			}
			World.startEvent(e -> {
				e.delay(2);
				rubble.add(new NPC(RUBBLE).spawn(at.getX(), at.getY(), 0, 0));
			});
		}
		if (bodies.isEmpty())
			startHeadPhase();
	}

	private static void startHeadPhase() {
		phase = Phase.HEAD;
		setBarrier(true, false);
		head.transform(HEAD);
		head.animate(AWAKEN);
		announce("The Hueycoatl shifts, opening a path up the mountain.");
		seerTalkIn = 3;
	}

	private static void setBarrier(boolean retracted, boolean respawning) {
		int[] ids = { BARRIER, BARRIER_RETRACTED, BARRIER_RESPAWN };
		GameObject b = null;
		for (int id : ids)
			if (b == null)
				b = Tile.getObject(id, BARRIER_POS.getX(), BARRIER_POS.getY(), 0);
		if (b == null)
			return;
		GameObject barrier = b;
		if (retracted) {
			barrier.animate(BARRIER_TRANSITION);
			World.startEvent(e -> {
				e.delay(3);
				barrier.setId(BARRIER_RETRACTED);
			});
		} else if (respawning && barrier.getId() == BARRIER_RETRACTED) {
			barrier.setId(BARRIER_RESPAWN);
			World.startEvent(e -> {
				e.delay(4);
				barrier.setId(BARRIER);
			});
		} else {
			barrier.setId(BARRIER);
		}
	}

	private static void resetEncounter() {
		for (BodyPart b : bodies)
			b.npc.remove();
		bodies.clear();
		for (NPC r : rubble)
			r.remove();
		rubble.clear();
		if (tail != null)
			tail.remove();
		tail = null;
		if (head != null && !head.isRemoved()) {
			head.getCombat().setDead(false);
			head.transform(HEAD_PLACEHOLDER);
			head.setHp(head.getMaxHp());
			head.animate(AWAKEN_RESET);
		} else {
			head = null;
		}
		clearSymbols();
		Arrays.fill(pillarFill, 0);
		seerCharge = 0;
		setLit(false);
		for (int i = 0; i < 3; i++)
			setEngraving(i, 0);
		startBodyPhase();
	}

	/* ------------------------------------------------------------------ head (NPCCombat for 14009-14013) */

	private int attackDelay = 6, attacksUntilSymbols = Random.get(3, 5);

	@Override
	public void init() {
		npc.hitListener = new HitListener().preDamage(this::preDamage).postDamage(this::postDamage);
	}

	private void preDamage(Hit hit) {
		if (phase == Phase.BODY || phase == Phase.TAIL || phase == Phase.DEAD || npc.getId() != HEAD) {
			hit.block();
			if (hit.attacker != null && hit.attacker.player != null && phase == Phase.TAIL)
				hit.attacker.player.sendMessage("The Hueycoatl has protected itself and is immune to damage.");
			return;
		}
		if (braziersLit)
			hit.boostDamage(0.3);
	}

	private void postDamage(Hit hit) {
		track(hit, false);
		if (phase == Phase.HEAD && npc.getHp() <= npc.getMaxHp() / 2)
			startTailPhase();
	}

	@Override
	public void follow() {
		// she never leaves the cliff
	}

	@Override
	public boolean attack() {
		return false; // timer-driven from process()
	}

	@Override
	public void process() {
		if (npc != head || phase == Phase.DEAD || phase == Phase.BODY || isDead())
			return;
		if (--attackDelay > 0)
			return;
		List<Player> targets = summitPlayers();
		boolean enraged = phase == Phase.FINAL;
		attackDelay = enraged ? 3 : 5;
		if (targets.isEmpty())
			return;
		if (--attacksUntilSymbols <= 0) {
			attacksUntilSymbols = Random.get(3, 5);
			glowingSymbols(targets);
			return;
		}
		rainFire(targets);
	}

	/** One style per volley: every player on the summit gets the same feather. */
	private void rainFire(List<Player> targets) {
		int s = Random.get(0, 2);
		npc.animate((phase == Phase.TAIL ? SHIELD_ATTACK_ANIM : ATTACK_ANIM)[s]);
		npc.graphics(CAST_GFX[s]);
		for (Player p : targets) {
			int delay = FEATHER[s].send(npc, p);
			Hit hit = new Hit(npc).clientDelay(delay).ignoreDefence().ignorePrayer();
			hit.randDamage(p.getPrayer().isActive(PRAYERS[s]) ? 2 : 14);
			hit.postDamage(t -> p.graphics(IMPACT_GFX[s], 0, 0));
			p.hit(hit);
		}
	}

	/** Glowing symbols under (and around) players; lightning 2 ticks later, damage 2 ticks after that. */
	private static void glowingSymbols(List<Player> targets) {
		Set<Position> tiles = new HashSet<>();
		for (Player p : targets) {
			tiles.add(p.getPosition().copy());
			for (int i = 0; i < 2; i++)
				tiles.add(new Position(p.getAbsX() + Random.get(-2, 2), p.getAbsY() + Random.get(-2, 2), 0));
		}
		for (Position t : tiles) {
			if (symbols.containsKey(t) || !SUMMIT.inBounds(t))
				continue;
			symbols.put(t, 4);
			symbolObjects.put(t, GameObject.spawn(ATTACK_TILE, t.getX(), t.getY(), 0, 22, 0));
		}
	}

	private static void symbolTick() {
		if (symbols.isEmpty())
			return;
		List<Position> strike = new ArrayList<>();
		for (Map.Entry<Position, Integer> e : symbols.entrySet()) {
			int left = e.getValue() - 1;
			e.setValue(left);
			if (left == 2)
				World.sendGraphics(LIGHTNING_GFX, 0, 0, e.getKey());
			if (left <= 0)
				strike.add(e.getKey());
		}
		for (Position t : strike) {
			symbols.remove(t);
			GameObject o = symbolObjects.remove(t);
			if (o != null)
				o.remove();
			for (Player p : summitPlayers())
				if (p.getAbsX() == t.getX() && p.getAbsY() == t.getY())
					p.hit(new Hit(head).randDamage(16).ignorePrayer().ignoreDefence());
		}
	}

	private static void clearSymbols() {
		for (GameObject o : symbolObjects.values())
			o.remove();
		symbolObjects.clear();
		symbols.clear();
	}

	/* ------------------------------------------------------------------ prayer pillars */

	private static void pillarTick() {
		List<Player> team = fighters();
		int n = team.size();
		int need = n <= 5 ? 1 : 2 + (n - 6) / 3;
		int[] praying = new int[3];
		for (Player p : team)
			for (int i = 0; i < 3; i++)
				if (p.getPrayer().isActive(PRAYERS[i]))
					praying[i]++;
		for (int i = 0; i < 3; i++) {
			int before = pillarFill[i] / 5;
			pillarFill[i] = Math.max(0, Math.min(25, pillarFill[i] + (praying[i] >= need ? 1 : -1)));
			if (pillarFill[i] / 5 != before)
				setEngraving(i, pillarFill[i] / 5);
		}
		if (++seerCharge >= 30) {
			seerCharge = 0;
			boolean full = pillarFill[0] == 25 && pillarFill[1] == 25 && pillarFill[2] == 25;
			if (full != braziersLit)
				setLit(full);
		}
	}

	private static void setEngraving(int style, int stage) {
		Position p = PILLAR_POS[style];
		for (int s = 0; s <= 5; s++) {
			GameObject o = Tile.getObject(ENGRAVING_EMPTY[style] + s, p.getX(), p.getY(), 0);
			if (o != null) {
				o.setId(ENGRAVING_EMPTY[style] + stage);
				return;
			}
		}
	}

	private static void setLit(boolean lit) {
		braziersLit = lit;
		for (int i = 0; i < 3; i++) {
			Position p = PILLAR_POS[i];
			GameObject o = Tile.getObject(BRAZIER_OFF[i], p.getX(), p.getY(), 0);
			if (o == null)
				o = Tile.getObject(BRAZIER_OFF[i] - 1, p.getX(), p.getY(), 0);
			if (o != null)
				o.setId(lit ? BRAZIER_OFF[i] - 1 : BRAZIER_OFF[i]);
		}
	}

	/* ------------------------------------------------------------------ tail */

	private static void startTailPhase() {
		phase = Phase.TAIL;
		head.transform(HEAD_INVULNERABLE);
		head.animate(SHIELD_ACTIVATION);
		announce("The Hueycoatl shields itself!");
		tailSide = 0;
		tail = new NPC(TAIL).spawn(TAIL_POS[0].getX(), TAIL_POS[0].getY(), 0, 0);
		tail.animate(TAIL_APPEAR);
		tail.hitListener = new HitListener().preDamage(Hueycoatl::tailPreDamage).postDamage(h -> {
			track(h, false);
			if (tail != null && tail.getHp() <= 1 && phase == Phase.TAIL)
				startFinalPhase();
		});
		tailSwitchIn = 33;
	}

	/** set while the tail is switching sides: attacks during the switch don't damage it */
	private static boolean tailSwitching;

	private static void tailPreDamage(Hit hit) {
		if (tail == null || tail.isHidden() || tailSwitching || phase != Phase.TAIL) {
			hit.block();
			return;
		}
		boolean crush = false;
		if (hit.attacker != null && hit.attacker.player != null) {
			var c = hit.attacker.player.getCombat();
			double crushBonus = c.getBonus(io.ruin.model.inter.handlers.EquipmentStats.CRUSH_ATTACK);
			crush = crushBonus >= c.getBonus(io.ruin.model.inter.handlers.EquipmentStats.STAB_ATTACK)
				&& crushBonus >= c.getBonus(io.ruin.model.inter.handlers.EquipmentStats.SLASH_ATTACK)
				&& crushBonus >= c.getBonus(io.ruin.model.inter.handlers.EquipmentStats.MAGIC_ATTACK)
				&& crushBonus >= c.getBonus(io.ruin.model.inter.handlers.EquipmentStats.RANGE_ATTACK);
		}
		int cap = crush ? 9 : 4;
		if (crush && hit.damage <= 0) {
			hit.cannotBlock = true;
			hit.damage = 1;
		}
		hit.type(HitType.DAMAGE_SHIELD_ME); // OSRS: the tail takes cyan shield hitsplats
		hit.damage = Math.min(cap, hit.damage);
		hit.damage = Math.min(hit.damage, Math.max(0, tail.getHp() - 1)); // the tail breaks, it doesn't die
	}

	private static void tailTick() {
		if (tail == null || --tailSwitchIn > 0)
			return;
		tailSwitchIn = 33;
		slamSequence(true);
	}

	/**
	 * The head plays its shield activation; 4 ticks later the tail slams (its disappear anim) and the shockwave
	 * rolls out from that side. A whole tail then resurfaces on the other side; a broken one stays put.
	 */
	private static void slamSequence(boolean switchSides) {
		NPC t = tail;
		if (t == null || head == null)
			return;
		head.animate(SHIELD_ACTIVATION);
		seerSays("Watch out for its slam!");
		World.startEvent(e -> {
			if (switchSides) {
				// wiki: the tail switches sides (attacks during the switch don't land), and on each reappearance it slams
				// the ground and sends a shockwave
				e.delay(2);
				if (tail != t || phase != Phase.TAIL)
					return;
				t.animate(TAIL_DISAPPEAR);
				tailSwitching = true;
				e.delay(2);
				if (tail != t || phase != Phase.TAIL)
					return;
				t.setHidden(true);
				tailSide = 1 - tailSide;
				t.getMovement().teleport(TAIL_POS[tailSide]);
				e.delay(2);
				if (tail != t || phase != Phase.TAIL)
					return;
				t.setHidden(false);
				t.animate(TAIL_APPEAR); // the reappearing slam
				e.delay(2);
				tailSwitching = false;
				if (tail != t)
					return;
				shockwave(tailSide);
				return;
			}
			// the broken tail (final phase) slams in place about every 15 seconds
			e.delay(4);
			if (tail != t)
				return;
			t.animate(TAIL_APPEAR);
			e.delay(2);
			if (tail == t)
				shockwave(tailSide);
		});
	}

	/**
	 * A chevron two tiles thick rolling across the summit at 2 tiles a tick: on row y its front is
	 * origin.x + dir * (2t - |y - origin.y|). Rows y 3276-3284 only (the tiles at y 3285+ are safe).
	 */
	private static void shockwave(int side) {
		Position o = WAVE_ORIGIN[side];
		int dir = side == 0 ? 1 : -1;
		int[] ids = side == 0 ? WAVE_EAST : WAVE_WEST;
		World.startEvent(e -> {
			for (int step = 0; step < 16; step++) {
				boolean any = false;
				Set<Long> hitTiles = new HashSet<>();
				for (int y = WAVE_MIN_Y; y <= WAVE_MAX_Y; y++) {
					int lag = Math.abs(y - o.getY());
					int front = o.getX() + dir * (2 * step - lag);
					int row = y > o.getY() ? 0 : y == o.getY() ? 1 : 2;
					for (int layer = 0; layer < 2; layer++) {
						int x = front - dir * layer;
						if (x < WAVE_MIN_X || x > WAVE_MAX_X || 2 * step - lag - layer < 0)
							continue;
						any = true;
						World.sendGraphics(ids[row] + (layer == 1 ? SECOND_LAYER : 0), 0, 0, x, y, 0);
						hitTiles.add(((long) x << 32) | y);
					}
				}
				for (Player p : summitPlayers()) {
					if (!hitTiles.contains(((long) p.getAbsX() << 32) | p.getAbsY()))
						continue;
					int row = p.getAbsY() > o.getY() ? 0 : p.getAbsY() == o.getY() ? 1 : 2;
					p.graphics(ids[row] + IMPACT, 0, 0);
					p.hit(new Hit(tail != null ? tail : head).randDamage(20, 25).ignorePrayer().ignoreDefence());
				}
				if (!any && step > 2)
					return;
				e.delay(1);
			}
		});
	}

	private static void startFinalPhase() {
		phase = Phase.FINAL;
		tail.transform(TAIL_BROKEN);
		tail.animate(TAIL_BREAK);
		head.transform(HEAD_ENRAGED);
		head.animate(SCREECH);
		announce("The Hueycoatl's shield falls!");
		seerSays("Well done! Now, let's weaken it a little more!");
		NPC h = head;
		World.startEvent(e -> {
			e.delay(4);
			if (head != h || phase != Phase.FINAL)
				return;
			h.animate(SCREECH_RESET);
			h.transform(HEAD);
		});
		shockwaveIn = 25;
	}

	/* ------------------------------------------------------------------ death + loot */

	@Override
	public void startDeath(Hit killHit) {
		if (npc != head) {
			super.startDeath(killHit);
			return;
		}
		setDead(true);
		phase = Phase.DEAD;
		respawnIn = 50; // 30 seconds
		npc.animate(KNOCKOUT);
		NPC dead = npc;
		World.startEvent(e -> {
			e.delay(3);
			if (head == dead && phase == Phase.DEAD)
				dead.transform(HEAD_DEFEATED);
		});
		if (tail != null) {
			tail.remove();
			tail = null;
		}
		clearSymbols();
		int took = ticks - startTick;
		globalKills++;
		if (globalBest < 0 || took < globalBest)
			globalBest = took;
		reward(new Position(1512, 3288, 0), took);
	}

	private static final int[][] MAIN = { // id, min, max, weight /68, noted
		{ 1432, 1, 26, 3, 1 }, { 1333, 1, 20, 3, 1 }, { 1093, 1, 20, 2, 1 }, { 1123, 1, 30, 3, 1 },
		{ 560, 22, 450, 3, 0 }, { 557, 150, 3000, 2, 0 }, { 564, 20, 400, 2, 0 }, { 561, 20, 400, 2, 0 },
		{ 5302, 3, 60, 2, 0 }, { 30088, 1, 20, 2, 0 }, { 5296, 2, 40, 2, 0 }, { 5304, 1, 20, 2, 0 }, { 5295, 1, 20, 2, 0 },
		{ 5298, 1, 20, 3, 0 }, { 5299, 1, 20, 3, 0 },
		{ 30068, 4, 80, 5, 0 }, { 9380, 15, 300, 3, 0 }, { 573, 4, 80, 3, 1 }, { 383, 9, 180, 3, 1 }, { 28924, 17, 350, 3, 0 },
		{ 29378, 5, 100, 3, 1 }, { 536, 2, 40, 3, 1 }, { 449, 6, 120, 2, 1 }, { 2, 60, 1200, 3, 0 }, { 824, 6, 120, 2, 0 },
		{ 225, 10, 200, 2, 1 } };

	private static final int DH_WAND = 30070, HIDE = 30085, TOME_OF_EARTH = 30066, HARD_CLUE = 2722,
		// the Tooth half of key: vanilla 30105 is our "Corrupt logs", so cache stage 43 copied it to 42774 (2026-11-15)
		MOON_KEY_TOOTH = 42774;

	private static void reward(Position at, int took) {
		List<Player> present = fighters();
		int n = present.size();
		int total = 0;
		for (Player p : present) {
			int[] c = contribution.get(p.getUserId());
			if (c != null)
				total += c[0] + c[1];
		}
		Player mvp = null;
		int best = -1;
		for (Player p : present) {
			int[] c = contribution.getOrDefault(p.getUserId(), new int[2]);
			int dmg = c[0] + c[1];
			// wiki: the moon key half (1/75) is rolled for everyone in the arena, regardless of participation
			if (Random.get(1, 75) == 1) {
				Item tooth = new Item(MOON_KEY_TOOTH, 1);
				new io.ruin.model.map.ground.GroundItem(tooth).owner(p).position(p.getPosition().copy()).spawn();
				p.addToCollectionLog(tooth);
			}
			if (dmg <= 0)
				continue;
			p.hueycoatlKills.increment(p);
			if (p.hueycoatlBestTicks <= 0 || took < p.hueycoatlBestTicks) {
				p.hueycoatlBestTicks = took;
				p.sendMessage("Fight duration: " + Color.RED.wrap(time(took)) + " (new personal best)");
			} else {
				p.sendMessage("Fight duration: " + Color.RED.wrap(time(took)) + ". Personal best: " + time(p.hueycoatlBestTicks));
			}
			if (dmg > best) {
				best = dmg;
				mvp = p;
			}
			boolean qualified = n <= 5 ? (c[0] >= 25 && c[1] >= 75) : (c[0] >= 25 || dmg >= 75);
			double scale = qualified ? 1.0 : 0.05;
			if (c[0] < 25) // wiki quotes the start of this message; the rest is ours
				p.sendMessage("As you didn't damage the Hueycoatl's body enough, your rewards have been reduced.");
			double share = total > 0 ? (double) dmg / total : 1.0;
			double contributionScale = Math.min(1.0, share * Math.max(1, n)) * scale;
			List<Item> loot = new ArrayList<>();
			int[] e = rollMain();
			int qty = (int) Math.max(1, Math.round(Random.get(e[1], e[2]) * share * scale));
			int id = e[0];
			if (e[4] == 1) {
				ObjType def = ObjType.get(id);
				if (def != null && def.notedId != -1)
					id = def.notedId;
			}
			loot.add(new Item(id, qty));
			boolean unique = false;
			if (Random.get() < contributionScale / 18.0) {
				int r = Random.get(1, 630); // wand 6/630, hide 22/630 per 1/18 -> weights 108/396/126 of 630
				unique = true;
				if (r <= 108)
					loot.add(new Item(DH_WAND, 1));
				else if (r <= 504)
					loot.add(new Item(HIDE, 3));
				else
					loot.add(new Item(TOME_OF_EARTH, 1));
			}
			if (Random.get(1, 50) == 1)
				loot.add(new Item(HARD_CLUE, 1));
			Position drop = p.getPosition().copy();
			for (Item item : loot) {
				new io.ruin.model.map.ground.GroundItem(item).owner(p).position(drop).spawn();
				p.addToCollectionLog(item);
				if (item.getId() == DH_WAND || item.getId() == HIDE || item.getId() == TOME_OF_EARTH)
					Broadcast.WORLD.sendNewsDropMessage(p, Icon.RARE_DROP, "<col=000000>" + p.getName(),
						" just received " + item.getDef().descriptiveName + " from the Hueycoatl!");
			}
			if (unique)
				p.getPacketSender().sendGraphics(LOOT_BEAM_GFX, 0, 0, drop.getX(), drop.getY(), 0);
			if (Random.get() < contributionScale / 400.0)
				Pet.HUBERTE.unlock(p, 0);
		}
		if (mvp != null)
			new io.ruin.model.map.ground.GroundItem(new Item(532, 1)).owner(mvp).position(mvp.getPosition().copy()).spawn();
	}

	private static int[] rollMain() {
		int roll = Random.get(0, 67);
		for (int[] e : MAIN) {
			roll -= e[3];
			if (roll < 0)
				return e;
		}
		return MAIN[0];
	}
}
