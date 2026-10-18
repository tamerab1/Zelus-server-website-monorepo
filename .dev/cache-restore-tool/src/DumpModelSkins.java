import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Store;

import java.io.File;
import java.util.Map;
import java.util.TreeMap;

// Read-only (2026-10-18): per model, the vertex skin labels (the animation groups its vertices follow, with counts and
// each group's centre), the face render priorities (global or per-face histogram), transparency and the bounding box --
// to compare ripped worn models with the originals they replace.
// Usage: <cachePath> <modelId> [modelId ...]
public class DumpModelSkins {
    public static void main(String[] args) throws Exception {
        try (Store store = new Store(new File(args[0]))) {
            store.load();
            for (int a = 1; a < args.length; a++) {
                int id = Integer.parseInt(args[a]);
                Archive ar = store.getIndex(IndexType.MODELS).getArchive(id);
                byte[] raw = ar.decompress(store.getStorage().loadArchive(ar));
                ModelDefinition m = new ModelLoader().load(id, raw);
                int[][] groups = m.getVertexGroups();
                Map<Integer, int[]> skins = new TreeMap<>(); // label -> count, sumX, sumY, sumZ
                if (groups != null)
                    for (int g = 0; g < groups.length; g++)
                        if (groups[g] != null)
                            for (int v : groups[g]) {
                                int[] s = skins.computeIfAbsent(g, k -> new int[4]);
                                s[0]++; s[1] += m.vertexX[v]; s[2] += m.vertexY[v]; s[3] += m.vertexZ[v];
                            }
                StringBuilder sb = new StringBuilder();
                for (Map.Entry<Integer, int[]> e : skins.entrySet()) {
                    int[] s = e.getValue();
                    sb.append(e.getKey()).append("x").append(s[0]).append("@(").append(s[1] / s[0]).append(",")
                            .append(s[2] / s[0]).append(",").append(s[3] / s[0]).append(") ");
                }
                Map<Integer, Integer> pri = new TreeMap<>();
                if (m.faceRenderPriorities != null)
                    for (byte p : m.faceRenderPriorities) pri.merge((int) p, 1, Integer::sum);
                int trans = 0;
                if (m.faceTransparencies != null)
                    for (byte t : m.faceTransparencies) if (t != 0) trans++;
                int t1 = raw[raw.length - 2] & 0xff, t2 = raw[raw.length - 1] & 0xff;
                System.out.printf("model %d [%s] v=%d f=%d priority=%s trans=%d skins: %s%n", id,
                        t2 == 0xff ? (t1 == 0xfd ? "type3" : t1 == 0xfe ? "type2" : t1 == 0xff ? "type1" : "?") : "old",
                        m.vertexCount, m.faceCount, m.faceRenderPriorities == null ? "global " + m.priority : pri.toString(), trans, sb);
            }
        }
    }
}
