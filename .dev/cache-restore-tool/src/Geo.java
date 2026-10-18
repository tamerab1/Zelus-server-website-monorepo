import net.runelite.cache.definitions.ModelDefinition;

/**
 * The client's inventory-icon camera (ItemSpriteFactory + Model.projectAndDraw) applied to a model's raw vertices
 * (2026-10-18, AutoFrameItems): orthographic coverage for choosing an angle, and exact perspective bounds for solving
 * zoom2d / xOffset2d / yOffset2d without the renderer's near-camera face culling getting in the way.
 */
class Geo {
    final int n, faces;
    final int[] vx, vy, vz, fa, fb, fc;
    final int height;
    /** The model's main axis (unit vector, largest spread of its vertices). */
    final double[] axis;

    Geo(ModelDefinition m, int rx, int ry, int rz) {
        n = m.vertexCount;
        faces = m.faceCount;
        vx = new int[n];
        vy = new int[n];
        vz = new int[n];
        int h = 0;
        for (int i = 0; i < n; i++) {
            vx[i] = m.vertexX[i] * rx / 128;
            vy[i] = m.vertexY[i] * ry / 128;
            vz[i] = m.vertexZ[i] * rz / 128;
            h = Math.max(h, -vy[i]);
        }
        height = h;
        fa = m.faceIndices1;
        fb = m.faceIndices2;
        fc = m.faceIndices3;
        axis = principalAxis();
    }

    /** Power iteration on the vertex covariance. */
    double[] principalAxis() {
        double mx = 0, my = 0, mz = 0;
        for (int i = 0; i < n; i++) { mx += vx[i]; my += vy[i]; mz += vz[i]; }
        mx /= n; my /= n; mz /= n;
        double[][] c = new double[3][3];
        for (int i = 0; i < n; i++) {
            double[] d = {vx[i] - mx, vy[i] - my, vz[i] - mz};
            for (int a = 0; a < 3; a++) for (int b = 0; b < 3; b++) c[a][b] += d[a] * d[b];
        }
        double[] v = {1, 0.7, 0.3};
        for (int it = 0; it < 100; it++) {
            double[] w = new double[3];
            for (int a = 0; a < 3; a++) for (int b = 0; b < 3; b++) w[a] += c[a][b] * v[b];
            double l = Math.sqrt(w[0] * w[0] + w[1] * w[1] + w[2] * w[2]);
            for (int a = 0; a < 3; a++) v[a] = w[a] / l;
        }
        return v;
    }

    /** The camera rotation of a model-space direction: {screen x, screen y, depth}. */
    double[] rotate(double x, double y, double z, int xan, int yan, int zan) {
        double sz = sin(zan), cz = cos(zan), sy = sin(yan), cy = cos(yan), sx = sin(xan), cx = cos(xan);
        double t = y * sz + x * cz;
        y = y * cz - x * sz;
        x = t;
        t = z * sy + x * cy;
        z = z * cy - x * sy;
        x = t;
        double ty = y * cx - z * sx;
        z = y * sx + z * cx;
        return new double[]{x, ty, z};
    }

    static double sin(int a) {
        return (int) (65536.0 * Math.sin(a * Math.PI / 1024)) / 65536.0;
    }

    static double cos(int a) {
        return (int) (65536.0 * Math.cos(a * Math.PI / 1024)) / 65536.0;
    }

    /** Screen x / y / depth per vertex (yaw by yan2d, then pitch by xan2d, as projectAndDraw); persp=false = orthographic. */
    double[][] project(int zoom, int xan, int yan, int xo, int yo, boolean persp) {
        return project(zoom, xan, yan, 0, xo, yo, persp);
    }

    double[][] project(int zoom, int xan, int yan, int zan, int xo, int yo, boolean persp) {
        double sy = sin(yan), cy = cos(yan), sx = sin(xan), cx = cos(xan), sz = sin(zan), cz = cos(zan);
        double yOff = height / 2.0 + Math.floor(zoom * sx) + yo, zOff = Math.floor(zoom * cx) + yo;
        double[][] out = new double[3][n];
        for (int i = 0; i < n; i++) {
            double x = vx[i], y = vy[i], z = vz[i];
            if (zan != 0) {
                double tz = y * sz + x * cz;
                y = y * cz - x * sz;
                x = tz;
            }
            double t = z * sy + x * cy;
            z = z * cy - x * sy;
            x = t + xo;
            y += yOff;
            z += zOff;
            double ty = y * cx - z * sx;
            z = y * sx + z * cx;
            out[0][i] = persp ? x * 512 / z + 16 : x;
            out[1][i] = persp ? ty * 512 / z + 16 : ty;
            out[2][i] = z;
        }
        return out;
    }

    /** Cells of a 68x60 grid (the 34x30 slot at 2x) the triangles fill once the model is scaled to fit (orthographic). */
    double coverage(int xan, int yan, int zan) {
        double[][] p = project(0, xan, yan, zan, 0, 0, false);
        double minX = 1e9, minY = 1e9, maxX = -1e9, maxY = -1e9;
        for (int i = 0; i < n; i++) {
            minX = Math.min(minX, p[0][i]);
            maxX = Math.max(maxX, p[0][i]);
            minY = Math.min(minY, p[1][i]);
            maxY = Math.max(maxY, p[1][i]);
        }
        double s = Math.min(68 / Math.max(1e-6, maxX - minX), 60 / Math.max(1e-6, maxY - minY));
        boolean[] grid = new boolean[68 * 60];
        for (int f = 0; f < faces; f++) {
            double ax = (p[0][fa[f]] - minX) * s, ay = (p[1][fa[f]] - minY) * s;
            double bx = (p[0][fb[f]] - minX) * s, by = (p[1][fb[f]] - minY) * s;
            double cx = (p[0][fc[f]] - minX) * s, cy = (p[1][fc[f]] - minY) * s;
            int x0 = (int) Math.max(0, Math.floor(Math.min(ax, Math.min(bx, cx))));
            int x1 = (int) Math.min(67, Math.ceil(Math.max(ax, Math.max(bx, cx))));
            int y0 = (int) Math.max(0, Math.floor(Math.min(ay, Math.min(by, cy))));
            int y1 = (int) Math.min(59, Math.ceil(Math.max(ay, Math.max(by, cy))));
            for (int y = y0; y <= y1; y++)
                for (int x = x0; x <= x1; x++) {
                    double px = x + 0.5, py = y + 0.5;
                    double d1 = (px - bx) * (ay - by) - (ax - bx) * (py - by);
                    double d2 = (px - cx) * (by - cy) - (bx - cx) * (py - cy);
                    double d3 = (px - ax) * (cy - ay) - (cx - ax) * (py - ay);
                    boolean neg = d1 < 0 || d2 < 0 || d3 < 0, pos = d1 > 0 || d2 > 0 || d3 > 0;
                    if (!(neg && pos))
                        grid[y * 68 + x] = true;
                }
        }
        int c = 0;
        for (boolean g : grid)
            if (g)
                c++;
        return c;
    }

    /** {minX, minY, maxX, maxY, minDepth} on screen. */
    double[] bounds(int zoom, int xan, int yan, int zan, int xo, int yo) {
        double[][] p = project(zoom, xan, yan, zan, xo, yo, true);
        double[] b = {1e9, 1e9, -1e9, -1e9, 1e9};
        for (int i = 0; i < n; i++) {
            b[0] = Math.min(b[0], p[0][i]);
            b[1] = Math.min(b[1], p[1][i]);
            b[2] = Math.max(b[2], p[0][i]);
            b[3] = Math.max(b[3], p[1][i]);
            b[4] = Math.min(b[4], p[2][i]);
        }
        return b;
    }

    /** {zoom2d, xOffset2d, yOffset2d}: centred, as large as fits with a 1px margin, nothing near the camera. */
    /**
     * The icon angle {xan2d, yan2d, zan2d}: side-on to the main axis (it keeps at least 96% of its length on screen),
     * and of those the view whose fitted silhouette fills the most of the slot (a long weapon ends up diagonal, broad
     * face shown). Ties go to the view that puts the model origin (a weapon grip) lower-left.
     */
    int[] bestAngle(boolean front) {
        return bestAngle(front, true);
    }

    /** allowZan=false: zan2d stays 0 (model widgets show the icon angles without it; 2026-10-18 soul looks). */
    int[] bestAngle(boolean front, boolean allowZan) {
        java.util.List<double[]> ranked = new java.util.ArrayList<>();
        for (int xan = 0; xan < 2048; xan += 64)
            for (int yan = 0; yan < 2048; yan += 64)
                for (int zan = 0; zan < (allowZan ? 2048 : 1); zan += 64) {
                    if (front && (zan != 0 || !((yan == 0 || yan == 1024) && (xan <= 256 || xan >= 1792))))
                        continue;
                    double[] a = rotate(axis[0], axis[1], axis[2], xan, yan, zan);
                    if (!front && Math.hypot(a[0], a[1]) < 0.96)
                        continue;
                    double score = coverage(xan, yan, zan);
                    double[][] p = project(0, xan, yan, zan, 0, 0, false);
                    double minX = 1e9, maxX = -1e9, minY = 1e9, maxY = -1e9;
                    for (int i = 0; i < n; i++) {
                        minX = Math.min(minX, p[0][i]); maxX = Math.max(maxX, p[0][i]);
                        minY = Math.min(minY, p[1][i]); maxY = Math.max(maxY, p[1][i]);
                    }
                    // origin lower-left: 0 at the top-right corner .. 1 at the bottom-left
                    double ox = (0 - minX) / Math.max(1e-6, maxX - minX), oy = (0 - minY) / Math.max(1e-6, maxY - minY);
                    score += 40 * ((1 - ox) + oy) / 2;
                    ranked.add(new double[]{score, xan, yan, zan});
                }
        ranked.sort((x, y) -> Double.compare(y[0], x[0]));
        for (double[] r : ranked) {
            int[] a = {(int) r[1], (int) r[2], (int) r[3]};
            if (converges(a[0], a[1], a[2]))
                return a;
        }
        throw new IllegalStateException("no icon angle converged");
    }

    /** The fit is usable: centred within a pixel, a sane zoom, nothing behind the near plane. */
    boolean converges(int xan, int yan, int zan) {
        int[] f = fit(xan, yan, zan);
        if (f[0] < 300 || f[0] > 20000 || Math.abs(f[1]) > 2000 || Math.abs(f[2]) > 2000)
            return false;
        double[] b = bounds(f[0], xan, yan, zan, f[1], f[2]);
        return b[4] > 60 && Math.abs((b[0] + b[2]) / 2 - 17.5) < 1 && Math.abs((b[1] + b[3]) / 2 - 15.5) < 1;
    }

    int[] fit(int xan, int yan) {
        return fit(xan, yan, 0);
    }

    int[] fit(int xan, int yan, int zan) {
        int zoom = 6000, xo = 0, yo = 0;
        for (int iter = 0; iter < 6; iter++) {
            for (int k = 0; k < 4; k++) { // centre: finite-difference Newton steps on the offsets
                double[] b = bounds(zoom, xan, yan, zan, xo, yo);
                double dx = (b[0] + b[2]) / 2 - 17.5, dy = (b[1] + b[3]) / 2 - 15.5;
                double[] bx = bounds(zoom, xan, yan, zan, xo + 20, yo), by = bounds(zoom, xan, yan, zan, xo, yo + 20);
                double gx = ((bx[0] + bx[2]) - (b[0] + b[2])) / 40.0, gy = ((by[1] + by[3]) - (b[1] + b[3])) / 40.0;
                if (Math.abs(gx) > 1e-6)
                    xo -= (int) Math.round(dx / gx);
                if (Math.abs(gy) > 1e-6)
                    yo -= (int) Math.round(dy / gy);
            }
            int lo = 100, hi = 60000;
            while (lo < hi) {
                int mid = (lo + hi) / 2;
                double[] b = bounds(mid, xan, yan, zan, xo, yo);
                boolean fits = b[4] > 60 && b[0] >= 1.5 && b[1] >= 1.5 && b[2] <= 33.5 && b[3] <= 29.5;
                if (fits)
                    hi = mid;
                else
                    lo = mid + 1;
            }
            zoom = lo;
        }
        return new int[]{zoom, xo, yo};
    }
}
