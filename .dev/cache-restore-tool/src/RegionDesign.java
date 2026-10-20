import net.runelite.cache.fs.Store;
import net.runelite.cache.region.Location;
import net.runelite.cache.region.Position;

import javax.imageio.ImageIO;
import java.io.File;
import java.util.*;

// Region design toolkit (2026-10-20, region 12100): a 64x64 design in memory -- per-tile height, underlay, overlay
// (+ shape / rotation) and a loc list -- with OSRS-correct placement helpers (straight walls on tile edges by
// orientation 0 W / 1 N / 2 E / 3 S, corners, rooms with gaps for doorways, floor fills, scatter), previewed through
// RenderRegion.Scene / SceneRenderer. Subclasses (or main's "kits" test) build a layout; BuildRegion12100 writes it.
public class RegionDesign {
    final int[][] height = new int[64][64];       // explicit plane-0 height byte (OSRS units of 8 world units)
    final int[][] underlay = new int[64][64];     // stored id (def id + 1), 0 = none
    final int[][] overlay = new int[64][64];      // stored id (def id + 1), 0 = none
    final int[][] overlayShape = new int[64][64]; // (shape << 2) | rotation
    final List<int[]> locs = new ArrayList<>();   // id, type, orientation, plane, x, y

    void loc(int id, int type, int orientation, int x, int y) {
        if (x < 0 || y < 0 || x > 63 || y > 63) return;
        locs.add(new int[]{id, type, orientation & 3, 0, x, y});
    }

    void floor(int x0, int y0, int x1, int y1, int under, int over) {
        for (int x = Math.max(0, x0); x <= Math.min(63, x1); x++)
            for (int y = Math.max(0, y0); y <= Math.min(63, y1); y++) {
                if (under >= 0) underlay[x][y] = under;
                if (over >= 0) {
                    overlay[x][y] = over;
                    overlayShape[x][y] = 0;
                }
            }
    }

    void raise(int x0, int y0, int x1, int y1, int h) {
        for (int x = Math.max(0, x0); x <= Math.min(63, x1); x++)
            for (int y = Math.max(0, y0); y <= Math.min(63, y1); y++) height[x][y] = h;
    }

    /**
     * A walled rectangle x0..x1 / y0..y1 (inclusive tiles inside): straight walls (type 0) on its outer edges, corner
     * pieces (type 2) in the four corners. gaps: "N:5-7" etc. = wall tiles left open for doorways (tile coordinates).
     */
    void room(int x0, int y0, int x1, int y1, int wall, String... gaps) {
        Set<String> open = new HashSet<>();
        for (String g : gaps) {
            String[] p = g.split(":");
            String[] r = p[1].split("-");
            int a = Integer.parseInt(r[0]), b = r.length > 1 ? Integer.parseInt(r[1]) : a;
            for (int i = a; i <= b; i++) open.add(p[0] + i);
        }
        for (int x = x0; x <= x1; x++) {
            if (!open.contains("S" + x)) loc(wall, x == x0 ? 2 : x == x1 ? 2 : 0, x == x0 ? 3 : x == x1 ? 2 : 3, x, y0);
            if (!open.contains("N" + x)) loc(wall, x == x0 ? 2 : x == x1 ? 2 : 0, x == x0 ? 0 : x == x1 ? 1 : 1, x, y1);
        }
        for (int y = y0 + 1; y < y1; y++) {
            if (!open.contains("W" + y)) loc(wall, 0, 0, x0, y);
            if (!open.contains("E" + y)) loc(wall, 0, 2, x1, y);
        }
    }

    RenderRegion.Scene scene(Store store) throws Exception {
        RenderRegion.Scene s = RenderRegion.Scene.empty(store);
        for (int x = 0; x < 64; x++) for (int y = 0; y < 64; y++) {
            int hh = height[x][y] == 1 ? 0 : height[x][y];
            s.heights[0][x][y] = -hh * 8;
            s.underlay[0][x][y] = underlay[x][y];
            s.overlay[0][x][y] = overlay[x][y];
            s.shape[0][x][y] = overlayShape[x][y];
        }
        for (int z = 0; z < 1; z++) {
            for (int i = 0; i < 64; i++) {
                s.heights[z][64][i] = s.heights[z][63][i];
                s.heights[z][i][64] = s.heights[z][i][63];
            }
            s.heights[z][64][64] = s.heights[z][63][63];
        }
        for (int[] l : locs) s.locs.add(new Location(l[0], l[1], l[2], new Position(l[4], l[5], l[3])));
        s.locCount = locs.size();
        return s;
    }

    void preview(Store store, String out, double yawDeg, double pitchDeg, double fx, double fy, double dist, int w, int h) throws Exception {
        RenderRegion.Scene s = scene(store);
        SceneRenderer r = new SceneRenderer(w, h, 0x1a1d24);
        r.camera(fx * 128, s.height(0, fx, fy), fy * 128, Math.toRadians(yawDeg), Math.toRadians(pitchDeg), dist * 128);
        s.draw(r, 0);
        ImageIO.write(r.image(), "png", new File(out));
    }

    /** Wall-kit test: a 5x5 room per kit on a plain floor. Usage: RegionDesign <cache> <out.png> <wallId,...> [floorOverlay] */
    public static void main(String[] a) throws Exception {
        try (Store store = new Store(new File(a[0]))) {
            store.load();
            RegionDesign d = new RegionDesign();
            int over = a.length > 3 ? Integer.parseInt(a[3]) : 0;
            d.floor(0, 0, 63, 63, 50, -1);
            String[] kits = a[2].split(",");
            for (int k = 0; k < kits.length; k++) {
                int x0 = 2 + (k % 4) * 9, y0 = 2 + (k / 4) * 9;
                d.floor(x0, y0, x0 + 5, y0 + 5, -1, over);
                d.room(x0, y0, x0 + 5, y0 + 5, Integer.parseInt(kits[k]), "S:2-3");
            }
            int rows = (kits.length + 3) / 4;
            d.preview(store, a[1], 20, 38, 2 + 18, 2 + rows * 4.5, 44, 1600, 1100);
            System.out.println("wrote " + a[1]);
        }
    }
}
