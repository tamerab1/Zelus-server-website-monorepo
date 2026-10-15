import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.ItemDefinition;
import net.runelite.cache.definitions.loaders.ItemLoader;
import net.runelite.cache.fs.*;
import net.runelite.cache.index.FileData;

import java.io.File;
import java.util.*;

// Read-only: prints "id name [inventory ops]" for the given item ids (first record of each id), via the same chunk
// split as the item editors (DumpItemNames can't read the current archive). Usage: <cachePath> <id>...
public class ItemNamesById {
    static String pairs(short[] f, short[] r) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < f.length; i++) b.append(i == 0 ? "" : ",").append(f[i]).append(",").append(r[i]);
        return b.toString();
    }

    public static void main(String[] args) throws Exception {
        Set<Integer> want = new LinkedHashSet<>();
        for (int i = 1; i < args.length; i++) want.add(Integer.parseInt(args[i]));
        Map<Integer, String> found = new HashMap<>();
        try (Store store = new Store(new File(args[0]))) {
            store.load();
            Archive archive = store.getIndex(IndexType.CONFIGS).getArchive(ConfigType.ITEM.getId());
            byte[] dec = archive.decompress(store.getStorage().loadArchive(archive));
            FileData[] fd = archive.getFileData();
            List<byte[]> contents = SpliceItemOption.splitChunks(dec, fd.length);
            ItemLoader loader = new ItemLoader();
            for (int i = 0; i < fd.length; i++) {
                int id = fd[i].getId();
                if (!want.contains(id) || found.containsKey(id)) continue;
                ItemDefinition d = loader.load(id, contents.get(i));
                found.put(id, d.name + (d.notedTemplate != -1 ? " (noted)" : "") + " " + Arrays.toString(d.interfaceOptions)
                        + " inv=" + d.inventoryModel + " male=" + d.maleModel0 + "/" + d.maleModel1 + "/" + d.maleModel2 + " female=" + d.femaleModel0 + " wear=" + d.wearPos1 + "/" + d.wearPos2 + "/" + d.wearPos3
                        + (d.colorFind == null ? "" : " recolour " + pairs(d.colorFind, d.colorReplace)));
            }
        }
        for (int id : want) System.out.println(id + "  " + found.getOrDefault(id, "<MISSING>"));
    }
}
