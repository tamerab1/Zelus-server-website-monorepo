import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.NpcDefinition;
import net.runelite.cache.definitions.loaders.NpcLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;

import java.io.File;
import java.io.PrintWriter;
import java.util.List;

// Read-only: dumps every npc id -> name pair in the cache as tab-separated lines,
// for the collection-log-luck drop-rate generator (which needs id->name but the
// only source of truth for npc names is the cache, not the combat JSON files).
// Usage: dump <cachePath> <outputPath>
public class DumpAllNpcNames {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        String outputPath = args[1];

        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Index index = store.getIndex(IndexType.CONFIGS);
            Archive archive = index.getArchive(ConfigType.NPC.getId());

            byte[] compressed = storage.loadArchive(archive);
            byte[] decompressed = archive.decompress(compressed);
            FileData[] fileData = archive.getFileData();
            List<byte[]> fileContents = SpliceItemOption.splitChunks(decompressed, fileData.length);

            NpcLoader loader = new NpcLoader();
            int written = 0;
            try (PrintWriter writer = new PrintWriter(outputPath, "UTF-8")) {
                for (int i = 0; i < fileData.length; i++) {
                    int id = fileData[i].getId();
                    try {
                        NpcDefinition def = loader.load(id, fileContents.get(i));
                        if (def.name != null && !def.name.equals("null")) {
                            writer.println(id + "\t" + def.name);
                            written++;
                        }
                    } catch (Exception e) {
                        // skip broken/duplicate slots
                    }
                }
            }
            System.out.println("Wrote " + written + " npc id->name entries to " + outputPath);
        }
    }
}
