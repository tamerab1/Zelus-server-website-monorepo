import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.ItemDefinition;
import net.runelite.cache.definitions.loaders.ItemLoader;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.definitions.providers.ItemProvider;
import net.runelite.cache.definitions.providers.ModelProvider;
import net.runelite.cache.fs.*;
import net.runelite.cache.index.FileData;
import net.runelite.cache.index.IndexData;
import net.runelite.cache.item.ItemSpriteFactory;
import net.runelite.cache.SpriteManager;
import net.runelite.cache.TextureManager;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;

// Auto-frames inventory icons (2026-10-18, new customs batch): for each item, renders its icon through the client's
// own ItemSpriteFactory at every icon camera angle (xan2d x yan2d grid, zan2d 0) and keeps the angle at which the
// model, scaled to fit, covers the most of the 36x32 slot (long weapons come out diagonal, armour face-on if
// "front" is given). Then it finds the zoom2d that leaves a 1px margin and the xOffset2d / yOffset2d that centre it.
// Spec lines:   item <id> [any|front|flat|<xan2d>,<yan2d>[,<zan2d>]]  (front: armour; flat: zan2d 0, for items also shown in model widgets)
// Writes ops 4/5/6/7/8/95 (zoom, xan, yan, x/y offset, zan) in place and re-decodes them; previews <outDir>/<id>.png.
// Usage: preview|apply <cachePath> <specFile> <outDir>
public class AutoFrameItems {
    static List<byte[]> contents;
    static FileData[] fileData;
    static final Map<Integer, ItemDefinition> overrides = new HashMap<>();
    static boolean warned;

    public static void main(String[] args) throws Exception {
        boolean apply = args[0].equals("apply");
        List<String[]> spec = new ArrayList<>();
        for (String line : java.nio.file.Files.readAllLines(new File(args[2]).toPath())) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            spec.add(line.split("\\s+"));
        }
        File outDir = new File(args[3]);
        outDir.mkdirs();
        try (Store store = new Store(new File(args[1]))) {
            store.load();
            Storage storage = store.getStorage();
            Index configs = store.getIndex(IndexType.CONFIGS);
            Archive items = configs.getArchive(ConfigType.ITEM.getId());
            fileData = items.getFileData();
            contents = SpliceItemOption.splitChunks(items.decompress(storage.loadArchive(items)), fileData.length);
            ItemLoader loader = new ItemLoader();
            ItemProvider ip = id -> {
                if (overrides.containsKey(id)) return overrides.get(id);
                int slot = slot(id);
                return slot < 0 ? null : loader.load(id, contents.get(slot));
            };
            ModelProvider mp = id -> {
                Archive a = store.getIndex(IndexType.MODELS).getArchive(id);
                return new ModelLoader().load(id, a.decompress(storage.loadArchive(a)));
            };
            SpriteManager sprites = new SpriteManager(store);
            try { sprites.load(); } catch (Exception ignored) { }
            TextureManager textures = new TextureManager(store);
            textures.load();

            for (String[] s : spec) {
                int id = Integer.parseInt(s[1]);
                String mode = s.length > 2 ? s[2] : "any";
                ItemDefinition base = loader.load(id, contents.get(slot(id)));
                // angle: geometric (the renderer drops faces near the camera, which fooled a render-based search)
                Geo geo = new Geo(mp.provide(base.inventoryModel), base.resizeX, base.resizeY, base.resizeZ);
                int[] ang;
                if (mode.contains(",")) {
                    String[] p = mode.split(",");
                    ang = new int[]{Integer.parseInt(p[0]), Integer.parseInt(p[1]), p.length > 2 ? Integer.parseInt(p[2]) : 0};
                } else
                    ang = geo.bestAngle(mode.equals("front"), !mode.equals("flat")); // flat: zan2d 0
                int xan = ang[0], yan = ang[1], zan = ang[2];
                int[] fz = geo.fit(xan, yan, zan);
                int zoom = fz[0], xo = fz[1], yo = fz[2];
                BufferedImage img = render(ip, mp, sprites, textures, framed(base, zoom, xan, yan, zan, xo, yo));
                if (img != null) // the icon renderer's sprite loader can miss new texture sprites: framing is geometric anyway
                    ImageIO.write(img, "png", new File(outDir, id + ".png"));
                System.out.printf("item %d \"%s\": zoom2d=%d xan2d=%d yan2d=%d zan2d=%d xOffset2d=%d yOffset2d=%d (was %d/%d/%d/%d off %d,%d)%n",
                        id, base.name, zoom, xan, yan, zan, xo, yo, base.zoom2d, base.xan2d, base.yan2d, base.zan2d, base.xOffset2d, base.yOffset2d);
                if (apply) {
                    byte[] w = contents.get(slot(id));
                    w = SpliceNewPetItem.replaceOrInsertItemOpcode(w, 4, SpliceNewPetItem.ushort(zoom));
                    w = SpliceNewPetItem.replaceOrInsertItemOpcode(w, 5, SpliceNewPetItem.ushort(xan));
                    w = SpliceNewPetItem.replaceOrInsertItemOpcode(w, 6, SpliceNewPetItem.ushort(yan));
                    w = SpliceNewPetItem.replaceOrInsertItemOpcode(w, 7, SpliceNewPetItem.ushort(xo & 0xffff));
                    w = SpliceNewPetItem.replaceOrInsertItemOpcode(w, 8, SpliceNewPetItem.ushort(yo & 0xffff));
                    w = SpliceNewPetItem.replaceOrInsertItemOpcode(w, 95, SpliceNewPetItem.ushort(zan));
                    ItemDefinition after = loader.load(id, w);
                    if (after.zoom2d != zoom || after.xan2d != xan || after.yan2d != yan || after.zan2d != zan
                            || after.xOffset2d != xo || after.yOffset2d != yo || !Objects.equals(after.name, base.name)
                            || after.inventoryModel != base.inventoryModel || after.maleModel0 != base.maleModel0)
                        throw new IllegalStateException("framing round-trip failed for " + id);
                    contents.set(slot(id), w);
                }
            }
            if (!apply) {
                System.out.println("PREVIEW only -- nothing written.");
                return;
            }
            byte[] joined = SpliceItemOption.joinChunks(contents);
            Container c = new Container(items.getCompression(), -1);
            c.compress(joined, null);
            storage.store(configs.getId(), items.getArchiveId(), c.data);
            items.setCrc(c.crc);
            items.setRevision(items.getRevision() + 1);
            items.setCompressedSize(c.data.length);
            items.setDecompressedSize(joined.length);
            IndexData indexData = configs.toIndexData();
            Container ic = new Container(configs.getCompression(), -1);
            ic.compress(indexData.writeIndexData(), null);
            storage.store(255, configs.getId(), ic.data);
            configs.setCrc(ic.crc);
            System.out.println("APPLY complete -- " + spec.size() + " item(s) framed.");
        }
    }

    static int slot(int id) {
        for (int i = 0; i < fileData.length; i++) if (fileData[i].getId() == id) return i;
        return -1;
    }

    static ItemDefinition framed(ItemDefinition base, int zoom, int xan, int yan, int xo, int yo) {
        return framed(base, zoom, xan, yan, 0, xo, yo);
    }

    static ItemDefinition framed(ItemDefinition base, int zoom, int xan, int yan, int zan, int xo, int yo) {
        ItemDefinition d = new ItemLoader().load(base.id, contents.get(slot(base.id)));
        d.zoom2d = zoom;
        d.xan2d = xan;
        d.yan2d = yan;
        d.zan2d = zan;
        d.xOffset2d = xo;
        d.yOffset2d = yo;
        return d;
    }

    static BufferedImage render(ItemProvider ip, ModelProvider mp, SpriteManager sm, TextureManager tm, ItemDefinition d) {
        overrides.put(d.id, d);
        try {
            return ItemSpriteFactory.createSprite(ip, mp, sm, tm, d.id, 1, 0, 0, false);
        } catch (Exception e) {
            if (!warned) {
                warned = true;
                e.printStackTrace();
            }
            return null;
        } finally {
            overrides.remove(d.id);
        }
    }

    /** {minX, minY, maxX, maxY, opaque pixel count} of the non-transparent pixels, or null. */
    static int[] bbox(BufferedImage img) {
        if (img == null) return null;
        int minX = 99, minY = 99, maxX = -1, maxY = -1, n = 0;
        for (int y = 0; y < img.getHeight(); y++)
            for (int x = 0; x < img.getWidth(); x++)
                if ((img.getRGB(x, y) >>> 24) != 0) {
                    minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                    n++;
                }
        return maxX < 0 ? null : new int[]{minX, minY, maxX, maxY, n};
    }
}
