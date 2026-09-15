import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;
import net.runelite.cache.io.InputStream;
import net.runelite.cache.io.OutputStream;

import java.io.File;
import java.util.List;
import java.util.TreeMap;

// Read-only. Same technique as ScanOpcodeUsage.java but for NPC configs -- checks whether opcodes
// 61/62/252 (the "wide 4-byte model id" NPC opcodes, same modern-vs-classic split already proven
// to break ITEM icon rendering) are used by any PRE-EXISTING (already-live, presumably-rendering)
// npc, or only by the newly-imported Mad Angel npcs (16305-16308) -- to check whether the boss
// itself might be suffering the identical invisible/placeholder-model bug just fixed for items.
public class ScanNpcOpcodeUsage {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Archive archive = store.getIndex(IndexType.CONFIGS).getArchive(ConfigType.NPC.getId());
            byte[] decompressed = archive.decompress(storage.loadArchive(archive));
            FileData[] fileData = archive.getFileData();
            List<byte[]> contents = SpliceItemOption.splitChunks(decompressed, fileData.length);

            TreeMap<Integer, Integer> counts = new TreeMap<>();
            int total = 0;
            for (int i = 0; i < fileData.length; i++) {
                int id = fileData[i].getId();
                if (id >= 16305 && id <= 16320) continue; // skip Mad Angel's own npcs
                total++;
                tally(contents.get(i), counts);
            }
            System.out.println("scanned " + total + " pre-existing npcs");
            for (int op : new int[]{1, 60, 61, 62, 252}) {
                System.out.println("opcode " + op + ": used by " + counts.getOrDefault(op, 0) + " pre-existing npcs");
            }

            System.out.println("--- Mad Angel npcs' own opcodes ---");
            int[] madIds = {16305, 16306, 16307, 16308};
            for (int id : madIds) {
                int slot = -1;
                for (int j = 0; j < fileData.length; j++) if (fileData[j].getId() == id) slot = j;
                if (slot == -1) { System.out.println(id + " NOT FOUND"); continue; }
                traceModelOpcode(id, contents.get(slot));
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
            // stop tallying this npc on first unrecognized opcode
        }
    }

    static void traceModelOpcode(int id, byte[] b) {
        InputStream in = new InputStream(b);
        try {
            while (true) {
                int code = in.readUnsignedByte();
                if (code == 0) { System.out.println("npc " + id + ": no model opcode found (end of record)"); return; }
                if (code == 1) {
                    int count = in.readUnsignedByte();
                    StringBuilder sb = new StringBuilder("npc " + id + ": NARROW opcode 1 models=[");
                    for (int i = 0; i < count; i++) sb.append(in.readUnsignedShort()).append(i < count - 1 ? "," : "");
                    System.out.println(sb + "]");
                    return;
                }
                if (code == 61) {
                    int count = in.readUnsignedByte();
                    StringBuilder sb = new StringBuilder("npc " + id + ": WIDE opcode 61 models=[");
                    for (int i = 0; i < count; i++) sb.append(in.readInt()).append(i < count - 1 ? "," : "");
                    System.out.println(sb + "]");
                    return;
                }
                skip(in, code);
            }
        } catch (Exception e) {
            System.out.println("npc " + id + ": decode error while searching for model opcode -- " + e);
        }
    }

    // Real modern npc opcode width table, transcribed from RS-Realm-Server-Package's NpcTypeDecoder.kt
    // cross-reference (same technique used for items/objects this session).
    // Transcribed 1:1 from RS-Realm-Server-Package's real NpcTypeDecoder.kt (read directly this
    // session, not guessed) -- the same source-of-truth technique used all along for loc/item/npc
    // opcode tables.
    static void skip(InputStream in, int code) {
        switch (code) {
            case 1: {
                int count = in.readUnsignedByte();
                for (int i = 0; i < count; i++) in.readUnsignedShort();
                break;
            }
            case 2: case 3: in.readString(); break;
            case 12: in.readUnsignedByte(); break;
            case 13: case 14: case 15: case 16: in.readUnsignedShort(); break;
            case 17: in.readUnsignedShort(); in.readUnsignedShort(); in.readUnsignedShort(); in.readUnsignedShort(); break;
            case 18: in.readUnsignedShort(); break;
            case 30: case 31: case 32: case 33: case 34: in.readString(); break;
            case 40: case 41: {
                int count = in.readUnsignedByte();
                for (int i = 0; i < count; i++) { in.readUnsignedShort(); in.readUnsignedShort(); }
                break;
            }
            case 60: {
                int count = in.readUnsignedByte();
                for (int i = 0; i < count; i++) in.readUnsignedShort();
                break;
            }
            case 61: {
                int count = in.readUnsignedByte();
                for (int i = 0; i < count; i++) in.readInt();
                break;
            }
            case 62: {
                int count = in.readUnsignedByte();
                for (int i = 0; i < count; i++) in.readInt();
                break;
            }
            case 74: case 75: case 76: case 77: case 78: case 79: in.readUnsignedShort(); break;
            case 93: break;
            case 95: in.readUnsignedShort(); break;
            case 97: in.readUnsignedShort(); break;
            case 98: in.readUnsignedShort(); break;
            case 99: break;
            case 100: in.readByte(); break;
            case 101: in.readByte(); break;
            case 102: {
                int enabledFlags = in.readUnsignedByte();
                int count = 0;
                int bits = enabledFlags;
                while (bits != 0) { bits >>>= 1; count++; }
                for (int i = 0; i < count; i++) {
                    if ((enabledFlags & (1 << i)) != 0) {
                        readNullableLargeSmart(in);
                        readUnsignedSmallSmartPlusOne(in);
                    }
                }
                break;
            }
            case 103: in.readUnsignedShort(); break;
            case 106: case 118: {
                readUnsignedShortOrNull(in);
                readUnsignedShortOrNull(in);
                if (code == 118) readUnsignedShortOrNull(in);
                int count = in.readUnsignedByte();
                for (int i = 0; i <= count; i++) readUnsignedShortOrNull(in);
                break;
            }
            case 107: case 109: case 111: break;
            case 114: in.readUnsignedShort(); break;
            case 115: in.readUnsignedShort(); in.readUnsignedShort(); in.readUnsignedShort(); in.readUnsignedShort(); break;
            case 116: in.readUnsignedShort(); break;
            case 117: in.readUnsignedShort(); in.readUnsignedShort(); in.readUnsignedShort(); in.readUnsignedShort(); break;
            case 122: case 123: break;
            case 124: in.readUnsignedShort(); break;
            case 126: in.readUnsignedShort(); break;
            case 129: case 130: break;
            case 145: break;
            case 146: in.readUnsignedShort(); break;
            case 200: in.readUnsignedByte(); break;
            case 201: in.readUnsignedByte(); break;
            case 202: in.readUnsignedByte(); break;
            case 203: {
                int count = in.readUnsignedByte() + 1;
                for (int i = 0; i < count; i++) { readCoordGrid(in); in.readUnsignedByte(); }
                break;
            }
            case 204: in.readUnsignedShort(); break;
            case 205: case 206: case 207: case 208: case 209: in.readUnsignedByte(); break;
            case 210: break;
            case 211: in.readUnsignedShort(); break;
            case 212: in.readUnsignedByte(); break;
            case 213: case 214: case 215: in.readUnsignedShort(); break;
            case 216: readUnsignedShortOrNull(in); break;
            case 147: break;
            case 249: {
                int length = in.readUnsignedByte();
                for (int i = 0; i < length; i++) {
                    boolean isString = in.readUnsignedByte() == 1;
                    in.read24BitInt();
                    if (isString) in.readString(); else in.readInt();
                }
                break;
            }
            case 252: for (int i = 0; i < 14; i++) in.readByte(); break;
            default:
                throw new RuntimeException("unrecognized npc opcode " + code);
        }
    }

    // org.rsmod.api.cache.util.readUnsignedShortOrNull: a plain ushort, but 0xFFFF means "null" --
    // width is identical either way (2 bytes), so for a byte-accounting skip we only need the width.
    static void readUnsignedShortOrNull(InputStream in) { in.readUnsignedShort(); }

    // org.rsmod.api.cache.util.readNullableLargeSmart: 1 or 4 bytes depending on the leading bit of
    // the first byte (a "smart" varint) -- peek it, then consume the rest.
    static void readNullableLargeSmart(InputStream in) {
        int peek = in.readUnsignedByte();
        if (peek >= 128) {
            // 4-byte form: put back the byte we already consumed by reading 3 more (net 4 total).
            in.readUnsignedByte(); in.readUnsignedByte(); in.readUnsignedByte();
        }
        // else: 1-byte form, already fully consumed.
    }

    // org.rsmod.api.cache.util.readUnsignedSmallSmartPlusOne: 1 or 2 bytes, same leading-bit smart
    // encoding as above.
    static void readUnsignedSmallSmartPlusOne(InputStream in) {
        int peek = in.readUnsignedByte();
        if (peek >= 128) in.readUnsignedByte();
    }

    // org.rsmod.api.cache.util.readCoordGrid: packed into a single 32-bit int.
    static void readCoordGrid(InputStream in) { in.readInt(); }

    // Copy-through twin of skip() above -- same real opcode table, but writes every consumed byte
    // back out unchanged instead of discarding it. Used by ImportAggyPet.java to carry every field
    // OTHER than the two narrowed model opcodes (61/62, handled by its own caller) through verbatim.
    static void skipCopy(InputStream in, OutputStream out, int code) {
        switch (code) {
            case 1: {
                int count = in.readUnsignedByte();
                out.writeByte(count);
                for (int i = 0; i < count; i++) out.writeShort(in.readUnsignedShort());
                break;
            }
            case 2: case 3: out.writeString(in.readString()); break;
            case 12: out.writeByte(in.readUnsignedByte()); break;
            case 13: case 14: case 15: case 16: out.writeShort(in.readUnsignedShort()); break;
            case 17: out.writeShort(in.readUnsignedShort()); out.writeShort(in.readUnsignedShort()); out.writeShort(in.readUnsignedShort()); out.writeShort(in.readUnsignedShort()); break;
            case 18: out.writeShort(in.readUnsignedShort()); break;
            case 30: case 31: case 32: case 33: case 34: out.writeString(in.readString()); break;
            case 40: case 41: {
                int count = in.readUnsignedByte();
                out.writeByte(count);
                for (int i = 0; i < count; i++) { out.writeShort(in.readUnsignedShort()); out.writeShort(in.readUnsignedShort()); }
                break;
            }
            case 60: {
                int count = in.readUnsignedByte();
                out.writeByte(count);
                for (int i = 0; i < count; i++) out.writeShort(in.readUnsignedShort());
                break;
            }
            case 61: case 62: {
                int count = in.readUnsignedByte();
                out.writeByte(count);
                for (int i = 0; i < count; i++) out.writeInt(in.readInt());
                break;
            }
            case 74: case 75: case 76: case 77: case 78: case 79: out.writeShort(in.readUnsignedShort()); break;
            case 93: break;
            case 95: out.writeShort(in.readUnsignedShort()); break;
            case 97: out.writeShort(in.readUnsignedShort()); break;
            case 98: out.writeShort(in.readUnsignedShort()); break;
            case 99: break;
            case 100: out.writeByte(in.readByte()); break;
            case 101: out.writeByte(in.readByte()); break;
            case 102: {
                int enabledFlags = in.readUnsignedByte();
                out.writeByte(enabledFlags);
                int count = 0;
                int bits = enabledFlags;
                while (bits != 0) { bits >>>= 1; count++; }
                for (int i = 0; i < count; i++) {
                    if ((enabledFlags & (1 << i)) != 0) {
                        copyNullableLargeSmart(in, out);
                        copyUnsignedSmallSmartPlusOne(in, out);
                    }
                }
                break;
            }
            case 103: out.writeShort(in.readUnsignedShort()); break;
            case 106: case 118: {
                out.writeShort(in.readUnsignedShort());
                out.writeShort(in.readUnsignedShort());
                if (code == 118) out.writeShort(in.readUnsignedShort());
                int count = in.readUnsignedByte();
                out.writeByte(count);
                for (int i = 0; i <= count; i++) out.writeShort(in.readUnsignedShort());
                break;
            }
            case 107: case 109: case 111: break;
            case 114: out.writeShort(in.readUnsignedShort()); break;
            case 115: out.writeShort(in.readUnsignedShort()); out.writeShort(in.readUnsignedShort()); out.writeShort(in.readUnsignedShort()); out.writeShort(in.readUnsignedShort()); break;
            case 116: out.writeShort(in.readUnsignedShort()); break;
            case 117: out.writeShort(in.readUnsignedShort()); out.writeShort(in.readUnsignedShort()); out.writeShort(in.readUnsignedShort()); out.writeShort(in.readUnsignedShort()); break;
            case 122: case 123: break;
            case 124: out.writeShort(in.readUnsignedShort()); break;
            case 126: out.writeShort(in.readUnsignedShort()); break;
            case 129: case 130: break;
            case 145: break;
            case 146: out.writeShort(in.readUnsignedShort()); break;
            case 200: out.writeByte(in.readUnsignedByte()); break;
            case 201: out.writeByte(in.readUnsignedByte()); break;
            case 202: out.writeByte(in.readUnsignedByte()); break;
            case 203: {
                int count = in.readUnsignedByte() + 1;
                out.writeByte(count - 1);
                for (int i = 0; i < count; i++) { out.writeInt(in.readInt()); out.writeByte(in.readUnsignedByte()); }
                break;
            }
            case 204: out.writeShort(in.readUnsignedShort()); break;
            case 205: case 206: case 207: case 208: case 209: out.writeByte(in.readUnsignedByte()); break;
            case 210: break;
            case 211: out.writeShort(in.readUnsignedShort()); break;
            case 212: out.writeByte(in.readUnsignedByte()); break;
            case 213: case 214: case 215: out.writeShort(in.readUnsignedShort()); break;
            case 216: out.writeShort(in.readUnsignedShort()); break;
            case 147: break;
            case 249: {
                int length = in.readUnsignedByte();
                out.writeByte(length);
                for (int i = 0; i < length; i++) {
                    int isString = in.readUnsignedByte();
                    out.writeByte(isString);
                    out.write24BitInt(in.read24BitInt());
                    if (isString == 1) out.writeString(in.readString()); else out.writeInt(in.readInt());
                }
                break;
            }
            case 252: for (int i = 0; i < 14; i++) out.writeByte(in.readByte()); break;
            default:
                throw new RuntimeException("skipCopy: unrecognized npc opcode " + code);
        }
    }

    static void copyNullableLargeSmart(InputStream in, OutputStream out) {
        int peek = in.readUnsignedByte();
        out.writeByte(peek);
        if (peek >= 128) {
            out.writeByte(in.readUnsignedByte());
            out.writeByte(in.readUnsignedByte());
            out.writeByte(in.readUnsignedByte());
        }
    }

    static void copyUnsignedSmallSmartPlusOne(InputStream in, OutputStream out) {
        int peek = in.readUnsignedByte();
        out.writeByte(peek);
        if (peek >= 128) out.writeByte(in.readUnsignedByte());
    }
}
