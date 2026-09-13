import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;
import net.runelite.cache.io.InputStream;

import java.io.File;
import java.util.*;

// Read-only. Checks whether any of the given object ids use opcode 77/92 (multi-loc / varbit
// variant lists) -- if so, those referenced sibling loc ids also need to exist, same crash class
// as the missing models did.
public class CheckMultiLocRefs {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        int[] ids = new int[args.length - 1];
        for (int i = 1; i < args.length; i++) ids[i - 1] = Integer.parseInt(args[i]);

        try (Store store = new Store(new File(cachePath))) {
            store.load();
            var storage = store.getStorage();
            var configs = store.getIndex(IndexType.CONFIGS);
            Archive archive = configs.getArchive(ConfigType.OBJECT.getId());
            byte[] decompressed = archive.decompress(storage.loadArchive(archive));
            FileData[] fileData = archive.getFileData();
            List<byte[]> contents = SpliceItemOption.splitChunks(decompressed, fileData.length);

            boolean anyFound = false;
            for (int id : ids) {
                int slot = -1;
                for (int j = 0; j < fileData.length; j++) if (fileData[j].getId() == id) slot = j;
                if (slot == -1) continue;
                List<Integer> multiLocs = scan(contents.get(slot));
                if (!multiLocs.isEmpty()) {
                    System.out.println("object " + id + " references multiLoc/varbit ids: " + multiLocs);
                    anyFound = true;
                }
            }
            if (!anyFound) System.out.println("none of the checked objects use opcode 77/92 (no multi-loc refs).");
        }
    }

    static List<Integer> scan(byte[] b) {
        List<Integer> found = new ArrayList<>();
        InputStream is = new InputStream(b);
        while (true) {
            int opcode = is.readUnsignedByte();
            if (opcode == 0) break;
            if (opcode == 1) {
                int count = is.readUnsignedByte();
                for (int i = 0; i < count; i++) { is.readUnsignedShort(); is.readUnsignedByte(); }
            } else if (opcode == 2 || opcode == 3) {
                is.readString();
            } else if (opcode == 5) {
                int count = is.readUnsignedByte();
                for (int i = 0; i < count; i++) is.readUnsignedShort();
            } else if (opcode == 6) {
                int count = is.readUnsignedByte();
                for (int i = 0; i < count; i++) { is.readInt(); is.readUnsignedByte(); }
            } else if (opcode == 7) {
                int count = is.readUnsignedByte();
                for (int i = 0; i < count; i++) is.readInt();
            } else if (opcode == 14 || opcode == 15) {
                is.readUnsignedByte();
            } else if (opcode == 17 || opcode == 18) {
            } else if (opcode == 19) {
                is.readUnsignedByte();
            } else if (opcode == 21 || opcode == 22 || opcode == 23) {
            } else if (opcode == 24) {
                is.readUnsignedShort();
            } else if (opcode == 27) {
            } else if (opcode == 28) {
                is.readUnsignedByte();
            } else if (opcode == 29) {
                is.readByte();
            } else if (opcode >= 30 && opcode < 35) {
                is.readString();
            } else if (opcode == 39) {
                is.readByte();
            } else if (opcode == 40 || opcode == 41) {
                int count = is.readUnsignedByte();
                for (int i = 0; i < count; i++) { is.readUnsignedShort(); is.readUnsignedShort(); }
            } else if (opcode == 61) {
                is.readUnsignedShort();
            } else if (opcode == 62 || opcode == 64) {
            } else if (opcode == 65 || opcode == 66 || opcode == 67) {
                is.readUnsignedShort();
            } else if (opcode == 68) {
                is.readUnsignedShort();
            } else if (opcode == 69) {
                is.readUnsignedByte();
            } else if (opcode == 70 || opcode == 71 || opcode == 72) {
                is.readShort();
            } else if (opcode == 73 || opcode == 74) {
            } else if (opcode == 75) {
                is.readUnsignedByte();
            } else if (opcode == 77 || opcode == 92) {
                readNullable(is);
                readNullable(is);
                if (opcode == 92) {
                    Integer defLoc = readNullable(is);
                    if (defLoc != null) found.add(defLoc);
                }
                int count = is.readUnsignedByte();
                for (int i = 0; i <= count; i++) {
                    Integer loc = readNullable(is);
                    if (loc != null) found.add(loc);
                }
            } else if (opcode == 78) {
                is.readUnsignedShort(); is.readUnsignedByte(); is.readUnsignedByte();
            } else if (opcode == 79) {
                is.readUnsignedShort(); is.readUnsignedShort(); is.readUnsignedByte(); is.readUnsignedByte();
                int count = is.readUnsignedByte();
                for (int i = 0; i < count; i++) is.readUnsignedShort();
            } else if (opcode == 81) {
                is.readUnsignedByte();
            } else if (opcode == 82) {
                is.readUnsignedShort();
            } else if (opcode == 89 || opcode == 90) {
            } else if (opcode == 91) {
                is.readUnsignedByte();
            } else if (opcode == 93) {
                is.readUnsignedByte(); is.readUnsignedShort(); is.readUnsignedByte(); is.readUnsignedShort();
            } else if (opcode == 94) {
            } else if (opcode == 95) {
                is.readUnsignedByte();
            } else if (opcode == 96) {
                is.readUnsignedByte();
            } else if (opcode == 100) {
                is.readUnsignedByte(); is.readUnsignedByte(); is.readString();
            } else if (opcode == 101) {
                is.readUnsignedByte(); is.readUnsignedShort(); is.readUnsignedShort(); is.readInt(); is.readInt(); is.readString();
            } else if (opcode == 102) {
                is.readUnsignedByte(); is.readUnsignedShort(); is.readUnsignedShort(); is.readUnsignedShort(); is.readInt(); is.readInt(); is.readString();
            } else if (opcode == 200) {
                is.readUnsignedShort();
            } else if (opcode == 249) {
                int length = is.readUnsignedByte();
                for (int i = 0; i < length; i++) {
                    boolean isString = is.readUnsignedByte() == 1;
                    is.read24BitInt();
                    if (isString) is.readString(); else is.readInt();
                }
            } else {
                throw new RuntimeException("unrecognized opcode " + opcode);
            }
        }
        return found;
    }

    static Integer readNullable(InputStream is) {
        int v = is.readUnsignedShort();
        return v == 0xFFFF ? null : v;
    }
}
