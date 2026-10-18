import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.ObjectDefinition;
import net.runelite.cache.definitions.OverlayDefinition;
import net.runelite.cache.definitions.loaders.ObjectLoader;
import net.runelite.cache.definitions.loaders.OverlayLoader;
import net.runelite.cache.fs.*;
import net.runelite.cache.index.FileData;
import net.runelite.cache.io.OutputStream;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.*;

// The Soul Sanctuary floor, done in the map itself (2026-10-18 round 10). The 5x5 dais model (62454) was a ground
// decoration bigger than its tile: the client paints each later tile's floor over it, so it came out cut into steps,
// and the old yard's cobblestone overlay (84) stuck out around it off-centre (it covered 3122-3127 x 3488-3494).
//   - a new floor overlay: dark obsidian flagstones, texture 54 (texture 43's square flagstones recoloured, made by
//     CreateRecoloredTextures into the one free texture id under 256 -- an overlay stores its texture in a byte)
//   - terrain m48_54: the 5x5 around the altar (3122-3126 x 3489-3493) gets that overlay, full tile; the cobblestone
//     left outside it is removed, so the grass underlay shows and the floor ends in a clean square on the grass
//   - loc 62455 "Soul brazier": a copy of the wrought-iron Brazier 33454 (its own flame animation 8170) with the flame
//     recoloured red/orange/yellow -> deep cyan / cyan / ice white; one on each corner of the floor
// Usage: verify|apply <cachePath>
public class BuildSanctuaryFloor {
    static final int X0 = 3122, Y0 = 3489, X1 = 3126, Y1 = 3493;       // the floor
    static final int CX0 = 3122, CY0 = 3488, CX1 = 3127, CY1 = 3494;   // the old cobblestone patch
    static final int COBBLE_STORED = 84, FLOOR_TEXTURE = 54, FLOOR_RGB = 0x1c2230;
    static final int BRAZIER_SRC = 33454, BRAZIER_LOC = 62455;
    static final int[][] FLAME = {{960, BuildSoulAltar.hsl(31, 7, 50)}, {6080, BuildSoulAltar.hsl(32, 7, 64)}, {11212, BuildSoulAltar.hsl(33, 6, 90)}};

    public static void main(String[] args) throws Exception {
        boolean apply = args[0].equals("apply");
        try (Store store = new Store(new File(args[1]))) {
            store.load();
            Storage storage = store.getStorage();
            Index cfg = store.getIndex(IndexType.CONFIGS), maps = store.getIndex(IndexType.MAPS);
            BuildSanctumMap.check(store.getIndex(IndexType.TEXTURES).getArchive(0).getFileData().length > 0, "textures");
            BuildSanctumMap.check(hasTexture(store, FLOOR_TEXTURE), "texture " + FLOOR_TEXTURE + " missing: run CreateRecoloredTextures first");

            // ---- the overlay ----
            Archive ovArc = cfg.getArchive(ConfigType.OVERLAY.getId());
            FileData[] ovFd = ovArc.getFileData();
            int overlayId = ovFd[ovFd.length - 1].getId() + 1;
            OutputStream o = new OutputStream();
            o.writeByte(1); o.write24BitInt(FLOOR_RGB);
            o.writeByte(2); o.writeByte(FLOOR_TEXTURE);
            o.writeByte(0);
            byte[] ovBytes = o.flip();
            OverlayDefinition od = new OverlayLoader().load(overlayId, ovBytes);
            BuildSanctumMap.check(od.getTexture() == FLOOR_TEXTURE && od.getRgbColor() == FLOOR_RGB, "overlay round trip");
            System.out.printf("overlay %d: texture %d, minimap #%06x%n", overlayId, FLOOR_TEXTURE, FLOOR_RGB);

            // ---- the terrain ----
            int rx = X0 >> 6, ry = Y0 >> 6;
            Archive m = maps.findArchiveByName("m" + rx + "_" + ry);
            BuildSanctumMap.check(m != null, "no terrain m" + rx + "_" + ry);
            byte[] in = m.decompress(storage.loadArchive(m));
            int[] counts = new int[2];
            byte[] out = rewrite(in, rx << 6, ry << 6, overlayId + 1, counts);
            check(in, out, rx << 6, ry << 6, overlayId + 1);
            System.out.printf("m%d_%d: %d tiles floored, %d cobblestone tiles returned to grass, %d -> %d bytes%n", rx, ry,
                    counts[0], counts[1], in.length, out.length);
            BuildSanctumMap.check(counts[0] == 25, "floor tiles " + counts[0]);

            // ---- the brazier ----
            Archive locArc = cfg.getArchive(ConfigType.OBJECT.getId());
            byte[] src = rawLoc(storage, locArc, BRAZIER_SRC);
            BuildSanctumMap.check(src[src.length - 1] == 0, "brazier def terminator");
            OutputStream b = new OutputStream();
            b.writeBytes(Arrays.copyOf(src, src.length - 1));
            b.writeByte(2); b.writeString("Soul brazier");
            b.writeByte(40); b.writeByte(FLAME.length);
            for (int[] f : FLAME) { b.writeShort(f[0]); b.writeShort(f[1]); }
            b.writeByte(0);
            byte[] brazier = b.flip();
            ObjectDefinition bd = new ObjectLoader().load(BRAZIER_LOC, brazier);
            ObjectDefinition sd = new ObjectLoader().load(BRAZIER_SRC, src);
            BuildSanctumMap.check(Arrays.equals(bd.getObjectModels(), sd.getObjectModels()) && bd.getAnimationID() == sd.getAnimationID()
                    && "Soul brazier".equals(bd.getName()) && bd.getRecolorToFind().length == FLAME.length, "brazier copy");
            BuildSanctumMap.check(!BuildLocSpinAnims.ids(locArc).contains(BRAZIER_LOC), "loc " + BRAZIER_LOC + " exists");
            System.out.printf("loc %d \"Soul brazier\": models %s, anim %d, flame recoloured%n", BRAZIER_LOC,
                    Arrays.toString(bd.getObjectModels()), bd.getAnimationID());

            if (!apply) { System.out.println("VERIFY OK -- nothing written."); return; }
            BuildFortuneStatue.insertSorted(storage, cfg, ovArc, new TreeMap<>(Map.of(overlayId, ovBytes)));
            BuildFortuneStatue.insertSorted(storage, cfg, locArc, new TreeMap<>(Map.of(BRAZIER_LOC, brazier)));
            BuildLocSpinAnims.writeRefTable(storage, cfg);
            Container c = new Container(m.getCompression(), -1);
            c.compress(out, null);
            storage.store(maps.getId(), m.getArchiveId(), c.data);
            m.setCrc(c.crc);
            m.setRevision(m.getRevision() + 1);
            m.setCompressedSize(c.data.length);
            m.setDecompressedSize(out.length);
            ImportCathedralMap.writeIndexReferenceTable(storage, maps);
            Archive m2 = maps.findArchiveByName("m" + rx + "_" + ry);
            BuildSanctumMap.check(Arrays.equals(m2.decompress(storage.loadArchive(m2)), out), "stored terrain");
            System.out.println("APPLY complete -- overlay " + overlayId + ", loc " + BRAZIER_LOC + ", m" + rx + "_" + ry + " revision " + m.getRevision());
        }
    }

    static boolean hasTexture(Store store, int id) {
        for (FileData f : store.getIndex(IndexType.TEXTURES).getArchive(0).getFileData()) if (f.getId() == id) return true;
        return false;
    }

    static byte[] rawLoc(Storage storage, Archive arc, int id) throws Exception {
        byte[] dec = arc.decompress(storage.loadArchive(arc));
        FileData[] fd = arc.getFileData();
        List<byte[]> chunks = SpliceItemOption.splitChunks(dec, fd.length);
        for (int i = 0; i < fd.length; i++) if (fd[i].getId() == id) return chunks.get(i);
        throw new IllegalStateException("no loc " + id);
    }

    static boolean inFloor(int x, int y) { return x >= X0 && x <= X1 && y >= Y0 && y <= Y1; }
    static boolean inCobble(int x, int y) { return x >= CX0 && x <= CX1 && y >= CY0 && y <= CY1; }

    /** Plane-0 floor tiles: any overlay op dropped, one full-tile overlay op written; cobblestone around: dropped. */
    static byte[] rewrite(byte[] in, int baseX, int baseY, int stored, int[] counts) {
        int p = 0;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int z = 0; z < 4; z++) for (int x = 0; x < 64; x++) for (int y = 0; y < 64; y++) {
            int gx = baseX + x, gy = baseY + y;
            boolean floor = z == 0 && inFloor(gx, gy);
            if (floor) { out.write(0); out.write(2); out.write(stored >> 8); out.write(stored); counts[0]++; }
            for (; ; ) {
                int rawOp = ((in[p] & 0xFF) << 8) | (in[p + 1] & 0xFF);
                int op = rawOp & 0x7FFF;
                if (op == 0) { out.write(in[p]); out.write(in[p + 1]); p += 2; break; }
                if (op == 1) { out.write(in[p]); out.write(in[p + 1]); out.write(in[p + 2]); p += 3; break; }
                if (op <= 49) {
                    int v = ((in[p + 2] & 0xFF) << 8) | (in[p + 3] & 0xFF);
                    boolean drop = floor || (z == 0 && inCobble(gx, gy) && v == COBBLE_STORED);
                    if (drop && !floor) counts[1]++;
                    if (!drop) out.write(in, p, 4);
                    p += 4;
                    continue;
                }
                out.write(in[p]); out.write(in[p + 1]);
                p += 2;
            }
        }
        BuildSanctumMap.check(p == in.length, "terrain leftover " + (in.length - p));
        return out.toByteArray();
    }

    /** Re-decodes both: every tile outside the edited ones byte-identical in meaning, the edited ones as intended. */
    static void check(byte[] src, byte[] dst, int baseX, int baseY, int stored) {
        int[][][] a = decode(src), b = decode(dst);
        for (int z = 0; z < 4; z++) for (int x = 0; x < 64; x++) for (int y = 0; y < 64; y++) {
            int gx = baseX + x, gy = baseY + y;
            int[] s = a[z][x * 64 + y], d = b[z][x * 64 + y];
            BuildSanctumMap.check(s[1] == d[1] && s[2] == d[2] && s[3] == d[3] && s[4] == d[4], "tile " + gx + "," + gy + " height/underlay/settings");
            if (z == 0 && inFloor(gx, gy)) BuildSanctumMap.check(d[0] == stored && d[5] == 0, "floor tile " + gx + "," + gy);
            else if (z == 0 && inCobble(gx, gy) && s[0] == COBBLE_STORED) BuildSanctumMap.check(d[0] == 0, "cobble tile " + gx + "," + gy);
            else BuildSanctumMap.check(s[0] == d[0] && s[5] == d[5], "untouched tile " + gx + "," + gy);
        }
    }

    /** Per tile: overlay(stored), heightFlag, height, underlay, settings, overlayShapeOp. */
    static int[][][] decode(byte[] in) {
        int[][][] t = new int[4][64 * 64][6];
        int p = 0;
        for (int z = 0; z < 4; z++) for (int x = 0; x < 64; x++) for (int y = 0; y < 64; y++) {
            int[] r = t[z][x * 64 + y];
            for (; ; ) {
                int op = (((in[p] & 0xFF) << 8) | (in[p + 1] & 0xFF)) & 0x7FFF;
                p += 2;
                if (op == 0) break;
                if (op == 1) { r[1] = 1; r[2] = in[p++] & 0xFF; break; }
                if (op <= 49) { r[0] = ((in[p] & 0xFF) << 8) | (in[p + 1] & 0xFF); r[5] = op - 2; p += 2; }
                else if (op <= 81) r[4] = op - 49;
                else r[3] = op - 81;
            }
        }
        BuildSanctumMap.check(p == in.length, "decode length");
        return t;
    }
}
