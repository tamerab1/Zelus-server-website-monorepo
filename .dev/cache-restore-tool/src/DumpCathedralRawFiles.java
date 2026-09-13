import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;
import java.io.File;
import java.nio.file.Files;
import java.util.List;

public class DumpCathedralRawFiles {
    public static void main(String[] args) throws Exception {
        try (Store store = new Store(new File(args[0]))) {
            store.load();
            Storage storage = store.getStorage();
            Index maps = store.getIndex(IndexType.MAPS);
            Archive group = maps.getArchive(10018);
            byte[] decompressed = group.decompress(storage.loadArchive(group));
            FileData[] fileData = group.getFileData();
            List<byte[]> contents = SpliceItemOption.splitChunks(decompressed, fileData.length);
            for (int i = 0; i < fileData.length; i++) {
                if (fileData[i].getId() == 0) Files.write(new File(args[1]).toPath(), contents.get(i));
                if (fileData[i].getId() == 1) Files.write(new File(args[2]).toPath(), contents.get(i));
            }
            System.out.println("wrote terrain+locs to disk");
        }
    }
}
