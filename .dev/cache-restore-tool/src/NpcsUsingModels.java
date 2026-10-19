import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.NpcDefinition;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.definitions.loaders.NpcLoader;
import net.runelite.cache.fs.*;
import net.runelite.cache.index.FileData;

import java.io.File;
import java.util.*;

// Read-only (2026-10-19): for each model id, its vertex / face count in the cache and every npc whose models use it
// (id, name, size, stand / walk). Tells whether a ripped model with a vanilla-range id is the cache's own model.
// Usage: NpcsUsingModels <cache> <modelId>...
public class NpcsUsingModels {
    public static void main(String[] args) throws Exception {
        try (Store store = new Store(new File(args[0]))) {
            store.load();
            Storage storage = store.getStorage();
            Index models = store.getIndex(IndexType.MODELS);
            Archive npcArchive = store.getIndex(IndexType.CONFIGS).getArchive(ConfigType.NPC.getId());
            FileData[] fd = npcArchive.getFileData();
            List<byte[]> files = SpliceItemOption.splitChunks(npcArchive.decompress(storage.loadArchive(npcArchive)), fd.length);
            for (int a = 1; a < args.length; a++) {
                int id = Integer.parseInt(args[a]);
                Archive ar = models.getArchive(id);
                if (ar == null) {
                    System.out.println("model " + id + ": not in cache");
                } else {
                    ModelDefinition m = new ModelLoader().load(id, ar.decompress(storage.loadArchive(ar)));
                    System.out.println("model " + id + ": v=" + m.vertexCount + " f=" + m.faceCount);
                }
                for (int i = 0; i < fd.length; i++) {
                    NpcDefinition d = new NpcLoader().load(fd[i].getId(), files.get(i));
                    if (d.models != null && Arrays.stream(d.models).anyMatch(x -> x == id))
                        System.out.printf("   npc %d %s size %d stand %d walk %d models %s%n", d.id, d.name, d.size,
                                d.standingAnimation, d.walkingAnimation, Arrays.toString(d.models));
                }
            }
        }
    }
}
