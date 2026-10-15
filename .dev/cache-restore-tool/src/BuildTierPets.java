import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.ItemManager;
import net.runelite.cache.TextureManager;
import net.runelite.cache.definitions.ItemDefinition;
import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.NpcDefinition;
import net.runelite.cache.definitions.loaders.ItemLoader;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.definitions.loaders.NpcLoader;
import net.runelite.cache.definitions.providers.ItemProvider;
import net.runelite.cache.definitions.providers.ModelProvider;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Container;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;
import net.runelite.cache.index.IndexData;
import net.runelite.cache.item.ItemSpriteFactory;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.*;

// Tiered combat pets (2026-10-12): builds pet npcs + item specs for every pet x tier from one spec.
// Spec lines:
//   theme <name> <pHue> <pSat> <pMul> <pAdd> <aHue> <aSat> <aMul> <aAdd> <accentSat> <accentLum>   (as GenThemeRecolors)
//   pet <key> <srcNpc> <scale> <zoom2d> <xan2d> <yan2d> <xOff> <yOff> [itemModel]
//       itemModel = model id for the pet ITEM when the npc has several models (a merged model, see "merge")
//   tier <key> <theme> <newNpc> <newItem> <placeholder> <texture|-> <texModels|-> <texItemModel|-> <name...>
//       texture   = texture id[:fraction] put on the model's main faces (T3, fraction of opaque faces, default 0.6); texModels = new model ids, one per
//       source model, that "texmodels" writes and the npc then uses; texItemModel likewise for the item.
// Modes (cache is only written by "npcs apply" / "frame apply"):
//   preview  <cache> <spec> <out.png>        icon sheet: base + every tier, recolour/texture in memory
//   sheet    <cache> <spec> <out.png>        icon sheet of the REAL tier items now in the cache
//   texmodels <cache> <spec> <outDir>        writes <id>.dat textured models (for InsertRawModels)
//   merge    <cache> <spec> <outDir>         writes merged item models (pet itemModel / texItemModel)
//   npcs verify|apply <cache> <spec>         creates every tier npc (sorted insert, one write)
//   itemspec <cache> <spec> <out.txt>        CreateItemVariants spec for every tier item
//   frame verify|apply <cache> <spec>        icon framing (op4-8) on every tier item, one write
//   gfxmodels <cache> <spec> <outDir>        textured copies of each effect model for T2/T3
//   gfx verify|apply <cache> <spec>          tinted spotanim copies per tier (one write)
//   spec line: gfx <srcSpotanim> <t1> <t2> <t3> <t2Model> <t3Model>   (themes g1/g2/g3 tint them)
public class BuildTierPets {
    record Theme(int pHue, int pSat, double pMul, int pAdd, int aHue, int aSat, double aMul, int aAdd, int accentSat, int accentLum) {}
    record Pet(String key, int srcNpc, int scale, int zoom, int xan, int yan, int xOff, int yOff, int itemModel) {}
    record Gfx(int src, int[] ids, int t2Model, int t3Model) {}
    record Tier(String key, String theme, int npc, int item, int placeholder, int texture, double texFraction, int[] texModels, int texItemModel, String name) {}

    static final int TEMPLATE_ITEM = 12650; // General Graardor Jr. pet item
    static final int PLACEHOLDER_TEMPLATE = 14387;

    static Map<String, Theme> themes = new LinkedHashMap<>();
    static Map<String, Pet> pets = new LinkedHashMap<>();
    static List<Tier> tiers = new ArrayList<>();
    static List<Gfx> gfxs = new ArrayList<>();
    static final int T2_TEXTURE = 187, T3_TEXTURE = 186;

    public static void main(String[] args) throws Exception {
        String mode = args[0];
        boolean twoArg = mode.equals("npcs") || mode.equals("frame") || mode.equals("gfx");
        String sub = twoArg ? args[1] : null;
        String cache = args[twoArg ? 2 : 1];
        readSpec(args[twoArg ? 3 : 2]);
        try (Store store = new Store(new File(cache))) {
            store.load();
            switch (mode) {
                case "preview" -> preview(store, args[3]);
                case "sheet" -> sheet(store, args[3]);
                case "gfxsheet" -> gfxSheet(store, args[3]);
                case "texmodels" -> texModels(store, new File(args[3]));
                case "merge" -> merge(store, new File(args[3]));
                case "npcs" -> npcs(store, sub.equals("apply"));
                case "itemspec" -> itemSpec(store, new File(args[3]));
                case "frame" -> frame(store, sub.equals("apply"));
                case "gfxmodels" -> gfxModels(store, new File(args[3]));
                case "gfx" -> gfx(store, sub.equals("apply"));
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
                case "theme" -> themes.put(p[1], new Theme(i(p[2]), i(p[3]), Double.parseDouble(p[4]), i(p[5]), i(p[6]), i(p[7]),
                        Double.parseDouble(p[8]), i(p[9]), i(p[10]), i(p[11])));
                case "pet" -> pets.put(p[1], new Pet(p[1], i(p[2]), i(p[3]), i(p[4]), i(p[5]), i(p[6]), i(p[7]), i(p[8]),
                        p.length > 9 ? i(p[9]) : -1));
                case "tier" -> {
                    String[] q = l.split("\\s+", 10);
                    int[] tm = q[7].equals("-") ? null : Arrays.stream(q[7].split(",")).mapToInt(Integer::parseInt).toArray();
                    String[] tex = q[6].split(":");
                    tiers.add(new Tier(q[1], q[2], i(q[3]), i(q[4]), i(q[5]), tex[0].equals("-") ? -1 : i(tex[0]),
                            tex.length > 1 ? Double.parseDouble(tex[1]) : 0.6, tm,
                            q[8].equals("-") ? -1 : i(q[8]), q[9]));
                }
                case "gfx" -> gfxs.add(new Gfx(i(p[1]), new int[]{i(p[2]), i(p[3]), i(p[4])}, i(p[5]), i(p[6])));
                default -> throw new IllegalArgumentException("bad spec line: " + l);
            }
        }
    }

    /* ---------------- shared loading ---------------- */

    static List<byte[]> npcContents;
    static FileData[] npcFd;

    static NpcDefinition npc(Store store, int id) throws Exception {
        if (npcContents == null) {
            Archive a = store.getIndex(IndexType.CONFIGS).getArchive(ConfigType.NPC.getId());
            npcFd = a.getFileData();
            npcContents = SpliceItemOption.splitChunks(a.decompress(store.getStorage().loadArchive(a)), npcFd.length);
        }
        for (int k = 0; k < npcFd.length; k++) if (npcFd[k].getId() == id) return new NpcLoader().load(id, npcContents.get(k));
        throw new IllegalStateException("npc " + id + " not found");
    }

    static ModelDefinition model(Store store, int id) throws Exception {
        Archive a = store.getIndex(IndexType.MODELS).getArchive(id);
        if (a == null) throw new IllegalStateException("model " + id + " missing");
        ModelDefinition m = new ModelLoader().load(id, a.decompress(store.getStorage().loadArchive(a)));
        if (m.packedVertexGroups == null && m.getVertexGroups() != null)
            m.packedVertexGroups = TextureModelFaces.packVertexGroups(m.getVertexGroups(), m.vertexCount);
        return m;
    }

    /** find -> replace pairs that theme every colour of the pet's models (source npc recolour resolved first). */
    static short[][] recolor(Store store, Pet pet, Theme t) throws Exception {
        NpcDefinition d = npc(store, pet.srcNpc);
        Map<Integer, Integer> src = new HashMap<>();
        if (d.recolorToFind != null)
            for (int k = 0; k < d.recolorToFind.length; k++) src.put(d.recolorToFind[k] & 0xFFFF, d.recolorToReplace[k] & 0xFFFF);
        TreeSet<Integer> raw = new TreeSet<>();
        for (int m : d.models) {
            ModelDefinition md = model(store, m);
            if (md.faceColors != null) for (short c : md.faceColors) raw.add(c & 0xFFFF);
        }
        raw.addAll(src.keySet());
        List<int[]> pairs = new ArrayList<>();
        for (int c : raw) {
            int eff = src.getOrDefault(c, c);
            int nc = GenThemeRecolors.map(eff, new GenThemeRecolors.Theme(t.pHue, t.pSat, t.pMul, t.pAdd, t.aHue, t.aSat, t.aMul, t.aAdd, t.accentSat, t.accentLum));
            if (nc < 0) nc = eff;
            if (nc != c) pairs.add(new int[]{c, nc});
        }
        if (pairs.size() > 255) throw new IllegalStateException(pet.key + ": too many recolours " + pairs.size());
        short[][] out = new short[2][pairs.size()];
        for (int k = 0; k < pairs.size(); k++) {
            out[0][k] = (short) pairs.get(k)[0];
            out[1][k] = (short) pairs.get(k)[1];
        }
        return out;
    }

    static ModelDefinition textured(ModelDefinition m, int texture, double fraction) {
        Set<Integer> cols = TextureModelFaces.autoColours(m, fraction);
        if (m.faceTextures == null) {
            m.faceTextures = new short[m.faceCount];
            Arrays.fill(m.faceTextures, (short) -1);
        }
        if (m.textureCoords == null) {
            m.textureCoords = new byte[m.faceCount];
            Arrays.fill(m.textureCoords, (byte) -1);
        }
        for (int f = 0; f < m.faceCount; f++) {
            if (cols.contains(m.faceColors[f] & 0xFFFF)) {
                m.faceTextures[f] = (short) texture;
                m.textureCoords[f] = -1;
            }
        }
        return m;
    }

    /** Concatenates several models into one (icon/ground model for multi-model npcs). */
    static ModelDefinition merge(List<ModelDefinition> ms) {
        ModelDefinition o = new ModelDefinition();
        int vc = 0, fc = 0, tc = 0;
        boolean anyTex = false;
        for (ModelDefinition m : ms) {
            vc += m.vertexCount;
            fc += m.faceCount;
            tc += m.numTextureFaces;
            anyTex |= m.faceTextures != null;
        }
        o.vertexCount = vc; o.faceCount = fc; o.numTextureFaces = tc;
        o.vertexX = new int[vc]; o.vertexY = new int[vc]; o.vertexZ = new int[vc];
        o.packedVertexGroups = new int[vc];
        o.faceIndices1 = new int[fc]; o.faceIndices2 = new int[fc]; o.faceIndices3 = new int[fc];
        o.faceColors = new short[fc];
        o.faceTransparencies = new byte[fc];
        o.faceRenderPriorities = new byte[fc];
        o.faceRenderTypes = new byte[fc];
        if (anyTex) {
            o.faceTextures = new short[fc];
            o.textureCoords = new byte[fc];
        }
        o.texIndices1 = new short[tc]; o.texIndices2 = new short[tc]; o.texIndices3 = new short[tc];
        o.textureRenderTypes = new byte[tc];
        int vo = 0, fo = 0, to = 0;
        for (ModelDefinition m : ms) {
            for (int v = 0; v < m.vertexCount; v++) {
                o.vertexX[vo + v] = m.vertexX[v]; o.vertexY[vo + v] = m.vertexY[v]; o.vertexZ[vo + v] = m.vertexZ[v];
                o.packedVertexGroups[vo + v] = m.packedVertexGroups == null ? 0 : m.packedVertexGroups[v];
            }
            for (int f = 0; f < m.faceCount; f++) {
                o.faceIndices1[fo + f] = m.faceIndices1[f] + vo;
                o.faceIndices2[fo + f] = m.faceIndices2[f] + vo;
                o.faceIndices3[fo + f] = m.faceIndices3[f] + vo;
                o.faceColors[fo + f] = m.faceColors[f];
                o.faceTransparencies[fo + f] = m.faceTransparencies == null ? 0 : m.faceTransparencies[f];
                o.faceRenderPriorities[fo + f] = m.faceRenderPriorities == null ? m.priority : m.faceRenderPriorities[f];
                o.faceRenderTypes[fo + f] = m.faceRenderTypes == null ? 0 : m.faceRenderTypes[f];
                if (anyTex) {
                    o.faceTextures[fo + f] = m.faceTextures == null ? -1 : m.faceTextures[f];
                    int tcoord = m.textureCoords == null ? -1 : m.textureCoords[f];
                    o.textureCoords[fo + f] = (byte) (tcoord < 0 ? -1 : tcoord + to);
                }
            }
            for (int t = 0; t < m.numTextureFaces; t++) {
                o.texIndices1[to + t] = (short) (m.texIndices1[t] + vo);
                o.texIndices2[to + t] = (short) (m.texIndices2[t] + vo);
                o.texIndices3[to + t] = (short) (m.texIndices3[t] + vo);
                o.textureRenderTypes[to + t] = m.textureRenderTypes == null ? 0 : m.textureRenderTypes[t];
            }
            vo += m.vertexCount; fo += m.faceCount; to += m.numTextureFaces;
        }
        o.priority = 0;
        return o;
    }

    static List<ModelDefinition> petModels(Store store, Pet pet, Tier tier) throws Exception {
        NpcDefinition d = npc(store, pet.srcNpc);
        List<ModelDefinition> out = new ArrayList<>();
        for (int m : d.models) {
            ModelDefinition md = model(store, m);
            out.add(tier != null && tier.texture >= 0 ? textured(md, tier.texture, tier.texFraction) : md);
        }
        return out;
    }

    /* ---------------- modes ---------------- */

    static void sheet(Store store, String outPng) throws Exception {
        TextureManager textures = new TextureManager(store);
        textures.load();
        Archive a = store.getIndex(IndexType.CONFIGS).getArchive(ConfigType.ITEM.getId());
        FileData[] fd = a.getFileData();
        List<byte[]> c = SpliceItemOption.splitChunks(a.decompress(store.getStorage().loadArchive(a)), fd.length);
        Map<Integer, Integer> slots = new HashMap<>();
        for (int k = 0; k < fd.length; k++) slots.put(fd[k].getId(), k);
        ItemProvider ip = id -> slots.containsKey(id) ? new ItemLoader().load(id, c.get(slots.get(id))) : null;
        ModelProvider mp = id -> { try { return model(store, id); } catch (Exception e) { throw new java.io.IOException(e); } };
        List<Pet> ps = new ArrayList<>(pets.values());
        int scale = 4, cw = 36 * scale, ch = 32 * scale;
        BufferedImage img = new BufferedImage(3 * (cw + 10) + 10, ps.size() * ch + 10, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(0x3E3529));
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        for (int row = 0; row < ps.size(); row++) {
            int col = 0;
            for (Tier t : tiers) {
                if (!t.key.equals(ps.get(row).key)) continue;
                BufferedImage s = ItemSpriteFactory.createSprite(ip, mp, sprites(store), textures, t.item, 1, 1, 0x302020, false);
                if (s != null) g.drawImage(s, 10 + col * (cw + 10), 5 + row * ch, cw, ch, null);
                col++;
            }
        }
        g.dispose();
        ImageIO.write(img, "PNG", new File(outPng));
        System.out.println("wrote " + outPng);
    }

    static net.runelite.cache.definitions.providers.SpriteProvider sprites(Store store) {
        // lazy per-archive sprites: SpriteManager.load() dies on a broken slot and drops every later sprite
        Map<Integer, net.runelite.cache.definitions.SpriteDefinition[]> spriteCache = new HashMap<>();
        net.runelite.cache.definitions.providers.SpriteProvider sprites = (id, frame) -> {
            var defs = spriteCache.computeIfAbsent(id, k -> {
                try {
                    Archive a = store.getIndex(IndexType.SPRITES).getArchive(k);
                    return a == null ? null : new net.runelite.cache.definitions.loaders.SpriteLoader().load(k, a.decompress(store.getStorage().loadArchive(a)));
                } catch (Exception e) { return null; }
            });
            return defs == null || frame >= defs.length ? null : defs[frame];
        };
        return sprites;
    }

    static void preview(Store store, String outPng) throws Exception {
        var sprites = sprites(store);
        TextureManager textures = new TextureManager(store);
        textures.load();

        List<Pet> ps = new ArrayList<>(pets.values());
        List<String> keys = new ArrayList<>();
        for (Tier t : tiers) if (!keys.contains(t.theme)) keys.add(t.theme);
        int scale = 4, cw = 36 * scale, ch = 32 * scale, cols = 1 + keys.size();
        BufferedImage sheet = new BufferedImage(cols * (cw + 10) + 10, ps.size() * ch + 10, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = sheet.createGraphics();
        g.setColor(new Color(0x3E3529));
        g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        for (int row = 0; row < ps.size(); row++) {
            Pet pet = ps.get(row);
            for (int col = 0; col < cols; col++) {
                Tier tier = null;
                if (col > 0) for (Tier t : tiers) if (t.key.equals(pet.key) && t.theme.equals(keys.get(col - 1))) tier = t;
                if (col > 0 && tier == null) continue;
                ModelDefinition merged = merge(petModels(store, pet, tier));
                short[][] rc = tier == null ? null : recolor(store, pet, themes.get(tier.theme));
                ItemDefinition def = new ItemDefinition(1);
                def.inventoryModel = 1;
                def.zoom2d = pet.zoom; def.xan2d = pet.xan; def.yan2d = pet.yan;
                def.xOffset2d = pet.xOff; def.yOffset2d = pet.yOff;
                def.resizeX = def.resizeY = def.resizeZ = 128;
                if (rc != null) { def.colorFind = rc[0]; def.colorReplace = rc[1]; }
                ItemProvider ip = id -> def;
                ModelProvider mp = id -> {
                    ModelDefinition copy = merge(List.of(merged));
                    return copy;
                };
                BufferedImage s = ItemSpriteFactory.createSprite(ip, mp, sprites, textures, 1, 1, 1, 0x302020, false);
                if (s != null) g.drawImage(s, 10 + col * (cw + 10), 5 + row * ch, cw, ch, null);
            }
        }
        g.dispose();
        ImageIO.write(sheet, "PNG", new File(outPng));
        System.out.println("wrote " + outPng + " (columns: base, " + keys + ")");
    }

    static void texModels(Store store, File dir) throws Exception {
        dir.mkdirs();
        for (Tier t : tiers) {
            if (t.texModels == null) continue;
            Pet pet = pets.get(t.key);
            NpcDefinition d = npc(store, pet.srcNpc);
            if (t.texModels.length != d.models.length)
                throw new IllegalStateException(t.name + ": need " + d.models.length + " texModels");
            for (int k = 0; k < d.models.length; k++) {
                ModelDefinition m = textured(model(store, d.models[k]), t.texture, t.texFraction);
                byte[] enc = ModelType3Encoder.encode(m);
                ModelDefinition back = new ModelLoader().load(t.texModels[k], enc);
                check(back.vertexCount == m.vertexCount && back.faceCount == m.faceCount, "round trip " + t.texModels[k]);
                int[] backSkins = back.packedVertexGroups != null ? back.packedVertexGroups
                        : back.getVertexGroups() == null ? null : TextureModelFaces.packVertexGroups(back.getVertexGroups(), back.vertexCount);
                check(m.packedVertexGroups == null || Arrays.equals(backSkins, m.packedVertexGroups), "skins lost " + t.texModels[k]);
                int texFaces = 0;
                for (int f = 0; f < back.faceCount; f++) if (back.faceTextures != null && back.faceTextures[f] == t.texture) texFaces++;
                check(texFaces > 0, "no textured faces " + t.texModels[k]);
                Files.write(new File(dir, t.texModels[k] + ".dat").toPath(), enc);
                System.out.println(t.name + ": model " + d.models[k] + " -> " + t.texModels[k] + " (" + texFaces + "/" + back.faceCount + " faces textured)");
            }
        }
    }

    static void merge(Store store, File dir) throws Exception {
        dir.mkdirs();
        for (Pet pet : pets.values()) {
            if (pet.itemModel < 0) continue;
            write(dir, pet.itemModel, merge(petModels(store, pet, null)));
            for (Tier t : tiers)
                if (t.key.equals(pet.key) && t.texItemModel >= 0) write(dir, t.texItemModel, merge(petModels(store, pet, t)));
        }
    }

    static void write(File dir, int id, ModelDefinition m) throws Exception {
        byte[] enc = ModelType3Encoder.encode(m);
        ModelDefinition back = new ModelLoader().load(id, enc);
        check(back.vertexCount == m.vertexCount && back.faceCount == m.faceCount, "merge round trip " + id);
        Files.write(new File(dir, id + ".dat").toPath(), enc);
        System.out.println("merged model " + id + ": " + m.vertexCount + " vertices, " + m.faceCount + " faces");
    }

    static void npcs(Store store, boolean apply) throws Exception {
        Storage storage = store.getStorage();
        Index index = store.getIndex(IndexType.CONFIGS);
        Archive archive = index.getArchive(ConfigType.NPC.getId());
        FileData[] fileData = archive.getFileData();
        List<byte[]> contents = new ArrayList<>(SpliceItemOption.splitChunks(archive.decompress(storage.loadArchive(archive)), fileData.length));
        List<Integer> ids = new ArrayList<>();
        List<Integer> hashes = new ArrayList<>();
        for (FileData fd : fileData) { ids.add(fd.getId()); hashes.add(fd.getNameHash()); }

        for (Tier t : tiers) {
            Pet pet = pets.get(t.key);
            if (ids.contains(t.npc)) throw new IllegalStateException("npc " + t.npc + " already exists");
            int srcSlot = ids.indexOf(pet.srcNpc);
            if (srcSlot < 0) throw new IllegalStateException("source npc " + pet.srcNpc + " missing");
            byte[] w = contents.get(srcSlot);
            NpcDefinition before = new NpcLoader().load(pet.srcNpc, w);

            // menu: only Talk-to / Pick-up (strips the monster's Attack/Examine-style ops)
            for (int op = 30; op <= 34; op++) w = EditNpcInPlace.removeOpcode(w, op);
            // 102 = overhead head icons (Xamphur carries Protect from Magic) -- pets never show one
            for (int op : new int[]{40, 41, 95, 99, 102, 111, 122, 93, 103}) w = EditNpcInPlace.removeOpcode(w, op);
            w = SplicePetify.replaceOrInsert(w, 2, SplicePetify.withTerminator(t.name));
            w = SplicePetify.replaceOrInsert(w, 12, new byte[]{1});
            w = SplicePetify.replaceOrInsert(w, 97, SplicePetify.twoBytes(pet.scale));
            w = SplicePetify.replaceOrInsert(w, 98, SplicePetify.twoBytes(pet.scale));
            w = SplicePetify.replaceOrInsert(w, 30, SplicePetify.withTerminator("Talk-to"));
            w = SplicePetify.replaceOrInsert(w, 32, SplicePetify.withTerminator("Pick-up"));
            w = SplicePetify.replaceOrInsert(w, 95, SplicePetify.twoBytes(0));
            w = SplicePetify.replaceOrInsert(w, 93, new byte[0]);  // hide minimap dot
            w = SplicePetify.replaceOrInsert(w, 111, new byte[0]); // follower: low-priority ops
            if (t.texModels != null) {
                byte[] mb = new byte[1 + t.texModels.length * 2];
                mb[0] = (byte) t.texModels.length;
                for (int k = 0; k < t.texModels.length; k++) {
                    mb[1 + k * 2] = (byte) (t.texModels[k] >> 8);
                    mb[2 + k * 2] = (byte) t.texModels[k];
                }
                w = SplicePetify.replaceOrInsert(w, 1, mb);
            }
            short[][] rc = recolor(store, pet, themes.get(t.theme));
            byte[] cb = new byte[1 + rc[0].length * 4];
            cb[0] = (byte) rc[0].length;
            for (int k = 0; k < rc[0].length; k++) {
                cb[1 + k * 4] = (byte) (rc[0][k] >> 8); cb[2 + k * 4] = (byte) rc[0][k];
                cb[3 + k * 4] = (byte) (rc[1][k] >> 8); cb[4 + k * 4] = (byte) rc[1][k];
            }
            if (rc[0].length > 0) w = SplicePetify.replaceOrInsert(w, 40, cb);

            NpcDefinition after = new NpcLoader().load(t.npc, w);
            check(t.name.equals(after.name), "name");
            check(after.size == 1 && after.widthScale == pet.scale && after.heightScale == pet.scale, "size/scale");
            check(after.standingAnimation == before.standingAnimation && after.walkingAnimation == before.walkingAnimation, "anims changed");
            check("Talk-to".equals(after.actions[0]) && "Pick-up".equals(after.actions[2]) && after.actions[1] == null
                    && after.actions[3] == null && after.actions[4] == null, "actions " + Arrays.toString(after.actions));
            check(after.combatLevel == 0 && !after.isMinimapVisible, "combat/minimap");
            check(after.headIconArchiveIds == null, "head icon still present");
            check(rc[0].length == 0 || Arrays.equals(after.recolorToFind, rc[0]) && Arrays.equals(after.recolorToReplace, rc[1]), "recolor");
            check(t.texModels == null ? Arrays.equals(after.models, before.models) : Arrays.equals(after.models, t.texModels), "models");

            int insertAt = ids.size();
            for (int k = 0; k < ids.size(); k++) if (ids.get(k) > t.npc) { insertAt = k; break; }
            contents.add(insertAt, w);
            ids.add(insertAt, t.npc);
            hashes.add(insertAt, -1);
            System.out.printf("%s npc %d \"%s\" from %d at slot %d: scale %d, %d recolours, models %s%n",
                    apply ? "ADD" : "WOULD ADD", t.npc, t.name, pet.srcNpc, insertAt, pet.scale, rc[0].length, Arrays.toString(after.models));
        }
        if (!apply) { System.out.println("VERIFY mode -- not writing."); return; }

        FileData[] nfd = new FileData[ids.size()];
        for (int k = 0; k < ids.size(); k++) {
            nfd[k] = new FileData();
            nfd[k].setId(ids.get(k));
            nfd[k].setNameHash(hashes.get(k));
        }
        archive.setFileData(nfd);
        writeArchive(store, index, archive, SpliceItemOption.joinChunks(contents));
        System.out.println("APPLY complete. Archive revision now " + archive.getRevision() + ".");
    }

    static void itemSpec(Store store, File out) throws Exception {
        Archive a = store.getIndex(IndexType.CONFIGS).getArchive(ConfigType.ITEM.getId());
        FileData[] fd = a.getFileData();
        List<byte[]> c = SpliceItemOption.splitChunks(a.decompress(store.getStorage().loadArchive(a)), fd.length);
        ItemDefinition tmpl = null;
        for (int k = 0; k < fd.length; k++) if (fd[k].getId() == TEMPLATE_ITEM) tmpl = new ItemLoader().load(TEMPLATE_ITEM, c.get(k));
        if (tmpl == null) throw new IllegalStateException("template item missing");
        try (PrintWriter pw = new PrintWriter(out)) {
            pw.println("# Generated by BuildTierPets itemspec (template item " + TEMPLATE_ITEM + ", inventory model " + tmpl.inventoryModel + ")");
            for (Tier t : tiers) {
                Pet pet = pets.get(t.key);
                int model = t.texItemModel >= 0 ? t.texItemModel
                        : t.texModels != null ? t.texModels[0]
                        : pet.itemModel >= 0 ? pet.itemModel : npc(store, pet.srcNpc).models[0];
                short[][] rc = recolor(store, pet, themes.get(t.theme));
                StringBuilder pairs = new StringBuilder();
                for (int k = 0; k < rc[0].length; k++) {
                    if (k > 0) pairs.append(',');
                    pairs.append(rc[0][k] & 0xFFFF).append(':').append(rc[1][k] & 0xFFFF);
                }
                pw.println("variant " + TEMPLATE_ITEM + " " + t.item + " " + t.placeholder + " 0 -|-|-|-|Drop " + t.name);
                pw.println("placeholderfrom " + t.item + " " + PLACEHOLDER_TEMPLATE);
                pw.println("models " + t.item + " " + tmpl.inventoryModel + ":" + model);
                if (pairs.length() > 0) pw.println("recolor " + t.item + " " + pairs);
            }
        }
        System.out.println("wrote " + out);
    }

    static void frame(Store store, boolean apply) throws Exception {
        Storage storage = store.getStorage();
        Index index = store.getIndex(IndexType.CONFIGS);
        Archive archive = index.getArchive(ConfigType.ITEM.getId());
        FileData[] fd = archive.getFileData();
        List<byte[]> c = new ArrayList<>(SpliceItemOption.splitChunks(archive.decompress(storage.loadArchive(archive)), fd.length));
        for (Tier t : tiers) {
            Pet pet = pets.get(t.key);
            int slot = -1;
            for (int k = 0; k < fd.length; k++) if (fd[k].getId() == t.item) slot = k;
            if (slot < 0) throw new IllegalStateException("item " + t.item + " missing (run CreateItemVariants first)");
            byte[] w = c.get(slot);
            ItemDefinition before = new ItemLoader().load(t.item, w);
            w = SpliceNewPetItem.replaceOrInsertItemOpcode(w, 4, SpliceNewPetItem.ushort(pet.zoom));
            w = SpliceNewPetItem.replaceOrInsertItemOpcode(w, 5, SpliceNewPetItem.ushort(pet.xan));
            w = SpliceNewPetItem.replaceOrInsertItemOpcode(w, 6, SpliceNewPetItem.ushort(pet.yan));
            w = SpliceNewPetItem.replaceOrInsertItemOpcode(w, 7, SpliceNewPetItem.ushort(pet.xOff & 0xFFFF));
            w = SpliceNewPetItem.replaceOrInsertItemOpcode(w, 8, SpliceNewPetItem.ushort(pet.yOff & 0xFFFF));
            ItemDefinition after = new ItemLoader().load(t.item, w);
            check(Objects.equals(after.name, before.name) && after.inventoryModel == before.inventoryModel, "frame changed other fields " + t.item);
            check(after.zoom2d == pet.zoom && after.xan2d == pet.xan && after.yan2d == pet.yan, "frame values " + t.item);
            c.set(slot, w);
            System.out.println("frame " + t.item + " \"" + after.name + "\" zoom " + pet.zoom + " xan " + pet.xan + " yan " + pet.yan);
        }
        if (!apply) { System.out.println("VERIFY mode -- not writing."); return; }
        writeArchive(store, index, archive, SpliceItemOption.joinChunks(c));
        System.out.println("APPLY complete. Archive revision now " + archive.getRevision() + ".");
    }

    static net.runelite.cache.definitions.SpotAnimDefinition spot(byte[] b, int id) {
        return new net.runelite.cache.definitions.loaders.SpotAnimLoader().load(id, b);
    }

    static ModelDefinition texturedAll(ModelDefinition m, int texture) {
        m.faceTextures = new short[m.faceCount];
        m.textureCoords = new byte[m.faceCount];
        Arrays.fill(m.faceTextures, (short) texture);
        Arrays.fill(m.textureCoords, (byte) -1);
        return m;
    }

    /** Preview: every effect's source + T1/T2/T3 spotanim, rendered like an icon (recolour + model from the cache). */
    static void gfxSheet(Store store, String outPng) throws Exception {
        TextureManager textures = new TextureManager(store);
        textures.load();
        Archive a = store.getIndex(IndexType.CONFIGS).getArchive(ConfigType.SPOTANIM.getId());
        FileData[] fd = a.getFileData();
        List<byte[]> c = SpliceItemOption.splitChunks(a.decompress(store.getStorage().loadArchive(a)), fd.length);
        Map<Integer, Integer> slots = new HashMap<>();
        for (int k = 0; k < fd.length; k++) slots.put(fd[k].getId(), k);
        int scale = 3, cw = 36 * scale, ch = 32 * scale;
        BufferedImage img = new BufferedImage(4 * (cw + 6) + 6, gfxs.size() * ch + 6, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setColor(new Color(0x3E3529));
        g2.fillRect(0, 0, img.getWidth(), img.getHeight());
        for (int row = 0; row < gfxs.size(); row++) {
            Gfx g = gfxs.get(row);
            int[] ids = {g.src, g.ids[0], g.ids[1], g.ids[2]};
            for (int col = 0; col < 4; col++) {
                Integer slot = slots.get(ids[col]);
                if (slot == null) continue;
                var sd = spot(c.get(slot), ids[col]);
                ItemDefinition def = new ItemDefinition(1);
                def.inventoryModel = sd.modelId;
                def.zoom2d = 900; def.xan2d = 300; def.yan2d = 0;
                def.resizeX = def.resizeY = def.resizeZ = 128;
                def.colorFind = sd.recolorToFind; def.colorReplace = sd.recolorToReplace;
                ModelProvider mp = id -> { try { return model(store, id); } catch (Exception e) { throw new java.io.IOException(e); } };
                BufferedImage s = ItemSpriteFactory.createSprite(id -> def, mp, sprites(store), textures, 1, 1, 0, 0, false);
                if (s != null) g2.drawImage(s, 6 + col * (cw + 6), 3 + row * ch, cw, ch, null);
            }
        }
        g2.dispose();
        ImageIO.write(img, "PNG", new File(outPng));
        System.out.println("wrote " + outPng);
    }

    static void gfxModels(Store store, File dir) throws Exception {
        dir.mkdirs();
        Archive a = store.getIndex(IndexType.CONFIGS).getArchive(ConfigType.SPOTANIM.getId());
        FileData[] fd = a.getFileData();
        List<byte[]> c = SpliceItemOption.splitChunks(a.decompress(store.getStorage().loadArchive(a)), fd.length);
        for (Gfx g : gfxs) {
            int slot = -1;
            for (int k = 0; k < fd.length; k++) if (fd[k].getId() == g.src) slot = k;
            check(slot >= 0, "spotanim " + g.src);
            int model = spot(c.get(slot), g.src).modelId;
            for (int[] out : new int[][]{{g.t2Model, T2_TEXTURE}, {g.t3Model, T3_TEXTURE}}) {
                ModelDefinition m = texturedAll(model(store, model), out[1]);
                byte[] enc = ModelType3Encoder.encode(m);
                ModelDefinition back = new ModelLoader().load(out[0], enc);
                int[] backSkins = back.packedVertexGroups != null ? back.packedVertexGroups
                        : back.getVertexGroups() == null ? null : TextureModelFaces.packVertexGroups(back.getVertexGroups(), back.vertexCount);
                check(back.faceCount == m.faceCount && (m.packedVertexGroups == null || Arrays.equals(backSkins, m.packedVertexGroups)),
                        "gfx model round trip " + out[0]);
                Files.write(new File(dir, out[0] + ".dat").toPath(), enc);
                System.out.println("gfx " + g.src + ": model " + model + " -> " + out[0] + " (texture " + out[1] + ")");
            }
        }
    }

    static void gfx(Store store, boolean apply) throws Exception {
        Index index = store.getIndex(IndexType.CONFIGS);
        Archive archive = index.getArchive(ConfigType.SPOTANIM.getId());
        FileData[] fileData = archive.getFileData();
        List<byte[]> contents = new ArrayList<>(SpliceItemOption.splitChunks(archive.decompress(store.getStorage().loadArchive(archive)), fileData.length));
        List<Integer> ids = new ArrayList<>();
        List<Integer> hashes = new ArrayList<>();
        for (FileData f : fileData) { ids.add(f.getId()); hashes.add(f.getNameHash()); }
        String[] themeNames = {"g1", "g2", "g3"};
        for (Gfx g : gfxs) {
            int srcSlot = ids.indexOf(g.src);
            check(srcSlot >= 0, "spotanim " + g.src);
            byte[] src = contents.get(srcSlot);
            var sd = spot(src, g.src);
            Map<Integer, Integer> existing = new HashMap<>();
            if (sd.recolorToFind != null)
                for (int k = 0; k < sd.recolorToFind.length; k++) existing.put(sd.recolorToFind[k] & 0xFFFF, sd.recolorToReplace[k] & 0xFFFF);
            TreeSet<Integer> raw = new TreeSet<>();
            for (short col : model(store, sd.modelId).faceColors) raw.add(col & 0xFFFF);
            raw.addAll(existing.keySet());
            for (int t = 0; t < 3; t++) {
                int newId = g.ids[t];
                check(!ids.contains(newId), "spotanim " + newId + " already exists");
                Theme th = themes.get(themeNames[t]);
                List<int[]> pairs = new ArrayList<>();
                for (int col : raw) {
                    int eff = existing.getOrDefault(col, col);
                    // pure black smoke (shadow spells) is tinted as a very dark grey
                    int nc = GenThemeRecolors.map(eff == 0 ? 24 : eff, new GenThemeRecolors.Theme(th.pHue, th.pSat, th.pMul, th.pAdd, th.aHue, th.aSat, th.aMul, th.aAdd, th.accentSat, th.accentLum));
                    if (nc < 0) nc = eff;
                    if (nc != col) pairs.add(new int[]{col, nc});
                }
                byte[] w = spotRemove(src, 40);
                w = spotRemove(w, 41);
                if (!pairs.isEmpty()) {
                    byte[] cb = new byte[1 + pairs.size() * 4];
                    cb[0] = (byte) pairs.size();
                    for (int k = 0; k < pairs.size(); k++) {
                        cb[1 + k * 4] = (byte) (pairs.get(k)[0] >> 8); cb[2 + k * 4] = (byte) pairs.get(k)[0];
                        cb[3 + k * 4] = (byte) (pairs.get(k)[1] >> 8); cb[4 + k * 4] = (byte) pairs.get(k)[1];
                    }
                    w = spotPut(w, 40, cb);
                }
                int model = t == 0 ? sd.modelId : t == 1 ? g.t2Model : g.t3Model;
                if (t > 0) w = spotPut(w, 1, SplicePetify.twoBytes(model));
                var after = spot(w, newId);
                check(after.modelId == model && after.animationId == sd.animationId && after.resizeX == sd.resizeX
                        && after.resizeY == sd.resizeY && after.rotaton == sd.rotaton, "spotanim fields " + newId);
                check(pairs.isEmpty() ? after.recolorToFind == null : after.recolorToFind.length == pairs.size(), "spotanim recolor " + newId);
                int insertAt = ids.size();
                for (int k = 0; k < ids.size(); k++) if (ids.get(k) > newId) { insertAt = k; break; }
                contents.add(insertAt, w);
                ids.add(insertAt, newId);
                hashes.add(insertAt, -1);
                System.out.printf("%s spotanim %d (T%d of %d): model %d, %d recolours%n", apply ? "ADD" : "WOULD ADD", newId, t + 1, g.src, model, pairs.size());
            }
        }
        if (!apply) { System.out.println("VERIFY mode -- not writing."); return; }
        FileData[] nfd = new FileData[ids.size()];
        for (int k = 0; k < ids.size(); k++) {
            nfd[k] = new FileData();
            nfd[k].setId(ids.get(k));
            nfd[k].setNameHash(hashes.get(k));
        }
        archive.setFileData(nfd);
        writeArchive(store, index, archive, SpliceItemOption.joinChunks(contents));
        System.out.println("APPLY complete. Spotanim archive revision now " + archive.getRevision() + ".");
    }

    /** [start, end) of the first record with this opcode in a spotanim def, or null. Opcodes: 1,2,4,5,6 ushort; 7,8 ubyte; 9 string; 40,41 tables. */
    static int[] spotSpan(byte[] b, int target) {
        int pos = 0;
        while (true) {
            int op = b[pos] & 0xFF;
            if (op == 0) return target == 0 ? new int[]{pos, pos} : null;
            int s0 = pos++;
            switch (op) {
                case 1, 2, 4, 5, 6 -> pos += 2;
                case 7, 8 -> pos += 1;
                case 9 -> { while (b[pos] != 0) pos++; pos++; }
                case 40, 41 -> pos += 1 + (b[pos] & 0xFF) * 4;
                default -> throw new IllegalStateException("unknown spotanim opcode " + op);
            }
            if (op == target) return new int[]{s0, pos};
        }
    }

    static byte[] spotRemove(byte[] b, int op) {
        int[] sp = spotSpan(b, op);
        if (sp == null) return b;
        byte[] out = new byte[b.length - (sp[1] - sp[0])];
        System.arraycopy(b, 0, out, 0, sp[0]);
        System.arraycopy(b, sp[1], out, sp[0], b.length - sp[1]);
        return out;
    }

    /** Replaces the record for {@code op}, or inserts it before the terminator. */
    static byte[] spotPut(byte[] b, int op, byte[] payload) {
        b = spotRemove(b, op);
        int at = spotSpan(b, 0)[0];
        byte[] out = new byte[b.length + 1 + payload.length];
        System.arraycopy(b, 0, out, 0, at);
        out[at] = (byte) op;
        System.arraycopy(payload, 0, out, at + 1, payload.length);
        System.arraycopy(b, at, out, at + 1 + payload.length, b.length - at);
        return out;
    }

    static void writeArchive(Store store, Index index, Archive archive, byte[] data) throws Exception {
        Storage storage = store.getStorage();
        Container container = new Container(archive.getCompression(), -1);
        container.compress(data, null);
        storage.store(index.getId(), archive.getArchiveId(), container.data);
        archive.setCrc(container.crc);
        archive.setRevision(archive.getRevision() + 1);
        archive.setCompressedSize(container.data.length);
        archive.setDecompressedSize(data.length);
        IndexData indexData = index.toIndexData();
        Container idx = new Container(index.getCompression(), -1);
        idx.compress(indexData.writeIndexData(), null);
        storage.store(255, index.getId(), idx.data);
        index.setCrc(idx.crc);
    }

    static void check(boolean ok, String what) {
        if (!ok) throw new IllegalStateException("check failed: " + what + " -- ABORTING, nothing written");
    }

    static int i(String s) { return Integer.parseInt(s); }
}
