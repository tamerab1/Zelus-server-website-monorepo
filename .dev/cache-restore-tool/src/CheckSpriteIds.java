import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Store;
import java.io.File;

public class CheckSpriteIds {
    public static void main(String[] args) throws Exception {
        try (Store store = new Store(new File(args[0]))) {
            store.load();
            Index sprites = store.getIndex(IndexType.SPRITES);
            for (int i = 1; i < args.length; i++) {
                int id = Integer.parseInt(args[i]);
                System.out.println(id + ": " + (sprites.getArchive(id) != null ? "TAKEN" : "free"));
            }
        }
    }
}
