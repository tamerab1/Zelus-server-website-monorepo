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

// Rewrites an existing npc's 5 ground-menu action slots (opcodes 30-34, one per slot) in place --
// can REMOVE an inherited action (e.g. a pet cloned from a real monster keeping its "Attack"
// option) as well as set new ones, unlike InsertNPCActions.java (insert-only, refuses to touch an
// already-populated slot).
//
// Usage: verify|apply <cachePath> <npcId> <slot0>,<slot1>,<slot2>,<slot3>,<slot4>
//   each slot value is either a label or "-" to leave unset/remove.
public class SetNpcActionsInPlace {
    public static void main(String[] args) throws Exception {
        String mode = args[0];
        String cachePath = args[1];
        int npcId = Integer.parseInt(args[2]);
        String[] slots = args[3].split(",", -1);
        if (slots.length != 5) throw new IllegalArgumentException("need exactly 5 comma-separated slot values");

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

            byte[] work = fileContents.get(slot);
            NpcLoader loader = new NpcLoader();
            NpcDefinition before = loader.load(npcId, work);
            System.out.println("BEFORE: name=\"" + before.name + "\" actions=" + java.util.Arrays.toString(before.actions));

            for (int actionSlot = 0; actionSlot < 5; actionSlot++) {
                int opcode = 30 + actionSlot;
                String label = slots[actionSlot];
                if (label.equals("-")) {
                    work = EditNpcRecolorInPlace.stripOpcodes(work, opcode);
                } else {
                    byte[] labelBytes = label.getBytes(java.nio.charset.Charset.forName("windows-1252"));
                    byte[] payload = new byte[labelBytes.length + 1];
                    System.arraycopy(labelBytes, 0, payload, 0, labelBytes.length);
                    work = SplicePetify.replaceOrInsert(work, opcode, payload);
                }
            }

            NpcDefinition after = loader.load(npcId, work);
            System.out.println("AFTER:  name=\"" + after.name + "\" actions=" + java.util.Arrays.toString(after.actions));

            if (!java.util.Objects.equals(before.name, after.name) || !java.util.Arrays.equals(before.models, after.models)) {
                throw new IllegalStateException("name/models changed unexpectedly -- ABORTING");
            }
            for (int actionSlot = 0; actionSlot < 5; actionSlot++) {
                String expected = slots[actionSlot].equals("-") ? null : slots[actionSlot];
                if (!java.util.Objects.equals(expected, after.actions[actionSlot])) {
                    throw new IllegalStateException("action slot " + actionSlot + " not applied correctly (expected \""
                            + expected + "\" got \"" + after.actions[actionSlot] + "\") -- ABORTING");
                }
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
