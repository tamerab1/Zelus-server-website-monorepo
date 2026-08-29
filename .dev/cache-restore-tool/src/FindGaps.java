import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;

import java.io.File;
import java.util.Arrays;
import java.util.TreeSet;

// Read-only: reports max id + free (unused) id slots near the top of the NPC/ITEM config archives,
// so new custom content can be assigned ids that don't collide with anything indexed.
public class FindGaps {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Index index = store.getIndex(IndexType.CONFIGS);

            report("NPC", storage, index, ConfigType.NPC.getId());
            report("ITEM", storage, index, ConfigType.ITEM.getId());
        }
    }

    static void report(String label, Storage storage, Index index, int configId) throws Exception {
        Archive archive = index.getArchive(configId);
        byte[] compressed = storage.loadArchive(archive);
        byte[] decompressed = archive.decompress(compressed);
        FileData[] fileData = archive.getFileData();

        TreeSet<Integer> ids = new TreeSet<>();
        for (FileData fd : fileData) ids.add(fd.getId());

        int max = ids.last();
        System.out.println("=== " + label + " === total entries=" + fileData.length + " maxId=" + max);

        // find gaps (unused ids) within [0, max+200]
        StringBuilder gaps = new StringBuilder();
        int gapCount = 0;
        int rangeStart = -1;
        for (int i = 0; i <= max + 200 && gapCount < 400; i++) {
            boolean present = ids.contains(i);
            if (!present && rangeStart == -1) {
                rangeStart = i;
            } else if (present && rangeStart != -1) {
                int rangeEnd = i - 1;
                int len = rangeEnd - rangeStart + 1;
                if (len >= 3) {
                    gaps.append(rangeStart).append("-").append(rangeEnd).append(" (len ").append(len).append("), ");
                    gapCount++;
                }
                rangeStart = -1;
            }
        }
        if (rangeStart != -1) {
            gaps.append(rangeStart).append("-").append(max + 200).append(" (open past maxId+200), ");
        }
        System.out.println(label + " free ranges (len>=3) up to maxId+200:");
        System.out.println(gaps.length() == 0 ? "  (none found)" : "  " + gaps);
        System.out.println();
    }
}
