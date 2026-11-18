package io.ruin.model.map.object.actions.impl.prifddinas.trahaearndistrict;

import io.ruin.api.utils.Random;
import io.ruin.cache.Color;
import io.ruin.model.World;
import io.ruin.model.combat.Hit;
import io.ruin.model.combat.HitType;
import io.ruin.model.entity.npc.NPC;
import io.ruin.model.entity.npc.NPCAction;
import io.ruin.model.entity.npc.NPCCombat;
import io.ruin.model.entity.player.Player;
import io.ruin.model.entity.shared.listeners.HitListener;
import io.ruin.model.item.Item;
import io.ruin.model.item.actions.impl.pet.Pet;
import io.ruin.model.item.containers.Equipment;
import io.ruin.model.map.Direction;
import io.ruin.model.map.Position;
import io.ruin.model.map.Projectile;
import io.ruin.model.map.Tile;
import io.ruin.model.map.object.GameObject;
import io.ruin.model.map.object.actions.ObjectAction;
import io.ruin.model.skills.mining.Pickaxe;
import io.ruin.model.stat.StatType;
import io.ruin.utility.Broadcast;

import java.util.*;

/**
 * Zalcano (Prifddinas, Trahaearn district), OSRS 1:1, shared world boss.
 * <p>
 * Loop: mine tephra from the glowing rock formation (normal ones yield far less), refine it at the east furnace,
 * imbue it at the west altar, then "Attack" Zalcano to throw imbued tephra at her armour (blue demonic symbols boost
 * throws by 50%). The armoured form (9049) shows the armour as its health bar (300). When the armour breaks she falls
 * (9050) and players "Mine" her real HP (1000, carried between phases) for 12-16 seconds, then she rises: armour back
 * to full, a shockwave destroys every player's tephra and throws back anyone adjacent.
 * <p>
 * Her attacks (typeless, prayers don't help): falling boulders, demonic symbols (orange burns, blue boosts), a golem
 * that heals her armour if it reaches her (drops imbued tephra when killed), a blast that deactivates the glowing rock
 * (another starts glowing) and a pebble barrage on everyone.
 * <p>
 * Loot is contribution based: PM = H + 2S, PT = min(H,400) + 2*min(S,300). Players need 5+ armour damage for loot and
 * 31+ combined damage for uniques; the MVP gets +10% (rounded up) on non-unique loot, 3 shards and infernal ashes.
 */
public class ZalcanoCombat extends NPCCombat {

	public static final int ARMOURED = 9049, WEAKENED = 9050, GOLEM = 9051;
	private static final int TEPHRA = 23905, REFINED_TEPHRA = 23906, IMBUED_TEPHRA = 23907;
	private static final int ROCK_GLOWING = 36192, ROCK_NORMAL = 36193, ROCK_DEPLETED = 36194, FURNACE = 36195, ALTAR = 36196;
	private static final int SYMBOL_ORANGE = 36199, SYMBOL_BLUE = 36200;
	private static final int SHIELD_MAX = 300;

	private static final int[][] ROCKS = { { 3040, 6057 }, { 3025, 6057 }, { 3025, 6040 }, { 3040, 6040 } }; // NE first: most likely to glow
	private static final int ARENA_MIN_X = 3017, ARENA_MAX_X = 3048, ARENA_MIN_Y = 6032, ARENA_MAX_Y = 6061;

	// animations / graphics (Zalcano seqs 8429-8440)
	/* gameval: zalcano_attack_melee 8431 / _ranged 8432 / _magic 8433 / _golem 8434 / _rock_fall 8435, fall_down 8437, stand_up 8439 */
	private static final int PEBBLE_ANIM = 8431, GOLEM_ANIM = 8434, SYMBOL_ANIM = 8433, ROCK_BLAST_ANIM = 8432, BOULDER_ANIM = 8435;
	private static final int FALL_DOWN_ANIM = 8437, STAND_UP_ANIM = 8439, ORE_HIT_GFX = 1732;
	private static final int THROW_ANIM = 929;
	private static final Projectile GOLEM_BALL = new Projectile(1729, 25, 31, 25, 35, 10, 0, 32);
	private static final Projectile ROCK_BALL = new Projectile(1728, 25, 31, 25, 35, 10, 0, 32);
	private static final Projectile PEBBLE = new Projectile(1302, 25, 31, 25, 35, 10, 0, 32);
	private static final Projectile TEPHRA_THROW = new Projectile(1731, 30, 31, 15, 30, 10, 0, 32); // zalcano_ore_throw
	private static final int BOULDER_GFX = 1727, GOLEM_SPAWN_GFX = 1730; // zalcano_rock_fall, zalcano_golem_spawn

	/* ------------------------------------------------------------------ state */

	private int bossHp = 1000;
	/** hits this class created (tephra throws, mining); anything else (weapons, spells) does nothing to her */
	private final Set<Hit> ownHits = Collections.newSetFromMap(new IdentityHashMap<>());
	private final Map<Integer, int[]> contribution = new HashMap<>(); // userId -> {hp damage, armour damage}
	private final Map<Integer, Player> contributors = new HashMap<>();
	private final GameObject[] rocks = new GameObject[4];
	private int glowing = 0;
	private boolean arenaReady = false;
	private final List<GameObject> symbols = new ArrayList<>();
	private final Map<Position, Integer> orangeTiles = new HashMap<>(); // tile -> tick it started burning
	private final Set<Position> blueTiles = new HashSet<>();
	private NPC golem;
	private int ticksToNextAttack = 10;
	private int weakenedTicksLeft = 0;
	/** OSRS's boss health bar: her armour (orange, 9049) while armoured, her real HP (9050) while down */
	private final io.ruin.model.entity.BossHudTracker hud = new io.ruin.model.entity.BossHudTracker();
	/** the yellow hint arrow OSRS puts over the glowing rock: which rock each arena player's arrow points at */
	private final Map<Player, Integer> arrowShown = new HashMap<>();

	/* ------------------------------------------------------------------ setup */

	@Override
	public void init() {
		npc.set("ZALCANO", this);
		npc.isMovementBlocked(false, true);
		npc.hitListener = new HitListener().preDamage(hit -> {
			// only tephra throws (armoured) and mining (weakened) hurt her; both are applied by this class
			if (!ownHits.remove(hit)) {
				hit.damage = 0;
				return;
			}
			// the armour bar can't "kill" the armoured form: it breaks instead
			if (npc.getId() == ARMOURED && hit.damage >= npc.getHp())
				hit.damage = Math.max(0, npc.getHp() - 1);
		}).postDamage(hit -> {
			if (hit.attacker == null || hit.attacker.player == null || hit.damage <= 0)
				return;
			Player p = hit.attacker.player;
			int[] c = contribution.computeIfAbsent(p.getUserId(), k -> new int[2]);
			contributors.put(p.getUserId(), p);
			if (npc.getId() == ARMOURED) {
				c[1] += hit.damage;
				if (npc.getHp() <= 1)
					breakArmour();
			} else {
				c[0] += hit.damage;
			}
		});
		npc.respawnListener = n -> resetEncounter();
		npc.set("ATTACK_OVERRIDE", (NPCAction) ZalcanoCombat::throwTephra);
	}

	private void readyArena() {
		if (arenaReady)
			return;
		arenaReady = true;
		for (int i = 0; i < ROCKS.length; i++) {
			GameObject rock = Tile.getObject(ROCK_DEPLETED, ROCKS[i][0], ROCKS[i][1], 0);
			if (rock == null)
				rock = Tile.getObject(ROCK_NORMAL, ROCKS[i][0], ROCKS[i][1], 0);
			if (rock == null)
				rock = Tile.getObject(ROCK_GLOWING, ROCKS[i][0], ROCKS[i][1], 0);
			rocks[i] = rock;
		}
		setGlowing(Random.rollPercent(50) ? 0 : Random.get(3));
	}

	private void setGlowing(int index) {
		glowing = index;
		for (int i = 0; i < rocks.length; i++)
			if (rocks[i] != null)
				rocks[i].setId(i == index ? ROCK_GLOWING : ROCK_NORMAL);
	}

	private void resetEncounter() {
		if (npc.getId() != ARMOURED)
			npc.transform(ARMOURED);
		npc.getCombat().restore();
		bossHp = 1000;
		contribution.clear();
		contributors.clear();
		clearSymbols();
		removeGolem();
		weakenedTicksLeft = 0;
		ticksToNextAttack = 10;
		if (arenaReady)
			setGlowing(Random.rollPercent(50) ? 0 : Random.get(3));
	}

	@Override
	public void follow() {
	}

	@Override
	public boolean attack() {
		return false; // everything runs from process(): her attacks hit the whole arena, not one target
	}

	private List<Player> arenaPlayers() {
		List<Player> list = new ArrayList<>();
		for (Player p : npc.localPlayers())
			if (p != null && inArena(p.getPosition()) && !p.getCombat().isDead())
				list.add(p);
		return list;
	}

	private static boolean inArena(Position p) {
		return p.getZ() == 0 && p.getX() >= ARENA_MIN_X && p.getX() <= ARENA_MAX_X && p.getY() >= ARENA_MIN_Y && p.getY() <= ARENA_MAX_Y;
	}

	private boolean underHer(Position p) {
		return p.getX() >= npc.getAbsX() && p.getX() < npc.getAbsX() + npc.getSize() && p.getY() >= npc.getAbsY() && p.getY() < npc.getAbsY() + npc.getSize();
	}

	/* ------------------------------------------------------------------ tick */

	@Override
	public void process() {
		readyArena();
		if (isDead())
			return;
		List<Player> players = arenaPlayers();
		hud.update(players, npc.getId(), npc.getHp(), npc.getId() == ARMOURED ? SHIELD_MAX : 1000);
		updateArrows(players);
		processSymbols(players);
		if (npc.getId() == WEAKENED) {
			for (Player p : players)
				if (underHer(p.getPosition()))
					p.hit(new Hit(HitType.DAMAGE).randDamage(3, 10));
			if (--weakenedTicksLeft <= 0)
				rise(players);
			return;
		}
		if (players.isEmpty())
			return;
		if (--ticksToNextAttack > 0)
			return;
		ticksToNextAttack = Random.get(8, 12);
		switch (Random.get(4)) {
			case 0 -> boulders(players);
			case 1 -> symbolsAttack(players);
			case 2 -> { if (golem == null || golem.isRemoved()) spawnGolem(); else boulders(players); }
			case 3 -> rockBlast();
			default -> pebbles(players);
		}
	}

	private void updateArrows(List<Player> players) {
		arrowShown.entrySet().removeIf(e -> {
			if (players.contains(e.getKey()))
				return false;
			if (e.getKey().isOnline())
				e.getKey().getPacketSender().resetHintIcon(false);
			return true;
		});
		GameObject rock = rocks[glowing];
		if (rock == null)
			return;
		var def = rock.getDef();
		int cx = rock.getPosition().getX() + (def == null ? 0 : (def.xLength - 1) / 2);
		int cy = rock.getPosition().getY() + (def == null ? 0 : (def.yLength - 1) / 2);
		for (Player p : players) {
			Integer shown = arrowShown.get(p);
			if (shown != null && shown == glowing)
				continue;
			p.getPacketSender().sendHintIcon(cx, cy);
			arrowShown.put(p, glowing);
		}
	}

	/* ------------------------------------------------------------------ armour phases */

	private void breakArmour() {
		if (npc.getId() != ARMOURED)
			return;
		npc.transform(WEAKENED);
		npc.animate(FALL_DOWN_ANIM);
		npc.getCombat().getStat(StatType.Hitpoints).currentLevel = Math.max(1, bossHp);
		weakenedTicksLeft = Random.get(20, 27); // 12-16 seconds
		for (Player p : arenaPlayers())
			p.sendMessage(Color.RED.wrap("Zalcano falls to the ground, hurt and vulnerable."));
	}

	private void rise(List<Player> players) {
		bossHp = npc.getHp();
		npc.transform(ARMOURED);
		npc.animate(STAND_UP_ANIM);
		npc.getCombat().getStat(StatType.Hitpoints).currentLevel = SHIELD_MAX;
		ticksToNextAttack = 6;
		for (Player p : players)
			p.sendMessage(Color.RED.wrap("Zalcano stands from the floor, ready to fight back."));
		for (Player p : players) {
			int destroyed = removeAll(p, TEPHRA) + removeAll(p, REFINED_TEPHRA) + removeAll(p, IMBUED_TEPHRA);
			if (p.getEquipment().getId(Equipment.SLOT_WEAPON) == IMBUED_TEPHRA) {
				p.getEquipment().remove(IMBUED_TEPHRA, p.getEquipment().get(Equipment.SLOT_WEAPON).getAmount());
				destroyed++;
			}
			if (destroyed > 0)
				p.sendMessage(Color.RED.wrap("Zalcano's shockwave destroys your tephra!"));
			if (isAdjacent(p.getPosition())) {
				p.hit(new Hit(HitType.DAMAGE).randDamage(5, 10));
				p.stun(1, true);
			}
		}
	}

	private boolean isAdjacent(Position p) {
		int x = p.getX(), y = p.getY(), bx = npc.getAbsX(), by = npc.getAbsY(), s = npc.getSize();
		return x >= bx - 1 && x <= bx + s && y >= by - 1 && y <= by + s;
	}

	private static int removeAll(Player p, int id) {
		int n = p.getInventory().count(id);
		if (n > 0)
			p.getInventory().remove(id, n);
		return n;
	}

	/* ------------------------------------------------------------------ attacks */

	private void boulders(List<Player> players) {
		npc.animate(BOULDER_ANIM);
		List<Position> tiles = new ArrayList<>();
		for (Player p : players) {
			tiles.add(p.getPosition().copy());
			for (int i = 0; i < 2; i++)
				tiles.add(p.getPosition().copy().translate(Random.get(-3, 3), Random.get(-3, 3), 0));
		}
		for (Position t : tiles)
			World.sendGraphics(BOULDER_GFX, 0, 0, t);
		npc.addEvent(e -> {
			e.delay(4);
			for (Player p : arenaPlayers())
				for (Position t : tiles)
					if (p.getPosition().equals(t)) {
						p.hit(new Hit(HitType.DAMAGE).randDamage(40, 50));
						break;
					}
		});
	}

	private void symbolsAttack(List<Player> players) {
		npc.animate(SYMBOL_ANIM);
		clearSymbols();
		int total = Random.get(12, 18), blue = Random.get(2, 5);
		Set<Position> used = new HashSet<>();
		for (int i = 0; i < total * 4 && used.size() < total; i++) {
			Player anchor = Random.get(players);
			Position t = anchor.getPosition().copy().translate(Random.get(-5, 5), Random.get(-5, 5), 0);
			if (!inArena(t) || underHer(t) || used.contains(t) || Tile.get(t, true).clipping != 0)
				continue;
			used.add(t);
			boolean isBlue = used.size() <= blue;
			symbols.add(GameObject.spawn(isBlue ? SYMBOL_BLUE : SYMBOL_ORANGE, t.getX(), t.getY(), 0, 22, 0));
			if (isBlue)
				blueTiles.add(t);
			else
				orangeTiles.put(t, (int) io.ruin.Server.currentTick() + 3);
		}
		npc.addEvent(e -> {
			e.delay(20);
			clearSymbols();
		});
	}

	private void processSymbols(List<Player> players) {
		if (orangeTiles.isEmpty())
			return;
		int now = (int) io.ruin.Server.currentTick();
		for (Player p : players) {
			Integer start = orangeTiles.get(p.getPosition());
			if (start == null || now < start)
				continue;
			int dmg = Math.min(26, 2 * (now - start) + 1); // grows the longer the symbol has been active
			p.hit(new Hit(HitType.DAMAGE).randDamage(1, dmg));
			p.forceText("Agh!"); // OSRS: players standing in a burning symbol cry out
			p.getMovement().drainEnergy(5);
		}
	}

	private void clearSymbols() {
		symbols.forEach(GameObject::remove);
		symbols.clear();
		orangeTiles.clear();
		blueTiles.clear();
	}

	private void spawnGolem() {
		npc.animate(GOLEM_ANIM);
		int[][] walls = { { ARENA_MIN_X + 2, ARENA_MIN_Y + 8 }, { ARENA_MAX_X - 2, ARENA_MIN_Y + 8 }, { ARENA_MIN_X + 2, ARENA_MAX_Y - 8 }, { ARENA_MAX_X - 2, ARENA_MAX_Y - 8 } };
		int[] w = Random.get(walls);
		Position spot = new Position(w[0], w[1], 0);
		int delay = GOLEM_BALL.send(npc, spot);
		npc.addEvent(e -> {
			e.delay(World.getTicks(delay) + 1);
			if (npc.getId() != ARMOURED || isDead())
				return;
			World.sendGraphics(GOLEM_SPAWN_GFX, 0, 0, spot);
			NPC g = golem = new NPC(GOLEM).spawn(spot.getX(), spot.getY(), 0, Direction.SOUTH, 0);
			g.deathEndListener = (entity, killer, killHit) -> {
				Player top = killer == null ? null : killer.player;
				if (top != null)
					new io.ruin.model.map.ground.GroundItem(new Item(IMBUED_TEPHRA, Random.get(16, 24))).owner(top).position(g.getPosition()).spawn();
				g.remove();
			};
			g.addEvent(ge -> {
				while (!g.isRemoved() && !g.getCombat().isDead()) {
					ge.delay(1);
					if (npc.getId() != ARMOURED || isDead()) {
						g.remove();
						return;
					}
					if (g.getPosition().isWithinDistance(npc.getCentrePosition(), npc.getSize() / 2 + 1)) {
						int heal = (int) Math.ceil(50.0 * g.getHp() / g.getMaxHp());
						npc.getCombat().getStat(StatType.Hitpoints).currentLevel = Math.min(SHIELD_MAX, npc.getHp() + heal);
						g.remove();
						return;
					}
					g.getRouteFinder().routeAbsolute(npc.getCentrePosition().getX(), npc.getCentrePosition().getY());
				}
			});
		});
	}

	private void removeGolem() {
		if (golem != null && !golem.isRemoved())
			golem.remove();
		golem = null;
	}

	private void rockBlast() {
		GameObject rock = rocks[glowing];
		if (rock == null)
			return;
		npc.animate(ROCK_BLAST_ANIM);
		Position target = rock.getPosition().copy();
		int delay = ROCK_BALL.send(npc, target);
		npc.addEvent(e -> {
			e.delay(World.getTicks(delay) + 1);
			for (Player p : arenaPlayers())
				if (Math.abs(p.getAbsX() - target.getX()) <= 2 && Math.abs(p.getAbsY() - target.getY()) <= 2)
					p.hit(new Hit(HitType.DAMAGE).randDamage(21, 28));
			int next;
			do {
				next = Random.get(3);
			} while (next == glowing);
			setGlowing(next);
		});
	}

	private void pebbles(List<Player> players) {
		npc.animate(PEBBLE_ANIM);
		for (Player p : players) {
			int delay = PEBBLE.send(npc, p);
			p.hit(new Hit(HitType.DAMAGE).randDamage(1, 5).clientDelay(delay));
		}
	}

	/* ------------------------------------------------------------------ player actions */

	private static ZalcanoCombat of(NPC npc) {
		return npc == null ? null : npc.get("ZALCANO");
	}

	private static boolean hasTephra(Player p) {
		return p.getInventory().contains(IMBUED_TEPHRA, 1) || p.getEquipment().getId(Equipment.SLOT_WEAPON) == IMBUED_TEPHRA;
	}

	private static void consumeTephra(Player p) {
		if (p.getInventory().contains(IMBUED_TEPHRA, 1))
			p.getInventory().remove(IMBUED_TEPHRA, 1);
		else
			p.getEquipment().remove(IMBUED_TEPHRA, 1);
	}

	/** "Attack" on the armoured form throws imbued tephra (it doesn't need to be equipped). */
	public static void throwTephra(Player player, NPC npc) {
		ZalcanoCombat z = of(npc);
		if (z == null)
			return;
		if (!hasTephra(player)) {
			player.sendMessage("You need to bring Zalcano down before you can do any damage to her.");
			return;
		}
		player.startEvent(e -> {
			while (true) {
				if (npc.isRemoved() || npc.getId() != ARMOURED || z.isDead())
					return;
				if (!hasTephra(player))
					return;
				if (!player.getPosition().isWithinDistance(npc.getCentrePosition(), 8)) {
					player.getRouteFinder().routeEntity(npc);
					e.delay(1);
					continue;
				}
				player.getMovement().reset();
				player.face(npc);
				player.animate(THROW_ANIM);
				consumeTephra(player);
				if (!hasTephra(player))
					player.sendMessage("That was your last one!");
				int smith = player.getStats().get(StatType.Smithing).currentLevel, rc = player.getStats().get(StatType.Runecrafting).currentLevel;
				int max = 5 + (smith + rc) / 14;
				if (z.blueTiles.contains(player.getPosition()))
					max = (int) Math.round(max * 1.5);
				int delay = TEPHRA_THROW.send(player, npc);
				Hit hit = new Hit(player, null).randDamage(max / 2, max).clientDelay(delay).ignoreDefence().ignorePrayer();
				hit.postDamage(t -> npc.graphics(ORE_HIT_GFX)); // zalcano_ore_hit
				z.ownHits.add(hit);
				npc.hit(hit);
				e.delay(4);
			}
		});
	}

	private static int miningMaxHit(Player p, Pickaxe pickaxe) {
		int level = p.getStats().get(StatType.Mining).currentLevel + Math.max((pickaxe.levelReq - 40) / 15, 0);
		return level >= 95 ? 33 : level >= 85 ? 32 : level >= 75 ? 31 : 30;
	}

	private static void mineZalcano(Player player, NPC npc) {
		ZalcanoCombat z = of(npc);
		if (z == null)
			return;
		Pickaxe pickaxe = Pickaxe.find(player);
		if (pickaxe == null) {
			player.sendMessage("You need a pickaxe to mine Zalcano.");
			return;
		}
		player.startEvent(e -> {
			while (!npc.isRemoved() && npc.getId() == WEAKENED && !z.isDead()) {
				if (!z.isAdjacent(player.getPosition()) || z.underHer(player.getPosition())) {
					player.getRouteFinder().routeEntity(npc);
					e.delay(1);
					continue;
				}
				player.face(npc);
				player.animate(pickaxe.regularAnimationID);
				e.delay(3);
				if (npc.getId() != WEAKENED || z.isDead())
					return;
				Hit hit = new Hit(player, null).randDamage(1, miningMaxHit(player, pickaxe)).ignoreDefence().ignorePrayer();
				z.ownHits.add(hit);
				npc.hit(hit);
				player.getStats().addXp(StatType.Mining, 35.2, true);
			}
		});
	}

	private static void mineRock(Player player, GameObject rock) {
		Pickaxe pickaxe = Pickaxe.find(player);
		if (pickaxe == null) {
			player.sendMessage("You need a pickaxe to mine the rock formation.");
			return;
		}
		player.startEvent(e -> {
			while (rock.getId() == ROCK_GLOWING || rock.getId() == ROCK_NORMAL) {
				if (player.getInventory().isFull() && !player.getInventory().contains(TEPHRA, 1)) {
					player.sendMessage("Your inventory is too full to hold any more tephra.");
					return;
				}
				player.animate(pickaxe.regularAnimationID);
				e.delay(3);
				boolean glowing = rock.getId() == ROCK_GLOWING;
				if (Random.get() < (glowing ? 0.75 : 0.2)) {
					player.getInventory().add(TEPHRA, 1);
					player.getStats().addXp(StatType.Mining, glowing ? 9 : 4, true);
				}
			}
		});
	}

	private static void convert(Player player, int from, int to, StatType skill, String verb) {
		if (!player.getInventory().contains(from, 1)) {
			player.sendMessage("You have nothing to " + verb + ".");
			return;
		}
		player.startEvent(e -> {
			while (player.getInventory().contains(from, 1)) {
				player.animate(skill == StatType.Smithing ? 899 : 791);
				e.delay(2);
				int n = Math.min(player.getInventory().count(from), 3);
				player.getInventory().remove(from, n);
				player.getInventory().add(to, n);
				player.getStats().addXp(skill, 4.0 * n, true);
			}
		});
	}

	/* ------------------------------------------------------------------ death + rewards */

	@Override
	public void startDeath(Hit killHit) {
		if (npc.getId() == WEAKENED)
			distributeRewards();
		hud.closeAll();
		for (Player p : arrowShown.keySet())
			if (p.isOnline())
				p.getPacketSender().resetHintIcon(false);
		arrowShown.clear();
		clearSymbols();
		removeGolem();
		super.startDeath(killHit);
	}

	/** {item, weight /36, min, max} - OSRS main reward table (ores/bars noted) */
	private static final int[][] MAIN_TABLE = {
		{ 565, 1, 64, 480 }, { 564, 1, 295, 926 }, { 560, 1, 201, 1046 }, { 563, 1, 166, 790 }, { 566, 1, 47, 392 }, { 561, 1, 1, 937 },
		{ 443, 3, 86, 800 }, { 454, 1, 169, 1052 }, { 448, 2, 56, 469 }, { 450, 2, 51, 343 }, { 452, 2, 3, 31 }, { 445, 3, 86, 756 },
		{ 2354, 3, 16, 699 }, { 2360, 3, 53, 501 }, { 2362, 2, 13, 111 }, { 2364, 2, 3, 27 }, { 1618, 2, 2, 21 }, { 1632, 2, 1, 12 },
		{ 9194, 2, 1, 45 }, { 7937, 1, 784, 5500 },
	};

	private void distributeRewards() {
		if (contribution.isEmpty())
			return;
		int mvp = -1, best = -1;
		for (Map.Entry<Integer, int[]> e : contribution.entrySet()) {
			int pm = e.getValue()[0] + 2 * e.getValue()[1];
			if (pm > best) {
				best = pm;
				mvp = e.getKey();
			}
		}
		// crystal tool seed table: one roll per kill, given to an eligible player weighted by tertiary points
		Player seedWinner = null;
		if (Random.get(199) == 0) {
			int total = 0;
			for (Map.Entry<Integer, int[]> e : contribution.entrySet())
				if (e.getValue()[0] + e.getValue()[1] >= 31)
					total += tertiaryPoints(e.getValue());
			int roll = total > 0 ? Random.get(total - 1) : -1;
			for (Map.Entry<Integer, int[]> e : contribution.entrySet()) {
				if (e.getValue()[0] + e.getValue()[1] < 31)
					continue;
				roll -= tertiaryPoints(e.getValue());
				if (roll < 0) {
					seedWinner = contributors.get(e.getKey());
					break;
				}
			}
		}
		// OSRS: everyone in the arena who fell short of the armour-damage threshold is told so
		for (Player p : arenaPlayers()) {
			int[] c = contribution.get(p.getUserId());
			if (c == null || c[1] < 5)
				p.sendMessage("You did not deal enough damage to Zalcano to be worthy of a reward.");
		}
		for (Map.Entry<Integer, int[]> e : contribution.entrySet()) {
			Player p = contributors.get(e.getKey());
			int[] c = e.getValue();
			if (p == null || !p.isOnline() || !inArena(p.getPosition()) || c[1] < 5)
				continue;
			boolean isMvp = e.getKey() == mvp;
			boolean uniqueEligible = c[0] + c[1] >= 31;
			double f = Math.min(1.0, tertiaryPoints(c) / 1000.0);
			List<Item> loot = new ArrayList<>();
			int[] row = rollMain();
			int qty = row[2] + (int) Math.round((row[3] - row[2]) * f);
			if (isMvp)
				qty = (int) Math.ceil(qty * 1.1);
			loot.add(new Item(row[0], Math.max(1, qty)));
			loot.add(new Item(23962, isMvp ? 3 : uniqueEligible ? 2 : 1)); // crystal shards
			if (isMvp)
				loot.add(new Item(25778, 1)); // infernal ashes
			if (uniqueEligible) {
				int shardRate = (int) Math.round(1500 - 750 * f);
				if (Random.get(shardRate - 1) == 0)
					loot.add(broadcast(new Item(23908, 1)));
				if (p == seedWinner)
					loot.add(broadcast(new Item(Random.get(39) == 0 ? 6571 : 23953, 1)));
				if (Random.get(2249) == 0)
					Pet.SMOLCANO.unlock(p, 0);
			}
			handleDrop(p, p.getPosition(), loot);
			p.zalcanoKills.increment(p);
		}
	}

	private static int tertiaryPoints(int[] c) {
		return Math.min(c[0], 400) + 2 * Math.min(c[1], 300);
	}

	private static Item broadcast(Item item) {
		item.lootBroadcast = Broadcast.GLOBAL;
		return item;
	}

	private static int[] rollMain() {
		int total = 0;
		for (int[] r : MAIN_TABLE)
			total += r[1];
		int roll = Random.get(total - 1);
		for (int[] r : MAIN_TABLE) {
			roll -= r[1];
			if (roll < 0)
				return r;
		}
		return MAIN_TABLE[0];
	}

	/* ------------------------------------------------------------------ registration */

	public static void register() {
		ObjectAction.register(ROCK_GLOWING, "Mine", ZalcanoCombat::mineRock);
		ObjectAction.register(ROCK_NORMAL, "Mine", ZalcanoCombat::mineRock);
		ObjectAction.register(FURNACE, "Refine", (p, o) -> convert(p, TEPHRA, REFINED_TEPHRA, StatType.Smithing, "refine"));
		ObjectAction.register(ALTAR, "Imbue", (p, o) -> convert(p, REFINED_TEPHRA, IMBUED_TEPHRA, StatType.Runecrafting, "imbue"));
		NPCAction.register(WEAKENED, "Mine", ZalcanoCombat::mineZalcano);
	}
}
