import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

// Read-only: reports the max existing varbit id, and which ids in a given
// range (inclusive) are NOT currently defined in the VARBIT archive -- those
// are safe to claim for new custom varbits since nothing in the live cache
// references them.
public class FindFreeVarbits {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        int rangeStart = Integer.parseInt(args[1]);
        int rangeEnd = Integer.parseInt(args[2]);
        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Index index = store.getIndex(IndexType.CONFIGS);
            Archive archive = index.getArchive(ConfigType.VARBIT.getId());
            FileData[] fileData = archive.getFileData();
            int max = Arrays.stream(fileData).mapToInt(FileData::getId).max().orElse(-1);
            Set<Integer> used = new HashSet<>();
            for (FileData fd : fileData) used.add(fd.getId());
            System.out.println("VARBIT archive: " + fileData.length + " file slots.");
            System.out.println("MAX_VARBIT_ID=" + max);
            StringBuilder free = new StringBuilder();
            for (int id = rangeStart; id <= rangeEnd; id++) {
                if (!used.contains(id)) {
                    free.append(id).append(" ");
                }
            }
            System.out.println("FREE_IN_RANGE[" + rangeStart + "-" + rangeEnd + "]=" + free.toString().trim());
        }
    }
}
