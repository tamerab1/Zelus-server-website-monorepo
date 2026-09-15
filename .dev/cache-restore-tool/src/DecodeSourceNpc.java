import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;
import net.runelite.cache.io.InputStream;

import java.io.File;
import java.util.List;

// Read-only. Fully decodes one NPC record from an arbitrary source cache (real opcode table, from
// RS-Realm-Server-Package's NpcTypeDecoder.kt) -- used here to check whether Aggy's pet-follower
// npc (source id 16317) is encoded with the classic narrow opcode 1 (like Mad Angel's own boss
// forms 16305-16308 already are, imported from this same "osv-server" reference cache) or the
// modern wide opcode 61/62 (which -- per ScanOpcodeUsage/ScanNpcOpcodeUsage's proof that opcode
// 44 for items and 61/62/252 for npcs are used by ZERO pre-existing Zelus content -- the live
// client cannot render), before deciding whether any narrow-conversion is needed on import.
public class DecodeSourceNpc {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        int targetId = Integer.parseInt(args[1]);

        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Archive archive = store.getIndex(IndexType.CONFIGS).getArchive(ConfigType.NPC.getId());
            byte[] decompressed = archive.decompress(storage.loadArchive(archive));
            FileData[] fileData = archive.getFileData();
            List<byte[]> contents = SpliceItemOption.splitChunks(decompressed, fileData.length);

            int slot = -1;
            for (int j = 0; j < fileData.length; j++) if (fileData[j].getId() == targetId) slot = j;
            if (slot == -1) { System.out.println("npc " + targetId + " NOT FOUND in this cache"); return; }
            decode(targetId, contents.get(slot));
        }
    }

    static void decode(int id, byte[] b) {
        InputStream in = new InputStream(b);
        StringBuilder opcodes = new StringBuilder();
        String name = null;
        int[] models = null, heads = null;
        boolean wideModels = false, wideHeads = false;
        Integer standAnim = null, walkAnim = null, resizeH = null, resizeV = null, vislevel = null;
        String[] ops = new String[5];
        while (true) {
            int code = in.readUnsignedByte();
            if (code == 0) break;
            opcodes.append(code).append(" ");
            switch (code) {
                case 1: {
                    int count = in.readUnsignedByte();
                    models = new int[count];
                    for (int i = 0; i < count; i++) models[i] = in.readUnsignedShort();
                    break;
                }
                case 61: {
                    int count = in.readUnsignedByte();
                    models = new int[count];
                    for (int i = 0; i < count; i++) models[i] = in.readInt();
                    wideModels = true;
                    break;
                }
                case 60: {
                    int count = in.readUnsignedByte();
                    heads = new int[count];
                    for (int i = 0; i < count; i++) heads[i] = in.readUnsignedShort();
                    break;
                }
                case 62: {
                    int count = in.readUnsignedByte();
                    heads = new int[count];
                    for (int i = 0; i < count; i++) heads[i] = in.readInt();
                    wideHeads = true;
                    break;
                }
                case 2: name = in.readString(); break;
                case 3: in.readString(); break;
                case 13: standAnim = in.readUnsignedShort(); break;
                case 14: walkAnim = in.readUnsignedShort(); break;
                case 97: resizeH = in.readUnsignedShort(); break;
                case 98: resizeV = in.readUnsignedShort(); break;
                case 95: vislevel = in.readUnsignedShort(); break;
                case 30: case 31: case 32: case 33: case 34: ops[code - 30] = in.readString(); break;
                default:
                    ScanNpcOpcodeUsage.skip(in, code);
            }
        }
        System.out.println("npc " + id + " \"" + name + "\"");
        System.out.println("  models=" + java.util.Arrays.toString(models) + (wideModels ? " (WIDE opcode 61)" : " (narrow opcode 1)"));
        if (heads != null) System.out.println("  heads=" + java.util.Arrays.toString(heads) + (wideHeads ? " (WIDE opcode 62)" : " (narrow opcode 60)"));
        System.out.println("  standAnim=" + standAnim + " walkAnim=" + walkAnim);
        System.out.println("  resizeH=" + resizeH + " resizeV=" + resizeV + " vislevel=" + vislevel);
        System.out.println("  ops=" + java.util.Arrays.toString(ops));
        System.out.println("  opcode sequence: " + opcodes);
    }
}
