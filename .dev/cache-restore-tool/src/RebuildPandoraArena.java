import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.loaders.LocationsLoader;
import net.runelite.cache.fs.*;
import net.runelite.cache.region.Location;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.*;

// Enlarges Pandora's lair (2026-10-20): the pinball room in map square 30_78 (old box local 39..63 both ways, 25x25)
// becomes a 41x41 box at local 23..63, grown west and south -- north and east are the map square's edges with other
// content beyond. A "9-slice" of the old room: corners, chamfers and props keep their exact shape; the straight runs of
// wall (9298), purple cushion (9308) and floor are lengthened by repeating old column 50 / 54 and old row 56. The green
// emblem is redrawn at exactly 2x (each old tile -> a 2x2 block) so it stays crisp; the red lights (9309) are refilled
// every other tile along the longer band lines; the pinball posts move with the emblem (the centre one, under the boss,
// is dropped). Every loc of the new box that isn't part of the old room (trees, path props) is removed; everything
// outside the box is compared back unchanged before anything is written.
// Usage: verify|apply <cachePath>
public class RebuildPandoraArena {
    static final int MX = 30, MY = 78, BOX0 = 23, OLD0 = 39;
    static final int EMB_X = 31, EMB_Y = 30; // new origin of the 2x emblem (old 46..58 x 46..59)
    static final int LIGHT = 9309, POSTS[] = {8982, 8984, 9079, 9081, 9258}, CENTRE_POST = 9079;

    static int invX(int nx) { return nx < BOX0 ? nx : nx <= 34 ? nx + 16 : nx <= 42 ? 50 : nx <= 45 ? nx + 8 : nx <= 54 ? 54 : nx; }
    static int invY(int ny) { return ny < BOX0 ? ny : ny <= 39 ? ny + 16 : ny <= 56 ? 56 : ny; }

    static List<Integer> fwdX(int ox) {
        List<Integer> r = new ArrayList<>();
        if (ox == 50) for (int x = 34; x <= 42; x++) r.add(x);
        else if (ox == 54) for (int x = 46; x <= 54; x++) r.add(x);
        else r.add(ox <= 49 ? ox - 16 : ox <= 53 ? ox - 8 : ox);
        return r;
    }

    static List<Integer> fwdY(int oy) {
        List<Integer> r = new ArrayList<>();
        if (oy == 56) for (int y = 40; y <= 56; y++) r.add(y);
        else r.add(oy <= 55 ? oy - 16 : oy);
        return r;
    }

    static boolean inBox(int x, int y) { return x >= BOX0 && y >= BOX0; }
    static boolean inOld(int x, int y) { return x >= OLD0 && y >= OLD0; }

    public static void main(String[] a) throws Exception {
        boolean apply = a[0].equals("apply");
        try (Store store = new Store(new File(a[1]))) {
            store.load();
            Storage st = store.getStorage();
            Index maps = store.getIndex(IndexType.MAPS);
            Archive m = maps.findArchiveByName("m" + MX + "_" + MY), l = maps.findArchiveByName("l" + MX + "_" + MY);
            byte[] terrain = m.decompress(st.loadArchive(m)), locRaw = l.decompress(st.loadArchive(l));
            RenderRegion.Scene s = RenderRegion.Scene.load(store, MX, MY);

            // ---- terrain: split the stream into raw per-tile byte runs, remap, rejoin
            byte[][][][] tile = new byte[4][64][64][];
            int p = 0;
            for (int z = 0; z < 4; z++) for (int x = 0; x < 64; x++) for (int y = 0; y < 64; y++) {
                int start = p;
                for (; ; ) {
                    int op = (((terrain[p] & 0xFF) << 8) | (terrain[p + 1] & 0xFF)) & 0x7FFF;
                    p += 2;
                    if (op == 0) break;
                    if (op == 1) { p++; break; }
                    if (op <= 49) p += 2;
                }
                tile[z][x][y] = Arrays.copyOfRange(terrain, start, p);
            }
            BuildSanctumMap.check(p == terrain.length, "terrain leftover");
            byte[] grey = tile[0][46][52];
            BuildSanctumMap.check(s.overlay[0][46][52] == 0, "grey sample");
            ByteArrayOutputStream tout = new ByteArrayOutputStream();
            int emblemTiles = 0;
            for (int z = 0; z < 4; z++) for (int x = 0; x < 64; x++) for (int y = 0; y < 64; y++) {
                byte[] raw = tile[z][x][y];
                if (inBox(x, y)) {
                    int ox = invX(x), oy = invY(y);
                    raw = tile[z][ox][oy];
                    int ov = s.overlay[0][ox][oy];
                    boolean interior = z == 0 && ox >= 45 && ox <= 59 && oy >= 46 && oy <= 60 && (ov == 0 || ov == 29);
                    if (interior) {
                        int rx = x - EMB_X, ry = y - EMB_Y;
                        if (rx >= 0 && rx < 26 && ry >= 0 && ry < 28) {
                            raw = tile[0][46 + rx / 2][46 + ry / 2];
                            if (s.overlay[0][46 + rx / 2][46 + ry / 2] != 0) emblemTiles++;
                        } else raw = grey;
                    }
                }
                tout.write(raw);
            }
            byte[] newTerrain = tout.toByteArray();

            // ---- locs
            List<Location> locs = new LocationsLoader().load(MX, MY, locRaw).getLocations();
            List<int[]> out = new ArrayList<>();
            Set<String> lights = new HashSet<>();
            int dropped = 0, kept = 0, arena = 0;
            for (Location loc : locs) {
                int x = loc.getPosition().getX() & 63, y = loc.getPosition().getY() & 63, z = loc.getPosition().getZ();
                int id = loc.getId(), t = loc.getType(), r = loc.getOrientation();
                if (!inBox(x, y)) { out.add(new int[]{id, t, r, z, x, y}); kept++; continue; }
                if (!inOld(x, y)) { dropped++; continue; } // trees / path props under the new room
                arena++;
                if (Arrays.stream(POSTS).anyMatch(i -> i == id)) {
                    if (id == CENTRE_POST) continue;
                    int nx = EMB_X + (x - 46) * 2 + (x > 52 ? 1 : 0), ny = EMB_Y + (y - 46) * 2 + 1;
                    out.add(new int[]{id, t, r, z, nx, ny});
                    continue;
                }
                for (int nx : fwdX(x)) for (int ny : fwdY(y)) {
                    out.add(new int[]{id, t, r, z, nx, ny});
                    if (id == LIGHT) lights.add(nx + "," + ny);
                }
            }
            // refill the lights every other tile along the four straight band lines
            int added = 0;
            List<int[]> lines = new ArrayList<>();
            for (int x = 29; x <= 59; x += 2) if (x < 43 || x > 45) lines.add(new int[]{x, 28});
            for (int x = 33; x <= 55; x += 2) lines.add(new int[]{x, 62});
            for (int y = 31; y <= 55; y += 2) { lines.add(new int[]{26, y}); lines.add(new int[]{62, y}); }
            for (int[] c : lines)
                if (lights.add(c[0] + "," + c[1])) { out.add(new int[]{LIGHT, 10, 2, 0, c[0], c[1]}); added++; }
            byte[] newLocs = BuildSanctumMap.encodeLocs(out);
            List<Location> back = new LocationsLoader().load(MX, MY, newLocs).getLocations();
            BuildSanctumMap.check(back.size() == out.size(), "loc round trip " + back.size() + " vs " + out.size());
            // everything outside the box comes back untouched
            Map<String, Integer> want = new HashMap<>(), got = new HashMap<>();
            for (Location loc : locs) if (!inBox(loc.getPosition().getX() & 63, loc.getPosition().getY() & 63)) want.merge(sig(loc), 1, Integer::sum);
            for (Location loc : back) if (!inBox(loc.getPosition().getX() & 63, loc.getPosition().getY() & 63)) got.merge(sig(loc), 1, Integer::sum);
            BuildSanctumMap.check(want.equals(got), "outside locs unchanged");

            RenderRegion.Scene check = RenderRegion.Scene.empty(store);
            check.decodeTerrain(newTerrain, MX * 64, MY * 64);
            for (int z = 0; z < 4; z++) for (int x = 0; x < 64; x++) for (int y = 0; y < 64; y++)
                if (!inBox(x, y)) {
                    BuildSanctumMap.check(check.overlay[z][x][y] == s.overlay[z][x][y] && check.underlay[z][x][y] == s.underlay[z][x][y]
                            && check.heights[z][x][y] == s.heights[z][x][y], "outside tile " + z + "," + x + "," + y);
                }
            System.out.printf("locs: %d outside kept, %d under the new room removed, %d arena -> %d new (+%d lights); emblem tiles %d%n",
                    kept, dropped, arena, out.size() - kept, added, emblemTiles);
            System.out.printf("terrain %d -> %d bytes, locs %d -> %d bytes; verified%n", terrain.length, newTerrain.length, locRaw.length, newLocs.length);
            if (!apply) { System.out.println("VERIFY OK -- nothing written."); return; }
            write(st, maps, m, newTerrain);
            write(st, maps, l, newLocs);
            ImportCathedralMap.writeIndexReferenceTable(st, maps);
            System.out.println("APPLY complete -- m/l" + MX + "_" + MY + " written.");
        }
    }

    static String sig(Location loc) {
        return loc.getId() + "@" + loc.getPosition().getZ() + "," + (loc.getPosition().getX() & 63) + "," + (loc.getPosition().getY() & 63)
                + "/" + loc.getType() + "/" + loc.getOrientation();
    }

    static void write(Storage st, Index maps, Archive ar, byte[] data) throws Exception {
        Container c = new Container(ar.getCompression(), -1);
        c.compress(data, null);
        st.store(maps.getId(), ar.getArchiveId(), c.data);
        ar.setCrc(c.crc);
        ar.setRevision(ar.getRevision() + 1);
        ar.setCompressedSize(c.data.length);
        ar.setDecompressedSize(data.length);
    }
}
