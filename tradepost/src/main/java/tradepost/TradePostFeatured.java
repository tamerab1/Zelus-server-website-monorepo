package tradepost;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import io.ruin.Server;
import lombok.extern.slf4j.Slf4j;

/**
 * Featured (promoted) listings on the Zelus Market: a seller pays {@link #FEE} coins to pin one of their sell listings
 * to the Featured tab (gold framed) for {@link #DURATION_SECONDS}. The fee is a coin sink. Stored in its own table
 * (created on start-up), keyed by offer id, so it survives restarts and needs no change to tradepost_offers.
 */
@Slf4j
public class TradePostFeatured {

	public static final long FEE = 500_000L;
	public static final long DURATION_SECONDS = 24 * 60 * 60;

	/** offer id -> featured until (epoch seconds). Game thread only. */
	private static final Map<Long, Long> featured = new HashMap<>();

	public static void load() {
		Server.gameDb.executeAwait(con -> {
			try (var create = con.prepareStatement(
					"CREATE TABLE IF NOT EXISTS tradepost_featured (offer_id BIGINT NOT NULL PRIMARY KEY, "
							+ "featured_until BIGINT NOT NULL)")) {
				create.executeUpdate();
			}
			try (var select = con.prepareStatement("SELECT offer_id, featured_until FROM tradepost_featured");
					var rs = select.executeQuery()) {
				var loaded = new HashMap<Long, Long>();
				while (rs.next()) {
					loaded.put(rs.getLong(1), rs.getLong(2));
				}
				synchronized (featured) {
					featured.putAll(loaded);
				}
			}
		});
		log.info("Loaded: " + featured.size() + " featured market listings.");
	}

	public static boolean isFeatured(TradePostOffer offer) {
		Long until;
		synchronized (featured) {
			until = featured.get(offer.id);
		}
		return until != null && until > Instant.now().getEpochSecond();
	}

	/** Seconds left on the promotion (0 when not featured). */
	public static long secondsLeft(TradePostOffer offer) {
		Long until;
		synchronized (featured) {
			until = featured.get(offer.id);
		}
		return until == null ? 0 : Math.max(0, until - Instant.now().getEpochSecond());
	}

	public static void feature(TradePostOffer offer) {
		var until = Math.max(Instant.now().getEpochSecond(), secondsLeft(offer) + Instant.now().getEpochSecond())
				+ DURATION_SECONDS;
		synchronized (featured) {
			featured.put(offer.id, until);
		}
		Server.gameDb.execute(con -> {
			try (var statement = con.prepareStatement(
					"INSERT INTO tradepost_featured (offer_id, featured_until) VALUES (?, ?) "
							+ "ON DUPLICATE KEY UPDATE featured_until = ?")) {
				statement.setLong(1, offer.id);
				statement.setLong(2, until);
				statement.setLong(3, until);
				statement.executeUpdate();
			}
		});
	}

	public static void unfeature(TradePostOffer offer) {
		boolean had;
		synchronized (featured) {
			had = featured.remove(offer.id) != null;
		}
		if (!had) {
			return;
		}
		Server.gameDb.execute(con -> {
			try (var statement = con.prepareStatement("DELETE FROM tradepost_featured WHERE offer_id = ?")) {
				statement.setLong(1, offer.id);
				statement.executeUpdate();
			}
		});
	}
}
