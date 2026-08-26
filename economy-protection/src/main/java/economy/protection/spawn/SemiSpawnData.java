package economy.protection.spawn;

import java.util.HashMap;
import java.util.Map;

/** Per-player persistent state for the ::spawn command's daily caps. */
public class SemiSpawnData {

	public Map<Integer, Integer> spawnedToday = new HashMap<>();
	public long dayEpoch;

	private static long currentDayEpoch() {
		return System.currentTimeMillis() / 86_400_000L;
	}

	private void rollDayIfNeeded() {
		long today = currentDayEpoch();
		if (dayEpoch != today) {
			dayEpoch = today;
			spawnedToday.clear();
		}
	}

	public int amountSpawnedToday(int itemId) {
		rollDayIfNeeded();
		return spawnedToday.getOrDefault(itemId, 0);
	}

	public void recordSpawn(int itemId, int amount) {
		rollDayIfNeeded();
		spawnedToday.merge(itemId, amount, Integer::sum);
	}
}
