package io.ruin.model.content.referral;

import io.ruin.api.utils.JsonUtils;
import io.ruin.api.utils.ServerWrapper;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.*;

/**
 * Every completed (paid) referral, with its time, so the Zelus Referrals screen can rank referrers within a promotion
 * window ({@link ReferralPromo}). Written by {@link ReferralSystem#checkMilestone} only after the anti-farm checks, so a
 * voided referral never counts. Saved in runtime/referrals/leaderboard.json.
 */
final class ReferralLeaderboard {

	static final class Completion {
		String referrer;
		String referred;
		long at;
	}

	private static final class SaveState {
		List<Completion> completions = new ArrayList<>();
	}

	/** Display name + count, for one leaderboard row. */
	record Row(String name, int count) {
	}

	private static SaveState state;

	static synchronized void record(String referrer, String referred) {
		Completion c = new Completion();
		c.referrer = referrer;
		c.referred = referred;
		c.at = System.currentTimeMillis();
		state().completions.add(c);
		save();
	}

	/** Referrers ranked by completions in [from, to), most first (ties: whoever got there first). */
	static synchronized List<Row> ranking(long from, long to) {
		Map<String, int[]> counts = new LinkedHashMap<>();
		Map<String, String> names = new HashMap<>();
		Map<String, Long> reached = new HashMap<>();
		for (Completion c : state().completions) {
			if (c.at < from || c.at >= to || c.referrer == null)
				continue;
			String key = c.referrer.toLowerCase(Locale.ROOT).trim();
			counts.computeIfAbsent(key, k -> new int[1])[0]++;
			names.put(key, c.referrer);
			reached.put(key, c.at);
		}
		List<String> keys = new ArrayList<>(counts.keySet());
		keys.sort(Comparator.<String>comparingInt(k -> -counts.get(k)[0]).thenComparingLong(reached::get));
		List<Row> rows = new ArrayList<>(keys.size());
		for (String k : keys)
			rows.add(new Row(names.get(k), counts.get(k)[0]));
		return rows;
	}

	private static SaveState state() {
		if (state == null)
			load();
		return state;
	}

	private static File file() {
		return new File(ServerWrapper.dataFolder, "runtime/referrals/leaderboard.json");
	}

	private static void load() {
		state = new SaveState();
		File file = file();
		if (!file.exists())
			return;
		try (FileReader reader = new FileReader(file)) {
			SaveState loaded = JsonUtils.GSON.fromJson(reader, SaveState.class);
			if (loaded != null && loaded.completions != null)
				state = loaded;
		} catch (Exception e) {
			ServerWrapper.logError("Unable to load the referral leaderboard", e);
		}
	}

	private static void save() {
		File file = file();
		file.getParentFile().mkdirs();
		try (FileWriter writer = new FileWriter(file)) {
			JsonUtils.GSON_PRETTY.toJson(state, writer);
		} catch (Exception e) {
			ServerWrapper.logError("Unable to save the referral leaderboard", e);
		}
	}

	private ReferralLeaderboard() {
	}
}
