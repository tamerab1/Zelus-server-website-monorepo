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

// Sets an existing item's inventory-icon camera framing in place: zoom2d(op4), xan2d(op5),
// yan2d(op6), xOffset2d(op7), yOffset2d(op8) [was wrongly 97/98 = notedID/notedTemplate until 2026-09-27]. resize (op110-112) left alone -- these 5 cover
// everything needed to re-center/re-angle/re-zoom an icon for a model whose bounding box doesn't
// match whatever template the item was cloned from.
//
// Usage: verify|apply <cachePath> <itemId> <zoom2d> <xan2d> <yan2d> <xOffset> <yOffset>
public class SetIconFraming {
    public static void main(String[] args) throws Exception {
        String mode = args[0];
        String cachePath = args[1];
        int itemId = Integer.parseInt(args[2]);
        int zoom2d = Integer.parseInt(args[3]);
        int xan2d = Integer.parseInt(args[4]);
        int yan2d = Integer.parseInt(args[5]);
        int xOffset = Integer.parseInt(args[6]);
        int yOffset = Integer.parseInt(args[7]);

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

            byte[] work = fileContents.get(slotIndex);
            ItemLoader loader = new ItemLoader();
            ItemDefinition before = loader.load(itemId, work);

            work = SpliceNewPetItem.replaceOrInsertItemOpcode(work, 4, SpliceNewPetItem.ushort(zoom2d));
            work = SpliceNewPetItem.replaceOrInsertItemOpcode(work, 5, SpliceNewPetItem.ushort(xan2d));
            work = SpliceNewPetItem.replaceOrInsertItemOpcode(work, 6, SpliceNewPetItem.ushort(yan2d));
            work = SpliceNewPetItem.replaceOrInsertItemOpcode(work, 7, SpliceNewPetItem.ushort(xOffset & 0xFFFF));
            work = SpliceNewPetItem.replaceOrInsertItemOpcode(work, 8, SpliceNewPetItem.ushort(yOffset & 0xFFFF));

            ItemDefinition after = loader.load(itemId, work);
            System.out.println(itemId + " \"" + after.name + "\": zoom2d=" + after.zoom2d + " xan2d=" + after.xan2d
                    + " yan2d=" + after.yan2d);

            if (!java.util.Objects.equals(after.name, before.name)) throw new IllegalStateException("name changed -- ABORTING");
            if (after.inventoryModel != before.inventoryModel) throw new IllegalStateException("inventoryModel changed -- ABORTING");
            if (after.maleModel0 != before.maleModel0) throw new IllegalStateException("maleModel0 changed -- ABORTING");

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

            System.out.println("APPLY complete for " + itemId + ".");
        }
    }
}
