import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;
import java.io.File;
import java.util.List;

// Read-only. Extracts sub-file 0 (terrain) and sub-file 1 (locs) from the source cache's unnamed
// MAPS group 10018 (mapsquare 39,34, the Fallen Cathedral -- confirmed against MadAngelEntrance.kt's
// own CATHEDRAL_TEMPLATE = copyAllLevels(312, 272), i.e. zone (312,272) = mapsquare (39,34)) and
// reports their sizes so they can be sanity-checked before writing anywhere.
public class DumpCathedralGroupFiles {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        int groupId = Integer.parseInt(args[1]);
        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Index maps = store.getIndex(IndexType.MAPS);
            Archive group = maps.getArchive(groupId);
            if (group == null) { System.out.println("group " + groupId + " not found"); return; }
            byte[] decompressed = group.decompress(storage.loadArchive(group));
            FileData[] fileData = group.getFileData();
            List<byte[]> contents = SpliceItemOption.splitChunks(decompressed, fileData.length);
            for (int i = 0; i < fileData.length; i++) {
                System.out.println("  subfile id=" + fileData[i].getId() + " size=" + contents.get(i).length + " bytes");
            }
        }
    }
}
