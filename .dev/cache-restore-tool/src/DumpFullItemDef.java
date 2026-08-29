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
import java.util.List;

public class DumpFullItemDef {
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

            for (String idStr : args[1].split(",")) {
                int targetId = Integer.parseInt(idStr.trim());
                int slot = -1;
                for (int i = 0; i < fileData.length; i++) {
                    if (fileData[i].getId() == targetId) slot = i;
                }
                if (slot == -1) {
                    System.out.println(targetId + ": NOT FOUND");
                    continue;
                }
                ItemDefinition def = new ItemLoader().load(targetId, fileContents.get(slot));
                System.out.println("=== " + targetId + " \"" + def.name + "\" ===");
                System.out.println("  resize=" + def.resizeX + "," + def.resizeY + "," + def.resizeZ);
                System.out.println("  an2d=" + def.xan2d + "," + def.yan2d + "," + def.zan2d);
                System.out.println("  cost=" + def.cost + " tradeable=" + def.isTradeable + " stackable=" + def.stackable);
                System.out.println("  inventoryModel=" + def.inventoryModel);
                System.out.println("  wearPos1=" + def.wearPos1 + " wearPos2=" + def.wearPos2 + " wearPos3=" + def.wearPos3);
                System.out.println("  members=" + def.members);
                System.out.println("  zoom2d=" + def.zoom2d + " xOff=" + def.xOffset2d + " yOff=" + def.yOffset2d);
                System.out.println("  ambient=" + def.ambient + " contrast=" + def.contrast);
                System.out.println("  options=" + java.util.Arrays.toString(def.options));
                System.out.println("  interfaceOptions=" + java.util.Arrays.toString(def.interfaceOptions));
                System.out.println("  maleModel0=" + def.maleModel0 + " maleModel1=" + def.maleModel1 + " maleModel2=" + def.maleModel2);
                System.out.println("  maleOffset=" + def.maleOffset + " maleHeadModel=" + def.maleHeadModel + " maleHeadModel2=" + def.maleHeadModel2);
                System.out.println("  femaleModel0=" + def.femaleModel0 + " femaleModel1=" + def.femaleModel1 + " femaleModel2=" + def.femaleModel2);
                System.out.println("  femaleOffset=" + def.femaleOffset + " femaleHeadModel=" + def.femaleHeadModel + " femaleHeadModel2=" + def.femaleHeadModel2);
                System.out.println("  category=" + def.category + " team=" + def.team + " weight=" + def.weight);
                System.out.println("  notedID=" + def.notedID + " notedTemplate=" + def.notedTemplate);
                System.out.println("  boughtId=" + def.boughtId + " boughtTemplateId=" + def.boughtTemplateId);
                System.out.println("  placeholderId=" + def.placeholderId + " placeholderTemplateId=" + def.placeholderTemplateId);
                System.out.println("  params=" + def.params);
            }
        }
    }
}
