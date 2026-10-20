import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.loaders.ModelLoader;
import java.io.File;
import java.nio.file.Files;

// Read-only (2026-10-20): x / y (height) / z extent, faces and texturing of raw model files.
public class ModelBounds {
    public static void main(String[] a) throws Exception {
        for (String p : a) {
            ModelDefinition m = new ModelLoader().load(0, Files.readAllBytes(new File(p).toPath()));
            int[] b = {Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE};
            for (int i = 0; i < m.vertexCount; i++) {
                b[0] = Math.min(b[0], m.vertexX[i]); b[1] = Math.max(b[1], m.vertexX[i]);
                b[2] = Math.min(b[2], m.vertexY[i]); b[3] = Math.max(b[3], m.vertexY[i]);
                b[4] = Math.min(b[4], m.vertexZ[i]); b[5] = Math.max(b[5], m.vertexZ[i]);
            }
            int tex = 0;
            if (m.faceTextures != null) for (short t : m.faceTextures) if (t != -1) tex++;
            System.out.printf("%s w %d h %d d %d faces %d verts %d textured %d%n", new File(p).getName(), b[1] - b[0], b[3] - b[2], b[5] - b[4], m.faceCount, m.vertexCount, tex);
        }
    }
}
