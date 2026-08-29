package io.ruin.model.content.sigils;

import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.dialogue.MessageDialogue;
import io.ruin.model.inter.dialogue.OptionsDialogue;
import io.ruin.model.inter.dialogue.YesNoDialogue;
import io.ruin.model.inter.utils.Option;
import io.ruin.model.item.actions.ItemAction;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Unlock/attune tracking for Sigil.java, mirroring LoyaltyTitleManager's shape:
 * an uncapped "unlocked" set plus a capped "active" set, both plain Integer sets
 * (keyed by Sigil ordinal) on PlayerAttributes -- no codec/migration needed, the whole
 * Player object is serialized by field reflection.
 */
public class SigilManager {

	public static final int MAX_ATTUNED_COMBAT_SIGILS = 3;

	public static boolean isUnlocked(Player player, Sigil sigil) {
		return sigil != null && unlocked(player).contains(sigil.ordinal());
	}

	public static boolean isAttuned(Player player, Sigil sigil) {
		return sigil != null && sigil.category == Sigil.Category.COMBAT && attuned(player).contains(sigil.ordinal());
	}

	/**
	 * Attunes a combat sigil, respecting the 3-slot cap. Permanent sigils have no slot to
	 * fill -- they're always "active" the moment they're unlocked -- so this is a no-op
	 * success for those.
	 */
	public static boolean attune(Player player, Sigil sigil) {
		if (sigil.category != Sigil.Category.COMBAT)
			return true;
		if (isAttuned(player, sigil))
			return true;
		if (attuned(player).size() >= MAX_ATTUNED_COMBAT_SIGILS)
			return false;
		attuned(player).add(sigil.ordinal());
		return true;
	}

	public static void unattune(Player player, Sigil sigil) {
		attuned(player).remove(sigil.ordinal());
	}

	private static void unlock(Player player, Sigil sigil) {
		unlocked(player).add(sigil.ordinal());
	}

	private static Set<Integer> unlocked(Player player) {
		if (player.unlockedSigils == null)
			player.unlockedSigils = new HashSet<>();
		return player.unlockedSigils;
	}

	private static Set<Integer> attuned(Player player) {
		if (player.attunedCombatSigils == null)
			player.attunedCombatSigils = new HashSet<>();
		return player.attunedCombatSigils;
	}

	private static void confirmAttune(Player player, Sigil sigil) {
		if (isUnlocked(player, sigil)) {
			player.dialogue(new MessageDialogue("You have already attuned this sigil."));
			return;
		}
		player.dialogue(new YesNoDialogue("Attune Sigil",
			"Attune this sigil, unlocking its power permanently?", sigil.unattunedId, 1, () -> {
				player.getInventory().remove(sigil.unattunedId, 1);
				unlock(player, sigil);
				if (sigil.category == Sigil.Category.COMBAT) {
					if (attune(player, sigil)) {
						player.sendMessage("You attune the sigil - its effect is now active. Use ::sigils to manage your attuned combat sigils.");
					} else {
						player.sendMessage("You attune the sigil, but you already have " + MAX_ATTUNED_COMBAT_SIGILS
							+ " combat sigils active. Use ::sigils to swap one out.");
					}
				} else {
					player.sendMessage("You attune the sigil. Its effect is now permanently active.");
				}
			}));
	}

	/**
	 * ::sigils -- lists every sigil the player has unlocked, letting them toggle which
	 * combat sigils are attuned (max 3). Permanent sigils are shown as always-active,
	 * nothing to toggle. Mirrors PvpPresetInterface.showPresetChoice's chatbox-list idiom
	 * instead of a dedicated interface widget.
	 */
	public static void openSigilList(Player player) {
		List<Option> options = new ArrayList<>();
		for (Sigil sigil : Sigil.VALUES) {
			if (!isUnlocked(player, sigil))
				continue;
			String label = sigil.displayName;
			if (sigil.category == Sigil.Category.PERMANENT) {
				options.add(new Option(label + " (Permanently Active)", p -> {
				}));
				continue;
			}
			boolean attunedNow = isAttuned(player, sigil);
			options.add(new Option(label + (attunedNow ? " (Attuned)" : ""), p -> {
				if (attunedNow) {
					unattune(p, sigil);
					p.sendMessage("You un-attune the " + label + ".");
				} else if (attune(p, sigil)) {
					p.sendMessage("You attune the " + label + ".");
				} else {
					p.sendMessage("You already have " + MAX_ATTUNED_COMBAT_SIGILS + " combat sigils attuned - un-attune one first.");
				}
				openSigilList(p);
			}));
		}
		if (options.isEmpty()) {
			player.dialogue(new MessageDialogue("You haven't unlocked any sigils yet. Attune one to get started."));
			return;
		}
		player.dialogue(new OptionsDialogue("Your Sigils", options));
	}

	public static void register() {
		for (Sigil sigil : Sigil.VALUES) {
			ItemAction.registerInventory(sigil.unattunedId, "Attune", (player, item) -> confirmAttune(player, sigil));
			ItemAction.registerInventory(sigil.unattunedId, "Inspect", (player, item) ->
				player.dialogue(new MessageDialogue(sigil.description)));
		}
	}

}
