package io.ruin.model.inter.handlers;

import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.Interface;
import io.ruin.model.inter.InterfaceAction;
import io.ruin.model.inter.InterfaceHandler;
import io.ruin.model.inter.actions.DefaultAction;
import io.ruin.model.inter.actions.OptionAction;
import io.ruin.model.inter.actions.SimpleAction;
import io.ruin.model.inter.dialogue.MessageDialogue;
import io.ruin.model.inter.dialogue.OptionsDialogue;
import io.ruin.model.inter.questtab.QuestTab;
import io.ruin.model.var.VarPlayerRepository;
import io.ruin.model.inter.utils.Option;
import io.ruin.model.inter.worldmap.Worldmap;

public class MainFrame {

	public static void register() {
		InterfaceHandler.register(Interface.ORBS, h -> {
			h.actions[6] = (OptionAction) XpCounter::select;
			h.actions[20] = (OptionAction) (p, option) -> {
				if (option == 1) {
					p.getPrayer().toggleQuickPrayers();
				} else {
					TabPrayer.setupQuickPrayers(p, true);
				}
			};
			h.actions[28] = (SimpleAction) p -> p.getMovement().toggleRunning();
			h.actions[46] = (SimpleAction) p -> p.openUrl("https://zelusrsps.com/store");
			h.actions[55] = new InterfaceAction() {
				@Override
				public void handleClick(Player player, int option, int slot, int itemId) {
					if (option == 3) {
						Worldmap.openWorldmapFull(player);
					} else {
						Worldmap.openWorldmap(player);

					}
				}
			};
			h.actions[36] = (SimpleAction) p -> p.getCombat().toggleSpecial();// 45 is wiki
			// the Teleports orb (AddTeleportOrb, 231 ids 54-58) is 57-61 in rev 241, in its original draw order,
			// right after Jagex's last component (56); 60 is its click layer
			h.actions[60] = (SimpleAction) TeleportNexus::openAnywhere;
		});
		InterfaceHandler.register(Interface.CHAT_BAR, h -> {
			h.actions[7] = (OptionAction) (player, option) -> {
				if (option == 2) {
					VarPlayerRepository.GAME_FILTER.toggle(player);
				} else if (option == 3) {
					player.dialogue(
							new MessageDialogue("Would you like to filter yells from non-staff members?"),
							new OptionsDialogue(
									new Option("Yes", () -> {
										player.yellFilter = true;
										player.sendMessage(
												"Yells from non-staff members will now be hidden when your game chat is set to filtered.");
									}),
									new Option("No", () -> {
										player.yellFilter = false;
										player.sendMessage(
												"Yells from non-staff members will now show even when your game chat is set to filtered.");
									})));
				}
			};
		});
		InterfaceHandler.register(Interface.FIXED_SCREEN, actions -> {
			actions.actions[70] = (DefaultAction) (player, option, slot, itemId) -> {
				if (option == 2)
					VarPlayerRepository.DISABLE_SPELL_FILTERING.toggle(player);
			};
			InterfaceHandler.register(Interface.FIXED_SCREEN, player -> {
				actions.actions[66] = (SimpleAction) QuestTab.MAIN::send;
			});
		});
		InterfaceHandler.register(Interface.RESIZED_SCREEN, actions -> {
			actions.actions[65] = (DefaultAction) (player, option, slot, itemId) -> {
				if (option == 2)
					VarPlayerRepository.DISABLE_SPELL_FILTERING.toggle(player);
			};
			InterfaceHandler.register(Interface.RESIZED_SCREEN, player -> {
				actions.actions[61] = (SimpleAction) QuestTab.MAIN::send;
			});
		});
		InterfaceHandler.register(Interface.RESIZED_STACKED_SCREEN, actions -> {
			InterfaceHandler.register(Interface.RESIZED_STACKED_SCREEN, player -> {
				actions.actions[52] = (SimpleAction) QuestTab.MAIN::send;
			});
		});
		InterfaceHandler.register(Interface.RESIZED_STACKED_SCREEN, actions -> {
			actions.actions[57] = (DefaultAction) (player, option, slot, itemId) -> {
				if (option == 2)
					VarPlayerRepository.DISABLE_SPELL_FILTERING.toggle(player);
			};
		});
	}

}
