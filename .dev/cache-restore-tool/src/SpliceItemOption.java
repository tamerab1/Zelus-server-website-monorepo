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
import net.runelite.cache.io.OutputStream;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

// Removes one "interfaceOptions" slot (opcodes 35-39, per net.runelite.cache's real ItemLoader --
// verified against this project's own live cache data already) from a single item's raw def bytes,
// then writes the modified multi-file items archive (index 2, archive 10 = ConfigType.ITEM) back
// using the real RuneLite fs library for the actual chunk-delta reconstruction, never a hand-rolled
// reimplementation of that format. List-backed (not the library's own Map-backed ArchiveFiles) to
// tolerate this cache's known 828 pre-existing duplicate file-id entries in this exact archive.
//
// Modes:
//   verify <cachePath> <itemId> <optionSlot 0-4>   -- read-only: locate + report the splice, no write
//   apply  <cachePath> <itemId> <optionSlot 0-4>   -- perform the write (archive 10 + index 2's own
//                                                      reference table only; nothing else touched)
public class SpliceItemOption {
    public static void main(String[] args) throws Exception {
        String mode = args[0];
        String cachePath = args[1];
        int targetItemId = Integer.parseInt(args[2]);
        int optionSlot = Integer.parseInt(args[3]); // 0-4 -> opcode 35-39

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

            List<byte[]> fileContents = splitChunks(decompressed, fileData.length);

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

                int[] span = findOptionSpan(raw, optionSlot);
                if (span == null) {
                    System.out.println("Slot " + slotIndex + ": option slot " + optionSlot
                            + " not present in this copy (nothing to remove here).");
                    continue;
                }
                System.out.println("Slot " + slotIndex + ": removing bytes [" + span[0] + "," + span[1]
                        + ") (" + (span[1] - span[0]) + " bytes) for option slot " + optionSlot);

                byte[] spliced = new byte[raw.length - (span[1] - span[0])];
                System.arraycopy(raw, 0, spliced, 0, span[0]);
                System.arraycopy(raw, span[1], spliced, span[0], raw.length - span[1]);

                ItemDefinition after = loader.load(targetItemId, spliced);
                System.out.println("Slot " + slotIndex + " AFTER:  name=\"" + after.name
                        + "\" interfaceOptions=" + java.util.Arrays.toString(after.interfaceOptions));

                if (!fieldsMatchExceptOption(before, after, optionSlot)) {
                    throw new IllegalStateException("Slot " + slotIndex
                            + ": splice changed something other than the target option slot -- ABORTING, no write performed.");
                }
                if (after.interfaceOptions[optionSlot] != null) {
                    throw new IllegalStateException("Slot " + slotIndex
                            + ": option slot still present after splice -- ABORTING.");
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

            byte[] newDecompressed = joinChunks(fileContents);
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

    // Mirrors ArchiveFiles.loadContents()'s real chunk-delta algorithm exactly, but returns an
    // ORDER-PRESERVING List<byte[]> (positional, matching fileData[] order) instead of populating
    // a Map-backed ArchiveFiles -- avoids the "duplicate file ids" exception this specific archive
    // is known to trigger (828 pre-existing dupes, unrelated data-quality issue, not touched here).
    static List<byte[]> splitChunks(byte[] data, int filesCount) {
        if (filesCount == 1) {
            List<byte[]> single = new ArrayList<>();
            single.add(data);
            return single;
        }

        InputStream stream = new InputStream(data);
        stream.setOffset(stream.getLength() - 1);
        int chunks = stream.readUnsignedByte();

        stream.setOffset(stream.getLength() - 1 - chunks * filesCount * 4);
        int[][] chunkSizes = new int[filesCount][chunks];
        int[] filesSize = new int[filesCount];

        for (int chunk = 0; chunk < chunks; ++chunk) {
            int chunkSize = 0;
            for (int id = 0; id < filesCount; ++id) {
                int delta = stream.readInt();
                chunkSize += delta;
                chunkSizes[id][chunk] = chunkSize;
                filesSize[id] += chunkSize;
            }
        }

        byte[][] fileContents = new byte[filesCount][];
        int[] fileOffsets = new int[filesCount];
        for (int i = 0; i < filesCount; ++i) {
            fileContents[i] = new byte[filesSize[i]];
        }

        stream.setOffset(0);
        for (int chunk = 0; chunk < chunks; ++chunk) {
            for (int id = 0; id < filesCount; ++id) {
                int chunkSize = chunkSizes[id][chunk];
                stream.readBytes(fileContents[id], fileOffsets[id], chunkSize);
                fileOffsets[id] += chunkSize;
            }
        }

        List<byte[]> result = new ArrayList<>(filesCount);
        for (byte[] fc : fileContents) {
            result.add(fc);
        }
        return result;
    }

    // Mirrors ArchiveFiles.saveContents()'s real algorithm exactly, list-based.
    static byte[] joinChunks(List<byte[]> files) {
        OutputStream stream = new OutputStream();
        int filesCount = files.size();

        if (filesCount == 1) {
            stream.writeBytes(files.get(0));
        } else {
            for (byte[] contents : files) {
                stream.writeBytes(contents);
            }
            int offset = 0;
            for (byte[] contents : files) {
                int chunkSize = contents.length;
                int sz = chunkSize - offset;
                offset = chunkSize;
                stream.writeInt(sz);
            }
            stream.writeByte(1); // chunks
        }
        return stream.flip();
    }

    // Walks the exact same opcode dispatch as net.runelite.cache's real ItemLoader.decodeValues(),
    // but only to track byte offsets -- returns [startOfOpcodeByte, endAfterPayload) for the single
    // interfaceOptions opcode (35+slot) if present, else null. Every other opcode's width logic is
    // copied verbatim from the real decoder so the walk stays correctly aligned throughout the file.
    static int[] findOptionSpan(byte[] b, int slot) {
        int targetOpcode = 35 + slot;
        InputStream is = new InputStream(b);
        while (true) {
            int start = is.getOffset();
            int opcode = is.readUnsignedByte();
            if (opcode == 0) {
                break;
            }
            skipOpcodePayload(opcode, is);
            int end = is.getOffset();
            if (opcode == targetOpcode) {
                return new int[]{start, end};
            }
        }
        return null;
    }

    static void skipOpcodePayload(int opcode, InputStream is) {
        if (opcode == 1) is.readUnsignedShort();
        else if (opcode == 2) is.readString();
        else if (opcode == 3) is.readString();
        else if (opcode == 4) is.readUnsignedShort();
        else if (opcode == 5) is.readUnsignedShort();
        else if (opcode == 6) is.readUnsignedShort();
        else if (opcode == 7) is.readUnsignedShort();
        else if (opcode == 8) is.readUnsignedShort();
        else if (opcode == 9) is.readString();
        else if (opcode == 11) { /* no payload */ }
        else if (opcode == 12) is.readInt();
        else if (opcode == 13) is.readByte();
        else if (opcode == 14) is.readByte();
        else if (opcode == 16) { /* no payload */ }
        else if (opcode == 23) { is.readUnsignedShort(); is.readUnsignedByte(); }
        else if (opcode == 24) is.readUnsignedShort();
        else if (opcode == 25) { is.readUnsignedShort(); is.readUnsignedByte(); }
        else if (opcode == 26) is.readUnsignedShort();
        else if (opcode == 27) is.readByte();
        else if (opcode >= 30 && opcode < 35) is.readString();
        else if (opcode >= 35 && opcode < 40) is.readString();
        else if (opcode == 40) {
            int n = is.readUnsignedByte();
            for (int i = 0; i < n; i++) { is.readUnsignedShort(); is.readUnsignedShort(); }
        } else if (opcode == 41) {
            int n = is.readUnsignedByte();
            for (int i = 0; i < n; i++) { is.readUnsignedShort(); is.readUnsignedShort(); }
        } else if (opcode == 42) is.readByte();
        else if (opcode == 43) {
            is.readUnsignedByte(); // opId
            while (true) {
                int subopId = is.readUnsignedByte() - 1;
                if (subopId == -1) break;
                is.readString();
            }
        } else if (opcode == 65) { /* no payload */ }
        else if (opcode == 75) is.readShort();
        else if (opcode == 78) is.readUnsignedShort();
        else if (opcode == 79) is.readUnsignedShort();
        else if (opcode == 90) is.readUnsignedShort();
        else if (opcode == 91) is.readUnsignedShort();
        else if (opcode == 92) is.readUnsignedShort();
        else if (opcode == 93) is.readUnsignedShort();
        else if (opcode == 94) is.readUnsignedShort();
        else if (opcode == 95) is.readUnsignedShort();
        else if (opcode == 97) is.readUnsignedShort();
        else if (opcode == 98) is.readUnsignedShort();
        else if (opcode >= 100 && opcode < 110) { is.readUnsignedShort(); is.readUnsignedShort(); }
        else if (opcode == 110) is.readUnsignedShort();
        else if (opcode == 111) is.readUnsignedShort();
        else if (opcode == 112) is.readUnsignedShort();
        else if (opcode == 113) is.readByte();
        else if (opcode == 114) is.readByte();
        else if (opcode == 115) is.readUnsignedByte();
        else if (opcode == 139) is.readUnsignedShort();
        else if (opcode == 140) is.readUnsignedShort();
        else if (opcode == 148) is.readUnsignedShort();
        else if (opcode == 149) is.readUnsignedShort();
        else if (opcode == 249) {
            int length = is.readUnsignedByte();
            for (int i = 0; i < length; i++) {
                boolean isString = is.readUnsignedByte() == 1;
                is.read24BitInt();
                if (isString) is.readString(); else is.readInt();
            }
        } else {
            throw new IllegalStateException("Unrecognized opcode " + opcode + " at offset " + is.getOffset()
                    + " -- refusing to guess a width, ABORTING splice (item def format not fully understood for this byte).");
        }
    }

    static boolean fieldsMatchExceptOption(ItemDefinition a, ItemDefinition b, int optionSlot) {
        if (!java.util.Objects.equals(a.name, b.name)) return false;
        if (!java.util.Objects.equals(a.examine, b.examine)) return false;
        if (a.inventoryModel != b.inventoryModel) return false;
        if (a.maleModel0 != b.maleModel0) return false;
        if (a.femaleModel0 != b.femaleModel0) return false;
        if (a.wearPos1 != b.wearPos1) return false;
        if (a.wearPos2 != b.wearPos2) return false;
        if (a.wearPos3 != b.wearPos3) return false;
        if (a.cost != b.cost) return false;
        if (a.isTradeable != b.isTradeable) return false;
        for (int i = 0; i < a.interfaceOptions.length; i++) {
            if (i == optionSlot) continue;
            if (!java.util.Objects.equals(a.interfaceOptions[i], b.interfaceOptions[i])) return false;
        }
        return true;
    }
}
