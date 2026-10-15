import net.runelite.cache.fs.*;
import net.runelite.cache.index.FileData;
import java.io.File;
import java.util.*;

// Read-only. Compares two caches (A = VPS, B = local) archive by archive in every index. For
// archives whose CRC differs, reports each side's revision; for multi-file archives (configs),
// drills down to per-file byte diffs so we can tell exactly which entries each side changed.
// Usage: <cacheA> <cacheB>
public class DiffCaches {
    public static void main(String[] args) throws Exception {
        try (Store a = new Store(new File(args[0])); Store b = new Store(new File(args[1]))) {
            a.load(); b.load();
            int onlyA = 0, onlyB = 0, diff = 0;
            for (Index ia : a.getIndexes()) {
                Index ib = null; for (Index t : b.getIndexes()) if (t.getId() == ia.getId()) ib = t;
                if (ib == null) { System.out.println("index " + ia.getId() + " missing in B"); continue; }
                Map<Integer, Archive> mb = new HashMap<>();
                for (Archive x : ib.getArchives()) mb.put(x.getArchiveId(), x);
                Set<Integer> seen = new HashSet<>();
                List<String> lines = new ArrayList<>();
                for (Archive xa : ia.getArchives()) {
                    Archive xb = mb.get(xa.getArchiveId());
                    seen.add(xa.getArchiveId());
                    if (xb == null) { onlyA++; lines.add("  ONLY-VPS archive " + xa.getArchiveId() + " rev " + xa.getRevision()); continue; }
                    if (xa.getCrc() == xb.getCrc()) continue;
                    diff++;
                    String line = "  DIFF archive " + xa.getArchiveId() + " revVPS=" + xa.getRevision() + " revLOCAL=" + xb.getRevision();
                    if (xa.getFileData().length > 1 || xb.getFileData().length > 1) {
                        line += fileDiff(a, xa, b, xb);
                    }
                    lines.add(line);
                }
                for (Archive xb : ib.getArchives()) if (!seen.contains(xb.getArchiveId())) { onlyB++; if (lines.size() < 400) lines.add("  ONLY-LOCAL archive " + xb.getArchiveId()); }
                if (!lines.isEmpty()) {
                    System.out.println("index " + ia.getId() + ": " + lines.size() + " differing");
                    int shown = 0;
                    for (String l : lines) { if (shown++ < 40 || l.contains("files:")) System.out.println(l); }
                }
            }
            System.out.println("TOTAL diff=" + diff + " onlyVPS=" + onlyA + " onlyLocal=" + onlyB);
        }
    }

    static String fileDiff(Store sa, Archive xa, Store sb, Archive xb) throws Exception {
        List<byte[]> fa = SpliceItemOption.splitChunks(xa.decompress(sa.getStorage().loadArchive(xa)), xa.getFileData().length);
        List<byte[]> fb = SpliceItemOption.splitChunks(xb.decompress(sb.getStorage().loadArchive(xb)), xb.getFileData().length);
        Map<Integer, byte[]> ma = new HashMap<>(), mb = new HashMap<>();
        for (int i = 0; i < fa.size(); i++) ma.put(xa.getFileData()[i].getId(), fa.get(i));
        for (int i = 0; i < fb.size(); i++) mb.put(xb.getFileData()[i].getId(), fb.get(i));
        List<Integer> changed = new ArrayList<>(), onlyVps = new ArrayList<>(), onlyLocal = new ArrayList<>();
        for (Map.Entry<Integer, byte[]> e : ma.entrySet()) {
            byte[] o = mb.get(e.getKey());
            if (o == null) onlyVps.add(e.getKey());
            else if (!Arrays.equals(o, e.getValue())) changed.add(e.getKey());
        }
        for (Integer k : mb.keySet()) if (!ma.containsKey(k)) onlyLocal.add(k);
        Collections.sort(changed); Collections.sort(onlyVps); Collections.sort(onlyLocal);
        return "\n    files: changed=" + changed.size() + " " + head(changed) + "\n           onlyVPS=" + onlyVps.size() + " " + head(onlyVps)
            + "\n           onlyLocal=" + onlyLocal.size() + " " + head(onlyLocal);
    }

    static String head(List<Integer> l) { return l.size() <= 60 ? l.toString() : l.subList(0, 60) + "..."; }
}
