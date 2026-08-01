package io.ruin.model.content.gearloadouts;

import io.ruin.model.item.Item;
import io.ruin.model.item.containers.Equipment;
import io.ruin.model.skills.magic.SpellBook;
import io.ruin.model.stat.StatType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GearLoadoutPreset {
	public static final int INVENTORY_SIZE = 28;
	public static final int EQUIPMENT_SIZE = Equipment.SLOT_QUIVER + 1;

	public static final StatType[] COMBAT_STATS = {
			StatType.Attack,
			StatType.Strength,
			StatType.Defence,
			StatType.Hitpoints,
			StatType.Ranged,
			StatType.Prayer,
			StatType.Magic
	};

	public List<Item> inventory = new ArrayList<>(Collections.nCopies(INVENTORY_SIZE, null));
	public HashMap<Integer, Item> equipment = new HashMap<>();
	public String presetName;
	public int[] combatLevels = new int[COMBAT_STATS.length];
	public int[] combatCurrentLevels = new int[COMBAT_STATS.length];
	public int spellBook = SpellBook.MODERN.ordinal();

	public GearLoadoutPreset() {
		normalize();
	}

	public GearLoadoutPreset(String presetName, List<Item> inventory, HashMap<Integer, Item> equipment) {
		this(presetName, inventory, equipment, null, SpellBook.MODERN.ordinal());
	}

	public GearLoadoutPreset(String presetName, List<Item> inventory, HashMap<Integer, Item> equipment, int[] combatLevels, int spellBook) {
		this(presetName, inventory, equipment, combatLevels, combatLevels, spellBook);
	}

	public GearLoadoutPreset(String presetName, List<Item> inventory, HashMap<Integer, Item> equipment, int[] combatLevels, int[] combatCurrentLevels, int spellBook) {
		this.presetName = presetName;
		this.inventory = copyInventory(inventory);
		this.equipment = copyEquipment(equipment);
		this.combatLevels = copyCombatLevels(combatLevels);
		this.combatCurrentLevels = copyCombatLevels(combatCurrentLevels);
		this.spellBook = validSpellBook(spellBook);
		normalize();
	}

	public void normalize() {
		if (inventory == null)
			inventory = new ArrayList<>();
		while (inventory.size() < INVENTORY_SIZE)
			inventory.add(null);
		if (inventory.size() > INVENTORY_SIZE)
			inventory = new ArrayList<>(inventory.subList(0, INVENTORY_SIZE));
		for (int slot = 0; slot < inventory.size(); slot++)
			inventory.set(slot, copy(inventory.get(slot)));
		if (equipment == null)
			equipment = new HashMap<>();
		HashMap<Integer, Item> normalizedEquipment = new HashMap<>();
		for (Map.Entry<Integer, Item> entry : equipment.entrySet()) {
			int slot = entry.getKey();
			if (slot < 0 || slot >= EQUIPMENT_SIZE)
				continue;
			Item item = copy(entry.getValue());
			if (item == null)
				continue;
			normalizedEquipment.put(slot, item);
		}
		equipment = normalizedEquipment;
		combatLevels = copyCombatLevels(combatLevels);
		combatCurrentLevels = copyCombatLevels(combatCurrentLevels);
		spellBook = validSpellBook(spellBook);
	}

	public Item getInventoryItem(int slot) {
		normalize();
		if (slot < 0 || slot >= INVENTORY_SIZE)
			return null;
		return inventory.get(slot);
	}

	public Item[] getInventoryItems() {
		normalize();
		Item[] items = new Item[INVENTORY_SIZE];
		for (int slot = 0; slot < INVENTORY_SIZE; slot++)
			items[slot] = copy(inventory.get(slot));
		return items;
	}

	public Item getEquipmentItem(int slot) {
		normalize();
		if (slot < 0 || slot >= EQUIPMENT_SIZE)
			return null;
		return equipment.get(slot);
	}

	public Item[] getEquipmentItems() {
		normalize();
		Item[] items = new Item[EQUIPMENT_SIZE];
		for (int slot = 0; slot < EQUIPMENT_SIZE; slot++)
			items[slot] = copy(equipment.get(slot));
		return items;
	}

	public int getCombatLevel(StatType type) {
		normalize();
		for (int i = 0; i < COMBAT_STATS.length; i++) {
			if (COMBAT_STATS[i] == type)
				return combatLevels[i];
		}
		return 1;
	}

	public int getCombatCurrentLevel(StatType type) {
		normalize();
		for (int i = 0; i < COMBAT_STATS.length; i++) {
			if (COMBAT_STATS[i] == type)
				return combatCurrentLevels[i] <= 0 ? combatLevels[i] : combatCurrentLevels[i];
		}
		return 1;
	}

	public SpellBook getSpellBook() {
		return SpellBook.VALUES[validSpellBook(spellBook)];
	}

	public void setSpellBook(SpellBook spellBook) {
		this.spellBook = spellBook == null ? SpellBook.MODERN.ordinal() : spellBook.ordinal();
	}

	public void setCombatLevel(StatType type, int fixedLevel, int currentLevel) {
		normalize();
		for (int i = 0; i < COMBAT_STATS.length; i++) {
			if (COMBAT_STATS[i] != type)
				continue;
			combatLevels[i] = fixedLevel;
			combatCurrentLevels[i] = currentLevel;
			return;
		}
	}

	private static List<Item> copyInventory(List<Item> inventory) {
		List<Item> copy = new ArrayList<>(Collections.nCopies(INVENTORY_SIZE, null));
		if (inventory == null)
			return copy;
		for (int slot = 0; slot < Math.min(inventory.size(), INVENTORY_SIZE); slot++)
			copy.set(slot, copy(inventory.get(slot)));
		return copy;
	}

	private static HashMap<Integer, Item> copyEquipment(HashMap<Integer, Item> equipment) {
		HashMap<Integer, Item> copy = new HashMap<>();
		if (equipment == null)
			return copy;
		for (Map.Entry<Integer, Item> entry : equipment.entrySet()) {
			int slot = entry.getKey();
			if (slot < 0 || slot >= EQUIPMENT_SIZE)
				continue;
			Item item = copy(entry.getValue());
			if (item != null)
				copy.put(slot, item);
		}
		return copy;
	}

	private static int[] copyCombatLevels(int[] combatLevels) {
		int[] copy = new int[COMBAT_STATS.length];
		if (combatLevels == null)
			return copy;
		System.arraycopy(combatLevels, 0, copy, 0, Math.min(combatLevels.length, copy.length));
		return copy;
	}

	private static int validSpellBook(int spellBook) {
		if (spellBook < 0 || spellBook >= SpellBook.VALUES.length)
			return SpellBook.MODERN.ordinal();
		return spellBook;
	}

	public static Item copy(Item item) {
		return item == null ? null : item.copy();
	}
}
