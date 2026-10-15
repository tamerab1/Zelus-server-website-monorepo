import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;
import net.runelite.cache.io.InputStream;

import java.io.File;
import java.util.List;

// Raw opcode-level walk of one or more npc def records (bypassing NpcLoader entirely, which
// silently mishandles/skips any opcode it doesn't recognize -- see NpcLoader.decodeValues's
// final `else { log.warn(...) }` branch, which does NOT consume the payload, so an unrecognized
// opcode desyncs the rest of the decode without throwing). Reuses ScanNpcOpcodeUsage's real
// opcode-width table (skip/traceModelOpcode) to walk byte-accurately.
public class TraceNpcModelOpcode {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Archive archive = store.getIndex(IndexType.CONFIGS).getArchive(ConfigType.NPC.getId());
            byte[] decompressed = archive.decompress(storage.loadArchive(archive));
            FileData[] fileData = archive.getFileData();
            List<byte[]> contents = SpliceItemOption.splitChunks(decompressed, fileData.length);

            for (String idStr : args[1].split(",")) {
                int id = Integer.parseInt(idStr.trim());
                int slot = -1;
                for (int j = 0; j < fileData.length; j++) if (fileData[j].getId() == id) slot = j;
                if (slot == -1) { System.out.println(id + " NOT FOUND"); continue; }
                byte[] raw = contents.get(slot);
                System.out.println("--- npc " + id + " (" + raw.length + " bytes) ---");
                ScanNpcOpcodeUsage.traceModelOpcode(id, raw);
                dumpAllOpcodes(id, raw);
            }
        }
    }

    static void dumpAllOpcodes(int id, byte[] b) {
        InputStream in = new InputStream(b);
        StringBuilder sb = new StringBuilder("npc " + id + " opcodes present (in order): ");
        try {
            while (true) {
                int code = in.readUnsignedByte();
                if (code == 0) break;
                sb.append(code).append(" ");
                ScanNpcOpcodeUsage.skip(in, code);
            }
            System.out.println(sb);
        } catch (Exception e) {
            System.out.println(sb + "  [WALK ERROR at offset " + in.getOffset() + ": " + e + "]");
        }
    }
}
