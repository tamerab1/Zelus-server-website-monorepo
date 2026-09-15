import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.ItemDefinition;
import net.runelite.cache.definitions.loaders.ItemLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Store;
import java.io.File;

public class CheckItemColorRecolor {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Index configIndex = store.getIndex(IndexType.CONFIGS);
            Archive itemArchive = configIndex.getArchive(ConfigType.ITEM.getId());
            byte[] decompressed = itemArchive.decompress(store.getStorage().loadArchive(itemArchive));
            var fileData = itemArchive.getFileData();
            var contents = SpliceItemOption.splitChunks(decompressed, fileData.length);
            ItemLoader loader = new ItemLoader();

            for (int i = 1; i < args.length; i++) {
                int id = Integer.parseInt(args[i]);
                int slot = -1;
                for (int j = 0; j < fileData.length; j++) if (fileData[j].getId() == id) slot = j;
                if (slot == -1) { System.out.println(id + " -> NOT FOUND"); continue; }
                ItemDefinition def = loader.load(id, contents.get(slot));
                System.out.println(id + " \"" + def.name + "\" -> colorFind=" + arr(def.colorFind)
                        + " colorReplace=" + arr(def.colorReplace)
                        + " textureFind=" + shortArr(def.textureFind) + " textureReplace=" + shortArr(def.textureReplace));
            }
        }
    }
    static String arr(short[] a) { return a == null ? "null" : java.util.Arrays.toString(a); }
    static String shortArr(short[] a) { return a == null ? "null" : java.util.Arrays.toString(a); }
}
