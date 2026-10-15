import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Store;

import java.io.File;
import java.nio.file.Files;
import java.util.*;

// Gives an UNTEXTURED model an (animated) texture on chosen faces, for items whose models have no
// textured faces to retexture with item opcode 41 (party hats, santa hat, mystery box, dragon
// platelegs...). Every face whose raw colour is in the list (or every face for "all") gets
// faceTextures = texId; textureCoords stay "none" so the client maps the texture across each
// face's own three vertices. Output is re-encoded with ModelType3Encoder (the format the
// equipment-compositing path needs) and verified by a full decode round-trip: vertices, faces,
// colours and per-face textures must all match what was intended.
// Spec lines:
//   model <srcModelId> <newModelId> <texId> <hsl,hsl,...|all|auto[:fraction]>
// "auto" picks the model's main colours: skin tones (hue 3-5, sat 2-4, lum 55-95) and pure black are
// never textured; the rest are ranked by how many OPAQUE faces (transparency < 128) use them and
// taken until they cover `fraction` (default 0.6) of those faces. Transparent faces count for
// nothing in the ranking (worn whip-style ghost layers), but faces of a picked colour are all
// textured.
// Writes <outDir>/<newModelId>.dat for InsertRawModel; nothing is written to the cache.
// Usage: <cachePath> <specFile> <outDir>
public class TextureModelFaces {
    public static void main(String[] args) throws Exception {
        File out = new File(args[2]);
        out.mkdirs();
        try (Store store = new Store(new File(args[0]))) {
            store.load();
            Index models = store.getIndex(IndexType.MODELS);
            for (String l : Files.readAllLines(new File(args[1]).toPath())) {
                l = l.trim();
                if (l.isEmpty() || l.startsWith("#")) continue;
                String[] p = l.split("\\s+");
                int src = Integer.parseInt(p[1]), dst = Integer.parseInt(p[2]);
                short tex = Short.parseShort(p[3]);
                if (models.getArchive(dst) != null) throw new IllegalStateException("model " + dst + " already exists");
                Archive a = models.getArchive(src);
                byte[] raw = a.decompress(store.getStorage().loadArchive(a));
                ModelDefinition m = new ModelLoader().load(src, raw);
                ModelDefinition orig = new ModelLoader().load(src, raw);

                Set<Integer> colours = new HashSet<>();
                boolean all = p[4].equals("all");
                if (p[4].startsWith("auto")) {
                    double fraction = p[4].contains(":") ? Double.parseDouble(p[4].substring(5)) : 0.6;
                    colours = autoColours(m, fraction);
                } else if (!all) {
                    for (String c : p[4].split(",")) colours.add(Integer.parseInt(c) & 0xFFFF);
                }

                short[] ft = m.faceTextures != null ? m.faceTextures.clone() : new short[m.faceCount];
                if (m.faceTextures == null) Arrays.fill(ft, (short) -1);
                int n = 0;
                for (int f = 0; f < m.faceCount; f++) {
                    if (all || colours.contains(m.faceColors[f] & 0xFFFF)) {
                        ft[f] = tex;
                        if (m.textureCoords != null) m.textureCoords[f] = -1;
                        n++;
                    }
                }
                if (n == 0) throw new IllegalStateException("model " + src + ": no faces matched " + p[4]);
                m.faceTextures = ft;
                // ModelLoader builds vertexGroups (the animation skin table) and then NULLS the raw
                // packedVertexGroups it came from, so encoding the loaded model would silently drop
                // every vertex skin: worn items then freeze instead of following the player's
                // animations. Rebuild the packed per-vertex labels from the table first.
                if (m.packedVertexGroups == null && m.getVertexGroups() != null)
                    m.packedVertexGroups = packVertexGroups(m.getVertexGroups(), m.vertexCount);

                byte[] enc = ModelType3Encoder.encode(m);
                ModelDefinition back = new ModelLoader().load(dst, enc);
                check(back.vertexCount == orig.vertexCount && back.faceCount == orig.faceCount, src + ": counts changed");
                check(Arrays.equals(back.vertexX, orig.vertexX) && Arrays.equals(back.vertexY, orig.vertexY)
                        && Arrays.equals(back.vertexZ, orig.vertexZ), src + ": vertices changed");
                check(Arrays.equals(back.faceIndices1, orig.faceIndices1) && Arrays.equals(back.faceIndices2, orig.faceIndices2)
                        && Arrays.equals(back.faceIndices3, orig.faceIndices3), src + ": faces changed");
                check(Arrays.equals(back.faceColors, orig.faceColors), src + ": colours changed");
                check(Arrays.deepEquals(back.getVertexGroups(), orig.getVertexGroups()), src + ": vertex skin groups changed");
                check(Arrays.equals(back.packedTransparencyVertexGroups, orig.packedTransparencyVertexGroups), src + ": face skin groups changed");
                check(back.faceTextures != null, src + ": textures missing after encode");
                for (int f = 0; f < back.faceCount; f++) {
                    short want = ft[f];
                    check(back.faceTextures[f] == want, src + ": face " + f + " texture " + back.faceTextures[f] + " != " + want);
                    if (want != -1)
                        check(back.textureCoords == null || back.textureCoords[f] == -1, src + ": face " + f + " has a texture coord");
                }
                Files.write(new File(out, dst + ".dat").toPath(), enc);
                System.out.printf("model %d -> %d  tex %d on %d/%d faces  %d bytes%s%n", src, dst, tex, n, m.faceCount, enc.length,
                        p[4].startsWith("auto") ? "  colours=" + colours : "");
            }
        }
    }

    static boolean skinOrBlack(int hsl) {
        int h = (hsl >> 10) & 63, s = (hsl >> 7) & 7, l = hsl & 127;
        return hsl == 0 || (h >= 3 && h <= 5 && s >= 2 && s <= 4 && l >= 55 && l <= 95);
    }

    static Set<Integer> autoColours(ModelDefinition m, double fraction) {
        Map<Integer, Integer> counts = new HashMap<>();
        int total = 0;
        for (int f = 0; f < m.faceCount; f++) {
            int c = m.faceColors[f] & 0xFFFF;
            boolean opaque = m.faceTransparencies == null || (m.faceTransparencies[f] & 0xFF) < 128;
            if (skinOrBlack(c) || !opaque) continue;
            counts.merge(c, 1, Integer::sum);
            total++;
        }
        List<Map.Entry<Integer, Integer>> ranked = new ArrayList<>(counts.entrySet());
        ranked.sort((x, y) -> y.getValue() - x.getValue());
        Set<Integer> picked = new LinkedHashSet<>();
        int cum = 0;
        for (Map.Entry<Integer, Integer> e : ranked) {
            if (total > 0 && cum >= fraction * total) break;
            picked.add(e.getKey());
            cum += e.getValue();
        }
        return picked;
    }

    static int[] packVertexGroups(int[][] groups, int vertexCount) {
        int[] packed = new int[vertexCount];
        boolean[] seen = new boolean[vertexCount];
        for (int g = 0; g < groups.length; g++) {
            for (int v : groups[g]) {
                check(!seen[v], "vertex " + v + " is in more than one skin group");
                seen[v] = true;
                packed[v] = g;
            }
        }
        return packed;
    }

    static void check(boolean ok, String msg) {
        if (!ok) throw new IllegalStateException(msg);
    }
}
