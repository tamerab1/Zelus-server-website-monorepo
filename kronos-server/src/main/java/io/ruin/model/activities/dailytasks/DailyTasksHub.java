package io.ruin.model.activities.dailytasks;

import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.InterfaceHandler;
import io.ruin.model.inter.ToplevelComponent;
import io.ruin.model.inter.actions.SimpleAction;
import io.ruin.model.item.Item;
import io.ruin.network.PacketSender;

/**
 * The Karn Daily Tasks window, interface 1188 (user 2026-11-15: "a new custom design for the daily tasks, matched to these
 * designs"; .dev BuildKarnHub + cache stage 42). Same rules as the old 1106 window (DailyTasksInterface holds the claim
 * logic): five tasks with their progress, an item + coin reward each (Claim once done), and a completion bonus once all
 * five are claimed-ready. The reset countdown ticks while it is open. ::dailies, the welcome screen's Open and every old
 * entry point (DailyTasksInterface.open) land here.
 */
public final class DailyTasksHub {

	private DailyTasksHub() {
	}

	public static final int INTERFACE = 1188;
	private static final int CLOSE = 5, SUBTITLE = 6, FIRST_TASK = 7, TASK_STRIDE = 27, BAR_STEPS = 20, REWARD = 23, COINS = 24,
			CLAIM = 25, CLAIM_ON = 25257, CLAIM_OFF = 25259, BONUS_TEXT = 143, BONUS_ITEM = 144, BONUS_INFO = 145, BONUS_CLAIM = 146, INV_BASE = 3200;
	private static final String GOLD = "<col=f5d27a>", GREEN = "<col=7fe08a>", GREY = "<col=a89a88>", RED = "<col=ff7a6a>", END = "</col>";

	public static void register() {
		InterfaceHandler.register(INTERFACE, h -> {
			h.actions[CLOSE] = (SimpleAction) Player::closeInterfaces;
			for (int i = 0; i < 5; i++) {
				int index = i;
				h.actions[FIRST_TASK + i * TASK_STRIDE + CLAIM] = (SimpleAction) p -> {
					p.getDailyTaskInterface().claimReward(p, index);
					refresh(p);
				};
			}
			h.actions[BONUS_CLAIM] = (SimpleAction) p -> {
				p.getDailyTaskInterface().claimCompletionReward(p);
				refresh(p);
			};
		});
	}

	public static void open(Player p) {
		p.openInterface(ToplevelComponent.MAINMODAL, INTERFACE);
		refresh(p);
		p.startEvent(event -> {
			event.setCancelCondition(() -> !p.isVisibleInterface(INTERFACE));
			while (true) {
				event.delay(2);
				sendTimer(p);
			}
		});
	}

	private static void sendTimer(Player p) {
		p.getPacketSender().sendString(INTERFACE, SUBTITLE, "Resets in " + p.getDailyTaskInterface().getTimeUntilReset());
	}

	private static String shorten(String s, int max) {
		return s == null ? "" : s.length() <= max ? s : s.substring(0, max - 2).trim() + "..";
	}

	private static void grid(PacketSender ps, int comp, int inv, Item item) {
		ps.sendClientScript(149, "IviiiIsssss", INTERFACE << 16 | comp, inv, 2, 2, 0, -1, "", "", "", "", ""); // 2x2: script 153 divides by (columns - 1)
		ps.sendIfEvents(INTERFACE, comp, 0, 0, 1024); // Examine
		ps.sendItems(-1, comp, inv, item == null ? new Item[0] : new Item[]{item});
	}

	static void refresh(Player p) {
		if (!p.isVisibleInterface(INTERFACE))
			return;
		PacketSender ps = p.getPacketSender();
		sendTimer(p);
		DailyTasks[] tasks = p.currentDailyTasks;
		int done = 0;
		for (int i = 0; i < 5; i++) {
			int base = FIRST_TASK + i * TASK_STRIDE;
			DailyTasks task = tasks == null ? null : tasks[i];
			if (task == null) {
				ps.sendString(INTERFACE, base, GREY + "No task" + END);
				ps.sendString(INTERFACE, base + 1, "");
				ps.sendString(INTERFACE, base + 2, "");
				for (int s = 0; s < BAR_STEPS; s++)
					ps.setHidden(INTERFACE, base + 3 + s, true);
				ps.setHidden(INTERFACE, base + REWARD, true);
				ps.setHidden(INTERFACE, base + COINS, true);
				ps.setHidden(INTERFACE, base + CLAIM, true);
				ps.setHidden(INTERFACE, base + CLAIM + 1, true);
				continue;
			}
			int total = Math.max(1, p.startingDailyTaskAmounts[i]);
			int progress = Math.max(0, total - p.currentDailyTaskAmounts[i]);
			boolean complete = p.currentDailyTaskAmounts[i] <= 0;
			boolean claimed = p.currentDailyRewardsClaimed[i];
			if (complete)
				done++;
			ps.sendString(INTERFACE, base, shorten(task.getName(), 30));
			ps.sendString(INTERFACE, base + 1, complete ? GREEN + "Done" + END : progress + " / " + total);
			String desc = (task.description + " " + p.startingDailyTaskAmounts[i] + " times.").replace("You're tasked with ", "")
					.replace("You must ", "").replaceAll("\\s+", " ").trim();
			desc = desc.isEmpty() ? desc : Character.toUpperCase(desc.charAt(0)) + desc.substring(1);
			ps.sendString(INTERFACE, base + 2, shorten(desc, 48));
			int fill = progress <= 0 ? 0 : Math.max(1, (int) Math.floor((double) BAR_STEPS * progress / total));
			for (int s = 0; s < BAR_STEPS; s++)
				ps.setHidden(INTERFACE, base + 3 + s, s >= fill);
			ps.setHidden(INTERFACE, base + REWARD, false);
			ps.setHidden(INTERFACE, base + COINS, false);
			grid(ps, base + REWARD, INV_BASE + i * 2, p.currentDailyRewards[i]);
			grid(ps, base + COINS, INV_BASE + i * 2 + 1, new Item(995, 500000 * (1 + task.difficulty.ordinal())));
			ps.setHidden(INTERFACE, base + CLAIM, false);
			ps.setHidden(INTERFACE, base + CLAIM + 1, false);
			// the button is crimson only when it can be pressed (claimable), dark otherwise
			ps.setGraphic(INTERFACE, base + CLAIM, complete && !claimed ? CLAIM_ON : CLAIM_OFF);
			ps.sendString(INTERFACE, base + CLAIM + 1, claimed ? GREY + "Claimed" + END : complete ? GOLD + "Claim" + END
					: progress > 0 ? GREY + "In progress" + END : GREY + "Not started" + END);
		}
		ps.sendString(INTERFACE, BONUS_TEXT, "Completed " + (done == 5 ? GREEN : done > 0 ? GOLD : RED) + done + " / 5" + END);
		grid(ps, BONUS_ITEM, INV_BASE + 10, p.dailyTasksCompletionReward);
		ps.sendString(INTERFACE, BONUS_INFO, "+ Karn points");
		ps.sendString(INTERFACE, BONUS_CLAIM + 1, p.dailyTaskCompletionRewardClaimed ? "Claimed" : done == 5 ? "Claim Bonus" : "Finish all 5");
	}
}
