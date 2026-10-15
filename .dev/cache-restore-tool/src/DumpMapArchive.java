import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import java.io.File;
import java.nio.file.Files;

public class DumpMapArchive {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        int archiveId = Integer.parseInt(args[1]);
        String outFile = args[2];
        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Index maps = store.getIndex(IndexType.MAPS);
            Archive a = maps.getArchive(archiveId);
            byte[] compressed = storage.loadArchive(a);
            byte[] decompressed = a.decompress(compressed);
            System.out.println("archive " + archiveId + ": compressed=" + compressed.length + " decompressed=" + decompressed.length);
            Files.write(new File(outFile).toPath(), decompressed);
        }
    }
}
