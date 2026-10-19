import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.fs.Store;

import java.io.File;

// Finds the icon camera angles that show a ripped inventory model UPRIGHT (2026-10-19, Vermilion masori body: two
// eyeballed picks were upside down). The worn model is upright by definition (y grows down, front = -z). The inventory
// model is the same mesh in another pose, so the rigid rotation worn -> inventory (Horn's quaternion fit over vertex i <->
// vertex i, checked by its RMS) carries "up" (0,-1,0) and "front" (0,0,-1) into the inventory model. Then every
// xan2d / yan2d (zan2d 0) is scored through Geo.rotate -- the icon camera's own rotation -- for: up pointing up the
// screen, front facing the viewer (tilted toward the top by a few degrees, like real OSRS icons).
// Usage: OrientIcon <cache> <wornModel> <inventoryModel> [<wornModel> <inventoryModel> ...]
public class OrientIcon {
    public static void main(String[] args) throws Exception {
        try (Store store = new Store(new File(args[0]))) {
            store.load();
            for (int a = 1; a + 1 < args.length; a += 2) {
                ModelDefinition w = BuildTierPets.model(store, Integer.parseInt(args[a]));
                ModelDefinition inv = BuildTierPets.model(store, Integer.parseInt(args[a + 1]));
                solve(args[a] + " -> " + args[a + 1], w, inv);
            }
        }
    }

    static void solve(String name, ModelDefinition w, ModelDefinition inv) {
        int n = Math.min(w.vertexCount, inv.vertexCount);
        double[] cw = centroid(w, n), ci = centroid(inv, n);
        double[][] S = new double[3][3];
        for (int i = 0; i < n; i++) {
            double[] p = {w.vertexX[i] - cw[0], w.vertexY[i] - cw[1], w.vertexZ[i] - cw[2]};
            double[] q = {inv.vertexX[i] - ci[0], inv.vertexY[i] - ci[1], inv.vertexZ[i] - ci[2]};
            for (int r = 0; r < 3; r++) for (int c = 0; c < 3; c++) S[r][c] += p[r] * q[c];
        }
        double[][] N = {
                {S[0][0] + S[1][1] + S[2][2], S[1][2] - S[2][1], S[2][0] - S[0][2], S[0][1] - S[1][0]},
                {S[1][2] - S[2][1], S[0][0] - S[1][1] - S[2][2], S[0][1] + S[1][0], S[2][0] + S[0][2]},
                {S[2][0] - S[0][2], S[0][1] + S[1][0], -S[0][0] + S[1][1] - S[2][2], S[1][2] + S[2][1]},
                {S[0][1] - S[1][0], S[2][0] + S[0][2], S[1][2] + S[2][1], -S[0][0] - S[1][1] + S[2][2]}};
        double[] q = maxEigenvector(N);
        double[][] R = rotation(q);
        double rms = 0, spread = 0;
        for (int i = 0; i < n; i++) {
            double[] p = {w.vertexX[i] - cw[0], w.vertexY[i] - cw[1], w.vertexZ[i] - cw[2]};
            double[] rp = mul(R, p);
            rms += sq(rp[0] - (inv.vertexX[i] - ci[0])) + sq(rp[1] - (inv.vertexY[i] - ci[1])) + sq(rp[2] - (inv.vertexZ[i] - ci[2]));
            spread += sq(p[0]) + sq(p[1]) + sq(p[2]);
        }
        rms = Math.sqrt(rms / n);
        spread = Math.sqrt(spread / n);
        double[] up = mul(R, new double[]{0, -1, 0}), front = mul(R, new double[]{0, 0, -1});
        System.out.printf("%s: %d vertices paired, fit RMS %.1f (model spread %.1f)%s%n", name, n, rms, spread,
                rms > spread * 0.25 ? "  ** POOR FIT: the meshes don't correspond vertex for vertex" : "");
        System.out.printf("  up in the inventory model (%.2f %.2f %.2f), front (%.2f %.2f %.2f)%n", up[0], up[1], up[2], front[0], front[1], front[2]);
        Geo g = new Geo(inv, 128, 128, 128);
        int bx = 0, by = 0;
        double best = -1e9;
        for (int xan = 0; xan < 2048; xan += 8)
            for (int yan = 0; yan < 2048; yan += 8) {
                double[] su = g.rotate(up[0], up[1], up[2], xan, yan, 0);
                double[] sf = g.rotate(front[0], front[1], front[2], xan, yan, 0);
                // screen y grows downward; depth grows away from the camera
                double score = -su[1] - 0.4 * Math.abs(su[0]) - sf[2] - 0.3 * Math.abs(sf[0]) - 0.5 * Math.abs(-sf[2] - 0.94);
                if (score > best) { best = score; bx = xan; by = yan; }
            }
        double[] su = g.rotate(up[0], up[1], up[2], bx, by, 0), sf = g.rotate(front[0], front[1], front[2], bx, by, 0);
        System.out.printf("  upright angles: xan2d %d, yan2d %d  (up on screen %.2f %.2f, front depth %.2f)%n", bx, by, su[0], su[1], sf[2]);
        String check = System.getProperty("check"); // -Dcheck=xan,yan;xan,yan : how other angles show it
        if (check != null)
            for (String c : check.split(";")) {
                int cx = Integer.parseInt(c.split(",")[0]), cy = Integer.parseInt(c.split(",")[1]);
                double[] u2 = g.rotate(up[0], up[1], up[2], cx, cy, 0), f2 = g.rotate(front[0], front[1], front[2], cx, cy, 0);
                System.out.printf("  at %d,%d: up on screen (%.2f, %.2f)%s, front depth %.2f%s%n", cx, cy, u2[0], u2[1],
                        u2[1] < -0.7 ? " = upright" : u2[1] > 0.7 ? " = UPSIDE DOWN" : " = sideways", f2[2], f2[2] < 0 ? " (front)" : " (back)");
            }
    }

    static double[] centroid(ModelDefinition m, int n) {
        double[] c = new double[3];
        for (int i = 0; i < n; i++) { c[0] += m.vertexX[i]; c[1] += m.vertexY[i]; c[2] += m.vertexZ[i]; }
        for (int k = 0; k < 3; k++) c[k] /= n;
        return c;
    }

    static double[] maxEigenvector(double[][] N) {
        // shift so the largest eigenvalue dominates, then power iteration
        double shift = 0;
        for (double[] r : N) for (double v : r) shift += Math.abs(v);
        double[] v = {1, 0.3, 0.2, 0.1};
        for (int it = 0; it < 2000; it++) {
            double[] u = new double[4];
            for (int r = 0; r < 4; r++) { for (int c = 0; c < 4; c++) u[r] += N[r][c] * v[c]; u[r] += shift * v[r]; }
            double l = Math.sqrt(u[0] * u[0] + u[1] * u[1] + u[2] * u[2] + u[3] * u[3]);
            for (int r = 0; r < 4; r++) v[r] = u[r] / l;
        }
        return v;
    }

    static double[][] rotation(double[] q) {
        double w = q[0], x = q[1], y = q[2], z = q[3];
        return new double[][]{
                {w * w + x * x - y * y - z * z, 2 * (x * y - w * z), 2 * (x * z + w * y)},
                {2 * (x * y + w * z), w * w - x * x + y * y - z * z, 2 * (y * z - w * x)},
                {2 * (x * z - w * y), 2 * (y * z + w * x), w * w - x * x - y * y + z * z}};
    }

    static double[] mul(double[][] R, double[] p) {
        return new double[]{R[0][0] * p[0] + R[0][1] * p[1] + R[0][2] * p[2], R[1][0] * p[0] + R[1][1] * p[1] + R[1][2] * p[2],
                R[2][0] * p[0] + R[2][1] * p[1] + R[2][2] * p[2]};
    }

    static double sq(double v) { return v * v; }
}
