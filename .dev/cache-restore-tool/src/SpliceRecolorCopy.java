import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.ItemDefinition;
import net.runelite.cache.definitions.NpcDefinition;
import net.runelite.cache.definitions.loaders.ItemLoader;
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
import java.util.ArrayList;
import java.util.List;

// Creates a brand-new item or NPC definition as a byte-for-byte copy of an existing one, PLUS
// a freshly-appended opcode-40 (colour-replace) block, under a new id. The source def is never
// modified -- this only ever appends a new FileData entry + new chunk content.
//
// Modes:
//   nextid <cachePath> <item|npc>                                                 -- print the next free id
//   verify <cachePath> <item|npc> <sourceId> <newId> <newName> <f1,r1,f2,r2,...>  -- read-only, no write
//   apply  <cachePath> <item|npc> <sourceId> <newId> <newName> <f1,r1,f2,r2,...>  -- perform the write
// Pass newName as "-" to keep the source's original name unchanged.
public class SpliceRecolorCopy {
    public static void main(String[] args) throws Exception {
        String mode = args[0];
        String cachePath = args[1];
        String kind = args[2];
        int configId = kind.equals("item") ? ConfigType.ITEM.getId() : ConfigType.NPC.getId();

        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Index index = store.getIndex(IndexType.CONFIGS);
            Archive archive = index.getArchive(configId);

            byte[] compressed = storage.loadArchive(archive);
            byte[] decompressed = archive.decompress(compressed);
            FileData[] fileData = archive.getFileData();
            List<byte[]> fileContents = SpliceItemOption.splitChunks(decompressed, fileData.length);

            if (mode.equals("nextid")) {
                int max = 0;
                for (FileData fd : fileData) {
                    if (fd.getId() > max) max = fd.getId();
                }
                System.out.println("next free " + kind + " id: " + (max + 1) + " (current max: " + max + ")");
                return;
            }

            int sourceId = Integer.parseInt(args[3]);
            int newId = Integer.parseInt(args[4]);
            String newName = args[5].equals("-") ? null : args[5];
            short[] find, replace;
            if (args[6].equals("-")) {
                find = new short[0];
                replace = new short[0];
            } else {
                String[] pairsStr = args[6].split(",");
                find = new short[pairsStr.length / 2];
                replace = new short[pairsStr.length / 2];
                for (int i = 0; i < find.length; i++) {
                    find[i] = (short) Integer.parseInt(pairsStr[i * 2].trim());
                    replace[i] = (short) Integer.parseInt(pairsStr[i * 2 + 1].trim());
                }
            }
            // Optional opcode-41 texture-replace pairs (same find/replace format as colour pairs,
            // but referencing texture ids instead of packed HSL values). args[7], "-" or omitted = none.
            short[] texFind = new short[0], texReplace = new short[0];
            if (args.length > 7 && !args[7].equals("-")) {
                String[] texPairsStr = args[7].split(",");
                texFind = new short[texPairsStr.length / 2];
                texReplace = new short[texPairsStr.length / 2];
                for (int i = 0; i < texFind.length; i++) {
                    texFind[i] = (short) Integer.parseInt(texPairsStr[i * 2].trim());
                    texReplace[i] = (short) Integer.parseInt(texPairsStr[i * 2 + 1].trim());
                }
            }

            for (FileData fd : fileData) {
                if (fd.getId() == newId) {
                    throw new IllegalStateException("id " + newId + " already exists -- refusing to overwrite. Pick a different id.");
                }
            }

            // This archive has ~828 known duplicate file-id entries (earlier slots are often
            // broken/stub defs); last matching slot wins, same as DumpModelColors and how the
            // live server's own loader resolves them -- do NOT break on first match here.
            int sourceSlot = -1;
            for (int i = 0; i < fileData.length; i++) {
                if (fileData[i].getId() == sourceId) {
                    sourceSlot = i;
                }
            }
            if (sourceSlot == -1) {
                throw new IllegalStateException("source id " + sourceId + " not found");
            }

            byte[] sourceRaw = fileContents.get(sourceSlot);

            byte[] renamed;
            if (newName == null) {
                renamed = sourceRaw;
            } else {
                int[] nameSpan = findOpcode2Span(sourceRaw, kind);
                if (nameSpan == null) {
                    throw new IllegalStateException("source has no opcode-2 (name) -- refusing to guess where to put a name, ABORTING");
                }
                byte[] nameBytes = newName.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
                // opcode(1) + name bytes + terminator(1)
                byte[] newNameField = new byte[1 + nameBytes.length + 1];
                newNameField[0] = 2;
                System.arraycopy(nameBytes, 0, newNameField, 1, nameBytes.length);
                newNameField[newNameField.length - 1] = 0;

                renamed = new byte[sourceRaw.length - (nameSpan[1] - nameSpan[0]) + newNameField.length];
                System.arraycopy(sourceRaw, 0, renamed, 0, nameSpan[0]);
                System.arraycopy(newNameField, 0, renamed, nameSpan[0], newNameField.length);
                System.arraycopy(sourceRaw, nameSpan[1], renamed, nameSpan[0] + newNameField.length,
                        sourceRaw.length - nameSpan[1]);
            }

            int insertAt = findTerminatorOffset(renamed, kind);

            OutputStream block = new OutputStream();
            if (find.length > 0) {
                block.writeByte(40);
                block.writeByte(find.length);
                for (int i = 0; i < find.length; i++) {
                    block.writeShort(find[i] & 0xFFFF);
                    block.writeShort(replace[i] & 0xFFFF);
                }
            }
            if (texFind.length > 0) {
                block.writeByte(41);
                block.writeByte(texFind.length);
                for (int i = 0; i < texFind.length; i++) {
                    block.writeShort(texFind[i] & 0xFFFF);
                    block.writeShort(texReplace[i] & 0xFFFF);
                }
            }
            byte[] blockBytes = block.flip();

            byte[] newRaw = new byte[renamed.length + blockBytes.length];
            System.arraycopy(renamed, 0, newRaw, 0, insertAt);
            System.arraycopy(blockBytes, 0, newRaw, insertAt, blockBytes.length);
            System.arraycopy(renamed, insertAt, newRaw, insertAt + blockBytes.length, renamed.length - insertAt);

            if (kind.equals("item")) {
                ItemLoader loader = new ItemLoader();
                ItemDefinition before = loader.load(sourceId, sourceRaw);
                ItemDefinition after = loader.load(newId, newRaw);
                System.out.println("BEFORE (source " + sourceId + "): name=\"" + before.name + "\" colorFind=" + arr(before.colorFind));
                System.out.println("AFTER  (new " + newId + "):    name=\"" + after.name + "\" colorFind=" + arr(after.colorFind)
                        + " colorReplace=" + arr(after.colorReplace)
                        + " textureFind=" + arr(after.textureFind) + " textureReplace=" + arr(after.textureReplace));
                String expectedName = newName == null ? before.name : newName;
                if (!java.util.Objects.equals(expectedName, after.name) || before.inventoryModel != after.inventoryModel) {
                    throw new IllegalStateException("splice changed name/model unexpectedly -- ABORTING");
                }
                if (find.length > 0 && (after.colorFind == null || after.colorFind.length != find.length)) {
                    throw new IllegalStateException("colorFind not applied correctly -- ABORTING");
                }
                if (texFind.length > 0 && (after.textureFind == null || after.textureFind.length != texFind.length)) {
                    throw new IllegalStateException("textureFind not applied correctly -- ABORTING");
                }
            } else {
                NpcLoader loader = new NpcLoader();
                NpcDefinition before = loader.load(sourceId, sourceRaw);
                NpcDefinition after = loader.load(newId, newRaw);
                System.out.println("BEFORE (source " + sourceId + "): name=\"" + before.name + "\" recolorToFind=" + arr(before.recolorToFind));
                System.out.println("AFTER  (new " + newId + "):    name=\"" + after.name + "\" recolorToFind=" + arr(after.recolorToFind)
                        + " recolorToReplace=" + arr(after.recolorToReplace));
                String expectedName = newName == null ? before.name : newName;
                if (!java.util.Objects.equals(expectedName, after.name) || !java.util.Arrays.equals(before.models, after.models)) {
                    throw new IllegalStateException("splice changed name/models unexpectedly -- ABORTING");
                }
                if (after.recolorToFind == null || after.recolorToFind.length != find.length) {
                    throw new IllegalStateException("recolorToFind not applied correctly -- ABORTING");
                }
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

            System.out.println("APPLY complete. New " + kind + " id " + newId + " added. Archive revision now "
                    + archive.getRevision() + ".");
        }
    }

    static String arr(short[] a) {
        return a == null ? "null" : java.util.Arrays.toString(a);
    }

    // Walks opcodes to find the terminator (opcode 0) byte offset -- that's where a new opcode
    // block gets inserted. Items and NPCs have DIFFERENT opcode-width tables (verified: same
    // opcode number can mean a different payload shape in each format), so this dispatches to
    // the format-specific table -- SpliceItemOption's for items, InsertNPCActions' for NPCs.
    // Using the wrong one would silently misalign the walk and corrupt the splice point.
    // Same opcode walk as findTerminatorOffset, but returns [start, end) for the opcode-2 (name)
    // record specifically -- start is the opcode byte itself, end is right after the terminator
    // of that record's null-terminated string payload.
    static int[] findOpcode2Span(byte[] b, String kind) {
        InputStream is = new InputStream(b);
        while (true) {
            int start = is.getOffset();
            int opcode = is.readUnsignedByte();
            if (opcode == 0) {
                return null;
            }
            if (kind.equals("item")) {
                SpliceItemOption.skipOpcodePayload(opcode, is);
            } else {
                InsertNPCActions.skipOpcodePayload(opcode, is);
            }
            if (opcode == 2) {
                return new int[]{start, is.getOffset()};
            }
        }
    }

    static int findTerminatorOffset(byte[] b, String kind) {
        InputStream is = new InputStream(b);
        while (true) {
            int start = is.getOffset();
            int opcode = is.readUnsignedByte();
            if (opcode == 0) {
                return start;
            }
            if (kind.equals("item")) {
                SpliceItemOption.skipOpcodePayload(opcode, is);
            } else {
                InsertNPCActions.skipOpcodePayload(opcode, is);
            }
        }
    }
}
