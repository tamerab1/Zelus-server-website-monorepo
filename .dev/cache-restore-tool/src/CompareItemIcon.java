import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.ItemDefinition;
import net.runelite.cache.definitions.loaders.ItemLoader;
import net.runelite.cache.fs.*;
import net.runelite.cache.index.FileData;
import java.io.File;
import java.util.*;

// Prints the inventory-icon-relevant fields (inventory model, framing, recolors) for item ids.
public class CompareItemIcon {
    public static void main(String[] args) throws Exception {
        try (Store store = new Store(new File(args[0]))) {
            store.load();
            Archive archive = store.getIndex(IndexType.CONFIGS).getArchive(ConfigType.ITEM.getId());
            byte[] dec = archive.decompress(store.getStorage().loadArchive(archive));
            FileData[] fd = archive.getFileData();
            List<byte[]> contents = SpliceItemOption.splitChunks(dec, fd.length);
            ItemLoader loader = new ItemLoader();
            for (String s : args[1].split(",")) {
                int id = Integer.parseInt(s.trim());
                for (int i = 0; i < fd.length; i++) if (fd[i].getId() == id) {
                    ItemDefinition d = loader.load(id, contents.get(i));
                    System.out.println(id + " \"" + d.name + "\" invModel=" + d.inventoryModel + " zoom=" + d.zoom2d
                        + " xan=" + d.xan2d + " yan=" + d.yan2d + " zan=" + d.zan2d + " xOff=" + d.xOffset2d + " yOff=" + d.yOffset2d
                        + " male0=" + d.maleModel0 + " recolF=" + Arrays.toString(d.colorFind) + " recolR=" + Arrays.toString(d.colorReplace)
                        + " retexF=" + Arrays.toString(d.textureFind));
                }
            }
        }
    }
}
