import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;

import java.io.File;

public class DumpSkinning {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Index models = store.getIndex(IndexType.MODELS);
            for (String idStr : args[1].split(",")) {
                int modelId = Integer.parseInt(idStr.trim());
                Archive archive = models.getArchive(modelId);
                if (archive == null) {
                    System.out.println(modelId + ": ARCHIVE NULL");
                    continue;
                }
                byte[] raw = archive.decompress(storage.loadArchive(archive));
                ModelDefinition def = new ModelLoader().load(modelId, raw.clone());
                System.out.println(modelId + ": vertexCount=" + def.vertexCount + " faceCount=" + def.faceCount
                        + " priority=" + def.priority
                        + " packedVertexGroups=" + (def.packedVertexGroups != null)
                        + " animaya=" + (def.animayaGroups != null)
                        + " faceTransparencies=" + (def.faceTransparencies != null)
                        + " packedTransparencyVG=" + (def.packedTransparencyVertexGroups != null)
                        + " faceRenderPriorities=" + (def.faceRenderPriorities != null));
                if (def.packedVertexGroups != null) {
                    java.util.Set<Integer> distinct = new java.util.TreeSet<>();
                    for (int v : def.packedVertexGroups) distinct.add(v);
                    System.out.println("    packedVertexGroups distinct=" + distinct);
                }
                if (def.animayaGroups != null) {
                    java.util.Set<Integer> distinctG = new java.util.TreeSet<>();
                    int maxLen = 0;
                    for (int[] g : def.animayaGroups) {
                        if (g == null) continue;
                        maxLen = Math.max(maxLen, g.length);
                        for (int v : g) distinctG.add(v);
                    }
                    System.out.println("    animayaGroups distinct=" + distinctG + " maxPerVertex=" + maxLen);
                }
            }
        }
    }
}
