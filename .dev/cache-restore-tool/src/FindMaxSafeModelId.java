import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Store;
import java.io.File;

public class FindMaxSafeModelId {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        try (Store store = new Store(new File(cachePath))) {
            store.load();
            var models = store.getIndex(IndexType.MODELS);
            int maxSafe = -1;
            int count = 0;
            for (Archive a : models.getArchives()) {
                if (a == null) continue;
                int id = a.getArchiveId();
                count++;
                if (id < 65536 && id > maxSafe) maxSafe = id;
            }
            System.out.println("Total archives: " + count + ", max safe (<65536) id: " + maxSafe);
            // show a window around it
            for (int id = maxSafe - 5; id <= maxSafe + 40; id++) {
                boolean exists = models.getArchive(id) != null;
                System.out.println("  " + id + " -> " + (exists ? "EXISTS" : "free"));
            }
        }
    }
}
