import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

public class DumpMapArchiveRaw {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        int archiveId = Integer.parseInt(args[1]);
        String outPath = args[2];
        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Archive archive = store.getIndex(IndexType.MAPS).getArchive(archiveId);
            byte[] raw = archive.decompress(storage.loadArchive(archive));
            Files.write(Paths.get(outPath), raw);
            System.out.println("archive " + archiveId + " decompressed size=" + raw.length + " revision=" + archive.getRevision());
        }
    }
}
