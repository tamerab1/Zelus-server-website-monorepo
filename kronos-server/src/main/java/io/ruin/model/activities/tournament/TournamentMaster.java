package io.ruin.model.activities.tournament;

import io.ruin.api.utils.Random;
import io.ruin.model.World;
import io.ruin.model.entity.npc.NPC;
import io.ruin.model.entity.npc.NPCAction;
import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.dialogue.NPCDialogue;
import io.ruin.model.inter.dialogue.OptionsDialogue;
import io.ruin.model.inter.utils.Option;

/**
 * The Zelus Tournament Master (NPC 10528, renamed + dressed in the cache; spawned at home 3072,3476 facing east).
 * Talk-to: tournament info with Join / View tournaments; View-Tournaments: the tournament hub. While registration is
 * open (or a tournament is coming up) he cheers and shouts it overhead; the shout is re-sent every
 * {@link #REFRESH_TICKS} ticks so it stays up for {@link #VISIBLE_TICKS} ticks (an overhead message alone lasts ~3s).
 */
public final class TournamentMaster {

	public static final int NPC_ID = 10528;
	private static final int CHEER = 862;
	private static final int VISIBLE_TICKS = 34, REFRESH_TICKS = 2, GAP_MIN_TICKS = 16, GAP_MAX_TICKS = 32;
	private static final String EXAMINE = "The master of competitive Zelus tournaments.";

	private TournamentMaster() {
	}

	public static void register() {
		NPCAction.register(NPC_ID, 1, (player, npc) -> talk(player, npc));
		NPCAction.register(NPC_ID, 2, (player, npc) -> TournamentHub.open(player));
		World.startEvent(e -> {
			while (true) {
				e.delay(Random.get(GAP_MIN_TICKS, GAP_MAX_TICKS));
				String line = announcement();
				if (line == null)
					continue;
				for (NPC npc : World.npcsSlots())
					if (npc.getId() == NPC_ID)
						npc.animate(CHEER);
				for (int t = 0; t < VISIBLE_TICKS; t += REFRESH_TICKS) {
					String now = announcement();
					if (now == null)
						break;
					for (NPC npc : World.npcsSlots())
						if (npc.getId() == NPC_ID)
							npc.forceText(now);
					e.delay(REFRESH_TICKS);
				}
			}
		});
	}

	/** The overhead line for the current tournament state, or null when there is nothing to announce. */
	private static String announcement() {
		String name = TournamentHandler.format().name;
		switch (TournamentHandler.state()) {
			case OPEN:
				return "[Tournament]: " + name + " registration is open! Talk to me to join.";
			case SCHEDULED: {
				long minutes = Math.max(1, (TournamentHandler.upcomingStart(0) - System.currentTimeMillis() + 59_999) / 60_000L);
				return minutes <= 30 ? "[Tournament]: " + name + " starts in " + minutes + " min - registration opens "
					+ TournamentHandler.REGISTRATION_MINUTES + " min before!" : null;
			}
			default:
				return null;
		}
	}

	private static void talk(Player player, NPC npc) {
		TournamentHandler.State state = TournamentHandler.state();
		String name = TournamentHandler.format().name;
		String intro = switch (state) {
			case OPEN -> "Registration for the " + name + " is open! " + TournamentHandler.entrants() + " / "
				+ TournamentHandler.bracketSize() + " fighters have signed up.";
			case RUNNING -> "The " + name + " is underway in the Pit. Come back for the next one!";
			default -> {
				long minutes = Math.max(1, (TournamentHandler.upcomingStart(0) - System.currentTimeMillis() + 59_999) / 60_000L);
				yield "The next tournament is the " + name + " in about " + minutes + " minutes. Registration opens "
					+ TournamentHandler.REGISTRATION_MINUTES + " minutes before it starts.";
			}
		};
		player.dialogue(new NPCDialogue(npc, intro),
			new OptionsDialogue("Tournament Master",
				new Option(state == TournamentHandler.State.OPEN ? "Join the tournament." : "Join the tournament (not open yet).",
					() -> TournamentHandler.join(player)),
				new Option("View tournaments.", () -> TournamentHub.open(player)),
				new Option("Nevermind.")));
	}

	/** NpcActionHandler examine hook: true if handled. */
	public static boolean examine(Player player, int npcId) {
		if (npcId != NPC_ID)
			return false;
		player.sendMessage(EXAMINE);
		return true;
	}
}
