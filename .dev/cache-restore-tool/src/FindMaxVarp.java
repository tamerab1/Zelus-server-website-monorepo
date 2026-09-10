import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;

import java.io.File;
import java.util.Arrays;

// Read-only: reports the max existing id (and slot count) in the VARPLAYER
// config archive, to check whether raw varps even have per-id cache
// definitions in this revision, and what the safe-to-add range looks like.
public class FindMaxVarp {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Index index = store.getIndex(IndexType.CONFIGS);
            Archive archive = index.getArchive(ConfigType.VARPLAYER.getId());
            if (archive == null) {
                System.out.println("No VARPLAYER archive in this cache (config type id="
                        + ConfigType.VARPLAYER.getId() + ") -- raw varps likely have no per-id definitions.");
                return;
            }
            FileData[] fileData = archive.getFileData();
            int max = Arrays.stream(fileData).mapToInt(FileData::getId).max().orElse(-1);
            System.out.println("VARPLAYER archive: " + fileData.length + " file slots.");
            System.out.println("MAX_VARP_ID=" + max);
        }
    }
}
