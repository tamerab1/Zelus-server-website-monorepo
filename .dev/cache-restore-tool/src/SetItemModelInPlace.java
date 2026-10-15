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

// Sets an EXISTING item's inventoryModel (op1) and male/female worn models (op23/op25) in place --
// same id, no new FileData entry. Used to re-point an already-created custom item at a different
// source model without re-doing its placeholder/ground-proxy/item_info.json wiring.
// Usage: <cachePath> <itemId> <invModelId> <wearModelId>
public class SetItemModelInPlace {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        int itemId = Integer.parseInt(args[1]);
        int invModelId = Integer.parseInt(args[2]);
        int wearModelId = Integer.parseInt(args[3]);

        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Index index = store.getIndex(IndexType.CONFIGS);
            Archive archive = index.getArchive(ConfigType.ITEM.getId());

            byte[] compressed = storage.loadArchive(archive);
            byte[] decompressed = archive.decompress(compressed);
            FileData[] fileData = archive.getFileData();
            List<byte[]> fileContents = SpliceItemOption.splitChunks(decompressed, fileData.length);

            int slot = -1;
            for (int i = 0; i < fileData.length; i++) if (fileData[i].getId() == itemId) slot = i;
            if (slot == -1) throw new IllegalStateException("item id " + itemId + " not found");

            byte[] raw = fileContents.get(slot);
            ItemLoader loader = new ItemLoader();
            ItemDefinition before = loader.load(itemId, raw);
            System.out.println("BEFORE: name=\"" + before.name + "\" inventoryModel=" + before.inventoryModel
                    + " maleModel0=" + before.maleModel0 + " femaleModel0=" + before.femaleModel0);

            byte[] work = SpliceNewPetItem.replaceOrInsertItemOpcode(raw, 1, SpliceNewPetItem.ushort(invModelId));
            int maleOffset = before.maleOffset & 0xFF;
            work = SpliceNewPetItem.replaceOrInsertItemOpcode(work, 23, new byte[]{
                    (byte) ((wearModelId >> 8) & 0xFF), (byte) (wearModelId & 0xFF), (byte) maleOffset});
            int femaleOffset = before.femaleOffset & 0xFF;
            work = SpliceNewPetItem.replaceOrInsertItemOpcode(work, 25, new byte[]{
                    (byte) ((wearModelId >> 8) & 0xFF), (byte) (wearModelId & 0xFF), (byte) femaleOffset});

            ItemDefinition after = loader.load(itemId, work);
            System.out.println("AFTER:  name=\"" + after.name + "\" inventoryModel=" + after.inventoryModel
                    + " maleModel0=" + after.maleModel0 + " femaleModel0=" + after.femaleModel0);

            if (after.inventoryModel != invModelId || after.maleModel0 != wearModelId || after.femaleModel0 != wearModelId)
                throw new IllegalStateException("model not applied correctly -- ABORTING");
            if (!java.util.Objects.equals(before.name, after.name))
                throw new IllegalStateException("name changed unexpectedly -- ABORTING");

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

            System.out.println("APPLY complete for item " + itemId + ". Archive revision now " + archive.getRevision() + ".");
        }
    }
}
