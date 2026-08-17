import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.ItemDefinition;
import net.runelite.cache.definitions.loaders.ItemLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;
import net.runelite.cache.io.InputStream;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

// Read-only lookup: prints name/examine for specific item ids, and optionally scans ALL item
// names for a substring match. Reuses the same List-backed positional chunk-splitter as
// SpliceItemOption (avoids the archive's known duplicate-file-id Map-loading crash).
//
// Usage:
//   lookup <cachePath> <id1,id2,...>          -- print names for specific ids
//   scan   <cachePath> <substring>            -- case-insensitive scan of every item name
public class LookupItemNames {
    public static void main(String[] args) throws Exception {
        String mode = args[0];
        String cachePath = args[1];

        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Index index = store.getIndex(IndexType.CONFIGS);
            Archive archive = index.getArchive(ConfigType.ITEM.getId());

            byte[] compressed = storage.loadArchive(archive);
            byte[] decompressed = archive.decompress(compressed);
            FileData[] fileData = archive.getFileData();
            List<byte[]> fileContents = SpliceItemOption.splitChunks(decompressed, fileData.length);

            ItemLoader loader = new ItemLoader();

            if (mode.equals("lookup")) {
                String[] idsStr = args[2].split(",");
                for (String idStr : idsStr) {
                    int targetId = Integer.parseInt(idStr.trim());
                    boolean found = false;
                    for (int i = 0; i < fileData.length; i++) {
                        if (fileData[i].getId() == targetId) {
                            found = true;
                            try {
                                ItemDefinition def = loader.load(targetId, fileContents.get(i));
                                System.out.println("id=" + targetId + " slot=" + i + " name=\"" + def.name
                                        + "\" examine=\"" + def.examine + "\" tradeable=" + def.isTradeable
                                        + " cost=" + def.cost + " notedID=" + def.notedID + " notedTemplate=" + def.notedTemplate
                                        + " groundOptions=" + java.util.Arrays.toString(def.options)
                                        + " inventoryOptions=" + java.util.Arrays.toString(def.interfaceOptions));
                            } catch (Exception e) {
                                System.out.println("id=" + targetId + " slot=" + i + " -- FAILED TO DECODE: " + e);
                            }
                        }
                    }
                    if (!found) {
                        System.out.println("id=" + targetId + " -- NOT FOUND in archive (no file slot with this id)");
                    }
                }
            } else if (mode.equals("scan")) {
                String needle = args[2].toLowerCase();
                int matches = 0;
                for (int i = 0; i < fileData.length; i++) {
                    int id = fileData[i].getId();
                    try {
                        ItemDefinition def = loader.load(id, fileContents.get(i));
                        if (def.name != null && def.name.toLowerCase().contains(needle)) {
                            System.out.println("id=" + id + " slot=" + i + " name=\"" + def.name + "\"");
                            matches++;
                        }
                    } catch (Exception e) {
                        // skip broken/duplicate slots, this archive has ~828 known pre-existing ones
                    }
                }
                System.out.println("Scan complete. " + matches + " match(es) for \"" + needle + "\".");
            }
        }
    }
}
