import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.ArchiveFiles;
import net.runelite.cache.fs.FSFile;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.io.InputStream;

import java.io.File;

// Read-only: lists every raw opcode byte present in a sequence record (no decoding of payload
// meaning, just opcode numbers) so an editing tool only needs to handle what's ACTUALLY there.
public class ListSeqOpcodes {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Archive seqArchive = store.getIndex(IndexType.CONFIGS).getArchive(ConfigType.SEQUENCE.getId());
            ArchiveFiles seqFiles = seqArchive.getFiles(storage.loadArchive(seqArchive));

            for (int i = 1; i < args.length; i++) {
                int seqId = Integer.parseInt(args[i]);
                FSFile file = seqFiles.findFile(seqId);
                if (file == null || file.getContents() == null) {
                    System.out.println("seq " + seqId + " -- NOT FOUND");
                    continue;
                }
                byte[] raw = file.getContents();
                System.out.println("seq " + seqId + " (" + raw.length + " bytes):");
                InputStream is = new InputStream(raw);
                while (true) {
                    int start = is.getOffset();
                    int opcode = is.readUnsignedByte();
                    if (opcode == 0) {
                        System.out.println("  [end at offset " + start + "]");
                        break;
                    }
                    System.out.println("  opcode " + opcode + " at offset " + start);
                    skip(is, opcode);
                }
            }
        }
    }

    // Byte-exact skip matching SequenceLoader's real opcode table (client cache module), needed
    // so the loop above can find the NEXT opcode byte correctly. Confirmed rev226=false for this
    // cache (opcode 14 decodes as a plain 4-byte animMayaID, which per SequenceLoader's real
    // conditional table only happens when rev226 is false).
    static void skip(InputStream is, int opcode) {
        if (opcode == 1) {
            int count = is.readUnsignedShort();
            for (int i = 0; i < count; i++) is.readUnsignedShort();
            for (int i = 0; i < count; i++) is.readUnsignedShort();
            for (int i = 0; i < count; i++) is.readUnsignedShort();
        } else if (opcode == 2) {
            is.readUnsignedShort();
        } else if (opcode == 3) {
            int count = is.readUnsignedByte();
            for (int i = 0; i < count; i++) is.readUnsignedByte();
        } else if (opcode == 4) {
            // no payload
        } else if (opcode == 5) {
            is.readUnsignedByte();
        } else if (opcode == 6) {
            is.readUnsignedShort();
        } else if (opcode == 7) {
            is.readUnsignedShort();
        } else if (opcode == 8) {
            is.readUnsignedByte();
        } else if (opcode == 9) {
            is.readUnsignedByte();
        } else if (opcode == 10) {
            is.readUnsignedByte();
        } else if (opcode == 11) {
            is.readUnsignedByte();
        } else if (opcode == 12) {
            int count = is.readUnsignedByte();
            for (int i = 0; i < count; i++) is.readUnsignedShort();
            for (int i = 0; i < count; i++) is.readUnsignedShort();
        } else if (opcode == 13) {
            int count = is.readUnsignedByte();
            for (int i = 0; i < count; i++) is.readBytes(new byte[3]);
        } else if (opcode == 14) {
            is.readInt();
        } else if (opcode == 15) {
            int count = is.readUnsignedShort();
            for (int i = 0; i < count; i++) {
                is.readUnsignedShort();
                is.readBytes(new byte[3]);
            }
        } else if (opcode == 16) {
            is.readUnsignedShort();
            is.readUnsignedShort();
        } else if (opcode == 17) {
            int count = is.readUnsignedByte();
            for (int i = 0; i < count; i++) is.readUnsignedByte();
        } else if (opcode == 18) {
            is.readString();
        } else {
            throw new RuntimeException("unmapped opcode " + opcode + " -- inspect manually");
        }
    }
}
