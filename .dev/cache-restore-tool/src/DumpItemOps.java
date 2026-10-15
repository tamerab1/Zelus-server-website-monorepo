import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.ItemDefinition;
import net.runelite.cache.definitions.loaders.ItemLoader;
import net.runelite.cache.fs.*;
import net.runelite.cache.index.FileData;
import java.io.File;
import java.util.*;

// Prints inventory options + worn options (params 451-459) for the given item ids.
public class DumpItemOps {
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
                    List<String> worn = new ArrayList<>();
                    if (d.params != null) for (int k = 451; k <= 459; k++) if (d.params.containsKey(k)) worn.add(k + "=" + d.params.get(k));
                    System.out.println(id + " \"" + d.name + "\" inv=" + Arrays.toString(d.interfaceOptions) + " worn=" + worn);
                }
            }
        }
    }
}
