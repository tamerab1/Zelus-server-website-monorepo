import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;
import net.runelite.cache.io.InputStream;

import java.io.File;
import java.util.List;

public class RawOpcodeWalk {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Index index = store.getIndex(IndexType.CONFIGS);
            Archive archive = index.getArchive(ConfigType.ITEM.getId());

            byte[] compressed = storage.loadArchive(archive);
            byte[] decompressed = archive.decompress(compressed);
            FileData[] fileData = archive.getFileData();
            List<byte[]> fileContents = SpliceItemOption.splitChunks(decompressed, fileData.length);

            for (int i = 1; i < args.length; i++) {
                int targetId = Integer.parseInt(args[i]);
                int slot = -1;
                for (int j = 0; j < fileData.length; j++) {
                    if (fileData[j].getId() == targetId) slot = j;
                }
                if (slot == -1) {
                    System.out.println(targetId + ": NOT FOUND");
                    continue;
                }
                byte[] raw = fileContents.get(slot);
                System.out.println("=== " + targetId + " raw opcode walk (len=" + raw.length + ") ===");
                InputStream in = new InputStream(raw);
                try {
                    while (true) {
                        int offset = in.getOffset();
                        int opcode = in.readUnsignedByte();
                        if (opcode == 0) {
                            System.out.println("  [terminator at offset " + offset + "]");
                            break;
                        }
                        Object val = readOpcode(in, opcode);
                        System.out.println("  opcode=" + opcode + " offset=" + offset + " value=" + val);
                    }
                } catch (Exception e) {
                    System.out.println("  !! WALK FAILED: " + e);
                }
            }
        }
    }

    static Object readOpcode(InputStream in, int opcode) {
        switch (opcode) {
            case 1: return in.readUnsignedShort();
            case 2: return in.readString();
            case 3: return in.readString();
            case 4: return in.readUnsignedShort();
            case 5: return in.readUnsignedShort();
            case 6: return in.readUnsignedShort();
            case 7: return in.readUnsignedShort();
            case 8: return in.readUnsignedShort();
            case 9: return in.readString();
            case 11: return "(stackable flag)";
            case 12: return in.readInt();
            case 13: return (int) in.readByte();
            case 14: return (int) in.readByte();
            case 16: return "(members flag)";
            case 23: return in.readUnsignedShort() + "/" + in.readUnsignedByte();
            case 24: return in.readUnsignedShort();
            case 25: return in.readUnsignedShort() + "/" + in.readUnsignedByte();
            case 26: return in.readUnsignedShort();
            case 27: return (int) in.readByte();
            case 42: return (int) in.readByte();
            case 65: return "(GE flag)";
            case 75: return in.readShort();
            case 78: return in.readUnsignedShort();
            case 79: return in.readUnsignedShort();
            case 90: return in.readUnsignedShort();
            case 91: return in.readUnsignedShort();
            case 92: return in.readUnsignedShort();
            case 93: return in.readUnsignedShort();
            case 94: return in.readUnsignedShort();
            case 95: return in.readUnsignedShort();
            case 97: return in.readUnsignedShort();
            case 98: return in.readUnsignedShort();
            case 110: return in.readUnsignedShort();
            case 111: return in.readUnsignedShort();
            case 112: return in.readUnsignedShort();
            case 113: return (int) in.readByte();
            case 114: return (int) in.readByte();
            case 115: return in.readUnsignedByte();
            case 139: return in.readUnsignedShort();
            case 140: return in.readUnsignedShort();
            case 148: return in.readUnsignedShort();
            case 149: return in.readUnsignedShort();
            default:
                if (opcode >= 30 && opcode < 35) return in.readString();
                if (opcode >= 35 && opcode < 40) return in.readString();
                if (opcode == 40 || opcode == 41) {
                    int n = in.readUnsignedByte();
                    StringBuilder sb = new StringBuilder("count=" + n + " [");
                    for (int i = 0; i < n; i++) {
                        sb.append(in.readUnsignedShort()).append("->").append(in.readUnsignedShort()).append(" ");
                    }
                    return sb.append("]").toString();
                }
                if (opcode >= 100 && opcode < 110) return in.readUnsignedShort() + "/" + in.readUnsignedShort();
                if (opcode == 249) {
                    int n = in.readUnsignedByte();
                    StringBuilder sb = new StringBuilder("count=" + n + " [");
                    for (int i = 0; i < n; i++) {
                        boolean isString = in.readUnsignedByte() == 1;
                        int key = (in.readUnsignedByte() << 16) | (in.readUnsignedByte() << 8) | in.readUnsignedByte();
                        Object v = isString ? in.readString() : in.readInt();
                        sb.append(key).append("=").append(v).append(" ");
                    }
                    return sb.append("]").toString();
                }
                throw new RuntimeException("!!! UNKNOWN OPCODE " + opcode + " - not in our decoder's table !!!");
        }
    }
}
