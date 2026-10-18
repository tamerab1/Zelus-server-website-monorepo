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

// The open-air Soul Sanctuary (2026-10-18 round 7): the walled room (62448 / 62449) read as a concrete bunker, so the
// sanctuary is a raised slate dais open to the world instead. Every piece is tile-centred, so a straight piece looks the
// same at either 90-degree rotation (no rotation-direction guesswork):
//   Sanctuary pillar      loc 62450 / model 61245, 1x1 solid: a slender fluted shaft on a stepped plinth, gold collars,
//                         a flowing cyan rune band (texture 261), a flared capital and a floating soul crystal on top
//   Sanctuary balustrade  loc 62451 / model 61246, 1x1 solid, under half a tile high: base and top rails with vase
//                         balusters and a cyan inlay along the top; it runs along model z (rotation 0 = north-south) and
//                         its rails reach into the neighbouring tiles up to a pillar's plinth so the run has no gaps
//   Dais step             loc 62452 / model 61247, floor decoration: one slate slab, half the dais' height
//   Slate apron           loc 62453 / model 61248, floor decoration: broken flat slabs that thin out into the path
//   Dais flagstone        model 61242 (loc 62447, rebuilt in place): four raised dark slate slabs with dark grout
// Floor heights taper 8 (dais) -> 4 (step) -> 2 (apron) -> the ground.
// Usage: verify|apply <cachePath> <previewDir> <outDir>  (apply adds the new models + locs; the flagstone model goes to
// <outDir> for InsertRawModels replace)
public class BuildSanctuaryPavilion {
    static final int PILLAR_LOC = 62450, RAIL_LOC = 62451, STEP_LOC = 62452, APRON_LOC = 62453;
    static final int PILLAR_MODEL = 61245, RAIL_MODEL = 61246, STEP_MODEL = 61247, APRON_MODEL = 61248, TILE_MODEL = 61242;
    static final double PLINTH = 30; // the pillar plinth's half width: the balustrade rails stop here

    static short hsl(int h, int s, int l) { return BuildSoulAltar.hsl(h, s, l); }
    static final short SLATE = hsl(38, 1, 17), SLATE_L = hsl(38, 1, 23), SLATE_D = hsl(38, 1, 11), GROUT = hsl(38, 1, 6),
            STONE = hsl(38, 1, 20), STONE_L = hsl(38, 1, 28), STONE_D = hsl(38, 1, 13);

    static void reset() {
        BuildSoulAltar.verts.clear();
        BuildSoulAltar.faces.clear();
        BuildSoulAltar.label = 0;
        BuildSoulAltar.tex = -1;
    }

    /** A flat-topped slab from (x0,z0) to (x1,z1), top at y = -h, its four sides down to the ground. */
    static void slab(double x0, double z0, double x1, double z1, double h, short top, short side) {
        int[] lo = {BuildSoulAltar.v(x0, 0, z0), BuildSoulAltar.v(x1, 0, z0), BuildSoulAltar.v(x1, 0, z1), BuildSoulAltar.v(x0, 0, z1)};
        int[] hi = {BuildSoulAltar.v(x0, -h, z0), BuildSoulAltar.v(x1, -h, z0), BuildSoulAltar.v(x1, -h, z1), BuildSoulAltar.v(x0, -h, z1)};
        double cx = (x0 + x1) / 2, cz = (z0 + z1) / 2;
        BuildSoulAltar.tri(hi[0], hi[1], hi[2], top, cx, 0, cz);
        BuildSoulAltar.tri(hi[0], hi[2], hi[3], top, cx, 0, cz);
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4;
            BuildSoulAltar.tri(lo[i], lo[j], hi[j], side, cx, -h / 2, cz);
            BuildSoulAltar.tri(lo[i], hi[j], hi[i], side, cx, -h / 2, cz);
        }
    }

    /** A flat polygon at height -h (a broken slab of the apron), its outline extruded down to the ground. */
    static void shard(double[][] pts, double h, short top, short side) {
        int n = pts.length;
        int[] lo = new int[n], hi = new int[n];
        double cx = 0, cz = 0;
        for (double[] p : pts) { cx += p[0] / n; cz += p[1] / n; }
        for (int i = 0; i < n; i++) {
            lo[i] = BuildSoulAltar.v(pts[i][0], 0, pts[i][1]);
            hi[i] = BuildSoulAltar.v(pts[i][0], -h, pts[i][1]);
        }
        for (int i = 1; i < n - 1; i++) BuildSoulAltar.tri(hi[0], hi[i], hi[i + 1], top, cx, 0, cz);
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            BuildSoulAltar.tri(lo[i], lo[j], hi[j], side, cx, -h / 2, cz);
            BuildSoulAltar.tri(lo[i], hi[j], hi[i], side, cx, -h / 2, cz);
        }
    }

    /** n-sided column segment with alternating side shades (reads as fluting). */
    static void fluted(int n, double r0, double y0, double r1, double y1, short a, short b) {
        double phase = Math.PI / n;
        int[] lo = BuildSoulAltar.ring(n, r0, y0, phase, 0, 0), hi = BuildSoulAltar.ring(n, r1, y1, phase, 0, 0);
        double my = (y0 + y1) / 2;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            short c = i % 2 == 0 ? a : b;
            BuildSoulAltar.tri(lo[i], lo[j], hi[j], c, 0, my, 0);
            BuildSoulAltar.tri(lo[i], hi[j], hi[i], c, 0, my, 0);
        }
    }

    static ModelDefinition tile() {
        reset();
        double h = 64, g = 2.5, y = 8;
        // grout bed under the four slabs, then the slabs (2x2, slightly different shades so the floor isn't a flat grid)
        slab(-h, -h, h, h, y - 1.5, GROUT, SLATE_D);
        short[] shade = {SLATE, SLATE_L, SLATE_L, SLATE};
        for (int i = 0; i < 4; i++) {
            double x0 = (i & 1) == 0 ? -h + g : g / 2, x1 = (i & 1) == 0 ? -g / 2 : h - g;
            double z0 = (i & 2) == 0 ? -h + g : g / 2, z1 = (i & 2) == 0 ? -g / 2 : h - g;
            slab(x0, z0, x1, z1, y, shade[i], SLATE_D);
        }
        return BuildSoulAltar.toModel(TILE_MODEL);
    }

    static ModelDefinition step() {
        reset();
        slab(-64, -64, 64, 64, 2.5, GROUT, SLATE_D);
        slab(-62, -62, 62, 62, 4, SLATE, SLATE_D);
        return BuildSoulAltar.toModel(STEP_MODEL);
    }

    static ModelDefinition apron() {
        reset();
        shard(new double[][]{{-60, -58}, {-6, -62}, {-2, -10}, {-54, -2}}, 2, SLATE_D, GROUT);
        shard(new double[][]{{6, -50}, {58, -60}, {54, 4}, {10, 0}}, 2, SLATE, GROUT);
        shard(new double[][]{{-48, 10}, {-4, 6}, {-14, 50}, {-50, 44}}, 2, SLATE, GROUT);
        shard(new double[][]{{18, 16}, {44, 22}, {36, 46}, {14, 40}}, 1.5, SLATE_D, GROUT);
        return BuildSoulAltar.toModel(APRON_MODEL);
    }

    static ModelDefinition pillar() {
        reset();
        // stepped plinth
        BuildSoulAltar.taper(0, 0, PLINTH, 0, 0, 0, PLINTH - 2, -12, STONE_D, STONE_L);
        BuildSoulAltar.taper(0, 0, PLINTH - 6, -12, 0, 0, PLINTH - 8, -24, STONE, STONE_L);
        BuildSoulAltar.frustum(16, 20, -24, 16, -32, BuildSoulAltar.GOLD, (short) 0, (short) 0, 0, 0); // base torus
        // fluted shaft, a gentle taper, with collars and the rune band
        fluted(16, 14, -32, 13, -120, STONE, STONE_L);
        BuildSoulAltar.tex = 261;
        BuildSoulAltar.frustum(16, 14.5, -120, 14, -136, BuildSoulAltar.GLOW, (short) 0, (short) 0, 0, 0);
        BuildSoulAltar.tex = -1;
        BuildSoulAltar.frustum(16, 15.5, -136, 15.5, -140, BuildSoulAltar.GOLD, (short) 0, (short) 0, 0, 0);
        fluted(16, 13, -140, 11.5, -268, STONE, STONE_L);
        BuildSoulAltar.frustum(16, 14, -268, 14, -273, BuildSoulAltar.GOLD, (short) 0, (short) 0, 0, 0);
        // flared capital and abacus
        BuildSoulAltar.frustum(16, 12, -273, 22, -290, STONE_L, (short) 0, (short) 0, 0, 0);
        BuildSoulAltar.taper(0, 0, 24, -290, 0, 0, 24, -300, STONE_D, STONE_L);
        BuildSoulAltar.frustum(16, 25, -300, 25, -303, BuildSoulAltar.GOLD, (short) 0, BuildSoulAltar.GOLD_L, 0, 0);
        // a floating soul crystal and two shards circling it
        BuildSoulAltar.tex = 261;
        BuildSoulAltar.crystal(-360, -334, -314, 9, BuildSoulAltar.GLOW, 0);
        BuildSoulAltar.octahedron(16, -326, 0, 3, 5, BuildSoulAltar.GLOW);
        BuildSoulAltar.octahedron(-16, -342, 0, 3, 5, BuildSoulAltar.GLOW);
        BuildSoulAltar.tex = -1;
        return BuildSoulAltar.toModel(PILLAR_MODEL);
    }

    static ModelDefinition balustrade() {
        reset();
        double end = 64 + (64 - PLINTH); // into the neighbouring tile, up to a pillar's plinth
        BuildSoulAltar.box(-13, 0, -end, 13, -9, end, STONE_D);          // base rail
        BuildSoulAltar.box(-10, -9, -end, 10, -12, end, STONE);           // its moulding
        for (double z = -48; z <= 48; z += 32) {                          // vase balusters
            BuildSoulAltar.label = 0;
            int[] r0 = BuildSoulAltar.ring(8, 4.5, -12, 0, 0, z), r1 = BuildSoulAltar.ring(8, 7.5, -24, 0, 0, z),
                    r2 = BuildSoulAltar.ring(8, 3.5, -40, 0, 0, z), r3 = BuildSoulAltar.ring(8, 5, -46, 0, 0, z);
            int[][] rings = {r0, r1, r2, r3};
            for (int k = 0; k < 3; k++)
                for (int i = 0; i < 8; i++) {
                    int j = (i + 1) % 8;
                    short c = k == 1 ? STONE_L : STONE;
                    BuildSoulAltar.tri(rings[k][i], rings[k][j], rings[k + 1][j], c, 0, -29, z);
                    BuildSoulAltar.tri(rings[k][i], rings[k + 1][j], rings[k + 1][i], c, 0, -29, z);
                }
        }
        BuildSoulAltar.box(-11, -46, -end, 11, -54, end, STONE);           // top rail
        BuildSoulAltar.box(-12, -54, -end, 12, -57, end, BuildSoulAltar.GOLD); // gold cap
        BuildSoulAltar.tex = 261;                                          // cyan inlay along the top
        int a = BuildSoulAltar.v(-3, -57.5, -end), b = BuildSoulAltar.v(3, -57.5, -end), c = BuildSoulAltar.v(3, -57.5, end), d = BuildSoulAltar.v(-3, -57.5, end);
        BuildSoulAltar.tri(a, b, c, BuildSoulAltar.GLOW, 0, 0, 0);
        BuildSoulAltar.tri(a, c, d, BuildSoulAltar.GLOW, 0, 0, 0);
        BuildSoulAltar.tex = -1;
        return BuildSoulAltar.toModel(RAIL_MODEL);
    }

    static byte[] solidLoc(int model, String name) {
        OutputStream o = new OutputStream();
        o.writeByte(1); o.writeByte(1); o.writeShort(model); o.writeByte(10);
        o.writeByte(2); o.writeString(name);
        o.writeByte(19); o.writeByte(0);
        o.writeByte(0);
        return o.flip();
    }

    static byte[] floorLoc(int model, String name) {
        OutputStream o = new OutputStream();
        o.writeByte(1); o.writeByte(1); o.writeShort(model); o.writeByte(22);
        o.writeByte(2); o.writeString(name);
        o.writeByte(17); // walk-through
        o.writeByte(21); // contoured to the terrain
        o.writeByte(0);
        return o.flip();
    }

    public static void main(String[] args) throws Exception {
        boolean apply = args[0].equals("apply");
        File previewDir = new File(args[2]), outDir = new File(args[3]);
        previewDir.mkdirs();
        outDir.mkdirs();
        try (Store store = new Store(new File(args[1]))) {
            store.load();
            Storage storage = store.getStorage();
            Index models = store.getIndex(IndexType.MODELS), cfg = store.getIndex(IndexType.CONFIGS);
            Archive locArc = cfg.getArchive(ConfigType.OBJECT.getId());
            Map<Integer, byte[]> fresh = new TreeMap<>();
            for (ModelDefinition m : new ModelDefinition[]{pillar(), balustrade(), step(), apron(), tile()}) {
                byte[] enc = ModelType3Encoder.encode(m);
                ModelDefinition back = new ModelLoader().load(m.id, enc);
                if (back.faceCount != m.faceCount || !Arrays.equals(back.faceTextures, m.faceTextures))
                    throw new IllegalStateException("model " + m.id + " round trip");
                System.out.printf("model %d: %d verts, %d faces%n", m.id, m.vertexCount, m.faceCount);
                ModelDefinition pv = new ModelLoader().load(m.id, enc);
                pv.faceTextures = null;
                ImageIO.write(BuildChampionshipBoard.preview(pv, new HashMap<>(), 30, 16), "png",
                        new File(previewDir, "pavilion_" + m.id + ".png"));
                if (m.id == TILE_MODEL)
                    java.nio.file.Files.write(new File(outDir, m.id + ".dat").toPath(), enc);
                else {
                    if (models.getArchive(m.id) != null) throw new IllegalStateException("model " + m.id + " exists");
                    fresh.put(m.id, enc);
                }
            }
            Map<Integer, byte[]> locs = new TreeMap<>(Map.of(
                    PILLAR_LOC, solidLoc(PILLAR_MODEL, "Sanctuary pillar"),
                    RAIL_LOC, solidLoc(RAIL_MODEL, "Sanctuary balustrade"),
                    STEP_LOC, floorLoc(STEP_MODEL, "Dais step"),
                    APRON_LOC, floorLoc(APRON_MODEL, "Slate path")));
            Set<Integer> ids = BuildLocSpinAnims.ids(locArc);
            for (Map.Entry<Integer, byte[]> e : locs.entrySet()) {
                if (ids.contains(e.getKey())) throw new IllegalStateException("loc " + e.getKey() + " exists");
                ObjectDefinition od = new ObjectLoader().load(e.getKey(), e.getValue());
                System.out.printf("loc %d \"%s\" %dx%d interact=%d%n", e.getKey(), od.getName(), od.getSizeX(), od.getSizeY(), od.getInteractType());
            }
            if (!apply) { System.out.println("VERIFY OK (dry run)"); return; }
            for (Map.Entry<Integer, byte[]> e : fresh.entrySet())
                BuildLocSpinAnims.addArchive(storage, models, e.getKey(), List.of(e.getValue()), new int[]{0}, 0);
            BuildLocSpinAnims.writeRefTable(storage, models);
            BuildFortuneStatue.insertSorted(storage, cfg, locArc, locs);
            BuildLocSpinAnims.writeRefTable(storage, cfg);
            System.out.println("APPLY OK -- flagstone model in " + outDir + " for InsertRawModels replace");
        }
    }
}
