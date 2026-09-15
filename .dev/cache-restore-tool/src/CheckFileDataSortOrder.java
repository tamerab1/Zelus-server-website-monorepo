import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;

import java.io.File;

public class CheckFileDataSortOrder {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Index index = store.getIndex(IndexType.CONFIGS);
            Archive archive = index.getArchive(ConfigType.ITEM.getId());
            FileData[] fds = archive.getFileData();
            int violations = 0;
            for (int i = 1; i < fds.length; i++) {
                if (fds[i].getId() < fds[i-1].getId()) {
                    violations++;
                    if (violations <= 10) {
                        System.out.println("OUT OF ORDER at index " + i + ": id[" + (i-1) + "]=" + fds[i-1].getId()
                                + " -> id[" + i + "]=" + fds[i].getId());
                    }
                }
            }
            System.out.println("total entries=" + fds.length + " violations=" + violations);
        }
    }
}
