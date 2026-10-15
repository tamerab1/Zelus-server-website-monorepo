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

import java.io.File;
import java.nio.charset.Charset;
import java.util.List;

// Mirrors SpliceNewWearableItem.java/SpliceNewNpc.java's exact validated pattern, for objects:
// clones a template object's full cache record (size, blocksProjectile, category etc. inherited
// unchanged), overrides ONLY name (opcode 2) and the model list (opcode 1, single model, type 10
// -- matching this project's own object_spawns.json convention), and replaces the action set with
// just ["Open", null, null, null, null] (strips any other action the template carried, e.g.
// "Check", that this batch has no Java handler for). Uses InsertObjectActions.skipOpcodePayload
// (same package) as the authoritative opcode-width table so the byte walk stays correctly aligned
// regardless of which opcodes a given template object actually uses.
//
// Modes:
//   verify <cachePath> <sourceObjectId> <newId> <newName> <newModelId>
//   apply  <cachePath> <sourceObjectId> <newId> <newName> <newModelId>
public class SpliceNewObject {
    public static void main(String[] args) throws Exception {
        String mode = args[0];
        String cachePath = args[1];
        int sourceId = Integer.parseInt(args[2]);
        int newId = Integer.parseInt(args[3]);
        String newName = args[4];
        int newModelId = Integer.parseInt(args[5]);

        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Index index = store.getIndex(IndexType.CONFIGS);
            Archive archive = index.getArchive(ConfigType.OBJECT.getId());

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
            ObjectLoader loader = new ObjectLoader();
            ObjectDefinition before = loader.load(sourceId, templateRaw);

            byte[] work = templateRaw;

            // Strip every existing action slot (opcodes 30-34) -- easiest done from the highest
            // opcode down so earlier removals don't shift the position of ones not yet removed.
            for (int op = 34; op >= 30; op--) {
                work = removeOpcode(work, op);
            }
            // Re-add just "Open" at slot 0 (opcode 30).
            work = replaceOpcode(work, 30, withTerminator("Open"));

            // name
            work = replaceOpcode(work, 2, withTerminator(newName));

            // opcode 1 = objectModels: 1-byte count, then count * (unsigned-short modelId, unsigned-byte type).
            byte[] modelsPayload = new byte[]{
                    (byte) 1,
                    (byte) ((newModelId >> 8) & 0xFF), (byte) (newModelId & 0xFF),
                    (byte) 10
            };
            work = replaceOpcode(work, 1, modelsPayload);

            byte[] newRaw = work;
            ObjectDefinition after = loader.load(newId, newRaw);

            System.out.println("BEFORE (source " + sourceId + "): name=\"" + before.getName() + "\" models="
                    + java.util.Arrays.toString(before.getObjectModels()) + " actions=" + java.util.Arrays.toString(before.getActions())
                    + " sizeX=" + before.getSizeX() + " sizeY=" + before.getSizeY());
            System.out.println("AFTER  (new " + newId + "):    name=\"" + after.getName() + "\" models="
                    + java.util.Arrays.toString(after.getObjectModels()) + " actions=" + java.util.Arrays.toString(after.getActions())
                    + " sizeX=" + after.getSizeX() + " sizeY=" + after.getSizeY());

            // Safety gate: only name/models/actions may differ.
            if (!newName.equals(after.getName())) throw new IllegalStateException("name not applied -- ABORTING");
            if (after.getObjectModels() == null || after.getObjectModels().length != 1 || after.getObjectModels()[0] != newModelId)
                throw new IllegalStateException("model not applied -- ABORTING");
            if (!"Open".equals(after.getActions()[0]) || after.getActions()[1] != null || after.getActions()[2] != null
                    || after.getActions()[3] != null || after.getActions()[4] != null)
                throw new IllegalStateException("actions not applied as expected -- ABORTING");
            if (before.getSizeX() != after.getSizeX() || before.getSizeY() != after.getSizeY())
                throw new IllegalStateException("size changed unexpectedly -- ABORTING");

            if (mode.equals("verify")) {
                System.out.println("VERIFY mode -- not writing. Re-run with 'apply' to persist.");
                return;
            }
            if (!mode.equals("apply")) {
                throw new IllegalArgumentException("mode must be 'verify' or 'apply', got: " + mode);
            }

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

            System.out.println("APPLY complete. New object id " + newId + " added. Archive revision now "
                    + archive.getRevision() + ".");
        }
    }

    static byte[] withTerminator(String s) {
        byte[] b = s.getBytes(Charset.forName("windows-1252"));
        byte[] out = new byte[b.length + 1];
        System.arraycopy(b, 0, out, 0, b.length);
        out[out.length - 1] = 0;
        return out;
    }

    /** Removes one opcode+payload if present; no-op if the object doesn't have it. */
    static byte[] removeOpcode(byte[] b, int targetOpcode) {
        InputStream is = new InputStream(b);
        while (true) {
            int posBeforeOpcode = is.getOffset();
            int opcode = is.readUnsignedByte();
            if (opcode == 0) return b; // not found
            int posAfterOpcode = is.getOffset();
            InsertObjectActions.skipOpcodePayload(opcode, is);
            if (opcode == targetOpcode) {
                int posAfterPayload = is.getOffset();
                byte[] result = new byte[b.length - (posAfterPayload - posBeforeOpcode)];
                System.arraycopy(b, 0, result, 0, posBeforeOpcode);
                System.arraycopy(b, posAfterPayload, result, posBeforeOpcode, b.length - posAfterPayload);
                return result;
            }
        }
    }

    /** Replaces an existing opcode's payload, or inserts it in ascending-opcode order if absent. */
    static byte[] replaceOpcode(byte[] b, int targetOpcode, byte[] newPayload) {
        InputStream is = new InputStream(b);
        int insertAt = -1;
        while (true) {
            int posBeforeOpcode = is.getOffset();
            int opcode = is.readUnsignedByte();
            if (opcode == 0) {
                if (insertAt == -1) insertAt = posBeforeOpcode;
                break;
            }
            int posAfterOpcode = is.getOffset();
            InsertObjectActions.skipOpcodePayload(opcode, is);
            int posAfterPayload = is.getOffset();
            if (opcode == targetOpcode) {
                byte[] result = new byte[b.length - (posAfterPayload - posBeforeOpcode) + 1 + newPayload.length];
                System.arraycopy(b, 0, result, 0, posBeforeOpcode);
                result[posBeforeOpcode] = (byte) targetOpcode;
                System.arraycopy(newPayload, 0, result, posBeforeOpcode + 1, newPayload.length);
                System.arraycopy(b, posAfterPayload, result, posBeforeOpcode + 1 + newPayload.length, b.length - posAfterPayload);
                return result;
            }
            if (opcode <= targetOpcode) insertAt = posAfterPayload;
        }
        byte[] insertion = new byte[1 + newPayload.length];
        insertion[0] = (byte) targetOpcode;
        System.arraycopy(newPayload, 0, insertion, 1, newPayload.length);
        byte[] result = new byte[b.length + insertion.length];
        System.arraycopy(b, 0, result, 0, insertAt);
        System.arraycopy(insertion, 0, result, insertAt, insertion.length);
        System.arraycopy(b, insertAt, result, insertAt + insertion.length, b.length - insertAt);
        return result;
    }
}
