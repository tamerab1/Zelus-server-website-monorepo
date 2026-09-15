import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.loaders.ModelLoader;

import java.io.File;
import java.nio.file.Files;

// Read-only: decodes a raw model file straight off disk (not from the cache) with the real
// ModelLoader, and prints vertex/face counts plus the vertex bounding box -- used to validate
// ripped source assets before packing them into the cache, and to compute icon-framing zoom.
// Usage: DumpRawModelInfo <file1.dat> [file2.dat ...]
public class DumpRawModelInfo {
    public static void main(String[] args) throws Exception {
        ModelLoader loader = new ModelLoader();
        for (String path : args) {
            byte[] raw = Files.readAllBytes(new File(path).toPath());
            try {
                ModelDefinition md = loader.load(0, raw.clone());
                int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
                int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
                int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
                for (int i = 0; i < md.vertexCount; i++) {
                    minX = Math.min(minX, md.vertexX[i]);
                    maxX = Math.max(maxX, md.vertexX[i]);
                    minY = Math.min(minY, md.vertexY[i]);
                    maxY = Math.max(maxY, md.vertexY[i]);
                    minZ = Math.min(minZ, md.vertexZ[i]);
                    maxZ = Math.max(maxZ, md.vertexZ[i]);
                }
                System.out.println(path + " -> OK verts=" + md.vertexCount + " faces=" + md.faceCount
                        + " x=[" + minX + "," + maxX + "] y=[" + minY + "," + maxY + "] z=[" + minZ + "," + maxZ + "]"
                        + " bytes=" + raw.length);
            } catch (Throwable t) {
                System.out.println(path + " -> EXCEPTION " + t);
            }
        }
    }
}
