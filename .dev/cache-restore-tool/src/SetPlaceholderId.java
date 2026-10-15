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

// Points an existing item's own placeholderId (opcode 148) at a freshly-minted dedicated
// placeholder item (see SpliceNewPlaceholder) -- without this the item still carries its
// clone-template's placeholderId, so depositing it into a bank placeholder slot shows the
// TEMPLATE's name/icon instead of its own.
//
// Usage: verify|apply <cachePath> <targetItemId> <newPlaceholderId>
public class SetPlaceholderId {
    public static void main(String[] args) throws Exception {
        String mode = args[0];
        String cachePath = args[1];
        int targetItemId = Integer.parseInt(args[2]);
        int newPlaceholderId = Integer.parseInt(args[3]);

        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Index index = store.getIndex(IndexType.CONFIGS);
            Archive archive = index.getArchive(ConfigType.ITEM.getId());

            byte[] compressed = storage.loadArchive(archive);
            byte[] decompressed = archive.decompress(compressed);
            FileData[] fileData = archive.getFileData();
            List<byte[]> fileContents = SpliceItemOption.splitChunks(decompressed, fileData.length);

            int slotIndex = -1;
            for (int i = 0; i < fileData.length; i++) if (fileData[i].getId() == targetItemId) { slotIndex = i; break; }
            if (slotIndex == -1) throw new IllegalStateException("item id " + targetItemId + " not found");

            byte[] raw = fileContents.get(slotIndex);
            ItemLoader loader = new ItemLoader();
            ItemDefinition before = loader.load(targetItemId, raw);
            System.out.println("BEFORE: name=\"" + before.name + "\" placeholderId=" + before.placeholderId);

            byte[] work = SpliceNewPetItem.replaceOrInsertItemOpcode(raw, 148, SpliceNewPetItem.ushort(newPlaceholderId));
            ItemDefinition after = loader.load(targetItemId, work);
            System.out.println("AFTER:  name=\"" + after.name + "\" placeholderId=" + after.placeholderId);

            if (after.placeholderId != newPlaceholderId) throw new IllegalStateException("placeholderId not applied -- ABORTING");
            if (!java.util.Objects.equals(after.name, before.name)) throw new IllegalStateException("name changed unexpectedly -- ABORTING");
            if (after.inventoryModel != before.inventoryModel) throw new IllegalStateException("inventoryModel changed unexpectedly -- ABORTING");

            if (mode.equals("verify")) { System.out.println("VERIFY mode -- not writing."); return; }
            if (!mode.equals("apply")) throw new IllegalArgumentException("mode must be verify or apply");

            fileContents.set(slotIndex, work);
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

            System.out.println("APPLY complete. Item " + targetItemId + " placeholderId now " + newPlaceholderId
                    + ". Archive revision now " + archive.getRevision() + ".");
        }
    }
}
