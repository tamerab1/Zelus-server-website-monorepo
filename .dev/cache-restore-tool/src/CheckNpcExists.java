import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;

import java.io.File;

// Read-only. Checks whether given npc ids already exist in a cache (used to check if Zelus already
// has Aggy's follower npc 16317 sitting unused from an earlier import batch, and separately to
// probe the reference cache's actual archive count/id range for diagnostic purposes).
public class CheckNpcExists {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        try (Store store = new Store(new File(cachePath))) {
            store.load();
            var configs = store.getIndex(IndexType.CONFIGS);
            if (configs == null) { System.out.println("CONFIGS index is null for this cache"); return; }
            Archive archive = configs.getArchive(ConfigType.NPC.getId());
            if (archive == null) { System.out.println("NPC archive is null"); return; }
            FileData[] fileData = archive.getFileData();
            System.out.println("total npc records: " + fileData.length);
            for (int i = 1; i < args.length; i++) {
                int id = Integer.parseInt(args[i]);
                boolean found = false;
                for (FileData fd : fileData) if (fd.getId() == id) found = true;
                System.out.println("npc " + id + ": " + (found ? "EXISTS" : "not present"));
            }
        }
    }
}
