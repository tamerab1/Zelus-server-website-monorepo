import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Store;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;

// Moves / scales a worn model in place (2026-10-18, Ascended soulbound spire 61230: its lantern head hung across the
// player's face and shoulder in the standard staff pose). Every vertex: p' = pivot + (p - pivot) * scale + move, with
// the pivot at the grip (the hand skin group's centre), so the hand still holds it. y grows downward; the right hand
// is on -x. Vertex skins, faces, colours and textures stay; full decode check. Reads the model from <inDir>/<id>.dat if
// present (chained after another tool), else from the cache.
// Spec lines:   model <id> <scale> <pivotX,pivotY,pivotZ> <dx,dy,dz> [<axisX,axisY,axisZ> <degrees>]
//               squashx <id> <zCut> <centreX> <factor>   (only vertices with z < zCut: x pulled toward centreX)
//               (optional: also turn the model about the axis through the pivot, Rodrigues, after scaling)
// Usage: <cachePath> <specFile> <inDir|-> <outDir>
public class TransformModel {
    public static void main(String[] args) throws Exception {
        File out = new File(args[3]);
        out.mkdirs();
        try (Store store = new Store(new File(args[0]))) {
            store.load();
            for (String line : Files.readAllLines(new File(args[1]).toPath())) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] p = line.split("\\s+");
                int id = Integer.parseInt(p[1]);
                // squashx <id> <zCut> <centreX> <factor>: only vertices with z < zCut, x pulled toward centreX
                double[] squash = p[0].equals("squashx")
                        ? new double[]{Double.parseDouble(p[2]), Double.parseDouble(p[3]), Double.parseDouble(p[4])} : null;
                if (squash != null)
                    p = new String[]{p[0], p[1], "1", "0,0,0", "0,0,0"};
                double s = Double.parseDouble(p[2]);
                double[] pv = Arrays.stream(p[3].split(",")).mapToDouble(Double::parseDouble).toArray();
                double[] mv = Arrays.stream(p[4].split(",")).mapToDouble(Double::parseDouble).toArray();
                double[] ax = p.length > 6 ? Arrays.stream(p[5].split(",")).mapToDouble(Double::parseDouble).toArray() : null;
                double deg = p.length > 6 ? Double.parseDouble(p[6]) : 0;
                if (ax != null) {
                    double l = Math.sqrt(ax[0] * ax[0] + ax[1] * ax[1] + ax[2] * ax[2]);
                    for (int i = 0; i < 3; i++) ax[i] /= l;
                }
                File chained = args[2].equals("-") ? null : new File(args[2], id + ".dat");
                byte[] raw;
                if (chained != null && chained.exists())
                    raw = Files.readAllBytes(chained.toPath());
                else {
                    Archive ar = store.getIndex(IndexType.MODELS).getArchive(id);
                    raw = ar.decompress(store.getStorage().loadArchive(ar));
                }
                ModelDefinition orig = new ModelLoader().load(id, raw), m = new ModelLoader().load(id, raw);
                double cs = Math.cos(Math.toRadians(deg)), sn = Math.sin(Math.toRadians(deg));
                for (int v = 0; v < m.vertexCount; v++) {
                    double x = (m.vertexX[v] - pv[0]) * s, y = (m.vertexY[v] - pv[1]) * s, z = (m.vertexZ[v] - pv[2]) * s;
                    if (ax != null) { // Rodrigues: r cos + (k x r) sin + k (k.r)(1 - cos)
                        double kd = ax[0] * x + ax[1] * y + ax[2] * z;
                        double cx = ax[1] * z - ax[2] * y, cy = ax[2] * x - ax[0] * z, cz = ax[0] * y - ax[1] * x;
                        double nx = x * cs + cx * sn + ax[0] * kd * (1 - cs), ny = y * cs + cy * sn + ax[1] * kd * (1 - cs),
                                nz = z * cs + cz * sn + ax[2] * kd * (1 - cs);
                        x = nx; y = ny; z = nz;
                    }
                    if (squash != null && m.vertexZ[v] < squash[0])
                        x = squash[1] + (m.vertexX[v] - squash[1]) * squash[2] - pv[0];
                    m.vertexX[v] = (int) Math.round(pv[0] + x + mv[0]);
                    m.vertexY[v] = (int) Math.round(pv[1] + y + mv[1]);
                    m.vertexZ[v] = (int) Math.round(pv[2] + z + mv[2]);
                }
                if (m.packedVertexGroups == null && m.getVertexGroups() != null)
                    m.packedVertexGroups = TextureModelFaces.packVertexGroups(m.getVertexGroups(), m.vertexCount);
                byte[] enc = ModelType3Encoder.encode(m);
                ModelDefinition back = new ModelLoader().load(id, enc);
                if (back.faceCount != orig.faceCount || !Arrays.equals(back.faceColors, orig.faceColors)
                        || !Arrays.equals(back.faceTextures, orig.faceTextures)
                        || !Arrays.deepEquals(back.getVertexGroups(), orig.getVertexGroups())
                        || !Arrays.equals(back.vertexX, m.vertexX) || !Arrays.equals(back.vertexY, m.vertexY))
                    throw new IllegalStateException("round-trip failed for " + id);
                int[] b = bounds(back);
                Files.write(new File(out, id + ".dat").toPath(), enc);
                System.out.printf("model %d: scale %.2f about (%s), move (%s) -> x[%d,%d] y[%d,%d] z[%d,%d]%n", id, s, p[3], p[4],
                        b[0], b[1], b[2], b[3], b[4], b[5]);
            }
        }
    }

    static int[] bounds(ModelDefinition m) {
        int[] b = {1 << 30, -(1 << 30), 1 << 30, -(1 << 30), 1 << 30, -(1 << 30)};
        for (int v = 0; v < m.vertexCount; v++) {
            b[0] = Math.min(b[0], m.vertexX[v]); b[1] = Math.max(b[1], m.vertexX[v]);
            b[2] = Math.min(b[2], m.vertexY[v]); b[3] = Math.max(b[3], m.vertexY[v]);
            b[4] = Math.min(b[4], m.vertexZ[v]); b[5] = Math.max(b[5], m.vertexZ[v]);
        }
        return b;
    }
}
