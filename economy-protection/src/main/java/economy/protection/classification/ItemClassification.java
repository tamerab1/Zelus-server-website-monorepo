package economy.protection.classification;

import io.ruin.cache.ObjType;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Static registry mapping item id -> its economy classification.
 * Populated at boot by {@code io.ruin.data.impl.economy.ItemClassificationLoader}
 * (a {@code DataFile}, loaded after the item cache and before any module's start()).
 */
@Slf4j
public final class ItemClassification {

	private static final Map<Integer, ClassifiedItem> classified = new HashMap<>();

	private ItemClassification() {
	}

	public static void register(ClassifiedItem item) {
		classified.put(item.id, item);
	}

	public static void clear() {
		classified.clear();
	}

	public static ClassifiedItem get(int id) {
		return classified.get(id);
	}

	public static boolean isSpawnable(int id) {
		ClassifiedItem item = classified.get(id);
		return item != null && item.has(ItemFlag.SPAWNABLE);
	}

	public static boolean isHighValue(int id) {
		ClassifiedItem item = classified.get(id);
		return item != null && item.has(ItemFlag.HIGH_VALUE);
	}

	public static boolean isDissolvable(int id) {
		ClassifiedItem item = classified.get(id);
		return item != null && item.has(ItemFlag.DISSOLVABLE);
	}

	/** True if this item must never touch the ground (spawnable or explicitly dissolvable). */
	public static boolean vanishesOnGround(int id) {
		return isSpawnable(id) || isDissolvable(id);
	}

	public static long pvpValueOf(int id) {
		ClassifiedItem item = classified.get(id);
		return item == null ? 0 : item.pvpValue;
	}

	/** 0 = unlimited. */
	public static int dailyCapOf(int id) {
		ClassifiedItem item = classified.get(id);
		return item == null ? 0 : item.dailySpawnCap;
	}

	/** All SPAWNABLE items, sorted by id — used to build the ::spawn browse list. */
	public static List<ClassifiedItem> spawnableItems() {
		List<ClassifiedItem> list = new ArrayList<>();
		for (ClassifiedItem item : classified.values()) {
			if (item.has(ItemFlag.SPAWNABLE)) {
				list.add(item);
			}
		}
		list.sort(Comparator.comparingInt(item -> item.id));
		return list;
	}

	/**
	 * Forces shop price/alch value to 0 and blocks trading for every SPAWNABLE item — and its
	 * noted counterpart, since {@code ::spawn} hands out a noted stack for amounts &gt; 1 (see
	 * {@code economy.protection.spawn.SpawnCommandHook}); without this, that noted id would keep
	 * its untouched vanilla ObjType and reopen the exact GE loophole this override exists to close.
	 * Must run after ALL {@code DataFile} loading has finished — see the ordering note on
	 * {@code io.ruin.data.impl.economy.ItemClassificationLoader}. Called from
	 * {@code economy.protection.module.Module#start()}.
	 */
	public static void applyObjTypeOverrides() {
		for (ClassifiedItem item : classified.values()) {
			if (!item.has(ItemFlag.SPAWNABLE)) {
				continue;
			}
			applyOverride(item.id);
			int notedId = ObjType.notedId(item.id);
			if (notedId != item.id) {
				applyOverride(notedId);
			}
		}
	}

	private static void applyOverride(int id) {
		ObjType def = ObjType.get(id);
		if (def == null) {
			log.warn("SPAWNABLE item {} has no ObjType definition — skipping ObjType overrides", id);
			return;
		}
		def.tradeable = false;
		def.value = 0;
		def.lowAlchValue = 0;
		def.highAlchValue = 0;
	}
}
