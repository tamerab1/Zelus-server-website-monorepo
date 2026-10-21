import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.ObjectDefinition;
import net.runelite.cache.definitions.loaders.ObjectLoader;
import net.runelite.cache.fs.*;
import net.runelite.cache.region.Location;
import net.runelite.cache.region.Position;

import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.util.*;

// The event boss cavern (::gb / ::vb / ::db, EventBossArea, centre 2560,4960), 2026-10-21: the floor FlattenTerrain
// levelled to height 34 (a flat grey plane) gets a battle-worn cavern look, in the map, across both map squares
// (39_77 / 40_77). Only the levelled floor tiles are touched (inside 2532..2578 x 4942..4982, no solid loc, height 34):
//   - dais: the bosses' spawn centre in rings -- a 2x2 lava seal, carved dark slabs (overlay 338), the obsidian rune
//     circle (436, cyan-glinting tiles), a lighter slab rim (337);
//   - floor: dark cracked bedrock (260) with value-noise patches of lighter rock (258) and scorched stone (31), bone
//     strewn ground (257) along the walls, and 6 short jagged, broken lava cracks (18) running out from the rim;
//   - rim: the floor under the cliff-edge locs next to it takes the bedrock / bones overlay too (height kept), so the
//     floor runs into the rock with no grey seam;
//   - height: everything at distance >= 4 from the rock stays exactly 34 (clean pathing / telegraphs); the last 3 tiles
//     rise +1..+2 into the cliff bases;
//   - decor (written as server spawns, not into the map): wrought-iron braziers, steam / sulphur vents, stalagmites,
//     ruined pillars, skeletons, bone piles and chains, only on wall-adjacent tiles that aren't in a passage, never
//     near the teleport tile, at least 2 tiles apart. Every central tile stays walkable.
// Every other tile / opcode is compared back unchanged before writing. Previews (with the decor) go to previewDir.
// Usage: verify|apply <cachePath> <previewDir> <spawnsOut.json>
public class BuildBossCavern {
    static final int X0 = 2532, Y0 = 4942, X1 = 2578, Y1 = 4982, LEVEL = 34;
    static final double CX = 2560, CY = 4960;
    static final int BX = 2496, BY = 4928, W = 128, H = 64; // both map squares
    static final int TELE_X = 2547, TELE_Y = 4957;
    /** Stored overlay values (definition id + 1). */
    static final int LAVA = 19, SLAB = 339, RUNE = 437, RIM = 338, BEDROCK = 261, ROCK = 259, SCORCH = 32, BONES = 258;

    public static void main(String[] a) throws Exception {
        boolean apply = a[0].equals("apply");
        File preview = new File(a[2]);
        preview.mkdirs();
        try (Store store = new Store(new File(a[1]))) {
            store.load();
            Storage st = store.getStorage();
            Index maps = store.getIndex(IndexType.MAPS);
            RenderRegion.Scene[] sc = {RenderRegion.Scene.load(store, 39, 77), RenderRegion.Scene.load(store, 40, 77)};

            // ---- the levelled floor mask over both squares ----
            boolean[][] solid = new boolean[W][H], mask = new boolean[W][H];
            for (int s = 0; s < 2; s++) {
                for (Location l : sc[s].locs) {
                    if (l.getPosition().getZ() != 0) continue;
                    int t = l.getType();
                    if (t >= 4 && t <= 8 || t == 22) continue;
                    ObjectDefinition d = new ObjectLoader().load(l.getId(), sc[s].objectBytes.get(l.getId()));
                    if (d.getInteractType() == 0) continue;
                    int sx = d.getSizeX(), sy = d.getSizeY();
                    if ((l.getOrientation() & 1) == 1) { int tmp = sx; sx = sy; sy = tmp; }
                    int gx = l.getPosition().getX() - BX, gy = l.getPosition().getY() - BY;
                    for (int dx = 0; dx < (t <= 3 || t == 9 ? 1 : sx); dx++)
                        for (int dy = 0; dy < (t <= 3 || t == 9 ? 1 : sy); dy++)
                            if (gx + dx >= 0 && gx + dx < W && gy + dy >= 0 && gy + dy < H) solid[gx + dx][gy + dy] = true;
                }
            }
            int floor = 0;
            for (int gx = 0; gx < W; gx++) for (int gy = 0; gy < H; gy++) {
                int wx = BX + gx, wy = BY + gy;
                if (wx < X0 || wy < Y0 || wx > X1 || wy > Y1 || solid[gx][gy]) continue;
                RenderRegion.Scene s = sc[gx >> 6];
                int lx = gx & 63, ly = gy;
                if (s.underlay[0][lx][ly] == 0 && s.overlay[0][lx][ly] == 0) continue;
                if (s.heights[0][lx][ly] != -LEVEL * 8) continue; // only what FlattenTerrain levelled
                mask[gx][gy] = true;
                floor++;
            }
            // ---- the rim: floor under the cliff-edge locs next to the levelled floor (overlay only, height kept) ----
            boolean[][] rim = new boolean[W][H];
            int rims = 0;
            for (int gx = 1; gx < W - 1; gx++) for (int gy = 1; gy < H - 1; gy++) {
                if (mask[gx][gy]) continue;
                int wx = BX + gx, wy = BY + gy;
                if (wx < X0 - 2 || wy < Y0 - 2 || wx > X1 + 2 || wy > Y1 + 2) continue;
                RenderRegion.Scene s = sc[gx >> 6];
                if (s.underlay[0][gx & 63][gy] == 0 && s.overlay[0][gx & 63][gy] == 0) continue;
                if (mask[gx + 1][gy] || mask[gx - 1][gy] || mask[gx][gy + 1] || mask[gx][gy - 1]) {
                    rim[gx][gy] = true;
                    rims++;
                }
            }
            System.out.println("rim tiles " + rims);

            // ---- distance to the rock (4-neighbour BFS) ----
            int[][] dist = new int[W][H];
            ArrayDeque<int[]> q = new ArrayDeque<>();
            for (int gx = 0; gx < W; gx++) for (int gy = 0; gy < H; gy++) {
                dist[gx][gy] = mask[gx][gy] ? Integer.MAX_VALUE : 0;
                if (!mask[gx][gy]) q.add(new int[]{gx, gy});
            }
            while (!q.isEmpty()) {
                int[] c = q.poll();
                for (int[] dd : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    int nx = c[0] + dd[0], ny = c[1] + dd[1];
                    if (nx < 0 || ny < 0 || nx >= W || ny >= H || dist[nx][ny] <= dist[c[0]][c[1]] + 1) continue;
                    dist[nx][ny] = dist[c[0]][c[1]] + 1;
                    q.add(new int[]{nx, ny});
                }
            }

            // ---- design ----
            int[][] ov = new int[W][H], ht = new int[W][H];
            Random rnd = new Random(4960);
            Set<Long> fissure = new HashSet<>();
            for (int f = 0; f < 6; f++) {
                double ang = Math.PI * 2 * f / 6 + 0.35 + rnd.nextDouble() * 0.4, r = 7.2;
                double x = CX + Math.cos(ang) * r, y = CY + Math.sin(ang) * r;
                // short, jagged and broken (a crack, not a painted line): strong turns, ~1 in 4 steps left as a gap
                int length = 9 + rnd.nextInt(6);
                for (int step = 0; step < length; step++) {
                    int gx = (int) Math.floor(x) - BX, gy = (int) Math.floor(y) - BY;
                    if (gx < 0 || gy < 0 || gx >= W || gy >= H || !mask[gx][gy] || dist[gx][gy] <= 2) break;
                    if (rnd.nextDouble() > 0.25) fissure.add(key(gx, gy));
                    ang += (rnd.nextDouble() - 0.5) * 1.3;
                    x += Math.cos(ang) * 0.9;
                    y += Math.sin(ang) * 0.9;
                }
            }
            Map<Integer, Integer> used = new TreeMap<>();
            for (int gx = 0; gx < W; gx++) for (int gy = 0; gy < H; gy++) {
                if (rim[gx][gy]) {
                    ov[gx][gy] = hash(BX + gx, BY + gy, 7) < 0.3 ? BONES : BEDROCK;
                    continue;
                }
                if (!mask[gx][gy]) continue;
                int wx = BX + gx, wy = BY + gy;
                double r = Math.hypot(wx + 0.5 - CX, wy + 0.5 - CY);
                int d = dist[gx][gy];
                int o;
                if (r < 1.5) o = LAVA;
                else if (r < 4.6) o = SLAB;
                else if (r < 5.6) o = RUNE;
                else if (r < 6.4) o = RIM;
                else if (fissure.contains(key(gx, gy))) o = LAVA;
                else if (d <= 2 && hash(wx, wy, 7) < 0.3) o = BONES;
                else {
                    double n = noise(wx / 5.0, wy / 5.0);
                    o = n < 0.22 ? SCORCH : n > 0.68 ? ROCK : BEDROCK;
                }
                ov[gx][gy] = o;
                used.merge(o, 1, Integer::sum);
                ht[gx][gy] = LEVEL + (d == 1 ? 2 : d == 2 ? 1 : d == 3 && hash(wx, wy, 3) < 0.4 ? 1 : 0);
            }
            System.out.println("floor tiles " + floor + ", overlays " + used + ", fissure tiles " + fissure.size());

            // ---- decor ----
            List<int[]> decor = placeDecor(mask, dist, rnd);
            for (int[] o : decor) {
                int s = (o[1] - BX) >> 6;
                sc[s].locs.add(new Location(o[0], 10, o[3], new Position(o[1], o[2], 0)));
            }
            StringBuilder json = new StringBuilder();
            json.append("[\n");
            for (int i = 0; i < decor.size(); i++) {
                int[] o = decor.get(i);
                json.append(String.format("\t{\"id\": %d, \"x\": %d, \"y\": %d, \"z\": 0, \"type\": 10, \"direction\": %d}%s // %s\n",
                    o[0], o[1], o[2], o[3], i + 1 < decor.size() ? "," : "", name(o[0])));
            }
            json.append("]\n");
            Files.writeString(new File(a[3]).toPath(), json.toString());
            System.out.println("decor: " + decor.size() + " spawns -> " + a[3]);

            // ---- terrain rewrite per square ----
            byte[][] dst = new byte[2][];
            Archive[] arch = new Archive[2];
            for (int s = 0; s < 2; s++) {
                arch[s] = maps.findArchiveByName("m" + (39 + s) + "_77");
                byte[] in = arch[s].decompress(st.loadArchive(arch[s]));
                dst[s] = rewrite(in, s, mask, rim, ov, ht);
                RenderRegion.Scene back = RenderRegion.Scene.empty(store);
                back.decodeTerrain(dst[s], (39 + s) * 64, 77 * 64);
                for (int z = 0; z < 4; z++) for (int x = 0; x < 64; x++) for (int y = 0; y < 64; y++) {
                    boolean m = z == 0 && mask[s * 64 + x][y];
                    if (z == 0 && rim[s * 64 + x][y]) {
                        BuildSanctumMap.check(back.overlay[0][x][y] == ov[s * 64 + x][y] && back.shape[0][x][y] == 0, "rim overlay " + x + "," + y);
                        BuildSanctumMap.check(back.heights[0][x][y] == sc[s].heights[0][x][y], "rim height " + x + "," + y);
                    } else if (m) {
                        BuildSanctumMap.check(back.heights[0][x][y] == -ht[s * 64 + x][y] * 8, "height " + x + "," + y);
                        BuildSanctumMap.check(back.overlay[0][x][y] == ov[s * 64 + x][y] && back.shape[0][x][y] == 0, "overlay " + x + "," + y);
                    } else {
                        if (z == 0 || !mask[s * 64 + x][y])
                            BuildSanctumMap.check(back.heights[z][x][y] == sc[s].heights[z][x][y], "untouched height " + z + "," + x + "," + y);
                        BuildSanctumMap.check(back.overlay[z][x][y] == sc[s].overlay[z][x][y] && back.shape[z][x][y] == sc[s].shape[z][x][y],
                            "untouched overlay " + z + "," + x + "," + y);
                    }
                    BuildSanctumMap.check(back.underlay[z][x][y] == sc[s].underlay[z][x][y], "underlay " + z + "," + x + "," + y);
                }
                // preview with the new terrain and the decor
                RenderRegion.Scene pv = RenderRegion.Scene.empty(store);
                pv.decodeTerrain(dst[s], (39 + s) * 64, 77 * 64);
                pv.locs.addAll(sc[s].locs);
                pv.objectBytes = sc[s].objectBytes;
                for (int[] cam : s == 0 ? new int[][]{{40, 52, 32}, {220, 52, 32}} : new int[][]{{40, 8, 32}, {220, 8, 32}}) {
                    SceneRenderer r = new SceneRenderer(1400, 900, 0x1a1d24);
                    r.camera(cam[1] * 128, pv.height(0, cam[1], cam[2]), cam[2] * 128, Math.toRadians(cam[0]), Math.toRadians(40), 34 * 128);
                    pv.draw(r, 0);
                    ImageIO.write(r.image(), "png", new File(preview, "cavern_m" + (39 + s) + "_77_yaw" + cam[0] + ".png"));
                }
            }
            System.out.println("verified both squares");
            if (!apply) {
                System.out.println("VERIFY OK -- nothing written.");
                return;
            }
            for (int s = 0; s < 2; s++) {
                Container c = new Container(arch[s].getCompression(), -1);
                c.compress(dst[s], null);
                st.store(maps.getId(), arch[s].getArchiveId(), c.data);
                arch[s].setCrc(c.crc);
                arch[s].setRevision(arch[s].getRevision() + 1);
                arch[s].setCompressedSize(c.data.length);
                arch[s].setDecompressedSize(dst[s].length);
            }
            ImportCathedralMap.writeIndexReferenceTable(st, maps);
            System.out.println("APPLY complete -- m39_77 / m40_77 written.");
        }
    }

    /** Masked plane-0 tiles: their overlays replaced by one full-tile overlay, other opcodes kept, explicit height. */
    static byte[] rewrite(byte[] in, int s, boolean[][] mask, boolean[][] rim, int[][] ov, int[][] ht) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int p = 0;
        for (int z = 0; z < 4; z++) for (int x = 0; x < 64; x++) for (int y = 0; y < 64; y++) {
            boolean m = z == 0 && mask[s * 64 + x][y], rm = z == 0 && rim[s * 64 + x][y];
            if (m || rm) {
                int v = ov[s * 64 + x][y];
                out.write(0); out.write(2); out.write(v >> 8); out.write(v);
            }
            for (; ; ) {
                int rawOp = ((in[p] & 0xFF) << 8) | (in[p + 1] & 0xFF);
                p += 2;
                int op = rawOp & 0x7FFF;
                if (op == 0 || op == 1) {
                    if (op == 1) p++;
                    if (m) {
                        out.write(0); out.write(1); out.write(ht[s * 64 + x][y]);
                    } else if (op == 1) {
                        out.write(rawOp >> 8); out.write(rawOp); out.write(in[p - 1]);
                    } else {
                        out.write(rawOp >> 8); out.write(rawOp);
                    }
                    break;
                }
                if (op <= 49) {
                    if (!m && !rm) out.write(in, p - 2, 4);
                    p += 2;
                } else {
                    out.write(rawOp >> 8); out.write(rawOp);
                }
            }
        }
        BuildSanctumMap.check(p == in.length, "terrain leftover");
        return out.toByteArray();
    }

    /** {id, x, y, direction}: braziers spread evenly round the rim, the other pieces between them. */
    static List<int[]> placeDecor(boolean[][] mask, int[][] dist, Random rnd) {
        List<int[]> edge = new ArrayList<>();
        for (int gx = 1; gx < W - 1; gx++) for (int gy = 1; gy < H - 1; gy++) {
            if (!mask[gx][gy] || dist[gx][gy] != 1) continue;
            int wx = BX + gx, wy = BY + gy;
            if (Math.hypot(wx - TELE_X, wy - TELE_Y) < 4) continue;
            int open = 0, deep = 0;
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) {
                if (mask[gx + dx][gy + dy]) open++;
                if (dist[gx + dx][gy + dy] >= 2) deep++;
            }
            if (open < 5 || deep == 0) continue; // a passage: keep it clear
            edge.add(new int[]{gx, gy});
        }
        edge.sort(Comparator.comparingDouble(e -> Math.atan2(BY + e[1] + 0.5 - CY, BX + e[0] + 0.5 - CX)));
        int[] others = {15214, 22542, 34795, 14477, 22543, 14526, 9524, 34796, 22541, 14478, 14528, 15214};
        List<int[]> out = new ArrayList<>();
        Set<Long> taken = new HashSet<>();
        int braziers = 14, n = edge.size();
        // pass 1: the braziers, evenly round the rim (the light); pass 2: everything else in the gaps
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < n; i++) {
                int[] e = edge.get(i);
                boolean brazierSlot = i % Math.max(1, n / braziers) == n / braziers / 2;
                int id = pass == 0 ? (brazierSlot ? 33454 : -1) : i % 3 == 0 ? others[(i / 3) % others.length] : -1;
                if (id < 0) continue;
                boolean free = true;
                for (int dx = -2; dx <= 2 && free; dx++) for (int dy = -2; dy <= 2; dy++)
                    if (taken.contains(key(e[0] + dx, e[1] + dy))) { free = false; break; }
                if (!free) continue;
                taken.add(key(e[0], e[1]));
                out.add(new int[]{id, BX + e[0], BY + e[1], rnd.nextInt(4)});
            }
        }
        return out;
    }

    static String name(int id) {
        return switch (id) {
            case 33454 -> "Brazier";
            case 15214 -> "Steam Vent";
            case 22541, 22542, 22543 -> "Stalagmite";
            case 34795, 34796 -> "Ruined Pillar";
            case 14477, 14478 -> "Skeleton";
            case 14526, 14528 -> "Bones";
            case 9524 -> "Chain";
            default -> "decor";
        };
    }

    static long key(int x, int y) {
        return ((long) x << 32) | (y & 0xffffffffL);
    }

    static double hash(int x, int y, int salt) {
        long h = x * 374761393L + y * 668265263L + salt * 2147483647L;
        h = (h ^ (h >>> 13)) * 1274126177L;
        return ((h ^ (h >>> 16)) & 0xffff) / 65536.0;
    }

    /** Smooth value noise in 0..1. */
    static double noise(double x, double y) {
        int ix = (int) Math.floor(x), iy = (int) Math.floor(y);
        double fx = x - ix, fy = y - iy;
        fx = fx * fx * (3 - 2 * fx);
        fy = fy * fy * (3 - 2 * fy);
        double a = hash(ix, iy, 11), b = hash(ix + 1, iy, 11), c = hash(ix, iy + 1, 11), d = hash(ix + 1, iy + 1, 11);
        return (a + (b - a) * fx) * (1 - fy) + (c + (d - c) * fx) * fy;
    }
}
