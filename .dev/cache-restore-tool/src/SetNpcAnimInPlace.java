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

// Sets an existing npc's standingAnimation (opcode 13) and walkingAnimation (opcode 14) in place.
// Usage: verify|apply <cachePath> <npcId> <standAnimId> <walkAnimId>
public class SetNpcAnimInPlace {
    public static void main(String[] args) throws Exception {
        String mode = args[0];
        String cachePath = args[1];
        int npcId = Integer.parseInt(args[2]);
        int standAnim = Integer.parseInt(args[3]);
        int walkAnim = Integer.parseInt(args[4]);

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
            for (int i = 0; i < fileData.length; i++) if (fileData[i].getId() == npcId) slot = i;
            if (slot == -1) throw new IllegalStateException("npc id " + npcId + " not found");

            byte[] work = fileContents.get(slot);
            NpcLoader loader = new NpcLoader();
            NpcDefinition before = loader.load(npcId, work);
            System.out.println("BEFORE: name=\"" + before.name + "\" standAnim=" + before.standingAnimation
                    + " walkAnim=" + before.walkingAnimation);

            work = SplicePetify.replaceOrInsert(work, 13, SplicePetify.twoBytes(standAnim));
            work = SplicePetify.replaceOrInsert(work, 14, SplicePetify.twoBytes(walkAnim));

            NpcDefinition after = loader.load(npcId, work);
            System.out.println("AFTER:  name=\"" + after.name + "\" standAnim=" + after.standingAnimation
                    + " walkAnim=" + after.walkingAnimation);

            if (after.standingAnimation != standAnim || after.walkingAnimation != walkAnim) {
                throw new IllegalStateException("anim not applied correctly -- ABORTING");
            }
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
