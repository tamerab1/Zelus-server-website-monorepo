import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;

import java.io.File;
import java.util.TreeSet;

// Read-only: prints runs of unused ITEM config ids in [lo, hi] (inclusive) of at least minRun length.
// Usage: <cachePath> <lo> <hi> <minRun>
public class ListFreeItemIds {
    public static void main(String[] args) throws Exception {
        int lo = Integer.parseInt(args[1]), hi = Integer.parseInt(args[2]), minRun = Integer.parseInt(args[3]);
        try (Store store = new Store(new File(args[0]))) {
            store.load();
            Index index = store.getIndex(IndexType.CONFIGS);
            Archive archive = index.getArchive(ConfigType.ITEM.getId());
            store.getStorage().loadArchive(archive);
            TreeSet<Integer> used = new TreeSet<>();
            for (FileData fd : archive.getFileData()) used.add(fd.getId());
            System.out.println("total ids=" + used.size() + " max=" + used.last());
            int runStart = -1;
            for (int id = lo; id <= hi + 1; id++) {
                boolean free = id <= hi && !used.contains(id);
                if (free && runStart < 0) runStart = id;
                if (!free && runStart >= 0) {
                    if (id - runStart >= minRun) System.out.println("free " + runStart + "-" + (id - 1) + " (" + (id - runStart) + ")");
                    runStart = -1;
                }
            }
        }
    }
}
