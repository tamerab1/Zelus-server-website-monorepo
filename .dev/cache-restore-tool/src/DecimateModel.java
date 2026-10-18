import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Store;

import java.io.File;
import java.nio.file.Files;
import java.util.*;

// Reduces a ripped model's face count (2026-10-18). The client merges a player's kits and worn items into ONE model and
// the GPU plugin draws at most 6144 faces of a model (GpuPlugin.MAX_TRIANGLE): with the Crimson Warden body (2404) +
// legs (2135) + Azure Fang (1588) worn, the base head / hands / feet -- merged after them -- were cut off. Real OSRS gear
// is a few hundred faces per piece.
// Quadric-error edge collapse (Garland-Heckbert): vertex quadrics from the area-weighted planes of their faces, plus
// boundary planes so open edges keep their silhouette; an edge only collapses inside one vertex skin group (animation
// stays exact), to the better of its two ends or its midpoint, never if a neighbouring face would flip or degenerate.
// Every face keeps its own colour, transparency, priority, texture, texture coords and face skin group.
// Spec lines:   model <id> <targetFaces>
// Writes <outDir>/<id>.dat (ModelType3Encoder, full decode check) for InsertRawModels replace; a preview sheet
// (before | after, two views) if a png path is given. Usage: <cachePath> <specFile> <outDir> [preview.png]
public class DecimateModel {
    public static void main(String[] args) throws Exception {
        File out = new File(args[2]);
        out.mkdirs();
        List<ModelDefinition[]> previews = new ArrayList<>();
        try (Store store = new Store(new File(args[0]))) {
            store.load();
            for (String line : Files.readAllLines(new File(args[1]).toPath())) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] p = line.split("\\s+");
                int id = Integer.parseInt(p[1]), target = Integer.parseInt(p[2]);
                Archive ar = store.getIndex(IndexType.MODELS).getArchive(id);
                byte[] raw = ar.decompress(store.getStorage().loadArchive(ar));
                ModelDefinition orig = new ModelLoader().load(id, raw);
                ModelDefinition m = decimate(new ModelLoader().load(id, raw), target);
                byte[] enc = ModelType3Encoder.encode(m);
                ModelDefinition back = new ModelLoader().load(id, enc);
                check(back.vertexCount == m.vertexCount && back.faceCount == m.faceCount, id + ": counts");
                check(Arrays.equals(back.faceColors, m.faceColors), id + ": colours");
                check(Arrays.equals(back.faceRenderPriorities, m.faceRenderPriorities) || back.faceRenderPriorities == null && m.faceRenderPriorities == null, id + ": priorities");
                check(Arrays.equals(back.faceTextures, m.faceTextures), id + ": textures");
                check(Arrays.deepEquals(back.getVertexGroups(), labelsToGroups(m.packedVertexGroups)), id + ": vertex skins");
                Files.write(new File(out, id + ".dat").toPath(), enc);
                System.out.printf("model %d: %d -> %d faces, %d -> %d vertices, %d bytes%n", id, orig.faceCount, back.faceCount,
                        orig.vertexCount, back.vertexCount, enc.length);
                previews.add(new ModelDefinition[]{orig, back});
            }
        }
        if (args.length > 3)
            preview(previews, new File(args[3]));
    }

    static void check(boolean ok, String what) {
        if (!ok) throw new IllegalStateException("round-trip failed: " + what);
    }

    static int[][] labelsToGroups(int[] labels) {
        if (labels == null) return null;
        int max = -1;
        for (int l : labels) max = Math.max(max, l);
        int[] counts = new int[max + 1];
        for (int l : labels) if (l >= 0) counts[l]++;
        int[][] g = new int[max + 1][];
        for (int i = 0; i <= max; i++) g[i] = new int[counts[i]];
        int[] fill = new int[max + 1];
        for (int v = 0; v < labels.length; v++) if (labels[v] >= 0) g[labels[v]][fill[labels[v]]++] = v;
        return g;
    }

    // ------------------------------------------------------------------ QEM

    static ModelDefinition decimate(ModelDefinition m, int target) {
        int n = m.vertexCount, fc = m.faceCount;
        double[][] pos = new double[n][];
        for (int v = 0; v < n; v++) pos[v] = new double[]{m.vertexX[v], m.vertexY[v], m.vertexZ[v]};
        int[] label = new int[n];
        Arrays.fill(label, -1);
        int[][] groups = m.getVertexGroups();
        if (groups != null)
            for (int g = 0; g < groups.length; g++)
                if (groups[g] != null) for (int v : groups[g]) label[v] = g;
        int[][] face = new int[fc][];
        for (int f = 0; f < fc; f++) face[f] = new int[]{m.faceIndices1[f], m.faceIndices2[f], m.faceIndices3[f]};
        boolean[] faceDead = new boolean[fc];
        int alive = fc;
        // drop faces that are already degenerate
        for (int f = 0; f < fc; f++) if (degenerate(face[f], pos)) { faceDead[f] = true; alive--; }

        List<Set<Integer>> vf = new ArrayList<>(); // vertex -> faces
        for (int v = 0; v < n; v++) vf.add(new HashSet<>());
        for (int f = 0; f < fc; f++) if (!faceDead[f]) for (int v : face[f]) vf.get(v).add(f);

        double[][] q = new double[n][10];
        for (int f = 0; f < fc; f++) {
            if (faceDead[f]) continue;
            double[] pl = plane(face[f], pos);
            if (pl == null) continue;
            for (int v : face[f]) addQ(q[v], pl, pl[4]);
        }
        // boundary edges: a plane through the edge perpendicular to its face keeps open silhouettes in place
        Map<Long, Integer> edgeCount = new HashMap<>();
        for (int f = 0; f < fc; f++) {
            if (faceDead[f]) continue;
            for (int k = 0; k < 3; k++) edgeCount.merge(key(face[f][k], face[f][(k + 1) % 3]), 1, Integer::sum);
        }
        for (int f = 0; f < fc; f++) {
            if (faceDead[f]) continue;
            double[] pl = plane(face[f], pos);
            if (pl == null) continue;
            for (int k = 0; k < 3; k++) {
                int a = face[f][k], b = face[f][(k + 1) % 3];
                if (edgeCount.get(key(a, b)) != 1) continue;
                double[] e = sub(pos[b], pos[a]);
                double[] nrm = cross(e, new double[]{pl[0], pl[1], pl[2]});
                double len = norm(nrm);
                if (len < 1e-9) continue;
                for (int i = 0; i < 3; i++) nrm[i] /= len;
                double d = -dot(nrm, pos[a]);
                double w = 10 * dot(e, e);
                double[] bp = {nrm[0], nrm[1], nrm[2], d};
                addQ(q[a], bp, w);
                addQ(q[b], bp, w);
            }
        }

        int[] parent = new int[n];
        for (int v = 0; v < n; v++) parent[v] = v;
        PriorityQueue<double[]> heap = new PriorityQueue<>(Comparator.comparingDouble(e -> e[0]));
        Set<Long> seen = new HashSet<>();
        for (int f = 0; f < fc; f++) {
            if (faceDead[f]) continue;
            for (int k = 0; k < 3; k++) {
                int a = face[f][k], b = face[f][(k + 1) % 3];
                if (label[a] != label[b] || !seen.add(key(a, b))) continue;
                pushEdge(heap, q, pos, a, b);
            }
        }
        int[] version = new int[n];
        while (alive > target && !heap.isEmpty()) {
            double[] e = heap.poll();
            int a = (int) e[1], b = (int) e[2];
            if (parent[a] != a || parent[b] != b || a == b) continue;
            if (version[a] != (int) e[6] || version[b] != (int) e[7]) continue; // stale entry
            double[] np = {e[3], e[4], e[5]};
            if (flips(a, b, np, vf, face, faceDead, pos) || flips(b, a, np, vf, face, faceDead, pos)) continue;
            // collapse b into a
            pos[a] = np;
            for (int i = 0; i < 10; i++) q[a][i] += q[b][i];
            parent[b] = a;
            for (int f : new ArrayList<>(vf.get(b))) {
                if (faceDead[f]) continue;
                for (int k = 0; k < 3; k++) if (face[f][k] == b) face[f][k] = a;
                if (face[f][0] == face[f][1] || face[f][1] == face[f][2] || face[f][0] == face[f][2]) {
                    faceDead[f] = true;
                    alive--;
                    for (int v : face[f]) vf.get(v).remove(f);
                } else
                    vf.get(a).add(f);
            }
            vf.get(b).clear();
            version[a]++;
            Set<Integer> nb = new HashSet<>();
            for (int f : vf.get(a)) for (int v : face[f]) if (v != a) nb.add(v);
            for (int v : nb) if (label[v] == label[a]) pushEdge(heap, q, pos, a, v, version);
        }

        // compact
        int[] remap = new int[n];
        Arrays.fill(remap, -1);
        int nv = 0;
        for (int f = 0; f < fc; f++) if (!faceDead[f]) for (int v : face[f]) if (remap[v] == -1) remap[v] = nv++;
        // texture triangles (PMN mapping) name vertices too: they follow their vertex's collapse and are always kept
        short[][] tex = {m.texIndices1, m.texIndices2, m.texIndices3};
        if (m.numTextureFaces > 0 && tex[0] != null)
            for (short[] t : tex)
                for (int i = 0; i < m.numTextureFaces; i++) {
                    int v = root(parent, t[i] & 0xffff);
                    if (remap[v] == -1) remap[v] = nv++;
                    t[i] = (short) remap[v];
                }
        int nf = alive;
        ModelDefinition r = m;
        int[] vx = new int[nv], vy = new int[nv], vz = new int[nv], lab = new int[nv];
        for (int v = 0; v < n; v++) {
            if (remap[v] == -1) continue;
            vx[remap[v]] = (int) Math.round(pos[v][0]);
            vy[remap[v]] = (int) Math.round(pos[v][1]);
            vz[remap[v]] = (int) Math.round(pos[v][2]);
            lab[remap[v]] = label[v];
        }
        int[] f1 = new int[nf], f2 = new int[nf], f3 = new int[nf];
        short[] col = new short[nf];
        byte[] tr = m.faceTransparencies == null ? null : new byte[nf];
        byte[] pr = m.faceRenderPriorities == null ? null : new byte[nf];
        short[] tx = m.faceTextures == null ? null : new short[nf];
        byte[] tc = m.textureCoords == null ? null : new byte[nf];
        int[] fsk = m.packedTransparencyVertexGroups == null ? null : new int[nf];
        byte[] rt = m.faceRenderTypes == null ? null : new byte[nf];
        int k = 0;
        for (int f = 0; f < fc; f++) {
            if (faceDead[f]) continue;
            f1[k] = remap[face[f][0]]; f2[k] = remap[face[f][1]]; f3[k] = remap[face[f][2]];
            col[k] = m.faceColors[f];
            if (tr != null) tr[k] = m.faceTransparencies[f];
            if (pr != null) pr[k] = m.faceRenderPriorities[f];
            if (tx != null) tx[k] = m.faceTextures[f];
            if (tc != null) tc[k] = m.textureCoords[f];
            if (fsk != null) fsk[k] = m.packedTransparencyVertexGroups[f];
            if (rt != null) rt[k] = m.faceRenderTypes[f];
            k++;
        }
        r.vertexCount = nv;
        r.vertexX = vx; r.vertexY = vy; r.vertexZ = vz;
        r.faceCount = nf;
        r.faceIndices1 = f1; r.faceIndices2 = f2; r.faceIndices3 = f3;
        r.faceColors = col;
        r.faceTransparencies = tr;
        r.faceRenderPriorities = pr;
        r.faceTextures = tx;
        r.textureCoords = tc;
        r.packedTransparencyVertexGroups = fsk;
        r.faceRenderTypes = rt;
        boolean anyLabel = false;
        for (int l : lab) if (l >= 0) anyLabel = true;
        r.packedVertexGroups = anyLabel ? lab : null;
        r.vertexNormals = null;
        r.faceNormals = null;
        return r;
    }

    static int root(int[] parent, int v) {
        while (parent[v] != v) v = parent[v];
        return v;
    }

    static boolean degenerate(int[] f, double[][] pos) {
        if (f[0] == f[1] || f[1] == f[2] || f[0] == f[2]) return true;
        return norm(cross(sub(pos[f[1]], pos[f[0]]), sub(pos[f[2]], pos[f[0]]))) < 1e-9;
    }

    /** {a, b, c, d, area} of a face's plane, or null if degenerate. */
    static double[] plane(int[] f, double[][] pos) {
        double[] nrm = cross(sub(pos[f[1]], pos[f[0]]), sub(pos[f[2]], pos[f[0]]));
        double len = norm(nrm);
        if (len < 1e-9) return null;
        for (int i = 0; i < 3; i++) nrm[i] /= len;
        return new double[]{nrm[0], nrm[1], nrm[2], -dot(nrm, pos[f[0]]), len / 2};
    }

    static void addQ(double[] q, double[] p, double w) {
        double a = p[0], b = p[1], c = p[2], d = p[3];
        q[0] += w * a * a; q[1] += w * a * b; q[2] += w * a * c; q[3] += w * a * d;
        q[4] += w * b * b; q[5] += w * b * c; q[6] += w * b * d;
        q[7] += w * c * c; q[8] += w * c * d; q[9] += w * d * d;
    }

    static double err(double[] q, double[] v) {
        double x = v[0], y = v[1], z = v[2];
        return q[0] * x * x + 2 * q[1] * x * y + 2 * q[2] * x * z + 2 * q[3] * x + q[4] * y * y + 2 * q[5] * y * z + 2 * q[6] * y
                + q[7] * z * z + 2 * q[8] * z + q[9];
    }

    static void pushEdge(PriorityQueue<double[]> heap, double[][] q, double[][] pos, int a, int b) {
        pushEdge(heap, q, pos, a, b, null);
    }

    static void pushEdge(PriorityQueue<double[]> heap, double[][] q, double[][] pos, int a, int b, int[] version) {
        double[] qs = new double[10];
        for (int i = 0; i < 10; i++) qs[i] = q[a][i] + q[b][i];
        double[] mid = {(pos[a][0] + pos[b][0]) / 2, (pos[a][1] + pos[b][1]) / 2, (pos[a][2] + pos[b][2]) / 2};
        double[][] cand = {pos[a], pos[b], mid};
        double best = Double.MAX_VALUE;
        double[] bp = null;
        for (double[] c : cand) {
            double e = err(qs, c);
            if (e < best) { best = e; bp = c; }
        }
        int va = version == null ? 0 : version[a], vb = version == null ? 0 : version[b];
        heap.add(new double[]{best, a, b, bp[0], bp[1], bp[2], va, vb});
    }

    /** Would moving vertex a to np (b merging in) flip or collapse any face of a that doesn't contain b? */
    static boolean flips(int a, int b, double[] np, List<Set<Integer>> vf, int[][] face, boolean[] dead, double[][] pos) {
        for (int f : vf.get(a)) {
            if (dead[f]) continue;
            int[] fv = face[f];
            if (fv[0] == b || fv[1] == b || fv[2] == b) continue;
            double[][] p0 = {pos[fv[0]], pos[fv[1]], pos[fv[2]]};
            double[] n0 = cross(sub(p0[1], p0[0]), sub(p0[2], p0[0]));
            double[][] p1 = p0.clone();
            for (int k = 0; k < 3; k++) if (fv[k] == a) p1[k] = np;
            double[] n1 = cross(sub(p1[1], p1[0]), sub(p1[2], p1[0]));
            double l0 = norm(n0), l1 = norm(n1);
            if (l1 < 1e-6 * Math.max(1, l0)) return true;
            if (l0 > 1e-9 && dot(n0, n1) / (l0 * l1) < 0.2) return true;
        }
        return false;
    }

    static long key(int a, int b) {
        return a < b ? ((long) a << 32) | b : ((long) b << 32) | a;
    }

    static double[] sub(double[] a, double[] b) { return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]}; }

    static double[] cross(double[] a, double[] b) {
        return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    static double dot(double[] a, double[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }

    static double norm(double[] a) { return Math.sqrt(dot(a, a)); }

    // ------------------------------------------------------------------ preview

    static void preview(List<ModelDefinition[]> pairs, File outFile) throws Exception {
        int cw = 260, ch = 260;
        java.awt.image.BufferedImage sheet = new java.awt.image.BufferedImage(4 * cw, pairs.size() * ch, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = sheet.createGraphics();
        for (int i = 0; i < pairs.size(); i++)
            for (int j = 0; j < 2; j++)
                for (int view = 0; view < 2; view++) {
                    ModelDefinition src = pairs.get(i)[j];
                    ModelDefinition c = copyForPreview(src);
                    java.awt.image.BufferedImage img = BuildChampionshipBoard.preview(c, new HashMap<>(), view == 0 ? 0.0 : 2.4, 15);
                    int x = (j * 2 + view) * cw, y = i * ch;
                    g.drawImage(img, x, y, cw, ch, null);
                    g.setColor(java.awt.Color.WHITE);
                    g.drawString((j == 0 ? "before " : "after ") + src.faceCount + "f", x + 4, y + 14);
                }
        g.dispose();
        javax.imageio.ImageIO.write(sheet, "png", outFile);
        System.out.println("preview " + outFile);
    }

    static ModelDefinition copyForPreview(ModelDefinition s) {
        ModelDefinition c = new ModelDefinition();
        c.vertexCount = s.vertexCount;
        c.faceCount = s.faceCount;
        int minY = 0, maxExt = 1;
        for (int v = 0; v < s.vertexCount; v++) {
            minY = Math.min(minY, s.vertexY[v]);
            maxExt = Math.max(maxExt, Math.max(Math.abs(s.vertexX[v]), Math.abs(s.vertexZ[v])));
        }
        double sc = 420.0 / Math.max(-minY, maxExt * 2);
        c.vertexX = new int[s.vertexCount]; c.vertexY = new int[s.vertexCount]; c.vertexZ = new int[s.vertexCount];
        for (int v = 0; v < s.vertexCount; v++) {
            c.vertexX[v] = (int) (s.vertexX[v] * sc);
            c.vertexY[v] = (int) (s.vertexY[v] * sc);
            c.vertexZ[v] = (int) (s.vertexZ[v] * sc);
        }
        c.faceIndices1 = s.faceIndices1; c.faceIndices2 = s.faceIndices2; c.faceIndices3 = s.faceIndices3;
        c.faceColors = s.faceColors;
        c.faceTransparencies = s.faceTransparencies;
        c.faceRenderTypes = s.faceRenderTypes;
        return c;
    }
}
