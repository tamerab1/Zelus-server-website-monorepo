import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.fs.Store;

import java.io.File;
import java.nio.file.Files;
import java.util.*;

// Gives textured faces that have NO texture triangle (textureCoords -1) shared, continuous texture mapping.
//
// Why: with textureCoords -1 the client uses the face's own three corners as its texture triangle, so EVERY triangle
// gets its own full, stretched copy of the texture -- neighbours never line up, which shows as shattered seams under
// the stock GPU plugin / software renderer (TextureModelFaces, BuildTierPets.textured and AddSparkles all produced
// such faces). Jagex models instead share a few planar texture triangles across whole surfaces; this does the same:
// faces are clustered by (vertex skin group, dominant normal axis, depth slab along that axis) and each cluster gets
// one texture triangle lying in the cluster's plane, with the same in-plane origin/axes model-wide (so the texture
// flows across clusters). The triangle's 3 helper vertices are unreferenced by faces and carry the cluster's skin
// group, so the mapping moves with the bone through animations. Untouched: geometry, colours, priorities, alpha,
// existing texture triangles. Skins are preserved and round-trip checked (ModelType3Encoder).
// Usage: RemapTextureUVs <cachePath> <outDir> <tileSize> <modelId|from-to>[,...]
//   writes <id>.dat for each model that needed it (then: InsertRawModels replace <cache> <outDir>)
public class RemapTextureUVs {
    static final int MAX_TEX_FACES = 255; // textureCoords is an unsigned byte - 1

    public static void main(String[] args) throws Exception {
        File out = new File(args[1]);
        int tile = Integer.parseInt(args[2]);
        List<Integer> ids = new ArrayList<>();
        for (String part : args[3].split(",")) {
            String[] r = part.split("-");
            int a = Integer.parseInt(r[0]), b = r.length > 1 ? Integer.parseInt(r[1]) : a;
            for (int i = a; i <= b; i++) ids.add(i);
        }
        out.mkdirs();
        int done = 0;
        try (Store store = new Store(new File(args[0]))) {
            store.load();
            for (int id : ids) {
                if (store.getIndex(net.runelite.cache.IndexType.MODELS).getArchive(id) == null)
                    continue;
                ModelDefinition m = BuildTierPets.model(store, id);
                String r = remap(m, tile);
                if (r == null)
                    continue;
                byte[] enc = ModelType3Encoder.encode(m);
                ModelDefinition back = new ModelLoader().load(id, enc);
                if (back.vertexCount != m.vertexCount || back.faceCount != m.faceCount || back.numTextureFaces != m.numTextureFaces)
                    throw new IllegalStateException(id + ": round trip changed the geometry");
                for (int f = 0; f < m.faceCount; f++)
                    if (back.faceTextures[f] != m.faceTextures[f] || (m.faceTextures[f] != -1 && back.textureCoords[f] != m.textureCoords[f]))
                        throw new IllegalStateException(id + ": round trip changed face " + f + " texturing: tex " + m.faceTextures[f] + "->" + back.faceTextures[f] + " coord " + m.textureCoords[f] + "->" + back.textureCoords[f] + " numTex " + m.numTextureFaces + " colour " + m.faceColors[f] + "->" + back.faceColors[f]);
                if (m.packedVertexGroups != null) {
                    int[] backSkins = back.packedVertexGroups != null ? back.packedVertexGroups
                            : TextureModelFaces.packVertexGroups(back.getVertexGroups(), back.vertexCount);
                    if (!Arrays.equals(backSkins, m.packedVertexGroups))
                        throw new IllegalStateException(id + ": vertex skins lost");
                }
                Files.write(new File(out, id + ".dat").toPath(), enc);
                System.out.println("model " + id + ": " + r);
                done++;
            }
        }
        System.out.println(done + " model(s) remapped");
    }

    /** Adds the shared texture triangles to m in place; null when m has no per-face-mapped textured faces. */
    static String remap(ModelDefinition m, int tile) {
        if (m.faceTextures == null)
            return null;
        List<Integer> faces = new ArrayList<>();
        for (int f = 0; f < m.faceCount; f++)
            if (m.faceTextures[f] != -1 && (m.textureCoords == null || m.textureCoords[f] == -1))
                faces.add(f);
        if (faces.isEmpty())
            return null;
        int existing = m.numTextureFaces;
        int budget = MAX_TEX_FACES - existing;
        if (budget < 3)
            throw new IllegalStateException("model " + m.id + " has no room for texture triangles (" + existing + " used)");

        int[] axisOf = new int[m.faceCount], groupOf = new int[m.faceCount];
        float[] depthOf = new float[m.faceCount];
        int[][] v = {m.vertexX, m.vertexY, m.vertexZ};
        for (int f : faces) {
            int a = m.faceIndices1[f], b = m.faceIndices2[f], c = m.faceIndices3[f];
            long ux = m.vertexX[b] - m.vertexX[a], uy = m.vertexY[b] - m.vertexY[a], uz = m.vertexZ[b] - m.vertexZ[a];
            long wx = m.vertexX[c] - m.vertexX[a], wy = m.vertexY[c] - m.vertexY[a], wz = m.vertexZ[c] - m.vertexZ[a];
            long nx = Math.abs(uy * wz - uz * wy), ny = Math.abs(uz * wx - ux * wz), nz = Math.abs(ux * wy - uy * wx);
            int axis = nx >= ny && nx >= nz ? 0 : ny >= nz ? 1 : 2;
            axisOf[f] = axis;
            depthOf[f] = (v[axis][a] + v[axis][b] + v[axis][c]) / 3f;
            groupOf[f] = m.packedVertexGroups == null ? 0 : m.packedVertexGroups[a];
        }

        // cluster by (group, axis, depth slab); widen the slab until the clusters fit the texture-triangle budget,
        // and as a last resort stop splitting by skin group
        Map<Long, List<Integer>> clusters = null;
        boolean byGroup = true;
        for (int slab = 8; ; slab *= 2) {
            clusters = new LinkedHashMap<>();
            for (int f : faces) {
                long key = ((long) (byGroup ? groupOf[f] : 0) << 40) | ((long) axisOf[f] << 32)
                        | ((Math.floorDiv((int) depthOf[f], slab) + 0x8000) & 0xFFFFFFFFL);
                clusters.computeIfAbsent(key, k -> new ArrayList<>()).add(f);
            }
            if (clusters.size() <= budget)
                break;
            if (slab >= 1 << 16) {
                if (!byGroup)
                    throw new IllegalStateException("model " + m.id + ": cannot fit " + clusters.size() + " clusters");
                byGroup = false;
                slab = 4; // restarts at 8
            }
        }

        int k = clusters.size();
        int nv = m.vertexCount + 3 * k, nt = existing + k;
        m.vertexX = Arrays.copyOf(m.vertexX, nv);
        m.vertexY = Arrays.copyOf(m.vertexY, nv);
        m.vertexZ = Arrays.copyOf(m.vertexZ, nv);
        if (m.packedVertexGroups != null)
            m.packedVertexGroups = Arrays.copyOf(m.packedVertexGroups, nv);
        m.texIndices1 = m.texIndices1 == null ? new short[nt] : Arrays.copyOf(m.texIndices1, nt);
        m.texIndices2 = m.texIndices2 == null ? new short[nt] : Arrays.copyOf(m.texIndices2, nt);
        m.texIndices3 = m.texIndices3 == null ? new short[nt] : Arrays.copyOf(m.texIndices3, nt);
        m.textureRenderTypes = m.textureRenderTypes == null ? new byte[nt] : Arrays.copyOf(m.textureRenderTypes, nt);
        if (m.textureCoords == null) {
            m.textureCoords = new byte[m.faceCount];
            Arrays.fill(m.textureCoords, (byte) -1);
        }
        int vi = m.vertexCount, ti = existing;
        for (List<Integer> cl : clusters.values()) {
            int f0 = cl.get(0), axis = axisOf[f0];
            double sum = 0;
            for (int f : cl) sum += depthOf[f];
            int depth = (int) Math.round(sum / cl.size());
            // in-plane axes: X-facing -> (u=Z, v=Y), Y-facing -> (u=X, v=Z), Z-facing -> (u=X, v=Y); y grows downward,
            // so v follows "down" on the side faces. P sits on the plane at the model-wide in-plane origin.
            int[] p = new int[3], mm = new int[3], n = new int[3];
            p[axis] = mm[axis] = n[axis] = depth;
            int uAxis = axis == 0 ? 2 : 0, vAxis = axis == 1 ? 2 : 1;
            mm[uAxis] += tile;
            n[vAxis] += tile;
            int[][] pts = {p, mm, n};
            for (int[] pt : pts) {
                m.vertexX[vi] = pt[0];
                m.vertexY[vi] = pt[1];
                m.vertexZ[vi] = pt[2];
                if (m.packedVertexGroups != null)
                    m.packedVertexGroups[vi] = groupOf[f0];
                vi++;
            }
            m.texIndices1[ti] = (short) (vi - 3);
            m.texIndices2[ti] = (short) (vi - 2);
            m.texIndices3[ti] = (short) (vi - 1);
            m.textureRenderTypes[ti] = 0;
            for (int f : cl)
                m.textureCoords[f] = (byte) ti;
            ti++;
        }
        m.vertexCount = nv;
        m.numTextureFaces = nt;
        return faces.size() + " faces -> " + k + " texture triangles" + (byGroup ? "" : " (groups merged)") + ", +" + 3 * k + " helper vertices";
    }
}
