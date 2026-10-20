package io.ruin.model.content.codex;

import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.InterfaceHandler;
import io.ruin.model.inter.ToplevelComponent;
import io.ruin.model.inter.actions.SimpleAction;
import io.ruin.model.inter.questtab.main.Achievements;
import io.ruin.model.item.Item;
import io.ruin.model.ScrollbarClientScript;
import io.ruin.network.PacketSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Zelus Codex (interface 1178, .dev/cache-restore-tool/src/BuildCodexInterface.java, 2026-10-20): the server's
 * milestone book, replacing the Reason "Achievements Interface" 849. Six tiers: Beginner and Zelus hold the
 * {@link ZelusTask}s; Easy / Medium / Hard / Elite show the original {@link Achievements} with their own counters and
 * claim path (Player.AchievementRewardHandler), so nobody's progress moved. A tier tab lists its tasks (status pip +
 * name); the card shows the description, progress, rewards and Claim Reward. Opened from the journal / newcomer
 * book (JournalTab.openAchievementInterface) and by Zezu.
 */
public final class ZelusCodex {

	public static final int INTERFACE = 1178;
	private static final int CLOSE = 2, TITLE = 3, SUMMARY = 4;
	/** Stone buttons: fill (click), +9 pressed layer, +19 label. */
	private static final int PRESSED = 9, LABEL = 19, STONE = 20;
	private static final int FIRST_TAB = 5, LIST_TITLE = 129, SCROLL = 131, SCROLLBAR = 132, LIST_HEIGHT = 208;
	/** Rows (21px on a 22px pitch): +1 fill (click), +2/+3 selected recess, +4 status pip, +6 name. */
	private static final int FIRST_ROW = 133, ROW_STRIDE = 7, ROWS = 40, ROW_PITCH = 22;
	private static final int EMPTY = 413, TASK_NAME = 418, TASK_TIER = 419, TASK_DESC = 421, PROGRESS_TEXT = 423,
		PROGRESS_FILL = 425, REWARDS = 429, FIRST_REWARD = 430, REWARD_STRIDE = 3, REWARD_SLOTS = 5, CLAIM = 445;
	private static final int BAR_WIDTH = 220;
	/** Clientscripts: 45 sets a component's colour, 10754 the meter fill's width (6px high). */
	private static final int SET_COLOUR = 45, SET_FILL_WIDTH = 10754;

	private static final String ORANGE = "<col=ff981f>", YELLOW = "<col=ffff00>", WHITE = "<col=ffffff>", GREEN = "<col=00ff00>",
		BLUE = "<col=6495ed>", GREY = "<col=9e9e9e>", END = "</col>";
	/** Status: not started, in progress, complete (claim it), claimed. */
	private static final int[] PIP = {0x6f6f6f, 0xc6a838, 0x3d6fc9, 0x2e7d32};
	private static final String[] NAME_COLOUR = {"<col=d8d0c0>", YELLOW, BLUE, GREEN};

	public enum Tier {
		BEGINNER("Beginner"), EASY("Easy"), MEDIUM("Medium"), HARD("Hard"), ELITE("Elite"), ZELUS("Zelus");

		final String label;

		Tier(String label) {
			this.label = label;
		}
	}

	/** One task as the Codex shows it: a ZelusTask or an original achievement. */
	private record Task(ZelusTask zelus, Achievements legacy) {
		String name() {
			return zelus != null ? zelus.title : legacy.getAchievementName();
		}

		Tier tier() {
			if (zelus != null) {
				return zelus.tier;
			}
			return switch (legacy.getAchievementType()) {
				case EASY -> Tier.EASY;
				case MEDIUM -> Tier.MEDIUM;
				case HARD -> Tier.HARD;
				case ELITE -> Tier.ELITE;
			};
		}

		int goal() {
			return zelus != null ? zelus.goal : legacy.getCompletionAmount();
		}

		int progress(Player p) {
			return Math.min(goal(), zelus != null ? zelus.progress(p) : p.getAchievementCurrentProgress(legacy));
		}

		boolean claimed(Player p) {
			return zelus != null ? zelus.claimed(p) : p.getAchievementRewardClaimed(legacy);
		}

		int status(Player p) {
			if (claimed(p)) return 3;
			int progress = progress(p);
			return progress >= goal() ? 2 : progress > 0 ? 1 : 0;
		}
	}

	/** Per-player view state. */
	private static final class State {
		Tier tier = Tier.BEGINNER;
		Task selected;
		final List<Task> rows = new ArrayList<>();
		int sentRows = -1;
	}

	private static final List<Task> TASKS = new ArrayList<>();

	static {
		for (ZelusTask t : ZelusTask.values()) {
			TASKS.add(new Task(t, null));
		}
		for (Achievements a : Achievements.VALUES) {
			TASKS.add(new Task(null, a));
		}
	}

	private ZelusCodex() {
	}

	public static void register() {
		InterfaceHandler.register(INTERFACE, h -> {
			h.actions[CLOSE] = (SimpleAction) Player::closeInterfaces;
			for (Tier t : Tier.values()) {
				h.actions[FIRST_TAB + t.ordinal() * STONE] = (SimpleAction) p -> {
					var s = state(p);
					s.tier = t;
					s.selected = null;
					draw(p, true);
				};
			}
			for (int r = 0; r < ROWS; r++) {
				int index = r;
				h.actions[FIRST_ROW + r * ROW_STRIDE + 1] = (SimpleAction) p -> {
					var s = state(p);
					if (index < s.rows.size()) {
						s.selected = s.rows.get(index);
						draw(p, false);
					}
				};
			}
			h.actions[CLAIM] = (SimpleAction) ZelusCodex::claim;
		});
	}

	private static State state(Player player) {
		if (player.zelusCodexState == null) {
			player.zelusCodexState = new State();
		}
		return (State) player.zelusCodexState;
	}

	public static void open(Player player) {
		// a player who already finished the starter screen has chosen their path
		if (!player.inTutorial) {
			ZelusTask.AWAKENING_IN_ZELUS.step(player, ZelusTask.STEP_PATH);
		}
		player.openInterface(ToplevelComponent.MAINMODAL, INTERFACE);
		player.getPacketSender().sendString(INTERFACE, TITLE, "Zelus Codex");
		draw(player, true);
	}

	static void announceComplete(Player player, String title) {
		player.sendMessage("<col=ff981f><shad=000000>[Zelus Codex]</shad></col> Task complete: <col=ffffff>" + title
			+ "</col>! Claim your reward in the Zelus Codex.");
		if (player.isVisibleInterface(INTERFACE)) {
			draw(player, false);
		}
	}

	private static void draw(Player player, boolean resetScroll) {
		var s = state(player);
		var ps = player.getPacketSender();

		int done = 0;
		for (Task t : TASKS) {
			if (t.progress(player) >= t.goal()) done++;
		}
		ps.sendString(INTERFACE, SUMMARY, GREY + "Completed" + END + " " + WHITE + done + " / " + TASKS.size() + END);

		for (Tier t : Tier.values()) {
			int tab = FIRST_TAB + t.ordinal() * STONE;
			ps.setHidden(INTERFACE, tab + PRESSED, t != s.tier);
			ps.sendString(INTERFACE, tab + LABEL, (t == s.tier ? "<col=ffc35a>" : ORANGE) + t.label + END);
		}

		s.rows.clear();
		int tierDone = 0;
		for (Task t : TASKS) {
			if (t.tier() == s.tier) {
				s.rows.add(t);
				if (t.progress(player) >= t.goal()) tierDone++;
			}
		}
		if (s.selected == null || s.selected.tier() != s.tier) {
			s.selected = s.rows.isEmpty() ? null : firstOpen(player, s.rows);
		}
		ps.sendString(INTERFACE, LIST_TITLE, s.tier.label + " tasks " + GREY + "(" + tierDone + "/" + s.rows.size() + ")" + END);

		for (int r = 0; r < ROWS; r++) {
			int row = FIRST_ROW + r * ROW_STRIDE;
			Task t = r < s.rows.size() ? s.rows.get(r) : null;
			ps.setHidden(INTERFACE, row, t == null);
			if (t == null) continue;
			int status = t.status(player);
			boolean selected = t == s.selected;
			ps.setHidden(INTERFACE, row + 2, !selected);
			ps.setHidden(INTERFACE, row + 3, !selected);
			ps.sendClientScript(SET_COLOUR, "Ii", INTERFACE << 16 | (row + 4), PIP[status]);
			ps.sendString(INTERFACE, row + 6, NAME_COLOUR[status] + t.name() + END);
		}
		ps.setHidden(INTERFACE, EMPTY, !s.rows.isEmpty());
		if (s.rows.isEmpty()) {
			ps.sendString(INTERFACE, EMPTY, "No tasks in this tier yet.");
		}
		if (resetScroll || s.sentRows != s.rows.size()) {
			int contentHeight = s.rows.size() * ROW_PITCH - 1;
			int count = contentHeight <= LIST_HEIGHT ? 0 : (int) Math.ceil((contentHeight - LIST_HEIGHT / 3D) / 15D);
			ScrollbarClientScript.create().interfaceId(INTERFACE).containerId(SCROLL).scrollbarChildId(SCROLLBAR)
				.childrenCount(count).withDarkGraphics().build().send(player);
			s.sentRows = s.rows.size();
		}
		drawCard(player, ps, s.selected);
	}

	/** The first task still to do (or to claim), else the first one. */
	private static Task firstOpen(Player player, List<Task> rows) {
		for (Task t : rows) {
			if (t.status(player) < 3) return t;
		}
		return rows.get(0);
	}

	private static void drawCard(Player player, PacketSender ps, Task t) {
		if (t == null) {
			ps.sendString(INTERFACE, TASK_NAME, "");
			ps.sendString(INTERFACE, TASK_TIER, "");
			ps.sendString(INTERFACE, TASK_DESC, "");
			ps.sendString(INTERFACE, PROGRESS_TEXT, "");
			ps.sendString(INTERFACE, REWARDS, "");
			ps.sendClientScript(SET_FILL_WIDTH, "Ii", INTERFACE << 16 | PROGRESS_FILL, 0);
			for (int i = 0; i < REWARD_SLOTS; i++) rewardSlot(ps, i, null);
			ps.sendString(INTERFACE, CLAIM + LABEL, GREY + "Claim Reward" + END);
			return;
		}
		int progress = t.progress(player), goal = t.goal(), status = t.status(player);
		ps.sendString(INTERFACE, TASK_NAME, t.name());
		ps.sendString(INTERFACE, TASK_TIER, t.tier().label + " task" + " · " + category(t));
		ps.sendString(INTERFACE, TASK_DESC, t.zelus != null ? t.zelus.description : t.legacy.getAchievementDesc());
		ps.sendString(INTERFACE, PROGRESS_TEXT, (progress >= goal ? GREEN : WHITE) + progress + " / " + goal + END);
		int width = goal <= 0 ? 0 : (int) Math.round(BAR_WIDTH * (double) progress / goal);
		ps.sendClientScript(SET_FILL_WIDTH, "Ii", INTERFACE << 16 | PROGRESS_FILL, progress > 0 ? Math.max(2, width) : 0);
		ps.sendString(INTERFACE, REWARDS, rewardText(t));
		Item[] items = t.zelus != null ? t.zelus.items : t.legacy.getItems();
		for (int i = 0; i < REWARD_SLOTS; i++) rewardSlot(ps, i, i < items.length ? items[i] : null);
		ps.sendString(INTERFACE, CLAIM + LABEL, switch (status) {
			case 3 -> GREEN + "Claimed" + END;
			case 2 -> "<col=ffc35a>Claim Reward" + END;
			default -> GREY + "In progress" + END;
		});
	}

	private static void rewardSlot(PacketSender ps, int i, Item item) {
		int well = FIRST_REWARD + i * REWARD_STRIDE;
		for (int k = 0; k < REWARD_STRIDE; k++) ps.setHidden(INTERFACE, well + k, item == null);
		if (item != null) ps.sendItem(INTERFACE, well + 2, item.getId(), item.getAmount());
	}

	private static String category(Task t) {
		if (t.zelus != null) return t.zelus.category;
		String skill = t.legacy.getSkillName();
		return skill == null || skill.isEmpty() ? "General" : Character.toUpperCase(skill.charAt(0)) + skill.substring(1);
	}

	private static String rewardText(Task t) {
		var lines = new ArrayList<String>();
		int points = t.zelus != null ? t.zelus.points : t.legacy.getReasonPointReward();
		if (points > 0) lines.add(bullet(YELLOW + String.format(Locale.ROOT, "%,d", points) + END + " Zelus points"));
		if (t.legacy != null) {
			if (t.legacy.getAchievementPointReward() > 0)
				lines.add(bullet(YELLOW + t.legacy.getAchievementPointReward() + END + " achievement point"
					+ (t.legacy.getAchievementPointReward() == 1 ? "" : "s")));
			if (t.legacy.getExperienceReward() > 0)
				lines.add(bullet(YELLOW + String.format(Locale.ROOT, "%,d", t.legacy.getExperienceReward()) + END + " "
					+ category(t) + " XP"));
			if (t.legacy.getPKPointReward() > 0)
				lines.add(bullet(YELLOW + t.legacy.getPKPointReward() + END + " PK points"));
		}
		// three lines fit above the item slots; the items show themselves below
		return String.join("<br>", lines.subList(0, Math.min(3, lines.size())));
	}

	private static String bullet(String s) {
		return WHITE + "-" + END + " " + s;
	}

	private static void claim(Player player) {
		var s = state(player);
		Task t = s.selected;
		if (t == null) return;
		if (t.legacy != null) {
			player.AchievementRewardHandler(t.legacy);
			draw(player, false);
			return;
		}
		ZelusTask task = t.zelus;
		if (task.claimed(player)) {
			player.sendMessage("You've already claimed the reward for " + task.title + ".");
			return;
		}
		if (task.progress(player) < task.goal) {
			player.sendMessage("Complete " + task.title + " first: " + task.progress(player) + " / " + task.goal + ".");
			return;
		}
		if (player.getInventory().getFreeSlots() < task.items.length) {
			player.sendMessage("You need " + task.items.length + " free inventory space"
				+ (task.items.length == 1 ? "" : "s") + " to claim this reward.");
			return;
		}
		task.markClaimed(player);
		player.updateReasonPoints(task.points);
		for (Item item : task.items) player.getInventory().addOrDrop(new Item(item.getId(), item.getAmount()));
		player.totalAchievementsCompleted++;
		player.sendMessage("<col=ff981f><shad=000000>[Zelus Codex]</shad></col> <col=00ff00>Reward claimed: " + task.title
			+ "</col> (" + String.format(Locale.ROOT, "%,d", task.points) + " Zelus points).");
		draw(player, false);
	}
}
