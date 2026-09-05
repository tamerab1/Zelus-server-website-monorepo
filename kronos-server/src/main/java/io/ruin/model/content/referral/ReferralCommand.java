package io.ruin.model.content.referral;

import io.ruin.Server;
import io.ruin.model.World;
import io.ruin.model.entity.player.Player;
import properties.ServerProperties;

import java.util.Set;

/**
 * ::referfriend <name> (aliases ::invite, ::ref) - links a brand new account to an existing
 * player's referral. The target may be online or offline: an offline target is looked up
 * straight off their save file via {@link OfflineReferralLookup}. ::ref used to be the unrelated
 * promo-code claim system in {@link io.ruin.model.content.RefSystem}; that command has been
 * retired in favour of aliasing it here.
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
			player.sendMessage("Usage: ::referfriend <name>.");
			return true;
		}

		// player.playTime is in game ticks, not real seconds - see ReferralSystem#checkMilestone.
		if (player.playTime >= Server.toTicks(ReferralSystem.REFERRAL_ELIGIBLE_PLAYTIME_SECONDS)) {
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

		String referrerName;
		String referrerReferredBy;
		String referrerIp = null;
		String referrerHwid;
		Set<String> referrerHwids;

		Player onlineReferrer = World.getPlayer(targetName);
		if (onlineReferrer != null) {
			referrerName = onlineReferrer.getName();
			referrerReferredBy = onlineReferrer.referredBy;
			referrerIp = onlineReferrer.getIp();
			referrerHwid = onlineReferrer.hwid;
			referrerHwids = onlineReferrer.hwids;
		} else {
			OfflineReferralLookup.Record record = OfflineReferralLookup.find(targetName);
			if (record == null) {
				player.sendMessage("'" + targetName + "' does not exist.");
				return true;
			}
			referrerName = record.name();
			referrerReferredBy = record.referredBy();
			referrerHwid = record.hwid();
			referrerHwids = record.hwids();
		}

		if (player.getName().equalsIgnoreCase(referrerReferredBy)) {
			player.sendMessage("You cannot refer " + referrerName + " - they already referred you.");
			return true;
		}
		if (!isIdentityCheckSkipped() && sharesIdentity(player, referrerIp, referrerHwid, referrerHwids)) {
			player.sendMessage("Referral denied - that account shares this computer or network with yours.");
			return true;
		}

		player.referredBy = referrerName;
		player.referralLinkedAtPlayTime = player.playTime;

		player.sendMessage("You are now linked to " + referrerName + "'s referral! Reach "
				+ ReferralSystem.formatMilestonePlaytime() + " playtime or "
				+ ReferralSystem.MILESTONE_TOTAL_LEVEL + " total level to unlock rewards for both of you.");
		if (onlineReferrer != null) {
			onlineReferrer.sendMessage(player.getName()
					+ " has used your referral link! You'll both be rewarded once they hit the milestone.");
		} else {
			player.sendMessage("<col=ff9040>" + referrerName + " is currently offline, but the link is saved.");
		}
		return true;
	}

	private static boolean sharesIdentity(Player a, String referrerIp, String referrerHwid, Set<String> referrerHwids) {
		if (referrerIp != null && a.getIp() != null && a.getIp().equals(referrerIp))
			return true;
		if (a.hwid != null && a.hwid.equals(referrerHwid))
			return true;
		for (String hwid : a.hwids)
			if (referrerHwids.contains(hwid))
				return true;
		return false;
	}

	private ReferralCommand() {
	}

}
