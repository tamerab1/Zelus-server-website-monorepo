import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

// Usage: items <cachePath> <startId> <count>   -- scans item CONFIGS archive's FileData ids
//        models <cachePath> <startId> <count>  -- scans MODELS index archive ids
public class FindFreeIds {
    public static void main(String[] args) throws Exception {
        String mode = args[0];
        String cachePath = args[1];
        int startId = Integer.parseInt(args[2]);
        int count = Integer.parseInt(args[3]);

        try (Store store = new Store(new File(cachePath))) {
            store.load();
            if (mode.equals("items")) {
                Index index = store.getIndex(IndexType.CONFIGS);
                Archive archive = index.getArchive(ConfigType.ITEM.getId());
                FileData[] fds = archive.getFileData();
                Set<Integer> used = new HashSet<>();
                int max = -1;
                for (FileData fd : fds) { used.add(fd.getId()); if (fd.getId() > max) max = fd.getId(); }
                System.out.println("total item defs: " + fds.length + ", max id: " + max);
                int found = 0;
                for (int id = startId; found < count && id < 65536; id++) {
                    boolean free = !used.contains(id);
                    System.out.println("  " + id + " -> " + (free ? "free" : "EXISTS"));
                    if (free) found++;
                }
            } else if (mode.equals("models")) {
                Index models = store.getIndex(IndexType.MODELS);
                int found = 0;
                for (int id = startId; found < count && id < 65536; id++) {
                    boolean free = models.getArchive(id) == null;
                    System.out.println("  " + id + " -> " + (free ? "free" : "EXISTS"));
                    if (free) found++;
                }
            } else {
                throw new IllegalArgumentException("mode must be items or models");
            }
        }
    }
}
