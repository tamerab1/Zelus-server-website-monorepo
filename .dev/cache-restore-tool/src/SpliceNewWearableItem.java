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

// Like SpliceNewPetItem, but for real wearable/wieldable gear: clones a template item's full cache
// record (so its "Wield"/"Wear" option, wearpos, stackable/GE flags all come along unchanged --
// already correct for the target equip slot), then overrides name, inventoryModel (ground+icon
// model, op1), maleModel0/femaleModel0 (worn model, op23/op25 -- both preserve the template's
// original maleOffset/femaleOffset byte, since that's a rig-position tweak independent of which
// mesh is plugged in), and optionally icon-framing (zoom2d/xan2d/yan2d/resize, op4-6/110-112) and
// examine text (op3). Everything else in the template's bytes (interfaceOptions, wearPos flags,
// cost, tradeable, stackable, params) is inherited byte-for-byte.
//
// Modes:
//   verify <cachePath> <sourceItemId> <newId> <newName> <invModelId> <wearModelId> <framing|-> <examine|->
//   apply  <cachePath> <sourceItemId> <newId> <newName> <invModelId> <wearModelId> <framing|-> <examine|->
// framing format: zoom2d,xan2d,yan2d,resizeX,resizeY,resizeZ  (pass "-" to keep template's values)
public class SpliceNewWearableItem {
    public static void main(String[] args) throws Exception {
        String mode = args[0];
        String cachePath = args[1];
        int sourceId = Integer.parseInt(args[2]);
        int newId = Integer.parseInt(args[3]);
        String newName = args[4];
        int invModelId = Integer.parseInt(args[5]);
        int wearModelId = Integer.parseInt(args[6]);

        Integer zoom2d = null, xan2d = null, yan2d = null, resizeX = null, resizeY = null, resizeZ = null;
        if (args.length > 7 && !args[7].equals("-")) {
            String[] p = args[7].split(",");
            zoom2d = Integer.parseInt(p[0].trim());
            xan2d = Integer.parseInt(p[1].trim());
            yan2d = Integer.parseInt(p[2].trim());
            resizeX = Integer.parseInt(p[3].trim());
            resizeY = Integer.parseInt(p[4].trim());
            resizeZ = Integer.parseInt(p[5].trim());
        }
        String newExamine = (args.length > 8 && !args[8].equals("-")) ? args[8] : null;

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
                if (fd.getId() == newId) {
                    throw new IllegalStateException("id " + newId + " already exists -- refusing to overwrite.");
                }
            }

            int sourceSlot = -1;
            for (int i = 0; i < fileData.length; i++) {
                if (fileData[i].getId() == sourceId) sourceSlot = i;
            }
            if (sourceSlot == -1) {
                throw new IllegalStateException("source id " + sourceId + " not found");
            }

            byte[] templateRaw = fileContents.get(sourceSlot);
            ItemLoader loader = new ItemLoader();
            ItemDefinition before = loader.load(sourceId, templateRaw);

            byte[] work = templateRaw;

            byte[] nameBytes = newName.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            byte[] namePayload = new byte[nameBytes.length + 1];
            System.arraycopy(nameBytes, 0, namePayload, 0, nameBytes.length);
            work = SpliceNewPetItem.replaceOrInsertItemOpcode(work, 2, namePayload);

            if (newExamine != null) {
                byte[] exBytes = newExamine.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
                byte[] exPayload = new byte[exBytes.length + 1];
                System.arraycopy(exBytes, 0, exPayload, 0, exBytes.length);
                work = SpliceNewPetItem.replaceOrInsertItemOpcode(work, 3, exPayload);
            }

            // opcode 1 = inventoryModel, unsigned short.
            work = SpliceNewPetItem.replaceOrInsertItemOpcode(work, 1, SpliceNewPetItem.ushort(invModelId));

            // wearModelId == -1 means "no wear model, same as the template" (e.g. rings/bonds) --
            // leave opcodes 23/25 untouched rather than writing a literal 0xFFFF, since ItemLoader
            // only reports maleModel0/femaleModel0 as -1 when the opcode is ABSENT entirely; an
            // explicit 0xFFFF payload decodes back as 65535, not -1 (confirmed 2026-09-15).
            if (wearModelId != -1) {
                // opcode 23 = maleModel0 (u16) + maleOffset (u8, preserved from template).
                int maleOffset = before.maleOffset & 0xFF;
                work = SpliceNewPetItem.replaceOrInsertItemOpcode(work, 23, new byte[]{
                        (byte) ((wearModelId >> 8) & 0xFF), (byte) (wearModelId & 0xFF), (byte) maleOffset
                });

                // opcode 25 = femaleModel0 (u16) + femaleOffset (u8, preserved from template).
                int femaleOffset = before.femaleOffset & 0xFF;
                work = SpliceNewPetItem.replaceOrInsertItemOpcode(work, 25, new byte[]{
                        (byte) ((wearModelId >> 8) & 0xFF), (byte) (wearModelId & 0xFF), (byte) femaleOffset
                });
            }

            if (zoom2d != null) {
                work = SpliceNewPetItem.replaceOrInsertItemOpcode(work, 4, SpliceNewPetItem.ushort(zoom2d));
                work = SpliceNewPetItem.replaceOrInsertItemOpcode(work, 5, SpliceNewPetItem.ushort(xan2d));
                work = SpliceNewPetItem.replaceOrInsertItemOpcode(work, 6, SpliceNewPetItem.ushort(yan2d));
                work = SpliceNewPetItem.replaceOrInsertItemOpcode(work, 110, SpliceNewPetItem.ushort(resizeX));
                work = SpliceNewPetItem.replaceOrInsertItemOpcode(work, 111, SpliceNewPetItem.ushort(resizeY));
                work = SpliceNewPetItem.replaceOrInsertItemOpcode(work, 112, SpliceNewPetItem.ushort(resizeZ));
            }

            byte[] newRaw = work;
            ItemDefinition after = loader.load(newId, newRaw);

            System.out.println("BEFORE (source " + sourceId + "): name=\"" + before.name + "\" inventoryModel="
                    + before.inventoryModel + " maleModel0=" + before.maleModel0 + " femaleModel0=" + before.femaleModel0
                    + " interfaceOptions=" + java.util.Arrays.toString(before.interfaceOptions));
            System.out.println("AFTER  (new " + newId + "):    name=\"" + after.name + "\" inventoryModel="
                    + after.inventoryModel + " maleModel0=" + after.maleModel0 + " femaleModel0=" + after.femaleModel0
                    + " interfaceOptions=" + java.util.Arrays.toString(after.interfaceOptions)
                    + " zoom2d=" + after.zoom2d + " xan2d=" + after.xan2d + " yan2d=" + after.yan2d);

            // Safety gate: only name/examine/inventoryModel/maleModel0/femaleModel0/framing may differ.
            if (!newName.equals(after.name)) throw new IllegalStateException("name not applied -- ABORTING");
            if (after.inventoryModel != invModelId) throw new IllegalStateException("inventoryModel not applied -- ABORTING");
            if (after.maleModel0 != wearModelId) throw new IllegalStateException("maleModel0 not applied -- ABORTING");
            if (after.femaleModel0 != wearModelId) throw new IllegalStateException("femaleModel0 not applied -- ABORTING");
            if (newExamine != null && !newExamine.equals(after.examine)) throw new IllegalStateException("examine not applied -- ABORTING");
            if (!java.util.Arrays.equals(before.interfaceOptions, after.interfaceOptions))
                throw new IllegalStateException("interfaceOptions changed unexpectedly -- ABORTING");
            if (before.wearPos1 != after.wearPos1 || before.wearPos2 != after.wearPos2 || before.wearPos3 != after.wearPos3)
                throw new IllegalStateException("wearPos changed unexpectedly -- ABORTING");
            if (before.isTradeable != after.isTradeable) throw new IllegalStateException("isTradeable changed unexpectedly -- ABORTING");
            if (before.stackable != after.stackable) throw new IllegalStateException("stackable changed unexpectedly -- ABORTING");
            if (before.cost != after.cost) throw new IllegalStateException("cost changed unexpectedly -- ABORTING");

            if (mode.equals("verify")) {
                System.out.println("VERIFY mode -- not writing. Re-run with 'apply' to persist.");
                return;
            }
            if (!mode.equals("apply")) {
                throw new IllegalArgumentException("mode must be 'verify' or 'apply', got: " + mode);
            }

            // IndexData.writeIndexData() delta-encodes ids as id[i]-id[i-1] and requires the
            // array to stay in ascending sorted order -- blindly appending at the end silently
            // corrupts the encoding whenever newId is LOWER than the current max id (confirmed
            // 2026-09-15: 27 low-id items appended after a higher max got silently relocated to
            // garbage ids on the next read). Insert at the correct sorted position instead.
            int insertAt = fileData.length;
            for (int i = 0; i < fileData.length; i++) {
                if (fileData[i].getId() > newId) {
                    insertAt = i;
                    break;
                }
            }
            fileContents.add(insertAt, newRaw);
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

            System.out.println("APPLY complete. New item id " + newId + " added. Archive revision now "
                    + archive.getRevision() + ".");
        }
    }
}
