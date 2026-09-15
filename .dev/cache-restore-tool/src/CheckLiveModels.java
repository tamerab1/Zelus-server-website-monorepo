import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Store;
import java.io.File;

public class CheckLiveModels {
    public static void main(String[] args) throws Exception {
        try (Store store = new Store(new File(args[0]))) {
            store.load();
            for (int i = 1; i < args.length; i++) {
                int id = Integer.parseInt(args[i]);
                Archive a = store.getIndex(IndexType.MODELS).getArchive(id);
                if (a == null) { System.out.println(id + " -> MISSING"); continue; }
                byte[] data = a.decompress(store.getStorage().loadArchive(a));
                ModelDefinition md = new ModelLoader().load(id, data);
                System.out.println(id + " -> verts=" + md.vertexCount + " faces=" + md.faceCount);
            }
        }
    }
}
