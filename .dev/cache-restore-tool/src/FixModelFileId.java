import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Container;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;
import net.runelite.cache.index.IndexData;

import java.io.File;
import java.util.List;

// Fixes the real root cause of the 2026-09-15 "new customs invisible" bug: InsertRawModel.java
// set each new model archive's single FileData entry's id to the MODEL id (e.g. 60300) instead of
// 0 (the correct convention for a single-file archive -- confirmed by comparing against 10+ known
// working custom models, ALL of which have fd.id=0 regardless of their own archive/model id).
// This doesn't touch the model bytes at all, only the index-7 reference table's per-file id field.
//
// Usage: verify|apply <cachePath> <modelId1,modelId2,...>
public class FixModelFileId {
    public static void main(String[] args) throws Exception {
        String mode = args[0];
        String cachePath = args[1];
        List<Integer> modelIds = new java.util.ArrayList<>();
        for (String s : args[2].split(",")) modelIds.add(Integer.parseInt(s.trim()));

        if (!mode.equals("verify") && !mode.equals("apply")) {
            throw new IllegalArgumentException("mode must be 'verify' or 'apply', got: " + mode);
        }

        boolean anyChanged = false;

        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Index models = store.getIndex(IndexType.MODELS);

            for (int modelId : modelIds) {
                Archive archive = models.getArchive(modelId);
                if (archive == null) {
                    System.out.println(modelId + ": ARCHIVE NULL -- skipping");
                    continue;
                }
                FileData[] fds = archive.getFileData();
                if (fds == null || fds.length != 1) {
                    System.out.println(modelId + ": expected exactly 1 FileData, found "
                            + (fds == null ? "null" : fds.length) + " -- skipping");
                    continue;
                }
                int currentId = fds[0].getId();
                if (currentId == 0) {
                    System.out.println(modelId + ": fd.id already 0 -- no change needed");
                    continue;
                }
                System.out.println(modelId + ": fd.id " + currentId + " -> 0"
                        + (mode.equals("verify") ? " [verify only, not writing]" : ""));
                if (mode.equals("apply")) {
                    FileData fd = new FileData();
                    fd.setId(0);
                    fd.setNameHash(fds[0].getNameHash());
                    archive.setFileData(new FileData[]{fd});
                    anyChanged = true;
                }
            }

            if (mode.equals("apply") && anyChanged) {
                IndexData indexData = models.toIndexData();
                byte[] rawIndex = indexData.writeIndexData();
                Container idxContainer = new Container(models.getCompression(), -1);
                idxContainer.compress(rawIndex, null);
                storage.store(255, models.getId(), idxContainer.data);
                models.setCrc(idxContainer.crc);
                System.out.println("APPLY complete. idx7 reference table (idx255 entry) updated.");
            } else if (mode.equals("apply")) {
                System.out.println("APPLY mode but nothing needed changing -- nothing written.");
            }
        }
    }
}
