package io.ruin.model.content.gearloadouts;

import io.ruin.cache.ObjType;
import io.ruin.model.entity.player.Player;
import io.ruin.model.item.Item;
import io.ruin.model.stat.Stat;
import io.ruin.model.stat.StatType;
import io.ruin.model.var.VarPlayerRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class GearLoadoutHandler {

	private static final int MAX_NAME_LENGTH = 20;

	public static void saveGearPreset(Player player) {
		player.stringInput("Enter a name for your preset", name -> saveGearPreset(player, name));
	}

	public static void createBlankPreset(Player player) {
		player.stringInput("Enter a name for your preset", name -> createBlankPreset(player, name));
	}

	public static void createBlankPreset(Player player, String name) {
		name = cleanName(name);
		if (name == null) {
			player.sendMessage("Preset names must be between 1 and " + MAX_NAME_LENGTH + " characters.");
			return;
		}
		List<GearLoadoutPreset> presets = GearLoadoutStore.getPresets(player);
		if (presets.size() >= GearLoadoutInterface.get(player).getUnlockedPresets(player)) {
			player.sendMessage("You don't have anymore preset slots to save this to.");
			return;
		}
		for (GearLoadoutPreset preset : presets) {
			if (preset.presetName != null && preset.presetName.equalsIgnoreCase(name)) {
				player.sendMessage("You already have a preset with that name!");
				return;
			}
		}
		GearLoadoutPreset preset = createBlankFromPlayer(player, name);
		presets.add(preset);
		GearLoadoutStore.save(player);
		GearLoadoutInterface.get(player).selectPreset(player, preset);
		player.sendMessage("Gear preset created!");
	}

	public static void saveGearPreset(Player player, String name) {
		name = cleanName(name);
		if (name == null) {
			player.sendMessage("Preset names must be between 1 and " + MAX_NAME_LENGTH + " characters.");
			return;
		}
		List<GearLoadoutPreset> presets = GearLoadoutStore.getPresets(player);
		if (presets.size() >= GearLoadoutInterface.get(player).getUnlockedPresets(player)) {
			player.sendMessage("You don't have anymore preset slots to save this to.");
			return;
		}
		for (GearLoadoutPreset preset : presets) {
			if (preset.presetName != null && preset.presetName.equalsIgnoreCase(name)) {
				player.sendMessage("You already have a preset with that name!");
				return;
			}
		}
		GearLoadoutPreset preset = createFromPlayer(player, name);
		presets.add(preset);
		GearLoadoutStore.save(player);
		GearLoadoutInterface.get(player).selectPreset(player, preset);
		player.sendMessage("Gear preset saved!");
	}

	public static void cloneCurrentLoadout(Player player, GearLoadoutPreset preset) {
		if (preset == null) {
			player.sendMessage("You don't have a preset selected to clone into.");
			return;
		}
		GearLoadoutPreset clone = createFromPlayer(player, preset.presetName);
		preset.inventory = clone.inventory;
		preset.equipment = clone.equipment;
		preset.combatLevels = clone.combatLevels;
		preset.combatCurrentLevels = clone.combatCurrentLevels;
		preset.spellBook = clone.spellBook;
		preset.normalize();
		GearLoadoutStore.save(player);
		GearLoadoutInterface.get(player).selectPreset(player, preset);
		player.sendMessage("Current loadout cloned into preset!");
	}

	public static void deleteGearPreset(Player player, GearLoadoutPreset preset) {
		if (preset == null) {
			player.sendMessage("You don't have a preset selected to delete.");
			return;
		}
		GearLoadoutStore.getPresets(player).remove(preset);
		GearLoadoutStore.save(player);
		GearLoadoutInterface.get(player).clearSelectionIf(preset);
		GearLoadoutInterface.open(player);
		player.sendMessage("Gear preset deleted!");
	}

	public static boolean canEquipPreset(Player player, GearLoadoutPreset preset) {
		if (preset == null)
			return false;
		preset.normalize();
		Map<String, Long> requiredItemCounts = new HashMap<>();
		for (Item item : preset.getEquipmentItems())
			addRequired(requiredItemCounts, item);
		for (Item item : preset.getInventoryItems())
			addRequired(requiredItemCounts, item);

		Map<String, Long> availableItemCounts = new HashMap<>();
		for (Item item : player.getBank().getItems())
			addAvailable(availableItemCounts, item);
		for (Item item : player.getInventory().getItems())
			addAvailable(availableItemCounts, item);
		for (Item item : player.getEquipment().getItems())
			addAvailable(availableItemCounts, item);

		for (Map.Entry<String, Long> entry : requiredItemCounts.entrySet()) {
			if (availableItemCounts.getOrDefault(entry.getKey(), 0L) < entry.getValue())
				return false;
		}
		return true;
	}

	public static void equipPreset(Player player, GearLoadoutPreset preset) {
		if (preset == null) {
			player.sendMessage("You don't have a preset selected to load.");
			return;
		}
		preset.normalize();
		if (!canEquipPreset(player, preset)) {
			player.sendMessage("You do not have the required items to equip this preset!");
			return;
		}

		if (!depositCurrentLoadout(player))
			return;

		Item[] equipment = preset.getEquipmentItems();
		for (int slot = 0; slot < equipment.length; slot++) {
			Item item = equipment[slot];
			if (item == null)
				continue;
			if (removeFromBank(player, item))
				player.getEquipment().set(slot, item.copy());
		}

		Item[] inventory = preset.getInventoryItems();
		for (int slot = 0; slot < inventory.length; slot++) {
			Item item = inventory[slot];
			if (item == null)
				continue;
			if (removeFromBank(player, item))
				player.getInventory().set(slot, item.copy());
		}

		applyStats(player, preset);
		preset.getSpellBook().setActive(player);
		player.getInventory().sendUpdates();
		player.getEquipment().sendUpdates();
		GearLoadoutInterface.open(player);
		player.sendMessage("Gear preset equipped!");
	}

	public static void spawnFreeKit(Player player, GearLoadoutPreset kit) {
		if (kit == null) {
			player.sendMessage("You don't have a free kit selected to load.");
			return;
		}
		kit.normalize();
		if (!depositCurrentLoadout(player))
			return;

		Item[] equipment = kit.getEquipmentItems();
		for (int slot = 0; slot < equipment.length; slot++) {
			Item item = equipment[slot];
			if (item != null)
				player.getEquipment().set(slot, item.copy());
		}

		Item[] inventory = kit.getInventoryItems();
		for (int slot = 0; slot < inventory.length; slot++) {
			Item item = inventory[slot];
			if (item != null)
				player.getInventory().set(slot, item.copy());
		}

		applyStats(player, kit);
		kit.getSpellBook().setActive(player);
		player.getInventory().sendUpdates();
		player.getEquipment().sendUpdates();
		GearLoadoutInterface.open(player);
		player.sendMessage("Free kit loaded!");
	}

	private static boolean depositCurrentLoadout(Player player) {
		player.getBank().deposit(player.getInventory(), false);
		player.getBank().deposit(player.getEquipment(), false);
		if (player.getInventory().isNotEmpty() || player.getEquipment().isNotEmpty()) {
			player.sendMessage("Your bank needs enough room before loading this preset.");
			player.getInventory().sendUpdates();
			player.getEquipment().sendUpdates();
			return false;
		}
		return true;
	}

	private static GearLoadoutPreset createBlankFromPlayer(Player player, String name) {
		ArrayList<Item> inventory = new ArrayList<>(Collections.nCopies(GearLoadoutPreset.INVENTORY_SIZE, null));
		HashMap<Integer, Item> equipment = new HashMap<>();
		int[] fixedLevels = new int[GearLoadoutPreset.COMBAT_STATS.length];
		int[] currentLevels = new int[GearLoadoutPreset.COMBAT_STATS.length];
		copyPlayerStats(player, fixedLevels, currentLevels);
		return new GearLoadoutPreset(name, inventory, equipment, fixedLevels, currentLevels, VarPlayerRepository.MAGIC_BOOK.get(player));
	}

	private static void applyStats(Player player, GearLoadoutPreset preset) {
		for (StatType type : GearLoadoutPreset.COMBAT_STATS) {
			Stat stat = player.getStats().get(type);
			int fixedLevel = clamp(preset.getCombatLevel(type), type == StatType.Hitpoints ? 10 : 1, 99);
			int currentLevel = clamp(preset.getCombatCurrentLevel(type), 0, 126);
			stat.fixedLevel = fixedLevel;
			stat.currentLevel = currentLevel <= 0 ? fixedLevel : currentLevel;
			stat.experience = Stat.xpForLevel(fixedLevel);
			stat.updated = true;
		}
		player.getPrayer().deactivateAll();
		player.getCombat().updateLevel();
	}

	private static GearLoadoutPreset createFromPlayer(Player player, String name) {
		ArrayList<Item> inventory = new ArrayList<>(Collections.nCopies(GearLoadoutPreset.INVENTORY_SIZE, null));
		Item[] playerInventory = player.getInventory().getItems();
		for (int slot = 0; slot < Math.min(playerInventory.length, GearLoadoutPreset.INVENTORY_SIZE); slot++)
			inventory.set(slot, GearLoadoutPreset.copy(playerInventory[slot]));

		HashMap<Integer, Item> equipment = new HashMap<>();
		Item[] playerEquipment = player.getEquipment().getItems();
		for (int slot = 0; slot < Math.min(playerEquipment.length, GearLoadoutPreset.EQUIPMENT_SIZE); slot++)
			equipment.put(slot, GearLoadoutPreset.copy(playerEquipment[slot]));

		int[] fixedLevels = new int[GearLoadoutPreset.COMBAT_STATS.length];
		int[] currentLevels = new int[GearLoadoutPreset.COMBAT_STATS.length];
		copyPlayerStats(player, fixedLevels, currentLevels);
		return new GearLoadoutPreset(name, inventory, equipment, fixedLevels, currentLevels, VarPlayerRepository.MAGIC_BOOK.get(player));
	}

	private static void copyPlayerStats(Player player, int[] fixedLevels, int[] currentLevels) {
		for (int i = 0; i < GearLoadoutPreset.COMBAT_STATS.length; i++) {
			Stat stat = player.getStats().get(GearLoadoutPreset.COMBAT_STATS[i]);
			fixedLevels[i] = stat.fixedLevel;
			currentLevels[i] = stat.currentLevel;
		}
	}

	private static boolean removeFromBank(Player player, Item item) {
		int removed = player.getBank().remove(ObjType.unnotedId(item.getId()), item.getAmount(), item.copyOfAttributes());
		if (removed != item.getAmount()) {
			player.sendMessage("A required item was missing while loading your preset.");
			return false;
		}
		return true;
	}

	private static void addRequired(Map<String, Long> counts, Item item) {
		addItemCount(counts, item);
	}

	private static void addAvailable(Map<String, Long> counts, Item item) {
		addItemCount(counts, item);
	}

	private static void addItemCount(Map<String, Long> counts, Item item) {
		if (item == null || item.getAmount() <= 0)
			return;
		counts.merge(itemKey(item), (long) item.getAmount(), Long::sum);
	}

	private static String itemKey(Item item) {
		return ObjType.unnotedId(item.getId()) + ":" + new TreeMap<>(item.copyOfAttributes());
	}

	private static String cleanName(String name) {
		if (name == null)
			return null;
		name = name.trim();
		if (name.isEmpty())
			return null;
		if (name.length() > MAX_NAME_LENGTH)
			name = name.substring(0, MAX_NAME_LENGTH);
		return name;
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}
}
