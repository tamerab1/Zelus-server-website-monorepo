package io.ruin.model.content.loyaltytitles;

import io.ruin.cache.ItemID;
import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.dialogue.MessageDialogue;
import io.ruin.model.inter.dialogue.YesNoDialogue;

public class LoyaltyTitleManager {

	public static boolean isUnlocked(Player player, LoyaltyTitle title) {
		return player.unlockedLoyaltyTitles.contains(title.id);
	}

	public static void unlock(Player player, LoyaltyTitle title) {
		player.unlockedLoyaltyTitles.add(title.id);
	}

	public static void equip(Player player, LoyaltyTitle title) {
		player.equippedLoyaltyTitleId = title.id;
	}

	public static void clearEquipped(Player player) {
		player.equippedLoyaltyTitleId = -1;
	}

	public static LoyaltyTitle getEquipped(Player player) {
		if (player.equippedLoyaltyTitleId == -1)
			return null;
		return LoyaltyTitle.get(player.equippedLoyaltyTitleId);
	}

	/**
	 * Renders the player's equipped loyalty title wrapped around their name, or just their
	 * plain name if none is equipped.
	 */
	public static String getDisplayName(Player player) {
		LoyaltyTitle title = getEquipped(player);
		if (title == null)
			return player.getName();
		return title.preview(player.getName());
	}

	/**
	 * Attempts to select a title from the interface. Handles the locked/purchasable/unlocked
	 * branches exactly matching the source system's UX.
	 */
	public static void select(Player player, LoyaltyTitle title) {
		if (isUnlocked(player, title)) {
			equip(player, title);
			player.dialogue(new MessageDialogue("Your title has been updated."));
			return;
		}
		if (title.isPurchasable()) {
			int cost = title.cost;
			player.dialogue(new YesNoDialogue("Purchase Title", "Purchase the title \"" + stripTags(title.preview(player.getName())) + "\" for " + cost + " coins?",
				ItemID.COINS_995, cost, () -> {
					if (player.getInventory().getAmount(ItemID.COINS_995) < cost) {
						player.dialogue(new MessageDialogue("You don't have enough coins."));
						return;
					}
					player.getInventory().remove(ItemID.COINS_995, cost);
					unlock(player, title);
					equip(player, title);
					player.dialogue(new MessageDialogue("Your title has been updated."));
				}));
			return;
		}
		player.dialogue(new MessageDialogue("You have not yet unlocked this title.<br><br>Unlock condition: " + title.requirement));
	}

	private static String stripTags(String text) {
		return text.replaceAll("<[^>]*>", "");
	}
}
