import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.NpcDefinition;
import net.runelite.cache.definitions.loaders.NpcLoader;
import net.runelite.cache.fs.*;
import net.runelite.cache.index.FileData;
import net.runelite.cache.index.IndexData;

import java.io.File;
import java.lang.reflect.Field;
import java.util.*;

// Replaces an EXISTING npc's whole body-model list (opcode 1: u8 count + u16 model ids) with a new list of any length,
// in place, in EVERY file slot carrying that npc id (this archive has duplicate ids; SetNpcModelsInPlace only swaps
// models 1:1 in the last slot). The opcode-1 block is located by its exact bytes for the decoded list (must occur once
// per slot); each edited record is re-decoded with the real NpcLoader and must differ only in `models`.
// Also replaces the chathead (dialogue head) list, opcode 60, same u8 count + u16 ids layout, with "chathead".
// Usage: verify|apply <cachePath> <npcId> <model,model,...> [chathead]
public class SetNpcModelListInPlace {
    public static void main(String[] args) throws Exception {
        String mode = args[0];
        int npc = Integer.parseInt(args[2]);
        int[] wanted = Arrays.stream(args[3].split(",")).mapToInt(Integer::parseInt).toArray();
        boolean chathead = args.length > 4 && args[4].equals("chathead");
        int opcode = chathead ? 60 : 1;
        String field = chathead ? "chatheadModels" : "models";
        if (wanted.length == 0 || wanted.length > 255)
            throw new IllegalArgumentException("1-255 models");
        try (Store store = new Store(new File(args[1]))) {
            store.load();
            Storage storage = store.getStorage();
            Index index = store.getIndex(IndexType.CONFIGS);
            Archive archive = index.getArchive(ConfigType.NPC.getId());
            byte[] dec = archive.decompress(storage.loadArchive(archive));
            FileData[] fd = archive.getFileData();
            List<byte[]> contents = SpliceItemOption.splitChunks(dec, fd.length);

            int edited = 0;
            for (int slot = 0; slot < fd.length; slot++) {
                if (fd[slot].getId() != npc)
                    continue;
                byte[] b = contents.get(slot);
                NpcDefinition curDef = new NpcLoader().load(npc, b);
                int[] cur = chathead ? curDef.chatheadModels : curDef.models;
                if (cur == null) throw new IllegalStateException("slot " + slot + ": no " + field + " block to replace");
                byte[] pat = block(opcode, cur);
                int at = -1;
                outer:
                for (int i = 0; i + pat.length <= b.length; i++) {
                    for (int j = 0; j < pat.length; j++) if (b[i + j] != pat[j]) continue outer;
                    if (at >= 0) throw new IllegalStateException("slot " + slot + ": model block occurs more than once");
                    at = i;
                }
                if (at < 0) throw new IllegalStateException("slot " + slot + ": model block not found");
                byte[] rep = block(opcode, wanted);
                byte[] n = new byte[b.length - pat.length + rep.length];
                System.arraycopy(b, 0, n, 0, at);
                System.arraycopy(rep, 0, n, at, rep.length);
                System.arraycopy(b, at + pat.length, n, at + rep.length, b.length - at - pat.length);

                NpcDefinition before = new NpcLoader().load(npc, b);
                NpcDefinition after = new NpcLoader().load(npc, n);
                int[] got = chathead ? after.chatheadModels : after.models;
                if (!Arrays.equals(got, wanted)) throw new IllegalStateException("slot " + slot + ": " + field + " decode wrong");
                for (Field f : NpcDefinition.class.getDeclaredFields()) {
                    if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) || f.getName().equals(field)) continue;
                    f.setAccessible(true);
                    if (!Objects.deepEquals(f.get(before), f.get(after))) throw new IllegalStateException("slot " + slot + ": " + f.getName() + " changed");
                }
                System.out.println("npc " + npc + " slot " + slot + " \"" + after.name + "\" " + field + " " + Arrays.toString(cur)
                        + " -> " + Arrays.toString(got));
                contents.set(slot, n);
                edited++;
            }
            if (edited == 0) throw new IllegalStateException("npc " + npc + " not found");
            if (mode.equals("verify")) {
                System.out.println("VERIFY OK -- " + edited + " slot(s), nothing written.");
                return;
            }
            if (!mode.equals("apply")) throw new IllegalArgumentException("mode must be verify or apply");
            byte[] joined = SpliceItemOption.joinChunks(contents);
            Container c = new Container(archive.getCompression(), -1);
            c.compress(joined, null);
            storage.store(index.getId(), archive.getArchiveId(), c.data);
            archive.setCrc(c.crc);
            archive.setRevision(archive.getRevision() + 1);
            archive.setCompressedSize(c.data.length);
            archive.setDecompressedSize(joined.length);
            IndexData indexData = index.toIndexData();
            Container ic = new Container(index.getCompression(), -1);
            ic.compress(indexData.writeIndexData(), null);
            storage.store(255, index.getId(), ic.data);
            index.setCrc(ic.crc);
            System.out.println("APPLY complete -- " + edited + " slot(s), npc archive revision " + archive.getRevision());
        }
    }

    /** opcode 1 / 60 bytes: [opcode, count, id hi, id lo, ...] */
    static byte[] block(int opcode, int[] models) {
        byte[] p = new byte[2 + models.length * 2];
        p[0] = (byte) opcode;
        p[1] = (byte) models.length;
        for (int i = 0; i < models.length; i++) {
            p[2 + i * 2] = (byte) (models[i] >> 8);
            p[3 + i * 2] = (byte) models[i];
        }
        return p;
    }
}
