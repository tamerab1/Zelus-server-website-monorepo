package core.reason.module;

import core.module.api.IModule;
import io.ruin.HooksV2;
import io.ruin.model.content.blackjack.BlackjackInterface;
import io.ruin.network.incoming.handlers.CommandHandler;

public class BlackjackModule implements IModule {

	@Override
	public void init() {
		CommandHandler.hooks.register(CommandHandler.Hook.Handle.class, ctx -> {
			switch (ctx.command()) {
				case "blackjack", "bj" -> {
					if (ctx.player().jailerName != null) {
						ctx.player().sendMessage("You cannot use commands while in jail.");
						return HooksV2.Result.Return;
					}
					if (ctx.player().isLocked()) {
						ctx.player().sendMessage("Please finish what you're doing first.");
						return HooksV2.Result.Return;
					}
					BlackjackInterface.open(ctx.player());
					return HooksV2.Result.Return;
				}
				default -> {
					return HooksV2.Result.Pass;
				}
			}
		});
	}
}
