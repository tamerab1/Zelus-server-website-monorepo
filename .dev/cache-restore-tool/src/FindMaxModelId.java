import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Store;

import java.io.File;

public class FindMaxModelId {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Index models = store.getIndex(IndexType.MODELS);
            int max = models.getArchives().stream().mapToInt(a -> a.getArchiveId()).max().orElse(-1);
            System.out.println("MAX_MODEL_ID=" + max);
        }
    }
}
