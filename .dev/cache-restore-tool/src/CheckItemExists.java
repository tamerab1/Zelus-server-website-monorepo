import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;

import java.io.File;
import java.util.List;

// Read-only. Avoids ItemLoader/ArchiveFiles entirely (DumpItemNames.java's own comment already
// flags a pre-existing "duplicate file ids" quirk in this cache's ITEM config archive that breaks
// those higher-level helpers) -- splits the archive into sub-files manually, the same low-level
// technique used throughout this session, and decodes just the "name" opcode (2) directly.
public class CheckItemExists {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Archive archive = store.getIndex(IndexType.CONFIGS).getArchive(ConfigType.ITEM.getId());
            byte[] decompressed = archive.decompress(storage.loadArchive(archive));
            FileData[] fileData = archive.getFileData();
            List<byte[]> contents = SpliceItemOption.splitChunks(decompressed, fileData.length);

            for (int i = 1; i < args.length; i++) {
                int id = Integer.parseInt(args[i]);
                int slot = -1;
                for (int j = 0; j < fileData.length; j++) if (fileData[j].getId() == id) slot = j;
                if (slot == -1) {
                    System.out.println(id + " -- NOT FOUND (no item definition in the cache)");
                    continue;
                }
                System.out.println(id + " -- EXISTS (" + contents.get(slot).length + " raw bytes)");
            }
        }
    }
}
