import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.ItemDefinition;
import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.loaders.ItemLoader;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Container;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;
import net.runelite.cache.index.IndexData;

import java.io.File;
import java.util.List;
import java.util.zip.CRC32;

// Isolated single-item test: relocates Aetherial Scythe's two models (60300 drop, 60301 wear) --
// currently in a "gap" in the middle of the MODELS index's sorted sequence -- to the TRUE tail end
// (65518/65519, genuinely past every other model id below the 16-bit ceiling), and repoints item
// 60256's op1/23/25 accordingly. Everything else (item 60256's other fields, all other 11 items)
// is left untouched. This isolates whether "gap position" vs "true append" affects client
// rendering, without touching anything else.
public class RelocateOneItemTest {
    public static void main(String[] args) throws Exception {
        String mode = args[0];
        String cachePath = args[1];
        int newDropId = 65518;
        int newWearId = 65519;
        int oldDropId = 60300, oldWearId = 60301, itemId = 60256;

        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Index models = store.getIndex(IndexType.MODELS);
            ModelLoader modelLoader = new ModelLoader();

            if (models.getArchive(newDropId) != null || models.getArchive(newWearId) != null) {
                throw new IllegalStateException("target ids already occupied");
            }

            Archive dropArchive = models.getArchive(oldDropId);
            Archive wearArchive = models.getArchive(oldWearId);
            byte[] dropRaw = dropArchive.decompress(storage.loadArchive(dropArchive));
            byte[] wearRaw = wearArchive.decompress(storage.loadArchive(wearArchive));

            ModelDefinition dropBefore = modelLoader.load(oldDropId, dropRaw.clone());
            ModelDefinition dropAfter = modelLoader.load(newDropId, dropRaw.clone());
            if (dropBefore.vertexCount != dropAfter.vertexCount) throw new IllegalStateException("drop model round-trip mismatch");
            ModelDefinition wearBefore = modelLoader.load(oldWearId, wearRaw.clone());
            ModelDefinition wearAfter = modelLoader.load(newWearId, wearRaw.clone());
            if (wearBefore.vertexCount != wearAfter.vertexCount) throw new IllegalStateException("wear model round-trip mismatch");
            System.out.println("Models verified: drop verts=" + dropBefore.vertexCount + ", wear verts=" + wearBefore.vertexCount);

            Index configIndex = store.getIndex(IndexType.CONFIGS);
            Archive itemArchive = configIndex.getArchive(ConfigType.ITEM.getId());
            byte[] itemDecompressed = itemArchive.decompress(storage.loadArchive(itemArchive));
            FileData[] itemFileData = itemArchive.getFileData();
            List<byte[]> itemContents = SpliceItemOption.splitChunks(itemDecompressed, itemFileData.length);

            int slot = -1;
            for (int i = 0; i < itemFileData.length; i++) if (itemFileData[i].getId() == itemId) slot = i;
            if (slot == -1) throw new IllegalStateException("item " + itemId + " not found");
            byte[] raw = itemContents.get(slot);
            ItemLoader itemLoader = new ItemLoader();
            ItemDefinition before = itemLoader.load(itemId, raw);
            if (before.inventoryModel != oldDropId) throw new IllegalStateException("unexpected inventoryModel");

            byte[] work = raw;
            work = SpliceNewPetItem.replaceOrInsertItemOpcode(work, 1, SpliceNewPetItem.ushort(newDropId));
            int maleOffset = before.maleOffset & 0xFF;
            int femaleOffset = before.femaleOffset & 0xFF;
            work = SpliceNewPetItem.replaceOrInsertItemOpcode(work, 23, new byte[]{
                    (byte) ((newWearId >> 8) & 0xFF), (byte) (newWearId & 0xFF), (byte) maleOffset});
            work = SpliceNewPetItem.replaceOrInsertItemOpcode(work, 25, new byte[]{
                    (byte) ((newWearId >> 8) & 0xFF), (byte) (newWearId & 0xFF), (byte) femaleOffset});

            ItemDefinition after = itemLoader.load(itemId, work);
            if (after.inventoryModel != newDropId || after.maleModel0 != newWearId || after.femaleModel0 != newWearId) {
                throw new IllegalStateException("item relocation verify failed");
            }
            if (!java.util.Objects.equals(before.name, after.name) || !java.util.Arrays.equals(before.interfaceOptions, after.interfaceOptions)) {
                throw new IllegalStateException("non-model field changed unexpectedly");
            }
            System.out.println("Item verified: " + before.name + " now points at drop=" + newDropId + " wear=" + newWearId);

            if (mode.equals("verify")) {
                System.out.println("VERIFY mode -- not writing.");
                return;
            }
            if (!mode.equals("apply")) throw new IllegalArgumentException("mode must be verify or apply");

            // write new model archives
            for (int[] pair : new int[][]{{newDropId, 0}, {newWearId, 1}}) {
                int newId = pair[0];
                byte[] raw2 = pair[1] == 0 ? dropRaw : wearRaw;
                Archive newArchive = models.addArchive(newId);
                newArchive.setNameHash(-1);
                newArchive.setCompression(0);
                newArchive.setRevision(1);
                Container container = new Container(0, -1);
                container.compress(raw2, null);
                CRC32 crc32 = new CRC32();
                crc32.update(container.data);
                storage.store(models.getId(), newId, container.data);
                newArchive.setCrc((int) crc32.getValue());
                newArchive.setCompressedSize(container.data.length);
                newArchive.setDecompressedSize(raw2.length);
                FileData fd = new FileData();
                fd.setId(newId);
                newArchive.setFileData(new FileData[]{fd});
            }
            IndexData modelsIndexData = models.toIndexData();
            byte[] rawModelsIndex = modelsIndexData.writeIndexData();
            Container modelsIdxContainer = new Container(models.getCompression(), -1);
            modelsIdxContainer.compress(rawModelsIndex, null);
            storage.store(255, models.getId(), modelsIdxContainer.data);
            CRC32 modelsIdxCrc = new CRC32();
            modelsIdxCrc.update(modelsIdxContainer.data);
            models.setCrc((int) modelsIdxCrc.getValue());
            System.out.println("Wrote 2 relocated model archives at " + newDropId + "/" + newWearId);

            itemContents.set(slot, work);
            byte[] newItemDecompressed = SpliceItemOption.joinChunks(itemContents);
            Container itemContainer = new Container(itemArchive.getCompression(), -1);
            itemContainer.compress(newItemDecompressed, null);
            storage.store(configIndex.getId(), itemArchive.getArchiveId(), itemContainer.data);
            itemArchive.setCrc(itemContainer.crc);
            itemArchive.setRevision(itemArchive.getRevision() + 1);
            itemArchive.setCompressedSize(itemContainer.data.length);
            itemArchive.setDecompressedSize(newItemDecompressed.length);
            IndexData configIndexData = configIndex.toIndexData();
            byte[] rawConfigIndex = configIndexData.writeIndexData();
            Container configIdxContainer = new Container(configIndex.getCompression(), -1);
            configIdxContainer.compress(rawConfigIndex, null);
            storage.store(255, configIndex.getId(), configIdxContainer.data);
            configIndex.setCrc(configIdxContainer.crc);
            System.out.println("APPLY complete. Item 60256 (Aetherial Scythe) now points at drop=" + newDropId + " wear=" + newWearId + ".");
        }
    }
}
