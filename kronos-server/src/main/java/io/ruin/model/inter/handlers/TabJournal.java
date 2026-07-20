package io.ruin.model.inter.handlers;

import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.ToplevelComponent;

public class TabJournal {

	public static void register() {
		// Questtab click handling is done in ActionButtonHandler (interface 845 COUNTS < 8)
	}

	public static void swap(Player player, int interfaceId) {
		player.getPacketSender().sendInterface(interfaceId, ToplevelComponent.QUEST_TAB_AREA);

//        if(player.isFixedScreen())
//            player.getPacketSender().sendInterface(interfaceId, 548, 68, ClientInterfaceType.OVERLAY);
//        else if(player.getGameFrameId() == ToplevelInterfaceType.RESIZABLE_TABS)
//            player.getPacketSender().sendInterface(interfaceId, 164, 70, ClientInterfaceType.OVERLAY);
//        else
//            player.getPacketSender().sendInterface(interfaceId, 161, 70, ClientInterfaceType.OVERLAY);
	}

	public static void restore(Player player) {
//        swap(player, Interface.NOTICEBOARD);
		//TabQuest.send(player);
//        QuestTab.MAIN.send(player);
		//player.journal.send(player);
	}

}
