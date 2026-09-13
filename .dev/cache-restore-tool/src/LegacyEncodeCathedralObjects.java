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
import java.util.*;

// Read Zelus cache, rewrite in place. Root cause of tonight's ::madangel client crash (confirmed
// by disassembling ik.class -- the client's own obfuscated LocType decoder -- from
// ~/.zelus/.runelite/cache/injected-client.jar): 117 of the 121 imported Cathedral objects use
// opcode 6/7 (wide 4-byte model id list), which is COMPLETELY ABSENT from this client build's
// decoder (exhaustively verified -- no comparison against 6 or 7 anywhere in its ~2400-instruction
// dispatch method). With no clean "unknown opcode" throw in this build, an unrecognized opcode
// silently desyncs the byte-stream reader; the client only crashes much later, elsewhere in the
// same record, with an unrelated-looking ArrayIndexOutOfBoundsException -- exactly what was
// observed. Every OTHER opcode these records use (2,5,14,15,17,18,19,21,22,23,24,27,28,29,
// 30-34,39,40,41,61,62,64,65-75,77,78,79,81,82,89,90,92,93,96,101,249) was individually spot
// -checked against the same disassembly and confirmed present, so nothing else needs touching.
//
// This downgrades every opcode-6 record to opcode 1 and every opcode-7 record to opcode 5 (narrow,
// 2-byte model ids -- exactly what LegacyEncodeMadAngelNpcs.java did for the npc opcode 61/62
// case). All model ids in play now fit a narrow field: the 109 already-free models are unchanged,
// and the 8 that previously needed remapping to 70001-70008 (too large for 2 bytes) were relocated
// to 65510-65517 by RelocateModelsSub65535.java specifically so this downgrade is possible. Every
// other opcode is copied through byte-for-byte, unchanged.
//
// mode: verify | apply
public class LegacyEncodeCathedralObjects {
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
            62264, 62266, 62373, 62375,
    };

    // Old model id -> new model id, for the 8 that were relocated off 70001-70008 (too wide for
    // narrow encoding) to 65510-65517 (free, under 65536).
    static final Map<Integer, Integer> MODEL_ID_FIX = new HashMap<>();
    static {
        MODEL_ID_FIX.put(70001, 65510);
        MODEL_ID_FIX.put(70002, 65511);
        MODEL_ID_FIX.put(70003, 65512);
        MODEL_ID_FIX.put(70004, 65513);
        MODEL_ID_FIX.put(70005, 65514);
        MODEL_ID_FIX.put(70006, 65515);
        MODEL_ID_FIX.put(70007, 65516);
        MODEL_ID_FIX.put(70008, 65517);
    }

    public static void main(String[] args) throws Exception {
        String mode = args[0];
        String cachePath = args[1];
        boolean apply = mode.equals("apply");
        if (!apply && !mode.equals("verify")) throw new IllegalArgumentException("mode must be verify or apply");

        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Index configs = store.getIndex(IndexType.CONFIGS);
            Archive archive = configs.getArchive(ConfigType.OBJECT.getId());
            byte[] decompressed = archive.decompress(storage.loadArchive(archive));
            FileData[] fileData = archive.getFileData();
            List<byte[]> contents = SpliceItemOption.splitChunks(decompressed, fileData.length);

            int changedCount = 0;
            for (int id : OBJECT_IDS) {
                int slot = -1;
                for (int j = 0; j < fileData.length; j++) if (fileData[j].getId() == id) slot = j;
                if (slot == -1) throw new IllegalStateException("object " + id + " not found");

                byte[] before = contents.get(slot);
                byte[] after = reencode(id, before);
                boolean changed = !Arrays.equals(before, after);
                System.out.println("object " + id + ": " + before.length + " -> " + after.length + " bytes"
                        + (changed ? " (downgraded)" : " (unchanged)"));
                if (changed) changedCount++;
                contents.set(slot, after);
            }

            System.out.println("\n" + changedCount + " of " + OBJECT_IDS.length + " objects modified.");
            if (!apply) {
                System.out.println("(dry run -- pass 'apply' to write)");
                return;
            }

            byte[] newDecompressed = SpliceItemOption.joinChunks(contents);
            Container container = new Container(archive.getCompression(), -1);
            container.compress(newDecompressed, null);

            storage.store(configs.getId(), archive.getArchiveId(), container.data);
            archive.setCrc(container.crc);
            archive.setRevision(archive.getRevision() + 1);
            archive.setCompressedSize(container.data.length);
            archive.setDecompressedSize(newDecompressed.length);

            IndexData indexData = configs.toIndexData();
            byte[] rawIndex = indexData.writeIndexData();
            Container idxContainer = new Container(configs.getCompression(), -1);
            idxContainer.compress(rawIndex, null);
            storage.store(255, configs.getId(), idxContainer.data);
            configs.setCrc(idxContainer.crc);

            System.out.println("APPLY complete. config[" + ConfigType.OBJECT.getId() + "] revision now " + archive.getRevision() + ".");
        }
    }

    static int fixModel(int model) {
        Integer fixed = MODEL_ID_FIX.get(model);
        return fixed != null ? fixed : model;
    }

    static byte[] reencode(int id, byte[] raw) {
        InputStream is = new InputStream(raw);
        OutputStream os = new OutputStream(raw.length + 8);
        while (true) {
            int opcode = is.readUnsignedByte();
            if (opcode == 0) {
                os.writeByte(0);
                break;
            }
            if (opcode == 6) {
                // Wide multi-model+shape -> narrow opcode 1.
                int count = is.readUnsignedByte();
                int[] models = new int[count];
                int[] shapes = new int[count];
                for (int i = 0; i < count; i++) {
                    models[i] = is.readInt();
                    shapes[i] = is.readUnsignedByte();
                }
                os.writeByte(1);
                os.writeByte(count);
                for (int i = 0; i < count; i++) {
                    int m = fixModel(models[i]);
                    if (m < 0 || m > 65535) {
                        throw new IllegalStateException("object " + id + ": model " + m + " does not fit narrow encoding");
                    }
                    os.writeShort(m);
                    os.writeByte(shapes[i]);
                }
            } else if (opcode == 7) {
                // Wide model list (no shapes) -> narrow opcode 5.
                int count = is.readUnsignedByte();
                int[] models = new int[count];
                for (int i = 0; i < count; i++) models[i] = is.readInt();
                os.writeByte(5);
                os.writeByte(count);
                for (int i = 0; i < count; i++) {
                    int m = fixModel(models[i]);
                    if (m < 0 || m > 65535) {
                        throw new IllegalStateException("object " + id + ": model " + m + " does not fit narrow encoding");
                    }
                    os.writeShort(m);
                }
            } else {
                copyKnownOpcode(id, opcode, is, os);
            }
        }
        return os.flip();
    }

    /** Every opcode these 121 records use, other than 6/7, copied through byte-for-byte unchanged. */
    static void copyKnownOpcode(int id, int opcode, InputStream is, OutputStream os) {
        os.writeByte(opcode);
        if (opcode == 1) {
            int count = is.readUnsignedByte();
            os.writeByte(count);
            for (int i = 0; i < count; i++) { os.writeShort(is.readUnsignedShort()); os.writeByte(is.readUnsignedByte()); }
        } else if (opcode == 2 || opcode == 3) {
            os.writeString(is.readString());
        } else if (opcode == 5) {
            int count = is.readUnsignedByte();
            os.writeByte(count);
            for (int i = 0; i < count; i++) os.writeShort(is.readUnsignedShort());
        } else if (opcode == 14 || opcode == 15) {
            os.writeByte(is.readUnsignedByte());
        } else if (opcode == 17 || opcode == 18) {
            // no payload
        } else if (opcode == 19) {
            os.writeByte(is.readUnsignedByte());
        } else if (opcode == 21 || opcode == 22 || opcode == 23) {
            // no payload
        } else if (opcode == 24) {
            os.writeShort(is.readUnsignedShort());
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
        } else if (opcode == 62 || opcode == 64) {
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
            throw new RuntimeException("copyKnownOpcode: unrecognized loc opcode " + opcode + " for object " + id);
        }
    }
}
