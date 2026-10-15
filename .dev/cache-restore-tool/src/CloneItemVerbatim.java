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

// Clones a source item's raw bytes 100% verbatim (name, model, examine, options, bonuses --
// EVERYTHING unchanged) into a new id. Used for minting a low-id ground-display-proxy item that
// must look and be named exactly like the real (high-id) item it stands in for on the network
// wire -- see GroundItem.GROUND_DISPLAY_PROXY_ID and reference_ground_item_15bit_objdel_limit.
//
// Usage: verify|apply <cachePath> <sourceItemId> <newId>
public class CloneItemVerbatim {
    public static void main(String[] args) throws Exception {
        String mode = args[0];
        String cachePath = args[1];
        int sourceId = Integer.parseInt(args[2]);
        int newId = Integer.parseInt(args[3]);

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
                if (fd.getId() == newId) throw new IllegalStateException("id " + newId + " already exists -- refusing to overwrite.");
            }
            int sourceSlot = -1;
            for (int i = 0; i < fileData.length; i++) if (fileData[i].getId() == sourceId) sourceSlot = i;
            if (sourceSlot == -1) throw new IllegalStateException("source id " + sourceId + " not found");

            byte[] raw = fileContents.get(sourceSlot);
            ItemLoader loader = new ItemLoader();
            ItemDefinition before = loader.load(sourceId, raw);
            ItemDefinition after = loader.load(newId, raw);

            System.out.println("source " + sourceId + " -> new " + newId + ": name=\"" + after.name
                    + "\" inventoryModel=" + after.inventoryModel + " maleModel0=" + after.maleModel0
                    + " cost=" + after.cost + " tradeable=" + after.isTradeable);

            if (!before.name.equals(after.name)) throw new IllegalStateException("name mismatch -- ABORTING");
            if (before.inventoryModel != after.inventoryModel) throw new IllegalStateException("inventoryModel mismatch -- ABORTING");

            if (mode.equals("verify")) { System.out.println("VERIFY mode -- not writing."); return; }
            if (!mode.equals("apply")) throw new IllegalArgumentException("mode must be verify or apply");

            int insertAt = fileData.length;
            for (int i = 0; i < fileData.length; i++) if (fileData[i].getId() > newId) { insertAt = i; break; }
            fileContents.add(insertAt, raw);
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

            System.out.println("APPLY complete. New item id " + newId + " added (verbatim clone of " + sourceId + "). Archive revision now " + archive.getRevision() + ".");
        }
    }
}
