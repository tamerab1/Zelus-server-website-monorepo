import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Container;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;
import net.runelite.cache.index.IndexData;

import java.io.File;
import java.nio.file.Files;
import java.util.zip.CRC32;

// Inserts a raw ripped model file as a brand-new single-file archive in index 7 (MODELS) at a
// chosen free id. Unlike MergeModels (which decodes+re-encodes to merge several NPC-scale parts
// into one), the source .dat files here already decode cleanly as real classic-format models via
// the project's own ModelLoader (confirmed against several samples first) -- so the raw bytes ARE
// already valid archive content, stored as-is (uncompressed, same convention MergeModels uses).
//
// Usage: verify|apply <cachePath> <newModelId> <sourceFile.dat>
public class InsertRawModel {
    public static void main(String[] args) throws Exception {
        String mode = args[0];
        String cachePath = args[1];
        int newModelId = Integer.parseInt(args[2]);
        File sourceFile = new File(args[3]);

        byte[] raw = Files.readAllBytes(sourceFile.toPath());

        ModelLoader loader = new ModelLoader();
        ModelDefinition before = loader.load(newModelId, raw.clone());
        System.out.println("source " + sourceFile.getName() + ": verts=" + before.vertexCount
                + " faces=" + before.faceCount + " bytes=" + raw.length);

        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Index models = store.getIndex(IndexType.MODELS);

            if (models.getArchive(newModelId) != null) {
                throw new IllegalStateException("model id " + newModelId + " already exists -- refusing to overwrite.");
            }

            // Round-trip check: re-decode the exact bytes we're about to store.
            ModelDefinition reloaded = loader.load(newModelId, raw.clone());
            if (reloaded.vertexCount != before.vertexCount || reloaded.faceCount != before.faceCount) {
                throw new IllegalStateException("round-trip mismatch -- ABORTING, nothing written");
            }

            if (mode.equals("verify")) {
                System.out.println("VERIFY mode -- not writing. Re-run with 'apply' to persist.");
                return;
            }
            if (!mode.equals("apply")) {
                throw new IllegalArgumentException("mode must be 'verify' or 'apply', got: " + mode);
            }

            Archive newArchive = models.addArchive(newModelId);
            newArchive.setNameHash(-1);
            newArchive.setCompression(0); // uncompressed, simplest valid encoding
            newArchive.setRevision(1);

            Container container = new Container(0, -1);
            container.compress(raw, null);

            CRC32 crc32 = new CRC32();
            crc32.update(container.data);
            int realCrc = (int) crc32.getValue();

            storage.store(models.getId(), newModelId, container.data);
            newArchive.setCrc(realCrc);
            newArchive.setCompressedSize(container.data.length);
            newArchive.setDecompressedSize(raw.length);

            // fd.id must be 0 (the file's index WITHIN this single-file archive), NOT the model
            // id -- confirmed 2026-09-15 as the actual root cause of the "new customs invisible"
            // bug: every known-working custom model has fd.id=0 regardless of its own archive id;
            // setting fd.id=newModelId here silently broke the real client's per-file lookup even
            // though RuneLite's offline decoder and the JS5 network layer both tolerated it fine.
            FileData fd = new FileData();
            fd.setId(0);
            newArchive.setFileData(new FileData[]{fd});

            IndexData indexData = models.toIndexData();
            byte[] rawIndex = indexData.writeIndexData();
            Container idxContainer = new Container(models.getCompression(), -1);
            idxContainer.compress(rawIndex, null);
            storage.store(255, models.getId(), idxContainer.data);
            CRC32 idxCrc32 = new CRC32();
            idxCrc32.update(idxContainer.data);
            models.setCrc((int) idxCrc32.getValue());

            System.out.println("APPLY complete. New model id " + newModelId + " written.");
        }
    }
}
