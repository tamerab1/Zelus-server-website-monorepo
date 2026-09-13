import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;
import java.io.File;
import java.util.*;

// Read-only. Checks a bulk list of ids against a target cache's OBJECT config archive, MODELS
// index, or SEQUENCE config archive (mode arg selects which). Prints only the OCCUPIED ones (an id
// not printed is free) so the output stays readable even for 100+ ids.
public class BulkCollisionCheck {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        String mode = args[1]; // object | model | sequence
        int[] ids = new int[args.length - 2];
        for (int i = 2; i < args.length; i++) ids[i - 2] = Integer.parseInt(args[i]);

        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            int occupiedCount = 0;
            if (mode.equals("model")) {
                Index models = store.getIndex(IndexType.MODELS);
                for (int id : ids) {
                    if (models.getArchive(id) != null) { System.out.println("OCCUPIED model " + id); occupiedCount++; }
                }
            } else {
                int configTypeId = mode.equals("object") ? ConfigType.OBJECT.getId() : ConfigType.SEQUENCE.getId();
                Index configs = store.getIndex(IndexType.CONFIGS);
                Archive archive = configs.getArchive(configTypeId);
                FileData[] fileData = archive.getFileData();
                Set<Integer> known = new HashSet<>();
                for (FileData fd : fileData) known.add(fd.getId());
                for (int id : ids) {
                    if (known.contains(id)) { System.out.println("OCCUPIED " + mode + " " + id); occupiedCount++; }
                }
            }
            System.out.println("total checked: " + ids.length + ", occupied: " + occupiedCount + ", free: " + (ids.length - occupiedCount));
        }
    }
}
