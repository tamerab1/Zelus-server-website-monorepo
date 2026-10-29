package io.ruin.model.skills.prayer;

import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.dialogue.MessageDialogue;
import io.ruin.model.inter.dialogue.NPCDialogue;
import io.ruin.model.inter.dialogue.OptionsDialogue;
import io.ruin.model.inter.utils.Option;
import io.ruin.model.item.actions.ItemAction;
import io.ruin.model.map.Bounds;
import io.ruin.model.map.MapListener;
import io.ruin.model.stat.StatType;
import io.ruin.model.var.VarPlayerRepository;

/**
 * How rev 241's alternate prayers are learnt (2026-10-29, OSRS wiki):
 * <ul>
 * <li>Deadeye / Mystic Vigour prayer scroll (Royal Titans): Read, "Would you like to absorb its power?" -> Learn /
 * Cancel; needs 62 / 63 Prayer; the scroll is consumed.</li>
 * <li>Zeal: bring the Horn of Bran (The Fractured Archive) into Camelot and King Arthur blesses you. OSRS plays a
 * cutscene; here it's his dialogue. The wiki doesn't say whether the horn is kept: it's taken (a tradeable horn that
 * stayed would unlock any number of accounts).</li>
 * </ul>
 */
public final class AlternatePrayers {

	public static final int DEADEYE_SCROLL = 30626, MYSTIC_VIGOUR_SCROLL = 30627, HORN_OF_BRAN = 34679, KING_ARTHUR = 3531;
	private static final Bounds CAMELOT = new Bounds(2744, 3483, 2776, 3520, 0);

	private AlternatePrayers() {
	}

	public static void register() {
		ItemAction.registerInventory(DEADEYE_SCROLL, "read", (p, item) -> scroll(p, item.getId(), "Deadeye", 62,
				VarPlayerRepository.DEADEYE_UNLOCK));
		ItemAction.registerInventory(MYSTIC_VIGOUR_SCROLL, "read", (p, item) -> scroll(p, item.getId(), "Mystic Vigour", 63,
				VarPlayerRepository.MYSTIC_VIGOUR_UNLOCK));
		MapListener.registerBounds(CAMELOT).onEnter(AlternatePrayers::camelot);
	}

	private static void scroll(Player player, int scroll, String prayer, int level, VarPlayerRepository unlock) {
		if (unlock.get(player) == 1) {
			player.dialogue(new MessageDialogue("You already know " + prayer + "."));
			return;
		}
		if (player.getStats().get(StatType.Prayer).fixedLevel < level) {
			player.dialogue(new MessageDialogue("You need a Prayer level of " + level + " to read this scroll."));
			return;
		}
		player.dialogue(new OptionsDialogue("Would you like to absorb its power?<br>This will consume the scroll.",
				new Option("Learn " + prayer, () -> {
					if (unlock.get(player) == 1 || !player.getInventory().contains(scroll))
						return;
					player.getInventory().remove(scroll, 1);
					unlock.set(player, 1);
					player.sendMessage("You study the scroll and learn a new prayer: " + prayer);
				}),
				new Option("Cancel")));
	}

	private static void camelot(Player player) {
		if (VarPlayerRepository.ZEAL_UNLOCK.get(player) == 1 || !player.getInventory().contains(HORN_OF_BRAN))
			return;
		player.dialogue(
				new NPCDialogue(KING_ARTHUR, "The Horn of Bran! Then the old tales are true. You have done Camelot a great"
						+ " service in bringing it home."),
				new NPCDialogue(KING_ARTHUR, "Kneel. May the zeal of Bran be yours, in every battle to come."),
				new io.ruin.model.inter.dialogue.ActionDialogue(() -> {
					if (VarPlayerRepository.ZEAL_UNLOCK.get(player) == 1 || !player.getInventory().contains(HORN_OF_BRAN))
						return;
					player.getInventory().remove(HORN_OF_BRAN, 1);
					VarPlayerRepository.ZEAL_UNLOCK.set(player, 1);
					player.dialogue(new MessageDialogue("King Arthur keeps the Horn of Bran.<br>You have learnt a new prayer: <col=000080>Zeal</col>."));
					player.sendMessage("You have learnt a new prayer: Zeal");
				}));
	}
}
