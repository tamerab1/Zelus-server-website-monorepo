import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;

import java.io.File;
import java.util.Arrays;

// Read-only: reports the max existing npc id in archive 9 (npcs) of the CONFIGS index.
public class FindMaxNpcId {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Index index = store.getIndex(IndexType.CONFIGS);
            Archive archive = index.getArchive(ConfigType.NPC.getId());
            FileData[] fileData = archive.getFileData();
            int max = Arrays.stream(fileData).mapToInt(FileData::getId).max().orElse(-1);
            System.out.println("Archive 9 (npcs): " + fileData.length + " file slots.");
            System.out.println("MAX_NPC_ID=" + max);
        }
    }
}
