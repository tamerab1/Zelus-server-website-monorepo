import net.runelite.cache.IndexType;
import net.runelite.cache.fs.*;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Arrays;

// The Soul Sanctuary floor under 117HD (2026-10-18 round 11): 117HD picks a tile's ground material from its UNDERLAY
// too, and the 5x5 floor sat on two -- underlay 51 (which it renders as pale sand) on the north-west tiles, 6 (grass) on
// the rest -- so half the floor came out chalk white. Every floor tile gets the same underlay, 6, which also removes the
// underlay blend seam through the middle. Only the underlay op of the 25 plane-0 floor tiles changes; everything else is
// decoded back and compared.
// Usage: verify|apply <cachePath>
public class SetFloorUnderlay {
    static final int UNDERLAY = 6;

    public static void main(String[] args) throws Exception {
        boolean apply = args[0].equals("apply");
        try (Store store = new Store(new File(args[1]))) {
            store.load();
            Storage storage = store.getStorage();
            Index maps = store.getIndex(IndexType.MAPS);
            int rx = BuildSanctuaryFloor.X0 >> 6, ry = BuildSanctuaryFloor.Y0 >> 6, bx = rx << 6, by = ry << 6;
            Archive m = maps.findArchiveByName("m" + rx + "_" + ry);
            byte[] in = m.decompress(storage.loadArchive(m));
            int changed = 0, p = 0;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            for (int z = 0; z < 4; z++) for (int x = 0; x < 64; x++) for (int y = 0; y < 64; y++) {
                boolean floor = z == 0 && BuildSanctuaryFloor.inFloor(bx + x, by + y);
                boolean wrote = false;
                for (; ; ) {
                    int rawOp = ((in[p] & 0xFF) << 8) | (in[p + 1] & 0xFF), op = rawOp & 0x7FFF;
                    if (op == 0 || op == 1) {
                        if (floor && !wrote) { int u = UNDERLAY + 81; out.write(u >> 8); out.write(u); changed++; }
                        out.write(in, p, op == 0 ? 2 : 3);
                        p += op == 0 ? 2 : 3;
                        break;
                    }
                    if (op <= 49) { out.write(in, p, 4); p += 4; continue; }
                    if (op <= 81) { out.write(in, p, 2); p += 2; continue; }
                    if (floor) { // replace this tile's underlay op in place
                        int u = (rawOp & 0x8000) | (UNDERLAY + 81);
                        out.write(u >> 8); out.write(u);
                        wrote = true;
                        changed++;
                    } else out.write(in, p, 2);
                    p += 2;
                }
            }
            BuildSanctumMap.check(p == in.length, "terrain leftover");
            byte[] res = out.toByteArray();
            int[][][] a = BuildSanctuaryFloor.decode(in), b = BuildSanctuaryFloor.decode(res);
            for (int z = 0; z < 4; z++) for (int i = 0; i < 64 * 64; i++) {
                int gx = bx + i / 64, gy = by + i % 64;
                int[] s = a[z][i], d = b[z][i];
                boolean floor = z == 0 && BuildSanctuaryFloor.inFloor(gx, gy);
                BuildSanctumMap.check(s[0] == d[0] && s[1] == d[1] && s[2] == d[2] && s[4] == d[4] && s[5] == d[5], "tile " + gx + "," + gy);
                BuildSanctumMap.check(floor ? d[3] == UNDERLAY : d[3] == s[3], "underlay " + gx + "," + gy);
            }
            System.out.printf("m%d_%d: %d floor tiles set to underlay %d, %d -> %d bytes%n", rx, ry, changed, UNDERLAY, in.length, res.length);
            BuildSanctumMap.check(changed == 25, "changed " + changed);
            if (!apply) { System.out.println("VERIFY OK -- nothing written."); return; }
            Container c = new Container(m.getCompression(), -1);
            c.compress(res, null);
            storage.store(maps.getId(), m.getArchiveId(), c.data);
            m.setCrc(c.crc);
            m.setRevision(m.getRevision() + 1);
            m.setCompressedSize(c.data.length);
            m.setDecompressedSize(res.length);
            ImportCathedralMap.writeIndexReferenceTable(storage, maps);
            Archive m2 = maps.findArchiveByName("m" + rx + "_" + ry);
            BuildSanctumMap.check(Arrays.equals(m2.decompress(storage.loadArchive(m2)), res), "stored terrain");
            System.out.println("APPLY complete -- m" + rx + "_" + ry + " revision " + m.getRevision());
        }
    }
}
