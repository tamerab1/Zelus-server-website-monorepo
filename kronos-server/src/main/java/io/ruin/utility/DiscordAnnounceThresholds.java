package io.ruin.utility;

import properties.ServerProperties;

/**
 * Configurable minimum-value gates for public Discord announcements, so common/trivial drops
 * don't spam a channel meant for genuinely rare or valuable finds.
 */
public final class DiscordAnnounceThresholds {

	private DiscordAnnounceThresholds() {
	}

	/**
	 * A collection log unlock gets announced to Discord if its high alch value (x amount) meets
	 * this, or it's a pet / a flagged rare unique (collectionlog CollectionLog.isNotableDrop).
	 * Configurable via "discord_collectionlog_min_value" in server.properties (10M since 2026-10-20).
	 */
	public static long collectionLogMinValue() {
		return (long) ServerProperties.get("discord_collectionlog_min_value", 10_000_000);
	}
}
