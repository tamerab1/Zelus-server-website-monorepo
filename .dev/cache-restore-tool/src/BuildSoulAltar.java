import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.ObjectDefinition;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.definitions.loaders.ObjectLoader;
import net.runelite.cache.fs.*;
import net.runelite.cache.io.OutputStream;

import javax.imageio.ImageIO;
import java.io.File;
import java.util.*;

// The Reliquary of Souls (2026-10-18): the Soulbound weapons' home altar, built from code. 3x3 footprint.
//   dais     three octagonal obsidian tiers; their top bands are the animated soul textures (253 teal, 254 gold) so the
//            seams glow and flow; a ring of eight glowing rune plates on the lowest tier
//   obelisks three obsidian rune obelisks at 120 degrees with gold collars and glowing teal tips
//   column   obsidian column + gold capital under the core
//   floating (skin label 1, spun + bobbed by one loc sequence: BuildLocSpinAnims after this tool)
//            the soul core (a teal-textured sphere), a ring of twelve gold beads, the three archetype emblems orbiting it
//            (Blade red, Recurve green, Spire teal) and six teal wisps. One label: the core turns on its own axis
//            (it is centred on the pivot) while the emblems, beads and wisps revolve around it.
// Loc record from scratch: model (opcode 1, type 10), name, 3x3, solid, ops Commune / Choose-Weapon / Guide.
// Usage: verify|apply <cachePath> <previewDir> <locId> <modelId>
public class BuildSoulAltar {
    static final String NAME = "Reliquary of Souls";
    static final String[] OPS = {"Commune", "Choose-Weapon", "Guide", null, null};
    static final int SIZE = 3, TEAL_TEX = 253, GOLD_TEX = 254;

    static short hsl(int h, int s, int l) { return (short) (h << 10 | s << 7 | l); }

    static final short OBSIDIAN = hsl(51, 2, 13), OBSIDIAN_L = hsl(51, 2, 21), OBSIDIAN_D = hsl(51, 2, 8),
            GOLD = hsl(7, 6, 60), GOLD_L = hsl(7, 5, 82), TEAL = hsl(32, 6, 70), RED = hsl(0, 6, 46), RED_L = hsl(0, 4, 70),
            GREEN = hsl(22, 6, 44), STEEL = hsl(0, 0, 90), WOOD = hsl(5, 4, 30);

    // ---- builder (BuildFortuneStatue's, plus a current texture) ----
    static final List<int[]> verts = new ArrayList<>();   // x, y, z, label
    static final List<int[]> faces = new ArrayList<>();   // a, b, c, colour, renderType, priority, texture, alpha
    static int label = 0, tex = -1;

    static int v(double x, double y, double z) {
        verts.add(new int[]{(int) Math.round(x), (int) Math.round(y), (int) Math.round(z), label});
        return verts.size() - 1;
    }

    static void tri(int a, int b, int c, short colour, double cx, double cy, double cz) {
        int[] A = verts.get(a), B = verts.get(b), C = verts.get(c);
        double ux = B[0] - A[0], uy = B[1] - A[1], uz = B[2] - A[2], vx = C[0] - A[0], vy = C[1] - A[1], vz = C[2] - A[2];
        double nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
        double ox = (A[0] + B[0] + C[0]) / 3.0 - cx, oy = (A[1] + B[1] + C[1]) / 3.0 - cy, oz = (A[2] + B[2] + C[2]) / 3.0 - cz;
        if (nx * ox + ny * oy + nz * oz < 0) { int t = b; b = c; c = t; }
        faces.add(new int[]{a, b, c, colour, 1, 0, tex, 0});
    }

    static int[] ring(int n, double r, double y, double phase, double cx, double cz) {
        int[] out = new int[n];
        for (int i = 0; i < n; i++) {
            double t = phase + 2 * Math.PI * i / n;
            out[i] = v(cx + r * Math.cos(t), y, cz + r * Math.sin(t));
        }
        return out;
    }

    static void frustum(int n, double r0, double y0, double r1, double y1, short side, short capLo, short capHi, double cx, double cz) {
        double phase = Math.PI / n;
        int[] lo = ring(n, r0, y0, phase, cx, cz), hi = ring(n, r1, y1, phase, cx, cz);
        double my = (y0 + y1) / 2;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            tri(lo[i], lo[j], hi[j], side, cx, my, cz);
            tri(lo[i], hi[j], hi[i], side, cx, my, cz);
        }
        int keep = tex;
        tex = -1; // caps are plain stone
        if (capLo != 0) {
            int[] c = ring(n, r0, y0, phase, cx, cz);
            for (int i = 1; i < n - 1; i++) tri(c[0], c[i], c[i + 1], capLo, cx, my, cz);
        }
        if (capHi != 0) {
            int[] c = ring(n, r1, y1, phase, cx, cz);
            for (int i = 1; i < n - 1; i++) tri(c[0], c[i], c[i + 1], capHi, cx, my, cz);
        }
        tex = keep;
    }

    /** A dais tier: obsidian body, its top band a flowing soul texture, a lighter cap. */
    static void tier(double rB, double y0, double rT, double y1, double band, int bandTex, short bandColour) {
        double t = (Math.abs(y1 - y0) - band) / Math.abs(y1 - y0);
        double rM = rB + (rT - rB) * t, yM = y0 + (y1 - y0) * t;
        frustum(8, rB, y0, rM, yM, OBSIDIAN, OBSIDIAN_D, (short) 0, 0, 0);
        tex = bandTex;
        frustum(8, rM, yM, rT, y1, bandColour, (short) 0, OBSIDIAN_L, 0, 0);
        tex = -1;
    }

    static void taper(double x0, double z0, double h0, double y0, double x1, double z1, double h1, double y1, short side, short cap) {
        int[] lo = {v(x0 - h0, y0, z0 - h0), v(x0 + h0, y0, z0 - h0), v(x0 + h0, y0, z0 + h0), v(x0 - h0, y0, z0 + h0)};
        int[] hi = {v(x1 - h1, y1, z1 - h1), v(x1 + h1, y1, z1 - h1), v(x1 + h1, y1, z1 + h1), v(x1 - h1, y1, z1 + h1)};
        double cx = (x0 + x1) / 2, cy = (y0 + y1) / 2, cz = (z0 + z1) / 2;
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4;
            tri(lo[i], lo[j], hi[j], side, cx, cy, cz);
            tri(lo[i], hi[j], hi[i], side, cx, cy, cz);
        }
        tri(lo[0], lo[1], lo[2], cap, cx, cy, cz); tri(lo[0], lo[2], lo[3], cap, cx, cy, cz);
        if (h1 > 0) { tri(hi[0], hi[1], hi[2], cap, cx, cy, cz); tri(hi[0], hi[2], hi[3], cap, cx, cy, cz); }
    }

    /** A box from two corners, axis aligned (for crossguards, plates). */
    static void box(double x0, double y0, double z0, double x1, double y1, double z1, short colour) {
        int[] p = new int[8];
        for (int i = 0; i < 8; i++) p[i] = v((i & 1) == 0 ? x0 : x1, (i & 2) == 0 ? y0 : y1, (i & 4) == 0 ? z0 : z1);
        double cx = (x0 + x1) / 2, cy = (y0 + y1) / 2, cz = (z0 + z1) / 2;
        int[][] q = {{0, 1, 3, 2}, {4, 5, 7, 6}, {0, 1, 5, 4}, {2, 3, 7, 6}, {0, 2, 6, 4}, {1, 3, 7, 5}};
        for (int[] f : q) {
            tri(p[f[0]], p[f[1]], p[f[2]], colour, cx, cy, cz);
            tri(p[f[0]], p[f[2]], p[f[3]], colour, cx, cy, cz);
        }
    }

    static void octahedron(double x, double y, double z, double w, double h, short colour) {
        int top = v(x, y - h, z), bot = v(x, y + h, z);
        int[] m = {v(x + w, y, z), v(x, y, z + w), v(x - w, y, z), v(x, y, z - w)};
        for (int i = 0; i < 4; i++) {
            tri(m[i], m[(i + 1) % 4], top, colour, x, y, z);
            tri(m[i], m[(i + 1) % 4], bot, colour, x, y, z);
        }
    }

    /** A UV sphere: `seg` around, `rings` from pole to pole. */
    static void sphere(double cx, double cy, double cz, double r, int seg, int rings, short colour) {
        int top = v(cx, cy - r, cz), bot = v(cx, cy + r, cz);
        int[][] lat = new int[rings - 1][];
        for (int i = 1; i < rings; i++) {
            double a = Math.PI * i / rings;
            lat[i - 1] = ring(seg, r * Math.sin(a), cy - r * Math.cos(a), 0, cx, cz);
        }
        for (int j = 0; j < seg; j++) {
            int k = (j + 1) % seg;
            tri(top, lat[0][j], lat[0][k], colour, cx, cy, cz);
            tri(bot, lat[rings - 2][j], lat[rings - 2][k], colour, cx, cy, cz);
            for (int i = 0; i < rings - 2; i++) {
                tri(lat[i][j], lat[i + 1][j], lat[i + 1][k], colour, cx, cy, cz);
                tri(lat[i][j], lat[i + 1][k], lat[i][k], colour, cx, cy, cz);
            }
        }
    }

    // ---- the altar, v2 (2026-10-18 round 2: the user's rework brief) ----
    static final short STONE = hsl(40, 1, 12), STONE_L = hsl(40, 1, 20), STONE_D = hsl(40, 1, 7),
            GLOW = hsl(31, 7, 100), GLOW_D = hsl(31, 7, 70), GOLD_GLOW = hsl(8, 7, 84);
    /** The Ascended looks orbiting the core (inventory models: the Azure Fang, the gold recurve, the gold spire). */
    static final int[] MINI_MODELS = {61236, 61221, 61229};
    /** Stood up along its main axis a model can come out head-down: the Fang does (checked on the previews). */
    static final boolean[] MINI_FLIP = {true, false, false};
    static ModelDefinition[] minis = new ModelDefinition[3];

    /** A glowing rune glyph standing proud of a tier riser: an X and a vertical bar, in the riser's own plane. */
    static void glyph(double a, double r, double y, double size, short colour) {
        double tx = -Math.sin(a), tz = Math.cos(a), nx = Math.cos(a), nz = Math.sin(a);
        double cx = r * nx + nx * 1.5, cz = r * nz + nz * 1.5; // just in front of the face
        double[][] bars = {{-1, -1, 1, 1}, {-1, 1, 1, -1}, {0, -1.3, 0, 1.3}};
        double w = 0.9;
        for (double[] bar : bars) {
            double du = bar[2] - bar[0], dv = bar[3] - bar[1], dl = Math.hypot(du, dv);
            double pu = -dv / dl * w, pv = du / dl * w; // perpendicular, in (tangent, vertical) units
            double[][] q = {{bar[0] * size + pu, bar[1] * size + pv}, {bar[2] * size + pu, bar[3] * size + pv},
                    {bar[2] * size - pu, bar[3] * size - pv}, {bar[0] * size - pu, bar[1] * size - pv}};
            int[] id = new int[4];
            for (int k = 0; k < 4; k++) id[k] = v(cx + tx * q[k][0], y + q[k][1], cz + tz * q[k][0]);
            double ix = cx - nx * 40, iz = cz - nz * 40; // a point behind: the quad faces outward
            tri(id[0], id[1], id[2], colour, ix, y, iz);
            tri(id[0], id[2], id[3], colour, ix, y, iz);
        }
    }

    /** A tier: obsidian riser with a glowing trim at its top edge and a glyph on every side, lighter top. */
    static void tier2(double rB, double y0, double rT, double y1, short trim, int glyphEvery) {
        // round 3: 16 sides (smoother), a taller trim that carries an animated texture -- azure 261 for the cyan trims,
        // flowing gold 254 for the gold one -- so the seams glow and move
        int n = 16;
        double trimH = 4;
        double rTrim = rT + (rB - rT) * trimH / Math.abs(y1 - y0);
        frustum(n, rB, y0, rTrim, y1 + trimH, STONE, STONE_D, (short) 0, 0, 0);
        tex = trim == GOLD_GLOW ? 254 : 261;
        frustum(n, rTrim, y1 + trimH, rT, y1, trim, (short) 0, STONE_L, 0, 0); // its tread (the cap) stays plain stone
        tex = -1;
        double rMid = (rB + rT) / 2 * Math.cos(Math.PI / n), yMid = (y0 + y1) / 2;
        tex = 261;
        for (int i = 0; i < n; i += glyphEvery) {
            double a = 2 * Math.PI * i / n + 2 * Math.PI / n; // the middle of a side (corners sit at pi/n + k 2pi/n)
            glyph(a, rMid, yMid, Math.min(5, Math.abs(y1 - y0) / 4), i % 4 == 0 ? GLOW : GOLD_GLOW);
        }
        tex = -1;
    }

    static final short GRANITE = hsl(38, 1, 30), GRANITE_L = hsl(38, 1, 42), GRANITE_D = hsl(38, 1, 22),
            GRANITE_TOP = hsl(38, 1, 48);

    /** One carved step: a faceted riser (alternating shades) from y0 up to yTop - bevel, a 45-degree bevel, a light tread. */
    static void step(double r, double y0, double yTop, double bevel, int glyphEvery) {
        int n = 12;
        double phase = Math.PI / n;
        int[] lo = ring(n, r, y0, phase, 0, 0), mid = ring(n, r, yTop + bevel, phase, 0, 0), top = ring(n, r - bevel, yTop, phase, 0, 0);
        double my = (y0 + yTop) / 2;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            short c = i % 2 == 0 ? GRANITE : GRANITE_D;
            tri(lo[i], lo[j], mid[j], c, 0, my, 0);
            tri(lo[i], mid[j], mid[i], c, 0, my, 0);
            tri(mid[i], mid[j], top[j], GRANITE_L, 0, my, 0);
            tri(mid[i], top[j], top[i], GRANITE_L, 0, my, 0);
        }
        int[] cap = ring(n, r - bevel, yTop, phase, 0, 0);
        for (int i = 1; i < n - 1; i++) tri(cap[0], cap[i], cap[i + 1], GRANITE_TOP, 0, my, 0);
        // a thin flowing cyan inlay ring just inside the tread's edge
        tex = 261;
        int[] g0 = ring(n, r - bevel - 3, yTop - 0.6, phase, 0, 0), g1 = ring(n, r - bevel - 6, yTop - 0.6, phase, 0, 0);
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            tri(g0[i], g0[j], g1[j], GLOW, 0, yTop + 20, 0);
            tri(g0[i], g1[j], g1[i], GLOW, 0, yTop + 20, 0);
        }
        double rMid = r * Math.cos(Math.PI / n);
        for (int i = 0; i < n; i += glyphEvery) {
            double a = 2 * Math.PI * i / n + 2 * Math.PI / n;
            glyph(a, rMid, (y0 + yTop + bevel) / 2, Math.min(4.5, Math.abs(yTop - y0) / 4), GLOW);
        }
        tex = -1;
    }

    /** n-sided column with alternating facet shades (fluting). */
    static void fluted(int n, double r0, double y0, double r1, double y1, short a, short b) {
        double phase = Math.PI / n;
        int[] lo = ring(n, r0, y0, phase, 0, 0), hi = ring(n, r1, y1, phase, 0, 0);
        double my = (y0 + y1) / 2;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            short c = i % 2 == 0 ? a : b;
            tri(lo[i], lo[j], hi[j], c, 0, my, 0);
            tri(lo[i], hi[j], hi[i], c, 0, my, 0);
        }
    }

    /** A hexagonal bipyramid crystal standing on its axis: tips at yTop / yBot, waist radius r at yWaist. */
    static void crystal(double yTop, double yWaist, double yBot, double r, short colour, int alpha) {
        int top = v(0, yTop, 0), bot = v(0, yBot, 0);
        int[] w = ring(6, r, yWaist, 0, 0, 0);
        int first = faces.size();
        for (int i = 0; i < 6; i++) {
            int j = (i + 1) % 6;
            tri(w[i], w[j], top, colour, 0, yWaist, 0);
            tri(w[i], w[j], bot, colour, 0, yWaist, 0);
        }
        for (int f = first; f < faces.size(); f++) faces.get(f)[7] = alpha;
    }

    /** Merges a (decimated) inventory model: stood upright along its main axis, scaled to `length`, centred at (x,y,z). */
    static void mini(ModelDefinition m, double x, double y, double z, double length, double facing, boolean flip) {
        Geo g = new Geo(m, 128, 128, 128);
        double[] ax = g.axis;
        // rotation taking the main axis onto -y (up), then a yaw so the flat side faces outward
        double[] up = {0, -1, 0};
        double[] k = {ax[1] * up[2] - ax[2] * up[1], ax[2] * up[0] - ax[0] * up[2], ax[0] * up[1] - ax[1] * up[0]};
        double kl = Math.sqrt(k[0] * k[0] + k[1] * k[1] + k[2] * k[2]), c = ax[0] * up[0] + ax[1] * up[1] + ax[2] * up[2];
        double ang = Math.atan2(kl, c);
        if (kl > 1e-9) for (int i = 0; i < 3; i++) k[i] /= kl;
        double mnx = 1e9, mxx = -1e9, mny = 1e9, mxy = -1e9, mnz = 1e9, mxz = -1e9;
        double[][] p = new double[m.vertexCount][];
        for (int i = 0; i < m.vertexCount; i++) {
            double[] r = {m.vertexX[i], m.vertexY[i], m.vertexZ[i]};
            if (kl > 1e-9) r = rot(r, k, ang);
            if (flip) r = rot(r, new double[]{1, 0, 0}, Math.PI); // tip up
            r = rot(r, new double[]{0, 1, 0}, facing);
            p[i] = r;
            mnx = Math.min(mnx, r[0]); mxx = Math.max(mxx, r[0]); mny = Math.min(mny, r[1]); mxy = Math.max(mxy, r[1]);
            mnz = Math.min(mnz, r[2]); mxz = Math.max(mxz, r[2]);
        }
        double s = length / Math.max(1, mxy - mny), ox = (mnx + mxx) / 2, oy = (mny + mxy) / 2, oz = (mnz + mxz) / 2;
        int base = verts.size();
        for (double[] r : p) v(x + (r[0] - ox) * s, y + (r[1] - oy) * s, z + (r[2] - oz) * s);
        for (int f = 0; f < m.faceCount; f++) {
            if (m.faceTransparencies != null && (m.faceTransparencies[f] & 0xff) >= 254) continue;
            faces.add(new int[]{base + m.faceIndices1[f], base + m.faceIndices2[f], base + m.faceIndices3[f], m.faceColors[f], 1, 0,
                    m.faceTextures == null ? -1 : m.faceTextures[f], m.faceTransparencies == null ? 0 : m.faceTransparencies[f] & 0xff});
        }
    }

    static double[] rot(double[] r, double[] k, double a) {
        double cs = Math.cos(a), sn = Math.sin(a), kd = k[0] * r[0] + k[1] * r[1] + k[2] * r[2];
        double cx = k[1] * r[2] - k[2] * r[1], cy = k[2] * r[0] - k[0] * r[2], cz = k[0] * r[1] - k[1] * r[0];
        return new double[]{r[0] * cs + cx * sn + k[0] * kd * (1 - cs), r[1] * cs + cy * sn + k[1] * kd * (1 - cs),
                r[2] * cs + cz * sn + k[2] * kd * (1 - cs)};
    }

    static ModelDefinition build(int id) {
        verts.clear();
        faces.clear();
        label = 0;
        tex = -1;
        // v4 (2026-10-18 r11, "flat, low-poly, plastic"): carved granite instead of near-black obsidian, so the lighting
        // reads -- faceted risers in alternating shades, a bevel and a lighter tread per step, glyphs set in the risers
        step(196, 0, -16, 3, 2);
        step(152, -19, -36, 3, 2);
        step(108, -39, -54, 4, 4);
        // three rune obelisks on the middle tread: granite, a flowing cyan band, glowing caps
        for (int i = 0; i < 3; i++) {
            double ang = Math.PI / 2 + 2 * Math.PI * i / 3, x = 130 * Math.cos(ang), z = 130 * Math.sin(ang);
            taper(x, z, 11, -39, x, z, 8, -78, GRANITE, GRANITE_L);
            tex = 261;
            taper(x, z, 8.5, -78, x, z, 7.5, -86, GLOW, GLOW);
            tex = -1;
            taper(x, z, 7.5, -86, x, z, 3, -108, GRANITE_D, GRANITE_L);
            octahedron(x, -117, z, 6, 9, GLOW);
        }
        // pedestal: a fluted granite column with a flared capital
        fluted(12, 30, -57, 22, -98, GRANITE, GRANITE_L);
        frustum(12, 22, -98, 34, -110, GRANITE_L, (short) 0, GRANITE, 0, 0);
        tex = 261;
        frustum(12, 34.5, -104, 34.5, -107, GLOW, (short) 0, (short) 0, 0, 0);
        tex = -1;
        // four carved stone claws rising from the capital, curving out and back in to cradle the floating crystal
        double[][] path = {{30, -110, 6}, {40, -132, 5.5}, {40, -156, 5}, {32, -178, 4}, {20, -196, 2.5}, {12, -206, 0.5}};
        for (int i = 0; i < 4; i++) {
            double ang = Math.PI / 4 + Math.PI / 2 * i, ca = Math.cos(ang), sa = Math.sin(ang);
            for (int k = 0; k + 1 < path.length; k++) {
                double[] p0 = path[k], p1 = path[k + 1];
                taper(p0[0] * ca, p0[0] * sa, p0[2], p0[1], p1[0] * ca, p1[0] * sa, p1[2], p1[1],
                        k % 2 == 0 ? GRANITE : GRANITE_L, GRANITE_L);
            }
            tex = 261; // a glowing rune on each claw's outer face
            octahedron(41 * ca, -144, 41 * sa, 3, 6, GLOW);
            tex = -1;
        }

        // ---- floating: label 1 ----
        label = 1;
        double cy = -185;
        // the spectral crystal: a bright flowing core inside a translucent shell. Round 12: its own label 2, centred on the
        // axis (x = z = 0), which the loop only bobs up and down -- on label 1 it turned with the orbiting weapons about
        // their off-axis centroid and swung around the pedestal (BuildAltarAnim)
        label = 2;
        tex = 261;
        crystal(cy - 34, cy + 2, cy + 30, 13, GLOW, 0);
        tex = -1;
        crystal(cy - 52, cy, cy + 44, 24, GLOW_D, 110);
        label = 1;
        // the three Ascended weapons orbiting (real models, upright, facing outward) with fading wisp trails
        for (int i = 0; i < 3; i++) {
            double a = Math.PI / 6 + 2 * Math.PI * i / 3, x = 96 * Math.cos(a), z = 96 * Math.sin(a);
            mini(minis[i], x, cy + 4, z, i == 1 ? 86 : 96, -a, MINI_FLIP[i]);
            for (int t = 1; t <= 4; t++) { // trail behind it along the orbit
                double b = a - t * 0.16;
                int first = faces.size();
                octahedron(96 * Math.cos(b), cy + 4 + Math.sin(t) * 6, 96 * Math.sin(b), 5 - t * 0.8, 3, GLOW);
                for (int f = first; f < faces.size(); f++) faces.get(f)[7] = 40 + t * 45;
            }
        }
        // free wisps
        double[][] wisp = {{0.3, 128, -130}, {1.4, 132, -222}, {2.4, 122, -150}, {3.5, 134, -236}, {4.4, 126, -140}, {5.5, 136, -210}};
        for (double[] w : wisp) octahedron(w[1] * Math.cos(w[0]), w[2], w[1] * Math.sin(w[0]), 3, 7, GLOW);
        label = 0;
        return toModel(id);
    }

    static ModelDefinition toModel(int id) {
        ModelDefinition d = new ModelDefinition();
        d.id = id;
        d.vertexCount = verts.size();
        d.vertexX = new int[d.vertexCount]; d.vertexY = new int[d.vertexCount]; d.vertexZ = new int[d.vertexCount];
        d.packedVertexGroups = new int[d.vertexCount];
        for (int i = 0; i < d.vertexCount; i++) {
            int[] p = verts.get(i);
            d.vertexX[i] = p[0]; d.vertexY[i] = p[1]; d.vertexZ[i] = p[2]; d.packedVertexGroups[i] = p[3];
        }
        d.faceCount = faces.size();
        d.faceIndices1 = new int[d.faceCount]; d.faceIndices2 = new int[d.faceCount]; d.faceIndices3 = new int[d.faceCount];
        d.faceColors = new short[d.faceCount];
        d.faceRenderTypes = new byte[d.faceCount];
        d.faceRenderPriorities = new byte[d.faceCount];
        d.faceTextures = new short[d.faceCount];
        d.faceTransparencies = new byte[d.faceCount];
        for (int i = 0; i < d.faceCount; i++) {
            int[] f = faces.get(i);
            d.faceIndices1[i] = f[0]; d.faceIndices2[i] = f[1]; d.faceIndices3[i] = f[2];
            d.faceColors[i] = (short) f[3]; d.faceRenderTypes[i] = (byte) f[4]; d.faceRenderPriorities[i] = (byte) f[5];
            d.faceTextures[i] = (short) f[6];
            d.faceTransparencies[i] = (byte) f[7];
        }
        d.priority = 0;
        return d;
    }

    /** The orbiting weapons: the Ascended inventory models, decimated to a few hundred faces each. */
    static void loadMinis(Store store) throws Exception {
        for (int i = 0; i < MINI_MODELS.length; i++) {
            ModelDefinition m = BuildTierPets.model(store, MINI_MODELS[i]);
            minis[i] = DecimateModel.decimate(m, 360);
        }
    }

    static byte[] loc(int model) {
        OutputStream o = new OutputStream();
        o.writeByte(1); o.writeByte(1); o.writeShort(model); o.writeByte(10);
        o.writeByte(2); o.writeString(NAME);
        o.writeByte(14); o.writeByte(SIZE);
        o.writeByte(15); o.writeByte(SIZE);
        o.writeByte(19); o.writeByte(1);
        for (int i = 0; i < OPS.length; i++) if (OPS[i] != null) { o.writeByte(30 + i); o.writeString(OPS[i]); }
        o.writeByte(0);
        return o.flip();
    }

    public static void main(String[] args) throws Exception {
        if (args[0].equals("model")) { // model <cache> <previewDir> <modelId> <outDir>: v2 rework, written for InsertRawModels replace
            try (Store store = new Store(new File(args[1]))) {
                store.load();
                loadMinis(store);
                int modelId = Integer.parseInt(args[3]);
                ModelDefinition m = build(modelId);
                byte[] enc = ModelType3Encoder.encode(m);
                ModelDefinition back = new ModelLoader().load(modelId, enc);
                if (back.faceCount != m.faceCount || !Arrays.equals(back.faceTextures, m.faceTextures)
                        || !Arrays.equals(back.faceTransparencies, m.faceTransparencies))
                    throw new IllegalStateException("model round trip mismatch");
                int floating = 0;
                for (int sk : m.packedVertexGroups) if (sk == 1) floating++;
                System.out.printf("model %d (v2): %d verts (%d floating), %d faces%n", modelId, m.vertexCount, floating, m.faceCount);
                File out = new File(args[4]);
                out.mkdirs();
                java.nio.file.Files.write(new File(out, modelId + ".dat").toPath(), enc);
                File previewDir = new File(args[2]);
                previewDir.mkdirs();
                ModelDefinition pv = new ModelLoader().load(modelId, enc);
                pv.faceTextures = null;
                for (double yaw : new double[]{30, 150})
                    ImageIO.write(BuildChampionshipBoard.preview(pv, new HashMap<>(), yaw, 16), "png",
                            new File(previewDir, String.format("soul_altar_v2_%d.png", (int) yaw)));
            }
            return;
        }
        boolean apply = args[0].equals("apply");
        File previewDir = new File(args[2]);
        previewDir.mkdirs();
        int locId = Integer.parseInt(args[3]), modelId = Integer.parseInt(args[4]);
        if (locId >= 65536) throw new IllegalStateException("loc id must be below 65536 (spawn packet)");
        try (Store store = new Store(new File(args[1]))) {
            store.load();
            Storage storage = store.getStorage();
            Index models = store.getIndex(IndexType.MODELS), cfg = store.getIndex(IndexType.CONFIGS);
            Archive locArc = cfg.getArchive(ConfigType.OBJECT.getId());
            if (models.getArchive(modelId) != null) throw new IllegalStateException("model " + modelId + " exists");
            if (BuildLocSpinAnims.ids(locArc).contains(locId)) throw new IllegalStateException("loc " + locId + " exists");

            loadMinis(store);
            ModelDefinition m = build(modelId);
            byte[] enc = ModelType3Encoder.encode(m);
            ModelDefinition back = new ModelLoader().load(modelId, enc);
            int[] skins = back.packedVertexGroups != null ? back.packedVertexGroups
                    : TextureModelFaces.packVertexGroups(back.getVertexGroups(), back.vertexCount);
            if (back.vertexCount != m.vertexCount || back.faceCount != m.faceCount || !Arrays.equals(skins, m.packedVertexGroups)
                    || !Arrays.equals(back.faceTextures, m.faceTextures) || !Arrays.equals(back.faceColors, m.faceColors))
                throw new IllegalStateException("model round trip mismatch");
            int floating = 0, textured = 0, ymin = 0;
            for (int s : skins) if (s == 1) floating++;
            for (short t : m.faceTextures) if (t != -1) textured++;
            for (int y : m.vertexY) ymin = Math.min(ymin, y);
            System.out.printf("model %d: %d verts (%d floating), %d faces (%d textured), height %d%n", modelId, m.vertexCount,
                    floating, m.faceCount, textured, -ymin);

            byte[] lb = loc(modelId);
            ObjectDefinition od = new ObjectLoader().load(locId, lb);
            if (!NAME.equals(od.getName()) || od.getObjectModels()[0] != modelId || od.getSizeX() != SIZE || od.getSizeY() != SIZE
                    || !"Commune".equals(od.getActions()[0]) || !"Guide".equals(od.getActions()[2]))
                throw new IllegalStateException("loc decodes wrong");
            System.out.println("loc " + locId + " \"" + NAME + "\" " + SIZE + "x" + SIZE + " ops=" + Arrays.toString(od.getActions()));

            // previews: textured faces shown in their colour (the flat preview renderer has no texture path)
            ModelDefinition pv = new ModelLoader().load(modelId, enc);
            pv.faceTextures = null;
            for (double yaw : new double[]{30, 150})
                ImageIO.write(BuildChampionshipBoard.preview(pv, new HashMap<>(), yaw, 16), "png",
                        new File(previewDir, String.format("soul_altar_%d.png", (int) yaw)));
            if (!apply) { System.out.println("VERIFY OK (dry run), previews in " + previewDir); return; }
            BuildLocSpinAnims.addArchive(storage, models, modelId, List.of(enc), new int[]{0}, 0);
            BuildLocSpinAnims.writeRefTable(storage, models);
            BuildFortuneStatue.insertSorted(storage, cfg, locArc, new TreeMap<>(Map.of(locId, lb)));
            BuildLocSpinAnims.writeRefTable(storage, cfg);
            System.out.println("APPLY OK -- now BuildLocSpinAnims apply <cache> " + locId + "," + modelId + ",1,<skeleton>,<frames>,<seq>,192,1,5");
        }
    }
}
