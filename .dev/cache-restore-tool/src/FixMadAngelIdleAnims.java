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

// Read source + Zelus caches, write Zelus only. Mad Angel's npc DEFINITIONS reference her
// readyAnim/walkAnim (opcodes 13/14) as raw source ids 4588 (16305 fighting / 16307 rising) and
// 1991 (16306 dormant / 16308 dead) -- both missed by the original PackMadAngel pass, which only
// remapped the 4 ids MadAngel.java's own animate() calls use. Both ids are OCCUPIED in Zelus by
// unrelated pre-existing content (confirmed via CheckIdleWalkAnimIds: different byte lengths than
// source), which is why she visibly plays a random unrelated pose between explicit animate() calls
// instead of her own idle/hover animation.
//
// This: (1) imports the real seq 4588 -> Zelus 14453 and seq 1991 -> Zelus 14454 (byte-for-byte,
// reusing PackMadAngel's own proven applyConfig merge-insert logic), then (2) rewrites the 4
// already-imported npc records in place so their opcode-13/14 payloads point at the new ids instead
// of the raw source ones.
//
// mode: verify | apply
public class FixMadAngelIdleAnims {
    static final int SRC_READY_FIGHTING = 4588; // 16305 / 16307
    static final int SRC_READY_DORMANT = 1991;  // 16306 / 16308
    static final int NEW_READY_FIGHTING = 14453;
    static final int NEW_READY_DORMANT = 14454;

    static final int[] NPC_IDS = {16305, 16306, 16307, 16308};

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
            ok &= PackMadAngel.verifyConfigPairs(dst, ConfigType.SEQUENCE.getId(), new int[][]{
                    {SRC_READY_FIGHTING, NEW_READY_FIGHTING}, {SRC_READY_DORMANT, NEW_READY_DORMANT}
            });
            System.out.println(ok ? "VERIFY: both new seq ids still free." : "VERIFY: FAILED -- see above.");
            if (!apply) {
                System.out.println("(dry run -- pass 'apply' to write)");
                return;
            }
            if (!ok) {
                System.out.println("ABORTING apply: verify failed above. Nothing written.");
                return;
            }

            System.out.println("=== APPLY: import the 2 missing idle/walk sequences ===");
            PackMadAngel.applyConfig(srcStorage, src.getIndex(IndexType.CONFIGS), dstStorage, dst.getIndex(IndexType.CONFIGS),
                    ConfigType.SEQUENCE.getId(), new int[][]{
                            {SRC_READY_FIGHTING, NEW_READY_FIGHTING}, {SRC_READY_DORMANT, NEW_READY_DORMANT}
                    }, null);

            System.out.println("=== APPLY: repoint npc readyAnim/walkAnim (13/14) in the 4 npc records ===");
            repointNpcAnims(dstStorage, dst.getIndex(IndexType.CONFIGS));

            System.out.println("=== DONE. ===");
        }
    }

    static void repointNpcAnims(Storage storage, Index configs) throws Exception {
        Archive archive = configs.getArchive(ConfigType.NPC.getId());
        byte[] decompressed = archive.decompress(storage.loadArchive(archive));
        FileData[] fileData = archive.getFileData();
        List<byte[]> contents = SpliceItemOption.splitChunks(decompressed, fileData.length);

        for (int id : NPC_IDS) {
            int slot = -1;
            for (int j = 0; j < fileData.length; j++) if (fileData[j].getId() == id) slot = j;
            if (slot == -1) throw new IllegalStateException("npc " + id + " not found");

            int oldAnim = (id == 16305 || id == 16307) ? SRC_READY_FIGHTING : SRC_READY_DORMANT;
            int newAnim = (id == 16305 || id == 16307) ? NEW_READY_FIGHTING : NEW_READY_DORMANT;

            byte[] before = contents.get(slot);
            int[] replacedCount = {0};
            byte[] after = rewrite(before, oldAnim, newAnim, replacedCount);
            System.out.println("  npc " + id + ": " + before.length + " -> " + after.length + " bytes, "
                    + replacedCount[0] + " occurrence(s) of " + oldAnim + " repointed to " + newAnim);
            if (replacedCount[0] == 0) {
                throw new IllegalStateException("npc " + id + ": expected to find readyAnim/walkAnim=" + oldAnim + " but found none -- aborting.");
            }
            contents.set(slot, after);
        }

        byte[] newDecompressed = SpliceItemOption.joinChunks(contents);
        Container container = new Container(archive.getCompression(), -1);
        container.compress(newDecompressed, null);

        storage.store(configs.getId(), archive.getArchiveId(), container.data);
        archive.setCrc(container.crc);
        archive.setRevision(archive.getRevision() + 1);
        archive.setCompressedSize(container.data.length);
        archive.setDecompressedSize(newDecompressed.length);

        PackMadAngel.writeIndexReferenceTable(storage, configs);
        System.out.println("  config[" + ConfigType.NPC.getId() + "] revision now " + archive.getRevision());
    }

    /** Copies the record through byte-for-byte, substituting opcode-13/14 values equal to oldAnim. */
    static byte[] rewrite(byte[] raw, int oldAnim, int newAnim, int[] replacedCount) {
        InputStream is = new InputStream(raw);
        OutputStream os = new OutputStream(raw.length + 4);
        while (true) {
            int opcode = is.readUnsignedByte();
            os.writeByte(opcode);
            if (opcode == 0) break;
            if (opcode == 1) {
                int count = is.readUnsignedByte();
                os.writeByte(count);
                for (int i = 0; i < count; i++) os.writeShort(is.readUnsignedShort());
            } else if (opcode == 2 || opcode == 3) {
                os.writeString(is.readString());
            } else if (opcode == 12) {
                os.writeByte(is.readUnsignedByte());
            } else if (opcode == 13 || opcode == 14) {
                int v = is.readUnsignedShort();
                if (v == oldAnim) {
                    v = newAnim;
                    replacedCount[0]++;
                }
                os.writeShort(v);
            } else if (opcode == 15 || opcode == 16) {
                os.writeShort(is.readUnsignedShort());
            } else if (opcode == 17) {
                // {walkAnim, turnBackAnim, turnLeftAnim, turnRightAnim} -- only walkAnim (the
                // first short) is ever a stand/walk anim id in this record shape; substitute it too
                // if it matches, the other 3 pass through unchanged.
                int walk = is.readUnsignedShort();
                if (walk == oldAnim) {
                    walk = newAnim;
                    replacedCount[0]++;
                }
                os.writeShort(walk);
                os.writeShort(is.readUnsignedShort());
                os.writeShort(is.readUnsignedShort());
                os.writeShort(is.readUnsignedShort());
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
                int enabledFlags = is.readUnsignedByte();
                os.writeByte(enabledFlags);
                int count = Integer.bitCount(enabledFlags);
                for (int i = 0; i < count; i++) {
                    int group = is.readBigSmart2();
                    os.writeBigSmart(group);
                    int file = is.readUnsignedShortSmartMinusOne();
                    os.writeShortSmart(file + 1);
                }
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
            } else if (opcode == 252) {
                byte[] chunk = new byte[14];
                is.readBytes(chunk);
                os.writeBytes(chunk);
            } else {
                throw new RuntimeException("rewrite: unexpected opcode " + opcode + " -- update this table before proceeding.");
            }
        }
        return os.flip();
    }
}
