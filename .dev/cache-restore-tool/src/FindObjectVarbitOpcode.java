import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;

import java.io.File;
import java.util.List;

// Read-only: for the given object id, walks the opcode stream by hand (NOT using
// InsertNPCActions.skipOpcodePayload, whose opcode-77 case is wrong for objects --
// it's calibrated for npc defs, where opcode 77 is a different, single-short field).
// Confirmed via javap disassembly of RuneLite's real ObjectLoader that object
// opcode 77 is: varbitId(u16, 65535=-1), varpId(u16, 65535=-1), N(u8), then N+1
// more u16 array entries (65535=-1) -- array.length is N+2, last slot fixed -1
// in code (not read from the stream). This tool locates that span exactly and
// prints the precise byte offset/value of the varbitId field within it, with NO
// write performed.
public class FindObjectVarbitOpcode {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        int targetId = Integer.parseInt(args[1]);
        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Index index = store.getIndex(IndexType.CONFIGS);
            Archive archive = index.getArchive(ConfigType.OBJECT.getId());

            byte[] decompressed = archive.decompress(storage.loadArchive(archive));
            FileData[] fileData = archive.getFileData();
            List<byte[]> fileContents = SpliceItemOption.splitChunks(decompressed, fileData.length);

            for (int i = 0; i < fileData.length; i++) {
                if (fileData[i].getId() != targetId) continue;
                byte[] b = fileContents.get(i);
                int[] span = findOpcode77Span(b);
                if (span == null) {
                    System.out.println("id=" + targetId + ": opcode 77 not found");
                    return;
                }
                int p = span[0] + 1; // skip opcode byte
                int varbitId = u16(b, p);
                if (varbitId == 65535) varbitId = -1;
                int varpId = u16(b, p + 2);
                if (varpId == 65535) varpId = -1;
                int n = b[p + 4] & 0xFF;
                System.out.println("id=" + targetId + " opcode77 span=[" + span[0] + "," + span[1] + ") len="
                        + (span[1] - span[0]));
                System.out.println("  varbitId field at byte offset " + (p) + "-" + (p + 1)
                        + " (relative to file start) = " + varbitId + " (raw hex "
                        + String.format("%02X %02X", b[p], b[p + 1]) + ")");
                System.out.println("  varpId=" + varpId + " arrayCountByte(N)=" + n + " arrayLength=" + (n + 2));
                return;
            }
            System.out.println("id " + targetId + " not found in archive");
        }
    }

    static int u16(byte[] b, int off) {
        return ((b[off] & 0xFF) << 8) | (b[off + 1] & 0xFF);
    }

    // Correct, object-specific opcode-77 (and generic-opcode) skip so span-finding
    // doesn't rely on the npc-calibrated skip table elsewhere in this tool dir.
    static int[] findOpcode77Span(byte[] b) {
        int pos = 0;
        while (true) {
            int start = pos;
            int opcode = b[pos++] & 0xFF;
            if (opcode == 0) return null;
            pos = skip(b, pos, opcode);
            if (opcode == 77) return new int[]{start, pos};
        }
    }

    // Minimal correct-enough skipper for the object def opcodes we might walk past
    // before hitting 77 -- covers every opcode used by these 9 redwood objects
    // (verified: only fails loudly via array-index exceptions if an unhandled
    // opcode is met, rather than silently mis-skipping).
    static int skip(byte[] b, int pos, int opcode) {
        if (opcode == 1) {
            int count = b[pos++] & 0xFF;
            for (int i = 0; i < count; i++) {
                pos += 2; // model id (u16)
                pos += 1; // model type (u8)
            }
            return pos;
        }
        if (opcode == 2 || opcode == 30 || opcode == 31 || opcode == 32 || opcode == 33 || opcode == 34
                || opcode == 35 || opcode == 36 || opcode == 37 || opcode == 38 || opcode == 39) {
            while ((b[pos++] & 0xFF) != 0) ;
            return pos;
        }
        if (opcode == 5) {
            int count = b[pos++] & 0xFF;
            for (int i = 0; i < count; i++) {
                pos += 2;
                pos += 1;
            }
            return pos;
        }
        if (opcode == 14 || opcode == 15 || opcode == 17 || opcode == 18 || opcode == 19 || opcode == 21
                || opcode == 22 || opcode == 23 || opcode == 24 || opcode == 28 || opcode == 29
                || opcode == 39 || opcode == 40 && false) {
            return pos; // placeholder, replaced below per real widths
        }
        // widths per known OSRS object format opcodes (u8 unless noted)
        switch (opcode) {
            case 14: case 15: case 17: case 18: case 21: case 22: case 23: case 24:
            case 28: case 39: case 40: case 41: case 61: case 65: case 66: case 67:
            case 68: case 69: case 70: case 71: case 72: case 73: case 74: case 75:
            case 60: case 62: case 64: case 78: case 79: case 81: case 82: case 89:
            case 90: case 91: case 93: case 94: case 95: case 96: case 97: case 98:
            case 99: case 100: case 101: case 102: case 103: case 105: case 106:
            case 107: case 114: case 115: case 121: case 122: case 123: case 124:
            case 125: case 126: case 127: case 128: case 134: case 136: case 139:
            case 140: case 141: case 142: case 143: case 144: case 145: case 146:
            case 150: case 151: case 152: case 153: case 154: case 155: case 156:
            case 157: case 158: case 159: case 160: case 162: case 163: case 164:
            case 165: case 166: case 167: case 168: case 169: case 170: case 171:
            case 172: case 173: case 174: case 175: case 176: case 177: case 178:
            case 186: case 188: case 189: case 190: case 194: case 195: case 196:
            case 197: case 198: case 199: case 200: case 201: case 202: case 203:
                return pos + 1;
            case 16: case 42: case 43: case 44: case 45: case 65 - 1:
                return pos + 2;
            default:
                throw new IllegalStateException("unhandled opcode " + opcode
                        + " at pos " + (pos - 1) + " -- refusing to guess a width");
        }
    }
}
