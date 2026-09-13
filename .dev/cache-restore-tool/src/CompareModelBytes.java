import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import java.io.File;
import java.util.Arrays;

// Read-only. Compares a model id's raw decompressed bytes between source and Zelus -- if
// byte-identical, this is the SAME shared vanilla asset already present, no import/remap needed.
public class CompareModelBytes {
    public static void main(String[] args) throws Exception {
        String srcPath = args[0], dstPath = args[1];
        try (Store src = new Store(new File(srcPath)); Store dst = new Store(new File(dstPath))) {
            src.load();
            dst.load();
            Index srcModels = src.getIndex(IndexType.MODELS);
            Index dstModels = dst.getIndex(IndexType.MODELS);
            Storage srcStorage = src.getStorage();
            Storage dstStorage = dst.getStorage();
            for (int i = 2; i < args.length; i++) {
                int id = Integer.parseInt(args[i]);
                Archive srcA = srcModels.getArchive(id);
                Archive dstA = dstModels.getArchive(id);
                byte[] srcBytes = srcA.decompress(srcStorage.loadArchive(srcA));
                byte[] dstBytes = dstA.decompress(dstStorage.loadArchive(dstA));
                boolean match = Arrays.equals(srcBytes, dstBytes);
                System.out.println("model " + id + ": " + (match ? "IDENTICAL" : "DIFFERENT") +
                        " (src=" + srcBytes.length + " bytes, dst=" + dstBytes.length + " bytes)");
            }
        }
    }
}
