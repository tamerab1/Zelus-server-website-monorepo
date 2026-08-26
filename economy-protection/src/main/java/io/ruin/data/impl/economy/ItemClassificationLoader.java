package io.ruin.data.impl.economy;

import com.google.gson.annotations.Expose;
import economy.protection.classification.ClassifiedItem;
import economy.protection.classification.ItemClassification;
import economy.protection.classification.ItemFlag;
import io.ruin.api.utils.JsonUtils;
import io.ruin.cache.ObjType;
import io.ruin.data.DataFile;
import lombok.extern.slf4j.Slf4j;

import java.util.EnumSet;
import java.util.List;

/**
 * Loads the semi-spawn/economy item whitelist from {@code economy/item_classification/*.json}.
 * Lives under {@code io.ruin.data.impl} (rather than the {@code economy.protection} package)
 * because {@link DataFile#loadUnpacked()} only scans that exact package for subclasses.
 *
 * Only populates the in-memory {@link ItemClassification} registry — it deliberately does NOT
 * mutate {@link ObjType} here. Every {@code DataFile} that doesn't override {@code priority()}
 * (this one included) defaults to {@code Integer.MAX_VALUE}, so relative execution order among
 * same-priority loaders is effectively arbitrary (HashSet iteration order, not insertion order).
 * {@code io.ruin.data.impl.items.item_info} is one such loader and unconditionally overwrites
 * {@code ObjType.tradeable} from its own vanilla data — if it happened to run after this one, it
 * would silently clobber the SPAWNABLE override. The {@code ObjType} side effects are applied
 * from {@code economy.protection.module.Module#start()} instead, which is guaranteed to run only
 * after every {@code DataFile} (including {@code item_info}) has finished loading.
 */
@Slf4j
public class ItemClassificationLoader extends DataFile {

	@Override
	public String path() {
		return "economy/item_classification/*.json";
	}

	@Override
	public Object fromJson(String fileName, String json) {
		List<Entry> entries = JsonUtils.fromJson(json, List.class, Entry.class);
		entries.forEach(this::apply);
		return entries;
	}

	private void apply(Entry entry) {
		EnumSet<ItemFlag> flags = EnumSet.noneOf(ItemFlag.class);
		if (entry.flags != null) {
			for (String flag : entry.flags) {
				try {
					flags.add(ItemFlag.valueOf(flag.trim().toUpperCase()));
				} catch (IllegalArgumentException e) {
					log.warn("Unknown ItemFlag '{}' for item {}", flag, entry.id);
				}
			}
		}

		ItemClassification.register(new ClassifiedItem(entry.id, flags, entry.pvpValue, entry.dailySpawnCap));
	}

	private static final class Entry {
		@Expose
		public int id;
		@Expose
		public List<String> flags;
		@Expose
		public long pvpValue;
		@Expose
		public int dailySpawnCap;
	}
}
