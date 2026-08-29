import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class CheckModelFormats {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Index models = store.getIndex(IndexType.MODELS);
            Map<String, Integer> counts = new HashMap<>();
            for (int i = 1; i < args.length; i++) {
                int modelId = Integer.parseInt(args[i]);
                Archive archive = models.getArchive(modelId);
                byte[] raw = archive.decompress(storage.loadArchive(archive));
                String type;
                if (raw[raw.length-1] == -3 && raw[raw.length-2] == -1) type = "Type3";
                else if (raw[raw.length-1] == -2 && raw[raw.length-2] == -1) type = "Type2";
                else if (raw[raw.length-1] == -1 && raw[raw.length-2] == -1) type = "Type1";
                else type = "OldFormat";
                counts.merge(type, 1, Integer::sum);
                System.out.println(modelId + ": " + type);
            }
            System.out.println("=== summary ===");
            counts.forEach((k, v) -> System.out.println(k + ": " + v));
        }
    }
}
