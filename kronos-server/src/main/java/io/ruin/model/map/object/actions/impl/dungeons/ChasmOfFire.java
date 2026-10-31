package io.ruin.model.map.object.actions.impl.dungeons;

import io.ruin.model.entity.player.Player;
import io.ruin.model.entity.shared.LockType;
import io.ruin.model.entity.shared.StepType;
import io.ruin.model.map.Direction;
import io.ruin.model.inter.dialogue.MessageDialogue;
import io.ruin.model.map.object.actions.ObjectAction;
import io.ruin.model.map.object.actions.impl.Ladder;

public class ChasmOfFire {

	private static void lift(Player player, boolean down) {
		player.startEvent(event -> {
			player.lock();
			player.dialogue(new MessageDialogue("You step inside the gibbet. <br> The chain creaks as you " + (down ? "descend further into the Chasm." :
				"ascend towards the surface.")).hideContinue().lineHeight(30));
			player.getPacketSender().fadeOut();
			event.delay(2);
			player.getMovement().teleport(player.getAbsX(), player.getAbsY(), player.getHeight() + (down ? -1 : +1));
			event.delay(1);
			player.dialogue(new MessageDialogue("You step inside the gibbet. <br> The chain creaks as you " + (down ? "descend further into the Chasm." :
				"ascend towards the surface.")).lineHeight(30));
			player.getPacketSender().fadeIn();
			player.unlock();
		});
	}

	/**
	 * Rev 241's two "Gap" jumps (object 56372) over the crack on the black demon level: each lands on the solid
	 * ground across it, west or east, from whichever side the player stands (landing tiles from the 241 terrain).
	 */
	private static final int GAP = 56372;
	private static final int[][] GAPS = {
		// gap x, gap y, west landing x, east landing x (both landings on the gap's row)
		{1443, 10097, 1442, 1446},
		{1450, 10097, 1449, 1452},
	};

	private static void jumpGap(Player player, int[] gap) {
		boolean east = player.getAbsX() < gap[0];
		int dx = (east ? gap[3] : gap[2]) - player.getAbsX(), dy = gap[1] - player.getAbsY();
		player.startEvent(event -> {
			player.lock(LockType.FULL_DELAY_DAMAGE);
			player.animate(1995, 15);
			event.delay(1);
			player.animate(1603);
			player.getMovement().force(dx, dy, 0, 0, 8, 50, east ? Direction.EAST : Direction.WEST);
			event.delay(2);
			player.unlock();
		});
	}

	public static void register() {
		/**
		 * Gaps
		 */
		for (int[] gap : GAPS)
			ObjectAction.register(GAP, gap[0], gap[1], 1, "jump", (player, obj) -> jumpGap(player, gap));

		/**
		 * Entrance/exit
		 */
		ObjectAction.register(30236, 1436, 3671, 0, "enter", (player, obj) -> player.startEvent(event -> {
			player.lock();
			player.stepAbs(1436, 3671, StepType.WALK);
			event.delay(1);
			Ladder.climb(player, 1435, 10077, 3, false, true, false);
			player.unlock();
		}));
		ObjectAction.register(30234, 1435, 10078, 3, "climb-up", (player, obj) -> Ladder.climb(player, 1435, 3671, 0, true, true, false));

		/**
		 * Lift
		 */
		ObjectAction.register(30258, 1437, 10094, 3, "enter", (player, obj) -> lift(player, true));
		ObjectAction.register(30258, 1452, 10068, 3, "enter", (player, obj) -> lift(player, true));
		ObjectAction.register(30258, 1458, 10075, 2, "enter", (player, obj) -> lift(player, true));
		ObjectAction.register(30259, 1437, 10094, 2, "enter", (player, obj) -> lift(player, false));
		ObjectAction.register(30259, 1452, 10068, 2, "enter", (player, obj) -> lift(player, false));
		ObjectAction.register(30258, 1458, 10095, 2, "enter", (player, obj) -> lift(player, true));
		ObjectAction.register(30259, 1458, 10095, 1, "enter", (player, obj) -> lift(player, false));
		ObjectAction.register(30259, 1458, 10075, 1, "enter", (player, obj) -> lift(player, false));
	}
}
