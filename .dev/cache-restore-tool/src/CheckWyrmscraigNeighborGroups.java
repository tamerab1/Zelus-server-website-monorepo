import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;
import java.io.File;
import java.util.List;

// Read-only, one-off: do the 4 mapsquares adjacent to the Cathedral's own (39,34) actually exist,
// with real terrain+loc sub-files, in the source cache? Group id = (x<<8)|z, same scheme
// ImportCathedralMap.java already used for 39,34 (=10018).
public class CheckWyrmscraigNeighborGroups {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        int[][] squares = {{38, 34}, {40, 34}, {39, 33}, {39, 35}};
        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            var maps = store.getIndex(IndexType.MAPS);
            for (int[] sq : squares) {
                int x = sq[0], z = sq[1];
                int groupId = (x << 8) | z;
                Archive a = maps.getArchive(groupId);
                if (a == null) {
                    System.out.println("(" + x + "," + z + ") group " + groupId + ": NOT FOUND");
                    continue;
                }
                byte[] decompressed = a.decompress(storage.loadArchive(a));
                FileData[] fd = a.getFileData();
                int terrainLen = -1, locLen = -1;
                List<byte[]> contents = SpliceItemOption.splitChunks(decompressed, fd.length);
                for (int i = 0; i < fd.length; i++) {
                    if (fd[i].getId() == 0) terrainLen = contents.get(i).length;
                    if (fd[i].getId() == 1) locLen = contents.get(i).length;
                }
                System.out.println("(" + x + "," + z + ") group " + groupId + ": FOUND, " + fd.length
                        + " sub-file(s), terrain=" + terrainLen + " bytes, locs=" + locLen + " bytes");
            }
        }
    }
}
