import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;
import java.io.File;
import java.util.List;

// Read-only, one-off: what ARE the 5 sub-files in a modern map group (this session's cathedral
// import only ever used sub-files 0/1 -- terrain/locs -- for a group that also had 5).
public class DumpMapGroupFileIds {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        int groupId = Integer.parseInt(args[1]);
        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            var maps = store.getIndex(IndexType.MAPS);
            Archive a = maps.getArchive(groupId);
            byte[] decompressed = a.decompress(storage.loadArchive(a));
            FileData[] fd = a.getFileData();
            List<byte[]> contents = SpliceItemOption.splitChunks(decompressed, fd.length);
            for (int i = 0; i < fd.length; i++) {
                System.out.println("sub-file id=" + fd[i].getId() + " len=" + contents.get(i).length);
            }
        }
    }
}
