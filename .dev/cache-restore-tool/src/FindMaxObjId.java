import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;
import java.io.File;
public class FindMaxObjId {
    public static void main(String[] args) throws Exception {
        try (Store store = new Store(new File(args[0]))) {
            store.load();
            Index index = store.getIndex(IndexType.CONFIGS);
            Archive archive = index.getArchive(ConfigType.OBJECT.getId());
            FileData[] fds = archive.getFileData();
            int max = -1;
            for (FileData fd : fds) if (fd.getId() > max) max = fd.getId();
            System.out.println("MAX_OBJECT_ID=" + max);
        }
    }
}
