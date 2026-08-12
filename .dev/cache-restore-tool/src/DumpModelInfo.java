import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;

import java.io.File;

// Read-only: raw model diagnostics by model id directly (no item/npc wrapper needed) --
// face count, texture usage, and transparency/priority data, to check for renderer-specific
// features (alpha blending, render priority) that a CPU/software renderer might not support.
public class DumpModelInfo {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        int[] modelIds = new int[args.length - 1];
        for (int i = 1; i < args.length; i++) modelIds[i - 1] = Integer.parseInt(args[i]);

        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Index models = store.getIndex(IndexType.MODELS);
            ModelLoader loader = new ModelLoader();

            for (int modelId : modelIds) {
                Archive archive = models.getArchive(modelId);
                if (archive == null) {
                    System.out.println("model " + modelId + " -- NOT FOUND");
                    continue;
                }
                byte[] data = archive.decompress(storage.loadArchive(archive));
                ModelDefinition md = loader.load(modelId, data);

                int totalFaces = md.faceColors == null ? 0 : md.faceColors.length;
                int texturedFaces = 0;
                if (md.faceTextures != null) {
                    for (short t : md.faceTextures) if (t != -1) texturedFaces++;
                }
                int transparentFaces = 0;
                int minTransparency = 255, maxTransparency = 0;
                if (md.faceTransparencies != null) {
                    for (byte t : md.faceTransparencies) {
                        int v = t & 0xFF;
                        if (v != 0) transparentFaces++;
                        minTransparency = Math.min(minTransparency, v);
                        maxTransparency = Math.max(maxTransparency, v);
                    }
                }
                int nonZeroRenderPriorityFaces = 0;
                if (md.faceRenderPriorities != null) {
                    for (byte p : md.faceRenderPriorities) if (p != 0) nonZeroRenderPriorityFaces++;
                }

                System.out.println("model " + modelId + ": totalFaces=" + totalFaces
                        + " texturedFaces=" + texturedFaces
                        + " vertices=" + (md.vertexCount)
                        + " faceTransparencies=" + (md.faceTransparencies == null ? "null (none)" :
                                "present, transparentFaces=" + transparentFaces + " range=[" + minTransparency + "," + maxTransparency + "]")
                        + " faceRenderPriorities=" + (md.faceRenderPriorities == null ? "null" :
                                "present, nonZeroPriorityFaces=" + nonZeroRenderPriorityFaces)
                        + " modelPriority=" + md.priority
                        + " packedTransparencyVertexGroups=" + (md.packedTransparencyVertexGroups == null ? "null" : "present len=" + md.packedTransparencyVertexGroups.length));
            }
        }
    }
}
