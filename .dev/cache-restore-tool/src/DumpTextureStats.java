import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;

import java.io.File;

public class DumpTextureStats {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Index models = store.getIndex(IndexType.MODELS);
            for (String idStr : args[1].split(",")) {
                int modelId = Integer.parseInt(idStr.trim());
                Archive archive = models.getArchive(modelId);
                byte[] raw = archive.decompress(storage.loadArchive(archive));
                ModelDefinition def = new ModelLoader().load(modelId, raw.clone());
                int texturedFaces = 0;
                if (def.faceTextures != null) {
                    for (short v : def.faceTextures) if (v != -1) texturedFaces++;
                }
                int coordOverrides = 0;
                if (def.textureCoords != null) {
                    for (byte v : def.textureCoords) if (v != -1) coordOverrides++;
                }
                System.out.println(modelId + ": faceCount=" + def.faceCount
                        + " numTextureFaces=" + def.numTextureFaces
                        + " texturedFaces=" + texturedFaces
                        + " textureCoordsNonNull=" + (def.textureCoords != null)
                        + " coordOverrides=" + coordOverrides
                        + " texIndicesNonNull=" + (def.texIndices1 != null));
                if (def.faceTextures != null) {
                    java.util.Set<Short> distinct = new java.util.TreeSet<>();
                    for (short v : def.faceTextures) if (v != -1) distinct.add(v);
                    System.out.println("    distinct faceTexture ids=" + distinct);
                }
            }
        }
    }
}
