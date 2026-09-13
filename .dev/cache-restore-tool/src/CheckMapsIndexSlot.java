import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Store;
import java.io.File;
public class CheckMapsIndexSlot {
    public static void main(String[] args) throws Exception {
        try (Store store = new Store(new File(args[0]))) {
            store.load();
            Index maps = store.getIndex(IndexType.MAPS);
            int maxId = -1;
            for (Archive a : maps.getArchives()) maxId = Math.max(maxId, a.getArchiveId());
            System.out.println("MAPS index: " + maps.getArchives().size() + " archives, max id=" + maxId);
            for (int i = 1; i < args.length; i++) {
                int id = Integer.parseInt(args[i]);
                Archive a = maps.getArchive(id);
                System.out.println("slot " + id + ": " + (a == null ? "FREE" : "OCCUPIED nameHash=" + a.getNameHash()));
            }
            // Also check by name hash for m39_34 / l39_34
            String[] names = {"m39_34", "l39_34"};
            for (String name : names) {
                int hash = javaHash(name);
                boolean found = false;
                for (Archive a : maps.getArchives()) {
                    if (a.getNameHash() == hash) { found = true; break; }
                }
                System.out.println("name '" + name + "' hash=" + hash + ": " + (found ? "OCCUPIED" : "free"));
            }
        }
    }
    static int javaHash(String s) {
        int hash = 0;
        for (int i = 0; i < s.length(); i++) hash = (hash << 5) - hash + s.charAt(i);
        return hash;
    }
}
