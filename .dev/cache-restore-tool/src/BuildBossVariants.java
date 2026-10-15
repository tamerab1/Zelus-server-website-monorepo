import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.TextureManager;
import net.runelite.cache.definitions.ItemDefinition;
import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.NpcDefinition;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.definitions.loaders.NpcLoader;
import net.runelite.cache.definitions.providers.ModelProvider;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;
import net.runelite.cache.item.ItemSpriteFactory;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.util.*;

// Boss variants (2026-10-13): recoloured + textured clones of existing monsters with new names, plus
// themed copies of the spotanims their special attacks use. Reuses BuildTierPets' helpers.
// Spec lines:
//   theme <name> <pHue> <pSat> <pMul> <pAdd> <aHue> <aSat> <aMul> <aAdd> <accentSat> <accentLum>   (GenThemeRecolors)
//   boss <key> <srcNpc> <newNpc> <theme> <texture> <fraction> <firstModel> <scale> <combatLevel> <name...>
//        one textured model per source model, ids firstModel, firstModel+1, ...; scale = 128-based width/height
//   gfx <srcSpotanim> <newSpotanim> <theme> <texture|-> <model|->   texture: every face of a copy of the effect model
// Modes (cache only written by "npcs apply" / "gfx apply"):
//   peek <cache> <out.png> <spotanim...>         renders existing spotanims (to pick sources)
//   texmodels <cache> <spec> <outDir>            textured boss models + textured gfx models (for InsertRawModels)
//   npcs verify|apply <cache> <spec>
//   gfx verify|apply <cache> <spec>
//   sheet <cache> <spec> <out.png>               renders the REAL boss npcs + gfx now in the cache (source | new)
public class BuildBossVariants {
    record Boss(String key, int src, int npc, String theme, int texture, double fraction, int firstModel, int scale, int level, String name) {}
    record Gfx(int src, int id, String theme, int texture, int model) {}

    static Map<String, BuildTierPets.Theme> themes = new LinkedHashMap<>();
    static List<Boss> bosses = new ArrayList<>();
    static List<Gfx> gfxs = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        String mode = args[0];
        if (mode.equals("peek")) {
            try (Store store = new Store(new File(args[1]))) {
                store.load();
                int[] ids = Arrays.stream(args, 3, args.length).mapToInt(Integer::parseInt).toArray();
                peek(store, args[2], ids);
            }
            return;
        }
        boolean twoArg = mode.equals("npcs") || mode.equals("gfx");
        String sub = twoArg ? args[1] : null;
        String cache = args[twoArg ? 2 : 1];
        readSpec(args[twoArg ? 3 : 2]);
        try (Store store = new Store(new File(cache))) {
            store.load();
            switch (mode) {
                case "texmodels" -> texModels(store, new File(args[3]));
                case "npcs" -> npcs(store, sub.equals("apply"));
                case "gfx" -> gfx(store, sub.equals("apply"));
                case "sheet" -> sheet(store, args[3]);
                default -> throw new IllegalArgumentException(mode);
            }
        }
    }

    static void readSpec(String path) throws Exception {
        for (String l : Files.readAllLines(new File(path).toPath())) {
            l = l.trim();
            if (l.isEmpty() || l.startsWith("#")) continue;
            String[] p = l.split("\\s+");
            switch (p[0]) {
                case "theme" -> themes.put(p[1], new BuildTierPets.Theme(i(p[2]), i(p[3]), Double.parseDouble(p[4]), i(p[5]), i(p[6]),
                        i(p[7]), Double.parseDouble(p[8]), i(p[9]), i(p[10]), i(p[11])));
                case "boss" -> {
                    String[] q = l.split("\\s+", 11);
                    bosses.add(new Boss(q[1], i(q[2]), i(q[3]), q[4], i(q[5]), Double.parseDouble(q[6]), i(q[7]), i(q[8]), i(q[9]), q[10]));
                }
                case "gfx" -> gfxs.add(new Gfx(i(p[1]), i(p[2]), p[3], p[4].equals("-") ? -1 : i(p[4]), p[5].equals("-") ? -1 : i(p[5])));
                default -> throw new IllegalArgumentException("bad spec line: " + l);
            }
        }
        for (Boss b : bosses) BuildTierPets.check(themes.containsKey(b.theme), "theme " + b.theme);
        for (Gfx g : gfxs) BuildTierPets.check(themes.containsKey(g.theme) && (g.texture < 0) == (g.model < 0), "gfx " + g.id);
    }

    static BuildTierPets.Pet asPet(Boss b) {
        return new BuildTierPets.Pet(b.key, b.src, b.scale, 0, 0, 0, 0, 0, -1);
    }

    /* ---------------- models ---------------- */

    static void texModels(Store store, File dir) throws Exception {
        dir.mkdirs();
        for (Boss b : bosses) {
            NpcDefinition d = BuildTierPets.npc(store, b.src);
            for (int k = 0; k < d.models.length; k++) {
                int id = b.firstModel + k;
                ModelDefinition m = BuildTierPets.textured(BuildTierPets.model(store, d.models[k]), b.texture, b.fraction);
                // multi-part npcs (the death spawns' body kits) can have a small part with nothing to texture
                write(dir, id, m, d.models.length == 1 ? b.texture : -1);
                System.out.println(b.name + ": model " + d.models[k] + " -> " + id);
            }
        }
        List<byte[]> spots = spotContents(store);
        for (Gfx g : gfxs) {
            if (g.model < 0) continue;
            int src = BuildTierPets.spot(spots.get(spotSlot(store, g.src)), g.src).modelId;
            write(dir, g.model, BuildTierPets.texturedAll(BuildTierPets.model(store, src), g.texture), g.texture);
            System.out.println("gfx " + g.id + ": model " + src + " -> " + g.model);
        }
    }

    static void write(File dir, int id, ModelDefinition m, int texture) throws Exception {
        byte[] enc = ModelType3Encoder.encode(m);
        ModelDefinition back = new ModelLoader().load(id, enc);
        BuildTierPets.check(back.vertexCount == m.vertexCount && back.faceCount == m.faceCount, "round trip " + id);
        int[] backSkins = back.packedVertexGroups != null ? back.packedVertexGroups
                : back.getVertexGroups() == null ? null : TextureModelFaces.packVertexGroups(back.getVertexGroups(), back.vertexCount);
        BuildTierPets.check(m.packedVertexGroups == null || Arrays.equals(backSkins, m.packedVertexGroups), "skins lost " + id);
        int tex = 0;
        for (int f = 0; f < back.faceCount; f++) if (back.faceTextures != null && back.faceTextures[f] == texture) tex++;
        BuildTierPets.check(texture < 0 || tex > 0, "no textured faces " + id);
        Files.write(new File(dir, id + ".dat").toPath(), enc);
    }

    /* ---------------- npcs ---------------- */

    static void npcs(Store store, boolean apply) throws Exception {
        Index index = store.getIndex(IndexType.CONFIGS);
        Archive archive = index.getArchive(ConfigType.NPC.getId());
        FileData[] fileData = archive.getFileData();
        List<byte[]> contents = new ArrayList<>(SpliceItemOption.splitChunks(archive.decompress(store.getStorage().loadArchive(archive)), fileData.length));
        List<Integer> ids = new ArrayList<>(), hashes = new ArrayList<>();
        for (FileData fd : fileData) { ids.add(fd.getId()); hashes.add(fd.getNameHash()); }

        for (Boss b : bosses) {
            BuildTierPets.check(!ids.contains(b.npc), "npc " + b.npc + " already exists");
            int srcSlot = ids.indexOf(b.src);
            BuildTierPets.check(srcSlot >= 0, "source npc " + b.src);
            byte[] w = contents.get(srcSlot);
            NpcDefinition before = new NpcLoader().load(b.src, w);
            for (int op : new int[]{40, 41, 102}) w = EditNpcInPlace.removeOpcode(w, op);
            w = SplicePetify.replaceOrInsert(w, 2, SplicePetify.withTerminator(b.name));
            w = SplicePetify.replaceOrInsert(w, 97, SplicePetify.twoBytes(b.scale));
            w = SplicePetify.replaceOrInsert(w, 98, SplicePetify.twoBytes(b.scale));
            w = SplicePetify.replaceOrInsert(w, 95, SplicePetify.twoBytes(b.level));
            int[] models = new int[before.models.length];
            byte[] mb = new byte[1 + models.length * 2];
            mb[0] = (byte) models.length;
            for (int k = 0; k < models.length; k++) {
                models[k] = b.firstModel + k;
                mb[1 + k * 2] = (byte) (models[k] >> 8);
                mb[2 + k * 2] = (byte) models[k];
            }
            w = SplicePetify.replaceOrInsert(w, 1, mb);
            short[][] rc = BuildTierPets.recolor(store, asPet(b), themes.get(b.theme));
            if (rc[0].length > 0) {
                byte[] cb = new byte[1 + rc[0].length * 4];
                cb[0] = (byte) rc[0].length;
                for (int k = 0; k < rc[0].length; k++) {
                    cb[1 + k * 4] = (byte) (rc[0][k] >> 8); cb[2 + k * 4] = (byte) rc[0][k];
                    cb[3 + k * 4] = (byte) (rc[1][k] >> 8); cb[4 + k * 4] = (byte) rc[1][k];
                }
                w = SplicePetify.replaceOrInsert(w, 40, cb);
            }
            NpcDefinition after = new NpcLoader().load(b.npc, w);
            BuildTierPets.check(b.name.equals(after.name), "name");
            BuildTierPets.check(after.size == before.size && after.widthScale == b.scale && after.heightScale == b.scale, "size/scale");
            BuildTierPets.check(after.standingAnimation == before.standingAnimation && after.walkingAnimation == before.walkingAnimation, "anims");
            BuildTierPets.check(Arrays.equals(after.actions, before.actions), "actions " + Arrays.toString(after.actions));
            BuildTierPets.check(after.combatLevel == b.level, "combat level");
            BuildTierPets.check(Arrays.equals(after.models, models), "models");
            BuildTierPets.check(rc[0].length == 0 || Arrays.equals(after.recolorToFind, rc[0]) && Arrays.equals(after.recolorToReplace, rc[1]), "recolor");

            int insertAt = ids.size();
            for (int k = 0; k < ids.size(); k++) if (ids.get(k) > b.npc) { insertAt = k; break; }
            contents.add(insertAt, w);
            ids.add(insertAt, b.npc);
            hashes.add(insertAt, -1);
            System.out.printf("%s npc %d \"%s\" (lvl %d) from %d: scale %d, %d recolours, models %s%n",
                    apply ? "ADD" : "WOULD ADD", b.npc, b.name, b.level, b.src, b.scale, rc[0].length, Arrays.toString(models));
        }
        if (!apply) { System.out.println("VERIFY mode -- not writing."); return; }
        archive.setFileData(fileData(ids, hashes));
        BuildTierPets.writeArchive(store, index, archive, SpliceItemOption.joinChunks(contents));
        System.out.println("APPLY complete. NPC archive revision now " + archive.getRevision() + ".");
    }

    /* ---------------- spotanims ---------------- */

    static List<byte[]> spotContents(Store store) throws Exception {
        Archive a = store.getIndex(IndexType.CONFIGS).getArchive(ConfigType.SPOTANIM.getId());
        return SpliceItemOption.splitChunks(a.decompress(store.getStorage().loadArchive(a)), a.getFileData().length);
    }

    static int spotSlot(Store store, int id) {
        FileData[] fd = store.getIndex(IndexType.CONFIGS).getArchive(ConfigType.SPOTANIM.getId()).getFileData();
        for (int k = 0; k < fd.length; k++) if (fd[k].getId() == id) return k;
        throw new IllegalStateException("spotanim " + id + " missing");
    }

    static void gfx(Store store, boolean apply) throws Exception {
        Index index = store.getIndex(IndexType.CONFIGS);
        Archive archive = index.getArchive(ConfigType.SPOTANIM.getId());
        FileData[] fileData = archive.getFileData();
        List<byte[]> contents = new ArrayList<>(spotContents(store));
        List<Integer> ids = new ArrayList<>(), hashes = new ArrayList<>();
        for (FileData f : fileData) { ids.add(f.getId()); hashes.add(f.getNameHash()); }
        for (Gfx g : gfxs) {
            BuildTierPets.check(!ids.contains(g.id), "spotanim " + g.id + " already exists");
            int srcSlot = ids.indexOf(g.src);
            BuildTierPets.check(srcSlot >= 0, "spotanim " + g.src);
            byte[] src = contents.get(srcSlot);
            var sd = BuildTierPets.spot(src, g.src);
            Map<Integer, Integer> existing = new HashMap<>();
            if (sd.recolorToFind != null)
                for (int k = 0; k < sd.recolorToFind.length; k++) existing.put(sd.recolorToFind[k] & 0xFFFF, sd.recolorToReplace[k] & 0xFFFF);
            TreeSet<Integer> raw = new TreeSet<>();
            for (short col : BuildTierPets.model(store, sd.modelId).faceColors) raw.add(col & 0xFFFF);
            raw.addAll(existing.keySet());
            BuildTierPets.Theme th = themes.get(g.theme);
            List<int[]> pairs = new ArrayList<>();
            for (int col : raw) {
                int eff = existing.getOrDefault(col, col);
                int nc = GenThemeRecolors.map(eff == 0 ? 24 : eff, new GenThemeRecolors.Theme(th.pHue(), th.pSat(), th.pMul(), th.pAdd(),
                        th.aHue(), th.aSat(), th.aMul(), th.aAdd(), th.accentSat(), th.accentLum()));
                if (nc < 0) nc = eff;
                if (nc != col) pairs.add(new int[]{col, nc});
            }
            byte[] w = BuildTierPets.spotRemove(src, 40);
            w = BuildTierPets.spotRemove(w, 41);
            if (!pairs.isEmpty()) {
                byte[] cb = new byte[1 + pairs.size() * 4];
                cb[0] = (byte) pairs.size();
                for (int k = 0; k < pairs.size(); k++) {
                    cb[1 + k * 4] = (byte) (pairs.get(k)[0] >> 8); cb[2 + k * 4] = (byte) pairs.get(k)[0];
                    cb[3 + k * 4] = (byte) (pairs.get(k)[1] >> 8); cb[4 + k * 4] = (byte) pairs.get(k)[1];
                }
                w = BuildTierPets.spotPut(w, 40, cb);
            }
            int model = g.model >= 0 ? g.model : sd.modelId;
            if (g.model >= 0) w = BuildTierPets.spotPut(w, 1, SplicePetify.twoBytes(model));
            var after = BuildTierPets.spot(w, g.id);
            BuildTierPets.check(after.modelId == model && after.animationId == sd.animationId && after.resizeX == sd.resizeX
                    && after.resizeY == sd.resizeY && after.rotaton == sd.rotaton, "spotanim fields " + g.id);
            int insertAt = ids.size();
            for (int k = 0; k < ids.size(); k++) if (ids.get(k) > g.id) { insertAt = k; break; }
            contents.add(insertAt, w);
            ids.add(insertAt, g.id);
            hashes.add(insertAt, -1);
            System.out.printf("%s spotanim %d from %d (%s): model %d, anim %d, %d recolours%n", apply ? "ADD" : "WOULD ADD",
                    g.id, g.src, g.theme, model, sd.animationId, pairs.size());
        }
        if (!apply) { System.out.println("VERIFY mode -- not writing."); return; }
        archive.setFileData(fileData(ids, hashes));
        BuildTierPets.writeArchive(store, index, archive, SpliceItemOption.joinChunks(contents));
        System.out.println("APPLY complete. Spotanim archive revision now " + archive.getRevision() + ".");
    }

    static FileData[] fileData(List<Integer> ids, List<Integer> hashes) {
        FileData[] nfd = new FileData[ids.size()];
        for (int k = 0; k < ids.size(); k++) {
            nfd[k] = new FileData();
            nfd[k].setId(ids.get(k));
            nfd[k].setNameHash(hashes.get(k));
        }
        return nfd;
    }

    /* ---------------- previews ---------------- */

    static BufferedImage render(Store store, TextureManager textures, int[] models, short[] find, short[] replace, int zoom) throws Exception {
        List<ModelDefinition> ms = new ArrayList<>();
        for (int m : models) ms.add(BuildTierPets.model(store, m));
        ModelDefinition merged = BuildTierPets.merge(ms);
        ItemDefinition def = new ItemDefinition(1);
        def.inventoryModel = 1;
        def.zoom2d = zoom; def.xan2d = 100; def.yan2d = 1800;
        def.resizeX = def.resizeY = def.resizeZ = 128;
        def.colorFind = find; def.colorReplace = replace;
        ModelProvider mp = id -> BuildTierPets.merge(List.of(merged));
        return ItemSpriteFactory.createSprite(id -> def, mp, BuildTierPets.sprites(store), textures, 1, 1, 0, 0x302020, false);
    }

    static int zoomFor(Store store, int[] models) throws Exception {
        int h = 1;
        for (int m : models) {
            ModelDefinition md = BuildTierPets.model(store, m);
            for (int v = 0; v < md.vertexCount; v++) h = Math.max(h, Math.max(Math.abs(md.vertexY[v]), Math.max(Math.abs(md.vertexX[v]), Math.abs(md.vertexZ[v]))));
        }
        return Math.max(600, h * 9);
    }

    static void sheet(Store store, String out) throws Exception {
        TextureManager textures = new TextureManager(store);
        textures.load();
        int cell = 160;
        int rows = bosses.size() + (gfxs.size() + 3) / 4;
        BufferedImage img = new BufferedImage(4 * cell + 10, rows * cell + 10, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setColor(new Color(0x3E3529));
        g2.fillRect(0, 0, img.getWidth(), img.getHeight());
        g2.setColor(Color.WHITE);
        int row = 0;
        for (Boss b : bosses) {
            NpcDefinition src = BuildTierPets.npc(store, b.src), nu = BuildTierPets.npc(store, b.npc);
            int zoom = zoomFor(store, src.models);
            BufferedImage a = render(store, textures, src.models, src.recolorToFind, src.recolorToReplace, zoom);
            BufferedImage c = render(store, textures, nu.models, nu.recolorToFind, nu.recolorToReplace, zoom);
            if (a != null) g2.drawImage(a, 5, 5 + row * cell, cell - 10, cell - 10, null);
            if (c != null) g2.drawImage(c, 5 + cell, 5 + row * cell, cell - 10, cell - 10, null);
            g2.drawString(nu.name + " (" + nu.combatLevel + ")", 10 + 2 * cell, 30 + row * cell);
            row++;
        }
        List<byte[]> spots = spotContents(store);
        for (int k = 0; k < gfxs.size(); k++) {
            Gfx g = gfxs.get(k);
            var sd = BuildTierPets.spot(spots.get(spotSlot(store, g.id)), g.id);
            int[] m = {sd.modelId};
            BufferedImage s = render(store, textures, m, sd.recolorToFind, sd.recolorToReplace, zoomFor(store, m));
            int x = 5 + (k % 4) * cell, y = 5 + (row + k / 4) * cell;
            if (s != null) g2.drawImage(s, x, y, cell - 10, cell - 10, null);
            g2.drawString(String.valueOf(g.id), x + 4, y + 14);
        }
        g2.dispose();
        ImageIO.write(img, "PNG", new File(out));
        System.out.println("wrote " + out);
    }

    static void peek(Store store, String out, int[] ids) throws Exception {
        TextureManager textures = new TextureManager(store);
        textures.load();
        int cell = 130, cols = 6;
        BufferedImage img = new BufferedImage(cols * cell, ((ids.length + cols - 1) / cols) * cell, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setColor(new Color(0x3E3529));
        g2.fillRect(0, 0, img.getWidth(), img.getHeight());
        g2.setColor(Color.WHITE);
        List<byte[]> spots = spotContents(store);
        for (int k = 0; k < ids.length; k++) {
            int x = (k % cols) * cell, y = (k / cols) * cell;
            g2.drawString(String.valueOf(ids[k]), x + 4, y + 14);
            try {
                var sd = BuildTierPets.spot(spots.get(spotSlot(store, ids[k])), ids[k]);
                int[] m = {sd.modelId};
                BufferedImage s = render(store, textures, m, sd.recolorToFind, sd.recolorToReplace, zoomFor(store, m));
                if (s != null) g2.drawImage(s, x + 5, y + 16, cell - 10, cell - 20, null);
                g2.drawString("anim " + sd.animationId, x + 4, y + cell - 4);
            } catch (Exception e) {
                g2.drawString("missing", x + 4, y + 40);
            }
        }
        g2.dispose();
        ImageIO.write(img, "PNG", new File(out));
        System.out.println("wrote " + out);
    }

    static int i(String s) { return Integer.parseInt(s); }
}
