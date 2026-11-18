package io.ruin.model.activities.bosses;

import io.ruin.api.utils.Random;
import io.ruin.cache.Color;
import io.ruin.model.World;
import io.ruin.model.combat.AttackStyle;
import io.ruin.model.combat.Hit;
import io.ruin.model.combat.HitType;
import io.ruin.model.entity.npc.NPC;
import io.ruin.model.entity.npc.NPCAction;
import io.ruin.model.entity.npc.NPCCombat;
import io.ruin.model.entity.player.Player;
import io.ruin.model.entity.shared.listeners.HitListener;
import io.ruin.model.item.Item;
import io.ruin.model.item.actions.ItemAction;
import io.ruin.model.item.actions.impl.pet.Pet;
import io.ruin.model.map.Direction;
import io.ruin.model.map.Position;
import io.ruin.model.map.Projectile;
import io.ruin.model.map.Tile;
import io.ruin.model.map.dynamic.DynamicMap;
import io.ruin.model.map.object.GameObject;
import io.ruin.model.map.object.actions.ObjectAction;
import io.ruin.model.skills.prayer.Prayer;
import io.ruin.model.stat.StatType;
import io.ruin.utility.Broadcast;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * The Maggot King (Vampyrium, The Blood Moon Rises; OSRS 30 June 2026). Solo instanced lair: enter through the
 * Darkwood trees by the scoreboard. Mechanics per the OSRS wiki (2026-11-13):
 * <ul>
 * <li>Opens at range and is immune to melee until ~65% HP.</li>
 * <li>Green spit (ranged) leaves poisonous bile on the hit tile and two adjacent; yellow spit (magic) leaves sticky
 * bile that drains Attack/Strength/Defence. Bile lasts until stepped on or buried under carrion.</li>
 * <li>Every 4-8 spits it screeches: anyone with a protection prayer up takes 15-25 a tick, its Defence is halved,
 * carrion rains on the arena (up to 25 direct, 10 splash) and buries bile. Its attacks slow by a tick after the first.</li>
 * <li>Below ~83% each screech launches an Ur-maggot larva (alternating west/east); larvae patrol a row, eat carrion
 * (+1 HP to the King each) and chew players they reach (3 damage, +1 a tick, healing the King). Below ~19% a screech
 * launches one per row.</li>
 * <li>From ~65% it alternates spit cycles with melee cycles: a 3x4 slam in front (55, 35 protected) that splashes
 * bile; click the "Maggot king head" as it lunges to Strike it (no delay, +50% damage). Below ~20% it stays in melee.</li>
 * <li>The corpse offers "Open-stomach" (normal loot) or "Take-eggs" (maggot eggs: pet chance, no normal loot).</li>
 * </ul>
 * HP 1500 (OSRS). Animation and spit graphics are the closest cache matches (skeletal seqs 13922-13938, spotanims
 * 3793-3801) and should be confirmed in-game.
 */
@Slf4j
public class MaggotKing extends NPCCombat {

	public static final int KING = 15742, CORPSE = 15741, LARVA = 15743;
	private static final int LAIR_REGION = 11645;
	private static final int ENTRANCE = 61048, EXIT = 61049;
	private static final Position OUTSIDE = new Position(2678, 7848, 0);
	private static final int POISON_BILE = 33423, STICKY_BILE = 33424, CARRION = 33425, HEAD = 33426;

	// static-map coordinates (shifted into the instance)
	/*
	 * Lair region 11645, read from the cache map's walkable flags: only two rooms are walkable, separated by the solid row
	 * y 8037 - the south room x 2904-2918, y 8033-8036 (the player's side: exactly the four rows the larvae patrol) and
	 * the north room x 2904-2918, y 8038-8044 (the King's). The exit doorway (61049) is at 2910,8028, south of the
	 * players' room. The King (5x5) sits centred in the north room facing south; the fight is across the gap, and his
	 * head (Strike) and corpse (Open-stomach / Take-eggs) are used from the players' side.
	 */
	private static final int KING_X = 2909, KING_Y = 8040, WALL_Y = 8038, PLAYER_X = 2911, PLAYER_Y = 8033; // KING_Y: resting at the back of its room; WALL_Y: at the bone wall for slams
	private static final int ARENA_MIN_X = 2904, ARENA_MAX_X = 2918, ARENA_MIN_Y = 8033, ARENA_MAX_Y = 8036;
	private static final int[] LARVA_ROWS = { 8033, 8034, 8035, 8036 };

	/* gameval: maggot_king_meleeattack 13925, _rangeattack 13933, _mageattack 13934, _screech 13922 (+ vfx 3793 / 3794) */
	private static final int RANGED_ANIM = 13933, MAGIC_ANIM = 13934, MELEE_ANIM = 13925, SCREECH_ANIM = 13922;
	private static final int SCREECH_GFX = 3793, SCREECH_PLAYER_GFX = 3794, BLOOD_GROUND_HIT = 3798, LARVA_FLY_ANIM = 13914;
	/* npc_maggot_king_walk_forward_melee_01 / _backward_melee_01 (to the bone wall and back); carrion loc anims */
	private static final int WALK_TO_WALL = 13928, WALK_FROM_WALL = 13930;
	private static final int CARRION_HIT_ANIM = 13942, CARRION_DESPAWN_ANIM = 13944, CARRION_DESPAWN_GFX = 3801;
	private static final Projectile CARRION_SPLATTER = new Projectile(3797, 0, 0, 0, 30, 10, 16, 64); // maggot_king_blood_projectile_small
	/* maggot_king_poison_projectile 1792 (the cache has one spit projectile, used for both), blood projectiles 3796 / 3797 */
	private static final Projectile GREEN_SPIT = new Projectile(1792, 60, 10, 30, 40, 10, 16, 64);
	private static final Projectile YELLOW_SPIT = new Projectile(1792, 60, 10, 30, 40, 10, 16, 64);
	private static final Projectile CARRION_DROP = new Projectile(3796, 90, 0, 20, 50, 10, 16, 64);

	private static final int MAX_SPIT = 46, MAX_SLAM = 55, MAX_SLAM_PRAYED = 35;

	/* ------------------------------------------------------------------ lair instance */

	private static final class Lair {
		DynamicMap map;
		Player player;
		NPC king;
		NPC corpse;
	}

	private static final Map<Integer, Lair> LAIRS = new HashMap<>();

	/** whether the player is in their Maggot King lair (MaggotKingItems: "inside Vampyrium") */
	public static boolean inLair(Player player) {
		Lair l = LAIRS.get(player.getUserId());
		return l != null && l.map != null && l.map.isIn(player);
	}

	private static void enter(Player player) {
		if (LAIRS.containsKey(player.getUserId())) {
			player.sendMessage("You're already in the Maggot King's lair.");
			return;
		}
		DynamicMap map;
		try {
			map = new DynamicMap().build(LAIR_REGION, 1);
		} catch (DynamicMap.DynamicMapBuildException e) {
			log.error("Unable to build the Maggot King lair", e);
			player.sendMessage("The lair is collapsing... try again in a moment.");
			return;
		}
		Lair lair = new Lair();
		lair.map = map;
		lair.player = player;
		LAIRS.put(player.getUserId(), lair);
		player.animate(2796);
		player.getMovement().teleport(map.convertX(PLAYER_X), map.convertY(PLAYER_Y), 0);
		map.assignListener(player).onExit((p, logout) -> leave(p, logout));
		// the exit doorway (3x3 at 2910,8028) sits behind blocked tiles below the players' room: usable from that room
		GameObject exit = io.ruin.model.map.Tile.getObject(EXIT, map.convertX(2910), map.convertY(8028), 0);
		if (exit != null) {
			int minX = map.convertX(ARENA_MIN_X), maxX = map.convertX(ARENA_MAX_X), minY = map.convertY(ARENA_MIN_Y), maxY = map.convertY(ARENA_MAX_Y);
			exit.skipReachCheck = pos -> pos.getX() >= minX && pos.getX() <= maxX && pos.getY() >= minY && pos.getY() <= maxY;
		}
		spawnKing(lair);
		player.sendMessage(Color.DARK_RED.wrap("The Maggot King stirs in its nest..."));
	}

	private static void spawnKing(Lair lair) {
		if (lair.map == null)
			return;
		NPC king = new NPC(KING).spawn(lair.map.convertX(KING_X), lair.map.convertY(KING_Y), 0, Direction.SOUTH, 0);
		king.ownerId = lair.player.getUserId();
		lair.king = king;
		MaggotKing c = (MaggotKing) king.getCombat();
		c.lair = lair;
		king.addEvent(e -> {
			e.delay(3);
			king.targetPlayer(lair.player, false);
			c.setTarget(lair.player);
		});
	}

	private static void leave(Player player, boolean logout) {
		Lair lair = LAIRS.remove(player.getUserId());
		if (lair == null)
			return;
		if (lair.king != null) {
			MaggotKing c = lair.king.getCombat() instanceof MaggotKing m ? m : null;
			if (c != null)
				c.cleanup();
			lair.king.remove();
		}
		if (lair.corpse != null)
			lair.corpse.remove();
		if (lair.map != null) {
			lair.map.destroy();
			lair.map = null;
		}
		if (logout)
			player.getMovement().teleport(OUTSIDE);
	}

	/* ------------------------------------------------------------------ state */

	private Lair lair;
	private final Map<Position, GameObject> bile = new HashMap<>();
	private final Map<Position, GameObject> carrion = new HashMap<>();
	private final List<NPC> larvae = new ArrayList<>();
	private boolean larvaFromWest = true;
	private int spitsLeft = Random.get(4, 8);
	private boolean meleeCycle = false;
	private int meleeLeft = 0;
	private boolean screechedOnce = false;
	private boolean swarmLaunched = false; // wiki: four larvae only on the FIRST screech below ~19%

	/** wiki: magic defence +150, raised to +400 while it is up at the bone wall slamming */
	private void wallDefence(boolean atWall) {
		setBonus(8, atWall ? 400 : 150); // 8 = magic defence
	}
	private GameObject head;
	private int nextDelay = 4;
	/** OSRS's boss health bar */
	private final io.ruin.model.entity.BossHudTracker hud = new io.ruin.model.entity.BossHudTracker();

	private int mx(int x) { return lair.map.convertX(x); }
	private int my(int y) { return lair.map.convertY(y); }

	private double hpRatio() {
		return (double) npc.getHp() / npc.getMaxHp();
	}

	private boolean inArena(Position p) {
		if (lair == null || lair.map == null)
			return false;
		return p.getX() >= mx(ARENA_MIN_X) && p.getX() <= mx(ARENA_MAX_X) && p.getY() >= my(ARENA_MIN_Y) && p.getY() <= my(ARENA_MAX_Y);
	}

	/* ------------------------------------------------------------------ setup */

	@Override
	public void init() {
		npc.hitListener = new HitListener().preDefend(hit -> {
			if (hit.attackStyle != null && hit.attackStyle.isMelee() && hpRatio() > 0.65) {
				hit.block();
				if (hit.attacker != null && hit.attacker.player != null)
					hit.attacker.player.sendMessage("The Maggot King is out of reach of your melee attacks.");
				return;
			}
			if (hit.attacker != null && hit.attacker.player != null && hit.attackStyle != null && hit.attackStyle.isMelee()
				&& Boolean.TRUE.equals(hit.attacker.player.remove("MAGGOT_PUNISH"))) {
				hit.boostDamage(0.5);
			}
		});
	}

	@Override
	public void follow() {
		// stays in its nest; melee covers the 3x4 area in front of it
	}

	private boolean inSlamZone(Player p) {
		int x = p.getAbsX(), y = p.getAbsY(), bx = npc.getAbsX(), by = npc.getAbsY();
		return p.getHeight() == npc.getHeight() && x >= bx && x < bx + npc.getSize() && y >= by - 4 && y <= by - 1;
	}

	/* ------------------------------------------------------------------ tick */

	@Override
	public void process() {
		if (lair == null || lair.player == null || isDead())
			return;
		Player p = lair.player;
		Position pos = p.getPosition();
		hud.update(lair.map != null && lair.map.isIn(p) ? List.of(p) : List.of(), KING, npc.getHp(), npc.getMaxHp());
		// wiki: bile stays until carrion covers it; every tick a player stands in it they take minor damage.
		// Green (poisonous) bile also drains 45 seconds of poison immunity (blocked by a serpentine helm with anti-venom+);
		// yellow (sticky) bile drains Attack, Strength or Defence and delays the next attack a tick (blocked by an
		// inoculation bracelet). NOTE: the wiki gives no number for "minor damage"; 1-3 here, drain 1-3 levels.
		GameObject b = bile.get(pos);
		if (b != null && !p.getCombat().isDead()) {
			if (b.getId() == POISON_BILE) {
				boolean serpentine = p.getEquipment().hasId(12931) || p.getEquipment().hasId(13197) || p.getEquipment().hasId(13199);
				if (!(serpentine && p.isPoisonImmune())) {
					p.hit(new Hit(npc).randDamage(1, 3).ignorePrayer().ignoreDefence());
					p.poisonImmunity = Math.max(0, p.poisonImmunity - 75);
				}
			} else if (!p.getEquipment().hasId(11088)) {
				int dmg = Random.get(1, 3);
				p.hit(new Hit(npc).fixedDamage(dmg).ignorePrayer().ignoreDefence());
				StatType s = Random.get(new StatType[] { StatType.Attack, StatType.Strength, StatType.Defence });
				p.getStats().get(s).drain(dmg);
				p.getCombat().delayAttack(1);
			}
		}
		processLarvae(p);
	}

	@Override
	public boolean attack() {
		if (target == null || target.player == null || lair == null)
			return false;
		Player p = target.player;
		int speed = screechedOnce ? 5 : 4;
		if (meleeCycle || hpRatio() <= 0.20) {
			int walk = slam(p);
			nextDelay = speed + walk;
			if (hpRatio() > 0.20 && --meleeLeft <= 0) {
				meleeCycle = false; // back to spitting (it retreats after its next screech)
				spitsLeft = Random.get(4, 8);
			}
			return true;
		}
		if (spitsLeft-- <= 0) {
			screech();
			nextDelay = 6;
			return true;
		}
		if (Random.get(1) == 0)
			spit(p, true);
		else
			spit(p, false);
		nextDelay = speed;
		return true;
	}

	@Override
	protected void afterAttack() {
		updateLastAttack(nextDelay);
	}

	/* ------------------------------------------------------------------ attacks */

	private void spit(Player p, boolean ranged) {
		npc.animate(ranged ? RANGED_ANIM : MAGIC_ANIM);
		int delay = (ranged ? GREEN_SPIT : YELLOW_SPIT).send(npc, p);
		Prayer protect = ranged ? Prayer.PROTECT_FROM_MISSILES : Prayer.PROTECT_FROM_MAGIC;
		npc.addEvent(e -> {
			e.delay(World.getTicks(delay));
			if (isDead() || lair == null)
				return;
			// wiki: the spit lands on the player (protection prayer blocks it fully); the green spit also poisons (8)
			Position landed = p.getPosition().copy();
			int max = p.getPrayer().isActive(protect) ? 0 : MAX_SPIT;
			if (max > 0)
				p.hit(new Hit(npc, ranged ? AttackStyle.RANGED : AttackStyle.MAGIC).randDamage(max).ignorePrayer());
			else
				p.hit(new Hit(npc, ranged ? AttackStyle.RANGED : AttackStyle.MAGIC).fixedDamage(0));
			if (ranged)
				p.poison(8);
			// bile on the tile the player stood on when it landed, and the two beside it (perpendicular to the path)
			Position tile = landed;
			boolean horizontal = Math.abs(tile.getX() - npc.getCentrePosition().getX()) < Math.abs(tile.getY() - npc.getCentrePosition().getY());
			for (int d = -1; d <= 1; d++)
				placeBile(horizontal ? tile.copy().translate(d, 0, 0) : tile.copy().translate(0, d, 0), ranged ? POISON_BILE : STICKY_BILE);
		});
	}

	/**
	 * Wiki: it walks up to the bone wall, lines its centre tile up with the player's, and slams the 3x4 in front of it.
	 * Returns the ticks spent walking.
	 */
	private int slam(Player p) {
		wallDefence(true);
		int tx = Math.max(mx(ARENA_MIN_X), Math.min(mx(ARENA_MAX_X) - npc.getSize() + 1, p.getAbsX() - npc.getSize() / 2));
		Position to = new Position(tx, my(WALL_Y), npc.getHeight());
		int walk = Math.max(Math.abs(npc.getAbsX() - to.getX()), Math.abs(npc.getAbsY() - to.getY()));
		if (walk > 0) {
			io.ruin.model.activities.raids.tfa.Raid4Fx.walkStance(npc, WALK_TO_WALL);
			io.ruin.model.activities.raids.tfa.Raid4Fx.slide(npc, to, walk * 30, 0);
		}
		npc.addEvent(e -> {
			if (walk > 0) {
				e.delay(walk);
				io.ruin.model.activities.raids.tfa.Raid4Fx.resetStance(npc);
			}
			if (isDead() || lair == null)
				return;
			slamNow(p);
		});
		return walk;
	}

	/** walks back from the wall after a screech (it stops retreating below ~20%) */
	private void retreat() {
		if (npc.getAbsY() == my(KING_Y) || hpRatio() <= 0.20)
			return;
		wallDefence(false);
		Position to = new Position(npc.getAbsX(), my(KING_Y), npc.getHeight());
		int walk = Math.abs(npc.getAbsY() - to.getY());
		io.ruin.model.activities.raids.tfa.Raid4Fx.walkStance(npc, WALK_FROM_WALL);
		io.ruin.model.activities.raids.tfa.Raid4Fx.slide(npc, to, walk * 30, 0);
		npc.addEvent(e -> {
			e.delay(walk);
			io.ruin.model.activities.raids.tfa.Raid4Fx.resetStance(npc);
		});
	}

	private void slamNow(Player p) {
		npc.animate(MELEE_ANIM);
		Position front = new Position(npc.getAbsX() + npc.getSize() / 2, npc.getAbsY() - 1, npc.getHeight());
		head = GameObject.spawn(HEAD, front.getX(), front.getY(), front.getZ(), 10, 0);
		GameObject thisHead = head;
		npc.addEvent(e -> {
			e.delay(2);
			if (!thisHead.isRemoved())
				thisHead.remove();
			if (isDead() || lair == null)
				return;
			if (inSlamZone(p)) {
				boolean prayed = p.getPrayer().isActive(Prayer.PROTECT_FROM_MELEE);
				p.hit(new Hit(npc, AttackStyle.CRUSH).randDamage(prayed ? MAX_SLAM_PRAYED : MAX_SLAM).ignorePrayer());
			}
			for (int i = 0; i < 3; i++)
				placeBile(front.copy().translate(Random.get(-2, 2), -Random.get(1, 2), 0), Random.get(1) == 0 ? POISON_BILE : STICKY_BILE);
		});
	}

	private void screech() {
		retreat();
		npc.animate(SCREECH_ANIM);
		npc.graphics(SCREECH_GFX);
		screechedOnce = true;
		Player p = lair.player;
		p.graphics(SCREECH_PLAYER_GFX, 0, 0);
		var def = npc.getCombat().getStat(StatType.Defence);
		int oldDef = def.currentLevel;
		def.currentLevel = Math.max(1, def.fixedLevel / 2);
		npc.addEvent(e -> {
			for (int t = 0; t < 3; t++) {
				if (isDead())
					return;
				if (p.getPrayer().isActive(Prayer.PROTECT_FROM_MAGIC) || p.getPrayer().isActive(Prayer.PROTECT_FROM_MISSILES)
					|| p.getPrayer().isActive(Prayer.PROTECT_FROM_MELEE))
					p.hit(new Hit(HitType.DAMAGE).randDamage(15, 25));
				e.delay(1);
			}
			rainCarrion(p);
			launchLarvae(p);
			e.delay(8);
			def.currentLevel = Math.max(def.currentLevel, oldDef); // drains are restored after the screech
		});
		spitsLeft = Random.get(4, 8);
		if (hpRatio() <= 0.65) {
			meleeCycle = true;
			meleeLeft = Random.get(3, 5);
		}
	}

	/**
	 * Wiki: carrion piles fall on random tiles (up to 25), then each splatters smaller bits onto tiles next to it (up to
	 * 10). Carrion covers bile. Cache: big / small blood projectiles 3796 / 3797, ground hit 3798, the carrion loc 33425
	 * with its hit / despawn anims (13942 / 13944) and despawn spotanim 3801.
	 */
	private void rainCarrion(Player p) {
		int piles = Random.get(10, 14); // OSRS footage: a screech covers most of the players' room in carrion
		for (int i = 0; i < piles; i++) {
			Position t = i == 0 ? p.getPosition().copy()
				: new Position(mx(Random.get(ARENA_MIN_X, ARENA_MAX_X)), my(Random.get(ARENA_MIN_Y, ARENA_MAX_Y)), npc.getHeight());
			int delay = CARRION_DROP.send(npc, t);
			npc.addEvent(e -> {
				e.delay(World.getTicks(delay));
				if (isDead() || lair == null)
					return;
				World.sendGraphics(BLOOD_GROUND_HIT, 0, 0, t);
				if (p.getPosition().equals(t))
					p.hit(new Hit(HitType.DAMAGE).randDamage(25));
				landCarrion(t);
				List<Position> around = new ArrayList<>();
				for (int dx = -1; dx <= 1; dx++)
					for (int dy = -1; dy <= 1; dy++)
						if (dx != 0 || dy != 0) {
							Position n = t.copy().translate(dx, dy, 0);
							if (inArena(n))
								around.add(n);
						}
				Collections.shuffle(around);
				for (Position n : around.subList(0, Math.min(around.size(), Random.get(2, 4)))) {
					int d2 = CARRION_SPLATTER.send(t, n);
					World.startEvent(e2 -> {
						e2.delay(World.getTicks(d2));
						if (isDead() || lair == null)
							return;
						World.sendGraphics(BLOOD_GROUND_HIT, 0, 0, n);
						if (p.getPosition().equals(n))
							p.hit(new Hit(HitType.DAMAGE).randDamage(10));
						landCarrion(n);
					});
				}
			});
		}
	}

	private void landCarrion(Position t) {
		removeBile(t); // carrion buries bile
		if (carrion.containsKey(t) || Tile.get(t, true).clipping != 0)
			return;
		GameObject pile = GameObject.spawn(CARRION, t.getX(), t.getY(), t.getZ(), 22, Random.get(3));
		pile.animate(CARRION_HIT_ANIM);
		carrion.put(t, pile);
	}

	/** a larva eats a carrion pile (despawn anim + spotanim) */
	private void eatCarrion(Position t) {
		GameObject pile = carrion.remove(t);
		if (pile == null)
			return;
		pile.animate(CARRION_DESPAWN_ANIM);
		World.sendGraphics(CARRION_DESPAWN_GFX, 0, 0, t);
		World.startEvent(e -> {
			e.delay(1);
			if (!pile.isRemoved())
				pile.remove();
		});
	}

	private void placeBile(Position t, int id) {
		if (!inArena(t) || Tile.get(t, true).clipping != 0 || carrion.containsKey(t))
			return;
		removeBile(t);
		bile.put(t, GameObject.spawn(id, t.getX(), t.getY(), t.getZ(), 22, Random.get(3)));
	}

	private void removeBile(Position t) {
		GameObject o = bile.remove(t);
		if (o != null && !o.isRemoved())
			o.remove();
	}

	/* ------------------------------------------------------------------ larvae */

	private void launchLarvae(Player p) {
		double r = hpRatio();
		if (r > 0.83)
			return;
		// wiki: the first screech below ~19% launches one larva per row (fewer if some are already out); otherwise one
		int count = 1;
		if (r <= 0.19 && !swarmLaunched) {
			swarmLaunched = true;
			larvae.removeIf(l -> l == null || l.isRemoved());
			count = Math.max(1, LARVA_ROWS.length - larvae.size());
		}
		for (int i = 0; i < count; i++) {
			int row = count == 1 ? LARVA_ROWS[Random.get(LARVA_ROWS.length - 1)] : LARVA_ROWS[(LARVA_ROWS.length - count + i)];
			boolean west = larvaFromWest;
			larvaFromWest = !larvaFromWest;
			Position land = new Position(mx(west ? ARENA_MIN_X + 1 : ARENA_MAX_X - 1), my(row), npc.getHeight());
			// wiki: the larva is launched through the air and can be killed with ranged before it lands
			Position from = new Position(npc.getAbsX() + npc.getSize() / 2, npc.getAbsY(), npc.getHeight());
			NPC larva = new NPC(LARVA).spawn(from.getX(), from.getY(), from.getZ(), Direction.SOUTH, 0);
			larva.ownerId = p.getUserId();
			larva.animate(LARVA_FLY_ANIM);
			int flight = Math.max(2, Math.max(Math.abs(from.getX() - land.getX()), Math.abs(from.getY() - land.getY())) / 3);
			io.ruin.model.activities.raids.tfa.Raid4Fx.slide(larva, land, flight * 30, 0);
			npc.addEvent(e -> {
				e.delay(flight);
				if (isDead() || lair == null || larva.isRemoved() || larva.getCombat().isDead())
					return;
				larva.set("ROW_Y", land.getY());
				larva.set("DIR", west ? 1 : -1);
				larvae.add(larva);
			});
		}
	}

	private void processLarvae(Player p) {
		larvae.removeIf(l -> l.isRemoved() || l.getCombat() == null || l.getCombat().isDead());
		for (NPC l : larvae) {
			int dir = l.get("DIR", 1);
			Position pos = l.getPosition();
			// eat carrion on its tile: +1 HP to the King
			if (carrion.containsKey(pos)) {
				eatCarrion(pos);
				heal(1);
			}
			if (pos.isWithinDistance(p.getPosition(), 1)) {
				int chew = l.get("CHEW", 3);
				p.hit(new Hit(HitType.DAMAGE).fixedDamage(chew));
				heal(chew);
				l.set("CHEW", chew + 1);
				continue;
			}
			l.set("CHEW", 3);
			int nx = pos.getX() + dir;
			if (nx < mx(ARENA_MIN_X) || nx > mx(ARENA_MAX_X)) {
				dir = -dir;
				l.set("DIR", dir);
				nx = pos.getX() + dir;
			}
			l.getRouteFinder().routeAbsolute(nx, l.get("ROW_Y", pos.getY()));
		}
	}

	private void heal(int amount) {
		var hp = npc.getCombat().getStat(StatType.Hitpoints);
		hp.currentLevel = Math.min(hp.fixedLevel, hp.currentLevel + amount);
	}

	/* ------------------------------------------------------------------ death */

	@Override
	public void startDeath(Hit killHit) {
		Player p = lair == null ? null : lair.player;
		Position at = npc.getPosition().copy();
		cleanup();
		if (p != null)
			p.maggotKingKills.increment(p);
		super.startDeath(killHit);
		npc.graphics(3795); // vfx_maggot_king_death_01
		if (lair != null && lair.map != null) {
			Lair l = lair;
			World.startEvent(e -> {
				e.delay(info.death_ticks + 1);
				if (l.map == null)
					return;
				l.corpse = new NPC(CORPSE).spawn(at.getX(), at.getY(), at.getZ(), Direction.SOUTH, 0);
				l.corpse.ownerId = l.player.getUserId();
				// the corpse lies in the King's room across the gap: loot it from anywhere in the players' room
				l.corpse.skipReachCheck = pos -> inArena(pos);
			});
		}
	}

	private void cleanup() {
		hud.closeAll();
		bile.values().forEach(GameObject::remove);
		bile.clear();
		carrion.values().forEach(GameObject::remove);
		carrion.clear();
		larvae.forEach(NPC::remove);
		larvae.clear();
		if (head != null && !head.isRemoved())
			head.remove();
	}

	@Override
	public void handleNewDrop(Player player, int npcID, Position dropPosition) {
		// no drop on death: the corpse holds the loot (Open-stomach / Take-eggs)
	}

	private static void lootCorpse(Player player, NPC corpse, boolean eggs) {
		Lair lair = LAIRS.get(player.getUserId());
		if (lair == null || lair.corpse != corpse || corpse.isRemoved())
			return;
		lair.corpse = null;
		corpse.remove();
		List<Item> loot = new ArrayList<>();
		if (eggs) {
			int roll = Random.get(1499);
			int[][] eggTable = { { 33665, 300 }, { 33667, 150 }, { 33669, 33 }, { 33671, 6 }, { 33673, 3 }, { 33675, 1 } };
			Item egg = null;
			for (int[] eg : eggTable) {
				roll -= eg[1];
				if (roll < 0) {
					egg = new Item(eg[0], 1);
					break;
				}
			}
			if (egg != null)
				loot.add(egg);
			else if (Random.get(1) == 0) {
				loot.add(new Item(33628, Random.get(2, 4)));
				loot.add(new Item(33627, 1));
			} else
				player.sendMessage("The eggs pop as you try to take them.");
		} else {
			if (Random.get(339) == 0)
				loot.add(broadcast(new Item(33634, 1))); // Elder venator fang 1/340
			else if (Random.get(519) == 0)
				loot.add(broadcast(new Item(33631, 1))); // Crimson kisten 1/520
			else
				loot.addAll(rollMain());
			if (Random.get(3499) == 0)
				Pet.MAGGOT_MARQUESS.unlock(player, 0);
		}
		if (Random.get(39) == 0)
			loot.add(new Item(12073, 1)); // elite clue 1/40
		NPC king = lair.king;
		if (king != null && king.getCombat() instanceof MaggotKing c && !loot.isEmpty())
			c.handleDrop(player, player.getPosition(), loot);
		// it respawns shortly after the corpse is dealt with
		World.startEvent(e -> {
			e.delay(9);
			if (LAIRS.get(player.getUserId()) == lair && lair.map != null) {
				if (lair.king != null)
					lair.king.remove();
				spawnKing(lair);
			}
		});
	}

	/** {item, weight /159, min, max} - OSRS Open-stomach table (noted resources) */
	private static final int[][] MAIN_TABLE = {
		{ 1620, 15, 10, 13 }, { 33651, 10, 51, 74 }, { 2971, 10, 50, 76 }, { 33625, 10, 23, 34 }, { 452, 5, 12, 15 },
		{ 33623, 5, 8, 11 }, { 445, 5, 24, 32 }, { 448, 5, 36, 41 }, { 450, 10, 30, 40 }, { 33657, 4, 4, 5 },
		{ 33661, 2, 112, 179 }, { 33663, 2, 178, 219 },
		{ 33679, 12, 1, 1 }, { 33687, 6, 1, 1 }, { 33689, 3, 1, 1 }, { 33691, 3, 1, 1 }, { 33693, 3, 1, 1 },
		{ 33683, 2, 1, 1 }, { 33677, 2, 1, 1 }, { 33685, 2, 1, 1 }, { 33681, 2, 1, 1 },
		{ -1, 41, 0, 0 }, // supplies: stymphike tartare 2-4 + dull ancient medal
	};

	private static List<Item> rollMain() {
		int total = 0;
		for (int[] r : MAIN_TABLE)
			total += r[1];
		int roll = Random.get(total - 1);
		for (int[] r : MAIN_TABLE) {
			roll -= r[1];
			if (roll < 0) {
				if (r[0] == -1)
					return List.of(new Item(33628, Random.get(2, 4)), new Item(33627, 1));
				return List.of(new Item(r[0], Random.get(r[2], r[3])));
			}
		}
		return List.of(new Item(1620, 10));
	}

	private static Item broadcast(Item item) {
		item.lootBroadcast = Broadcast.GLOBAL;
		return item;
	}

	/** Popping an egg: the rarer the egg, the likelier the Maggot marquess (about 1/1,500 per egg-take overall). */
	private static void popEgg(Player player, Item item) {
		int chance = switch (item.getId()) {
			case 33665 -> 4000;
			case 33667 -> 2000;
			case 33669 -> 400;
			case 33671 -> 80;
			case 33673 -> 20;
			default -> 2;
		};
		item.remove();
		player.animate(827);
		if (Random.get(chance - 1) == 0) {
			player.sendMessage(Color.DARK_GREEN.wrap("Something wriggles out of the egg!"));
			Pet.MAGGOT_MARQUESS.unlock(player, 0);
		} else {
			player.sendMessage("The egg pops. Nothing inside survived.");
		}
	}

	/* ------------------------------------------------------------------ registration */

	public static void register() {
		ObjectAction.register(ENTRANCE, "Enter", (player, obj) -> enter(player));
		ObjectAction.register(EXIT, "Exit", (player, obj) -> player.getMovement().teleport(OUTSIDE));
		ObjectAction.register(EXIT, "Quick-exit", (player, obj) -> player.getMovement().teleport(OUTSIDE));
		ObjectAction.register(HEAD, "Strike", (player, obj) -> {
			Lair lair = LAIRS.get(player.getUserId());
			if (lair == null || lair.king == null || obj.isRemoved())
				return;
			// punish: an immediate melee swing with +50% damage on its first hit
			player.set("MAGGOT_PUNISH", true);
			player.getCombat().updateLastAttack(0);
			player.getCombat().setTarget(lair.king);
		});
		NPCAction.register(CORPSE, "Open-stomach", (player, npc) -> lootCorpse(player, npc, false));
		NPCAction.register(CORPSE, "Take-eggs", (player, npc) -> lootCorpse(player, npc, true));
		for (int egg = 33665; egg <= 33675; egg += 2)
			ItemAction.registerInventory(egg, "Pop", MaggotKing::popEgg);
	}
}
