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
import java.util.List;

// Read Zelus cache, rewrite in place. The 4 already-imported Mad Angel npc records (16305-16308)
// use opcodes 61/62 (wide 4-byte model arrays) and 252 (an unexplained 14-byte field) -- both
// newer additions that crashed Zelus's OWN NPCType.java at boot (now fixed) AND, per the client
// crash after connecting, the game CLIENT's own npc decoder too, which is raw/obfuscated bytecode
// this project cannot patch the way NPCType.java was patched.
//
// Her actual model ids (61845/61847/61786/etc) all comfortably fit in an unsigned short, so opcode
// 61's wide encoding was never NECESSARY for her specifically -- it's just what the source cache
// happened to use. This re-encodes those 4 records to the older, universally-understood opcodes
// (61->1, 62->60) and drops opcode 252 entirely (rsmod's own decoder calls it "read past, not
// understood" -- nothing anywhere depends on its content), producing bytes indistinguishable in
// format from every other already-working npc in the cache.
//
// mode: verify | apply
public class LegacyEncodeMadAngelNpcs {
    static final int[] NPC_IDS = {16305, 16306, 16307, 16308};

    public static void main(String[] args) throws Exception {
        String mode = args[0];
        String cachePath = args[1];
        boolean apply = mode.equals("apply");
        if (!apply && !mode.equals("verify")) throw new IllegalArgumentException("mode must be verify or apply");

        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Index configs = store.getIndex(IndexType.CONFIGS);
            Archive archive = configs.getArchive(ConfigType.NPC.getId());
            byte[] decompressed = archive.decompress(storage.loadArchive(archive));
            FileData[] fileData = archive.getFileData();
            List<byte[]> contents = SpliceItemOption.splitChunks(decompressed, fileData.length);

            for (int id : NPC_IDS) {
                int slot = -1;
                for (int j = 0; j < fileData.length; j++) if (fileData[j].getId() == id) slot = j;
                if (slot == -1) {
                    throw new IllegalStateException("npc " + id + " not found -- run the pack step first.");
                }
                byte[] before = contents.get(slot);
                byte[] after = reencode(before);
                System.out.println("npc " + id + ": " + before.length + " bytes -> " + after.length + " bytes"
                        + (java.util.Arrays.equals(before, after) ? " (no change needed)" : " (opcodes downgraded)"));
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

            System.out.println("APPLY complete. config[" + ConfigType.NPC.getId() + "] revision now " + archive.getRevision() + ".");
        }
    }

    static byte[] reencode(byte[] raw) {
        InputStream is = new InputStream(raw);
        OutputStream os = new OutputStream(raw.length + 8);
        while (true) {
            int opcode = is.readUnsignedByte();
            if (opcode == 0) {
                os.writeByte(0);
                break;
            }
            if (opcode == 61) {
                int count = is.readUnsignedByte();
                int[] models = new int[count];
                for (int i = 0; i < count; i++) models[i] = is.readInt();
                for (int m : models) {
                    if (m < 0 || m > 65535) {
                        throw new IllegalStateException("model id " + m + " does not fit narrow opcode 1 -- cannot downgrade, keep opcode 61");
                    }
                }
                os.writeByte(1);
                os.writeByte(count);
                for (int m : models) os.writeShort(m);
            } else if (opcode == 62) {
                int count = is.readUnsignedByte();
                int[] models = new int[count];
                for (int i = 0; i < count; i++) models[i] = is.readInt();
                for (int m : models) {
                    if (m < 0 || m > 65535) {
                        throw new IllegalStateException("chathead model id " + m + " does not fit narrow opcode 60 -- cannot downgrade, keep opcode 62");
                    }
                }
                os.writeByte(60);
                os.writeByte(count);
                for (int m : models) os.writeShort(m);
            } else if (opcode == 252) {
                is.skip(14); // dropped entirely -- see class javadoc
            } else {
                // Every other opcode: copy through byte-for-byte using the same field-width table
                // as DumpNpcModelsV2 (already validated against every opcode these 4 records use).
                copyKnownOpcode(opcode, is, os);
            }
        }
        return os.flip();
    }

    static void copyKnownOpcode(int opcode, InputStream is, OutputStream os) {
        os.writeByte(opcode);
        if (opcode == 1) {
            int count = is.readUnsignedByte();
            os.writeByte(count);
            for (int i = 0; i < count; i++) os.writeShort(is.readUnsignedShort());
        } else if (opcode == 2 || opcode == 3) {
            os.writeString(is.readString());
        } else if (opcode == 12) {
            os.writeByte(is.readUnsignedByte());
        } else if (opcode >= 13 && opcode <= 16) {
            os.writeShort(is.readUnsignedShort());
        } else if (opcode == 17) {
            for (int i = 0; i < 4; i++) os.writeShort(is.readUnsignedShort());
        } else if (opcode == 18) {
            os.writeShort(is.readUnsignedShort());
        } else if (opcode >= 30 && opcode < 35) {
            os.writeString(is.readString());
        } else if (opcode == 40 || opcode == 41) {
            int count = is.readUnsignedByte();
            os.writeByte(count);
            for (int i = 0; i < count; i++) { os.writeShort(is.readUnsignedShort()); os.writeShort(is.readUnsignedShort()); }
        } else if (opcode == 60) {
            int count = is.readUnsignedByte();
            os.writeByte(count);
            for (int i = 0; i < count; i++) os.writeShort(is.readUnsignedShort());
        } else if (opcode >= 74 && opcode <= 79) {
            os.writeShort(is.readUnsignedShort());
        } else if (opcode == 93 || opcode == 99 || opcode == 107 || opcode == 109 || opcode == 111
                || opcode == 122 || opcode == 123 || opcode == 129 || opcode == 130 || opcode == 145 || opcode == 147
                || opcode == 210) {
            // no payload
        } else if (opcode == 95) {
            os.writeShort(is.readUnsignedShort());
        } else if (opcode == 97 || opcode == 98) {
            os.writeShort(is.readUnsignedShort());
        } else if (opcode == 100 || opcode == 101) {
            os.writeByte(is.readByte());
        } else if (opcode == 102) {
            // Not observed in any of the 4 Mad Angel records (verified via DumpNpcModelsV2's full
            // decode earlier); a correct passthrough needs faithfully round-tripping its variable
            // -length "large smart" encoding, which isn't worth building for a path never actually
            // exercised. Fail loudly rather than risk a silent, half-correct passthrough.
            throw new RuntimeException("copyKnownOpcode: opcode 102 (head icons) unexpectedly present -- "
                    + "this needs a real smart-encoding passthrough implementation before proceeding.");
        } else if (opcode == 103) {
            os.writeShort(is.readUnsignedShort());
        } else if (opcode == 106 || opcode == 118) {
            os.writeShort(is.readUnsignedShort());
            os.writeShort(is.readUnsignedShort());
            if (opcode == 118) os.writeShort(is.readUnsignedShort());
            int count = is.readUnsignedByte();
            os.writeByte(count);
            for (int i = 0; i <= count; i++) os.writeShort(is.readUnsignedShort());
        } else if (opcode == 114 || opcode == 116) {
            os.writeShort(is.readUnsignedShort());
        } else if (opcode == 115 || opcode == 117) {
            for (int i = 0; i < 4; i++) os.writeShort(is.readUnsignedShort());
        } else if (opcode == 124 || opcode == 126) {
            os.writeShort(is.readUnsignedShort());
        } else if (opcode == 146) {
            os.writeShort(is.readUnsignedShort());
        } else if (opcode >= 200 && opcode <= 202) {
            os.writeByte(is.readUnsignedByte());
        } else if (opcode == 203) {
            int count = is.readUnsignedByte() + 1;
            os.writeByte(count - 1);
            for (int i = 0; i < count; i++) { os.writeInt(is.readInt()); os.writeByte(is.readUnsignedByte()); }
        } else if (opcode == 204 || opcode == 211 || opcode == 213 || opcode == 214 || opcode == 215 || opcode == 216) {
            os.writeShort(is.readUnsignedShort());
        } else if (opcode >= 205 && opcode <= 209) {
            os.writeByte(is.readUnsignedByte());
        } else if (opcode == 212) {
            os.writeByte(is.readUnsignedByte());
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
            throw new RuntimeException("copyKnownOpcode: truly unexpected opcode " + opcode
                    + " -- this record uses something DumpNpcModelsV2 never saw either; investigate before proceeding.");
        }
    }
}
