import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.NpcDefinition;
import net.runelite.cache.definitions.loaders.NpcLoader;
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

// Same technique as InsertObjectActions.java (index 2, archive 9 = ConfigType.NPC here instead
// of archive 6) but mirrors net.runelite.cache's real NpcLoader.decodeValues() opcode widths
// instead of ObjectLoader's -- NPCs and objects use a different opcode set beyond the shared
// 30-34 "actions" range this tool actually inserts into.
//
// Modes:
//   verify <cachePath> <npcId>                    -- read-only: dump current actions, no write
//   apply  <cachePath> <npcId> <slot>=<label> ...  -- insert one or more action slots (0-4)
public class InsertNPCActions {
    public static void main(String[] args) throws Exception {
        String mode = args[0];
        String cachePath = args[1];
        int targetNpcId = Integer.parseInt(args[2]);

        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Index index = store.getIndex(IndexType.CONFIGS);
            Archive archive = index.getArchive(ConfigType.NPC.getId());

            byte[] compressed = storage.loadArchive(archive);
            byte[] decompressed = archive.decompress(compressed);

            FileData[] fileData = archive.getFileData();
            System.out.println("Archive " + ConfigType.NPC.getId() + " (npcs): " + fileData.length + " file slots, compression="
                    + archive.getCompression() + " revision=" + archive.getRevision());

            List<byte[]> fileContents = splitChunks(decompressed, fileData.length);

            List<Integer> matches = new ArrayList<>();
            for (int i = 0; i < fileData.length; i++) {
                if (fileData[i].getId() == targetNpcId) {
                    matches.add(i);
                }
            }
            System.out.println("Found " + matches.size() + " file slot(s) with id " + targetNpcId + ": " + matches);
            if (matches.isEmpty()) {
                System.out.println("Nothing to do.");
                return;
            }

            NpcLoader loader = new NpcLoader();

            if (mode.equals("verify")) {
                for (int slotIndex : matches) {
                    NpcDefinition def = loader.load(targetNpcId, fileContents.get(slotIndex));
                    System.out.println("Slot " + slotIndex + ": name=\"" + def.name
                            + "\" actions=" + java.util.Arrays.toString(def.actions));
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
                NpcDefinition before = loader.load(targetNpcId, raw);
                System.out.println("Slot " + slotIndex + " BEFORE: name=\"" + before.name
                        + "\" actions=" + java.util.Arrays.toString(before.actions));

                byte[] current = raw;
                for (int actionSlot = 0; actionSlot < 5; actionSlot++) {
                    if (newLabels[actionSlot] == null) {
                        continue;
                    }
                    if (before.actions[actionSlot] != null) {
                        throw new IllegalStateException("Slot " + slotIndex + " action " + actionSlot
                                + " is already set to \"" + before.actions[actionSlot] + "\" -- refusing to overwrite, ABORTING.");
                    }
                    current = insertActionOpcode(current, actionSlot, newLabels[actionSlot]);
                }

                NpcDefinition after = loader.load(targetNpcId, current);
                System.out.println("Slot " + slotIndex + " AFTER:  name=\"" + after.name
                        + "\" actions=" + java.util.Arrays.toString(after.actions));

                if (!fieldsMatchExceptActions(before, after)) {
                    throw new IllegalStateException("Slot " + slotIndex
                            + ": insertion changed something other than actions[] -- ABORTING, no write performed.");
                }
                for (int actionSlot = 0; actionSlot < 5; actionSlot++) {
                    if (newLabels[actionSlot] != null && !newLabels[actionSlot].equals(after.actions[actionSlot])) {
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

            System.out.println("APPLY complete. Archive " + ConfigType.NPC.getId() + " revision now " + archive.getRevision()
                    + ", new compressed size " + container.data.length + " bytes.");
        }
    }

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
            skipOpcodePayload(opcode, is);
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

    // Mirrors net.runelite.cache's real NpcLoader.decodeValues() dispatch exactly (NOT
    // ObjectLoader's -- NPCs use a different opcode set past the shared 30-34 actions range),
    // but only to track byte widths. Unlike the real loader (which log.warn()s and silently
    // treats unknown opcodes as zero-width -- risky to mirror), this throws on anything
    // unrecognized so a misalignment aborts loudly instead of corrupting data.
    static void skipOpcodePayload(int opcode, InputStream is) {
        if (opcode == 1) {
            int length = is.readUnsignedByte();
            for (int i = 0; i < length; i++) is.readUnsignedShort();
        } else if (opcode == 2) is.readString();
        else if (opcode == 12) is.readUnsignedByte();
        else if (opcode == 13) is.readUnsignedShort();
        else if (opcode == 14) is.readUnsignedShort();
        else if (opcode == 15) is.readUnsignedShort();
        else if (opcode == 16) is.readUnsignedShort();
        else if (opcode == 17) { is.readUnsignedShort(); is.readUnsignedShort(); is.readUnsignedShort(); is.readUnsignedShort(); }
        else if (opcode == 18) is.readUnsignedShort();
        else if (opcode >= 30 && opcode < 35) is.readString();
        else if (opcode == 40) {
            int length = is.readUnsignedByte();
            for (int i = 0; i < length; i++) { is.readUnsignedShort(); is.readUnsignedShort(); }
        } else if (opcode == 41) {
            int length = is.readUnsignedByte();
            for (int i = 0; i < length; i++) { is.readUnsignedShort(); is.readUnsignedShort(); }
        } else if (opcode == 60) {
            int length = is.readUnsignedByte();
            for (int i = 0; i < length; i++) is.readUnsignedShort();
        } else if (opcode == 74) is.readUnsignedShort();
        else if (opcode == 75) is.readUnsignedShort();
        else if (opcode == 76) is.readUnsignedShort();
        else if (opcode == 77) is.readUnsignedShort();
        else if (opcode == 78) is.readUnsignedShort();
        else if (opcode == 79) is.readUnsignedShort();
        else if (opcode == 93) { /* no payload */ }
        else if (opcode == 95) is.readUnsignedShort();
        else if (opcode == 97) is.readUnsignedShort();
        else if (opcode == 98) is.readUnsignedShort();
        else if (opcode == 99) { /* no payload */ }
        else if (opcode == 100) is.readByte();
        else if (opcode == 101) is.readByte();
        else if (opcode == 102) {
            // rev210HeadIcons=true is NpcLoader's default (no configureForRevision() call) --
            // mirrored here since this project's live NPC archive was already loading fine
            // under that default before this tool ever touched it.
            int bitfield = is.readUnsignedByte();
            int len = 0;
            for (int v = bitfield; v != 0; v >>= 1) len++;
            for (int i = 0; i < len; i++) {
                if ((bitfield & (1 << i)) != 0) {
                    is.readBigSmart2();
                    is.readUnsignedShortSmartMinusOne();
                }
            }
        } else if (opcode == 103) is.readUnsignedShort();
        else if (opcode == 106) {
            is.readUnsignedShort();
            is.readUnsignedShort();
            int length = is.readUnsignedByte();
            for (int i = 0; i <= length; i++) is.readUnsignedShort();
        } else if (opcode == 107) { /* no payload */ }
        else if (opcode == 109) { /* no payload */ }
        else if (opcode == 111) { /* no payload */ }
        else if (opcode == 114) is.readUnsignedShort();
        else if (opcode == 115) { is.readUnsignedShort(); is.readUnsignedShort(); is.readUnsignedShort(); is.readUnsignedShort(); }
        else if (opcode == 116) is.readUnsignedShort();
        else if (opcode == 117) { is.readUnsignedShort(); is.readUnsignedShort(); is.readUnsignedShort(); is.readUnsignedShort(); }
        else if (opcode == 118) {
            is.readUnsignedShort();
            is.readUnsignedShort();
            is.readUnsignedShort();
            int length = is.readUnsignedByte();
            for (int i = 0; i <= length; i++) is.readUnsignedShort();
        } else if (opcode == 122) { /* no payload */ }
        else if (opcode == 123) { /* no payload */ }
        else if (opcode == 124) is.readUnsignedShort();
        else if (opcode == 126) is.readUnsignedShort();
        else if (opcode == 249) {
            int length = is.readUnsignedByte();
            for (int i = 0; i < length; i++) {
                boolean isString = is.readUnsignedByte() == 1;
                is.read24BitInt();
                if (isString) is.readString(); else is.readInt();
            }
        } else {
            throw new IllegalStateException("Unrecognized opcode " + opcode + " at offset " + is.getOffset()
                    + " -- refusing to guess a width, ABORTING (npc def format not fully understood for this byte).");
        }
    }

    static boolean fieldsMatchExceptActions(NpcDefinition a, NpcDefinition b) {
        if (!java.util.Objects.equals(a.name, b.name)) return false;
        if (a.size != b.size) return false;
        if (a.combatLevel != b.combatLevel) return false;
        if (!java.util.Arrays.equals(a.models, b.models)) return false;
        if (!java.util.Arrays.equals(a.chatheadModels, b.chatheadModels)) return false;
        return true;
    }
}
