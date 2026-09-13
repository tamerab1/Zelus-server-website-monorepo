import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import java.io.File;
import java.util.Arrays;

// Read-only. Confirms the two newly-written archives round-trip byte-identical against the
// original source extraction, and that Region.java's own lookup convention (name hash search) now
// resolves them.
public class VerifyCathedralMap {
    public static void main(String[] args) throws Exception {
        String zelusPath = args[0];
        byte[] expectedTerrain = readAllBytes(args[1]);
        byte[] expectedLocs = readAllBytes(args[2]);
        try (Store store = new Store(new File(zelusPath))) {
            store.load();
            Storage storage = store.getStorage();
            Index maps = store.getIndex(IndexType.MAPS);

            int terrainId = findByName(maps, "m39_34");
            int locId = findByName(maps, "l39_34");
            System.out.println("m39_34 resolved to archive id " + terrainId);
            System.out.println("l39_34 resolved to archive id " + locId);

            Archive terrainArchive = maps.getArchive(terrainId);
            Archive locArchive = maps.getArchive(locId);
            byte[] terrainReadback = terrainArchive.decompress(storage.loadArchive(terrainArchive));
            byte[] locReadback = locArchive.decompress(storage.loadArchive(locArchive));

            System.out.println("terrain match: " + Arrays.equals(terrainReadback, expectedTerrain) + " (" + terrainReadback.length + " bytes)");
            System.out.println("locs match: " + Arrays.equals(locReadback, expectedLocs) + " (" + locReadback.length + " bytes)");
        }
    }

    static int findByName(Index index, String name) {
        int hash = 0;
        for (int i = 0; i < name.length(); i++) hash = (hash << 5) - hash + name.charAt(i);
        for (Archive a : index.getArchives()) {
            if (a.getNameHash() == hash) return a.getArchiveId();
        }
        return -1;
    }

    static byte[] readAllBytes(String path) throws Exception {
        return java.nio.file.Files.readAllBytes(new File(path).toPath());
    }
}
