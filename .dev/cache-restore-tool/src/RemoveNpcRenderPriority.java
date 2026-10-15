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

import java.io.File;
import java.util.List;

// Removes opcode 99 (a zero-payload presence flag that sets hasRenderPriority=true) from an
// existing npc, in place. No confirmed-working npc in this cache combines hasRenderPriority=true
// with a non-null recolorToFind/Replace table (checked every existing recolored npc: pets,
// Corrupted Lobstrosity all have hasRenderPriority=false; every hasRenderPriority=true npc
// (Draconies, Voidbuss, Meboom, base Tekton) has no recolor) -- this is the evidence-based fix for
// the Draco invisibility bug.
//
// Usage: verify|apply <cachePath> <npcId>
public class RemoveNpcRenderPriority {
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

            int slotIndex = -1;
            for (int i = 0; i < fileData.length; i++) if (fileData[i].getId() == npcId) { slotIndex = i; break; }
            if (slotIndex == -1) throw new IllegalStateException("npc id " + npcId + " not found");

            byte[] raw = fileContents.get(slotIndex);
            NpcLoader loader = new NpcLoader();
            NpcDefinition before = loader.load(npcId, raw);
            System.out.println("BEFORE: name=\"" + before.name + "\" hasRenderPriority=" + before.hasRenderPriority);

            int[] span = findOpcode99Span(raw);
            if (span == null) {
                System.out.println("opcode 99 not present -- nothing to remove.");
                return;
            }
            byte[] work = new byte[raw.length - (span[1] - span[0])];
            System.arraycopy(raw, 0, work, 0, span[0]);
            System.arraycopy(raw, span[1], work, span[0], raw.length - span[1]);

            NpcDefinition after = loader.load(npcId, work);
            System.out.println("AFTER:  name=\"" + after.name + "\" hasRenderPriority=" + after.hasRenderPriority
                    + " models=" + java.util.Arrays.toString(after.models)
                    + " recolorToFind=" + java.util.Arrays.toString(after.recolorToFind));

            if (after.hasRenderPriority) throw new IllegalStateException("hasRenderPriority still true -- ABORTING");
            if (!java.util.Objects.equals(before.name, after.name)) throw new IllegalStateException("name changed -- ABORTING");
            if (!java.util.Arrays.equals(before.models, after.models)) throw new IllegalStateException("models changed -- ABORTING");
            if (!java.util.Arrays.equals(before.recolorToFind, after.recolorToFind)) throw new IllegalStateException("recolor changed unexpectedly -- ABORTING");
            if (before.standingAnimation != after.standingAnimation || before.walkingAnimation != after.walkingAnimation)
                throw new IllegalStateException("animations changed unexpectedly -- ABORTING");
            if (before.size != after.size) throw new IllegalStateException("size changed unexpectedly -- ABORTING");
            if (!java.util.Arrays.equals(before.actions, after.actions)) throw new IllegalStateException("actions changed unexpectedly -- ABORTING");

            if (mode.equals("verify")) { System.out.println("VERIFY mode -- not writing."); return; }
            if (!mode.equals("apply")) throw new IllegalArgumentException("mode must be verify or apply");

            fileContents.set(slotIndex, work);
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

            System.out.println("APPLY complete. npc " + npcId + " hasRenderPriority removed. Archive revision now " + archive.getRevision() + ".");
        }
    }

    // Walks the same opcode-width table InsertNPCActions.skipOpcodePayload already uses.
    static int[] findOpcode99Span(byte[] b) {
        InputStream is = new InputStream(b);
        while (true) {
            int start = is.getOffset();
            int opcode = is.readUnsignedByte();
            if (opcode == 0) return null;
            int end;
            if (opcode == 99) {
                end = is.getOffset(); // zero-payload opcode -- span is just the opcode byte itself
                return new int[]{start, end};
            }
            InsertNPCActions.skipOpcodePayload(opcode, is);
        }
    }
}
