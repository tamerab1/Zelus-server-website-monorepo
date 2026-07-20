package io.ruin.model.entity.npc.actions.edgeville;

import io.ruin.cache.Icon;
import io.ruin.model.World;
import io.ruin.model.entity.npc.NPCAction;
import io.ruin.model.inter.dialogue.OptionsDialogue;
import io.ruin.model.inter.utils.Option;
import io.ruin.utility.Broadcast;

import static io.ruin.process.event.EventWorker.startEvent;

public class VoteManager {

	private static final String VOTE_URL = World.type.getWebsiteUrl() + "vote";

	private static int voteMysteryBoxesClaimed = 0;

	public static void register() {
		NPCAction.register(4058, "vote", (player, npc) -> {
			player.dialogue(
				new OptionsDialogue("What would you like to do?",
					new Option("Vote for " + World.type.getWorldName(), () -> player.openUrl("Voting Page", VOTE_URL)),
					new Option("View my vote streak", () -> player.getDailyVote().open()),
					new Option("Nothing, thanks.", player::closeDialogue)
				)
			);
		});
		startEvent(e -> {
			while (true) {
				e.delay(3000); //30 minutes
				if (voteMysteryBoxesClaimed > 1) {
					Broadcast.WORLD.sendNews(Icon.ANNOUNCEMENT, "Another " + voteMysteryBoxesClaimed + " players have claimed their Voting Box! Type ::vote and claim yours now!");
					voteMysteryBoxesClaimed = 0;
				}
			}
		});
	}

}
