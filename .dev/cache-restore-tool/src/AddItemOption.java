import java.io.File;
import java.util.ArrayList;
import java.util.List;

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

// Reverse of SpliceItemOption (which only removes an interfaceOptions opcode): inserts a NEW
// interfaceOptions opcode (35+slot) with the given string into a target item's raw def bytes, for
// a slot currently unused (null). Mirrors SpliceItemOption's structure/safety checks exactly:
// same chunk-delta split/join (reused directly), same verify/apply split, same "update every
// duplicate-id slot" behavior as RenameDef, same post-splice re-decode assertion that nothing
// else changed, same surgical write (archive 10 + index 255's own reference-table entry only).
//
// Usage:
//   verify <cachePath> <itemId> <optionSlot 0-4> <text>   -- read-only: locate + report, no write
//   apply  <cachePath> <itemId> <optionSlot 0-4> <text>   -- perform the write
public class AddItemOption {
    public static void main(String[] args) throws Exception {
        String mode = args[0];
        String cachePath = args[1];
        int targetItemId = Integer.parseInt(args[2]);
        int optionSlot = Integer.parseInt(args[3]); // 0-4 -> opcode 35-39
        String text = args[4];

        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Index index = store.getIndex(IndexType.CONFIGS);
            Archive archive = index.getArchive(ConfigType.ITEM.getId());

            byte[] compressed = storage.loadArchive(archive);
            byte[] decompressed = archive.decompress(compressed);

            FileData[] fileData = archive.getFileData();
            System.out.println("Archive 10 (items): " + fileData.length + " file slots, compression="
                    + archive.getCompression() + " revision=" + archive.getRevision());

            List<byte[]> fileContents = SpliceItemOption.splitChunks(decompressed, fileData.length);

            List<Integer> matches = new ArrayList<>();
            for (int i = 0; i < fileData.length; i++) {
                if (fileData[i].getId() == targetItemId) {
                    matches.add(i);
                }
            }
            System.out.println("Found " + matches.size() + " file slot(s) with id " + targetItemId + ": " + matches);
            if (matches.isEmpty()) {
                System.out.println("Nothing to do.");
                return;
            }

            ItemLoader loader = new ItemLoader();
            boolean anyChanged = false;
            for (int slotIndex : matches) {
                byte[] raw = fileContents.get(slotIndex);
                ItemDefinition before = loader.load(targetItemId, raw);
                System.out.println("Slot " + slotIndex + " BEFORE: name=\"" + before.name
                        + "\" interfaceOptions=" + java.util.Arrays.toString(before.interfaceOptions));

                if (before.interfaceOptions[optionSlot] != null) {
                    System.out.println("Slot " + slotIndex + ": option slot " + optionSlot
                            + " already set to \"" + before.interfaceOptions[optionSlot] + "\" -- refusing to overwrite, skipping this slot.");
                    continue;
                }

                int terminatorOffset = findTerminatorOffset(raw);
                int opcode = 35 + optionSlot;
                byte[] textBytes = text.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
                byte[] newField = new byte[1 + textBytes.length + 1];
                newField[0] = (byte) opcode;
                System.arraycopy(textBytes, 0, newField, 1, textBytes.length);
                newField[newField.length - 1] = 0;

                byte[] spliced = new byte[raw.length + newField.length];
                System.arraycopy(raw, 0, spliced, 0, terminatorOffset);
                System.arraycopy(newField, 0, spliced, terminatorOffset, newField.length);
                System.arraycopy(raw, terminatorOffset, spliced, terminatorOffset + newField.length,
                        raw.length - terminatorOffset);

                ItemDefinition after = loader.load(targetItemId, spliced);
                System.out.println("Slot " + slotIndex + " AFTER:  name=\"" + after.name
                        + "\" interfaceOptions=" + java.util.Arrays.toString(after.interfaceOptions));

                if (!SpliceItemOption.fieldsMatchExceptOption(before, after, optionSlot)) {
                    throw new IllegalStateException("Slot " + slotIndex
                            + ": splice changed something other than the target option slot -- ABORTING, no write performed.");
                }
                if (!text.equals(after.interfaceOptions[optionSlot])) {
                    throw new IllegalStateException("Slot " + slotIndex
                            + ": option slot did not decode to the expected text after splice -- ABORTING.");
                }

                fileContents.set(slotIndex, spliced);
                anyChanged = true;
            }

            if (!anyChanged) {
                System.out.println("No slots were actually modified. Not writing anything.");
                return;
            }

            if (mode.equals("verify")) {
                System.out.println("VERIFY mode -- not writing. Re-run with 'apply' to persist.");
                return;
            }

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

            System.out.println("APPLY complete. Archive 10 revision now " + archive.getRevision()
                    + ", new compressed size " + container.data.length + " bytes.");
        }
    }

    // Walks the same opcode dispatch as SpliceItemOption.skipOpcodePayload (reused directly) to
    // find the offset of the terminating opcode-0 byte, i.e. the correct insertion point for a
    // brand new opcode record (inserted immediately before the terminator).
    static int findTerminatorOffset(byte[] b) {
        net.runelite.cache.io.InputStream is = new net.runelite.cache.io.InputStream(b);
        while (true) {
            int start = is.getOffset();
            int opcode = is.readUnsignedByte();
            if (opcode == 0) {
                return start;
            }
            SpliceItemOption.skipOpcodePayload(opcode, is);
        }
    }
}
