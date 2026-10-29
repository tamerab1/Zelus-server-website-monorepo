package io.ruin.model.activities.raids.tfa;

import io.ruin.api.utils.Random;
import io.ruin.model.World;
import io.ruin.model.entity.npc.NPC;
import io.ruin.model.entity.npc.NPCAction;
import io.ruin.model.inter.dialogue.NPCDialogue;
import io.ruin.model.map.Direction;

/**
 * The Archive lobby's people (region 16470), at the tiles the live RuneLite fight logs recorded them on
 * (Julian-Dasilva/ArchiveOcellusTrainer docs/fight-logs: "appears at 16470(x, y)", and offsets read against the
 * banker's), with their cache ops: the Banker (Talk-to / Bank / Collect), the Record Keeper, General Montoya and the
 * Wizard apprentice (Talk-to), two druids, a temple knight and four Alliance builders at work.
 */
public final class Raid4LobbyNpcs {

	private Raid4LobbyNpcs() {
	}

	static final int BANKER = 16704, RECORD_KEEPER = 16692, MONTOYA = 16705, APPRENTICE = 16693, DRUID_1 = 16694,
			DRUID_2 = 16695, TEMPLE_KNIGHT = 16710;
	static final int[] BUILDERS = { 16706, 16707, 16708, 16709 };
	/** {id, local x, local y} in region 16470 (base 4096, 5504). */
	static final int[][] SPAWNS = { { BANKER, 38, 49 }, { RECORD_KEEPER, 40, 39 }, { MONTOYA, 21, 46 }, { APPRENTICE, 19, 35 },
			{ DRUID_1, 21, 42 }, { DRUID_2, 42, 31 }, { TEMPLE_KNIGHT, 34, 30 }, { 16706, 33, 45 }, { 16707, 31, 50 },
			{ 16708, 27, 44 }, { 16709, 28, 45 } };
	static final int SEQ_PICKUP_FLOOR = 827, SEQ_ADD_FEATHER = 8481;

	public static void register() {
		for (int[] s : SPAWNS) {
			// RSProx (cutscene_timeline): the banker sits SW at her desk and the Record Keeper stands N, neither moves;
			// both have seated/static idles (15055/15057) that break if they wander
			boolean seated = s[0] == BANKER || s[0] == RECORD_KEEPER;
			Direction face = s[0] == BANKER ? Direction.SOUTH_WEST : s[0] == RECORD_KEEPER ? Direction.NORTH : Direction.SOUTH;
			NPC npc = new NPC(s[0]).spawn(4096 + s[1], 5504 + s[2], 0, face, seated ? 0 : 2);
			for (int b : BUILDERS)
				if (b == s[0])
					work(npc);
		}
		NPCAction.register(BANKER, 1, (p, n) -> p.dialogue(new NPCDialogue(n, "Good day. Your bank is safe with us while you're in the Archive.")));
		NPCAction.register(BANKER, 3, (p, n) -> p.getBank().open());
		NPCAction.register(BANKER, 4, (p, n) -> p.getBank().open());
		NPCAction.register(RECORD_KEEPER, 1, (p, n) -> {
			int kc = p.fracturedArchiveKills.getKills();
			p.dialogue(new NPCDialogue(n, "I keep the records of every party that enters the Archive. You have completed "
					+ "The Fractured Archive " + kc + " time" + (kc == 1 ? "" : "s") + "."));
		});
		NPCAction.register(MONTOYA, 1, (p, n) -> p.dialogue(
				new NPCDialogue(n, "The Archive is breached. Form a party at the grouping board, then head through the barrier."),
				new NPCDialogue(n, "Beat the Guardian, pick a side at each door and push on to Ocellus. Don't let the Archive fall.")));
		NPCAction.register(APPRENTICE, 1, (p, n) -> p.dialogue(new NPCDialogue(n,
				"The magic in here is like nothing I've studied. Mind the cracks on the floor in there.")));
	}

	/** A builder at work: picks something up, adds a feather, walks a few tiles, 10 and 7 ticks apart in turn. */
	private static void work(NPC npc) {
		World.startEvent(e -> {
			boolean feather = false;
			while (!npc.isRemoved()) {
				e.delay(feather ? 7 : 10);
				npc.animate(feather ? SEQ_ADD_FEATHER : SEQ_PICKUP_FLOOR);
				feather = !feather;
				if (Random.get(3) == 0)
					npc.stepAbs(npc.spawnPosition.getX() + Random.get(-2, 2), npc.spawnPosition.getY() + Random.get(-2, 2),
							io.ruin.model.entity.shared.StepType.NORMAL);
			}
		});
	}
}
