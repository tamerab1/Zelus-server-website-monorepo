package io.ruin.model.content.referral;

import discord.webhooks.logs.ReferralHook;
import io.ruin.cache.ItemID;
import io.ruin.db.ReferralRewardDatabase;
import io.ruin.model.World;
import io.ruin.model.entity.player.Player;
import io.ruin.model.entity.shared.listeners.LoginListener;
import io.ruin.model.item.Item;
import io.ruin.utility.Broadcast;

/**
 * Refer-a-friend milestone engine. See {@link ReferralCommand} for the ::referfriend link-up
 * command that establishes the referrer/referred relationship.
 *
 * A referral only pays out once the referred player proves they're a genuine, retained player
 * - never at link/sign-up time - to resist alt-farming. checkMilestone is swept cheaply from
 * Player#tick (throttled, see the call site there) and on every login, mirroring the
 * LoyaltyTitleManager safety-net-sweep pattern already used in this codebase
 * (see LoyaltyTitleManager#checkAllUnlocks) rather than relying on a dedicated level-up/XP hook,
 * which doesn't exist here.
 */
public final class ReferralSystem {

	/** Referred player must reach this much playTime (seconds) OR the total level below. */
	public static final int MILESTONE_PLAYTIME_SECONDS = 10 * 3600;
	public static final int MILESTONE_TOTAL_LEVEL = 750;

	/** Only accounts younger than this (playTime, seconds) may submit a referral link at all. */
	public static final int REFERRAL_ELIGIBLE_PLAYTIME_SECONDS = 2 * 3600;

	public static final int REFERRER_POINT_REWARD = 500;

	// Refer Package - a brand-new item id (60235), not a repurposed existing one. See
	// io.ruin.model.item.actions.impl.boxes.mystery.ReferPackage for its "open" reward table.
	// This item must never be granted anywhere else (shop/drop table/box) - referral is its
	// only legitimate source.
	public static int REFERRER_BOX_ITEM_ID = ItemID.REFER_PACKAGE;

	public static final int REFERRED_XP_SCROLL_ITEM_ID = ItemID.DOUBLE_EXP_SCROLL;
	public static final int REFERRED_XP_SCROLL_AMOUNT = 2;
	public static final int REFERRED_MYSTERY_BOX_ITEM_ID = ItemID.TOB_REFUND_CHEST; // id 6199, "Mystery box"

	public static void register() {
		LoginListener.register(player -> {
			checkMilestone(player);
			deliverPendingReward(player);
		});
	}

	/**
	 * Cheap guarded sweep - the vast majority of players have referredBy == null and return
	 * on the first check. Safe to call frequently (login, and throttled per-tick).
	 */
	public static void checkMilestone(Player referred) {
		if (referred.referredBy == null || referred.referralRewardClaimed)
			return;

		boolean playtimeMet = referred.playTime >= MILESTONE_PLAYTIME_SECONDS;
		boolean totalLevelMet = referred.getStats().totalLevel >= MILESTONE_TOTAL_LEVEL;
		if (!playtimeMet && !totalLevelMet)
			return;

		referred.referralRewardClaimed = true;

		giveOrBank(referred, REFERRED_XP_SCROLL_ITEM_ID, REFERRED_XP_SCROLL_AMOUNT);
		giveOrBank(referred, REFERRED_MYSTERY_BOX_ITEM_ID, 1);
		referred.sendMessage("<col=2e8b57>Your referral milestone is complete - enjoy your reward!");

		Player referrer = World.getPlayer(referred.referredBy);
		if (referrer != null) {
			payoutReferrer(referrer, referred.getName());
		} else {
			ReferralRewardDatabase.queue(referred.referredBy, referred.getName());
		}

		ReferralHook.sendMilestoneCompletedToDiscord(referred.referredBy, referred.getName());
		Broadcast.WORLD.sendNews(null, "Referral", referred.getName() + " reached the milestone! Both "
				+ referred.getName() + " and " + referred.referredBy + " have received their referral rewards.");
	}

	private static void deliverPendingReward(Player referrer) {
		PendingReferralReward reward = ReferralRewardDatabase.takeAndDelete(referrer.getName());
		if (reward == null)
			return;
		payoutReferrer(referrer, reward.referredPlayerName);
		referrer.sendMessage("<col=2e8b57>Welcome back! Your referral reward for " + reward.referredPlayerName
				+ " reaching their milestone was waiting for you.");
	}

	private static void payoutReferrer(Player referrer, String referredName) {
		giveOrBank(referrer, REFERRER_BOX_ITEM_ID, 1);
		referrer.updateDonatorPoints(REFERRER_POINT_REWARD);
		referrer.totalReferredPlayers++;
		referrer.sendMessage("<col=2e8b57>" + referredName + " (your referral) hit their milestone! You received a "
				+ "Referral Mystery Box and " + REFERRER_POINT_REWARD + " donator points.");
	}

	private static void giveOrBank(Player player, int itemId, int amount) {
		Item item = new Item(itemId, amount);
		if (player.getInventory().hasRoomFor(item)) {
			player.getInventory().add(item);
		} else {
			player.getBank().add(itemId, amount);
		}
	}

	private ReferralSystem() {
	}

}
