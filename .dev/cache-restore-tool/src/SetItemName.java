import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.ItemDefinition;
import net.runelite.cache.definitions.loaders.ItemLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Container;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.IndexData;

import java.io.File;
import java.util.List;

// In-place op2 (name) rename of an existing item, leaving every other field untouched.
// Usage: verify|apply <cachePath> <itemId> <newName>
public class SetItemName {
    public static void main(String[] args) throws Exception {
        String mode = args[0];
        String cachePath = args[1];
        int itemId = Integer.parseInt(args[2]);
        String newName = args[3];

        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Index index = store.getIndex(IndexType.CONFIGS);
            Archive archive = index.getArchive(ConfigType.ITEM.getId());

            byte[] compressed = storage.loadArchive(archive);
            byte[] decompressed = archive.decompress(compressed);
            var fileData = archive.getFileData();
            List<byte[]> fileContents = SpliceItemOption.splitChunks(decompressed, fileData.length);

            int slot = -1;
            for (int i = 0; i < fileData.length; i++) if (fileData[i].getId() == itemId) slot = i;
            if (slot == -1) throw new IllegalStateException("item " + itemId + " not found");

            byte[] raw = fileContents.get(slot);
            ItemLoader loader = new ItemLoader();
            ItemDefinition before = loader.load(itemId, raw);

            byte[] nameBytes = newName.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            byte[] namePayload = new byte[nameBytes.length + 1];
            System.arraycopy(nameBytes, 0, namePayload, 0, nameBytes.length);
            byte[] work = SpliceNewPetItem.replaceOrInsertItemOpcode(raw, 2, namePayload);

            ItemDefinition after = loader.load(itemId, work);
            System.out.println("BEFORE: name=\"" + before.name + "\"");
            System.out.println("AFTER:  name=\"" + after.name + "\"");

            if (!newName.equals(after.name)) throw new IllegalStateException("name not applied -- ABORTING");
            if (before.inventoryModel != after.inventoryModel) throw new IllegalStateException("inventoryModel changed unexpectedly -- ABORTING");
            if (before.maleModel0 != after.maleModel0) throw new IllegalStateException("maleModel0 changed unexpectedly -- ABORTING");

            if (mode.equals("verify")) {
                System.out.println("VERIFY mode -- not writing.");
                return;
            }
            if (!mode.equals("apply")) throw new IllegalArgumentException("mode must be verify or apply");

            fileContents.set(slot, work);
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

            System.out.println("APPLY complete for item " + itemId + ".");
        }
    }
}
