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

// The Soulbound Sanctuary room (2026-10-18 round 2), built from code with BuildSoulAltar's builder:
//   Sanctuary wall    loc 62448 / model 61243, 1x1 solid block: ancient dark stone with a plinth, block courses, a flowing
//                     cyan rune band (texture 261) on all four faces, a gold string course and an overhanging capstone
//   Sanctuary archway loc 62449 / model 61244, 3x1 WALK-THROUGH: an arched lintel over the 3-tile entrance, resting on
//                     the wall posts either side, with a gold rim and a glowing keystone
//   Arcane flagstone  model 61242 (loc 62447, rebuilt in place): polished obsidian with glowing cyan grout along its
//                     edges (texture 261, so the floor reads as a lit grid) and a small rune at its centre
// Usage: verify|apply <cachePath> <previewDir> <outDir>   (apply adds the wall / arch models + locs; the flagstone model is
// written to <outDir> for InsertRawModels replace)
public class BuildSanctuaryRoom {
    static final int WALL_LOC = 62448, WALL_MODEL = 61243, ARCH_LOC = 62449, ARCH_MODEL = 61244, TILE_MODEL = 61242;
    static short hsl(int h, int s, int l) { return BuildSoulAltar.hsl(h, s, l); }
    static final short STONE = hsl(38, 1, 22), STONE_L = hsl(38, 1, 30), STONE_D = hsl(38, 1, 14), MORTAR = hsl(38, 1, 9);

    static void reset() {
        BuildSoulAltar.verts.clear();
        BuildSoulAltar.faces.clear();
        BuildSoulAltar.label = 0;
        BuildSoulAltar.tex = -1;
    }

    static ModelDefinition wall() {
        reset();
        double h = 64;
        BuildSoulAltar.taper(0, 0, h, 0, 0, 0, h, -14, STONE_D, STONE_L);              // plinth
        BuildSoulAltar.taper(0, 0, h - 4, -14, 0, 0, h - 4, -60, STONE, STONE_L);      // lower course
        BuildSoulAltar.taper(0, 0, h - 3, -60, 0, 0, h - 3, -63, MORTAR, MORTAR);      // mortar line
        BuildSoulAltar.taper(0, 0, h - 4, -63, 0, 0, h - 4, -92, STONE, STONE_L);      // middle course
        BuildSoulAltar.tex = 261;
        BuildSoulAltar.taper(0, 0, h - 3, -92, 0, 0, h - 3, -100, BuildSoulAltar.GLOW, BuildSoulAltar.GLOW); // rune band
        BuildSoulAltar.tex = -1;
        BuildSoulAltar.taper(0, 0, h - 4, -100, 0, 0, h - 4, -150, STONE, STONE_L);    // upper course
        BuildSoulAltar.taper(0, 0, h - 2, -150, 0, 0, h - 2, -155, BuildSoulAltar.GOLD, BuildSoulAltar.GOLD_L); // string course
        BuildSoulAltar.taper(0, 0, h - 4, -155, 0, 0, h - 4, -176, STONE, STONE_L);
        BuildSoulAltar.taper(0, 0, h + 2, -176, 0, 0, h, -186, STONE_D, STONE_L);     // capstone
        // carved glyphs in the rune band's middle, one per face
        for (int i = 0; i < 4; i++)
            BuildSoulAltar.glyph(Math.PI / 2 * i, h - 3, -120, 6, BuildSoulAltar.GLOW);
        return BuildSoulAltar.toModel(WALL_MODEL);
    }

    static ModelDefinition arch() {
        reset();
        double half = 256, d = 60; // the beam reaches the centres of the posts either side (the arch spans 3 tiles)
        // lintel beam and its gold rim
        BuildSoulAltar.box(-half, -176, -d, half, -150, d, STONE);
        BuildSoulAltar.box(-half, -186, -d - 4, half, -176, d + 4, STONE_D);
        BuildSoulAltar.box(-half, -150, -d - 2, half, -146, d + 2, BuildSoulAltar.GOLD);
        // the arch: voussoir blocks along a half ellipse under the beam, from post to post
        int n = 9;
        for (int i = 0; i < n; i++) {
            double a0 = Math.PI * i / n, a1 = Math.PI * (i + 1) / n;
            double x0 = -192 * Math.cos(a0), y0 = -96 - 50 * Math.sin(a0), x1 = -192 * Math.cos(a1), y1 = -96 - 50 * Math.sin(a1);
            int p0 = BuildSoulAltar.v(x0, y0, -d), p1 = BuildSoulAltar.v(x1, y1, -d), p2 = BuildSoulAltar.v(x1, -146, -d), p3 = BuildSoulAltar.v(x0, -146, -d);
            int q0 = BuildSoulAltar.v(x0, y0, d), q1 = BuildSoulAltar.v(x1, y1, d), q2 = BuildSoulAltar.v(x1, -146, d), q3 = BuildSoulAltar.v(x0, -146, d);
            short c = i % 2 == 0 ? STONE : STONE_L;
            double cx = (x0 + x1) / 2, cy = (y0 - 146) / 2;
            BuildSoulAltar.tri(p0, p1, p2, c, cx, cy, 0); BuildSoulAltar.tri(p0, p2, p3, c, cx, cy, 0);
            BuildSoulAltar.tri(q0, q1, q2, c, cx, cy, 0); BuildSoulAltar.tri(q0, q2, q3, c, cx, cy, 0);
            BuildSoulAltar.tri(p0, p1, q1, STONE_D, cx, -200, 0); BuildSoulAltar.tri(p0, q1, q0, STONE_D, cx, -200, 0); // intrados
        }
        // springers down the posts' faces
        for (int s = -1; s <= 1; s += 2)
            BuildSoulAltar.box(s * 192 - 8, -96, -d, s * 192 + 8, 0, d, STONE);
        // keystone with a glowing rune
        BuildSoulAltar.box(-16, -160, -d - 6, 16, -140, d + 6, BuildSoulAltar.GOLD);
        BuildSoulAltar.tex = 261;
        BuildSoulAltar.octahedron(0, -150, -d - 9, 7, 9, BuildSoulAltar.GLOW);
        BuildSoulAltar.octahedron(0, -150, d + 9, 7, 9, BuildSoulAltar.GLOW);
        BuildSoulAltar.tex = -1;
        return BuildSoulAltar.toModel(ARCH_MODEL);
    }

    static ModelDefinition tile() {
        reset();
        short slab = hsl(40, 1, 9), sheen = hsl(40, 1, 15);
        double h = 64, g = 3, y0 = 0, y1 = -2;
        // polished slab: a darker rim and a slightly lighter centre panel (a sheen), all a hair above the ground
        int[] outer = {BuildSoulAltar.v(-h + g, y1, -h + g), BuildSoulAltar.v(h - g, y1, -h + g), BuildSoulAltar.v(h - g, y1, h - g), BuildSoulAltar.v(-h + g, y1, h - g)};
        int[] inner = {BuildSoulAltar.v(-40, y1 - 0.5, -40), BuildSoulAltar.v(40, y1 - 0.5, -40), BuildSoulAltar.v(40, y1 - 0.5, 40), BuildSoulAltar.v(-40, y1 - 0.5, 40)};
        BuildSoulAltar.tri(inner[0], inner[1], inner[2], sheen, 0, 10, 0);
        BuildSoulAltar.tri(inner[0], inner[2], inner[3], sheen, 0, 10, 0);
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4;
            BuildSoulAltar.tri(outer[i], outer[j], inner[j], slab, 0, 10, 0);
            BuildSoulAltar.tri(outer[i], inner[j], inner[i], slab, 0, 10, 0);
        }
        // glowing grout along the four edges (half a line each: the neighbour's half completes it)
        BuildSoulAltar.tex = 261;
        double[][] strips = {{-h, -h, h, -h + g}, {-h, h - g, h, h}, {-h, -h + g, -h + g, h - g}, {h - g, -h + g, h, h - g}};
        for (double[] st : strips) {
            int a = BuildSoulAltar.v(st[0], y0 - 1, st[1]), b = BuildSoulAltar.v(st[2], y0 - 1, st[1]),
                    c = BuildSoulAltar.v(st[2], y0 - 1, st[3]), d = BuildSoulAltar.v(st[0], y0 - 1, st[3]);
            BuildSoulAltar.tri(a, b, c, BuildSoulAltar.GLOW_D, 0, 10, 0);
            BuildSoulAltar.tri(a, c, d, BuildSoulAltar.GLOW_D, 0, 10, 0);
        }
        // a small rune star at the centre
        double y = y1 - 1;
        int c0 = BuildSoulAltar.v(0, y, -8), c1 = BuildSoulAltar.v(8, y, 0), c2 = BuildSoulAltar.v(0, y, 8), c3 = BuildSoulAltar.v(-8, y, 0);
        BuildSoulAltar.tri(c0, c1, c2, BuildSoulAltar.GLOW, 0, 10, 0);
        BuildSoulAltar.tri(c0, c2, c3, BuildSoulAltar.GLOW, 0, 10, 0);
        BuildSoulAltar.tex = -1;
        return BuildSoulAltar.toModel(TILE_MODEL);
    }

    static byte[] wallLoc() {
        OutputStream o = new OutputStream();
        o.writeByte(1); o.writeByte(1); o.writeShort(WALL_MODEL); o.writeByte(10);
        o.writeByte(2); o.writeString("Sanctuary wall");
        o.writeByte(19); o.writeByte(0);
        o.writeByte(0);
        return o.flip();
    }

    static byte[] archLoc() {
        OutputStream o = new OutputStream();
        o.writeByte(1); o.writeByte(1); o.writeShort(ARCH_MODEL); o.writeByte(10);
        o.writeByte(2); o.writeString("Sanctuary archway");
        o.writeByte(14); o.writeByte(3);
        o.writeByte(15); o.writeByte(1);
        o.writeByte(17); // walk-through: the entrance is open
        o.writeByte(19); o.writeByte(0);
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
            for (ModelDefinition m : new ModelDefinition[]{wall(), arch(), tile()}) {
                byte[] enc = ModelType3Encoder.encode(m);
                ModelDefinition back = new ModelLoader().load(m.id, enc);
                if (back.faceCount != m.faceCount || !Arrays.equals(back.faceTextures, m.faceTextures))
                    throw new IllegalStateException("model " + m.id + " round trip");
                System.out.printf("model %d: %d verts, %d faces%n", m.id, m.vertexCount, m.faceCount);
                ModelDefinition pv = new ModelLoader().load(m.id, enc);
                pv.faceTextures = null;
                ImageIO.write(BuildChampionshipBoard.preview(pv, new HashMap<>(), 30, m.id == TILE_MODEL ? 55 : 16), "png",
                        new File(previewDir, "room_" + m.id + ".png"));
                if (m.id == TILE_MODEL)
                    java.nio.file.Files.write(new File(outDir, m.id + ".dat").toPath(), enc);
                else {
                    if (models.getArchive(m.id) != null) throw new IllegalStateException("model " + m.id + " exists");
                    fresh.put(m.id, enc);
                }
            }
            Map<Integer, byte[]> locs = new TreeMap<>(Map.of(WALL_LOC, wallLoc(), ARCH_LOC, archLoc()));
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
