import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.fs.Store;

import java.io.File;
import java.util.*;

// Read-only. Splits a model into connected parts (vertices joined by faces) and prints each part's vertex/face
// count, bounds (y grows downward), centroid, colours and current skin labels. Used to find floating pieces
// (an orb or emblem hovering above a base) that can be given their own vertex label for a loc animation.
// Usage: AnalyzeModelParts <cachePath> <model[,model...]>
public class AnalyzeModelParts {
    public static void main(String[] args) throws Exception {
        try (Store store = new Store(new File(args[0]))) {
            store.load();
            for (String s : args[1].split(",")) {
                ModelDefinition m = BuildTierPets.model(store, Integer.parseInt(s));
                int[] part = parts(m);
                int n = Arrays.stream(part).max().orElse(-1) + 1;
                System.out.println("model " + s + ": " + m.vertexCount + " verts, " + m.faceCount + " faces, " + n + " parts, skins="
                        + (m.packedVertexGroups == null ? "none" : new TreeSet<>(box(m.packedVertexGroups))));
                for (int p = 0; p < n; p++) {
                    int vc = 0, fc = 0; long sx = 0, sy = 0, sz = 0;
                    int x0 = 1 << 30, x1 = -x0, y0 = x0, y1 = -x0, z0 = x0, z1 = -x0;
                    for (int v = 0; v < m.vertexCount; v++) if (part[v] == p) {
                        vc++; sx += m.vertexX[v]; sy += m.vertexY[v]; sz += m.vertexZ[v];
                        x0 = Math.min(x0, m.vertexX[v]); x1 = Math.max(x1, m.vertexX[v]);
                        y0 = Math.min(y0, m.vertexY[v]); y1 = Math.max(y1, m.vertexY[v]);
                        z0 = Math.min(z0, m.vertexZ[v]); z1 = Math.max(z1, m.vertexZ[v]);
                    }
                    if (vc == 0) continue;
                    TreeMap<Integer, Integer> cols = new TreeMap<>();
                    for (int f = 0; f < m.faceCount; f++) if (part[m.faceIndices1[f]] == p) {
                        fc++; cols.merge(m.faceColors[f] & 0xFFFF, 1, Integer::sum);
                    }
                    TreeMap<Integer,Integer> lab = new TreeMap<>(); if (m.packedVertexGroups != null) for (int v = 0; v < m.vertexCount; v++) if (part[v] == p) lab.merge(m.packedVertexGroups[v], 1, Integer::sum);
                    System.out.printf("  part %d labels=" + lab + ": %d v %d f  x[%d,%d] y[%d,%d] z[%d,%d] c=(%d,%d,%d) colours=%s%n", p, vc, fc,
                            x0, x1, y0, y1, z0, z1, sx / vc, sy / vc, sz / vc, cols.size() > 8 ? cols.size() + " distinct" : cols.toString());
                }
            }
        }
    }

    static List<Integer> box(int[] a) { List<Integer> l = new ArrayList<>(); for (int x : a) l.add(x); return l; }

    /** union-find over face edges; parts numbered in order of first vertex. */
    static int[] parts(ModelDefinition m) {
        int[] uf = new int[m.vertexCount];
        for (int i = 0; i < uf.length; i++) uf[i] = i;
        for (int f = 0; f < m.faceCount; f++) {
            union(uf, m.faceIndices1[f], m.faceIndices2[f]);
            union(uf, m.faceIndices1[f], m.faceIndices3[f]);
        }
        Map<Integer, Integer> ids = new HashMap<>();
        int[] out = new int[m.vertexCount];
        for (int v = 0; v < uf.length; v++) {
            int r = find(uf, v);
            boolean used = false;
            for (int f = 0; f < m.faceCount && !used; f++) used = m.faceIndices1[f] == v || m.faceIndices2[f] == v || m.faceIndices3[f] == v;
            out[v] = used ? ids.computeIfAbsent(r, k -> ids.size()) : -1;
        }
        return out;
    }

    static int find(int[] uf, int v) { while (uf[v] != v) v = uf[v] = uf[uf[v]]; return v; }
    static void union(int[] uf, int a, int b) { uf[find(uf, a)] = find(uf, b); }
}
