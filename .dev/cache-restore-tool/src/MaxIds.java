import net.runelite.cache.*;
import net.runelite.cache.fs.*;
import net.runelite.cache.index.FileData;
import java.io.File;
import java.util.*;

// Read-only (2026-10-20): highest model / npc / seq ids and the first free runs above the custom ranges.
// Usage: MaxIds <cache> <fromModel> <fromNpc> <count>
public class MaxIds {
    public static void main(String[] a) throws Exception {
        try (Store store = new Store(new File(a[0]))) {
            store.load();
            Index models = store.getIndex(IndexType.MODELS);
            Archive npc = store.getIndex(IndexType.CONFIGS).getArchive(ConfigType.NPC.getId());
            Set<Integer> npcIds = new HashSet<>();
            for (FileData fd : npc.getFileData()) npcIds.add(fd.getId());
            int maxM = 0; for (Archive ar : models.getArchives()) maxM = Math.max(maxM, ar.getArchiveId());
            System.out.println("max model " + maxM + ", npcs " + npcIds.size() + " max npc " + Collections.max(npcIds));
            int n = Integer.parseInt(a[3]);
            System.out.println("free models from " + a[1] + ": " + run(id -> models.getArchive(id) == null, Integer.parseInt(a[1]), n));
            System.out.println("free npcs from " + a[2] + ": " + run(id -> !npcIds.contains(id), Integer.parseInt(a[2]), n));
            List<Integer> used = new ArrayList<>();
            for (int id = Integer.parseInt(a[2]); id < Integer.parseInt(a[2]) + 40; id++) if (npcIds.contains(id)) used.add(id);
            System.out.println("used npcs in that window: " + used);
        }
    }
    static List<Integer> run(java.util.function.IntPredicate free, int from, int n) {
        for (int s = from; s < 70000; s++) {
            boolean ok = true;
            for (int k = 0; k < n; k++) if (!free.test(s + k)) { ok = false; break; }
            if (ok) { List<Integer> r = new ArrayList<>(); for (int k = 0; k < n; k++) r.add(s + k); return r; }
        }
        return null;
    }
}
