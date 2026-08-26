package io.ruin.model.activities.tournament;

import io.ruin.model.entity.player.Player;

/**
 * A single 1v1 fight within a running tournament bracket.
 */
class TournamentMatch {

	private final Player p1;
	private final Player p2;

	TournamentMatch(Player p1, Player p2) {
		this.p1 = p1;
		this.p2 = p2;
	}

	void start() {
		p1.getCombat().restore();
		p2.getCombat().restore();

		p1.getMovement().teleport(TournamentHandler.ARENA_SPAWN_A);
		p2.getMovement().teleport(TournamentHandler.ARENA_SPAWN_B);

		attach(p1, p2);
		attach(p2, p1);

		p1.sendMessage("Your tournament match against " + p2.getName() + " has begun!");
		p2.sendMessage("Your tournament match against " + p1.getName() + " has begun!");
	}

	private void attach(Player player, Player opponent) {
		player.attackPlayerListener = (attacker, target, message) -> {
			if (target != opponent) {
				if (message)
					attacker.sendMessage("You can only attack your tournament opponent.");
				return false;
			}
			return true;
		};
		player.deathEndListener = (entity, killer, killHit) -> handleDeath(entity.player);
		player.teleportListener = p -> {
			p.sendMessage("You can't teleport out of a tournament match.");
			return false;
		};
	}

	private void handleDeath(Player loser) {
		Player winner = loser == p1 ? p2 : p1;

		detach(p1);
		detach(p2);

		if (loser.getCombat().isDead()) {
			loser.getCombat().setDead(false);
			loser.animate(-1, 0);
		}
		loser.getCombat().resetKillers();
		loser.getCombat().restore();

		winner.getCombat().restore();

		TournamentHandler.onMatchComplete(winner, loser);
	}

	private void detach(Player player) {
		player.attackPlayerListener = null;
		player.deathEndListener = null;
		player.teleportListener = null;
	}
}
