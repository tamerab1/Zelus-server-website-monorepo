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

// Root cause (2026-09-18): LocType.java's opcode 1 decode skips (does not apply) its payload if
// modelIds is already non-null from an earlier opcode 5 in the same byte stream -- the Zelus Chest
// template (used for Kulyx Box/Prismatic Chest/Suprise) encodes its own model via opcode 5, so our
// added opcode 1 (new model) was silently ignored and the template's own model always rendered.
// This strips opcode 5 entirely so opcode 1 (already correct) is the only model-list opcode left.
// Usage: verify|apply <cachePath> <objectId>
public class StripOpcode5 {
    public static void main(String[] args) throws Exception {
        String mode = args[0];
        String cachePath = args[1];
        int targetId = Integer.parseInt(args[2]);

        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Index index = store.getIndex(IndexType.CONFIGS);
            Archive archive = index.getArchive(ConfigType.OBJECT.getId());

            byte[] decompressed = archive.decompress(storage.loadArchive(archive));
            FileData[] fds = archive.getFileData();
            List<byte[]> fileContents = SpliceItemOption.splitChunks(decompressed, fds.length);

            int slot = -1;
            for (int i = 0; i < fds.length; i++) if (fds[i].getId() == targetId) slot = i;
            if (slot == -1) throw new IllegalStateException("id " + targetId + " not found");

            byte[] work = fileContents.get(slot);
            ObjectLoader loader = new ObjectLoader();
            ObjectDefinition before = loader.load(targetId, work);

            work = SpliceNewObject.removeOpcode(work, 5);

            ObjectDefinition after = loader.load(targetId, work);
            System.out.println("BEFORE: name=\"" + before.getName() + "\" models=" + java.util.Arrays.toString(before.getObjectModels()));
            System.out.println("AFTER:  name=\"" + after.getName() + "\" models=" + java.util.Arrays.toString(after.getObjectModels()));

            if (after.getObjectModels() == null || after.getObjectModels().length != 1)
                throw new IllegalStateException("unexpected model count after strip -- ABORTING");
            if (!before.getName().equals(after.getName())) throw new IllegalStateException("name changed -- ABORTING");

            if (mode.equals("verify")) { System.out.println("VERIFY -- not writing"); return; }
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

            System.out.println("APPLY complete for " + targetId + ". Archive revision now " + archive.getRevision() + ".");
        }
    }
}
