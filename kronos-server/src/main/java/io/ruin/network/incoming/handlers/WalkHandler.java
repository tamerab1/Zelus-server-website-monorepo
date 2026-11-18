package io.ruin.network.incoming.handlers;

import io.ruin.api.buffer.InBuffer;
import io.ruin.model.World;
import io.ruin.model.entity.npc.NPC;
import io.ruin.model.entity.player.Player;
import io.ruin.network.incoming.Incoming;
import io.ruin.utility.IdHolder;
import net.rsprot.protocol.game.incoming.misc.user.MoveGameClick;
import net.rsprot.protocol.game.incoming.misc.user.MoveMinimapClick;
import net.rsprot.protocol.message.codec.incoming.MessageConsumer;

import static io.ruin.network.ClientProt204.*;

@IdHolder(ids = {MOVE_GAMECLICK, MOVE_MINIMAPCLICK})
public class WalkHandler implements Incoming {

	public static class Minimap implements MessageConsumer<Player, MoveMinimapClick> {

		@Override
		public void consume(Player player, MoveMinimapClick msg) {
			handle(player, msg.getX(), msg.getZ(), msg.getKeyCombination());
		}
	}

	public static class Game implements MessageConsumer<Player, MoveGameClick> {

		@Override
		public void consume(Player player, MoveGameClick msg) {
			var boat = player.boat;
			if (boat != null) { // sailing: a click beside the boat arrives in sea coords, map it onto the deck
				if (boat.navigating)
					return;
				var dest = boat.resolveClick(msg.getX(), msg.getZ(), player.getHeight());
				handle(player, dest.getX(), dest.getY(), msg.getKeyCombination());
				return;
			}
			handle(player, msg.getX(), msg.getZ(), msg.getKeyCombination());
		}
	}

	@Override
	public void handle(Player player, InBuffer in, int opcode) {
	}

	private static void handle(Player player, int x, int y, int type) {
		// Hespori vines: a walk click while entangled is a struggle, not a step
		if (io.ruin.model.activities.bosses.Hespori.struggle(player))
			return;
		// Eclipse Moon clones: a click while bound turns the player to face it (parry)
		if (io.ruin.model.activities.moonsofperil.bosses.EclipseMoon.faceClick(player, x, y))
			return;
		if (player.isLocked() || player.isStunned()) {
			/* is our player new to the game? */
			if (player.inTutorial) {
				return;
			}
			/* is our player currently transformed? */ // TODO Inspect the backlashes but we shouldnt be unmorphing if locked.
			/*
			 * if (player.getAppearance().getNpcId() != -1)
			 * TransformationRing.unmorph(player);
			 * else
			 * return;
			 */
			/* if they're viewing an orb, reset viewing orb */
			if (player.usingTournamentOrb) {

				return;
			}

			// Return always if locked why let them move?
			return;
		}

		if (player.emoteDelay.isDelayed()) {
			player.resetAnimation();
			player.emoteDelay.reset();
		}

		player.resetActions(true, true, true);
		if (player.isAdmin() || World.isDev() || player.isOwner()) {
			NPC npc = player.get("CONTROLLING_NPC");
			if (npc != null) {
				if (type == 2) {
					npc.getMovement().teleport(x, y, player.getHeight());
				} else {
					npc.getRouteFinder().routeAbsolute(x, y);
				}
				return;
			} else if (type == 2) {
				int z = player.getHeight();
				player.getMovement().teleport(x, y, z);
				player.sendFilteredMessage("<col=cc0000>::tele: " + x + "," + y + "," + z);
				return;
			}
		}
		player.getMovement().setCtrlRun(type == 1);
		player.getRouteFinder().routeAbsolute(x, y, true);

	}

}
