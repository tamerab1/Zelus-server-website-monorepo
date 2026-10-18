import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.ItemDefinition;
import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.loaders.ItemLoader;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.List;

// Read-only (2026-10-18, Soulbound v2): the camera for an item model shown in an interface MODEL widget (if_setobject
// + IF_SETANGLE: zoom, angle X, angle Y; no offsets, angle Z pinned to 0 by the zelussoulweapon plugin). The widget
// projects like an inventory icon (Geo) but centred on the widget. Picks the side-on angle (main axis on screen, a
// 25-31 degrees from above, xan2d 144-176) that covers the most of the box (broad face shown) while it stays inside the box with a margin
// at every yaw of the plugin's +-150 sway. Prints a Java table row per look; writes a silhouette preview sheet.
// Spec lines:   look <itemId> <boxW> <boxH> <label>
// Usage: <cachePath> <specFile> <out.png>
public class WidgetModelFrame {
    static final int SWAY = 150;

    public static void main(String[] args) throws Exception {
        List<String[]> spec = new ArrayList<>();
        for (String line : Files.readAllLines(new File(args[1]).toPath())) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            spec.add(line.split("\\s+", 5));
        }
        int cols = 3, cellW = 180, cellH = 120;
        BufferedImage sheet = new BufferedImage(cols * cellW, ((spec.size() + cols - 1) / cols) * cellH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        g.setColor(new Color(0x2a2620));
        g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        try (Store store = new Store(new File(args[0]))) {
            store.load();
            Archive items = store.getIndex(IndexType.CONFIGS).getArchive(ConfigType.ITEM.getId());
            FileData[] fd = items.getFileData();
            List<byte[]> contents = SpliceItemOption.splitChunks(items.decompress(store.getStorage().loadArchive(items)), fd.length);
            for (int k = 0; k < spec.size(); k++) {
                String[] s = spec.get(k);
                int id = Integer.parseInt(s[1]), w = Integer.parseInt(s[2]), h = Integer.parseInt(s[3]);
                ItemDefinition def = null;
                for (int i = 0; i < fd.length; i++)
                    if (fd[i].getId() == id) def = new ItemLoader().load(id, contents.get(i));
                Archive ma = store.getIndex(IndexType.MODELS).getArchive(def.inventoryModel);
                ModelDefinition m = new ModelLoader().load(def.inventoryModel, ma.decompress(store.getStorage().loadArchive(ma)));
                Geo geo = new Geo(m, def.resizeX, def.resizeY, def.resizeZ);
                int bestZoom = Integer.MAX_VALUE, bx = 0, by = 0;
                double bestArea = -1;
                for (int xan = 144; xan <= 176; xan += 16) // a clean 25-31 degree showcase pitch (round 6)
                    for (int yan = 0; yan < 2048; yan += 32) {
                        double[] a = geo.rotate(geo.axis[0], geo.axis[1], geo.axis[2], xan, yan, 0);
                        if (Math.hypot(a[0], a[1]) < 0.9)
                            continue;
                        int z = fitZoom(geo, xan, yan, w, h);
                        double area = area(geo, z, xan, yan, w, h); // the face shown, not just the length
                        if (area > bestArea) {
                            bestArea = area;
                            bestZoom = z;
                            bx = xan;
                            by = yan;
                        }
                    }
                System.out.printf("%s item %d box %dx%d: zoom=%d xan=%d yan=%d%n", s[4], id, w, h, bestZoom, bx, by);
                // preview: the box, the model silhouette at the chosen camera (sway centre) and its sway extremes outlined
                int ox = (k % cols) * cellW + (cellW - w) / 2, oy = (k / cols) * cellH + 16 + (cellH - 16 - h) / 2;
                g.setColor(new Color(0x33383e));
                g.fillRect(ox, oy, w, h);
                for (int sway : new int[]{-SWAY, SWAY, 0}) {
                    double[][] p = geo.project(bestZoom, bx, (by + sway + 2048) & 2047, 0, 0, 0, true);
                    g.setColor(sway == 0 ? new Color(0x2fd2d8) : new Color(0x55606a));
                    for (int f = 0; f < geo.faces; f++) {
                        int[] xs = {px(p[0][geo.fa[f]], w, ox), px(p[0][geo.fb[f]], w, ox), px(p[0][geo.fc[f]], w, ox)};
                        int[] ys = {px(p[1][geo.fa[f]], h, oy), px(p[1][geo.fb[f]], h, oy), px(p[1][geo.fc[f]], h, oy)};
                        g.fillPolygon(xs, ys, 3);
                    }
                }
                g.setColor(new Color(0xb08a48));
                g.drawRect(ox, oy, w - 1, h - 1);
                g.setColor(Color.WHITE);
                g.drawString(s[4] + " z" + bestZoom + " " + bx + "/" + by, (k % cols) * cellW + 4, (k / cols) * cellH + 12);
            }
        }
        g.dispose();
        ImageIO.write(sheet, "png", new File(args[2]));
        System.out.println("wrote " + args[2]);
    }

    /** Box pixels the model covers at a camera (the sway centre). */
    static double area(Geo geo, int zoom, int xan, int yan, int w, int h) {
        double[][] p = geo.project(zoom, xan, yan, 0, 0, 0, true);
        boolean[] grid = new boolean[w * h];
        for (int f = 0; f < geo.faces; f++) {
            double ax = p[0][geo.fa[f]] - 16 + w / 2.0, ay = p[1][geo.fa[f]] - 16 + h / 2.0;
            double bx = p[0][geo.fb[f]] - 16 + w / 2.0, by = p[1][geo.fb[f]] - 16 + h / 2.0;
            double cx = p[0][geo.fc[f]] - 16 + w / 2.0, cy = p[1][geo.fc[f]] - 16 + h / 2.0;
            int x0 = (int) Math.max(0, Math.floor(Math.min(ax, Math.min(bx, cx))));
            int x1 = (int) Math.min(w - 1, Math.ceil(Math.max(ax, Math.max(bx, cx))));
            int y0 = (int) Math.max(0, Math.floor(Math.min(ay, Math.min(by, cy))));
            int y1 = (int) Math.min(h - 1, Math.ceil(Math.max(ay, Math.max(by, cy))));
            for (int y = y0; y <= y1; y++)
                for (int x = x0; x <= x1; x++) {
                    double qx = x + 0.5, qy = y + 0.5;
                    double d1 = (qx - bx) * (ay - by) - (ax - bx) * (qy - by);
                    double d2 = (qx - cx) * (by - cy) - (bx - cx) * (qy - cy);
                    double d3 = (qx - ax) * (cy - ay) - (cx - ax) * (qy - ay);
                    boolean neg = d1 < 0 || d2 < 0 || d3 < 0, pos = d1 > 0 || d2 > 0 || d3 > 0;
                    if (!(neg && pos))
                        grid[y * w + x] = true;
                }
        }
        int c = 0;
        for (boolean b : grid)
            if (b)
                c++;
        return c;
    }

    static int px(double v, int size, int origin) {
        return (int) Math.round(v - 16 + size / 2.0) + origin;
    }

    /** The smallest zoom at which the model stays 6% inside the box at the centre and both ends of the sway. */
    static int fitZoom(Geo geo, int xan, int yan, int w, int h) {
        int lo = 100, hi = 60000;
        while (lo < hi) {
            int mid = (lo + hi) / 2;
            boolean fits = true;
            for (int sway : new int[]{-SWAY, -SWAY / 2, 0, SWAY / 2, SWAY}) {
                double[] b = geo.bounds(mid, xan, (yan + sway + 2048) & 2047, 0, 0, 0);
                double x0 = b[0] - 16 + w / 2.0, x1 = b[2] - 16 + w / 2.0, y0 = b[1] - 16 + h / 2.0, y1 = b[3] - 16 + h / 2.0;
                if (b[4] < 60 || x0 < w * 0.06 || x1 > w * 0.94 || y0 < h * 0.08 || y1 > h * 0.92) {
                    fits = false;
                    break;
                }
            }
            if (fits) hi = mid; else lo = mid + 1;
        }
        return lo;
    }
}
