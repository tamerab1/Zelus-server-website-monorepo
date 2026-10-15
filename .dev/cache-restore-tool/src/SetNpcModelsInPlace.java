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

// Repoints an EXISTING npc's body models (opcode 1: u8 count + u16 model ids, located by its
// exact bytes for the decoded model list, which must occur once) using an old:new map, in place. Re-decodes with the real NpcLoader
// and checks that only `models` changed, exactly as mapped, before writing.
// Usage: verify|apply <cachePath> <npcId> <oldModel>:<newModel>[,...] [<npcId> <map> ...]
public class SetNpcModelsInPlace {
    public static void main(String[] args) throws Exception {
        String mode = args[0];
        try (Store store = new Store(new File(args[1]))) {
            store.load();
            Storage storage = store.getStorage();
            Index index = store.getIndex(IndexType.CONFIGS);
            Archive archive = index.getArchive(ConfigType.NPC.getId());
            byte[] dec = archive.decompress(storage.loadArchive(archive));
            FileData[] fd = archive.getFileData();
            List<byte[]> contents = SpliceItemOption.splitChunks(dec, fd.length);

            for (int a = 2; a + 1 < args.length; a += 2) {
                int npc = Integer.parseInt(args[a]);
                Map<Integer, Integer> map = new HashMap<>();
                for (String pair : args[a + 1].split(",")) {
                    String[] kv = pair.split(":");
                    map.put(Integer.parseInt(kv[0]), Integer.parseInt(kv[1]));
                }
                int slot = -1;
                for (int i = 0; i < fd.length; i++) if (fd[i].getId() == npc) slot = i;
                if (slot < 0) throw new IllegalStateException("npc " + npc + " not found");
                byte[] b = contents.get(slot);
                // Locate opcode 1 by its exact bytes: [1, count, id0 hi, id0 lo, ...] for the decoded
                // model list. It must occur exactly once in the record.
                int[] cur = new NpcLoader().load(npc, b).models;
                byte[] pat = new byte[2 + cur.length * 2];
                pat[0] = 1;
                pat[1] = (byte) cur.length;
                for (int i = 0; i < cur.length; i++) {
                    pat[2 + i * 2] = (byte) (cur[i] >> 8);
                    pat[3 + i * 2] = (byte) cur[i];
                }
                int at = -1;
                outer:
                for (int i = 0; i + pat.length <= b.length; i++) {
                    for (int j = 0; j < pat.length; j++) if (b[i + j] != pat[j]) continue outer;
                    if (at >= 0) throw new IllegalStateException("npc " + npc + ": model block occurs more than once");
                    at = i;
                }
                if (at < 0) throw new IllegalStateException("npc " + npc + ": model block not found");
                int count = cur.length;
                byte[] n = b.clone();
                int[] expected = new int[count];
                for (int i = 0; i < count; i++) {
                    int o = at + 2 + i * 2;
                    int old = (b[o] & 0xFF) << 8 | (b[o + 1] & 0xFF);
                    int nu = map.getOrDefault(old, old);
                    expected[i] = nu;
                    n[o] = (byte) (nu >> 8);
                    n[o + 1] = (byte) nu;
                }
                NpcDefinition before = new NpcLoader().load(npc, b);
                NpcDefinition after = new NpcLoader().load(npc, n);
                if (!Arrays.equals(after.models, expected)) throw new IllegalStateException("npc " + npc + ": models decode wrong");
                for (int old : map.keySet())
                    if (Arrays.stream(before.models).noneMatch(m -> m == old)) throw new IllegalStateException("npc " + npc + " does not use model " + old);
                for (Field f : NpcDefinition.class.getDeclaredFields()) {
                    if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) || f.getName().equals("models")) continue;
                    f.setAccessible(true);
                    if (!Objects.deepEquals(f.get(before), f.get(after))) throw new IllegalStateException("npc " + npc + ": " + f.getName() + " changed");
                }
                System.out.println("npc " + npc + " \"" + after.name + "\" models " + Arrays.toString(before.models) + " -> " + Arrays.toString(after.models));
                contents.set(slot, n);
            }
            if (mode.equals("verify")) {
                System.out.println("VERIFY OK -- nothing written.");
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
            System.out.println("APPLY complete -- npc archive revision " + archive.getRevision());
        }
    }
}
