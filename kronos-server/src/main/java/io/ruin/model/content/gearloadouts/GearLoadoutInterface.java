package io.ruin.model.content.gearloadouts;

import io.ruin.model.content.equipmentpresets.GearPresetHandler;
import io.ruin.model.entity.player.Player;
import io.ruin.model.entity.player.SecondaryGroup;
import io.ruin.model.inter.InterfaceHandler;
import io.ruin.model.inter.ToplevelComponent;
import io.ruin.model.inter.actions.SimpleAction;
import io.ruin.model.inter.dialogue.OptionsDialogue;
import io.ruin.model.inter.handlers.EquipmentStats;
import io.ruin.model.inter.questtab.dashboard.QuestDashboards;
import io.ruin.model.inter.utils.Option;
import io.ruin.model.item.Item;
import io.ruin.model.skills.magic.SpellBook;
import io.ruin.model.stat.StatType;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PvP Loadouts: the PvP Mode version of the regular Equipment Presets screen. Interface 1156 (and 1157 for resizable,
 * opened in the world-map slot like 1142) is a copy of 5900 with the same component ids, built by
 * .dev/cache-restore-tool/src/BuildPvpLoadoutsInterface.java. Same layout and flow as the regular presets (gear preview
 * with an inventory toggle, 6-row list with scrolling, Save / Update / Delete / Load), with the PvP features:
 * <ul>
 *     <li>each loadout's own combat levels (click a level to edit it) and combat level, plus its gear bonuses;</li>
 *     <li>My loadouts / Free kits list switch; the loadout's spellbook (click to cycle);</li>
 *     <li>Spawn Supplies (::spawn) and a Battle Presets switch.</li>
 * </ul>
 * Loadouts live in GearLoadoutStore, free kits in FreeGearLoadoutKits; all loading/saving goes through
 * GearLoadoutHandler as before (it still calls {@link #selectPreset}, {@link #clearSelectionIf} and {@link #open}).
 */
public class GearLoadoutInterface {

	public static final int INTERFACE_ID = 1156, LARGE_INTERFACE_ID = 1157;
	private static final Map<String, GearLoadoutInterface> INTERFACES = new ConcurrentHashMap<>();

	private static final int CLOSE_BUTTON = 7, TITLE = 5, STATS_HEADER = 8, PREVIEW_HEADER = 9, PANEL_HEADER = 10;
	/** 5900's 16 stat lines: 20-26 the loadout's combat levels, 27 its combat level, 28-35 its gear bonuses. */
	private static final int FIRST_LEVEL_LINE = 20, COMBAT_LINE = 27, FIRST_BONUS_LINE = 28;
	/** Equipment bonus indexes shown on lines 28-35: stab/slash/crush/magic/range attack, melee/ranged str, magic dmg. */
	private static final int[] SHOWN_BONUSES = {0, 1, 2, 3, 4, 10, 11, 12};
	private static final int SPELLBOOK_BUTTON = 36, SPELLBOOK_TEXT = 38, LIST_BUTTON = 39, LIST_TEXT = 41,
		SPAWN_BUTTON = 42, SPAWN_TEXT = 44, SELECTED_SUMMARY = 46, LOAD_BUTTON = 47, LOAD_TEXT = 49;
	private static final int[] EQUIPMENT_COMPONENTS = {70, 71, 72, 73, 74, 75, 76, 77, 78, 79, 80};
	private static final int[] EQUIPMENT_PLACEHOLDERS = {63, 64, 65, 66, 67, 68, 69, 82, 83, 84, 85};
	private static final int[] EQUIPMENT_BACKGROUNDS = {51, 52, 53, 54, 55, 56, 57, 58, 59, 60, 61};
	private static final int EQUIPMENT_CONTAINER_START = 9320, INVENTORY_COMPONENT = 81, INVENTORY_CONTAINER = 9332,
		INVENTORY_OUTLINE = 87, VIEW_TOGGLE_BACKGROUND = 62, VIEW_TOGGLE = 86, INVENTORY_SPRITE = 900, EQUIPMENT_SPRITE = 901;
	private static final int[] ROW_BUTTONS = {90, 91, 92, 93, 94, 95};
	private static final int[] ROW_TEXT = {102, 103, 104, 105, 106, 107};
	private static final int[] ROW_SELECTION = {108, 109, 110, 111, 112, 113};
	private static final int UPDATE_BUTTON = 114, UPDATE_TEXT = 116, DELETE_BUTTON = 117, DELETE_TEXT = 119,
		SCROLL_TRACK = 120, SCROLL_THUMB = 121, SCROLL_UP = 122, SCROLL_UP_BACKGROUND = 123, SCROLL_UP_TEXT = 124,
		SCROLL_DOWN = 125, SCROLL_DOWN_BACKGROUND = 126, SCROLL_DOWN_TEXT = 127, CREATE_BUTTON = 128, CREATE_TEXT = 130,
		PAGE_TEXT = 131, PRESET_PANEL_FIRST = 90, PRESET_PANEL_LAST = 131, BATTLE_PRESETS = 132, BATTLE_PRESETS_TEXT = 133;
	private static final int VISIBLE_ROWS = 6;

	private static final String GOLD = "<col=ffd35a>", GREY = "<col=777777>", WHITE = "<col=ffffff>", RED = "<col=ff6a4a>",
		GREEN = "<col=55ff55>", END = "</col>";

	public GearLoadoutPreset currentPreset;
	boolean currentPresetIsFreeKit;
	PresetListMode listMode = PresetListMode.PRESETS;
	private int scrollOffset;
	private boolean showingInventory;
	/** The group actually open: 1156, or 1157 on resizable clients. */
	private int ifId = INTERFACE_ID;

	public static GearLoadoutInterface get(Player player) {
		return INTERFACES.computeIfAbsent(interfaceKey(player), key -> new GearLoadoutInterface());
	}

	/** The window shrank below the large screen's size: reopen on the normal-size screen. */
	public static void shrinkIfOpen(Player player) {
		if (player.isVisibleInterface(LARGE_INTERFACE_ID)) {
			player.closeInterface(ToplevelComponent.WORLD_MAP);
			open(player);
		}
	}

	public static void open(Player player) {
		register(); // the handlers are registered on first use (nothing registers them at startup)
		get(player).openInterface(player);
	}

	private static String interfaceKey(Player player) {
		return player.getName() == null ? "unknown" : player.getName().toLowerCase();
	}

	private void openInterface(Player player) {
		ensureSelection(player);
		boolean large = player.fitsLargeInterface();
		ifId = large ? LARGE_INTERFACE_ID : INTERFACE_ID;
		if (large) {
			player.closeInterface(ToplevelComponent.MAINMODAL);
			player.openInterface(ToplevelComponent.WORLD_MAP, ifId);
		} else {
			player.openInterface(ToplevelComponent.MAINMODAL, ifId);
		}
		var ps = player.getPacketSender();
		ps.sendString(ifId, TITLE, "PvP Loadouts");
		ps.sendString(ifId, STATS_HEADER, "Loadout Stats");
		ps.sendString(ifId, PREVIEW_HEADER, "Player Preview");
		ps.sendString(ifId, SPAWN_TEXT, "Spawn Supplies");
		ps.sendString(ifId, CREATE_TEXT, "+ Save Current Gear");
		update(player);
	}

	/* ---------------- selection (kept API: GearLoadoutHandler calls these) ---------------- */

	public void selectPreset(Player player, GearLoadoutPreset preset) {
		selectPreset(player, preset, false);
	}

	public void selectFreeKit(Player player, GearLoadoutPreset preset) {
		selectPreset(player, preset, true);
	}

	public boolean isViewingFreeKit() {
		return currentPresetIsFreeKit;
	}

	private void selectPreset(Player player, GearLoadoutPreset preset, boolean freeKit) {
		currentPreset = preset;
		currentPresetIsFreeKit = freeKit;
		listMode = freeKit ? PresetListMode.FREE_KITS : PresetListMode.PRESETS;
		update(player);
	}

	public void clearSelectionIf(GearLoadoutPreset preset) {
		if (currentPreset == preset) {
			currentPreset = null;
			currentPresetIsFreeKit = false;
		}
	}

	public void cycleSpellBook(Player player) {
		if (currentPreset == null) {
			player.sendMessage("You don't have a preset selected to update.");
			return;
		}
		if (currentPresetIsFreeKit) {
			player.sendMessage("Free kit spellbooks can't be changed.");
			return;
		}
		currentPreset.normalize();
		SpellBook next = SpellBook.VALUES[(currentPreset.getSpellBook().ordinal() + 1) % SpellBook.VALUES.length];
		currentPreset.setSpellBook(next);
		GearLoadoutStore.save(player);
		update(player);
		player.sendMessage("Preset spellbook changed to " + formatSpellBookName(next) + ".");
	}

	public void setPresetStat(Player player, StatType statType) {
		if (currentPreset == null) {
			player.sendMessage("You don't have a preset selected to update.");
			return;
		}
		if (currentPresetIsFreeKit) {
			player.sendMessage("Free kit stats can't be changed.");
			return;
		}
		int min = statType == StatType.Hitpoints ? 10 : 1;
		int max = 99;
		player.integerInput("Enter " + statType.name() + " level (" + min + "-" + max + "):", level -> {
			if (currentPreset == null) {
				player.sendMessage("You don't have a preset selected to update.");
				return;
			}
			if (level < min || level > max) {
				player.sendMessage("Invalid level. Please enter a level between " + min + " and " + max + ".");
				return;
			}
			currentPreset.setCombatLevel(statType, level, level);
			GearLoadoutStore.save(player);
			update(player);
			player.sendMessage("Preset " + statType.name() + " level set to " + level + ".");
		});
	}

	// Regular players get 3 slots; Donator (the base rank) gets 5, and each donator rank above
	// that adds one more (Super=6, Elite=7, Noble=8, Gold=9, Platinum=10, Legendary=11, Supreme=12).
	public int getUnlockedPresets(Player player) {
		SecondaryGroup group = player.getSecondaryGroup();
		if (group.id < SecondaryGroup.DONATOR.id)
			return 3;
		return 5 + (group.id - SecondaryGroup.DONATOR.id);
	}

	void update(Player player) {
		ensureSelection(player);
		if (!player.isVisibleInterface(ifId))
			return;
		sendStats(player);
		sendOptions(player);
		sendLoadoutView(player);
		sendRows(player);
	}

	private void ensureSelection(Player player) {
		if (currentPresetIsFreeKit) {
			GearLoadoutPreset freeKit = matchingFreeKit(currentPreset);
			if (freeKit != null) {
				currentPreset = freeKit;
				return;
			}
		}
		List<GearLoadoutPreset> playerPresets = GearLoadoutStore.getPresets(player);
		if (!currentPresetIsFreeKit && currentPreset != null && playerPresets.contains(currentPreset))
			return;
		if (listMode == PresetListMode.PRESETS && !playerPresets.isEmpty()) {
			currentPreset = playerPresets.get(0);
			currentPresetIsFreeKit = false;
			return;
		}
		if (listMode == PresetListMode.FREE_KITS) {
			List<GearLoadoutPreset> freeKits = FreeGearLoadoutKits.getKits();
			currentPreset = freeKits.isEmpty() ? null : freeKits.get(0);
			currentPresetIsFreeKit = currentPreset != null;
			return;
		}
		currentPreset = null;
		currentPresetIsFreeKit = false;
	}

	private GearLoadoutPreset matchingFreeKit(GearLoadoutPreset preset) {
		if (preset == null)
			return null;
		for (GearLoadoutPreset freeKit : FreeGearLoadoutKits.getKits()) {
			if (freeKit == preset || (freeKit.presetName != null && freeKit.presetName.equals(preset.presetName)))
				return freeKit;
		}
		return null;
	}

	private List<GearLoadoutPreset> list(Player player) {
		return listMode == PresetListMode.FREE_KITS ? FreeGearLoadoutKits.getKits() : GearLoadoutStore.getPresets(player);
	}

	/* ---------------- rendering ---------------- */

	/** Left column: the loadout's combat levels (editable on own loadouts), combat level and gear bonuses. */
	private void sendStats(Player player) {
		var ps = player.getPacketSender();
		GearLoadoutPreset preset = currentPreset;
		for (int i = 0; i < GearLoadoutPreset.COMBAT_STATS.length; i++) {
			StatType type = GearLoadoutPreset.COMBAT_STATS[i];
			String value = preset == null || preset.getCombatLevel(type) <= 0 ? "--" : String.valueOf(preset.getCombatLevel(type));
			ps.sendString(ifId, FIRST_LEVEL_LINE + i, WHITE + type.name() + ": " + END
				+ (preset != null && !currentPresetIsFreeKit ? RED : GREY) + value + END);
		}
		ps.sendString(ifId, COMBAT_LINE, GOLD + "Combat level: " + (preset == null ? "--" : BattlePresets.combatLevel(preset)) + END);
		int[] bonuses = new int[EquipmentStats.Stat.VALUES.length];
		if (preset != null) {
			for (Item item : preset.getEquipmentItems()) {
				if (item == null || item.getDef() == null || item.getDef().equipBonuses == null)
					continue;
				int[] itemBonuses = item.getDef().equipBonuses;
				for (int b = 0; b < bonuses.length && b < itemBonuses.length; b++)
					bonuses[b] += itemBonuses[b];
			}
		}
		for (int line = 0; line < SHOWN_BONUSES.length; line++)
			EquipmentStats.Stat.VALUES[SHOWN_BONUSES[line]].sendString(player, bonuses[SHOWN_BONUSES[line]], ifId, FIRST_BONUS_LINE + line);
	}

	/** Bottom-left buttons, the selected summary, Load, and the list action texts. */
	private void sendOptions(Player player) {
		var ps = player.getPacketSender();
		boolean freeKits = listMode == PresetListMode.FREE_KITS;
		ps.sendString(ifId, SPELLBOOK_TEXT, "Spellbook: " + GOLD + (currentPreset == null ? "-" : formatSpellBookName(currentPreset.getSpellBook())) + END);
		ps.sendString(ifId, LIST_TEXT, "Showing: " + GOLD + (freeKits ? "Free kits" : "My loadouts") + END);
		if (currentPreset == null) {
			ps.sendString(ifId, SELECTED_SUMMARY, freeKits ? "No free kits configured." : "Save your current gear as a loadout.");
			ps.setHidden(ifId, LOAD_BUTTON, true);
		} else {
			ps.sendString(ifId, SELECTED_SUMMARY, GOLD + currentPreset.presetName + END + "<br>"
				+ formatSpellBookName(currentPreset.getSpellBook()) + " · CB " + BattlePresets.combatLevel(currentPreset)
				+ (currentPresetIsFreeKit ? " · Free kit" : ""));
			ps.setHidden(ifId, LOAD_BUTTON, false);
			ps.sendString(ifId, LOAD_TEXT, currentPresetIsFreeKit ? "Spawn Kit" : "Load Loadout");
		}
		boolean own = currentPreset != null && !currentPresetIsFreeKit;
		ps.sendString(ifId, UPDATE_TEXT, own ? "Update" : GREY + "Update" + END);
		ps.sendString(ifId, DELETE_TEXT, own ? RED + "Delete" + END : GREY + "Delete" + END);
	}

	/** Right column: the selected loadout's gear (or inventory), like the regular presets' preview. */
	private void sendLoadoutView(Player player) {
		var ps = player.getPacketSender();
		ps.sendString(ifId, PANEL_HEADER, showingInventory ? "Inventory / Options" : "Equipment / Options");
		ps.setGraphic(ifId, VIEW_TOGGLE, showingInventory ? EQUIPMENT_SPRITE : INVENTORY_SPRITE);
		ps.setHidden(ifId, VIEW_TOGGLE_BACKGROUND, false);
		ps.setHidden(ifId, VIEW_TOGGLE, false);
		for (int component : EQUIPMENT_BACKGROUNDS)
			ps.setHidden(ifId, component, showingInventory);
		for (int component : EQUIPMENT_COMPONENTS)
			ps.setHidden(ifId, component, showingInventory);
		for (int component : EQUIPMENT_PLACEHOLDERS)
			ps.setHidden(ifId, component, showingInventory);
		ps.setHidden(ifId, INVENTORY_COMPONENT, !showingInventory);
		ps.setHidden(ifId, INVENTORY_OUTLINE, !showingInventory);
		for (int component = PRESET_PANEL_FIRST; component <= PRESET_PANEL_LAST; component++)
			ps.setHidden(ifId, component, showingInventory);

		if (showingInventory) {
			ps.sendClientScript(149, "IviiiIsssss", ifId << 16 | INVENTORY_COMPONENT, INVENTORY_CONTAINER, 4, 7, 0, -1, "", "", "", "", "");
			Item[] items = currentPreset == null ? new Item[GearLoadoutPreset.INVENTORY_SIZE] : currentPreset.getInventoryItems();
			ps.sendItems(INVENTORY_CONTAINER, copy(items));
			return;
		}
		Item[] equipment = currentPreset == null ? new Item[GearLoadoutPreset.EQUIPMENT_SIZE] : currentPreset.getEquipmentItems();
		for (int index = 0; index < EQUIPMENT_COMPONENTS.length; index++) {
			int slot = GearPresetHandler.EQUIPMENT_SLOTS[index];
			Item item = slot < equipment.length ? equipment[slot] : null;
			int container = EQUIPMENT_CONTAINER_START + index;
			ps.sendClientScript(149, "IviiiIsssss", ifId << 16 | EQUIPMENT_COMPONENTS[index], container, 4, 7, 1, -1, "", "", "", "", "");
			ps.sendItems(container, new Item[]{item == null ? null : item.copy()});
			ps.setHidden(ifId, EQUIPMENT_PLACEHOLDERS[index], item != null);
		}
	}

	private static Item[] copy(Item[] items) {
		Item[] out = new Item[items.length];
		for (int i = 0; i < items.length; i++)
			out[i] = items[i] == null ? null : items[i].copy();
		return out;
	}

	/** The 6-row list (own loadouts up to the unlocked count, or the free kits), scroll arrows and page text. */
	private void sendRows(Player player) {
		if (showingInventory)
			return;
		var ps = player.getPacketSender();
		List<GearLoadoutPreset> list = list(player);
		boolean freeKits = listMode == PresetListMode.FREE_KITS;
		int unlocked = freeKits ? list.size() : getUnlockedPresets(player);
		int max = maxScroll(list.size());
		scrollOffset = Math.max(0, Math.min(scrollOffset, max));
		for (int row = 0; row < VISIBLE_ROWS; row++) {
			int index = scrollOffset + row;
			GearLoadoutPreset preset = index < list.size() ? list.get(index) : null;
			String text = preset == null ? GREY + (freeKits ? "-" : index < unlocked ? "Empty" : "Locked") + END
				: index >= unlocked ? GREY + truncate(preset.presetName, 9) + END : truncate(preset.presetName, 9);
			ps.sendString(ifId, ROW_TEXT[row], text);
			ps.setHidden(ifId, ROW_SELECTION[row], preset == null || preset != currentPreset);
		}
		boolean scrollable = max > 0;
		for (int component : new int[]{SCROLL_TRACK, SCROLL_THUMB, SCROLL_UP, SCROLL_UP_BACKGROUND, SCROLL_UP_TEXT,
			SCROLL_DOWN, SCROLL_DOWN_BACKGROUND, SCROLL_DOWN_TEXT})
			ps.setHidden(ifId, component, !scrollable);
		if (scrollable)
			ps.setAlignment(ifId, SCROLL_THUMB, 492, 246 + (12 * scrollOffset / max));
		ps.sendString(ifId, PAGE_TEXT, list.isEmpty() ? (freeKits ? "No free kits" : "No saved loadouts")
			: (scrollOffset + 1) + "-" + Math.min(scrollOffset + VISIBLE_ROWS, list.size()) + " of " + list.size()
			+ (freeKits ? " kits" : " (" + unlocked + " slots)"));
	}

	private static int maxScroll(int size) {
		return Math.max(0, size - VISIBLE_ROWS);
	}

	/* ---------------- actions ---------------- */

	private void selectRow(Player player, int row) {
		List<GearLoadoutPreset> list = list(player);
		int index = scrollOffset + row;
		if (index >= list.size()) {
			player.sendMessage(listMode == PresetListMode.FREE_KITS ? "There isn't a free kit in this slot."
				: "This slot is empty: press Save Current Gear to save a loadout.");
			return;
		}
		if (listMode == PresetListMode.PRESETS && index >= getUnlockedPresets(player)) {
			player.sendMessage("Preset locked, you'll get access to more preset slots depending on your donator rank.");
			return;
		}
		selectPreset(player, list.get(index), listMode == PresetListMode.FREE_KITS);
	}

	private void toggleList(Player player) {
		listMode = listMode == PresetListMode.FREE_KITS ? PresetListMode.PRESETS : PresetListMode.FREE_KITS;
		scrollOffset = 0;
		currentPreset = null;
		currentPresetIsFreeKit = listMode == PresetListMode.FREE_KITS;
		update(player);
	}

	private void load(Player player) {
		if (currentPreset == null) {
			player.sendMessage("You don't have a preset selected to load.");
			return;
		}
		if (currentPresetIsFreeKit)
			GearLoadoutHandler.spawnFreeKit(player, currentPreset);
		else
			GearLoadoutHandler.equipPreset(player, currentPreset);
	}

	private void create(Player player) {
		if (GearLoadoutStore.getPresets(player).size() >= getUnlockedPresets(player)) {
			player.sendMessage("You don't have anymore preset slots to save this to.");
			return;
		}
		listMode = PresetListMode.PRESETS;
		GearLoadoutHandler.saveGearPreset(player); // asks for a name, saves the current gear/stats/spellbook
	}

	private void updateSelected(Player player) {
		if (currentPreset == null || currentPresetIsFreeKit) {
			player.sendMessage(currentPreset == null ? "Select one of your loadouts first." : "Free kits can't be overwritten.");
			return;
		}
		GearLoadoutPreset preset = currentPreset;
		player.dialogue(new OptionsDialogue("Overwrite \"" + preset.presetName + "\" with your current gear, stats and spellbook?",
			new Option("Yes, overwrite it.", () -> {
				GearLoadoutHandler.cloneCurrentLoadout(player, preset);
				open(player); // the dialogue closed the screen
			}),
			new Option("Nevermind.")));
	}

	private void deleteSelected(Player player) {
		if (currentPreset == null || currentPresetIsFreeKit) {
			player.sendMessage(currentPreset == null ? "Select one of your loadouts first." : "Free kits can't be deleted.");
			return;
		}
		GearLoadoutPreset preset = currentPreset;
		player.dialogue(new OptionsDialogue("Delete \"" + preset.presetName + "\"?",
			new Option("Yes, delete it.", () -> GearLoadoutHandler.deleteGearPreset(player, preset)),
			new Option("Nevermind.")));
	}

	private void scroll(Player player, int direction) {
		int max = maxScroll(list(player).size());
		if (max == 0)
			return;
		scrollOffset = Math.max(0, Math.min(max, scrollOffset + direction * 3));
		update(player);
	}

	/* ---------------- helpers ---------------- */

	private static String formatSpellBookName(SpellBook spellBook) {
		String name = spellBook.name().toLowerCase();
		return name.substring(0, 1).toUpperCase() + name.substring(1);
	}

	private static String truncate(String text, int maxLength) {
		if (text == null)
			return "";
		return text.length() <= maxLength ? text : text.substring(0, Math.max(0, maxLength - 2)) + "..";
	}

	private static boolean registered;

	public static synchronized void register() {
		if (registered)
			return;
		registered = true;
		for (int id : new int[]{INTERFACE_ID, LARGE_INTERFACE_ID})
			register(id);
	}

	private static void register(int interfaceId) {
		InterfaceHandler.register(interfaceId, h -> {
			h.actions[CLOSE_BUTTON] = (SimpleAction) p -> p.closeInterface(
				interfaceId == LARGE_INTERFACE_ID ? ToplevelComponent.WORLD_MAP : ToplevelComponent.MAINMODAL);
			h.actions[VIEW_TOGGLE] = (SimpleAction) p -> {
				GearLoadoutInterface ui = get(p);
				ui.showingInventory = !ui.showingInventory;
				ui.update(p);
			};
			h.actions[SPELLBOOK_BUTTON] = (SimpleAction) p -> get(p).cycleSpellBook(p);
			h.actions[LIST_BUTTON] = (SimpleAction) p -> get(p).toggleList(p);
			h.actions[SPAWN_BUTTON] = (SimpleAction) p -> {
				if (QuestDashboards.requirePvpMode(p))
					QuestDashboards.openSpawn.accept(p);
			};
			h.actions[LOAD_BUTTON] = (SimpleAction) p -> get(p).load(p);
			for (int row = 0; row < ROW_BUTTONS.length; row++) {
				int r = row;
				h.actions[ROW_BUTTONS[row]] = (SimpleAction) p -> get(p).selectRow(p, r);
			}
			h.actions[UPDATE_BUTTON] = (SimpleAction) p -> get(p).updateSelected(p);
			h.actions[DELETE_BUTTON] = (SimpleAction) p -> get(p).deleteSelected(p);
			h.actions[SCROLL_UP] = (SimpleAction) p -> get(p).scroll(p, -1);
			h.actions[SCROLL_DOWN] = (SimpleAction) p -> get(p).scroll(p, 1);
			h.actions[CREATE_BUTTON] = (SimpleAction) p -> get(p).create(p);
			// on resizable this screen sits in the world-map slot, drawn over the modal Battle Presets opens in: close it first
			h.actions[BATTLE_PRESETS] = (SimpleAction) p -> {
				if (interfaceId == LARGE_INTERFACE_ID)
					p.closeInterface(ToplevelComponent.WORLD_MAP);
				BattlePresets.open(p);
			};
			for (int i = 0; i < GearLoadoutPreset.COMBAT_STATS.length; i++) {
				StatType type = GearLoadoutPreset.COMBAT_STATS[i];
				h.actions[FIRST_LEVEL_LINE + i] = (SimpleAction) p -> get(p).setPresetStat(p, type);
			}
		});
	}

	enum PresetListMode {
		FREE_KITS,
		PRESETS
	}
}
