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

// The compact Soul Sanctuary dais (2026-10-18 round 9): the 9x9 pavilion read as an empty slab, so the sanctuary is one
// 5x5 marble platform under the altar with a pillar on each corner (BuildSanctuaryPavilion's 62450).
//   Sanctuary dais  loc 62454 / model 61249: ONE model spanning 5x5 tiles (640 x 640), spawned as a ground decoration
//                   on the centre tile so it can share tiles with the altar and pillars. It is NOT contoured to the
//                   terrain: the per-tile flagstones were, and bending each tile to the slope split its two triangles
//                   into different shades (the checker the user saw). Pale ancient marble in 25 large flagstones with
//                   fine seams, a darker bevelled plinth, a gold border and a flowing cyan inlay (texture 261).
// Usage: verify|apply <cachePath> <previewDir>
public class BuildSanctuaryDais {
    static final int DAIS_LOC = 62454, DAIS_MODEL = 61249;
    static final double HALF = 320, H = 6; // low, so the altar's base tier stays in view

    static short hsl(int h, int s, int l) { return BuildSoulAltar.hsl(h, s, l); }
    static final short MARBLE = hsl(36, 1, 70), MARBLE_L = hsl(36, 1, 78), MARBLE_D = hsl(36, 1, 58),
            SEAM = hsl(36, 1, 44), PLINTH = hsl(36, 1, 40), PLINTH_D = hsl(36, 1, 30);

    static void quad(double x0, double z0, double x1, double z1, double y, short c) {
        int a = BuildSoulAltar.v(x0, y, z0), b = BuildSoulAltar.v(x1, y, z0), d = BuildSoulAltar.v(x1, y, z1), e = BuildSoulAltar.v(x0, y, z1);
        BuildSoulAltar.tri(a, b, d, c, (x0 + x1) / 2, 0, (z0 + z1) / 2);
        BuildSoulAltar.tri(a, d, e, c, (x0 + x1) / 2, 0, (z0 + z1) / 2);
    }

    static ModelDefinition dais() {
        BuildSoulAltar.verts.clear();
        BuildSoulAltar.faces.clear();
        BuildSoulAltar.label = 0;
        BuildSoulAltar.tex = -1;
        // plinth: a slab from the ground up to the top, its sides darker, a slight bevel at the rim
        BuildSoulAltar.taper(0, 0, HALF + 4, 2, 0, 0, HALF, -H + 2, PLINTH_D, PLINTH);
        BuildSoulAltar.taper(0, 0, HALF, -H + 2, 0, 0, HALF - 3, -H, PLINTH, SEAM);
        // 25 flagstones, one per tile, raised a hair above the seam bed; shades alternate gently, no pattern inside
        double top = -H - 0.6, g = 2.5;
        for (int i = 0; i < 5; i++)
            for (int j = 0; j < 5; j++) {
                double x0 = -HALF + 3 + i * 126.8 + g, z0 = -HALF + 3 + j * 126.8 + g;
                short c = (i * 3 + j * 7) % 3 == 0 ? MARBLE_L : (i + j) % 2 == 0 ? MARBLE : MARBLE_D;
                quad(x0, z0, x0 + 126.8 - 2 * g, z0 + 126.8 - 2 * g, top, c);
            }
        // gold border along the rim and a flowing cyan inlay just inside it
        double y = top - 0.6, b = HALF - 3;
        quad(-b, -b, b, -b + 8, y, BuildSoulAltar.GOLD);
        quad(-b, b - 8, b, b, y, BuildSoulAltar.GOLD);
        quad(-b, -b + 8, -b + 8, b - 8, y, BuildSoulAltar.GOLD);
        quad(b - 8, -b + 8, b, b - 8, y, BuildSoulAltar.GOLD);
        BuildSoulAltar.tex = 261;
        double c = b - 14;
        quad(-c, -c, c, -c + 3, y, BuildSoulAltar.GLOW);
        quad(-c, c - 3, c, c, y, BuildSoulAltar.GLOW);
        quad(-c, -c + 3, -c + 3, c - 3, y, BuildSoulAltar.GLOW);
        quad(c - 3, -c + 3, c, c - 3, y, BuildSoulAltar.GLOW);
        BuildSoulAltar.tex = -1;
        // layers, so nothing depends on depth sorting inside this flat model: the plinth (and its top, the seam bed)
        // under the flagstones, the gold border and cyan inlay over them
        for (int[] f : BuildSoulAltar.faces) {
            short col = (short) f[3];
            f[5] = col == PLINTH || col == PLINTH_D || col == SEAM ? 0 : col == BuildSoulAltar.GOLD || col == BuildSoulAltar.GLOW ? 2 : 1;
        }
        return BuildSoulAltar.toModel(DAIS_MODEL);
    }

    static byte[] daisLoc() {
        OutputStream o = new OutputStream();
        o.writeByte(1); o.writeByte(1); o.writeShort(DAIS_MODEL); o.writeByte(22);
        o.writeByte(2); o.writeString("Sanctuary dais");
        o.writeByte(17); // walk-through (no opcode 21: kept flat, not bent to the terrain)
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
            ModelDefinition m = dais();
            byte[] enc = ModelType3Encoder.encode(m);
            ModelDefinition back = new ModelLoader().load(m.id, enc);
            if (back.faceCount != m.faceCount || !Arrays.equals(back.faceTextures, m.faceTextures))
                throw new IllegalStateException("model round trip");
            System.out.printf("model %d: %d verts, %d faces%n", m.id, m.vertexCount, m.faceCount);
            ModelDefinition pv = new ModelLoader().load(m.id, enc);
            pv.faceTextures = null;
            ImageIO.write(BuildChampionshipBoard.preview(pv, new HashMap<>(), 30, 40), "png", new File(previewDir, "dais_" + m.id + ".png"));
            if (models.getArchive(m.id) != null) throw new IllegalStateException("model " + m.id + " exists");
            if (BuildLocSpinAnims.ids(locArc).contains(DAIS_LOC)) throw new IllegalStateException("loc " + DAIS_LOC + " exists");
            ObjectDefinition od = new ObjectLoader().load(DAIS_LOC, daisLoc());
            System.out.printf("loc %d \"%s\" interact=%d%n", DAIS_LOC, od.getName(), od.getInteractType());
            if (!apply) { System.out.println("VERIFY OK (dry run)"); return; }
            BuildLocSpinAnims.addArchive(storage, models, m.id, List.of(enc), new int[]{0}, 0);
            BuildLocSpinAnims.writeRefTable(storage, models);
            BuildFortuneStatue.insertSorted(storage, cfg, locArc, new TreeMap<>(Map.of(DAIS_LOC, daisLoc())));
            BuildLocSpinAnims.writeRefTable(storage, cfg);
            System.out.println("APPLY OK");
        }
    }
}
