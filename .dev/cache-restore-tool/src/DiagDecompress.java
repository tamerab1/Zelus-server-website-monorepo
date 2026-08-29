import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;

import java.io.File;

public class DiagDecompress {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Index models = store.getIndex(IndexType.MODELS);
            for (int i = 1; i < args.length; i++) {
                int modelId = Integer.parseInt(args[i]);
                try {
                    Archive archive = models.getArchive(modelId);
                    if (archive == null) {
                        System.out.println(modelId + ": NO ARCHIVE (missing from index)");
                        continue;
                    }
                    byte[] raw;
                    try {
                        raw = storage.loadArchive(archive);
                    } catch (Exception e) {
                        System.out.println(modelId + ": loadArchive FAILED - " + e);
                        continue;
                    }
                    if (raw == null) {
                        System.out.println(modelId + ": loadArchive returned NULL (crc=" + archive.getCrc() + " compression=" + archive.getCompression() + " revision=" + archive.getRevision() + " nameHash=" + archive.getNameHash() + " compressedSize=" + archive.getCompressedSize() + ")");
                        continue;
                    }
                    try {
                        byte[] decompressed = archive.decompress(raw);
                        System.out.println(modelId + ": OK decompressedLen=" + decompressed.length + " rawLen=" + raw.length + " crc=" + archive.getCrc());
                    } catch (Exception e) {
                        System.out.println(modelId + ": decompress FAILED - " + e + " (rawLen=" + raw.length + " crc=" + archive.getCrc() + " compression=" + archive.getCompression() + ")");
                    }
                } catch (Exception e) {
                    System.out.println(modelId + ": OUTER ERROR - " + e);
                }
            }
        }
    }
}
