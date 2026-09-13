import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Store;
import java.io.File;
public class CheckMapRegion {
    public static void main(String[] args) throws Exception {
        try (Store store = new Store(new File(args[0]))) {
            store.load();
            Index maps = store.getIndex(IndexType.MAPS);
            for (int i = 1; i < args.length; i++) {
                int regionId = Integer.parseInt(args[i]);
                int rx = regionId >> 8, ry = regionId & 0xFF;
                String terrainName = "l" + rx + "_" + ry;
                String locName = "m" + rx + "_" + ry;
                int terrainHash = archiveHash(terrainName);
                int locHash = archiveHash(locName);
                Archive terrain = findByNameHash(maps, terrainHash);
                Archive loc = findByNameHash(maps, locHash);
                System.out.println("region " + regionId + " (" + rx + "," + ry + "): terrain(l_" + rx + "_" + ry + ")="
                        + (terrain != null ? "FOUND id=" + terrain.getArchiveId() : "not found by name hash")
                        + " loc(m_" + rx + "_" + ry + ")="
                        + (loc != null ? "FOUND id=" + loc.getArchiveId() : "not found by name hash"));
            }
        }
    }
    static Archive findByNameHash(Index index, int hash) {
        for (Archive a : index.getArchives()) {
            if (a.getNameHash() == hash) return a;
        }
        return null;
    }
    static int archiveHash(String name) {
        int hash = 0;
        String upper = name.toUpperCase();
        for (int i = 0; i < upper.length(); i++) {
            hash = hash * 61 + upper.charAt(i) - 32;
        }
        return hash;
    }
}
