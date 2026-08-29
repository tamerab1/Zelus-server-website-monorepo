import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Store;

import java.io.File;
import java.util.List;

public class CheckSpriteOrder {
    public static void main(String[] args) throws Exception {
        try (Store store = new Store(new File(args[0]))) {
            store.load();
            Index sprites = store.getIndex(IndexType.SPRITES);
            List<Archive> archives = sprites.getArchives();
            System.out.println("archive count=" + archives.size());
            System.out.println("last 10 archives in list order:");
            for (int i = Math.max(0, archives.size()-10); i < archives.size(); i++) {
                System.out.println("  [" + i + "] id=" + archives.get(i).getArchiveId());
            }
            int violations = 0;
            for (int i = 1; i < archives.size(); i++) {
                if (archives.get(i).getArchiveId() <= archives.get(i-1).getArchiveId()) violations++;
            }
            System.out.println("ordering violations: " + violations);
        }
    }
}
