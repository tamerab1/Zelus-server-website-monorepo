import net.runelite.cache.ConfigType;
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

// Removes one file-data slot by id from the ITEM config archive -- for cleaning up a bad
// entry (e.g. one corrupted by an out-of-order/unsorted insert) before redoing it correctly.
// Usage: verify|apply <cachePath> <itemId>
public class RemoveItemById {
    public static void main(String[] args) throws Exception {
        String mode = args[0];
        String cachePath = args[1];
        int itemId = Integer.parseInt(args[2]);

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
            for (int i = 0; i < fileData.length; i++) if (fileData[i].getId() == itemId) { slotIndex = i; break; }
            if (slotIndex == -1) throw new IllegalStateException("item id " + itemId + " not found -- nothing to remove");

            System.out.println("removing slot " + slotIndex + " (id=" + itemId + ") of " + fileData.length + " total slots");

            if (mode.equals("verify")) { System.out.println("VERIFY mode -- not writing."); return; }
            if (!mode.equals("apply")) throw new IllegalArgumentException("mode must be verify or apply");

            fileContents.remove(slotIndex);
            FileData[] newFileData = new FileData[fileData.length - 1];
            System.arraycopy(fileData, 0, newFileData, 0, slotIndex);
            System.arraycopy(fileData, slotIndex + 1, newFileData, slotIndex, fileData.length - slotIndex - 1);
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

            System.out.println("APPLY complete. Removed item " + itemId + ". Archive revision now " + archive.getRevision() + ".");
        }
    }
}
