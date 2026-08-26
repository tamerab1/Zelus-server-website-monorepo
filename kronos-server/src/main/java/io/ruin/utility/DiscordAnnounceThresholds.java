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
	 * A collection log unlock only gets announced to Discord if its high alch value (x amount)
	 * meets this, or it's a pet (pets are always notable regardless of value). Configurable via
	 * "discord_collectionlog_min_value" in server.properties.
	 */
	public static long collectionLogMinValue() {
		return (long) ServerProperties.get("discord_collectionlog_min_value", 1_000_000);
	}
}
