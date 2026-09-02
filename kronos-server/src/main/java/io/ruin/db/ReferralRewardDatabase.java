package io.ruin.db;

import com.alibaba.fastjson2.JSON;
import io.ruin.api.utils.ServerWrapper;
import io.ruin.model.World;
import io.ruin.model.content.referral.PendingReferralReward;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Tiny durable mailbox for referral rewards that must be delivered to a referrer who is
 * offline when their referred player hits a milestone. One JSON file per pending referrer,
 * consumed and deleted the next time that referrer logs in - see ReferralSystem#register.
 *
 * Deliberately not built on top of {@link DatabaseFile}: that abstraction never deletes the
 * backing file on remove(), whereas this queue needs a real take-and-delete so a reward is
 * never paid out twice.
 */
@Slf4j
public final class ReferralRewardDatabase {

	private static final Path ROOT = Path.of(
		ServerWrapper.dataFolder.getAbsolutePath(),
		"runtime", "referrals", "pending",
		World.stage.name().toLowerCase(),
		World.type.name().toLowerCase()
	);

	public static void register() {
		try {
			Files.createDirectories(ROOT);
		} catch (final Exception e) {
			throw new IllegalStateException(e);
		}
	}

	public static synchronized void queue(String referrerUsername, String referredPlayerName) {
		try {
			Files.writeString(path(referrerUsername), JSON.toJSONString(new PendingReferralReward(referredPlayerName)));
		} catch (Exception e) {
			log.error("Failed to queue referral reward for " + referrerUsername, e);
		}
	}

	public static synchronized PendingReferralReward takeAndDelete(String referrerUsername) {
		Path file = path(referrerUsername);
		if (!Files.exists(file))
			return null;
		try {
			PendingReferralReward reward = JSON.parseObject(Files.readString(file), PendingReferralReward.class);
			Files.deleteIfExists(file);
			return reward;
		} catch (Exception e) {
			log.error("Failed to read/delete pending referral reward for " + referrerUsername, e);
			return null;
		}
	}

	private static Path path(String username) {
		return ROOT.resolve(username.toLowerCase() + ".json");
	}

	private ReferralRewardDatabase() {
	}

}
