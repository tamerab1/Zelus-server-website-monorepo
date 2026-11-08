package io.ruin.model.entity.player.groupironmode.hook;

import io.ruin.HooksV2.Result;
import io.ruin.cache.Color;
import io.ruin.model.activities.DonationBossHandler;
import io.ruin.model.activities.VoteBossHandler;
import io.ruin.model.activities.tempevents.summerevent.SummerEvent;
import io.ruin.model.combat.Killer;
import io.ruin.model.entity.player.GameMode;
import io.ruin.model.entity.player.Player;
import io.ruin.model.entity.player.PlayerCombat;
import io.ruin.model.var.VarPlayerRepository;
import lombok.experimental.ExtensionMethod;

import static io.ruin.model.entity.player.GameMode.IRONMAN;

@ExtensionMethod(Attributes.class)
public class GroupDeathHook {

	/**
	 * 2026-11-06: was a PlayerCombat OnDeath hook that nothing registered (and OnDeath only runs for wilderness / PvP
	 * deaths, and its Break results would have cut off the other death hooks for every non-group player), so hardcore
	 * groups never lost a life. Now GameMode.groupDeath calls this on every Hardcore Group Ironman death outside the safe
	 * areas (GameMode.safeHardcoreDeath).
	 */
	public static void register() {
		GameMode.hardcoreGroupDeath = GroupDeathHook::loseLife;
	}

	static void loseLife(Player player) {
		var group = player.getGroupIron();
		if (group == null)
			return;
		player.sendMessage(Color.RED.wrap("You have fallen as a Hardcore Group Ironman, one of your group lives has been lost!"));
		group.updateGroupLives(-1); // at 0 it revokes hardcore for every online member; PlayerHook does it at login for the rest
		if (group.getLivesRemaining() < 1 && player.getGameMode() == GameMode.HARDCORE_GROUP_IRONMAN) {
			player.sendMessage("Your group has lost all of their lives and your hardcore group status has been revoked.");
			VarPlayerRepository.IRONMAN_MODE.set(player, GameMode.GROUP_IRONMAN.ordinal());
		}
	}
}
