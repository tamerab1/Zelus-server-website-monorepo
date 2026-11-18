package io.ruin.model.activities.bosses.obor;

import io.ruin.api.utils.Random;
import io.ruin.model.World;
import io.ruin.model.combat.AttackStyle;
import io.ruin.model.combat.Hit;
import io.ruin.model.entity.npc.NPC;
import io.ruin.model.entity.npc.NPCCombat;
import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.dialogue.MessageDialogue;
import io.ruin.model.item.Item;
import io.ruin.model.item.actions.ItemObjectAction;
import io.ruin.model.map.Direction;
import io.ruin.model.map.Position;
import io.ruin.model.map.Tile;
import io.ruin.model.map.dynamic.DynamicMap;
import io.ruin.model.map.ground.GroundItem;
import io.ruin.model.map.object.actions.ObjectAction;
import io.ruin.model.skills.prayer.Prayer;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * Obor, the hill giant boss (npc 7416, size 2, 120 hp, attack speed 6), in a private copy of his lair (region 12441,
 * Edgeville Dungeon). Built from the OSRS wiki ("Obor", "Gate (Obor)", "Chest (Obor's lair)", "Giant bones", 2026-11-14)
 * and the rev 241 cache (anims GIANT_BASIC_STAMP 7183 / GIANT_UPDATE_BASIC_ATTACK 4652, spotanims EARTHBLAST_IMPACT 140 +
 * ROCKFALL 60).
 * <ul>
 * <li>Gate 29486/29487 (3095,9832-3): a giant key unlocks it the first time for good and isn't used up. Exit 29488/29489
 * Open / Quick-exit.</li>
 * <li>Attacks: ranged (max 26, halved by Protect from Missiles) and melee (crush, max 22, 25% knockback); which one and
 * whether he walks up follow the wiki's prayer / distance rules ({@link #attack()}).</li>
 * <li>Climbing rocks 29491 lead down to him. The chest 56380 opens once per kill with a giant key (used up).</li>
 * <li>Giant bones (always) are buried from the floor for 150 Prayer xp and can't be picked up.</li>
 * </ul>
 */
@Slf4j
public class Obor extends NPCCombat {

	public static final int OBOR = 7416, GIANT_KEY = 20754, HILL_GIANT_CLUB = 20756, GIANT_BONES = 30898;
	private static final int ENTRANCE_R = 29486, ENTRANCE_L = 29487, EXIT_R = 29488, EXIT_L = 29489, ROCKS = 29491, CHEST = 56380;
	private static final int REGION = 12441;
	private static final int ANIM_STAMP = 7183, ANIM_MELEE = 4652, GFX_EARTH = 140, GFX_ROCKFALL = 60, CLIMB = 740;
	private static final Position LANDING = new Position(3092, 9814, 0), SPAWN = new Position(3091, 9799, 0); // wiki LocLine centre 3091.5,9799 (size 2)
	public static final Position OUTSIDE = new Position(3096, 9832, 0);

	private static final class Lair {
		DynamicMap map;
		Player player;
		boolean chestReady;
	}

	private static final Map<Integer, Lair> LAIRS = new HashMap<>();

	Lair lair;

	public static void register() {
		for (int gate : new int[] { ENTRANCE_R, ENTRANCE_L }) {
			ObjectAction.register(gate, 1, (p, obj) -> enter(p));
			ItemObjectAction.register(GIANT_KEY, gate, (p, item, obj) -> enter(p));
		}
		for (int door : new int[] { EXIT_R, EXIT_L }) {
			ObjectAction.register(door, 1, (p, obj) -> leave(p, false)); // Open
			ObjectAction.register(door, 2, (p, obj) -> leave(p, true));  // Quick-exit
		}
		ObjectAction.register(ROCKS, 1, (p, obj) -> climb(p));
		ObjectAction.register(CHEST, 1, (p, obj) -> openChest(p));
		ItemObjectAction.register(GIANT_KEY, CHEST, (p, item, obj) -> openChest(p));
		// Giant bones: buried straight from the floor (150 Prayer xp); they won't go in a backpack
		io.ruin.model.map.ground.GroundItemAction.register(GIANT_BONES, 4,
			(p, g, distance) -> p.sendMessage("I don't think those will fit in my backpack..."));
		io.ruin.model.map.ground.GroundItemAction.register(GIANT_BONES, 3, (p, g, distance) -> {
			if (g.isRemoved())
				return;
			g.remove();
			p.animate(827);
			p.getStats().addXp(io.ruin.model.stat.StatType.Prayer, 150, true);
			p.sendFilteredMessage("You bury the bones.");
		});
	}

	/* ------------------------------------------------------------------ lair */

	/**
	 * The gate (wiki "Gate (Obor)"): a giant key unlocks it the first time, permanently, and isn't used up. After that it
	 * opens without a key. NOTE: the in-game warning / no-key texts aren't on the wiki; the message below is ours.
	 */
	private static void enter(Player p) {
		if (!p.oborUnlocked) {
			if (!p.getInventory().contains(GIANT_KEY, 1)) {
				p.sendMessage("The gate is locked. You'll need a giant key to open it.");
				return;
			}
			p.oborUnlocked = true;
			p.sendMessage("You unlock the gate with your giant key.");
		}
		DynamicMap map;
		try {
			map = new DynamicMap().build(REGION, 1);
		} catch (DynamicMap.DynamicMapBuildException e) {
			log.error("Unable to build Obor's lair", e);
			p.sendMessage("The lair is currently unstable. Try again in a moment.");
			return;
		}
		Lair l = new Lair();
		l.map = map;
		l.player = p;
		LAIRS.put(p.getUserId(), l);
		p.startEvent(e -> {
			p.lock();
			p.getPacketSender().fadeOut();
			e.delay(2);
			p.getMovement().teleport(map.convertX(LANDING.getX()), map.convertY(LANDING.getY()), 0);
			map.assignListener(p).onExit((pl, logout) -> {
				if (logout)
					pl.getMovement().teleport(OUTSIDE);
				LAIRS.remove(pl.getUserId());
				map.destroy();
			});
			p.face(Direction.SOUTH);
			p.getPacketSender().fadeIn();
			spawnBoss(l);
			p.unlock();
		});
	}

	private static void spawnBoss(Lair l) {
		if (l.map == null || l.player == null || LAIRS.get(l.player.getUserId()) != l)
			return;
		NPC obor = new NPC(OBOR).spawn(l.map.convertX(SPAWN.getX()), l.map.convertY(SPAWN.getY()), 0, Direction.NORTH, 3);
		l.map.addNpc(obor);
		((Obor) obor.getCombat()).lair = l;
	}

	private static void leave(Player p, boolean quick) {
		if (quick) {
			p.getMovement().teleport(OUTSIDE);
			return;
		}
		p.startEvent(e -> {
			p.lock();
			p.getPacketSender().fadeOut();
			e.delay(2);
			p.getMovement().teleport(OUTSIDE);
			p.getPacketSender().fadeIn();
			p.unlock();
		});
	}

	private static void climb(Player p) {
		p.startEvent(e -> {
			p.lock();
			p.face(Direction.NORTH);
			p.animate(CLIMB);
			p.getMovement().force(0, -3, 0, 0, 0, 30, Direction.NORTH);
			e.delay(2);
			p.resetAnimation();
			p.unlock();
		});
	}

	private static void openChest(Player p) {
		Lair l = LAIRS.get(p.getUserId());
		if (l == null || !l.chestReady) {
			p.sendMessage("You need to defeat Obor before you can open this chest.");
			return;
		}
		if (!p.getInventory().contains(GIANT_KEY, 1)) {
			p.sendMessage("You need a giant key to open this chest.");
			return;
		}
		p.getInventory().remove(GIANT_KEY, 1);
		l.chestReady = false;
		p.animate(536);
		for (Item item : rollChest()) {
			if (p.getInventory().hasRoomFor(item.getId(), item.getAmount()))
				p.getInventory().add(item.getId(), item.getAmount());
			else
				new GroundItem(item).owner(p).position(p.getPosition()).spawn();
			p.addToCollectionLog(item);
			if (item.getId() == HILL_GIANT_CLUB)
				io.ruin.utility.Broadcast.WORLD.sendNewsDropMessage(p, io.ruin.cache.Icon.RARE_DROP, "<col=000000>" + p.getName(),
					" just received a Hill giant club from Obor's chest!");
		}
		p.dialogue(new MessageDialogue("You find some treasure inside the chest."));
	}

	/* ------------------------------------------------------------------ fight */

	@Override
	public void init() {
	}

	@Override
	public void process() {
	}

	/** whether he's walking up to the player (decided with each attack, wiki "Obor" mechanics) */
	private boolean approaching = true;

	@Override
	public void follow() {
		// he only closes in when he has decided to approach; otherwise he holds his ground and keeps throwing
		follow(approaching ? 1 : 8);
	}

	/**
	 * The wiki's attack rules:
	 * <ul>
	 * <li>Not adjacent: ranged. With Protect from Missiles on he approaches; without it, a 1/3 chance he approaches.</li>
	 * <li>Adjacent with Protect from Melee: 50% melee / 50% ranged. Adjacent with Protect from Missiles: 1/3 ranged,
	 * otherwise melee. (Adjacent with neither: not stated; melee.)</li>
	 * </ul>
	 */
	@Override
	public boolean attack() {
		if (!target.isPlayer() || !withinDistance(8))
			return false;
		Player p = target.player;
		boolean missiles = p.getPrayer().isActive(Prayer.PROTECT_FROM_MISSILES), melee = p.getPrayer().isActive(Prayer.PROTECT_FROM_MELEE);
		boolean ranged;
		if (!withinDistance(1)) {
			ranged = true;
			approaching = missiles || Random.get(2) == 0;
		} else if (melee) {
			ranged = Random.get(1) == 0;
		} else if (missiles) {
			ranged = Random.get(2) == 0;
		} else {
			ranged = false;
		}
		if (ranged)
			stamp(p);
		else
			knockback(p);
		return true;
	}

	/** ranged (max 26); Protect from Missiles halves it rather than blocking it */
	private void stamp(Player p) {
		npc.animate(ANIM_STAMP);
		Position tile = p.getPosition().copy();
		World.sendGraphics(GFX_EARTH, 0, 60, tile);
		World.sendGraphics(GFX_ROCKFALL, 0, 60, tile);
		Hit hit = new Hit(npc, AttackStyle.RANGED).randDamage(26).delay(2).ignorePrayer();
		if (p.getPrayer().isActive(Prayer.PROTECT_FROM_MISSILES))
			hit.boostDamage(-0.5);
		p.hit(hit);
	}

	/** melee (crush, max 22); a 25% chance to knock the player back (NOTE: distance not on the wiki; up to 3 tiles) */
	private void knockback(Player p) {
		npc.animate(ANIM_MELEE);
		if (Random.get(3) != 0) {
			p.hit(new Hit(npc, AttackStyle.CRUSH).randDamage(22).delay(2));
			return;
		}
		int dx = Integer.signum(p.getAbsX() - (npc.getAbsX() + 1)), dy = Integer.signum(p.getAbsY() - (npc.getAbsY() + 1));
		if (dx == 0 && dy == 0)
			dy = -1;
		int tiles = 0;
		int x = p.getAbsX(), y = p.getAbsY(), z = p.getHeight();
		while (tiles < 3) {
			Tile t = Tile.get(x + dx, y + dy, z);
			if (t == null || !t.isTileFree())
				break;
			x += dx;
			y += dy;
			tiles++;
		}
		if (tiles > 0 && !p.isLocked()) {
			p.getMovement().reset();
			p.getMovement().force(dx * tiles, dy * tiles, 0, 0, 0, 30, Direction.getDirection(-dx, -dy));
		}
		// wiki: a knocked-back player has a 1/3 chance to be knocked down too (human_knockdown 7210)
		if (Random.get(2) == 0 && !p.isLocked()) {
			p.animate(7210);
			p.stun(2, true);
		}
		p.hit(new Hit(npc, AttackStyle.CRUSH).randDamage(22).delay(2));
	}

	@Override
	public void startDeath(Hit killHit) {
		Lair l = lair;
		Player p = l == null ? null : l.player;
		Position at = npc.getPosition().copy();
		super.startDeath(killHit);
		if (p == null)
			return;
		for (Item item : rollKill()) {
			new GroundItem(item).owner(p).position(at).spawn();
			p.addToCollectionLog(item);
		}
		l.chestReady = true;
		World.startEvent(e -> {
			e.delay(50);
			spawnBoss(l);
		});
	}

	/* ------------------------------------------------------------------ loot (OSRS wiki tables) */

	/** OSRS wiki main table (id, min, max, chance), giant key 1/16 rolled first; then tertiaries. */
	private static final double[][] KILL = {{1153,1,1,1.0/27}, {1203,1,1,4.0/135}, {1191,1,1,1.0/45}, {1295,1,1,2.0/135}, {884,3,3,2.0/45}, {554,15,15,1.0/45}, {555,7,7,1.0/45}, {563,2,2,1.0/45}, {886,10,10,2.0/135}, {558,3,3,2.0/135}, {564,2,2,2.0/135}, {561,6,6,2.0/135}, {562,2,2,1.0/135}, {560,2,2,1.0/135}, {199,1,1,10.0/771}, {201,1,1,10.0/1029}, {203,1,1,10.0/1371}, {205,1,1,10.0/1763}, {207,1,1,5.0/1122}, {209,1,1,5.0/1543}, {211,1,1,5.0/2057}, {213,1,1,10.0/4937}, {215,1,1,10.0/6171}, {2485,1,1,10.0/8229}, {217,1,1,10.0/8229}, {5318,4,4,1.0/44}, {5319,4,4,1.0/59}, {5324,4,4,1.0/89}, {5322,3,3,1.0/179}, {5320,3,3,1.0/359}, {5323,2,2,1.0/718}, {5321,2,2,1.0/1502}, {22879,2,2,1.0/2065}, {5305,4,4,1.0/142}, {5307,3,3,1.0/143}, {5308,3,3,1.0/190}, {5306,2,2,1.0/191}, {5309,2,2,1.0/286}, {5310,2,2,1.0/573}, {5311,1,1,1.0/1128}, {5096,1,1,1.0/173}, {5098,1,1,1.0/261}, {5097,1,1,1.0/404}, {5099,1,1,1.0/547}, {5100,1,1,1.0/685}, {5101,1,1,1.0/204}, {5102,1,1,1.0/292}, {5103,1,1,1.0/409}, {5104,1,1,1.0/1024}, {5105,1,1,1.0/2826}, {5106,1,1,1.0/7450}, {5291,1,1,1.0/517}, {5292,1,1,1.0/759}, {5293,1,1,1.0/1111}, {5294,1,1,1.0/1639}, {5295,1,1,1.0/2400}, {5296,1,1,1.0/3523}, {5297,1,1,1.0/5175}, {5298,1,1,1.0/7528}, {5299,1,1,1.0/11041}, {5300,1,1,1.0/16562}, {5301,1,1,1.0/23660}, {5302,1,1,1.0/33125}, {5303,1,1,1.0/55208}, {5304,1,1,1.0/82812}, {5282,1,1,1.0/269}, {5281,1,1,1.0/448}, {5280,1,1,1.0/672}, {22873,1,1,1.0/1345}, {995,38,38,14.0/135}, {995,52,52,2.0/27}, {995,15,15,8.0/135}, {995,8,8,2.0/45}, {995,88,88,2.0/135}, {225,1,1,11.0/135}, {1917,1,1,2.0/45}, {1446,1,1,2.0/135}, {1623,1,1,1.0/180}, {1621,1,1,1.0/360}, {1619,1,1,1.0/720}, {1462,1,1,1.0/1920}, {1617,1,1,1.0/2880}, {830,5,5,1.0/5760}, {987,1,1,1.0/5760}, {985,1,1,1.0/5760}, {1247,1,1,1.0/92160}, {2366,1,1,1.0/184320}, {1249,1,1,1.0/245760}};
	private static final double[][] KILL_TERTIARY = {{13475,1,1,1.0/25}, {23182,1,1,1.0/50}, {10976,1,1,1.0/400}, {6800,1,1,1.0/5000}, {10977,1,1,2.0/10025}};

	/** id, min, max, weight /118, noted */
	private static final int[][] CHEST_TABLE = {
		{ HILL_GIANT_CLUB, 1, 1, 1, 0 },
		{ 1201, 1, 1, 3, 0 }, { 1163, 1, 1, 5, 0 }, { 1147, 1, 1, 6, 0 }, { 1113, 1, 1, 3, 0 }, { 1079, 1, 1, 3, 0 },
		{ 1093, 1, 1, 3, 0 }, { 1373, 1, 1, 4, 0 }, { 1303, 1, 1, 5, 0 }, { 1319, 1, 1, 3, 0 },
		{ 563, 50, 99, 12, 0 }, { 564, 60, 119, 12, 0 }, { 562, 100, 199, 10, 0 }, { 560, 40, 79, 10, 0 }, { 561, 40, 79, 7, 0 },
		{ 995, 10000, 15000, 6, 0 }, { 995, 12000, 20000, 4, 0 }, { 225, 20, 20, 8, 1 }, { 532, 50, 50, 8, 1 },
		{ 1619, 5, 5, 5, 1 }, { 1617, 5, 5, 5, 1 } };

	private static Item pick(int[][] table) {
		int total = 0;
		for (int[] e : table)
			total += e[3];
		int roll = Random.get(0, total - 1);
		for (int[] e : table) {
			roll -= e[3];
			if (roll < 0) {
				int id = e[0];
				if (e[4] == 1 && io.ruin.cache.ObjType.get(id).notedId != -1)
					id = io.ruin.cache.ObjType.get(id).notedId;
				return new Item(id, Random.get(e[1], e[2]));
			}
		}
		return null;
	}

	private static List<Item> rollKill() {
		List<Item> loot = new ArrayList<>();
		loot.add(new Item(30898, 1)); // giant bones (the always drop)
		if (Random.get(1, 16) == 1)
			loot.add(new Item(GIANT_KEY, 1));
		else {
			double r = Random.get(), acc = 0;
			for (double[] e : KILL) {
				acc += e[3] * 16.0 / 15.0; // the rest of the table, scaled to the non-key share
				if (r < acc) {
					loot.add(new Item((int) e[0], Random.get((int) e[1], (int) e[2])));
					break;
				}
			}
		}
		for (double[] e : KILL_TERTIARY)
			if (Random.get() < e[3])
				loot.add(new Item((int) e[0], Random.get((int) e[1], (int) e[2])));
		return loot;
	}

	private static List<Item> rollChest() {
		List<Item> loot = new ArrayList<>();
		loot.add(new Item(23182, 1)); // clue scroll (beginner)
		loot.add(new Item(13475, 1)); // ensouled giant head
		Item i = pick(CHEST_TABLE);
		if (i != null)
			loot.add(i);
		if (Random.get(1, 16) == 1)
			loot.add(new Item(GIANT_KEY, 1));
		return loot;
	}
}
