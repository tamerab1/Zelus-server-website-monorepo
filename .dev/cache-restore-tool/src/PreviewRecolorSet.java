import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.ItemManager;
import net.runelite.cache.definitions.ItemDefinition;
import net.runelite.cache.definitions.loaders.ItemLoader;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.definitions.providers.ItemProvider;
import net.runelite.cache.definitions.providers.ModelProvider;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;
import net.runelite.cache.item.ItemSpriteFactory;
import net.runelite.cache.SpriteManager;
import net.runelite.cache.TextureManager;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

// Read-only preview for CreateRecoloredItemSet: renders each spec item's source icon and its
// recoloured icon (recolour applied in memory only), side by side at 4x, into one PNG sheet.
// Usage: <cachePath> <specFile> <out.png>
public class PreviewRecolorSet {
    public static void main(String[] args) throws Exception {
        List<String> lines = Files.readAllLines(new File(args[1]).toPath());
        short[] find = null, replace = null;
        List<Integer> sources = new ArrayList<>();
        for (String raw : lines) {
            String l = raw.trim();
            if (l.isEmpty() || l.startsWith("#")) continue;
            String[] p = l.split("\\s+");
            if (p[0].equals("recolor")) {
                String[] pairs = p[1].split(",");
                find = new short[pairs.length / 2];
                replace = new short[pairs.length / 2];
                for (int i = 0; i < find.length; i++) {
                    find[i] = (short) Integer.parseInt(pairs[i * 2]);
                    replace[i] = (short) Integer.parseInt(pairs[i * 2 + 1]);
                }
            } else if (p[0].equals("item")) {
                sources.add(Integer.parseInt(p[1]));
            }
        }
        final short[] f = find, r = replace;

        try (Store store = new Store(new File(args[0]))) {
            store.load();
            Archive itemArchive = store.getIndex(IndexType.CONFIGS).getArchive(ConfigType.ITEM.getId());
            byte[] dec = itemArchive.decompress(store.getStorage().loadArchive(itemArchive));
            FileData[] fd = itemArchive.getFileData();
            List<byte[]> contents = SpliceItemOption.splitChunks(dec, fd.length);
            ItemLoader loader = new ItemLoader();

            // Ids >= 1_000_000 are "recoloured copy of id - 1_000_000".
            ItemProvider provider = id -> {
                int real = id >= 1_000_000 ? id - 1_000_000 : id;
                int slot = -1;
                for (int i = 0; i < fd.length; i++) if (fd[i].getId() == real) slot = i;
                if (slot == -1) return null;
                ItemDefinition d = loader.load(real, contents.get(slot));
                if (id >= 1_000_000) {
                    d.colorFind = f;
                    d.colorReplace = r;
                }
                return d;
            };
            ModelProvider models = modelId -> {
                Archive a = store.getIndex(IndexType.MODELS).getArchive(modelId);
                return new ModelLoader().load(modelId, a.decompress(store.getStorage().loadArchive(a)));
            };
            SpriteManager sprites = new SpriteManager(store);
            try { sprites.load(); } catch (Exception ignored) { }
            TextureManager textures = new TextureManager(store);
            textures.load();

            int scale = 4, cw = 36 * scale, ch = 32 * scale;
            BufferedImage sheet = new BufferedImage(cw * 2 + 30, ch * sources.size() + 10, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = sheet.createGraphics();
            g.setColor(new Color(0x3E3529));
            g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            for (int row = 0; row < sources.size(); row++) {
                int id = sources.get(row);
                for (int col = 0; col < 2; col++) {
                    BufferedImage s = ItemSpriteFactory.createSprite(provider, models, sprites, textures,
                            col == 0 ? id : id + 1_000_000, 1, 1, 0x302020, false);
                    if (s != null) g.drawImage(s, 10 + col * (cw + 10), 5 + row * ch, cw, ch, null);
                }
            }
            g.dispose();
            ImageIO.write(sheet, "PNG", new File(args[2]));
            System.out.println("wrote " + args[2]);
        }
    }
}
