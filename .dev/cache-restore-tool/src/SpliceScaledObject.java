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
import java.util.Arrays;
import java.util.List;

// Clones an object with its own model list, renamed, with one action in slot 0 and a uniform model scale
// (opcodes 65/66/67 = modelSizeX / height / modelSizeY, 128 = 100%). Everything else in the template record is
// kept (size, clipping, animation...). Sorted insert, like SpliceNewObject. Written for the Rivals Challenge Bell
// (2026-10-17): The Cloister Bell's mesh is 545 tall / 507 wide on a 1x1 object, so it swallowed the lobby.
// Usage: verify|apply <cachePath> <sourceObjectId> <newId> <newName> <action> <scale>
public class SpliceScaledObject {
    public static void main(String[] args) throws Exception {
        String mode = args[0];
        int sourceId = Integer.parseInt(args[2]), newId = Integer.parseInt(args[3]);
        String newName = args[4], action = args[5];
        int scale = Integer.parseInt(args[6]);
        if (newId > 65535) throw new IllegalArgumentException("spawned object ids must fit 16 bits");
        try (Store store = new Store(new File(args[1]))) {
            store.load();
            Storage storage = store.getStorage();
            Index index = store.getIndex(IndexType.CONFIGS);
            Archive archive = index.getArchive(ConfigType.OBJECT.getId());
            byte[] decompressed = archive.decompress(storage.loadArchive(archive));
            FileData[] fileData = archive.getFileData();
            List<byte[]> fileContents = SpliceItemOption.splitChunks(decompressed, fileData.length);
            int sourceSlot = -1;
            for (int i = 0; i < fileData.length; i++) {
                if (fileData[i].getId() == newId) throw new IllegalStateException("id " + newId + " already exists");
                if (fileData[i].getId() == sourceId) sourceSlot = i;
            }
            if (sourceSlot == -1) throw new IllegalStateException("source id " + sourceId + " not found");

            byte[] templateRaw = fileContents.get(sourceSlot);
            ObjectLoader loader = new ObjectLoader();
            ObjectDefinition before = loader.load(sourceId, templateRaw);
            byte[] work = templateRaw;
            for (int op = 34; op >= 30; op--) work = SpliceNewObject.removeOpcode(work, op);
            work = SpliceNewObject.replaceOpcode(work, 30, SpliceNewObject.withTerminator(action));
            work = SpliceNewObject.replaceOpcode(work, 2, SpliceNewObject.withTerminator(newName));
            byte[] s = {(byte) (scale >> 8), (byte) scale};
            for (int op = 65; op <= 67; op++) work = SpliceNewObject.replaceOpcode(work, op, s);

            ObjectDefinition after = loader.load(newId, work);
            System.out.println("BEFORE " + sourceId + ": \"" + before.getName() + "\" models=" + Arrays.toString(before.getObjectModels())
                + " actions=" + Arrays.toString(before.getActions()) + " size=" + before.getSizeX() + "x" + before.getSizeY()
                + " scale=" + before.getModelSizeX() + "/" + before.getModelSizeHeight() + "/" + before.getModelSizeY());
            System.out.println("AFTER  " + newId + ": \"" + after.getName() + "\" models=" + Arrays.toString(after.getObjectModels())
                + " actions=" + Arrays.toString(after.getActions()) + " size=" + after.getSizeX() + "x" + after.getSizeY()
                + " scale=" + after.getModelSizeX() + "/" + after.getModelSizeHeight() + "/" + after.getModelSizeY());
            if (!newName.equals(after.getName()) || !action.equals(after.getActions()[0])
                || after.getModelSizeX() != scale || after.getModelSizeHeight() != scale || after.getModelSizeY() != scale
                || !Arrays.equals(before.getObjectModels(), after.getObjectModels())
                || before.getSizeX() != after.getSizeX() || before.getSizeY() != after.getSizeY())
                throw new IllegalStateException("re-decode mismatch -- ABORTING");
            if (mode.equals("verify")) {
                System.out.println("VERIFY OK -- nothing written.");
                return;
            }
            if (!mode.equals("apply")) throw new IllegalArgumentException("mode must be verify or apply");

            int insertAt = fileData.length;
            for (int i = 0; i < fileData.length; i++)
                if (fileData[i].getId() > newId) { insertAt = i; break; }
            fileContents.add(insertAt, work);
            FileData[] nfd = new FileData[fileData.length + 1];
            System.arraycopy(fileData, 0, nfd, 0, insertAt);
            FileData e = new FileData();
            e.setId(newId);
            e.setNameHash(-1);
            nfd[insertAt] = e;
            System.arraycopy(fileData, insertAt, nfd, insertAt + 1, fileData.length - insertAt);
            archive.setFileData(nfd);
            byte[] joined = SpliceItemOption.joinChunks(fileContents);
            Container c = new Container(archive.getCompression(), -1);
            c.compress(joined, null);
            storage.store(index.getId(), archive.getArchiveId(), c.data);
            archive.setCrc(c.crc);
            archive.setRevision(archive.getRevision() + 1);
            archive.setCompressedSize(c.data.length);
            archive.setDecompressedSize(joined.length);
            IndexData indexData = index.toIndexData();
            Container ic = new Container(index.getCompression(), -1);
            ic.compress(indexData.writeIndexData(), null);
            storage.store(255, index.getId(), ic.data);
            index.setCrc(ic.crc);
            System.out.println("APPLY complete -- object " + newId + " added.");
        }
    }
}
