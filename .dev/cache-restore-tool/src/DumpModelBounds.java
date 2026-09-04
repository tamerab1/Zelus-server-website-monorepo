import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;

import java.io.File;

// Read-only: prints each model's vertex bounding box (x/y/z extent in raw model units) --
// used to estimate a sane starting zoom2d for an item icon built from a raw NPC body model
// (these vary wildly in native scale, unlike purpose-built pet meshes).
public class DumpModelBounds {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        int[] modelIds = new int[args.length - 1];
        for (int i = 1; i < args.length; i++) modelIds[i - 1] = Integer.parseInt(args[i]);

        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Index models = store.getIndex(IndexType.MODELS);
            ModelLoader loader = new ModelLoader();

            for (int modelId : modelIds) {
                Archive archive = models.getArchive(modelId);
                if (archive == null) {
                    System.out.println("model " + modelId + " -- NOT FOUND");
                    continue;
                }
                byte[] data = archive.decompress(storage.loadArchive(archive));
                ModelDefinition md = loader.load(modelId, data);

                int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
                int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
                int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
                for (int i = 0; i < md.vertexCount; i++) {
                    minX = Math.min(minX, md.vertexX[i]);
                    maxX = Math.max(maxX, md.vertexX[i]);
                    minY = Math.min(minY, md.vertexY[i]);
                    maxY = Math.max(maxY, md.vertexY[i]);
                    minZ = Math.min(minZ, md.vertexZ[i]);
                    maxZ = Math.max(maxZ, md.vertexZ[i]);
                }
                int height = maxY - minY;
                int width = Math.max(maxX - minX, maxZ - minZ);
                System.out.println("model " + modelId + ": x=[" + minX + "," + maxX + "] y=[" + minY + "," + maxY
                        + "] z=[" + minZ + "," + maxZ + "] height=" + height + " width=" + width);
            }
        }
    }
}
