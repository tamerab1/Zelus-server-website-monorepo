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
import net.runelite.cache.io.InputStream;

import java.io.File;
import java.util.List;

// Unlike SpliceRecolorCopy (which deliberately forbids changing inventoryModel -- it's built for
// same-shape recolors of an EXISTING pet item), this creates a new item def that reuses a source
// item's boilerplate opcodes (tradeable=false, weight, right-click options, etc.) but points its
// inventoryModel at an ARBITRARY model id -- for pets whose source boss has never had a pet item
// before, so there's no existing same-shape item to recolor from.
//
// Modes:
//   verify <cachePath> <sourceItemId> <newId> <newName> <newInventoryModelId> <f1,r1,f2,r2,...|->
//   apply  <cachePath> <sourceItemId> <newId> <newName> <newInventoryModelId> <f1,r1,f2,r2,...|->
public class SpliceNewPetItem {
    public static void main(String[] args) throws Exception {
        String mode = args[0];
        String cachePath = args[1];
        int sourceId = Integer.parseInt(args[2]);
        int newId = Integer.parseInt(args[3]);
        String newName = args[4];
        int newModelId = Integer.parseInt(args[5]);
        String colorPairsArg = args.length > 6 && !args[6].equals("-") ? args[6] : null;
        // Optional icon-framing overrides -- needed when newModelId is a raw in-world model
        // (e.g. a boss's own body mesh) rather than a purpose-built small icon mesh, since the
        // source item's zoom2d/resize/rotation was tuned for ITS OWN model's proportions and
        // very likely frames a differently-scaled model badly (too small/cropped/edge-on).
        // Format: zoom2d,xan2d,yan2d,resizeX,resizeY,resizeZ -- pass "-" or omit to keep source's.
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

            // Last matching slot wins (this archive has ~828 known duplicate file-id entries,
            // earlier slots often broken/stub defs) -- same convention as DumpModelColors /
            // SpliceRecolorCopy.
            int sourceSlot = -1;
            for (int i = 0; i < fileData.length; i++) {
                if (fileData[i].getId() == sourceId) sourceSlot = i;
            }
            if (sourceSlot == -1) {
                throw new IllegalStateException("source id " + sourceId + " not found");
            }

            byte[] work = fileContents.get(sourceSlot);

            byte[] nameBytes = newName.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            byte[] namePayload = new byte[nameBytes.length + 1];
            System.arraycopy(nameBytes, 0, namePayload, 0, nameBytes.length);
            namePayload[namePayload.length - 1] = 0;
            work = replaceOrInsertItemOpcode(work, 2, namePayload);

            // opcode 1 = inventoryModel, unsigned short.
            work = replaceOrInsertItemOpcode(work, 1, new byte[]{
                (byte) ((newModelId >> 8) & 0xFF), (byte) (newModelId & 0xFF)
            });

            if (zoom2d != null) {
                work = replaceOrInsertItemOpcode(work, 4, ushort(zoom2d));   // zoom2d
                work = replaceOrInsertItemOpcode(work, 5, ushort(xan2d));    // xan2d
                work = replaceOrInsertItemOpcode(work, 6, ushort(yan2d));    // yan2d
                work = replaceOrInsertItemOpcode(work, 110, ushort(resizeX));
                work = replaceOrInsertItemOpcode(work, 111, ushort(resizeY));
                work = replaceOrInsertItemOpcode(work, 112, ushort(resizeZ));
            }

            if (colorPairsArg != null) {
                String[] pairsStr = colorPairsArg.split(",");
                short[] find = new short[pairsStr.length / 2];
                short[] replace = new short[pairsStr.length / 2];
                for (int i = 0; i < find.length; i++) {
                    find[i] = (short) Integer.parseInt(pairsStr[i * 2].trim());
                    replace[i] = (short) Integer.parseInt(pairsStr[i * 2 + 1].trim());
                }
                java.io.ByteArrayOutputStream colorBlock = new java.io.ByteArrayOutputStream();
                colorBlock.write(find.length);
                for (int i = 0; i < find.length; i++) {
                    colorBlock.write((find[i] >> 8) & 0xFF);
                    colorBlock.write(find[i] & 0xFF);
                    colorBlock.write((replace[i] >> 8) & 0xFF);
                    colorBlock.write(replace[i] & 0xFF);
                }
                work = replaceOrInsertItemOpcode(work, 40, colorBlock.toByteArray());
            }

            byte[] newRaw = work;

            ItemLoader loader = new ItemLoader();
            ItemDefinition before = loader.load(sourceId, fileContents.get(sourceSlot));
            ItemDefinition after = loader.load(newId, newRaw);
            System.out.println("BEFORE (source " + sourceId + "): name=\"" + before.name
                    + "\" inventoryModel=" + before.inventoryModel);
            System.out.println("AFTER  (new " + newId + "):    name=\"" + after.name
                    + "\" inventoryModel=" + after.inventoryModel
                    + " colorFind=" + arr(after.colorFind) + " colorReplace=" + arr(after.colorReplace));

            if (!newName.equals(after.name)) {
                throw new IllegalStateException("name not applied correctly -- ABORTING");
            }
            if (after.inventoryModel != newModelId) {
                throw new IllegalStateException("inventoryModel not applied correctly -- ABORTING");
            }
            if (colorPairsArg != null && (after.colorFind == null || after.colorFind.length == 0)) {
                throw new IllegalStateException("colorFind not applied correctly -- ABORTING");
            }

            if (mode.equals("verify")) {
                System.out.println("VERIFY mode -- not writing. Re-run with 'apply' to persist.");
                return;
            }

            fileContents.add(newRaw);
            FileData[] newFileData = new FileData[fileData.length + 1];
            System.arraycopy(fileData, 0, newFileData, 0, fileData.length);
            FileData newEntry = new FileData();
            newEntry.setId(newId);
            newEntry.setNameHash(-1);
            newFileData[fileData.length] = newEntry;
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

    static String arr(short[] a) {
        return a == null ? "null" : java.util.Arrays.toString(a);
    }

    static byte[] ushort(int value) {
        return new byte[]{(byte) ((value >> 8) & 0xFF), (byte) (value & 0xFF)};
    }

    // Same replace-or-insert-before-terminator logic as SplicePetify.replaceOrInsert, but walks
    // the opcode stream with SpliceItemOption's ITEM-format opcode-width table instead of
    // InsertNPCActions' NPC-format one -- items and NPCs have different opcode payload shapes for
    // the same opcode number, so reusing the NPC walker here would silently misparse the item
    // bytes and corrupt the splice.
    static byte[] replaceOrInsertItemOpcode(byte[] b, int targetOpcode, byte[] newPayload) {
        int[] span = findItemOpcodeSpan(b, targetOpcode);
        if (span != null) {
            int payloadStart = span[0] + 1;
            byte[] out = new byte[b.length - (span[1] - payloadStart) + newPayload.length];
            System.arraycopy(b, 0, out, 0, payloadStart);
            System.arraycopy(newPayload, 0, out, payloadStart, newPayload.length);
            System.arraycopy(b, span[1], out, payloadStart + newPayload.length, b.length - span[1]);
            return out;
        } else {
            int insertAt = findItemTerminatorOffset(b);
            byte[] out = new byte[b.length + 1 + newPayload.length];
            System.arraycopy(b, 0, out, 0, insertAt);
            out[insertAt] = (byte) targetOpcode;
            System.arraycopy(newPayload, 0, out, insertAt + 1, newPayload.length);
            System.arraycopy(b, insertAt, out, insertAt + 1 + newPayload.length, b.length - insertAt);
            return out;
        }
    }

    static int[] findItemOpcodeSpan(byte[] b, int targetOpcode) {
        InputStream is = new InputStream(b);
        while (true) {
            int start = is.getOffset();
            int opcode = is.readUnsignedByte();
            if (opcode == 0) return null;
            SpliceItemOption.skipOpcodePayload(opcode, is);
            if (opcode == targetOpcode) return new int[]{start, is.getOffset()};
        }
    }

    static int findItemTerminatorOffset(byte[] b) {
        InputStream is = new InputStream(b);
        while (true) {
            int start = is.getOffset();
            int opcode = is.readUnsignedByte();
            if (opcode == 0) return start;
            SpliceItemOption.skipOpcodePayload(opcode, is);
        }
    }
}
