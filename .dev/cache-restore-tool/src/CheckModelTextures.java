import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Store;
import java.io.File;
import java.util.TreeSet;

// Checks whether a model's referenced texture ids actually exist in this cache's TEXTURES index --
// a model referencing a texture id from its original (unrelated) source engine that doesn't exist
// here would fail to resolve that face's appearance, which on some renderers means the WHOLE
// model gets skipped rather than just that face.
public class CheckModelTextures {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        try (Store store = new Store(new File(cachePath))) {
            store.load();
            var texIndex = store.getIndex(IndexType.TEXTURES);
            int maxTexId = -1;
            if (texIndex != null) {
                for (var arch : texIndex.getArchives()) {
                    if (arch != null && arch.getArchiveId() > maxTexId) maxTexId = arch.getArchiveId();
                }
            }
            System.out.println("TEXTURES index max archive id = " + maxTexId + " (null index = " + (texIndex == null) + ")");

            for (int i = 1; i < args.length; i++) {
                int id = Integer.parseInt(args[i]);
                Archive a = store.getIndex(IndexType.MODELS).getArchive(id);
                byte[] data = a.decompress(store.getStorage().loadArchive(a));
                ModelDefinition md = new ModelLoader().load(id, data);
                if (md.faceTextures == null) {
                    System.out.println("model " + id + " -> no faceTextures (flat colored)");
                    continue;
                }
                TreeSet<Integer> distinctTex = new TreeSet<>();
                for (short t : md.faceTextures) if (t != -1) distinctTex.add((int) t);
                System.out.println("model " + id + " -> distinct texture ids referenced: " + distinctTex);
                for (int tex : distinctTex) {
                    boolean exists = texIndex != null && texIndex.getArchive(tex) != null;
                    System.out.println("    texture " + tex + " exists in TEXTURES index: " + exists);
                }
            }
        }
    }
}
