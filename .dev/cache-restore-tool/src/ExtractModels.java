import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Store;

import java.io.File;
import java.nio.file.Files;

// Read-only (2026-10-19): writes the raw bytes of cache models to <outDir>/<id>.dat, e.g. to check what was really
// written (fd.id 0 checked too). Usage: ExtractModels <cache> <outDir> <id>...
public class ExtractModels {
    public static void main(String[] args) throws Exception {
        File out = new File(args[1]);
        out.mkdirs();
        try (Store store = new Store(new File(args[0]))) {
            store.load();
            for (int i = 2; i < args.length; i++) {
                int id = Integer.parseInt(args[i]);
                Archive a = store.getIndex(IndexType.MODELS).getArchive(id);
                if (a.getFileData().length != 1 || a.getFileData()[0].getId() != 0) throw new IllegalStateException(id + ": fd.id must be 0");
                Files.write(new File(out, id + ".dat").toPath(), a.decompress(store.getStorage().loadArchive(a)));
                System.out.println("model " + id + " ok");
            }
        }
    }
}
