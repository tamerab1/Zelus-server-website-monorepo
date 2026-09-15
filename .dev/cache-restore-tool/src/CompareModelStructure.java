import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Store;
import java.io.File;

public class CompareModelStructure {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        try (Store store = new Store(new File(cachePath))) {
            store.load();
            for (int i = 1; i < args.length; i++) {
                int id = Integer.parseInt(args[i]);
                Archive a = store.getIndex(IndexType.MODELS).getArchive(id);
                byte[] data = a.decompress(store.getStorage().loadArchive(a));
                ModelDefinition md = new ModelLoader().load(id, data);
                System.out.println("=== model " + id + " (raw bytes=" + data.length + ") ===");
                System.out.println("  vertexCount=" + md.vertexCount + " faceCount=" + md.faceCount
                        + " numTextureFaces=" + md.numTextureFaces + " priority=" + md.priority);
                System.out.println("  faceRenderPriorities=" + (md.faceRenderPriorities == null ? "null" : "present len=" + md.faceRenderPriorities.length));
                System.out.println("  faceRenderTypes=" + (md.faceRenderTypes == null ? "null" : "present")
                        + " faceTransparencies=" + (md.faceTransparencies == null ? "null" : "present")
                        + " faceColors=" + (md.faceColors == null ? "null" : "present len=" + md.faceColors.length)
                        + " faceTextures=" + (md.faceTextures == null ? "null" : "present"));
                System.out.println("  textureCoords=" + (md.textureCoords == null ? "null" : "present")
                        + " textureRenderTypes=" + (md.textureRenderTypes == null ? "null" : "present"));
                System.out.println("  packedTransparencyVertexGroups=" + (md.packedTransparencyVertexGroups == null ? "null" : "present")
                        + " packedVertexGroups=" + (md.packedVertexGroups == null ? "null" : "present"));
                System.out.println("  animayaGroups=" + (md.animayaGroups == null ? "null" : "present")
                        + " animayaScales=" + (md.animayaScales == null ? "null" : "present"));
                int n = data.length;
                StringBuilder tail = new StringBuilder();
                for (int k = Math.max(0, n - 24); k < n; k++) tail.append(data[k] & 0xFF).append(" ");
                System.out.println("  last 24 raw bytes: " + tail);
                StringBuilder head = new StringBuilder();
                for (int k = 0; k < Math.min(24, n); k++) head.append(data[k] & 0xFF).append(" ");
                System.out.println("  first 24 raw bytes: " + head);
            }
        }
    }
}
