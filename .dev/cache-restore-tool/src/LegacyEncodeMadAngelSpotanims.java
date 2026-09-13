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

// Read Zelus cache, rewrite in place. All 8 imported Mad Angel spotanims (4010-4017) use opcode 3
// (wide 4-byte model id) -- confirmed via CheckSpotanimOpcode.java: every one of them is
// [2,3,7,8]. This is the SAME "wide model opcode missing from this client build" pattern already
// found and fixed twice tonight (npc opcode 61/62, loc/object opcode 6/7) -- the client's own
// (unpatchable, obfuscated) spotanim decoder almost certainly only understands opcode 1 (narrow),
// matching how its npc and loc decoders behaved. The visible symptom -- a "null head" rendering on
// the player during her ranged/magic attacks (GFX_SMITE_HIT = spotanim 4013, played via
// t.graphics(...) in MadAngel.basicAttack) -- matches this exactly: a spotanim whose model
// reference the client can't read renders as a broken/empty placeholder instead of the real model.
// All 8 models (61842, 60639, 60639, 61848, 60632 x4) fit comfortably under 65535, so this is a
// pure format downgrade with no id remapping needed.
//
// mode: verify | apply
public class LegacyEncodeMadAngelSpotanims {
    static final int[] SPOTANIM_IDS = {4010, 4011, 4012, 4013, 4014, 4015, 4016, 4017};

    public static void main(String[] args) throws Exception {
        String mode = args[0];
        String cachePath = args[1];
        boolean apply = mode.equals("apply");
        if (!apply && !mode.equals("verify")) throw new IllegalArgumentException("mode must be verify or apply");

        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Index configs = store.getIndex(IndexType.CONFIGS);
            Archive archive = configs.getArchive(ConfigType.SPOTANIM.getId());
            byte[] decompressed = archive.decompress(storage.loadArchive(archive));
            FileData[] fileData = archive.getFileData();
            List<byte[]> contents = SpliceItemOption.splitChunks(decompressed, fileData.length);

            for (int id : SPOTANIM_IDS) {
                int slot = -1;
                for (int j = 0; j < fileData.length; j++) if (fileData[j].getId() == id) slot = j;
                if (slot == -1) throw new IllegalStateException("spotanim " + id + " not found");

                byte[] before = contents.get(slot);
                byte[] after = reencode(id, before);
                System.out.println("spotanim " + id + ": " + before.length + " -> " + after.length + " bytes"
                        + (Arrays.equals(before, after) ? " (no change)" : " (downgraded)"));
                contents.set(slot, after);
            }

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

            System.out.println("APPLY complete. config[" + ConfigType.SPOTANIM.getId() + "] revision now " + archive.getRevision() + ".");
        }
    }

    static byte[] reencode(int id, byte[] raw) {
        InputStream is = new InputStream(raw);
        OutputStream os = new OutputStream(raw.length);
        while (true) {
            int opcode = is.readUnsignedByte();
            if (opcode == 0) {
                os.writeByte(0);
                break;
            }
            if (opcode == 3) {
                int model = is.readInt();
                if (model < 0 || model > 65535) {
                    throw new IllegalStateException("spotanim " + id + ": model " + model + " does not fit narrow encoding");
                }
                os.writeByte(1);
                os.writeShort(model);
            } else if (opcode == 1) {
                os.writeByte(1);
                os.writeShort(is.readUnsignedShort());
            } else if (opcode == 2) {
                os.writeByte(2);
                os.writeShort(is.readUnsignedShort());
            } else if (opcode == 4 || opcode == 5 || opcode == 6) {
                os.writeByte(opcode);
                os.writeShort(is.readUnsignedShort());
            } else if (opcode == 7 || opcode == 8) {
                os.writeByte(opcode);
                os.writeByte(is.readUnsignedByte());
            } else if (opcode == 9) {
                os.writeByte(9);
                os.writeString(is.readString());
            } else if (opcode == 10) {
                os.writeByte(10);
                // no payload
            } else if (opcode == 40 || opcode == 41) {
                os.writeByte(opcode);
                int count = is.readUnsignedByte();
                os.writeByte(count);
                for (int i = 0; i < count; i++) { os.writeShort(is.readUnsignedShort()); os.writeShort(is.readUnsignedShort()); }
            } else {
                throw new RuntimeException("reencode: unrecognized spotanim opcode " + opcode + " for id " + id);
            }
        }
        return os.flip();
    }
}
