import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Store;
import java.io.File;

public class ProbeModelRange {
    public static void main(String[] args) throws Exception {
        String path = args[0];
        int lo = Integer.parseInt(args[1]);
        int hi = Integer.parseInt(args[2]);
        try (Store store = new Store(new File(path))) {
            store.load();
            var index = store.getIndex(IndexType.MODELS);
            for (int id = lo; id <= hi; id++) {
                boolean exists = index.getArchive(id) != null;
                if (exists) System.out.println(id + " -> EXISTS");
            }
        }
    }
}
