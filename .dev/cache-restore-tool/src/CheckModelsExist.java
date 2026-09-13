import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Store;
import java.io.File;
public class CheckModelsExist {
    public static void main(String[] args) throws Exception {
        try (Store store = new Store(new File(args[0]))) {
            store.load();
            Index models = store.getIndex(IndexType.MODELS);
            int missing = 0;
            for (int i = 1; i < args.length; i++) {
                int id = Integer.parseInt(args[i]);
                Archive a = models.getArchive(id);
                if (a == null) { System.out.println("MISSING model " + id); missing++; }
            }
            System.out.println("checked " + (args.length-1) + ", missing " + missing);
        }
    }
}
