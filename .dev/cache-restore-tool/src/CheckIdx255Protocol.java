import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import java.io.File;

public class CheckIdx255Protocol {
    public static void main(String[] args) throws Exception {
        try (Store store = new Store(new File(args[0]))) {
            store.load();
            Index index255 = store.getIndexes().stream().filter(i -> i.getId() == 255).findFirst().orElseThrow();
            Archive archive7 = index255.getArchive(7); // MODELS index's own reference-table entry
            byte[] raw = archive7.decompress(store.getStorage().loadArchive(archive7));
            System.out.println("protocol byte = " + (raw[0] & 0xFF));
            System.out.println("first 16 bytes = " + java.util.Arrays.toString(java.util.Arrays.copyOfRange(raw, 0, Math.min(16, raw.length))));
        }
    }
}
