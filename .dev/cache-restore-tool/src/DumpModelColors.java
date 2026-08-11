import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.ItemDefinition;
import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.NpcDefinition;
import net.runelite.cache.definitions.loaders.ItemLoader;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.definitions.loaders.NpcLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;
import net.runelite.cache.models.JagexColor;

// Read-only: dumps the distinct packed-HSL face colours used by an item's or NPC's 3D
// model(s), sorted by face count (most-used first) so you know which raw HSL values to
// target with a recolorToFind/recolorToReplace splice. Reuses SpliceItemOption.splitChunks()
// (proven positional List-backed chunk splitter) for both the item and NPC config archives,
// which both have pre-existing duplicate file-id entries that crash RuneLite's Map-backed
// ArchiveFiles.loadContents().
//
// Usage:
//   item <cachePath> <itemId>
//   npc  <cachePath> <npcId>
public class DumpModelColors {
    public static void main(String[] args) throws Exception {
        String mode = args[0];
        String cachePath = args[1];
        int targetId = Integer.parseInt(args[2]);

        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();

            int[] modelIds;
            if (mode.equals("item")) {
                Index index = store.getIndex(IndexType.CONFIGS);
                Archive archive = index.getArchive(ConfigType.ITEM.getId());
                byte[] decompressed = archive.decompress(storage.loadArchive(archive));
                FileData[] fileData = archive.getFileData();
                List<byte[]> fileContents = SpliceItemOption.splitChunks(decompressed, fileData.length);

                ItemLoader loader = new ItemLoader();
                ItemDefinition def = null;
                // Duplicate ids exist in this archive (~828 known) -- last matching slot wins,
                // matching how the live server's own loader resolves them.
                for (int i = 0; i < fileData.length; i++) {
                    if (fileData[i].getId() == targetId) {
                        def = loader.load(targetId, fileContents.get(i));
                    }
                }
                if (def == null) {
                    System.out.println("id=" + targetId + " -- NOT FOUND");
                    return;
                }
                System.out.println("name=\"" + def.name + "\" inventoryModel=" + def.inventoryModel
                        + " maleModel0=" + def.maleModel0 + " maleModel1=" + def.maleModel1
                        + " maleModel2=" + def.maleModel2);
                if (def.colorFind != null) {
                    System.out.println("EXISTING colorFind/Replace pairs (this item is already a recolor):");
                    for (int i = 0; i < def.colorFind.length; i++) {
                        System.out.println("  " + JagexColor.formatHSL(def.colorFind[i]) + " -> "
                                + JagexColor.formatHSL(def.colorReplace[i]));
                    }
                }
                modelIds = new int[]{def.inventoryModel, def.maleModel0, def.maleModel1, def.maleModel2};
            } else if (mode.equals("npc")) {
                Index index = store.getIndex(IndexType.CONFIGS);
                Archive archive = index.getArchive(ConfigType.NPC.getId());
                byte[] decompressed = archive.decompress(storage.loadArchive(archive));
                FileData[] fileData = archive.getFileData();
                List<byte[]> fileContents = SpliceItemOption.splitChunks(decompressed, fileData.length);

                NpcLoader loader = new NpcLoader();
                NpcDefinition def = null;
                for (int i = 0; i < fileData.length; i++) {
                    if (fileData[i].getId() == targetId) {
                        def = loader.load(targetId, fileContents.get(i));
                        break;
                    }
                }
                if (def == null) {
                    System.out.println("id=" + targetId + " -- NOT FOUND");
                    return;
                }
                System.out.println("name=\"" + def.name + "\" models=" + java.util.Arrays.toString(def.models));
                if (def.recolorToFind != null) {
                    System.out.println("EXISTING recolorToFind/Replace pairs (this NPC is already a recolor):");
                    for (int i = 0; i < def.recolorToFind.length; i++) {
                        System.out.println("  " + JagexColor.formatHSL(def.recolorToFind[i]) + " -> "
                                + JagexColor.formatHSL(def.recolorToReplace[i]));
                    }
                }
                modelIds = def.models == null ? new int[0] : def.models;
            } else {
                System.out.println("mode must be 'item' or 'npc'");
                return;
            }

            Index models = store.getIndex(IndexType.MODELS);
            Map<Short, Integer> colorCounts = new TreeMap<>();
            for (int modelId : modelIds) {
                if (modelId <= 0) continue;
                Archive modelArchive = models.getArchive(modelId);
                if (modelArchive == null) {
                    System.out.println("model " + modelId + " -- NOT FOUND");
                    continue;
                }
                byte[] data = modelArchive.decompress(storage.loadArchive(modelArchive));
                ModelDefinition model = new ModelLoader().load(modelId, data);
                if (model.faceColors == null) continue;
                for (short c : model.faceColors) {
                    colorCounts.merge(c, 1, Integer::sum);
                }
            }

            System.out.println("Distinct face colours across " + modelIds.length + " model(s), by face count:");
            colorCounts.entrySet().stream()
                    .sorted((a, b) -> b.getValue() - a.getValue())
                    .forEach(e -> {
                        short hsl = e.getKey();
                        int rgb = JagexColor.HSLtoRGB(hsl, JagexColor.BRIGHTNESS_LOW);
                        System.out.printf("  hsl=%d (%s)  hue=%d sat=%d lum=%d  approxRGB=#%06X  faces=%d%n",
                                hsl, JagexColor.formatHSL(hsl),
                                JagexColor.unpackHue(hsl), JagexColor.unpackSaturation(hsl), JagexColor.unpackLuminance(hsl),
                                rgb, e.getValue());
                    });
        }
    }
}
