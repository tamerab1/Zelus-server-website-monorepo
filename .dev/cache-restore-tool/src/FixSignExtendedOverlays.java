import net.runelite.cache.IndexType;
import net.runelite.cache.fs.*;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

// Terrain fix (2026-10-21, 117HD smudge on the home floors): some home tiles (m48_54: the casino checker 3116-3130 x
// 3466-3475 and the bank floor 3100-3109 x 3485-3490) store their overlay as a sign-extended byte -- 0xFF9D / 0xFFAE
// instead of 157 / 174 (overlays 156 gold and 173 square flagstones, stored +1). 117HD looks the raw value up, finds
// no rule and falls back to its NONE override (dirt material, blended), so the floor smudged into muddy diamonds.
// Every overlay value >= 0x8000 in the given regions becomes its low byte; heights, shapes, underlays and settings are
// untouched (re-decoded and compared tile by tile). Usage: verify|apply <cachePath> <rx_ry>[,<rx_ry>...]
public class FixSignExtendedOverlays {
    public static void main(String[] args) throws Exception {
        boolean apply = args[0].equals("apply");
        try (Store store = new Store(new File(args[1]))) {
            store.load();
            Storage storage = store.getStorage();
            Index maps = store.getIndex(IndexType.MAPS);
            for (String region : args[2].split(",")) {
                Archive m = maps.findArchiveByName("m" + region);
                BuildSanctumMap.check(m != null, "no terrain m" + region);
                byte[] in = m.decompress(storage.loadArchive(m));
                Map<Integer, Integer> fixed = new TreeMap<>();
                byte[] out = rewrite(in, fixed);
                int[][][] a = BuildSanctuaryFloor.decode(in), b = BuildSanctuaryFloor.decode(out);
                for (int z = 0; z < 4; z++) for (int t = 0; t < 64 * 64; t++) {
                    int[] s = a[z][t], d = b[z][t];
                    BuildSanctumMap.check(s[1] == d[1] && s[2] == d[2] && s[3] == d[3] && s[4] == d[4] && s[5] == d[5],
                        "m" + region + " tile " + z + ":" + t + " changed more than its overlay");
                    BuildSanctumMap.check(s[0] == d[0] || (s[0] >= 0x8000 && d[0] == (s[0] & 0xFF)), "m" + region + " tile " + z + ":" + t + " overlay");
                }
                System.out.printf("m%s: %s (value -> low byte: tiles), %d -> %d bytes%n", region, fixed, in.length, out.length);
                if (fixed.isEmpty() || !apply)
                    continue;
                Container c = new Container(m.getCompression(), -1);
                c.compress(out, null);
                storage.store(maps.getId(), m.getArchiveId(), c.data);
                m.setCrc(c.crc);
                m.setRevision(m.getRevision() + 1);
                m.setCompressedSize(c.data.length);
                m.setDecompressedSize(out.length);
                ImportCathedralMap.writeIndexReferenceTable(storage, maps);
                Archive m2 = maps.findArchiveByName("m" + region);
                BuildSanctumMap.check(Arrays.equals(m2.decompress(storage.loadArchive(m2)), out), "stored terrain m" + region);
                System.out.println("APPLY complete -- m" + region + " revision " + m.getRevision());
            }
            if (!apply)
                System.out.println("VERIFY OK -- nothing written.");
        }
    }

    /** Same opcode walk as BuildSanctuaryFloor.rewrite: 2-byte opcodes, an overlay (op 2-49) carries a 2-byte value. */
    static byte[] rewrite(byte[] in, Map<Integer, Integer> fixed) {
        int p = 0;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int z = 0; z < 4; z++) for (int x = 0; x < 64; x++) for (int y = 0; y < 64; y++) {
            for (; ; ) {
                int op = (((in[p] & 0xFF) << 8) | (in[p + 1] & 0xFF)) & 0x7FFF;
                if (op == 0) { out.write(in, p, 2); p += 2; break; }
                if (op == 1) { out.write(in, p, 3); p += 3; break; }
                if (op <= 49) {
                    int v = ((in[p + 2] & 0xFF) << 8) | (in[p + 3] & 0xFF);
                    out.write(in, p, 2);
                    if (v >= 0x8000) {
                        fixed.merge(v, 1, Integer::sum);
                        v &= 0xFF;
                    }
                    out.write(v >> 8);
                    out.write(v);
                    p += 4;
                    continue;
                }
                out.write(in, p, 2);
                p += 2;
            }
        }
        BuildSanctumMap.check(p == in.length, "terrain leftover " + (in.length - p));
        return out.toByteArray();
    }
}
