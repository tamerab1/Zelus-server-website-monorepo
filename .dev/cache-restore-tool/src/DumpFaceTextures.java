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
import java.util.List;

// Diagnostic: checks whether an item's model(s) have any textured faces (faceTextures != -1),
// and whether the item def itself has a textureFind/textureReplace override table (opcode 41).
public class DumpFaceTextures {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        int targetId = Integer.parseInt(args[1]);

        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Index index = store.getIndex(IndexType.CONFIGS);
            Archive archive = index.getArchive(ConfigType.ITEM.getId());
            byte[] decompressed = archive.decompress(storage.loadArchive(archive));
            FileData[] fileData = archive.getFileData();
            List<byte[]> fileContents = SpliceItemOption.splitChunks(decompressed, fileData.length);

            ItemLoader loader = new ItemLoader();
            ItemDefinition def = null;
            for (int i = 0; i < fileData.length; i++) {
                if (fileData[i].getId() == targetId) {
                    def = loader.load(targetId, fileContents.get(i));
                }
            }
            if (def == null) {
                System.out.println("id=" + targetId + " -- NOT FOUND");
                return;
            }
            System.out.println("name=\"" + def.name + "\" inventoryModel=" + def.inventoryModel
                    + " maleModel0=" + def.maleModel0);
            System.out.println("item-level textureFind=" + (def.textureFind == null ? "null" : java.util.Arrays.toString(def.textureFind)));
            System.out.println("item-level textureReplace=" + (def.textureReplace == null ? "null" : java.util.Arrays.toString(def.textureReplace)));

            Index models = store.getIndex(IndexType.MODELS);
            int[] modelIds = {def.inventoryModel, def.maleModel0, def.maleModel1, def.maleModel2};
            ModelLoader mloader = new ModelLoader();
            for (int modelId : modelIds) {
                if (modelId <= 0) continue;
                Archive modelArchive = models.getArchive(modelId);
                if (modelArchive == null) {
                    System.out.println("model " + modelId + " -- NOT FOUND");
                    continue;
                }
                byte[] data = modelArchive.decompress(storage.loadArchive(modelArchive));
                ModelDefinition md = mloader.load(modelId, data);
                int totalFaces = md.faceColors == null ? 0 : md.faceColors.length;
                int texturedFaces = 0;
                java.util.Set<Short> distinctTextures = new java.util.TreeSet<>();
                if (md.faceTextures != null) {
                    for (short t : md.faceTextures) {
                        if (t != -1) {
                            texturedFaces++;
                            distinctTextures.add(t);
                        }
                    }
                }
                System.out.println("model " + modelId + ": totalFaces=" + totalFaces
                        + " texturedFaces=" + texturedFaces
                        + " distinctTextureIds=" + distinctTextures);
            }
        }
    }
}
