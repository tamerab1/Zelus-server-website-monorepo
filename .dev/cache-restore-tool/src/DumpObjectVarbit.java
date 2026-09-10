import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.ObjectDefinition;
import net.runelite.cache.definitions.loaders.ObjectLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;

import java.io.File;
import java.util.List;

// Read-only: dumps varbitID/varpID/configChangeDest for the given object ids,
// using the same decompress+splitChunks approach as LookupObjectNames.java
// (archive.getFiles() throws "duplicate file ids" on this live cache).
public class DumpObjectVarbit {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Index index = store.getIndex(IndexType.CONFIGS);
            Archive archive = index.getArchive(ConfigType.OBJECT.getId());

            byte[] decompressed = archive.decompress(storage.loadArchive(archive));
            FileData[] fileData = archive.getFileData();
            List<byte[]> fileContents = SpliceItemOption.splitChunks(decompressed, fileData.length);

            ObjectLoader loader = new ObjectLoader();

            for (int a = 1; a < args.length; a++) {
                int targetId = Integer.parseInt(args[a]);
                boolean found = false;
                for (int i = 0; i < fileData.length; i++) {
                    if (fileData[i].getId() == targetId) {
                        found = true;
                        ObjectDefinition def = loader.load(targetId, fileContents.get(i));
                        System.out.println("id=" + targetId
                                + " name=" + def.getName()
                                + " varbitID=" + def.getVarbitID()
                                + " varpID=" + def.getVarpID()
                                + " configChangeDest=" + java.util.Arrays.toString(def.getConfigChangeDest()));
                    }
                }
                if (!found) System.out.println("id=" + targetId + " -- NOT FOUND");
            }
        }
    }
}
