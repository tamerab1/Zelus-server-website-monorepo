import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Container;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;
import net.runelite.cache.index.IndexData;
import net.runelite.cache.io.InputStream;
import net.runelite.cache.io.OutputStream;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Read source + Zelus. Imports the 117 Cathedral-furniture OBJECT (loc) definitions that
// DecodeCathedralLocs found missing from Zelus's OBJECT config -- referenced by the newly-imported
// Cathedral map's loc list (m39_34/l39_34), and the prime suspect for the client's persistent
// "this.ax.ae is null" crash: any client subsystem that walks a loaded map's locs and looks up each
// one's ObjectComposition (a worldmap/minimap indexer, most likely -- it would run at client init
// regardless of login, matching the crash's observed timing) hits an unknown object id and derefs a
// null model array downstream.
//
// Of the 115 models these objects reference, 109 are free in Zelus and import 1:1; 6 collide with
// existing unrelated content, of which 1 (id 1570, "Tree") is verified BYTE-IDENTICAL already (see
// CompareModelBytes) so it's reused in place untouched; the other 5 (3509, 9235, 12415, 18579,
// 22838 -- all differ, one substantially) are remapped to free ids 70001-70005. Of the 2 referenced
// object-idle-animation sequences, 13564 is free (1:1); 5906 collides and is remapped to 16200.
// Every object record referencing a remapped model/anim has its own bytes patched to the new id
// (same "decode the known opcode table, substitute, re-encode" technique as
// LegacyEncodeMadAngelNpcs.java and PackMadAngel's spotanim patcher).
//
// mode: verify | apply
public class ImportCathedralObjects {
    // Models: sourceId -> targetId. 1570 maps to itself (reused in place, byte-identical, not
    // re-imported -- see MODELS_TO_IMPORT below, which omits it).
    static final Map<Integer, Integer> MODEL_REMAP = new HashMap<>();
    static {
        MODEL_REMAP.put(1570, 1570); // identical, reuse in place
        MODEL_REMAP.put(3509, 70001);
        MODEL_REMAP.put(9235, 70002);
        MODEL_REMAP.put(12415, 70003);
        MODEL_REMAP.put(18579, 70004);
        MODEL_REMAP.put(22838, 70005);
        // The other 109 referenced models were all free (verified via BulkCollisionCheck) -- 1:1,
        // no remap needed. Filled in via a second static block AFTER MODELS_TO_IMPORT below, since
        // Java forbids a forward reference to a not-yet-declared static field from an earlier block.
    }
    /**
     * ALL 115 models these objects reference, minus 1570 (byte-identical, already present -- see
     * class javadoc). This was originally written with only the 5 REMAPPED ids here, on the wrong
     * assumption that "free" (from BulkCollisionCheck) meant "already present" -- it means the
     * opposite, available to import. Caught before boot only because CheckModelsExist re-verified
     * every model this import's own object records reference actually resolves post-apply.
     */
    static final int[] MODELS_TO_IMPORT = {
            3509, 9235, 12415, 18579, 22838, // remapped (collided)
            58532, 58596, 58598, 58599, 58600, 58601, 58602, 58603, 58604,
            61615, 61616, 61617, 61618, 61619, 61620, 61621, 61622, 61623, 61624,
            61625, 61626, 61627, 61628, 61629, 61630, 61631, 61632, 61633, 61634,
            61635, 61636, 61637, 61638, 61639, 61640, 61641, 61642, 61643, 61644,
            61645, 61646, 61647, 61648, 61649, 61650, 61651, 61652, 61653, 61654,
            61655, 61656, 61657, 61658, 61659, 61660, 61661, 61662, 61663, 61664,
            61665, 61666, 61667, 61668, 61669, 61670, 61671, 61672, 61673, 61674,
            61675, 61676, 61677, 61678, 61679, 61680, 61681, 61682, 61683, 61684,
            61713, 61714, 61715, 61716, 61717, 61718, 61719, 61720, 61721, 61722,
            61723, 61724, 61725, 61726, 61727, 61728, 61729, 61730, 61731,
            61735, 61736, 61737, 61738, 61739, 61740, 61741, 61742, 61748, 61754, 61852,
    };
    static {
        for (int m : MODELS_TO_IMPORT) {
            MODEL_REMAP.putIfAbsent(m, m);
        }
    }

    static final Map<Integer, Integer> ANIM_REMAP = new HashMap<>();
    static {
        ANIM_REMAP.put(5906, 16200);
        ANIM_REMAP.put(13564, 13564); // free, imported 1:1
    }
    static final int[] ANIMS_TO_IMPORT = {5906, 13564};

    static final int[] OBJECT_IDS = {
            60436, 60438, 60439, 60440, 60441, 60442, 60443, 60444, 60453, 60454, 61180,
            62201, 62202, 62203, 62204, 62205, 62206, 62207, 62208, 62214, 62219,
            62227, 62228, 62229, 62230, 62231, 62232, 62234, 62235, 62236, 62238, 62239, 62240, 62241,
            62246, 62248, 62249, 62250, 62252, 62253, 62254, 62258, 62259, 62263, 62265, 62267,
            62279, 62280, 62281, 62282, 62283, 62284, 62285, 62286, 62287, 62288, 62289,
            62290, 62291, 62292, 62293, 62294, 62295, 62296, 62297, 62298, 62299,
            62300, 62301, 62302, 62303, 62304, 62305, 62306, 62307, 62308, 62309,
            62310, 62311, 62312, 62313, 62314, 62315, 62316, 62317, 62318, 62319,
            62320, 62321, 62322, 62323, 62324, 62325, 62326, 62327, 62328, 62329,
            62330, 62331, 62332, 62333, 62334, 62335, 62336, 62337, 62338, 62339,
            62340, 62341, 62342, 62368, 62369, 62372, 62374, 62378, 62386, 62387,
    };

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
            for (int a : ANIMS_TO_IMPORT) {
                int target = ANIM_REMAP.get(a);
                ok &= verifySequenceFree(dst, target, a);
            }
            ok &= verifyObjectsFree(dst);

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

            System.out.println("=== APPLY: object-idle animations ===");
            int[][] animPairs = new int[ANIMS_TO_IMPORT.length][2];
            for (int i = 0; i < ANIMS_TO_IMPORT.length; i++) {
                animPairs[i] = new int[]{ANIMS_TO_IMPORT[i], ANIM_REMAP.get(ANIMS_TO_IMPORT[i])};
            }
            PackMadAngel.applyConfig(srcStorage, src.getIndex(IndexType.CONFIGS), dstStorage, dst.getIndex(IndexType.CONFIGS),
                    ConfigType.SEQUENCE.getId(), animPairs, null);

            System.out.println("=== APPLY: object definitions (117), model/anim refs patched ===");
            int[][] objectPairs = new int[OBJECT_IDS.length][2];
            for (int i = 0; i < OBJECT_IDS.length; i++) objectPairs[i] = new int[]{OBJECT_IDS[i], OBJECT_IDS[i]};
            PackMadAngel.applyConfig(srcStorage, src.getIndex(IndexType.CONFIGS), dstStorage, dst.getIndex(IndexType.CONFIGS),
                    ConfigType.OBJECT.getId(), objectPairs, ImportCathedralObjects::patchObjectRefs);

            System.out.println("=== DONE. ===");
        }
    }

    static boolean verifySequenceFree(Store dst, int targetId, int sourceId) throws Exception {
        Index configs = dst.getIndex(IndexType.CONFIGS);
        Archive archive = configs.getArchive(ConfigType.SEQUENCE.getId());
        boolean occupied = false;
        for (FileData fd : archive.getFileData()) if (fd.getId() == targetId) occupied = true;
        System.out.println("sequence target " + targetId + " (from source " + sourceId + "): " + (occupied ? "OCCUPIED -- CONFLICT" : "free"));
        return !occupied;
    }

    static boolean verifyObjectsFree(Store dst) throws Exception {
        Index configs = dst.getIndex(IndexType.CONFIGS);
        Archive archive = configs.getArchive(ConfigType.OBJECT.getId());
        java.util.Set<Integer> known = new java.util.HashSet<>();
        for (FileData fd : archive.getFileData()) known.add(fd.getId());
        boolean ok = true;
        for (int id : OBJECT_IDS) {
            boolean occupied = known.contains(id);
            if (occupied) System.out.println("object target " + id + ": OCCUPIED -- CONFLICT");
            ok &= !occupied;
        }
        System.out.println("object ids checked: " + OBJECT_IDS.length + " (conflicts printed above, if any)");
        return ok;
    }

    /** Rewrites a loc record, substituting any model/anim id that was remapped. Every other opcode passes through unchanged. */
    static byte[] patchObjectRefs(int id, byte[] raw) {
        InputStream is = new InputStream(raw);
        OutputStream os = new OutputStream(raw.length + 8);
        while (true) {
            int opcode = is.readUnsignedByte();
            os.writeByte(opcode);
            if (opcode == 0) break;
            if (opcode == 1) {
                int count = is.readUnsignedByte();
                os.writeByte(count);
                for (int i = 0; i < count; i++) {
                    int model = is.readUnsignedShort();
                    os.writeShort(remapModel(model));
                    os.writeByte(is.readUnsignedByte());
                }
            } else if (opcode == 2 || opcode == 3) {
                os.writeString(is.readString());
            } else if (opcode == 5) {
                int count = is.readUnsignedByte();
                os.writeByte(count);
                for (int i = 0; i < count; i++) os.writeShort(remapModel(is.readUnsignedShort()));
            } else if (opcode == 6) {
                int count = is.readUnsignedByte();
                os.writeByte(count);
                for (int i = 0; i < count; i++) {
                    int model = is.readInt();
                    os.writeInt(remapModel(model));
                    os.writeByte(is.readUnsignedByte());
                }
            } else if (opcode == 7) {
                int count = is.readUnsignedByte();
                os.writeByte(count);
                for (int i = 0; i < count; i++) os.writeInt(remapModel(is.readInt()));
            } else if (opcode == 14 || opcode == 15) {
                os.writeByte(is.readUnsignedByte());
            } else if (opcode == 17 || opcode == 18) {
                // no payload
            } else if (opcode == 19) {
                os.writeByte(is.readUnsignedByte());
            } else if (opcode == 21 || opcode == 22 || opcode == 23) {
                // no payload
            } else if (opcode == 24) {
                int anim = is.readUnsignedShort();
                os.writeShort(anim == 0xFFFF ? anim : remapAnim(anim));
            } else if (opcode == 27) {
                // no payload
            } else if (opcode == 28) {
                os.writeByte(is.readUnsignedByte());
            } else if (opcode == 29) {
                os.writeByte(is.readByte());
            } else if (opcode >= 30 && opcode < 35) {
                os.writeString(is.readString());
            } else if (opcode == 39) {
                os.writeByte(is.readByte());
            } else if (opcode == 40 || opcode == 41) {
                int count = is.readUnsignedByte();
                os.writeByte(count);
                for (int i = 0; i < count; i++) { os.writeShort(is.readUnsignedShort()); os.writeShort(is.readUnsignedShort()); }
            } else if (opcode == 61) {
                os.writeShort(is.readUnsignedShort());
            } else if (opcode == 62) {
                // no payload
            } else if (opcode == 64) {
                // no payload
            } else if (opcode == 65 || opcode == 66 || opcode == 67) {
                os.writeShort(is.readUnsignedShort());
            } else if (opcode == 68) {
                os.writeShort(is.readUnsignedShort());
            } else if (opcode == 69) {
                os.writeByte(is.readUnsignedByte());
            } else if (opcode == 70 || opcode == 71 || opcode == 72) {
                os.writeShort(is.readShort());
            } else if (opcode == 73 || opcode == 74) {
                // no payload
            } else if (opcode == 75) {
                os.writeByte(is.readUnsignedByte());
            } else if (opcode == 77 || opcode == 92) {
                os.writeShort(is.readUnsignedShort());
                os.writeShort(is.readUnsignedShort());
                if (opcode == 92) os.writeShort(is.readUnsignedShort());
                int count = is.readUnsignedByte();
                os.writeByte(count);
                for (int i = 0; i <= count; i++) os.writeShort(is.readUnsignedShort());
            } else if (opcode == 78) {
                os.writeShort(is.readUnsignedShort()); os.writeByte(is.readUnsignedByte()); os.writeByte(is.readUnsignedByte());
            } else if (opcode == 79) {
                os.writeShort(is.readUnsignedShort()); os.writeShort(is.readUnsignedShort());
                os.writeByte(is.readUnsignedByte()); os.writeByte(is.readUnsignedByte());
                int count = is.readUnsignedByte();
                os.writeByte(count);
                for (int i = 0; i < count; i++) os.writeShort(is.readUnsignedShort());
            } else if (opcode == 81) {
                os.writeByte(is.readUnsignedByte());
            } else if (opcode == 82) {
                os.writeShort(is.readUnsignedShort());
            } else if (opcode == 89 || opcode == 90) {
                // no payload
            } else if (opcode == 91) {
                os.writeByte(is.readUnsignedByte());
            } else if (opcode == 93) {
                os.writeByte(is.readUnsignedByte()); os.writeShort(is.readUnsignedShort());
                os.writeByte(is.readUnsignedByte()); os.writeShort(is.readUnsignedShort());
            } else if (opcode == 94) {
                // no payload
            } else if (opcode == 95) {
                os.writeByte(is.readUnsignedByte());
            } else if (opcode == 96) {
                os.writeByte(is.readUnsignedByte());
            } else if (opcode == 100) {
                os.writeByte(is.readUnsignedByte()); os.writeByte(is.readUnsignedByte()); os.writeString(is.readString());
            } else if (opcode == 101) {
                os.writeByte(is.readUnsignedByte()); os.writeShort(is.readUnsignedShort()); os.writeShort(is.readUnsignedShort());
                os.writeInt(is.readInt()); os.writeInt(is.readInt()); os.writeString(is.readString());
            } else if (opcode == 102) {
                os.writeByte(is.readUnsignedByte()); os.writeShort(is.readUnsignedShort()); os.writeShort(is.readUnsignedShort());
                os.writeShort(is.readUnsignedShort()); os.writeInt(is.readInt()); os.writeInt(is.readInt()); os.writeString(is.readString());
            } else if (opcode == 200) {
                os.writeShort(is.readUnsignedShort());
            } else if (opcode == 249) {
                int length = is.readUnsignedByte();
                os.writeByte(length);
                for (int i = 0; i < length; i++) {
                    boolean isString = is.readUnsignedByte() == 1;
                    os.writeByte(isString ? 1 : 0);
                    os.write24BitInt(is.read24BitInt());
                    if (isString) os.writeString(is.readString()); else os.writeInt(is.readInt());
                }
            } else {
                throw new RuntimeException("patchObjectRefs: unrecognized loc opcode " + opcode + " for object " + id);
            }
        }
        return os.flip();
    }

    /**
     * Swappable so {@code patchObjectRefs} can be reused verbatim by a later, separate import pass
     * (see ImportCathedralMultiLocSiblings) with its OWN remap tables -- single-threaded, sequential
     * use only within one process invocation, never concurrent.
     */
    static Map<Integer, Integer> activeModelRemap = MODEL_REMAP;
    static Map<Integer, Integer> activeAnimRemap = ANIM_REMAP;

    static int remapModel(int model) {
        Integer target = activeModelRemap.get(model);
        return target != null ? target : model;
    }

    static int remapAnim(int anim) {
        Integer target = activeAnimRemap.get(anim);
        return target != null ? target : anim;
    }
}
