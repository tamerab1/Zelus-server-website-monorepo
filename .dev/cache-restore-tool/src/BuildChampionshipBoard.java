import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.ObjectDefinition;
import net.runelite.cache.definitions.SpriteDefinition;
import net.runelite.cache.definitions.TextureDefinition;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.definitions.loaders.ObjectLoader;
import net.runelite.cache.definitions.loaders.SpriteLoader;
import net.runelite.cache.definitions.loaders.TextureLoader;
import net.runelite.cache.fs.*;
import net.runelite.cache.fs.Container;
import net.runelite.cache.index.FileData;
import net.runelite.cache.index.IndexData;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.List;
import java.util.zip.CRC32;

// The Pit Championship Board (object 44930, 2026-10-15): a new model built from code instead of the plain wooden
// noticeboard 3293, plus a painted parchment face.
//   - Frame: charcoal-oak posts on iron feet with gold caps, finials and rivets; a charcoal frame with gold trim.
//   - Crest above the board: gold-rimmed steel shield, crossed swords and a gold championship cup (geometry).
//   - Crimson banners hanging in front of both posts, gold borders, a gold cup emblem, swallowtail ends (geometry).
//   - Face: "CHAMPIONSHIPS" title plate, CURRENT EVENT / UPCOMING TOURNAMENT columns, a 1-5 TOP CHAMPIONS table with
//     alternating rows, trophies, crossed swords and stars. Painted with Java2D at 4x, resampled to 256x256 and split
//     into four 128x128 textures (sprites 24532-24535, textures appended above the current max). Each texture sits on
//     its own FLAT quad whose own corners are the texture triangle (P = top-left, M = top-right, N = bottom-left), the
//     standard sign mapping: no helper vertices and no projection (cf. the failed RemapTextureUVs).
// The text is static: the live standings stay in the hub (interface 1148) behind "View".
// The model faces +x like 3293 (its ink marks sat on the +x side) and keeps the 2x3 footprint; the object's model list
// (opcode 5) is pointed at the new model in every slot of id 44930, nothing else on the object changes.
// rebuild / rebuild-verify: the board is already in the cache (its own sprites 24532-24535, textures 210-213, model
// 60132): overwrite them in place with bumped revisions so clients download the new version.
// Variant "scoreboard" (2026-10-15): the same craftsmanship as the Zelus Scoreboard board, object 72001 (a clone of
// 44930's record: name "Zelus Scoreboard", only "View", model 60133), sprites 24536-24539, textures 214-217, royal-blue
// banners, gold star emblems and a face for the PvP / PvM / Skilling / Misc scoreboard (interface 1163).
// Variant "rivals" (2026-10-15): the Division Rivals Leaderboard, object 62392 (a clone of 44930's record: name
// "Rivals Leaderboard", only "View", model 60137), sprites 24543-24546, textures 221-224, blood-red banners with gold
// crossed-blade emblems, a crimson crest plate, and an obsidian / crimson face: DIVISION RIVALS title, crossed blades,
// a 1-5 standings table with medals and the division ladder (Elite, D1-D10). Static: the live table is behind "View".
// Usage: verify|apply|rebuild-verify|rebuild <cachePath> <previewDir> [champ|scoreboard|rivals]
public class BuildChampionshipBoard {
    static int OBJECT_ID = 44930, MODEL_ID = 60132, FIRST_SPRITE = 24532, TEX_BASE = 210;
    /**
     * Below 65536: the zone packet that spawns objects (rsprot LocAddChangeV2) carries a 16-bit id, so the first pick,
     * 72001, reached the client wrapped to 6465 and the board was invisible (2026-10-15). 72001 is removed on rebuild.
     */
    static final int SCOREBOARD_OBJECT = 62391, OLD_SCOREBOARD_OBJECT = 72001;
    static boolean SCOREBOARD, RIVALS;
    static final int RIVALS_OBJECT = 62392;
    /** The cloned object's id / name (the scoreboard and rivals variants). */
    static int CLONE_ID = SCOREBOARD_OBJECT;
    static String CLONE_NAME = "Zelus Scoreboard";

    // ---- packed 16-bit HSL ----
    static short hsl(int h, int s, int l) {
        return (short) (h << 10 | s << 7 | l);
    }

    static final short CHARCOAL = hsl(6, 1, 16), CHARCOAL_D = hsl(6, 1, 10), CHARCOAL_L = hsl(6, 1, 22);
    static final short IRON = hsl(0, 0, 34), IRON_D = hsl(0, 0, 20);
    static final short GOLD = hsl(7, 6, 62), GOLD_D = hsl(7, 6, 44), GOLD_L = hsl(8, 5, 80);
    static final short STEEL = hsl(38, 1, 70), STEEL_D = hsl(40, 2, 30);
    static final short CRIMSON = hsl(0, 6, 34), CRIMSON_D = hsl(0, 6, 22);
    static final short ROYAL = hsl(42, 6, 30), ROYAL_D = hsl(42, 6, 18);
    static final short BLOOD = hsl(0, 7, 26), BLOOD_D = hsl(0, 7, 14);
    static short BANNER = CRIMSON, PLATE = STEEL_D;

    // ---- model builder ----
    static final List<int[]> verts = new ArrayList<>();
    static final List<int[]> faces = new ArrayList<>();   // a, b, c, colour, renderType, texture (-1), texCoord (-1), priority
    /**
     * Render priority for the faces added next. The client sorts a model's faces by depth only WITHIN a priority, and
     * draws higher priorities later, so every decoration that sits on a bigger surface (bands, rivets, trims, crest and
     * banner layers) gets a higher priority than that surface; otherwise the long post / beam faces sort in front of
     * them. Decorations are mirrored onto the back as well, so they also read correctly from behind.
     */
    static int prio = 0;
    static final List<int[]> texTris = new ArrayList<>(); // p, m, n

    static int v(int x, int y, int z) {
        verts.add(new int[]{x, y, z});
        return verts.size() - 1;
    }

    /**
     * The side the next triangles must be visible from. The client back-face culls: a triangle is drawn only from the
     * side its (b - a) x (c - a) normal points to (cache models: 94 of 102 faces of 2491 point outward). Triangles are
     * flipped to face `facing`; 2026-10-15 the first build ignored this, so some faces (the crest's swords) vanished.
     */
    static double[] facing = {1, 0, 0};

    static void tri(int a, int b, int c, short colour) {
        faces.add(oriented(a, b, c, new int[]{a, b, c, colour, 1, -1, -1, prio}));
    }

    static int[] oriented(int a, int b, int c, int[] face) {
        int[] A = verts.get(a), B = verts.get(b), C = verts.get(c);
        double ux = B[0] - A[0], uy = B[1] - A[1], uz = B[2] - A[2], vx = C[0] - A[0], vy = C[1] - A[1], vz = C[2] - A[2];
        double nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
        if (nx * facing[0] + ny * facing[1] + nz * facing[2] < 0) {
            face[1] = c;
            face[2] = b;
        }
        return face;
    }

    static void quad(int a, int b, int c, int d, short colour) {
        tri(a, b, c, colour);
        tri(a, c, d, colour);
    }

    /** Axis-aligned box: x0..x1, y0..y1 (y up is negative), z0..z1. */
    static void box(int x0, int x1, int y0, int y1, int z0, int z1, short side, short front) {
        int a = v(x0, y0, z0), b = v(x1, y0, z0), c = v(x1, y0, z1), d = v(x0, y0, z1);
        int e = v(x0, y1, z0), f = v(x1, y1, z0), g = v(x1, y1, z1), h = v(x0, y1, z1);
        double[] keep = facing;
        int sy = y1 > y0 ? 1 : -1;
        facing = new double[]{0, -sy, 0};
        quad(a, b, c, d, side);   // y0 plane
        facing = new double[]{0, sy, 0};
        quad(e, h, g, f, side);   // y1 plane
        facing = new double[]{0, 0, z1 > z0 ? -1 : 1};
        quad(a, e, f, b, side);   // z0
        facing = new double[]{0, 0, z1 > z0 ? 1 : -1};
        quad(d, c, g, h, side);   // z1
        facing = new double[]{x1 > x0 ? 1 : -1, 0, 0};
        quad(b, f, g, c, front);  // x1 (front, +x)
        facing = new double[]{x1 > x0 ? -1 : 1, 0, 0};
        quad(a, d, h, e, side);   // x0 (back)
        facing = keep;
    }

    /** A box cut into pieces about `step` long along y (axis 1) or z (axis 2), so its faces are short. */
    static void segBox(int x0, int x1, int y0, int y1, int z0, int z1, short side, short front, int axis, int step) {
        int from = axis == 1 ? y0 : z0, to = axis == 1 ? y1 : z1, n = Math.max(1, Math.abs(to - from) / step);
        for (int k = 0; k < n; k++) {
            int a = from + (to - from) * k / n, b = from + (to - from) * (k + 1) / n;
            if (axis == 1) box(x0, x1, a, b, z0, z1, side, front);
            else box(x0, x1, y0, y1, a, b, side, front);
        }
    }

    static void box(int x0, int x1, int y0, int y1, int z0, int z1, short colour) {
        box(x0, x1, y0, y1, z0, z1, colour, colour);
    }

    /** Flat polygon in the plane x = const, points as {z, y}, fan-triangulated from the first point (convex). */
    static void poly(int x, int[][] zy, short colour) {
        // seen from +x when in front of the centre plane, from -x behind it, and from both sides on it
        double[] keep = facing;
        for (int dir : x > 0 ? new int[]{1} : x < 0 ? new int[]{-1} : new int[]{1, -1}) {
            facing = new double[]{dir, 0, 0};
            int[] ids = new int[zy.length];
            for (int i = 0; i < zy.length; i++)
                ids[i] = v(x, zy[i][1], zy[i][0]);
            for (int i = 1; i + 1 < zy.length; i++)
                tri(ids[0], ids[i], ids[i + 1], colour);
        }
        facing = keep;
    }

    /** A straight bar of width w from (z0,y0) to (z1,y1) in the plane x. */
    static void bar(int x, double z0, double y0, double z1, double y1, double w, short colour) {
        double dz = z1 - z0, dy = y1 - y0, len = Math.hypot(dz, dy);
        double nz = -dy / len * w / 2, ny = dz / len * w / 2;
        poly(x, new int[][]{
                {(int) Math.round(z0 + nz), (int) Math.round(y0 + ny)}, {(int) Math.round(z1 + nz), (int) Math.round(y1 + ny)},
                {(int) Math.round(z1 - nz), (int) Math.round(y1 - ny)}, {(int) Math.round(z0 - nz), (int) Math.round(y0 - ny)}}, colour);
    }

    /**
     * Textured flat quad in the plane x: corners TL, TR, BR, BL as {z, y} as seen by the viewer at +x, for whom -z is on
     * the left. Its triangles are wound to face +x. (2026-10-15: the first build left the face quads wound toward -x, so
     * players saw them from behind and every letter was mirrored; build 2 fixed the winding but also swapped the
     * corners, mirroring it again. Corners as here + winding toward +x is the in-game-verified combination.)
     */
    static void texQuad(int x, int[] tl, int[] tr, int[] br, int[] bl, int texture) {
        int p = v(x, tl[1], tl[0]), m = v(x, tr[1], tr[0]), c = v(x, br[1], br[0]), n = v(x, bl[1], bl[0]);
        texTris.add(new int[]{p, m, n});
        int coord = texTris.size() - 1;
        double[] keep = facing;
        facing = new double[]{1, 0, 0};
        faces.add(oriented(p, m, c, new int[]{p, m, c, 0, 0, texture, coord, prio}));
        faces.add(oriented(p, c, n, new int[]{p, c, n, 0, 0, texture, coord, prio}));
        facing = keep;
    }

    static void gemCup(int x, int cz, int top, int scale, short colour, short dark) {
        // bowl, handles, stem and foot of a trophy cup, centred on z = cz, top edge at y = top; scale 10 = full size
        int s = scale;
        poly(x, new int[][]{{cz - 2 * s, top}, {cz + 2 * s, top}, {cz + s, top + 3 * s}, {cz - s, top + 3 * s}}, colour);
        bar(x, cz - 2 * s, top + s / 2.0, cz - 3 * s, top + s, s * 0.6, colour);
        bar(x, cz - 3 * s, top + s, cz - 1.5 * s, top + 2.2 * s, s * 0.6, colour);
        bar(x, cz + 2 * s, top + s / 2.0, cz + 3 * s, top + s, s * 0.6, colour);
        bar(x, cz + 3 * s, top + s, cz + 1.5 * s, top + 2.2 * s, s * 0.6, colour);
        poly(x, new int[][]{{cz - s / 3, top + 3 * s}, {cz + s / 3, top + 3 * s}, {cz + s / 3, top + 4 * s}, {cz - s / 3, top + 4 * s}}, dark);
        poly(x, new int[][]{{cz - 3 * s / 2, top + 4 * s}, {cz + 3 * s / 2, top + 4 * s}, {cz + 3 * s / 2, top + 4 * s + s * 3 / 4}, {cz - 3 * s / 2, top + 4 * s + s * 3 / 4}}, colour);
    }

    /** A decoration on both faces of a surface: the same shape at x = +front and x = -front. */
    interface Layer {
        void at(int x);
    }

    static void bothSides(int front, Layer layer) {
        layer.at(front);
        layer.at(-front);
    }

    /** The championship cup, or on the scoreboard a five-point star of the same size. */
    static void emblem(int x, int cz, int top, int s) {
        if (!SCOREBOARD) {
            gemCup(x, cz, top, s, GOLD, GOLD_D);
            return;
        }
        if (RIVALS) { // two crossed gold blades with a gold boss where they cross
            double cy = top + 2.4 * s, r = 2.6 * s;
            bar(x, cz - r, cy - r, cz + r * 0.8, cy + r * 0.8, 0.9 * s, GOLD_L);
            bar(x, cz + r, cy - r, cz - r * 0.8, cy + r * 0.8, 0.9 * s, GOLD);
            bar(x, cz - r * 0.55, cy + r * 0.25, cz - r * 0.25, cy + r * 0.55, 1.2 * s, GOLD_D);
            bar(x, cz + r * 0.55, cy + r * 0.25, cz + r * 0.25, cy + r * 0.55, 1.2 * s, GOLD_D);
            return;
        }
        double cy = top + 2.4 * s, r = 2.6 * s, ri = r * 0.42;
        for (int i = 0; i < 5; i++) {
            double a0 = -Math.PI / 2 + i * 2 * Math.PI / 5, a1 = a0 + Math.PI / 5, am = a0 - Math.PI / 5;
            int[] tip = {(int) Math.round(cz + r * Math.cos(a0)), (int) Math.round(cy + r * Math.sin(a0))};
            int[] right = {(int) Math.round(cz + ri * Math.cos(a1)), (int) Math.round(cy + ri * Math.sin(a1))};
            int[] left = {(int) Math.round(cz + ri * Math.cos(am)), (int) Math.round(cy + ri * Math.sin(am))};
            poly(x, new int[][]{tip, right, {cz, (int) Math.round(cy)}, left}, i % 2 == 0 ? GOLD : GOLD_L);
        }
    }

    static void buildModel(int[] tex) {
        int top = -384, bottom = -166, mid = -275;
        // ---- priority 0: the main surfaces ----
        prio = 0;
        for (int side : new int[]{-1, 1}) {
            int c = side * 162;
            segBox(-10, 10, -20, -430, c - 10, c + 10, CHARCOAL, CHARCOAL_L, 1, 40);     // post, in 40-unit pieces
            box(-13, 13, -430, -442, c - 13, c + 13, GOLD_D, GOLD);                      // cap
            box(-6, 6, -442, -458, c - 6, c + 6, GOLD_D, GOLD);                          // finial
            box(-3, 3, -458, -470, c - 3, c + 3, GOLD, GOLD_L);
        }
        // back of the board: congruent with the face's 2 x 2 split so it never sorts in front of it
        int[] zs = {-140, 0, 140}, ys = {top, mid, bottom};
        for (int i = 0; i < 2; i++)
            for (int j = 0; j < 2; j++) {
                int a = v(-4, ys[i], zs[j]), b = v(-4, ys[i], zs[j + 1]), c = v(-4, ys[i + 1], zs[j + 1]), d = v(-4, ys[i + 1], zs[j]);
                facing = new double[]{-1, 0, 0};
                quad(a, d, c, b, CHARCOAL_D);
                facing = new double[]{1, 0, 0};
            }
        // frame: beams and stiles are bands of gold / charcoal / gold laid side by side (flush, never overlapping),
        // so the gold trim can't sort behind the wood the way a strip laid on top of it did
        segBox(-6, 10, -404, -400, -152, 152, GOLD_D, GOLD, 2, 38);                // top beam
        segBox(-6, 10, -400, -388, -152, 152, CHARCOAL, CHARCOAL_L, 2, 38);
        segBox(-6, 10, -388, top, -140, 140, GOLD_D, GOLD, 2, 38);
        segBox(-6, 10, bottom, -162, -140, 140, GOLD_D, GOLD, 2, 38);              // bottom beam
        segBox(-6, 10, -162, -150, -152, 152, CHARCOAL, CHARCOAL_L, 2, 38);
        segBox(-6, 10, -150, -146, -152, 152, GOLD_D, GOLD, 2, 38);
        for (int side : new int[]{-1, 1}) {                                        // stiles
            int in = side * 140, edge = side * 143, out = side * 152;
            segBox(-6, 10, -388, -162, Math.min(in, edge), Math.max(in, edge), GOLD_D, GOLD, 1, 38);
            segBox(-6, 10, -388, -162, Math.min(edge, out), Math.max(edge, out), CHARCOAL, CHARCOAL_L, 1, 38);
        }
        // iron feet / bands round the posts, gold rivets front and back, gold trim on both faces of the frame: plain
        // depth-sorted geometry (priority 0) against the short post / beam pieces, so nothing shows through
        for (int side : new int[]{-1, 1}) {
            int c = side * 162;
            box(-16, 16, 0, -24, c - 16, c + 16, IRON_D, IRON);
            for (int band : new int[]{-110, -330})
                box(-12, 12, band, band - 8, c - 12, c + 12, IRON_D, IRON);
            for (int y : new int[]{-60, -170, -250}) {
                box(10, 13, y, y - 5, c - 3, c + 2, GOLD_D, GOLD);
                box(-13, -10, y, y - 5, c - 3, c + 2, GOLD_D, GOLD);
            }
        }

        // the painted parchment face: 2 x 2 textured quads
        for (int i = 0; i < 2; i++)
            for (int j = 0; j < 2; j++)
                texQuad(4, new int[]{zs[j], ys[i]}, new int[]{zs[j + 1], ys[i]}, new int[]{zs[j + 1], ys[i + 1]},
                        new int[]{zs[j], ys[i + 1]}, tex[i * 2 + j]);
        // crest base (one gold shield, seen from both sides) and the banner cloths beside the posts
        int[][] rim = {{-64, -508}, {64, -508}, {64, -450}, {0, -392}, {-64, -450}};
        poly(0, rim, GOLD);
        for (int side : new int[]{-1, 1}) {
            int c = side * 196, l = c - 20, r = c + 20, bt = -404, bb = -262, tip = -234;
            box(-3, 3, bt - 8, bt - 2, side > 0 ? 172 : -220, side > 0 ? 220 : -172, GOLD_D, GOLD);   // arm off the post
            poly(0, new int[][]{{l, bt}, {r, bt}, {r, bb}, {l, bb}}, BANNER);
            poly(0, new int[][]{{l, bb}, {c, bb}, {l, tip}}, BANNER);
            poly(0, new int[][]{{c, bb}, {r, bb}, {r, tip}}, BANNER);
        }

        // ---- priority 1: layers laid flat on the crest and the banner cloths (mirrored on the back) ----
        prio = 1;
        int[][] plate = {{-56, -500}, {56, -500}, {56, -452}, {0, -402}, {-56, -452}};
        bothSides(1, x -> poly(x, plate, PLATE));
        for (int side : new int[]{-1, 1}) {
            int c = side * 196, l = c - 20, r = c + 20, bt = -404, bb = -262;
            bothSides(1, x -> {
                bar(x, l + 3, bt, l + 3, bb, 4, GOLD);
                bar(x, r - 3, bt, r - 3, bb, 4, GOLD);
                bar(x, l, bt + 6, r, bt + 6, 4, GOLD);
                bar(x, l + 2, bb - 4, r - 2, bb - 4, 3, GOLD_D);
            });
        }

        // ---- priority 2: crossed swords on the crest, cup emblems on the banners ----
        prio = 2;
        bothSides(2, x -> {
            bar(x, -42, -490, 38, -418, 7, STEEL);
            bar(x, 42, -490, -38, -418, 7, STEEL);
        });
        for (int side : new int[]{-1, 1}) {
            int c = side * 196;
            bothSides(2, x -> emblem(x, c, -372, 5));
        }

        // ---- priority 3: sword guards / grips and the championship cup ----
        prio = 3;
        bothSides(3, x -> {
            bar(x, -50, -484, -32, -500, 5, GOLD);
            bar(x, 50, -484, 32, -500, 5, GOLD);
            bar(x, -48, -496, -40, -488, 4, GOLD_D);
            bar(x, 48, -496, 40, -488, 4, GOLD_D);
            emblem(x, 0, -486, 8);
        });
        prio = 0;
    }

    static ModelDefinition toDefinition() {
        ModelDefinition d = new ModelDefinition();
        d.id = MODEL_ID;
        d.vertexCount = verts.size();
        d.vertexX = new int[d.vertexCount];
        d.vertexY = new int[d.vertexCount];
        d.vertexZ = new int[d.vertexCount];
        for (int i = 0; i < d.vertexCount; i++) {
            d.vertexX[i] = verts.get(i)[0];
            d.vertexY[i] = verts.get(i)[1];
            d.vertexZ[i] = verts.get(i)[2];
        }
        d.faceCount = faces.size();
        d.faceIndices1 = new int[d.faceCount];
        d.faceIndices2 = new int[d.faceCount];
        d.faceIndices3 = new int[d.faceCount];
        d.faceColors = new short[d.faceCount];
        d.faceRenderTypes = new byte[d.faceCount];
        d.faceTextures = new short[d.faceCount];
        d.textureCoords = new byte[d.faceCount];
        d.faceRenderPriorities = new byte[d.faceCount];
        for (int i = 0; i < d.faceCount; i++) {
            int[] f = faces.get(i);
            d.faceIndices1[i] = f[0];
            d.faceIndices2[i] = f[1];
            d.faceIndices3[i] = f[2];
            d.faceColors[i] = (short) f[3];
            d.faceRenderTypes[i] = (byte) f[4];
            d.faceTextures[i] = (short) f[5];
            d.textureCoords[i] = (byte) f[6];
            d.faceRenderPriorities[i] = (byte) f[7];
        }
        d.numTextureFaces = texTris.size();
        d.textureRenderTypes = new byte[d.numTextureFaces];
        d.texIndices1 = new short[d.numTextureFaces];
        d.texIndices2 = new short[d.numTextureFaces];
        d.texIndices3 = new short[d.numTextureFaces];
        for (int i = 0; i < d.numTextureFaces; i++) {
            d.texIndices1[i] = (short) texTris.get(i)[0];
            d.texIndices2[i] = (short) texTris.get(i)[1];
            d.texIndices3[i] = (short) texTris.get(i)[2];
        }
        d.priority = 0;
        return d;
    }

    // ---- the painted face ----
    static final int PW = 1120, PH = 872; // 4x the face's 280 x 218 world units

    static BufferedImage paintFace() {
        BufferedImage img = new BufferedImage(PW, PH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        // worn parchment: warm cream centre, darker gold edges, stains and fibres
        g.setPaint(new RadialGradientPaint(new Point2D.Float(PW / 2f, PH / 2f), PW * 0.62f, new float[]{0f, 0.7f, 1f},
                new Color[]{new Color(0xE8D3A0), new Color(0xD2B475), new Color(0x9C7A3E)}));
        g.fillRect(0, 0, PW, PH);
        Random rnd = new Random(44930);
        for (int i = 0; i < 26; i++) {
            int r = 30 + rnd.nextInt(90);
            g.setColor(new Color(120, 86, 40, 14 + rnd.nextInt(16)));
            g.fillOval(rnd.nextInt(PW), rnd.nextInt(PH), r, (int) (r * (0.6 + rnd.nextDouble() * 0.6)));
        }
        for (int i = 0; i < 2600; i++) {
            g.setColor(new Color(110, 80, 40, 18 + rnd.nextInt(30)));
            int x = rnd.nextInt(PW), y = rnd.nextInt(PH);
            g.drawLine(x, y, x + rnd.nextInt(9) - 4, y + rnd.nextInt(3) - 1);
        }

        // title plate
        int plateY = 22, plateH = 170;
        g.setColor(new Color(0x1d1712));
        g.fillRoundRect(30, plateY, PW - 60, plateH, 26, 26);
        g.setStroke(new BasicStroke(8f));
        g.setColor(new Color(0xC99B3A));
        g.drawRoundRect(30, plateY, PW - 60, plateH, 26, 26);
        g.setStroke(new BasicStroke(3f));
        g.setColor(new Color(0x7A5A22));
        g.drawRoundRect(44, plateY + 14, PW - 88, plateH - 28, 18, 18);
        for (int x : new int[]{56, PW - 56})
            for (int y : new int[]{plateY + 26, plateY + plateH - 26})
                rivet(g, x, y, 9);
        Font title = new Font("Georgia", Font.BOLD, 112);
        drawTitle(g, "CHAMPIONSHIPS", title, PW / 2, plateY + plateH / 2 + 40);

        // two event columns
        int colY = 214, colH = 196, gap = 24, colW = (PW - 60 - gap) / 2;
        panel(g, 30, colY, colW, colH);
        panel(g, 30 + colW + gap, colY, colW, colH);
        Font head = new Font("Georgia", Font.BOLD, 42), body = new Font("Georgia", Font.BOLD, 40);
        crossedSwords(g, 30 + 46, colY + 50, 26);
        shadowText(g, "CURRENT EVENT", head, new Color(0xE0461C), 30 + 86, colY + 66);
        shadowText(g, "Live bracket", body, new Color(0x3a2a14), 30 + 40, colY + 128);
        shadowText(g, "View to spectate", body, new Color(0x3a2a14), 30 + 40, colY + 172);
        int rx = 30 + colW + gap;
        trophy(g, rx + 48, colY + 48, 26, new Color(0xE8B83A), new Color(0x8A6418));
        shadowText(g, "UPCOMING", head, new Color(0xD99A12), rx + 92, colY + 66);
        shadowText(g, "TOURNAMENT", body, new Color(0xB57E0E), rx + 40, colY + 124);
        shadowText(g, "Every 30 minutes", body, new Color(0x3a2a14), rx + 40, colY + 172);

        // top champions table
        int tY = 432;
        g.setColor(new Color(0x2a2016));
        g.fillRoundRect(30, tY, PW - 60, 64, 14, 14);
        g.setColor(new Color(0xC99B3A));
        g.setStroke(new BasicStroke(4f));
        g.drawRoundRect(30, tY, PW - 60, 64, 14, 14);
        star(g, 80, tY + 32, 20, new Color(0xF2C440));
        star(g, PW - 80, tY + 32, 20, new Color(0xF2C440));
        Font th = new Font("Georgia", Font.BOLD, 46);
        centred(g, "TOP CHAMPIONS", th, new Color(0xF2C440), PW / 2, tY + 48);
        int rowY = tY + 76, rowH = 70;
        Color[] medals = {new Color(0xE8B83A), new Color(0xC8CCD4), new Color(0xC07A3A), new Color(0x8A6A40), new Color(0x8A6A40)};
        Font rank = new Font("Georgia", Font.BOLD, 40), name = new Font("Georgia", Font.BOLD, 38);
        for (int i = 0; i < 5; i++) {
            int y = rowY + i * rowH;
            g.setColor(i % 2 == 0 ? new Color(0xC9A86A) : new Color(0xDCC08A));
            g.fillRect(36, y, PW - 72, rowH - 4);
            g.setColor(new Color(0x8A6A3A));
            g.setStroke(new BasicStroke(2f));
            g.drawRect(36, y, PW - 72, rowH - 4);
            medal(g, 84, y + rowH / 2 - 2, 26, medals[i], String.valueOf(i + 1), rank);
            if (i < 3)
                trophy(g, 150, y + rowH / 2 - 2, 20, medals[i], medals[i].darker().darker());
            else
                crossedSwords(g, 150, y + rowH / 2 - 2, 18);
            // engraved name line and score slot (the live standings are in the hub)
            g.setColor(new Color(0x6a4e28));
            g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[]{2f, 10f}, 0f));
            g.drawLine(196, y + rowH / 2 + 10, PW - 260, y + rowH / 2 + 10);
            star(g, PW - 220, y + rowH / 2 - 2, 14, new Color(0xB07A18));
            shadowText(g, "pts", name, new Color(0x3a2a14), PW - 190, y + rowH / 2 + 12);
        }
        g.dispose();
        return img;
    }

    /** The Zelus Scoreboard face: title plate, the four categories, and the daily / weekly Score Point (SbP) rewards. */
    static BufferedImage paintScoreboardFace() {
        BufferedImage img = new BufferedImage(PW, PH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setPaint(new RadialGradientPaint(new Point2D.Float(PW / 2f, PH / 2f), PW * 0.62f, new float[]{0f, 0.7f, 1f},
                new Color[]{new Color(0xE8D3A0), new Color(0xD2B475), new Color(0x9C7A3E)}));
        g.fillRect(0, 0, PW, PH);
        Random rnd = new Random(72001);
        for (int i = 0; i < 26; i++) {
            int r = 30 + rnd.nextInt(90);
            g.setColor(new Color(120, 86, 40, 14 + rnd.nextInt(16)));
            g.fillOval(rnd.nextInt(PW), rnd.nextInt(PH), r, (int) (r * (0.6 + rnd.nextDouble() * 0.6)));
        }
        for (int i = 0; i < 2600; i++) {
            g.setColor(new Color(110, 80, 40, 18 + rnd.nextInt(30)));
            int x = rnd.nextInt(PW), y = rnd.nextInt(PH);
            g.drawLine(x, y, x + rnd.nextInt(9) - 4, y + rnd.nextInt(3) - 1);
        }

        // title plate
        int plateY = 22, plateH = 170;
        g.setColor(new Color(0x1d1712));
        g.fillRoundRect(30, plateY, PW - 60, plateH, 26, 26);
        g.setStroke(new BasicStroke(8f));
        g.setColor(new Color(0xC99B3A));
        g.drawRoundRect(30, plateY, PW - 60, plateH, 26, 26);
        g.setStroke(new BasicStroke(3f));
        g.setColor(new Color(0x7A5A22));
        g.drawRoundRect(44, plateY + 14, PW - 88, plateH - 28, 18, 18);
        for (int x : new int[]{56, PW - 56})
            for (int y : new int[]{plateY + 26, plateY + plateH - 26})
                rivet(g, x, y, 9);
        drawTitle(g, "SCOREBOARD", new Font("Georgia", Font.BOLD, 120), PW / 2, plateY + plateH / 2 + 42);

        // the four categories, 2 x 2
        String[][] cats = {
                {"PVP", "Kills  -  Singles  -  Multi"},
                {"PVM", "Bosses  -  Raids  -  Loot"},
                {"SKILLING", "Experience  -  Resources"},
                {"MISC", "Votes  -  Chests  -  Wins"}};
        Color[] heads = {new Color(0xD0381C), new Color(0x7A2CB8), new Color(0x2E8A2E), new Color(0xC08A10)};
        int gap = 20, tileW = (PW - 60 - gap) / 2, tileH = 160, top = 212;
        Font head = new Font("Georgia", Font.BOLD, 52), body = new Font("Georgia", Font.BOLD, 29);
        for (int i = 0; i < 4; i++) {
            int x = 30 + (i % 2) * (tileW + gap), y = top + (i / 2) * (tileH + gap);
            panel(g, x, y, tileW, tileH);
            int ix = x + 58, iy = y + 78;
            switch (i) {
                case 0 -> crossedSwords(g, ix, iy, 34);
                case 1 -> skull(g, ix, iy, 34);
                case 2 -> pickaxe(g, ix, iy, 34);
                default -> coins(g, ix, iy, 30);
            }
            shadowText(g, cats[i][0], head, heads[i], x + 112, y + 70);
            shadowText(g, cats[i][1], body, new Color(0x3a2a14), x + 112, y + 124);
        }

        // rewards strip
        int rY = top + 2 * (tileH + gap) + 6;
        g.setColor(new Color(0x2a2016));
        g.fillRoundRect(30, rY, PW - 60, 64, 14, 14);
        g.setColor(new Color(0xC99B3A));
        g.setStroke(new BasicStroke(4f));
        g.drawRoundRect(30, rY, PW - 60, 64, 14, 14);
        star(g, 80, rY + 32, 20, new Color(0xF2C440));
        star(g, PW - 80, rY + 32, 20, new Color(0xF2C440));
        centred(g, "DAILY & WEEKLY REWARDS", new Font("Georgia", Font.BOLD, 44), new Color(0xF2C440), PW / 2, rY + 47);
        Font line = new Font("Georgia", Font.BOLD, 38);
        Color[] medals = {new Color(0xE8B83A), new Color(0xC8CCD4), new Color(0xC07A3A)};
        int ly = rY + 82;
        for (int row = 0; row < 2; row++) {
            int y = ly + row * 64;
            g.setColor(row == 0 ? new Color(0xC9A86A) : new Color(0xDCC08A));
            g.fillRect(36, y, PW - 72, 56);
            g.setColor(new Color(0x8A6A3A));
            g.setStroke(new BasicStroke(2f));
            g.drawRect(36, y, PW - 72, 56);
            shadowText(g, row == 0 ? "Daily" : "Weekly", line, new Color(0x3a2a14), 60, y + 40);
            int[] pkp = row == 0 ? new int[]{50, 30, 15} : new int[]{250, 150, 75};
            for (int m = 0; m < 3; m++) {
                int mx = 300 + m * 250;
                medal(g, mx, y + 28, 22, medals[m], String.valueOf(m + 1), new Font("Georgia", Font.BOLD, 30));
                shadowText(g, pkp[m] + " SbP", line, new Color(0x1d5a1d), mx + 34, y + 40);
            }
        }
        g.dispose();
        return img;
    }

    static void skull(Graphics2D g, int cx, int cy, int s) {
        g.setColor(new Color(0x5a4a3a));
        g.fillOval(cx - s, cy - s - 4, 2 * s, (int) (1.8 * s));
        g.fillRoundRect(cx - s * 2 / 3, cy + s / 2 - 4, s * 4 / 3, s / 2 + 6, 8, 8);
        g.setColor(new Color(0xEDE4D0));
        g.fillOval(cx - s + 3, cy - s - 1, 2 * s - 6, (int) (1.8 * s) - 6);
        g.fillRoundRect(cx - s * 2 / 3 + 3, cy + s / 2 - 1, s * 4 / 3 - 6, s / 2, 6, 6);
        g.setColor(new Color(0x2a1a08));
        g.fillOval(cx - s / 2 - 4, cy - s / 3, s / 2 + 2, s / 2);
        g.fillOval(cx + 2, cy - s / 3, s / 2 + 2, s / 2);
        g.fillPolygon(new int[]{cx, cx - 5, cx + 5}, new int[]{cy + s / 4, cy + s / 2, cy + s / 2}, 3);
        for (int t = -2; t <= 2; t++)
            g.drawLine(cx + t * 6, cy + s / 2 + 2, cx + t * 6, cy + s - 2);
    }

    static void pickaxe(Graphics2D g, int cx, int cy, int s) {
        AffineTransform t = g.getTransform();
        g.translate(cx, cy);
        g.rotate(-Math.PI / 4);
        g.setColor(new Color(0x6a4420));
        g.fillRect(-s / 10, -s, s / 5, 2 * s);
        g.setColor(new Color(0x3a2410));
        g.drawRect(-s / 10, -s, s / 5, 2 * s);
        g.setStroke(new BasicStroke(s * 0.22f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(0x9aa4ae));
        g.drawArc(-s, -s - s / 3, 2 * s, s, 20, 140);
        g.setStroke(new BasicStroke(2f));
        g.setColor(new Color(0x4a525a));
        g.drawArc(-s, -s - s / 3, 2 * s, s, 20, 140);
        g.setTransform(t);
    }

    static void coins(Graphics2D g, int cx, int cy, int s) {
        for (int i = 2; i >= 0; i--) {
            int y = cy + s / 2 - i * s / 3;
            g.setColor(new Color(0x8A6418));
            g.fillOval(cx - s, y - s / 3, 2 * s, s * 2 / 3 + 6);
            g.setColor(new Color(0xE8B83A));
            g.fillOval(cx - s, y - s / 3, 2 * s, s * 2 / 3);
            g.setColor(new Color(0xF6D76B));
            g.drawOval(cx - s + 6, y - s / 3 + 4, 2 * s - 12, s * 2 / 3 - 8);
        }
    }

    /** Division Rivals: obsidian stone with crimson velvet panels, gold trim; text drawn left-to-right (not mirrored). */
    static BufferedImage paintRivalsFace() {
        BufferedImage img = new BufferedImage(PW, PH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setPaint(new RadialGradientPaint(new Point2D.Float(PW / 2f, PH * 0.45f), PW * 0.7f, new float[]{0f, 0.65f, 1f},
                new Color[]{new Color(0x2a2224), new Color(0x17121a), new Color(0x0a080a)}));
        g.fillRect(0, 0, PW, PH);
        Random rnd = new Random(62392);
        for (int i = 0; i < 1800; i++) { // obsidian grain
            g.setColor(new Color(90, 70, 80, 10 + rnd.nextInt(22)));
            int x = rnd.nextInt(PW), y = rnd.nextInt(PH);
            g.drawLine(x, y, x + rnd.nextInt(13) - 6, y + rnd.nextInt(5) - 2);
        }
        g.setStroke(new BasicStroke(10f));
        g.setColor(new Color(0xC99B3A));
        g.drawRect(12, 12, PW - 24, PH - 24);
        g.setStroke(new BasicStroke(3f));
        g.setColor(new Color(0x6a1018));
        g.drawRect(28, 28, PW - 56, PH - 56);

        // title plate: crimson velvet, crossed blades either side
        int plateY = 40, plateH = 190;
        g.setPaint(new LinearGradientPaint(0, plateY, 0, plateY + plateH, new float[]{0f, 0.5f, 1f},
                new Color[]{new Color(0x5a0c14), new Color(0x7a1420), new Color(0x3a060c)}));
        g.fillRoundRect(44, plateY, PW - 88, plateH, 26, 26);
        g.setStroke(new BasicStroke(8f));
        g.setColor(new Color(0xC99B3A));
        g.drawRoundRect(44, plateY, PW - 88, plateH, 26, 26);
        for (int x : new int[]{70, PW - 70})
            for (int y : new int[]{plateY + 26, plateY + plateH - 26})
                rivet(g, x, y, 9);
        crossedSwords(g, 150, plateY + 92, 52);
        crossedSwords(g, PW - 150, plateY + 92, 52);
        centred(g, "DIVISION RIVALS", new Font("Georgia", Font.BOLD, 92), new Color(0xF2C440), PW / 2, plateY + 112);
        centred(g, "LEADERBOARD", new Font("Georgia", Font.BOLD, 54), new Color(0xE8E0D0), PW / 2, plateY + 170);

        // top 5 table
        int top = 262, rowH = 74;
        Font hf = new Font("Georgia", Font.BOLD, 34), rf = new Font("Georgia", Font.BOLD, 40);
        g.setColor(new Color(0x2a0a10));
        g.fillRect(52, top, PW - 104, 50);
        g.setColor(new Color(0xC99B3A));
        g.setStroke(new BasicStroke(3f));
        g.drawRect(52, top, PW - 104, 50 + 5 * rowH);
        shadowText(g, "RANK", hf, new Color(0xF2C440), 76, top + 37);
        shadowText(g, "COMBATANT", hf, new Color(0xF2C440), 250, top + 37);
        shadowText(g, "SR", hf, new Color(0xF2C440), PW - 170, top + 37);
        Color[] medals = {new Color(0xE8B83A), new Color(0xC8CCD4), new Color(0xC07A3A), new Color(0x8a5a5a), new Color(0x8a5a5a)};
        for (int r = 0; r < 5; r++) {
            int y = top + 50 + r * rowH;
            g.setColor(r % 2 == 0 ? new Color(0x1c1815) : new Color(0x151210));
            g.fillRect(54, y, PW - 108, rowH);
            medal(g, 120, y + rowH / 2, 26, medals[r], String.valueOf(r + 1), new Font("Georgia", Font.BOLD, 32));
            g.setColor(new Color(0x4a3a30));
            g.fillRoundRect(250, y + 28, 340 - r * 30, 18, 9, 9); // name placeholder bar
            g.setColor(new Color(0x2a7a2a));
            g.fillRoundRect(PW - 190, y + 28, 110, 18, 9, 9);   // rating placeholder bar
        }

        // division ladder strip
        int dy = top + 50 + 5 * rowH + 26;
        g.setColor(new Color(0x1a0c10));
        g.fillRoundRect(44, dy, PW - 88, 92, 16, 16);
        g.setColor(new Color(0xC99B3A));
        g.setStroke(new BasicStroke(4f));
        g.drawRoundRect(44, dy, PW - 88, 92, 16, 16);
        star(g, 90, dy + 46, 22, new Color(0xF2C440));
        star(g, PW - 90, dy + 46, 22, new Color(0xF2C440));
        centred(g, "ELITE  -  D1  -  D5  -  D10", new Font("Georgia", Font.BOLD, 46), new Color(0xE8E0D0), PW / 2, dy + 62);
        g.dispose();
        return img;
    }

    static void drawTitle(Graphics2D g, String text, Font font, int cx, int baseline) {
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();
        int w = fm.stringWidth(text);
        Font fit = font;
        if (w > PW - 130) {
            fit = font.deriveFont(font.getSize2D() * (PW - 130) / w);
            g.setFont(fit);
            w = g.getFontMetrics().stringWidth(text);
        }
        Shape glyphs = fit.createGlyphVector(g.getFontRenderContext(), text).getOutline(cx - w / 2f, baseline);
        g.translate(6, 7);
        g.setColor(new Color(0, 0, 0, 200));
        g.fill(glyphs);
        g.translate(-6, -7);
        Rectangle b = glyphs.getBounds();
        g.setPaint(new LinearGradientPaint(0, b.y, 0, b.y + b.height, new float[]{0f, 0.45f, 1f},
                new Color[]{new Color(0xFFE27A), new Color(0xF0A020), new Color(0xB8230F)}));
        g.fill(glyphs);
        g.setStroke(new BasicStroke(4f));
        g.setColor(new Color(0x4a1a08));
        g.draw(glyphs);
    }

    static void shadowText(Graphics2D g, String text, Font font, Color colour, int x, int y) {
        g.setFont(font);
        g.setColor(new Color(0, 0, 0, 90));
        g.drawString(text, x + 3, y + 3);
        g.setColor(colour);
        g.drawString(text, x, y);
    }

    static void centred(Graphics2D g, String text, Font font, Color colour, int cx, int y) {
        g.setFont(font);
        shadowText(g, text, font, colour, cx - g.getFontMetrics().stringWidth(text) / 2, y);
    }

    static void panel(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(new Color(255, 245, 220, 70));
        g.fillRoundRect(x, y, w, h, 16, 16);
        g.setStroke(new BasicStroke(4f));
        g.setColor(new Color(0x7A5A22));
        g.drawRoundRect(x, y, w, h, 16, 16);
    }

    static void rivet(Graphics2D g, int x, int y, int r) {
        g.setColor(new Color(0x6a4a14));
        g.fillOval(x - r, y - r, 2 * r, 2 * r);
        g.setColor(new Color(0xE8C060));
        g.fillOval(x - r + 2, y - r + 2, 2 * r - 6, 2 * r - 6);
    }

    static void star(Graphics2D g, int cx, int cy, int r, Color c) {
        Path2D p = new Path2D.Double();
        for (int i = 0; i < 10; i++) {
            double a = -Math.PI / 2 + i * Math.PI / 5, rr = i % 2 == 0 ? r : r * 0.45;
            if (i == 0) p.moveTo(cx + rr * Math.cos(a), cy + rr * Math.sin(a));
            else p.lineTo(cx + rr * Math.cos(a), cy + rr * Math.sin(a));
        }
        p.closePath();
        g.setColor(new Color(0, 0, 0, 110));
        g.translate(2, 2);
        g.fill(p);
        g.translate(-2, -2);
        g.setColor(c);
        g.fill(p);
    }

    static void trophy(Graphics2D g, int cx, int cy, int s, Color c, Color dark) {
        g.setStroke(new BasicStroke(s * 0.18f));
        g.setColor(dark);
        g.drawArc(cx - s - s / 3, cy - s + s / 6, s * 2 / 3, s * 2 / 3, 90, 180);
        g.drawArc(cx + s - s / 3, cy - s + s / 6, s * 2 / 3, s * 2 / 3, 270, 180);
        Path2D bowl = new Path2D.Double();
        bowl.moveTo(cx - s, cy - s);
        bowl.lineTo(cx + s, cy - s);
        bowl.quadTo(cx + s, cy + s * 0.2, cx, cy + s * 0.35);
        bowl.quadTo(cx - s, cy + s * 0.2, cx - s, cy - s);
        g.setColor(c);
        g.fill(bowl);
        g.fillRect(cx - s / 6, cy + s / 3, s / 3, s / 2);
        g.fillRoundRect(cx - s * 2 / 3, cy + s * 5 / 6, s * 4 / 3, s / 3, 4, 4);
        g.setColor(dark);
        g.setStroke(new BasicStroke(2f));
        g.draw(bowl);
    }

    static void crossedSwords(Graphics2D g, int cx, int cy, int s) {
        for (int dir : new int[]{-1, 1}) {
            AffineTransform t = g.getTransform();
            g.translate(cx, cy);
            g.rotate(dir * Math.PI / 4);
            g.setColor(new Color(0xB8C0C8));
            g.fillRect(-s / 8, -s, s / 4, (int) (s * 1.5));
            g.setColor(new Color(0x5a6068));
            g.drawRect(-s / 8, -s, s / 4, (int) (s * 1.5));
            g.setColor(new Color(0xC99B3A));
            g.fillRect(-s / 2, s / 2, s, s / 6);
            g.setColor(new Color(0x5a3a18));
            g.fillRect(-s / 10, s / 2 + s / 6, s / 5, s / 3);
            g.setTransform(t);
        }
    }

    static void medal(Graphics2D g, int cx, int cy, int r, Color c, String n, Font font) {
        g.setColor(c.darker().darker());
        g.fillOval(cx - r, cy - r, 2 * r, 2 * r);
        g.setColor(c);
        g.fillOval(cx - r + 4, cy - r + 4, 2 * r - 8, 2 * r - 8);
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();
        g.setColor(new Color(0x2a1a08));
        g.drawString(n, cx - fm.stringWidth(n) / 2, cy + fm.getAscent() / 2 - 4);
    }

    /** Resamples the 4x painting to 256 x 256 and cuts it into four 128 x 128 tiles (TL, TR, BL, BR). */
    static int[][] tiles(BufferedImage face) {
        Image scaled = face.getScaledInstance(256, 256, Image.SCALE_AREA_AVERAGING);
        BufferedImage s = new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = s.createGraphics();
        g.drawImage(scaled, 0, 0, null);
        g.dispose();
        int[][] out = new int[4][];
        for (int t = 0; t < 4; t++) {
            int ox = (t % 2) * 128, oy = (t / 2) * 128;
            out[t] = s.getRGB(ox, oy, 128, 128, null, 0, 128);
        }
        return out;
    }

    /** Median-cut down to at most 254 colours; black stays off index 0 (transparent). */
    static int[] quantize(int[] px) {
        List<int[]> boxes = new ArrayList<>();
        int[] all = new int[px.length];
        for (int i = 0; i < px.length; i++)
            all[i] = px[i] & 0xffffff;
        boxes.add(all);
        while (boxes.size() < 254) {
            int best = -1, bestRange = 0, bestCh = 0;
            for (int b = 0; b < boxes.size(); b++) {
                int[] box = boxes.get(b);
                if (box.length < 2) continue;
                for (int ch = 0; ch < 3; ch++) {
                    int lo = 255, hi = 0;
                    for (int c : box) {
                        int v = c >> (16 - ch * 8) & 0xff;
                        lo = Math.min(lo, v);
                        hi = Math.max(hi, v);
                    }
                    if (hi - lo > bestRange) {
                        bestRange = hi - lo;
                        best = b;
                        bestCh = ch;
                    }
                }
            }
            if (best < 0 || bestRange == 0) break;
            int[] box = boxes.remove(best);
            final int shift = 16 - bestCh * 8;
            Integer[] sorted = new Integer[box.length];
            for (int i = 0; i < box.length; i++) sorted[i] = box[i];
            Arrays.sort(sorted, Comparator.comparingInt(c -> c >> shift & 0xff));
            int half = sorted.length / 2;
            int[] a = new int[half], b2 = new int[sorted.length - half];
            for (int i = 0; i < half; i++) a[i] = sorted[i];
            for (int i = half; i < sorted.length; i++) b2[i - half] = sorted[i];
            boxes.add(a);
            boxes.add(b2);
        }
        int[] palette = new int[boxes.size()];
        for (int b = 0; b < boxes.size(); b++) {
            long r = 0, gg = 0, bl = 0;
            for (int c : boxes.get(b)) {
                r += c >> 16 & 0xff;
                gg += c >> 8 & 0xff;
                bl += c & 0xff;
            }
            int n = Math.max(1, boxes.get(b).length);
            int col = (int) (r / n) << 16 | (int) (gg / n) << 8 | (int) (bl / n);
            palette[b] = col == 0 ? 0x010101 : col;
        }
        int[] out = new int[px.length];
        Map<Integer, Integer> cache = new HashMap<>();
        for (int i = 0; i < px.length; i++) {
            int c = px[i] & 0xffffff;
            out[i] = cache.computeIfAbsent(c, k -> {
                int bestI = 0;
                long bestD = Long.MAX_VALUE;
                for (int p : palette) {
                    long dr = (k >> 16 & 0xff) - (p >> 16 & 0xff), dg = (k >> 8 & 0xff) - (p >> 8 & 0xff), db = (k & 0xff) - (p & 0xff);
                    long d = dr * dr * 3 + dg * dg * 4 + db * db * 2;
                    if (d < bestD) {
                        bestD = d;
                        bestI = p;
                    }
                }
                return bestI;
            });
        }
        return out;
    }

    // ---- offline preview (flat-shaded painter's renderer with the same P/M/N texture mapping) ----
    static BufferedImage preview(ModelDefinition m, Map<Integer, int[]> texPixels, double yawDeg, double pitchDeg) {
        int W = 900, H = 760;
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(0x3d4a2a));
        g.fillRect(0, 0, W, H);
        double yaw = Math.toRadians(yawDeg), pitch = Math.toRadians(pitchDeg);
        int n = m.vertexCount;
        double[] sx = new double[n], sy = new double[n], sz = new double[n];
        for (int i = 0; i < n; i++) {
            // camera in front (+x) of the board; yaw turns around the vertical axis
            double x = m.vertexX[i], y = m.vertexY[i] + 260, z = m.vertexZ[i];
            double x1 = x * Math.cos(yaw) - z * Math.sin(yaw), z1 = x * Math.sin(yaw) + z * Math.cos(yaw);
            double y1 = y * Math.cos(pitch) - x1 * Math.sin(pitch), d = x1 * Math.cos(pitch) + y * Math.sin(pitch);
            double depth = 1100 - d;
            sx[i] = W / 2.0 + z1 * 1500 / depth;
            sy[i] = H / 2.0 + y1 * 1500 / depth;
            sz[i] = depth;
        }
        Integer[] order = new Integer[m.faceCount];
        for (int i = 0; i < order.length; i++) order[i] = i;
        Arrays.sort(order, (a, b) -> {
            int pa = m.faceRenderPriorities == null ? 0 : m.faceRenderPriorities[a];
            int pb = m.faceRenderPriorities == null ? 0 : m.faceRenderPriorities[b];
            if (pa != pb) return Integer.compare(pa, pb);
            return Double.compare(sz[m.faceIndices1[b]] + sz[m.faceIndices2[b]] + sz[m.faceIndices3[b]],
                    sz[m.faceIndices1[a]] + sz[m.faceIndices2[a]] + sz[m.faceIndices3[a]]);
        });
        for (int f : order) {
            int a = m.faceIndices1[f], b = m.faceIndices2[f], c = m.faceIndices3[f];
            // back-face cull like the client: keep a face only if its world normal points at the camera
            double[] nn = normal(m, a, b, c);
            double camX = 1100 * Math.cos(pitch) * Math.cos(yaw), camZ = -1100 * Math.cos(pitch) * Math.sin(yaw);
            double camY = 1100 * Math.sin(pitch) - 250;
            double tox = camX - m.vertexX[a], toy = camY - m.vertexY[a], toz = camZ - m.vertexZ[a];
            if (nn[0] * tox + nn[1] * toy + nn[2] * toz <= 0) continue;
            double[] nrm = normal(m, a, b, c);
            double light = 0.55 + 0.45 * Math.abs(nrm[0] * 0.7 - nrm[1] * 0.5 + nrm[2] * 0.3);
            int tex = m.faceTextures != null ? m.faceTextures[f] : -1;
            if (tex < 0) {
                Path2D p = new Path2D.Double();
                p.moveTo(sx[a], sy[a]);
                p.lineTo(sx[b], sy[b]);
                p.lineTo(sx[c], sy[c]);
                p.closePath();
                g.setColor(shade(hslToRgb(m.faceColors[f] & 0xffff), light));
                g.fill(p);
                g.draw(p);
                continue;
            }
            int coord = m.textureCoords[f] & 0xff;
            int P = m.texIndices1[coord], M = m.texIndices2[coord], N = m.texIndices3[coord];
            int[] tp = texPixels.get(tex);
            int minX = (int) Math.floor(Math.min(sx[a], Math.min(sx[b], sx[c]))), maxX = (int) Math.ceil(Math.max(sx[a], Math.max(sx[b], sx[c])));
            int minY = (int) Math.floor(Math.min(sy[a], Math.min(sy[b], sy[c]))), maxY = (int) Math.ceil(Math.max(sy[a], Math.max(sy[b], sy[c])));
            for (int py = Math.max(0, minY); py <= Math.min(H - 1, maxY); py++)
                for (int px = Math.max(0, minX); px <= Math.min(W - 1, maxX); px++) {
                    double[] bc = bary(px + 0.5, py + 0.5, sx[a], sy[a], sx[b], sy[b], sx[c], sy[c]);
                    if (bc == null) continue;
                    // perspective-correct world point, then the type-0 P/M/N mapping
                    double w0 = bc[0] / sz[a], w1 = bc[1] / sz[b], w2 = bc[2] / sz[c], ws = w0 + w1 + w2;
                    double wx = (w0 * m.vertexX[a] + w1 * m.vertexX[b] + w2 * m.vertexX[c]) / ws;
                    double wy = (w0 * m.vertexY[a] + w1 * m.vertexY[b] + w2 * m.vertexY[c]) / ws;
                    double wz = (w0 * m.vertexZ[a] + w1 * m.vertexZ[b] + w2 * m.vertexZ[c]) / ws;
                    double[] uv = uv(m, P, M, N, wx, wy, wz);
                    int tx = Math.floorMod((int) Math.floor(uv[0] * 128), 128), ty = Math.floorMod((int) Math.floor(uv[1] * 128), 128);
                    img.setRGB(px, py, shade(new Color(tp[ty * 128 + tx]), Math.min(1, light + 0.15)).getRGB());
                }
        }
        g.dispose();
        return img;
    }

    static double[] uv(ModelDefinition m, int P, int M, int N, double x, double y, double z) {
        double[] p = {m.vertexX[P], m.vertexY[P], m.vertexZ[P]};
        double[] e1 = {m.vertexX[M] - p[0], m.vertexY[M] - p[1], m.vertexZ[M] - p[2]};
        double[] e2 = {m.vertexX[N] - p[0], m.vertexY[N] - p[1], m.vertexZ[N] - p[2]};
        double[] q = {x - p[0], y - p[1], z - p[2]};
        return new double[]{dot(q, e1) / dot(e1, e1), dot(q, e2) / dot(e2, e2)};
    }

    static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    static double[] normal(ModelDefinition m, int a, int b, int c) {
        double ux = m.vertexX[b] - m.vertexX[a], uy = m.vertexY[b] - m.vertexY[a], uz = m.vertexZ[b] - m.vertexZ[a];
        double vx = m.vertexX[c] - m.vertexX[a], vy = m.vertexY[c] - m.vertexY[a], vz = m.vertexZ[c] - m.vertexZ[a];
        double nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx, l = Math.sqrt(nx * nx + ny * ny + nz * nz);
        return l == 0 ? new double[]{1, 0, 0} : new double[]{nx / l, ny / l, nz / l};
    }

    static double[] bary(double px, double py, double ax, double ay, double bx, double by, double cx, double cy) {
        double d = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy);
        if (Math.abs(d) < 1e-9) return null;
        double l1 = ((by - cy) * (px - cx) + (cx - bx) * (py - cy)) / d;
        double l2 = ((cy - ay) * (px - cx) + (ax - cx) * (py - cy)) / d;
        double l3 = 1 - l1 - l2;
        return l1 < 0 || l2 < 0 || l3 < 0 ? null : new double[]{l1, l2, l3};
    }

    static Color shade(Color c, double f) {
        return new Color((int) Math.min(255, c.getRed() * f), (int) Math.min(255, c.getGreen() * f), (int) Math.min(255, c.getBlue() * f));
    }

    static Color shade(int rgb, double f) {
        return shade(new Color(rgb), f);
    }

    /** OSRS packed HSL to RGB (the client's colour palette formula, brightness 0.8). */
    static int hslToRgb(int hsl) {
        double h = (hsl >> 10 & 63) / 64.0 + 0.0078125, s = (hsl >> 7 & 7) / 8.0 + 0.0625, l = (hsl & 127) / 128.0;
        double r = l, gg = l, b = l;
        if (s != 0) {
            double q = l < 0.5 ? l * (1 + s) : l + s - l * s, p = 2 * l - q;
            r = hue(p, q, h + 1 / 3.0);
            gg = hue(p, q, h);
            b = hue(p, q, h - 1 / 3.0);
        }
        int ri = (int) (Math.pow(r, 0.8) * 256), gi = (int) (Math.pow(gg, 0.8) * 256), bi = (int) (Math.pow(b, 0.8) * 256);
        return Math.min(255, ri) << 16 | Math.min(255, gi) << 8 | Math.min(255, bi);
    }

    static double hue(double p, double q, double t) {
        if (t < 0) t += 1;
        if (t > 1) t -= 1;
        if (t < 1 / 6.0) return p + (q - p) * 6 * t;
        if (t < 0.5) return q;
        if (t < 2 / 3.0) return p + (q - p) * (2 / 3.0 - t) * 6;
        return p;
    }

    public static void main(String[] args) throws Exception {
        boolean rebuild = args[0].startsWith("rebuild");
        if (args.length > 3 && args[3].equals("rivals")) {
            SCOREBOARD = true; // the cloned-object path
            RIVALS = true;
            OBJECT_ID = CLONE_ID = RIVALS_OBJECT;
            CLONE_NAME = "Rivals Leaderboard";
            MODEL_ID = 60137;
            FIRST_SPRITE = 24543;
            TEX_BASE = 221;
            BANNER = BLOOD;
            PLATE = BLOOD_D;
        } else if (args.length > 3 && args[3].equals("scoreboard")) {
            SCOREBOARD = true;
            OBJECT_ID = SCOREBOARD_OBJECT;
            MODEL_ID = 60133;
            FIRST_SPRITE = 24536;
            TEX_BASE = 214;
            BANNER = ROYAL;
            PLATE = CRIMSON_D;
        }
        String tag = RIVALS ? "rivals" : SCOREBOARD ? "scoreboard" : "board";
        boolean apply = args[0].equals("apply") || args[0].equals("rebuild");
        File previewDir = new File(args[2]);
        previewDir.mkdirs();
        try (Store store = new Store(new File(args[1]))) {
            store.load();
            Storage storage = store.getStorage();
            Index textures = store.getIndex(IndexType.TEXTURES), sprites = store.getIndex(IndexType.SPRITES);
            Index models = store.getIndex(IndexType.MODELS), configs = store.getIndex(IndexType.CONFIGS);

            // ---- textures ----
            Archive texArchive = textures.getArchive(0);
            byte[] texDec = texArchive.decompress(storage.loadArchive(texArchive));
            FileData[] texFd = texArchive.getFileData();
            List<byte[]> texContents = SpliceItemOption.splitChunks(texDec, texFd.length);
            int maxTex = 0;
            for (FileData f : texFd) maxTex = Math.max(maxTex, f.getId());
            byte[] template = null;
            int templateId = -1;
            for (int i = 0; i < texFd.length && template == null; i++) {
                byte[] b = texContents.get(i);
                if (b.length == 12 && (b[3] & 0xff) == 1) {
                    TextureDefinition t = new TextureLoader().load(texFd[i].getId(), b);
                    if (t.animationSpeed == 0) {
                        template = b;
                        templateId = texFd[i].getId();
                    }
                }
            }
            check(template != null, "no non-animated single-sprite texture to copy");
            int[] texIds = new int[4];
            for (int t = 0; t < 4; t++) texIds[t] = rebuild ? TEX_BASE + t : maxTex + 1 + t;
            check(rebuild || maxTex + 1 == TEX_BASE, "expected the next free texture id to be " + TEX_BASE + ", found " + (maxTex + 1));
            if (rebuild)
                for (int t = 0; t < 4; t++)
                    check(CreateRecoloredTextures.slotOf(texFd, texIds[t]) != -1, "rebuild: texture " + texIds[t] + " missing");

            BufferedImage face = RIVALS ? paintRivalsFace() : SCOREBOARD ? paintScoreboardFace() : paintFace();
            ImageIO.write(face, "png", new File(previewDir, tag + "_face_4x.png"));
            int[][] tilePx = tiles(face);
            Map<Integer, int[]> texPixels = new HashMap<>();
            List<byte[]> spriteBytes = new ArrayList<>(), textureBytes = new ArrayList<>();
            int spriteCompression = sprites.getArchive(1043).getCompression();
            for (int t = 0; t < 4; t++) {
                int spriteId = FIRST_SPRITE + t;
                check(rebuild ? sprites.getArchive(spriteId) != null : sprites.getArchive(spriteId) == null,
                        "sprite " + spriteId + (rebuild ? " missing (rebuild)" : " already exists"));
                int[] q = quantize(tilePx[t]);
                byte[] enc = BuildBrandIcons.encode(q, 128, 128);
                SpriteDefinition back = new SpriteLoader().load(spriteId, enc)[0];
                check(back.getWidth() == 128 && back.getHeight() == 128, "sprite size");
                int[] got = back.getPixels();
                long sr = 0, sg = 0, sb = 0;
                for (int i = 0; i < got.length; i++) {
                    check((got[i] & 0xffffff) == q[i], "sprite " + spriteId + " pixel " + i + " does not round-trip");
                    sr += q[i] >> 16 & 0xff;
                    sg += q[i] >> 8 & 0xff;
                    sb += q[i] & 0xff;
                }
                int avg = (int) (sr / q.length) << 16 | (int) (sg / q.length) << 8 | (int) (sb / q.length);
                int avgHsl = CreateRecoloredTextures.rgbToHsl16(avg);
                byte[] tb = template.clone();
                tb[0] = (byte) (avgHsl >> 8);
                tb[1] = (byte) avgHsl;
                tb[4] = (byte) (spriteId >> 8);
                tb[5] = (byte) spriteId;
                tb[10] = 0;
                tb[11] = 0;
                TextureDefinition td = new TextureLoader().load(texIds[t], tb);
                check(Arrays.equals(td.getFileIds(), new int[]{spriteId}) && td.animationSpeed == 0, "texture " + texIds[t] + " re-decode");
                spriteBytes.add(enc);
                textureBytes.add(tb);
                texPixels.put(texIds[t], q);
                BufferedImage tile = new BufferedImage(128, 128, BufferedImage.TYPE_INT_RGB);
                tile.setRGB(0, 0, 128, 128, q, 0, 128);
                ImageIO.write(tile, "png", new File(previewDir, "texture_" + texIds[t] + ".png"));
                System.out.printf("texture %d (template %d) sprite %d avg #%06x%n", texIds[t], templateId, spriteId, avg);
            }

            // ---- model ----
            buildModel(texIds);
            ModelDefinition def = toDefinition();
            byte[] modelBytes = ModelType3Encoder.encode(def);
            ModelDefinition back = new ModelLoader().load(MODEL_ID, modelBytes);
            check(back.faceRenderPriorities != null, "face priorities lost");
            for (int i = 0; i < def.faceCount; i++)
                check(back.faceRenderPriorities[i] == def.faceRenderPriorities[i], "face " + i + " priority");
            check(back.vertexCount == def.vertexCount && back.faceCount == def.faceCount && back.numTextureFaces == def.numTextureFaces,
                    "model counts did not round-trip");
            for (int i = 0; i < def.vertexCount; i++)
                check(back.vertexX[i] == def.vertexX[i] && back.vertexY[i] == def.vertexY[i] && back.vertexZ[i] == def.vertexZ[i], "vertex " + i);
            for (int i = 0; i < def.faceCount; i++) {
                check(back.faceIndices1[i] == def.faceIndices1[i] && back.faceIndices2[i] == def.faceIndices2[i]
                        && back.faceIndices3[i] == def.faceIndices3[i] && back.faceColors[i] == def.faceColors[i], "face " + i);
                short tex = back.faceTextures == null ? -1 : back.faceTextures[i];
                check(tex == def.faceTextures[i], "face " + i + " texture");
                if (tex != -1)
                    check((back.textureCoords[i] & 0xff) == (def.textureCoords[i] & 0xff), "face " + i + " texture coord");
            }
            for (int i = 0; i < def.numTextureFaces; i++)
                check(back.texIndices1[i] == def.texIndices1[i] && back.texIndices2[i] == def.texIndices2[i]
                        && back.texIndices3[i] == def.texIndices3[i], "texture triangle " + i);
            check(rebuild ? models.getArchive(MODEL_ID) != null : models.getArchive(MODEL_ID) == null,
                    "model " + MODEL_ID + (rebuild ? " missing (rebuild)" : " already exists"));
            int minY = 0, minX = 1 << 30, maxX = -(1 << 30), minZ = 1 << 30, maxZ = -(1 << 30);
            for (int i = 0; i < def.vertexCount; i++) {
                minY = Math.min(minY, def.vertexY[i]);
                minX = Math.min(minX, def.vertexX[i]);
                maxX = Math.max(maxX, def.vertexX[i]);
                minZ = Math.min(minZ, def.vertexZ[i]);
                maxZ = Math.max(maxZ, def.vertexZ[i]);
            }
            System.out.printf("model %d: %d vertices, %d faces (%d textured quads), %d bytes; x[%d,%d] z[%d,%d] top %d%n",
                    MODEL_ID, def.vertexCount, def.faceCount, def.numTextureFaces, modelBytes.length, minX, maxX, minZ, maxZ, minY);
            for (double[] view : new double[][]{{0, -12}, {-35, -18}, {35, -25}, {180, -15}, {60, -45}})
                ImageIO.write(preview(back, texPixels, view[0], view[1]), "png",
                        new File(previewDir, String.format("%s_preview_%+d.png", tag, (int) view[0])));

            // ---- object: model list -> [MODEL_ID] in every slot ----
            Archive objArchive = configs.getArchive(ConfigType.OBJECT.getId());
            byte[] objDec = objArchive.decompress(storage.loadArchive(objArchive));
            FileData[] objFd = objArchive.getFileData();
            List<byte[]> objContents = SpliceItemOption.splitChunks(objDec, objFd.length);
            ObjectLoader loader = new ObjectLoader();
            int edited = 0;
            if (SCOREBOARD) {
                int src = -1, existing = -1;
                for (int slot = 0; slot < objFd.length; slot++) {
                    if (objFd[slot].getId() == 44930 && "Pit Championship Board".equals(loader.load(44930, objContents.get(slot)).getName()))
                        src = slot;
                    if (objFd[slot].getId() == CLONE_ID)
                        existing = slot;
                }
                check(src != -1, "source object 44930 \"Pit Championship Board\" not found");
                check(rebuild || existing == -1, "object " + CLONE_ID + " already exists (use rebuild)");
                byte[] work = objContents.get(src);
                for (int op = 34; op >= 31; op--)
                    work = SpliceNewObject.removeOpcode(work, op);
                work = SpliceNewObject.replaceOpcode(work, 30, SpliceNewObject.withTerminator("View"));
                work = SpliceNewObject.replaceOpcode(work, 2, SpliceNewObject.withTerminator(CLONE_NAME));
                work = SpliceNewObject.replaceOpcode(work, 5, new byte[]{1, (byte) (MODEL_ID >> 8), (byte) MODEL_ID});
                ObjectDefinition before = loader.load(44930, objContents.get(src)), after = loader.load(CLONE_ID, work);
                check(CLONE_NAME.equals(after.getName()) && Arrays.equals(after.getObjectModels(), new int[]{MODEL_ID})
                        && "View".equals(after.getActions()[0]) && after.getActions()[1] == null && after.getActions()[2] == null
                        && after.getSizeX() == before.getSizeX() && after.getSizeY() == before.getSizeY()
                        && after.getModelSizeX() == before.getModelSizeX(), "object " + CLONE_ID + " clone");
                List<FileData> fds = new ArrayList<>(Arrays.asList(objFd));
                for (int slot = fds.size() - 1; slot >= 0; slot--)
                    if (fds.get(slot).getId() == OLD_SCOREBOARD_OBJECT) {
                        fds.remove(slot);
                        objContents.remove(slot);
                        System.out.println("removed the old object " + OLD_SCOREBOARD_OBJECT + " (its id does not fit the spawn packet)");
                    }
                if (existing != -1) {
                    objContents.set(existing, work);
                } else {
                    int at = 0;
                    while (at < fds.size() && fds.get(at).getId() < CLONE_ID)
                        at++; // sorted insert: the archive must stay in ascending id order
                    FileData fd = new FileData();
                    fd.setId(CLONE_ID);
                    fd.setNameHash(-1);
                    fds.add(at, fd);
                    objContents.add(at, work);
                }
                objArchive.setFileData(fds.toArray(new FileData[0]));
                for (int i = 1; i < fds.size(); i++)
                    check(fds.get(i - 1).getId() <= fds.get(i).getId(), "object archive out of order at " + i); // duplicate ids exist (44930 x2)
                edited = 1;
                System.out.printf("object %d \"%s\" models %s, size %dx%d, scale %d, actions %s (clone of 44930)%n", CLONE_ID,
                        after.getName(), Arrays.toString(after.getObjectModels()), after.getSizeX(), after.getSizeY(),
                        after.getModelSizeX(), Arrays.toString(after.getActions()));
            }
            for (int slot = 0; slot < objFd.length && !SCOREBOARD; slot++) {
                if (objFd[slot].getId() != OBJECT_ID) continue;
                byte[] raw = objContents.get(slot);
                ObjectDefinition before = loader.load(OBJECT_ID, raw);
                check(before.getObjectTypes() == null, "slot " + slot + " uses typed models (opcode 1)");
                byte[] work = SpliceNewObject.replaceOpcode(raw, 5, new byte[]{1, (byte) (MODEL_ID >> 8), (byte) MODEL_ID});
                ObjectDefinition after = loader.load(OBJECT_ID, work);
                check(Arrays.equals(after.getObjectModels(), new int[]{MODEL_ID}), "slot " + slot + " model list");
                check(Objects.equals(before.getName(), after.getName()) && Arrays.equals(before.getActions(), after.getActions())
                        && before.getSizeX() == after.getSizeX() && before.getSizeY() == after.getSizeY()
                        && before.getAnimationID() == after.getAnimationID()
                        && before.getModelSizeX() == after.getModelSizeX() && before.getModelSizeHeight() == after.getModelSizeHeight()
                        && before.getModelSizeY() == after.getModelSizeY(), "slot " + slot + " other fields changed");
                objContents.set(slot, work);
                edited++;
                System.out.printf("object %d slot %d \"%s\" models %s -> %s, size %dx%d, scale %d/%d/%d, actions %s%n", OBJECT_ID, slot,
                        after.getName(), Arrays.toString(before.getObjectModels()), Arrays.toString(after.getObjectModels()),
                        after.getSizeX(), after.getSizeY(), after.getModelSizeX(), after.getModelSizeHeight(), after.getModelSizeY(),
                        Arrays.toString(after.getActions()));
            }
            check(edited > 0, "object " + OBJECT_ID + " not found");

            if (!apply) {
                System.out.println("VERIFY OK -- previews in " + previewDir + ", nothing written.");
                return;
            }

            // sprites
            for (int t = 0; t < 4; t++)
                storeArchive(storage, sprites, FIRST_SPRITE + t, spriteCompression, spriteBytes.get(t));
            storeIndex(storage, sprites);
            // textures (appended, or replaced in their slots on rebuild)
            if (rebuild) {
                for (int t = 0; t < 4; t++)
                    texContents.set(CreateRecoloredTextures.slotOf(texFd, texIds[t]), textureBytes.get(t));
            } else {
                List<FileData> fdList = new ArrayList<>(Arrays.asList(texFd));
                for (int t = 0; t < 4; t++) {
                    FileData fd = new FileData();
                    fd.setId(texIds[t]);
                    fd.setNameHash(-1);
                    fdList.add(fd);
                    texContents.add(textureBytes.get(t));
                }
                texArchive.setFileData(fdList.toArray(new FileData[0]));
            }
            rewrite(storage, textures, texArchive, SpliceItemOption.joinChunks(texContents));
            storeIndex(storage, textures);
            // model
            storeArchive(storage, models, MODEL_ID, models.getArchive(3293).getCompression(), modelBytes);
            storeIndex(storage, models);
            // object
            rewrite(storage, configs, objArchive, SpliceItemOption.joinChunks(objContents));
            storeIndex(storage, configs);
            System.out.println((rebuild ? "REBUILD" : "APPLY") + " complete -- 4 sprites, 4 textures, model " + MODEL_ID + ", object " + OBJECT_ID + " (" + edited + " slots).");
        }
    }

    static void storeArchive(Storage storage, Index index, int id, int compression, byte[] raw) throws Exception {
        Archive a = index.getArchive(id);
        if (a == null) {
            a = index.addArchive(id);
            a.setRevision(1);
        } else {
            a.setRevision(a.getRevision() + 1);
        }
        a.setNameHash(-1);
        a.setCompression(compression);
        Container c = new Container(compression, -1);
        c.compress(raw, null);
        storage.store(index.getId(), id, c.data);
        CRC32 crc = new CRC32();
        crc.update(c.data);
        a.setCrc((int) crc.getValue());
        a.setCompressedSize(c.data.length);
        a.setDecompressedSize(raw.length);
        FileData fd = new FileData();
        fd.setId(0);
        a.setFileData(new FileData[]{fd});
    }

    static void rewrite(Storage storage, Index index, Archive archive, byte[] raw) throws Exception {
        Container c = new Container(archive.getCompression(), -1);
        c.compress(raw, null);
        storage.store(index.getId(), archive.getArchiveId(), c.data);
        archive.setCrc(c.crc);
        archive.setRevision(archive.getRevision() + 1);
        archive.setCompressedSize(c.data.length);
        archive.setDecompressedSize(raw.length);
    }

    static void storeIndex(Storage storage, Index index) throws Exception {
        IndexData indexData = index.toIndexData();
        Container c = new Container(index.getCompression(), -1);
        c.compress(indexData.writeIndexData(), null);
        storage.store(255, index.getId(), c.data);
        index.setCrc(c.crc);
    }

    static void check(boolean ok, String what) {
        if (!ok) throw new IllegalStateException("CHECK FAILED: " + what + " -- nothing written");
    }
}
