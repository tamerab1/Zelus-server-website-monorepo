import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Store;
import java.io.File;
public class CheckExistingMapCompression {
    public static void main(String[] args) throws Exception {
        try (Store store = new Store(new File(args[0]))) {
            store.load();
            Index maps = store.getIndex(IndexType.MAPS);
            int shown = 0;
            for (Archive a : maps.getArchives()) {
                System.out.println("archive id=" + a.getArchiveId() + " nameHash=" + a.getNameHash() + " compression=" + a.getCompression());
                if (++shown >= 5) break;
            }
        }
    }
}
