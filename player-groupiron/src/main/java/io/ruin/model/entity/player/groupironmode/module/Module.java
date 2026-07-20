package io.ruin.model.entity.player.groupironmode.module;

import core.module.api.IModule;
import io.ruin.model.entity.player.groupironmode.*;
import io.ruin.model.entity.player.groupironmode.hook.*;

public class Module implements IModule {

	@Override
	public void start() {
		Attributes.register();
		CommandsHook.register();
		PlayerHook.register();
		GroundItemHook.register();
		HouseHook.register();
		JournalTabHook.register();
		MapHook.register();
		ObjectHook.register();
		PlayerActionHook.register();
		TradeHook.register();
		TutorialHook.register();

		GroupSettingsTabInterface.register();

		GroupIronGroups.register();
		GroupBank.register();
	}
}
