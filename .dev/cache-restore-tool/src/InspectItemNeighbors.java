import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;
import java.io.File;
import java.util.Arrays;

public class InspectItemNeighbors {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        int lo = Integer.parseInt(args[1]);
        int hi = Integer.parseInt(args[2]);
        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Index index = store.getIndex(IndexType.CONFIGS);
            Archive archive = index.getArchive(ConfigType.ITEM.getId());
            int[] ids = Arrays.stream(archive.getFileData()).mapToInt(FileData::getId).sorted().toArray();
            System.out.println("ids in [" + lo + "," + hi + "]:");
            for (int id : ids) if (id >= lo && id <= hi) System.out.println("  " + id);
            // nearest below lo and above hi
            int below = -1, above = -1;
            for (int id : ids) {
                if (id < lo) below = id;
                if (id > hi && above == -1) above = id;
            }
            System.out.println("nearest below " + lo + ": " + below);
            System.out.println("nearest above " + hi + ": " + above);
        }
    }
}
