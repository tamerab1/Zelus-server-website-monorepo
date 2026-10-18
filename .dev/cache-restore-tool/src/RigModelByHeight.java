import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Store;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;

// Rigs an unrigged loc model for a loc animation (2026-10-18, the Discord Statue 61238): every vertex above a height
// (y < cutY; y grows downward) gets vertex skin label `upper`, the rest `lower`, so a loc sequence can move the upper
// part alone (BuildLocSpinAnims). Re-encoded with ModelType3Encoder and decoded back: geometry, colours and faces must
// be unchanged and the labels exactly as assigned. Writes <outDir>/<modelId>.dat for InsertRawModels replace.
// Usage: <cachePath> <modelId> <cutY> <upperLabel> <lowerLabel> <outDir>
public class RigModelByHeight {
    public static void main(String[] args) throws Exception {
        int id = Integer.parseInt(args[1]), cut = Integer.parseInt(args[2]);
        int upper = Integer.parseInt(args[3]), lower = Integer.parseInt(args[4]);
        try (Store store = new Store(new File(args[0]))) {
            store.load();
            Archive ar = store.getIndex(IndexType.MODELS).getArchive(id);
            byte[] raw = ar.decompress(store.getStorage().loadArchive(ar));
            ModelDefinition orig = new ModelLoader().load(id, raw), m = new ModelLoader().load(id, raw);
            if (orig.getVertexGroups() != null && Arrays.stream(orig.getVertexGroups()).anyMatch(g -> g != null && g.length > 0))
                throw new IllegalStateException("model " + id + " is already rigged");
            int[] labels = new int[m.vertexCount];
            int up = 0;
            for (int v = 0; v < m.vertexCount; v++) {
                labels[v] = m.vertexY[v] < cut ? upper : lower;
                if (labels[v] == upper) up++;
            }
            m.packedVertexGroups = labels;
            byte[] enc = ModelType3Encoder.encode(m);
            ModelDefinition back = new ModelLoader().load(id, enc);
            check(back.vertexCount == orig.vertexCount && back.faceCount == orig.faceCount, "counts");
            check(Arrays.equals(back.vertexX, orig.vertexX) && Arrays.equals(back.vertexY, orig.vertexY)
                    && Arrays.equals(back.vertexZ, orig.vertexZ), "vertices");
            check(Arrays.equals(back.faceIndices1, orig.faceIndices1) && Arrays.equals(back.faceIndices2, orig.faceIndices2)
                    && Arrays.equals(back.faceIndices3, orig.faceIndices3), "faces");
            check(Arrays.equals(back.faceColors, orig.faceColors), "colours");
            int[][] groups = back.getVertexGroups();
            check(groups != null && groups.length > upper && groups[upper].length == up
                    && groups[lower].length == m.vertexCount - up, "labels");
            File out = new File(args[5]);
            out.mkdirs();
            Files.write(new File(out, id + ".dat").toPath(), enc);
            System.out.printf("model %d rigged: %d vertices label %d (y < %d), %d label %d, %d bytes%n", id, up, upper, cut,
                    m.vertexCount - up, lower, enc.length);
        }
    }

    static void check(boolean ok, String what) {
        if (!ok) throw new IllegalStateException("round-trip failed: " + what);
    }
}
