import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.ItemDefinition;
import net.runelite.cache.definitions.loaders.ItemLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;

import java.io.File;
import java.lang.reflect.Field;
import java.util.List;

// Read-only. Full decode of the 5 new Mad Angel unique items from the SOURCE cache -- bypasses
// ArchiveFiles/ItemManager entirely (this cache's own ITEM config archive has a pre-existing
// "duplicate file ids" quirk that crashes those helpers, per DumpItemNames.java's own comment;
// CheckItemExists.java already proved the manual splitChunks approach avoids it). ItemLoader.load()
// itself is fine once handed already-split bytes directly -- this reflects over every int/String
// field on the returned ItemDefinition so nothing is missed, since the exact field names in this
// pinned cache-library version aren't guessed.
public class DecodeMadAngelItems {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        int[] ids = {34032, 34033, 34027, 34030, 34042};

        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Archive archive = store.getIndex(IndexType.CONFIGS).getArchive(ConfigType.ITEM.getId());
            byte[] decompressed = archive.decompress(storage.loadArchive(archive));
            FileData[] fileData = archive.getFileData();
            List<byte[]> contents = SpliceItemOption.splitChunks(decompressed, fileData.length);

            ItemLoader loader = new ItemLoader();
            for (int id : ids) {
                int slot = -1;
                for (int j = 0; j < fileData.length; j++) if (fileData[j].getId() == id) slot = j;
                if (slot == -1) {
                    System.out.println(id + " -- NOT FOUND");
                    continue;
                }
                byte[] raw = contents.get(slot);
                ItemDefinition def;
                try {
                    def = loader.load(id, raw);
                } catch (Exception e) {
                    System.out.println(id + " -- LOAD FAILED: " + e + " (raw " + raw.length + " bytes)");
                    continue;
                }
                System.out.println("=== " + id + " (" + raw.length + " raw bytes) ===");
                for (Field f : ItemDefinition.class.getDeclaredFields()) {
                    f.setAccessible(true);
                    Object val = f.get(def);
                    if (val == null) continue;
                    if (val instanceof int[]) {
                        int[] arr = (int[]) val;
                        boolean allZeroOrNeg = true;
                        for (int v : arr) if (v > 0) allZeroOrNeg = false;
                        if (allZeroOrNeg) continue;
                        System.out.println("  " + f.getName() + " = " + java.util.Arrays.toString(arr));
                    } else if (val instanceof Integer) {
                        int v = (Integer) val;
                        if (v == 0 || v == -1) continue;
                        System.out.println("  " + f.getName() + " = " + v);
                    } else if (val instanceof String) {
                        if (((String) val).isEmpty() || val.equals("null")) continue;
                        System.out.println("  " + f.getName() + " = \"" + val + "\"");
                    } else if (val instanceof Boolean) {
                        if (!((Boolean) val)) continue;
                        System.out.println("  " + f.getName() + " = " + val);
                    }
                }
            }
        }
    }
}
