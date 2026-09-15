import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;

import java.io.File;

// Read-only. Checks whether given ids exist within a named ConfigType archive (SEQUENCE, SPOTANIM,
// etc.) in a cache -- generic version of CheckNpcExists for any config type.
public class CheckConfigExists {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        String configTypeName = args[1]; // e.g. SEQUENCE, SPOTANIM, NPC, ITEM
        try (Store store = new Store(new File(cachePath))) {
            store.load();
            var configs = store.getIndex(IndexType.CONFIGS);
            if (configs == null) { System.out.println("CONFIGS index is null"); return; }
            ConfigType type = ConfigType.valueOf(configTypeName.toUpperCase());
            Archive archive = configs.getArchive(type.getId());
            if (archive == null) { System.out.println(configTypeName + " archive is null"); return; }
            FileData[] fileData = archive.getFileData();
            System.out.println("total " + configTypeName + " records: " + fileData.length);
            for (int i = 2; i < args.length; i++) {
                int id = Integer.parseInt(args[i]);
                boolean found = false;
                for (FileData fd : fileData) if (fd.getId() == id) found = true;
                System.out.println(configTypeName + " " + id + ": " + (found ? "EXISTS" : "not present"));
            }
        }
    }
}
