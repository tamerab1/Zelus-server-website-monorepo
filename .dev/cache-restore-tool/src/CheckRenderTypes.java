import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.fs.*;
import java.io.File;
public class CheckRenderTypes {
    public static void main(String[] args) throws Exception {
        try (Store store = new Store(new File(args[0]))) {
            store.load();
            Storage storage = store.getStorage();
            Index models = store.getIndex(IndexType.MODELS);
            for (String idStr : args[1].split(",")) {
                int id = Integer.parseInt(idStr.trim());
                Archive a = models.getArchive(id);
                byte[] raw = a.decompress(storage.loadArchive(a));
                ModelDefinition def = new ModelLoader().load(id, raw.clone());
                System.out.println(id + ": faceRenderTypes=" + (def.faceRenderTypes != null)
                        + " textureRenderTypes=" + (def.textureRenderTypes != null));
            }
        }
    }
}
