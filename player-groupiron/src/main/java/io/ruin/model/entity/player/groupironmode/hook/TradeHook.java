package io.ruin.model.entity.player.groupironmode.hook;

import io.ruin.HooksV2.Result;
import io.ruin.model.entity.player.GameMode;
import io.ruin.model.item.containers.Trade;
import lombok.experimental.ExtensionMethod;

@ExtensionMethod(Attributes.class)
public class TradeHook implements Trade.Hook {

	public static void register() {
		Trade.hooks.register(Trade.Hook.OnRequest.class, TradeHook::handle);
	}

	private static Result handle(Trade.Hook.OnRequest ctx) {
		var player = ctx.player();
		var target = ctx.target();

		var admin = player.isManager() || target.isAdmin() || target.isManager();
		var pGameMode = player.getGameMode();
		if (admin) {
			return Result.Pass;
		}

		// Any ironman variant (solo/hardcore/ultimate/group) on EITHER side gates the trade.
		// Group/Hardcore Group Ironmen may trade with their own group's members; every other
		// combination is blocked -- this used to only check one side at a time (and only
		// blocked solo ironmen via a getGroupIron() != null check that's never true for them),
		// letting a plain account freely trade items into any ironman or group-ironman account.
		boolean playerIronMan = pGameMode.isIronMan();
		boolean targetIronMan = target.getGameMode().isIronMan();
		if (playerIronMan || targetIronMan) {
			boolean playerGroup = pGameMode.isAnyOf(GameMode.GROUP_IRONMAN, GameMode.HARDCORE_GROUP_IRONMAN);
			boolean targetGroup = target.getGameMode().isAnyOf(GameMode.GROUP_IRONMAN, GameMode.HARDCORE_GROUP_IRONMAN);
			boolean sameGroup = playerGroup && targetGroup && player.newGroupId != 0
					&& player.newGroupId == target.newGroupId;
			if (!sameGroup) {
				player.sendMessage("Ironman stand alone.");
				return Result.Return;
			}
		}

		return Result.Pass;
	}

}
