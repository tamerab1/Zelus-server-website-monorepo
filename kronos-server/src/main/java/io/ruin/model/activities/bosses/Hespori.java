package io.ruin.model.activities.bosses;

import io.ruin.api.utils.Random;
import io.ruin.cache.Color;
import io.ruin.model.World;
import io.ruin.model.combat.AttackStyle;
import io.ruin.model.combat.Hit;
import io.ruin.model.combat.HitType;
import io.ruin.model.entity.npc.NPC;
import io.ruin.model.entity.npc.NPCCombat;
import io.ruin.model.entity.player.Player;
import io.ruin.model.entity.shared.listeners.HitListener;
import io.ruin.model.item.Item;
import io.ruin.model.item.actions.impl.pet.Pet;
import io.ruin.model.map.Direction;
import io.ruin.model.map.Position;
import io.ruin.model.map.Projectile;
import io.ruin.model.map.object.actions.ObjectAction;
import io.ruin.model.skills.farming.patch.Patch;
import io.ruin.model.skills.prayer.Prayer;
import io.ruin.model.stat.StatType;

import java.util.ArrayList;
import java.util.List;

/**
 * Hespori (Farming Guild west-wing cave), OSRS 1:1. Harvesting a fully grown Hespori patch wakes it; it fights only
 * the player who grew it. Four flower buds bloom at the start, at 66% and at 33% HP, and Hespori is immune while any
 * bud is open (each bud dies to a single hit). Attacks every 6 ticks: two ranged spores (8 + 8, 2 + 2 protected) or a
 * magic wave (14, 3 protected), may poison, and sometimes entangles the player in vines: click to walk to struggle
 * free (5 struggles) or take a heavy hit when the vines snap. Killing it clears the patch and gives the OSRS loot:
 * one anima seed roll, one main seed-table roll, the 1/35 bottomless compost bucket and the Tangleroot roll.
 * <p>
 * Ids, projectiles and flower tiles match Near Reality's capture (Kris) and the OSRS wiki.
 */
public class Hespori extends NPCCombat {

	public static final int HESPORI = 8583, FLOWER_BLOOMING = 8584, FLOWER_STILL = 8585;
	private static final Position PATCH = new Position(1246, 10086, 0);
	private static final Position CAVE_INSIDE = new Position(1243, 10081, 0), CAVE_OUTSIDE = new Position(1230, 3729, 0);
	private static final int[][] FLOWER_TILES = { { 1243, 10083 }, { 1243, 10091 }, { 1251, 10091 }, { 1251, 10083 } };

	private static final int SPAWN_ANIM = 8221, MAGIC_ANIM = 8223, RANGED_ANIM = 8224, BLOOM_ANIM = 8229, STILL_ANIM = 8230;
	private static final Projectile SPORE_1 = new Projectile(1639, 24, 14, 30, 11, 25, 64, 5);
	private static final Projectile SPORE_2 = new Projectile(1639, 24, 14, 55, 11, 25, 64, 5);
	private static final Projectile MAGIC = new Projectile(1640, 85, 39, 90, 11, 25, 64, 5);
	private static final Projectile SNARE = new Projectile(1642, 85, 39, 90, 11, 25, 64, 5);
	private static final int MAGIC_HIT_GFX = 1641, SNARE_HIT_GFX = 1643;

	public static final String ENTANGLE_KEY = "HESPORI_ENTANGLE";

	private Player owner;
	private Patch patch;
	private final List<NPC> flowers = new ArrayList<>();
	private boolean bloomedAt66 = false, bloomedAt33 = false;

	/* ------------------------------------------------------------------ start */

	/** Called from the grown patch's harvest option. */
	public static void start(Player player, Patch patch) {
		if (player.get("HESPORI_ACTIVE") != null) {
			player.sendMessage("The Hespori is already awake!");
			return;
		}
		NPC npc = new NPC(HESPORI).spawn(PATCH.getX(), PATCH.getY(), PATCH.getZ(), Direction.SOUTH, 0);
		npc.ownerId = player.getUserId();
		npc.isMovementBlocked(false, true);
		Hespori combat = (Hespori) npc.getCombat();
		combat.owner = player;
		combat.patch = patch;
		player.set("HESPORI_ACTIVE", npc);
		player.sendMessage("The Hespori doesn't take kindly to your attempt to harvest it.");
		npc.animate(SPAWN_ANIM);
		for (int[] t : FLOWER_TILES) {
			NPC flower = new NPC(FLOWER_STILL).spawn(t[0], t[1], 0, Direction.SOUTH, 0);
			flower.ownerId = player.getUserId();
			flower.isMovementBlocked(false, true);
			// A bud closes on any single hit (OSRS: 10 HP, every hit deals 10). It never actually dies: its HP pool is
			// large and it's restored, so it can bloom again at the 66% / 33% thresholds.
			flower.hitListener = new HitListener().preDamage(hit -> {
				hit.damage = flower.getId() == FLOWER_BLOOMING ? 10 : 0;
			}).postDamage(hit -> {
				if (flower.getId() != FLOWER_BLOOMING)
					return;
				flower.getCombat().restore();
				flower.getCombat().reset();
				flower.animate(STILL_ANIM);
				flower.transform(FLOWER_STILL);
				if (!combat.isImmune())
					player.sendMessage(Color.DARK_GREEN.wrap("The last flower closes. The Hespori is vulnerable!"));
			});
			combat.flowers.add(flower);
		}
		combat.bloom();
		npc.lock();
		npc.addEvent(e -> {
			e.delay(2);
			npc.unlock();
			npc.targetPlayer(player, false);
			combat.setTarget(player);
		});
		combat.watchOwner();
	}

	/** Ends the encounter cleanly if the owner leaves, logs out, dies or teleports. */
	private void watchOwner() {
		npc.addEvent(e -> {
			while (!npc.isRemoved()) {
				e.delay(2);
				if (owner == null || !owner.isOnline() || owner.getCombat().isDead() || owner.getHeight() != npc.getHeight()
					|| !owner.getPosition().isWithinDistance(npc.getPosition(), 20)) {
					despawn();
					return;
				}
			}
		});
	}

	private void despawn() {
		flowers.forEach(NPC::remove);
		flowers.clear();
		if (owner != null) {
			owner.remove("HESPORI_ACTIVE");
			owner.remove(ENTANGLE_KEY);
		}
		if (!npc.isRemoved())
			npc.remove();
	}

	private void bloom() {
		for (NPC f : flowers) {
			f.animate(BLOOM_ANIM);
			f.transform(FLOWER_BLOOMING);
		}
	}

	boolean isImmune() {
		for (NPC f : flowers)
			if (f.getId() == FLOWER_BLOOMING)
				return true;
		return false;
	}

	/* ------------------------------------------------------------------ combat */

	@Override
	public void init() {
		npc.hitListener = new HitListener().preDefend(hit -> {
			if (isImmune()) {
				hit.block();
				if (hit.attacker != null && hit.attacker.player != null)
					hit.attacker.player.sendMessage("The Hespori is currently immune to your attacks.");
			}
		}).postDamage(hit -> {
			double r = (double) npc.getHp() / npc.getMaxHp();
			if (npc.getHp() > 0 && (!bloomedAt66 && r <= 2.0 / 3.0 || !bloomedAt33 && r <= 1.0 / 3.0)) {
				if (r <= 1.0 / 3.0)
					bloomedAt33 = true;
				bloomedAt66 = true;
				bloom();
			}
		});
	}

	@Override
	public void follow() {
		// rooted in the patch
	}

	@Override
	public void process() {
	}

	@Override
	public boolean attack() {
		if (target == null || target.player == null || target.player != owner)
			return false;
		Player p = target.player;
		if (Random.get(2) == 0)
			p.poison(4);
		if (Random.get(17) == 0 && p.get(ENTANGLE_KEY) == null) {
			entangle(p);
			return true;
		}
		if (Random.get(1) == 0) {
			npc.animate(RANGED_ANIM);
			boolean prayed = p.getPrayer().isActive(Prayer.PROTECT_FROM_MISSILES);
			for (Projectile proj : new Projectile[] { SPORE_1, SPORE_2 }) {
				int delay = proj.send(npc, p);
				p.hit(new Hit(npc, AttackStyle.RANGED).randDamage(prayed ? 2 : 8).clientDelay(delay).ignorePrayer());
			}
		} else {
			npc.animate(MAGIC_ANIM);
			boolean prayed = p.getPrayer().isActive(Prayer.PROTECT_FROM_MAGIC);
			int delay = MAGIC.send(npc, p);
			p.hit(new Hit(npc, AttackStyle.MAGIC).randDamage(prayed ? 3 : 14).clientDelay(delay).ignorePrayer());
			p.graphics(MAGIC_HIT_GFX, 124, delay);
		}
		return true;
	}

	private void entangle(Player p) {
		npc.animate(MAGIC_ANIM);
		int delay = SNARE.send(npc, p);
		Object lock = new Object();
		npc.addEvent(e -> {
			e.delay(World.getTicks(delay));
			if (npc.isRemoved() || p.getCombat().isDead())
				return;
			p.graphics(SNARE_HIT_GFX, 0, 0);
			p.getRouteFinder().routeAbsolute(p.getAbsX(), p.getAbsY());
			p.freeze(8, npc);
			p.sendMessage(Color.RED.wrap("The Hespori entangles you in some vines!"));
			int[] struggles = { 5 };
			p.set(ENTANGLE_KEY, (Runnable) () -> {
				if (struggles[0] > 0) {
					struggles[0]--;
					p.sendMessage("You feel the vines loosen slightly as you try to move.");
				} else {
					p.remove(ENTANGLE_KEY);
					p.getMovement().reset();
					p.resetFreeze();
					p.sendMessage(Color.DARK_GREEN.wrap("You manage to break free of the vines!"));
				}
			});
			p.set("HESPORI_ENTANGLE_LOCK", lock);
			e.delay(10); // wiki: after 6 seconds (10 ticks)
			if (p.get("HESPORI_ENTANGLE_LOCK") == lock && p.get(ENTANGLE_KEY) != null) {
				p.remove(ENTANGLE_KEY);
				p.resetFreeze();
				if (!npc.isRemoved() && !p.getCombat().isDead()) {
					p.hit(new Hit(HitType.DAMAGE).randDamage(40, 50)); // wiki: 40+
					p.sendMessage(Color.RED.wrap("The vines suddenly break, damaging you in the process."));
				}
			}
		});
	}

	/* ------------------------------------------------------------------ death + loot */

	@Override
	public void handleNewDrop(Player player, int npcID, Position dropPosition) {
		// kill count, points and hooks from the shared path (Hespori has no newDrops table, so it rolls nothing)
		super.handleNewDrop(player, npcID, dropPosition);
		if (player != owner)
			return;
		// OSRS: the defeated Hespori stays in the patch (HESPORI_PLANT_DEAD); harvesting it gives the XP and the loot
		if (patch != null) {
			patch.setProduceCount(1);
			patch.update();
			player.sendMessage("The Hespori is defeated. Harvest it to claim your reward.");
		} else {
			harvest(player);
		}
		despawn();
	}

	/** The harvest of a defeated Hespori (HesporiPatch): 12,600 Farming XP, the seed loot, the bucket and the pet roll. */
	public static void harvest(Player player) {
		List<Item> loot = new ArrayList<>();
		loot.add(new Item(Random.get(ANIMA_SEEDS), Random.get(1, 2)));
		loot.add(rollMainTable());
		if (Random.get(34) == 0) {
			Item bucket = new Item(BOTTOMLESS_COMPOST_BUCKET, 1);
			bucket.lootBroadcast = io.ruin.utility.Broadcast.GLOBAL;
			loot.add(bucket);
		}
		player.animate(830); // spade
		for (Item item : loot) {
			player.getInventory().addOrDrop(item);
			player.sendMessage("You harvest " + item.getAmount() + " x " + item.getDef().name + ".");
			player.addToCollectionLog(item);
		}
		int farming = player.getStats().get(StatType.Farming).fixedLevel;
		if (Random.get(Math.max(1, 7000 - 25 * farming) - 1) == 0)
			Pet.TANGLEROOT.unlock(player, 0);
		player.getStats().addXp(StatType.Farming, 12600, true);
	}

	private static final int BOTTOMLESS_COMPOST_BUCKET = 22994;
	private static final int[] ANIMA_SEEDS = { 22881 /* Attas */, 22883 /* Iasor */, 22885 /* Kronos */ };

	/** {seed id, weight (/80), min, max} - OSRS Hespori main table */
	private static final int[][] MAIN_TABLE = {
		{ 5321, 2, 10, 20 }, /* watermelon */ { 22879, 2, 6, 16 }, /* snape grass */
		{ 22887, 5, 8, 18 }, /* white lily */ { 5100, 2, 6, 14 }, /* limpwurt */
		{ 5311, 2, 10, 20 }, /* wildblood */ { 5105, 2, 10, 16 }, /* whiteberry */ { 5106, 2, 8, 16 }, /* poison ivy */
		{ 5297, 3, 2, 8 }, /* irit */ { 5298, 3, 2, 5 }, /* avantoe */ { 5299, 3, 2, 5 }, /* kwuarm */
		{ 5296, 3, 2, 5 }, /* toadflax */ { 5301, 3, 2, 5 }, /* cadantine */ { 5302, 3, 2, 5 }, /* lantadyme */
		{ 5303, 3, 2, 5 }, /* dwarf weed */ { 5295, 2, 1, 2 }, /* ranarr */ { 5300, 2, 1, 1 }, /* snapdragon */
		{ 5304, 2, 1, 1 }, /* torstol */
		{ 5314, 4, 2, 4 }, /* maple */ { 5313, 3, 2, 5 }, /* willow */ { 5315, 2, 1, 1 }, /* yew */ { 5316, 1, 1, 1 }, /* magic */
		{ 5287, 3, 3, 6 }, /* pineapple */ { 5288, 3, 1, 3 }, /* papaya */ { 5289, 3, 1, 3 }, /* palm */ { 22877, 2, 1, 1 }, /* dragonfruit */
		{ 21486, 4, 2, 5 }, /* teak */ { 21488, 3, 1, 3 }, /* mahogany */ { 5280, 2, 4, 14 }, /* cactus */
		{ 22873, 2, 4, 14 }, /* potato cactus */ { 22869, 2, 1, 1 }, /* celastrus */ { 5317, 1, 1, 1 }, /* spirit */
		{ 22871, 1, 1, 1 }, /* redwood */
	};

	private static Item rollMainTable() {
		int total = 0;
		for (int[] e : MAIN_TABLE)
			total += e[1];
		int roll = Random.get(total - 1);
		for (int[] e : MAIN_TABLE) {
			roll -= e[1];
			if (roll < 0)
				return new Item(e[0], Random.get(e[2], e[3]));
		}
		return new Item(MAIN_TABLE[0][0], 10);
	}

	@Override
	public void startDeath(Hit killHit) {
		for (NPC f : flowers)
			f.lock();
		super.startDeath(killHit);
		// owner died / left before the loot step ran: make sure the flowers don't linger
		World.startEvent(e -> {
			e.delay(info.death_ticks + 3);
			if (!flowers.isEmpty())
				despawn();
		});
	}

	/* ------------------------------------------------------------------ cave + struggle hook */

	public static void register() {
		ObjectAction.register(34499, "Enter", (player, obj) -> {
			player.animate(2796);
			player.getMovement().teleport(CAVE_INSIDE);
		});
		ObjectAction.register(34435, "Exit", (player, obj) -> {
			player.animate(2796);
			player.getMovement().teleport(CAVE_OUTSIDE);
		});
	}

	/** WalkHandler hook: an entangled player's walk click is a struggle against the vines. */
	public static boolean struggle(Player player) {
		Object r = player.get(ENTANGLE_KEY);
		if (r instanceof Runnable run) {
			run.run();
			return true;
		}
		return false;
	}
}
