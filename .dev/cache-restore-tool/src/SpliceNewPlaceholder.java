import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.ItemDefinition;
import net.runelite.cache.definitions.loaders.ItemLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Container;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;
import net.runelite.cache.index.IndexData;

import java.io.File;
import java.util.List;

// Fixes the 2026-09-15 "wrong bank placeholder icon" bug: every custom item built by cloning a
// real template (SpliceNewWearableItem/SpliceNewPetItem) inherited the TEMPLATE's own
// placeholderId (op148) unchanged. That id points to a generic placeholder item (blank name,
// inventoryModel=0, placeholderTemplateId=14401) whose OWN placeholderId (op148) back-references
// the TEMPLATE, not the new clone -- so depositing the custom item into a bank placeholder slot
// showed the template's name/icon (e.g. Armadyl godsword for Ancient Cleaver, Imperial bow for
// Draconic/Brimstone Hornbow). This mints a genuinely new, dedicated placeholder item for one
// target item: clones an existing generic placeholder's byte structure verbatim (blank name,
// inventoryModel=0, placeholderTemplateId=14401 preserved) and overrides ONLY op148 to back
// reference the target item's own id.
//
// Usage: verify|apply <cachePath> <placeholderTemplateSourceId> <newPlaceholderId> <targetRealItemId>
public class SpliceNewPlaceholder {
    public static void main(String[] args) throws Exception {
        String mode = args[0];
        String cachePath = args[1];
        int sourceId = Integer.parseInt(args[2]);
        int newId = Integer.parseInt(args[3]);
        int targetRealItemId = Integer.parseInt(args[4]);

        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Index index = store.getIndex(IndexType.CONFIGS);
            Archive archive = index.getArchive(ConfigType.ITEM.getId());

            byte[] compressed = storage.loadArchive(archive);
            byte[] decompressed = archive.decompress(compressed);
            FileData[] fileData = archive.getFileData();
            List<byte[]> fileContents = SpliceItemOption.splitChunks(decompressed, fileData.length);

            for (FileData fd : fileData) {
                if (fd.getId() == newId) {
                    throw new IllegalStateException("id " + newId + " already exists -- refusing to overwrite.");
                }
            }

            int sourceSlot = -1;
            for (int i = 0; i < fileData.length; i++) {
                if (fileData[i].getId() == sourceId) sourceSlot = i;
            }
            if (sourceSlot == -1) throw new IllegalStateException("source id " + sourceId + " not found");

            byte[] templateRaw = fileContents.get(sourceSlot);
            ItemLoader loader = new ItemLoader();
            ItemDefinition before = loader.load(sourceId, templateRaw);

            byte[] work = SpliceNewPetItem.replaceOrInsertItemOpcode(templateRaw, 148,
                    SpliceNewPetItem.ushort(targetRealItemId));

            ItemDefinition after = loader.load(newId, work);

            System.out.println("BEFORE (source " + sourceId + "): placeholderId=" + before.placeholderId
                    + " placeholderTemplateId=" + before.placeholderTemplateId + " inventoryModel=" + before.inventoryModel);
            System.out.println("AFTER  (new " + newId + "):    placeholderId=" + after.placeholderId
                    + " placeholderTemplateId=" + after.placeholderTemplateId + " inventoryModel=" + after.inventoryModel);

            if (after.placeholderId != targetRealItemId) throw new IllegalStateException("placeholderId not applied -- ABORTING");
            if (after.placeholderTemplateId != before.placeholderTemplateId) throw new IllegalStateException("placeholderTemplateId changed unexpectedly -- ABORTING");
            if (after.inventoryModel != before.inventoryModel) throw new IllegalStateException("inventoryModel changed unexpectedly -- ABORTING");

            if (mode.equals("verify")) {
                System.out.println("VERIFY mode -- not writing.");
                return;
            }
            if (!mode.equals("apply")) throw new IllegalArgumentException("mode must be verify or apply");

            // See SpliceNewWearableItem's identical fix: IndexData.writeIndexData() requires
            // ascending sorted ids, so a blind append corrupts the encoding whenever newId is
            // lower than the current max id. Insert at the correct sorted position instead.
            int insertAt = fileData.length;
            for (int i = 0; i < fileData.length; i++) {
                if (fileData[i].getId() > newId) {
                    insertAt = i;
                    break;
                }
            }
            fileContents.add(insertAt, work);
            FileData[] newFileData = new FileData[fileData.length + 1];
            System.arraycopy(fileData, 0, newFileData, 0, insertAt);
            FileData newEntry = new FileData();
            newEntry.setId(newId);
            newEntry.setNameHash(-1);
            newFileData[insertAt] = newEntry;
            System.arraycopy(fileData, insertAt, newFileData, insertAt + 1, fileData.length - insertAt);
            archive.setFileData(newFileData);

            byte[] newDecompressed = SpliceItemOption.joinChunks(fileContents);
            Container container = new Container(archive.getCompression(), -1);
            container.compress(newDecompressed, null);

            storage.store(index.getId(), archive.getArchiveId(), container.data);
            archive.setCrc(container.crc);
            archive.setRevision(archive.getRevision() + 1);
            archive.setCompressedSize(container.data.length);
            archive.setDecompressedSize(newDecompressed.length);

            IndexData indexData = index.toIndexData();
            byte[] rawIndex = indexData.writeIndexData();
            Container idxContainer = new Container(index.getCompression(), -1);
            idxContainer.compress(rawIndex, null);
            storage.store(255, index.getId(), idxContainer.data);
            index.setCrc(idxContainer.crc);

            System.out.println("APPLY complete. New placeholder item id " + newId + " added, backreferences " + targetRealItemId + ".");
        }
    }
}
