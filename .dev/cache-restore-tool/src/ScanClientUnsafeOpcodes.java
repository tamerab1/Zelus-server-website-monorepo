import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;
import net.runelite.cache.io.InputStream;

import java.io.File;
import java.util.*;

// Read-only. Walks each given object id's Zelus record and reports which opcodes it uses that are
// CONFIRMED ABSENT from the client's own obfuscated LocType decoder (ik.class, method
// "void ai(wt, int, byte)", disassembled from ~/.zelus/.runelite/cache/injected-client.jar) --
// the client only recognizes: 1,5,14,15,17,18,19,21,22,23,27,28,29,30,39,40,41,61,62,65,66,67,68,
// 69,70,71,72,73,74,75,77,78,79,81,82,89,90,92. Anything else desyncs its byte-stream reader
// (no clean "unknown opcode" throw in this build -- it silently misreads, eventually crashing much
// later with an unrelated-looking ArrayIndexOutOfBoundsException, exactly matching tonight's
// ::madangel client crash).
public class ScanClientUnsafeOpcodes {
    static final Set<Integer> CLIENT_SAFE = new HashSet<>(Arrays.asList(
            1, 5, 14, 15, 17, 18, 19, 21, 22, 23, 27, 28, 29, 30, 39, 40, 41,
            61, 62, 65, 66, 67, 68, 69, 70, 71, 72, 73, 74, 75, 77, 78, 79, 81, 82, 89, 90, 92
    ));

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

            int affectedCount = 0;
            Map<Integer, Integer> opcodeFrequency = new TreeMap<>();
            for (int id : ids) {
                int slot = -1;
                for (int j = 0; j < fileData.length; j++) if (fileData[j].getId() == id) slot = j;
                if (slot == -1) { System.out.println(id + " -- NOT FOUND"); continue; }
                Set<Integer> unsafe = scan(contents.get(slot));
                if (!unsafe.isEmpty()) {
                    System.out.println(id + " -- UNSAFE opcodes: " + unsafe);
                    affectedCount++;
                    for (int op : unsafe) opcodeFrequency.merge(op, 1, Integer::sum);
                }
            }
            System.out.println("\n=== SUMMARY ===");
            System.out.println("objects checked: " + ids.length + ", affected: " + affectedCount);
            System.out.println("opcode frequency across affected objects: " + opcodeFrequency);
        }
    }

    static Set<Integer> scan(byte[] b) {
        Set<Integer> unsafe = new TreeSet<>();
        InputStream is = new InputStream(b);
        while (true) {
            int opcode = is.readUnsignedByte();
            if (opcode == 0) break;
            if (!CLIENT_SAFE.contains(opcode)) unsafe.add(opcode);
            skipOne(opcode, is);
        }
        return unsafe;
    }

    // Same full opcode table as DecodeCathedralObjects/patchObjectRefs -- just skips payload bytes
    // without storing anything, since this pass only needs to know WHICH opcodes are present.
    static void skipOne(int opcode, InputStream is) {
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
            is.readUnsignedShort();
            is.readUnsignedShort();
            if (opcode == 92) is.readUnsignedShort();
            int count = is.readUnsignedByte();
            for (int i = 0; i <= count; i++) is.readUnsignedShort();
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
}
