import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.io.InputStream;
import net.runelite.cache.io.OutputStream;
import net.runelite.cache.index.FileData;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

// ROOT CAUSE (confirmed 2026-09-14): io.ruin.cache.NPCType#setCustomFields() overrides (previously
// tried: standAnimation/walkAnimation/models for id 16317) are SERVER-SIDE JAVA ONLY. The live
// OSRS client independently decodes readyAnim/walkAnim/models straight from its own downloaded
// copy of the RAW CACHE BYTES for that npc id -- it never sees Zelus's in-memory NPCType object at
// all. Every prior "fix" to Aggy's animation this session was invisible to the client by
// construction, exactly the same class of mistake already avoided for the 5 unique ITEMS earlier
// (where a real binary cache edit, not a Java field, was what actually mattered).
//
// This patches npc 16317's raw cache record in place: opcode 13 (standAnimation) and opcode 14
// (walkAnimation), both currently 4588 ("npc_mad_angel_idle", confirmed via DecodeSourceNpc.java to
// render as a frozen T-pose), are rewritten to 14453 ("human_golem_powered_up_idle") -- the exact
// animation id already confirmed live/working on the boss's own active combat form (16305). Both
// are plain ushort fields at the same byte width in and out, so this is a same-length in-place
// value swap, not a re-encode.
// mode: verify | apply
public class FixAggyAnimation {
    static final int NPC_ID = 16317;
    static final int OLD_ANIM = 4588;
    static final int NEW_ANIM = 14453;

    public static void main(String[] args) throws Exception {
        String mode = args[0];
        String cachePath = args[1];
        boolean apply = mode.equals("apply");
        if (!apply && !mode.equals("verify")) throw new IllegalArgumentException("mode must be verify or apply");

        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Index configs = store.getIndex(IndexType.CONFIGS);
            Archive archive = configs.getArchive(ConfigType.NPC.getId());
            byte[] decompressed = archive.decompress(store.getStorage().loadArchive(archive));
            FileData[] fileData = archive.getFileData();
            List<byte[]> contents = SpliceItemOption.splitChunks(decompressed, fileData.length);

            int slot = -1;
            for (int i = 0; i < fileData.length; i++) if (fileData[i].getId() == NPC_ID) slot = i;
            if (slot == -1) { System.out.println("VERIFY FAILED: npc " + NPC_ID + " not found"); return; }

            byte[] raw = contents.get(slot);
            int[] found = new int[2]; // [standOpcodeCount, walkOpcodeCount]
            byte[] patched = patch(raw, found);
            System.out.println("opcode 13 (standAnimation) occurrences patched: " + found[0]);
            System.out.println("opcode 14 (walkAnimation) occurrences patched: " + found[1]);
            boolean ok = found[0] == 1 && found[1] == 1;
            System.out.println(ok ? "VERIFY: OK (exactly one of each opcode found, as expected)."
                    : "VERIFY FAILED: expected exactly 1 of each opcode, see counts above.");
            if (!apply) { System.out.println("(dry run -- pass 'apply' to write)"); return; }
            if (!ok) { System.out.println("ABORTING apply: verify failed above. Nothing written."); return; }

            List<byte[]> mutableContents = new ArrayList<>(contents);
            mutableContents.set(slot, patched);
            byte[] newDecompressed = SpliceItemOption.joinChunks(mutableContents);
            net.runelite.cache.fs.Container container = new net.runelite.cache.fs.Container(archive.getCompression(), -1);
            container.compress(newDecompressed, null);

            store.getStorage().store(configs.getId(), ConfigType.NPC.getId(), container.data);
            archive.setCrc(container.crc);
            archive.setRevision(archive.getRevision() + 1);
            archive.setCompressedSize(container.data.length);
            archive.setDecompressedSize(newDecompressed.length);
            PackMadAngel.writeIndexReferenceTable(store.getStorage(), configs);
            System.out.println("APPLIED. config[npc] revision now " + archive.getRevision());
        }
    }

    /** Copies every opcode through unchanged except 13/14, whose ushort payload is rewritten from
     * OLD_ANIM to NEW_ANIM (only if it actually equals OLD_ANIM, and counts occurrences either way
     * for the verify step). Uses the same real modern npc opcode width table already proven against
     * this exact record (ScanNpcOpcodeUsage.skipCopy). */
    static byte[] patch(byte[] raw, int[] countsOut) {
        InputStream in = new InputStream(raw);
        OutputStream out = new OutputStream(raw.length);
        while (true) {
            int code = in.readUnsignedByte();
            switch (code) {
                case 0:
                    out.writeByte(0);
                    return out.flip();
                case 13: {
                    int val = in.readUnsignedShort();
                    countsOut[0]++;
                    out.writeByte(13);
                    out.writeShort(val == OLD_ANIM ? NEW_ANIM : val);
                    break;
                }
                case 14: {
                    int val = in.readUnsignedShort();
                    countsOut[1]++;
                    out.writeByte(14);
                    out.writeShort(val == OLD_ANIM ? NEW_ANIM : val);
                    break;
                }
                default:
                    out.writeByte(code);
                    ScanNpcOpcodeUsage.skipCopy(in, out, code);
            }
        }
    }
}
