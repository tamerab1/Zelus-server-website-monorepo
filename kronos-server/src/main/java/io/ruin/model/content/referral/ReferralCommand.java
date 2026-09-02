package io.ruin.model.content.referral;

import io.ruin.model.World;
import io.ruin.model.entity.player.Player;
import properties.ServerProperties;

/**
 * ::referfriend <name> (alias ::invite) - links a brand new account to an existing player's
 * referral. NOTE: not named "::ref" - that command is already taken by the unrelated promo-code
 * claim system in {@link io.ruin.model.content.RefSystem}.
 *
 * Only creates the relationship; no reward is granted here. {@link ReferralSystem#checkMilestone}
 * pays out once the referred player proves retention (playtime or total level threshold), which
 * is what actually resists alt-farming.
 */
public final class ReferralCommand {

	// TEMP DEV-ONLY TESTING SWITCH: set referral_skip_identity_check=true in server.properties to
	// skip the same-IP/HWID check (both test accounts share one machine locally, so it always
	// fires). Defaults to false - never leave this true anywhere near production; it exists purely
	// so the milestone/reward path can be verified end-to-end before a second real machine is
	// available. Remove this switch entirely once local multi-machine/beta testing replaces it.
	private static boolean isIdentityCheckSkipped() {
		return ServerProperties.get("referral_skip_identity_check", false);
	}

	public static boolean handle(Player player, String[] args) {
		if (args.length < 1) {
			player.sendMessage("Usage: ::referfriend <name> - your friend must be online.");
			return true;
		}

		if (player.playTime >= ReferralSystem.REFERRAL_ELIGIBLE_PLAYTIME_SECONDS) {
			player.sendMessage("Referral links can only be used within your first "
					+ (ReferralSystem.REFERRAL_ELIGIBLE_PLAYTIME_SECONDS / 3600) + " hours of playtime.");
			return true;
		}
		if (player.referredBy != null) {
			player.sendMessage("You have already been referred by " + player.referredBy + ".");
			return true;
		}

		String targetName = String.join(" ", args);
		if (targetName.equalsIgnoreCase(player.getName())) {
			player.sendMessage("You cannot refer yourself.");
			return true;
		}

		Player referrer = World.getPlayer(targetName);
		if (referrer == null) {
			player.sendMessage("'" + targetName + "' must be online to link a referral.");
			return true;
		}
		if (player.getName().equalsIgnoreCase(referrer.referredBy)) {
			player.sendMessage("You cannot refer " + referrer.getName() + " - they already referred you.");
			return true;
		}
		if (!isIdentityCheckSkipped() && sharesIdentity(player, referrer)) {
			player.sendMessage("Referral denied - that account shares this computer or network with yours.");
			return true;
		}

		player.referredBy = referrer.getName();
		player.referralLinkedAtPlayTime = player.playTime;

		player.sendMessage("You are now linked to " + referrer.getName() + "'s referral! Reach "
				+ (ReferralSystem.MILESTONE_PLAYTIME_SECONDS / 3600) + " hours playtime or "
				+ ReferralSystem.MILESTONE_TOTAL_LEVEL + " total level to unlock rewards for both of you.");
		referrer.sendMessage(player.getName()
				+ " has used your referral link! You'll both be rewarded once they hit the milestone.");
		return true;
	}

	private static boolean sharesIdentity(Player a, Player b) {
		if (a.getIp() != null && a.getIp().equals(b.getIp()))
			return true;
		if (a.hwid != null && a.hwid.equals(b.hwid))
			return true;
		for (String hwid : a.hwids)
			if (b.hwids.contains(hwid))
				return true;
		return false;
	}

	private ReferralCommand() {
	}

}
