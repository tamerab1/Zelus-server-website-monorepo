package io.ruin.model.content.equipmentpresets;

import io.ruin.cache.ObjType;
import io.ruin.model.entity.player.Player;
import io.ruin.model.entity.player.SecondaryGroup;
import io.ruin.model.item.Item;
import io.ruin.model.item.ItemContainerG;
import io.ruin.model.item.containers.Equipment;
import io.ruin.model.map.object.actions.impl.OccultAltar;
import io.ruin.model.skills.magic.SpellBook;
import io.ruin.model.var.VarPlayerRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Server-authoritative capture, validation, persistence repair and loading. */
public final class GearPresetHandler {

	public static final int INVENTORY_SIZE = 28;
	/** Sized for the 54 px preset buttons in the cache interface. */
	public static final int MAX_NAME_LENGTH = 10;
	/** Absolute safety ceiling used only by login-time compaction/repair -- the real, donor-tier
	 * gated cap a player can actually save up to is maxPresets(Player). */
	public static final int MAX_PRESETS = 100;

	/** 3 presets for everyone, +1 at Donator, +1 at Super Donator, +2 (slots 6 and 7) at Noble
	 * Donator and above. */
	public static int maxPresets(Player player) {
		var group = player.getSecondaryGroup();
		if (group.equalToOrGreaterThan(SecondaryGroup.NOBLE_DONATOR))
			return 7;
		if (group.equalToOrGreaterThan(SecondaryGroup.SUPER_DONATOR))
			return 5;
		if (group.equalToOrGreaterThan(SecondaryGroup.DONATOR))
			return 4;
		return 3;
	}

	public static final int[] EQUIPMENT_SLOTS = {
		Equipment.SLOT_HAT,
		Equipment.SLOT_CAPE,
		Equipment.SLOT_AMULET,
		Equipment.SLOT_WEAPON,
		Equipment.SLOT_CHEST,
		Equipment.SLOT_SHIELD,
		Equipment.SLOT_LEGS,
		Equipment.SLOT_HANDS,
		Equipment.SLOT_FEET,
		Equipment.SLOT_RING,
		Equipment.SLOT_AMMO,
		Equipment.SLOT_QUIVER
	};

	/** Stable equip order avoids weapon/shield conflicts while using Equipment.equip. */
	private static final int[] LOAD_EQUIPMENT_ORDER = {
		Equipment.SLOT_HAT,
		Equipment.SLOT_CAPE,
		Equipment.SLOT_AMULET,
		Equipment.SLOT_CHEST,
		Equipment.SLOT_LEGS,
		Equipment.SLOT_HANDS,
		Equipment.SLOT_FEET,
		Equipment.SLOT_RING,
		Equipment.SLOT_AMMO,
		Equipment.SLOT_QUIVER,
		Equipment.SLOT_WEAPON,
		Equipment.SLOT_SHIELD
	};

	private static final Set<Integer> VALID_EQUIPMENT_SLOTS = new HashSet<>();

	static {
		for (int slot : EQUIPMENT_SLOTS)
			VALID_EQUIPMENT_SLOTS.add(slot);
	}

	private GearPresetHandler() {
	}

	public static GearPreset capture(Player player, String name, boolean restoreStats,
									 boolean restoreSpecialAttack) {
		return new GearPreset(name, captureInventory(player), captureEquipment(player), activeSpellBook(player),
			restoreStats, restoreSpecialAttack);
	}

	public static void update(Player player, GearPreset preset, boolean restoreStats,
							  boolean restoreSpecialAttack) {
		preset.replaceContents(captureInventory(player), captureEquipment(player), activeSpellBook(player),
			restoreStats, restoreSpecialAttack);
	}

	public static List<Item> captureInventory(Player player) {
		List<Item> inventory = new ArrayList<>(INVENTORY_SIZE);
		Item[] current = player.getInventory().getItems();
		for (int slot = 0; slot < INVENTORY_SIZE; slot++) {
			Item item = slot < current.length ? current[slot] : null;
			inventory.add(item == null ? null : item.copy());
		}
		return inventory;
	}

	public static Map<Integer, Item> captureEquipment(Player player) {
		Map<Integer, Item> equipment = new LinkedHashMap<>();
		for (int slot : EQUIPMENT_SLOTS) {
			Item item = player.getEquipment().getSafe(slot);
			if (item != null)
				equipment.put(slot, item.copy());
		}
		return equipment;
	}

	public static int activeSpellBook(Player player) {
		int book = VarPlayerRepository.MAGIC_BOOK.get(player);
		return book < 0 || book >= SpellBook.VALUES.length ? SpellBook.MODERN.ordinal() : book;
	}

	/**
	 * Compacts legacy data during login/open: null/placeholder records are removed,
	 * actual record order and names are retained, and malformed item entries are
	 * discarded rather than trusted during a later load.
	 */
	public static void sanitizePresets(Player player) {
		player.gearPresets = compactPresets(player.gearPresets, activeSpellBook(player));
	}

	/** Package-visible so ordered-list migration can be regression tested without a live player. */
	static List<GearPreset> compactPresets(List<GearPreset> source, int legacyBook) {
		List<GearPreset> compact = new ArrayList<>();
		Set<String> identities = new HashSet<>();
		if (source != null) {
			for (GearPreset preset : source) {
				if (preset == null || !preset.sanitize(legacyBook))
					continue;
				while (!identities.add(preset.getId()))
					preset.regenerateId();
				compact.add(preset);
				if (compact.size() >= MAX_PRESETS)
					break;
			}
		}
		return compact;
	}

	public static String sanitizeName(String input) {
		if (input == null)
			return null;
		input = input.trim();
		StringBuilder clean = new StringBuilder();
		for (int offset = 0; offset < input.length() && clean.length() < MAX_NAME_LENGTH; offset++) {
			char character = input.charAt(offset);
			boolean safe = character >= 'A' && character <= 'Z'
				|| character >= 'a' && character <= 'z'
				|| character >= '0' && character <= '9'
				|| character == ' ' || character == '-' || character == '_' || character == '\'';
			if (safe)
				clean.append(character);
		}
		String name = clean.toString().trim();
		return name.isBlank() || name.equalsIgnoreCase("empty") ? null : name;
	}

	static List<Item> sanitizeInventory(List<Item> source) {
		List<Item> sanitized = new ArrayList<>(INVENTORY_SIZE);
		for (int slot = 0; slot < INVENTORY_SIZE; slot++) {
			Item item = source != null && slot < source.size() ? source.get(slot) : null;
			sanitized.add(isValidStoredItem(item) ? item.copy() : null);
		}
		return sanitized;
	}

	static HashMap<Integer, Item> sanitizeEquipment(Map<Integer, Item> source) {
		HashMap<Integer, Item> sanitized = new HashMap<>();
		if (source == null)
			return sanitized;
		for (Map.Entry<Integer, Item> entry : source.entrySet()) {
			Integer slot = entry.getKey();
			Item item = entry.getValue();
			if (slot == null || !VALID_EQUIPMENT_SLOTS.contains(slot) || !isValidStoredItem(item))
				continue;
			ObjType def = item.getDef();
			if (def.equipSlot != slot)
				continue;
			sanitized.put(slot, item.copy());
		}
		return sanitized;
	}

	private static boolean isValidStoredItem(Item item) {
		if (item == null || item.getId() < 0 || item.getAmount() <= 0)
			return false;
		ObjType def = ObjType.get(item.getId());
		return def != null && !def.isPlaceholder() && (def.stackable || item.getAmount() == 1);
	}

	public static boolean load(Player player, GearPreset preset) {
		if (preset == null || !player.gearPresets.contains(preset))
			return false;
		if (!validatePreset(preset)) {
			player.sendMessage("That preset contains invalid item data and cannot be loaded.");
			return false;
		}
		if (!hasRequiredItems(player, preset)) {
			player.sendMessage("You do not have all of the items required by this preset.");
			return false;
		}

		/* Forced deposits are safe here because the interface itself validates bank access. */
		player.getBank().deposit(player.getInventory(), false, true);
		player.getBank().deposit(player.getEquipment(), false, true);
		if (!player.getInventory().isEmpty() || !player.getEquipment().isEmpty()) {
			player.sendMessage("Your bank does not have enough room to load this preset safely.");
			return false;
		}

		for (int slot : LOAD_EQUIPMENT_ORDER) {
			Item requested = preset.getEquipment().get(slot);
			if (requested == null)
				continue;
			if (!withdrawAndEquip(player, slot, requested)) {
				player.sendMessage("The preset stopped because " + requested.getDef().descriptiveName
					+ " could not be equipped. No items were duplicated.");
				return false;
			}
		}

		List<Item> inventory = preset.getInventory();
		for (int slot = 0; slot < INVENTORY_SIZE; slot++) {
			Item requested = slot < inventory.size() ? inventory.get(slot) : null;
			if (requested == null)
				continue;
			if (!withdrawToSlot(player, slot, requested)) {
				player.sendMessage("The preset stopped because an inventory item could not be withdrawn."
					+ " No items were duplicated.");
				return false;
			}
		}

		OccultAltar.switchBook(player, SpellBook.VALUES[preset.getSpellBook()], false);
		if (preset.isRestoreStats())
			player.getStats().restore(true);
		if (preset.isRestoreSpecialAttack())
			player.getCombat().restoreSpecial(100);
		player.getEquipment().sendUpdates();
		player.getInventory().sendUpdates();
		player.getBank().sendUpdates();
		return true;
	}

	private static boolean validatePreset(GearPreset preset) {
		if (preset.getSpellBook() < 0 || preset.getSpellBook() >= SpellBook.VALUES.length)
			return false;
		if (preset.getInventory() == null || preset.getInventory().size() > INVENTORY_SIZE)
			return false;
		for (Item item : preset.getInventory()) {
			if (item != null && !isValidStoredItem(item))
				return false;
		}
		if (preset.getEquipment() == null)
			return false;
		for (Map.Entry<Integer, Item> entry : preset.getEquipment().entrySet()) {
			if (!VALID_EQUIPMENT_SLOTS.contains(entry.getKey()) || !isValidStoredItem(entry.getValue()))
				return false;
			if (entry.getValue().getDef().equipSlot != entry.getKey())
				return false;
		}
		return true;
	}

	private static boolean hasRequiredItems(Player player, GearPreset preset) {
		Map<ItemKey, Long> required = new HashMap<>();
		for (Item item : preset.getInventory())
			addCount(required, item);
		for (Item item : preset.getEquipment().values())
			addCount(required, item);

		Map<ItemKey, Long> available = new HashMap<>();
		addContainerCounts(available, player.getBank());
		addContainerCounts(available, player.getInventory());
		addContainerCounts(available, player.getEquipment());
		for (Map.Entry<ItemKey, Long> entry : required.entrySet()) {
			if (available.getOrDefault(entry.getKey(), 0L) < entry.getValue())
				return false;
		}
		return true;
	}

	private static void addContainerCounts(Map<ItemKey, Long> counts, ItemContainerG<? extends Item> container) {
		for (Item item : container.getItems())
			addCount(counts, item);
	}

	private static void addCount(Map<ItemKey, Long> counts, Item item) {
		if (item == null || item.getAmount() <= 0 || item.getDef() == null || item.getDef().isPlaceholder())
			return;
		ItemKey key = ItemKey.of(item);
		long current = counts.getOrDefault(key, 0L);
		counts.put(key, Math.min(Integer.MAX_VALUE, current + item.getAmount()));
	}

	private static boolean withdrawAndEquip(Player player, int slot, Item requested) {
		if (!withdrawToInventory(player, requested))
			return false;
		Item inventoryItem = player.getInventory().findItem(requested.getId(), requested.copyOfAttributes());
		if (inventoryItem == null)
			return false;
		player.getEquipment().equip(inventoryItem);
		Item equipped = player.getEquipment().getSafe(slot);
		return equipped != null && equipped.getId() == requested.getId()
			&& equipped.getAttributeHash() == requested.getAttributeHash()
			&& equipped.getAmount() == requested.getAmount();
	}

	private static boolean withdrawToInventory(Player player, Item requested) {
		int bankId = ObjType.unnotedId(requested.getId());
		Map<String, String> attributes = requested.copyOfAttributes();
		int removed = player.getBank().remove(bankId, requested.getAmount(), attributes);
		if (removed != requested.getAmount()) {
			if (removed > 0)
				player.getBank().add(bankId, removed, attributes);
			return false;
		}
		int added = player.getInventory().add(requested.getId(), requested.getAmount(), attributes);
		if (added != requested.getAmount()) {
			if (added > 0)
				player.getInventory().remove(requested.getId(), added, attributes);
			player.getBank().add(bankId, removed, attributes);
			return false;
		}
		return true;
	}

	private static boolean withdrawToSlot(Player player, int slot, Item requested) {
		if (player.getInventory().getSafe(slot) != null)
			return false;
		int bankId = ObjType.unnotedId(requested.getId());
		Map<String, String> attributes = requested.copyOfAttributes();
		int removed = player.getBank().remove(bankId, requested.getAmount(), attributes);
		if (removed != requested.getAmount()) {
			if (removed > 0)
				player.getBank().add(bankId, removed, attributes);
			return false;
		}
		player.getInventory().set(slot, new Item(requested.getId(), requested.getAmount(), attributes));
		return true;
	}

	private static final class ItemKey {
		private final int id;
		private final int attributeHash;

		private ItemKey(int id, int attributeHash) {
			this.id = id;
			this.attributeHash = attributeHash;
		}

		static ItemKey of(Item item) {
			return new ItemKey(ObjType.unnotedId(item.getId()), item.getAttributeHash());
		}

		@Override
		public boolean equals(Object object) {
			if (this == object)
				return true;
			if (!(object instanceof ItemKey))
				return false;
			ItemKey other = (ItemKey) object;
			return id == other.id && attributeHash == other.attributeHash;
		}

		@Override
		public int hashCode() {
			return 31 * id + attributeHash;
		}
	}
}
