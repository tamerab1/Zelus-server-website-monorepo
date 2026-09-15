import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Store;
import net.runelite.cache.io.InputStream;
import net.runelite.cache.io.OutputStream;

import java.io.File;

// Imports Aggy's pet-follower npc (source id 16317, confirmed via DecodeSourceNpc.java against
// RS-Realm-Server-Package's real "game" cache) into Zelus at the same id (16317, free -- checked
// via CheckNpcExists.java). Both models it references (body 61845, head 61786) and its animation
// (seq 4588) already exist in Zelus's own cache (imported earlier as part of the boss's own asset
// batch / already-present vanilla content), so this needs NO model or animation import -- only the
// npc config record itself, with its two model opcodes narrowed from modern wide (61/62, proven by
// ScanNpcOpcodeUsage.java to be used by zero of Zelus's 15,421 pre-existing npcs -- the same
// client-incompatible modern-cache-revision issue already fixed for the 5 unique items) to the
// classic narrow opcodes (1/60) every other npc in this cache already uses. No remap needed since
// both model ids already sit under 65536 at their existing Zelus slots.
// mode: verify | apply
public class ImportAggyPet {
    static final int NPC_ID = 16317;

    public static void main(String[] args) throws Exception {
        String mode = args[0];
        String srcPath = args[1];
        String dstPath = args[2];
        boolean apply = mode.equals("apply");
        if (!apply && !mode.equals("verify")) throw new IllegalArgumentException("mode must be verify or apply");

        try (Store src = new Store(new File(srcPath)); Store dst = new Store(new File(dstPath))) {
            src.load();
            dst.load();

            var srcConfigs = src.getIndex(IndexType.CONFIGS);
            var srcArchive = srcConfigs.getArchive(ConfigType.NPC.getId());
            if (srcArchive == null) { System.out.println("VERIFY FAILED: source NPC archive missing"); return; }
            var srcFileData = srcArchive.getFileData();
            int srcSlot = -1;
            for (int i = 0; i < srcFileData.length; i++) if (srcFileData[i].getId() == NPC_ID) srcSlot = i;
            if (srcSlot == -1) { System.out.println("VERIFY FAILED: npc " + NPC_ID + " not found in source"); return; }

            var dstConfigs = dst.getIndex(IndexType.CONFIGS);
            var dstArchive = dstConfigs.getArchive(ConfigType.NPC.getId());
            var dstFileData = dstArchive.getFileData();
            boolean occupied = false;
            for (var fd : dstFileData) if (fd.getId() == NPC_ID) occupied = true;
            System.out.println("npc target " + NPC_ID + ": " + (occupied ? "OCCUPIED -- CONFLICT" : "free"));
            if (occupied) { System.out.println("VERIFY FAILED"); return; }
            System.out.println("VERIFY: OK.");
            if (!apply) { System.out.println("(dry run -- pass 'apply' to write)"); return; }

            var srcDecompressed = srcArchive.decompress(src.getStorage().loadArchive(srcArchive));
            var srcContents = SpliceItemOption.splitChunks(srcDecompressed, srcFileData.length);
            byte[] raw = srcContents.get(srcSlot);
            byte[] narrowed = narrowify(raw);
            System.out.println("npc " + NPC_ID + ": narrowed " + raw.length + " -> " + narrowed.length + " bytes");

            int[][] pairs = {{NPC_ID, NPC_ID}};
            PackMadAngel.applyConfig(src.getStorage(), srcConfigs, dst.getStorage(), dstConfigs,
                    ConfigType.NPC.getId(), pairs, (id, r) -> narrowed);

            System.out.println("=== DONE. ===");
        }
    }

    static byte[] narrowify(byte[] raw) {
        InputStream in = new InputStream(raw);
        OutputStream out = new OutputStream(raw.length);
        while (true) {
            int code = in.readUnsignedByte();
            switch (code) {
                case 0: out.writeByte(0); return out.flip();
                case 61: { // wide models -> classic opcode 1
                    int count = in.readUnsignedByte();
                    out.writeByte(1);
                    out.writeByte(count);
                    for (int i = 0; i < count; i++) out.writeShort(in.readInt());
                    break;
                }
                case 62: { // wide heads -> classic opcode 60
                    int count = in.readUnsignedByte();
                    out.writeByte(60);
                    out.writeByte(count);
                    for (int i = 0; i < count; i++) out.writeShort(in.readInt());
                    break;
                }
                default:
                    out.writeByte(code);
                    ScanNpcOpcodeUsage.skipCopy(in, out, code);
            }
        }
    }
}
