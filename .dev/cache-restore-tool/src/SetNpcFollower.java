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

// Sets an existing npc's isFollower flag in place (opcode 111 -- a bare flag opcode with no
// payload, confirmed via javap disassembly of NpcLoader: opcode 111 sets both isFollower=true and
// lowPriorityFollowerOps=true; opcode 122 sets isFollower alone. Uses 111 to match how existing
// pet-follower npcs like Nox (17050) are encoded.
//
// Usage: verify|apply <cachePath> <npcId>
public class SetNpcFollower {
    public static void main(String[] args) throws Exception {
        String mode = args[0];
        String cachePath = args[1];
        int npcId = Integer.parseInt(args[2]);

        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Index index = store.getIndex(IndexType.CONFIGS);
            Archive archive = index.getArchive(ConfigType.NPC.getId());

            byte[] compressed = storage.loadArchive(archive);
            byte[] decompressed = archive.decompress(compressed);
            FileData[] fileData = archive.getFileData();
            List<byte[]> fileContents = SpliceItemOption.splitChunks(decompressed, fileData.length);

            int slot = -1;
            for (int i = 0; i < fileData.length; i++) {
                if (fileData[i].getId() == npcId) slot = i;
            }
            if (slot == -1) throw new IllegalStateException("npc id " + npcId + " not found");

            byte[] raw = fileContents.get(slot);
            NpcLoader loader = new NpcLoader();
            NpcDefinition before = loader.load(npcId, raw);

            byte[] work = SplicePetify.replaceOrInsert(raw, 111, new byte[0]);

            NpcDefinition after = loader.load(npcId, work);
            System.out.println("BEFORE: name=\"" + before.name + "\" isFollower=" + before.isFollower);
            System.out.println("AFTER:  name=\"" + after.name + "\" isFollower=" + after.isFollower);

            if (!after.isFollower) throw new IllegalStateException("isFollower not applied -- ABORTING");
            if (!java.util.Objects.equals(before.name, after.name) || !java.util.Arrays.equals(before.models, after.models)) {
                throw new IllegalStateException("name/models changed unexpectedly -- ABORTING");
            }

            if (mode.equals("verify")) {
                System.out.println("VERIFY mode -- not writing.");
                return;
            }
            if (!mode.equals("apply")) throw new IllegalArgumentException("mode must be verify or apply");

            fileContents.set(slot, work);
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

            System.out.println("APPLY complete for npc " + npcId + ". Archive revision now " + archive.getRevision() + ".");
        }
    }
}
