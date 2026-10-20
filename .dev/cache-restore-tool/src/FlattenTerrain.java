import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.ObjectDefinition;
import net.runelite.cache.definitions.loaders.ObjectLoader;
import net.runelite.cache.fs.*;
import net.runelite.cache.region.Location;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.*;

// Levels a stretch of floor (2026-10-20, the new global boss cavern at 2542,4958): inside a world rectangle every
// plane-0 floor tile -- no solid wall / scenery loc on it -- gets one explicit height (opcode 1), and the listed
// overlays are cleared (back to the bare underlay). Tiles under walls / rocks keep their height so the cliffs don't
// move. Height "median" = the median of the masked floor tiles. Works per map square (run it for each the rectangle
// touches); every other tile and opcode is compared back unchanged before anything is written.
// Usage: verify|apply <cachePath> <mx_my> <x0,y0,x1,y1 world> <height|median> [clearOverlays a,b,...]
public class FlattenTerrain {
    public static void main(String[] a) throws Exception {
        boolean apply = a[0].equals("apply");
        String[] sq = a[2].split("_");
        int mx = Integer.parseInt(sq[0]), my = Integer.parseInt(sq[1]);
        int[] r = Arrays.stream(a[3].split(",")).mapToInt(Integer::parseInt).toArray();
        Set<Integer> clear = new HashSet<>();
        if (a.length > 5) for (String s : a[5].split(",")) clear.add(Integer.parseInt(s));
        try (Store store = new Store(new File(a[1]))) {
            store.load();
            Storage st = store.getStorage();
            Index maps = store.getIndex(IndexType.MAPS);
            Archive m = maps.findArchiveByName("m" + mx + "_" + my);
            byte[] in = m.decompress(st.loadArchive(m));
            RenderRegion.Scene s = RenderRegion.Scene.load(store, mx, my);
            boolean[][] solid = new boolean[64][64];
            for (Location l : s.locs) {
                if (l.getPosition().getZ() != 0) continue;
                int t = l.getType();
                if (t >= 4 && t <= 8 || t == 22) continue; // wall decorations / floor decorations never block a tile
                ObjectDefinition d = new ObjectLoader().load(l.getId(), s.objectBytes.get(l.getId()));
                if (d.getInteractType() == 0) continue;
                int sx = d.getSizeX(), sy = d.getSizeY();
                if ((l.getOrientation() & 1) == 1) { int tmp = sx; sx = sy; sy = tmp; }
                int lx = l.getPosition().getX() & 63, ly = l.getPosition().getY() & 63;
                for (int dx = 0; dx < (t <= 3 || t == 9 ? 1 : sx); dx++)
                    for (int dy = 0; dy < (t <= 3 || t == 9 ? 1 : sy); dy++)
                        if (lx + dx < 64 && ly + dy < 64) solid[lx + dx][ly + dy] = true;
            }
            boolean[][] mask = new boolean[64][64];
            List<Integer> hs = new ArrayList<>();
            int n = 0;
            for (int x = 0; x < 64; x++) for (int y = 0; y < 64; y++) {
                int wx = mx * 64 + x, wy = my * 64 + y;
                if (wx < r[0] || wy < r[1] || wx > r[2] || wy > r[3] || solid[x][y]) continue;
                if (s.underlay[0][x][y] == 0 && s.overlay[0][x][y] == 0) continue; // no floor at all
                mask[x][y] = true;
                hs.add(-s.heights[0][x][y] / 8);
                n++;
            }
            Collections.sort(hs);
            int height = a[4].equals("median") ? hs.get(hs.size() / 2) : Integer.parseInt(a[4]);
            System.out.println("m" + mx + "_" + my + ": " + n + " floor tiles levelled to " + height
                    + (hs.isEmpty() ? "" : " (were " + hs.get(0) + ".." + hs.get(hs.size() - 1) + ")"));
            if (n == 0) return;

            // rewrite the u16 tile stream: masked tiles get "1 <height>" and lose the cleared overlays
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            int p = 0, cleared = 0;
            for (int z = 0; z < 4; z++) for (int x = 0; x < 64; x++) for (int y = 0; y < 64; y++) {
                boolean level = z == 0 && mask[x][y];
                for (; ; ) {
                    int rawOp = ((in[p] & 0xFF) << 8) | (in[p + 1] & 0xFF);
                    p += 2;
                    int op = rawOp & 0x7FFF;
                    if (op == 0 || op == 1) {
                        if (op == 1) p++;
                        if (level) {
                            out.write(0); out.write(1); out.write(height);
                        } else if (op == 1) {
                            out.write(rawOp >> 8); out.write(rawOp); out.write(in[p - 1]);
                        } else {
                            out.write(rawOp >> 8); out.write(rawOp);
                        }
                        break;
                    }
                    if (op <= 49) {
                        int v = ((in[p] & 0xFF) << 8) | (in[p + 1] & 0xFF);
                        p += 2;
                        if (level && clear.contains(v)) { cleared++; continue; }
                        out.write(rawOp >> 8); out.write(rawOp); out.write(v >> 8); out.write(v);
                    } else {
                        out.write(rawOp >> 8); out.write(rawOp);
                    }
                }
            }
            BuildSanctumMap.check(p == in.length, "terrain leftover");
            byte[] dst = out.toByteArray();

            // verify: decode the result; masked tiles have the height, cleared overlays are gone, everything else is equal
            RenderRegion.Scene back = RenderRegion.Scene.empty(store);
            back.decodeTerrain(dst, mx * 64, my * 64);
            for (int z = 0; z < 4; z++) for (int x = 0; x < 64; x++) for (int y = 0; y < 64; y++) {
                boolean level = z == 0 && mask[x][y];
                if (level) {
                    BuildSanctumMap.check(back.heights[0][x][y] == -(height == 1 ? 0 : height) * 8, "height at " + x + "," + y);
                    int was = s.overlay[0][x][y];
                    BuildSanctumMap.check(back.overlay[0][x][y] == (clear.contains(was) ? 0 : was), "overlay at " + x + "," + y);
                } else {
                    if (z == 0 || !anyMaskBelow(mask, x, y))
                        BuildSanctumMap.check(back.heights[z][x][y] == s.heights[z][x][y], "untouched height " + z + "," + x + "," + y);
                    BuildSanctumMap.check(back.overlay[z][x][y] == s.overlay[z][x][y], "untouched overlay " + z + "," + x + "," + y);
                }
                BuildSanctumMap.check(back.underlay[z][x][y] == s.underlay[z][x][y], "underlay " + z + "," + x + "," + y);
            }
            System.out.println("  overlays cleared on " + cleared + " tiles; verified");
            if (!apply) {
                System.out.println("VERIFY OK -- nothing written.");
                return;
            }
            Container c = new Container(m.getCompression(), -1);
            c.compress(dst, null);
            st.store(maps.getId(), m.getArchiveId(), c.data);
            m.setCrc(c.crc);
            m.setRevision(m.getRevision() + 1);
            m.setCompressedSize(c.data.length);
            m.setDecompressedSize(dst.length);
            ImportCathedralMap.writeIndexReferenceTable(st, maps);
            System.out.println("APPLY complete -- m" + mx + "_" + my + " written.");
        }
    }

    /** Upper planes' default heights stack on the plane below, so they move with it. */
    static boolean anyMaskBelow(boolean[][] mask, int x, int y) {
        return mask[x][y];
    }
}
