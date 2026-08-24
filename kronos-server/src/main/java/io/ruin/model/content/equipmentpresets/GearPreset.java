package io.ruin.model.content.equipmentpresets;

import io.ruin.model.item.Item;
import io.ruin.model.skills.magic.SpellBook;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Persistent, server-owned equipment preset data.
 *
 * <p>The original JSON field names are deliberately retained so existing player
 * saves continue to deserialize without a migration.</p>
 */
public class GearPreset {

	public static final int CURRENT_SCHEMA_VERSION = 2;

	private int schemaVersion;
	private String id;
	private List<Item> inventory = new ArrayList<>();
	private HashMap<Integer, Item> equipment = new HashMap<>();
	private String presetName;
	/** SpellBook.MODERN.ordinal() -- a literal, not a SpellBook reference, so constructing/
	 * deserializing a GearPreset never forces SpellBook's static init (which eagerly builds every
	 * spell, including ones that validate against live item-cache data unavailable outside a full
	 * server boot -- e.g. in unit tests). */
	private int spellBook = 0;
	private boolean restoreStats = true;
	private boolean restoreSpecialAttack = true;

	/** Required by the player attribute serializer. */
	public GearPreset() {
		id = UUID.randomUUID().toString();
	}

	public GearPreset(String presetName, List<Item> inventory, Map<Integer, Item> equipment,
					  int spellBook, boolean restoreStats, boolean restoreSpecialAttack) {
		this();
		this.schemaVersion = CURRENT_SCHEMA_VERSION;
		this.presetName = presetName;
		this.inventory = copyInventory(inventory);
		this.equipment = copyEquipment(equipment);
		this.spellBook = spellBook;
		this.restoreStats = restoreStats;
		this.restoreSpecialAttack = restoreSpecialAttack;
	}

	public String getId() {
		return id;
	}

	void regenerateId() {
		id = UUID.randomUUID().toString();
	}

	public String getPresetName() {
		return presetName;
	}

	public List<Item> getInventory() {
		return inventory;
	}

	public Map<Integer, Item> getEquipment() {
		return equipment;
	}

	public int getSpellBook() {
		return spellBook;
	}

	public boolean isRestoreStats() {
		return restoreStats;
	}

	public boolean isRestoreSpecialAttack() {
		return restoreSpecialAttack;
	}

	public void replaceContents(List<Item> inventory, Map<Integer, Item> equipment, int spellBook,
							boolean restoreStats, boolean restoreSpecialAttack) {
		this.inventory = copyInventory(inventory);
		this.equipment = copyEquipment(equipment);
		this.spellBook = spellBook;
		this.restoreStats = restoreStats;
		this.restoreSpecialAttack = restoreSpecialAttack;
		this.schemaVersion = CURRENT_SCHEMA_VERSION;
	}

	/**
	 * Repairs legacy/malformed data in place. A false result means the record is
	 * only a hole/placeholder and should be removed from the ordered collection.
	 */
	boolean sanitize(int legacySpellBook) {
		if (presetName == null)
			return false;
		presetName = GearPresetHandler.sanitizeName(presetName);
		if (presetName == null || presetName.equalsIgnoreCase("empty"))
			return false;

		if (id == null || id.isBlank())
			id = UUID.randomUUID().toString();
		if (inventory == null)
			inventory = new ArrayList<>();
		if (equipment == null)
			equipment = new HashMap<>();
		if (schemaVersion < CURRENT_SCHEMA_VERSION) {
			spellBook = legacySpellBook;
			restoreStats = true;
			restoreSpecialAttack = true;
		}
		if (spellBook < 0 || spellBook >= SpellBook.VALUES.length)
			spellBook = SpellBook.MODERN.ordinal();

		inventory = GearPresetHandler.sanitizeInventory(inventory);
		equipment = GearPresetHandler.sanitizeEquipment(equipment);
		schemaVersion = CURRENT_SCHEMA_VERSION;
		return true;
	}

	private static List<Item> copyInventory(List<Item> source) {
		List<Item> copy = new ArrayList<>();
		if (source == null)
			return copy;
		for (Item item : source)
			copy.add(item == null ? null : item.copy());
		return copy;
	}

	private static HashMap<Integer, Item> copyEquipment(Map<Integer, Item> source) {
		HashMap<Integer, Item> copy = new HashMap<>();
		if (source == null)
			return copy;
		for (Map.Entry<Integer, Item> entry : source.entrySet())
			copy.put(entry.getKey(), entry.getValue() == null ? null : entry.getValue().copy());
		return copy;
	}
}
