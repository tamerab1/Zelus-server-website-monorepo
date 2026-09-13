import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Store;
import java.io.File;
public class CheckFrameArchives {
    public static void main(String[] args) throws Exception {
        try (Store store = new Store(new File(args[0]))) {
            store.load();
            Index animations = store.getIndex(IndexType.ANIMATIONS);
            for (int i = 1; i < args.length; i++) {
                int id = Integer.parseInt(args[i]);
                Archive a = animations.getArchive(id);
                System.out.println("frame archive " + id + ": " + (a == null ? "FREE" : "OCCUPIED"));
            }
        }
    }
}
