import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;
import net.runelite.cache.io.InputStream;

import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

// Read-only. Prints the RAW OPCODE SEQUENCE (not the decoded values) for each of the 10 new Mad
// Angel items, from Zelus's own live cache, and flags the first opcode NOT present in Zelus's own
// io.ruin.cache.ObjType#decode(InBuffer,int) -- which, unlike LocType/NPCType, has NO final `else`
// that crashes on an unrecognized opcode. It just silently does nothing and consumes zero bytes,
// which desyncs every field read after that point for that one item -- a silent corruption, not a
// crash, and exactly consistent with "some items decode partially/wrong, nothing logged".
public class TraceItemOpcodes {
    // Every opcode ObjType.java's decode(InBuffer, int) actually recognizes, transcribed 1:1 from
    // reading that method directly this session.
    static final Set<Integer> ZELUS_SUPPORTED = new TreeSet<>(java.util.Arrays.asList(
            1, 2, 3, 4, 5, 6, 7, 8, 9, 11, 12, 13, 14, 16, 23, 24, 25, 26, 27,
            30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 65, 75, 78, 79,
            90, 91, 92, 93, 94, 95, 97, 98,
            100, 101, 102, 103, 104, 105, 106, 107, 108, 109,
            110, 111, 112, 113, 114, 115, 139, 140, 148, 149, 249
    ));

    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        int[] ids = {34027, 34028, 34029, 34030, 34031, 34032, 34033, 34034, 34042, 34043};

        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Archive archive = store.getIndex(IndexType.CONFIGS).getArchive(ConfigType.ITEM.getId());
            byte[] decompressed = archive.decompress(storage.loadArchive(archive));
            FileData[] fileData = archive.getFileData();
            List<byte[]> contents = SpliceItemOption.splitChunks(decompressed, fileData.length);

            for (int id : ids) {
                int slot = -1;
                for (int j = 0; j < fileData.length; j++) if (fileData[j].getId() == id) slot = j;
                if (slot == -1) { System.out.println(id + " -- NOT FOUND"); continue; }
                byte[] raw = contents.get(slot);
                traceOpcodes(id, raw);
            }
        }
    }

    // Uses the REAL modern opcode table (ObjTypeDecoder.kt) to correctly walk the bytes -- same as
    // DecodeMadAngelItemsV2.java -- but this time PRINTING the raw opcode sequence and cross-
    // checking each one against Zelus's own supported set instead of just decoding values.
    static void traceOpcodes(int id, byte[] b) {
        InputStream in = new InputStream(b);
        StringBuilder seq = new StringBuilder();
        int firstUnsupported = -1;
        while (true) {
            int code = in.readUnsignedByte();
            if (code == 0) break;
            boolean supported = ZELUS_SUPPORTED.contains(code);
            seq.append(code).append(supported ? "" : "*").append(" ");
            if (!supported && firstUnsupported == -1) firstUnsupported = code;
            skipPayload(in, code);
        }
        System.out.println("item " + id + " opcodes: " + seq.toString().trim()
                + (firstUnsupported == -1 ? "  [all supported by Zelus's ObjType]"
                : "  <<< FIRST UNSUPPORTED BY ZELUS: opcode " + firstUnsupported + " (marked with *)"));
    }

    /** Same real modern-format skip logic as DecodeMadAngelItemsV2's decoder -- just to correctly
     * advance past each opcode's payload while tracing, using the SOURCE format's own widths. */
    static void skipPayload(InputStream in, int code) {
        switch (code) {
            case 1: in.readUnsignedShort(); break;
            case 2: case 3: case 9: in.readString(); break;
            case 4: case 5: case 6: in.readUnsignedShort(); break;
            case 7: case 8: in.readShort(); break;
            case 11: case 15: case 16: break;
            case 12: in.readInt(); break;
            case 13: case 14: case 27: in.readByte(); break;
            case 23: in.readUnsignedShort(); in.readUnsignedByte(); break;
            case 24: in.readUnsignedShort(); break;
            case 25: in.readUnsignedShort(); in.readUnsignedByte(); break;
            case 26: in.readUnsignedShort(); break;
            case 30: case 31: case 32: case 33: case 34:
            case 35: case 36: case 37: case 38: case 39: in.readString(); break;
            case 40: case 41: {
                int count = in.readUnsignedByte();
                for (int i = 0; i < count; i++) { in.readUnsignedShort(); in.readUnsignedShort(); }
                break;
            }
            case 42: in.readByte(); break;
            case 43: {
                in.readUnsignedByte();
                int subop = in.readUnsignedByte();
                while (subop != 0) { in.readString(); subop = in.readUnsignedByte(); }
                break;
            }
            case 44: in.readInt(); break;
            case 45: in.readInt(); in.readUnsignedByte(); break;
            case 46: case 47: in.readInt(); break;
            case 48: in.readInt(); in.readUnsignedByte(); break;
            case 49: case 50: case 51: case 52: case 53: case 54: in.readInt(); break;
            case 65: case 160: break;
            case 75: in.readShort(); break;
            case 78: case 79: case 90: case 91: case 92: case 93: case 94: case 95: in.readUnsignedShort(); break;
            case 97: case 98: in.readUnsignedShort(); break;
            case 100: case 101: case 102: case 103: case 104:
            case 105: case 106: case 107: case 108: case 109:
                in.readUnsignedShort(); in.readUnsignedShort(); break;
            case 110: case 111: case 112: in.readUnsignedShort(); break;
            case 113: case 114: case 115: in.readByte(); break;
            case 139: case 140: case 148: case 149: in.readUnsignedShort(); break;
            case 200: {
                int count = in.readUnsignedByte();
                for (int i = 0; i < count; i++) in.readUnsignedShort();
                break;
            }
            case 201: case 202: case 203: in.readInt(); break;
            case 204: case 205: in.readUnsignedShort(); break;
            case 206: break;
            case 207: in.readUnsignedShort(); break;
            case 208: in.readByte(); break;
            case 209: in.readUnsignedShort(); break;
            case 210: case 211: in.readUnsignedShort(); break;
            case 212: in.readUnsignedByte(); break;
            case 249: {
                int length = in.readUnsignedByte();
                for (int i = 0; i < length; i++) {
                    boolean isString = in.readUnsignedByte() == 1;
                    in.read24BitInt();
                    if (isString) in.readString(); else in.readInt();
                }
                break;
            }
            default:
                throw new RuntimeException("unrecognized obj opcode " + code);
        }
    }
}
