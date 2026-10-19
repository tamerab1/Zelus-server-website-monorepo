import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.SpriteManager;
import net.runelite.cache.TextureManager;
import net.runelite.cache.definitions.ItemDefinition;
import net.runelite.cache.definitions.loaders.ItemLoader;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.definitions.providers.ItemProvider;
import net.runelite.cache.definitions.providers.ModelProvider;
import net.runelite.cache.fs.*;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

// Read-only (2026-10-19, Vermilion masori icons): renders one item's inventory icon through the client's ItemSpriteFactory
// over a grid of camera angles (rows xan2d, columns yan2d; zan2d fixed), each fitted like AutoFrameItems (zoom + centred
// offsets), 3x on the inventory brown with its angles printed -- to pick an upright angle by eye when a rip's
// inventory model isn't oriented like the template's.
// Usage: IconAngleGrid <cache> <itemId> <zan2d> <xan,xan,..> <yan,yan,..> <out.png>
public class IconAngleGrid {
    public static void main(String[] args) throws Exception {
        int id = Integer.parseInt(args[1]), zan = Integer.parseInt(args[2]);
        String[] xs = args[3].split(","), ys = args[4].split(",");
        try (Store store = new Store(new File(args[0]))) {
            store.load();
            Storage storage = store.getStorage();
            Archive items = store.getIndex(IndexType.CONFIGS).getArchive(ConfigType.ITEM.getId());
            AutoFrameItems.fileData = items.getFileData();
            AutoFrameItems.contents = SpliceItemOption.splitChunks(items.decompress(storage.loadArchive(items)), AutoFrameItems.fileData.length);
            ItemLoader loader = new ItemLoader();
            ItemProvider ip = i -> {
                if (AutoFrameItems.overrides.containsKey(i)) return AutoFrameItems.overrides.get(i);
                int slot = AutoFrameItems.slot(i);
                return slot < 0 ? null : loader.load(i, AutoFrameItems.contents.get(slot));
            };
            ModelProvider mp = i -> {
                Archive a = store.getIndex(IndexType.MODELS).getArchive(i);
                return new ModelLoader().load(i, a.decompress(storage.loadArchive(a)));
            };
            SpriteManager sprites = new SpriteManager(store);
            try { sprites.load(); } catch (Exception ignored) { }
            TextureManager textures = new TextureManager(store);
            textures.load();
            ItemDefinition base = loader.load(id, AutoFrameItems.contents.get(AutoFrameItems.slot(id)));
            Geo geo = new Geo(mp.provide(base.inventoryModel), base.resizeX, base.resizeY, base.resizeZ);
            int cw = 36 * 3 + 8, ch = 32 * 3 + 16;
            BufferedImage sheet = new BufferedImage(cw * ys.length, ch * xs.length, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = sheet.createGraphics();
            g.setColor(new Color(0x3e3529));
            g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
            for (int r = 0; r < xs.length; r++)
                for (int c = 0; c < ys.length; c++) {
                    int xan = Integer.parseInt(xs[r]), yan = Integer.parseInt(ys[c]);
                    int[] fz = geo.fit(xan, yan, zan);
                    BufferedImage img = AutoFrameItems.render(ip, mp, sprites, textures, AutoFrameItems.framed(base, fz[0], xan, yan, zan, fz[1], fz[2]));
                    if (img != null) g.drawImage(img, c * cw + 4, r * ch + 14, 36 * 3, 32 * 3, null);
                    g.setColor(new Color(0x5a4d3b));
                    g.drawRect(c * cw + 4, r * ch + 14, 36 * 3, 32 * 3);
                    g.setColor(Color.WHITE);
                    g.setFont(new Font("SansSerif", Font.PLAIN, 10));
                    g.drawString(xan + "," + yan, c * cw + 4, r * ch + 11);
                }
            g.dispose();
            ImageIO.write(sheet, "png", new File(args[5]));
            System.out.println("wrote " + args[5]);
        }
    }
}
