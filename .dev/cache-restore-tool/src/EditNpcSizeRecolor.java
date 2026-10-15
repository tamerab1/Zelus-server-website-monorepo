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

// Edits an EXISTING npc def in place (same id) -- sets size (opcode 12, tile footprint),
// widthScale/heightScale (opcode 97/98, visual model scale -- same opcodes EditNpcInPlace.java
// already uses), combatLevel (opcode 95), and a recolor table (opcode 40: count-prefixed list of
// find/replace HSL shorts). Used to turn a cloned Lobstrosity (SpliceNewNpc) into a bigger,
// differently-colored boss variant without touching the model itself.
//
// Usage: <cachePath> <npcId> <size> <widthScale> <heightScale> <combatLevel> <find1,replace1,find2,replace2,...>
public class EditNpcSizeRecolor {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        int targetId = Integer.parseInt(args[1]);
        int size = Integer.parseInt(args[2]);
        int widthScale = Integer.parseInt(args[3]);
        int heightScale = Integer.parseInt(args[4]);
        int combatLevel = Integer.parseInt(args[5]);
        String[] recolorParts = args[6].split(",");
        int pairCount = recolorParts.length / 2;

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
                if (fileData[i].getId() == targetId) slot = i;
            }
            if (slot == -1) throw new IllegalStateException("npc id " + targetId + " not found");

            byte[] work = fileContents.get(slot);
            NpcLoader loader = new NpcLoader();
            NpcDefinition before = loader.load(targetId, work);

            work = SplicePetify.replaceOrInsert(work, 12, new byte[]{(byte) size});
            work = SplicePetify.replaceOrInsert(work, 97, SplicePetify.twoBytes(widthScale));
            work = SplicePetify.replaceOrInsert(work, 98, SplicePetify.twoBytes(heightScale));
            work = SplicePetify.replaceOrInsert(work, 95, SplicePetify.twoBytes(combatLevel));

            byte[] recolorPayload = new byte[1 + pairCount * 4];
            recolorPayload[0] = (byte) pairCount;
            int off = 1;
            for (int i = 0; i < pairCount; i++) {
                int find = Integer.parseInt(recolorParts[i * 2].trim());
                int replace = Integer.parseInt(recolorParts[i * 2 + 1].trim());
                recolorPayload[off++] = (byte) ((find >> 8) & 0xFF);
                recolorPayload[off++] = (byte) (find & 0xFF);
                recolorPayload[off++] = (byte) ((replace >> 8) & 0xFF);
                recolorPayload[off++] = (byte) (replace & 0xFF);
            }
            work = SplicePetify.replaceOrInsert(work, 40, recolorPayload);

            byte[] newRaw = work;
            NpcDefinition after = loader.load(targetId, newRaw);

            System.out.println("BEFORE: name=\"" + before.name + "\" size=" + before.size + " widthScale=" + before.widthScale
                    + " heightScale=" + before.heightScale + " combatLevel=" + before.combatLevel
                    + " recolorToFind=" + java.util.Arrays.toString(before.recolorToFind));
            System.out.println("AFTER:  name=\"" + after.name + "\" size=" + after.size + " widthScale=" + after.widthScale
                    + " heightScale=" + after.heightScale + " combatLevel=" + after.combatLevel
                    + " recolorToFind=" + java.util.Arrays.toString(after.recolorToFind)
                    + " recolorToReplace=" + java.util.Arrays.toString(after.recolorToReplace));

            if (after.size != size) throw new IllegalStateException("size not applied -- ABORTING");
            if (after.widthScale != widthScale || after.heightScale != heightScale)
                throw new IllegalStateException("scale not applied -- ABORTING");
            if (after.combatLevel != combatLevel) throw new IllegalStateException("combatLevel not applied -- ABORTING");
            if (after.recolorToFind == null || after.recolorToFind.length != pairCount)
                throw new IllegalStateException("recolor not applied -- ABORTING");
            if (!before.name.equals(after.name)) throw new IllegalStateException("name changed unexpectedly -- ABORTING");
            if (!java.util.Arrays.equals(before.models, after.models))
                throw new IllegalStateException("models changed unexpectedly -- ABORTING");

            fileContents.set(slot, newRaw);

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

            System.out.println("APPLY complete. Npc " + targetId + " updated. Archive revision now " + archive.getRevision() + ".");
        }
    }
}
