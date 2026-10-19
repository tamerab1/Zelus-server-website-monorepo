import net.runelite.cache.IndexType;
import net.runelite.cache.fs.*;
import java.io.File;

// Read-only (2026-10-19): highest texture / sprite ids and the first free sprite ids at or above a start.
// Usage: FreeSpriteIds <cache> <start>
public class FreeSpriteIds {
    public static void main(String[] args) throws Exception {
        try (Store store = new Store(new File(args[0]))) {
            store.load();
            Index sp = store.getIndex(IndexType.SPRITES);
            int max = sp.getArchives().stream().mapToInt(Archive::getArchiveId).max().orElse(-1);
            int maxTex = -1;
            for (Archive a : store.getIndex(IndexType.TEXTURES).getArchives())
                for (var fd : a.getFileData()) maxTex = Math.max(maxTex, fd.getId());
            StringBuilder free = new StringBuilder();
            for (int id = Integer.parseInt(args[1]), n = 0; n < 6; id++) if (sp.getArchive(id) == null) { free.append(id).append(' '); n++; }
            System.out.println("max sprite " + max + ", max texture " + maxTex + ", free sprites: " + free);
        }
    }
}
