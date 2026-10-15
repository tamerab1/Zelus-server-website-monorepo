package io.ruin.model.content.referral;

import io.ruin.api.utils.JsonUtils;
import io.ruin.api.utils.ServerWrapper;

import java.io.File;
import java.io.FileReader;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * The referral promotion shown on the Zelus Referrals screen, from cfg/referral_promo.json (re-read whenever the file
 * changes, no restart needed):
 * <pre>
 * {
 *   "startsAt": "2026-10-01T00:00:00Z",          optional, default: the 1st of the current month (server time)
 *   "endsAt": "2026-11-01T00:00:00Z",            optional, default: the 1st of next month
 *   "banner": "$250 in bonds - Most referrals wins",
 *   "prizeCaption": "1st place: $250 in Donator bonds",
 *   "prizeItems": [ { "id": 30468, "amount": 2 }, { "id": 30467, "amount": 1 } ]
 * }
 * </pre>
 * With no dates the promotion rolls over every calendar month. The prize is display only: the owner pays the winner.
 */
final class ReferralPromo {

	static final class Prize {
		int id;
		int amount = 1;
	}

	private static final class Config {
		String startsAt;
		String endsAt;
		String banner = "$250 in bonds - Most referrals wins";
		String prizeCaption = "1st place: $250 in Donator bonds";
		List<Prize> prizeItems = new ArrayList<>();
	}

	private static Config config = new Config();
	private static long loadedModified = -1;

	static String banner() {
		return refresh().banner == null ? "" : config.banner;
	}

	static String prizeCaption() {
		return refresh().prizeCaption == null ? "" : config.prizeCaption;
	}

	static List<Prize> prizes() {
		return refresh().prizeItems == null ? List.of() : config.prizeItems;
	}

	static long startMillis() {
		Config c = refresh();
		if (c.startsAt != null && !c.startsAt.isBlank())
			return Instant.parse(c.startsAt).toEpochMilli();
		return LocalDate.now().withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
	}

	static long endMillis() {
		Config c = refresh();
		if (c.endsAt != null && !c.endsAt.isBlank())
			return Instant.parse(c.endsAt).toEpochMilli();
		return LocalDate.now().withDayOfMonth(1).plusMonths(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
	}

	private static Config refresh() {
		File file = new File(ServerWrapper.dataFolder, "cfg/referral_promo.json");
		long modified = file.exists() ? file.lastModified() : 0;
		if (modified == loadedModified)
			return config;
		loadedModified = modified;
		Config loaded = new Config();
		if (file.exists()) {
			try (FileReader reader = new FileReader(file)) {
				Config read = JsonUtils.GSON.fromJson(reader, Config.class);
				if (read != null) {
					// validate the dates now so a typo can't break the screen later
					if (read.startsAt != null && !read.startsAt.isBlank())
						Instant.parse(read.startsAt);
					if (read.endsAt != null && !read.endsAt.isBlank())
						Instant.parse(read.endsAt);
					loaded = read;
				}
			} catch (Exception e) {
				ServerWrapper.logError("Bad cfg/referral_promo.json, using the monthly default", e);
			}
		}
		config = loaded;
		return config;
	}

	private ReferralPromo() {
	}
}
