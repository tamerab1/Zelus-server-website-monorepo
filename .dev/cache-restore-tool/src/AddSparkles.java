import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.fs.Store;

import java.io.File;
import java.nio.file.Files;
import java.util.*;

// Adds weapon-bound sparkles to a worn weapon model: `count` tiny octahedron "gems" spread along the model's longest
// axis, each offset a few units from an anchor vertex and given THAT vertex's skin group, so the sparkles move with
// the hand through every animation (unlike a spotanim, which plays at the player's feet). Gem faces get an animated
// glitter texture (fast scroll = twinkle) and a high render priority. Merged with BuildTierPets.merge, re-encoded with
// ModelType3Encoder (vertex skins preserved, round-trip checked) and written as <newModel>.dat for InsertRawModels.
// Usage: <cachePath> <srcModel> <newModel> <outDir> <texture> <hslColour> <count> <halfSize> <spread> <seed>
public class AddSparkles {
    public static void main(String[] args) throws Exception {
        int src = Integer.parseInt(args[1]), dst = Integer.parseInt(args[2]);
        File out = new File(args[3]);
        short texture = (short) Integer.parseInt(args[4]);
        short colour = (short) Integer.parseInt(args[5]);
        int count = Integer.parseInt(args[6]), half = Integer.parseInt(args[7]), spread = Integer.parseInt(args[8]);
        Random rnd = new Random(Long.parseLong(args[9]));
        try (Store store = new Store(new File(args[0]))) {
            store.load();
            ModelDefinition m = BuildTierPets.model(store, src);
            if (m.packedVertexGroups == null)
                throw new IllegalStateException("model " + src + " has no vertex skins: sparkles could not follow the hand");

            // anchors: vertices ordered along the longest axis, picked at even intervals
            int[][] axes = {m.vertexX, m.vertexY, m.vertexZ};
            int best = 0;
            long bestSpan = -1;
            for (int a = 0; a < 3; a++) {
                int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
                for (int v = 0; v < m.vertexCount; v++) { lo = Math.min(lo, axes[a][v]); hi = Math.max(hi, axes[a][v]); }
                if (hi - lo > bestSpan) { bestSpan = hi - lo; best = a; }
            }
            final int[] key = axes[best];
            Integer[] order = new Integer[m.vertexCount];
            for (int v = 0; v < m.vertexCount; v++) order[v] = v;
            Arrays.sort(order, Comparator.comparingInt(v -> key[v]));

            ModelDefinition g = new ModelDefinition();
            int vc = count * 6, fc = count * 8;
            g.vertexCount = vc; g.faceCount = fc;
            g.vertexX = new int[vc]; g.vertexY = new int[vc]; g.vertexZ = new int[vc];
            g.packedVertexGroups = new int[vc];
            g.faceIndices1 = new int[fc]; g.faceIndices2 = new int[fc]; g.faceIndices3 = new int[fc];
            g.faceColors = new short[fc];
            g.faceTransparencies = new byte[fc];
            g.faceRenderPriorities = new byte[fc];
            g.faceRenderTypes = new byte[fc];
            g.faceTextures = new short[fc];
            g.textureCoords = new byte[fc];
            int[][] dirs = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
            // the 8 faces of an octahedron, by index into dirs
            int[][] tris = {{0, 2, 4}, {2, 1, 4}, {1, 3, 4}, {3, 0, 4}, {2, 0, 5}, {1, 2, 5}, {3, 1, 5}, {0, 3, 5}};
            for (int k = 0; k < count; k++) {
                int anchor = order[Math.min(m.vertexCount - 1, (int) ((k + 0.5) * m.vertexCount / count))];
                int cx = m.vertexX[anchor] + rnd.nextInt(2 * spread + 1) - spread;
                int cy = m.vertexY[anchor] + rnd.nextInt(2 * spread + 1) - spread;
                int cz = m.vertexZ[anchor] + rnd.nextInt(2 * spread + 1) - spread;
                int size = Math.max(1, half - 1 + rnd.nextInt(3)); // vary the gems a little
                for (int d = 0; d < 6; d++) {
                    int v = k * 6 + d;
                    g.vertexX[v] = cx + dirs[d][0] * size;
                    g.vertexY[v] = cy + dirs[d][1] * size;
                    g.vertexZ[v] = cz + dirs[d][2] * size;
                    g.packedVertexGroups[v] = m.packedVertexGroups[anchor];
                }
                for (int t = 0; t < 8; t++) {
                    int f = k * 8 + t;
                    g.faceIndices1[f] = k * 6 + tris[t][0];
                    g.faceIndices2[f] = k * 6 + tris[t][1];
                    g.faceIndices3[f] = k * 6 + tris[t][2];
                    g.faceColors[f] = colour;
                    g.faceRenderPriorities[f] = 10;
                    g.faceTextures[f] = texture;
                    g.textureCoords[f] = -1;
                }
            }
            ModelDefinition merged = BuildTierPets.merge(Arrays.asList(m, g));
            byte[] enc = ModelType3Encoder.encode(merged);
            ModelDefinition back = new ModelLoader().load(dst, enc);
            if (back.vertexCount != merged.vertexCount || back.faceCount != merged.faceCount)
                throw new IllegalStateException("round trip changed the geometry");
            int[] backSkins = back.packedVertexGroups != null ? back.packedVertexGroups
                    : TextureModelFaces.packVertexGroups(back.getVertexGroups(), back.vertexCount);
            if (!Arrays.equals(backSkins, merged.packedVertexGroups))
                throw new IllegalStateException("vertex skins lost");
            out.mkdirs();
            Files.write(new File(out, dst + ".dat").toPath(), enc);
            System.out.println("model " + src + " -> " + dst + ": " + count + " sparkles (" + fc + " faces, tex " + texture
                    + ") on " + m.vertexCount + "+" + vc + " vertices, skins kept");
        }
    }
}
