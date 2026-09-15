import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;
import net.runelite.cache.io.InputStream;

import java.io.File;
import java.util.List;

// Read-only. Dumps the raw param map (opcode 249) key/value pairs for a given item, from an
// arbitrary source cache, using the real modern opcode table -- to see Hallowfell's actual combat
// bonus/param values (if stored there) rather than guessing them.
public class DumpItemParams {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        int targetId = Integer.parseInt(args[1]);

        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Archive archive = store.getIndex(IndexType.CONFIGS).getArchive(ConfigType.ITEM.getId());
            byte[] decompressed = archive.decompress(storage.loadArchive(archive));
            FileData[] fileData = archive.getFileData();
            List<byte[]> contents = SpliceItemOption.splitChunks(decompressed, fileData.length);

            int slot = -1;
            for (int j = 0; j < fileData.length; j++) if (fileData[j].getId() == targetId) slot = j;
            if (slot == -1) { System.out.println("item " + targetId + " NOT FOUND"); return; }
            dump(targetId, contents.get(slot));
        }
    }

    static void dump(int id, byte[] b) {
        InputStream in = new InputStream(b);
        System.out.println("item " + id + " params:");
        while (true) {
            int code = in.readUnsignedByte();
            if (code == 0) break;
            if (code == 249) {
                int length = in.readUnsignedByte();
                for (int i = 0; i < length; i++) {
                    boolean isString = in.readUnsignedByte() == 1;
                    int key = in.read24BitInt();
                    if (isString) {
                        String value = in.readString();
                        System.out.println("  param " + key + " (0x" + Integer.toHexString(key) + ") = \"" + value + "\"");
                    } else {
                        int value = in.readInt();
                        System.out.println("  param " + key + " (0x" + Integer.toHexString(key) + ") = " + value);
                    }
                }
            } else {
                TraceItemOpcodes.skipPayload(in, code);
            }
        }
    }
}
