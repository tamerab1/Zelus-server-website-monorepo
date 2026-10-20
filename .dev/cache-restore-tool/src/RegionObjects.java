import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.ObjectDefinition;
import net.runelite.cache.definitions.loaders.ObjectLoader;
import net.runelite.cache.fs.*;
import net.runelite.cache.region.Location;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import java.util.List;

// Read-only (2026-10-20, region design): the objects a map square uses (id, loc type, count, name, size, actions), most
// used first, and a contact sheet that renders each one on its own (SceneRenderer, fitted to its bounds) -- the palette
// for building a new region out of real OSRS pieces.
// Usage: RegionObjects <cache> <mx_my>[,<mx_my>...] <out.png> [max] [type filter: all|walls|scenery|floor] [x0,y0,x1,y1]
public class RegionObjects {
    public static void main(String[] a) throws Exception {
        int max = a.length > 3 ? Integer.parseInt(a[3]) : 96;
        String filter = a.length > 4 ? a[4] : "all";
        int[] rect = a.length > 5 ? Arrays.stream(a[5].split(",")).mapToInt(Integer::parseInt).toArray() : new int[]{0, 0, 63, 63};
        try (Store store = new Store(new File(a[0]))) {
            store.load();
            RenderRegion.Scene defs = RenderRegion.Scene.empty(store);
            Map<String, int[]> counts = new LinkedHashMap<>(); // "id:type" -> count, orientation seen
            for (String sq : a[1].split(",")) {
                String[] p = sq.split("_");
                RenderRegion.Scene s = RenderRegion.Scene.load(store, Integer.parseInt(p[0]), Integer.parseInt(p[1]));
                for (Location l : s.locs) {
                    int lx = l.getPosition().getX() & 63, ly = l.getPosition().getY() & 63;
                    if (lx < rect[0] || ly < rect[1] || lx > rect[2] || ly > rect[3]) continue;
                    int t = l.getType();
                    boolean wall = t <= 3 || t == 9, deco = t >= 4 && t <= 8, floor = t == 22;
                    if (filter.equals("walls") && !wall) continue;
                    if (filter.equals("scenery") && (wall || deco || floor)) continue;
                    if (filter.equals("floor") && !floor) continue;
                    counts.computeIfAbsent(l.getId() + ":" + t, k -> new int[2])[0]++;
                }
            }
            List<Map.Entry<String, int[]>> list = new ArrayList<>(counts.entrySet());
            list.sort((x, y) -> y.getValue()[0] - x.getValue()[0]);
            list = list.subList(0, Math.min(max, list.size()));
            int cell = 150, cols = 8, rows = (list.size() + cols - 1) / cols;
            BufferedImage sheet = new BufferedImage(cols * cell, rows * (cell + 14), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = sheet.createGraphics();
            g.setFont(new Font("Dialog", Font.PLAIN, 10));
            for (int i = 0; i < list.size(); i++) {
                String[] k = list.get(i).getKey().split(":");
                int id = Integer.parseInt(k[0]), type = Integer.parseInt(k[1]);
                ObjectDefinition d = new ObjectLoader().load(id, defs.objectBytes.get(id));
                SceneRenderer r = new SceneRenderer(cell * 2, cell * 2, 0x2c3a24);
                // framed by the model's own size: its height and footprint set the camera distance
                int span = Math.max(1, Math.max(d.getSizeX(), d.getSizeY()));
                int top = 0, ext = 64 * span;
                if (d.getObjectModels() != null)
                    for (int mId : d.getObjectModels()) {
                        try {
                            var md = BuildTierPets.model(store, mId);
                            for (int v = 0; v < md.vertexCount; v++) {
                                top = Math.min(top, md.vertexY[v] * d.getModelSizeHeight() / 128);
                                ext = Math.max(ext, Math.abs(md.vertexX[v] * d.getModelSizeX() / 128));
                                ext = Math.max(ext, Math.abs(md.vertexZ[v] * d.getModelSizeY() / 128));
                            }
                        } catch (Exception ignored) {
                        }
                    }
                double size = Math.max(-top, ext * 2) + 64;
                double cx = 32 * 128 + (type <= 9 ? 64 : span * 64), cz = 32 * 128 + (type <= 9 ? 64 : span * 64);
                r.camera(cx, top / 2.0, cz, Math.toRadians(30), Math.toRadians(28), size * 1.9);
                defs.drawLoc(r, id, type, 0, 0, 32, 32);
                BufferedImage img = r.image();
                int x = (i % cols) * cell, y = (i / cols) * (cell + 14);
                g.drawImage(img, x, y + 14, cell, cell, null);
                g.setColor(Color.YELLOW);
                String name = d.getName() == null ? "" : d.getName();
                g.drawString(id + " t" + type + " x" + list.get(i).getValue()[0], x + 2, y + 11);
                g.setColor(Color.WHITE);
                g.drawString(name.length() > 20 ? name.substring(0, 20) : name, x + 2, y + 26);
                System.out.printf("%6d t%-2d x%-4d %dx%d %-22s %s%n", id, type, list.get(i).getValue()[0], d.getSizeX(), d.getSizeY(),
                        name, d.getActions() == null ? "" : Arrays.toString(d.getActions()));
            }
            g.dispose();
            ImageIO.write(sheet, "png", new File(a[2]));
        }
    }
}
