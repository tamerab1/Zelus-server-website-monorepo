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

// Sets (or removes) an existing item's worn-appearance model (opcode 23=maleModel0+maleOffset,
// opcode 25=femaleModel0+femaleOffset) in place, independent of inventoryModel (opcode 1).
// Used to revert items whose worn model was wrongly set to a raw, overly-complex ripped model
// (causing the in-world player-composition renderer to break) back to a known-good vanilla
// template's worn model, while keeping a distinct custom inventoryModel for the icon.
//
// Usage: verify|apply <cachePath> <itemId> <maleModelId|-1> <maleOffset> <femaleModelId|-1> <femaleOffset>
// maleModelId/femaleModelId == -1 removes that opcode entirely (no wear model, e.g. non-wearable items).
public class SetWornModel {
    public static void main(String[] args) throws Exception {
        String mode = args[0];
        String cachePath = args[1];
        int itemId = Integer.parseInt(args[2]);
        int maleModelId = Integer.parseInt(args[3]);
        int maleOffset = Integer.parseInt(args[4]);
        int femaleModelId = Integer.parseInt(args[5]);
        int femaleOffset = Integer.parseInt(args[6]);

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
            System.out.println("BEFORE: name=\"" + before.name + "\" maleModel0=" + before.maleModel0
                    + " femaleModel0=" + before.femaleModel0 + " inventoryModel=" + before.inventoryModel);

            byte[] work = raw;
            if (maleModelId == -1) {
                int[] span = findOpcodeSpan(work, 23);
                if (span != null) {
                    byte[] out = new byte[work.length - (span[1] - span[0])];
                    System.arraycopy(work, 0, out, 0, span[0]);
                    System.arraycopy(work, span[1], out, span[0], work.length - span[1]);
                    work = out;
                }
            } else {
                work = SpliceNewPetItem.replaceOrInsertItemOpcode(work, 23, new byte[]{
                        (byte) ((maleModelId >> 8) & 0xFF), (byte) (maleModelId & 0xFF), (byte) maleOffset});
            }
            if (femaleModelId == -1) {
                int[] span = findOpcodeSpan(work, 25);
                if (span != null) {
                    byte[] out = new byte[work.length - (span[1] - span[0])];
                    System.arraycopy(work, 0, out, 0, span[0]);
                    System.arraycopy(work, span[1], out, span[0], work.length - span[1]);
                    work = out;
                }
            } else {
                work = SpliceNewPetItem.replaceOrInsertItemOpcode(work, 25, new byte[]{
                        (byte) ((femaleModelId >> 8) & 0xFF), (byte) (femaleModelId & 0xFF), (byte) femaleOffset});
            }

            ItemDefinition after = loader.load(itemId, work);
            System.out.println("AFTER:  name=\"" + after.name + "\" maleModel0=" + after.maleModel0
                    + " femaleModel0=" + after.femaleModel0 + " inventoryModel=" + after.inventoryModel);

            if (!java.util.Objects.equals(after.name, before.name)) throw new IllegalStateException("name changed -- ABORTING");
            if (after.inventoryModel != before.inventoryModel) throw new IllegalStateException("inventoryModel changed unexpectedly -- ABORTING");
            if (after.maleModel0 != maleModelId) throw new IllegalStateException("maleModel0 not applied -- ABORTING");
            if (after.femaleModel0 != femaleModelId) throw new IllegalStateException("femaleModel0 not applied -- ABORTING");

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

            System.out.println("APPLY complete. Item " + itemId + " worn model updated. Archive revision now " + archive.getRevision() + ".");
        }
    }

    static int[] findOpcodeSpan(byte[] b, int targetOpcode) {
        net.runelite.cache.io.InputStream is = new net.runelite.cache.io.InputStream(b);
        while (true) {
            int start = is.getOffset();
            int opcode = is.readUnsignedByte();
            if (opcode == 0) return null;
            SpliceItemOption.skipOpcodePayload(opcode, is);
            if (opcode == targetOpcode) return new int[]{start, is.getOffset()};
        }
    }
}
