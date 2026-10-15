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

import java.io.File;
import java.util.List;

// Mirrors SpliceNewWearableItem.java's exact validated pattern, for NPCs: clones a template npc's
// full cache record (size, animations, combat stats, actions all inherited unchanged -- safe
// default for a "visual/spawnable only" npc), then overrides ONLY name (opcode 2) and the models
// array (opcode 1, single model) to point at the new content. Sorted-insert into the NPC config
// archive (index 2, archive 9), same duplicate-file-id-tolerant List-backed splitter as items use.
//
// Modes:
//   verify <cachePath> <sourceNpcId> <newId> <newName> <newModelId>
//   apply  <cachePath> <sourceNpcId> <newId> <newName> <newModelId>
public class SpliceNewNpc {
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
            Archive archive = index.getArchive(ConfigType.NPC.getId());

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
            NpcLoader loader = new NpcLoader();
            NpcDefinition before = loader.load(sourceId, templateRaw);

            byte[] work = templateRaw;

            byte[] nameBytes = newName.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            byte[] namePayload = new byte[nameBytes.length + 1];
            System.arraycopy(nameBytes, 0, namePayload, 0, nameBytes.length);
            work = SplicePetify.replaceOrInsert(work, 2, namePayload);

            // opcode 1 = models: 1-byte count, then count * unsigned-short model ids.
            byte[] modelsPayload = new byte[]{
                    (byte) 1,
                    (byte) ((newModelId >> 8) & 0xFF), (byte) (newModelId & 0xFF)
            };
            work = SplicePetify.replaceOrInsert(work, 1, modelsPayload);

            byte[] newRaw = work;
            NpcDefinition after = loader.load(newId, newRaw);

            System.out.println("BEFORE (source " + sourceId + "): name=\"" + before.name + "\" size=" + before.size
                    + " models=" + java.util.Arrays.toString(before.models) + " combatLevel=" + before.combatLevel);
            System.out.println("AFTER  (new " + newId + "):    name=\"" + after.name + "\" size=" + after.size
                    + " models=" + java.util.Arrays.toString(after.models) + " combatLevel=" + after.combatLevel);

            // Safety gate: only name/models may differ.
            if (!newName.equals(after.name)) throw new IllegalStateException("name not applied -- ABORTING");
            if (after.models == null || after.models.length != 1 || after.models[0] != newModelId)
                throw new IllegalStateException("models not applied -- ABORTING");
            if (before.size != after.size) throw new IllegalStateException("size changed unexpectedly -- ABORTING");
            if (before.combatLevel != after.combatLevel) throw new IllegalStateException("combatLevel changed unexpectedly -- ABORTING");
            if (!java.util.Arrays.equals(before.actions, after.actions))
                throw new IllegalStateException("actions changed unexpectedly -- ABORTING");
            if (before.standingAnimation != after.standingAnimation)
                throw new IllegalStateException("standingAnimation changed unexpectedly -- ABORTING");

            if (mode.equals("verify")) {
                System.out.println("VERIFY mode -- not writing. Re-run with 'apply' to persist.");
                return;
            }
            if (!mode.equals("apply")) {
                throw new IllegalArgumentException("mode must be 'verify' or 'apply', got: " + mode);
            }

            // IndexData.writeIndexData() requires ascending sorted ids -- insert at correct position.
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

            System.out.println("APPLY complete. New npc id " + newId + " added. Archive revision now "
                    + archive.getRevision() + ".");
        }
    }
}
