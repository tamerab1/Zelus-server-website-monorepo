package io.ruin.model.content.combatachievements;

import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.InterfaceHandler;
import io.ruin.model.inter.ToplevelComponent;
import io.ruin.model.inter.actions.SimpleAction;
import io.ruin.model.map.object.actions.ObjectAction;

import java.util.List;

public class CombatAchievementInterface {
	private final int INTERFACE_ID = 1130;
	/** Claim Rewards (the scroll icon by the close button) and the Search box (2026-10-20: both had no handler). */
	private static final int CLAIM = 17, SEARCH = 91, TITLE = 14, TIER_LABEL = 853;
	private CombatAchievement.Tier tier;
	private FilterType filterType;
	/** Search text (lower case): lists matching tasks from every tier; null = the normal tier view. */
	private String search;

	public void open(Player player) {
		player.openInterface(ToplevelComponent.MAINMODAL, INTERFACE_ID);
		player.getPacketSender().sendString(INTERFACE_ID, TITLE, "Combat Achievements");
		loadCompletionCount(player);
		if(tier == null) {
			sendTier(player, CombatAchievement.Tier.EASY);
		} else {
			sendTier(player, tier);
		}

	}
	boolean typeDropdownOpen = false;
	private void toggleTypeDropDown(Player player, boolean force) {
		if(!typeDropdownOpen) {
			typeDropdownOpen = true;
			player.getPacketSender().setHidden(INTERFACE_ID, 871, false);
		} else {
			typeDropdownOpen = false;
			player.getPacketSender().setHidden(INTERFACE_ID, 871, true);
		}
		if(tierDropdownOpen && !force)
			toggleTierDropDown(player, true);
	}

	boolean tierDropdownOpen = false;
	private void toggleTierDropDown(Player player, boolean force) {
		if(!tierDropdownOpen) {
			tierDropdownOpen = true;
			player.getPacketSender().setHidden(INTERFACE_ID, 855, false);
		} else {
			tierDropdownOpen = false;
			player.getPacketSender().setHidden(INTERFACE_ID, 855, true);
		}
		if(typeDropdownOpen && !force)
			toggleTypeDropDown(player, true);
	}

	private void loadCompletionCount(Player player) {
		player.getPacketSender().sendString(INTERFACE_ID, 23, sendAchievementProgress(player, CombatAchievement.Tier.EASY));
		player.getPacketSender().sendString(INTERFACE_ID, 34, sendAchievementProgress(player, CombatAchievement.Tier.MEDIUM));
		player.getPacketSender().sendString(INTERFACE_ID, 45, sendAchievementProgress(player, CombatAchievement.Tier.HARD));
		player.getPacketSender().sendString(INTERFACE_ID, 56, sendAchievementProgress(player, CombatAchievement.Tier.ELITE));
		player.getPacketSender().sendString(INTERFACE_ID, 67, sendAchievementProgress(player, CombatAchievement.Tier.MASTER));
		player.getPacketSender().sendString(INTERFACE_ID, 78, sendAchievementProgress(player, CombatAchievement.Tier.GRANDMASTER));
	}

	/** A tier tab / the tier dropdown: shows that tier (and ends a search). */
	private void selectTier(Player player, CombatAchievement.Tier tier) {
		search = null;
		sendTier(player, tier);
	}

	private void sendTier(Player player, CombatAchievement.Tier tier) {
		this.tier = tier;
		int startingParentComp = 94;
		int startingSpriteComp = 96;
		int startingNameComp = 97;
		int startingDescComp = 98;
		List<CombatAchievements> achievements = player.combatAchievementsList.stream().filter(search != null
			? achievement -> matches(achievement, search)
			: achievement -> achievement.getCombatAchievement().getTier() == tier &&
				((filterType == FilterType.COMPLETED) == player.combatAchievementStore.getOrDefault(achievement.ordinal(), false))
		).limit(150).toList(); // 150 rows in the list


		for(int i = 94; i < 844; i+=5) {
			player.getPacketSender().setHidden(INTERFACE_ID, i, true);
		}
		for (CombatAchievements achievement : achievements) {
			// a completed task's name in deep emerald (the default near-white was almost invisible on its row)
			boolean done = player.combatAchievementStore.getOrDefault(achievement.ordinal(), false);
			player.getPacketSender().sendString(INTERFACE_ID, startingNameComp,
				(done ? "<col=0b5e3c>" : "") + achievement.getCombatAchievement().getName() + (done ? "</col>" : ""));
			player.getPacketSender().sendString(INTERFACE_ID, startingDescComp, achievement.getCombatAchievement().getDesc());
			player.getPacketSender().setHidden(INTERFACE_ID, startingParentComp, false);
			startingDescComp += 5;
			startingSpriteComp += 5;
			startingNameComp += 5;
			startingParentComp += 5;
		}
		player.getPacketSender().sendString(INTERFACE_ID, TIER_LABEL, search != null ? "Search (" + achievements.size() + ")"
			: tier.toString().substring(0, 1).toUpperCase() + tier.toString().substring(1).toLowerCase());
		if(tierDropdownOpen)
			toggleTierDropDown(player, false);
	}

	private static boolean matches(CombatAchievements achievement, String query) {
		CombatAchievement a = achievement.getCombatAchievement();
		return a != null && (a.getName().toLowerCase().contains(query) || a.getDesc().toLowerCase().contains(query));
	}

	private void promptSearch(Player player) {
		player.stringInput("Search combat achievements (name or description):", text -> {
			String query = text == null ? "" : text.trim().toLowerCase();
			search = query.isEmpty() ? null : query;
			open(player); // the input box closes the screen
			if (search != null)
				player.sendMessage("Showing combat achievements matching \"" + query + "\". Pick a tier to go back.");
		});
	}

	private void claim(Player player) {
		CombatAchievementSystem.claimNextReward(player);
		if (player.isVisibleInterface(INTERFACE_ID))
			loadCompletionCount(player);
	}

	private void sendFilter(Player player, FilterType filterType) {
		this.filterType = filterType;
		search = null;
		sendTier(player, tier);
		toggleTypeDropDown(player, false);
		player.getPacketSender().sendString(INTERFACE_ID, 869, filterType == FilterType.UNCOMPLETED ? "Uncompleted" : "Completed");
	}

	private String sendAchievementProgress(Player player, CombatAchievement.Tier tier) {
		int completed = CombatAchievementSystem.getAchievementsCompletedByTier(player, tier);
		int total = CombatAchievementSystem.getCombatAchievementsOfType(tier);
		String colour;
		if (completed == 0) {
			colour = "FF0000";
		} else if (completed == total) {
			colour = "27ae60";
		} else {
			colour = "c2a532";
		}
		int barComp = -1;
		switch (tier) {
			case EASY -> barComp = 26;
			case MEDIUM -> barComp = 37;
			case HARD -> barComp = 48;
			case ELITE -> barComp = 59;
			case MASTER -> barComp = 70;
			case GRANDMASTER -> barComp = 81;
		}
		int barInterfaceHash = INTERFACE_ID << 16 | barComp;
		float width = (float) completed / total;
		float barWidth = (width * 70);
		if(completed == 0)
			barWidth = 0;
		player.getPacketSender().sendClientScript(10606, "Ii", barInterfaceHash, (int) barWidth);
		return "<col=" + colour + ">" + completed + "/" + total;
	}

	public static void register() {
		InterfaceHandler.register(1130, h -> {
			h.actions[870] = (SimpleAction) player -> player.getCombatAchievementInterface().toggleTypeDropDown(player, false);
			h.actions[868] = (SimpleAction) player -> player.getCombatAchievementInterface().toggleTypeDropDown(player, false);
			h.actions[854] = (SimpleAction) player -> player.getCombatAchievementInterface().toggleTierDropDown(player, false);
			h.actions[852] = (SimpleAction) player -> player.getCombatAchievementInterface().toggleTierDropDown(player, false);
			h.actions[20] = (SimpleAction) player -> player.getCombatAchievementInterface().selectTier(player, CombatAchievement.Tier.EASY);
			h.actions[859] = (SimpleAction) player -> player.getCombatAchievementInterface().selectTier(player, CombatAchievement.Tier.EASY);
			h.actions[31] = (SimpleAction) player -> player.getCombatAchievementInterface().selectTier(player, CombatAchievement.Tier.MEDIUM);
			h.actions[860] = (SimpleAction) player -> player.getCombatAchievementInterface().selectTier(player, CombatAchievement.Tier.MEDIUM);
			h.actions[42] = (SimpleAction) player -> player.getCombatAchievementInterface().selectTier(player, CombatAchievement.Tier.HARD);
			h.actions[861] = (SimpleAction) player -> player.getCombatAchievementInterface().selectTier(player, CombatAchievement.Tier.HARD);
			h.actions[53] = (SimpleAction) player -> player.getCombatAchievementInterface().selectTier(player, CombatAchievement.Tier.ELITE);
			h.actions[862] = (SimpleAction) player -> player.getCombatAchievementInterface().selectTier(player, CombatAchievement.Tier.ELITE);
			h.actions[64] = (SimpleAction) player -> player.getCombatAchievementInterface().selectTier(player, CombatAchievement.Tier.MASTER);
			h.actions[863] = (SimpleAction) player -> player.getCombatAchievementInterface().selectTier(player, CombatAchievement.Tier.MASTER);
			h.actions[75] = (SimpleAction) player -> player.getCombatAchievementInterface().selectTier(player, CombatAchievement.Tier.GRANDMASTER);
			h.actions[864] = (SimpleAction) player -> player.getCombatAchievementInterface().selectTier(player, CombatAchievement.Tier.GRANDMASTER);
			h.actions[875] = (SimpleAction) player -> player.getCombatAchievementInterface().sendFilter(player, FilterType.UNCOMPLETED);
			h.actions[876] = (SimpleAction) player -> player.getCombatAchievementInterface().sendFilter(player, FilterType.COMPLETED);
			h.actions[CLAIM] = (SimpleAction) player -> player.getCombatAchievementInterface().claim(player);
			h.actions[SEARCH] = (SimpleAction) player -> player.getCombatAchievementInterface().promptSearch(player);
		});
	}

	enum FilterType {
		COMPLETED,
		UNCOMPLETED
	}
}
