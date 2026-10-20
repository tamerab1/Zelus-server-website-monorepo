import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.SpriteDefinition;
import net.runelite.cache.definitions.loaders.SpriteLoader;
import net.runelite.cache.fs.*;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

// Read-only (2026-10-20, interface redesign): contact sheet of sprite groups (every frame), labelled id:frame WxH, on a
// stone background, to find OSRS UI pieces (9-slice buttons, insets, scrollbars, icons) by eye.
// Usage: SpriteSheet <cache> <out.png> <from-to|id>[,...] [maxSide]
public class SpriteSheet {
    public static void main(String[] a) throws Exception {
        int max = a.length > 3 ? Integer.parseInt(a[3]) : 120;
        List<int[]> ids = new ArrayList<>();
        for (String part : a[2].split(",")) {
            String[] r = part.split("-");
            int from = Integer.parseInt(r[0]), to = r.length > 1 ? Integer.parseInt(r[1]) : from;
            for (int i = from; i <= to; i++) ids.add(new int[]{i});
        }
        try (Store store = new Store(new File(a[0]))) {
            store.load();
            Storage storage = store.getStorage();
            Index sprites = store.getIndex(IndexType.SPRITES);
            List<BufferedImage> imgs = new ArrayList<>();
            List<String> labels = new ArrayList<>();
            for (int[] idv : ids) {
                Archive ar = sprites.getArchive(idv[0]);
                if (ar == null) continue;
                SpriteDefinition[] defs;
                try {
                    defs = new SpriteLoader().load(idv[0], ar.decompress(storage.loadArchive(ar)));
                } catch (Exception e) {
                    continue;
                }
                for (SpriteDefinition sd : defs) {
                    if (sd.getWidth() <= 0 || sd.getHeight() <= 0 || sd.getWidth() > max || sd.getHeight() > max) continue;
                    BufferedImage s = new BufferedImage(sd.getWidth(), sd.getHeight(), BufferedImage.TYPE_INT_ARGB);
                    int[] px = sd.getPixels();
                    for (int i = 0; i < px.length; i++)
                        if (px[i] != 0) s.setRGB(i % sd.getWidth(), i / sd.getWidth(), 0xff000000 | px[i]);
                    imgs.add(s);
                    labels.add(idv[0] + ":" + sd.getFrame() + " " + sd.getWidth() + "x" + sd.getHeight());
                    if (imgs.size() >= 400) break;
                }
                if (imgs.size() >= 400) break;
            }
            int cell = Math.max(48, Math.min(max * 2, 200)), cols = Math.max(1, 1400 / (cell + 4));
            int rows = (imgs.size() + cols - 1) / cols;
            BufferedImage sheet = new BufferedImage(cols * (cell + 4), Math.max(1, rows) * (cell + 18), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = sheet.createGraphics();
            g.setColor(new Color(0x3e3529));
            g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
            g.setFont(new Font("Dialog", Font.PLAIN, 10));
            for (int i = 0; i < imgs.size(); i++) {
                int x = (i % cols) * (cell + 4), y = (i / cols) * (cell + 18);
                BufferedImage s = imgs.get(i);
                int sc = Math.max(1, Math.min(4, cell / Math.max(s.getWidth(), s.getHeight())));
                g.drawImage(s, x + 2, y + 14, s.getWidth() * sc, s.getHeight() * sc, null);
                g.setColor(Color.YELLOW);
                g.drawString(labels.get(i), x + 2, y + 11);
            }
            g.dispose();
            ImageIO.write(sheet, "png", new File(a[1]));
            System.out.println("wrote " + imgs.size() + " sprites to " + a[1]);
        }
    }
}
