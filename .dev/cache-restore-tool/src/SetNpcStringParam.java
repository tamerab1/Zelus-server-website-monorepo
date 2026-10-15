import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.NpcDefinition;
import net.runelite.cache.definitions.loaders.NpcLoader;
import net.runelite.cache.fs.*;
import net.runelite.cache.index.FileData;
import net.runelite.cache.index.IndexData;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;

// Rewrites one STRING param value (opcode 249 params block) on existing npc defs, in place.
// Written for Draco (30561-30565): cloned from CoX Tekton, they inherited param 510 = "Tekton",
// which is the boss HP-HUD display-name override (clientscript 2103 reads nc_param 510 first and
// only falls back to the npc's real name when it's empty) -- so the HUD said "Tekton".
//
// Usage: <cachePath> <paramKey> <oldValue> <newValue> <npcId,npcId,...> [--dry]
public class SetNpcStringParam {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        int key = Integer.parseInt(args[1]);
        String oldVal = args[2], newVal = args[3];
        Set<Integer> ids = new HashSet<>();
        for (String s : args[4].split(",")) ids.add(Integer.parseInt(s.trim()));
        boolean dry = args.length > 5 && args[5].equals("--dry");

        // entry layout: isString(1) key(3, big-endian) value(cp1252 + 0 terminator)
        byte[] oldEntry = entry(key, oldVal), newEntry = entry(key, newVal);

        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Index index = store.getIndex(IndexType.CONFIGS);
            Archive archive = index.getArchive(ConfigType.NPC.getId());
            byte[] decompressed = archive.decompress(storage.loadArchive(archive));
            FileData[] fd = archive.getFileData();
            List<byte[]> contents = SpliceItemOption.splitChunks(decompressed, fd.length);
            NpcLoader loader = new NpcLoader();

            int updated = 0;
            for (int i = 0; i < fd.length; i++) {
                int id = fd[i].getId();
                if (!ids.contains(id)) continue;
                byte[] b = contents.get(i);
                int at = indexOf(b, oldEntry);
                if (at < 0 || indexOf(b, oldEntry, at + 1) >= 0)
                    throw new IllegalStateException(id + ": expected exactly one match of param entry -- ABORTING");
                byte[] out = new byte[b.length - oldEntry.length + newEntry.length];
                System.arraycopy(b, 0, out, 0, at);
                System.arraycopy(newEntry, 0, out, at, newEntry.length);
                System.arraycopy(b, at + oldEntry.length, out, at + newEntry.length, b.length - at - oldEntry.length);

                NpcDefinition before = loader.load(id, b), after = loader.load(id, out);
                Map<Integer, Object> expect = new HashMap<>(before.params);
                expect.put(key, newVal);
                if (!expect.equals(after.params) || !Objects.equals(before.name, after.name)
                        || !Arrays.equals(before.models, after.models)
                        || before.standingAnimation != after.standingAnimation
                        || before.combatLevel != after.combatLevel
                        || !Arrays.equals(before.actions, after.actions)
                        || !Arrays.equals(before.recolorToReplace, after.recolorToReplace))
                    throw new IllegalStateException(id + ": verification failed -- ABORTING, nothing written");
                System.out.println(id + " \"" + after.name + "\" " + before.params.get(key) + " -> " + after.params.get(key));
                contents.set(i, out);
                updated++;
            }
            if (updated != ids.size()) throw new IllegalStateException("only found " + updated + "/" + ids.size() + " -- ABORTING");
            if (dry) { System.out.println("dry run, nothing written"); return; }

            byte[] newDec = SpliceItemOption.joinChunks(contents);
            Container c = new Container(archive.getCompression(), -1);
            c.compress(newDec, null);
            storage.store(index.getId(), archive.getArchiveId(), c.data);
            archive.setCrc(c.crc);
            archive.setRevision(archive.getRevision() + 1);
            archive.setCompressedSize(c.data.length);
            archive.setDecompressedSize(newDec.length);

            IndexData indexData = index.toIndexData();
            Container ic = new Container(index.getCompression(), -1);
            ic.compress(indexData.writeIndexData(), null);
            storage.store(255, index.getId(), ic.data);
            index.setCrc(ic.crc);
            System.out.println("Wrote " + updated + " npc def(s). Archive revision now " + archive.getRevision());
        }
    }

    static byte[] entry(int key, String v) {
        byte[] s = v.getBytes(StandardCharsets.ISO_8859_1);
        byte[] e = new byte[4 + s.length + 1];
        e[0] = 1; e[1] = (byte) (key >> 16); e[2] = (byte) (key >> 8); e[3] = (byte) key;
        System.arraycopy(s, 0, e, 4, s.length);
        return e;
    }

    static int indexOf(byte[] h, byte[] n) { return indexOf(h, n, 0); }
    static int indexOf(byte[] h, byte[] n, int from) {
        outer:
        for (int i = from; i <= h.length - n.length; i++) {
            for (int j = 0; j < n.length; j++) if (h[i + j] != n[j]) continue outer;
            return i;
        }
        return -1;
    }
}
