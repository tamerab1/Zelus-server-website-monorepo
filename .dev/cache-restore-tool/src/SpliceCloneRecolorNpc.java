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
import net.runelite.cache.io.OutputStream;

import java.io.File;
import java.util.List;

// Clones an existing npc wholesale (model, animations, actions, size, combat-relevant flags all
// UNCHANGED) into a new id, overriding only name (opcode 2) and a recolorToFind/Replace table
// (opcode 40). Built for cloning a multi-phase boss (each phase npc id differs only in
// animation/action/name-suffix, sharing one model) where every phase needs the same rename+recolor
// applied without disturbing anything else.
//
// Usage: verify|apply <cachePath> <sourceNpcId> <newId> <newName> <f1,r1,f2,r2,...>
public class SpliceCloneRecolorNpc {
    public static void main(String[] args) throws Exception {
        String mode = args[0];
        String cachePath = args[1];
        int sourceId = Integer.parseInt(args[2]);
        int newId = Integer.parseInt(args[3]);
        String newName = args[4];
        String[] pairsStr = args[5].split(",");

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
                if (fd.getId() == newId) throw new IllegalStateException("id " + newId + " already exists -- refusing to overwrite.");
            }
            int sourceSlot = -1;
            for (int i = 0; i < fileData.length; i++) if (fileData[i].getId() == sourceId) sourceSlot = i;
            if (sourceSlot == -1) throw new IllegalStateException("source id " + sourceId + " not found");

            byte[] templateRaw = fileContents.get(sourceSlot);
            NpcLoader loader = new NpcLoader();
            NpcDefinition before = loader.load(sourceId, templateRaw);

            byte[] nameBytes = newName.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            byte[] namePayload = new byte[nameBytes.length + 1];
            System.arraycopy(nameBytes, 0, namePayload, 0, nameBytes.length);
            byte[] work = SplicePetify.replaceOrInsert(templateRaw, 2, namePayload);

            OutputStream colorPayload = new OutputStream();
            colorPayload.writeByte(pairsStr.length / 2);
            for (int i = 0; i < pairsStr.length; i += 2) {
                colorPayload.writeShort(Short.parseShort(pairsStr[i].trim()) & 0xFFFF);
                colorPayload.writeShort(Short.parseShort(pairsStr[i + 1].trim()) & 0xFFFF);
            }
            work = SplicePetify.replaceOrInsert(work, 40, colorPayload.flip());

            NpcDefinition after = loader.load(newId, work);
            System.out.println("BEFORE (source " + sourceId + "): name=\"" + before.name + "\" models="
                    + java.util.Arrays.toString(before.models) + " standAnim=" + before.standingAnimation
                    + " walkAnim=" + before.walkingAnimation + " actions=" + java.util.Arrays.toString(before.actions));
            System.out.println("AFTER  (new " + newId + "):    name=\"" + after.name + "\" models="
                    + java.util.Arrays.toString(after.models) + " standAnim=" + after.standingAnimation
                    + " walkAnim=" + after.walkingAnimation + " actions=" + java.util.Arrays.toString(after.actions)
                    + " recolorToFind=" + java.util.Arrays.toString(after.recolorToFind));

            if (!newName.equals(after.name)) throw new IllegalStateException("name not applied -- ABORTING");
            if (!java.util.Arrays.equals(before.models, after.models)) throw new IllegalStateException("models changed unexpectedly -- ABORTING");
            if (before.standingAnimation != after.standingAnimation || before.walkingAnimation != after.walkingAnimation)
                throw new IllegalStateException("animations changed unexpectedly -- ABORTING");
            if (!java.util.Arrays.equals(before.actions, after.actions)) throw new IllegalStateException("actions changed unexpectedly -- ABORTING");
            if (before.size != after.size) throw new IllegalStateException("size changed unexpectedly -- ABORTING");
            if (before.combatLevel != after.combatLevel) throw new IllegalStateException("combatLevel changed unexpectedly -- ABORTING");
            if (after.recolorToFind == null || after.recolorToFind.length != pairsStr.length / 2)
                throw new IllegalStateException("recolor not applied -- ABORTING");

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

            System.out.println("APPLY complete. New npc id " + newId + " added. Archive revision now " + archive.getRevision() + ".");
        }
    }
}
