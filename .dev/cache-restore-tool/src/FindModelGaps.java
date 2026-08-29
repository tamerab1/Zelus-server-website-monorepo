import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Store;
import java.io.File;
import java.util.HashSet;
import java.util.Set;

public class FindModelGaps {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        int lo = Integer.parseInt(args[1]);
        int hi = Integer.parseInt(args[2]);
        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Index models = store.getIndex(IndexType.MODELS);
            Set<Integer> used = new HashSet<>();
            for (var a : models.getArchives()) used.add(a.getArchiveId());
            System.out.println("Free model ids in [" + lo + "," + hi + "]:");
            int count = 0;
            for (int id = lo; id <= hi && count < 40; id++) {
                if (!used.contains(id)) {
                    System.out.println("  " + id);
                    count++;
                }
            }
        }
    }
}
