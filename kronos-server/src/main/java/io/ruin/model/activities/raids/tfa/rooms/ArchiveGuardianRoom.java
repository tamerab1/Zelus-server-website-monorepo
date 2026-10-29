package io.ruin.model.activities.raids.tfa.rooms;

import io.ruin.api.utils.Random;
import io.ruin.model.activities.raids.tfa.Raid4Controller;
import io.ruin.model.activities.raids.tfa.Raid4Fx;
import io.ruin.model.activities.raids.tfa.Raid4Instance;
import io.ruin.model.activities.raids.tfa.Raid4NexusDoors;
import io.ruin.model.activities.raids.tfa.Raid4Room;
import io.ruin.model.activities.raids.tfa.Raid4RoomType;
import io.ruin.model.activities.raids.tfa.Raid4Schedule;
import io.ruin.model.activities.raids.tfa.Raid4Scaling;
import io.ruin.model.activities.raids.tfa.bosses.guardian.ArchiveGuardian;
import io.ruin.model.activities.raids.tfa.bosses.guardian.Battery;
import io.ruin.model.activities.raids.tfa.bosses.guardian.InertGuardian;
import io.ruin.model.combat.AttackStyle;
import io.ruin.model.combat.Hit;
import io.ruin.model.entity.npc.NPC;
import io.ruin.model.entity.player.Player;
import io.ruin.model.map.Bounds;
import io.ruin.model.map.Direction;
import io.ruin.model.map.Position;
import io.ruin.model.map.dynamic.DynamicMap;
import io.ruin.model.map.object.GameObject;
import io.ruin.model.skills.prayer.Prayer;
import io.ruin.model.var.VarPlayerRepository;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static io.ruin.model.activities.raids.tfa.Raid4Ids.*;

/**
 * Room 1, the Archive Guardian, in the Nexus. The boss's own attacks and phases are in {@link ArchiveGuardian};
 * this class owns the fixtures they act on (inert guardians, mushrooms, tendrils), the tick schedule and the
 * room lifecycle.
 * <p>
 * Mechanics are the OSRS wiki's (Strategies page, October 2026). Where the wiki gives no number, the constant
 * says so; they're grouped here and in {@link ArchiveGuardian} for tuning.
 */
@Slf4j
public class ArchiveGuardianRoom implements Raid4Room {

	/* Mushrooms: "spawn under every player", pop when a player is within 1 tile, hit within 2. */
	static final int MUSHROOM_ARM_TICKS = 2; // not on the wiki
	static final int MUSHROOM_FUSE_TICKS = 2; // not on the wiki ("shortly after")
	static final int MUSHROOM_LIFETIME = 50; // not on the wiki
	static final int MUSHROOM_NEAR_DAMAGE = 10;
	static final int MUSHROOM_NEAR_POISON = 10;
	static final int MUSHROOM_FAR_MIN = 3, MUSHROOM_FAR_MAX = 4;
	static final int RUN_DISABLE_TICKS = 8; // "several seconds"

	/* Tendrils: 3x3 swipe, three hits, max 25 or 11 under Protect from Melee. */
	static final int TENDRIL_WINDUP_TICKS = 2; // not on the wiki
	static final int TENDRIL_HITS = 3;
	static final int TENDRIL_MAX = 25, TENDRIL_MAX_PROTECTED = 11;

	/** A battery charge strips this share of the full shield ("about 40%", 596 of 1490 at 6-scale). */
	public static final double BATTERY_CHARGE_SHARE = 0.40;

	private static final int SPAWN_TICKS = 5; // guardian_plan 2: spawn + 5
	/** Arena floor, local coords: inside the battery bases (x 18-44) and the section doors (y 16-46). */
	private static final int ARENA_MIN_LOCAL = 20, ARENA_MAX_LOCAL = 42;

	private Raid4Controller controller;
	private Raid4Instance instance;
	private DynamicMap map;
	@Getter
	private final Raid4Schedule schedule = new Raid4Schedule();

	private NPC dormant;
	@Getter
	private NPC guardian;
	@Getter
	private final Battery[] batteries = new Battery[BATTERY_LOCAL.length];
	private final List<Mushroom> mushrooms = new ArrayList<>();
	private final List<NPC> tendrils = new ArrayList<>();
	private final Map<Player, Long> runDisabledUntil = new HashMap<>();

	@Getter
	private int scale;
	private boolean started, complete;

	@Override
	public Raid4RoomType type() {
		return Raid4RoomType.NEXUS;
	}

	@Override
	public void init(Raid4Controller controller, DynamicMap map) {
		this.controller = controller;
		this.instance = controller.getInstance();
		this.map = map;
		Raid4NexusDoors.sealAll(instance, type());
		dormant = spawn(NPC_GUARDIAN_START, guardianTile(), Direction.SOUTH);
		for (int i = 0; i < batteries.length; i++) {
			// guardian_plan 2: the south-east battery (4139,5592) has no ops the whole fight (overloaded first)
			Battery.Charge charge = BATTERY_LOCAL[i][0] == 43 && BATTERY_LOCAL[i][1] == 24 ? Battery.Charge.BEAM : Battery.Charge.MELEE;
			Position tile = instance.local(type(), BATTERY_LOCAL[i][0], BATTERY_LOCAL[i][1], 0);
			batteries[i] = new Battery(i, charge, tile, spawn(NPC_BATTERY_INACTIVE, tile, Direction.SOUTH));
		}
	}

	public Position guardianTile() {
		return instance.local(type(), GUARDIAN_LOCAL_X, GUARDIAN_LOCAL_Y, 0);
	}

	/** Centre tile of the boss's 5x5, wherever it has floated to (cones, tendrils and the beam work from here). */
	public Position guardianCentre() {
		Position sw = guardian != null && !guardian.isRemoved() ? guardian.getPosition() : guardianTile();
		return sw.relative(2, 2);
	}

	/** "Touch" on the dormant guardian. */
	public void touch(Player player, NPC npc) {
		if (npc != dormant || started)
			return;
		if (!controller.getParty().isMember(player))
			return;
		// footage: "Touch" -> "Begin the challenge? Yes / No"
		player.dialogue(new io.ruin.model.inter.dialogue.OptionsDialogue("Begin the challenge?",
				new io.ruin.model.inter.utils.Option("Yes.", () -> {
					player.closeDialogue();
					if (npc == dormant && !started)
						start();
				}),
				new io.ruin.model.inter.utils.Option("No.", player::closeDialogue)));
	}

	@Override
	public void start() {
		if (started)
			return;
		started = true;
		scale = Raid4Scaling.scale(controller.getParty().alive().size());
		dormant.animate(SEQ_GUARDIAN_SPAWN);
		dormant.graphics(GFX_GUARDIAN_SPAWN);
		controller.challengeStarted("Archive Guardian");
		schedule.after(SPAWN_TICKS, () -> {
			removeNpc(dormant);
			dormant = null;
			guardian = spawn(NPC_GUARDIAN, guardianTile(), Direction.SOUTH);
			guardian.getCombat().setAllowRespawn(false);
			// Leash to the arena floor inside the battery bases and doors; a 5x5's SW tile can't pass x/y 41.
			guardian.attackBounds = new Bounds(instance.local(type(), ARENA_MIN_LOCAL, ARENA_MIN_LOCAL, 0),
					instance.local(type(), ARENA_MAX_LOCAL - 4, ARENA_MAX_LOCAL - 4, 0), 0);
			((ArchiveGuardian) guardian.getCombat()).bind(this);
			for (Battery battery : batteries)
				activate(battery);
		});
	}

	@Override
	public void tick() {
		schedule.tick();
		if (!started || complete)
			return;
		struckThisTick.clear();
		if (guardian != null && !guardian.isRemoved())
			((ArchiveGuardian) guardian.getCombat()).roomTick();
		tickMushrooms();
		tickRunDisable();
	}

	/* Inert guardians */

	private void activate(Battery battery) {
		removeNpc(battery.npc);
		battery.npc = spawn(NPC_BATTERY_ACTIVE, battery.tile, Direction.SOUTH);
		battery.npc.getCombat().setAllowRespawn(false);
		battery.npc.getCombat().getStat(io.ruin.model.stat.StatType.Hitpoints).fixedLevel = Raid4Scaling.hp(180, scale);
		battery.npc.getCombat().getStat(io.ruin.model.stat.StatType.Hitpoints).restore();
		((InertGuardian) battery.npc.getCombat()).bind(this, battery);
		battery.state = Battery.State.ACTIVE;
		if (battery.charge == Battery.Charge.BEAM && battery.npc.avatar() != null)
			battery.npc.avatarExtended().setAllOpsInvisible();
	}

	/**
	 * A battery at 0 (guardian_plan 7): the next tick its despawn seq and no ops; +2 the destroyed form; +3 the
	 * Guardian takes 40% of the shield's max.
	 */
	public void charge(Battery battery, Player by) {
		if (battery.state == Battery.State.DESTROYED || guardian == null)
			return;
		battery.state = Battery.State.DESTROYED;
		Position tile = battery.tile;
		NPC old = battery.npc;
		schedule.after(1, () -> {
			if (old != null && !old.isRemoved()) {
				old.animate(SEQ_BATTERY_DESPAWN);
				if (old.avatar() != null)
					old.avatarExtended().setAllOpsInvisible();
			}
		});
		schedule.after(3, () -> {
			removeNpc(old);
			battery.npc = spawn(NPC_BATTERY_DESTROYED, tile, Direction.SOUTH);
			battery.npc.animate(SEQ_BATTERY_DESPAWN_IDLE);
		});
		if (by != null)
			controller.getPoints().mechanic(by);
		schedule.after(4, () -> {
			if (guardian != null && !guardian.isRemoved())
				((ArchiveGuardian) guardian.getCombat()).onBatteryCharged(battery);
		});
	}

	/** The enrage's overloads, in order: south-east, north-east, south-west, north-west (9). */
	public Battery overloadNext() {
		int[][] order = { { 43, 24 }, { 43, 37 }, { 18, 24 }, { 18, 37 } };
		for (int[] o : order)
			for (Battery b : batteries)
				if (BATTERY_LOCAL[b.index][0] == o[0] && BATTERY_LOCAL[b.index][1] == o[1] && b.state != Battery.State.DESTROYED) {
					charge(b, null);
					return b;
				}
		return null;
	}

	/** His 5x5 blocks walking while he is down. */
	public void blockGuardian(boolean block) {
		if (guardian == null)
			return;
		Position sw = guardian.getPosition();
		for (int x = 0; x < 5; x++)
			for (int y = 0; y < 5; y++) {
				io.ruin.model.map.Tile t = io.ruin.model.map.Tile.get(sw.getX() + x, sw.getY() + y, sw.getZ(), true);
				if (block)
					t.flagUnmovable();
				else
					t.unflagUnmovable();
			}
	}

	/** Inside the arena floor (between the battery bases and the section doors). */
	public boolean onArenaFloor(Position p) {
		int x = instance.localX(type(), p), y = instance.localY(type(), p);
		return x >= ARENA_MIN_LOCAL && x <= ARENA_MAX_LOCAL && y >= ARENA_MIN_LOCAL && y <= ARENA_MAX_LOCAL;
	}

	/* Mushrooms */

	private static final class Mushroom {
		final GameObject loc;
		final long spawnedAt;
		long explodesAt = -1;

		Mushroom(GameObject loc, long spawnedAt) {
			this.loc = loc;
			this.spawnedAt = spawnedAt;
		}
	}

	/**
	 * 6: two mushrooms a raider round the tile they stood on the tick before: one on a tile beside it, one exactly
	 * three out, each an even pick among that ring's open tiles, never on a wall or a live mushroom.
	 */
	public void spawnMushrooms(Map<Player, Position> lastTiles) {
		for (Player player : fighters()) {
			Position c = lastTiles.getOrDefault(player, player.getPosition()).copy();
			for (int ring : new int[] { 1, 3 }) {
				List<Position> open = new ArrayList<>();
				for (int dx = -ring; dx <= ring; dx++)
					for (int dy = -ring; dy <= ring; dy++) {
						if (Math.max(Math.abs(dx), Math.abs(dy)) != ring)
							continue;
						Position t = c.relative(dx, dy);
						io.ruin.model.map.Tile tile = io.ruin.model.map.Tile.get(t.getX(), t.getY(), t.getZ(), false);
						if (!onArenaFloor(t) || (tile != null && tile.clipping != 0))
							continue;
						if (mushrooms.stream().anyMatch(m -> m.loc.x == t.getX() && m.loc.y == t.getY()))
							continue;
						open.add(t);
					}
				if (!open.isEmpty())
					mushrooms.add(new Mushroom(instance.spawnObject(LOC_MUSHROOM_SPAWN, Random.get(open), 10, 0), schedule.now()));
			}
		}
	}

	/** +3 active; the first tick on or after active + 1 with a raider within 1 bursts it the tick after. */
	private void tickMushrooms() {
		long now = schedule.now();
		for (Iterator<Mushroom> it = mushrooms.iterator(); it.hasNext();) {
			Mushroom m = it.next();
			long age = now - m.spawnedAt;
			if (m.explodesAt < 0) {
				if (age == 3) {
					m.loc.setId(LOC_MUSHROOM_ACTIVE);
					m.loc.animate(8631);
				}
				if (age >= 4 && anyFighterWithin(m.loc, 1))
					m.explodesAt = now + 1;
				continue;
			}
			if (now >= m.explodesAt) {
				explode(m);
				it.remove();
			}
		}
	}

	/** The burst: the despawn loc and its sound; +1 everyone within a tile is poisoned (10) and too drowsy to run. */
	private void explode(Mushroom m) {
		Position centre = new Position(m.loc.x, m.loc.y, m.loc.z);
		m.loc.setId(LOC_MUSHROOM_DESPAWN);
		for (Player p : fighters())
			p.getPacketSender().sendAreaSound(12175, 0, 12, centre.getX(), centre.getY(), 2);
		schedule.after(2, () -> instance.removeObject(m.loc));
		schedule.after(1, () -> {
			for (Player player : fighters()) {
				if (chebyshev(player.getPosition(), centre) > 1 || player.isPoisonImmune())
					continue;
				player.poison(10);
				player.sendMessage("You have been poisoned!");
				runDisabledUntil.put(player, schedule.now() + RUN_DISABLE_TICKS);
				VarPlayerRepository.RUNNING.set(player, 0);
			}
		});
	}

	private void tickRunDisable() {
		long now = schedule.now();
		runDisabledUntil.entrySet().removeIf(e -> {
			if (now >= e.getValue())
				return true;
			if (VarPlayerRepository.RUNNING.get(e.getKey()) != 0) {
				VarPlayerRepository.RUNNING.set(e.getKey(), 0);
				e.getKey().sendMessage("You're too drowsy to run!");
			}
			return false;
		});
	}

	/* Vines (8) */

	/**
	 * A wave: one vine a raider at A = their tile (as the tick began) plus one step in the 8-way direction from his
	 * middle; blocked, the step toward him; then their own tile. Attacks at +3, +6, +9 on every raider exactly one tile
	 * from it (17..19, Protect from Melee 40% off), despawn +12, gone +13.
	 */
	public void vineWave(Map<Player, Position> lastTiles) {
		Position mid = guardianCentre();
		java.util.Set<Long> taken = new java.util.HashSet<>();
		for (NPC v : tendrils)
			taken.add(((long) v.getAbsX() << 32) | v.getAbsY());
		for (Player player : fighters()) {
			Position p = lastTiles.getOrDefault(player, player.getPosition()).copy();
			double ang = Math.atan2(p.getY() - mid.getY(), p.getX() - mid.getX());
			int oct = (int) Math.round(ang / (Math.PI / 4));
			int[][] dirs = { { 1, 0 }, { 1, 1 }, { 0, 1 }, { -1, 1 }, { -1, 0 }, { -1, -1 }, { 0, -1 }, { 1, -1 } };
			int[] d = dirs[((oct % 8) + 8) % 8];
			Position tile = null;
			for (Position c : new Position[] { p.relative(d[0], d[1]), p.relative(-d[0], -d[1]), p }) {
				io.ruin.model.map.Tile t = io.ruin.model.map.Tile.get(c.getX(), c.getY(), c.getZ(), false);
				boolean blocked = (t != null && t.clipping != 0) || !onArenaFloor(c)
						|| taken.contains(((long) c.getX() << 32) | c.getY()) || chebyshev(c, mid) <= 2;
				if (!blocked) {
					tile = c;
					break;
				}
			}
			if (tile == null)
				continue;
			taken.add(((long) tile.getX() << 32) | tile.getY());
			NPC vine = spawn(NPC_GUARDIAN_VINE, tile, Direction.SOUTH);
			vine.face(p);
			vine.animate(SEQ_TENTACLE_SPAWN);
			vine.graphics(GFX_TENTACLE_INTRO);
			for (Player q : fighters())
				q.getPacketSender().sendAreaSound(12218, 0, 0, tile.getX(), tile.getY(), 4);
			tendrils.add(vine);
			Position at = tile;
			for (int k = 1; k <= 3; k++) {
				schedule.after(3 * k, () -> {
					if (vine.isRemoved())
						return;
					vine.animate(SEQ_TENTACLE_ATTACK);
					for (Player target : fighters()) {
						if (chebyshev(target.getPosition(), at) != 1 || struckThisTick.contains(target))
							continue;
						struckThisTick.add(target);
						int dmg = Random.get(17, 19);
						if (target.getPrayer().isActive(Prayer.PROTECT_FROM_MELEE))
							dmg = (int) Math.round(dmg * 0.6);
						hitPlayer(target, new Hit(guardian, AttackStyle.CRUSH).fixedDamage(dmg).ignoreDefence().ignorePrayer());
					}
				});
			}
			schedule.after(12, () -> vine.animate(SEQ_TENTACLE_DESPAWN));
			schedule.after(13, () -> {
				tendrils.remove(vine);
				removeNpc(vine);
			});
		}
	}

	/** One vine hit a raider a beat however many vines reach them. */
	private final java.util.Set<Player> struckThisTick = new java.util.HashSet<>();

	/* Guardian death */

	@Override
	public void onNpcDeath(NPC npc) {
		if (npc != guardian || complete)
			return;
		complete = true;
		Position tile = guardian.getPosition().copy(); // where it fell, not where it rose
		log.info("Raid 4: Archive Guardian died at {}, cleared Nexus in 5 ticks", tile);
		clearHazards();
		controller.roomComplete(this);
		// RSProx guardian_plan 11: +7 the dead form, the line and section 1's doors ajar ("_slightly"); +10 they open.
		// The hall is not rebuilt: the raiders walk to a door, which takes them on to the first path's boss.
		schedule.after(7, () -> {
			spawn(NPC_GUARDIAN_DEAD, tile, Direction.SOUTH);
			controller.challengeComplete("Archive Guardian");
			io.ruin.model.activities.raids.tfa.Raid4NexusDoors.showChoice(instance, type(), controller.getNextSlot());
		});
		schedule.after(10, () -> io.ruin.model.activities.raids.tfa.Raid4NexusDoors.openChoice(instance, type(),
				controller.getNextSlot()));
		// nexus_plan 1: K+15 Armacus (postguardian) fades in at (4127,5577) and runs north; K+16 and K+24 a four of
		// Guardians of Armadyl on (4126/4128, 5573/5574), the same fade-in; K+20 he stops at (4127,5586) and speaks
		schedule.after(15, () -> {
			NPC armacus = spawn(NPC_ARMACUS_POSTGUARDIAN, instance.local(type(), 31, 9, 0), Direction.NORTH);
			Raid4Fx.faceAngle(armacus, 1024, true);
			Raid4Fx.fade(armacus, 0, 30, 255, 0);
			armacus.stepAbs(armacus.getAbsX(), armacus.getAbsY() + 9, io.ruin.model.entity.shared.StepType.RUN);
			schedule.after(5, () -> armacus.forceText("Pick a side and get to work!"));
		});
		for (int wave : new int[] { 16, 24 })
			schedule.after(wave, () -> {
				for (int[] t : new int[][] { { 30, 6 }, { 30, 5 }, { 32, 6 }, { 32, 5 } }) {
					NPC g = spawn(NPC_ALLIANCE_GUARDIAN_FIRST + Random.get(5), instance.local(type(), t[0], t[1], 0), Direction.NORTH);
					Raid4Fx.fade(g, 0, 30, 255, 0);
					g.stepAbs(g.getAbsX(), g.getAbsY() + 8, io.ruin.model.entity.shared.StepType.RUN);
				}
			});
	}

	private void clearHazards() {
		for (Mushroom m : mushrooms)
			instance.removeObject(m.loc);
		mushrooms.clear();
		for (NPC vine : tendrils)
			removeNpc(vine);
		tendrils.clear();
		runDisabledUntil.clear();
	}

	@Override
	public void cleanup() {
		schedule.clear();
		clearHazards();
		if (map.getNpcs() != null) {
			for (NPC npc : new ArrayList<>(map.getNpcs()))
				removeNpc(npc);
		}
	}

	/* Helpers shared with the boss */

	/** Living members standing in this room. */
	public List<Player> fighters() {
		List<Player> list = controller.getParty().alive();
		list.removeIf(p -> instance.roomAt(p.getPosition()) != type());
		return list;
	}

	public void hitPlayer(Player player, Hit hit) {
		// An invincible player (::god) never finishes a queued hit, so they'd pile up to the 250-hit cap.
		if (player.isInvincible())
			return;
		hit.postDamage(entity -> controller.getPoints().damageTaken(player, hit.damage));
		player.hit(hit);
	}

	public void recordDamage(Player player, int damage) {
		controller.getPoints().damageDealt(player, damage);
	}

	public void recordMechanic(Player player) {
		controller.getPoints().mechanic(player);
	}

	public void message(String message) {
		controller.getParty().message(message);
	}

	public Raid4Instance instance() {
		return instance;
	}

	private boolean anyFighterWithin(GameObject loc, int distance) {
		Position pos = new Position(loc.x, loc.y, loc.z);
		for (Player player : fighters()) {
			if (chebyshev(player.getPosition(), pos) <= distance)
				return true;
		}
		return false;
	}

	public static int chebyshev(Position a, Position b) {
		return Math.max(Math.abs(a.getX() - b.getX()), Math.abs(a.getY() - b.getY()));
	}

	public Player randomFighter() {
		List<Player> list = fighters();
		return list.isEmpty() ? null : Random.get(list);
	}

	private NPC spawn(int id, Position pos, Direction dir) {
		NPC npc = new NPC(id).spawn(pos.getX(), pos.getY(), pos.getZ(), dir, 0);
		map.addNpc(npc);
		return npc;
	}

	private void removeNpc(NPC npc) {
		if (npc == null)
			return;
		if (map.getNpcs() != null)
			map.getNpcs().remove(npc);
		if (!npc.isRemoved())
			npc.remove();
	}

	@Override
	public boolean isStarted() {
		return started;
	}

	@Override
	public boolean isComplete() {
		return complete;
	}

	@Override
	public Position entryPosition() {
		return instance.walkable(type(), NEXUS_ARRIVAL_LOCAL_X, NEXUS_ARRIVAL_LOCAL_Y, 0, 4);
	}

	@Override
	public void onEnter(Player player) {
		// The aura is an npc spotanim, sent to whoever is in view when it's set: replay it as members arrive.
		if (dormant != null && !started)
			schedule.after(2, () -> {
				if (dormant != null && !started)
					dormant.graphics(GFX_GUARDIAN_SPAWN_IDLE);
			});
	}

	@Override
	public Position spectatePosition() {
		return instance.walkable(type(), NEXUS_SPECTATE_LOCAL_X, NEXUS_SPECTATE_LOCAL_Y, 0, 4);
	}
}
