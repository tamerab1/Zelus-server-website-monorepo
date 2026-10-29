package io.ruin.model.activities.raids.tfa;

import io.ruin.model.entity.npc.NPC;
import io.ruin.model.entity.player.Player;
import io.ruin.model.map.Position;
import io.ruin.model.map.dynamic.DynamicMap;
import io.ruin.model.map.object.GameObject;
import lombok.extern.slf4j.Slf4j;

import static io.ruin.model.activities.raids.tfa.Raid4Ids.*;

/**
 * The Nexus after the Guardian falls (source planes 2-3): no fight, just the path doors. The party comes back
 * here between bosses.
 */
@Slf4j
public class Raid4NexusRoom implements Raid4Room {

	private Raid4Controller controller;

	@Override
	public Raid4RoomType type() {
		return Raid4RoomType.NEXUS_CLEARED;
	}

	/* RSProx nexus_plan §4: the atrium's people each visit, local to square (64,87) (absolute - (4096, 5568)). */
	static final int ARMACUS = 16696, EVA = 16697, IVY = 16698, GUARDIAN = 16699, QUARTERMASTER = Raid4Quartermaster.NPC_QUARTERMASTER;
	static final int[] WORKER_TYPES = { 16710, 16711, 16712, 16694, 16695, GUARDIAN };
	/** The workers' spots and their benches' seqs: grindstone 7319, table 832, runes 8444, builders 827 / 8481. */
	static final int[][] WORKERS = { { 26, 12, 7319 }, { 28, 20, 832 }, { 36, 21, 8444 }, { 27, 34, 827 }, { 35, 30, 8481 }, { 30, 28, 827 } };

	private final java.util.List<NPC> people = new java.util.ArrayList<>();
	private DynamicMap map;
	private int visit;
	private long ticks;
	/** The side of the boss room the party comes back from (null: no arrival box, e.g. a test jump). */
	private final Raid4RoomType.Side from;

	public Raid4NexusRoom() {
		this(null);
	}

	public Raid4NexusRoom(Raid4RoomType.Side from) {
		this.from = from;
	}

	@Override
	public void init(Raid4Controller controller, DynamicMap map) {
		this.controller = controller;
		this.map = map;
		Raid4NexusDoors.showChoice(controller.getInstance(), type(), controller.getNextSlot());
		visit = controller.getNextSlot(); // 3 after stage 2, 4 after stage 3, 5 after stage 4
		switch (visit) {
			case 3 -> {
				add(NPC_EVA_SWORD, 31, 29, io.ruin.model.map.Direction.SOUTH);
				add(ARMACUS, 36, 11, io.ruin.model.map.Direction.NORTH);
			}
			case 4 -> {
				add(EVA, 39, 35, io.ruin.model.map.Direction.WEST);
				quartermaster = add(QUARTERMASTER, Raid4Quartermaster.LOCAL_X, Raid4Quartermaster.LOCAL_Y, io.ruin.model.map.Direction.SOUTH);
				ivy = add(NPC_IVY_NOOP, 31, 45, io.ruin.model.map.Direction.SOUTH);
			}
			case 5 -> {
				add(NPC_ARMACUS_POSTGUARDIAN, 32, 51, io.ruin.model.map.Direction.SOUTH);
				add(NPC_EVA_SWORD, 30, 51, io.ruin.model.map.Direction.SOUTH);
				ivy = add(IVY, 31, 50, io.ruin.model.map.Direction.SOUTH);
			}
			default -> {
			}
		}
		// nexus_plan §4: the broken scoreboard (4119,5578) r2 every visit; the loot wheelbarrow (4130,5608) from A2
		placeLoc(Raid4Ids.LOC_IN_RAID_SCOREBOARD_BROKEN, 23, 10, 2);
		if (visit == 4) // A2 only: A3 lists the scoreboard and the exit, no wheelbarrow
			placeLoc(Raid4Ids.LOC_LOOT_WHEELBARROW, 34, 40, 0);
		// the Ocellus door (4124,5621) r0: closed on A1/A2, open on A3
		placeLoc(visit >= 5 ? LOC_NEXUS_OCELLUS_DOOR_OPEN : LOC_NEXUS_OCELLUS_DOOR_CLOSED, 28, 53, 0);
		for (int[] w : WORKERS)
			workers.add(new int[] { people.indexOf(add(WORKER_TYPES[io.ruin.api.utils.Random.get(WORKER_TYPES.length - 1)], w[0], w[1],
					io.ruin.model.map.Direction.NORTH)), w[2], 4 + io.ruin.api.utils.Random.get(2) });
	}

	private NPC ivy, quartermaster;

	/** A shape-10 loc on a local tile: the map's own scoreboard or Ocellus door there is swapped, anything else is spawned. */
	private void placeLoc(int id, int x, int y, int rot) {
		Position at = controller.getInstance().local(type(), x, y, 0);
		io.ruin.model.map.Tile tile = io.ruin.model.map.Tile.get(at.getX(), at.getY(), at.getZ(), false);
		if (tile != null && tile.gameObjects != null)
			for (io.ruin.model.map.object.GameObject obj : tile.gameObjects)
				if (obj.getType() == 10 && (obj.getId() == id || obj.getId() == Raid4Ids.LOC_IN_RAID_SCOREBOARD
						|| obj.getId() == Raid4Ids.LOC_IN_RAID_SCOREBOARD_BROKEN || obj.originalId == LOC_NEXUS_OCELLUS_DOOR_CLOSED
						|| obj.originalId == LOC_NEXUS_OCELLUS_DOOR_OPEN || obj.originalId == LOC_NEXUS_OCELLUS_DOOR_CLOSED - 1)) {
					obj.setId(id);
					return;
				}
		GameObject spawned = controller.getInstance().spawnObject(id, at, 10, rot);
		log.info("Raid 4: Nexus visit {} spawned loc {} at {} (local {},{}), tile objects {}", visit, id, at, x, y,
				spawned.tile == null ? null : spawned.tile.gameObjects);
	}
	private final java.util.List<int[]> workers = new java.util.ArrayList<>();

	private NPC add(int id, int x, int y, io.ruin.model.map.Direction dir) {
		Position at = controller.getInstance().local(type(), x, y, 0);
		NPC npc = new NPC(id).spawn(at.getX(), at.getY(), at.getZ(), dir, 0);
		map.addNpc(npc);
		people.add(npc);
		return npc;
	}

	@Override
	public void start() {
	}

	@Override
	public void tick() {
		ticks++;
		// the Quartermaster: "Stock available!" (+5), "Shop's open!", then every 12
		if (quartermaster != null && ticks >= 5 && (ticks - 5) % 12 == 0)
			quartermaster.forceText(((ticks - 5) / 12) % 2 == 0 ? "Stock available!" : "Shop's open!");
		if (ivy != null) {
			if (visit == 4 && ticks >= 12 && (ticks - 12) % 14 == 0)
				ivy.forceText("Give them no quarter, pick a side!");
			if (visit == 5 && ticks == 9)
				ivy.forceText("The situation is almost under control!");
			if (visit == 5 && ticks == 22)
				ivy.forceText("Check the vault for danger and let's end this!");
		}
		for (int[] w : workers) {
			if (w[0] < 0 || w[0] >= people.size())
				continue;
			if (ticks % w[2] == 0)
				people.get(w[0]).animate(w[1]);
		}
	}

	@Override
	public void onNpcDeath(NPC npc) {
	}

	@Override
	public void cleanup() {
		for (NPC npc : people)
			if (!npc.isRemoved())
				npc.remove();
		people.clear();
	}

	@Override
	public boolean isStarted() {
		return true;
	}

	@Override
	public boolean isComplete() {
		return true;
	}

	/**
	 * RSProx nexus_plan 6: back from a boss, a raider lands on a random tile of a 3x3 beside that side's door, facing
	 * into the atrium: from the west x 4114..4116 facing east, from the east 4138..4140 facing west; the rows by visit
	 * y 5585..5587 (A1, after stage 2), 5598..5600 (A2), 5611..5613 (A3). Local to the atrium square (64,87).
	 */
	@Override
	public Position entryPosition() {
		if (from == null)
			return controller.getInstance().walkable(type(), NEXUS_ARRIVAL_LOCAL_X, NEXUS_ARRIVAL_LOCAL_Y, 0, 4);
		int x0 = from == Raid4RoomType.Side.WEST ? 18 : 42;
		int y0 = visit <= 3 ? 17 : visit == 4 ? 30 : 43;
		return controller.getInstance().walkable(type(), x0 + io.ruin.api.utils.Random.get(2), y0 + io.ruin.api.utils.Random.get(2), 0, 2);
	}

	@Override
	public void onEnter(Player player) {
		if (from != null) // facing into the atrium, away from the door they came through
			player.face(player.getPosition().relative(from == Raid4RoomType.Side.WEST ? 5 : -5, 0));
	}

	@Override
	public Position spectatePosition() {
		return entryPosition();
	}
}
