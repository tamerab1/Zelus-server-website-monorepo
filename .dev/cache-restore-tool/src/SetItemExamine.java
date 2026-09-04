import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.ItemDefinition;
import net.runelite.cache.definitions.loaders.ItemLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Container;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;
import net.runelite.cache.index.IndexData;

import java.io.File;
import java.util.List;

// Sets opcode 3 (examine text) on an EXISTING item def in place -- the client renders Examine
// text directly from this cached string with no server round-trip, so this is the only way to
// make a pet's Examine option show its perk description.
// Usage: <cachePath> <itemId> <examineText>
public class SetItemExamine {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        int targetId = Integer.parseInt(args[1]);
        String examine = args[2];

        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Index index = store.getIndex(IndexType.CONFIGS);
            Archive archive = index.getArchive(ConfigType.ITEM.getId());

            byte[] compressed = storage.loadArchive(archive);
            byte[] decompressed = archive.decompress(compressed);
            FileData[] fileData = archive.getFileData();
            List<byte[]> fileContents = SpliceItemOption.splitChunks(decompressed, fileData.length);

            byte[] textBytes = (examine + "\0").getBytes(java.nio.charset.StandardCharsets.US_ASCII);

            int updated = 0;
            ItemLoader loader = new ItemLoader();
            for (int i = 0; i < fileData.length; i++) {
                if (fileData[i].getId() != targetId) continue;
                byte[] work = fileContents.get(i);

                ItemDefinition before = loader.load(targetId, work);
                work = SpliceNewPetItem.replaceOrInsertItemOpcode(work, 3, textBytes);
                ItemDefinition after = loader.load(targetId, work);

                System.out.println("BEFORE: name=\"" + before.name + "\" examine=\"" + before.examine + "\"");
                System.out.println("AFTER:  name=\"" + after.name + "\" examine=\"" + after.examine + "\"");
                if (!examine.equals(after.examine)) {
                    throw new IllegalStateException("examine not applied correctly -- ABORTING, nothing written");
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

            System.out.println("Updated " + updated + " occurrence(s) of item id " + targetId
                    + ". Archive revision now " + archive.getRevision() + ".");
        }
    }
}
