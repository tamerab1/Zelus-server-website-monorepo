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

// Sets an existing item's inventoryModel (opcode 1) in place, independent of the worn model.
// Used to fall an oversized custom model's icon back to its template's real inventory model when
// the custom model exceeds net.runelite.cache.item.Model's hardcoded 6500-vertex/6000-face
// buffers (confirmed real engine limit, not a bug in this tooling -- reproduced the exact
// ArrayIndexOutOfBoundsException via RenderItemIcon.java against the genuine item-icon renderer).
//
// Usage: verify|apply <cachePath> <itemId> <newInventoryModelId>
public class SetInventoryModel {
    public static void main(String[] args) throws Exception {
        String mode = args[0];
        String cachePath = args[1];
        int itemId = Integer.parseInt(args[2]);
        int newModelId = Integer.parseInt(args[3]);

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
            if (slotIndex == -1) throw new IllegalStateException("item id " + itemId + " not found");

            byte[] raw = fileContents.get(slotIndex);
            ItemLoader loader = new ItemLoader();
            ItemDefinition before = loader.load(itemId, raw);
            System.out.println("BEFORE: name=\"" + before.name + "\" inventoryModel=" + before.inventoryModel);

            byte[] work = SpliceNewPetItem.replaceOrInsertItemOpcode(raw, 1, SpliceNewPetItem.ushort(newModelId));
            ItemDefinition after = loader.load(itemId, work);
            System.out.println("AFTER:  name=\"" + after.name + "\" inventoryModel=" + after.inventoryModel);

            if (!java.util.Objects.equals(after.name, before.name)) throw new IllegalStateException("name changed -- ABORTING");
            if (after.inventoryModel != newModelId) throw new IllegalStateException("inventoryModel not applied -- ABORTING");
            if (after.maleModel0 != before.maleModel0) throw new IllegalStateException("maleModel0 changed unexpectedly -- ABORTING");

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

            System.out.println("APPLY complete. Item " + itemId + " inventoryModel now " + newModelId + ". Archive revision now " + archive.getRevision() + ".");
        }
    }
}
