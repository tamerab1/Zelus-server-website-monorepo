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

// Clones a source item's FULL raw opcode-encoded definition bytes onto a target item's
// existing slot, verbatim. An item's own id is never encoded inside its own opcode stream
// (id comes purely from which FileData slot the bytes live in -- confirmed by reading
// ItemLoader.load()/decodeValues()), so this is a safe, exact clone: wearPos1/2/3,
// maleModel0-2/femaleModel0-2 (+offsets), interfaceOptions, isTradeable, cost, boughtId,
// placeholderId, params -- literally everything -- ends up byte-for-byte identical between
// source and target. Used to fix ground-display-proxy items (see GROUND_DISPLAY_PROXY_ID
// in GroundItem.java) that were only ever cloned from a generic template with a matching
// inventoryModel (op1), never given real wear/appearance opcodes, and so showed up
// unwearable / bond-flavoured (Redeem/Deposit/Drop, tradeable, cost=2000000) instead of
// matching the real item they're meant to be a second copy of.
//
// Usage: <cachePath> <sourceId> <targetId>[,<targetId>...]
public class CloneItemDefBytes {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        int sourceId = Integer.parseInt(args[1]);
        String[] targetIds = args[2].split(",");

        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Index index = store.getIndex(IndexType.CONFIGS);
            Archive archive = index.getArchive(ConfigType.ITEM.getId());

            byte[] compressed = storage.loadArchive(archive);
            byte[] decompressed = archive.decompress(compressed);
            FileData[] fileData = archive.getFileData();
            List<byte[]> fileContents = SpliceItemOption.splitChunks(decompressed, fileData.length);

            int sourceSlot = -1;
            for (int i = 0; i < fileData.length; i++) {
                if (fileData[i].getId() == sourceId) sourceSlot = i;
            }
            if (sourceSlot == -1) {
                System.out.println("source id " + sourceId + " not found -- aborting, nothing changed");
                return;
            }
            byte[] sourceBytes = fileContents.get(sourceSlot);
            ItemLoader loader = new ItemLoader();
            ItemDefinition sourceDef = loader.load(sourceId, sourceBytes);
            System.out.println("SOURCE " + sourceId + " \"" + sourceDef.name + "\": wearPos=" + sourceDef.wearPos1
                    + "," + sourceDef.wearPos2 + "," + sourceDef.wearPos3
                    + " maleModel0=" + sourceDef.maleModel0 + " tradeable=" + sourceDef.isTradeable
                    + " cost=" + sourceDef.cost);

            int updated = 0;
            for (String targetIdStr : targetIds) {
                int targetId = Integer.parseInt(targetIdStr.trim());
                int targetSlot = -1;
                for (int i = 0; i < fileData.length; i++) {
                    if (fileData[i].getId() == targetId) targetSlot = i;
                }
                if (targetSlot == -1) {
                    System.out.println("target id " + targetId + " not found -- skipping");
                    continue;
                }
                fileContents.set(targetSlot, sourceBytes.clone());
                ItemDefinition after = loader.load(targetId, fileContents.get(targetSlot));
                System.out.println("  -> " + targetId + " now: name=\"" + after.name + "\" wearPos="
                        + after.wearPos1 + "," + after.wearPos2 + "," + after.wearPos3
                        + " maleModel0=" + after.maleModel0 + " tradeable=" + after.isTradeable
                        + " cost=" + after.cost);
                updated++;
            }

            if (updated == 0) {
                System.out.println("No targets updated -- not writing archive.");
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

            System.out.println("Cloned " + sourceId + " onto " + updated + " target(s). Archive revision now "
                    + archive.getRevision() + ".");
        }
    }
}
