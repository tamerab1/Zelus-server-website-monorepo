import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Store;

import java.io.File;
import java.util.Map;
import java.util.TreeMap;

// Read-only (2026-10-18): for each model, which face render priorities its faces use per vertex skin group (a face's
// group = its first vertex's label) -- how a real worn model layers its body parts, to copy onto a ripped one.
// Usage: <cachePath> <modelId> [modelId ...]
public class DumpSkinPriorities {
    public static void main(String[] args) throws Exception {
        try (Store store = new Store(new File(args[0]))) {
            store.load();
            for (int a = 1; a < args.length; a++) {
                int id = Integer.parseInt(args[a]);
                Archive ar = store.getIndex(IndexType.MODELS).getArchive(id);
                ModelDefinition m = new ModelLoader().load(id, ar.decompress(store.getStorage().loadArchive(ar)));
                int[] label = new int[m.vertexCount];
                int[][] groups = m.getVertexGroups();
                java.util.Arrays.fill(label, -1);
                if (groups != null)
                    for (int g = 0; g < groups.length; g++)
                        if (groups[g] != null)
                            for (int v : groups[g]) label[v] = g;
                Map<Integer, Map<Integer, Integer>> byGroup = new TreeMap<>();
                for (int f = 0; f < m.faceCount; f++) {
                    int p = m.faceRenderPriorities == null ? m.priority : m.faceRenderPriorities[f];
                    byGroup.computeIfAbsent(label[m.faceIndices1[f]], k -> new TreeMap<>()).merge(p, 1, Integer::sum);
                }
                System.out.println("model " + id + " group -> {priority=faces}: " + byGroup);
            }
        }
    }
}
