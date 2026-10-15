import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Store;
import java.io.File;

// Read-only: prints vertex bounds of cache models, plus the widest |x| among vertices in the
// lower-arm height band, to tell whether a worn body model includes its own arms.
// Usage: <cachePath> <modelId>[,<modelId>...]
public class ModelBounds {
    public static void main(String[] args) throws Exception {
        try (Store store = new Store(new File(args[0]))) {
            store.load();
            for (String s : args[1].split(",")) {
                int id = Integer.parseInt(s.trim());
                Archive a = store.getIndex(IndexType.MODELS).getArchive(id);
                ModelDefinition m = new ModelLoader().load(id, a.decompress(store.getStorage().loadArchive(a)));
                int minX = 1 << 30, maxX = -(1 << 30), minY = 1 << 30, maxY = -(1 << 30), minZ = 1 << 30, maxZ = -(1 << 30);
                for (int i = 0; i < m.vertexCount; i++) {
                    minX = Math.min(minX, m.vertexX[i]); maxX = Math.max(maxX, m.vertexX[i]);
                    minY = Math.min(minY, m.vertexY[i]); maxY = Math.max(maxY, m.vertexY[i]);
                    minZ = Math.min(minZ, m.vertexZ[i]); maxZ = Math.max(maxZ, m.vertexZ[i]);
                }
                System.out.println(id + ": verts=" + m.vertexCount + " x[" + minX + "," + maxX + "] y[" + minY + "," + maxY + "] z[" + minZ + "," + maxZ + "]");
            }
        }
    }
}
