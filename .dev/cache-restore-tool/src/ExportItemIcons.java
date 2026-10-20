import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.TextureManager;
import net.runelite.cache.definitions.ItemDefinition;
import net.runelite.cache.definitions.loaders.ItemLoader;
import net.runelite.cache.definitions.providers.ItemProvider;
import net.runelite.cache.definitions.providers.ModelProvider;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;
import net.runelite.cache.item.ItemSpriteFactory;

// Read-only (2026-10-20, Discord thumbnails): renders the inventory icon of every item from <fromId> up -- the custom
// range, whose ids the public RuneLite icon server (static.runelite.net/cache/item/icon/<id>.png) shows as nothing or as
// an unrelated real OSRS item -- into <outDir>/<id>.png, 3x nearest-neighbour (108x96) so Discord shows it crisp. The
// game server attaches these to its webhook embeds (discord.webhooks.ItemIcons). Skips noted / placeholder / bought
// copies (they never appear in posts) and nameless slots. Same lazy loaders as RenderItemsLazy (new textures can't
// crash it). Re-run after each item batch and ship data/item_icons with the cache.
// Usage: ExportItemIcons <cachePath> <outDir> <fromId>
public class ExportItemIcons {
    public static void main(String[] args) throws Exception {
        File out = new File(args[1]);
        out.mkdirs();
        int from = Integer.parseInt(args[2]);
        try (Store store = new Store(new File(args[0]))) {
            store.load();
            TextureManager textures = new TextureManager(store);
            textures.load();
            Archive a = store.getIndex(IndexType.CONFIGS).getArchive(ConfigType.ITEM.getId());
            FileData[] fd = a.getFileData();
            List<byte[]> c = SpliceItemOption.splitChunks(a.decompress(store.getStorage().loadArchive(a)), fd.length);
            Map<Integer, Integer> slots = new HashMap<>();
            for (int k = 0; k < fd.length; k++) slots.put(fd[k].getId(), k);
            ItemProvider ip = id -> slots.containsKey(id) ? new ItemLoader().load(id, c.get(slots.get(id))) : null;
            ModelProvider mp = id -> { try { return BuildTierPets.model(store, id); } catch (Exception e) { throw new java.io.IOException(e); } };
            var sprites = BuildTierPets.sprites(store);
            int written = 0, skipped = 0, failed = 0;
            for (int id : new java.util.TreeSet<>(slots.keySet())) {
                if (id < from) continue;
                ItemDefinition def = ip.provide(id);
                if (def == null || def.name == null || def.name.isEmpty() || def.name.equalsIgnoreCase("null")
                        || def.notedTemplate != -1 || def.placeholderTemplateId != -1 || def.boughtTemplateId != -1) {
                    skipped++;
                    continue;
                }
                try {
                    BufferedImage s = ItemSpriteFactory.createSprite(ip, mp, sprites, textures, id, 1, 1, 0x302020, false);
                    if (s == null) { failed++; continue; }
                    BufferedImage big = new BufferedImage(s.getWidth() * 3, s.getHeight() * 3, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D g = big.createGraphics();
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                    g.drawImage(s, 0, 0, big.getWidth(), big.getHeight(), null);
                    g.dispose();
                    ImageIO.write(big, "PNG", new File(out, id + ".png"));
                    written++;
                } catch (Exception e) {
                    failed++;
                    System.out.println("  " + id + " \"" + def.name + "\": " + e);
                }
            }
            System.out.println("icons written " + written + ", skipped " + skipped + " (noted / placeholder / nameless), failed " + failed);
        }
    }
}
