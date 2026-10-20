import net.runelite.cache.definitions.OverlayDefinition;
import net.runelite.cache.definitions.UnderlayDefinition;
import net.runelite.cache.fs.Store;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import java.util.List;

// Read-only (2026-10-20, region design): the floors (stored overlay / underlay ids) a map square uses, with counts and a
// swatch sheet (overlay: its rgb / texture average colour and texture id; underlay: its colour).
// Usage: RegionFloors <cache> <mx_my>[,...] <out.png>
public class RegionFloors {
    public static void main(String[] a) throws Exception {
        try (Store store = new Store(new File(a[0]))) {
            store.load();
            Map<Integer, Integer> over = new TreeMap<>(), under = new TreeMap<>();
            RenderRegion.Scene defs = null;
            for (String sq : a[1].split(",")) {
                String[] p = sq.split("_");
                RenderRegion.Scene s = RenderRegion.Scene.load(store, Integer.parseInt(p[0]), Integer.parseInt(p[1]));
                defs = s;
                for (int x = 0; x < 64; x++) for (int y = 0; y < 64; y++) {
                    if (s.overlay[0][x][y] > 0) over.merge(s.overlay[0][x][y], 1, Integer::sum);
                    if (s.underlay[0][x][y] > 0) under.merge(s.underlay[0][x][y], 1, Integer::sum);
                }
            }
            List<String> rows = new ArrayList<>();
            List<Integer> colours = new ArrayList<>();
            for (var e : over.entrySet()) {
                OverlayDefinition od = defs.overlays.get(e.getKey() - 1);
                int c = od == null ? 0 : od.getTexture() >= 0 && defs.textureColour.containsKey(od.getTexture()) ? defs.textureColour.get(od.getTexture()) : od.getRgbColor();
                rows.add("overlay " + e.getKey() + " x" + e.getValue() + (od != null && od.getTexture() >= 0 ? " tex " + od.getTexture() : ""));
                colours.add(c);
            }
            for (var e : under.entrySet()) {
                UnderlayDefinition ud = defs.underlays.get(e.getKey() - 1);
                rows.add("underlay " + e.getKey() + " x" + e.getValue());
                colours.add(ud == null ? 0 : ud.getColor());
            }
            BufferedImage img = new BufferedImage(4 * 220, ((rows.size() + 3) / 4) * 40, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            for (int i = 0; i < rows.size(); i++) {
                int x = (i % 4) * 220, y = (i / 4) * 40;
                g.setColor(new Color(colours.get(i)));
                g.fillRect(x + 4, y + 4, 32, 32);
                g.setColor(Color.WHITE);
                g.drawString(rows.get(i), x + 42, y + 24);
                System.out.println(rows.get(i) + String.format(" #%06x", colours.get(i)));
            }
            g.dispose();
            ImageIO.write(img, "png", new File(a[2]));
        }
    }
}
