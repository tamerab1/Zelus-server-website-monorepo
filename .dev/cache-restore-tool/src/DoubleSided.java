import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.fs.Store;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;

// Makes a thin worn sheet (wings) visible from both sides (2026-10-19, Vermilion Dreadwings). The GPU plugin culls back
// faces: a single-sided wing membrane vanished from one side, showing the ground / armour straight through it. Every face
// gets a reversed twin. Layering by facing: the face of each pair that faces BACK (+z normal: seen when looking at the
// player's back) goes on priority <back>, the one facing the front on <front> -- e.g. 10 / 1: from behind the wings draw
// over the head and a helmet (6), from the front they stay under the torso (3) like a cape's lining.
// Colours, textures, alpha and face skins are copied to the twin. Reads <id> from the cache, writes <outDir>/<id>.dat.
// Usage: DoubleSided <cache> <modelId> <outDir> <front> <back>
public class DoubleSided {
    public static void main(String[] args) throws Exception {
        int id = Integer.parseInt(args[1]);
        byte front = Byte.parseByte(args[3]), back = Byte.parseByte(args[4]);
        ModelDefinition m;
        try (Store store = new Store(new File(args[0]))) {
            store.load();
            m = BuildTierPets.model(store, id);
        }
        int n = m.faceCount;
        int[] a = Arrays.copyOf(m.faceIndices1, n * 2), b = Arrays.copyOf(m.faceIndices2, n * 2), c = Arrays.copyOf(m.faceIndices3, n * 2);
        byte[] pri = new byte[n * 2];
        int backs = 0;
        for (int f = 0; f < n; f++) {
            a[n + f] = m.faceIndices3[f];
            b[n + f] = m.faceIndices2[f];
            c[n + f] = m.faceIndices1[f];
            // client winding: normal = (B - A) x (C - A); +z = towards the player's back
            int v0 = m.faceIndices1[f], v1 = m.faceIndices2[f], v2 = m.faceIndices3[f];
            long ax = m.vertexX[v1] - m.vertexX[v0], ay = m.vertexY[v1] - m.vertexY[v0];
            long bx = m.vertexX[v2] - m.vertexX[v0], by = m.vertexY[v2] - m.vertexY[v0];
            long nz = ax * by - ay * bx;
            boolean facesBack = nz > 0;
            pri[f] = facesBack ? back : front;
            pri[n + f] = facesBack ? front : back;
            if (facesBack) backs++;
        }
        m.faceIndices1 = a;
        m.faceIndices2 = b;
        m.faceIndices3 = c;
        m.faceColors = dup(m.faceColors, n);
        if (m.faceTransparencies != null) m.faceTransparencies = Arrays.copyOf(m.faceTransparencies, n * 2);
        if (m.faceTransparencies != null) System.arraycopy(m.faceTransparencies, 0, m.faceTransparencies, n, n);
        if (m.faceRenderTypes != null) { m.faceRenderTypes = Arrays.copyOf(m.faceRenderTypes, n * 2); System.arraycopy(m.faceRenderTypes, 0, m.faceRenderTypes, n, n); }
        if (m.faceTextures != null) m.faceTextures = dup(m.faceTextures, n);
        if (m.textureCoords != null) { m.textureCoords = Arrays.copyOf(m.textureCoords, n * 2); System.arraycopy(m.textureCoords, 0, m.textureCoords, n, n); }
        if (m.packedTransparencyVertexGroups != null) {
            m.packedTransparencyVertexGroups = Arrays.copyOf(m.packedTransparencyVertexGroups, n * 2);
            System.arraycopy(m.packedTransparencyVertexGroups, 0, m.packedTransparencyVertexGroups, n, n);
        }
        m.faceRenderPriorities = pri;
        m.priority = 0;
        m.faceCount = n * 2;
        byte[] enc = ModelType3Encoder.encode(m);
        ModelDefinition back2 = new ModelLoader().load(id, enc);
        if (back2.faceCount != n * 2 || !Arrays.equals(back2.faceRenderPriorities, pri) || !Arrays.equals(back2.faceColors, m.faceColors))
            throw new IllegalStateException("round trip");
        new File(args[2]).mkdirs();
        Files.write(new File(args[2], id + ".dat").toPath(), enc);
        System.out.printf("model %d: %d -> %d faces (%d facing back on %d, their twins on %d)%n", id, n, n * 2, backs, back, front);
    }

    static short[] dup(short[] s, int n) {
        short[] r = Arrays.copyOf(s, n * 2);
        System.arraycopy(s, 0, r, n, n);
        return r;
    }
}
