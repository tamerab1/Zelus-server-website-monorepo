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
import net.runelite.cache.io.OutputStream;

import java.io.File;
import java.util.List;

// One-off: clones the shooting-star rock (41223, "Crashed Star") into a new standalone object
// with the SAME models (41609/41605, no new geometry needed, just a recolor), a dark-red/gold
// palette shift via recolorToFind/Replace (opcode 40), renamed, and only a "Mine" action (drops
// "Prospect", not part of this new minigame). Does not touch the original shooting-star event at
// all (separate object id, ShootingStars.java untouched).
//
// Usage: verify|apply <cachePath> <newId> <newName>
public class SpliceCrimsonStar {
    static final int SOURCE_ID = 41223;

    // (findColor, replaceColor) pairs computed from the real face colors of models 41609/41605
    // (dumped via DumpRawModelColors.java), shifted to a dark-red/gold palette while preserving
    // each original color's relative light/dark role.
    static final short[][] PAIRS = {
            {6315, 803},
            {29070, 906},
            {28950, 788},
            {(short) -28381, 7991},
            {0, 515},
            {148, 662},
            {(short) -915, 8036},
            {152, 666},
            {4193, 7898},
            {5157, 5805},
            {5169, 5815},
            {15547, 9025},
            {15397, 6829},
            {15483, 8947},
    };

    public static void main(String[] args) throws Exception {
        String mode = args[0];
        String cachePath = args[1];
        int newId = Integer.parseInt(args[2]);
        String newName = args[3];

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
                if (fd.getId() == newId) throw new IllegalStateException("id " + newId + " already exists");
            }
            int sourceSlot = -1;
            for (int i = 0; i < fileData.length; i++) if (fileData[i].getId() == SOURCE_ID) sourceSlot = i;
            if (sourceSlot == -1) throw new IllegalStateException("source " + SOURCE_ID + " not found");

            byte[] templateRaw = fileContents.get(sourceSlot);
            ObjectLoader loader = new ObjectLoader();
            ObjectDefinition before = loader.load(SOURCE_ID, templateRaw);

            byte[] work = templateRaw;
            for (int op = 34; op >= 30; op--) work = SpliceNewObject.removeOpcode(work, op);
            work = SpliceNewObject.replaceOpcode(work, 30, SpliceNewObject.withTerminator("Mine"));
            work = SpliceNewObject.replaceOpcode(work, 2, SpliceNewObject.withTerminator(newName));

            OutputStream colorPayload = new OutputStream();
            colorPayload.writeByte(PAIRS.length);
            for (short[] pair : PAIRS) {
                colorPayload.writeShort(pair[0]);
                colorPayload.writeShort(pair[1]);
            }
            work = SpliceNewObject.replaceOpcode(work, 40, colorPayload.flip());

            ObjectDefinition after = loader.load(newId, work);
            System.out.println("BEFORE (source " + SOURCE_ID + "): name=\"" + before.getName() + "\" models="
                    + java.util.Arrays.toString(before.getObjectModels()) + " actions=" + java.util.Arrays.toString(before.getActions()));
            System.out.println("AFTER  (new " + newId + "):    name=\"" + after.getName() + "\" models="
                    + java.util.Arrays.toString(after.getObjectModels()) + " actions=" + java.util.Arrays.toString(after.getActions())
                    + " recolorToFind=" + java.util.Arrays.toString(after.getRecolorToFind())
                    + " recolorToReplace=" + java.util.Arrays.toString(after.getRecolorToReplace()));

            if (!newName.equals(after.getName())) throw new IllegalStateException("name not applied -- ABORTING");
            if (!java.util.Arrays.equals(before.getObjectModels(), after.getObjectModels()))
                throw new IllegalStateException("models changed unexpectedly -- ABORTING");
            if (!"Mine".equals(after.getActions()[0]) || after.getActions()[1] != null)
                throw new IllegalStateException("actions not applied as expected -- ABORTING");
            if (after.getRecolorToFind() == null || after.getRecolorToFind().length != PAIRS.length)
                throw new IllegalStateException("recolor not applied -- ABORTING");
            if (before.getSizeX() != after.getSizeX() || before.getSizeY() != after.getSizeY())
                throw new IllegalStateException("size changed unexpectedly -- ABORTING");

            if (mode.equals("verify")) { System.out.println("VERIFY mode -- not writing."); return; }
            if (!mode.equals("apply")) throw new IllegalArgumentException("mode must be verify or apply");

            int insertAt = fileData.length;
            for (int i = 0; i < fileData.length; i++) if (fileData[i].getId() > newId) { insertAt = i; break; }
            fileContents.add(insertAt, work);
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

            System.out.println("APPLY complete. New object id " + newId + " added. Archive revision now " + archive.getRevision() + ".");
        }
    }
}
