import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Container;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;
import net.runelite.cache.index.IndexData;

import java.io.File;
import java.util.zip.CRC32;

// Copies each given model archive to a brand-new (currently unused) model id, byte-for-byte
// (same compression, same decompressed content) -- used to relocate the drag-drop-bundle
// headwear models out of the 62714-65163 range (where jj.ap()/the client's model lookup
// mysteriously always fails, even with correct Type3 data byte-verified present and CRC-matched
// on disk) into a lower, proven-working range (matching custom pet Nox's working model 59773).
//
// Usage: <verify|apply> <cachePath> <oldId1:newId1,oldId2:newId2,...>
public class RelocateModels {
    public static void main(String[] args) throws Exception {
        String mode = args[0];
        String cachePath = args[1];
        String[] pairs = args[2].split(",");

        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Index models = store.getIndex(IndexType.MODELS);

            boolean anyFailed = false;
            int[] oldIds = new int[pairs.length];
            int[] newIds = new int[pairs.length];
            byte[][] decompressedData = new byte[pairs.length][];
            int[] compressions = new int[pairs.length];

            for (int i = 0; i < pairs.length; i++) {
                String[] parts = pairs[i].split(":");
                int oldId = Integer.parseInt(parts[0].trim());
                int newId = Integer.parseInt(parts[1].trim());
                oldIds[i] = oldId;
                newIds[i] = newId;

                Archive oldArchive = models.getArchive(oldId);
                if (oldArchive == null) {
                    System.out.println(oldId + " -> " + newId + ": OLD ARCHIVE NULL - skipping");
                    anyFailed = true;
                    continue;
                }
                if (models.getArchive(newId) != null) {
                    System.out.println(oldId + " -> " + newId + ": NEW id " + newId
                            + " already exists - ABORTING, refusing to overwrite.");
                    anyFailed = true;
                    continue;
                }

                byte[] raw = storage.loadArchive(oldArchive);
                byte[] decompressed = oldArchive.decompress(raw);
                decompressedData[i] = decompressed;
                compressions[i] = oldArchive.getCompression();
                System.out.println(oldId + " -> " + newId + ": read OK (" + decompressed.length
                        + " decompressed bytes, compression=" + compressions[i] + ")");
            }

            if (mode.equals("verify")) {
                System.out.println(anyFailed ? "VERIFY: at least one pair failed." : "VERIFY: all pairs OK.");
                return;
            }

            if (!mode.equals("apply")) {
                throw new IllegalArgumentException("mode must be 'verify' or 'apply', got: " + mode);
            }

            if (anyFailed) {
                System.out.println("ABORTING apply: at least one pair failed verification above. Nothing written.");
                return;
            }

            for (int i = 0; i < pairs.length; i++) {
                int newId = newIds[i];
                Archive newArchive = models.addArchive(newId);
                newArchive.setNameHash(-1);
                newArchive.setCompression(compressions[i]);
                newArchive.setRevision(1);

                Container container = new Container(compressions[i], -1);
                container.compress(decompressedData[i], null);

                // Container.compress() never sets container.crc -- compute it ourselves the same
                // way decompress() does (CRC32 over the full container bytes; revision is -1 here
                // so there's no trailing revision suffix to exclude). Same pattern as
                // ApplyType1ToType2.java / ApplyType3.java.
                CRC32 crc32 = new CRC32();
                crc32.update(container.data);
                int realCrc = (int) crc32.getValue();

                storage.store(models.getId(), newId, container.data);
                newArchive.setCrc(realCrc);
                newArchive.setCompressedSize(container.data.length);
                newArchive.setDecompressedSize(decompressedData[i].length);

                FileData fd = new FileData();
                fd.setId(newId);
                newArchive.setFileData(new FileData[]{fd});

                System.out.println(oldIds[i] + " -> " + newId + ": WRITTEN");
            }

            IndexData indexData = models.toIndexData();
            byte[] rawIndex = indexData.writeIndexData();
            Container idxContainer = new Container(models.getCompression(), -1);
            idxContainer.compress(rawIndex, null);
            storage.store(255, models.getId(), idxContainer.data);
            CRC32 idxCrc32 = new CRC32();
            idxCrc32.update(idxContainer.data);
            models.setCrc((int) idxCrc32.getValue());

            System.out.println("APPLY complete. " + pairs.length + " model(s) relocated.");
        }
    }
}
