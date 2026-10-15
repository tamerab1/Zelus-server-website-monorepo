import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;
import java.io.File;

public class DumpLastFileDataEntries {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        int n = Integer.parseInt(args[1]);
        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Index index = store.getIndex(IndexType.CONFIGS);
            Archive archive = index.getArchive(ConfigType.ITEM.getId());
            FileData[] fd = archive.getFileData();
            System.out.println("total slots: " + fd.length);
            for (int i = Math.max(0, fd.length - n); i < fd.length; i++) {
                System.out.println("slot " + i + " -> id=" + fd[i].getId());
            }
        }
    }
}
