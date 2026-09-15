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
import net.runelite.cache.io.InputStream;
import net.runelite.cache.io.OutputStream;

import java.io.File;
import java.util.List;

// Item-config equivalent of EditNpcRecolorInPlace: replaces an EXISTING item's opcode-40 (colour
// find/replace) block in place -- same id, no new FileData entry.
//
// Usage: verify|apply <cachePath> <itemId> <f1,r1,f2,r2,...>
public class EditItemRecolorInPlace {
    public static void main(String[] args) throws Exception {
        String mode = args[0];
        String cachePath = args[1];
        int itemId = Integer.parseInt(args[2]);
        String[] pairsStr = args[3].split(",");
        short[] find = new short[pairsStr.length / 2];
        short[] replace = new short[pairsStr.length / 2];
        for (int i = 0; i < find.length; i++) {
            find[i] = (short) Integer.parseInt(pairsStr[i * 2].trim());
            replace[i] = (short) Integer.parseInt(pairsStr[i * 2 + 1].trim());
        }

        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Index index = store.getIndex(IndexType.CONFIGS);
            Archive archive = index.getArchive(ConfigType.ITEM.getId());

            byte[] compressed = storage.loadArchive(archive);
            byte[] decompressed = archive.decompress(compressed);
            FileData[] fileData = archive.getFileData();
            List<byte[]> fileContents = SpliceItemOption.splitChunks(decompressed, fileData.length);

            int slot = -1;
            for (int i = 0; i < fileData.length; i++) {
                if (fileData[i].getId() == itemId) slot = i; // last matching slot wins
            }
            if (slot == -1) throw new IllegalStateException("item id " + itemId + " not found");

            byte[] raw = fileContents.get(slot);
            ItemLoader loader = new ItemLoader();
            ItemDefinition before = loader.load(itemId, raw);

            byte[] stripped = stripOpcodes(raw, 40, 41);

            OutputStream block = new OutputStream();
            block.writeByte(40);
            block.writeByte(find.length);
            for (int i = 0; i < find.length; i++) {
                block.writeShort(find[i] & 0xFFFF);
                block.writeShort(replace[i] & 0xFFFF);
            }
            byte[] blockBytes = block.flip();

            int insertAt = findTerminatorOffset(stripped);
            byte[] work = new byte[stripped.length + blockBytes.length];
            System.arraycopy(stripped, 0, work, 0, insertAt);
            System.arraycopy(blockBytes, 0, work, insertAt, blockBytes.length);
            System.arraycopy(stripped, insertAt, work, insertAt + blockBytes.length, stripped.length - insertAt);

            ItemDefinition after = loader.load(itemId, work);
            System.out.println("BEFORE: name=\"" + before.name + "\" colorFind=" + arr(before.colorFind));
            System.out.println("AFTER:  name=\"" + after.name + "\" colorFind=" + arr(after.colorFind)
                    + " colorReplace=" + arr(after.colorReplace));

            if (after.colorFind == null || after.colorFind.length != find.length) {
                throw new IllegalStateException("colorFind not applied correctly -- ABORTING");
            }
            if (!java.util.Objects.equals(before.name, after.name) || before.inventoryModel != after.inventoryModel) {
                throw new IllegalStateException("name/inventoryModel changed unexpectedly -- ABORTING");
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

            System.out.println("APPLY complete for item " + itemId + ". Archive revision now " + archive.getRevision() + ".");
        }
    }

    static String arr(short[] a) {
        return a == null ? "null" : java.util.Arrays.toString(a);
    }

    static byte[] stripOpcodes(byte[] b, int... targetOpcodes) {
        InputStream is = new InputStream(b);
        OutputStream out = new OutputStream();
        while (true) {
            int start = is.getOffset();
            int opcode = is.readUnsignedByte();
            if (opcode == 0) {
                out.writeByte(0);
                break;
            }
            int payloadStart = is.getOffset();
            SpliceItemOption.skipOpcodePayload(opcode, is);
            int end = is.getOffset();
            boolean skip = false;
            for (int t : targetOpcodes) if (t == opcode) skip = true;
            if (!skip) {
                out.writeBytes(java.util.Arrays.copyOfRange(b, start, end));
            }
        }
        return out.flip();
    }

    static int findTerminatorOffset(byte[] b) {
        InputStream is = new InputStream(b);
        while (true) {
            int start = is.getOffset();
            int opcode = is.readUnsignedByte();
            if (opcode == 0) return start;
            SpliceItemOption.skipOpcodePayload(opcode, is);
        }
    }
}
