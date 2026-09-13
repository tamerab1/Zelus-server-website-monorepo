import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Store;
import java.io.File;

// Read-only, one-off: is the real overworld terrain around the Fallen Cathedral's real-world
// entrance (mapsquare 39,34) already present in Zelus's base cache, independent of the cathedral
// import? Checks the four adjacent mapsquares by name hash.
public class CheckNeighborMaps {
    public static void main(String[] args) throws Exception {
        try (Store store = new Store(new File(args[0]))) {
            store.load();
            Index maps = store.getIndex(IndexType.MAPS);
            String[] names = {"m38_34", "l38_34", "m40_34", "l40_34", "m39_33", "l39_33", "m39_35", "l39_35"};
            for (String name : names) {
                int hash = javaHash(name);
                boolean found = false;
                for (Archive a : maps.getArchives()) {
                    if (a.getNameHash() == hash) { found = true; break; }
                }
                System.out.println("name '" + name + "' hash=" + hash + ": " + (found ? "PRESENT" : "MISSING"));
            }
        }
    }
    static int javaHash(String s) {
        int hash = 0;
        for (int i = 0; i < s.length(); i++) hash = (hash << 5) - hash + s.charAt(i);
        return hash;
    }
}
