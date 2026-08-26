import java.io.File;
import java.util.List;

import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Container;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;

// Renames an EXISTING item/npc def in place (same id, no new FileData entry) by replacing its
// opcode-2 name field. Updates every duplicate-id occurrence of that slot (this archive has known
// duplicate file-id entries -- must not stop at the first match, see project memory).
// Usage: <cachePath> <item|npc> <id> <newName>
public class RenameDef {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        String kind = args[1];
        int targetId = Integer.parseInt(args[2]);
        String newName = args[3];
        int configId = kind.equals("item") ? ConfigType.ITEM.getId() : ConfigType.NPC.getId();

        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Index index = store.getIndex(IndexType.CONFIGS);
            Archive archive = index.getArchive(configId);

            byte[] compressed = storage.loadArchive(archive);
            byte[] decompressed = archive.decompress(compressed);
            FileData[] fileData = archive.getFileData();
            List<byte[]> fileContents = SpliceItemOption.splitChunks(decompressed, fileData.length);

            int updated = 0;
            for (int i = 0; i < fileData.length; i++) {
                if (fileData[i].getId() != targetId)
                    continue;
                byte[] raw = fileContents.get(i);
                int[] nameSpan = SpliceRecolorCopy.findOpcode2Span(raw, kind);
                if (nameSpan == null) {
                    System.out.println("slot " + i + " (id " + targetId + ") has no opcode-2 -- skipping");
                    continue;
                }
                byte[] nameBytes = newName.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
                byte[] newNameField = new byte[1 + nameBytes.length + 1];
                newNameField[0] = 2;
                System.arraycopy(nameBytes, 0, newNameField, 1, nameBytes.length);
                newNameField[newNameField.length - 1] = 0;

                byte[] renamed = new byte[raw.length - (nameSpan[1] - nameSpan[0]) + newNameField.length];
                System.arraycopy(raw, 0, renamed, 0, nameSpan[0]);
                System.arraycopy(newNameField, 0, renamed, nameSpan[0], newNameField.length);
                System.arraycopy(raw, nameSpan[1], renamed, nameSpan[0] + newNameField.length,
                        raw.length - nameSpan[1]);
                fileContents.set(i, renamed);
                updated++;
            }

            if (updated == 0) {
                System.out.println("id " + targetId + " not found -- nothing renamed");
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

            net.runelite.cache.index.IndexData indexData = index.toIndexData();
            byte[] rawIndex = indexData.writeIndexData();
            Container idxContainer = new Container(index.getCompression(), -1);
            idxContainer.compress(rawIndex, null);
            storage.store(255, index.getId(), idxContainer.data);
            index.setCrc(idxContainer.crc);

            System.out.println("Renamed " + updated + " occurrence(s) of " + kind + " id " + targetId + " to \"" + newName + "\". Archive revision now " + archive.getRevision() + ".");
        }
    }
}
