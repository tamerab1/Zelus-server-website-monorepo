import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.SpriteDefinition;
import net.runelite.cache.definitions.loaders.SpriteLoader;
import net.runelite.cache.fs.*;
import net.runelite.cache.index.FileData;
import net.runelite.cache.index.IndexData;

import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.CRC32;

// Creates hover/highlight copies of existing single-frame sprites: a new sprite archive whose
// palette is blended toward a tint colour (pixel layout untouched). Used for interface hover
// effects via script 44 (if_setgraphic). Spec lines:
//   sprite <srcSpriteId> <newSpriteId> <tint rrggbb> <amount 0..1>
// Sprite archive palette layout and the store/index pipeline are the same as CreateRecoloredTextures.
// Every pixel of the new sprite is checked against the expected remap before anything is written.
// Usage: verify|apply <cachePath> <specFile> [previewDir]
public class CreateBrightenedSprites {
    public static void main(String[] args) throws Exception {
        String mode = args[0];
        List<String[]> specs = new ArrayList<>();
        for (String l : Files.readAllLines(new File(args[2]).toPath())) {
            l = l.trim();
            if (!l.isEmpty() && !l.startsWith("#")) specs.add(l.split("\\s+"));
        }
        String previewDir = args.length > 3 ? args[3] : null;

        try (Store store = new Store(new File(args[1]))) {
            store.load();
            Storage storage = store.getStorage();
            Index sprites = store.getIndex(IndexType.SPRITES);
            List<Object[]> out = new ArrayList<>();

            for (String[] p : specs) {
                int src = Integer.parseInt(p[1]), dst = Integer.parseInt(p[2]);
                int tint = Integer.parseInt(p[3], 16);
                double amount = Double.parseDouble(p[4]);
                if (dst <= 0 || dst >= 65536) throw new IllegalStateException("sprite id must fit u16: " + dst);
                if (sprites.getArchive(dst) != null) throw new IllegalStateException("sprite " + dst + " already exists");

                Archive sa = sprites.getArchive(src);
                byte[] spr = sa.decompress(storage.loadArchive(sa));
                int frames = u16(spr, spr.length - 2);
                if (frames != 1) throw new IllegalStateException("sprite " + src + " has " + frames + " frames");
                int hdr = spr.length - 7 - frames * 8;
                int palLen = (spr[hdr + 4] & 0xFF) + 1;
                int pal = hdr - (palLen - 1) * 3;

                byte[] nb = spr.clone();
                Map<Integer, Integer> remap = new HashMap<>();
                for (int i = 0; i < palLen - 1; i++) {
                    int o = pal + i * 3;
                    int c = (spr[o] & 0xFF) << 16 | (spr[o + 1] & 0xFF) << 8 | (spr[o + 2] & 0xFF);
                    int n = blend(c, tint, amount);
                    if (n == 0) n = 0x010101; // 0 is the transparent colour
                    remap.put(c, n);
                    nb[o] = (byte) (n >> 16);
                    nb[o + 1] = (byte) (n >> 8);
                    nb[o + 2] = (byte) n;
                }

                SpriteDefinition before = new SpriteLoader().load(src, spr)[0];
                SpriteDefinition after = new SpriteLoader().load(dst, nb)[0];
                if (before.getWidth() != after.getWidth() || before.getHeight() != after.getHeight()
                        || before.getOffsetX() != after.getOffsetX() || before.getOffsetY() != after.getOffsetY())
                    throw new IllegalStateException("sprite " + src + ": geometry changed");
                int[] bp = before.getPixels(), ap = after.getPixels();
                for (int i = 0; i < bp.length; i++) {
                    int o = bp[i] & 0xFFFFFF, n = ap[i] & 0xFFFFFF;
                    int expect = o == 0 ? 0 : remap.getOrDefault(o, -1);
                    if (expect != n) throw new IllegalStateException("sprite " + src + ": pixel " + i + " not remapped as expected");
                }
                if (previewDir != null) {
                    java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(before.getWidth() * 2 + 4, before.getHeight(),
                            java.awt.image.BufferedImage.TYPE_INT_RGB);
                    img.setRGB(0, 0, before.getWidth(), before.getHeight(), bp, 0, before.getWidth());
                    img.setRGB(before.getWidth() + 4, 0, after.getWidth(), after.getHeight(), ap, 0, after.getWidth());
                    java.awt.image.BufferedImage big = new java.awt.image.BufferedImage(img.getWidth() * 3, img.getHeight() * 3,
                            java.awt.image.BufferedImage.TYPE_INT_RGB);
                    big.getGraphics().drawImage(img, 0, 0, big.getWidth(), big.getHeight(), null);
                    javax.imageio.ImageIO.write(big, "png", new File(previewDir, "sprite_" + src + "_to_" + dst + ".png"));
                }
                System.out.printf("sprite %d -> %d  %dx%d  palette=%d  tint=#%06x x%.2f%n", src, dst,
                        before.getWidth(), before.getHeight(), palLen - 1, tint, amount);
                out.add(new Object[]{dst, sa.getCompression(), nb});
            }

            if (mode.equals("verify")) {
                System.out.println("VERIFY OK -- " + out.size() + " sprites, nothing written.");
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

    static int blend(int c, int tint, double t) {
        int r = (int) Math.round(((c >> 16) & 0xFF) * (1 - t) + ((tint >> 16) & 0xFF) * t);
        int g = (int) Math.round(((c >> 8) & 0xFF) * (1 - t) + ((tint >> 8) & 0xFF) * t);
        int b = (int) Math.round((c & 0xFF) * (1 - t) + (tint & 0xFF) * t);
        return r << 16 | g << 8 | b;
    }

    static int u16(byte[] b, int o) {
        return (b[o] & 0xFF) << 8 | (b[o + 1] & 0xFF);
    }
}
