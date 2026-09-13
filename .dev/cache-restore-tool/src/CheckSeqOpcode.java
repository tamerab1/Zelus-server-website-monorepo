import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.ArchiveFiles;
import net.runelite.cache.fs.FSFile;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.io.InputStream;

import java.io.File;

// Read-only. Reports the raw opcode SEQUENCE used by each given sequence (animation) id, using the
// authoritative table from RS-Realm-Server-Package's own SeqTypeDecoder.kt (rsmod), so it can be
// compared against what the client's own (obfuscated, unpatchable) sequence decoder supports --
// same investigative technique as CheckSpotanimOpcode.java.
public class CheckSeqOpcode {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Index configs = store.getIndex(IndexType.CONFIGS);
            Archive archive = configs.getArchive(ConfigType.SEQUENCE.getId());
            ArchiveFiles files = archive.getFiles(storage.loadArchive(archive));

            for (int i = 1; i < args.length; i++) {
                int id = Integer.parseInt(args[i]);
                FSFile file = files.findFile(id);
                if (file == null) { System.out.println(id + " NOT FOUND"); continue; }
                byte[] b = file.getContents();
                InputStream is = new InputStream(b);
                StringBuilder sb = new StringBuilder();
                sb.append(id).append(" (").append(b.length).append(" bytes): opcodes=[");
                try {
                    while (true) {
                        int opcode = is.readUnsignedByte();
                        if (opcode == 0) break;
                        sb.append(opcode).append(",");
                        skip(opcode, is);
                    }
                    sb.append("]");
                    System.out.println(sb);
                } catch (Exception e) {
                    System.out.println(sb + "] -- DECODE FAILED: " + e.getMessage());
                }
            }
        }
    }

    static void skip(int opcode, InputStream is) {
        if (opcode == 1) {
            int count = is.readUnsignedShort();
            for (int i = 0; i < count; i++) is.readUnsignedShort(); // delay[]
            for (int i = 0; i < count; i++) is.readUnsignedShort(); // frameGroup[]
            for (int i = 0; i < count; i++) is.readUnsignedShort(); // frameIndex[]
        } else if (opcode == 2) {
            is.readUnsignedShort();
        } else if (opcode == 3) {
            int count = is.readUnsignedByte();
            for (int i = 0; i < count; i++) is.readUnsignedByte();
        } else if (opcode == 4) {
            // no payload
        } else if (opcode == 5) {
            is.readUnsignedByte();
        } else if (opcode == 6 || opcode == 7) {
            is.readUnsignedShort();
        } else if (opcode == 8) {
            is.readUnsignedByte();
        } else if (opcode == 9 || opcode == 10) {
            is.readUnsignedByte();
        } else if (opcode == 11) {
            is.readUnsignedByte();
        } else if (opcode == 12) {
            int count = is.readUnsignedByte();
            for (int i = 0; i < count; i++) is.readUnsignedShort(); // iframeGroup[]
            for (int i = 0; i < count; i++) is.readUnsignedShort(); // iframeIndex[]
        } else if (opcode == 13) {
            is.readInt();
        } else if (opcode == 14) {
            int count = is.readUnsignedShort();
            for (int i = 0; i < count; i++) {
                is.readUnsignedShort(); is.readUnsignedShort();
                is.readUnsignedByte(); is.readUnsignedByte(); is.readUnsignedByte(); is.readUnsignedByte();
            }
        } else if (opcode == 15) {
            is.readUnsignedShort(); is.readUnsignedShort();
        } else if (opcode == 16) {
            is.readByte();
        } else if (opcode == 17) {
            int count = is.readUnsignedByte();
            for (int i = 0; i < count; i++) is.readUnsignedByte();
        } else if (opcode == 18) {
            is.readString();
        } else if (opcode == 19) {
            // no payload
        } else {
            throw new RuntimeException("unknown seq opcode " + opcode);
        }
    }
}
