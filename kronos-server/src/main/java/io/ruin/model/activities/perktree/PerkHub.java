package io.ruin.model.activities.perktree;

import io.ruin.api.utils.NumberUtils;
import io.ruin.model.ScrollbarClientScript;
import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.InterfaceHandler;
import io.ruin.model.inter.ToplevelComponent;
import io.ruin.model.inter.actions.SimpleAction;
import io.ruin.network.PacketSender;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The unified Zelus Perks screen (interface 1151, built by .dev/cache-restore-tool/src/BuildPerkInterface.java). It
 * replaces the four old perk screens (1094 home, 1095 shop, 1096 owned, 1097 repository) with one window: browse tabs
 * (All / Combat / Skilling / Utility / Owned / Sets), the 5 active slots, the 5 loadouts, a scrolling perk list with a
 * status badge per perk, and a detail panel with the actions. Every action goes through the unchanged
 * PlayerPerkHandler / PlayerPerkInterface logic (purchase incl. the free level 5 crystal, upgrade, equip, unequip,
 * loadouts). Buying and upgrading take a second click to confirm. Per-player state lives on PlayerPerkInterface.
 */
public final class PerkHub {

	public static final int INTERFACE = 1151;
	private static final int CLOSE = 2, TITLE = 3, SUB_HEADER = 4;
	/** Tabs: box, border, gold outline x2, label. */
	private static final int FIRST_TAB = 10, TAB_STRIDE = 5, TAB_OUTLINE = 2, TAB_LABEL = 4;
	/** Active slots: fill, shadow x2, border, icon, level (or unlock level when locked), padlock sprite. */
	private static final int ACTIVE_COUNT = 46, FIRST_SLOT = 47, SLOT_STRIDE = 7, SLOT_ICON = 4, SLOT_LEVEL = 5, SLOT_LOCK = 6;
	private static final int SAVE = 88, FIRST_LOADOUT = 90, LOADOUT_STRIDE = 4, LOADOUT_LABEL = 1, LOADOUT_CLEAR = 2;
	private static final int SCROLL = 115, SCROLLBAR = 116;
	/** Rows: layer, box, border, gold outline x2, icon, name, description, badge box, badge text. */
	private static final int FIRST_ROW = 117, ROW_STRIDE = 10, ROW_BOX = 1, ROW_OUTLINE = 3, ROW_ICON = 5, ROW_NAME = 6,
		ROW_DESC = 7, ROW_BADGE = 8, ROW_BADGE_TEXT = 9, ROWS = 52;
	private static final int ROW_PITCH = 38, SCROLL_HEIGHT = 120;
	private static final int DETAIL_ICON = 644, DETAIL_NAME = 645, DETAIL_LEVEL = 646, DETAIL_NOW = 647, DETAIL_NEXT = 648;
	private static final int BUTTON_C = 674;
	/** Wide centred button (Buy / Unlock) used when it is the only action. */
	private static final int BUTTON_WIDE = 853;
	/** Search bar: input box (click = search), its text, border, magnifier, Clear box + label. */
	private static final int SEARCH = 855, SEARCH_TEXT = 856, SEARCH_CLEAR = 859;
	/** Owned perk actions: two full-width bezel buttons (box + label each). */
	private static final int BUTTON_LEFT = 861, BUTTON_RIGHT = 863;
	/** Set Overview page (over the right column): title, back, status, effect, 5 required perks, Equip / Combinations. */
	private static final int SET_VIEW = 676, SET_TITLE = 682, SET_BACK = 683, SET_STATUS = 685, SET_EFFECT = 686,
		SET_REQUIRED = 687, FIRST_SET_PERK = 688, SET_PERK_STRIDE = 5, SET_PERK_ICON = 2, SET_PERK_NAME = 3,
		SET_PERK_STATE = 4, SET_EQUIP = 713, SET_COMBOS = 715;
	/** The 5 framed panels the Set Overview covers; hidden while it is open so nothing underneath takes clicks. */
	private static final int[] PANELS = {5, 40, 82, 110, 637};
	/** Combos Browser: header, Back to Set, sub line, scroll + bar, 4 cards (layer, fill, border, title, equip box/label,
	 *  then 5 perks x fill, border, icon, name, state). */
	private static final int COMBO_VIEW = 717, COMBO_TITLE = 723, COMBO_BACK = 724, COMBO_SUB = 726, COMBO_SCROLL = 727,
		COMBO_SCROLLBAR = 728, FIRST_COMBO = 729, COMBO_STRIDE = 31, COMBO_CARD_TITLE = 3, COMBO_CARD_EQUIP = 4,
		COMBO_FIRST_PERK = 6, COMBO_PERK_STRIDE = 5, COMBO_CARDS = 4, COMBO_PITCH = 86, COMBO_SCROLL_HEIGHT = 206;
	private static final int BUTTON_A = 649, BUTTON_B = 651;
	/** The detail panel's big icon slot (fill, border, icon), swapped for the loadout mini-slot row. */
	private static final int DETAIL_ICON_FILL = 642, DETAIL_ICON_BORDER = 643;
	/** Loadout preview row (hidden layer) + 5 mini slots of (fill, border, icon, level). */
	private static final int LOADOUT_ROW = 653, FIRST_MINI = 654, MINI_STRIDE = 4, MINI_ICON = 2, MINI_LEVEL = 3;

	private static final int SLOTS = 5, LOADOUTS = 5;
	/** Active slot unlocks: 3 base, the 4th at Perk Tree level 50, the 5th at 75 (PlayerPerkHandler.calculatePlayersTotalPerks). */
	private static final int[] SLOT_UNLOCK_LEVEL = {0, 0, 0, 50, 75};

	private static final String GOLD = "<col=ffc35a>", GREY = "<col=9a9a9a>", WHITE = "<col=ffffff>", GREEN = "<col=00ff00>",
		RED = "<col=ff5050>", ORANGE = "<col=ff981f>", YELLOW = "<col=ffff00>", END = "</col>";

	enum Tab {
		ALL("All"), COMBAT("Combat"), SKILLING("Skilling"), UTILITY("Utility"), OWNED("Owned"), SETS("Sets");
		final String label;

		Tab(String label) {
			this.label = label;
		}
	}

	private static final Tab[] TABS = Tab.values();

	/** A loadout slot selected for preview in the detail panel. */
	private record Loadout(int index) {
	}

	private static final Set<Perks> COMBAT = EnumSet.of(Perks.ACCURATE_BLOWS, Perks.ARCANE_ENHANCEMENT, Perks.BLESSED_BOLTS,
		Perks.BLOOD_SACRIFICE, Perks.CRIMSON_CHIN, Perks.DAMAGE_REFLECT, Perks.DOUBLE_TAP, Perks.ENHANCED_MAGICAL_WEAPONS,
		Perks.FINEST_WIZARDRY, Perks.RECURRENT_DAMAGE, Perks.SKILLED_RANGER, Perks.SPEEDY_STRIKES, Perks.TAKE_YOUR_TIME,
		Perks.THE_CRUSHER, Perks.THE_SLASHER, Perks.THE_STABBER, Perks.THE_SOULSPLIT, Perks.THE_SPECIAL_ATTACK, Perks.VANQUISHED_FOE);
	private static final Set<Perks> SKILLING = EnumSet.of(Perks.ARTS_N_CRAFTS, Perks.BOB_THE_BUILDER, Perks.DANCING_IN_THE_YIELDS,
		Perks.EXPERIENCE_ENHANCER, Perks.FLETCHING_DREAMS, Perks.HUNTING_IN_ITS_TRUE_FORM, Perks.KITCHEN_EXPERT, Perks.MASTER_THIEF,
		Perks.ONE_WITH_THE_RUNES, Perks.PACKS_N_POTIONS, Perks.RUNNING_PAYS, Perks.SMITHING_DYNASTY, Perks.SPEED_FISHER,
		Perks.TAKING_IT_ON_THE_CHIN, Perks.THE_ARSONIST, Perks.THE_ART_OF_MINING, Perks.THE_EFFICIENT_CHOP);

	private PerkHub() {
	}

	public static void register() {
		InterfaceHandler.register(INTERFACE, h -> {
			h.actions[CLOSE] = (SimpleAction) p -> p.closeInterface(ToplevelComponent.MAINMODAL);
			for (int t = 0; t < TABS.length; t++) {
				Tab tab = TABS[t];
				h.actions[FIRST_TAB + t * TAB_STRIDE] = (SimpleAction) p -> {
					PlayerPerkInterface s = p.getPerkInterface();
					s.hubTab = tab.ordinal();
					s.hubSearch = null;
					s.hubSelected = null;
					s.hubConfirm = null;
					refresh(p);
				};
			}
			for (int k = 0; k < SLOTS; k++) {
				int slot = k;
				h.actions[FIRST_SLOT + k * SLOT_STRIDE + SLOT_ICON] = (SimpleAction) p -> {
					if (p.getPlayerPerkHandler().removeFromActivePerks(p, slot))
						refresh(p);
				};
			}
			h.actions[SAVE] = (SimpleAction) p -> p.getPerkInterface().savePerkPreset(p);
			for (int r = 0; r < LOADOUTS; r++) {
				int preset = r;
				h.actions[FIRST_LOADOUT + r * LOADOUT_STRIDE] = (SimpleAction) p -> {
					PlayerPerkInterface s = p.getPerkInterface();
					s.hubSelected = new Loadout(preset);
					s.hubConfirm = null;
					refresh(p);
				};
				h.actions[FIRST_LOADOUT + r * LOADOUT_STRIDE + LOADOUT_CLEAR] = (SimpleAction) p -> {
					p.getPerkInterface().removePreset(p, preset);
					refresh(p);
				};
			}
			for (int r = 0; r < ROWS; r++) {
				int index = r;
				h.actions[FIRST_ROW + r * ROW_STRIDE + ROW_BOX] = (SimpleAction) p -> select(p, index, false);
				h.actions[FIRST_ROW + r * ROW_STRIDE + ROW_BADGE] = (SimpleAction) p -> select(p, index, true);
			}
			h.actions[BUTTON_A] = (SimpleAction) p -> button(p, true);
			h.actions[BUTTON_B] = (SimpleAction) p -> button(p, false);
			h.actions[BUTTON_C] = (SimpleAction) PerkHub::buttonC;
			h.actions[BUTTON_WIDE] = (SimpleAction) p -> button(p, true);
			h.actions[BUTTON_LEFT] = (SimpleAction) p -> button(p, true);
			h.actions[BUTTON_RIGHT] = (SimpleAction) p -> button(p, false);
			h.actions[SEARCH_CLEAR] = (SimpleAction) p -> {
				PlayerPerkInterface s = p.getPerkInterface();
				if (s.hubSearch != null) {
					s.hubSearch = null;
					refresh(p);
				}
			};
			h.actions[SEARCH] = (SimpleAction) p -> {
				PlayerPerkInterface s = p.getPerkInterface();
				p.stringInput("Search perks and perk sets:", text -> {
					String query = text == null ? "" : text.trim().toLowerCase();
					s.hubSearch = query.isEmpty() ? null : query;
					s.hubSelected = null;
					s.hubCombos = false;
					open(p); // the input box closed the screen
				});
			};
			h.actions[SET_BACK] = (SimpleAction) p -> {
				p.getPerkInterface().hubSelected = null;
				p.getPerkInterface().hubCombos = false;
				refresh(p);
			};
			h.actions[COMBO_BACK] = (SimpleAction) p -> {
				p.getPerkInterface().hubCombos = false;
				refresh(p);
			};
			for (int c = 0; c < COMBO_CARDS; c++) {
				int index = c;
				h.actions[FIRST_COMBO + c * COMBO_STRIDE + COMBO_CARD_EQUIP] = (SimpleAction) p -> {
					if (p.getPerkInterface().hubSelected instanceof PerkSets set && index < set.perkCombinations.length) {
						equipCombination(p, set, set.perkCombinations[index]);
						refresh(p);
					}
				};
			}
			h.actions[SET_EQUIP] = (SimpleAction) p -> {
				if (p.getPerkInterface().hubSelected instanceof PerkSets set) {
					equipSet(p, set);
					refresh(p);
				}
			};
			h.actions[SET_COMBOS] = (SimpleAction) p -> {
				if (p.getPerkInterface().hubSelected instanceof PerkSets set)
					showCombinations(p, set);
			};
		});
	}

	public static void open(Player player) {
		PlayerPerkInterface s = player.getPerkInterface();
		s.hubConfirm = null;
		s.hubRows = null; // a fresh screen needs its scrollbar set up (refresh only sends it when the list changes)
		player.openInterface(ToplevelComponent.MAINMODAL, INTERFACE);
		player.getPacketSender().sendString(INTERFACE, TITLE, "ZELUS PERKS");
		player.getPacketSender().setHidden(INTERFACE, SAVE, true);
		player.getPacketSender().setHidden(INTERFACE, SAVE + 1, true);
		refresh(player);
	}

	/* ---------------- rendering ---------------- */

	private static void refresh(Player player) {
		PlayerPerkInterface s = player.getPerkInterface();
		PlayerPerkHandler handler = player.getPlayerPerkHandler();
		PacketSender ps = player.getPacketSender();
		Tab tab = TABS[s.hubTab];

		int maxSlots = handler.calculatePlayersTotalPerks(player);
		List<Perks> active = handler.getActivePerks(player);
		int xpToNext = handler.calculateExperienceForLevel(player.perkTreeLevel) - player.perkTreeExperience;
		ps.sendString(INTERFACE, SUB_HEADER, YELLOW + "Perk Points:" + END + " " + GREEN + "[ " + NumberUtils.formatNumber(player.perkPoints) + " ]" + END
			+ "  <col=7f7f7f>|</col>  " + WHITE + "Perk Tree:" + END + " " + ORANGE + "Lv " + player.perkTreeLevel + END
			+ " <col=a0a0a0>(" + NumberUtils.formatNumber(Math.max(0, xpToNext)) + " XP to next)</col>");

		for (int t = 0; t < TABS.length; t++) {
			int box = FIRST_TAB + t * TAB_STRIDE;
			boolean tabActive = TABS[t] == tab && s.hubSearch == null;
			outline(ps, box + TAB_OUTLINE, tabActive);
			ps.sendString(INTERFACE, box + TAB_LABEL, (tabActive ? GOLD : WHITE) + TABS[t].label + END);
		}

		// active slots
		ps.sendString(INTERFACE, ACTIVE_COUNT, active.size() + " / " + maxSlots);
		for (int k = 0; k < SLOTS; k++) {
			int slot = FIRST_SLOT + k * SLOT_STRIDE;
			boolean locked = k >= maxSlots;
			Perks perk = !locked && k < active.size() ? active.get(k) : null;
			ps.setHidden(INTERFACE, slot + SLOT_ICON, perk == null);
			ps.setHidden(INTERFACE, slot + SLOT_LOCK, !locked);
			ps.sendString(INTERFACE, slot + SLOT_LEVEL, locked ? RED + "Lv " + SLOT_UNLOCK_LEVEL[k] + END
				: perk == null ? "" : "Lv " + level(player, perk));
			if (perk != null)
				ps.sendClientScript(perk.scriptId, "I", INTERFACE << 16 | (slot + SLOT_ICON));
		}

		// loadouts
		String[] names = {player.presetNameOne, player.presetNameTwo, player.presetNameThree, player.presetNameFour, player.presetNameFive};
		for (int r = 0; r < LOADOUTS; r++) {
			boolean empty = names[r] == null || names[r].equalsIgnoreCase("Empty");
			ps.sendString(INTERFACE, FIRST_LOADOUT + r * LOADOUT_STRIDE + LOADOUT_LABEL,
				(empty ? GREY + (r + 1) + ". Empty" : WHITE + (r + 1) + ". " + names[r]) + END);
		}

		// list rows
		List<Object> rows = s.hubSearch != null ? search(s.hubSearch) : rows(player, tab);
		// the scrollbar script (10532) resets the scroll to the top: only send it when the list itself changed, so
		// selecting a row lower down keeps it in view
		boolean listChanged = s.hubRows == null || !s.hubRows.equals(rows);
		s.hubRows = rows;
		ps.sendString(INTERFACE, SEARCH_TEXT, s.hubSearch != null ? WHITE + s.hubSearch + END : GREY + "Search perks..." + END);
		ps.sendString(INTERFACE, SEARCH_CLEAR + 1, s.hubSearch != null ? ORANGE + "Clear" + END : GREY + "Clear" + END);
		for (int r = 0; r < ROWS; r++) {
			int row = FIRST_ROW + r * ROW_STRIDE;
			boolean used = r < rows.size();
			ps.setHidden(INTERFACE, row, !used);
			if (!used)
				continue;
			Object entry = rows.get(r);
			outline(ps, row + ROW_OUTLINE, entry == s.hubSelected);
			if (entry instanceof Perks perk)
				sendPerkRow(player, row, perk);
			else
				sendSetRow(player, row, (PerkSets) entry);
		}
		if (listChanged) {
			int contentHeight = rows.size() * ROW_PITCH - 2;
			int count = contentHeight <= SCROLL_HEIGHT ? 0 : (int) Math.ceil((contentHeight - SCROLL_HEIGHT / 3D) / 15D);
			ScrollbarClientScript.create().interfaceId(INTERFACE).containerId(SCROLL).scrollbarChildId(SCROLLBAR)
				.childrenCount(count).withDarkGraphics().build().send(player);
		}

		sendDetail(player);
	}

	/** Perks then perk sets whose name contains the query (any tab), capped at the row count. */
	private static List<Object> search(String query) {
		List<Object> rows = new ArrayList<>();
		for (Perks perk : Perks.VALUES)
			if (name(perk).toLowerCase().contains(query))
				rows.add(perk);
		rows.sort(Comparator.comparing(o -> name((Perks) o)));
		for (PerkSets set : PerkSets.values())
			if (set.perkSet.getPerkSetName().toLowerCase().contains(query))
				rows.add(set);
		return rows.size() > ROWS ? new ArrayList<>(rows.subList(0, ROWS)) : rows;
	}

	private static List<Object> rows(Player player, Tab tab) {
		List<Object> rows = new ArrayList<>();
		if (tab == Tab.SETS) {
			for (PerkSets set : PerkSets.values())
				rows.add(set);
			rows.sort(Comparator.comparing(o -> ((PerkSets) o).perkSet.getPerkSetName()));
			return rows;
		}
		for (Perks perk : Perks.VALUES) {
			boolean show = switch (tab) {
				case COMBAT -> COMBAT.contains(perk);
				case SKILLING -> SKILLING.contains(perk);
				case UTILITY -> !COMBAT.contains(perk) && !SKILLING.contains(perk);
				case OWNED -> owns(player, perk);
				default -> true;
			};
			if (show)
				rows.add(perk);
		}
		rows.sort(Comparator.comparing(o -> name((Perks) o)));
		return rows;
	}

	private static void sendPerkRow(Player player, int row, Perks perk) {
		PacketSender ps = player.getPacketSender();
		boolean owned = owns(player, perk);
		ps.sendString(INTERFACE, row + ROW_NAME, GOLD + name(perk) + END
			+ (owned ? GREY + "  Lv " + level(player, perk) + "/" + maxLevel(perk) + END : ""));
		ps.sendString(INTERFACE, row + ROW_DESC, Objects.requireNonNull(perk.getInterfacePerk()).getPerkDescription());
		ps.sendString(INTERFACE, row + ROW_BADGE_TEXT, badge(player, perk));
		ps.sendClientScript(perk.scriptId, "I", INTERFACE << 16 | (row + ROW_ICON));
	}

	private static void sendSetRow(Player player, int row, PerkSets set) {
		PacketSender ps = player.getPacketSender();
		boolean active = player.activePerkSetsList.contains(set);
		ps.sendString(INTERFACE, row + ROW_NAME, GOLD + set.perkSet.getPerkSetName() + END);
		ps.sendString(INTERFACE, row + ROW_DESC, set.perkSet.getPerkSetDescription());
		int owned = ownedCount(player, bestCombination(player, set));
		ps.sendString(INTERFACE, row + ROW_BADGE_TEXT, active ? GOLD + "[ Active ]" + END
			: owned == 5 ? GREEN + "[ Ready ]" + END : GREY + "[ " + owned + "/5 owned ]" + END);
		ps.sendClientScript(set.scriptId, "I", INTERFACE << 16 | (row + ROW_ICON));
	}

	private static String badge(Player player, Perks perk) {
		if (isActive(player, perk))
			return GOLD + "[ Equipped ]" + END;
		if (owns(player, perk))
			return GREEN + "[ Owned ]" + END;
		if (player.hasFreePerkUnlock)
			return GREEN + "[ Free Lv 5 ]" + END;
		int price = price(perk);
		return (player.perkPoints >= price ? WHITE : RED) + "[ " + price + " Pts ]" + END;
	}

	private static void sendDetail(Player player) {
		PlayerPerkInterface s = player.getPerkInterface();
		PacketSender ps = player.getPacketSender();
		Object selected = s.hubSelected;
		boolean loadoutView = selected instanceof Loadout;
		// the Set Overview replaces the whole screen body; hidden (and the panels shown) for anything else, Back included
		boolean setView = selected instanceof PerkSets;
		if (!setView)
			s.hubCombos = false;
		boolean comboView = setView && s.hubCombos;
		ps.setHidden(INTERFACE, SET_VIEW, !setView || comboView);
		ps.setHidden(INTERFACE, COMBO_VIEW, !comboView);
		for (int panel : PANELS)
			ps.setHidden(INTERFACE, panel, setView);
		showButton(player, BUTTON_C, null);
		showButton(player, BUTTON_WIDE, null);
		showButton(player, BUTTON_LEFT, null);
		showButton(player, BUTTON_RIGHT, null);
		ps.setHidden(INTERFACE, LOADOUT_ROW, !loadoutView);
		ps.setHidden(INTERFACE, DETAIL_ICON_FILL, loadoutView);
		ps.setHidden(INTERFACE, DETAIL_ICON_BORDER, loadoutView);
		ps.setHidden(INTERFACE, DETAIL_ICON, selected == null || loadoutView);
		if (selected == null) {
			ps.sendString(INTERFACE, DETAIL_NAME, "Select a perk");
			ps.sendString(INTERFACE, DETAIL_LEVEL, GREY + "Click any perk in the list to see its details." + END);
			ps.sendString(INTERFACE, DETAIL_NOW, GREY + "Earn Perk Points by levelling your Perk Tree: talk to the Perk Master for perk tasks." + END);
			ps.sendString(INTERFACE, DETAIL_NEXT, "");
			showButton(player, BUTTON_A, null);
			showButton(player, BUTTON_B, null);
			return;
		}
		if (selected instanceof Loadout loadout) {
			sendLoadoutDetail(player, loadout.index());
			return;
		}
		if (selected instanceof PerkSets set) {
			if (comboView)
				sendComboView(player, set);
			else
				sendSetView(player, set);
			ps.sendClientScript(set.scriptId, "I", INTERFACE << 16 | DETAIL_ICON);
			ps.sendString(INTERFACE, DETAIL_NAME, set.perkSet.getPerkSetName());
			int maxSlots = player.getPlayerPerkHandler().calculatePlayersTotalPerks(player);
			Perks[] best = bestCombination(player, set);
			int owned = ownedCount(player, best);
			ps.sendString(INTERFACE, DETAIL_LEVEL, player.activePerkSetsList.contains(set) ? GOLD + "Active" + END
				: maxSlots < 5 ? RED + "Needs 5 active slots (Perk Tree Lv 75)" + END
				: owned == 5 ? GREEN + "Ready: you own a full combination" + END
				: GREY + "Own and equip all 5 perks of one combination" + END);
			ps.sendString(INTERFACE, DETAIL_NOW, oneLine(set.perkSet.getPerkSetEffect()));
			ps.sendString(INTERFACE, DETAIL_NEXT, "Best combo (" + owned + "/5 owned): " + comboNames(best));
			showButton(player, BUTTON_A, owned == 5 ? GREEN + "Equip set" + END : GREY + "Equip set" + END);
			showButton(player, BUTTON_B, "Combos");
			return;
		}
		Perks perk = (Perks) selected;
		boolean owned = owns(player, perk);
		int level = owned ? level(player, perk) : 1;
		int max = maxLevel(perk);
		ps.sendClientScript(perk.scriptId, "I", INTERFACE << 16 | DETAIL_ICON);
		ps.sendString(INTERFACE, DETAIL_NAME, name(perk));
		ps.sendString(INTERFACE, DETAIL_LEVEL, owned
			? "Level " + WHITE + level + " / " + max + END + (isActive(player, perk) ? GOLD + "  ·  Equipped" + END : GREEN + "  ·  Owned" + END)
			: GREY + "Not owned  ·  " + END + (player.hasFreePerkUnlock ? GREEN + "Free level 5 unlock available" + END
				: "Cost " + WHITE + price(perk) + " Perk Points" + END));
		ps.sendString(INTERFACE, DETAIL_NOW, clip((owned ? "Now: " : "Lv 1: ") + oneLine(effectAt(perk, level)), 150));
		ps.sendString(INTERFACE, DETAIL_NEXT, owned && level < max ? GREY + clip("Lv " + (level + 1) + ": " + oneLine(effectAt(perk, level + 1)), 52) + END
			: owned ? GOLD + "Maximum level reached." + END : "");

		String confirm = s.hubConfirm;
		if (!owned) {
			String buy = player.hasFreePerkUnlock ? "Unlock (Free Lv 5)" : "Buy · " + price(perk) + " Pts";
			showButton(player, BUTTON_A, null);
			showButton(player, BUTTON_B, null);
			showButton(player, BUTTON_WIDE, "buy".equals(confirm) ? ORANGE + "Confirm: " + buy + "?" + END : buy);
			return;
		}
		showButton(player, BUTTON_A, null);
		showButton(player, BUTTON_B, null);
		showButton(player, BUTTON_LEFT, isActive(player, perk) ? "Unequip" : "Equip");
		showButton(player, BUTTON_RIGHT, level >= max ? GREY + "Max level" + END
			: "upgrade".equals(confirm) ? ORANGE + "Confirm: " + upgradeCost(player, perk) + " Pts?" + END
			: "Upgrade · " + upgradeCost(player, perk) + " Pts");
	}

	/** Loadout preview: the perks (with their saved levels) and the perk set they form, plus Load / Clear. */
	private static void sendLoadoutDetail(Player player, int index) {
		PacketSender ps = player.getPacketSender();
		java.util.Map<Integer, Integer> preset = preset(player, index);
		String[] names = {player.presetNameOne, player.presetNameTwo, player.presetNameThree, player.presetNameFour, player.presetNameFive};
		ps.sendString(INTERFACE, DETAIL_NAME, "Loadout " + (index + 1) + (preset.isEmpty() ? "" : ": " + names[index]));
		if (preset.isEmpty()) {
			ps.setHidden(INTERFACE, LOADOUT_ROW, true);
			ps.sendString(INTERFACE, DETAIL_LEVEL, GREY + "Empty slot." + END);
			ps.sendString(INTERFACE, DETAIL_NOW, GREY + "Equip the perks you want, then press Save here to store your"
				+ " active perks in loadout " + (index + 1) + "." + END);
			ps.sendString(INTERFACE, DETAIL_NEXT, "");
			showButton(player, BUTTON_A, null);
			showButton(player, BUTTON_B, null);
			showButton(player, BUTTON_C, GREEN + "Save here" + END);
			return;
		}
		// a row of mini perk slots: icon + "Lv n" badge per stored perk
		List<Perks> perks = new ArrayList<>();
		List<Integer> levels = new ArrayList<>();
		for (java.util.Map.Entry<Integer, Integer> entry : preset.entrySet()) {
			perks.add(Perks.VALUES[entry.getKey()]);
			levels.add(entry.getValue());
		}
		for (int k = 0; k < SLOTS; k++) {
			int mini = FIRST_MINI + k * MINI_STRIDE;
			boolean used = k < perks.size();
			ps.setHidden(INTERFACE, mini + MINI_ICON, !used);
			ps.sendString(INTERFACE, mini + MINI_LEVEL, used ? "Lv " + levels.get(k) : "");
			if (used)
				ps.sendClientScript(perks.get(k).scriptId, "I", INTERFACE << 16 | (mini + MINI_ICON));
		}
		String set = null;
		for (PerkSets perkSet : PerkSets.values())
			if (perkSet.hasAnyPerksetCombination(perks) != null)
				set = perkSet.perkSet.getPerkSetName();
		ps.sendString(INTERFACE, DETAIL_LEVEL, perks.size() + " perk" + (perks.size() == 1 ? "" : "s")
			+ (set == null ? GREY + "  ·  no perk set (see Sets)" + END : GOLD + "  ·  Perk set: " + set + END));
		ps.sendString(INTERFACE, DETAIL_NOW, "");
		ps.sendString(INTERFACE, DETAIL_NEXT, "");
		showButton(player, BUTTON_A, GREEN + "Load" + END);
		showButton(player, BUTTON_B, "Clear");
		showButton(player, BUTTON_C, "Save here");
	}

	private static java.util.Map<Integer, Integer> preset(Player player, int index) {
		return switch (index) {
			case 0 -> player.perkPresetOne;
			case 1 -> player.perkPresetTwo;
			case 2 -> player.perkPresetThree;
			case 3 -> player.perkPresetFour;
			default -> player.perkPresetFive;
		};
	}

	private static void showButton(Player player, int box, String label) {
		PacketSender ps = player.getPacketSender();
		ps.setHidden(INTERFACE, box, label == null);
		ps.setHidden(INTERFACE, box + 1, label == null);
		if (label != null)
			ps.sendString(INTERFACE, box + 1, label);
	}

	private static void outline(PacketSender ps, int first, boolean show) {
		ps.setHidden(INTERFACE, first, !show);
		ps.setHidden(INTERFACE, first + 1, !show);
	}

	/* ---------------- actions ---------------- */

	private static void select(Player player, int index, boolean badge) {
		PlayerPerkInterface s = player.getPerkInterface();
		if (s.hubRows == null || index >= s.hubRows.size())
			return;
		Object entry = s.hubRows.get(index);
		s.hubSelected = entry;
		s.hubConfirm = null;
		// clicking a price badge goes straight to the buy confirmation
		if (badge && entry instanceof Perks perk && !owns(player, perk))
			s.hubConfirm = "buy";
		refresh(player);
	}

	private static void button(Player player, boolean primary) {
		PlayerPerkInterface s = player.getPerkInterface();
		PlayerPerkHandler handler = player.getPlayerPerkHandler();
		Object selected = s.hubSelected;
		if (selected instanceof Loadout loadout) {
			int index = loadout.index();
			if (presetEmpty(player, index)) {
				// loadPreset clears the active perks before it checks the slot, so an empty slot would unequip everything
				player.sendMessage("You don't have a preset saved in this slot.");
			} else if (primary) {
				player.getPerkInterface().loadPreset(player, index);
				return; // loadPreset reopens the screen
			} else {
				player.getPerkInterface().removePreset(player, index);
			}
			refresh(player);
			return;
		}
		if (selected instanceof PerkSets set) {
			if (primary)
				equipSet(player, set);
			else
				showCombinations(player, set);
			refresh(player);
			return;
		}
		if (!(selected instanceof Perks perk))
			return;
		boolean owned = owns(player, perk);
		String confirm = s.hubConfirm;
		s.hubConfirm = null;
		if (primary && !owned) {
			if (!"buy".equals(confirm)) {
				s.hubConfirm = "buy";
			} else {
				handler.purchasePerk(player, perk, price(perk));
			}
		} else if (primary) {
			if (isActive(player, perk)) {
				int index = handler.getActivePerks(player).indexOf(perk);
				if (index >= 0)
					handler.removeFromActivePerks(player, index);
			} else {
				handler.addToActivePerks(player, perk);
			}
		} else if (owned) {
			if (level(player, perk) >= maxLevel(perk)) {
				player.sendMessage("You can't upgrade this perk any further.");
			} else if (!"upgrade".equals(confirm)) {
				s.hubConfirm = "upgrade";
			} else {
				handler.upgradePerk(player, perk, upgradeCost(player, perk));
			}
		}
		refresh(player);
	}

	/** The Set Overview page: requirement status, the effect per level, the best combination's 5 perks, actions. */
	private static void sendSetView(Player player, PerkSets set) {
		PacketSender ps = player.getPacketSender();
		int maxSlots = player.getPlayerPerkHandler().calculatePlayersTotalPerks(player);
		Perks[] best = bestCombination(player, set);
		int owned = ownedCount(player, best);
		boolean active = player.activePerkSetsList.contains(set);
		ps.sendString(INTERFACE, SET_TITLE, "Perk Set: " + set.perkSet.getPerkSetName());
		ps.sendString(INTERFACE, SET_BACK + 1, "<lt> Back");
		ps.sendString(INTERFACE, SET_STATUS, active ? GREEN + "[ Set Active ]" + END
			: maxSlots < 5 ? RED + "Requires 5 active slots (Perk Tree Lv 75)" + END
			: owned == 5 ? GREEN + "Ready: you own a full combination" + END
			: GREY + "Own all 5 perks of one combination to use it" + END);
		ps.sendString(INTERFACE, SET_EFFECT, setEffect(set));
		ps.sendString(INTERFACE, SET_REQUIRED, "Required perks" + GREY + "  (best combination, " + owned + "/5 owned)" + END);
		for (int k = 0; k < SLOTS; k++) {
			int slot = FIRST_SET_PERK + k * SET_PERK_STRIDE;
			Perks perk = k < best.length ? best[k] : null;
			ps.setHidden(INTERFACE, slot + SET_PERK_ICON, perk == null);
			if (perk == null) {
				ps.sendString(INTERFACE, slot + SET_PERK_NAME, "");
				ps.sendString(INTERFACE, slot + SET_PERK_STATE, "");
				continue;
			}
			String perkName = name(perk);
			ps.sendString(INTERFACE, slot + SET_PERK_NAME, perkName.length() > 16 ? perkName.substring(0, 15) + ".." : perkName);
			ps.sendClientScript(perk.scriptId, "I", INTERFACE << 16 | (slot + SET_PERK_ICON));
			ps.sendString(INTERFACE, slot + SET_PERK_STATE, isActive(player, perk) ? GOLD + "[Active]" + END
				: owns(player, perk) ? GREEN + "[Owned]" + END : RED + "[Missing]" + END);
		}
		boolean canEquip = owned == 5 && maxSlots >= 5;
		ps.sendString(INTERFACE, SET_EQUIP + 1, canEquip ? GREEN + "Equip Full Set" + END : GREY + "Equip Full Set" + END);
		ps.sendString(INTERFACE, SET_COMBOS + 1, "View All Combos");
	}

	/** The Combos Browser: one card per combination with its 5 perks (owned level / missing) and Equip This Combo. */
	private static void sendComboView(Player player, PerkSets set) {
		PacketSender ps = player.getPacketSender();
		boolean slotsOk = player.getPlayerPerkHandler().calculatePlayersTotalPerks(player) >= 5;
		ps.sendString(INTERFACE, COMBO_TITLE, "Combinations: " + set.perkSet.getPerkSetName());
		ps.sendString(INTERFACE, COMBO_BACK + 1, "<lt> Back to Set");
		ps.sendString(INTERFACE, COMBO_SUB, "Own and equip all 5 perks of any one combination to activate the set."
			+ (slotsOk ? "" : RED + "  Needs 5 active slots (Lv 75)." + END));
		Perks[][] combos = set.perkCombinations;
		for (int c = 0; c < COMBO_CARDS; c++) {
			int card = FIRST_COMBO + c * COMBO_STRIDE;
			boolean used = c < combos.length;
			ps.setHidden(INTERFACE, card, !used);
			if (!used)
				continue;
			Perks[] combination = combos[c];
			int owned = ownedCount(player, combination);
			ps.sendString(INTERFACE, card + COMBO_CARD_TITLE, "Combo #" + (c + 1) + "  " + (owned == 5 ? GREEN : GREY)
				+ owned + "/5 owned" + END);
			ps.setHidden(INTERFACE, card + COMBO_CARD_EQUIP, owned < 5);
			ps.setHidden(INTERFACE, card + COMBO_CARD_EQUIP + 1, owned < 5);
			if (owned == 5)
				ps.sendString(INTERFACE, card + COMBO_CARD_EQUIP + 1, slotsOk ? GREEN + "Equip This Combo" + END : GREY + "Equip This Combo" + END);
			for (int k = 0; k < SLOTS; k++) {
				int slot = card + COMBO_FIRST_PERK + k * COMBO_PERK_STRIDE;
				Perks perk = combination[k];
				ps.sendClientScript(perk.scriptId, "I", INTERFACE << 16 | (slot + 2));
				String perkName = name(perk);
				ps.sendString(INTERFACE, slot + 3, perkName.length() > 16 ? perkName.substring(0, 15) + ".." : perkName);
				ps.sendString(INTERFACE, slot + 4, isActive(player, perk) ? GOLD + "Active (Lv " + level(player, perk) + ")" + END
					: owns(player, perk) ? GREEN + "Owned (Lv " + level(player, perk) + ")" + END : RED + "Missing" + END);
			}
		}
		int contentHeight = combos.length * COMBO_PITCH - 4;
		int count = contentHeight <= COMBO_SCROLL_HEIGHT ? 0 : (int) Math.ceil((contentHeight - COMBO_SCROLL_HEIGHT / 3D) / 15D);
		ScrollbarClientScript.create().interfaceId(INTERFACE).containerId(COMBO_SCROLL).scrollbarChildId(COMBO_SCROLLBAR)
			.childrenCount(count).withDarkGraphics().build().send(player);
	}

	/** The set's effect as an intro line and one bullet per level ("Lv 3: drop rates boosted by 15.25%."). */
	private static String setEffect(PerkSets set) {
		String effect = set.perkSet.getPerkSetEffect();
		if (effect == null)
			return "";
		StringBuilder sb = new StringBuilder();
		for (String part : effect.split("<br>")) {
			String line = oneLine(part);
			if (line.isEmpty())
				continue;
			java.util.regex.Matcher level = java.util.regex.Pattern.compile("^At level (\\d)\\s*,?\\s*(.*)$").matcher(line);
			if (level.matches()) {
				line = GOLD + "Lv " + level.group(1) + ":" + END + " " + capitalise(stripLead(level.group(2)));
			} else {
				line = capitalise(stripLead(line.replaceFirst("^When this perk set is active\s*", "")));
			}
			sb.append(sb.length() == 0 ? "" : "<br>").append(line);
		}
		return sb.toString();
	}

	/** "you will have a 4% chance..." -> "4% chance...", "your drop rates will be boosted by" -> "drop rates boosted by". */
	private static String stripLead(String text) {
		return text.replaceFirst("^(you will have an? |you will |your )", "").replace(" will be ", " ");
	}

	private static String capitalise(String text) {
		return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
	}

	/** Button C: "Save here" in the loadout view (stores the active perks in that slot, asking for a name). */
	private static void buttonC(Player player) {
		if (!(player.getPerkInterface().hubSelected instanceof Loadout loadout))
			return;
		if (player.activePerks.isEmpty()) {
			player.sendMessage("You must have at least one perk equipped to save a loadout.");
			return;
		}
		player.getPerkInterface().savePreset(player, loadout.index()); // asks for a name, then reopens this screen
	}

	/** The combination of this set the player owns the most perks of. */
	private static Perks[] bestCombination(Player player, PerkSets set) {
		Perks[] best = set.perkCombinations[0];
		for (Perks[] combination : set.perkCombinations)
			if (ownedCount(player, combination) > ownedCount(player, best))
				best = combination;
		return best;
	}

	private static int ownedCount(Player player, Perks[] combination) {
		int owned = 0;
		for (Perks perk : combination)
			if (owns(player, perk))
				owned++;
		return owned;
	}

	private static String comboNames(Perks[] combination) {
		StringBuilder sb = new StringBuilder();
		for (Perks perk : combination)
			sb.append(sb.length() == 0 ? "" : ", ").append(name(perk));
		return sb.toString();
	}

	/**
	 * Equips a full combination of the set: unequips the current perks and slots the 5 in through the normal
	 * add/remove logic (slot limits and set detection included). Needs a fully owned combination and 5 slots.
	 */
	private static void equipSet(Player player, PerkSets set) {
		Perks[] combination = null;
		for (Perks[] c : set.perkCombinations)
			if (ownedCount(player, c) == 5)
				combination = c;
		if (combination == null) {
			player.sendMessage("You don't own all 5 perks of any " + set.perkSet.getPerkSetName() + " combination yet.");
			return;
		}
		equipCombination(player, set, combination);
	}

	/** Replaces the active perks with this combination's 5 (all must be owned, 5 slots needed). */
	private static void equipCombination(Player player, PerkSets set, Perks[] combination) {
		PlayerPerkHandler handler = player.getPlayerPerkHandler();
		if (handler.calculatePlayersTotalPerks(player) < 5) {
			player.sendMessage("A perk set needs 5 active perks: you unlock the 5th slot at Perk Tree level 75.");
			return;
		}
		if (ownedCount(player, combination) < 5) {
			player.sendMessage("You don't own all 5 perks of this combination yet.");
			return;
		}
		while (!handler.getActivePerks(player).isEmpty())
			if (!handler.removeFromActivePerks(player, 0))
				break;
		for (Perks perk : combination)
			handler.addToActivePerks(player, perk);
		player.sendMessage(ORANGE + "[Perks]" + END + " Equipped the " + set.perkSet.getPerkSetName() + " set: " + comboNames(combination) + ".");
	}

	/** Opens the in-window Combos Browser (no chat lines, no parchment scroll). */
	private static void showCombinations(Player player, PerkSets set) {
		player.getPerkInterface().hubCombos = true;
		refresh(player);
	}

	/* ---------------- helpers ---------------- */

	private static boolean presetEmpty(Player player, int preset) {
		return preset(player, preset).isEmpty();
	}

	private static boolean owns(Player player, Perks perk) {
		return player.ownedPerks.containsKey(perk.ordinal());
	}

	private static boolean isActive(Player player, Perks perk) {
		return player.activePerks.containsKey(perk.ordinal());
	}

	private static int level(Player player, Perks perk) {
		Integer level = player.ownedPerks.get(perk.ordinal());
		return level == null ? 1 : level;
	}

	private static int maxLevel(Perks perk) {
		return Objects.requireNonNull(perk.getInterfacePerk()).getPerkMaxLevel();
	}

	/** Same cost as the old owned-perks screen: the perk's upgrade price times its current level. */
	private static int upgradeCost(Player player, Perks perk) {
		return perk.getUpgradePrice() * level(player, perk);
	}

	private static int price(Perks perk) {
		for (PerkShop shop : PerkShop.values())
			if (shop.getPerk() == perk)
				return shop.getPrice();
		return 0;
	}

	private static String name(Perks perk) {
		return Objects.requireNonNull(perk.getInterfacePerk()).getPerkName();
	}

	/** The perk's effect text at a given level (the shared display instance is put back to level 1 afterwards). */
	private static String effectAt(Perks perk, int level) {
		PlayerPerk display = Objects.requireNonNull(perk.getInterfacePerk());
		display.setPerkLevel(level);
		String effect = display.getPerkEffect();
		display.setPerkLevel(1);
		return effect == null ? "" : effect;
	}

	private static final java.util.regex.Pattern DECIMAL = java.util.regex.Pattern.compile("\\d+\\.\\d+");

	/**
	 * One line of effect text with clean numbers: the perk classes build their text from raw doubles, so a value like
	 * 13.750000000000002 is rounded to 2 decimals (trailing zeros dropped: 13.75, 25.0 -> 25) and "13.75 %" -> "13.75%".
	 */
	/** At most max characters (3 lines of the detail box), cut at a word with "..". */
	private static String clip(String text, int max) {
		if (text == null || text.length() <= max)
			return text == null ? "" : text;
		int cut = text.lastIndexOf(' ', max - 2);
		return text.substring(0, cut > 0 ? cut : max - 2) + "..";
	}

	private static String oneLine(String text) {
		if (text == null)
			return "";
		String line = text.replace("<br>", " ").replaceAll(" {2,}", " ").trim();
		java.util.regex.Matcher m = DECIMAL.matcher(line);
		StringBuilder sb = new StringBuilder();
		while (m.find()) {
			String rounded = new java.math.BigDecimal(m.group()).setScale(2, java.math.RoundingMode.HALF_UP)
				.stripTrailingZeros().toPlainString();
			m.appendReplacement(sb, rounded);
		}
		m.appendTail(sb);
		return sb.toString().replaceAll("(\\d) %", "$1%");
	}
}
