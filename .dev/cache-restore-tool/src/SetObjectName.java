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

public class SetObjectName {
    public static void main(String[] args) throws Exception {
        String mode = args[0];
        String cachePath = args[1];
        int objId = Integer.parseInt(args[2]);
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

            int slotIndex = -1;
            for (int i = 0; i < fileData.length; i++) if (fileData[i].getId() == objId) { slotIndex = i; break; }
            if (slotIndex == -1) throw new IllegalStateException("object id " + objId + " not found");

            byte[] raw = fileContents.get(slotIndex);
            ObjectLoader loader = new ObjectLoader();
            ObjectDefinition before = loader.load(objId, raw);
            System.out.println("BEFORE: name=\"" + before.getName() + "\"");

            byte[] work = SpliceNewObject.replaceOpcode(raw, 2, SpliceNewObject.withTerminator(newName));
            ObjectDefinition after = loader.load(objId, work);
            System.out.println("AFTER:  name=\"" + after.getName() + "\"");

            if (!newName.equals(after.getName())) throw new IllegalStateException("name not applied -- ABORTING");
            if (!java.util.Arrays.equals(before.getObjectModels(), after.getObjectModels()))
                throw new IllegalStateException("models changed unexpectedly -- ABORTING");
            if (!java.util.Arrays.equals(before.getActions(), after.getActions()))
                throw new IllegalStateException("actions changed unexpectedly -- ABORTING");
            if (!java.util.Arrays.equals(before.getRecolorToFind(), after.getRecolorToFind()))
                throw new IllegalStateException("recolor changed unexpectedly -- ABORTING");

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

            System.out.println("APPLY complete. Object " + objId + " renamed. Archive revision now " + archive.getRevision() + ".");
        }
    }
}
