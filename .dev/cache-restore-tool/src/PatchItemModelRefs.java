import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Container;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;
import net.runelite.cache.index.IndexData;
import net.runelite.cache.io.InputStream;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;

// Rewrites specific model-id fields (opcodes 1/23/25/90/91: inventoryModel, maleModel0,
// femaleModel0, maleHeadModel, femaleHeadModel) inside an EXISTING item definition's raw bytes,
// in place, leaving every other byte identical. Used to relocate the drag-drop-bundle headwear
// items' model references after their models were copied to new ids (see RelocateModels.java).
//
// idMapCsv format: oldModelId1:newModelId1,oldModelId2:newModelId2,...  (applied to whichever of
// opcodes 1/23/25/90/91 currently hold that old id, for every item id given)
//
// Usage: <verify|apply> <cachePath> <itemId1,itemId2,...> <idMapCsv>
public class PatchItemModelRefs {
    public static void main(String[] args) throws Exception {
        String mode = args[0];
        String cachePath = args[1];
        int[] itemIds = java.util.Arrays.stream(args[2].split(","))
                .mapToInt(s -> Integer.parseInt(s.trim())).toArray();
        Map<Integer, Integer> idMap = new HashMap<>();
        for (String pair : args[3].split(",")) {
            String[] parts = pair.split(":");
            idMap.put(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()));
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

            boolean anyFailed = false;
            Map<Integer, byte[]> patchedByItemId = new HashMap<>();

            for (int itemId : itemIds) {
                int slot = -1;
                for (int i = 0; i < fileData.length; i++) {
                    if (fileData[i].getId() == itemId) slot = i;
                }
                if (slot == -1) {
                    System.out.println(itemId + ": NOT FOUND - skipping");
                    anyFailed = true;
                    continue;
                }
                byte[] raw = fileContents.get(slot);
                byte[] patched = raw.clone();
                int replacements = patchModelRefs(patched, idMap, itemId);
                System.out.println(itemId + ": " + replacements + " model-id field(s) replaced ("
                        + raw.length + " bytes, unchanged length)");
                patchedByItemId.put(itemId, patched);
            }

            if (mode.equals("verify")) {
                System.out.println(anyFailed ? "VERIFY: at least one item failed." : "VERIFY: all items OK.");
                return;
            }

            if (!mode.equals("apply")) {
                throw new IllegalArgumentException("mode must be 'verify' or 'apply', got: " + mode);
            }

            if (anyFailed) {
                System.out.println("ABORTING apply: at least one item failed. Nothing written.");
                return;
            }

            for (int itemId : itemIds) {
                int slot = -1;
                for (int i = 0; i < fileData.length; i++) {
                    if (fileData[i].getId() == itemId) slot = i;
                }
                fileContents.set(slot, patchedByItemId.get(itemId));
            }

            byte[] newDecompressed = SpliceItemOption.joinChunks(fileContents);
            Container container = new Container(archive.getCompression(), -1);
            container.compress(newDecompressed, null);

            CRC32 crc32 = new CRC32();
            crc32.update(container.data);
            int realCrc = (int) crc32.getValue();

            storage.store(index.getId(), archive.getArchiveId(), container.data);
            archive.setCrc(realCrc);
            archive.setRevision(archive.getRevision() + 1);
            archive.setCompressedSize(container.data.length);
            archive.setDecompressedSize(newDecompressed.length);

            IndexData indexData = index.toIndexData();
            byte[] rawIndex = indexData.writeIndexData();
            Container idxContainer = new Container(index.getCompression(), -1);
            idxContainer.compress(rawIndex, null);
            storage.store(255, index.getId(), idxContainer.data);
            CRC32 idxCrc32 = new CRC32();
            idxCrc32.update(idxContainer.data);
            index.setCrc((int) idxCrc32.getValue());

            System.out.println("APPLY complete. " + itemIds.length + " item(s) patched, archive revision now "
                    + archive.getRevision() + ".");
        }
    }

    // Walks the item's opcode stream (mirroring ObjType.decode()'s exact table) and, for opcodes
    // 1/23/25/90/91 specifically, overwrites the 2-byte model-id value in place if it's a key in
    // idMap. Returns the number of fields actually replaced. Throws on any opcode this walker
    // doesn't recognize (same safety net as RawOpcodeWalk).
    static int patchModelRefs(byte[] data, Map<Integer, Integer> idMap, int itemId) {
        InputStream in = new InputStream(data);
        int replacements = 0;
        while (true) {
            int opcode = in.readUnsignedByte();
            if (opcode == 0) break;
            int valueOffset = in.getOffset(); // position right after the opcode byte
            switch (opcode) {
                case 1: case 4: case 5: case 6: case 7: case 8:
                case 24: case 26: case 78: case 79:
                case 90: case 91: case 92: case 93: case 94: case 95:
                case 97: case 98: case 110: case 111: case 112:
                case 139: case 140: case 148: case 149:
                case 161: {
                    int val = in.readUnsignedShort();
                    if ((opcode == 1 || opcode == 90 || opcode == 91) && idMap.containsKey(val)) {
                        int newVal = idMap.get(val);
                        data[valueOffset] = (byte) (newVal >> 8);
                        data[valueOffset + 1] = (byte) newVal;
                        replacements++;
                    }
                    break;
                }
                case 23: case 25: {
                    int val = in.readUnsignedShort();
                    in.readUnsignedByte(); // maleOffset/femaleOffset, untouched
                    if (idMap.containsKey(val)) {
                        int newVal = idMap.get(val);
                        data[valueOffset] = (byte) (newVal >> 8);
                        data[valueOffset + 1] = (byte) newVal;
                        replacements++;
                    }
                    break;
                }
                case 2: case 3: case 9: case 164:
                    in.readString();
                    break;
                case 11: case 16: case 65:
                    break;
                case 12: case 159: case 162:
                    in.readInt();
                    break;
                case 13: case 14: case 27: case 42: case 113: case 114:
                    in.readByte();
                    break;
                case 75:
                    in.readShort();
                    break;
                case 115: case 150: case 151: case 152: case 153: case 154:
                case 155: case 156: case 157: case 158: case 165:
                    in.readUnsignedByte();
                    break;
                case 163:
                    in.readByte(); in.readByte(); in.readByte(); in.readByte();
                    break;
                case 40: case 41: {
                    int n = in.readUnsignedByte();
                    for (int i = 0; i < n; i++) { in.readUnsignedShort(); in.readUnsignedShort(); }
                    break;
                }
                case 132: case 160: {
                    int n = in.readUnsignedByte();
                    for (int i = 0; i < n; i++) in.readUnsignedShort();
                    break;
                }
                case 134:
                    in.readUnsignedByte(); in.readInt(); in.readInt();
                    break;
                case 249: {
                    int n = in.readUnsignedByte();
                    for (int i = 0; i < n; i++) {
                        boolean isString = in.readUnsignedByte() == 1;
                        in.readUnsignedByte(); in.readUnsignedByte(); in.readUnsignedByte();
                        if (isString) in.readString(); else in.readInt();
                    }
                    break;
                }
                default:
                    if (opcode >= 30 && opcode < 35) { in.readString(); break; }
                    if (opcode >= 35 && opcode < 40) { in.readString(); break; }
                    if (opcode >= 100 && opcode < 110) { in.readUnsignedShort(); in.readUnsignedShort(); break; }
                    throw new RuntimeException("item " + itemId + ": unknown opcode " + opcode
                            + " at offset " + (valueOffset - 1) + " -- refusing to guess, aborting patch");
            }
        }
        return replacements;
    }
}
