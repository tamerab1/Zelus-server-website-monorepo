import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Store;
import java.io.File;
import java.util.TreeMap;

// Read-only: face colour histogram for individual cache models. Usage: <cachePath> <modelId>[,<modelId>...]
public class ModelFaceColors {
    public static void main(String[] args) throws Exception {
        try (Store store = new Store(new File(args[0]))) {
            store.load();
            for (String s : args[1].split(",")) {
                int id = Integer.parseInt(s.trim());
                Archive a = store.getIndex(IndexType.MODELS).getArchive(id);
                ModelDefinition m = new ModelLoader().load(id, a.decompress(store.getStorage().loadArchive(a)));
                TreeMap<Integer, Integer> h = new TreeMap<>();
                for (int i = 0; i < m.faceCount; i++) h.merge(m.faceColors[i] & 0xFFFF, 1, Integer::sum);
                System.out.println(id + ": " + h);
            }
        }
    }
}
