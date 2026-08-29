import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;

import java.io.File;

public class FullCacheAudit {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        int totalArchives = 0;
        int badArchives = 0;
        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            for (Index index : store.getIndexes()) {
                for (Archive archive : index.getArchives()) {
                    totalArchives++;
                    try {
                        byte[] data = storage.loadArchive(archive);
                        if (data == null) {
                            System.out.println("NULL DATA: index=" + index.getId() + " archiveId=" + archive.getArchiveId());
                            badArchives++;
                        }
                    } catch (Exception e) {
                        System.out.println("EXCEPTION: index=" + index.getId() + " archiveId=" + archive.getArchiveId() + " -> " + e);
                        badArchives++;
                    }
                }
            }
        }
        System.out.println();
        System.out.println("Scanned " + totalArchives + " archives, " + badArchives + " bad");
    }
}
