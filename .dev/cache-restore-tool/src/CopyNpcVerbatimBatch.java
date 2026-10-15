import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Container;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;
import net.runelite.cache.index.IndexData;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

// Copies one or more existing npc def records to brand-new ids, byte-for-byte verbatim (no name/
// recolor/anything touched) -- used to move the Draco/Baby Draco family off ids >65535, which
// silently break the client's NpcInfo wire encoding (14-bit clamp on first spawn, 16-bit
// Transformation wraparound on npc.transform() phase changes -- see NpcTransformationEncoder.kt's
// `id: UShort`). The source records at their old (broken) ids are left in place, untouched.
//
// Usage: verify|apply <cachePath> <srcId1:newId1,srcId2:newId2,...>
public class CopyNpcVerbatimBatch {
    public static void main(String[] args) throws Exception {
        String mode = args[0];
        String cachePath = args[1];
        String[] pairs = args[2].split(",");

        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Index index = store.getIndex(IndexType.CONFIGS);
            Archive archive = index.getArchive(ConfigType.NPC.getId());

            byte[] compressed = storage.loadArchive(archive);
            byte[] decompressed = archive.decompress(compressed);
            FileData[] fileData = archive.getFileData();
            List<byte[]> fileContents = new ArrayList<>(SpliceItemOption.splitChunks(decompressed, fileData.length));
            List<Integer> idsInOrder = new ArrayList<>();
            List<Integer> nameHashesInOrder = new ArrayList<>();
            for (FileData fd : fileData) {
                idsInOrder.add(fd.getId());
                nameHashesInOrder.add(fd.getNameHash());
            }

            for (String pair : pairs) {
                String[] parts = pair.split(":");
                int srcId = Integer.parseInt(parts[0].trim());
                int newId = Integer.parseInt(parts[1].trim());

                if (idsInOrder.contains(newId)) throw new IllegalStateException("id " + newId + " already exists -- refusing to overwrite.");
                int srcSlot = idsInOrder.indexOf(srcId);
                if (srcSlot == -1) throw new IllegalStateException("source id " + srcId + " not found");

                byte[] raw = fileContents.get(srcSlot);
                int insertAt = idsInOrder.size();
                for (int i = 0; i < idsInOrder.size(); i++) if (idsInOrder.get(i) > newId) { insertAt = i; break; }
                fileContents.add(insertAt, raw);
                idsInOrder.add(insertAt, newId);
                nameHashesInOrder.add(insertAt, -1);
                System.out.println((mode.equals("apply") ? "COPIED" : "WOULD COPY") + " npc " + srcId + " -> " + newId
                        + " (" + raw.length + " bytes) at slot " + insertAt);
            }

            if (mode.equals("verify")) { System.out.println("VERIFY mode -- not writing."); return; }
            if (!mode.equals("apply")) throw new IllegalArgumentException("mode must be verify or apply");

            FileData[] newFileData = new FileData[idsInOrder.size()];
            for (int i = 0; i < idsInOrder.size(); i++) {
                FileData fd = new FileData();
                fd.setId(idsInOrder.get(i));
                fd.setNameHash(nameHashesInOrder.get(i));
                newFileData[i] = fd;
            }
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

            System.out.println("APPLY complete. Archive revision now " + archive.getRevision() + ".");
        }
    }
}
