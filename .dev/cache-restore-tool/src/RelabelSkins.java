import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.fs.Store;

import java.io.File;
import java.nio.file.Files;
import java.util.*;

// Re-rigs mislabelled vertices of a worn model. Rule "from:to:yMin:yMax" moves every vertex with skin label `from`
// whose y lies in [yMin, yMax] to label `to` (y grows downward). Used for the Draconic / Draconies platebodies, whose
// whole upper arm sat on the elbow labels 20/26 instead of the upper-arm labels 21/25 (Torva and the arm kit use 21/25
// for y ~ -169..-145, 20/26 only for the elbow ring, 19/22 for the forearm): under animation the sleeve pivoted at the
// elbow and tore away from the shoulder. Geometry/colours untouched; re-encoded with ModelType3Encoder, round-trip checked.
// Usage: RelabelSkins <cachePath> <outDir> <model[,model...]> <from:to:yMin:yMax> [...]   (then InsertRawModels replace)
public class RelabelSkins {
    public static void main(String[] args) throws Exception {
        File out = new File(args[1]);
        out.mkdirs();
        List<int[]> rules = new ArrayList<>();
        for (int i = 3; i < args.length; i++)
            rules.add(Arrays.stream(args[i].split(":")).mapToInt(Integer::parseInt).toArray());
        try (Store store = new Store(new File(args[0]))) {
            store.load();
            for (String s : args[2].split(",")) {
                int id = Integer.parseInt(s);
                ModelDefinition m = BuildTierPets.model(store, id);
                if (m.packedVertexGroups == null)
                    throw new IllegalStateException("model " + id + " has no skins");
                Map<String, Integer> moved = new TreeMap<>();
                for (int v = 0; v < m.vertexCount; v++)
                    for (int[] r : rules)
                        if (m.packedVertexGroups[v] == r[0] && m.vertexY[v] >= r[2] && m.vertexY[v] <= r[3]) {
                            m.packedVertexGroups[v] = r[1];
                            moved.merge(r[0] + "->" + r[1], 1, Integer::sum);
                            break;
                        }
                byte[] enc = ModelType3Encoder.encode(m);
                ModelDefinition back = new ModelLoader().load(id, enc);
                int[] backSkins = back.packedVertexGroups != null ? back.packedVertexGroups
                        : TextureModelFaces.packVertexGroups(back.getVertexGroups(), back.vertexCount);
                if (back.vertexCount != m.vertexCount || back.faceCount != m.faceCount || !Arrays.equals(backSkins, m.packedVertexGroups))
                    throw new IllegalStateException("model " + id + ": round trip mismatch");
                Files.write(new File(out, id + ".dat").toPath(), enc);
                System.out.println("model " + id + ": relabelled " + moved);
            }
        }
    }
}
