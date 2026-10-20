import net.runelite.cache.*;
import net.runelite.cache.fs.*;
import net.runelite.cache.index.FileData;
import java.io.File;
import java.util.*;

// Read-only (2026-10-20): max item id and the first free run of item ids above a start.
// Usage: FreeItemIds <cache> <from> <count>
public class FreeItemIds {
    public static void main(String[] a) throws Exception {
        try (Store store = new Store(new File(a[0]))) {
            store.load();
            Archive items = store.getIndex(IndexType.CONFIGS).getArchive(ConfigType.ITEM.getId());
            Set<Integer> ids = new HashSet<>();
            for (FileData fd : items.getFileData()) ids.add(fd.getId());
            System.out.println("items " + ids.size() + " max " + Collections.max(ids));
            int from = Integer.parseInt(a[1]), n = Integer.parseInt(a[2]);
            for (int s = from; s < 70000; s++) {
                boolean ok = true;
                for (int k = 0; k < n; k++) if (ids.contains(s + k)) { ok = false; break; }
                if (ok) { System.out.println("free run " + s + ".." + (s + n - 1)); return; }
            }
        }
    }
}
