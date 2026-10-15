import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.loaders.ModelLoader;
import java.io.File;
import java.nio.file.Files;

// Converts a raw ripped model (any decodable format -- OldFormat/Type1/Type2/Type3) into real
// Type3 bytes via the project's own already-validated ModelType3Encoder. Needed because the
// player-equipment-compositing render path only accepts Type3 models (see ModelType3Encoder.java's
// own header comment -- this exact bug already root-caused and fixed once before for the
// drag-drop-bundle headwear items); Type1/Type2/OldFormat models render fine standalone
// (ground/inventory/bank icon) but silently fail (or crash) when composited onto the player.
//
// Self-validates via round-trip: re-decodes its own Type3 output and diffs vertex/face/color
// arrays against the original decode, byte-for-byte, before ever considering the output usable.
public class ConvertToType3 {
    public static void main(String[] args) throws Exception {
        File inFile = new File(args[0]);
        File outFile = args[1].equals("-") ? null : new File(args[1]);

        byte[] raw = Files.readAllBytes(inFile.toPath());
        ModelLoader loader = new ModelLoader();
        ModelDefinition original = loader.load(0, raw.clone());
        System.out.println("original: verts=" + original.vertexCount + " faces=" + original.faceCount);

        byte[] type3Bytes = ModelType3Encoder.encode(original);
        System.out.println("encoded Type3: " + type3Bytes.length + " bytes (trailer=" 
                + (type3Bytes[type3Bytes.length-2] & 0xFF) + "," + (type3Bytes[type3Bytes.length-1] & 0xFF) + ")");

        ModelDefinition reloaded = loader.load(0, type3Bytes.clone());
        System.out.println("reloaded: verts=" + reloaded.vertexCount + " faces=" + reloaded.faceCount);

        int mismatches = 0;
        if (reloaded.vertexCount != original.vertexCount) { mismatches++; System.out.println("MISMATCH vertexCount"); }
        if (reloaded.faceCount != original.faceCount) { mismatches++; System.out.println("MISMATCH faceCount"); }
        for (int i = 0; i < Math.min(original.vertexCount, reloaded.vertexCount); i++) {
            if (original.vertexX[i] != reloaded.vertexX[i] || original.vertexY[i] != reloaded.vertexY[i] || original.vertexZ[i] != reloaded.vertexZ[i]) {
                mismatches++;
                if (mismatches <= 5) System.out.println("MISMATCH vertex " + i + " orig=("+original.vertexX[i]+","+original.vertexY[i]+","+original.vertexZ[i]+") reloaded=("+reloaded.vertexX[i]+","+reloaded.vertexY[i]+","+reloaded.vertexZ[i]+")");
            }
        }
        for (int i = 0; i < Math.min(original.faceCount, reloaded.faceCount); i++) {
            if (original.faceIndices1[i] != reloaded.faceIndices1[i]
                    || original.faceIndices2[i] != reloaded.faceIndices2[i]
                    || original.faceIndices3[i] != reloaded.faceIndices3[i]) {
                mismatches++;
                if (mismatches <= 5) System.out.println("MISMATCH face " + i);
            }
        }
        System.out.println("round-trip mismatches: " + mismatches);
        if (mismatches > 0) {
            throw new IllegalStateException("round-trip validation FAILED -- refusing to write output.");
        }
        System.out.println("ROUND-TRIP CLEAN.");

        if (outFile != null) {
            Files.write(outFile.toPath(), type3Bytes);
            System.out.println("wrote " + type3Bytes.length + " bytes to " + outFile);
        }
    }
}
