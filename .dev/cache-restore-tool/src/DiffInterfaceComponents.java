import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.ArchiveFiles;
import net.runelite.cache.fs.FSFile;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

// Read-only: dumps the raw bytes of several components in one interface group and shows
// where they differ, to correlate a byte offset with a known field value (e.g. originalY)
// without needing to fully reverse-engineer the fixed-format component layout.
// Usage: <cachePath> <groupId> <child1> <child2> [<child3> ...]
public class DiffInterfaceComponents {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        int group = Integer.parseInt(args[1]);
        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Index index = store.getIndex(IndexType.INTERFACES);
            Archive archive = index.getArchive(group);
            byte[] archiveData = storage.loadArchive(archive);
            ArchiveFiles files = archive.getFiles(archiveData);

            Map<Integer, byte[]> raws = new HashMap<>();
            for (int i = 2; i < args.length; i++) {
                int child = Integer.parseInt(args[i]);
                FSFile match = null;
                for (FSFile f : files.getFiles()) {
                    if (f.getFileId() == child) { match = f; break; }
                }
                if (match == null) {
                    System.out.println("child " + child + " not found");
                    continue;
                }
                byte[] contents = match.getContents();
                raws.put(child, contents);
                System.out.println("child " + child + ": " + contents.length + " bytes");
            }

            Integer[] children = raws.keySet().toArray(new Integer[0]);
            java.util.Arrays.sort(children);
            if (children.length < 2) return;

            int minLen = Integer.MAX_VALUE;
            for (byte[] b : raws.values()) minLen = Math.min(minLen, b.length);

            System.out.println("\nDiffing byte-by-byte (min length " + minLen + "):");
            for (int off = 0; off < minLen; off++) {
                boolean allSame = true;
                byte first = raws.get(children[0])[off];
                for (Integer c : children) {
                    if (raws.get(c)[off] != first) { allSame = false; break; }
                }
                if (!allSame) {
                    StringBuilder sb = new StringBuilder("offset " + off + ": ");
                    for (Integer c : children) {
                        sb.append("child").append(c).append("=").append(raws.get(c)[off] & 0xFF).append(" ");
                    }
                    System.out.println(sb);
                }
            }
        }
    }
}
