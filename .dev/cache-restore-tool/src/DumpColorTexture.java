import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.ItemDefinition;
import net.runelite.cache.definitions.loaders.ItemLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public class DumpColorTexture {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Index index = store.getIndex(IndexType.CONFIGS);
            Archive archive = index.getArchive(ConfigType.ITEM.getId());

            byte[] compressed = storage.loadArchive(archive);
            byte[] decompressed = archive.decompress(compressed);
            FileData[] fileData = archive.getFileData();
            List<byte[]> fileContents = SpliceItemOption.splitChunks(decompressed, fileData.length);

            for (int i = 1; i < args.length; i++) {
                int targetId = Integer.parseInt(args[i]);
                int slot = -1;
                for (int j = 0; j < fileData.length; j++) {
                    if (fileData[j].getId() == targetId) slot = j;
                }
                if (slot == -1) {
                    System.out.println(targetId + ": NOT FOUND");
                    continue;
                }
                ItemDefinition def = new ItemLoader().load(targetId, fileContents.get(slot));
                System.out.println(targetId + " \"" + def.name + "\":");
                System.out.println("  colorFind=" + Arrays.toString(def.colorFind) + " colorReplace=" + Arrays.toString(def.colorReplace));
                System.out.println("  textureFind=" + Arrays.toString(def.textureFind) + " textureReplace=" + Arrays.toString(def.textureReplace));
                System.out.println("  wearPos1=" + def.wearPos1 + " wearPos2=" + def.wearPos2 + " wearPos3=" + def.wearPos3);
            }
        }
    }
}
