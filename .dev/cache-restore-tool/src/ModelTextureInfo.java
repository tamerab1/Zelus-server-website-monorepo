import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.fs.*;

import java.io.File;
import java.util.*;

// Read-only: per model, face colour -> {faces, textured faces, texture ids, alpha} so a recolour can tell the flat
// stone / trim colours from textured or translucent (liquid) faces. Usage: <cachePath> <modelId>[,<modelId>...]
public class ModelTextureInfo {
    public static void main(String[] args) throws Exception {
        try (Store store = new Store(new File(args[0]))) {
            store.load();
            Storage storage = store.getStorage();
            for (String s : args[1].split(",")) {
                int id = Integer.parseInt(s.trim());
                Archive a = store.getIndex(IndexType.MODELS).getArchive(id);
                ModelDefinition m = new ModelLoader().load(id, a.decompress(storage.loadArchive(a)));
                Map<Integer, int[]> stats = new TreeMap<>();
                Map<Integer, Set<Integer>> tex = new TreeMap<>();
                for (int f = 0; f < m.faceCount; f++) {
                    int c = m.faceColors[f] & 0xffff;
                    int[] st = stats.computeIfAbsent(c, k -> new int[3]);
                    st[0]++;
                    if (m.faceTextures != null && m.faceTextures[f] != -1) {
                        st[1]++;
                        tex.computeIfAbsent(c, k -> new TreeSet<>()).add((int) m.faceTextures[f]);
                    }
                    if (m.faceTransparencies != null && m.faceTransparencies[f] != 0)
                        st[2] = Math.max(st[2], m.faceTransparencies[f] & 0xff);
                }
                System.out.println("model " + id + " faces=" + m.faceCount);
                for (var e : stats.entrySet()) {
                    int c = e.getKey();
                    System.out.printf("  %5d h=%2d s=%d l=%3d faces=%3d textured=%3d tex=%s alpha=%d%n", c, c >> 10, (c >> 7) & 7, c & 127,
                            e.getValue()[0], e.getValue()[1], tex.getOrDefault(c, Set.of()), e.getValue()[2]);
                }
            }
        }
    }
}
