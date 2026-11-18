package io.ruin.model.activities.bosses.royaltitans;

import io.ruin.api.utils.Random;
import io.ruin.cache.Color;
import io.ruin.cache.Icon;
import io.ruin.cache.ObjType;
import io.ruin.model.World;
import io.ruin.model.combat.AttackStyle;
import io.ruin.model.combat.Hit;
import io.ruin.model.combat.SetEffect;
import io.ruin.model.entity.npc.NPC;
import io.ruin.model.entity.npc.NPCAction;
import io.ruin.model.entity.npc.NPCCombat;
import io.ruin.model.entity.player.Player;
import io.ruin.model.entity.shared.listeners.HitListener;
import io.ruin.model.inter.ToplevelComponent;
import io.ruin.model.inter.handlers.EquipmentStats;
import io.ruin.model.item.Item;
import io.ruin.model.item.actions.impl.pet.Pet;
import io.ruin.model.map.Direction;
import io.ruin.model.map.Position;
import io.ruin.model.map.Projectile;
import io.ruin.model.map.dynamic.DynamicMap;
import io.ruin.model.map.ground.GroundItem;
import io.ruin.model.map.object.GameObject;
import io.ruin.model.map.object.actions.ObjectAction;
import io.ruin.model.skills.magic.spells.ancient.IceBarrage;
import io.ruin.model.skills.magic.spells.ancient.IceBlitz;
import io.ruin.model.skills.magic.spells.ancient.IceBurst;
import io.ruin.model.skills.magic.spells.ancient.IceRush;
import io.ruin.model.skills.magic.spells.modern.FireBlast;
import io.ruin.model.skills.magic.spells.modern.FireSpell;
import io.ruin.model.skills.magic.spells.modern.FireSurge;
import io.ruin.model.skills.magic.spells.modern.FireWave;
import io.ruin.model.skills.magic.spells.modern.WaterBlast;
import io.ruin.model.skills.magic.spells.modern.WaterSpell;
import io.ruin.model.skills.magic.spells.modern.WaterSurge;
import io.ruin.model.skills.magic.spells.modern.WaterWave;
import io.ruin.model.skills.magic.spells.TargetSpell;
import io.ruin.model.skills.prayer.Prayer;
import io.ruin.model.stat.StatType;
import io.ruin.utility.Broadcast;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * The Royal Titans: Branda the Fire Queen (rt_fire_queen 12596) and Eldric the Ice King (rt_ice_king 14147), in an
 * instanced copy of their cave (region 11669) off the Asgarnian Ice Dungeon.
 * <p>
 * Mechanics are the OSRS wiki's (Royal Titans, Royal Titans/Strategies, the titan / elemental / Fire / Icicles pages,
 * 2026-11-14); every animation, spotanim, projectile, npc and loc id is the rev 241 cache's, chosen by its gameval name
 * (see .dev/boss_audit_20261114/gameval_rt.txt). There is no public packet capture of this fight, so the exact tick
 * order inside an attack is inferred from those names; see the NOTE comments.
 * <ul>
 * <li>The titans only attack with a melee stomp (crush, up to 26, Protect from Melee blocks it). Their other actions are
 * walls and elementals in phase 1, and elementals plus Elemental Blast in phase 2.</li>
 * <li>2026-11-15 rework from a packet capture of the real fight (posture cycle, the stomp wave, summon01 = wall /
 * summon02 = elementals, the blast's tiles, timings, safe-tile npc with its headbar): see the constants' notes. The
 * paragraph below is the first version's reading of the wiki, kept for the rules that still hold.</li>
 * <li>Phase 1: each titan walks its half. In melee it lines up with you and stomps the three columns in front of it; it
 * then backs off for about 20 seconds, raising a row of fire / icicles (four ticks to form; standing on a tile takes up
 * to 12 and stops that tile forming) or, once it has taken 100 damage, summoning three elementals.</li>
 * <li>Phase 2 (either titan at 35% = 210 or lower): both teleport to the ends of the strip and stay there. The stomp
 * covers the whole arena; summons continue; no more walls; Elemental Blast hits the arena three times with one safe tile
 * (loc 56003, "This looks like a safe spot.") shown first, up to 30 each. Standing where a titan lands knocks you back
 * for 15-30.</li>
 * <li>One titan down: the other's stomp pierces prayer (up to 10) and its blast safe tile is the row furthest from it.</li>
 * <li>Players: ranged accuracy x6 against a titan that's out of melee range (and through a blast). Elemental weaknesses:
 * Branda 50% water, Eldric 50% fire.</li>
 * <li>Elementals (40 hp): a magic spell hits them with a chance of magic accuracy bonus + 30 % (at least 30%; void mage
 * x1.45); the opposing element does double and water / fire spells hit the 3x3 around the target; each one killed takes
 * a tick off its titan's next attack; an opposing wall or blast destroys them; left alive they explode for 8-10 in a 3x3.</li>
 * <li>Walls: Fire is doused by Water Blast or higher or any ice spell, Icicles melted by Fire Blast or higher; the tile and
 * the two beside it go, whatever the spell's accuracy.</li>
 * <li>Loot: Loot (two rolls), Sacrifice (no drops, pet x2) or Take pages (10-19 desiccated pages) on ONE titan; quantities
 * and unique rates scale with your share of the damage.</li>
 * </ul>
 */
@Slf4j
public class RoyalTitans extends NPCCombat {

	public static final int BRANDA = 12596, ELDRIC = 14147, BRANDA_INACTIVE = 14148, ELDRIC_INACTIVE = 14149;
	public static final int FIRE_ELEMENTAL = 14150, ICE_ELEMENTAL = 14151, FIRE_HITBOX = 14152, ICE_HITBOX = 14153;
	private static final int ENTRANCE = 55986, EXIT = 55987, SCOREBOARD = 56004, SHORTCUT_EAST = 55988, SHORTCUT_WEST = 55989;
	private static final int REGION = 11669;
	private static final Position OUTSIDE = new Position(2950, 9574, 0), ENTRY = new Position(2911, 9572, 0); // the open strip just south of the exit tunnel (55987 at 2910,9578); 2911,9576 (round 1, 11-14) is on the raised tunnel floor and stranded players
	/* the level 60 Agility tunnel (wiki map pins), no experience */
	private static final Position SHORTCUT_WEST_SIDE = new Position(2970, 9549, 0), SHORTCUT_EAST_SIDE = new Position(2987, 9547, 0);

	/* per titan: index 0 = Branda (fire, west), 1 = Eldric (ice, east). Gameval names in the comments. */
	private static final int[] SPAWN_ANIM = { 11966, 11963 };     // *_SPAWN01
	private static final int[] MELEE_ANIM = { 11974, 11973 };     // *_ATTACK_MELEE01
	/* 2026-11-15 (capture-backed, see the class note): SUMMON01 conjures the WALL (it lands ~4 ticks later) and SUMMON02
	 * summons the three ELEMENTALS (they appear exactly 2 ticks later). They were the other way round. */
	private static final int[] SUMMON_ANIM = { 11978, 11977 };    // *_ATTACK_SUMMON02 (elementals)
	private static final int[] WALL_ANIM = { 11976, 11975 };      // *_ATTACK_SUMMON01 (the row of fire / icicles)
	/* Branda fire_giant_branda_queen_attack_aoe01/02 (11981/11982); Eldric ice_giant_edric_king_attack_aoe01/02 (11979/11980 -
	 * the cache spells it "edric", which the first audit missed: he had been casting with his summon anims) */
	private static final int[] BLAST_CHARGE_ANIM = { 11981, 11979 }, BLAST_RELEASE_ANIM = { 11982, 11980 };
	private static final int[] TELEPORT_ANIM = { 11984, 11983 };  // *_TELEPORT01
	private static final int[] DEATH_ANIM = { 11992, 11987 };     // *_DEATH01
	private static final int[] INACTIVE_ANIM = { 11990, 11985 };  // *_INACTIVE01 (then the inactive npc's own readyanim, INACTIVE02)
	/* spotanims on the titan (Branda has the VFX set; the cache has none of these for Eldric) */
	private static final int[] SPAWN_GFX = { 3217, -1 }, SUMMON_GFX = { 3213, -1 }, WALL_GFX = { 3212, -1 }, TELEPORT_GFX = { 3216, -1 },
		DEATH_GFX = { 3224, -1 }, BLAST_CHARGE_GFX = { 3214, -1 }, BLAST_RELEASE_GFX = { 3215, -1 };
	/* the blast as it lands, one map spotanim per burning tile: *_ATTACK_AOE_PROJ01 (3209 fire / 3210 ice). 3215 is Branda's
	 * own aoe02 effect (on her, with the release), not a tile graphic: drawing it on every tile was wrong */
	private static final int[] BLAST_TILE_GFX = { 3209, 3210 };
	/* the stomp: a travelling wave of vfx_ice_giant_eldric_king_melee01_proj01 tile graphics, one tile every two ticks
	 * (the only wave graphic in the cache: both titans use it) */
	private static final int STOMP_WAVE_GFX = 3211, WAVE_TICKS_PER_TILE = 2;
	/* the blast's tell: an invisible npc on the one safe tile with a headbar that fills over the 7 ticks before the blast
	 * (headbar 78, fill 0 -> 30 over 210 x 20ms) */
	private static final int SAFE_TILE_NPC = 14154, BLAST_WARN_TICKS = 7, BLAST_RELEASE_DELAY = 6, SAFE_BAR = 78;
	/* walls: telegraph spot SPAWN01, forming spot SPAWN02, then the loc (with its own idle anim); despawn loc anim + spot */
	private static final int[] WALL_LOC = { 56001, 56002 }, WALL_TELEGRAPH_GFX = { 3218, 3221 }, WALL_FORM_GFX = { 3219, 3222 },
		WALL_DESPAWN_GFX = { 3220, 3223 }, WALL_DESPAWN_ANIM = { 12007, 12011 },
		/* the wall loc's own model (55961 fire / 55942 ice) forms with ITS anim, *_loc01_spawn02 (12004 / 12009); 12003 / 12008
		 * belong to the telegraph models (55960 / 55941) and twisted the loc ("snowballs", user 2026-11-14) */
		WALL_SPAWN_ANIM = { 12004, 12009 },
		HITBOX = { FIRE_HITBOX, ICE_HITBOX };
	/* elementals: spawn / death / explode spotanims and the explode anim */
	private static final int[] ELEMENTAL = { FIRE_ELEMENTAL, ICE_ELEMENTAL }, ELEMENTAL_SPAWN_GFX = { 3204, 3202 },
		ELEMENTAL_DEATH_GFX = { 3205, 3203 }, ELEMENTAL_EXPLODE_GFX = { 3207, 3206 }, ELEMENTAL_EXPLODE_ANIM = { 11961, 11959 };
	/* lava / ice floor locs around a titan as it rises or falls (each with its own loc anim) */
	private static final int[][] SPAWN_FLOOR = { { 55993, 55994 }, { 55997, 55998 } }, DEATH_FLOOR = { { 55995, 55996 }, { 55999, 56000 } };
	private static final int[][] SPAWN_FLOOR_ANIM = { { 11967, 11968 }, { 11964, 11965 } }, DEATH_FLOOR_ANIM = { { 11993, 11994 }, { 11988, 11989 } };
	private static final int SAFE_MARKER = 56003; // vfx_giant_attack_aoe_protection01

	/* static arena */
	private static final int STRIP_MIN_X = 2909, STRIP_MAX_X = 2913, STRIP_MIN_Y = 9561, STRIP_MAX_Y = 9573;
	private static final Position[] SPAWN = { new Position(2906, 9566, 0), new Position(2914, 9566, 0) };
	/* phase 2: Branda at the north end, Eldric at the south end, each covering three rows */
	private static final Position[] P2_POS = { new Position(2910, 9571, 0), new Position(2910, 9561, 0) };
	/*
	 * Phase 1 movement and timing (2026-11-15, from a packet capture of the real fight): each titan works a box on its own
	 * flank, three columns wide (Branda x2904-2906, Eldric x2914-2916), moving towards the player's row but clamped to it.
	 * It alternates two postures: FAR (its outer column, ~26 ticks) where it can't be meleed and conjures walls (every
	 * ~13 ticks) and summons elementals (at least 14 ticks apart, once it has taken 100 damage), and NEAR (its inner
	 * column, ~15 ticks) where it stomps every 7. It stands still for 5 ticks after rising; Eldric rises 2 ticks after
	 * Branda. The turn after a blast is 8 ticks. A wall stands 40 ticks.
	 */
	private static final int ATTACK_SPEED = 7, ATTACK_SPEED_AFTER_BLAST = 8, WALL_LIFE = 40, WALL_GAP = 13, SUMMON_GAP = 14,
		FAR_TICKS = 26, NEAR_TICKS = 15, SPAWN_GRACE = 5;
	private static final int[] COLUMN_FAR = { 2904, 2916 }, COLUMN_NEAR = { 2906, 2914 };
	private static final int BOX_MIN_Y = 9561, BOX_MAX_Y = 9571, BLAST_MIN_Y = 9564, BLAST_MAX_Y = 9570, HALF_SPLIT_Y = 9567;

	/* ------------------------------------------------------------------ instance */

	private static final class Arena {
		DynamicMap map;
		Player player; // the leader (whose instance it is)
		final List<Player> players = new ArrayList<>(); // 1-2 fighters
		final Set<Integer> everEntered = new HashSet<>(), looted = new HashSet<>(), deadCounted = new HashSet<>();
		final Map<Integer, Integer> damage = new HashMap<>(); // userId -> damage dealt to the titans
		final NPC[] titans = new NPC[2];
		final boolean[] down = new boolean[2];
		boolean phase2;
		int startTick, blastCooldown, blastingUntil;
		final List<NPC> elementals = new ArrayList<>();
		final List<Wall> walls = new ArrayList<>();
		final List<GameObject> floorLocs = new ArrayList<>();
	}

	private static final class Wall {
		final int side, x, y;
		GameObject loc;
		NPC hitbox;
		int ticksLeft = WALL_LIFE;

		Wall(int side, int x, int y) {
			this.side = side;
			this.x = x;
			this.y = y;
		}
	}

	private static final Map<Integer, Arena> ARENAS = new HashMap<>();
	private static int globalKills, globalDeaths, globalBest = -1;

	public static void register() {
		ObjectAction.register(ENTRANCE, 1, (p, obj) -> enter(p));
		ObjectAction.register(ENTRANCE, 2, (p, obj) -> join(p));
		ObjectAction.register(ENTRANCE, 3, (p, obj) -> enter(p)); // Quick-start
		ObjectAction.register(EXIT, 1, (p, obj) -> p.sendMessage("The tunnel leads back to the Asgarnian Ice Dungeon."));
		ObjectAction.register(EXIT, 2, (p, obj) -> p.getMovement().teleport(OUTSIDE)); // Quick-escape
		ObjectAction.register(SCOREBOARD, 1, (p, obj) -> scoreboard(p));
		ObjectAction.register(SHORTCUT_WEST, 1, (p, obj) -> shortcut(p, SHORTCUT_EAST_SIDE));
		ObjectAction.register(SHORTCUT_EAST, 1, (p, obj) -> shortcut(p, SHORTCUT_WEST_SIDE));
		for (int i = 0; i < 2; i++) {
			int side = i;
			int npcId = side == 0 ? BRANDA_INACTIVE : ELDRIC_INACTIVE;
			NPCAction.register(npcId, 1, (p, n) -> claim(p, side, 0));
			NPCAction.register(npcId, 3, (p, n) -> claim(p, side, 1));
			NPCAction.register(npcId, 4, (p, n) -> claim(p, side, 2));
			// Douse / Melt: cast your autocast spell at the wall tile
			NPCAction.register(HITBOX[i], 1, (p, n) -> p.getCombat().setTarget(n));
		}
	}

	private static void shortcut(Player p, Position to) {
		if (p.getStats().get(StatType.Agility).currentLevel < 60) {
			p.sendMessage("You need an Agility level of at least 60 to use this shortcut.");
			return;
		}
		p.startEvent(e -> {
			p.lock();
			p.getPacketSender().fadeOut();
			e.delay(2);
			p.getMovement().teleport(to);
			p.getPacketSender().fadeIn();
			p.unlock();
		});
	}

	private static void scoreboard(Player p) {
		p.openInterface(ToplevelComponent.MAINMODAL, 909);
		p.getPacketSender().sendString(909, 9, String.valueOf(p.royalTitansKills.getKills()));
		p.getPacketSender().sendString(909, 11, String.valueOf(p.royalTitansDeaths));
		p.getPacketSender().sendString(909, 13, time(p.royalTitansBestTicks));
		p.getPacketSender().sendString(909, 15, String.valueOf(globalKills));
		p.getPacketSender().sendString(909, 17, String.valueOf(globalDeaths));
		p.getPacketSender().sendString(909, 19, time(globalBest));
	}

	private static String time(int ticks) {
		if (ticks <= 0)
			return "-";
		int cs = ticks * 60;
		return cs / 6000 + ":" + String.format("%02d.%02d", (cs / 100) % 60, cs % 100);
	}

	private static void enter(Player p) {
		if (ARENAS.containsKey(p.getUserId()))
			return;
		DynamicMap map;
		try {
			map = new DynamicMap().build(REGION, 1);
		} catch (DynamicMap.DynamicMapBuildException e) {
			log.error("Unable to build the Royal Titans arena", e);
			p.sendMessage("The tunnel is blocked. Try again in a moment.");
			return;
		}
		Arena a = new Arena();
		a.map = map;
		a.player = p;
		// the exit tunnel stands on the raised floor north of the strip (y 9574 is a wall); use it from the strip instead
		// of opening a way up there (that stranded players: 2026-11-14)
		io.ruin.model.map.object.GameObject exit = io.ruin.model.map.Tile.getObject(EXIT, map.convertX(2910), map.convertY(9578), 0);
		if (exit != null) {
			int sx1 = map.convertX(2908), sx2 = map.convertX(2914), sy1 = map.convertY(9571), sy2 = map.convertY(9573);
			exit.skipReachCheck = pos -> pos.getX() >= sx1 && pos.getX() <= sx2 && pos.getY() >= sy1 && pos.getY() <= sy2;
		} else {
			log.warn("Royal Titans: exit tunnel not found in the instance");
		}
		addPlayer(a, p);
		a.startTick = (int) io.ruin.Server.currentTick();
		spawnTitan(a, 0);
		// Eldric rises two ticks after Branda, every time (the seed of their stagger)
		p.addEvent(e -> {
			e.delay(2);
			if (a.map != null && !a.players.isEmpty())
				spawnTitan(a, 1);
		});
	}

	private static void spawnTitan(Arena a, int i) {
		Position s = SPAWN[i];
		NPC t = new NPC(i == 0 ? BRANDA : ELDRIC).spawn(a.map.convertX(s.getX()), a.map.convertY(s.getY()), 0, i == 0 ? Direction.EAST : Direction.WEST, 0);
		t.ownerId = a.player.getUserId();
		a.titans[i] = t;
		RoyalTitans c = (RoyalTitans) t.getCombat();
		c.arena = a;
		c.side = i;
		// it stands still for exactly 5 ticks after rising; the first action (a wall) comes 8 ticks after the rise
		c.grace = SPAWN_GRACE;
		c.actionDelay = 8;
		c.far = false;
		c.postureTicks = NEAR_TICKS;
		c.rise(t.getPosition());
		t.hitListener = new HitListener().preDefend(c::preDefend).preDamage(c::preDamage).postDamage(h -> {
			if (h.attacker != null && h.attacker.player != null && h.damage > 0)
				a.damage.merge(h.attacker.player.getUserId(), h.damage, Integer::sum);
		});
	}

	private static void addPlayer(Arena a, Player p) {
		a.players.add(p);
		a.everEntered.add(p.getUserId());
		ARENAS.put(p.getUserId(), a);
		p.getMovement().teleport(a.map.convertX(ENTRY.getX()), a.map.convertY(ENTRY.getY()), 0);
		a.map.assignListener(p).onExit((pl, logout) -> destroy(pl, logout));
	}

	/**
	 * Join: name a player whose Titans fight you want to join. Only while neither titan has lost more than a quarter of
	 * its HP (450+ of 600), two players at most; nobody can rejoin after dying or leaving.
	 */
	private static void join(Player p) {
		if (ARENAS.containsKey(p.getUserId()))
			return;
		p.stringInput("Whose fight do you want to join?", name -> {
			Player leader = World.getPlayer(name);
			Arena a = leader == null ? null : ARENAS.get(leader.getUserId());
			if (a == null || a.map == null) {
				p.sendMessage("That player isn't fighting the Royal Titans.");
				return;
			}
			if (a.everEntered.contains(p.getUserId())) {
				p.sendMessage("You can't rejoin a fight you've already left.");
				return;
			}
			if (a.players.size() >= 2) {
				p.sendMessage("That fight is already full.");
				return;
			}
			for (int i = 0; i < 2; i++)
				if (a.down[i] || a.phase2 || a.titans[i] == null || a.titans[i].getHp() < 450) {
					p.sendMessage("That fight has progressed too far for you to join.");
					return;
				}
			addPlayer(a, p);
			for (Player other : a.players)
				if (other != p)
					other.sendMessage(p.getName() + " has joined your fight against the Royal Titans.");
		});
	}

	private static void destroy(Player p, boolean logout) {
		Arena a = ARENAS.remove(p.getUserId());
		if (a == null)
			return;
		a.players.remove(p);
		if (logout)
			p.getMovement().teleport(OUTSIDE);
		if (!a.players.isEmpty()) {
			if (a.player == p)
				a.player = a.players.get(0);
			return;
		}
		clearAdds(a);
		for (NPC t : a.titans)
			if (t != null)
				t.remove();
		for (GameObject o : a.floorLocs)
			if (!o.isRemoved())
				o.remove();
		if (a.map != null) {
			a.map.destroy();
			a.map = null;
		}
	}

	private static void clearAdds(Arena a) {
		for (NPC e : a.elementals)
			e.remove();
		a.elementals.clear();
		for (Wall w : a.walls) {
			if (w.loc != null && !w.loc.isRemoved())
				w.loc.remove();
			if (w.hitbox != null)
				w.hitbox.remove();
		}
		a.walls.clear();
	}

	/* ------------------------------------------------------------------ titan state */

	private Arena arena;
	private int side, actionDelay = ATTACK_SPEED, damageTaken = 0, grace, postureTicks;
	private boolean far; // the FAR posture: on its outer column, walls + summons, can't be meleed
	private long lastSummonTick = -1000, lastWallTick = -1000;

	/** Players alive and inside this arena. */
	private List<Player> fighters() {
		List<Player> list = new ArrayList<>();
		for (Player f : arena.players)
			if (!f.getCombat().isDead() && ARENAS.get(f.getUserId()) == arena)
				list.add(f);
		return list;
	}

	private int mx(int x) {
		return arena.map.convertX(x);
	}

	private int my(int y) {
		return arena.map.convertY(y);
	}

	private NPC other() {
		return arena.titans[1 - side];
	}

	private boolean alone() {
		return arena.down[1 - side];
	}

	/** player hits on a titan: ranged x6 out of melee range (and through a blast), elemental weaknesses */
	void preDefend(Hit hit) {
		if (arena == null || hit.attacker == null || hit.attacker.player == null)
			return;
		Player p = hit.attacker.player;
		if (hit.attackStyle != null && hit.attackStyle.isRanged()
			&& (!inMeleeRange(p) || io.ruin.Server.currentTick() <= arena.blastingUntil))
			hit.boostAttack(5.0);
		TargetSpell spell = hit.attackSpell;
		if (spell != null && ((side == 0 && spell instanceof WaterSpell) || (side == 1 && spell instanceof FireSpell)))
			hit.boostDamage(0.5);
	}

	void preDamage(Hit hit) {
		if (arena == null || arena.down[side]) {
			hit.block();
			return;
		}
		damageTaken += Math.max(0, hit.damage);
	}

	/** melee distance: on its inner column, the player on the strip's edge column beside it, within its three rows */
	private boolean inMeleeRange(Player p) {
		if (arena.phase2)
			return npc.getPosition().isWithinDistance(p.getPosition(), 1) || Math.abs(p.getAbsY() - (npc.getAbsY() + 1)) <= 2
				&& Math.abs(p.getAbsX() - (npc.getAbsX() + 1)) <= 2;
		int edgeX = mx(side == 0 ? STRIP_MIN_X : STRIP_MAX_X);
		return npc.getAbsX() == mx(COLUMN_NEAR[side]) && p.getAbsX() == edgeX
			&& p.getAbsY() >= npc.getAbsY() && p.getAbsY() <= npc.getAbsY() + 2;
	}

	@Override
	public void init() {
		// hit listeners are set by the arena when the titans spawn
	}

	@Override
	public void follow() {
		// movement is driven by process()
	}

	@Override
	public boolean attack() {
		return false;
	}

	/** The titan rises out of its lava / ice: spawn anim, its spotanim, the floor locs either side. */
	private void rise(Position at) {
		npc.animate(SPAWN_ANIM[side]);
		if (SPAWN_GFX[side] != -1)
			npc.graphics(SPAWN_GFX[side]);
		for (int i = 0; i < 2; i++) {
			GameObject floor = GameObject.spawn(SPAWN_FLOOR[side][i], i == 0 ? at.getX() - 1 : at.getX() + 3, at.getY(), 0, 10, 0);
			floor.animate(SPAWN_FLOOR_ANIM[side][i]);
			arena.floorLocs.add(floor);
		}
	}

	@Override
	public void process() {
		if (arena == null || arena.players.isEmpty() || arena.down[side] || isDead())
			return;
		if (side == 0 || arena.down[0])
			arenaTick();
		List<Player> alive = fighters();
		if (alive.isEmpty())
			return;
		// each titan works on the player nearest its own rows
		Player p = alive.get(0);
		for (Player f : alive)
			if (Math.abs(f.getAbsY() - npc.getAbsY() - 1) < Math.abs(p.getAbsY() - npc.getAbsY() - 1))
				p = f;
		if (!arena.phase2 && (npc.getHp() <= 210 || (other() != null && !alone() && other().getHp() <= 210))) {
			if (side == 0 || arena.down[0])
				startPhase2();
			return;
		}
		if (grace > 0) { // standing still after rising
			grace--;
			actionDelay--;
			return;
		}
		if (!arena.phase2) {
			if (--postureTicks <= 0) { // FAR (~26 ticks) <-> NEAR (~15 ticks)
				far = !far;
				postureTicks = far ? FAR_TICKS : NEAR_TICKS;
			}
			walk(p);
		}
		if (--actionDelay > 0)
			return;
		actionDelay = ATTACK_SPEED;
		if (arena.phase2)
			phase2Action(p);
		else
			phase1Action(p);
	}

	/* ------------------------------------------------------------------ phase 1 */

	/**
	 * NEAR: stomp every 7 ticks. FAR: conjure a wall (every ~13 ticks) or, once it has taken 100 damage and at least 14
	 * ticks after its last summon, three elementals.
	 */
	private void phase1Action(Player p) {
		long now = io.ruin.Server.currentTick();
		if (!far) {
			stomp(p);
			return;
		}
		if (damageTaken >= 100 && now - lastSummonTick >= SUMMON_GAP && countElementals(side) < 6
			&& (now - lastWallTick < WALL_GAP || Random.get(1) == 0)) {
			lastSummonTick = now;
			summon(p);
		} else if (now - lastWallTick >= WALL_GAP) {
			lastWallTick = now;
			wall(p);
		} else {
			actionDelay = 2; // nothing ready yet: look again shortly
		}
	}

	private int countElementals(int s) {
		int n = 0;
		for (NPC e : arena.elementals)
			if (e.getId() == ELEMENTAL[s])
				n++;
		return n;
	}

	/**
	 * One step a tick inside its box: x to its posture's column (outer while FAR, inner while NEAR), y towards the
	 * player's row (its middle row on the player), clamped to the box.
	 */
	private void walk(Player p) {
		int targetX = mx(far ? COLUMN_FAR[side] : COLUMN_NEAR[side]);
		int targetY = Math.max(my(BOX_MIN_Y), Math.min(my(BOX_MAX_Y), p.getAbsY() - 1));
		int x = npc.getAbsX(), y = npc.getAbsY();
		if (x != targetX || y != targetY)
			npc.stepAbs(x + Integer.signum(targetX - x), y + Integer.signum(targetY - y), io.ruin.model.entity.shared.StepType.WALK);
		npc.face(side == 0 ? Direction.EAST : Direction.WEST);
	}

	/**
	 * The stomp: a wave of ice / fire (3211) that sweeps the strip one tile every two ticks from the titan's side, along
	 * the player's row in phase 1 and along every floor row in phase 2. It hits whoever is on a swept row when the wave
	 * reaches their tile: up to 26 crush (Protect from Melee blocks it), or up to 10 through prayer from a lone survivor.
	 */
	private void stomp(Player target) {
		npc.animate(MELEE_ANIM[side]);
		int minX = mx(STRIP_MIN_X), maxX = mx(STRIP_MAX_X);
		List<Integer> rows = new ArrayList<>();
		if (arena.phase2) {
			for (int y = my(BLAST_MIN_Y); y <= my(BLAST_MAX_Y); y++)
				rows.add(y);
		} else
			rows.add(target.getAbsY());
		for (int y : rows)
			for (int i = 0; i <= maxX - minX; i++) {
				int x = side == 0 ? minX + i : maxX - i;
				World.sendGraphics(STOMP_WAVE_GFX, 0, i * WAVE_TICKS_PER_TILE * 30, x, y, 0);
			}
		npc.addEvent(e -> {
			for (int i = 0; i <= maxX - minX; i++) {
				e.delay(i == 0 ? 1 : WAVE_TICKS_PER_TILE);
				if (isDead() || arena == null)
					return;
				int x = side == 0 ? minX + i : maxX - i;
				for (Player f : fighters()) {
					if (f.getAbsX() != x || !rows.contains(f.getAbsY()))
						continue;
					Hit hit = new Hit(npc, AttackStyle.CRUSH);
					if (alone())
						hit.randDamage(10).ignorePrayer(); // the survivor's stomp pierces prayer
					else if (f.getPrayer().isActive(Prayer.PROTECT_FROM_MELEE))
						hit.fixedDamage(0);
					else
						hit.randDamage(26).ignorePrayer();
					f.hit(hit);
				}
			}
		});
	}

	/* ------------------------------------------------------------------ elementals */

	private void summon(Player p) {
		npc.animate(SUMMON_ANIM[side]);
		if (SUMMON_GFX[side] != -1)
			npc.graphics(SUMMON_GFX[side]);
		npc.addEvent(e -> {
			e.delay(2);
			if (isDead() || arena == null)
				return;
			for (int i = 0; i < 3; i++) {
				int x = mx(Random.get(STRIP_MIN_X, STRIP_MAX_X)), y = my(Random.get(STRIP_MIN_Y + 2, STRIP_MAX_Y - 2));
				World.sendGraphics(ELEMENTAL_SPAWN_GFX[side], 0, 0, x, y, 0);
				NPC el = new NPC(ELEMENTAL[side]).spawn(x, y, 0, 3);
				el.ownerId = p.getUserId();
				arena.elementals.add(el);
				el.hitListener = new HitListener().preDefend(h -> elementalPreDefend(el, h)).postDamage(h -> elementalPostDamage(el, h));
				el.targetPlayer(p, false);
				// NOTE: the wiki gives no fuse; left alive they explode for 8-10 in a 3x3 with no clear warning
				el.addEvent(ee -> {
					ee.delay(Random.get(25, 35));
					if (el.isRemoved() || el.getCombat().isDead() || arena == null)
						return;
					el.animate(ELEMENTAL_EXPLODE_ANIM[side]);
					World.sendGraphics(ELEMENTAL_EXPLODE_GFX[side], 0, 0, el.getAbsX(), el.getAbsY(), 0);
					ee.delay(1);
					for (Player f : fighters())
						if (Math.abs(f.getAbsX() - el.getAbsX()) <= 1 && Math.abs(f.getAbsY() - el.getAbsY()) <= 1)
							f.hit(new Hit(el).randDamage(8, 10).ignorePrayer().ignoreDefence());
					el.remove();
					arena.elementals.remove(el);
				});
			}
		});
	}

	private int elementSideOf(NPC el) {
		return el.getId() == FIRE_ELEMENTAL ? 0 : 1;
	}

	/** magic: hits with a chance of magic accuracy bonus + 30 % (min 30%, void mage x1.45); the opposing element does double */
	private void elementalPreDefend(NPC el, Hit hit) {
		if (hit.attacker == null || hit.attacker.player == null || hit.attackStyle == null || !hit.attackStyle.isMagic())
			return;
		Player p = hit.attacker.player;
		double chance = Math.max(30, p.getEquipment().bonuses[EquipmentStats.MAGIC_ATTACK] + 30) / 100.0;
		if (SetEffect.VOID_MAGE.hasPieces(p) || SetEffect.VOID_MAGE2.hasPieces(p) || SetEffect.ELITE_VOID_MAGE.hasPieces(p))
			chance *= 1.45;
		if (Random.get() < chance)
			hit.ignoreDefence();
		else
			hit.block();
		if (opposing(el, hit.attackSpell))
			hit.boostDamage(1.0);
	}

	private boolean opposing(NPC el, TargetSpell spell) {
		return spell != null && ((elementSideOf(el) == 0 && spell instanceof WaterSpell) || (elementSideOf(el) == 1 && spell instanceof FireSpell));
	}

	/** water / fire spells hit the 3x3 around the target; each elemental killed takes a tick off its titan's next attack */
	private void elementalPostDamage(NPC el, Hit hit) {
		if (arena == null)
			return;
		if (hit.damage > 0 && hit.attackSpell != null && (hit.attackSpell instanceof WaterSpell || hit.attackSpell instanceof FireSpell)
			&& hit.attacker != null && !hit.isBlocked()) {
			for (NPC o : new ArrayList<>(arena.elementals)) {
				if (o == el || o.isRemoved() || o.getCombat().isDead())
					continue;
				if (Math.abs(o.getAbsX() - el.getAbsX()) <= 1 && Math.abs(o.getAbsY() - el.getAbsY()) <= 1)
					o.hit(new Hit(hit.attacker).fixedDamage(opposing(o, hit.attackSpell) ? hit.damage * 2 : hit.damage).ignoreDefence());
			}
		}
		if (el.getHp() <= 0 || el.getCombat().isDead())
			elementalDied(el);
	}

	private void elementalDied(NPC el) {
		if (!arena.elementals.remove(el))
			return;
		int s = elementSideOf(el);
		World.sendGraphics(ELEMENTAL_DEATH_GFX[s], 0, 0, el.getAbsX(), el.getAbsY(), 0);
		NPC titan = arena.titans[s];
		if (titan != null && !arena.down[s] && titan.getCombat() instanceof RoyalTitans c && c.actionDelay > 1)
			c.actionDelay--;
	}

	/** an opposing wall or blast destroys an elemental outright */
	private void destroyElementalsOn(int element, java.util.function.Predicate<NPC> on) {
		for (NPC el : new ArrayList<>(arena.elementals)) {
			if (elementSideOf(el) == element || el.isRemoved() || !on.test(el))
				continue;
			World.sendGraphics(ELEMENTAL_DEATH_GFX[elementSideOf(el)], 0, 0, el.getAbsX(), el.getAbsY(), 0);
			el.remove();
			arena.elementals.remove(el);
		}
	}

	/* ------------------------------------------------------------------ walls */

	/** A row of fire / icicles across the strip near the player: telegraphed, formed four ticks later. */
	private void wall(Player p) {
		npc.animate(WALL_ANIM[side]);
		if (WALL_GFX[side] != -1)
			npc.graphics(WALL_GFX[side]);
		// a full-width row anywhere on the strip (the capture: 9570, then 9567, then 9562 ...), not just by the player
		int y = my(Random.get(STRIP_MIN_Y, STRIP_MAX_Y));
		List<Wall> row = new ArrayList<>();
		for (int x = mx(STRIP_MIN_X); x <= mx(STRIP_MAX_X); x++) {
			World.sendGraphics(WALL_TELEGRAPH_GFX[side], 0, 0, x, y, 0);
			Wall w = new Wall(side, x, y);
			w.hitbox = new NPC(HITBOX[side]).spawn(x, y, 0, 0);
			w.hitbox.hitListener = new HitListener().preDefend(h -> {
				h.block();
				if (removes(w, h.attackSpell))
					douse(w);
			});
			row.add(w);
			arena.walls.add(w);
		}
		npc.addEvent(e -> {
			e.delay(2);
			if (arena == null)
				return;
			// the wall rises: its loc with the forming anim (then its idle anim from the loc def). The *_loc01_spawn02
			// spotanim is the same model + anim, so it isn't sent as well (that drew every wall twice)
			for (Wall w : row) {
				if (!arena.walls.contains(w))
					continue;
				w.loc = GameObject.spawn(WALL_LOC[side], w.x, w.y, 0, 10, 0);
				w.loc.animate(WALL_SPAWN_ANIM[side]);
			}
			e.delay(2);
			if (arena == null)
				return;
			for (Wall w : row) {
				if (!arena.walls.contains(w))
					continue;
				boolean blocked = false;
				for (Player f : fighters())
					if (f.getAbsX() == w.x && f.getAbsY() == w.y) {
						f.hit(new Hit(npc).randDamage(12).ignorePrayer().ignoreDefence());
						blocked = true;
					}
				if (blocked) { // someone stood in it: hit, and that piece doesn't stay
					removeWall(w);
					arena.walls.remove(w);
					continue;
				}
				destroyElementalsOn(side, el -> el.getAbsX() == w.x && el.getAbsY() == w.y);
			}
		});
	}

	/** Fire: Water Blast or higher, or any ice spell. Icicles: Fire Blast or higher. Whatever the spell's accuracy. */
	private static boolean removes(Wall w, TargetSpell spell) {
		if (spell == null)
			return false;
		if (w.side == 0)
			return spell instanceof WaterBlast || spell instanceof WaterWave || spell instanceof WaterSurge
				|| spell instanceof IceRush || spell instanceof IceBurst || spell instanceof IceBlitz || spell instanceof IceBarrage;
		return spell instanceof FireBlast || spell instanceof FireWave || spell instanceof FireSurge;
	}

	/** Douse / melt one tile and the two beside it. */
	private void douse(Wall hitWall) {
		if (arena == null)
			return;
		for (Iterator<Wall> it = arena.walls.iterator(); it.hasNext();) {
			Wall w = it.next();
			if (w.y != hitWall.y || Math.abs(w.x - hitWall.x) > 1)
				continue;
			removeWall(w);
			it.remove();
		}
	}

	private void removeWall(Wall w) {
		// the despawn spotanim is the wall's own model + *_loc01_despawn01: swap the loc for it (animating the loc as well
		// drew the wall twice)
		if (w.loc != null && !w.loc.isRemoved())
			w.loc.remove();
		World.sendGraphics(WALL_DESPAWN_GFX[w.side], 0, 0, w.x, w.y, 0);
		if (w.hitbox != null)
			w.hitbox.remove();
	}

	/* run once a tick by Branda's handler (Eldric's once she's down): walls fade, deaths counted */
	private void arenaTick() {
		for (Player f : arena.players)
			if (f.getCombat().isDead() && arena.deadCounted.add(f.getUserId())) {
				f.royalTitansDeaths++;
				globalDeaths++;
			}
		for (Iterator<Wall> it = arena.walls.iterator(); it.hasNext();) {
			Wall w = it.next();
			if (--w.ticksLeft > 0)
				continue;
			removeWall(w);
			it.remove();
		}
		for (NPC el : new ArrayList<>(arena.elementals))
			if (el.isRemoved() || el.getCombat() == null || el.getCombat().isDead())
				elementalDied(el);
		if (arena.blastCooldown > 0)
			arena.blastCooldown--;
	}

	/* ------------------------------------------------------------------ phase 2 */

	private void startPhase2() {
		arena.phase2 = true;
		for (Wall w : new ArrayList<>(arena.walls)) {
			removeWall(w);
			arena.walls.remove(w);
		}
		for (int i = 0; i < 2; i++) {
			NPC t = arena.titans[i];
			if (t == null || arena.down[i])
				continue;
			RoyalTitans c = (RoyalTitans) t.getCombat();
			int s = i;
			t.animate(TELEPORT_ANIM[i]);
			if (TELEPORT_GFX[i] != -1)
				t.graphics(TELEPORT_GFX[i]);
			c.actionDelay = 8;
			c.far = false;
			c.grace = 0;
			t.addEvent(e -> {
				e.delay(2);
				Position dest = new Position(mx(P2_POS[s].getX()), my(P2_POS[s].getY()), 0);
				// anyone standing where a titan lands is knocked back
				for (Player f : fighters())
					if (f.getAbsX() >= dest.getX() && f.getAbsX() < dest.getX() + 3 && f.getAbsY() >= dest.getY() && f.getAbsY() < dest.getY() + 3) {
						f.hit(new Hit(t).randDamage(15, 30).ignorePrayer().ignoreDefence());
						int dy = s == 0 ? -3 : 3;
						f.getMovement().force(0, dy, 0, 0, 0, 30, s == 0 ? Direction.NORTH : Direction.SOUTH);
					}
				t.getMovement().teleport(dest);
				t.face(s == 0 ? Direction.SOUTH : Direction.NORTH);
				c.rise(dest);
			});
		}
	}

	private void phase2Action(Player p) {
		// the blast: Branda leads it while she's up, Eldric alone once she's down; both titans cast it together
		boolean leads = side == 0 || arena.down[0];
		if (leads && arena.blastCooldown <= 0 && Random.get(2) == 0) {
			elementalBlast();
			return;
		}
		if (!leads && io.ruin.Server.currentTick() < arena.blastingUntil) {
			actionDelay = 2; // the other titan is mid-blast with its partner
			return;
		}
		long now = io.ruin.Server.currentTick();
		if (damageTaken >= 100 && now - lastSummonTick >= SUMMON_GAP && countElementals(side) < 6 && Random.get(2) == 0) {
			lastSummonTick = now;
			summon(p);
		} else
			stomp(p);
	}

	/**
	 * Elemental Blast (from a capture of the real fight): both titans charge (aoe01; Branda's own aoe01 effect), and the one
	 * tile the blast will spare is marked at once by npc 14154 whose headbar fills over the 7 ticks to the blast (plus the
	 * protection loc). Six ticks after the charge they release (aoe02; Branda's aoe02 effect) and on the seventh the
	 * floor burns: Branda's fire on her half (y 9568-9570), Eldric's ice on his (9564-9567), one map spotanim per tile,
	 * every tile but the safe one. Up to 30 on anyone on a burning tile; elementals on the opposing element's tiles are
	 * destroyed. With one titan down the survivor burns the whole floor and the safe tile is in the row furthest from it.
	 * The turn after a blast is 8 ticks.
	 */
	private void elementalBlast() {
		arena.blastCooldown = 30;
		arena.blastingUntil = (int) io.ruin.Server.currentTick() + BLAST_WARN_TICKS + 3;
		for (int i = 0; i < 2; i++) {
			NPC t = arena.titans[i];
			if (t == null || arena.down[i])
				continue;
			t.animate(BLAST_CHARGE_ANIM[i]);
			if (BLAST_CHARGE_GFX[i] != -1)
				t.graphics(BLAST_CHARGE_GFX[i]);
			((RoyalTitans) t.getCombat()).actionDelay = BLAST_WARN_TICKS + ATTACK_SPEED_AFTER_BLAST;
		}
		int minY = my(BLAST_MIN_Y), maxY = my(BLAST_MAX_Y), split = my(HALF_SPLIT_Y);
		boolean alone = arena.down[0] || arena.down[1];
		int survivor = arena.down[0] ? 1 : 0;
		int safeX = mx(Random.get(STRIP_MIN_X, STRIP_MAX_X));
		int safeY = !alone ? Random.get(minY, maxY) : survivor == 0 ? minY : maxY;
		NPC marker = new NPC(SAFE_TILE_NPC).spawn(safeX, safeY, 0, 0);
		marker.lock();
		GameObject circle = GameObject.spawn(SAFE_MARKER, safeX, safeY, 0, 22, 0);
		npc.addEvent(e -> {
			e.delay(1);
			if (!marker.isRemoved())
				try {
					marker.avatarExtended().addHeadBar(-1, SAFE_BAR, SAFE_BAR, 0, 30, 0, (BLAST_WARN_TICKS - 1) * 30);
				} catch (Exception ignored) { // the marker left the update list: no bar, the protection loc still shows the tile
				}
			e.delay(BLAST_RELEASE_DELAY - 1);
			if (arena == null) {
				marker.remove();
				circle.remove();
				return;
			}
			for (int i = 0; i < 2; i++) {
				NPC t = arena.titans[i];
				if (t == null || arena.down[i])
					continue;
				t.animate(BLAST_RELEASE_ANIM[i]);
				if (BLAST_RELEASE_GFX[i] != -1)
					t.graphics(BLAST_RELEASE_GFX[i]);
			}
			e.delay(BLAST_WARN_TICKS - BLAST_RELEASE_DELAY);
			marker.remove();
			circle.remove();
			if (arena == null || (arena.down[0] && arena.down[1]))
				return;
			boolean[] burns = { !arena.down[0], !arena.down[1] };
			for (int x = mx(STRIP_MIN_X); x <= mx(STRIP_MAX_X); x++)
				for (int y = minY; y <= maxY; y++) {
					if (x == safeX && y == safeY)
						continue;
					int half = y > split ? 0 : 1; // Branda's fire to the north, Eldric's ice to the south
					if (!burns[half])
						half = 1 - half;  // a fallen titan's half is burnt by the survivor
					World.sendGraphics(BLAST_TILE_GFX[half], 0, 0, x, y, 0);
				}
			for (Player f : fighters())
				if (f.getAbsY() >= minY && f.getAbsY() <= maxY && !(f.getAbsX() == safeX && f.getAbsY() == safeY))
					f.hit(new Hit(npc).randDamage(1, 30).ignorePrayer().ignoreDefence());
			for (int el = 0; el < 2; el++) {
				if (arena.down[el])
					continue;
				int element = el;
				destroyElementalsOn(element, n -> !(n.getAbsX() == safeX && n.getAbsY() == safeY)
					&& n.getAbsY() >= minY && n.getAbsY() <= maxY && ((n.getAbsY() > split ? 0 : 1) == element || alone));
			}
		});
	}

	/* ------------------------------------------------------------------ death + loot */

	@Override
	public void startDeath(Hit killHit) {
		if (arena == null) {
			super.startDeath(killHit);
			return;
		}
		setDead(true);
		arena.down[side] = true;
		npc.animate(DEATH_ANIM[side]);
		if (DEATH_GFX[side] != -1)
			npc.graphics(DEATH_GFX[side]);
		Arena a = arena;
		int s = side;
		Position at = npc.getPosition().copy();
		npc.addEvent(e -> {
			e.delay(3);
			for (int i = 0; i < 2; i++) {
				GameObject floor = GameObject.spawn(DEATH_FLOOR[s][i], i == 0 ? at.getX() - 1 : at.getX() + 3, at.getY(), 0, 10, 0);
				floor.animate(DEATH_FLOOR_ANIM[s][i]);
				a.floorLocs.add(floor);
			}
			npc.transform(s == 0 ? BRANDA_INACTIVE : ELDRIC_INACTIVE);
			npc.animate(INACTIVE_ANIM[s]);
		});
		if (a.down[0] && a.down[1]) {
			clearAdds(a);
			int took = (int) io.ruin.Server.currentTick() - a.startTick;
			globalKills++;
			if (globalBest < 0 || took < globalBest)
				globalBest = took;
			for (Player p : new ArrayList<>(a.players)) {
				p.royalTitansKills.increment(p);
				if (p.royalTitansBestTicks <= 0 || took < p.royalTitansBestTicks) {
					p.royalTitansBestTicks = took;
					p.sendMessage("Fight duration: " + Color.RED.wrap(time(took)) + " (new personal best)");
				} else {
					p.sendMessage("Fight duration: " + Color.RED.wrap(time(took)) + ". Personal best: " + time(p.royalTitansBestTicks));
				}
			}
		}
	}

	private static final int[][] BRANDA_TABLE = { // id, min, max, weight /55, noted
		{ 1401, 4, 4, 2, 1 }, { 1393, 6, 6, 2, 1 }, { 1093, 2, 2, 2, 0 }, { 1333, 2, 2, 2, 0 }, { 1275, 2, 2, 2, 0 },
		{ 554, 1500, 3000, 2, 0 }, { 562, 200, 400, 2, 0 }, { 560, 150, 300, 2, 0 }, { 561, 100, 150, 2, 0 }, { 563, 100, 150, 2, 0 },
		{ 566, 100, 150, 2, 0 }, { 565, 100, 150, 2, 0 }, { 892, 100, 300, 2, 0 },
		{ 453, 280, 320, 2, 1 }, { 444, 60, 100, 2, 1 }, { 569, 20, 40, 2, 1 },
		{ 207, 6, 8, 2, 1 }, { 211, 6, 8, 2, 1 }, { 213, 6, 8, 2, 1 }, { 215, 6, 8, 2, 1 }, { 2485, 6, 8, 2, 1 }, { 217, 6, 8, 2, 1 },
		{ 209, 6, 8, 2, 1 },
		{ 5314, 4, 6, 1, 0 }, { 5289, 2, 2, 1, 0 }, { 5315, 2, 2, 1, 0 },
		{ 995, 10000, 30000, 2, 0 }, { 2434, 2, 4, 2, 1 }, { 30640, 6, 24, 2, 0 } };

	private static final int[][] ELDRIC_TABLE = { // id, min, max, weight /56, noted
		{ 1403, 4, 4, 2, 1 }, { 1395, 6, 6, 2, 1 }, { 1079, 2, 2, 2, 0 }, { 1185, 2, 2, 2, 0 }, { 1359, 2, 2, 2, 0 }, { 1275, 2, 2, 1, 0 },
		{ 555, 1500, 3000, 2, 0 }, { 562, 200, 400, 2, 0 }, { 560, 150, 300, 2, 0 }, { 561, 100, 150, 2, 0 }, { 563, 100, 150, 2, 0 },
		{ 566, 100, 150, 2, 0 }, { 565, 100, 150, 2, 0 }, { 892, 100, 300, 2, 0 },
		{ 453, 280, 320, 2, 1 }, { 444, 60, 100, 2, 1 }, { 571, 20, 40, 2, 1 },
		{ 5298, 4, 4, 2, 0 }, { 5301, 4, 4, 2, 0 }, { 5303, 3, 3, 2, 0 }, { 5297, 4, 4, 2, 0 }, { 5299, 4, 4, 2, 0 }, { 5302, 4, 4, 2, 0 },
		{ 5295, 4, 4, 2, 0 },
		{ 5314, 4, 6, 1, 0 }, { 5289, 2, 2, 1, 0 }, { 5315, 2, 2, 1, 0 },
		{ 995, 10000, 30000, 2, 0 }, { 2434, 2, 4, 2, 1 }, { 30640, 6, 24, 2, 0 } };

	// cache stage 43: the official ice crown / prayer scrolls live on 42771 / 42770 / 42769 (30626-30628 are our old custom ids:
	// 30628 is a Donator Weapon Box, which the titans were dropping)
	private static final int[] CROWN = { 30631, 42771 }, SCROLL = { 42770, 42769 };
	private static final int GIANTSOUL = 30637, DESICCATED = 30640, HARD_CLUE = 2722, ELITE_CLUE = 12073;

	/** mode 0 = Loot, 1 = Sacrifice, 2 = Take pages. One titan per kill. */
	private static void claim(Player p, int side, int mode) {
		Arena a = ARENAS.get(p.getUserId());
		if (a == null || !a.down[0] || !a.down[1]) {
			p.sendMessage("You can't do that yet.");
			return;
		}
		if (!a.looted.add(p.getUserId())) {
			p.sendMessage("You've already claimed your reward from the Royal Titans.");
			return;
		}
		NPC titan = a.titans[side];
		Position at = p.getPosition().copy();
		List<Item> loot = new ArrayList<>();
		// loot scales with your share of the damage (elementals excluded); a full share of the titans' 1200 HP split
		// between the players present counts as 100%
		int mine = a.damage.getOrDefault(p.getUserId(), 0);
		double share = Math.min(1.0, mine / (1200.0 / Math.max(1, a.everEntered.size())));
		boolean pet;
		if (mode == 0) {
			double scale = Math.max(0.01, share);
			if (Random.get() < scale / 75.0)
				loot.add(new Item(CROWN[side], 1));
			else if (Random.get() < scale / 75.0)
				loot.add(new Item(SCROLL[side], 1));
			else if (Random.get() < scale / 16.0)
				loot.add(new Item(GIANTSOUL, 1));
			else {
				for (int i = 0; i < 2; i++)
					loot.add(roll(side == 0 ? BRANDA_TABLE : ELDRIC_TABLE, side == 0 ? 55 : 56, share));
			}
			pet = Random.get(1, 3000) == 1;
		} else if (mode == 1) {
			pet = Random.get(1, 1500) == 1;
			if (!pet)
				p.sendMessage("You don't feel lucky during your sacrifice.");
		} else {
			// a single page from 72 damage, guaranteed from 120
			int pages = mine >= 120 ? Random.get(10, 19) : mine >= 72 ? 1 : 0;
			if (pages > 0)
				loot.add(new Item(DESICCATED, pages));
			pet = Random.get(1, 3000) == 1;
		}
		if (Random.get(1, 25) == 1)
			loot.add(new Item(HARD_CLUE, 1));
		if (Random.get(1, 100) == 1)
			loot.add(new Item(ELITE_CLUE, 1));
		// the titan's loot goes straight into the inventory (only what doesn't fit is left at your feet)
		for (Item item : loot) {
			if (p.getInventory().hasRoomFor(item))
				p.getInventory().add(item.getId(), item.getAmount());
			else
				new GroundItem(item).owner(p).position(at).spawn();
			p.addToCollectionLog(item);
			int id = item.getId();
			if (id == CROWN[0] || id == CROWN[1] || id == SCROLL[0] || id == SCROLL[1] || id == GIANTSOUL)
				Broadcast.WORLD.sendNewsDropMessage(p, Icon.RARE_DROP, "<col=000000>" + p.getName(),
					" just received " + item.getDef().descriptiveName + " from the Royal Titans!");
		}
		if (pet)
			Pet.BRAN.unlock(p, 0);
		if (titan != null)
			titan.animate(DEATH_ANIM[side]);
	}

	private static Item roll(int[][] table, int total, double share) {
		int r = Random.get(0, total - 1);
		for (int[] e : table) {
			r -= e[3];
			if (r < 0) {
				int id = e[0];
				if (e[4] == 1) {
					ObjType def = ObjType.get(id);
					if (def != null && def.notedId != -1)
						id = def.notedId;
				}
				int qty = Random.get(e[1], e[2]);
				if (share < 1.0)
					qty = Math.max(1, (int) Math.round(qty * Math.max(0.01, share)));
				return new Item(id, qty);
			}
		}
		return new Item(995, 10000);
	}
}
