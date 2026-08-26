package io.ruin.model.content;

import io.ruin.cache.ItemID;
import io.ruin.model.entity.npc.NPCAction;
import io.ruin.model.entity.player.Player;
import io.ruin.model.entity.shared.listeners.LoginListener;
import io.ruin.model.inter.ToplevelComponent;
import io.ruin.model.item.Item;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Daily login reward streak. Shares interface 1109 with DailyVoteInterface
 * (same widget, populated with different data depending on which one the
 * player last opened) -- see DailyVoteInterface.register() for the shared
 * claim button dispatch, and Player.viewingDailyLoginRewards for the flag
 * that routes it. Progresses once per calendar day on login instead of on
 * an external vote postback.
 */
public class DailyLoginInterface {
	public class DailyReward {
		transient boolean claimed;
		transient Item reward;

		public DailyReward(boolean claimed, Item reward) {
			this.claimed = claimed;
			this.reward = reward;
		}

		public void setClaimed(boolean claimed) {
			this.claimed = claimed;
		}
	}

	private static final int INTERFACE_ID = 1109;

	// icon used to display donator-point rewards in the reward slots (matches the same
	// stand-in icon used elsewhere for this currency, e.g. LoyaltyTitleManager.DONATOR_POINTS_ICON)
	private static final int DONATOR_POINTS_ICON = 8851;

	// 1-indexed login day -> donator points awarded instead of an inventory item; checked in
	// claimReward() before the normal item-granting path
	private static final Map<Integer, Integer> DONATOR_POINTS_DAYS = Map.of(
			6, 100,
			15, 350,
			25, 500,
			28, 1000);

	private static final List<Item> loginRewards = Arrays.asList(
			new Item(ItemID.PRAYER_POTION4, 5),
			new Item(ItemID.SUPER_RESTORE4, 5),
			new Item(ItemID.DIVINE_SUPER_COMBAT_POTION4, 1),
			new Item(ItemID.COINS_995, 350000),
			new Item(ItemID.SARADOMIN_BREW4, 5),
			new Item(DONATOR_POINTS_ICON, 100),              // Day 6: 100 Donator Points (see DONATOR_POINTS_DAYS)
			new Item(ItemID.DHAROKS_ARMOUR_SET, 1),
			new Item(ItemID.COINS_995, 1000000),
			new Item(608, 5),                                // Drop Rate Scroll
			new Item(ItemID.INSTANCE_TOKEN, 5),
			new Item(ItemID.BLUE_PARTYHAT, 1),
			new Item(ItemID.BANDOS_TASSETS, 1),
			new Item(30452, 1),                              // Slayer mystery box
			new Item(33020, 1),                              // Perk Exp Lamp
			new Item(DONATOR_POINTS_ICON, 350),              // Day 15: 350 Donator Points (see DONATOR_POINTS_DAYS)
			new Item(ItemID.COINS_995, 10000000),
			new Item(ItemID.ABYSSAL_WHIP, 1),
			new Item(6199, 1),                               // Mystery box
			new Item(30452, 1),                              // Slayer mystery box
			new Item(ItemID.TOXIC_BLOWPIPE, 1),
			new Item(ItemID.COINS_995, 15000000),
			new Item(ItemID.VESTAS_CHAINBODY, 1),
			new Item(ItemID.MASORI_CHAPS, 1),
			new Item(6199, 2),                               // Mystery box x2
			new Item(DONATOR_POINTS_ICON, 500),              // Day 25: 500 Donator Points (see DONATOR_POINTS_DAYS)
			new Item(ItemID.DEVOUT_BOOTS, 1),
			new Item(ItemID.RED_PARTYHAT, 1),
			new Item(DONATOR_POINTS_ICON, 1000));            // Day 28: 1000 Donator Points (see DONATOR_POINTS_DAYS)

	public static void register() {
		// Interface action 468 is registered once, in DailyVoteInterface,
		// and dispatches to whichever of the two systems the player last
		// opened -- registering it again here would silently overwrite
		// that handler (InterfaceHandler.register keys by component, last
		// write wins).
		LoginListener.register(p -> p.getDailyLogin().loginCheck());
		NPCAction.register(1810, 1, (player, npc) -> player.getDailyLogin().open());
	}

	Player player;
	List<Item> rewardsLogged = new ArrayList<>();

	HashMap<Integer, DailyReward> entries = new HashMap<>();

	public DailyLoginInterface(Player player) {
		this.player = player;
	}

	public void open() {
		player.viewingDailyLoginRewards = true;

		player.openInterface(ToplevelComponent.MAINMODAL, INTERFACE_ID);
		// Component 14 is the title text baked into the widget (see
		// data/cache/toml/1_patches/interface/1109.toml:372, "Daily Vote
		// Streak" by default) -- overridden here since this screen is
		// shared with DailyVoteInterface, whose open() resets it back.
		player.getPacketSender().sendString(INTERFACE_ID, 14, "Daily Login Streak");
		player.getPacketSender().sendString(INTERFACE_ID, 22, "Current Login Streak: " + player.loginStreak);
		int startingLockComponent = 42;
		int startingClaimedComponent = 40;
		int startingContainerComponent = 41;
		int startingTextComponent = 39;

		List<Integer> itemContainerComponentIds = new ArrayList<>();

		int loops = 0;
		for (int i = 0; i < 28; i++) {
			if (loops == 4) {
				startingClaimedComponent++;
				startingContainerComponent++;
				startingTextComponent++;
				loops = 0;
			}
			player.getPacketSender().sendString(INTERFACE_ID, startingClaimedComponent, "<col=cc3333>Unclaimed");

			player.getPacketSender().sendClientScript(
					149, "IviiiIsssss",
					INTERFACE_ID << 16 | startingContainerComponent, 1000 + i,
					4, 7, 1, -1, "", "", "", "", "");

			player.getPacketSender().sendItems(
					-1,
					startingContainerComponent,
					1000 + i,
					loginRewards.get(i));
			itemContainerComponentIds.add(i, startingContainerComponent);
			player.getPacketSender().sendString(INTERFACE_ID, startingTextComponent, "Day " + (i + 1));

			loops++;
			startingClaimedComponent += 14;
			startingContainerComponent += 14;
			startingTextComponent += 14;
		}
		player.getPacketSender().sendClientScript(
				149, "IviiiIsssss",
				INTERFACE_ID << 16 | 464, 2850,
				4, 7, 1, -1, "", "", "", "", "");

		var rewardIdx = player.loginStreak - 1;
		var reward = rewardIdx < 0 ? loginRewards.get(0)
				: rewardIdx >= loginRewards.size() ? loginRewards.getLast() : loginRewards.get(rewardIdx);
		player.getPacketSender().sendItems(
				-1,
				464,
				2850,
				reward);

		loops = 0;
		startingClaimedComponent = 40;

		for (int i = 0; i < player.loginStreak; i++) {
			if (loops == 4) {
				startingLockComponent++;
				startingClaimedComponent++;
				loops = 0;
			}
			player.getPacketSender().setHidden(INTERFACE_ID, startingLockComponent, true);

			if (i == (player.loginStreak - 1) && player.claimedLoginToday)
				player.getPacketSender().sendString(INTERFACE_ID, startingClaimedComponent, "<col=26ff1f>Claimed");
			else if (i < (player.loginStreak - 1))
				player.getPacketSender().sendString(INTERFACE_ID, startingClaimedComponent, "<col=26ff1f>Claimed");
			else
				player.getPacketSender().sendString(INTERFACE_ID, startingClaimedComponent, "<col=cc3333>Unclaimed");

			startingLockComponent += 14;
			startingClaimedComponent += 14;
			loops++;
		}
	}

	/** Called once per login (see DailyLoginInterface's LoginListener registration). */
	public void loginCheck() {
		LocalDate today = LocalDate.now(ZoneId.systemDefault());
		LocalDate lastRewardDate = player.lastLoginRewardInEpoch <= 0
				? null
				: Instant.ofEpochSecond(player.lastLoginRewardInEpoch).atZone(ZoneId.systemDefault()).toLocalDate();

		if (lastRewardDate == null || !lastRewardDate.isEqual(today)) {
			if (lastRewardDate == null || lastRewardDate.isEqual(today.minusDays(1))) {
				player.loginStreak++;
				if (lastRewardDate != null)
					player.sendMessage("You now have a daily login streak of " + player.loginStreak + ".");
			} else {
				player.loginStreak = 1;
				player.sendMessage("Your daily login streak has been reset as you missed a day.");
			}

			if (player.loginStreak > loginRewards.size()) {
				player.loginStreak = 1;
				entries.clear();
				rewardsLogged.clear();
			}

			player.canClaimLoginReward = true;
			player.claimedLoginToday = false;
			player.lastLoginRewardInEpoch = Instant.now().getEpochSecond();
			player.todaysLoginReward = new DailyReward(false, loginRewards.get(player.loginStreak - 1));
			entries.put(player.loginStreak, player.todaysLoginReward);
		}

		// Auto-open so the player is shown they have a reward waiting -- fires on every
		// login while today's reward sits unclaimed, not just the first login of the day.
		if (player.canClaimLoginReward && !player.claimedLoginToday) {
			open();
		}
	}

	public void claimReward(Player player) {
		if (player.getInventory().getFreeSlots() < 3) {
			player.sendMessage("You need at least 3 free inventory slots to claim your reward.");
			return;
		}
		if (player.claimedLoginToday) {
			player.sendMessage("You have already claimed your reward for today.");
			return;
		}
		if (!player.canClaimLoginReward) {
			player.sendMessage("You must log in today to claim this reward.");
			return;
		}

		player.canClaimLoginReward = false;

		Integer donatorPoints = DONATOR_POINTS_DAYS.get(player.loginStreak);
		if (donatorPoints != null) {
			player.updateDonatorPoints(donatorPoints);
			player.claimedLoginToday = true;
			player.sendMessage("You have received " + donatorPoints + " donator points.");
			player.getDailyLogin().open();
			return;
		}

		var rewardIdx = player.loginStreak - 1;

		Item reward;
		if (rewardIdx >= loginRewards.size()) {
			reward = loginRewards.getLast();
		} else {
			reward = loginRewards.get(rewardIdx);
		}

		player.getInventory().addOrDrop(reward);
		player.claimedLoginToday = true;
		player.sendMessage("You have received " + reward.getAmount() + "x " + reward.getDef().name + ".");
		player.getDailyLogin().open();
	}
}
