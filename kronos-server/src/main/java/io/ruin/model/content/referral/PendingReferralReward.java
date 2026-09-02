package io.ruin.model.content.referral;

/**
 * Durable record of a referrer reward that couldn't be delivered live because the referrer
 * was offline when their referred player hit a milestone. Consumed by
 * ReferralSystem#deliverPendingReward on that referrer's next login.
 */
public class PendingReferralReward {

	public String referredPlayerName;
	public long queuedAtEpochSecond;

	public PendingReferralReward() {
	}

	public PendingReferralReward(String referredPlayerName) {
		this.referredPlayerName = referredPlayerName;
		this.queuedAtEpochSecond = System.currentTimeMillis() / 1000L;
	}

}
