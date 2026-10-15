package io.ruin.model.activities.tournament;

import io.ruin.model.World;
import io.ruin.model.entity.player.Player;
import io.ruin.utility.Random;

/**
 * A single 1v1 fight within a running tournament bracket. A match that is still going after
 * {@link #TIME_LIMIT_TICKS} is won by the player with more hitpoints left (a coin flip on a tie).
 */
class TournamentMatch {

	/** 5 minutes. */
	static final int TIME_LIMIT_TICKS = 500;
	private static final int WARNING_TICKS = 400;

	private final Player p1;
	private final Player p2;
	private boolean finished;

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

		World.startEvent(e -> {
			e.delay(WARNING_TICKS);
			if (finished)
				return;
			p1.sendMessage("<col=ff0000>One minute left! If time runs out, the player with more hitpoints wins.");
			p2.sendMessage("<col=ff0000>One minute left! If time runs out, the player with more hitpoints wins.");
			e.delay(TIME_LIMIT_TICKS - WARNING_TICKS);
			if (finished)
				return;
			int hp1 = p1.getHp(), hp2 = p2.getHp();
			Player loser = hp1 == hp2 ? (Random.rollPercent(50) ? p1 : p2) : hp1 < hp2 ? p1 : p2;
			p1.sendMessage("Time's up! " + (loser == p1 ? p2 : p1).getName() + " wins on hitpoints.");
			p2.sendMessage("Time's up! " + (loser == p1 ? p2 : p1).getName() + " wins on hitpoints.");
			finish(loser, false);
		});
	}

	boolean involves(Player player) {
		return player == p1 || player == p2;
	}

	String description() {
		return p1.getName() + " vs " + p2.getName();
	}

	/** The player logged out mid-match. */
	void forfeit(Player player) {
		finish(player, true);
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
		player.deathEndListener = (entity, killer, killHit) -> finish(entity.player, false);
		player.teleportListener = p -> {
			p.sendMessage("You can't teleport out of a tournament match.");
			return false;
		};
	}

	private void finish(Player loser, boolean forfeited) {
		if (finished)
			return;
		finished = true;
		Player winner = loser == p1 ? p2 : p1;

		detach(p1);
		detach(p2);

		if (loser.getCombat().isDead()) {
			loser.getCombat().setDead(false);
			loser.animate(-1, 0);
		}
		loser.getCombat().resetKillers();
		loser.getCombat().restore();

		winner.getCombat().reset();
		winner.getCombat().restore();

		TournamentHandler.onMatchComplete(winner, loser, forfeited);
	}

	private void detach(Player player) {
		player.attackPlayerListener = null;
		player.deathEndListener = null;
		player.teleportListener = null;
	}
}
