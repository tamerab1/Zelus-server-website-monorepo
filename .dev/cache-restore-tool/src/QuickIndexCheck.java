import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Store;
import java.io.File;

public class QuickIndexCheck {
    public static void main(String[] args) throws Exception {
        try (Store store = new Store(new File(args[0]))) {
            store.load();
            for (IndexType t : IndexType.values()) {
                try {
                    Index idx = store.getIndex(t);
                    int count = idx.getArchives().size();
                    int maxId = idx.getArchives().stream().mapToInt(a -> a.getArchiveId()).max().orElse(-1);
                    System.out.println(t + " (id=" + t.getNumber() + "): archives=" + count + " maxId=" + maxId);
                } catch (Exception e) {
                    System.out.println(t + ": ERROR " + e);
                }
            }
        }
    }
}
