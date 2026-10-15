import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Store;
import java.io.File;

// Read-only: prints which model archive ids in [lo, hi] exist. Usage: <cachePath> <lo> <hi>
public class ListModelIds {
    public static void main(String[] args) throws Exception {
        try (Store store = new Store(new File(args[0]))) {
            store.load();
            var idx = store.getIndex(IndexType.MODELS);
            int lo = Integer.parseInt(args[1]), hi = Integer.parseInt(args[2]), max = 0;
            StringBuilder used = new StringBuilder();
            for (Archive a : idx.getArchives()) {
                max = Math.max(max, a.getArchiveId());
                if (a.getArchiveId() >= lo && a.getArchiveId() <= hi) used.append(a.getArchiveId()).append(' ');
            }
            System.out.println("max model id=" + max + " used in range: " + used);
        }
    }
}
