package economy.protection.classification;

import io.ruin.api.utils.PackageLoader;
import io.ruin.cache.ObjType;
import io.ruin.data.DataFile;
import io.ruin.data.impl.economy.ItemClassificationLoader;
import io.ruin.data.impl.items.item_info;
import io.ruin.test.ServerTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unlike {@link ItemClassificationTest}, which only exercises the in-memory
 * registry, these tests run the loader against a real booted ObjType cache.
 *
 * Regression coverage for the bug where SPAWNABLE items stayed tradeable in
 * production: item_info.java unconditionally reapplies vanilla `tradeable`
 * values from its own JSON, and — since it shares ItemClassificationLoader's
 * same default DataFile priority — could run either before or after it
 * depending on HashSet iteration order. The fix moved the ObjType override
 * out of the DataFile load phase entirely and into
 * ItemClassification.applyObjTypeOverrides(), called from Module#start()
 * (guaranteed to run after every DataFile has finished). The second test
 * below reproduces the worst-case order (item_info running last) and
 * confirms the override still holds.
 */
public class ItemClassificationLoaderRuntimeTest {

	@BeforeAll
	static void setup() throws Exception {
		ServerTest.start();
	}

	@Test
	void spawnableItemFromStarterJsonIsClassifiedAndOverrideApplies() throws Exception {
		DataFile.load(new ItemClassificationLoader());
		assertTrue(ItemClassification.isSpawnable(385), "385 should be registered as SPAWNABLE from starter.json");

		ItemClassification.applyObjTypeOverrides();
		assertFalse(ObjType.get(385).tradeable, "385 should have been flipped non-tradeable by applyObjTypeOverrides()");
	}

	@Test
	void overrideSurvivesEvenWhenItemInfoRunsAfterTheClassificationLoader() throws Exception {
		// Worst-case DataFile ordering: item_info (which unconditionally
		// reapplies vanilla tradeable=true for 385) runs LAST, same as the
		// real production race that caused the original bug.
		// item_info.fromJson() unloads shield_types.MAP as a one-shot side
		// effect, so re-running item_info in this same JVM (ServerTest.start()
		// already ran it once) needs shield_types reloaded first.
		DataFile.load(new ItemClassificationLoader());
		DataFile.load(new io.ruin.data.impl.items.shield_types());
		DataFile.load(new item_info());
		assertTrue(ObjType.get(385).tradeable, "sanity check: item_info alone should leave 385 tradeable, matching vanilla data");

		// Module#start() applies the override after all DataFile loading, so it must still win.
		ItemClassification.applyObjTypeOverrides();
		assertFalse(ObjType.get(385).tradeable, "SPAWNABLE override must win even if item_info loaded after the classification loader");
	}

	@Test
	void notedCounterpartOfASpawnableItemIsAlsoLockedDown() throws Exception {
		// ::spawn hands out a noted stack when amount > 1 (SpawnCommandHook),
		// so the noted id must be just as untradeable as the unnoted one —
		// otherwise noting is a fresh GE loophole around the SPAWNABLE lock.
		DataFile.load(new ItemClassificationLoader());
		int notedId = ObjType.notedId(385);
		assertNotEquals(385, notedId, "sanity check: 385 (shark) should have a noted counterpart in this cache");

		ItemClassification.applyObjTypeOverrides();
		assertFalse(ObjType.get(notedId).tradeable, "the noted counterpart of a SPAWNABLE item must also be non-tradeable");
	}

	@Test
	void classpathScanOfIoRuinDataImplFindsTheEconomySubpackageLoader() {
		var found = PackageLoader.loadExtending(DataFile.class, "io.ruin.data.impl");
		var names = found.stream().map(Class::getName).toList();
		assertTrue(names.contains("io.ruin.data.impl.economy.ItemClassificationLoader"),
				"ItemClassificationLoader (in subpackage io.ruin.data.impl.economy) should be discovered by the io.ruin.data.impl scan");
	}
}
