import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.SpriteDefinition;
import net.runelite.cache.definitions.TextureDefinition;
import net.runelite.cache.definitions.loaders.SpriteLoader;
import net.runelite.cache.definitions.loaders.TextureLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;

import java.io.File;
import java.util.List;

public class CheckTexture {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Index textures = store.getIndex(IndexType.TEXTURES);
            Index sprites = store.getIndex(IndexType.SPRITES);

            Archive texArchive = textures.getArchive(0);
            byte[] texDecompressed = texArchive.decompress(storage.loadArchive(texArchive));
            FileData[] texFileData = texArchive.getFileData();
            List<byte[]> texContents = SpliceItemOption.splitChunks(texDecompressed, texFileData.length);

            for (int a = 1; a < args.length; a++) {
                int texId = Integer.parseInt(args[a]);
                int slot = -1;
                for (int i = 0; i < texFileData.length; i++) {
                    if (texFileData[i].getId() == texId) { slot = i; break; }
                }
                if (slot == -1) {
                    System.out.println("texture " + texId + ": NOT FOUND in " + texFileData.length + " entries");
                    continue;
                }
                TextureLoader loader = new TextureLoader();
                TextureDefinition def = loader.load(texId, texContents.get(slot));
                int[] spriteIds = def.getFileIds();
                System.out.println("texture " + texId + ": spriteIds=" + java.util.Arrays.toString(spriteIds));
                for (int sid : spriteIds) {
                    Archive spriteArchive = sprites.getArchive(sid);
                    if (spriteArchive == null) {
                        System.out.println("  sprite " + sid + ": ARCHIVE MISSING");
                        continue;
                    }
                    byte[] spriteRaw = spriteArchive.decompress(storage.loadArchive(spriteArchive));
                    SpriteDefinition[] spriteDefs = new SpriteLoader().load(sid, spriteRaw);
                    for (SpriteDefinition sd : spriteDefs) {
                        int[] px = sd.getPixels();
                        int nonZero = 0;
                        for (int p : px) if ((p >>> 24) != 0) nonZero++;
                        System.out.println("  sprite " + sid + ": " + sd.getWidth() + "x" + sd.getHeight()
                                + " opaquePixels=" + nonZero + "/" + px.length);
                    }
                }
            }
        }
    }
}
