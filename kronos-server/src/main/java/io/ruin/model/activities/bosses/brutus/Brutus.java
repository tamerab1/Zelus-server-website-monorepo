package io.ruin.model.activities.bosses.brutus;

import io.ruin.api.utils.Random;
import io.ruin.cache.Color;
import io.ruin.cache.Icon;
import io.ruin.model.activities.raids.tfa.Raid4Fx;
import io.ruin.model.combat.AttackStyle;
import io.ruin.model.combat.Hit;
import io.ruin.model.entity.npc.NPC;
import io.ruin.model.entity.npc.NPCCombat;
import io.ruin.model.entity.player.Player;
import io.ruin.model.entity.shared.listeners.HitListener;
import io.ruin.model.inter.ToplevelComponent;
import io.ruin.model.inter.dialogue.ItemDialogue;
import io.ruin.model.inter.dialogue.MessageDialogue;
import io.ruin.model.inter.dialogue.OptionsDialogue;
import io.ruin.model.inter.utils.Option;
import io.ruin.model.item.Item;
import io.ruin.model.item.actions.impl.pet.Pet;
import io.ruin.model.map.Bounds;
import io.ruin.model.map.Direction;
import io.ruin.model.map.MapListener;
import io.ruin.model.map.Position;
import io.ruin.model.map.Tile;
import io.ruin.model.map.dynamic.DynamicMap;
import io.ruin.model.map.ground.GroundItem;
import io.ruin.model.map.object.GameObject;
import io.ruin.model.map.object.actions.ObjectAction;
import io.ruin.model.skills.prayer.Prayer;
import io.ruin.model.stat.StatType;
import io.ruin.model.var.VarPlayerRepository;
import io.ruin.utility.Broadcast;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * Brutus (cowboss 15626) and Demonic Brutus (cowboss_hardmode 15628) in an instanced copy of the Lumbridge cow field.
 * <p>
 * Brutus is built from an RSProx capture of the fight (MRobertEvers/oldschool-clientc
 * docs/rsprox-analysis/quests/ides_of_milk.md, rev 241) and OpenRune's port of it:
 * <ul>
 * <li>The Release gate (60763/60760 at 3262/3263,3294): an instanced field; the player on the gate tile; Brutus
 * spawns at 3261,3287 (cow_boss_spawn 13790), faces the player, says "Moo" at T+5 and comes at T+6.</li>
 * <li>A 0-3 crush swing every 5 ticks (cow_boss_attack 13783); the rotation is melee, melee, special, melee, melee.</li>
 * <li>Specials alternate (the first is random): stomp ("*snorts*", 13785) x3, 8 ticks apart: at S+4
 * vfx_cowboss_stomp_impact02 (3589) on him and impact01 (3588) on a side's three tiles or an L of five round the
 * nearest corner; charge ("*growls*", 13778) along the 4-way line, at S+4 a 4-7 tile slide stopped by walls. Both hit
 * 20-24% of the player's Hitpoints level (max 19), ignoring prayer. The next swing comes 8 ticks after the special.</li>
 * <li>Out of reach for 21 ticks: "*huff*" (cow_boss_heavy_breath 13780) and he paths round.</li>
 * <li>Death: "m-moo...", cow_boss_death 13784, respawn after 36 ticks.</li>
 * </ul>
 * Demonic Brutus (wiki, 2026-11): feed him an abyssal potato (33118) at the gate. 750 HP, a prayer-piercing swing of up
 * to 43, and specials after 3 swings, alternating: four bulls (him and three ghosts, 15629) in turn round the player,
 * clockwise (counter-clockwise below 250 HP), each charging across the player or stomping from a diagonal for 45-56;
 * faster below 500 HP. At 0 HP he says "moo moo!" and heals to 487 for a second phase whose specials also drop four
 * orbs (magic, melee, ranged, random; 18-20, prayable). Drops: Brutus slippers, 1/400 Beef.
 */
@Slf4j
public class Brutus extends NPCCombat {

	public static final int BRUTUS = 15626, ROUTEFIND = 15627, DEMONIC = 15628, GHOST = 15629;
	/* locs */
	private static final int GATE_L = 60763, GATE_R = 60760, GATE_L_NOOP = 60761, GATE_R_NOOP = 60762;
	private static final int EXIT_L = 60765, EXIT_R = 60764, FENCE_L = 1558, FENCE_R = 1560, SCOREBOARD = 60766, SCOREBOARD_PLAIN = 60767,
		SCOREBOARD_UBER = 60768;
	/* anims (cow_boss_*) */
	private static final int CHARGE = 13778, GHOST_CHARGE = 13779, HEAVY_BREATH = 13780, ATTACK = 13783, STOMP = 13785,
		GHOST_STOMP = 13786, DESPAWN = 13789, SPAWN = 13790, CHARGE_FADE_OUT = 13795, GHOST_CHARGE_FADE_OUT = 13796,
		CHARGE_FADE_IN = 13797, GHOST_CHARGE_FADE_IN = 13798, STOMP_FADE_IN = 13799, GHOST_STOMP_FADE_IN = 13800;
	/* spotanims */
	private static final int STOMP_TILE_GFX = 3588, STOMP_SELF_GFX = 3589;
	/* phase-2 orbs, magic / melee / ranged (vfx_cowboss_hm_*): spawn over the head, the orb, launch, impact */
	private static final int[] ORB_SPAWN = { 3602, 3601, 3600 }, ORB_IDLE = { 3593, 3592, 3591 }, ORB_LAUNCH = { 3599, 3598, 3597 },
		ORB_IMPACT = { 3596, 3595, 3594 };
	private static final Prayer[] ORB_PRAYER = { Prayer.PROTECT_FROM_MAGIC, Prayer.PROTECT_FROM_MELEE, Prayer.PROTECT_FROM_MISSILES };
	/* items */
	/* the release (RSProx): the cowbell box, Boss Moosic, and the fight's synths (spawn / stomp / charge) */
	private static final int COWBELL_AMULET = 33104, BOSS_MOOSIC = 859, SYNTH_SPAWN = 11212, SYNTH_STOMP = 11213, SYNTH_CHARGE = 11210;
	private static final int ABYSSAL_POTATO = 33118, BULL_BONES = 33115, RAW_TBONE = 33106, MOOLETA = 33101, BUCKET = 33091,
		COW_SLIPPERS = 33093, BRUTUS_SLIPPERS = 33099;

	/* the field (static coords) */
	private static final Position SPAWN_POS = new Position(3261, 3287, 0), ENTRY = new Position(3262, 3294, 0),
		EXIT = new Position(3252, 3267, 0);
	private static final Bounds FIELD = new Bounds(3224, 3256, 3279, 3311, 0);
	private static final Bounds SCOREBOARD_AREA = new Bounds(3236, 3255, 3275, 3305, 0);
	private static final int SIZE = 3;

	private static final VarPlayerRepository SCOREBOARDS = VarPlayerRepository.varpbit(20090, false);

	/* world scoreboard since boot: {kills, deaths, best} per mode */
	private static final int[][] WORLD = { { 0, 0, -1 }, { 0, 0, -1 } };

	/* ------------------------------------------------------------------ the field instance */

	private static final class Field {
		DynamicMap map;
		Player player;
		NPC boss;
		boolean demonic;
		int startTick;
		long respawnAt = -1; // server tick Brutus returns, while he's dead
		final List<NPC> life = new ArrayList<>(); // the field's cows, farmers, chickens re-added in the instance
	}

	private static final Map<Integer, Field> FIELDS = new HashMap<>();

	public static void register() {
		// Bull bones (OSRS: ground op3 "Bury", no Take -- they can't be picked up): buried where they fall, 40 Prayer xp
		// (wiki Remains / F2P Prayer training). Nothing handled the op before, so Bury did nothing (user 2026-11-15).
		io.ruin.model.map.ground.GroundItemAction.register(BULL_BONES, 3, (p, g, distance) -> {
			if (g.isRemoved())
				return;
			g.remove();
			p.animate(827);
			p.getStats().addXp(io.ruin.model.stat.StatType.Prayer, 40, true);
			p.sendFilteredMessage("You bury the bones.");
		});
		for (int gate : new int[] { GATE_L, GATE_R })
			ObjectAction.register(gate, 1, (p, obj) -> release(p));
		for (int exit : new int[] { EXIT_L, EXIT_R }) {
			ObjectAction.register(exit, 1, (p, obj) -> p.dialogue(new OptionsDialogue("Are you sure you want to leave?",
				new Option("Yes.", () -> leaveField(p)), new Option("No.", p::closeDialogue))));
			ObjectAction.register(exit, 2, (p, obj) -> leaveField(p));
		}
		ObjectAction.register(SCOREBOARD, 1, (p, obj) -> scoreboard(p, false));
		ObjectAction.register(SCOREBOARD_PLAIN, 1, (p, obj) -> scoreboard(p, false));
		ObjectAction.register(SCOREBOARD_UBER, 1, (p, obj) -> scoreboard(p, false));
		ObjectAction.register(SCOREBOARD, 2, (p, obj) -> scoreboard(p, true));
		ObjectAction.register(SCOREBOARD_UBER, 2, (p, obj) -> scoreboard(p, true));
		// the Growth in the Scar (60770 multiloc -> 60772 "Pluck") gives the abyssal potato that summons Demonic Brutus
		for (int growth : new int[] { 60770, 60772 })
			ObjectAction.register(growth, 1, (p, obj) -> {
				if (!p.getInventory().hasRoomFor(ABYSSAL_POTATO)) {
					p.sendMessage("You need a free inventory space to pluck the growth.");
					return;
				}
				p.animate(827);
				p.getInventory().add(ABYSSAL_POTATO, 1);
				p.sendMessage("You pluck an abyssal potato from the growth.");
			});
		// the scoreboard multiloc shows plant3 at 0, the plain board at 1 and the Demonic one at 2
		MapListener.registerBounds(SCOREBOARD_AREA).onEnter(p -> SCOREBOARDS.set(p, 2));
	}

	private static void release(Player p) {
		if (FIELDS.containsKey(p.getUserId())) {
			p.sendMessage("You're already in the field with Brutus.");
			return;
		}
		if (p.getInventory().contains(ABYSSAL_POTATO, 1)) {
			p.dialogue(new OptionsDialogue("Feed Brutus the abyssal potato?",
				new Option("Yes.", () -> {
					if (!p.getInventory().contains(ABYSSAL_POTATO, 1))
						return;
					p.getInventory().remove(ABYSSAL_POTATO, 1);
					enter(p, true);
				}),
				new Option("No, release him as he is.", () -> enter(p, false)),
				new Option("Cancel.", p::closeDialogue)));
			return;
		}
		if (p.brutusDontAskAgain) {
			enter(p, false);
			return;
		}
		p.dialogue(new OptionsDialogue("Release Brutus?",
			new Option("Yes.", () -> enter(p, false)),
			new Option("Yes, and don't ask again.", () -> {
				p.brutusDontAskAgain = true;
				enter(p, false);
			}),
			new Option("No.", p::closeDialogue)));
	}

	private static void enter(Player p, boolean demonic) {
		p.closeDialogue();
		DynamicMap map;
		try {
			map = new DynamicMap().build(FIELD);
		} catch (DynamicMap.DynamicMapBuildException e) {
			log.error("Unable to build the Brutus field", e);
			p.sendMessage("The field is too muddy right now. Try again in a moment.");
			return;
		}
		Field f = new Field();
		f.map = map;
		f.player = p;
		f.demonic = demonic;
		FIELDS.put(p.getUserId(), f);
		// the release, tick for tick as recorded (RSProx, ides_of_milk.md): R the cowbell box with no continue + fade out;
		// R+2 the instanced field, the player on the gate tile facing south, the gates swapped, Brutus added, Boss Moosic
		// (859) and synth 11212; R+4 fade in and the same box with a continue
		String bell = demonic ? "You offer the potato... and hear something terrible approaching."
			: "You ring the bell... and hear something approaching.";
		p.startEvent(e -> {
			p.lock();
			p.dialogue(new ItemDialogue().one(COWBELL_AMULET, bell + "<br><br><col=7f7f7f>Please wait...</col>").hideContinue());
			p.getPacketSender().fadeOut();
			e.delay(2);
			p.getMovement().teleport(map.convertX(ENTRY.getX()), map.convertY(ENTRY.getY()), 0);
			map.assignListener(p).onExit((pl, logout) -> destroy(pl, logout));
			swapGates(map);
			addFieldLife(f);
			spawnBoss(f);
			p.face(Direction.SOUTH);
			p.getPacketSender().sendMusic(BOSS_MOOSIC);
			p.getPacketSender().sendSoundEffect(SYNTH_SPAWN, 1, 0);
			e.delay(2);
			p.getPacketSender().fadeIn();
			p.dialogue(new ItemDialogue().one(COWBELL_AMULET, bell));
			p.unlock();
		});
	}

	/**
	 * The field's own life: Jagex's copy re-adds the field's farmers, cows, chickens and ducks (the capture); ours copied
	 * the map only, so the pen stood empty. Copies of the live field's npcs at the same spots.
	 */
	private static void addFieldLife(Field f) {
		for (NPC n : io.ruin.model.World.npcsSlots()) {
			if (n == null || n.isRemoved() || n.spawnPosition == null || n.getHeight() != 0 || !FIELD.inBounds(n.getPosition()))
				continue;
			if (n.getId() == BRUTUS || n.getId() == DEMONIC || n.getId() == GHOST || n.getId() == ROUTEFIND || n.getId() == 15624)
				continue;
			NPC copy = new NPC(n.getId()).spawn(f.map.convertX(n.spawnPosition.getX()), f.map.convertY(n.spawnPosition.getY()), 0, 2);
			f.life.add(copy);
		}
	}

	private static void swapGates(DynamicMap map) {
		int[][] swaps = { { 3262, 3294, GATE_L, GATE_L_NOOP }, { 3263, 3294, GATE_R, GATE_R_NOOP }, { 3253, 3266, FENCE_L, EXIT_L },
			{ 3253, 3267, FENCE_R, EXIT_R } };
		for (int[] s : swaps) {
			GameObject o = Tile.getObject(s[2], map.convertX(s[0]), map.convertY(s[1]), 0);
			if (o != null)
				o.setId(s[3]);
		}
	}

	private static void spawnBoss(Field f) {
		if (f.map == null || f.player == null)
			return;
		NPC b = new NPC(f.demonic ? DEMONIC : BRUTUS).spawn(f.map.convertX(SPAWN_POS.getX()), f.map.convertY(SPAWN_POS.getY()), 0, Direction.NORTH, 0);
		b.ownerId = f.player.getUserId();
		f.boss = b;
		f.startTick = (int) io.ruin.Server.currentTick();
		Brutus c = (Brutus) b.getCombat();
		c.field = f;
		b.animate(SPAWN);
		b.face(f.player);
		c.attackDelay = 6;
		c.arriveTicks = 5;
	}

	/** Cowbell amulet "Ring": a dead Brutus comes back 13 ticks after the bell instead of waiting out his 36. */
	public static void ring(Player p) {
		Field f = FIELDS.get(p.getUserId());
		p.animate(4412);
		if (f == null || f.respawnAt < 0) {
			p.sendMessage("You ring the bell.");
			return;
		}
		f.respawnAt = io.ruin.Server.currentTick() + 13;
		p.sendMessage("You ring the bell... and hear something approaching.");
	}

	private static void leaveField(Player p) {
		Field f = FIELDS.get(p.getUserId());
		p.closeDialogue();
		if (f == null || f.map == null) {
			p.getMovement().teleport(EXIT);
			return;
		}
		p.getMovement().teleport(EXIT);
	}

	private static void destroy(Player p, boolean logout) {
		Field f = FIELDS.remove(p.getUserId());
		if (f == null)
			return;
		if (f.boss != null) {
			if (f.boss.getCombat() instanceof Brutus b)
				b.cleanup();
			f.boss.remove();
		}
		for (NPC n : f.life)
			if (!n.isRemoved())
				n.remove();
		f.life.clear();
		if (f.map != null) {
			f.map.destroy();
			f.map = null;
		}
		if (logout)
			p.getMovement().teleport(EXIT);
	}

	private static void scoreboard(Player p, boolean demonic) {
		int mode = demonic ? 1 : 0;
		p.openInterface(ToplevelComponent.MAINMODAL, 950);
		p.getPacketSender().sendString(950, 8, String.valueOf(demonic ? p.demonicBrutusKills.getKills() : p.brutusKills.getKills()));
		p.getPacketSender().sendString(950, 10, String.valueOf(demonic ? p.demonicBrutusDeaths : p.brutusDeaths));
		p.getPacketSender().sendString(950, 12, time(demonic ? p.demonicBrutusBestTicks : p.brutusBestTicks));
		p.getPacketSender().sendString(950, 14, String.valueOf(WORLD[mode][0]));
		p.getPacketSender().sendString(950, 16, String.valueOf(WORLD[mode][1]));
		p.getPacketSender().sendString(950, 18, time(WORLD[mode][2]));
	}

	private static String time(int ticks) {
		if (ticks <= 0)
			return "-";
		int cs = ticks * 60; // centiseconds
		return cs / 6000 + ":" + String.format("%02d.%02d", (cs / 100) % 60, cs % 100);
	}

	/* ------------------------------------------------------------------ combat */

	private Field field;
	private int attackDelay = 6, arriveTicks = -1, swings = 0, nextSpecial = -1, unreachedTicks = 0;
	private boolean special = false, phaseTwo = false;
	private int chargeSide = -1;
	private final List<NPC> ghosts = new ArrayList<>();
	private int damageTaken = 0;
	private boolean countedDeath = false;

	private boolean demonic() {
		return npc.getId() == DEMONIC;
	}

	@Override
	public void init() {
		npc.hitListener = new HitListener().preDamage(this::preDamage).postDamage(h -> damageTaken += Math.max(0, h.damage));
	}

	private void preDamage(Hit hit) {
		if (!demonic() || phaseTwo)
			return;
		// phase 1 ends at 0 HP: he heals to 487 instead of dying
		if (hit.damage >= npc.getHp()) {
			hit.damage = Math.max(0, npc.getHp() - 1);
			npc.addEvent(e -> {
				e.delay(1);
				if (phaseTwo || isDead())
					return;
				phaseTwo = true;
				npc.forceText("moo moo!");
				npc.setHp(487);
				swings = 0;
			});
		}
	}

	@Override
	public void follow() {
		if (special || arriveTicks > 0)
			return;
		follow(1);
	}

	@Override
	public boolean attack() {
		return false; // timer-driven from process()
	}

	@Override
	public void process() {
		if (field == null || field.player == null || isDead())
			return;
		Player p = field.player;
		if (p.getCombat().isDead()) {
			if (!countedDeath) {
				countedDeath = true;
				onPlayerDeath(p);
			}
			return;
		}
		countedDeath = false;
		if (arriveTicks > 0) {
			if (--arriveTicks == 0) {
				npc.forceText("Moo");
				setTarget(p);
				npc.targetPlayer(p, false);
			}
			attackDelay--;
			return;
		}
		if (special)
			return;
		if (!inReach(npc.getPosition(), p.getPosition())) {
			if (++unreachedTicks >= 21) {
				unreachedTicks = 0;
				npc.forceText("*huff*");
				npc.animate(HEAVY_BREATH);
				npc.transform(ROUTEFIND);
				npc.addEvent(e -> {
					e.delay(3);
					if (!isDead())
						npc.transform(demonic() ? DEMONIC : BRUTUS);
				});
				npc.getRouteFinder().routeEntity(p);
			}
		} else {
			unreachedTicks = 0;
		}
		if (--attackDelay > 0)
			return;
		if (nextSpecial < 0)
			nextSpecial = Random.get(1);
		int specialAfter = demonic() ? 3 : 2;
		if (swings >= specialAfter) {
			swings = demonic() ? 0 : -2; // normal Brutus: melee, melee, SPECIAL, melee, melee
			if (demonic())
				demonicSpecial(p);
			else
				normalSpecial(p);
			return;
		}
		if (inReach(npc.getPosition(), p.getPosition())) {
			swing(p);
			swings++;
			attackDelay = 5;
		} else {
			attackDelay = 1;
		}
	}

	private void swing(Player p) {
		npc.face(p);
		npc.animate(ATTACK);
		Hit hit = new Hit(npc, AttackStyle.CRUSH).randDamage(demonic() ? 43 : 3);
		if (demonic())
			hit.ignorePrayer(); // Demonic Brutus's swing hits through Protect from Melee
		p.hit(hit);
	}

	private static boolean inReach(Position sw, Position player) {
		int dx = player.getX() - sw.getX(), dy = player.getY() - sw.getY();
		boolean besideX = dx == -1 || dx == SIZE, besideY = dy == -1 || dy == SIZE;
		boolean alongX = dx >= 0 && dx < SIZE, alongY = dy >= 0 && dy < SIZE;
		return (besideX && alongY) || (besideY && alongX);
	}

	private int specialDamage(Player p) {
		int lvl = p.getStats().get(StatType.Hitpoints).fixedLevel;
		return Math.max(1, Math.min(19, Random.get(lvl * 20 / 100, lvl * 24 / 100)));
	}

	/* ------------------------------------------------------------------ normal Brutus specials */

	private void normalSpecial(Player p) {
		boolean charge = nextSpecial == 0 && chargeRoom(npc.getPosition(), chargeDir(npc.getPosition(), p.getPosition())) >= 3;
		nextSpecial = charge ? 1 : 0;
		special = true;
		npc.getMovement().reset();
		if (charge)
			charge(p);
		else
			stompSeries(p);
	}

	private void charge(Player p) {
		int[] d = chargeDir(npc.getPosition(), p.getPosition());
		npc.forceText("*growls*");
		if (field != null && field.player != null)
			field.player.getPacketSender().sendSoundEffect(SYNTH_CHARGE, 1, 0);
		npc.animate(CHARGE);
		npc.face(npc.getAbsX() + 1 + d[0] * 5, npc.getAbsY() + 1 + d[1] * 5);
		npc.addEvent(e -> {
			e.delay(4);
			if (isDead())
				return;
			int room = chargeRoom(npc.getPosition(), d);
			int dist = Math.min(Random.get(4, 7), room);
			if (dist > 0) {
				Position start = npc.getPosition().copy();
				Position dest = start.copy().translate(d[0] * dist, d[1] * dist, 0);
				if (inLane(start, dest, p.getPosition()))
					p.hit(new Hit(npc).fixedDamage(specialDamage(p)).ignorePrayer().ignoreDefence());
				Raid4Fx.slide(npc, dest, 60, angle(d));
			}
			npc.faceNone();
			e.delay(4);
			special = false;
			attackDelay = 1;
			npc.face(p);
		});
	}

	private void stompSeries(Player p) {
		npc.addEvent(e -> {
			for (int i = 0; i < 3; i++) {
				if (isDead())
					return;
				int[] aim = stompAim(npc.getPosition(), p.getPosition());
				npc.animate(STOMP);
				npc.forceText("*snorts*");
				if (field != null && field.player != null)
					field.player.getPacketSender().sendSoundEffect(SYNTH_STOMP, 1, 0);
				npc.face(npc.getAbsX() + 1 + aim[0] * 5, npc.getAbsY() + 1 + aim[1] * 5);
				e.delay(4);
				if (isDead())
					return;
				npc.graphics(STOMP_SELF_GFX);
				List<Position> tiles = stompTiles(npc.getPosition(), aim[0], aim[1]);
				for (Position t : tiles)
					p.getPacketSender().sendGraphics(STOMP_TILE_GFX, 0, 0, t.getX(), t.getY(), t.getZ());
				if (tiles.contains(p.getPosition()) || beneathTiles(npc.getPosition(), aim[0], aim[1]).contains(p.getPosition()))
					p.hit(new Hit(npc).fixedDamage(specialDamage(p)).ignorePrayer().ignoreDefence());
				e.delay(i == 2 ? 2 : 4);
			}
			npc.face(p);
			e.delay(2);
			special = false;
			attackDelay = 1;
		});
	}

	private static int angle(int[] d) {
		return d[0] < 0 ? 512 : d[0] > 0 ? 1536 : d[1] > 0 ? 1024 : 0;
	}

	private static int[] chargeDir(Position sw, Position player) {
		int dx = player.getX() - (sw.getX() + 1), dy = player.getY() - (sw.getY() + 1);
		return Math.abs(dx) >= Math.abs(dy) ? new int[] { Integer.signum(dx), 0 } : new int[] { 0, Integer.signum(dy) };
	}

	/** Tiles he can slide (max 7): walls and blocked tiles stop him. */
	private int chargeRoom(Position sw, int[] d) {
		if (d[0] == 0 && d[1] == 0)
			return 0;
		int room = 0;
		int x = sw.getX(), y = sw.getY(), z = sw.getZ();
		while (room < 7) {
			x += d[0];
			y += d[1];
			boolean blocked = false;
			for (int i = 0; i < SIZE && !blocked; i++) {
				int tx = d[0] != 0 ? (d[0] > 0 ? x + SIZE - 1 : x) : x + i;
				int ty = d[1] != 0 ? (d[1] > 0 ? y + SIZE - 1 : y) : y + i;
				Tile t = Tile.get(tx, ty, z, false);
				if (t != null && t.clipping != 0)
					blocked = true;
			}
			if (blocked)
				break;
			room++;
		}
		return room;
	}

	private static boolean inLane(Position start, Position dest, Position tile) {
		int minX = Math.min(start.getX(), dest.getX()), minY = Math.min(start.getY(), dest.getY());
		int maxX = Math.max(start.getX(), dest.getX()) + SIZE - 1, maxY = Math.max(start.getY(), dest.getY()) + SIZE - 1;
		boolean underStart = tile.getX() - start.getX() >= 0 && tile.getX() - start.getX() < SIZE && tile.getY() - start.getY() >= 0
			&& tile.getY() - start.getY() < SIZE;
		return tile.getZ() == start.getZ() && tile.getX() >= minX && tile.getX() <= maxX && tile.getY() >= minY && tile.getY() <= maxY && !underStart;
	}

	/** A side (one of the pair 0) when level with its middle, otherwise the nearest corner. */
	private static int[] stompAim(Position sw, Position player) {
		int max = SIZE - 1, relX = player.getX() - sw.getX(), relY = player.getY() - sw.getY();
		int sideX = relX < 0 ? -1 : relX > max ? 1 : 0, sideY = relY < 0 ? -1 : relY > max ? 1 : 0;
		int alongX = Math.min(Math.max(relX, 0), max), alongY = Math.min(Math.max(relY, 0), max);
		int cx = sideX != 0 ? sideX : sideY != 0 && alongX == 0 ? -1 : sideY != 0 && alongX == max ? 1 : 0;
		int cy = sideY != 0 ? sideY : sideX != 0 && alongY == 0 ? -1 : sideX != 0 && alongY == max ? 1 : 0;
		return new int[] { cx, cy };
	}

	/** A side's three tiles, or an L of five round the corner (capture, rev 241). */
	private static List<Position> stompTiles(Position sw, int cx, int cy) {
		int max = SIZE - 1, z = sw.getZ();
		List<Position> out = new ArrayList<>();
		if (cx == 0 || cy == 0) {
			for (int i = 0; i <= max; i++) {
				if (cy != 0)
					out.add(new Position(sw.getX() + i, sw.getY() + (cy < 0 ? -1 : SIZE), z));
				else
					out.add(new Position(sw.getX() + (cx < 0 ? -1 : SIZE), sw.getY() + i, z));
			}
			return out;
		}
		int edgeX = cx < 0 ? -1 : SIZE, edgeY = cy < 0 ? -1 : SIZE, inX = cx < 0 ? 0 : max, inY = cy < 0 ? 0 : max;
		out.add(new Position(sw.getX() + edgeX, sw.getY() + edgeY, z));
		out.add(new Position(sw.getX() + inX, sw.getY() + edgeY, z));
		out.add(new Position(sw.getX() + inX - cx, sw.getY() + edgeY, z));
		out.add(new Position(sw.getX() + edgeX, sw.getY() + inY, z));
		out.add(new Position(sw.getX() + edgeX, sw.getY() + inY - cy, z));
		return out;
	}

	/** The tiles under him a slam also hits: its side of his centre, never the centre itself. */
	private static List<Position> beneathTiles(Position sw, int cx, int cy) {
		int max = SIZE - 1, mid = max / 2, z = sw.getZ();
		int inX = cx < 0 ? 0 : max, inY = cy < 0 ? 0 : max;
		List<Position> out = new ArrayList<>();
		if (cx == 0) {
			for (int i = 0; i <= max; i++)
				out.add(new Position(sw.getX() + i, sw.getY() + inY, z));
			out.add(new Position(sw.getX(), sw.getY() + mid, z));
			out.add(new Position(sw.getX() + max, sw.getY() + mid, z));
		} else if (cy == 0) {
			for (int i = 0; i <= max; i++)
				out.add(new Position(sw.getX() + inX, sw.getY() + i, z));
			out.add(new Position(sw.getX() + mid, sw.getY(), z));
			out.add(new Position(sw.getX() + mid, sw.getY() + max, z));
		} else {
			out.add(new Position(sw.getX() + inX, sw.getY() + inY, z));
			out.add(new Position(sw.getX() + inX - cx, sw.getY() + inY, z));
			out.add(new Position(sw.getX() + inX, sw.getY() + inY - cy, z));
		}
		return out;
	}

	/* ------------------------------------------------------------------ Demonic Brutus specials */

	/* clockwise from the start side: N, E, S, W (charges) and NE, SE, SW, NW (stomps) */
	private static final int[][] ORTHO = { { 0, 1 }, { 1, 0 }, { 0, -1 }, { -1, 0 } };
	private static final int[][] DIAG = { { 1, 1 }, { 1, -1 }, { -1, -1 }, { -1, 1 } };

	private void demonicSpecial(Player p) {
		boolean charge = nextSpecial == 0;
		nextSpecial = charge ? 1 : 0;
		special = true;
		npc.getMovement().reset();
		boolean clockwise = npc.getHp() > 250;
		boolean fast = phaseTwo || npc.getHp() <= 500;
		int windup = fast ? 2 : 3, gap = fast ? 2 : 3;
		// wiki: the fight opens west or south of the player; charges then swap to the opposite side after each one
		if (chargeSide < 0)
			chargeSide = Random.get(1) == 0 ? 3 : 2; // ORTHO index: W or S
		int start = charge ? chargeSide : Math.floorMod(chargeSide, 4);
		if (charge)
			chargeSide = (chargeSide + 2) % 4;
		// phase 2: four orbs over the player's head, always magic, melee, ranged, then a random one
		int[] orbOrder = { 0, 1, 2, Random.get(2) };
		if (phaseTwo)
			for (int i = 0; i < 4; i++) {
				int slot = i, o = orbOrder[i];
				io.ruin.model.activities.raids.tfa.Raid4Fx.spot(p, slot, ORB_SPAWN[o], 0, 250 + i * 20);
				npc.addEvent(ev -> {
					ev.delay(1);
					io.ruin.model.activities.raids.tfa.Raid4Fx.spot(p, slot, ORB_IDLE[o], 0, 250 + slot * 20);
				});
			}
		npc.addEvent(e -> {
			for (int i = 0; i < 4; i++) {
				if (isDead())
					break;
				int idx = Math.floorMod(start + (clockwise ? i : -i), 4);
				boolean real = i == 0;
				int orb = orbOrder[i];
				if (charge)
					ghostCharge(p, ORTHO[idx], real, windup, orb);
				else
					ghostStomp(p, DIAG[idx], real, windup, orb);
				e.delay(gap);
			}
			e.delay(windup + 2);
			for (NPC g : ghosts)
				g.remove();
			ghosts.clear();
			special = false;
			attackDelay = 2;
			npc.face(p);
		});
	}

	private NPC bull(boolean real, Position sw) {
		if (real) {
			npc.getMovement().teleport(sw);
			return npc;
		}
		NPC g = new NPC(GHOST).spawn(sw.getX(), sw.getY(), sw.getZ(), Direction.NORTH, 0);
		ghosts.add(g);
		return g;
	}

	/** A bull appears on the far side of the player (orthogonal) and charges straight through them. */
	private void ghostCharge(Player p, int[] side, boolean real, int windup, int orb) {
		Position pp = p.getPosition().copy();
		int[] dir = { -side[0], -side[1] };
		Position sw = new Position(pp.getX() + side[0] * 5 - 1, pp.getY() + side[1] * 5 - 1, pp.getZ());
		NPC b = bull(real, sw);
		b.animate(real ? CHARGE_FADE_IN : GHOST_CHARGE_FADE_IN);
		b.face(pp);
		b.addEvent(e -> {
			e.delay(1);
			b.animate(real ? CHARGE : GHOST_CHARGE);
			e.delay(windup);
			if (isDead())
				return;
			Position start = b.getPosition().copy();
			Position dest = start.copy().translate(dir[0] * 10, dir[1] * 10, 0);
			if (inLane(start, dest, p.getPosition()))
				p.hit(new Hit(npc).randDamage(45, 56).ignorePrayer().ignoreDefence());
			if (phaseTwo)
				orbHit(p, orb);
			Raid4Fx.slide(b, dest, 60, angle(dir));
			e.delay(2);
			b.animate(real ? CHARGE_FADE_OUT : GHOST_CHARGE_FADE_OUT);
		});
	}

	/** A bull appears diagonally across from the player and stomps the corner L towards them. */
	private void ghostStomp(Player p, int[] corner, boolean real, int windup, int orb) {
		Position pp = p.getPosition().copy();
		// the bull's nearest corner tile sits diagonally next to the player
		int swX = corner[0] > 0 ? pp.getX() + 1 : pp.getX() - SIZE, swY = corner[1] > 0 ? pp.getY() + 1 : pp.getY() - SIZE;
		Position sw = new Position(swX, swY, pp.getZ());
		NPC b = bull(real, sw);
		b.animate(real ? STOMP_FADE_IN : GHOST_STOMP_FADE_IN);
		b.face(pp);
		b.addEvent(e -> {
			e.delay(1);
			b.animate(real ? STOMP : GHOST_STOMP);
			e.delay(windup);
			if (isDead())
				return;
			int[] aim = stompAim(sw, pp);
			List<Position> tiles = stompTiles(sw, aim[0], aim[1]);
			b.graphics(STOMP_SELF_GFX);
			for (Position t : tiles)
				p.getPacketSender().sendGraphics(STOMP_TILE_GFX, 0, 0, t.getX(), t.getY(), t.getZ());
			if (tiles.contains(p.getPosition()))
				p.hit(new Hit(npc).randDamage(45, 56).ignorePrayer().ignoreDefence());
			if (phaseTwo)
				orbHit(p, orb);
			if (!real) {
				e.delay(2);
				b.animate(DESPAWN);
			}
		});
	}

	private void orbHit(Player p, int style) {
		boolean prayed = p.getPrayer().isActive(ORB_PRAYER[style]);
		p.graphics(ORB_LAUNCH[style], 250, 0);
		io.ruin.model.activities.raids.tfa.Raid4Fx.spot(p, 3, ORB_IMPACT[style], 30, 0);
		p.hit(new Hit(npc).randDamage(prayed ? 0 : 18, prayed ? 0 : 20).ignorePrayer().ignoreDefence());
	}

	private void cleanup() {
		for (NPC g : ghosts)
			g.remove();
		ghosts.clear();
	}

	/* ------------------------------------------------------------------ death + loot */

	@Override
	public void startDeath(Hit killHit) {
		if (demonic() && !phaseTwo) {
			npc.setHp(1);
			return;
		}
		npc.forceText(demonic() ? "m-moo..." : "m-moo...");
		cleanup();
		Field f = field;
		Player p = f == null ? null : f.player;
		boolean dem = demonic();
		if (p != null) {
			int took = (int) io.ruin.Server.currentTick() - f.startTick;
			int mode = dem ? 1 : 0;
			WORLD[mode][0]++;
			if (WORLD[mode][2] < 0 || took < WORLD[mode][2])
				WORLD[mode][2] = took;
			if (dem) {
				p.demonicBrutusKills.increment(p);
				if (p.demonicBrutusBestTicks <= 0 || took < p.demonicBrutusBestTicks)
					p.demonicBrutusBestTicks = took;
			} else {
				p.brutusKills.increment(p);
				if (p.brutusBestTicks <= 0 || took < p.brutusBestTicks)
					p.brutusBestTicks = took;
			}
			p.sendMessage("Fight duration: " + Color.RED.wrap(time(took)) + ".");
			reward(p, npc.getPosition().copy(), dem);
		}
		super.startDeath(killHit);
		if (f != null && !dem) {
			// he comes back 36 ticks later while the player stays in the field
			f.respawnAt = io.ruin.Server.currentTick() + 36;
			io.ruin.model.World.startEvent(e -> {
				while (io.ruin.Server.currentTick() < f.respawnAt)
					e.delay(1);
				f.respawnAt = -1;
				if (f.map != null && f.player != null && FIELDS.get(f.player.getUserId()) == f)
					spawnBoss(f);
			});
		}
	}

	private static final int[][] MAIN = { // id, min, max, weight /81, noted
		{ 1153, 1, 1, 2, 0 }, { 1115, 1, 1, 2, 0 }, { 1067, 1, 1, 1, 0 }, { 1081, 1, 1, 1, 0 },
		{ 884, 14, 14, 10, 0 }, { 556, 29, 29, 10, 0 }, { 558, 18, 18, 8, 0 }, { 562, 12, 12, 2, 0 },
		{ 5318, 3, 3, 10, 0 }, { 5312, 2, 2, 5, 0 },
		{ 33106, 3, 3, 10, 1 }, { 1739, 1, 1, 10, 0 }, { 1521, 2, 2, 5, 0 }, { 1511, 2, 2, 5, 0 },
		{ 995, 60, 80, 15, 0 }, { 995, 80, 100, 5, 0 }, { 995, 100, 120, 5, 0 } };

	private static void reward(Player p, Position near, boolean demonic) {
		List<Item> loot = new ArrayList<>();
		if (demonic) {
			loot.add(new Item(BRUTUS_SLIPPERS, 1));
			if (Random.get(1, 400) == 1)
				Pet.BEEF.unlock(p, 0);
		} else {
			loot.add(new Item(BULL_BONES, 1));
			loot.add(new Item(RAW_TBONE, 1));
			int pre = Random.get(1, 150);
			if (pre <= 5)
				loot.add(new Item(MOOLETA, 1));
			else if (pre <= 9)
				loot.add(new Item(BUCKET, 1));
			else if (pre == 10)
				loot.add(new Item(COW_SLIPPERS, 1));
			else {
				int roll = Random.get(0, 80);
				for (int[] e : MAIN) {
					roll -= e[3];
					if (roll < 0) {
						int id = e[0];
						if (e[4] == 1 && io.ruin.cache.ObjType.get(id) != null && io.ruin.cache.ObjType.get(id).notedId != -1)
							id = io.ruin.cache.ObjType.get(id).notedId;
						loot.add(new Item(id, Random.get(e[1], e[2])));
						break;
					}
				}
			}
			if (Random.get(1, 15) == 1)
				loot.add(new Item(23182, 1)); // clue scroll (beginner)
			if (Random.get(1, 40) == 1)
				loot.add(new Item(2677, 1)); // clue scroll (easy)
			if (Random.get(1, 1000) == 1)
				Pet.BEEF.unlock(p, 0);
		}
		// drops land on his tile closest to the player
		Position drop = new Position(Math.max(near.getX(), Math.min(p.getAbsX(), near.getX() + SIZE - 1)),
			Math.max(near.getY(), Math.min(p.getAbsY(), near.getY() + SIZE - 1)), near.getZ());
		for (Item item : loot) {
			new GroundItem(item).owner(p).position(drop).spawn();
			p.addToCollectionLog(item);
			int id = item.getId();
			if (id == MOOLETA || id == BUCKET || id == COW_SLIPPERS)
				Broadcast.WORLD.sendNewsDropMessage(p, Icon.RARE_DROP, "<col=000000>" + p.getName(),
					" just received " + item.getDef().descriptiveName + " from Brutus!");
		}
	}

	/** Deaths in the field count on the scoreboard. */
	private static void onPlayerDeath(Player p) {
		Field f = FIELDS.get(p.getUserId());
		if (f == null)
			return;
		if (f.demonic) {
			p.demonicBrutusDeaths++;
			WORLD[1][1]++;
		} else {
			p.brutusDeaths++;
			WORLD[0][1]++;
		}
	}
}
