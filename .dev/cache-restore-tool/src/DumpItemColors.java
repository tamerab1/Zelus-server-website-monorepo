import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.ItemDefinition;
import net.runelite.cache.definitions.NpcDefinition;
import net.runelite.cache.definitions.loaders.ItemLoader;
import net.runelite.cache.definitions.loaders.NpcLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;

import java.io.File;
import java.util.List;

// Read-only dump of colorFind/colorReplace/textureFind/textureReplace + model ids for item or
// npc defs, to look for malformed/out-of-range recolor values that could break client rendering.
// Usage: <cachePath> <item|npc> <id1,id2,...>
public class DumpItemColors {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        String kind = args[1];
        int configId = kind.equals("item") ? ConfigType.ITEM.getId() : ConfigType.NPC.getId();

        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Index index = store.getIndex(IndexType.CONFIGS);
            Archive archive = index.getArchive(configId);

            byte[] compressed = storage.loadArchive(archive);
            byte[] decompressed = archive.decompress(compressed);
            FileData[] fileData = archive.getFileData();
            List<byte[]> fileContents = SpliceItemOption.splitChunks(decompressed, fileData.length);

            for (String idStr : args[2].split(",")) {
                int targetId = Integer.parseInt(idStr.trim());
                int slot = -1;
                for (int i = 0; i < fileData.length; i++) {
                    if (fileData[i].getId() == targetId) slot = i;
                }
                if (slot == -1) {
                    System.out.println(targetId + ": NOT FOUND");
                    continue;
                }
                if (kind.equals("item")) {
                    ItemDefinition def = new ItemLoader().load(targetId, fileContents.get(slot));
                    System.out.println(targetId + " \"" + def.name + "\":");
                    System.out.println("  models: male0=" + def.maleModel0 + " male1=" + def.maleModel1
                            + " male2=" + def.maleModel2 + " inv=" + def.inventoryModel);
                    System.out.println("  colorFind=" + arr(def.colorFind) + " colorReplace=" + arr(def.colorReplace));
                    System.out.println("  textureFind=" + arr(def.textureFind) + " textureReplace=" + arr(def.textureReplace));
                } else {
                    NpcDefinition def = new NpcLoader().load(targetId, fileContents.get(slot));
                    System.out.println(targetId + " \"" + def.name + "\":");
                    System.out.println("  models=" + java.util.Arrays.toString(def.models));
                    System.out.println("  recolorToFind=" + arr(def.recolorToFind) + " recolorToReplace=" + arr(def.recolorToReplace));
                    System.out.println("  retextureToFind=" + arr(def.retextureToFind) + " retextureToReplace=" + arr(def.retextureToReplace));
                }
            }
        }
    }

    static String arr(short[] a) {
        return a == null ? "null" : java.util.Arrays.toString(a);
    }
}
