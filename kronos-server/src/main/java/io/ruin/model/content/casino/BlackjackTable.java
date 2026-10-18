package io.ruin.model.content.casino;

import io.ruin.model.World;
import io.ruin.model.entity.npc.NPC;
import io.ruin.model.entity.npc.NPCAction;
import io.ruin.model.inter.dialogue.NPCDialogue;
import io.ruin.model.inter.dialogue.OptionsDialogue;
import io.ruin.model.inter.utils.Option;
import io.ruin.model.map.Direction;
import io.ruin.model.map.Position;
import io.ruin.model.map.object.actions.ObjectAction;

/**
 * The Zelus Blackjack Table (object 62442, .dev/cache-restore-tool BuildBlackjackTable, 2026-10-18): option 1
 * "Play Blackjack" opens the existing Blackjack window. Nothing else of Blackjack is touched. Spawned at 3122,3464
 * (custom_objects.json, rotation 2: playing side north). The two Mounted Max Capes that overlapped its tiles are gone
 * from the MAP FILE itself (.dev/cache-restore-tool RemoveMapLocs): removing them at runtime deleted the alcove's
 * diagonal wall corner on the client (same scene layer) and cleared the table's own clipping on the server. The old
 * BJ Dealer NPCs are no longer spawned (HomeHandler).
 * <p>
 * Its dealer, npc 30609 "Blackjack Dealer" (build_blackjack_dealer.sh: tuxedo with a crimson Zelus bow tie), stands
 * behind the table's straight side at 3123,3463 facing north: Talk-to greets, Play Blackjack opens the same window.
 */
public class BlackjackTable {
	public static final int OBJECT = 62442, DEALER = 30609;
	private static final Position DEALER_SPOT = new Position(3123, 3463, 0);

	public static void register() {
		ObjectAction.register(OBJECT, 1, (player, obj) -> io.ruin.model.content.blackjack.BlackjackInterface.open(player));
		NPCAction.register(DEALER, "Play Blackjack", (player, npc) -> io.ruin.model.content.blackjack.BlackjackInterface.open(player));
		NPCAction.register(DEALER, "Talk-to", (player, npc) -> player.dialogue(
				new NPCDialogue(npc, "Welcome to the Zelus tables, " + player.getName() + "! Blackjack pays 3 to 2 and "
						+ "the dealer stands on all 17s. Care to take a seat?"),
				new OptionsDialogue("Play Blackjack?",
						new Option("Deal me in.", () -> io.ruin.model.content.blackjack.BlackjackInterface.open(player)),
						new Option("Maybe later.", player::closeDialogue))));
		World.startEvent(e -> {
			e.delay(1); // once the world is up
			new NPC(DEALER).spawn(DEALER_SPOT, Direction.NORTH);
		});
	}
}
