package io.ruin.model.content.rivals;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

/**
 * Per-login visual demo. Never attach this to saved player attributes or combat results.
 * TODO backend: validated ranked results, division/checkpoint persistence, anti-boosting,
 * disconnect/forfeit handling, authoritative matchmaking, weekly scheduling and rewards,
 * and server-authoritative skill ratings and global leaderboard positions.
 */
public final class DivisionRivalsDemoState {

	public static final int MAX_STEPS = 10;
	public static final Duration DEMO_RESET_TIME = Duration.ofHours(86);
	public static final int DEMO_SKILL_RATING = 1850;
	public static final int DEMO_GLOBAL_LEADERBOARD_RANK = 1;
	public static final List<String> TEST_PRESETS = List.of("ready", "queued", "disabled", "weekly", "tier1",
			"eligible", "claimed", "locked", "current", "checkpoint", "complete", "elite", "short", "long");

	public enum QueueStatus { NOT_QUEUED, QUEUED, DISABLED }
	public enum NodeState { INCOMPLETE, COMPLETE, CURRENT, CHECKPOINT_LOCKED, CHECKPOINT_UNLOCKED, PROMOTION, FINISHED }

	/** Replace this snapshot with server-authoritative data at the interface adapter boundary. */
	public record InterfaceState(int division, String divisionName, int divisionProgress,
			int divisionRequiredSteps, int checkpointProgress, int winStreak, int weeklyWins,
			Duration weeklyResetTime, QueueStatus queueStatus, boolean tier1Completed,
			boolean tier2Completed, boolean rewardClaimable, boolean rewardClaimed,
			int skillRating, int globalLeaderboardRank) {
		public boolean elite() { return division == 0; }
		public int checkpointStep() { return Math.min(3, divisionRequiredSteps - 1); }
		public NodeState nodeState(int step) {
			if (step < 1 || step > divisionRequiredSteps)
				throw new IllegalArgumentException("Step outside current division");
			if (step == checkpointStep())
				return checkpointProgress >= step ? NodeState.CHECKPOINT_UNLOCKED : NodeState.CHECKPOINT_LOCKED;
			if (step == divisionRequiredSteps)
				return divisionProgress >= step ? NodeState.FINISHED : NodeState.PROMOTION;
			if (step <= divisionProgress)
				return NodeState.COMPLETE;
			return step == divisionProgress + 1 ? NodeState.CURRENT : NodeState.INCOMPLETE;
		}
	}

	private int division;
	private int divisionProgress;
	private int divisionRequiredSteps;
	private int checkpointProgress;
	private int winStreak;
	private int weeklyWins;
	private QueueStatus queueStatus;
	private boolean rewardClaimed;
	private int skillRating;
	private int globalLeaderboardRank;
	private int testPresetIndex;

	public DivisionRivalsDemoState() {
		reset();
	}

	public void reset() {
		division = 4;
		divisionRequiredSteps = 5;
		divisionProgress = 3;
		checkpointProgress = 3;
		winStreak = 3;
		weeklyWins = 6;
		queueStatus = QueueStatus.NOT_QUEUED;
		rewardClaimed = false;
		skillRating = DEMO_SKILL_RATING;
		globalLeaderboardRank = DEMO_GLOBAL_LEADERBOARD_RANK;
		testPresetIndex = -1;
	}

	public InterfaceState snapshot() {
		return new InterfaceState(division, divisionName(division), divisionProgress, divisionRequiredSteps,
				checkpointProgress, winStreak, weeklyWins, DEMO_RESET_TIME, queueStatus,
				weeklyWins >= 4, weeklyWins >= 8, weeklyWins >= 8 && !rewardClaimed, rewardClaimed,
				skillRating, globalLeaderboardRank);
	}

	public void nextDivision() {
		if (division == 0)
			return;
		division--;
		resetDivisionProgress();
	}

	public void previousDivision() {
		if (division == 10)
			return;
		division++;
		resetDivisionProgress();
	}

	/** Direct rank selection is confined to this per-login visual preview. */
	public void selectDivision(int rank) {
		if (rank < 0 || rank > 10)
			throw new IllegalArgumentException("Demo division must be 1-10 or elite");
		division = rank;
		resetDivisionProgress();
	}

	private void resetDivisionProgress() {
		divisionProgress = division == 0 ? divisionRequiredSteps : 0;
		checkpointProgress = division == 0 ? checkpointStep() : 0;
		queueStatus = QueueStatus.NOT_QUEUED;
	}

	/** Demo victories are never sourced from the combat system. */
	public void win() {
		weeklyWins = saturatedIncrement(weeklyWins);
		winStreak = saturatedIncrement(winStreak);
		queueStatus = QueueStatus.NOT_QUEUED;
		if (division == 0)
			return;
		divisionProgress = Math.min(divisionRequiredSteps, divisionProgress + 1);
		if (divisionProgress >= checkpointStep())
			checkpointProgress = checkpointStep();
		if (divisionProgress == divisionRequiredSteps)
			nextDivision();
	}

	/** Checkpoint floor prevents demo regression below an attained checkpoint; no demotion. */
	public void loss() {
		winStreak = 0;
		queueStatus = QueueStatus.NOT_QUEUED;
		if (division != 0)
			divisionProgress = Math.max(checkpointProgress, divisionProgress - 1);
	}

	public boolean toggleQueue() {
		if (queueStatus == QueueStatus.DISABLED)
			return false;
		queueStatus = queueStatus == QueueStatus.QUEUED ? QueueStatus.NOT_QUEUED : QueueStatus.QUEUED;
		return true;
	}

	/** Returns true once per demo week. No inventory, economy or real reward calls belong here. */
	public boolean claimReward() {
		if (!snapshot().rewardClaimable())
			return false;
		rewardClaimed = true;
		return true;
	}

	/** Explicit visual presets make completed/disabled states reachable without backend machinery. */
	public boolean preset(String name) {
		switch (name) {
			case "default", "ready" -> reset();
			case "disabled" -> queueStatus = QueueStatus.DISABLED;
			case "eligible" -> { weeklyWins = 8; rewardClaimed = false; }
			case "complete" -> { divisionProgress = divisionRequiredSteps; checkpointProgress = checkpointStep(); }
			case "checkpoint" -> { divisionProgress = checkpointStep(); checkpointProgress = checkpointStep(); }
			case "locked" -> { divisionProgress = 0; checkpointProgress = 0; }
			case "elite" -> { division = 0; resetDivisionProgress(); }
			default -> { return false; }
		}
		return true;
	}

	/** Each test starts from the same snapshot, so prior claims, queueing or step counts cannot leak in. */
	public boolean testPreset(String name) {
		if (name == null)
			return false;
		String normalized = name.toLowerCase(Locale.ROOT);
		int index = TEST_PRESETS.indexOf(normalized);
		if (index < 0) {
			try {
				index = Integer.parseInt(normalized) - 1;
			} catch (NumberFormatException ignored) {
				return false;
			}
		}
		if (index < 0 || index >= TEST_PRESETS.size())
			return false;
		reset();
		testPresetIndex = index;
		switch (TEST_PRESETS.get(index)) {
			case "queued" -> queueStatus = QueueStatus.QUEUED;
			case "disabled" -> queueStatus = QueueStatus.DISABLED;
			case "weekly" -> { weeklyWins = 0; winStreak = 0; }
			case "tier1" -> weeklyWins = 4;
			case "eligible" -> weeklyWins = 8;
			case "claimed" -> { weeklyWins = 8; rewardClaimed = true; }
			case "locked" -> { divisionProgress = 0; checkpointProgress = 0; }
			case "current" -> { divisionProgress = 1; checkpointProgress = 0; }
			case "checkpoint" -> { divisionProgress = checkpointStep(); checkpointProgress = checkpointStep(); }
			case "complete" -> { divisionProgress = divisionRequiredSteps; checkpointProgress = checkpointStep(); }
			case "elite" -> selectDivision(0);
			case "short" -> { configureSteps(1); divisionProgress = 0; checkpointProgress = 0; }
			case "long" -> configureSteps(MAX_STEPS);
			default -> { /* Ready uses the standard snapshot. */ }
		}
		return true;
	}

	/** A single repeated command visits every visible state and wraps to the first one. */
	public String nextTestPreset() {
		int next = (testPresetIndex + 1) % TEST_PRESETS.size();
		testPreset(TEST_PRESETS.get(next));
		return testPresetName();
	}

	public String testPresetName() {
		return testPresetIndex < 0 ? null : TEST_PRESETS.get(testPresetIndex);
	}

	public int testPresetNumber() { return testPresetIndex + 1; }

	public void configureSteps(int count) {
		if (count < 1 || count > MAX_STEPS)
			throw new IllegalArgumentException("Demo step count must be 1-" + MAX_STEPS);
		divisionRequiredSteps = count;
		divisionProgress = Math.min(divisionProgress, count);
		checkpointProgress = divisionProgress >= checkpointStep() ? checkpointStep() : 0;
		if (division == 0)
			resetDivisionProgress();
	}

	private int checkpointStep() { return Math.min(3, divisionRequiredSteps - 1); }
	private static int saturatedIncrement(int value) { return value == Integer.MAX_VALUE ? value : value + 1; }

	/** New sheet's ordered top-row badges; descriptors are shared with the cache asset manifest. */
	public static String divisionName(int division) {
		return switch (division) {
			case 10 -> "Iron";
			case 9 -> "Bronze";
			case 8 -> "Silver";
			case 7 -> "Winged Silver";
			case 6 -> "Emerald";
			case 5 -> "Winged Gold";
			case 4 -> "Gold";
			case 3 -> "Platinum";
			case 2 -> "Royal";
			case 1 -> "Champion";
			case 0 -> "Elite";
			default -> throw new IllegalArgumentException("Unknown division " + division);
		};
	}
}
