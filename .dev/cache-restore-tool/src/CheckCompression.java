import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Store;
import java.io.File;

public class CheckCompression {
    public static void main(String[] args) throws Exception {
        try (Store store = new Store(new File(args[0]))) {
            store.load();
            Index models = store.getIndex(IndexType.MODELS);
            for (int i = 1; i < args.length; i++) {
                int id = Integer.parseInt(args[i]);
                Archive a = models.getArchive(id);
                System.out.println(id + ": compression=" + a.getCompression());
            }
            // sample some other, unrelated, definitely-normal models
            System.out.println("--- sample of other model ids ---");
            for (int id : new int[]{1,100,1000,10000,27633}) {
                Archive a = models.getArchive(id);
                if (a != null) System.out.println(id + ": compression=" + a.getCompression());
            }
        }
    }
}
