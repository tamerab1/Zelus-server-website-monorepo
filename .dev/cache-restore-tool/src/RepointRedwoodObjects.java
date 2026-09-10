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

import java.io.File;
import java.util.List;

// Repoints objects 34052-34059's varbitID field (opcode 77 in the object def format,
// confirmed via javap disassembly of RuneLite's real ObjectLoader: opcode byte, then
// varbitId(u16), varpId(u16), N(u8), N+1 more u16 array entries) from the shared
// 7907 to their own new dedicated varbit (17796-17803, added by AddRedwoodVarbits.java
// -- must run that tool with 'apply' FIRST). 34051 is left on 7907 unchanged.
//
// Deliberately does NOT reimplement the full opcode-77 payload encoder/decoder --
// instead finds the exact byte offset of the 2-byte varbitId field by locating the
// unique byte pattern [0x4D (opcode 77), 0x1E, 0xE3 (7907 big-endian)] in the file
// (aborts if that 3-byte pattern doesn't appear EXACTLY once), overwrites only those
// 2 bytes, then fully re-decodes the result with the real ObjectLoader and verifies
// varbitID/varpID/configChangeDest/name/models/actions/size are all identical to the
// original EXCEPT the intended varbitID change. Aborts the whole run (writes nothing)
// if any single object fails verification.
//
// Usage: <cachePath> [apply]
public class RepointRedwoodObjects {
    static final int[] OBJECT_IDS = {34052, 34053, 34054, 34055, 34056, 34057, 34058, 34059};
    static final int OLD_VARBIT_ID = 7907;
    static final int FIRST_NEW_VARBIT_ID = 17796;

    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        boolean apply = args.length > 1 && args[1].equals("apply");

        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Index index = store.getIndex(IndexType.CONFIGS);
            Archive archive = index.getArchive(ConfigType.OBJECT.getId());

            byte[] decompressed = archive.decompress(storage.loadArchive(archive));
            FileData[] fileData = archive.getFileData();
            List<byte[]> fileContents = SpliceItemOption.splitChunks(decompressed, fileData.length);

            ObjectLoader loader = new ObjectLoader();
            byte[] oldPattern = {0x4D, (byte) ((OLD_VARBIT_ID >> 8) & 0xFF), (byte) (OLD_VARBIT_ID & 0xFF)};

            int changed = 0;
            for (int oi = 0; oi < OBJECT_IDS.length; oi++) {
                int objId = OBJECT_IDS[oi];
                int newVarbitId = FIRST_NEW_VARBIT_ID + oi;

                int slot = -1;
                for (int i = 0; i < fileData.length; i++) {
                    if (fileData[i].getId() == objId) { slot = i; break; }
                }
                if (slot == -1) throw new IllegalStateException("object " + objId + " not found -- ABORTING.");

                byte[] before = fileContents.get(slot);
                ObjectDefinition beforeDef = loader.load(objId, before);
                if (beforeDef.getVarbitID() != OLD_VARBIT_ID) {
                    throw new IllegalStateException("object " + objId + " varbitID is " + beforeDef.getVarbitID()
                            + ", expected " + OLD_VARBIT_ID + " -- ABORTING (state doesn't match assumptions).");
                }

                int matchAt = -1, matchCount = 0;
                for (int p = 0; p + 3 <= before.length; p++) {
                    if (before[p] == oldPattern[0] && before[p + 1] == oldPattern[1] && before[p + 2] == oldPattern[2]) {
                        matchCount++;
                        matchAt = p;
                    }
                }
                if (matchCount != 1) {
                    throw new IllegalStateException("object " + objId + ": expected exactly 1 occurrence of "
                            + "[opcode77,varbitId=7907] byte pattern, found " + matchCount + " -- ABORTING, "
                            + "refusing to guess which one.");
                }

                byte[] after = before.clone();
                after[matchAt + 1] = (byte) ((newVarbitId >> 8) & 0xFF);
                after[matchAt + 2] = (byte) (newVarbitId & 0xFF);

                ObjectDefinition afterDef = loader.load(objId, after);

                verify(objId, "varbitID", afterDef.getVarbitID(), newVarbitId);
                verify(objId, "varpID", afterDef.getVarpID(), beforeDef.getVarpID());
                verifyArray(objId, "configChangeDest", afterDef.getConfigChangeDest(), beforeDef.getConfigChangeDest());
                verifyStr(objId, "name", afterDef.getName(), beforeDef.getName());
                verifyArray(objId, "objectModels", afterDef.getObjectModels(), beforeDef.getObjectModels());
                verifyArray(objId, "objectTypes", afterDef.getObjectTypes(), beforeDef.getObjectTypes());
                verify(objId, "sizeX", afterDef.getSizeX(), beforeDef.getSizeX());
                verify(objId, "sizeY", afterDef.getSizeY(), beforeDef.getSizeY());
                verifyStrArray(objId, "actions", afterDef.getActions(), beforeDef.getActions());
                if (after.length != before.length) {
                    throw new IllegalStateException("object " + objId + ": byte length changed (" + before.length
                            + " -> " + after.length + ") -- should be impossible for a same-width field swap -- ABORTING.");
                }

                System.out.println("object " + objId + " ('" + beforeDef.getName() + "'): varbitID " + OLD_VARBIT_ID
                        + " -> " + newVarbitId + ", verified byte-identical otherwise. OK.");

                fileContents.set(slot, after);
                changed++;
            }

            System.out.println("Verified " + changed + "/" + OBJECT_IDS.length + " objects OK.");
            if (!apply) {
                System.out.println("DRY RUN -- not writing. Re-run with 'apply' to persist.");
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

            System.out.println("APPLY complete. OBJECT archive revision now " + archive.getRevision() + ".");
        }
    }

    static void verify(int objId, String field, int actual, int expected) {
        if (actual != expected) {
            throw new IllegalStateException("object " + objId + ": " + field + " mismatch after edit -- expected "
                    + expected + " got " + actual + " -- ABORTING.");
        }
    }

    static void verifyStr(int objId, String field, String actual, String expected) {
        if (!java.util.Objects.equals(actual, expected)) {
            throw new IllegalStateException("object " + objId + ": " + field + " mismatch -- expected \"" + expected
                    + "\" got \"" + actual + "\" -- ABORTING.");
        }
    }

    static void verifyArray(int objId, String field, int[] actual, int[] expected) {
        if (!java.util.Arrays.equals(actual, expected)) {
            throw new IllegalStateException("object " + objId + ": " + field + " mismatch -- expected "
                    + java.util.Arrays.toString(expected) + " got " + java.util.Arrays.toString(actual)
                    + " -- ABORTING.");
        }
    }

    static void verifyStrArray(int objId, String field, String[] actual, String[] expected) {
        if (!java.util.Arrays.equals(actual, expected)) {
            throw new IllegalStateException("object " + objId + ": " + field + " mismatch -- ABORTING.");
        }
    }
}
