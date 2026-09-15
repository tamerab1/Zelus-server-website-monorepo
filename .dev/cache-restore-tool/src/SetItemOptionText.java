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

// In-place fix for a single interfaceOptions string slot (opcode 35-39, slots 0-4), leaving every
// other field untouched. Used to correct a mislabeled option text cloned verbatim from a template
// item whose special mechanic (e.g. "Uncharge") doesn't apply to the new item.
//
// Modes:
//   verify <cachePath> <itemId> <optionSlot 0-4> <newText>
//   apply  <cachePath> <itemId> <optionSlot 0-4> <newText>
public class SetItemOptionText {
    public static void main(String[] args) throws Exception {
        String mode = args[0];
        String cachePath = args[1];
        int itemId = Integer.parseInt(args[2]);
        int optionSlot = Integer.parseInt(args[3]);
        String newText = args[4];
        int opcode = 35 + optionSlot;

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

            byte[] nameBytes = newText.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            byte[] payload = new byte[nameBytes.length + 1];
            System.arraycopy(nameBytes, 0, payload, 0, nameBytes.length);
            payload[payload.length - 1] = 0;

            byte[] work = SpliceNewPetItem.replaceOrInsertItemOpcode(raw, opcode, payload);
            ItemDefinition after = loader.load(itemId, work);

            System.out.println("BEFORE: interfaceOptions=" + java.util.Arrays.toString(before.interfaceOptions));
            System.out.println("AFTER:  interfaceOptions=" + java.util.Arrays.toString(after.interfaceOptions));

            if (!newText.equals(after.interfaceOptions[optionSlot])) {
                throw new IllegalStateException("option text not applied correctly -- ABORTING");
            }
            for (int i = 0; i < 5; i++) {
                if (i == optionSlot) continue;
                if (!java.util.Objects.equals(before.interfaceOptions[i], after.interfaceOptions[i])) {
                    throw new IllegalStateException("a different option slot changed unexpectedly -- ABORTING");
                }
            }
            if (!java.util.Objects.equals(before.name, after.name) || before.inventoryModel != after.inventoryModel
                    || before.maleModel0 != after.maleModel0 || before.femaleModel0 != after.femaleModel0
                    || before.zoom2d != after.zoom2d || before.xan2d != after.xan2d || before.yan2d != after.yan2d) {
                throw new IllegalStateException("non-option field changed unexpectedly -- ABORTING");
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
