import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;
import net.runelite.cache.io.InputStream;
import net.runelite.cache.io.OutputStream;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// ROOT CAUSE (confirmed 2026-09-13 via ScanOpcodeUsage.java: opcodes 15/44-54 are used by ZERO of
// Zelus's ~18,717 PRE-EXISTING items -- opcode 1 alone is used by all 18,717). The 5 Mad Angel
// unique items were imported using RS-Realm's MODERN opcode encoding (opcode 44 = 4-byte wide
// inventoryModel, 45/48 = 4-byte wide manwear/womanwear). Zelus's live game CLIENT is built off a
// pre-wide-model-id revision that has never had a code path for these opcodes at all (this is a
// property of the compiled/injected client core itself, not of io.ruin.cache.ObjType.java, which
// I already patched server-side only -- that fix made ::iteminfo read correctly but the item's
// displayed NAME in-game comes from a server-sent string regardless, so it never proved the client
// decoded anything; the dwarf-head placeholder icon is exactly what a client falls back to when it
// cannot resolve an item's model).
//
// FIX: rewrite these 5 items' records to use the CLASSIC narrow opcodes that all 18,717 other,
// already-correctly-rendering items use (1/23/25 in place of 44/45/48), remapping the two model
// ids that exceed the 16-bit ceiling opcode 1 can address (70009->61850, 70010->61851, both
// confirmed free via FindFreeModelIds.java) since a wide id cannot be expressed narrow at all.
// mode: verify | apply
public class FixMadAngelItemModels {
    static final int[] ITEM_IDS = {34027, 34030, 34032, 34033, 34042};

    // old (wide) model id -> new low model id. Only entries actually needed; models already
    // under 65536 (61703, 61706, 28441, 61824, 61825) are left as their own id (identity, no remap).
    static final Map<Integer, Integer> MODEL_REMAP = new HashMap<>();
    static {
        MODEL_REMAP.put(70009, 61850); // Sunstone crystal -- was source 12302, wide-remapped by ImportMadAngelItems
        MODEL_REMAP.put(70010, 61851); // Aggy -- was source 61848, wide-remapped by ImportMadAngelItems
    }
    // new low id -> the id ALREADY present in Zelus's own live cache holding the identical mesh
    // bytes (70009/70010 -- written there by ImportMadAngelItems.java's earlier wide-id remap).
    // No need to reach back to the RS-Realm reference cache; this is a same-cache copy.
    static final Map<Integer, Integer> NEW_MODEL_SOURCE = new HashMap<>();
    static {
        NEW_MODEL_SOURCE.put(61850, 70009);
        NEW_MODEL_SOURCE.put(61851, 70010);
    }

    public static void main(String[] args) throws Exception {
        String mode = args[0];
        String dstPath = args[1]; // live Zelus cache (source AND destination for the model copy)
        boolean apply = mode.equals("apply");
        if (!apply && !mode.equals("verify")) throw new IllegalArgumentException("mode must be verify or apply");

        try (Store dst = new Store(new File(dstPath))) {
            dst.load();

            boolean ok = true;
            for (int newId : NEW_MODEL_SOURCE.keySet()) {
                boolean free = dst.getIndex(IndexType.MODELS).getArchive(newId) == null;
                System.out.println("model target " + newId + ": " + (free ? "free" : "OCCUPIED -- CONFLICT"));
                ok &= free;
            }
            Archive itemArchive = dst.getIndex(IndexType.CONFIGS).getArchive(ConfigType.ITEM.getId());
            java.util.Set<Integer> known = new java.util.HashSet<>();
            for (FileData fd : itemArchive.getFileData()) known.add(fd.getId());
            for (int id : ITEM_IDS) {
                boolean present = known.contains(id);
                System.out.println("item " + id + ": " + (present ? "present (will be patched)" : "MISSING -- CANNOT PATCH"));
                ok &= present;
            }

            System.out.println(ok ? "VERIFY: OK." : "VERIFY: FAILED -- see above.");
            if (!apply) {
                System.out.println("(dry run -- pass 'apply' to write)");
                return;
            }
            if (!ok) {
                System.out.println("ABORTING apply: verify failed above. Nothing written.");
                return;
            }

            System.out.println("=== APPLY: copy mesh data to the 2 new low model ids (same-cache copy) ===");
            Storage dstStorage = dst.getStorage();
            int[][] modelPairs = new int[NEW_MODEL_SOURCE.size()][2];
            int mi = 0;
            for (Map.Entry<Integer, Integer> e : NEW_MODEL_SOURCE.entrySet()) modelPairs[mi++] = new int[]{e.getValue(), e.getKey()};
            Index modelsIndex = dst.getIndex(IndexType.MODELS);
            PackMadAngel.applySingle(dstStorage, modelsIndex, dstStorage, modelsIndex, modelPairs);

            System.out.println("=== APPLY: rewrite item opcodes 44/45/48 -> classic 1/23/25 in place ===");
            replaceItemsInPlace(dstStorage, dst.getIndex(IndexType.CONFIGS));

            System.out.println("=== DONE. ===");
        }
    }

    static void replaceItemsInPlace(Storage storage, Index configs) throws Exception {
        Archive archive = configs.getArchive(ConfigType.ITEM.getId());
        byte[] decompressed = archive.decompress(storage.loadArchive(archive));
        FileData[] fileData = archive.getFileData();
        List<byte[]> contents = SpliceItemOption.splitChunks(decompressed, fileData.length);

        List<byte[]> mutableContents = new ArrayList<>(contents);
        for (int id : ITEM_IDS) {
            int slot = -1;
            for (int j = 0; j < fileData.length; j++) if (fileData[j].getId() == id) slot = j;
            if (slot == -1) throw new IllegalStateException("item " + id + " vanished since verify");
            byte[] fixed = narrowifyModelOpcodes(id, mutableContents.get(slot));
            mutableContents.set(slot, fixed);
            System.out.println("  item " + id + ": rewritten (" + contents.get(slot).length + " -> " + fixed.length + " bytes)");
        }

        byte[] newDecompressed = SpliceItemOption.joinChunks(mutableContents);
        net.runelite.cache.fs.Container container = new net.runelite.cache.fs.Container(archive.getCompression(), -1);
        container.compress(newDecompressed, null);

        storage.store(configs.getId(), ConfigType.ITEM.getId(), container.data);
        archive.setCrc(container.crc);
        archive.setRevision(archive.getRevision() + 1);
        archive.setCompressedSize(container.data.length);
        archive.setDecompressedSize(newDecompressed.length);

        PackMadAngel.writeIndexReferenceTable(storage, configs);
        System.out.println("  config[item] revision now " + archive.getRevision());
    }

    static int remapModel(int model) {
        Integer target = MODEL_REMAP.get(model);
        return target != null ? target : model;
    }

    /** Re-encodes one item record, converting any wide (modern, opcode>=44) model-bearing opcode
     * into the classic narrow opcode every other item in this cache already uses, applying the
     * model id remap where needed. Every other opcode is copied through byte-for-byte unchanged. */
    static byte[] narrowifyModelOpcodes(int id, byte[] raw) {
        InputStream in = new InputStream(raw);
        OutputStream out = new OutputStream(raw.length);
        while (true) {
            int code = in.readUnsignedByte();
            switch (code) {
                case 0:
                    out.writeByte(0);
                    return out.flip();
                case 44: { // wide inventoryModel -> classic opcode 1
                    int model = in.readInt();
                    out.writeByte(1);
                    out.writeShort(remapModel(model));
                    break;
                }
                case 45: { // wide manwear (+offset) -> classic opcode 23
                    int model = in.readInt();
                    int offset = in.readUnsignedByte();
                    out.writeByte(23);
                    out.writeShort(remapModel(model));
                    out.writeByte(offset);
                    break;
                }
                case 46: { // wide manwear2 -> classic opcode 24
                    int model = in.readInt();
                    out.writeByte(24);
                    out.writeShort(remapModel(model));
                    break;
                }
                case 47: { // wide manwear3 -> classic opcode 78
                    int model = in.readInt();
                    out.writeByte(78);
                    out.writeShort(remapModel(model));
                    break;
                }
                case 48: { // wide womanwear (+offset) -> classic opcode 25
                    int model = in.readInt();
                    int offset = in.readUnsignedByte();
                    out.writeByte(25);
                    out.writeShort(remapModel(model));
                    out.writeByte(offset);
                    break;
                }
                case 49: { // wide womanwear2 -> classic opcode 26
                    int model = in.readInt();
                    out.writeByte(26);
                    out.writeShort(remapModel(model));
                    break;
                }
                case 50: { // wide womanwear3 -> classic opcode 79
                    int model = in.readInt();
                    out.writeByte(79);
                    out.writeShort(remapModel(model));
                    break;
                }
                case 51: { // wide manhead -> classic opcode 90
                    int model = in.readInt();
                    out.writeByte(90);
                    out.writeShort(remapModel(model));
                    break;
                }
                case 52: { // wide manhead2 -> classic opcode 92
                    int model = in.readInt();
                    out.writeByte(92);
                    out.writeShort(remapModel(model));
                    break;
                }
                case 53: { // wide womanhead -> classic opcode 91
                    int model = in.readInt();
                    out.writeByte(91);
                    out.writeShort(remapModel(model));
                    break;
                }
                case 54: { // wide womanhead2 -> classic opcode 93
                    int model = in.readInt();
                    out.writeByte(93);
                    out.writeShort(remapModel(model));
                    break;
                }
                default:
                    out.writeByte(code);
                    copyPayload(in, out, code, id);
            }
        }
    }

    /** Copies one opcode's payload through unchanged, using the real modern opcode width table
     * (same as patchItemRefs/DecodeMadAngelItemsV2) for every opcode NOT handled above. */
    static void copyPayload(InputStream in, OutputStream out, int code, int id) {
        switch (code) {
            case 1: out.writeShort(in.readUnsignedShort()); break;
            case 2: case 3: case 9: out.writeString(in.readString()); break;
            case 4: case 5: case 6: out.writeShort(in.readUnsignedShort()); break;
            case 7: case 8: out.writeShort(in.readShort()); break;
            case 11: case 15: case 16: break;
            case 12: out.writeInt(in.readInt()); break;
            case 13: case 14: case 27: out.writeByte(in.readByte()); break;
            case 23: out.writeShort(in.readUnsignedShort()); out.writeByte(in.readUnsignedByte()); break;
            case 24: out.writeShort(in.readUnsignedShort()); break;
            case 25: out.writeShort(in.readUnsignedShort()); out.writeByte(in.readUnsignedByte()); break;
            case 26: out.writeShort(in.readUnsignedShort()); break;
            case 30: case 31: case 32: case 33: case 34:
            case 35: case 36: case 37: case 38: case 39:
                out.writeString(in.readString()); break;
            case 40: case 41: {
                int count = in.readUnsignedByte();
                out.writeByte(count);
                for (int i = 0; i < count; i++) { out.writeShort(in.readUnsignedShort()); out.writeShort(in.readUnsignedShort()); }
                break;
            }
            case 42: out.writeByte(in.readByte()); break;
            case 43: {
                out.writeByte(in.readUnsignedByte());
                int subop = in.readUnsignedByte();
                out.writeByte(subop);
                while (subop != 0) {
                    out.writeString(in.readString());
                    subop = in.readUnsignedByte();
                    out.writeByte(subop);
                }
                break;
            }
            case 65: case 160: break;
            case 75: out.writeShort(in.readShort()); break;
            case 78: case 79: case 90: case 91: case 92: case 93: case 94: case 95: out.writeShort(in.readUnsignedShort()); break;
            case 97: case 98: out.writeShort(in.readUnsignedShort()); break;
            case 100: case 101: case 102: case 103: case 104:
            case 105: case 106: case 107: case 108: case 109:
                out.writeShort(in.readUnsignedShort()); out.writeShort(in.readUnsignedShort()); break;
            case 110: case 111: case 112: out.writeShort(in.readUnsignedShort()); break;
            case 113: case 114: case 115: out.writeByte(in.readByte()); break;
            case 139: case 140: case 148: case 149: out.writeShort(in.readUnsignedShort()); break;
            case 200: {
                int count = in.readUnsignedByte();
                out.writeByte(count);
                for (int i = 0; i < count; i++) out.writeShort(in.readUnsignedShort());
                break;
            }
            case 201: case 202: case 203: out.writeInt(in.readInt()); break;
            case 204: case 205: out.writeShort(in.readUnsignedShort()); break;
            case 206: break;
            case 207: out.writeShort(in.readUnsignedShort()); break;
            case 208: out.writeByte(in.readByte()); break;
            case 209: out.writeShort(in.readUnsignedShort()); break;
            case 210: case 211: out.writeShort(in.readUnsignedShort()); break;
            case 212: out.writeByte(in.readUnsignedByte()); break;
            case 249: {
                int length = in.readUnsignedByte();
                out.writeByte(length);
                for (int i = 0; i < length; i++) {
                    boolean isString = in.readUnsignedByte() == 1;
                    out.writeByte(isString ? 1 : 0);
                    out.write24BitInt(in.read24BitInt());
                    if (isString) out.writeString(in.readString()); else out.writeInt(in.readInt());
                }
                break;
            }
            default:
                throw new RuntimeException("copyPayload: unrecognized obj opcode " + code + " for item " + id);
        }
    }
}
