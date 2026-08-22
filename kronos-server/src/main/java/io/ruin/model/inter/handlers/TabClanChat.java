package io.ruin.model.inter.handlers;

import io.ruin.model.inter.InterfaceHandler;
import io.ruin.model.inter.ToplevelComponent;
import io.ruin.model.inter.actions.SimpleAction;
import io.ruin.model.var.VarPlayerRepository;

import static io.ruin.model.inter.InterfaceEventMask.ClickOp1;
import static io.ruin.model.inter.InterfaceEventMask.getMask;

public class TabClanChat {

	public static void register() {
		InterfaceHandler.register(707, h ->
		{
			h.actions[3] = (SimpleAction) p -> {
				boolean iron = p.getGameMode().isGroupIronman();
				p.getPacketSender().sendToplevelSubInterface(7, 7, iron ? ToplevelComponent.GROUP_CLAN_TAB_AREA : ToplevelComponent.CLAN_TAB_AREA);
				VarPlayerRepository.CLAN_ACTIVE_TAB.set(p.player, 0);
			};
			h.actions[4] = (SimpleAction) p -> {
				boolean iron = p.getGameMode().isGroupIronman();
				p.getPacketSender().sendToplevelSubInterface(701, 7, iron ? ToplevelComponent.GROUP_CLAN_TAB_AREA : ToplevelComponent.CLAN_TAB_AREA);
				VarPlayerRepository.CLAN_ACTIVE_TAB.set(p.player, 1);
			};
			h.actions[5] = (SimpleAction) p -> {
				boolean iron = p.getGameMode().isGroupIronman();
				p.getPacketSender().sendToplevelSubInterface(702, 7, iron ? ToplevelComponent.GROUP_CLAN_TAB_AREA : ToplevelComponent.CLAN_TAB_AREA);
				VarPlayerRepository.CLAN_ACTIVE_TAB.set(p.player, 2);
			};
			h.actions[6] = (SimpleAction) p -> {
				// Clan Settings has no real backing implementation -- no clan ownership/rank
				// model exists server-side, and neither interface 76's buttons nor the
				// CLANCHANNEL_KICKUSER/AFFINEDCLANSETTINGS_* protocol messages have a
				// registered handler anywhere, so every action in it is currently a silent
				// no-op. Gated to staff only so regular players aren't shown a dead interface
				// that looks functional but does nothing.
				if (!p.player.isStaff()) {
					p.player.sendMessage("Clan settings aren't available yet.");
					return;
				}
				boolean iron = p.getGameMode().isGroupIronman();
				p.getPacketSender().sendClientScript(828, "i", 1);
				p.getPacketSender().sendToplevelSubInterface(76, 7, iron ? ToplevelComponent.GROUP_CLAN_TAB_AREA : ToplevelComponent.CLAN_TAB_AREA);
				p.getPacketSender().sendIfEvents(76, 26, 0, 23, getMask(ClickOp1));
				VarPlayerRepository.CLAN_ACTIVE_TAB.set(p.player, 3);
			};
		});
		InterfaceHandler.register(727, h ->
		{
			h.actions[3] = (SimpleAction) p -> {
				boolean iron = p.getGameMode().isGroupIronman();
				p.getPacketSender().sendToplevelSubInterface(7, 7, iron ? ToplevelComponent.GROUP_CLAN_TAB_AREA : ToplevelComponent.CLAN_TAB_AREA);
				VarPlayerRepository.CLAN_ACTIVE_TAB.set(p.player, 0);
			};
			h.actions[4] = (SimpleAction) p -> {
				boolean iron = p.getGameMode().isGroupIronman();
				p.getPacketSender().sendToplevelSubInterface(701, 7, iron ? ToplevelComponent.GROUP_CLAN_TAB_AREA : ToplevelComponent.CLAN_TAB_AREA);
				VarPlayerRepository.CLAN_ACTIVE_TAB.set(p.player, 1);
			};
			h.actions[5] = (SimpleAction) p -> {
				boolean iron = p.getGameMode().isGroupIronman();
				p.getPacketSender().sendToplevelSubInterface(702, 7, iron ? ToplevelComponent.GROUP_CLAN_TAB_AREA : ToplevelComponent.CLAN_TAB_AREA);
				VarPlayerRepository.CLAN_ACTIVE_TAB.set(p.player, 2);
			};
			h.actions[6] = (SimpleAction) p -> {
				// See the identical check above (interface 707's action[6]) for why this is
				// staff-gated: no real backing implementation exists for it yet.
				if (!p.player.isStaff()) {
					p.player.sendMessage("Clan settings aren't available yet.");
					return;
				}
				boolean iron = p.getGameMode().isGroupIronman();
				p.getPacketSender().sendClientScript(828, "i", 1);
				p.getPacketSender().sendToplevelSubInterface(76, 7, iron ? ToplevelComponent.GROUP_CLAN_TAB_AREA : ToplevelComponent.CLAN_TAB_AREA);
				p.getPacketSender().sendIfEvents(76, 26, 0, 23, getMask(ClickOp1));
				VarPlayerRepository.CLAN_ACTIVE_TAB.set(p.player, 3);
			};
		});

	}

}
