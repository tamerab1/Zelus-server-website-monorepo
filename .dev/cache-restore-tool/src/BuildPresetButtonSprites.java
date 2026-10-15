import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.SpriteDefinition;
import net.runelite.cache.definitions.loaders.SpriteLoader;
import net.runelite.cache.fs.*;
import net.runelite.cache.index.FileData;
import net.runelite.cache.index.IndexData;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.*;
import java.util.List;
import java.util.zip.CRC32;

// Builds the bank "Presets" button sprites: copies of the bank's "?" tutorial button frames
// (2521 normal, 2522 hover) with the "?" glyph wiped and the armour figure from the
// "deposit worn items" icon (1042, its green arrow dropped) drawn in its place.
// The new single-frame sprites are encoded from scratch and must decode back (real SpriteLoader)
// to exactly the composed pixels before anything is written.
// Usage: verify|apply <cachePath> <normalId> <hoverId> [previewDir]
public class BuildPresetButtonSprites {
    public static void main(String[] args) throws Exception {
        String mode = args[0];
        int[] dst = {Integer.parseInt(args[2]), Integer.parseInt(args[3])};
        int[] src = {2521, 2522};
        File previewDir = args.length > 4 ? new File(args[4]) : null;
        try (Store store = new Store(new File(args[1]))) {
            store.load();
            Storage storage = store.getStorage();
            Index sprites = store.getIndex(IndexType.SPRITES);
            SpriteDefinition figureSrc = load(storage, sprites, 1042);
            BufferedImage figure = figure(figureSrc);

            List<Object[]> out = new ArrayList<>();
            for (int k = 0; k < 2; k++) {
                if (sprites.getArchive(dst[k]) != null) throw new IllegalStateException("sprite " + dst[k] + " already exists");
                Archive sa = sprites.getArchive(src[k]);
                SpriteDefinition base = load(storage, sprites, src[k]);
                int w = base.getWidth(), h = base.getHeight();
                int[] px = base.getPixels().clone();
                wipeGlyph(px, w, h);
                drawFigure(px, w, h, figure);

                byte[] enc = encode(px, w, h, base);
                SpriteDefinition back = new SpriteLoader().load(dst[k], enc)[0];
                if (back.getWidth() != w || back.getHeight() != h || back.getOffsetX() != base.getOffsetX() || back.getOffsetY() != base.getOffsetY())
                    throw new IllegalStateException("geometry mismatch");
                int[] bp = back.getPixels();
                for (int i = 0; i < px.length; i++)
                    if ((bp[i] & 0xFFFFFF) != (px[i] & 0xFFFFFF)) throw new IllegalStateException("pixel " + i + " did not round-trip");
                if (previewDir != null) {
                    BufferedImage img = new BufferedImage(w * 8, h * 8, BufferedImage.TYPE_INT_RGB);
                    BufferedImage s = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
                    s.setRGB(0, 0, w, h, px, 0, w);
                    img.getGraphics().drawImage(s, 0, 0, w * 8, h * 8, null);
                    javax.imageio.ImageIO.write(img, "png", new File(previewDir, "preset_button_" + dst[k] + ".png"));
                }
                System.out.println("sprite " + src[k] + " -> " + dst[k] + " " + w + "x" + h + " (" + enc.length + " bytes, round-trip OK)");
                out.add(new Object[]{dst[k], sa.getCompression(), enc});
            }
            if (mode.equals("verify")) {
                System.out.println("VERIFY OK -- nothing written.");
                return;
            }
            if (!mode.equals("apply")) throw new IllegalArgumentException("mode must be verify or apply");
            for (Object[] o : out) {
                int id = (Integer) o[0];
                byte[] raw = (byte[]) o[2];
                Archive a = sprites.addArchive(id);
                a.setNameHash(-1);
                a.setCompression((Integer) o[1]);
                a.setRevision(1);
                Container c = new Container(a.getCompression(), -1);
                c.compress(raw, null);
                storage.store(sprites.getId(), id, c.data);
                CRC32 crc = new CRC32();
                crc.update(c.data);
                a.setCrc((int) crc.getValue());
                a.setCompressedSize(c.data.length);
                a.setDecompressedSize(raw.length);
                FileData fd = new FileData();
                fd.setId(0);
                a.setFileData(new FileData[]{fd});
            }
            IndexData indexData = sprites.toIndexData();
            Container c = new Container(sprites.getCompression(), -1);
            c.compress(indexData.writeIndexData(), null);
            storage.store(255, sprites.getId(), c.data);
            sprites.setCrc(c.crc);
            System.out.println("APPLY complete -- " + out.size() + " sprites written.");
        }
    }

    static SpriteDefinition load(Storage storage, Index sprites, int id) throws Exception {
        Archive a = sprites.getArchive(id);
        return new SpriteLoader().load(id, a.decompress(storage.loadArchive(a)))[0];
    }

    static int lum(int c) {
        return ((c >> 16) & 0xFF) * 3 + ((c >> 8) & 0xFF) * 6 + (c & 0xFF);
    }

    /** Replaces the dark "?" pixels inside the bevelled frame with the frame's fill colour of that row. */
    static void wipeGlyph(int[] px, int w, int h) {
        for (int y = 4; y < h - 4; y++) {
            int fill = px[y * w + 4] & 0xFFFFFF;
            for (int x = 4; x < w - 4; x++)
                px[y * w + x] = fill;
        }
    }

    /** The armour figure from the deposit-worn icon, green arrow removed, scaled to fit the button. */
    static BufferedImage figure(SpriteDefinition d) {
        int w = d.getWidth(), h = d.getHeight();
        int[] p = d.getPixels();
        int minX = w, minY = h, maxX = -1, maxY = -1;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int c = p[y * w + x] & 0xFFFFFF;
                if (c == 0) continue;
                int r = c >> 16 & 0xFF, g = c >> 8 & 0xFF, b = c & 0xFF;
                if (g > r + 30 && g > b + 30) continue; // the green arrow
                img.setRGB(x, y, 0xFF000000 | c);
                minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                minY = Math.min(minY, y); maxY = Math.max(maxY, y);
            }
        return img.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    static void drawFigure(int[] px, int w, int h, BufferedImage fig) {
        int box = 15;
        double s = Math.min((double) box / fig.getWidth(), (double) box / fig.getHeight());
        int fw = Math.max(1, (int) Math.round(fig.getWidth() * s)), fh = Math.max(1, (int) Math.round(fig.getHeight() * s));
        BufferedImage sc = new BufferedImage(fw, fh, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = sc.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(fig, 0, 0, fw, fh, null);
        g.dispose();
        int ox = (w - fw) / 2, oy = (h - fh) / 2;
        for (int y = 0; y < fh; y++)
            for (int x = 0; x < fw; x++) {
                int c = sc.getRGB(x, y);
                int a = c >>> 24;
                if (a < 40) continue;
                int i = (oy + y) * w + ox + x;
                int bg = px[i];
                int r = ((c >> 16 & 0xFF) * a + (bg >> 16 & 0xFF) * (255 - a)) / 255;
                int gg = ((c >> 8 & 0xFF) * a + (bg >> 8 & 0xFF) * (255 - a)) / 255;
                int b = ((c & 0xFF) * a + (bg & 0xFF) * (255 - a)) / 255;
                int n = r << 16 | gg << 8 | b;
                px[i] = n == 0 ? 0x010101 : n;
            }
    }

    /** Single-frame sprite archive: [flags=0][indices][palette][maxW][maxH][palLen-1][offX][offY][w][h][count]. */
    static byte[] encode(int[] px, int w, int h, SpriteDefinition base) {
        LinkedHashMap<Integer, Integer> pal = new LinkedHashMap<>();
        byte[] idx = new byte[px.length];
        for (int i = 0; i < px.length; i++) {
            int c = px[i] & 0xFFFFFF;
            if (c == 0) continue;
            Integer k = pal.get(c);
            if (k == null) {
                k = pal.size() + 1;
                if (k > 255) throw new IllegalStateException("more than 255 colours");
                pal.put(c, k);
            }
            idx[i] = (byte) (int) k;
        }
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write(0);
        o.write(idx, 0, idx.length);
        for (int c : pal.keySet()) {
            o.write(c >> 16); o.write(c >> 8); o.write(c);
        }
        u16(o, base.getMaxWidth()); u16(o, base.getMaxHeight());
        o.write(pal.size());
        u16(o, base.getOffsetX()); u16(o, base.getOffsetY());
        u16(o, w); u16(o, h);
        u16(o, 1);
        return o.toByteArray();
    }

    static void u16(ByteArrayOutputStream o, int v) {
        o.write(v >> 8); o.write(v);
    }
}
