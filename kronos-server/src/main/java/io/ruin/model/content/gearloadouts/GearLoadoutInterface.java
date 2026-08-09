package io.ruin.model.content.gearloadouts;

import io.ruin.cache.InterfaceDef;
import io.ruin.model.entity.player.Player;
import io.ruin.model.entity.player.SecondaryGroup;
import io.ruin.model.inter.InterfaceHandler;
import io.ruin.model.inter.ToplevelComponent;
import io.ruin.model.inter.actions.SimpleAction;
import io.ruin.model.inter.dialogue.OptionsDialogue;
import io.ruin.model.inter.utils.Option;
import io.ruin.model.item.Item;
import io.ruin.model.item.containers.Equipment;
import io.ruin.model.skills.magic.SpellBook;
import io.ruin.model.stat.StatType;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GearLoadoutInterface {

	private static final int INTERFACE_ID = 706;
	private static final Map<String, GearLoadoutInterface> INTERFACES = new ConcurrentHashMap<>();
	private static boolean registered;
	private static final int PRESET_COUNT = 13;
	private static final int CREATE_BUTTON = 47;
	private static final int CREATE_BUTTON_BACKGROUND = 48;
	private static final int CREATE_BUTTON_ICON = 52;
	private static final int CLONE_BUTTON = 109;
	private static final int CLONE_BUTTON_BACKGROUND = 110;
	private static final int CLONE_BUTTON_ICON = 115;
	private static final int LOAD_BUTTON = 119;
	private static final int LOAD_BUTTON_BACKGROUND = 120;
	private static final int LOAD_BUTTON_ICON = 125;
	private static final int DELETE_BUTTON = 126;
	private static final int DELETE_BUTTON_BACKGROUND = 127;
	private static final int DELETE_ACTION_COMPONENT = 131;
	private static final int DELETE_BUTTON_ICON = 132;
	private static final int CURRENT_PRESET_NAME = 117;
	private static final int STAT_PANEL = 172;
	private static final int STAT_TITLE = 173;
	private static final int SPELLBOOK_VALUE_TEXT = 181;
	private static final int FREE_KIT_SELECTOR_BUTTON = 183;
	private static final int FREE_KIT_SELECTOR_BORDER = 184;
	private static final int FREE_KIT_SELECTOR_BACKGROUND = 185;
	private static final int FREE_KIT_SELECTOR_TEXT = 186;
	private static final int STAT_SPELLBOOK_ICON = 194;
	private static final int PRESET_SELECTOR_BUTTON = 195;
	private static final int PRESET_SELECTOR_BORDER = 196;
	private static final int PRESET_SELECTOR_BACKGROUND = 197;
	private static final int PRESET_SELECTOR_TEXT = 198;
	private static final int REQUIRED_COMPONENT_COUNT = PRESET_SELECTOR_TEXT + 1;

	private static final int[] PRESET_BUTTONS = { 71, 74, 77, 80, 83, 86, 89, 92, 95, 98, 101, 104, 107 };
	private static final int[] PRESET_TEXTS = { 72, 75, 78, 81, 84, 87, 90, 93, 96, 99, 102, 105, 108 };
	// Each row of 4 must be ascending (left-to-right) to match slot order -- this used to be
	// descending per row (136,135,134,133, ...), which mirrored every row in the preview so it
	// no longer matched the preset's actual stored item order (confirmed by comparing a free
	// kit's saved JSON against what actually spawns -- spawning already used plain slot order).
	private static final int[] INVENTORY_COMPONENTS = {
			133, 134, 135, 136,
			137, 138, 139, 140,
			141, 142, 143, 144,
			145, 146, 147, 148,
			149, 150, 151, 152,
			153, 154, 155, 156,
			157, 158, 159, 160
	};
	private static final int[] STAT_TEXTS = { 174, 175, 176, 177, 178, 179, 180 };
	private static final int[] STAT_ICON_BUTTONS = { 187, 188, 189, 190, 191, 192, 193 };
	private static final EquipmentPreview[] EQUIPMENT_PREVIEWS = {
			new EquipmentPreview(Equipment.SLOT_HAT, 161, 34, 4732),
			new EquipmentPreview(Equipment.SLOT_QUIVER, 182, -1, 4743),
			new EquipmentPreview(Equipment.SLOT_CAPE, 168, 35, 4733),
			new EquipmentPreview(Equipment.SLOT_AMULET, 162, 36, 4734),
			new EquipmentPreview(Equipment.SLOT_AMMO, 169, 44, 4735),
			new EquipmentPreview(Equipment.SLOT_WEAPON, 170, 37, 4736),
			new EquipmentPreview(Equipment.SLOT_CHEST, 163, 38, 4737),
			new EquipmentPreview(Equipment.SLOT_SHIELD, 171, 39, 4738),
			new EquipmentPreview(Equipment.SLOT_LEGS, 164, 40, 4739),
			new EquipmentPreview(Equipment.SLOT_HANDS, 167, 41, 4740),
			new EquipmentPreview(Equipment.SLOT_FEET, 165, 42, 4741),
			new EquipmentPreview(Equipment.SLOT_RING, 166, 43, 4742)
	};

	public GearLoadoutPreset currentPreset;
	private boolean selectorOpen;
	private boolean currentPresetIsFreeKit;
	private PresetListMode listMode = PresetListMode.PRESETS;

	public static GearLoadoutInterface get(Player player) {
		return INTERFACES.computeIfAbsent(interfaceKey(player), key -> new GearLoadoutInterface());
	}

	public static void open(Player player) {
		if (!isInterfacePacked()) {
			player.sendMessage("Gear loadouts interface " + INTERFACE_ID + " is missing from the cache. Copy the TOML patch and rebuild the cache.");
			return;
		}
		register();
		get(player).openInterface(player);
	}

	private static boolean isInterfacePacked() {
		return InterfaceDef.COUNTS != null
				&& INTERFACE_ID >= 0
				&& INTERFACE_ID < InterfaceDef.COUNTS.length
				&& INTERFACE_ID < InterfaceHandler.HANDLERS.length
				&& InterfaceDef.COUNTS[INTERFACE_ID] >= REQUIRED_COMPONENT_COUNT;
	}

	private static String interfaceKey(Player player) {
		return player.getName() == null ? "unknown" : player.getName().toLowerCase();
	}

	private void openInterface(Player player) {
		ensureSelection(player);
		selectorOpen = false;
		player.openInterface(ToplevelComponent.MAINMODAL, INTERFACE_ID);
		update(player);
	}

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
		selectorOpen = false;
		update(player);
	}

	public void clearSelectionIf(GearLoadoutPreset preset) {
		if (currentPreset == preset) {
			currentPreset = null;
			currentPresetIsFreeKit = false;
		}
	}

	public void cyclePreset(Player player) {
		togglePresetDropdown(player);
	}

	public void toggleFreeKitDropdown(Player player) {
		toggleSelector(player, PresetListMode.FREE_KITS);
	}

	public void togglePresetDropdown(Player player) {
		toggleSelector(player, PresetListMode.PRESETS);
	}

	private void toggleSelector(Player player, PresetListMode mode) {
		List<GearLoadoutPreset> list = presetsForMode(player, mode);
		if (list.isEmpty()) {
			selectorOpen = false;
			update(player);
			player.sendMessage(mode == PresetListMode.FREE_KITS ? "There aren't any free kits configured yet." : "You don't have any presets saved yet.");
			return;
		}
		boolean sameMode = listMode == mode;
		listMode = mode;
		if (!currentSelectionMatches(player, mode)) {
			currentPreset = list.get(0);
			currentPresetIsFreeKit = mode == PresetListMode.FREE_KITS;
		}
		selectorOpen = sameMode ? !selectorOpen : true;
		update(player);
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
		sendSpellBook(player, currentPreset);
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

	private void update(Player player) {
		ensureSelection(player);
		sendSelectors(player);
		sendPreview(player);
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
		if (!currentPresetIsFreeKit && currentPreset != null && playerPresets.contains(currentPreset)) {
			return;
		}
		if (!playerPresets.isEmpty()) {
			currentPreset = playerPresets.get(0);
			currentPresetIsFreeKit = false;
			listMode = PresetListMode.PRESETS;
			return;
		}
		List<GearLoadoutPreset> freeKits = FreeGearLoadoutKits.getKits();
		currentPreset = freeKits.isEmpty() ? null : freeKits.get(0);
		currentPresetIsFreeKit = currentPreset != null;
		listMode = currentPresetIsFreeKit ? PresetListMode.FREE_KITS : PresetListMode.PRESETS;
	}

	private void sendSelectors(Player player) {
		int unlockedPresets = getUnlockedPresets(player);
		player.getPacketSender().setHidden(INTERFACE_ID, 66, !selectorOpen);
		setStatsHidden(player, selectorOpen);
		player.getPacketSender().sendString(INTERFACE_ID, FREE_KIT_SELECTOR_TEXT, selectorText("Free Kits", listMode == PresetListMode.FREE_KITS));
		player.getPacketSender().sendString(INTERFACE_ID, PRESET_SELECTOR_TEXT, selectorText("Presets", listMode == PresetListMode.PRESETS));
		List<GearLoadoutPreset> visiblePresets = presetsForMode(player, listMode);
		for (int i = 0; i < PRESET_COUNT; i++) {
			boolean showRow = selectorOpen && shouldShowRow(i, visiblePresets.size(), unlockedPresets);
			player.getPacketSender().setHidden(INTERFACE_ID, PRESET_BUTTONS[i], !showRow);
			String text;
			if (!showRow)
				text = "";
			else {
				GearLoadoutPreset preset = visiblePresets.get(i);
				preset.normalize();
				text = preset == currentPreset ? "<col=ff981f>" + truncate(preset.presetName, 16) : "<col=e6804d>" + truncate(preset.presetName, 16);
			}
			player.getPacketSender().sendString(INTERFACE_ID, PRESET_TEXTS[i], text);
		}
	}

	private boolean shouldShowRow(int slot, int listSize, int unlockedPresets) {
		if (listMode == PresetListMode.FREE_KITS)
			return slot < listSize;
		return slot < listSize && slot < unlockedPresets;
	}

	private List<GearLoadoutPreset> presetsForMode(Player player, PresetListMode mode) {
		return mode == PresetListMode.FREE_KITS ? FreeGearLoadoutKits.getKits() : GearLoadoutStore.getPresets(player);
	}

	private boolean currentSelectionMatches(Player player, PresetListMode mode) {
		if (mode == PresetListMode.FREE_KITS)
			return currentPresetIsFreeKit && matchingFreeKit(currentPreset) != null;
		return !currentPresetIsFreeKit && currentPreset != null && GearLoadoutStore.getPresets(player).contains(currentPreset);
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

	private String selectorText(String label, boolean selected) {
		return (selected ? "<col=ff981f>" : "<col=e6804d>") + label;
	}

	private void setStatsHidden(Player player, boolean hidden) {
		player.getPacketSender().setHidden(INTERFACE_ID, STAT_PANEL, hidden);
		player.getPacketSender().setHidden(INTERFACE_ID, STAT_TITLE, hidden);
		player.getPacketSender().setHidden(INTERFACE_ID, SPELLBOOK_VALUE_TEXT, hidden);
		player.getPacketSender().setHidden(INTERFACE_ID, STAT_SPELLBOOK_ICON, hidden);
		for (int statText : STAT_TEXTS)
			player.getPacketSender().setHidden(INTERFACE_ID, statText, hidden);
		for (int statIcon : STAT_ICON_BUTTONS)
			player.getPacketSender().setHidden(INTERFACE_ID, statIcon, hidden);
	}

	private void sendPreview(Player player) {
		if (currentPreset == null) {
			player.getPacketSender().sendString(INTERFACE_ID, CURRENT_PRESET_NAME, "None");
			sendEquipment(player, null);
			sendInventory(player, null);
			sendStats(player, null);
			sendSpellBook(player, null);
			return;
		}

		currentPreset.normalize();
		player.getPacketSender().sendString(INTERFACE_ID, CURRENT_PRESET_NAME, currentPreset.presetName);
		sendEquipment(player, currentPreset);
		sendInventory(player, currentPreset);
		sendStats(player, currentPreset);
		sendSpellBook(player, currentPreset);
	}

	private void sendEquipment(Player player, GearLoadoutPreset preset) {
		Item[] equipment = preset == null ? new Item[GearLoadoutPreset.EQUIPMENT_SIZE] : preset.getEquipmentItems();
		for (EquipmentPreview preview : EQUIPMENT_PREVIEWS) {
			Item item = preview.slot < equipment.length ? equipment[preview.slot] : null;
			if (preview.placeholderComponent != -1)
				player.getPacketSender().setHidden(INTERFACE_ID, preview.placeholderComponent, item != null);
			sendItemToContainer(player, preview.containerId, preview.itemComponent, item);
		}
	}

	private void sendInventory(Player player, GearLoadoutPreset preset) {
		Item[] inventory = preset == null ? new Item[GearLoadoutPreset.INVENTORY_SIZE] : preset.getInventoryItems();
		for (int slot = 0; slot < INVENTORY_COMPONENTS.length; slot++)
			sendItemToContainer(player, 2732 + slot, INVENTORY_COMPONENTS[slot], inventory[slot]);
	}

	private void sendStats(Player player, GearLoadoutPreset preset) {
		for (int i = 0; i < GearLoadoutPreset.COMBAT_STATS.length; i++) {
			StatType statType = GearLoadoutPreset.COMBAT_STATS[i];
			String text = selectorOpen ? "" : formatStatText(statType, preset);
			player.getPacketSender().sendString(INTERFACE_ID, STAT_TEXTS[i], text);
		}
	}

	private String formatStatText(StatType statType, GearLoadoutPreset preset) {
		if (preset == null || preset.getCombatLevel(statType) <= 0)
			return "--/--";
		return preset.getCombatCurrentLevel(statType) + "/" + preset.getCombatLevel(statType);
	}

	private void sendSpellBook(Player player, GearLoadoutPreset preset) {
		String text = "None";
		SpellBook spellBook = SpellBook.MODERN;
		if (preset != null) {
			spellBook = preset.getSpellBook();
			text = formatSpellBookName(spellBook);
		}
		int sprite = spellBookSprite(spellBook);
		player.getPacketSender().setGraphic(INTERFACE_ID, STAT_SPELLBOOK_ICON, sprite);
		player.getPacketSender().sendString(INTERFACE_ID, SPELLBOOK_VALUE_TEXT, selectorOpen ? "" : text);
	}

	private int spellBookSprite(SpellBook spellBook) {
		return switch (spellBook) {
			case MODERN -> 780;
			case ANCIENT -> 1583;
			case LUNAR -> 1584;
			case ARCEUUS -> 1711;
		};
	}

	private static String formatSpellBookName(SpellBook spellBook) {
		String name = spellBook.name().toLowerCase();
		return name.substring(0, 1).toUpperCase() + name.substring(1);
	}

	private static String truncate(String text, int maxLength) {
		if (text == null)
			return "";
		if (text.length() <= maxLength)
			return text;
		return text.substring(0, Math.max(0, maxLength - 2)) + "..";
	}

	private void sendItemToContainer(Player player, int containerId, int componentId, Item item) {
		if (!InterfaceDef.valid(INTERFACE_ID, componentId))
			return;
		player.getPacketSender().sendClientScript(
				149, "IviiiIsssss",
				INTERFACE_ID << 16 | componentId, containerId,
				4, 7, 1, -1, "", "", "", "", ""
		);
		player.getPacketSender().sendItems(
				-1,
				componentId,
				containerId,
				item == null ? new Item[1] : new Item[] { item.copy() }
		);
	}

	public static synchronized void register() {
		if (registered)
			return;
		if (!isInterfacePacked())
			return;
		InterfaceHandler.register(INTERFACE_ID, h -> {
			SimpleAction createPreset = player -> {
				if (GearLoadoutStore.getPresets(player).size() >= GearLoadoutInterface.get(player).getUnlockedPresets(player)) {
					player.sendMessage("You don't have anymore preset slots to save this to.");
					return;
				}
				// createBlankPreset() made an empty preset (no items, just combat stats) --
				// saveGearPreset() captures the player's current inventory/equipment instead,
				// which is what "Create" is actually meant to do here.
				GearLoadoutHandler.saveGearPreset(player);
			};
			h.actions[CREATE_BUTTON] = createPreset;
			h.actions[CREATE_BUTTON_BACKGROUND] = createPreset;
			h.actions[CREATE_BUTTON_ICON] = createPreset;
			SimpleAction clonePreset = player -> {
				if (GearLoadoutInterface.get(player).currentPreset == null) {
					player.sendMessage("Create or select a preset to clone into.");
					return;
				}
				if (GearLoadoutInterface.get(player).isViewingFreeKit()) {
					player.sendMessage("Free kits can't be overwritten. Select a saved preset first.");
					return;
				}
				player.dialogue(new OptionsDialogue("Clone your current gear, inventory, stats, and spellbook into this preset?",
						new Option("Yes, overwrite this preset.", () -> GearLoadoutHandler.cloneCurrentLoadout(player, GearLoadoutInterface.get(player).currentPreset)),
						new Option("Nevermind.")));
			};
			h.actions[CLONE_BUTTON] = clonePreset;
			h.actions[CLONE_BUTTON_BACKGROUND] = clonePreset;
			h.actions[CLONE_BUTTON_ICON] = clonePreset;
			SimpleAction loadPreset = player -> {
				if (GearLoadoutInterface.get(player).currentPreset == null) {
					player.sendMessage("You don't have a preset selected to load.");
					return;
				}
				if (GearLoadoutInterface.get(player).isViewingFreeKit())
					GearLoadoutHandler.spawnFreeKit(player, GearLoadoutInterface.get(player).currentPreset);
				else
					GearLoadoutHandler.equipPreset(player, GearLoadoutInterface.get(player).currentPreset);
			};
			h.actions[LOAD_BUTTON] = loadPreset;
			h.actions[LOAD_BUTTON_BACKGROUND] = loadPreset;
			h.actions[LOAD_BUTTON_ICON] = loadPreset;
			SimpleAction deletePreset = player -> {
				if (GearLoadoutInterface.get(player).currentPreset == null) {
					player.sendMessage("You don't have a preset selected to delete.");
					return;
				}
				if (GearLoadoutInterface.get(player).isViewingFreeKit()) {
					player.sendMessage("Free kits can't be deleted.");
					return;
				}
				player.dialogue(new OptionsDialogue("Are you sure you want to delete this preset?",
						new Option("Yes, delete this preset.", () -> GearLoadoutHandler.deleteGearPreset(player, GearLoadoutInterface.get(player).currentPreset)),
						new Option("Nevermind.")));
			};
			h.actions[DELETE_BUTTON] = deletePreset;
			h.actions[DELETE_BUTTON_BACKGROUND] = deletePreset;
			h.actions[DELETE_ACTION_COMPONENT] = deletePreset;
			h.actions[DELETE_BUTTON_ICON] = deletePreset;
			SimpleAction cycleSpellBook = player -> GearLoadoutInterface.get(player).cycleSpellBook(player);
			bind(h, STAT_SPELLBOOK_ICON, cycleSpellBook);
			SimpleAction toggleFreeKitDropdown = player -> GearLoadoutInterface.get(player).toggleFreeKitDropdown(player);
			bind(h, FREE_KIT_SELECTOR_BUTTON, toggleFreeKitDropdown);
			bind(h, FREE_KIT_SELECTOR_BORDER, toggleFreeKitDropdown);
			bind(h, FREE_KIT_SELECTOR_BACKGROUND, toggleFreeKitDropdown);
			bind(h, FREE_KIT_SELECTOR_TEXT, toggleFreeKitDropdown);
			SimpleAction togglePresetDropdown = player -> GearLoadoutInterface.get(player).togglePresetDropdown(player);
			bind(h, PRESET_SELECTOR_BUTTON, togglePresetDropdown);
			bind(h, PRESET_SELECTOR_BORDER, togglePresetDropdown);
			bind(h, PRESET_SELECTOR_BACKGROUND, togglePresetDropdown);
			bind(h, PRESET_SELECTOR_TEXT, togglePresetDropdown);
			for (int i = 0; i < PRESET_BUTTONS.length; i++) {
				int slot = i;
				h.actions[PRESET_BUTTONS[i]] = (SimpleAction) player -> {
					if (GearLoadoutInterface.get(player).listMode == PresetListMode.FREE_KITS) {
						List<GearLoadoutPreset> freeKits = FreeGearLoadoutKits.getKits();
						if (slot >= freeKits.size()) {
							GearLoadoutInterface.get(player).selectorOpen = false;
							GearLoadoutInterface.get(player).update(player);
							player.sendMessage("There isn't a free kit in this slot.");
							return;
						}
						GearLoadoutInterface.get(player).selectFreeKit(player, freeKits.get(slot));
						return;
					}
					if (slot >= GearLoadoutInterface.get(player).getUnlockedPresets(player)) {
						GearLoadoutInterface.get(player).selectorOpen = false;
						GearLoadoutInterface.get(player).update(player);
						player.sendMessage("Preset locked, you'll get access to more preset slots depending on your donator rank.");
						return;
					}
					List<GearLoadoutPreset> presets = GearLoadoutStore.getPresets(player);
					if (slot >= presets.size()) {
						GearLoadoutInterface.get(player).selectorOpen = false;
						GearLoadoutInterface.get(player).update(player);
						player.sendMessage("You don't have a preset in this slot.");
						return;
					}
					GearLoadoutInterface.get(player).selectPreset(player, presets.get(slot));
				};
			}
			for (int i = 0; i < STAT_ICON_BUTTONS.length; i++) {
				StatType statType = GearLoadoutPreset.COMBAT_STATS[i];
				bind(h, STAT_ICON_BUTTONS[i], (SimpleAction) player -> GearLoadoutInterface.get(player).setPresetStat(player, statType));
			}
		});
		registered = true;
	}

	private static void bind(InterfaceHandler handler, int componentId, SimpleAction action) {
		if (componentId >= 0 && componentId < handler.actions.length)
			handler.actions[componentId] = action;
	}

	private enum PresetListMode {
		FREE_KITS,
		PRESETS
	}

	private static class EquipmentPreview {
		private final int slot;
		private final int itemComponent;
		private final int placeholderComponent;
		private final int containerId;

		private EquipmentPreview(int slot, int itemComponent, int placeholderComponent, int containerId) {
			this.slot = slot;
			this.itemComponent = itemComponent;
			this.placeholderComponent = placeholderComponent;
			this.containerId = containerId;
		}
	}
}
