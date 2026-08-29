import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Container;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.IndexType;
import net.runelite.cache.index.IndexData;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

// Restores model archives to their PRISTINE original bytes from the drag-drop-for-other-server
// bundle's cache-overlay/model/<id>.bin source files, bypassing any decode/re-encode of whatever
// is currently in the cache (which was found to differ from the source by 104 bytes in model
// 64983 -- some prior, unknown edit, NOT the original import). This is a faithful re-import: the
// source .bin is exactly the decompressed content, wrapped with the archive's existing compression
// and a correctly-computed CRC (see ApplyType1ToType2 for why container.crc can't be trusted).
//
// Usage: <cachePath> <overlayModelDir> <modelId1,modelId2,...>
public class RestorePristineModels {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        String overlayDir = args[1];
        int[] modelIds = java.util.Arrays.stream(args[2].split(","))
                .mapToInt(s -> Integer.parseInt(s.trim())).toArray();

        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Index modelsIndex = store.getIndex(IndexType.MODELS);

            for (int modelId : modelIds) {
                Archive archive = modelsIndex.getArchive(modelId);
                if (archive == null) {
                    System.out.println(modelId + ": ARCHIVE NULL - skipping");
                    continue;
                }
                File srcFile = new File(overlayDir, modelId + ".bin");
                if (!srcFile.exists()) {
                    System.out.println(modelId + ": source .bin not found at " + srcFile + " - skipping");
                    continue;
                }
                byte[] pristine = Files.readAllBytes(srcFile.toPath());

                Container container = new Container(archive.getCompression(), -1);
                container.compress(pristine, null);

                java.util.zip.CRC32 crc32 = new java.util.zip.CRC32();
                crc32.update(container.data);
                int realCrc = (int) crc32.getValue();

                storage.store(modelsIndex.getId(), modelId, container.data);
                archive.setCrc(realCrc);
                archive.setRevision(archive.getRevision() + 1);
                archive.setCompressedSize(container.data.length);
                archive.setDecompressedSize(pristine.length);

                System.out.println(modelId + ": restored from pristine source (" + pristine.length
                        + " bytes, revision " + archive.getRevision() + ")");
            }

            IndexData indexData = modelsIndex.toIndexData();
            byte[] rawIndex = indexData.writeIndexData();
            Container idxContainer = new Container(modelsIndex.getCompression(), -1);
            idxContainer.compress(rawIndex, null);
            storage.store(255, modelsIndex.getId(), idxContainer.data);
            java.util.zip.CRC32 idxCrc32 = new java.util.zip.CRC32();
            idxCrc32.update(idxContainer.data);
            modelsIndex.setCrc((int) idxCrc32.getValue());

            System.out.println("Done.");
        }
    }
}
