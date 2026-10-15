import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;
import java.io.File;

public class CheckSlot {
    public static void main(String[] args) throws Exception {
        try (Store store = new Store(new File(args[0]))) {
            store.load();
            Index index = store.getIndex(IndexType.CONFIGS);
            Archive archive = index.getArchive(ConfigType.OBJECT.getId());
            FileData[] fds = archive.getFileData();
            System.out.println("length=" + fds.length);
            for (int i = 57812; i < fds.length; i++) {
                System.out.println("slot " + i + " id=" + fds[i].getId());
            }
        }
    }
}
