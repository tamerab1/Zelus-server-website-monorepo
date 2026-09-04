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

// Edits icon-framing opcodes on an EXISTING item def in place (same id, no new FileData entry).
// SpliceNewPetItem's framing override never touched opcode 7/8 (x/y icon offset) -- items created
// through it silently kept the TEMPLATE item's own offset, which is wrong for any model with
// different proportions (e.g. inherited Nox's yOffset=-55, cropping full-body humanoid models to
// legs-only or pushing small models off-frame entirely). This tool lets every framing opcode be
// retuned independently once the real id already exists.
//
// Usage: <cachePath> <itemId> <zoom2d> <xan2d> <yan2d> <resizeX> <resizeY> <resizeZ> <xOffset> <yOffset>
// Pass "-" for any single value to leave that opcode untouched.
public class EditItemInPlace {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        int targetId = Integer.parseInt(args[1]);

        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Index index = store.getIndex(IndexType.CONFIGS);
            Archive archive = index.getArchive(ConfigType.ITEM.getId());

            byte[] compressed = storage.loadArchive(archive);
            byte[] decompressed = archive.decompress(compressed);
            FileData[] fileData = archive.getFileData();
            List<byte[]> fileContents = SpliceItemOption.splitChunks(decompressed, fileData.length);

            int[][] ops = {
                {4, parseArg(args, 2)}, {5, parseArg(args, 3)}, {6, parseArg(args, 4)},
                {110, parseArg(args, 5)}, {111, parseArg(args, 6)}, {112, parseArg(args, 7)},
                {7, parseArg(args, 8)}, {8, parseArg(args, 9)}, {1, parseArg(args, 10)},
            };

            int updated = 0;
            ItemLoader loader = new ItemLoader();
            for (int i = 0; i < fileData.length; i++) {
                if (fileData[i].getId() != targetId) continue;
                byte[] work = fileContents.get(i);

                ItemDefinition before = loader.load(targetId, work);

                for (int[] op : ops) {
                    if (op[1] == Integer.MIN_VALUE) continue; // "-" sentinel, skip
                    work = SpliceNewPetItem.replaceOrInsertItemOpcode(work, op[0], signedShort(op[1]));
                }

                ItemDefinition after = loader.load(targetId, work);
                System.out.println("BEFORE: name=\"" + before.name + "\" inventoryModel=" + before.inventoryModel
                        + " zoom2d=" + before.zoom2d
                        + " xan2d=" + before.xan2d + " yan2d=" + before.yan2d
                        + " resize=" + before.resizeX + "," + before.resizeY + "," + before.resizeZ
                        + " xOffset=" + before.xOffset2d + " yOffset=" + before.yOffset2d);
                System.out.println("AFTER:  name=\"" + after.name + "\" inventoryModel=" + after.inventoryModel
                        + " zoom2d=" + after.zoom2d
                        + " xan2d=" + after.xan2d + " yan2d=" + after.yan2d
                        + " resize=" + after.resizeX + "," + after.resizeY + "," + after.resizeZ
                        + " xOffset=" + after.xOffset2d + " yOffset=" + after.yOffset2d);

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

    static int parseArg(String[] args, int idx) {
        if (idx >= args.length || args[idx].equals("-")) return Integer.MIN_VALUE;
        return Integer.parseInt(args[idx]);
    }

    static byte[] signedShort(int value) {
        return new byte[]{(byte) ((value >> 8) & 0xFF), (byte) (value & 0xFF)};
    }
}
