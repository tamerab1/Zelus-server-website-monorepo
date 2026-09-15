import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;
import net.runelite.cache.index.IndexData;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

// Read-only. Checks the ITEM config archive's fileData listing for (a) duplicate ids anywhere
// near the new Mad Angel item range, (b) whether the ids actually landed in strictly-increasing
// positional order (IndexData.writeIndexData()'s own delta-encoding requirement), and (c) whether
// re-deriving/re-serializing the index's own reference table throws or round-trips clean.
public class CheckItemArchiveIntegrity {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Index configs = store.getIndex(IndexType.CONFIGS);
            Archive archive = configs.getArchive(ConfigType.ITEM.getId());
            FileData[] fileData = archive.getFileData();

            System.out.println("total fileData entries: " + fileData.length);

            Map<Integer, Integer> counts = new HashMap<>();
            for (FileData fd : fileData) {
                counts.merge(fd.getId(), 1, Integer::sum);
            }
            int dupTotal = 0;
            for (Map.Entry<Integer, Integer> e : counts.entrySet()) {
                if (e.getValue() > 1) dupTotal++;
            }
            System.out.println("distinct duplicated ids overall: " + dupTotal);

            System.out.println();
            System.out.println("=== positional order check around the new Mad Angel ids ===");
            int prev = Integer.MIN_VALUE;
            boolean sawTarget = false;
            for (int i = 0; i < fileData.length; i++) {
                int id = fileData[i].getId();
                boolean nearTarget = id >= 34000 && id <= 34100;
                boolean outOfOrder = id < prev;
                if (nearTarget || outOfOrder || (sawTarget && i < fileData.length)) {
                    if (nearTarget) sawTarget = true;
                }
                if (nearTarget) {
                    System.out.println("  pos " + i + ": id=" + id + (outOfOrder ? "  <-- OUT OF ORDER (prev=" + prev + ")" : "")
                            + (counts.get(id) > 1 ? "  <-- DUPLICATE (" + counts.get(id) + "x)" : ""));
                }
                prev = id;
            }

            System.out.println();
            System.out.println("=== full monotonic check (first violation only, if any) ===");
            prev = Integer.MIN_VALUE;
            boolean anyViolation = false;
            for (int i = 0; i < fileData.length; i++) {
                int id = fileData[i].getId();
                if (id < prev) {
                    System.out.println("  FIRST OUT-OF-ORDER at pos " + i + ": id=" + id + " after prev=" + prev);
                    anyViolation = true;
                    break;
                }
                prev = id;
            }
            if (!anyViolation) System.out.println("  fully monotonic non-decreasing -- no violation found");

            System.out.println();
            System.out.println("=== index reference table round-trip ===");
            try {
                IndexData indexData = configs.toIndexData();
                byte[] raw = indexData.writeIndexData();
                System.out.println("  wrote " + raw.length + " bytes OK, no exception");
            } catch (Exception e) {
                System.out.println("  THREW: " + e);
            }

            System.out.println();
            System.out.println("=== direct lookup of the 10 new items by id (does getFileData even find them uniquely?) ===");
            int[] ids = {34027, 34028, 34029, 34030, 34031, 34032, 34033, 34034, 34042, 34043};
            for (int id : ids) {
                int firstPos = -1, count = 0;
                for (int i = 0; i < fileData.length; i++) {
                    if (fileData[i].getId() == id) {
                        if (firstPos == -1) firstPos = i;
                        count++;
                    }
                }
                System.out.println("  id " + id + ": found " + count + " time(s), first at position " + firstPos);
            }
        }
    }
}
