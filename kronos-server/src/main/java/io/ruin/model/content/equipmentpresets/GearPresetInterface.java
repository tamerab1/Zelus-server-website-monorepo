package io.ruin.model.content.equipmentpresets;

import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.Interface;
import io.ruin.model.inter.InterfaceHandler;
import io.ruin.model.inter.ToplevelComponent;
import io.ruin.model.inter.actions.DefaultAction;
import io.ruin.model.inter.actions.SimpleAction;
import io.ruin.model.inter.dialogue.OptionsDialogue;
import io.ruin.model.inter.handlers.EquipmentStats;
import io.ruin.model.inter.handlers.TabEquipment;
import io.ruin.model.inter.utils.Option;
import io.ruin.model.item.Item;
import io.ruin.model.item.containers.Equipment;
import io.ruin.model.map.Position;
import io.ruin.model.map.object.actions.impl.OccultAltar;
import io.ruin.model.skills.magic.SpellBook;

import java.util.List;

/** Live interface controller for the ordered equipment preset collection. */
public class GearPresetInterface {

	public static final int INTERFACE_ID = Interface.BANK_PRESET_INTERFACE;
	public static final int VISIBLE_PRESETS = 6;

	private static final int[] STAT_COMPONENTS = {
		20, 21, 22, 23, 24, 25, 26, 27,
		28, 29, 30, 31, 32, 33, 34, 35
	};
	private static final int SPELLBOOK_TEXT = 38;
	private static final int RESTORE_STATS_TEXT = 41;
	private static final int RESTORE_SPEC_TEXT = 44;
	private static final int SELECTED_SUMMARY = 46;
	private static final int LOAD_BUTTON = 47;
	private static final int PANEL_HEADER = 10;

	private static final int[] EQUIPMENT_COMPONENTS = {
		70, 71, 72, 73, 74, 75, 76, 77, 78, 79, 80
	};
	private static final int[] EQUIPMENT_PLACEHOLDERS = {
		63, 64, 65, 66, 67, 68, 69, 82, 83, 84, 85
	};
	private static final int[] EQUIPMENT_BACKGROUNDS = {
		51, 52, 53, 54, 55, 56, 57, 58, 59, 60, 61
	};
	private static final int[] EQUIPMENT_SLOTS = GearPresetHandler.EQUIPMENT_SLOTS;
	private static final int EQUIPMENT_CONTAINER_START = 9320;
	private static final int INVENTORY_COMPONENT = 81;
	private static final int INVENTORY_CONTAINER = 9332;
	private static final int INVENTORY_OUTLINE = 87;
	private static final int VIEW_TOGGLE_BACKGROUND = 62;
	private static final int VIEW_TOGGLE = 86;
	private static final int INVENTORY_SPRITE = 900;
	private static final int EQUIPMENT_SPRITE = 901;

	private static final int[] PRESET_BUTTONS = {90, 91, 92, 93, 94, 95};
	private static final int[] PRESET_TEXT = {102, 103, 104, 105, 106, 107};
	private static final int[] PRESET_SELECTION = {108, 109, 110, 111, 112, 113};
	private static final int UPDATE_TEXT = 116;
	private static final int DELETE_TEXT = 119;
	private static final int SCROLL_TRACK = 120;
	private static final int SCROLL_THUMB = 121;
	private static final int SCROLL_UP = 122;
	private static final int SCROLL_UP_BACKGROUND = 123;
	private static final int SCROLL_UP_TEXT = 124;
	private static final int SCROLL_DOWN = 125;
	private static final int SCROLL_DOWN_BACKGROUND = 126;
	private static final int SCROLL_DOWN_TEXT = 127;
	private static final int PAGE_TEXT = 131;
	private static final int PRESET_PANEL_FIRST = 90;
	private static final int PRESET_PANEL_LAST = 131;

	private String selectedPresetId;
	private int scrollOffset;
	private boolean editingRestoreStats = true;
	private boolean editingRestoreSpecialAttack = true;
	private boolean showingInventory;
	private boolean openedFromBank;
	private Position accessPosition;

	public void open(Player player) {
		if (!player.isVisibleInterface(Interface.BANK)) {
			player.sendMessage("Equipment presets can only be opened from a bank.");
			return;
		}
		if (player.getGameMode().isUltimateIronman()) {
			player.sendMessage("Ultimate ironmen cannot use bank equipment presets.");
			return;
		}
		GearPresetHandler.sanitizePresets(player);
		selectedPresetId = null;
		scrollOffset = 0;
		editingRestoreStats = true;
		editingRestoreSpecialAttack = true;
		showingInventory = false;
		openedFromBank = true;
		accessPosition = player.getPosition().copy();
		player.openInterface(ToplevelComponent.MAINMODAL, INTERFACE_ID);
		refresh(player);
	}

	public void refresh(Player player) {
		GearPresetHandler.sanitizePresets(player);
		clampState(player);
		if (!showingInventory)
			setPresetPanelHidden(player, false);
		refreshStats(player);
		refreshOptions(player);
		/* refreshLoadoutView's setPresetPanelHidden(showingInventory) does a coarse show/hide of
		 * the whole 90-131 preset panel range -- it must run before refreshPresetButtons' more
		 * specific scroll-visibility logic, or it clobbers that and shows dead scroll arrows when
		 * there's nothing to scroll. */
		refreshLoadoutView(player);
		refreshPresetButtons(player);
	}

	private void refreshStats(Player player) {
		int[] bonuses = player.getEquipment().bonuses;
		for (int i = 0; i < STAT_COMPONENTS.length; i++)
			EquipmentStats.Stat.VALUES[i].sendString(player, bonuses[i], INTERFACE_ID, STAT_COMPONENTS[i]);
	}

	private void refreshCurrentEquipment(Player player) {
		/* A selected preset previews its own saved gear here instead of the player's live
		 * equipment -- Load is the only action that actually equips anything. */
		GearPreset preview = selected(player);
		for (int index = 0; index < EQUIPMENT_COMPONENTS.length; index++) {
			int component = EQUIPMENT_COMPONENTS[index];
			int container = EQUIPMENT_CONTAINER_START + index;
			int slot = EQUIPMENT_SLOTS[index];
			Item item = preview == null ? player.getEquipment().getSafe(slot) : preview.getEquipment().get(slot);
			player.getPacketSender().sendClientScript(149, "IviiiIsssss",
				INTERFACE_ID << 16 | component, container, 4, 7, 1, -1,
				preview == null ? "Remove" : "", "", "", "", "");
			/*
			 * Modern clients only accept inventory updates with the sentinel combined widget id.
			 * Script 149 binds this component to the unique container id, so the container-only
			 * overload is both sufficient and avoids emitting the now-invalid positive widget id.
			 */
			player.getPacketSender().sendItems(container,
				new Item[]{item == null ? null : item.copy()});
			player.getPacketSender().setHidden(INTERFACE_ID, EQUIPMENT_PLACEHOLDERS[index], item != null);
		}
	}

	private void refreshCurrentInventory(Player player) {
		GearPreset preview = selected(player);
		player.getPacketSender().sendClientScript(149, "IviiiIsssss",
			INTERFACE_ID << 16 | INVENTORY_COMPONENT, INVENTORY_CONTAINER, 4, 7, 0, -1,
			"", "", "", "", "");
		/* Use the sentinel widget id required by the current protocol implementation. */
		Item[] items = preview == null ? player.getInventory().getItems() : preview.getInventory().toArray(new Item[0]);
		player.getPacketSender().sendItems(INVENTORY_CONTAINER, items);
	}

	private void refreshLoadoutView(Player player) {
		player.getPacketSender().sendString(INTERFACE_ID, PANEL_HEADER,
			showingInventory ? "Inventory / Options" : "Equipment / Options");
		player.getPacketSender().setGraphic(INTERFACE_ID, VIEW_TOGGLE,
			showingInventory ? EQUIPMENT_SPRITE : INVENTORY_SPRITE);
		player.getPacketSender().setHidden(INTERFACE_ID, VIEW_TOGGLE_BACKGROUND, false);
		player.getPacketSender().setHidden(INTERFACE_ID, VIEW_TOGGLE, false);

		for (int component : EQUIPMENT_BACKGROUNDS)
			player.getPacketSender().setHidden(INTERFACE_ID, component, showingInventory);
		for (int component : EQUIPMENT_COMPONENTS)
			player.getPacketSender().setHidden(INTERFACE_ID, component, showingInventory);
		for (int component : EQUIPMENT_PLACEHOLDERS)
			player.getPacketSender().setHidden(INTERFACE_ID, component, showingInventory);

		player.getPacketSender().setHidden(INTERFACE_ID, INVENTORY_COMPONENT, !showingInventory);
		player.getPacketSender().setHidden(INTERFACE_ID, INVENTORY_OUTLINE, !showingInventory);
		setPresetPanelHidden(player, showingInventory);
		if (showingInventory)
			refreshCurrentInventory(player);
		else
			refreshCurrentEquipment(player);
	}

	private void setPresetPanelHidden(Player player, boolean hidden) {
		for (int component = PRESET_PANEL_FIRST; component <= PRESET_PANEL_LAST; component++)
			player.getPacketSender().setHidden(INTERFACE_ID, component, hidden);
	}

	private void toggleLoadoutView(Player player) {
		if (!canEdit(player) || !takeAction(player))
			return;
		showingInventory = !showingInventory;
		refresh(player);
	}

	private void refreshOptions(Player player) {
		int book = GearPresetHandler.activeSpellBook(player);
		player.getPacketSender().sendString(INTERFACE_ID, SPELLBOOK_TEXT,
			"Current spellbook: <col=ffd35a>" + displayName(SpellBook.VALUES[book]) + "</col>");
		player.getPacketSender().sendString(INTERFACE_ID, RESTORE_STATS_TEXT,
			"Restore stats: " + toggleText(editingRestoreStats));
		player.getPacketSender().sendString(INTERFACE_ID, RESTORE_SPEC_TEXT,
			"Restore spec: " + toggleText(editingRestoreSpecialAttack));

		GearPreset selected = selected(player);
		if (selected == null) {
			player.getPacketSender().sendString(INTERFACE_ID, SELECTED_SUMMARY,
				"Select a saved preset.");
			player.getPacketSender().sendString(INTERFACE_ID, UPDATE_TEXT, "<col=777777>Update Preset</col>");
			player.getPacketSender().sendString(INTERFACE_ID, DELETE_TEXT, "<col=777777>Delete Preset</col>");
			player.getPacketSender().setHidden(INTERFACE_ID, LOAD_BUTTON, true);
		} else {
			SpellBook savedBook = SpellBook.VALUES[selected.getSpellBook()];
			player.getPacketSender().sendString(INTERFACE_ID, SELECTED_SUMMARY,
				"<col=ffd35a>" + selected.getPresetName() + "</col><br>" + displayName(savedBook)
					+ " | Stats:" + onOff(selected.isRestoreStats())
					+ " | Spec:" + onOff(selected.isRestoreSpecialAttack()));
			player.getPacketSender().sendString(INTERFACE_ID, UPDATE_TEXT, "Update Preset");
			player.getPacketSender().sendString(INTERFACE_ID, DELETE_TEXT, "<col=ff7777>Delete Preset</col>");
			player.getPacketSender().setHidden(INTERFACE_ID, LOAD_BUTTON, false);
		}
	}

	private void refreshPresetButtons(Player player) {
		List<GearPreset> presets = player.gearPresets;
		for (int visual = 0; visual < VISIBLE_PRESETS; visual++) {
			int actual = visiblePresetIndex(scrollOffset, visual, presets.size());
			GearPreset preset = actual < 0 ? null : presets.get(actual);
			player.getPacketSender().sendString(INTERFACE_ID, PRESET_TEXT[visual],
				preset == null ? "<col=777777>Preset</col>" : preset.getPresetName());
			boolean selected = preset != null && preset.getId().equals(selectedPresetId);
			player.getPacketSender().setHidden(INTERFACE_ID, PRESET_SELECTION[visual], !selected);
		}

		int maxOffset = maxScrollOffset(presets.size());
		boolean scrollable = maxOffset > 0;
		for (int component : new int[]{SCROLL_TRACK, SCROLL_THUMB, SCROLL_UP, SCROLL_UP_BACKGROUND,
			SCROLL_UP_TEXT, SCROLL_DOWN, SCROLL_DOWN_BACKGROUND, SCROLL_DOWN_TEXT})
			player.getPacketSender().setHidden(INTERFACE_ID, component, !scrollable);
		if (scrollable) {
			int thumbY = 246 + (12 * scrollOffset / maxOffset);
			player.getPacketSender().setAlignment(INTERFACE_ID, SCROLL_THUMB, 492, thumbY);
		}
		if (presets.isEmpty()) {
			player.getPacketSender().sendString(INTERFACE_ID, PAGE_TEXT, "No saved presets");
		} else {
			int first = scrollOffset + 1;
			int last = Math.min(scrollOffset + VISIBLE_PRESETS, presets.size());
			player.getPacketSender().sendString(INTERFACE_ID, PAGE_TEXT,
				first + "-" + last + " of " + presets.size());
		}
	}

	private void selectVisible(Player player, int visualIndex) {
		if (visualIndex < 0 || visualIndex >= VISIBLE_PRESETS)
			return;
		int actualIndex = visiblePresetIndex(scrollOffset, visualIndex, player.gearPresets.size());
		if (actualIndex < 0) {
			player.sendMessage("That position is only a placeholder; it is not a saved preset.");
			return;
		}
		GearPreset preset = player.gearPresets.get(actualIndex);
		selectedPresetId = preset.getId();
		editingRestoreStats = preset.isRestoreStats();
		editingRestoreSpecialAttack = preset.isRestoreSpecialAttack();
		refresh(player);
	}

	private void create(Player player) {
		if (!canEdit(player) || player.consumerString != null || !takeAction(player))
			return;
		int maxPresets = GearPresetHandler.maxPresets(player);
		if (player.gearPresets.size() >= maxPresets) {
			player.sendMessage("You have reached your maximum of " + maxPresets
				+ " presets. Donate to unlock more preset slots.");
			return;
		}
		player.stringInput("Enter a preset name (max " + GearPresetHandler.MAX_NAME_LENGTH + " characters):", input -> {
			if (!player.isVisibleInterface(INTERFACE_ID))
				return;
			String name = GearPresetHandler.sanitizeName(input);
			if (name == null) {
				player.sendMessage("Use a non-blank name with letters, numbers, spaces, apostrophes, - or _.");
				return;
			}
			if (!canEdit(player) || player.gearPresets.size() >= GearPresetHandler.maxPresets(player))
				return;
			GearPreset preset = GearPresetHandler.capture(player, name, editingRestoreStats,
				editingRestoreSpecialAttack);
			player.gearPresets.add(preset);
			selectedPresetId = preset.getId();
			scrollOffset = Math.max(0, player.gearPresets.size() - VISIBLE_PRESETS);
			player.sendMessage("Equipment preset \"" + name + "\" created.");
			refresh(player);
		});
	}

	private void update(Player player) {
		if (!canEdit(player) || !takeAction(player))
			return;
		GearPreset preset = selected(player);
		if (preset == null) {
			player.sendMessage("Select a saved preset before updating it.");
			return;
		}
		GearPresetHandler.update(player, preset, editingRestoreStats, editingRestoreSpecialAttack);
		player.sendMessage("Equipment preset \"" + preset.getPresetName() + "\" updated.");
		refresh(player);
	}

	private void askDelete(Player player) {
		if (!canEdit(player) || !takeAction(player))
			return;
		GearPreset preset = selected(player);
		if (preset == null) {
			player.sendMessage("Select a saved preset before deleting it.");
			return;
		}
		String identity = preset.getId();
		String name = preset.getPresetName();
		player.dialogue(new OptionsDialogue("Delete preset \"" + name + "\"?",
			new Option("Yes, delete it.", () -> deleteConfirmed(player, identity)),
			new Option("No, keep it.")));
	}

	private void deleteConfirmed(Player player, String identity) {
		/* Can't use canEdit() here -- the "Yes, delete it." OptionsDialogue this runs from
		 * already closed the presets interface (and the bank underneath it), same as any
		 * dialogue, so isVisibleInterface(INTERFACE_ID) is guaranteed false by this point. That
		 * previously made every delete silently no-op. Only the real safety checks apply now. */
		if (player.isLocked() || player.getCombat().isDead() || !takeAction(player))
			return;
		int index = indexOf(player, identity);
		if (index < 0) {
			player.sendMessage("That preset no longer exists.");
			return;
		}
		String name = player.gearPresets.get(index).getPresetName();
		player.gearPresets.remove(index); // List removal is the compaction operation.
		if (player.gearPresets.isEmpty()) {
			selectedPresetId = null;
			editingRestoreStats = true;
			editingRestoreSpecialAttack = true;
		} else {
			GearPreset next = player.gearPresets.get(Math.min(index, player.gearPresets.size() - 1));
			selectedPresetId = next.getId();
			editingRestoreStats = next.isRestoreStats();
			editingRestoreSpecialAttack = next.isRestoreSpecialAttack();
		}
		player.sendMessage("Equipment preset \"" + name + "\" deleted.");
	}

	private void load(Player player) {
		if (!canLoad(player) || !takeAction(player))
			return;
		GearPreset preset = selected(player);
		if (preset == null) {
			player.sendMessage("Select a saved preset before loading it.");
			return;
		}
		if (GearPresetHandler.load(player, preset))
			player.sendMessage("Equipment preset \"" + preset.getPresetName() + "\" loaded.");
		refresh(player);
	}

	private void cycleSpellBook(Player player) {
		if (!canEdit(player) || !takeAction(player))
			return;
		int next = (GearPresetHandler.activeSpellBook(player) + 1) % SpellBook.VALUES.length;
		OccultAltar.switchBook(player, SpellBook.VALUES[next], false);
		player.sendMessage("Current spellbook changed to " + displayName(SpellBook.VALUES[next])
			+ ". Use Update Preset to save it to the selected preset.");
		refresh(player);
	}

	private boolean canEdit(Player player) {
		if (!openedFromBank || !player.isVisibleInterface(INTERFACE_ID) || accessPosition == null)
			return false;
		if (player.getPosition().getDistance(accessPosition) > 4) {
			player.sendMessage("Move back to the bank to use equipment presets.");
			return false;
		}
		if (player.isLocked() || player.getCombat().isDead()) {
			player.sendMessage("You cannot use equipment presets right now.");
			return false;
		}
		return true;
	}

	private boolean canLoad(Player player) {
		if (!canEdit(player))
			return false;
		if (player.getGameMode().isUltimateIronman()) {
			player.sendMessage("Ultimate ironmen cannot use bank equipment presets.");
			return false;
		}
		if (player.wildernessLevel > 0 || player.pvpAttackZone) {
			player.sendMessage("Equipment presets cannot be loaded in a PvP area.");
			return false;
		}
		if (player.getCombat().isAttacking(10) || player.getCombat().isDefending(10)) {
			player.sendMessage("You must be out of combat before loading a preset.");
			return false;
		}
		return true;
	}

	private boolean takeAction(Player player) {
		if (player.presetDelay.isDelayed())
			return false;
		player.presetDelay.delay(1);
		return true;
	}

	private void scroll(Player player, int direction) {
		int max = maxScrollOffset(player.gearPresets.size());
		if (max == 0)
			return;
		scrollOffset = Math.max(0, Math.min(max, scrollOffset + (direction * 3)));
		refreshPresetButtons(player);
	}

	private void clampState(Player player) {
		scrollOffset = clampScrollOffset(scrollOffset, player.gearPresets.size());
		if (selectedPresetId != null && indexOf(player, selectedPresetId) < 0)
			selectedPresetId = null;
	}

	static int maxScrollOffset(int presetCount) {
		return Math.max(0, presetCount - VISIBLE_PRESETS);
	}

	static int clampScrollOffset(int requestedOffset, int presetCount) {
		return Math.max(0, Math.min(requestedOffset, maxScrollOffset(presetCount)));
	}

	static int visiblePresetIndex(int scrollOffset, int visualIndex, int presetCount) {
		if (visualIndex < 0 || visualIndex >= VISIBLE_PRESETS)
			return -1;
		int actualIndex = clampScrollOffset(scrollOffset, presetCount) + visualIndex;
		return actualIndex < presetCount ? actualIndex : -1;
	}

	private GearPreset selected(Player player) {
		int index = indexOf(player, selectedPresetId);
		return index < 0 ? null : player.gearPresets.get(index);
	}

	private int indexOf(Player player, String identity) {
		if (identity == null)
			return -1;
		for (int index = 0; index < player.gearPresets.size(); index++) {
			if (identity.equals(player.gearPresets.get(index).getId()))
				return index;
		}
		return -1;
	}

	private static String displayName(SpellBook book) {
		String lower = book.name().toLowerCase();
		return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
	}

	private static String toggleText(boolean enabled) {
		return enabled ? "<col=55ff55>ON</col>" : "<col=ff5555>OFF</col>";
	}

	private static String onOff(boolean enabled) {
		return enabled ? "On" : "Off";
	}

	private void close() {
		selectedPresetId = null;
		scrollOffset = 0;
		showingInventory = false;
		openedFromBank = false;
		accessPosition = null;
	}

	/** Called from Equipment.sendUpdates so bonuses/items/model stay live while open. */
	public static void refreshEquipmentState(Player player) {
		if (player.isVisibleInterface(INTERFACE_ID)) {
			GearPresetInterface controller = player.getGearPresetInterface();
			controller.refreshStats(player);
			if (!controller.showingInventory)
				controller.refreshCurrentEquipment(player);
		}
	}

	/** Called from Inventory.sendUpdates so the alternate loadout view remains live. */
	public static void refreshInventoryState(Player player) {
		if (player.isVisibleInterface(INTERFACE_ID)) {
			GearPresetInterface controller = player.getGearPresetInterface();
			if (controller.showingInventory)
				controller.refreshCurrentInventory(player);
		}
	}

	public static void register() {
		InterfaceHandler.register(INTERFACE_ID, handler -> {
			handler.actions[VIEW_TOGGLE] = (SimpleAction) player ->
				player.getGearPresetInterface().toggleLoadoutView(player);
			handler.actions[36] = (SimpleAction) player -> player.getGearPresetInterface().cycleSpellBook(player);
			handler.actions[39] = (SimpleAction) player -> {
				GearPresetInterface controller = player.getGearPresetInterface();
				if (!controller.canEdit(player))
					return;
				controller.editingRestoreStats = !controller.editingRestoreStats;
				controller.refreshOptions(player);
			};
			handler.actions[42] = (SimpleAction) player -> {
				GearPresetInterface controller = player.getGearPresetInterface();
				if (!controller.canEdit(player))
					return;
				controller.editingRestoreSpecialAttack = !controller.editingRestoreSpecialAttack;
				controller.refreshOptions(player);
			};
			for (int visual = 0; visual < PRESET_BUTTONS.length; visual++) {
				int visualIndex = visual;
				handler.actions[PRESET_BUTTONS[visual]] = (SimpleAction) player ->
					player.getGearPresetInterface().selectVisible(player, visualIndex);
			}
			handler.actions[47] = (SimpleAction) player -> player.getGearPresetInterface().load(player);
			handler.actions[114] = (SimpleAction) player -> player.getGearPresetInterface().update(player);
			handler.actions[117] = (SimpleAction) player -> player.getGearPresetInterface().askDelete(player);
			handler.actions[122] = (SimpleAction) player -> player.getGearPresetInterface().scroll(player, -1);
			handler.actions[125] = (SimpleAction) player -> player.getGearPresetInterface().scroll(player, 1);
			handler.actions[128] = (SimpleAction) player -> player.getGearPresetInterface().create(player);

			for (int index = 0; index < EQUIPMENT_COMPONENTS.length; index++) {
				int equipmentSlot = EQUIPMENT_SLOTS[index];
				handler.actions[EQUIPMENT_COMPONENTS[index]] = (DefaultAction) (player, option, slot, itemId) -> {
					GearPresetInterface controller = player.getGearPresetInterface();
					if (controller.selected(player) != null)
						return; // previewing a saved preset's gear, not the player's own -- nothing to unequip
					TabEquipment.itemAction(player, option, equipmentSlot);
					refreshEquipmentState(player);
				};
			}
			handler.closedAction = (player, nextInterface) -> player.getGearPresetInterface().close();
		});
	}
}
