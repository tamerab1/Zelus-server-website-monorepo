import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;
import net.runelite.cache.io.InputStream;

import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

// Read-only. Prints the RAW OPCODE SEQUENCE for a given SEQUENCE (animation) record, using the
// real modern opcode table (SeqTypeDecoder.kt, read directly from RS-Realm-Server-Package this
// session), and flags any opcode Zelus's own io.ruin.cache.SeqType#decode(InBuffer,int) either (a)
// doesn't handle at all (no final else -- silently consumes 0 bytes, desyncing everything after) or
// (b) handles with an INCOMPATIBLE format -- opcode 13 is the dangerous case: modern format is a
// single 4-byte int ("keyframeSet"), but Zelus's opcode 13 reads a byte COUNT followed by count*3
// bytes of "medium" values (an old, unrelated field) -- worse than silently skipping, it consumes
// the WRONG number of bytes, corrupting the frame data table (opcode 1) if 13 appears after it, or
// corrupting anything following 13 if it appears before other fields.
public class TraceSeqOpcodes {
    // Opcodes Zelus's SeqType.java recognizes AT ALL (whether or not the format actually matches
    // the modern encoding -- see WRONG_FORMAT below for the ones that are outright incompatible).
    static final Set<Integer> ZELUS_SUPPORTED = new TreeSet<>(java.util.Arrays.asList(
            1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13
    ));
    // Opcodes Zelus DOES have a branch for, but whose byte WIDTH/shape does not match the modern
    // encoder that produced these cache records -- these are worse than a plain gap, since Zelus's
    // decoder will consume some wrong number of bytes rather than cleanly skip zero.
    static final Set<Integer> WRONG_FORMAT_IN_ZELUS = new TreeSet<>(java.util.Arrays.asList(13));

    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        int[] ids = new int[args.length - 1];
        for (int i = 1; i < args.length; i++) ids[i - 1] = Integer.parseInt(args[i]);

        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Archive archive = store.getIndex(IndexType.CONFIGS).getArchive(ConfigType.SEQUENCE.getId());
            byte[] decompressed = archive.decompress(storage.loadArchive(archive));
            FileData[] fileData = archive.getFileData();
            List<byte[]> contents = SpliceItemOption.splitChunks(decompressed, fileData.length);

            for (int id : ids) {
                int slot = -1;
                for (int j = 0; j < fileData.length; j++) if (fileData[j].getId() == id) slot = j;
                if (slot == -1) { System.out.println(id + " -- NOT FOUND"); continue; }
                traceOpcodes(id, contents.get(slot));
            }
        }
    }

    static void traceOpcodes(int id, byte[] b) {
        InputStream in = new InputStream(b);
        StringBuilder seq = new StringBuilder();
        int firstProblem = -1;
        String problemKind = null;
        while (true) {
            int code = in.readUnsignedByte();
            if (code == 0) break;
            boolean supported = ZELUS_SUPPORTED.contains(code);
            boolean wrongFormat = WRONG_FORMAT_IN_ZELUS.contains(code);
            String flag = !supported ? "*" : (wrongFormat ? "!" : "");
            seq.append(code).append(flag).append(" ");
            if (firstProblem == -1 && !supported) { firstProblem = code; problemKind = "UNHANDLED (silently skips 0 bytes)"; }
            if (firstProblem == -1 && wrongFormat) { firstProblem = code; problemKind = "WRONG FORMAT (reads incompatible byte width)"; }
            skipPayload(in, code);
        }
        System.out.println("seq " + id + " opcodes: " + seq.toString().trim()
                + (firstProblem == -1 ? "  [fully compatible with Zelus's SeqType]"
                : "  <<< FIRST PROBLEM: opcode " + firstProblem + " -- " + problemKind));
    }

    // Real modern width table, transcribed from SeqTypeDecoder.kt.
    static void skipPayload(InputStream in, int code) {
        switch (code) {
            case 1: {
                int count = in.readUnsignedShort();
                for (int i = 0; i < count; i++) in.readUnsignedShort(); // delay[]
                for (int i = 0; i < count; i++) in.readUnsignedShort(); // frameGroup[]
                for (int i = 0; i < count; i++) in.readUnsignedShort(); // frameIndex[]
                break;
            }
            case 2: in.readUnsignedShort(); break;
            case 3: {
                int count = in.readUnsignedByte();
                for (int i = 0; i < count; i++) in.readUnsignedByte();
                break;
            }
            case 4: break;
            case 5: in.readUnsignedByte(); break;
            case 6: in.readUnsignedShort(); break;
            case 7: in.readUnsignedShort(); break;
            case 8: in.readUnsignedByte(); break;
            case 9: in.readUnsignedByte(); break;
            case 10: in.readUnsignedByte(); break;
            case 11: in.readUnsignedByte(); break;
            case 12: {
                int count = in.readUnsignedByte();
                for (int i = 0; i < count; i++) in.readUnsignedShort(); // groups[]
                for (int i = 0; i < count; i++) in.readUnsignedShort(); // files[]
                break;
            }
            case 13: in.readInt(); break; // modern keyframeSet
            case 14: {
                int count = in.readUnsignedShort();
                for (int i = 0; i < count; i++) {
                    in.readUnsignedShort(); // index
                    in.readUnsignedShort(); // type
                    in.readUnsignedByte();  // weight
                    in.readUnsignedByte();  // loops
                    in.readUnsignedByte();  // range
                    in.readUnsignedByte();  // size
                }
                break;
            }
            case 15: in.readUnsignedShort(); in.readUnsignedShort(); break;
            case 16: in.readByte(); break;
            case 17: {
                int count = in.readUnsignedByte();
                for (int i = 0; i < count; i++) in.readUnsignedByte();
                break;
            }
            case 18: in.readString(); break;
            case 19: break;
            default:
                throw new RuntimeException("unrecognized modern seq opcode " + code);
        }
    }
}
