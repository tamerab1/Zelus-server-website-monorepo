import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.ObjectDefinition;
import net.runelite.cache.definitions.loaders.ObjectLoader;
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
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

// Inserts new "actions" (opcodes 30-34, per net.runelite.cache's real ObjectLoader) slots for a
// single object's raw def bytes, then writes the modified multi-file object archive (index 2,
// archive 6 = ConfigType.OBJECT) back using RuneLite's own fs library for the chunk-delta
// reconstruction. Same list-backed technique as SpliceItemOption.java (this archive has the same
// class of pre-existing duplicate-file-id issue as the items archive).
//
// Modes:
//   verify <cachePath> <objectId>                    -- read-only: dump current actions, no write
//   apply  <cachePath> <objectId> <slot>=<label> ...  -- insert one or more action slots (0-4)
public class InsertObjectActions {
    public static void main(String[] args) throws Exception {
        String mode = args[0];
        String cachePath = args[1];
        int targetObjectId = Integer.parseInt(args[2]);

        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Index index = store.getIndex(IndexType.CONFIGS);
            Archive archive = index.getArchive(ConfigType.OBJECT.getId());

            byte[] compressed = storage.loadArchive(archive);
            byte[] decompressed = archive.decompress(compressed);

            FileData[] fileData = archive.getFileData();
            System.out.println("Archive 6 (objects): " + fileData.length + " file slots, compression="
                    + archive.getCompression() + " revision=" + archive.getRevision());

            List<byte[]> fileContents = splitChunks(decompressed, fileData.length);

            List<Integer> matches = new ArrayList<>();
            for (int i = 0; i < fileData.length; i++) {
                if (fileData[i].getId() == targetObjectId) {
                    matches.add(i);
                }
            }
            System.out.println("Found " + matches.size() + " file slot(s) with id " + targetObjectId + ": " + matches);
            if (matches.isEmpty()) {
                System.out.println("Nothing to do.");
                return;
            }

            ObjectLoader loader = new ObjectLoader();

            if (mode.equals("verify")) {
                for (int slotIndex : matches) {
                    ObjectDefinition def = loader.load(targetObjectId, fileContents.get(slotIndex));
                    System.out.println("Slot " + slotIndex + ": name=\"" + def.getName()
                            + "\" actions=" + java.util.Arrays.toString(def.getActions()));
                }
                return;
            }

            // apply: remaining args are "slot=Label"
            String[] newLabels = new String[5];
            for (int i = 3; i < args.length; i++) {
                String[] parts = args[i].split("=", 2);
                int slot = Integer.parseInt(parts[0]);
                newLabels[slot] = parts[1];
            }

            boolean anyChanged = false;
            for (int slotIndex : matches) {
                byte[] raw = fileContents.get(slotIndex);
                ObjectDefinition before = loader.load(targetObjectId, raw);
                System.out.println("Slot " + slotIndex + " BEFORE: name=\"" + before.getName()
                        + "\" actions=" + java.util.Arrays.toString(before.getActions()));

                byte[] current = raw;
                for (int actionSlot = 0; actionSlot < 5; actionSlot++) {
                    if (newLabels[actionSlot] == null) {
                        continue;
                    }
                    if (before.getActions()[actionSlot] != null) {
                        throw new IllegalStateException("Slot " + slotIndex + " action " + actionSlot
                                + " is already set to \"" + before.getActions()[actionSlot] + "\" -- refusing to overwrite, ABORTING.");
                    }
                    current = insertActionOpcode(current, actionSlot, newLabels[actionSlot]);
                }

                ObjectDefinition after = loader.load(targetObjectId, current);
                System.out.println("Slot " + slotIndex + " AFTER:  name=\"" + after.getName()
                        + "\" actions=" + java.util.Arrays.toString(after.getActions()));

                if (!fieldsMatchExceptActions(before, after)) {
                    throw new IllegalStateException("Slot " + slotIndex
                            + ": insertion changed something other than actions[] -- ABORTING, no write performed.");
                }
                for (int actionSlot = 0; actionSlot < 5; actionSlot++) {
                    if (newLabels[actionSlot] != null && !newLabels[actionSlot].equals(after.getActions()[actionSlot])) {
                        throw new IllegalStateException("Slot " + slotIndex + " action " + actionSlot
                                + " did not come out as expected -- ABORTING.");
                    }
                }

                fileContents.set(slotIndex, current);
                anyChanged = true;
            }

            if (!anyChanged) {
                System.out.println("No slots were actually modified. Not writing anything.");
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

            System.out.println("APPLY complete. Archive 6 revision now " + archive.getRevision()
                    + ", new compressed size " + container.data.length + " bytes.");
        }
    }

    // Inserts one new opcode record (30+slot, followed by the CP1252 string + null terminator)
    // immediately after the LAST currently-present opcode whose number is <= 30+slot (or right
    // before the terminating 0x00 if none qualify) -- keeps opcodes in their conventional
    // ascending order without needing to know this object's exact existing opcode set up front.
    static byte[] insertActionOpcode(byte[] b, int slot, String label) {
        int targetOpcode = 30 + slot;
        InputStream is = new InputStream(b);
        int insertAt = -1;
        while (true) {
            int posBeforeOpcode = is.getOffset();
            int opcode = is.readUnsignedByte();
            if (opcode == 0) {
                insertAt = posBeforeOpcode;
                break;
            }
            InsertObjectActions.skipOpcodePayload(opcode, is);
            if (opcode <= targetOpcode) {
                insertAt = is.getOffset();
            }
        }

        byte[] labelBytes = label.getBytes(Charset.forName("windows-1252"));
        byte[] insertion = new byte[1 + labelBytes.length + 1];
        insertion[0] = (byte) targetOpcode;
        System.arraycopy(labelBytes, 0, insertion, 1, labelBytes.length);
        insertion[insertion.length - 1] = 0;

        byte[] result = new byte[b.length + insertion.length];
        System.arraycopy(b, 0, result, 0, insertAt);
        System.arraycopy(insertion, 0, result, insertAt, insertion.length);
        System.arraycopy(b, insertAt, result, insertAt + insertion.length, b.length - insertAt);
        return result;
    }

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
            stream.writeByte(1);
        }
        return stream.flip();
    }

    // Mirrors net.runelite.cache's real ObjectLoader.processOp() dispatch exactly, but only to
    // track byte widths (skip, don't decode into a def) -- keeps the insertion-point walk
    // correctly aligned across every opcode this object's def actually uses.
    static void skipOpcodePayload(int opcode, InputStream is) {
        if (opcode == 1) {
            int length = is.readUnsignedByte();
            for (int i = 0; i < length; i++) { is.readUnsignedShort(); is.readUnsignedByte(); }
        } else if (opcode == 2) is.readString();
        else if (opcode == 5) {
            int length = is.readUnsignedByte();
            for (int i = 0; i < length; i++) is.readUnsignedShort();
        } else if (opcode == 14) is.readUnsignedByte();
        else if (opcode == 15) is.readUnsignedByte();
        else if (opcode == 17) { /* no payload */ }
        else if (opcode == 18) { /* no payload */ }
        else if (opcode == 19) is.readUnsignedByte();
        else if (opcode == 21) { /* no payload */ }
        else if (opcode == 22) { /* no payload */ }
        else if (opcode == 23) { /* no payload */ }
        else if (opcode == 24) is.readUnsignedShort();
        else if (opcode == 27) { /* no payload */ }
        else if (opcode == 28) is.readUnsignedByte();
        else if (opcode == 29) is.readByte();
        else if (opcode == 39) is.readByte();
        else if (opcode >= 30 && opcode < 35) is.readString();
        else if (opcode == 40) {
            int length = is.readUnsignedByte();
            for (int i = 0; i < length; i++) { is.readShort(); is.readShort(); }
        } else if (opcode == 41) {
            int length = is.readUnsignedByte();
            for (int i = 0; i < length; i++) { is.readShort(); is.readShort(); }
        } else if (opcode == 61) is.readUnsignedShort();
        else if (opcode == 62) { /* no payload */ }
        else if (opcode == 64) { /* no payload */ }
        else if (opcode == 65) is.readUnsignedShort();
        else if (opcode == 66) is.readUnsignedShort();
        else if (opcode == 67) is.readUnsignedShort();
        else if (opcode == 68) is.readUnsignedShort();
        else if (opcode == 69) is.readByte();
        else if (opcode == 70) is.readUnsignedShort();
        else if (opcode == 71) is.readUnsignedShort();
        else if (opcode == 72) is.readUnsignedShort();
        else if (opcode == 73) { /* no payload */ }
        else if (opcode == 74) { /* no payload */ }
        else if (opcode == 75) is.readUnsignedByte();
        else if (opcode == 77) {
            is.readUnsignedShort();
            is.readUnsignedShort();
            int length = is.readUnsignedByte();
            for (int i = 0; i <= length; i++) is.readUnsignedShort();
        } else if (opcode == 78) {
            is.readUnsignedShort();
            is.readUnsignedByte();
            is.readUnsignedByte(); // rev220SoundData assumed true (matches this project's revision)
        } else if (opcode == 79) {
            is.readUnsignedShort();
            is.readUnsignedShort();
            is.readUnsignedByte();
            is.readUnsignedByte();
            int length = is.readUnsignedByte();
            for (int i = 0; i < length; i++) is.readUnsignedShort();
        } else if (opcode == 81) is.readUnsignedByte();
        else if (opcode == 82) is.readUnsignedShort();
        else if (opcode == 89) { /* no payload */ }
        else if (opcode == 90) { /* no payload */ }
        else if (opcode == 92) {
            is.readUnsignedShort();
            is.readUnsignedShort();
            is.readUnsignedShort();
            int length = is.readUnsignedByte();
            for (int i = 0; i <= length; i++) is.readUnsignedShort();
        } else if (opcode == 249) {
            int length = is.readUnsignedByte();
            for (int i = 0; i < length; i++) {
                boolean isString = is.readUnsignedByte() == 1;
                is.read24BitInt();
                if (isString) is.readString(); else is.readInt();
            }
        } else {
            throw new IllegalStateException("Unrecognized opcode " + opcode + " at offset " + is.getOffset()
                    + " -- refusing to guess a width, ABORTING (object def format not fully understood for this byte).");
        }
    }

    static boolean fieldsMatchExceptActions(ObjectDefinition a, ObjectDefinition b) {
        if (!java.util.Objects.equals(a.getName(), b.getName())) return false;
        if (a.getSizeX() != b.getSizeX()) return false;
        if (a.getSizeY() != b.getSizeY()) return false;
        if (a.getCategory() != b.getCategory()) return false;
        if (a.isBlocksProjectile() != b.isBlocksProjectile()) return false;
        if (!java.util.Arrays.equals(a.getObjectModels(), b.getObjectModels())) return false;
        if (!java.util.Arrays.equals(a.getObjectTypes(), b.getObjectTypes())) return false;
        return true;
    }
}
