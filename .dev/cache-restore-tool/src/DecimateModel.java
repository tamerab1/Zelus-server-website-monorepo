import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.loaders.ModelLoader;
import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

// Vertex-clustering decimation: snaps vertices to a grid, welds any that land in the same cell,
// drops faces that degenerate (2+ corners now the same vertex) as a result. Deliberately crude
// (no quadric-error-metric edge selection) but preserves overall silhouette reasonably at
// moderate-to-aggressive reduction ratios, unlike random face deletion (which punches holes).
// Auto-searches for the coarsest-fitting grid cell size that gets under a target face count.
public class DecimateModel {
    public static ModelDefinition decimate(ModelDefinition src, int cellSize) {
        ModelDefinition out = new ModelDefinition();
        Map<Long, Integer> cellToNewIndex = new HashMap<>();
        int[] remap = new int[src.vertexCount];
        int newVertCount = 0;
        int[] nvx = new int[src.vertexCount];
        int[] nvy = new int[src.vertexCount];
        int[] nvz = new int[src.vertexCount];

        for (int i = 0; i < src.vertexCount; i++) {
            long cx = Math.floorDiv(src.vertexX[i], cellSize);
            long cy = Math.floorDiv(src.vertexY[i], cellSize);
            long cz = Math.floorDiv(src.vertexZ[i], cellSize);
            long key = (cx & 0x1FFFFF) | ((cy & 0x1FFFFF) << 21) | ((cz & 0x1FFFFF) << 42);
            Integer existing = cellToNewIndex.get(key);
            if (existing == null) {
                int idx = newVertCount++;
                // representative position = the cell's snapped center (keeps geometry bounded,
                // avoids drift toward whichever vertex happened to land first)
                nvx[idx] = (int) (cx * cellSize + cellSize / 2);
                nvy[idx] = (int) (cy * cellSize + cellSize / 2);
                nvz[idx] = (int) (cz * cellSize + cellSize / 2);
                cellToNewIndex.put(key, idx);
                remap[i] = idx;
            } else {
                remap[i] = existing;
            }
        }

        int[] fi1 = new int[src.faceCount], fi2 = new int[src.faceCount], fi3 = new int[src.faceCount];
        short[] fcolor = new short[src.faceCount];
        byte[] frtype = src.faceRenderTypes != null ? new byte[src.faceCount] : null;
        byte[] fprio = src.faceRenderPriorities != null ? new byte[src.faceCount] : null;
        int newFaceCount = 0;
        for (int i = 0; i < src.faceCount; i++) {
            int a = remap[src.faceIndices1[i]];
            int b = remap[src.faceIndices2[i]];
            int c = remap[src.faceIndices3[i]];
            if (a == b || b == c || a == c) continue; // degenerate after welding -- drop
            fi1[newFaceCount] = a;
            fi2[newFaceCount] = b;
            fi3[newFaceCount] = c;
            fcolor[newFaceCount] = src.faceColors[i];
            if (frtype != null) frtype[newFaceCount] = src.faceRenderTypes[i];
            if (fprio != null) fprio[newFaceCount] = src.faceRenderPriorities[i];
            newFaceCount++;
        }

        out.vertexCount = newVertCount;
        out.vertexX = java.util.Arrays.copyOf(nvx, newVertCount);
        out.vertexY = java.util.Arrays.copyOf(nvy, newVertCount);
        out.vertexZ = java.util.Arrays.copyOf(nvz, newVertCount);
        out.faceCount = newFaceCount;
        out.faceIndices1 = java.util.Arrays.copyOf(fi1, newFaceCount);
        out.faceIndices2 = java.util.Arrays.copyOf(fi2, newFaceCount);
        out.faceIndices3 = java.util.Arrays.copyOf(fi3, newFaceCount);
        out.faceColors = java.util.Arrays.copyOf(fcolor, newFaceCount);
        if (frtype != null) out.faceRenderTypes = java.util.Arrays.copyOf(frtype, newFaceCount);
        if (fprio != null) out.faceRenderPriorities = java.util.Arrays.copyOf(fprio, newFaceCount);
        out.numTextureFaces = 0;
        out.priority = src.priority;
        return out;
    }

    public static void main(String[] args) throws Exception {
        File inFile = new File(args[0]);
        int targetMaxFaces = Integer.parseInt(args[1]);
        File outFile = args.length > 2 && !args[2].equals("-") ? new File(args[2]) : null;

        byte[] raw = Files.readAllBytes(inFile.toPath());
        ModelLoader loader = new ModelLoader();
        ModelDefinition original = loader.load(0, raw.clone());
        System.out.println("original: verts=" + original.vertexCount + " faces=" + original.faceCount);

        // binary-search cell size (in model units, typically 1-200 range) for the coarsest that
        // still exceeds target, then step down: find smallest cell size whose result is <= target.
        int lo = 1, hi = 512;
        ModelDefinition best = null;
        int bestCell = -1;
        for (int iter = 0; iter < 20; iter++) {
            int mid = (lo + hi) / 2;
            if (mid == 0) mid = 1;
            ModelDefinition candidate = decimate(original, mid);
            System.out.println("cellSize=" + mid + " -> verts=" + candidate.vertexCount + " faces=" + candidate.faceCount);
            if (candidate.faceCount <= targetMaxFaces) {
                best = candidate;
                bestCell = mid;
                hi = mid - 1; // try finer (smaller cell = less aggressive) to preserve more detail
            } else {
                lo = mid + 1;
            }
            if (lo > hi) break;
        }
        if (best == null) {
            throw new IllegalStateException("could not find a cell size getting under " + targetMaxFaces + " faces within search range");
        }
        System.out.println("SELECTED cellSize=" + bestCell + ": verts=" + best.vertexCount + " faces=" + best.faceCount);

        byte[] type3Bytes = ModelType3Encoder.encode(best);
        System.out.println("encoded Type3: " + type3Bytes.length + " bytes");

        ModelDefinition reloaded = loader.load(0, type3Bytes.clone());
        int mismatches = 0;
        if (reloaded.vertexCount != best.vertexCount || reloaded.faceCount != best.faceCount) mismatches = 999999;
        System.out.println("round-trip: reloaded verts=" + reloaded.vertexCount + " faces=" + reloaded.faceCount
                + (mismatches == 0 ? " OK" : " MISMATCH"));
        if (mismatches != 0) throw new IllegalStateException("round-trip failed -- refusing to write");

        if (outFile != null) {
            Files.write(outFile.toPath(), type3Bytes);
            System.out.println("wrote " + type3Bytes.length + " bytes to " + outFile);
        }
    }
}
