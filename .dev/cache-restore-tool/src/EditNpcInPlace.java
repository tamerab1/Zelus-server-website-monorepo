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

// Edits an EXISTING npc def in place (same id, no new FileData entry) -- sets widthScale/
// heightScale (opcode 97/98) and, optionally, strips an opcode entirely (e.g. opcode 103
// "rotation", a fixed-facing override that a few source NPCs carry and that overrides the
// generic pet-follow facing logic, making the pet always render facing one fixed direction
// while idle regardless of the player's position).
//
// Usage: <cachePath> <npcId> <widthScale> <heightScale> [stripOpcode|-]
public class EditNpcInPlace {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        int targetId = Integer.parseInt(args[1]);
        int widthScale = Integer.parseInt(args[2]);
        int heightScale = Integer.parseInt(args[3]);
        int[] stripOpcodes = args.length > 4 && !args[4].equals("-")
                ? java.util.Arrays.stream(args[4].split(",")).mapToInt(Integer::parseInt).toArray()
                : new int[0];
        int combatLevel = args.length > 5 && !args[5].equals("-") ? Integer.parseInt(args[5]) : -1;

        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Index index = store.getIndex(IndexType.CONFIGS);
            Archive archive = index.getArchive(ConfigType.NPC.getId());

            byte[] compressed = storage.loadArchive(archive);
            byte[] decompressed = archive.decompress(compressed);
            FileData[] fileData = archive.getFileData();
            List<byte[]> fileContents = SpliceItemOption.splitChunks(decompressed, fileData.length);

            int updated = 0;
            NpcLoader loader = new NpcLoader();
            for (int i = 0; i < fileData.length; i++) {
                if (fileData[i].getId() != targetId) continue;
                byte[] work = fileContents.get(i);

                NpcDefinition before = loader.load(targetId, work);

                work = SplicePetify.replaceOrInsert(work, 97, SplicePetify.twoBytes(widthScale));
                work = SplicePetify.replaceOrInsert(work, 98, SplicePetify.twoBytes(heightScale));

                for (int op : stripOpcodes) {
                    work = removeOpcode(work, op);
                }

                if (combatLevel >= 0) {
                    work = SplicePetify.replaceOrInsert(work, 95, SplicePetify.twoBytes(combatLevel));
                }

                NpcDefinition after = loader.load(targetId, work);
                System.out.println("slot " + i + " BEFORE: name=\"" + before.name + "\" widthScale=" + before.widthScale
                        + " heightScale=" + before.heightScale + " combatLevel=" + before.combatLevel);
                System.out.println("slot " + i + " AFTER:  name=\"" + after.name + "\" widthScale=" + after.widthScale
                        + " heightScale=" + after.heightScale + " combatLevel=" + after.combatLevel);

                if (after.widthScale != widthScale || after.heightScale != heightScale) {
                    throw new IllegalStateException("scale not applied correctly -- ABORTING, nothing written");
                }
                if (combatLevel >= 0 && after.combatLevel != combatLevel) {
                    throw new IllegalStateException("combatLevel not applied correctly -- ABORTING, nothing written");
                }

                fileContents.set(i, work);
                updated++;
            }

            if (updated == 0) {
                System.out.println("id " + targetId + " not found -- nothing changed");
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

            System.out.println("Updated " + updated + " occurrence(s) of npc id " + targetId
                    + ". Archive revision now " + archive.getRevision() + ".");
        }
    }

    // Cuts the given opcode's [opcodeByte, payload) span out of the byte stream entirely
    // (unlike replaceOrInsert, which swaps the payload -- here we want the opcode to be
    // absent altogether, matching how NPCs that never had it look to the loader).
    static byte[] removeOpcode(byte[] b, int targetOpcode) {
        int[] span = SplicePetify.findOpcodeSpan(b, targetOpcode);
        if (span == null) {
            System.out.println("  (opcode " + targetOpcode + " not present -- nothing to strip)");
            return b;
        }
        byte[] out = new byte[b.length - (span[1] - span[0])];
        System.arraycopy(b, 0, out, 0, span[0]);
        System.arraycopy(b, span[1], out, span[0], b.length - span[1]);
        return out;
    }
}
