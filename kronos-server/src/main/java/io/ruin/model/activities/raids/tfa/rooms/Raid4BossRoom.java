package io.ruin.model.activities.raids.tfa.rooms;

import io.ruin.model.activities.raids.tfa.Raid4Controller;
import io.ruin.model.activities.raids.tfa.Raid4Instance;
import io.ruin.model.activities.raids.tfa.Raid4Room;
import io.ruin.model.activities.raids.tfa.Raid4Scaling;
import io.ruin.model.activities.raids.tfa.Raid4Schedule;
import io.ruin.model.combat.Hit;
import io.ruin.model.entity.npc.NPC;
import io.ruin.model.entity.player.Player;
import io.ruin.model.map.Direction;
import io.ruin.model.map.Position;
import io.ruin.model.map.dynamic.DynamicMap;
import lombok.Getter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Shared plumbing for the boss rooms after Ezekiel: the schedule, room-local coordinates, spawning, member
 * lists, damage/points bookkeeping and burns. Subclasses implement the fight in {@link #init(Raid4Controller,
 * DynamicMap)}'s spawns plus {@link #onStart(Player)}, {@link #onTick()} and {@link #onNpcDeath(NPC)}.
 * <p>
 * Every arena in the archive is walled with map locs that stay up, so members walk in through the room's boss
 * teleporter and are carried back out next to it when the fight ends.
 */
public abstract class Raid4BossRoom implements Raid4Room {

	protected Raid4Controller controller;
	protected Raid4Instance instance;
	protected DynamicMap map;
	@Getter
	protected final Raid4Schedule schedule = new Raid4Schedule();
	protected final List<NPC> spawned = new ArrayList<>();

	@Getter
	protected int scale = Raid4Scaling.MIN_SCALE;
	protected boolean started, complete;

	/** Burns: damage per hit, hits left. Every burn hits each {@value #BURN_INTERVAL} ticks. */
	private final Map<Player, int[]> burns = new HashMap<>();
	private static final int BURN_INTERVAL = 4, BURN_HITS = 5;

	@Override
	public void init(Raid4Controller controller, DynamicMap map) {
		this.controller = controller;
		this.instance = controller.getInstance();
		this.map = map;
		spawnFixtures();
	}

	/** Spawn the dormant boss(es) and props. */
	protected abstract void spawnFixtures();

	/** The arena's walkable interior, in template-local coordinates: {minX, minY, maxX, maxY}. */
	protected abstract int[] arenaBounds();

	/** The tile inside the arena the boss teleporter puts members on. */
	protected abstract int[] arenaEntry();

	/** The tile outside the arena, by the teleporter, where members wait and land after the fight. */
	protected abstract int[] outside();

	/** Where members land when they come in from the Nexus. */
	protected int[] entry() {
		return outside();
	}

	/** First member steps into the arena. */
	protected abstract void onStart(Player first);

	/** Every tick of a started, unfinished fight. */
	protected abstract void onTick();

	@Override
	public void start() {
		List<Player> inside = fighters();
		inside.removeIf(p -> !inArena(p.getPosition()));
		if (!inside.isEmpty())
			begin(inside.get(0));
	}

	private void begin(Player first) {
		if (started)
			return;
		started = true;
		scale = Raid4Scaling.scale(controller.getParty().alive().size());
		controller.challengeStarted(challengeName());
		onStart(first);
	}

	@Override
	public void tick() {
		schedule.tick();
		if (complete)
			return;
		if (!started) {
			for (Player player : fighters()) {
				if (inArena(player.getPosition())) {
					begin(player);
					break;
				}
			}
			return;
		}
		tickBurns();
		onTick();
	}

	/** Ends the fight: the room is cleared, hazards go, and everyone is carried out of the arena. */
	protected void finish(String log) {
		if (complete)
			return;
		complete = true;
		controller.challengeComplete(challengeName());
		controller.roomComplete(this);
		burns.clear();
		schedule.after(navigatorDelay(), () -> {
			clearHazards();
			afterClear();
		});
	}

	/**
	 * The Archive transporter (Use / Quick-use, RSProx: every room): the raider is put over the wall onto the
	 * landing tile two ticks after the op, with spotanim abyssal_demon_teleport (409) and a facing reset.
	 */
	protected void transport(Player player) {
		player.lock();
		player.addEvent(e -> {
			e.delay(2);
			player.unlock();
			if (complete || !canEnter(player))
				return;
			io.ruin.model.activities.raids.tfa.Raid4Fx.teleportNow(player, walkable(arenaEntry(), 0));
			player.graphics(409);
			if (player.avatar() != null)
				player.avatarExtended().resetFacing();
		});
	}

	/** A puppet's health reached 0: its death animation starts ({@link #onNpcDeath} follows when it is removed). */
	public void onNpcDying(NPC npc) {
	}

	/** Ticks from the "Challenge complete" line to the Navigator (RSProx: Ezekiel +3, Bork +3). */
	protected int navigatorDelay() {
		return 3;
	}

	/** The Navigator's facing. */
	protected Direction navigatorFacing() {
		return Direction.SOUTH;
	}

	/** After the kill, once the hazards are gone: the way on. */
	protected void afterClear() {
		if (usesNavigator()) {
			// live: the Alliance Navigator appears in the arena; "Follow" leads the party back to the Nexus
			spawn(io.ruin.model.activities.raids.tfa.Raid4Ids.NPC_ALLIANCE_NAVIGATOR, walkable(navigatorSpot(), 0), navigatorFacing());
			return;
		}
		for (Player player : fighters()) {
			if (inArena(player.getPosition()))
				player.getMovement().teleport(walkable(outside(), 2));
		}
	}

	/** The name in "Challenge started/complete: ..." (capitalised room name). */
	protected String challengeName() {
		String name = type().displayName;
		return Character.toUpperCase(name.charAt(0)) + name.substring(1);
	}

	/**
	 * True where the live room spawns the Alliance Navigator after the kill (Ezekiel, Bork, Dreadnoth, Xarth in the
	 * footage). Moia opens her fence gate and the Dagon'hai's Archive transporter appears instead: those carry the
	 * party out to their teleporter as before.
	 */
	protected boolean usesNavigator() {
		return true;
	}

	/** Where the Navigator appears; the arena entry tile unless a room knows better. */
	protected int[] navigatorSpot() {
		return arenaEntry();
	}

	/** Remove the room's temporary npcs and loc effects. The bosses' corpses are already gone. */
	protected void clearHazards() {
		for (NPC npc : new ArrayList<>(spawned))
			removeNpc(npc);
		spawned.clear();
	}

	@Override
	public void cleanup() {
		schedule.clear();
		burns.clear();
		clearHazards();
		if (map.getNpcs() != null) {
			for (NPC npc : new ArrayList<>(map.getNpcs()))
				removeNpc(npc);
		}
	}

	@Override
	public void useTeleporter(Player player) {
		if (complete) {
			// boss down: the transporter carries the party back to the Nexus (or on to Ocellus after the fourth)
			controller.returnToNexus(player);
			return;
		}
		if (inArena(player.getPosition()))
			return;
		if (!canEnter(player))
			return;
		if (!started) {
			// live: "Begin the challenge? Yes / No" before the first member goes in
			player.dialogue(new io.ruin.model.inter.dialogue.OptionsDialogue("Begin the challenge?",
					new io.ruin.model.inter.utils.Option("Yes.", () -> {
						player.closeDialogue();
						if (!complete && canEnter(player) && !inArena(player.getPosition()))
							transport(player);
					}),
					new io.ruin.model.inter.utils.Option("No.", player::closeDialogue)));
			return;
		}
		transport(player);
	}

	/** Override to refuse entry mid-fight (sealed arenas). */
	protected boolean canEnter(Player player) {
		return true;
	}

	/* Members */

	/** Living members standing in this room. */
	public List<Player> fighters() {
		List<Player> list = controller.getParty().alive();
		list.removeIf(p -> instance.roomAt(p.getPosition()) != type());
		return list;
	}

	/** Living members inside the arena itself. */
	public List<Player> inArena() {
		List<Player> list = fighters();
		list.removeIf(p -> !inArena(p.getPosition()));
		return list;
	}

	public void hitPlayer(Player player, Hit hit) {
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

	/* Burns (no shared burn system in the server: rooms own them) */

	public void burn(Player player, int damage) {
		int[] b = burns.get(player);
		if (b == null || b[0] < damage)
			burns.put(player, new int[] { damage, BURN_HITS, BURN_INTERVAL });
		else
			b[1] = BURN_HITS;
	}

	private void tickBurns() {
		for (Iterator<Map.Entry<Player, int[]>> it = burns.entrySet().iterator(); it.hasNext();) {
			Map.Entry<Player, int[]> e = it.next();
			Player player = e.getKey();
			int[] b = e.getValue();
			if (player.getCombat().isDead() || !fighters().contains(player)) {
				it.remove();
				continue;
			}
			if (--b[2] > 0)
				continue;
			b[2] = BURN_INTERVAL;
			hitPlayer(player, new Hit().fixedDamage(b[0]).ignoreDefence().ignorePrayer());
			if (--b[1] <= 0)
				it.remove();
		}
	}

	/* Coordinates */

	/** The instance plane the fight is on (Ocellus fights upstairs). */
	protected int plane() {
		return 0;
	}

	protected Position local(int x, int y) {
		return instance.local(type(), x, y, plane());
	}

	protected int lx(Position p) {
		return instance.localX(type(), p);
	}

	protected int ly(Position p) {
		return instance.localY(type(), p);
	}

	protected Position walkable(int[] xy, int radius) {
		return instance.walkable(type(), xy[0], xy[1], plane(), radius);
	}

	public boolean inArena(Position p) {
		int[] b = arenaBounds();
		int x = lx(p), y = ly(p);
		return instance.roomAt(p) == type() && p.getZ() == local(0, 0).getZ() && x >= b[0] && x <= b[2] && y >= b[1] && y <= b[3];
	}

	/** A random walkable tile inside the arena. */
	protected Position randomArenaTile() {
		int[] b = arenaBounds();
		for (int i = 0; i < 20; i++) {
			int x = b[0] + io.ruin.api.utils.Random.get(b[2] - b[0]);
			int y = b[1] + io.ruin.api.utils.Random.get(b[3] - b[1]);
			Position p = local(x, y);
			if (io.ruin.model.map.Tile.get(p.getX(), p.getY(), p.getZ(), true).clipping == 0)
				return p;
		}
		return local((b[0] + b[2]) / 2, (b[1] + b[3]) / 2);
	}

	/** Is p inside the size x size square whose south-west tile is sw? */
	protected static boolean under(Position sw, int size, Position p) {
		return p.getZ() == sw.getZ() && p.getX() >= sw.getX() && p.getX() < sw.getX() + size && p.getY() >= sw.getY()
				&& p.getY() < sw.getY() + size;
	}

	/** Chebyshev distance from p to the nearest tile of a size x size square at sw (0 = under it). */
	protected static int distance(Position sw, int size, Position p) {
		int dx = Math.max(0, Math.max(sw.getX() - p.getX(), p.getX() - (sw.getX() + size - 1)));
		int dy = Math.max(0, Math.max(sw.getY() - p.getY(), p.getY() - (sw.getY() + size - 1)));
		return Math.max(dx, dy);
	}

	protected static long key(Position p) {
		return ((long) p.getX() << 32) | (p.getY() & 0xffffffffL);
	}

	/* Npcs */

	protected NPC spawn(int id, Position pos, Direction dir) {
		NPC npc = new NPC(id).spawn(pos.getX(), pos.getY(), pos.getZ(), dir, 0);
		map.addNpc(npc);
		if (npc.getCombat() != null)
			npc.getCombat().setAllowRespawn(false);
		return npc;
	}

	/** A temporary npc that {@link #clearHazards()} removes. */
	protected NPC spawnTemp(int id, Position pos, Direction dir) {
		NPC npc = spawn(id, pos, dir);
		spawned.add(npc);
		return npc;
	}

	protected void removeNpc(NPC npc) {
		if (npc == null)
			return;
		spawned.remove(npc);
		if (map.getNpcs() != null)
			map.getNpcs().remove(npc);
		if (!npc.isRemoved())
			npc.remove();
	}

	/** Holds an npc in one sequence via the base-animation-set block (ready/walk/turn all = seq). */
	protected static void setPose(NPC npc, int seq) {
		var avatar = npc.avatar();
		if (avatar == null)
			return;
		var info = avatar.getExtendedInfo();
		info.setReadyAnim(seq);
		info.setTurnAnims(seq, seq);
		info.setWalkAnims(seq, seq, seq, seq);
		info.setRunAnims(seq, seq, seq, seq);
		info.setCrawlAnims(seq, seq, seq, seq);
	}

	/* Raid4Room */

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
		return walkable(entry(), 2);
	}

	@Override
	public Position spectatePosition() {
		return entryPosition();
	}
}
