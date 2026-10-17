import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

// Read-only: the first run of N consecutive free ids at or above a floor, for sprites, models and objects.
// Usage: <cachePath> <spriteFloor> <spriteCount> <modelFloor> <objectFloor>
public class FindFreeIdRuns {
    public static void main(String[] args) throws Exception {
        try (Store store = new Store(new File(args[0]))) {
            store.load();
            Index sprites = store.getIndex(IndexType.SPRITES), models = store.getIndex(IndexType.MODELS);
            int s = run(sprites, Integer.parseInt(args[1]), Integer.parseInt(args[2]));
            int m = run(models, Integer.parseInt(args[3]), 1);
            Set<Integer> objs = new HashSet<>();
            for (FileData fd : store.getIndex(IndexType.CONFIGS).getArchive(ConfigType.OBJECT.getId()).getFileData())
                objs.add(fd.getId());
            int o = Integer.parseInt(args[4]);
            while (objs.contains(o)) o++;
            System.out.println("sprites " + s + "-" + (s + Integer.parseInt(args[2]) - 1) + "  model " + m + "  object " + o);
        }
    }

    static int run(Index index, int floor, int count) {
        for (int start = floor; ; start++) {
            boolean ok = true;
            for (int i = 0; i < count && ok; i++)
                ok = index.getArchive(start + i) == null;
            if (ok) return start;
        }
    }
}
