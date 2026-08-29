import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Store;

import java.io.File;
import java.util.List;

public class CountSprites {
    public static void main(String[] args) throws Exception {
        try (Store store = new Store(new File(args[0]))) {
            store.load();
            Index sprites = store.getIndex(IndexType.SPRITES);
            List<Archive> archives = sprites.getArchives();
            int max = 0;
            for (Archive a : archives) max = Math.max(max, a.getArchiveId());
            System.out.println("total archives=" + archives.size() + " maxId=" + max);
            int count90k = 0;
            for (Archive a : archives) if (a.getArchiveId() >= 89000) { count90k++; System.out.println("  found high id: " + a.getArchiveId()); }
            System.out.println("archives with id>=89000: " + count90k);
        }
    }
}
