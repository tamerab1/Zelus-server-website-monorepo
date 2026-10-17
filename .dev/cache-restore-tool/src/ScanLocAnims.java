import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.ObjectDefinition;
import net.runelite.cache.definitions.SequenceDefinition;
import net.runelite.cache.definitions.loaders.ObjectLoader;
import net.runelite.cache.definitions.loaders.SequenceLoader;
import net.runelite.cache.fs.*;

import java.io.File;
import java.util.*;

// Read-only. How do the cache's animated locs loop? Prints the frameStep / maxLoops / frame-count spread of every
// sequence used as a loc animation (opcode 24), plus a few named examples, and the highest seq id in use.
// Usage: ScanLocAnims <cachePath>
public class ScanLocAnims {
    public static void main(String[] args) throws Exception {
        try (Store store = new Store(new File(args[0]))) {
            store.load();
            Index cfg = store.getIndex(IndexType.CONFIGS);
            Map<Integer, String> locSeqs = new TreeMap<>();
            Archive objs = cfg.getArchive(ConfigType.OBJECT.getId());
            Map<Integer, byte[]> of = files(store, objs);
            for (Map.Entry<Integer, byte[]> f : of.entrySet()) {
                ObjectDefinition d = new ObjectLoader().load(f.getKey(), f.getValue());
                if (d.getAnimationID() > 0) locSeqs.putIfAbsent(d.getAnimationID(), f.getKey() + " " + d.getName());
            }
            Archive seqs = cfg.getArchive(ConfigType.SEQUENCE.getId());
            Map<Integer, byte[]> sf = files(store, seqs);
            Map<String, Integer> spread = new TreeMap<>();
            int max = 0, shown = 0;
            for (Map.Entry<Integer, byte[]> f : sf.entrySet()) {
                max = Math.max(max, f.getKey());
                if (!locSeqs.containsKey(f.getKey())) continue;
                SequenceDefinition s = new SequenceLoader().load(f.getKey(), f.getValue());
                if (s.frameIDs == null) continue;
                int n = s.frameIDs.length;
                String step = s.frameStep == -1 ? "-1" : s.frameStep == n ? "=n" : s.frameStep == 0 ? "0" : "other";
                spread.merge("step " + step + " maxLoops " + s.maxLoops, 1, Integer::sum);
                if (shown++ < 12)
                    System.out.println("seq " + f.getKey() + " (loc " + locSeqs.get(f.getKey()) + "): frames=" + n + " step=" + s.frameStep
                            + " maxLoops=" + s.maxLoops + " lens=" + Arrays.toString(Arrays.copyOf(s.frameLengths, Math.min(6, n)))
                            + " firstFrame=" + (s.frameIDs[0] >>> 16) + ":" + (s.frameIDs[0] & 0xFFFF));
            }
            System.out.println("loc seqs: " + locSeqs.size() + ", spread " + spread + ", max seq id " + max);
        }
    }

    /** file id -> bytes for a config group, split the way every tool here does it (tolerates duplicate ids). */
    static Map<Integer, byte[]> files(Store store, Archive a) throws Exception {
        byte[] dec = a.decompress(store.getStorage().loadArchive(a));
        net.runelite.cache.index.FileData[] fd = a.getFileData();
        List<byte[]> chunks = SpliceItemOption.splitChunks(dec, fd.length);
        Map<Integer, byte[]> out = new TreeMap<>();
        for (int i = 0; i < fd.length; i++) out.put(fd[i].getId(), chunks.get(i));
        return out;
    }
}
