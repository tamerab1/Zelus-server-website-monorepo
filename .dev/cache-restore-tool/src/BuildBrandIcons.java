import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.SpriteDefinition;
import net.runelite.cache.definitions.loaders.SpriteLoader;
import net.runelite.cache.fs.*;
import net.runelite.cache.index.FileData;
import net.runelite.cache.index.IndexData;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.*;
import java.util.zip.CRC32;

// Draws the Quick tools brand icons (2026-10-15) as new single-frame 18x16 sprites, from pixel-art masks below:
//   24526  Discord "Clyde": blurple head with white eyes, darker rim
//   24527  Donator badge: gold coin, bronze rim, highlight, green "$"
//   24528 / 24529  Zelus Market pager arrows (left / right), gold; 24530 / 24531 the dim (disabled) pair
// Sprites that already exist with the same pixels are skipped, so the tool can be re-run after new ones are added.
// QuestDashboards shows them on the Discord / Store buttons (IF_SETGRAPHIC). Encoded like BuildPresetButtonSprites and
// decoded back (real SpriteLoader) to the exact pixels before anything is written.
// Usage: verify|apply <cachePath> [previewDir]
public class BuildBrandIcons {
    static final int W = 18, H = 16;

    static final String[] CLYDE = {
            "..................",
            "....XXX....XXX....",
            "...XXXXXXXXXXXX...",
            "..XXXXXXXXXXXXXX..",
            "..XXXXXXXXXXXXXX..",
            ".XXXXXXXXXXXXXXXX.",
            ".XXXWWXXXXXXWWXXX.",
            ".XXWWWWXXXXWWWWXX.",
            ".XXWWWWXXXXWWWWXX.",
            ".XXXWWXXXXXXWWXXX.",
            ".XXXXXXXXXXXXXXXX.",
            ".XXXXXXXXXXXXXXXX.",
            "..XXXXX....XXXXX..",
            "...XXX......XXX...",
            "..................",
            "..................",
    };

    static final String[] DOLLAR = {
            "..................",
            "......XXXXXX......",
            "....XXXXXXXXXX....",
            "...XXXXXGGXXXXX...",
            "..XXXXXGGGGXXXXX..",
            "..XXXXGGXGXXXXXX..",
            ".XXXXXGGXXXXXXXXX.",
            ".XXXXXXGGGXXXXXXX.",
            ".XXXXXXXXGGXXXXXX.",
            ".XXXXXXXXGGXXXXXX.",
            "..XXXXGXXGGXXXXX..",
            "..XXXXXGGGGXXXXX..",
            "...XXXXXGGXXXXX...",
            "....XXXXXXXXXX....",
            "......XXXXXX......",
            "..................",
    };

    static final String[] ARROW_LEFT = {
            "..................",
            "..................",
            "..................",
            "..........X.......",
            ".........XX.......",
            "........XXX.......",
            ".......XXXX.......",
            "......XXXXX.......",
            ".......XXXX.......",
            "........XXX.......",
            ".........XX.......",
            "..........X.......",
            "..................",
            "..................",
            "..................",
            "..................",
    };

    static String[] mirror(String[] mask) {
        String[] out = new String[mask.length];
        for (int i = 0; i < mask.length; i++)
            out[i] = new StringBuilder(mask[i]).reverse().toString();
        return out;
    }

    public static void main(String[] args) throws Exception {
        boolean apply = args[0].equals("apply");
        Map<Integer, int[]> sprites = new LinkedHashMap<>();
        sprites.put(24526, paint(CLYDE, 0x5865f2, 0x3c45a5, 0x7983f5, 0xffffff));
        sprites.put(24527, paint(DOLLAR, 0xd9a521, 0x6b4e0b, 0xf6d76b, 0x1f7a2e));
        sprites.put(24528, paint(ARROW_LEFT, 0xffc35a, 0x8a6a3a, 0xffdf9a, 0));
        sprites.put(24529, paint(mirror(ARROW_LEFT), 0xffc35a, 0x8a6a3a, 0xffdf9a, 0));
        sprites.put(24530, paint(ARROW_LEFT, 0x5a4d3a, 0x3a3128, 0x5a4d3a, 0));
        sprites.put(24531, paint(mirror(ARROW_LEFT), 0x5a4d3a, 0x3a3128, 0x5a4d3a, 0));
        try (Store store = new Store(new File(args[1]))) {
            store.load();
            Storage storage = store.getStorage();
            Index index = store.getIndex(IndexType.SPRITES);
            int compression = index.getArchive(1043).getCompression();
            Map<Integer, byte[]> out = new LinkedHashMap<>();
            for (Map.Entry<Integer, int[]> e : sprites.entrySet()) {
                int id = e.getKey();
                byte[] enc = encode(e.getValue(), W, H);
                if (index.getArchive(id) != null) {
                    Archive existing = index.getArchive(id);
                    if (!Arrays.equals(existing.decompress(storage.loadArchive(existing)), enc))
                        throw new IllegalStateException("sprite " + id + " already exists with other pixels");
                    System.out.println("sprite " + id + " already in the cache (identical), skipped");
                    continue;
                }
                SpriteDefinition back = new SpriteLoader().load(id, enc)[0];
                int[] got = back.getPixels();
                for (int i = 0; i < got.length; i++)
                    if ((got[i] & 0xffffff) != (e.getValue()[i] & 0xffffff))
                        throw new IllegalStateException("sprite " + id + " pixel " + i + " does not round-trip");
                out.put(id, enc);
                System.out.println("sprite " + id + " " + W + "x" + H + " (" + enc.length + " bytes, round-trip OK)");
                if (args.length > 2) {
                    BufferedImage img = new BufferedImage(W * 4, H * 4, BufferedImage.TYPE_INT_RGB);
                    for (int y = 0; y < H * 4; y++)
                        for (int x = 0; x < W * 4; x++) {
                            int c = e.getValue()[(y / 4) * W + x / 4];
                            img.setRGB(x, y, c == 0 ? 0x36302a : c);
                        }
                    ImageIO.write(img, "png", new File(args[2], id + ".png"));
                }
            }
            if (!apply) {
                System.out.println("VERIFY OK -- nothing written.");
                return;
            }
            for (Map.Entry<Integer, byte[]> e : out.entrySet()) {
                int id = e.getKey();
                byte[] raw = e.getValue();
                Archive a = index.addArchive(id);
                a.setNameHash(-1);
                a.setCompression(compression);
                a.setRevision(1);
                Container c = new Container(compression, -1);
                c.compress(raw, null);
                storage.store(index.getId(), id, c.data);
                CRC32 crc = new CRC32();
                crc.update(c.data);
                a.setCrc((int) crc.getValue());
                a.setCompressedSize(c.data.length);
                a.setDecompressedSize(raw.length);
                FileData fd = new FileData();
                fd.setId(0);
                a.setFileData(new FileData[]{fd});
            }
            IndexData indexData = index.toIndexData();
            Container c = new Container(index.getCompression(), -1);
            c.compress(indexData.writeIndexData(), null);
            storage.store(255, index.getId(), c.data);
            index.setCrc(c.crc);
            System.out.println("APPLY complete -- " + out.size() + " sprites written.");
        }
    }

    /**
     * X = body, its edge pixels drawn in rim; the upper-left body pixels get the highlight; W / G = the glyph colour.
     * '.' is transparent (0).
     */
    static int[] paint(String[] mask, int body, int rim, int highlight, int glyph) {
        int[] px = new int[W * H];
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) {
                char ch = mask[y].charAt(x);
                if (ch == '.')
                    continue;
                if (ch != 'X') {
                    px[y * W + x] = glyph;
                    continue;
                }
                boolean edge = solid(mask, x - 1, y) ^ true || solid(mask, x + 1, y) ^ true
                        || solid(mask, x, y - 1) ^ true || solid(mask, x, y + 1) ^ true;
                boolean upperLeft = !edge && (!solid(mask, x - 2, y) || !solid(mask, x, y - 2)) && x < W / 2 && y < H / 2;
                px[y * W + x] = edge ? rim : upperLeft ? highlight : body;
            }
        return px;
    }

    static boolean solid(String[] mask, int x, int y) {
        return x >= 0 && y >= 0 && x < W && y < H && mask[y].charAt(x) != '.';
    }

    /** Single-frame sprite archive: [flags=0][indices][palette][maxW][maxH][palLen-1][offX][offY][w][h][count]. */
    static byte[] encode(int[] px, int w, int h) {
        LinkedHashMap<Integer, Integer> pal = new LinkedHashMap<>();
        byte[] idx = new byte[px.length];
        for (int i = 0; i < px.length; i++) {
            int c = px[i] & 0xFFFFFF;
            if (c == 0)
                continue;
            Integer k = pal.get(c);
            if (k == null) {
                k = pal.size() + 1;
                pal.put(c, k);
            }
            idx[i] = (byte) (int) k;
        }
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write(0);
        o.write(idx, 0, idx.length);
        for (int c : pal.keySet()) {
            o.write(c >> 16);
            o.write(c >> 8);
            o.write(c);
        }
        u16(o, w);
        u16(o, h);
        o.write(pal.size());
        u16(o, 0);
        u16(o, 0);
        u16(o, w);
        u16(o, h);
        u16(o, 1);
        return o.toByteArray();
    }

    static void u16(ByteArrayOutputStream o, int v) {
        o.write(v >> 8);
        o.write(v);
    }
}
