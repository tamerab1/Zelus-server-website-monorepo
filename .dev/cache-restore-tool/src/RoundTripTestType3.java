import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;

import java.io.File;
import java.util.Arrays;

// Verifies ModelType3Encoder before it ever touches real cache data: for each given model id,
// decode(original) -> encode -> decode(reencoded) -> compare every field for exact equality.
// Unlike Type2, Type3 stores faceTextures/textureCoords/texIndices/faceColors directly (no lossy
// packing), so this comparison is a full exact match, not a partial one.
// Usage: <cachePath> <modelId1,modelId2,...>
public class RoundTripTestType3 {
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
                byte trailerA = raw[raw.length - 1];
                byte trailerB = raw[raw.length - 2];
                String origFormat = formatName(trailerA, trailerB);

                ModelDefinition orig = new ModelLoader().load(modelId, raw.clone());

                byte[] reencoded;
                try {
                    reencoded = ModelType3Encoder.encode(orig);
                } catch (Throwable t) {
                    System.out.println(modelId + " (" + origFormat + "): ENCODE EXCEPTION " + t);
                    t.printStackTrace();
                    continue;
                }

                ModelDefinition roundTrip;
                try {
                    roundTrip = new ModelLoader().load(modelId, reencoded.clone());
                } catch (Throwable t) {
                    System.out.println(modelId + " (" + origFormat + "): RE-DECODE EXCEPTION " + t);
                    t.printStackTrace();
                    continue;
                }

                java.util.List<String> diffs = compare(orig, roundTrip);
                if (diffs.isEmpty()) {
                    System.out.println(modelId + " (" + origFormat + " -> Type3, " + raw.length + "->"
                            + reencoded.length + " bytes): PASS");
                } else {
                    System.out.println(modelId + " (" + origFormat + "): FAIL - " + diffs.size() + " mismatches");
                    for (String d : diffs) System.out.println("    " + d);
                }
            }
        }
    }

    static String formatName(byte last, byte secondLast) {
        if (last == -3 && secondLast == -1) return "Type3";
        if (last == -2 && secondLast == -1) return "Type2";
        if (last == -1 && secondLast == -1) return "Type1";
        return "OldFormat";
    }

    static java.util.List<String> compare(ModelDefinition a, ModelDefinition b) {
        java.util.List<String> diffs = new java.util.ArrayList<>();
        eq(diffs, "vertexCount", a.vertexCount, b.vertexCount);
        eq(diffs, "faceCount", a.faceCount, b.faceCount);
        eq(diffs, "numTextureFaces", a.numTextureFaces, b.numTextureFaces);
        eq(diffs, "priority", a.priority, b.priority);
        eqArr(diffs, "vertexX", a.vertexX, b.vertexX);
        eqArr(diffs, "vertexY", a.vertexY, b.vertexY);
        eqArr(diffs, "vertexZ", a.vertexZ, b.vertexZ);
        eqArr(diffs, "faceIndices1", a.faceIndices1, b.faceIndices1);
        eqArr(diffs, "faceIndices2", a.faceIndices2, b.faceIndices2);
        eqArr(diffs, "faceIndices3", a.faceIndices3, b.faceIndices3);
        eqArr(diffs, "faceRenderPriorities", a.faceRenderPriorities, b.faceRenderPriorities);
        eqArr(diffs, "faceTransparencies", a.faceTransparencies, b.faceTransparencies);
        eqArr(diffs, "faceRenderTypes", a.faceRenderTypes, b.faceRenderTypes);
        if (!ModelType3Encoder.DISABLE_TEXTURING) {
            eqArr(diffs, "faceTextures", a.faceTextures, b.faceTextures);
            eqArr(diffs, "textureCoords", a.textureCoords, b.textureCoords);
            eqArr(diffs, "texIndices1", a.texIndices1, b.texIndices1);
            eqArr(diffs, "texIndices2", a.texIndices2, b.texIndices2);
            eqArr(diffs, "texIndices3", a.texIndices3, b.texIndices3);
        }
        eqArr(diffs, "faceColors", a.faceColors, b.faceColors);
        eqArr(diffs, "packedVertexGroups", a.packedVertexGroups, b.packedVertexGroups);
        eqArr(diffs, "packedTransparencyVertexGroups", a.packedTransparencyVertexGroups, b.packedTransparencyVertexGroups);
        eqDeep(diffs, "animayaGroups", a.animayaGroups, b.animayaGroups);
        eqDeep(diffs, "animayaScales", a.animayaScales, b.animayaScales);
        return diffs;
    }

    static void eq(java.util.List<String> diffs, String name, Object a, Object b) {
        if (!java.util.Objects.equals(a, b)) diffs.add(name + ": " + a + " != " + b);
    }

    static void eqArr(java.util.List<String> diffs, String name, int[] a, int[] b) {
        if (!Arrays.equals(a, b)) diffs.add(name + " differs (int[]) " + preview(a) + " vs " + preview(b));
    }

    static void eqArr(java.util.List<String> diffs, String name, short[] a, short[] b) {
        if (!Arrays.equals(a, b)) diffs.add(name + " differs (short[]) " + preview(a) + " vs " + preview(b));
    }

    static void eqArr(java.util.List<String> diffs, String name, byte[] a, byte[] b) {
        if (!Arrays.equals(a, b)) diffs.add(name + " differs (byte[]) " + preview(a) + " vs " + preview(b));
    }

    static void eqDeep(java.util.List<String> diffs, String name, int[][] a, int[][] b) {
        if (!Arrays.deepEquals(a, b)) diffs.add(name + " differs (int[][])");
    }

    static String preview(int[] a) {
        if (a == null) return "null";
        return Arrays.toString(Arrays.copyOfRange(a, 0, Math.min(10, a.length))) + (a.length > 10 ? "..." : "");
    }

    static String preview(short[] a) {
        if (a == null) return "null";
        return Arrays.toString(Arrays.copyOfRange(a, 0, Math.min(10, a.length))) + (a.length > 10 ? "..." : "");
    }

    static String preview(byte[] a) {
        if (a == null) return "null";
        return Arrays.toString(Arrays.copyOfRange(a, 0, Math.min(10, a.length))) + (a.length > 10 ? "..." : "");
    }
}
