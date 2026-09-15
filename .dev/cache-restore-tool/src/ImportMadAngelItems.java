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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Read source + Zelus. Imports the 5 Mad Angel unique drop items (Sunstone crystal, Ardeaglais
// teleport, Hallowfell, Jar of light, "Aggy") plus their 5 linked placeholder/cert items, and the
// 6 models they need (a 7th, Jar of light's 28441, is already present byte-identical in Zelus --
// confirmed via CompareModelBytes.java, standard shared vanilla asset, not re-imported).
//
// Two model collisions found and remapped (confirmed DIFFERENT via CompareModelBytes.java, not
// assumed from the id alone): 12302 (Sunstone crystal) and 61848 (Aggy -- coincidentally the same
// slot MadAngelIds' own earlier import already remapped an unrelated smite-hit vfx model into) ->
// 70009 / 70010, the next free ids after ImportCathedralObjects.java's own 70001-70005 block.
//
// Opcode table translated 1:1 from RS-Realm-Server-Package's own ObjTypeDecoder.kt (same
// cross-reference technique used all session for loc/npc/spotanim) -- confirmed necessary because
// net.runelite.cache's own stock ItemLoader doesn't know opcodes 44-54 (wide 4-byte model ids) and
// silently mis-decodes these items (DecodeMadAngelItems.java's own output: cost/zoom came through,
// name never did -- confirmed and root-caused before writing this importer).
//
// mode: verify | apply
public class ImportMadAngelItems {
    static final Map<Integer, Integer> MODEL_REMAP = new HashMap<>();
    static {
        MODEL_REMAP.put(61703, 61703);
        MODEL_REMAP.put(61706, 61706);
        MODEL_REMAP.put(61824, 61824);
        MODEL_REMAP.put(61825, 61825);
        MODEL_REMAP.put(12302, 70009); // collision, confirmed DIFFERENT bytes
        MODEL_REMAP.put(61848, 70010); // collision, confirmed DIFFERENT bytes
        // 28441 (Jar of light) deliberately absent -- already present byte-identical, reused in place.
    }
    static final int[] MODELS_TO_IMPORT = {61703, 61706, 61824, 61825, 12302, 61848};

    static final int[] ITEM_IDS = {34027, 34028, 34029, 34030, 34031, 34032, 34033, 34034, 34042, 34043};

    public static void main(String[] args) throws Exception {
        String mode = args[0];
        String srcPath = args[1];
        String dstPath = args[2];
        boolean apply = mode.equals("apply");
        if (!apply && !mode.equals("verify")) throw new IllegalArgumentException("mode must be verify or apply");

        try (Store src = new Store(new File(srcPath)); Store dst = new Store(new File(dstPath))) {
            src.load();
            dst.load();
            Storage srcStorage = src.getStorage();
            Storage dstStorage = dst.getStorage();

            boolean ok = true;
            for (int m : MODELS_TO_IMPORT) {
                int target = MODEL_REMAP.get(m);
                boolean free = dst.getIndex(IndexType.MODELS).getArchive(target) == null;
                System.out.println("model target " + target + " (from source " + m + "): " + (free ? "free" : "OCCUPIED -- CONFLICT"));
                ok &= free;
            }
            ok &= verifyItemsFree(dst);

            System.out.println(ok ? "VERIFY: all target ids free." : "VERIFY: FAILED -- see above.");
            if (!apply) {
                System.out.println("(dry run -- pass 'apply' to write)");
                return;
            }
            if (!ok) {
                System.out.println("ABORTING apply: verify failed above. Nothing written.");
                return;
            }

            System.out.println("=== APPLY: models ===");
            int[][] modelPairs = new int[MODELS_TO_IMPORT.length][2];
            for (int i = 0; i < MODELS_TO_IMPORT.length; i++) {
                modelPairs[i] = new int[]{MODELS_TO_IMPORT[i], MODEL_REMAP.get(MODELS_TO_IMPORT[i])};
            }
            PackMadAngel.applySingle(srcStorage, src.getIndex(IndexType.MODELS), dstStorage, dst.getIndex(IndexType.MODELS), modelPairs);

            System.out.println("=== APPLY: items (10), model refs patched where remapped ===");
            int[][] itemPairs = new int[ITEM_IDS.length][2];
            for (int i = 0; i < ITEM_IDS.length; i++) itemPairs[i] = new int[]{ITEM_IDS[i], ITEM_IDS[i]};
            PackMadAngel.applyConfig(srcStorage, src.getIndex(IndexType.CONFIGS), dstStorage, dst.getIndex(IndexType.CONFIGS),
                    ConfigType.ITEM.getId(), itemPairs, ImportMadAngelItems::patchItemRefs);

            System.out.println("=== DONE. ===");
        }
    }

    static boolean verifyItemsFree(Store dst) throws Exception {
        Index configs = dst.getIndex(IndexType.CONFIGS);
        Archive archive = configs.getArchive(ConfigType.ITEM.getId());
        java.util.Set<Integer> known = new java.util.HashSet<>();
        for (FileData fd : archive.getFileData()) known.add(fd.getId());
        boolean ok = true;
        for (int id : ITEM_IDS) {
            boolean occupied = known.contains(id);
            if (occupied) System.out.println("item target " + id + ": OCCUPIED -- CONFLICT");
            ok &= !occupied;
        }
        System.out.println("item ids checked: " + ITEM_IDS.length + " (conflicts printed above, if any)");
        return ok;
    }

    static int remapModel(int model) {
        Integer target = MODEL_REMAP.get(model);
        return target != null ? target : model;
    }

    /** Full rewrite of an item record using ObjTypeDecoder.kt's real opcode table -- every opcode
     * is read and re-written verbatim except 1/44 (model), which are remapped if the source id is
     * one of the two collisions above. */
    static byte[] patchItemRefs(int id, byte[] raw) {
        InputStream in = new InputStream(raw);
        OutputStream out = new OutputStream(raw.length + 8);
        while (true) {
            int code = in.readUnsignedByte();
            out.writeByte(code);
            if (code == 0) break;
            switch (code) {
                case 1: out.writeShort(remapModel(in.readUnsignedShort())); break;
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
                case 44: out.writeInt(remapModel(in.readInt())); break;
                case 45: out.writeInt(in.readInt()); out.writeByte(in.readUnsignedByte()); break;
                case 46: case 47: out.writeInt(in.readInt()); break;
                case 48: out.writeInt(in.readInt()); out.writeByte(in.readUnsignedByte()); break;
                case 49: case 50: case 51: case 52: case 53: case 54: out.writeInt(in.readInt()); break;
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
                    throw new RuntimeException("patchItemRefs: unrecognized obj opcode " + code + " for item " + id);
            }
        }
        return out.flip();
    }
}
