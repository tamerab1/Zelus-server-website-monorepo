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
import java.util.Arrays;

// Read-only: prints the model ids (main + chathead) an npc definition references, so they can be
// fed into DumpModelInfo separately. Read-only, no writes.
public class DumpNpcModels {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Index index = store.getIndex(IndexType.CONFIGS);
            Archive archive = index.getArchive(ConfigType.NPC.getId());
            byte[] decompressed = archive.decompress(storage.loadArchive(archive));
            FileData[] fileData = archive.getFileData();
            java.util.List<byte[]> fileContents = SpliceItemOption.splitChunks(decompressed, fileData.length);
            NpcLoader loader = new NpcLoader();
            for (int i = 1; i < args.length; i++) {
                int id = Integer.parseInt(args[i]);
                int slot = -1;
                for (int j = 0; j < fileData.length; j++) if (fileData[j].getId() == id) slot = j;
                if (slot == -1) {
                    System.out.println(id + " -- NOT FOUND");
                    continue;
                }
                NpcDefinition def = loader.load(id, fileContents.get(slot));
                System.out.println(id + " name=" + def.name
                        + " models=" + Arrays.toString(def.models)
                        + " chatheadModels=" + Arrays.toString(def.chatheadModels));
            }
        }
    }
}
