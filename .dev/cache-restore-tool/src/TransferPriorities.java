import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.fs.Store;

import java.io.File;
import java.nio.file.Files;
import java.util.*;

// Layers a ripped worn model like the real OSRS piece it replaces (2026-10-19, customs19 round 2). The GPU plugin draws
// faces 0-9 strictly by priority: the Vermilion masori body had 480 faces on 0 -- BELOW a cape's lining (1) -- so capes
// painted through the torso; the Vorkath body had 822 on 10, over the head. Each face takes the priority of the nearest
// face (centroid) of the reference model that sits on the same vertex skin label (same body part), falling back to the
// nearest face overall. Writes <outDir>/<id>.dat for InsertRawModels replace.
// Spec lines:   <modelId> <referenceModelId>
// Usage: TransferPriorities <cache> <spec> <outDir>
public class TransferPriorities {
    public static void main(String[] args) throws Exception {
        File out = new File(args[2]);
        out.mkdirs();
        try (Store store = new Store(new File(args[0]))) {
            store.load();
            for (String line : Files.readAllLines(new File(args[1]).toPath())) {
                line = line.replaceAll("#.*", "").trim();
                if (line.isEmpty()) continue;
                String[] p = line.split("\\s+");
                int id = Integer.parseInt(p[0]), refId = Integer.parseInt(p[1]);
                ModelDefinition m = BuildTierPets.model(store, id), ref = BuildTierPets.model(store, refId);
                int[] lab = RigView.labels(m), rlab = RigView.labels(ref);
                double[][] rc = centroids(ref);
                byte[] pri = new byte[m.faceCount];
                Map<Integer, Integer> before = hist(m), after = new TreeMap<>();
                double[][] mc = centroids(m);
                for (int f = 0; f < m.faceCount; f++) {
                    int l = lab[m.faceIndices1[f]];
                    int best = -1, any = -1;
                    double bd = Double.MAX_VALUE, ad = Double.MAX_VALUE;
                    for (int g = 0; g < ref.faceCount; g++) {
                        double d = sq(mc[f][0] - rc[g][0]) + sq(mc[f][1] - rc[g][1]) + sq(mc[f][2] - rc[g][2]);
                        if (d < ad) { ad = d; any = g; }
                        if (rlab[ref.faceIndices1[g]] == l && d < bd) { bd = d; best = g; }
                    }
                    int g = best >= 0 ? best : any;
                    pri[f] = ref.faceRenderPriorities == null ? ref.priority : ref.faceRenderPriorities[g];
                    after.merge((int) pri[f], 1, Integer::sum);
                }
                m.faceRenderPriorities = pri;
                byte[] enc = ModelType3Encoder.encode(m);
                ModelDefinition back = new ModelLoader().load(id, enc);
                if (back.faceCount != m.faceCount || !Arrays.equals(back.faceRenderPriorities, pri)
                        || !Arrays.deepEquals(back.getVertexGroups(), m.getVertexGroups()))
                    throw new IllegalStateException(id + ": round trip");
                Files.write(new File(out, id + ".dat").toPath(), enc);
                System.out.printf("model %d <- %d: priorities %s -> %s (reference %s)%n", id, refId, before, after, hist(ref));
            }
        }
    }

    static Map<Integer, Integer> hist(ModelDefinition m) {
        Map<Integer, Integer> h = new TreeMap<>();
        for (int f = 0; f < m.faceCount; f++)
            h.merge(m.faceRenderPriorities == null ? (int) m.priority : m.faceRenderPriorities[f], 1, Integer::sum);
        return h;
    }

    static double[][] centroids(ModelDefinition m) {
        double[][] c = new double[m.faceCount][3];
        for (int f = 0; f < m.faceCount; f++) {
            int a = m.faceIndices1[f], b = m.faceIndices2[f], d = m.faceIndices3[f];
            c[f][0] = (m.vertexX[a] + m.vertexX[b] + m.vertexX[d]) / 3.0;
            c[f][1] = (m.vertexY[a] + m.vertexY[b] + m.vertexY[d]) / 3.0;
            c[f][2] = (m.vertexZ[a] + m.vertexZ[b] + m.vertexZ[d]) / 3.0;
        }
        return c;
    }

    static double sq(double v) { return v * v; }
}
