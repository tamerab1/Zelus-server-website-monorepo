import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.loaders.ModelLoader;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.List;

// Read-only (2026-10-19, custom boss rigs): renders a model three times (front x/y, side z/y, top x/z) with every vertex
// skin label in its own colour and its number at the label's centroid, prints per-label vertex count / centroid / box,
// and draws a 50-unit grid with axis values so part boundaries can be read off for BossRig part rules.
// Optional part rules (BossRig syntax) are applied first, to check a segmentation.
// Usage: RigView <model.dat> <out.png> [rigSpec]
public class RigView {
    public static void main(String[] args) throws Exception {
        ModelDefinition m = new ModelLoader().load(0, Files.readAllBytes(new File(args[0]).toPath()));
        int[] lab = labels(m);
        if (args.length > 2) lab = BossRig.relabel(m, lab, BossRig.parse(new File(args[2])).rules);
        render(m, lab, new File(args[1]));
    }

    static int[] labels(ModelDefinition m) {
        int[] lab = new int[m.vertexCount];
        int[][] g = m.getVertexGroups();
        if (g != null)
            for (int l = 0; l < g.length; l++)
                if (g[l] != null) for (int v : g[l]) lab[v] = l;
        return lab;
    }

    static Color colour(int l) {
        float h = (l * 0.618034f) % 1f;
        return Color.getHSBColor(h, 0.75f, 0.95f);
    }

    static void render(ModelDefinition m, int[] lab, File out) throws Exception {
        int[] b = FindBossRig.bounds(m);
        Map<Integer, double[]> stats = new TreeMap<>();
        for (int v = 0; v < m.vertexCount; v++) {
            double[] s = stats.computeIfAbsent(lab[v], k -> new double[]{0, 0, 0, 0, 1e9, -1e9, 1e9, -1e9, 1e9, -1e9});
            s[0]++; s[1] += m.vertexX[v]; s[2] += m.vertexY[v]; s[3] += m.vertexZ[v];
            s[4] = Math.min(s[4], m.vertexX[v]); s[5] = Math.max(s[5], m.vertexX[v]);
            s[6] = Math.min(s[6], m.vertexY[v]); s[7] = Math.max(s[7], m.vertexY[v]);
            s[8] = Math.min(s[8], m.vertexZ[v]); s[9] = Math.max(s[9], m.vertexZ[v]);
        }
        for (var e : stats.entrySet()) {
            double[] s = e.getValue();
            System.out.printf("label %3d: %5d verts  centre %5.0f %5.0f %5.0f  x[%d,%d] y[%d,%d] z[%d,%d]%n", e.getKey(), (int) s[0],
                    s[1] / s[0], s[2] / s[0], s[3] / s[0], (int) s[4], (int) s[5], (int) s[6], (int) s[7], (int) s[8], (int) s[9]);
        }
        int W = 520, H = 520;
        BufferedImage img = new BufferedImage(W * 3, H + 20, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(0x202020));
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        int ext = Math.max(Math.max(b[1] - b[0], b[3] - b[2]), b[5] - b[4]);
        double sc = (W - 60) / (double) Math.max(1, ext);
        String[] names = {"FRONT  x right, y down (camera at -z)", "SIDE  z right (+z = behind), y down", "TOP  x right, z down"};
        for (int view = 0; view < 3; view++) {
            int ox = view * W;
            int ha = view == 1 ? 2 : 0, va = view == 2 ? 2 : 1; // axes: 0 x, 1 y, 2 z
            int hmin = b[ha * 2], hmax = b[ha * 2 + 1], vmin = b[va * 2], vmax = b[va * 2 + 1];
            double cx = ox + W / 2.0 - (hmin + hmax) / 2.0 * sc, cy = 20 + H / 2.0 - (vmin + vmax) / 2.0 * sc;
            // grid
            g.setFont(new Font("SansSerif", Font.PLAIN, 9));
            for (int t = (int) Math.floor(hmin / 50.0) * 50; t <= hmax; t += 50) {
                int px = (int) (cx + t * sc);
                g.setColor(t == 0 ? new Color(0x707070) : new Color(0x333333));
                g.drawLine(px, 20, px, 20 + H);
                g.setColor(new Color(0x909090));
                g.drawString("" + t, px + 1, 30);
            }
            for (int t = (int) Math.floor(vmin / 50.0) * 50; t <= vmax; t += 50) {
                int py = (int) (cy + t * sc);
                g.setColor(t == 0 ? new Color(0x707070) : new Color(0x333333));
                g.drawLine(ox, py, ox + W, py);
                g.setColor(new Color(0x909090));
                g.drawString("" + t, ox + 2, py - 1);
            }
            // faces sorted by depth (painter), coloured by their first vertex's label, lit by facing
            int dAxis = view == 0 ? 2 : view == 1 ? 0 : 1;
            int sign = view == 0 ? -1 : view == 1 ? -1 : -1; // camera on the negative side of the depth axis
            Integer[] order = new Integer[m.faceCount];
            double[] depth = new double[m.faceCount];
            int[][] P = {m.vertexX, m.vertexY, m.vertexZ};
            for (int f = 0; f < m.faceCount; f++) {
                order[f] = f;
                depth[f] = P[dAxis][m.faceIndices1[f]] + P[dAxis][m.faceIndices2[f]] + P[dAxis][m.faceIndices3[f]];
            }
            Arrays.sort(order, (p, q) -> Double.compare(depth[q] * -sign, depth[p] * -sign));
            for (int f : order) {
                int[] vs = {m.faceIndices1[f], m.faceIndices2[f], m.faceIndices3[f]};
                int[] xs = new int[3], ys = new int[3];
                for (int k = 0; k < 3; k++) {
                    xs[k] = (int) (cx + P[ha][vs[k]] * sc);
                    ys[k] = (int) (cy + P[va][vs[k]] * sc);
                }
                double ax = P[0][vs[1]] - P[0][vs[0]], ay = P[1][vs[1]] - P[1][vs[0]], az = P[2][vs[1]] - P[2][vs[0]];
                double bx = P[0][vs[2]] - P[0][vs[0]], by = P[1][vs[2]] - P[1][vs[0]], bz = P[2][vs[2]] - P[2][vs[0]];
                double[] nrm = {ay * bz - az * by, az * bx - ax * bz, ax * by - ay * bx};
                double len = Math.sqrt(nrm[0] * nrm[0] + nrm[1] * nrm[1] + nrm[2] * nrm[2]) + 1e-9;
                double light = 0.45 + 0.55 * Math.abs(nrm[dAxis] / len);
                Color c = colour(lab[vs[0]]);
                g.setColor(new Color((int) (c.getRed() * light), (int) (c.getGreen() * light), (int) (c.getBlue() * light)));
                g.fillPolygon(xs, ys, 3);
            }
            g.setFont(new Font("SansSerif", Font.BOLD, 11));
            for (var e : stats.entrySet()) {
                double[] s = e.getValue();
                double[] c3 = {s[1] / s[0], s[2] / s[0], s[3] / s[0]};
                int px = (int) (cx + c3[ha] * sc), py = (int) (cy + c3[va] * sc);
                g.setColor(Color.BLACK);
                g.drawString("" + e.getKey(), px + 1, py + 1);
                g.setColor(Color.WHITE);
                g.drawString("" + e.getKey(), px, py);
            }
            g.setColor(Color.YELLOW);
            g.drawString(names[view], ox + 4, 14);
        }
        g.dispose();
        ImageIO.write(img, "png", out);
        System.out.println("wrote " + out);
    }
}
