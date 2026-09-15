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

// In-place icon-framing correction for an EXISTING item def: overrides zoom2d/xan2d/yan2d/zan2d
// (op4/5/6/95) only, leaving every other field (models, options, wearpos, etc.) untouched. Used to
// fix a bad icon angle without having to delete and recreate the item.
//
// Modes:
//   verify <cachePath> <itemId> <zoom2d> <xan2d> <yan2d> <zan2d>
//   apply  <cachePath> <itemId> <zoom2d> <xan2d> <yan2d> <zan2d>
public class UpdateItemFraming {
    public static void main(String[] args) throws Exception {
        String mode = args[0];
        String cachePath = args[1];
        int itemId = Integer.parseInt(args[2]);
        int zoom2d = Integer.parseInt(args[3]);
        int xan2d = Integer.parseInt(args[4]);
        int yan2d = Integer.parseInt(args[5]);
        int zan2d = Integer.parseInt(args[6]);

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
            if (slot == -1) throw new IllegalStateException("item " + itemId + " not found");

            byte[] raw = fileContents.get(slot);
            ItemLoader loader = new ItemLoader();
            ItemDefinition before = loader.load(itemId, raw);

            byte[] work = raw;
            work = SpliceNewPetItem.replaceOrInsertItemOpcode(work, 4, SpliceNewPetItem.ushort(zoom2d));
            work = SpliceNewPetItem.replaceOrInsertItemOpcode(work, 5, SpliceNewPetItem.ushort(xan2d));
            work = SpliceNewPetItem.replaceOrInsertItemOpcode(work, 6, SpliceNewPetItem.ushort(yan2d));
            work = SpliceNewPetItem.replaceOrInsertItemOpcode(work, 95, SpliceNewPetItem.ushort(zan2d));

            ItemDefinition after = loader.load(itemId, work);
            System.out.println("BEFORE: zoom2d=" + before.zoom2d + " xan2d=" + before.xan2d + " yan2d=" + before.yan2d + " zan2d=" + before.zan2d);
            System.out.println("AFTER:  zoom2d=" + after.zoom2d + " xan2d=" + after.xan2d + " yan2d=" + after.yan2d + " zan2d=" + after.zan2d);

            if (after.zoom2d != zoom2d || after.xan2d != xan2d || after.yan2d != yan2d || after.zan2d != zan2d) {
                throw new IllegalStateException("framing not applied correctly -- ABORTING");
            }
            if (!java.util.Objects.equals(before.name, after.name) || before.inventoryModel != after.inventoryModel
                    || before.maleModel0 != after.maleModel0 || before.femaleModel0 != after.femaleModel0) {
                throw new IllegalStateException("non-framing field changed unexpectedly -- ABORTING");
            }

            if (mode.equals("verify")) {
                System.out.println("VERIFY mode -- not writing.");
                return;
            }

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
