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

// Read Zelus cache, rewrite in place. After downgrading opcode 6/7 (LegacyEncodeCathedralObjects),
// ScanClientUnsafeOpcodes + direct disassembly of the client's LocType decoder (ik.class) confirmed
// 4 more opcodes genuinely absent from its dispatch -- unlike 2/24/32/64 (which turned out to be
// scanner false positives, individually verified present in the bytecode), NONE of 93, 96, 101, 249
// appear anywhere in its ~2400-instruction method. These affect 8 objects total: 62205/62206/62207
// (opcode 96, "raise"), 62219 (opcode 93, sound fade curves), 62265/62266/62267 (opcode 249, custom
// params), 62375 (opcode 101, area sound trigger). All four are cosmetic/audio fields with nothing
// server-side depending on their content (same reasoning as dropping npc opcode 252 earlier
// tonight) -- this strips them out entirely rather than downgrading, since there is no narrower
// equivalent opcode for the client to fall back to.
//
// mode: verify | apply
public class StripUnsupportedObjectOpcodes {
    static final int[] OBJECT_IDS = {62205, 62206, 62207, 62219, 62265, 62266, 62267, 62375};
    static final Set<Integer> STRIP = new HashSet<>(Arrays.asList(93, 96, 101, 249));

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

            for (int id : OBJECT_IDS) {
                int slot = -1;
                for (int j = 0; j < fileData.length; j++) if (fileData[j].getId() == id) slot = j;
                if (slot == -1) throw new IllegalStateException("object " + id + " not found");

                byte[] before = contents.get(slot);
                byte[] after = strip(id, before);
                System.out.println("object " + id + ": " + before.length + " -> " + after.length + " bytes");
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

            System.out.println("APPLY complete. config[" + ConfigType.OBJECT.getId() + "] revision now " + archive.getRevision() + ".");
        }
    }

    static byte[] strip(int id, byte[] raw) {
        InputStream is = new InputStream(raw);
        OutputStream os = new OutputStream(raw.length);
        while (true) {
            int opcode = is.readUnsignedByte();
            if (opcode == 0) {
                os.writeByte(0);
                break;
            }
            if (STRIP.contains(opcode)) {
                skipPayload(opcode, is);
            } else {
                LegacyEncodeCathedralObjects.copyKnownOpcode(id, opcode, is, os);
            }
        }
        return os.flip();
    }

    static void skipPayload(int opcode, InputStream is) {
        if (opcode == 93) {
            is.readUnsignedByte(); is.readUnsignedShort(); is.readUnsignedByte(); is.readUnsignedShort();
        } else if (opcode == 96) {
            is.readUnsignedByte();
        } else if (opcode == 101) {
            is.readUnsignedByte(); is.readUnsignedShort(); is.readUnsignedShort(); is.readInt(); is.readInt(); is.readString();
        } else if (opcode == 249) {
            int length = is.readUnsignedByte();
            for (int i = 0; i < length; i++) {
                boolean isString = is.readUnsignedByte() == 1;
                is.read24BitInt();
                if (isString) is.readString(); else is.readInt();
            }
        } else {
            throw new IllegalStateException("skipPayload: opcode " + opcode + " not in strip list");
        }
    }
}
