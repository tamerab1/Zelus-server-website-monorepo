import net.runelite.cache.IndexType;
import net.runelite.cache.fs.*;
import net.runelite.cache.index.FileData;

import java.io.File;
import java.util.*;

// Read-only. Max/free archive ids in the frames (0) and skeletons (1) indexes, and the file-id layout of given frame archives.
// Usage: ProbeAnimIndexes <cachePath> [frameArchive...]
public class ProbeAnimIndexes {
    public static void main(String[] args) throws Exception {
        try (Store store = new Store(new File(args[0]))) {
            store.load();
            for (IndexType t : new IndexType[]{IndexType.ANIMATIONS, IndexType.SKELETONS}) {
                Index idx = store.getIndex(t);
                TreeSet<Integer> ids = new TreeSet<>();
                for (Archive a : idx.getArchives()) ids.add(a.getArchiveId());
                System.out.println(t + ": " + ids.size() + " archives, max " + ids.last() + ", top 5 " + ids.descendingSet().stream().limit(5).toList()
                        + ", protocol " + idx.getProtocol() + ", named " + idx.isNamed());
            }
            for (int i = 1; i < args.length; i++) {
                Archive a = store.getIndex(IndexType.ANIMATIONS).getArchive(Integer.parseInt(args[i]));
                List<Integer> fids = new ArrayList<>();
                for (FileData fd : a.getFileData()) fids.add(fd.getId());
                byte[] dec = a.decompress(store.getStorage().loadArchive(a));
                System.out.println("frames " + args[i] + ": files " + fids + " compression " + a.getCompression() + " decLen " + dec.length);
            }
        }
    }
}
