package io.ruin.model.activities.raids.tfa;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * Tick-exact scheduling for a room, advanced once per tick by the room. A telegraph sent on tick T with
 * {@code after(N, resolve)} resolves on exactly tick T+N, and nothing outlives {@link #clear()}, so a cleaned-up
 * room can't land a late hit.
 */
@Slf4j
public class Raid4Schedule {

	private record Task(long due, Runnable action) {
	}

	private final List<Task> tasks = new ArrayList<>();
	private long now;

	/** Runs {@code action} {@code ticks} ticks from now (minimum 1). */
	public void after(int ticks, Runnable action) {
		tasks.add(new Task(now + Math.max(1, ticks), action));
	}

	public void tick() {
		now++;
		if (tasks.isEmpty())
			return;
		List<Task> due = new ArrayList<>();
		tasks.removeIf(task -> {
			if (task.due <= now) {
				due.add(task);
				return true;
			}
			return false;
		});
		for (Task task : due) {
			try {
				task.action.run();
			} catch (Exception e) {
				log.error("Raid 4: scheduled action failed", e);
			}
		}
	}

	public long now() {
		return now;
	}

	public void clear() {
		tasks.clear();
	}
}
