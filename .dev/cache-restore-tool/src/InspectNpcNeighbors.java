import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;
import java.io.File;
import java.util.Arrays;

public class InspectNpcNeighbors {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        int target = Integer.parseInt(args[1]);
        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Index index = store.getIndex(IndexType.CONFIGS);
            Archive archive = index.getArchive(ConfigType.NPC.getId());
            int[] ids = Arrays.stream(archive.getFileData()).mapToInt(FileData::getId).sorted().toArray();
            int below = -1, above = -1;
            for (int id : ids) {
                if (id < target) below = id;
                if (id > target && above == -1) above = id;
            }
            System.out.println("nearest below " + target + ": " + below + " (delta " + (target-below) + ")");
            System.out.println("nearest above " + target + ": " + above + " (delta " + (above-target) + ")");
        }
    }
}
