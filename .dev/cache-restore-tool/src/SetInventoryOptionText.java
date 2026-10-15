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

// Overwrites (or removes) a single inventoryOptions slot's text (opcode 35+slot) on an existing
// item, in place -- for converting a cloned item's default "Drop" (slot 4 / opcode 39) into
// "Destroy" (the real OSRS client shows its native destroy-confirm prompt automatically whenever
// an item's LAST inventory option string is exactly "Destroy" -- this is stock client behavior
// tied to that literal string, not a custom dialogue we build ourselves), or to null out an
// inherited option that doesn't apply to the new item (e.g. a godsword's "Dismantle" when no
// matching shard item exists for it).
//
// Modes:
//   verify <cachePath> <itemId> <optionSlot 0-4> <newText|-remove->
//   apply  <cachePath> <itemId> <optionSlot 0-4> <newText|-remove->
public class SetInventoryOptionText {
    public static void main(String[] args) throws Exception {
        String mode = args[0];
        String cachePath = args[1];
        int targetItemId = Integer.parseInt(args[2]);
        int optionSlot = Integer.parseInt(args[3]);
        String newText = args[4].equals("-remove-") ? null : args[4];

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
            for (int i = 0; i < fileData.length; i++) {
                if (fileData[i].getId() == targetItemId) { slotIndex = i; break; }
            }
            if (slotIndex == -1) throw new IllegalStateException("item id " + targetItemId + " not found");

            byte[] raw = fileContents.get(slotIndex);
            ItemLoader loader = new ItemLoader();
            ItemDefinition before = loader.load(targetItemId, raw);
            System.out.println("BEFORE: name=\"" + before.name + "\" interfaceOptions="
                    + java.util.Arrays.toString(before.interfaceOptions));

            byte[] work;
            if (newText == null) {
                int[] span = SpliceItemOption.findOptionSpan(raw, optionSlot);
                if (span == null) {
                    System.out.println("slot " + optionSlot + " already absent -- nothing to remove.");
                    return;
                }
                work = new byte[raw.length - (span[1] - span[0])];
                System.arraycopy(raw, 0, work, 0, span[0]);
                System.arraycopy(raw, span[1], work, span[0], raw.length - span[1]);
            } else {
                byte[] textBytes = newText.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
                byte[] payload = new byte[textBytes.length + 1];
                System.arraycopy(textBytes, 0, payload, 0, textBytes.length);
                work = SpliceNewPetItem.replaceOrInsertItemOpcode(raw, 35 + optionSlot, payload);
            }

            ItemDefinition after = loader.load(targetItemId, work);
            System.out.println("AFTER:  name=\"" + after.name + "\" interfaceOptions="
                    + java.util.Arrays.toString(after.interfaceOptions));

            if (!java.util.Objects.equals(after.name, before.name)) throw new IllegalStateException("name changed unexpectedly -- ABORTING");
            if (after.inventoryModel != before.inventoryModel) throw new IllegalStateException("inventoryModel changed unexpectedly -- ABORTING");
            if (after.maleModel0 != before.maleModel0) throw new IllegalStateException("maleModel0 changed unexpectedly -- ABORTING");
            for (int i = 0; i < 5; i++) {
                if (i == optionSlot) continue;
                if (!java.util.Objects.equals(after.interfaceOptions[i], before.interfaceOptions[i]))
                    throw new IllegalStateException("slot " + i + " changed unexpectedly -- ABORTING");
            }
            String expected = newText;
            if (!java.util.Objects.equals(after.interfaceOptions[optionSlot], expected))
                throw new IllegalStateException("target slot not applied correctly -- ABORTING");

            if (mode.equals("verify")) {
                System.out.println("VERIFY mode -- not writing.");
                return;
            }
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

            System.out.println("APPLY complete. Item " + targetItemId + " slot " + optionSlot
                    + " now \"" + expected + "\". Archive revision now " + archive.getRevision() + ".");
        }
    }
}
