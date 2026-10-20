package io.ruin.model.content.referral;

import io.ruin.model.World;
import io.ruin.model.entity.player.Player;
import io.ruin.network.HWIDManager;
import properties.ServerProperties;

import java.util.Set;

/**
 * ::referfriend <name> (aliases ::invite, ::refer, ::ref) - links an account (once, no playtime limit) to an existing
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
	static boolean isIdentityCheckSkipped() {
		return ServerProperties.get("referral_skip_identity_check", false);
	}

	/** ::referfriend with no name opens the Zelus Referrals screen (ReferralInterface). */
	public static boolean handle(Player player, String[] args) {
		if (args.length < 1) {
			ReferralInterface.open(player);
			return true;
		}
		String error = tryLink(player, String.join(" ", args));
		if (error != null)
			player.sendMessage(error);
		return true;
	}

	/** Links the player to targetName's referral. Returns null on success, or the reason it was refused. */
	public static String tryLink(Player player, String targetName) {
		// One referral per account, with no playtime cutoff (2026-10-20: it used to expire after 2 hours of play).
		if (player.referredBy != null)
			return "You have already been referred by " + player.referredBy + ".";

		targetName = targetName.trim();
		if (targetName.isEmpty())
			return "Enter the name of the player who invited you.";
		if (targetName.equalsIgnoreCase(player.getName()))
			return "You cannot refer yourself.";

		String referrerName;
		String referrerReferredBy;
		String referrerIp = null;
		String referrerHwid;
		Set<String> referrerHwids;

		Player onlineReferrer = World.getPlayer(targetName);
		if (onlineReferrer != null) {
			if (onlineReferrer == player)
				return "You cannot refer yourself.";
			referrerName = onlineReferrer.getName();
			referrerReferredBy = onlineReferrer.referredBy;
			referrerIp = onlineReferrer.getIp();
			referrerHwid = onlineReferrer.hwid;
			referrerHwids = onlineReferrer.hwids;
		} else {
			OfflineReferralLookup.Record record = OfflineReferralLookup.find(targetName);
			if (record == null)
				return "'" + targetName + "' does not exist.";
			referrerName = record.name();
			referrerReferredBy = record.referredBy();
			referrerHwid = record.hwid();
			referrerHwids = record.hwids();
		}

		if (player.getName().equalsIgnoreCase(referrerReferredBy))
			return "You cannot refer " + referrerName + " - they already referred you.";
		if (!isIdentityCheckSkipped() && sharesIdentity(player, referrerIp, referrerHwid, referrerHwids))
			return "Referral denied - that account shares this computer or network with yours.";

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
		return null;
	}

	// HWIDManager.isHwidValid() is required here: clients that can't report a real device
	// name send back the same placeholder ("unknown", or null/empty) rather than something
	// unique, which was matching unrelated players as "sharing" a computer.
	static boolean sharesIdentity(Player a, String referrerIp, String referrerHwid, Set<String> referrerHwids) {
		if (referrerIp != null && a.getIp() != null && a.getIp().equals(referrerIp))
			return true;
		if (HWIDManager.isHwidValid(a.hwid) && a.hwid.equals(referrerHwid))
			return true;
		for (String hwid : a.hwids)
			if (HWIDManager.isHwidValid(hwid) && referrerHwids.contains(hwid))
				return true;
		return false;
	}

	private ReferralCommand() {
	}

}
