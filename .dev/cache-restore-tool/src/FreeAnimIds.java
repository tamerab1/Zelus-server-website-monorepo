import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.fs.*;
import net.runelite.cache.index.FileData;

import java.io.File;

// Read-only (2026-10-19): the highest skeleton / frame group / sequence ids and the first free skeleton+frame id at or
// above a start, so new hand-made animations can be given ids. Usage: FreeAnimIds <cache> <start>
public class FreeAnimIds {
    public static void main(String[] args) throws Exception {
        try (Store store = new Store(new File(args[0]))) {
            store.load();
            Index sk = store.getIndex(IndexType.SKELETONS), fr = store.getIndex(IndexType.ANIMATIONS);
            int maxSk = sk.getArchives().stream().mapToInt(Archive::getArchiveId).max().orElse(-1);
            int maxFr = fr.getArchives().stream().mapToInt(Archive::getArchiveId).max().orElse(-1);
            Archive seq = store.getIndex(IndexType.CONFIGS).getArchive(ConfigType.SEQUENCE.getId());
            int maxSeq = -1;
            for (FileData f : seq.getFileData()) maxSeq = Math.max(maxSeq, f.getId());
            System.out.println("max skeleton " + maxSk + ", max frame group " + maxFr + ", max seq " + maxSeq);
            StringBuilder free = new StringBuilder();
            for (int id = Integer.parseInt(args[1]), n = 0; n < 12; id++)
                if (sk.getArchive(id) == null && fr.getArchive(id) == null) { free.append(id).append(' '); n++; }
            System.out.println("free skeleton+frame ids: " + free);
        }
    }
}
