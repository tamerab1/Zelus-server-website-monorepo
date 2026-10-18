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

// The Soulbound Sanctuary props (2026-10-18), built from code with BuildSoulAltar's builder:
//   Soul Brazier  loc 62446 / model 61241, 1x1, solid: a carved obsidian pillar, gold collar and bowl, a cyan soul flame
//                 (animated azure texture 261 inner tongues + translucent outer tongues, skin label 1, flickered by a loc
//                 loop: BuildLocSpinAnims after this tool)
//   Arcane flagstone loc 62447 / model 61242, 1x1 ground decoration (type 22), walk-through, contoured to the terrain: a
//                 dark slab with bevelled edges and a faint cyan rune inlay
// Usage: verify|apply <cachePath> <previewDir>
public class BuildSanctuaryProps {
    static final int BRAZIER_LOC = 62446, BRAZIER_MODEL = 61241, TILE_LOC = 62447, TILE_MODEL = 61242;

    static short hsl(int h, int s, int l) { return BuildSoulAltar.hsl(h, s, l); }

    static ModelDefinition brazier() {
        BuildSoulAltar.verts.clear();
        BuildSoulAltar.faces.clear();
        BuildSoulAltar.label = 0;
        BuildSoulAltar.tex = -1;
        short stone = BuildSoulAltar.STONE, stoneL = BuildSoulAltar.STONE_L, gold = BuildSoulAltar.GOLD, goldL = BuildSoulAltar.GOLD_L,
                glow = BuildSoulAltar.GLOW;
        // foot, pillar, collar, bowl
        BuildSoulAltar.taper(0, 0, 40, 0, 0, 0, 34, -14, stone, stoneL);
        BuildSoulAltar.taper(0, 0, 22, -14, 0, 0, 17, -130, stone, stoneL);
        BuildSoulAltar.taper(0, 0, 24, -40, 0, 0, 23, -48, gold, goldL);
        BuildSoulAltar.taper(0, 0, 21, -118, 0, 0, 22, -130, gold, goldL);
        BuildSoulAltar.frustum(8, 18, -130, 36, -152, gold, gold, stoneL, 0, 0);
        // glowing rune slits on the pillar's four faces
        for (int i = 0; i < 4; i++) {
            double a = Math.PI / 2 * i;
            BuildSoulAltar.glyph(a, 19.5, -86, 6, glow);
        }
        // the flame: label 1
        BuildSoulAltar.label = 1;
        BuildSoulAltar.tex = 261;
        for (int i = 0; i < 5; i++) {
            double a = 2 * Math.PI * i / 5, r = i == 0 ? 0 : 12;
            tongue(r * Math.cos(a), r * Math.sin(a), i == 0 ? 11 : 7, -150, 4 * Math.cos(a + 1), i == 0 ? -222 : -196,
                    4 * Math.sin(a + 1), glow, 0);
        }
        BuildSoulAltar.tex = -1;
        for (int i = 0; i < 6; i++) { // translucent outer flame
            double a = 2 * Math.PI * i / 6 + 0.4;
            tongue(20 * Math.cos(a), 20 * Math.sin(a), 8, -150, 9 * Math.cos(a + 0.8), -206, 9 * Math.sin(a + 0.8),
                    BuildSoulAltar.GLOW_D, 120);
        }
        BuildSoulAltar.label = 0;
        return BuildSoulAltar.toModel(BRAZIER_MODEL);
    }

    /** A pyramid from a square base (half-size h at y0) to a tip, with an alpha. */
    static void tongue(double bx, double bz, double h, double y0, double tx, double ty, double tz, short colour, int alpha) {
        int first = BuildSoulAltar.faces.size();
        int[] b = {BuildSoulAltar.v(bx - h, y0, bz - h), BuildSoulAltar.v(bx + h, y0, bz - h), BuildSoulAltar.v(bx + h, y0, bz + h),
                BuildSoulAltar.v(bx - h, y0, bz + h)};
        int t = BuildSoulAltar.v(tx, ty, tz);
        double cx = (4 * bx + tx) / 5, cy = (4 * y0 + ty) / 5, cz = (4 * bz + tz) / 5;
        for (int i = 0; i < 4; i++) BuildSoulAltar.tri(b[i], b[(i + 1) % 4], t, colour, cx, cy, cz);
        for (int f = first; f < BuildSoulAltar.faces.size(); f++) BuildSoulAltar.faces.get(f)[7] = alpha;
    }

    static ModelDefinition flagstone() {
        BuildSoulAltar.verts.clear();
        BuildSoulAltar.faces.clear();
        BuildSoulAltar.label = 0;
        BuildSoulAltar.tex = -1;
        short slab = hsl(40, 1, 17), bevel = hsl(40, 1, 11), inlay = hsl(31, 6, 64);
        // slab top (a hair above the ground), bevelled down to the tile edge
        double h = 62, in = 54, y0 = 0, y1 = -3;
        int[] outer = {BuildSoulAltar.v(-h, y0, -h), BuildSoulAltar.v(h, y0, -h), BuildSoulAltar.v(h, y0, h), BuildSoulAltar.v(-h, y0, h)};
        int[] inner = {BuildSoulAltar.v(-in, y1, -in), BuildSoulAltar.v(in, y1, -in), BuildSoulAltar.v(in, y1, in), BuildSoulAltar.v(-in, y1, in)};
        BuildSoulAltar.tri(inner[0], inner[1], inner[2], slab, 0, 10, 0);
        BuildSoulAltar.tri(inner[0], inner[2], inner[3], slab, 0, 10, 0);
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4;
            BuildSoulAltar.tri(outer[i], outer[j], inner[j], bevel, 0, 10, 0);
            BuildSoulAltar.tri(outer[i], inner[j], inner[i], bevel, 0, 10, 0);
        }
        // the rune inlay: a thin diamond ring and a centre dot, just above the slab
        double r = 26, w = 2.2, y = y1 - 0.6;
        double[][] dia = {{0, -r}, {r, 0}, {0, r}, {-r, 0}};
        for (int i = 0; i < 4; i++) {
            double[] p0 = dia[i], p1 = dia[(i + 1) % 4];
            double dx = p1[0] - p0[0], dz = p1[1] - p0[1], l = Math.hypot(dx, dz), nx = -dz / l * w, nz = dx / l * w;
            int a = BuildSoulAltar.v(p0[0] + nx, y, p0[1] + nz), b = BuildSoulAltar.v(p1[0] + nx, y, p1[1] + nz),
                    c = BuildSoulAltar.v(p1[0] - nx, y, p1[1] - nz), d = BuildSoulAltar.v(p0[0] - nx, y, p0[1] - nz);
            BuildSoulAltar.tri(a, b, c, inlay, 0, 10, 0);
            BuildSoulAltar.tri(a, c, d, inlay, 0, 10, 0);
        }
        int c0 = BuildSoulAltar.v(0, y, -5), c1 = BuildSoulAltar.v(5, y, 0), c2 = BuildSoulAltar.v(0, y, 5), c3 = BuildSoulAltar.v(-5, y, 0);
        BuildSoulAltar.tri(c0, c1, c2, inlay, 0, 10, 0);
        BuildSoulAltar.tri(c0, c2, c3, inlay, 0, 10, 0);
        return BuildSoulAltar.toModel(TILE_MODEL);
    }

    static byte[] brazierLoc() {
        OutputStream o = new OutputStream();
        o.writeByte(1); o.writeByte(1); o.writeShort(BRAZIER_MODEL); o.writeByte(10);
        o.writeByte(2); o.writeString("Soul brazier");
        o.writeByte(19); o.writeByte(0);
        o.writeByte(0);
        return o.flip();
    }

    static byte[] tileLoc() {
        OutputStream o = new OutputStream();
        o.writeByte(1); o.writeByte(1); o.writeShort(TILE_MODEL); o.writeByte(22);
        o.writeByte(2); o.writeString("Arcane flagstone");
        o.writeByte(17); // walk-through
        o.writeByte(21); // contoured to the terrain
        o.writeByte(0);
        return o.flip();
    }

    public static void main(String[] args) throws Exception {
        boolean apply = args[0].equals("apply");
        File previewDir = new File(args[2]);
        previewDir.mkdirs();
        try (Store store = new Store(new File(args[1]))) {
            store.load();
            Storage storage = store.getStorage();
            Index models = store.getIndex(IndexType.MODELS), cfg = store.getIndex(IndexType.CONFIGS);
            Archive locArc = cfg.getArchive(ConfigType.OBJECT.getId());
            Set<Integer> locIds = BuildLocSpinAnims.ids(locArc);
            Map<Integer, byte[]> modelBytes = new TreeMap<>();
            for (ModelDefinition m : new ModelDefinition[]{brazier(), flagstone()}) {
                if (models.getArchive(m.id) != null) throw new IllegalStateException("model " + m.id + " exists");
                byte[] enc = ModelType3Encoder.encode(m);
                ModelDefinition back = new ModelLoader().load(m.id, enc);
                if (back.faceCount != m.faceCount || !Arrays.equals(back.faceTransparencies, m.faceTransparencies))
                    throw new IllegalStateException("model " + m.id + " round trip");
                modelBytes.put(m.id, enc);
                int ymin = 0;
                for (int y : m.vertexY) ymin = Math.min(ymin, y);
                System.out.printf("model %d: %d verts, %d faces, height %d%n", m.id, m.vertexCount, m.faceCount, -ymin);
                ModelDefinition pv = new ModelLoader().load(m.id, enc);
                pv.faceTextures = null;
                ImageIO.write(BuildChampionshipBoard.preview(pv, new HashMap<>(), 30, m.id == TILE_MODEL ? 50 : 14), "png",
                        new File(previewDir, "sanctuary_" + m.id + ".png"));
            }
            Map<Integer, byte[]> locs = new TreeMap<>(Map.of(BRAZIER_LOC, brazierLoc(), TILE_LOC, tileLoc()));
            for (Map.Entry<Integer, byte[]> e : locs.entrySet()) {
                if (locIds.contains(e.getKey())) throw new IllegalStateException("loc " + e.getKey() + " exists");
                ObjectDefinition od = new ObjectLoader().load(e.getKey(), e.getValue());
                System.out.printf("loc %d \"%s\" %dx%d interact=%d%n", e.getKey(), od.getName(), od.getSizeX(), od.getSizeY(), od.getInteractType());
            }
            if (!apply) { System.out.println("VERIFY OK (dry run)"); return; }
            for (Map.Entry<Integer, byte[]> e : modelBytes.entrySet())
                BuildLocSpinAnims.addArchive(storage, models, e.getKey(), List.of(e.getValue()), new int[]{0}, 0);
            BuildLocSpinAnims.writeRefTable(storage, models);
            BuildFortuneStatue.insertSorted(storage, cfg, locArc, locs);
            BuildLocSpinAnims.writeRefTable(storage, cfg);
            System.out.println("APPLY OK -- then BuildLocSpinAnims apply <cache> " + BRAZIER_LOC + "," + BRAZIER_MODEL + ",1,...");
        }
    }
}
