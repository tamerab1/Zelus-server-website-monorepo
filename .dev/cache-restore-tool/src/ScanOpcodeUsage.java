import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;
import net.runelite.cache.io.InputStream;

import java.io.File;
import java.util.List;
import java.util.TreeMap;

// Read-only. Tallies how many PRE-EXISTING items in Zelus's own live cache use each opcode, using
// the same modern opcode-width table as TraceItemOpcodes/DecodeMadAngelItemsV2. Opcodes that NEVER
// appear on any pre-existing (already-rendering) item are foreign to this cache's classic revision
// -- confirms whether 44-54/15 (the "modern wide model" block) were ever exercised by anything
// besides our own newly-imported Mad Angel items.
public class ScanOpcodeUsage {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Archive archive = store.getIndex(IndexType.CONFIGS).getArchive(ConfigType.ITEM.getId());
            byte[] decompressed = archive.decompress(storage.loadArchive(archive));
            FileData[] fileData = archive.getFileData();
            List<byte[]> contents = SpliceItemOption.splitChunks(decompressed, fileData.length);

            TreeMap<Integer, Integer> counts = new TreeMap<>();
            int skippedForNewItems = 0;
            for (int i = 0; i < fileData.length; i++) {
                int id = fileData[i].getId();
                if (id >= 34027 && id <= 34043) { skippedForNewItems++; continue; }
                tally(contents.get(i), counts);
            }
            System.out.println("(skipped " + skippedForNewItems + " of our own new items)");
            for (int op : new int[]{15, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53, 54, 1}) {
                System.out.println("opcode " + op + ": used by " + counts.getOrDefault(op, 0) + " pre-existing items");
            }
        }
    }

    static void tally(byte[] b, TreeMap<Integer, Integer> counts) {
        InputStream in = new InputStream(b);
        try {
            while (true) {
                int code = in.readUnsignedByte();
                if (code == 0) return;
                counts.merge(code, 1, Integer::sum);
                skip(in, code);
            }
        } catch (Exception e) {
            // stop tallying this item on first unrecognized opcode -- rest already counted is valid
        }
    }

    static void skip(InputStream in, int code) {
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
                throw new RuntimeException("unrecognized " + code);
        }
    }
}
