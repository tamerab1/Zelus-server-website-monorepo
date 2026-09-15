import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

import net.runelite.cache.IndexType;
import net.runelite.cache.SpriteManager;
import net.runelite.cache.TextureManager;
import net.runelite.cache.definitions.ItemDefinition;
import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.definitions.providers.ItemProvider;
import net.runelite.cache.definitions.providers.ModelProvider;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.item.ItemSpriteFactory;

// Fast icon-framing iteration: renders a synthetic ItemDefinition directly from explicit
// zoom2d/xan2d/yan2d/zan2d/resize values against a model id already in the cache, without needing
// a real item def to exist first. Used to converge on framing values before committing them via
// SpliceNewWearableItem.
//
// Usage: RenderModelIconDirect <cachePath> <outFile.png> <modelId> <zoom2d> <xan2d> <yan2d> <zan2d> <resizeX> <resizeY> <resizeZ>
public class RenderModelIconDirect {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        String outFile = args[1];
        int modelId = Integer.parseInt(args[2]);
        int zoom2d = Integer.parseInt(args[3]);
        int xan2d = Integer.parseInt(args[4]);
        int yan2d = Integer.parseInt(args[5]);
        int zan2d = Integer.parseInt(args[6]);
        int resizeX = Integer.parseInt(args[7]);
        int resizeY = Integer.parseInt(args[8]);
        int resizeZ = Integer.parseInt(args[9]);

        try (Store store = new Store(new File(cachePath))) {
            store.load();

            ItemDefinition def = new ItemDefinition(999999);
            def.name = "preview";
            def.inventoryModel = modelId;
            def.zoom2d = zoom2d;
            def.xan2d = xan2d;
            def.yan2d = yan2d;
            def.zan2d = zan2d;
            def.resizeX = resizeX;
            def.resizeY = resizeY;
            def.resizeZ = resizeZ;

            ItemProvider itemProvider = itemId -> def;
            ModelProvider modelProvider = mid -> {
                Archive archive = store.getIndex(IndexType.MODELS).getArchive(mid);
                byte[] data = archive.decompress(store.getStorage().loadArchive(archive));
                return new ModelLoader().load(mid, data);
            };

            SpriteManager spriteManager = new SpriteManager(store);
            spriteManager.load();
            TextureManager textureManager = new TextureManager(store);
            textureManager.load();

            BufferedImage sprite = ItemSpriteFactory.createSprite(
                    itemProvider, modelProvider, spriteManager, textureManager, 999999, 1, 0, 0, false);

            if (sprite == null) {
                System.out.println("NULL SPRITE");
                return;
            }
            ImageIO.write(sprite, "PNG", new File(outFile));
            System.out.println("wrote " + outFile + " (" + sprite.getWidth() + "x" + sprite.getHeight() + ")");
        }
    }
}
