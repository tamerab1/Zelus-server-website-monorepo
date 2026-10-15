package tradepost.hook;

import io.ruin.HooksV2.Result;
import io.ruin.HooksV2.Hook;
import io.ruin.network.incoming.handlers.CommandHandler;
import tradepost.inter.ZelusMarket;
import tradepost.module.Module;

public class Commands implements Hook<CommandHandler.Hook.Handle> {

	public static void register() {
		CommandHandler.hooks.register(CommandHandler.Hook.Handle.class, new Commands());
	}

	@Override
	public Result handle(CommandHandler.Hook.Handle ctx) {
		var player = ctx.player();
		var command = ctx.command();

		switch (command) {
			case "market", "zelusmarket", "tradepost" -> {
				// anywhere but the Wilderness / mid-fight (the GE booths and clerks work as before)
				if (player.wildernessLevel > 0 || player.getCombat().isDefending(17)) {
					player.sendMessage("You can't open the Zelus Market in the Wilderness or during combat.");
					return Result.Return;
				}
				ZelusMarket.open(player);
				return Result.Return;
			}
		}

		if (!player.isAdmin()) {
			return Result.Pass;
		}

		switch (command) {
			case "tp_disable" -> {
				Module.ENABLED = false;
				return Result.Return;
			}

			case "tp_enable" -> {
				Module.ENABLED = true;
				return Result.Return;
			}
		}

		return Result.Pass;
	}
}
