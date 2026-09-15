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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;

// Diagnostic test: relocates our 23 new model archives (60300-60322, inserted into the MIDDLE of
// the MODELS index's existing sorted id sequence, since real content already exists up to 70010)
// to a genuinely-past-the-current-max range (70011+), and updates the 12 item defs' op1/23/25 to
// point at the new ids. Tests whether "gap" ids specifically (vs true tail-end append) are the
// actual cause of the reported invisible-model bug -- everything else about the data has already
// been proven correct by other means.
//
// Usage: verify|apply <cachePath>
public class RelocateNewModelsAndItems {
    // old model id -> new model id
    static final int[][] MODEL_MAP = {
        {60300, 70011}, {60301, 70012}, {60302, 70013}, {60303, 70014},
        {60304, 70015}, {60305, 70016}, {60306, 70017}, {60307, 70018},
        {60308, 70019}, {60309, 70020}, {60310, 70021}, {60311, 70022},
        {60312, 70023}, {60313, 70024}, {60314, 70025}, {60315, 70026},
        {60316, 70027}, {60317, 70028}, {60318, 70029}, {60319, 70030},
        {60320, 70031}, {60321, 70032}, {60322, 70033},
    };

    // item id -> [oldInvModel, newInvModel, oldWearModel, newWearModel]
    static final int[][] ITEM_MAP = {
        {60256, 60300, 70011, 60301, 70012},
        {60257, 60302, 70013, 60303, 70014},
        {60258, 60304, 70015, 60305, 70016},
        {60259, 60306, 70017, 60307, 70018},
        {60260, 60308, 70019, 60309, 70020},
        {60261, 60310, 70021, 60311, 70022},
        {60262, 60312, 70023, 60313, 70024},
        {60263, 60314, 70025, 60315, 70026},
        {60264, 60316, 70027, 60317, 70028},
        {60265, 60318, 70029, 60319, 70030},
        {60266, 60320, 70031, 60321, 70032},
        {60267, 60322, 70033, 60322, 70033}, // Striders: single model reused for both roles
    };

    public static void main(String[] args) throws Exception {
        String mode = args[0];
        String cachePath = args[1];

        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();

            // --- Step 1: copy each model archive's raw bytes to the new id ---
            Index models = store.getIndex(IndexType.MODELS);
            ModelLoader modelLoader = new ModelLoader();
            Map<Integer, byte[]> newModelRaw = new LinkedHashMap<>();
            for (int[] pair : MODEL_MAP) {
                int oldId = pair[0], newId = pair[1];
                Archive oldArchive = models.getArchive(oldId);
                if (oldArchive == null) throw new IllegalStateException("old model " + oldId + " missing");
                if (models.getArchive(newId) != null) throw new IllegalStateException("new model id " + newId + " already occupied");
                byte[] raw = oldArchive.decompress(storage.loadArchive(oldArchive));
                ModelDefinition before = modelLoader.load(oldId, raw.clone());
                ModelDefinition after = modelLoader.load(newId, raw.clone());
                if (before.vertexCount != after.vertexCount || before.faceCount != after.faceCount) {
                    throw new IllegalStateException("model " + oldId + " -> " + newId + " round-trip mismatch");
                }
                newModelRaw.put(newId, raw);
                System.out.println("model " + oldId + " -> " + newId + " OK (verts=" + before.vertexCount + " faces=" + before.faceCount + ")");
            }

            // --- Step 2: splice new item defs pointing at the relocated models ---
            Index configIndex = store.getIndex(IndexType.CONFIGS);
            Archive itemArchive = configIndex.getArchive(ConfigType.ITEM.getId());
            byte[] itemDecompressed = itemArchive.decompress(storage.loadArchive(itemArchive));
            FileData[] itemFileData = itemArchive.getFileData();
            List<byte[]> itemContents = SpliceItemOption.splitChunks(itemDecompressed, itemFileData.length);

            ItemLoader itemLoader = new ItemLoader();
            Map<Integer, byte[]> newItemBytes = new LinkedHashMap<>();
            for (int[] row : ITEM_MAP) {
                int itemId = row[0], oldInv = row[1], newInv = row[2], oldWear = row[3], newWear = row[4];
                int slot = -1;
                for (int i = 0; i < itemFileData.length; i++) if (itemFileData[i].getId() == itemId) slot = i;
                if (slot == -1) throw new IllegalStateException("item " + itemId + " not found");
                byte[] raw = itemContents.get(slot);
                ItemDefinition before = itemLoader.load(itemId, raw);
                if (before.inventoryModel != oldInv) throw new IllegalStateException("item " + itemId + " inventoryModel mismatch: expected " + oldInv + " got " + before.inventoryModel);

                byte[] work = raw;
                work = SpliceNewPetItem.replaceOrInsertItemOpcode(work, 1, SpliceNewPetItem.ushort(newInv));
                int maleOffset = before.maleOffset & 0xFF;
                int femaleOffset = before.femaleOffset & 0xFF;
                work = SpliceNewPetItem.replaceOrInsertItemOpcode(work, 23, new byte[]{
                        (byte) ((newWear >> 8) & 0xFF), (byte) (newWear & 0xFF), (byte) maleOffset});
                work = SpliceNewPetItem.replaceOrInsertItemOpcode(work, 25, new byte[]{
                        (byte) ((newWear >> 8) & 0xFF), (byte) (newWear & 0xFF), (byte) femaleOffset});

                ItemDefinition after = itemLoader.load(itemId, work);
                if (after.inventoryModel != newInv || after.maleModel0 != newWear || after.femaleModel0 != newWear) {
                    throw new IllegalStateException("item " + itemId + " relocation verify failed");
                }
                if (!java.util.Objects.equals(before.name, after.name) || !java.util.Arrays.equals(before.interfaceOptions, after.interfaceOptions)) {
                    throw new IllegalStateException("item " + itemId + " non-model field changed unexpectedly");
                }
                newItemBytes.put(slot, work);
                System.out.println("item " + itemId + ": inventoryModel " + oldInv + "->" + newInv
                        + ", wearModel " + oldWear + "->" + newWear + " OK");
            }

            if (mode.equals("verify")) {
                System.out.println("VERIFY mode -- not writing. Re-run with 'apply' to persist.");
                return;
            }
            if (!mode.equals("apply")) {
                throw new IllegalArgumentException("mode must be verify or apply");
            }

            // --- Apply: write new model archives ---
            for (Map.Entry<Integer, byte[]> e : newModelRaw.entrySet()) {
                int newId = e.getKey();
                byte[] raw = e.getValue();
                Archive newArchive = models.addArchive(newId);
                newArchive.setNameHash(-1);
                newArchive.setCompression(0);
                newArchive.setRevision(1);
                Container container = new Container(0, -1);
                container.compress(raw, null);
                CRC32 crc32 = new CRC32();
                crc32.update(container.data);
                storage.store(models.getId(), newId, container.data);
                newArchive.setCrc((int) crc32.getValue());
                newArchive.setCompressedSize(container.data.length);
                newArchive.setDecompressedSize(raw.length);
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
            System.out.println("Wrote " + newModelRaw.size() + " relocated model archives.");

            // --- Apply: overwrite item defs in place with updated model refs ---
            for (Map.Entry<Integer, byte[]> e : newItemBytes.entrySet()) {
                itemContents.set(e.getKey(), e.getValue());
            }
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
            System.out.println("Updated " + newItemBytes.size() + " item defs to point at relocated models.");
            System.out.println("APPLY complete.");
        }
    }
}
