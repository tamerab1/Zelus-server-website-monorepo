import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.TextureManager;
import net.runelite.cache.definitions.loaders.ItemLoader;
import net.runelite.cache.definitions.providers.ItemProvider;
import net.runelite.cache.definitions.providers.ModelProvider;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;
import net.runelite.cache.item.ItemSpriteFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Read-only: renders inventory icons like RenderItemIcon, but with BuildTierPets' lazy sprite provider
// (SpriteManager.load() dies on a broken sprite slot, so icons using new textures crash RenderItemIcon).
// Writes one 36x32 PNG per item plus a 4x contact sheet.
// Usage: RenderItemsLazy <cachePath> <outDir> <itemId:name> [itemId:name ...]
public class RenderItemsLazy {
    public static void main(String[] args) throws Exception {
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
            int n = args.length - 2, scale = 4;
            BufferedImage sheet = new BufferedImage(n * (36 * scale + 8) + 8, 32 * scale + 8, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = sheet.createGraphics();
            g.setColor(new Color(0x3E3529));
            g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
            for (int i = 0; i < n; i++) {
                String[] p = args[i + 2].split(":");
                int id = Integer.parseInt(p[0]);
                BufferedImage s = ItemSpriteFactory.createSprite(ip, mp, sprites, textures, id, 1, 1, 0x302020, false);
                ImageIO.write(s, "PNG", new File(args[1], p[1] + ".png"));
                g.drawImage(s, 8 + i * (36 * scale + 8), 4, 36 * scale, 32 * scale, null);
                System.out.println(id + " -> " + p[1] + ".png");
            }
            g.dispose();
            ImageIO.write(sheet, "PNG", new File(args[1], "sheet.png"));
        }
    }
}
