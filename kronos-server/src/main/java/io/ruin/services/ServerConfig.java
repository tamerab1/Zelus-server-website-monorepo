package io.ruin.services;

import io.ruin.Server;
import lombok.extern.slf4j.Slf4j;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Small persistent key/value store in the `reason`.`server_config` table, for settings that
 * must survive process restarts and redeploys (unlike anything held only in JVM memory).
 */
@Slf4j
public final class ServerConfig {

	private static final String LAUNCH_TIMESTAMP_KEY = "launch_timestamp";

	// -1 = not yet loaded from the DB, 0 = loaded and genuinely unset.
	private static final AtomicLong launchTimestampMs = new AtomicLong(-1);

	public static void ensureTable() {
		if (Server.gameDb == null) {
			return;
		}
		Server.gameDb.execute(con -> {
			try (PreparedStatement stmt = con.prepareStatement(
					"CREATE TABLE IF NOT EXISTS server_config (" +
					"config_key VARCHAR(64) NOT NULL, " +
					"config_value VARCHAR(255) NOT NULL, " +
					"PRIMARY KEY (config_key))")) {
				stmt.executeUpdate();
			}
		});
	}

	/**
	 * Epoch millis of the official launch, or 0 if never set (e.g. still in beta).
	 * Cached after first successful load -- this is read on every uptime display.
	 */
	public static long getLaunchTimestamp() {
		long cached = launchTimestampMs.get();
		if (cached != -1) {
			return cached;
		}
		long loaded = loadLaunchTimestamp();
		launchTimestampMs.set(loaded);
		return loaded;
	}

	private static long loadLaunchTimestamp() {
		if (Server.gameDb == null) {
			return 0L;
		}
		long[] result = {0L};
		Server.gameDb.execute(con -> {
			try (PreparedStatement stmt = con.prepareStatement(
					"SELECT config_value FROM server_config WHERE config_key = ?")) {
				stmt.setString(1, LAUNCH_TIMESTAMP_KEY);
				try (ResultSet rs = stmt.executeQuery()) {
					if (rs.next()) {
						result[0] = Long.parseLong(rs.getString(1));
					}
				}
			} catch (Exception e) {
				log.error("Failed loading launch_timestamp from server_config", e);
			}
		});
		return result[0];
	}

	/**
	 * Sets the official launch epoch (millis) that uptime is measured from going forward.
	 * Intended to be called once, deliberately, via an admin command at the actual launch
	 * moment -- not automatically, since "launch" is a real-world event this codebase has
	 * no other way to know about.
	 */
	public static void setLaunchTimestamp(long epochMs) {
		if (Server.gameDb == null) {
			return;
		}
		Server.gameDb.execute(con -> {
			try (PreparedStatement stmt = con.prepareStatement(
					"INSERT INTO server_config (config_key, config_value) VALUES (?, ?) " +
					"ON DUPLICATE KEY UPDATE config_value = ?")) {
				stmt.setString(1, LAUNCH_TIMESTAMP_KEY);
				stmt.setString(2, String.valueOf(epochMs));
				stmt.setString(3, String.valueOf(epochMs));
				stmt.executeUpdate();
			}
		});
		launchTimestampMs.set(epochMs);
	}
}
