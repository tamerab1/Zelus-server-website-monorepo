import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.SpriteDefinition;
import net.runelite.cache.definitions.TextureDefinition;
import net.runelite.cache.definitions.loaders.SpriteLoader;
import net.runelite.cache.definitions.loaders.TextureLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Container;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;
import net.runelite.cache.index.IndexData;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.CRC32;

// Creates recoloured copies of an existing (single-sprite) texture: a new palette-remapped sprite
// archive plus a new texture entry pointing at it. Spec lines:
//   texture <srcTexId> <newTexId> <newSpriteId> <dir|-> <speed|-> <stops>
// <stops> = gradient "pos:rrggbb,pos:rrggbb,..." (pos 0..1). Every palette colour of the source
// sprite is ranked by "heat" (0.45R+0.55G, normalised over the palette -- dark red -> 0, yellow
// -> 1, which is exactly how the lava palettes are built) and replaced by the gradient colour at
// that heat, so the pixel layout / flow pattern is untouched and only the colours change.
// Texture bytes = source bytes with avg-colour (u16 HSL), sprite id, and optionally the trailing
// animation direction/speed replaced. Everything is re-decoded and checked before writing.
// Usage: verify|apply <cachePath> <specFile>
public class CreateRecoloredTextures {
    public static void main(String[] args) throws Exception {
        String mode = args[0];
        List<String[]> specs = new ArrayList<>();
        for (String raw : Files.readAllLines(new File(args[2]).toPath())) {
            String l = raw.trim();
            if (l.isEmpty() || l.startsWith("#")) continue;
            String[] p = l.split("\\s+");
            if (!p[0].equals("texture") || p.length != 7) throw new IllegalArgumentException("bad line: " + l);
            specs.add(p);
        }

        try (Store store = new Store(new File(args[1]))) {
            store.load();
            Storage storage = store.getStorage();
            Index textures = store.getIndex(IndexType.TEXTURES);
            Index sprites = store.getIndex(IndexType.SPRITES);
            Archive texArchive = textures.getArchive(0);
            byte[] texDecompressed = texArchive.decompress(storage.loadArchive(texArchive));
            FileData[] texFd = texArchive.getFileData();
            List<byte[]> texContents = SpliceItemOption.splitChunks(texDecompressed, texFd.length);

            List<int[]> newSprites = new ArrayList<>();
            List<byte[]> newSpriteBytes = new ArrayList<>();
            List<int[]> newTextures = new ArrayList<>();
            List<byte[]> newTextureBytes = new ArrayList<>();

            for (String[] p : specs) {
                int srcTex = Integer.parseInt(p[1]), newTex = Integer.parseInt(p[2]), newSprite = Integer.parseInt(p[3]);
                check(newSprite > 0 && newSprite < 65536, "sprite id must fit u16: " + newSprite);
                check(sprites.getArchive(newSprite) == null, "sprite " + newSprite + " already exists");
                check(slotOf(texFd, newTex) == -1, "texture " + newTex + " already exists");
                int maxTex = texFd[texFd.length - 1].getId();
                for (int[] t : newTextures) maxTex = Math.max(maxTex, t[0]);
                check(newTex > maxTex, "new texture ids must be appended above " + maxTex);

                byte[] srcTexBytes = texContents.get(requireSlot(texFd, srcTex));
                TextureDefinition st = new TextureLoader().load(srcTex, srcTexBytes);
                check(st.getFileIds().length == 1, "source texture must use exactly one sprite");
                check(srcTexBytes.length == 12 && (srcTexBytes[3] & 0xFF) == 1, "unexpected texture layout");
                int srcSprite = st.getFileIds()[0];

                // --- sprite: remap palette bytes in place ---
                Archive sa = sprites.getArchive(srcSprite);
                byte[] spr = sa.decompress(storage.loadArchive(sa));
                int frames = u16(spr, spr.length - 2);
                int hdr = spr.length - 7 - frames * 8;
                int palLen = (spr[hdr + 4] & 0xFF) + 1;
                int pal = hdr - (palLen - 1) * 3;
                double[][] stops = parseStops(p[6]);
                double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
                for (int i = 0; i < palLen - 1; i++) {
                    double h = heat(rgbAt(spr, pal + i * 3));
                    lo = Math.min(lo, h);
                    hi = Math.max(hi, h);
                }
                byte[] out = spr.clone();
                int[] oldPal = new int[palLen], newPal = new int[palLen];
                long sr = 0, sg = 0, sb = 0;
                for (int i = 0; i < palLen - 1; i++) {
                    int c = rgbAt(spr, pal + i * 3);
                    int n = gradient(stops, (heat(c) - lo) / (hi - lo));
                    if (n == 0) n = 0x010101; // 0 is the transparent colour in sprites/textures
                    oldPal[i + 1] = c;
                    newPal[i + 1] = n;
                    out[pal + i * 3] = (byte) (n >> 16);
                    out[pal + i * 3 + 1] = (byte) (n >> 8);
                    out[pal + i * 3 + 2] = (byte) n;
                }

                SpriteDefinition before = new SpriteLoader().load(srcSprite, spr)[0];
                SpriteDefinition after = new SpriteLoader().load(newSprite, out)[0];
                check(before.getWidth() == after.getWidth() && before.getHeight() == after.getHeight(), "sprite dims changed");
                int[] bp = before.getPixels(), ap = after.getPixels();
                for (int i = 0; i < bp.length; i++) {
                    int o = bp[i] & 0xFFFFFF, n = ap[i] & 0xFFFFFF;
                    int expect = -1;
                    for (int k = 1; k < palLen; k++) if (oldPal[k] == o) { expect = newPal[k]; break; }
                    check(expect == n, "pixel " + i + " not remapped as expected");
                    sr += (n >> 16) & 0xFF;
                    sg += (n >> 8) & 0xFF;
                    sb += n & 0xFF;
                }
                String preview = System.getProperty("preview");
                if (preview != null) {
                    java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(after.getWidth() * 2, after.getHeight(),
                            java.awt.image.BufferedImage.TYPE_INT_RGB);
                    img.setRGB(0, 0, before.getWidth(), before.getHeight(), bp, 0, before.getWidth());
                    img.setRGB(after.getWidth(), 0, after.getWidth(), after.getHeight(), ap, 0, after.getWidth());
                    javax.imageio.ImageIO.write(img, "png", new File(preview, "texture_" + newTex + ".png"));
                }
                int avg = (int) (sr / bp.length) << 16 | (int) (sg / bp.length) << 8 | (int) (sb / bp.length);
                int avgHsl = rgbToHsl16(avg);

                // --- texture: copy with avg colour / sprite id / animation replaced ---
                byte[] nt = srcTexBytes.clone();
                nt[0] = (byte) (avgHsl >> 8);
                nt[1] = (byte) avgHsl;
                nt[4] = (byte) (newSprite >> 8);
                nt[5] = (byte) newSprite;
                int dir = p[4].equals("-") ? st.animationDirection : Integer.parseInt(p[4]);
                int speed = p[5].equals("-") ? st.animationSpeed : Integer.parseInt(p[5]);
                nt[10] = (byte) dir;
                nt[11] = (byte) speed;
                TextureDefinition t = new TextureLoader().load(newTex, nt);
                check(Arrays.equals(t.getFileIds(), new int[]{newSprite}) && t.animationDirection == dir && t.animationSpeed == speed
                        && t.field1777 == avgHsl && t.field1778 == st.field1778
                        && Arrays.equals(t.field1780, st.field1780) && Arrays.equals(t.field1781, st.field1781)
                        && Arrays.equals(t.field1786, st.field1786), "texture " + newTex + " re-decode mismatch");

                newSprites.add(new int[]{newSprite, sa.getCompression()});
                newSpriteBytes.add(out);
                newTextures.add(new int[]{newTex});
                newTextureBytes.add(nt);
                System.out.printf("texture %d -> %d  sprite %d -> %d  avg=#%06x hsl=%d dir=%d speed=%d palette=%d colours%n",
                        srcTex, newTex, srcSprite, newSprite, avg, avgHsl, dir, speed, palLen - 1);
            }

            if (mode.equals("verify")) {
                System.out.println("VERIFY OK -- " + newSprites.size() + " sprites + " + newTextures.size() + " textures, nothing written.");
                return;
            }
            if (!mode.equals("apply")) throw new IllegalArgumentException("mode must be verify or apply");

            for (int i = 0; i < newSprites.size(); i++) {
                int id = newSprites.get(i)[0];
                byte[] raw = newSpriteBytes.get(i);
                Archive a = sprites.addArchive(id);
                a.setNameHash(-1);
                a.setCompression(newSprites.get(i)[1]);
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
            storeIndex(storage, sprites);

            List<FileData> fdList = new ArrayList<>(Arrays.asList(texFd));
            for (int i = 0; i < newTextures.size(); i++) {
                FileData fd = new FileData();
                fd.setId(newTextures.get(i)[0]);
                fd.setNameHash(-1);
                fdList.add(fd);
                texContents.add(newTextureBytes.get(i));
            }
            texArchive.setFileData(fdList.toArray(new FileData[0]));
            byte[] joined = SpliceItemOption.joinChunks(texContents);
            Container tc = new Container(texArchive.getCompression(), -1);
            tc.compress(joined, null);
            storage.store(textures.getId(), texArchive.getArchiveId(), tc.data);
            texArchive.setCrc(tc.crc);
            texArchive.setRevision(texArchive.getRevision() + 1);
            texArchive.setCompressedSize(tc.data.length);
            texArchive.setDecompressedSize(joined.length);
            storeIndex(storage, textures);
            System.out.println("APPLY complete -- " + newSprites.size() + " sprites + " + newTextures.size() + " textures written.");
        }
    }

    static void storeIndex(Storage storage, Index index) throws Exception {
        IndexData indexData = index.toIndexData();
        Container c = new Container(index.getCompression(), -1);
        c.compress(indexData.writeIndexData(), null);
        storage.store(255, index.getId(), c.data);
        index.setCrc(c.crc);
    }

    static double heat(int rgb) {
        return 0.45 * ((rgb >> 16) & 0xFF) + 0.55 * ((rgb >> 8) & 0xFF);
    }

    static double[][] parseStops(String s) {
        String[] parts = s.split(",");
        double[][] r = new double[parts.length][];
        for (int i = 0; i < parts.length; i++) {
            String[] kv = parts[i].split(":");
            r[i] = new double[]{Double.parseDouble(kv[0]), Integer.parseInt(kv[1], 16)};
        }
        return r;
    }

    static int gradient(double[][] stops, double t) {
        t = Math.max(0, Math.min(1, t));
        for (int i = 1; i < stops.length; i++) {
            if (t <= stops[i][0]) {
                double f = (t - stops[i - 1][0]) / (stops[i][0] - stops[i - 1][0]);
                return lerp((int) stops[i - 1][1], (int) stops[i][1], f);
            }
        }
        return (int) stops[stops.length - 1][1];
    }

    static int lerp(int a, int b, double f) {
        int r = (int) Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * f);
        int g = (int) Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * f);
        int bl = (int) Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * f);
        return r << 16 | g << 8 | bl;
    }

    // Jagex 16-bit HSL: hue 6 bits, saturation 3 bits, lightness 7 bits.
    static int rgbToHsl16(int rgb) {
        double r = ((rgb >> 16) & 0xFF) / 255.0, g = ((rgb >> 8) & 0xFF) / 255.0, b = (rgb & 0xFF) / 255.0;
        double max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        double l = (max + min) / 2, h = 0, s = 0;
        if (max != min) {
            double d = max - min;
            s = l > 0.5 ? d / (2 - max - min) : d / (max + min);
            if (max == r) h = ((g - b) / d + (g < b ? 6 : 0)) / 6;
            else if (max == g) h = ((b - r) / d + 2) / 6;
            else h = ((r - g) / d + 4) / 6;
        }
        int hi = Math.min(63, (int) (h * 64)), si = Math.min(7, (int) (s * 8)), li = Math.min(127, (int) (l * 128));
        return hi << 10 | si << 7 | li;
    }

    static int rgbAt(byte[] b, int off) {
        return (b[off] & 0xFF) << 16 | (b[off + 1] & 0xFF) << 8 | (b[off + 2] & 0xFF);
    }

    static int u16(byte[] b, int off) {
        return (b[off] & 0xFF) << 8 | (b[off + 1] & 0xFF);
    }

    static int slotOf(FileData[] fd, int id) {
        for (int i = 0; i < fd.length; i++) if (fd[i].getId() == id) return i;
        return -1;
    }

    static int requireSlot(FileData[] fd, int id) {
        int s = slotOf(fd, id);
        check(s != -1, "id " + id + " not found");
        return s;
    }

    static void check(boolean ok, String msg) {
        if (!ok) throw new IllegalStateException("CHECK FAILED: " + msg);
    }
}
