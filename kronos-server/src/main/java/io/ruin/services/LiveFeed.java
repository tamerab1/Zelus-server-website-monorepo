package io.ruin.services;

import io.ruin.model.World;
import io.ruin.model.entity.player.Player;
import io.ruin.process.event.EventWorker;
import io.ruin.services.http.HttpRequestEvent;
import io.ruin.services.http.HttpRequestMethod;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/**
 * Real in-game events for the website's Live Game Feed (2026-10-21). The website used to fill the feed with made-up
 * entries ("X is on a 1-kill streak", "X reached total level 2277" with invented times) because the game never pushed
 * anything to its /events/push endpoint. Now it does: every world news broadcast (rare drops, 99s, maxed accounts)
 * via {@link io.ruin.utility.Broadcast#sendNewsDropMessage}, killstreak milestones (every 5th kill) and Pit
 * championship wins. Fire-and-forget, off the game thread; nothing happens when the website API is disabled.
 */
@Slf4j
public final class LiveFeed {

	private LiveFeed() {
	}

	/** type: pvp_kill | killstreak | rare_drop | level_up | tournament_win | bounty_claim (the website's EVENT_STYLE). */
	public static void push(String type, String username, String message) {
		if (!World.apiEnabled || message == null)
			return;
		String text = clean(message);
		if (text.isEmpty())
			return;
		Map<String, Object> body = Map.of(
			"secret", World.gameEventsSecret == null ? "" : World.gameEventsSecret,
			"events", List.of(Map.of(
				"event_type", type,
				"username", username == null ? "" : username,
				"message", text.length() > 500 ? text.substring(0, 500) : text)));
		EventWorker.submitHttpRequest(new HttpRequestEvent("events/push", body, HttpRequestMethod.POST, r -> {
		}, r -> log.warn("Live feed push failed: {}", text)));
	}

	/** A world news broadcast: 99s / maxed accounts are level_up, everything else a rare drop. */
	public static void fromBroadcast(Player player, String title, String message) {
		String text = clean((title == null ? "" : title) + (message == null ? "" : message));
		push(text.contains("level 99") ? "level_up" : "rare_drop", player == null ? "" : player.getName(), text);
	}

	/** Chat markup (<col=..>, <shad=..>, <img=..>, ...) removed, whitespace collapsed. */
	static String clean(String s) {
		return s.replaceAll("<[^>]*>", "").replaceAll("\s+", " ").trim();
	}
}
