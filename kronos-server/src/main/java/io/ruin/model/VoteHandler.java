package io.ruin.model;

import io.ruin.Server;
import io.ruin.model.entity.player.Player;
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

	// Same 84000s window playerVotedRecently() already uses, so a genuine
	// repeat vote after the window rolls over is still allowed -- this only
	// blocks a SECOND grant for the same (player, site) inside one window.
	private static final long CLAIM_WINDOW_SECONDS = 84000;

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
