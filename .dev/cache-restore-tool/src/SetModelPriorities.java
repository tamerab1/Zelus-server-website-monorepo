import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Store;

import java.io.File;
import java.nio.file.Files;
import java.util.*;

// Face render priorities for ripped worn models (2026-10-18). The client's software and GPU renderers draw a merged
// player model in priority layers, so a worn model that puts every face on one layer fights the body under it: a
// chestplate all on 10 painted over the head and neck (117HD hid it with its depth buffer), a staff on 4 sank into the
// torso. Real gear layers by body part (Torva platebody: neck 2-3, torso 3, arms 10; Torva legs 1; held weapons 10), so
// a face takes the priority of its body part = its first vertex's skin group.
// Spec lines:   model <id> global <priority>
//               model <id> groups <defaultPriority> <group:priority,...>
// Writes <outDir>/<id>.dat (ModelType3Encoder, vertex skins re-packed, full decode round-trip) for
// InsertRawModels replace. Nothing is written to the cache.
// Usage: <cachePath> <specFile> <outDir>
public class SetModelPriorities {
    public static void main(String[] args) throws Exception {
        File out = new File(args[2]);
        out.mkdirs();
        try (Store store = new Store(new File(args[0]))) {
            store.load();
            for (String line : Files.readAllLines(new File(args[1]).toPath())) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] p = line.split("\\s+");
                int id = Integer.parseInt(p[1]);
                Archive ar = store.getIndex(IndexType.MODELS).getArchive(id);
                byte[] raw = ar.decompress(store.getStorage().loadArchive(ar));
                ModelDefinition orig = new ModelLoader().load(id, raw), m = new ModelLoader().load(id, raw);
                byte[] pri = new byte[m.faceCount];
                Map<Integer, Integer> hist = new TreeMap<>();
                if (p[2].equals("global")) {
                    Arrays.fill(pri, (byte) Integer.parseInt(p[3]));
                } else {
                    int def = Integer.parseInt(p[3]);
                    Map<Integer, Integer> byGroup = new HashMap<>();
                    for (String gp : p[4].split(",")) {
                        String[] kv = gp.split(":");
                        byGroup.put(Integer.parseInt(kv[0]), Integer.parseInt(kv[1]));
                    }
                    int[] label = new int[m.vertexCount];
                    Arrays.fill(label, -1);
                    int[][] groups = m.getVertexGroups();
                    for (int g = 0; groups != null && g < groups.length; g++)
                        if (groups[g] != null)
                            for (int v : groups[g]) label[v] = g;
                    for (int f = 0; f < m.faceCount; f++)
                        pri[f] = (byte) (int) byGroup.getOrDefault(label[m.faceIndices1[f]], def);
                }
                for (byte b : pri) hist.merge((int) b, 1, Integer::sum);
                m.faceRenderPriorities = pri;
                m.priority = 0;
                if (m.packedVertexGroups == null && m.getVertexGroups() != null)
                    m.packedVertexGroups = TextureModelFaces.packVertexGroups(m.getVertexGroups(), m.vertexCount);
                byte[] enc = ModelType3Encoder.encode(m);
                ModelDefinition back = new ModelLoader().load(id, enc);
                check(back.vertexCount == orig.vertexCount && back.faceCount == orig.faceCount, id + ": counts");
                check(Arrays.equals(back.vertexX, orig.vertexX) && Arrays.equals(back.vertexY, orig.vertexY)
                        && Arrays.equals(back.vertexZ, orig.vertexZ), id + ": vertices");
                check(Arrays.equals(back.faceIndices1, orig.faceIndices1) && Arrays.equals(back.faceIndices2, orig.faceIndices2)
                        && Arrays.equals(back.faceIndices3, orig.faceIndices3), id + ": faces");
                check(Arrays.equals(back.faceColors, orig.faceColors), id + ": colours");
                check(Arrays.equals(back.faceTextures, orig.faceTextures), id + ": textures");
                check(Arrays.equals(back.faceTransparencies, orig.faceTransparencies), id + ": transparency");
                check(Arrays.deepEquals(back.getVertexGroups(), orig.getVertexGroups()), id + ": vertex skins");
                check(Arrays.equals(back.packedTransparencyVertexGroups, orig.packedTransparencyVertexGroups), id + ": face skins");
                check(Arrays.equals(back.faceRenderPriorities, pri), id + ": priorities");
                Files.write(new File(out, id + ".dat").toPath(), enc);
                System.out.printf("model %d: priorities %s (was %s), %d bytes%n", id, hist,
                        orig.faceRenderPriorities == null ? "global " + orig.priority : "per-face", enc.length);
            }
        }
    }

    static void check(boolean ok, String what) {
        if (!ok) throw new IllegalStateException("round-trip failed: " + what);
    }
}
