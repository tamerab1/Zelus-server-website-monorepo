import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.fs.*;

import java.io.File;
import java.nio.file.Files;
import java.util.*;

// Read-only (2026-10-19): distinct texture ids used by raw .dat models (faces per texture), and whether each texture
// exists in the cache's TEXTURES index (a missing one renders those faces wrong).
// Usage: RawModelTextures <cache> <model.dat>...
public class RawModelTextures {
    public static void main(String[] args) throws Exception {
        try (Store store = new Store(new File(args[0]))) {
            store.load();
            Index textures = store.getIndex(IndexType.TEXTURES);
            Set<Integer> have = new HashSet<>();
            for (Archive a : textures.getArchives())
                for (var fd : a.getFileData()) have.add(fd.getId());
            System.out.println("textures in cache: " + have.size());
            for (int i = 1; i < args.length; i++) {
                ModelDefinition m = new ModelLoader().load(0, Files.readAllBytes(new File(args[i]).toPath()));
                Map<Integer, Integer> count = new TreeMap<>();
                if (m.faceTextures != null) for (short t : m.faceTextures) if (t != -1) count.merge((int) t, 1, Integer::sum);
                StringBuilder sb = new StringBuilder();
                count.forEach((t, c) -> sb.append(" ").append(t).append(have.contains(t) ? "" : "(MISSING)").append("x").append(c));
                System.out.println(new File(args[i]).getName() + ":" + sb);
            }
        }
    }
}
