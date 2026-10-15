import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import javax.imageio.ImageIO;

import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.SpriteManager;
import net.runelite.cache.TextureManager;
import net.runelite.cache.definitions.ItemDefinition;
import net.runelite.cache.definitions.loaders.ItemLoader;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.definitions.providers.ItemProvider;
import net.runelite.cache.definitions.providers.ModelProvider;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;
import net.runelite.cache.item.ItemSpriteFactory;

// Read-only: renders an item's real inventory icon (the same 36x32 sprite the client draws)
// straight from the live cache's 3D model + colour/texture data, and writes it as a PNG.
// Uses SpliceItemOption.splitChunks() (the proven List-backed positional splitter already
// validated against this cache's item config archive, which has ~828 pre-existing duplicate
// file-id entries that crash RuneLite's Map-backed ArchiveFiles.loadContents()) -- everything
// else (models, sprites, textures) loads through the real, unmodified RuneLite cache managers.
//
// Usage: RenderItemIcon <cachePath> <outDir> <itemId[@modelId]:filename.png> [...]  (@modelId = draw that model instead)
public class RenderItemIcon {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        String outDir = args[1];

        try (Store store = new Store(new File(cachePath))) {
            store.load();

            Storage storage = store.getStorage();
            Index configIndex = store.getIndex(IndexType.CONFIGS);
            Archive itemArchive = configIndex.getArchive(ConfigType.ITEM.getId());
            byte[] itemArchiveData = storage.loadArchive(itemArchive);
            byte[] decompressed = itemArchive.decompress(itemArchiveData);
            FileData[] fileData = itemArchive.getFileData();
            List<byte[]> fileContents = SpliceItemOption.splitChunks(decompressed, fileData.length);

            ItemLoader itemLoader = new ItemLoader();
            java.util.Map<Integer, Integer> modelOverride = new java.util.HashMap<>();
            ItemProvider itemProvider = itemId -> {
                for (int i = 0; i < fileData.length; i++) {
                    if (fileData[i].getId() == itemId) {
                        try {
                            ItemDefinition d = itemLoader.load(itemId, fileContents.get(i));
                            Integer m = modelOverride.get(itemId);
                            if (m != null)
                                d.inventoryModel = m;
                            return d;
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
                return null;
            };

            ModelProvider modelProvider = modelId -> {
                Index models = store.getIndex(IndexType.MODELS);
                Archive archive = models.getArchive(modelId);
                byte[] data = archive.decompress(store.getStorage().loadArchive(archive));
                return new ModelLoader().load(modelId, data);
            };

            SpriteManager spriteManager = new SpriteManager(store);
            try {
                spriteManager.load();
            } catch (Exception e) {
                System.out.println("WARNING: SpriteManager.load() failed on a broken sprite slot ("
                        + e + ") -- continuing with partial/no sprite data, fine for pure 3D-model icons.");
            }

            TextureManager textureManager = new TextureManager(store);
            textureManager.load();

            File out = new File(outDir);
            out.mkdirs();

            for (int i = 2; i < args.length; i++) {
                String[] parts = args[i].split(":", 2);
                // itemId@modelId:file.png renders another model (e.g. a worn model) with the item's icon camera
                String[] idm = parts[0].split("@", 2);
                int itemId = Integer.parseInt(idm[0]);
                modelOverride.clear();
                if (idm.length == 2)
                    modelOverride.put(itemId, Integer.parseInt(idm[1]));
                String filename = parts[1];

                ItemDefinition def = itemProvider.provide(itemId);
                if (def == null) {
                    System.out.println(itemId + " -> NO ITEM DEFINITION FOUND");
                    continue;
                }

                BufferedImage sprite;
                try {
                    sprite = ItemSpriteFactory.createSprite(
                        itemProvider, modelProvider, spriteManager, textureManager,
                        itemId, 1, 0, 0, false
                    );
                } catch (IOException e) {
                    System.out.println(itemId + " -> RENDER FAILED: " + e);
                    continue;
                }

                if (sprite == null) {
                    System.out.println(itemId + " -> NULL SPRITE (missing inventoryModel="
                        + def.inventoryModel + "?)");
                    continue;
                }

                File pngFile = new File(out, filename);
                ImageIO.write(sprite, "PNG", pngFile);
                System.out.println(itemId + " (\"" + def.name + "\", inventoryModel=" + def.inventoryModel
                    + ") -> " + pngFile.getAbsolutePath() + " (" + sprite.getWidth() + "x" + sprite.getHeight() + ")");
            }
        }
    }
}
