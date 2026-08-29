import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;

import java.io.File;

public class CheckVertexRange {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Index models = store.getIndex(IndexType.MODELS);
            for (int a = 1; a < args.length; a++) {
                int modelId = Integer.parseInt(args[a]);
                Archive archive = models.getArchive(modelId);
                byte[] raw = archive.decompress(storage.loadArchive(archive));
                ModelDefinition def = new ModelLoader().load(modelId, raw.clone());
                int minX=Integer.MAX_VALUE,maxX=Integer.MIN_VALUE,minY=Integer.MAX_VALUE,maxY=Integer.MIN_VALUE,minZ=Integer.MAX_VALUE,maxZ=Integer.MIN_VALUE;
                for (int x : def.vertexX) { minX=Math.min(minX,x); maxX=Math.max(maxX,x); }
                for (int y : def.vertexY) { minY=Math.min(minY,y); maxY=Math.max(maxY,y); }
                for (int z : def.vertexZ) { minZ=Math.min(minZ,z); maxZ=Math.max(maxZ,z); }
                System.out.println(modelId + ": X[" + minX + "," + maxX + "] Y[" + minY + "," + maxY
                        + "] Z[" + minZ + "," + maxZ + "] faceColors[0..5]="
                        + java.util.Arrays.toString(java.util.Arrays.copyOf(def.faceColors, Math.min(5, def.faceColors.length))));
            }
        }
    }
}
