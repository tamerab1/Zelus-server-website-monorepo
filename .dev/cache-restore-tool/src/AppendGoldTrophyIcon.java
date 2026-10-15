import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.SpriteDefinition;
import net.runelite.cache.definitions.loaders.SpriteLoader;
import net.runelite.cache.fs.*;
import net.runelite.cache.index.IndexData;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

// Appends a gold trophy to the chat mod-icon sheet (sprite 423, the <img=N> icons): a copy of frame 29 (the silver
// trophy with handles) with its shades mapped onto a gold ramp by luminance. A raw splice: every existing frame block
// and palette entry stays byte-identical, the new colours are appended to the shared palette, and the trailer tables
// (offsets / sizes / count) get one more entry. The result must decode with every old frame unchanged.
// The new frame's index is printed (Icon.PIT_TROPHY). Usage: verify|apply <cachePath>
public class AppendGoldTrophyIcon {
    static final int SPRITE = 423, SOURCE_FRAME = 29;
    /** Gold ramp, dark to light. */
    static final int[] GOLD = {0x4a3200, 0x7a5200, 0xa87410, 0xd49a1c, 0xf2c23a, 0xffe27a};

    public static void main(String[] args) throws Exception {
        boolean apply = args[0].equals("apply");
        try (Store store = new Store(new File(args[1]))) {
            store.load();
            Storage storage = store.getStorage();
            Index index = store.getIndex(IndexType.SPRITES);
            Archive archive = index.getArchive(SPRITE);
            byte[] data = archive.decompress(storage.loadArchive(archive));
            SpriteDefinition[] before = new SpriteLoader().load(SPRITE, data);

            // ---- parse the trailer ----
            int count = u16(data, data.length - 2);
            int t = data.length - 7 - count * 8;
            int maxW = u16(data, t), maxH = u16(data, t + 2), palLen = (data[t + 4] & 0xff) + 1;
            int[] offX = new int[count], offY = new int[count], ws = new int[count], hs = new int[count];
            int p = t + 5;
            for (int i = 0; i < count; i++) { offX[i] = u16(data, p); p += 2; }
            for (int i = 0; i < count; i++) { offY[i] = u16(data, p); p += 2; }
            for (int i = 0; i < count; i++) { ws[i] = u16(data, p); p += 2; }
            for (int i = 0; i < count; i++) { hs[i] = u16(data, p); p += 2; }
            int palStart = t - (palLen - 1) * 3;
            int[] palette = new int[palLen];
            for (int i = 1; i < palLen; i++) {
                int o = palStart + (i - 1) * 3;
                palette[i] = (data[o] & 0xff) << 16 | (data[o + 1] & 0xff) << 8 | data[o + 2] & 0xff;
                if (palette[i] == 0) palette[i] = 1;
            }
            // ---- walk the frame blocks to find the source frame's ----
            int pos = 0, srcStart = -1, srcFlags = 0;
            for (int f = 0; f < count; f++) {
                int flags = data[pos] & 0xff;
                int size = ws[f] * hs[f];
                if (f == SOURCE_FRAME) {
                    srcStart = pos;
                    srcFlags = flags;
                }
                pos += 1 + size + ((flags & 2) != 0 ? size : 0);
            }
            if (pos != palStart)
                throw new IllegalStateException("frame blocks end at " + pos + ", palette starts at " + palStart);
            int w = ws[SOURCE_FRAME], h = hs[SOURCE_FRAME], size = w * h;
            System.out.println("sprite " + SPRITE + ": " + count + " frames, palette " + palLen + ", source frame " + SOURCE_FRAME
                + " " + w + "x" + h + " flags=" + srcFlags);

            // ---- recolour: each used palette index -> a gold shade by luminance (black outline stays) ----
            byte[] srcIdx = new byte[size];
            if ((srcFlags & 1) == 0) {
                System.arraycopy(data, srcStart + 1, srcIdx, 0, size);
            } else { // stored column-major
                for (int x = 0; x < w; x++)
                    for (int y = 0; y < h; y++)
                        srcIdx[x + y * w] = data[srcStart + 1 + y + x * h];
            }
            List<Integer> newColours = new ArrayList<>();
            int[] map = new int[256];
            byte[] outIdx = new byte[size];
            for (int i = 0; i < size; i++) {
                int idx = srcIdx[i] & 0xff;
                if (idx == 0)
                    continue;
                if (map[idx] == 0) {
                    int c = palette[idx];
                    int lum = ((c >> 16 & 0xff) * 299 + (c >> 8 & 0xff) * 587 + (c & 0xff) * 114) / 1000;
                    int gold = lum < 24 ? c : GOLD[Math.min(GOLD.length - 1, lum * GOLD.length / 256)];
                    int found = -1;
                    for (int k = 1; k < palLen; k++)
                        if (palette[k] == gold) found = k;
                    if (found == -1) {
                        int existing = newColours.indexOf(gold);
                        if (existing == -1) {
                            newColours.add(gold);
                            existing = newColours.size() - 1;
                        }
                        found = palLen + existing;
                    }
                    map[idx] = found;
                }
                outIdx[i] = (byte) map[idx];
            }
            int newPalLen = palLen + newColours.size();
            if (newPalLen > 256)
                throw new IllegalStateException("palette would exceed 256 entries");
            byte[] alpha = null;
            if ((srcFlags & 2) != 0) {
                alpha = new byte[size];
                if ((srcFlags & 1) == 0)
                    System.arraycopy(data, srcStart + 1 + size, alpha, 0, size);
                else
                    for (int x = 0; x < w; x++)
                        for (int y = 0; y < h; y++)
                            alpha[x + y * w] = data[srcStart + 1 + size + y + x * h];
            }

            // ---- write: old frame blocks, new block, palette (+ new colours), trailer (+ one entry) ----
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(data, 0, palStart);
            out.write(alpha == null ? 0 : 2); // row-major
            out.write(outIdx, 0, size);
            if (alpha != null)
                out.write(alpha, 0, size);
            out.write(data, palStart, t - palStart);
            for (int c : newColours) {
                out.write(c >> 16); out.write(c >> 8); out.write(c);
            }
            u16(out, maxW); u16(out, maxH);
            out.write(newPalLen - 1);
            for (int i = 0; i <= count; i++) u16(out, i < count ? offX[i] : offX[SOURCE_FRAME]);
            for (int i = 0; i <= count; i++) u16(out, i < count ? offY[i] : offY[SOURCE_FRAME]);
            for (int i = 0; i <= count; i++) u16(out, i < count ? ws[i] : w);
            for (int i = 0; i <= count; i++) u16(out, i < count ? hs[i] : h);
            u16(out, count + 1);
            byte[] result = out.toByteArray();

            // ---- verify: every old frame decodes identically, the new one is the recoloured source ----
            SpriteDefinition[] after = new SpriteLoader().load(SPRITE, result);
            if (after.length != count + 1)
                throw new IllegalStateException("frame count " + after.length);
            for (int f = 0; f < count; f++)
                if (!java.util.Arrays.equals(before[f].getPixels(), after[f].getPixels()) || before[f].getWidth() != after[f].getWidth())
                    throw new IllegalStateException("frame " + f + " changed -- ABORTING");
            SpriteDefinition gold = after[count];
            int opaque = 0;
            for (int px : gold.getPixels()) if (px != 0) opaque++;
            System.out.println("new frame " + count + ": " + gold.getWidth() + "x" + gold.getHeight() + ", " + opaque
                + " opaque px, +" + newColours.size() + " palette colours");
            System.out.println("CONST PIT_TROPHY = " + count);
            if (!apply) {
                System.out.println("VERIFY OK -- nothing written.");
                return;
            }
            Container container = new Container(archive.getCompression(), -1);
            container.compress(result, null);
            storage.store(index.getId(), SPRITE, container.data);
            archive.setCrc(container.crc);
            archive.setRevision(archive.getRevision() + 1);
            archive.setCompressedSize(container.data.length);
            archive.setDecompressedSize(result.length);
            IndexData indexData = index.toIndexData();
            Container ic = new Container(index.getCompression(), -1);
            ic.compress(indexData.writeIndexData(), null);
            storage.store(255, index.getId(), ic.data);
            index.setCrc(ic.crc);
            System.out.println("APPLY complete -- sprite " + SPRITE + " now has " + (count + 1) + " frames.");
        }
    }

    static int u16(byte[] b, int o) {
        return (b[o] & 0xff) << 8 | b[o + 1] & 0xff;
    }

    static void u16(ByteArrayOutputStream o, int v) {
        o.write(v >> 8);
        o.write(v);
    }
}
