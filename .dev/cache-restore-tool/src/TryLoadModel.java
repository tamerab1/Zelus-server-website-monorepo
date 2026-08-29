import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;

import java.io.File;

public class TryLoadModel {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Index models = store.getIndex(IndexType.MODELS);
            for (int i = 1; i < args.length; i++) {
                int modelId = Integer.parseInt(args[i]);
                try {
                    Archive archive = models.getArchive(modelId);
                    if (archive == null) {
                        System.out.println(modelId + ": ARCHIVE NULL (missing entirely)");
                        continue;
                    }
                    byte[] raw = archive.decompress(storage.loadArchive(archive));
                    ModelLoader loader = new ModelLoader();
                    ModelDefinition def = loader.load(modelId, raw.clone());
                    System.out.println(modelId + ": OK verts=" + def.vertexCount + " faces=" + def.faceCount
                            + " packedVG=" + (def.packedVertexGroups != null)
                            + " faceTex=" + (def.faceTextures != null)
                            + " vertexSkins=" + (def.packedVertexGroups != null ? java.util.Arrays.toString(
                                    java.util.Arrays.stream(def.packedVertexGroups).distinct().sorted().toArray())
                                    : "null"));
                } catch (Throwable t) {
                    System.out.println(modelId + ": EXCEPTION " + t);
                }
            }
        }
    }
}
