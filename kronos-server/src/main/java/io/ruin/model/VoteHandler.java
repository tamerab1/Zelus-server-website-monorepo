package io.ruin.model;

import io.ruin.Server;
import io.ruin.model.activities.VoteBossHandler;
import io.ruin.model.entity.player.DonatorBonus;
import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.questtab.main.Achievements;
import io.ruin.model.activities.newcomertasks.NewcomerTasks;
import lombok.extern.slf4j.Slf4j;

import java.sql.PreparedStatement;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class VoteHandler {
	// Was a plain HashMap -- mutated from whatever thread the vote-claim HTTP
	// callback lands on, with no synchronization. ConcurrentHashMap makes the
	// in-memory recent-vote check itself thread-safe; it does NOT make claiming
	// a reward idempotent across a restart or a real race -- see tryClaimSite().
	static Map<String, Long> votedHwids = new ConcurrentHashMap<>();

	// This is NOT the daily vote-reward guard -- that's already enforced
	// upstream, atomically, by the website's /voting/claim/{username} endpoint
	// (claim_votes_for_game() flips each vote row pending->claimed in the same
	// DB transaction it returns it in, so a row can never be handed to the
	// game server twice). This window exists only to debounce two ::claimvote
	// presses racing on the SAME http response within the same request cycle.
	//
	// CONFIRMED LIVE BUG 2026-09-03: this used to be 84000s (~23h20m, meant to
	// approximate "once per day"), which is LESS than 24h -- so a player whose
	// claim times drift earlier by even a couple hours day-to-day (completely
	// normal) could have two real, different days' claims land in the SAME
	// fixed epoch bucket. 'gandalf' claimed at 2026-09-02 20:04:43 UTC (window
	// 21290) and again at 2026-09-03 09:22:13 UTC, only 13h17m later -- still
	// window 21290. tryClaimSite() rejected BOTH sites as "duplicate" even
	// though the website had already (correctly) marked those NEW votes
	// claimed, so the reward was silently lost with no way to reclaim it.
	// A short debounce closes the real race this guards against without ever
	// rejecting a genuinely later claim.
	private static final long CLAIM_WINDOW_SECONDS = 30;

	/**
	 * Atomically records a (player, site, window) claim via the database's own
	 * unique-key constraint. Returns true only for the FIRST caller to record
	 * a given claim in the current window -- safe even if two ::claimvote
	 * presses race each other and both receive the same non-empty claimed-vote
	 * list from the website, and safe across a server restart (unlike the
	 * in-memory map above).
	 */
	public static boolean tryClaimSite(String playerUuid, String site) {
		final long window = Instant.now().getEpochSecond() / CLAIM_WINDOW_SECONDS;
		final AtomicBoolean claimed = new AtomicBoolean(false);
		Server.gameDb.executeAwait(con -> {
			try (PreparedStatement insert = con.prepareStatement(
					"INSERT INTO vote_claims (player_uuid, site, claim_window, claimed_at) VALUES (?, ?, ?, ?)")) {
				insert.setString(1, playerUuid);
				insert.setString(2, site.toUpperCase());
				insert.setLong(3, window);
				insert.setLong(4, Instant.now().getEpochSecond());
				insert.executeUpdate();
				claimed.set(true);
			} catch (SQLIntegrityConstraintViolationException dup) {
				// Already claimed this (player, site, window) -- expected on a
				// race or a double-submit, not an error.
				claimed.set(false);
			}
		});
		if (!claimed.get()) {
			log.info("Rejected duplicate vote claim: player={} site={} window={}", playerUuid, site, window);
		}
		return claimed.get();
	}

	/**
	 * Grants the reward for a batch of server-verified claimed-vote sites --
	 * shared by the real ::claimvote flow (CommandHandlerRegular, driven by the
	 * website's /voting/claim/{username} response) and manual admin restitution
	 * (CommandHandlerManager's ::forcevoteclaim, for when a player's vote row
	 * was already consumed website-side but the reward never reached them, e.g.
	 * the CLAIM_WINDOW_SECONDS bug above) -- both must go through the exact same
	 * per-site dedup and reward logic, not a hand-rolled duplicate of it.
	 */
	public static void grantClaimedVotes(Player player, List<String> claimedVotes) {
		boolean votedRuneLocus = false;
		boolean votedRspsList = false;
		int newlyClaimed = 0;
		int points = 0;
		for (String site : claimedVotes) {
			if (!tryClaimSite(player.uuid(), site)) {
				continue;
			}
			newlyClaimed++;
			if (site.equalsIgnoreCase("RUNELOCUS")) {
				points += 2;
				votedRuneLocus = true;
			} else if (site.equalsIgnoreCase("RSPS_LIST")) {
				points += 1;
				votedRspsList = true;
			}
		}
		if (newlyClaimed == 0) {
			player.sendMessage("<col=000080>You've already claimed that vote reward.");
			return;
		}
		points += DonatorBonus.VOTE_POINT_BONUS.handleBonus(player);
		boolean votedBothSites = votedRuneLocus && votedRspsList;

		addHwid(player.hwid);
		player.votePoints += points;
		player.votesClaimed += newlyClaimed;
		player.claimedVotes++;
		player.lastVoteClaimInEpoch = Instant.now().getEpochSecond();
		if (player.claimedVotes == Achievements.THIRD_PART_CANDIDATE.getCompletionAmount())
			player.sendMessage("<col=000080>You have completed the achievement: <col=800000>"
					+ Achievements.THIRD_PART_CANDIDATE.getAchievementName());

		VoteBossHandler.addVote(claimedVotes.size());
		player.getDailyVote().voteCheck();
		if (player.votesClaimed == 1)
			player.sendMessage("<col=000080>You have completed the newcomer task: <col=800000>"
					+ NewcomerTasks.VOTE_CLAIM.getFormattedName() + "!");

		if (votedBothSites) {
			player.getInventory().addOrDrop(30602, 1);
		}

		player.sendMessage(
				"You have received " + points + " vote points, you now have " + player.getVotePoints() + " vote points.");
		if (votedBothSites) {
			player.sendMessage("You are also rewarded with a Vote raffle ticket for voting on both sites!");
		}
		player.getDailyVote().open();
	}

	public static void addHwid(String hwid) {
		//System.out.println("Adding hwid: " + hwid);
		votedHwids.put(hwid, Instant.now().getEpochSecond());
		// System.out.println("value is: " + votedHwids.get(hwid));
	}

	public static void getAllHwids() {
		for (Map.Entry<String, Long> entry : votedHwids.entrySet()) {
			// System.out.println("Key = " + entry.getKey() + ", Value = " + entry.getValue());
		}
	}

	public static boolean playerVotedRecently(Player player) {
		Instant now = Instant.now();
		Instant instant = Instant.ofEpochSecond(player.lastVoteClaimInEpoch);
		if (now.isAfter(instant.plusSeconds(84000))) {
			return false;
		}
		return true;
	}

	public static boolean playerDonatedRecently(Player player) {
		Instant now = Instant.now();
		Instant instant = Instant.ofEpochSecond(player.lastDonationClaimInEpoch);
		if (now.isAfter(instant.plusSeconds(84000))) {
			return false;
		}
		return true;
	}

	public static boolean hasVotedRecently(String hwid) {
		// System.out.println("the hwid is: " + hwid);
		if (hwid.equalsIgnoreCase("unknown"))
			return false;
		if (votedHwids.get(hwid) == null) {
			// System.out.println("the hwid is null");
			return false;
		}
		long lastVoteEpoch = votedHwids.get(hwid);
		Instant now = Instant.now();
		Instant instant = Instant.ofEpochSecond(lastVoteEpoch);
		if (now.isAfter(instant.plusSeconds(36000))) {
			return false;
		}
		//System.out.println("the hwid: " + hwid + " has voted recently");
		return true;
	}
}
