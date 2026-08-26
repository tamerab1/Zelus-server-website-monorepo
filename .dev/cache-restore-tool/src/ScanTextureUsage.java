import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.ItemDefinition;
import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.loaders.ItemLoader;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;

import java.io.File;
import java.util.*;

// Scans every item in the archive, finds which items reference textured model faces, and
// prints one example item name per distinct texture id encountered. Used to find candidate
// alternate textures (different color/animation) that could substitute for a known texture id
// via an item-level textureFind/textureReplace (opcode 41) override.
public class ScanTextureUsage {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];

        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Index index = store.getIndex(IndexType.CONFIGS);
            Archive archive = index.getArchive(ConfigType.ITEM.getId());
            byte[] decompressed = archive.decompress(storage.loadArchive(archive));
            FileData[] fileData = archive.getFileData();
            List<byte[]> fileContents = SpliceItemOption.splitChunks(decompressed, fileData.length);

            ItemLoader loader = new ItemLoader();
            Index models = store.getIndex(IndexType.MODELS);
            ModelLoader mloader = new ModelLoader();

            Map<Short, String> textureExamples = new TreeMap<>();
            Map<Integer, ModelDefinition> modelCache = new HashMap<>();

            for (int i = 0; i < fileData.length; i++) {
                int id = fileData[i].getId();
                ItemDefinition def;
                try {
                    def = loader.load(id, fileContents.get(i));
                } catch (Exception e) {
                    continue;
                }
                if (def.name == null) continue;
                int[] modelIds = {def.inventoryModel, def.maleModel0};
                for (int modelId : modelIds) {
                    if (modelId <= 0) continue;
                    ModelDefinition md = modelCache.get(modelId);
                    if (md == null && !modelCache.containsKey(modelId)) {
                        Archive modelArchive = models.getArchive(modelId);
                        if (modelArchive == null) {
                            modelCache.put(modelId, null);
                            continue;
                        }
                        try {
                            byte[] data = modelArchive.decompress(storage.loadArchive(modelArchive));
                            md = mloader.load(modelId, data);
                        } catch (Exception e) {
                            md = null;
                        }
                        modelCache.put(modelId, md);
                    }
                    if (md == null || md.faceTextures == null) continue;
                    for (short t : md.faceTextures) {
                        if (t != -1 && !textureExamples.containsKey(t)) {
                            textureExamples.put(t, def.name + " (item " + id + ", model " + modelId + ")");
                        }
                    }
                }
            }

            System.out.println("Total distinct textures found: " + textureExamples.size());
            for (Map.Entry<Short, String> e : textureExamples.entrySet()) {
                System.out.println("texture " + e.getKey() + " -> " + e.getValue());
            }
        }
    }
}
