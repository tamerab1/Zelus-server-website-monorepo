import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

public class DumpRawModelBytes {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        int modelId = Integer.parseInt(args[1]);
        String outPath = args[2];
        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Index models = store.getIndex(IndexType.MODELS);
            Archive archive = models.getArchive(modelId);
            byte[] raw = archive.decompress(storage.loadArchive(archive));
            Files.write(Paths.get(outPath), raw);
            System.out.println("wrote " + raw.length + " bytes to " + outPath);
        }
    }
}
