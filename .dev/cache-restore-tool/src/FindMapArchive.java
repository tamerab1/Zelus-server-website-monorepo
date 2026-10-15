import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Store;
import java.io.File;

public class FindMapArchive {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        int rx = Integer.parseInt(args[1]);
        int ry = Integer.parseInt(args[2]);
        String terrainName = "m" + rx + "_" + ry;
        String locName = "l" + rx + "_" + ry;
        int terrainHash = javaHash(terrainName);
        int locHash = javaHash(locName);
        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Index maps = store.getIndex(IndexType.MAPS);
            Archive terrainArchive = null, locArchive = null;
            for (Archive a : maps.getArchives()) {
                if (a.getNameHash() == terrainHash) terrainArchive = a;
                if (a.getNameHash() == locHash) locArchive = a;
            }
            System.out.println(terrainName + " (hash=" + terrainHash + ") -> archive id " + (terrainArchive != null ? terrainArchive.getArchiveId() : "NOT FOUND")
                    + (terrainArchive != null ? " revision=" + terrainArchive.getRevision() + " compression=" + terrainArchive.getCompression() : ""));
            System.out.println(locName + " (hash=" + locHash + ") -> archive id " + (locArchive != null ? locArchive.getArchiveId() : "NOT FOUND")
                    + (locArchive != null ? " revision=" + locArchive.getRevision() + " compression=" + locArchive.getCompression() : ""));
        }
    }

    static int javaHash(String s) {
        int hash = 0;
        for (int i = 0; i < s.length(); i++) hash = (hash << 5) - hash + s.charAt(i);
        return hash;
    }
}
