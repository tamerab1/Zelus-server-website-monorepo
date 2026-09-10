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
import net.runelite.cache.io.OutputStream;

import java.io.File;
import java.util.List;

// Fixes the Tiny Tormented pet (33031) and its 3 recolors (60175/60176/60177): their
// inventoryOptions slot 4 (opcode 39) is "Destroy" instead of "Drop", so
// Pet.java's registered "drop" handler (which spawns the pet NPC) never fires -- confirmed
// via LookupItemNames.java against every other working pet item, which all have "Drop" in
// that exact slot.
//
// Reuses SpliceItemOption's already-verified opcode walker (findOptionSpan/skipOpcodePayload,
// which mirrors RuneLite's real ItemLoader.decodeValues() exactly) to locate the opcode-39
// span, then REPLACES its payload (rather than removing it, which is all the existing tool
// does) with "Drop\0". Re-decodes with the real ItemLoader afterward and verifies every
// other field is untouched before ever writing anything.
//
// Usage: <cachePath> [apply]
public class FixPetDropOption {
    static final int[] ITEM_IDS = {33031, 60175, 60176, 60177};
    static final int OPTION_SLOT = 4; // opcode 39, the 5th inventory option
    static final String OLD_TEXT = "Destroy";
    static final String NEW_TEXT = "Drop";

    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        boolean apply = args.length > 1 && args[1].equals("apply");

        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Index index = store.getIndex(IndexType.CONFIGS);
            Archive archive = index.getArchive(ConfigType.ITEM.getId());

            byte[] decompressed = archive.decompress(storage.loadArchive(archive));
            FileData[] fileData = archive.getFileData();
            List<byte[]> fileContents = SpliceItemOption.splitChunks(decompressed, fileData.length);

            ItemLoader loader = new ItemLoader();
            int changed = 0;

            for (int itemId : ITEM_IDS) {
                for (int i = 0; i < fileData.length; i++) {
                    if (fileData[i].getId() != itemId) continue;

                    byte[] before = fileContents.get(i);
                    ItemDefinition beforeDef = loader.load(itemId, before);
                    String current = beforeDef.interfaceOptions == null || beforeDef.interfaceOptions.length <= OPTION_SLOT
                            ? null : beforeDef.interfaceOptions[OPTION_SLOT];
                    if (!OLD_TEXT.equals(current)) {
                        System.out.println("id=" + itemId + " slot=" + i + ": option " + OPTION_SLOT + " is \""
                                + current + "\", not \"" + OLD_TEXT + "\" -- skipping (already fixed or unexpected state).");
                        continue;
                    }

                    int[] span = SpliceItemOption.findOptionSpan(before, OPTION_SLOT);
                    if (span == null) {
                        throw new IllegalStateException("id=" + itemId + ": decoded option present but opcode span "
                                + "not found -- ABORTING.");
                    }

                    OutputStream payload = new OutputStream(NEW_TEXT.length() + 1);
                    payload.writeString(NEW_TEXT);
                    byte[] newPayload = payload.flip();

                    byte[] after = new byte[before.length - (span[1] - span[0]) + 1 + newPayload.length];
                    int opcodeStart = span[0];
                    System.arraycopy(before, 0, after, 0, opcodeStart);
                    after[opcodeStart] = before[opcodeStart]; // keep the same opcode byte (39)
                    System.arraycopy(newPayload, 0, after, opcodeStart + 1, newPayload.length);
                    System.arraycopy(before, span[1], after, opcodeStart + 1 + newPayload.length,
                            before.length - span[1]);

                    ItemDefinition afterDef = loader.load(itemId, after);
                    if (!NEW_TEXT.equals(afterDef.interfaceOptions[OPTION_SLOT])) {
                        throw new IllegalStateException("id=" + itemId + ": option " + OPTION_SLOT + " is \""
                                + afterDef.interfaceOptions[OPTION_SLOT] + "\" after edit, expected \"" + NEW_TEXT
                                + "\" -- ABORTING.");
                    }
                    if (!SpliceItemOption.fieldsMatchExceptOption(beforeDef, afterDef, OPTION_SLOT)) {
                        throw new IllegalStateException("id=" + itemId + ": edit changed something other than "
                                + "option " + OPTION_SLOT + " -- ABORTING.");
                    }

                    System.out.println("id=" + itemId + " ('" + beforeDef.name + "') slot=" + i + ": option "
                            + OPTION_SLOT + " \"" + OLD_TEXT + "\" -> \"" + NEW_TEXT + "\", all other fields verified unchanged.");

                    fileContents.set(i, after);
                    changed++;
                }
            }

            System.out.println("Prepared " + changed + " item edit(s).");
            if (!apply) {
                System.out.println("DRY RUN -- not writing. Re-run with 'apply' to persist.");
                return;
            }
            if (changed == 0) {
                System.out.println("Nothing to write.");
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

            System.out.println("APPLY complete. Archive 10 (items) revision now " + archive.getRevision() + ".");
        }
    }
}
