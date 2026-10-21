import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Store;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

// Moves every vertex of a model by (dx, dy, dz) and optionally gives the faces of chosen colours an (animated) texture,
// in one re-encode (2026-10-21: Souly Wings 60313 sat with its roots inside the torso -- shifted back on +z -- and its
// pale-gold energy faces take the flowing texture 265). Same encode path and round-trip checks as TextureModelFaces:
// vertex skins rebuilt from the loaded table, faces / colours / skin groups unchanged, vertices moved by exactly the
// offset, textures exactly as intended. Writes <outDir>/<dstModel>.dat for InsertRawModels (dst == src: replace).
// Optional [priority]: every face on that render priority (round 2, 2026-10-21: the Souly Wings glow drew over the chest
// from a raised camera in GPU mode -- the back-and-up faces on the cape "outer" layer 5 face such a camera -- so the
// whole wing goes under the body on 0; 117HD sorts by depth and is unaffected).
// Or [split:<back>:<rest>] (round 3b): every face on ONE layer made the body, arms and legs paint over the wings from
// behind in GPU mode, so faces pointing back (+z, not mostly up) go on <back> (10: depth-sorted with the arms) and the
// rest (toward the body, sideways, up-facing) on <rest> (0: the torso covers them from the front, no glow leak).
// Usage: <cachePath> <srcModel> <dstModel> <dx,dy,dz> <texId|-> <hsl,...|-> <outDir> [priority|split:<back>:<rest>]
public class ShiftTextureModel {
    public static void main(String[] args) throws Exception {
        int src = Integer.parseInt(args[1]), dst = Integer.parseInt(args[2]);
        String[] d = args[3].split(",");
        int dx = Integer.parseInt(d[0]), dy = Integer.parseInt(d[1]), dz = Integer.parseInt(d[2]);
        boolean texture = !args[4].equals("-");
        File out = new File(args[6]);
        out.mkdirs();
        try (Store store = new Store(new File(args[0]))) {
            store.load();
            Index models = store.getIndex(IndexType.MODELS);
            if (dst != src && models.getArchive(dst) != null) throw new IllegalStateException("model " + dst + " already exists");
            Archive a = models.getArchive(src);
            byte[] raw = a.decompress(store.getStorage().loadArchive(a));
            ModelDefinition m = new ModelLoader().load(src, raw);
            ModelDefinition orig = new ModelLoader().load(src, raw);
            for (int v = 0; v < m.vertexCount; v++) {
                m.vertexX[v] += dx;
                m.vertexY[v] += dy;
                m.vertexZ[v] += dz;
            }
            short[] ft = m.faceTextures != null ? m.faceTextures.clone() : null;
            int n = 0;
            if (texture) {
                short tex = Short.parseShort(args[4]);
                Set<Integer> colours = new HashSet<>();
                for (String c : args[5].split(",")) colours.add(Integer.parseInt(c) & 0xFFFF);
                if (ft == null) {
                    ft = new short[m.faceCount];
                    Arrays.fill(ft, (short) -1);
                }
                for (int f = 0; f < m.faceCount; f++) {
                    if (colours.contains(m.faceColors[f] & 0xFFFF)) {
                        ft[f] = tex;
                        if (m.textureCoords != null) m.textureCoords[f] = -1;
                        n++;
                    }
                }
                if (n == 0) throw new IllegalStateException("model " + src + ": no faces matched " + args[5]);
                m.faceTextures = ft;
            }
            String pspec = args.length > 7 ? args[7] : "";
            int priority = pspec.isEmpty() || pspec.startsWith("split") ? -1 : Integer.parseInt(pspec);
            byte[] wanted = null;
            if (priority >= 0) {
                wanted = new byte[m.faceCount];
                Arrays.fill(wanted, (byte) priority);
            } else if (pspec.startsWith("split")) {
                String[] sp = pspec.split(":");
                byte back = Byte.parseByte(sp[1]), rest = Byte.parseByte(sp[2]);
                wanted = new byte[m.faceCount];
                int nb = 0;
                for (int f = 0; f < m.faceCount; f++) {
                    double[] nv = AuditWornModels.normal(m, f);
                    boolean backward = nv[2] > 0.3 && nv[1] > -0.5; // y is down: -0.5 = mostly up-facing
                    wanted[f] = backward ? back : rest;
                    if (backward) nb++;
                }
                System.out.printf("split: %d faces back on %d, %d on %d%n", nb, back, m.faceCount - nb, rest);
            }
            if (wanted != null) {
                m.faceRenderPriorities = wanted.clone();
                m.priority = 0;
            }
            if (m.packedVertexGroups == null && m.getVertexGroups() != null)
                m.packedVertexGroups = TextureModelFaces.packVertexGroups(m.getVertexGroups(), m.vertexCount);

            byte[] enc = ModelType3Encoder.encode(m);
            ModelDefinition back = new ModelLoader().load(dst, enc);
            check(back.vertexCount == orig.vertexCount && back.faceCount == orig.faceCount, "counts changed");
            for (int v = 0; v < orig.vertexCount; v++)
                check(back.vertexX[v] == orig.vertexX[v] + dx && back.vertexY[v] == orig.vertexY[v] + dy
                    && back.vertexZ[v] == orig.vertexZ[v] + dz, "vertex " + v + " not moved by exactly the offset");
            check(Arrays.equals(back.faceIndices1, orig.faceIndices1) && Arrays.equals(back.faceIndices2, orig.faceIndices2)
                && Arrays.equals(back.faceIndices3, orig.faceIndices3), "faces changed");
            check(Arrays.equals(back.faceColors, orig.faceColors), "colours changed");
            check(Arrays.equals(back.faceTransparencies, orig.faceTransparencies), "transparencies changed");
            if (wanted == null)
                check(Arrays.equals(back.faceRenderPriorities, orig.faceRenderPriorities), "priorities changed");
            else
                for (int f = 0; f < back.faceCount; f++)
                    check((back.faceRenderPriorities == null ? back.priority : back.faceRenderPriorities[f]) == wanted[f], "face " + f + " priority");
            check(Arrays.deepEquals(back.getVertexGroups(), orig.getVertexGroups()), "vertex skin groups changed");
            check(Arrays.equals(back.packedTransparencyVertexGroups, orig.packedTransparencyVertexGroups), "face skin groups changed");
            if (texture)
                for (int f = 0; f < back.faceCount; f++)
                    check(back.faceTextures != null && back.faceTextures[f] == ft[f], "face " + f + " texture wrong");
            Files.write(new File(out, dst + ".dat").toPath(), enc);
            System.out.printf("model %d -> %d  moved %d,%d,%d  tex %s on %d/%d faces  priority %s  %d bytes%n", src, dst, dx, dy, dz,
                texture ? args[4] : "-", n, m.faceCount, wanted == null ? "kept" : pspec, enc.length);
        }
    }

    static void check(boolean ok, String msg) {
        if (!ok) throw new IllegalStateException(msg);
    }
}
