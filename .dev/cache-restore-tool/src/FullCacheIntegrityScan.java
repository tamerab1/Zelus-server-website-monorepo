import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Container;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;

import java.io.File;
import java.util.zip.CRC32;

// Read-only full sweep across EVERY index in the given cache: recomputes the CRC of every archive by
// actually reading and decompressing its stored bytes, comparing against the recorded CRC. This is
// the generalized version of VerifyIndexIntegrity.java (which only covered MAPS/CONFIGS) -- built
// 2026-09-16 to find every real corruption in the production cache after the v1.1.89 mass rebuild,
// not just whatever the server's own boot log happened to log warnings about for MODELS.
public class FullCacheIntegrityScan {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];

        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();

            int grandTotal = 0, grandMismatches = 0, grandUnreadable = 0;

            for (IndexType type : IndexType.values()) {
                Index index;
                try {
                    index = store.getIndex(type);
                } catch (Exception e) {
                    System.out.println(type + ": no such index in this cache (" + e.getMessage() + ")");
                    continue;
                }
                if (index == null) {
                    continue;
                }

                int total = 0, checked = 0, mismatches = 0, unreadable = 0;
                for (Archive a : index.getArchives()) {
                    total++;
                    try {
                        byte[] raw = storage.loadArchive(a);
                        Container c = Container.decompress(raw, null);
                        checked++;
                        CRC32 crcRaw = new CRC32();
                        crcRaw.update(raw);
                        CRC32 crcDecompressed = new CRC32();
                        crcDecompressed.update(c.data);
                        long stored = a.getCrc() & 0xFFFFFFFFL;
                        boolean matchesRaw = (crcRaw.getValue() == stored);
                        boolean matchesDecompressed = (crcDecompressed.getValue() == stored);
                        if (!matchesRaw && !matchesDecompressed) {
                            mismatches++;
                            System.out.println("[" + type + "] CRC MISMATCH archive " + a.getArchiveId()
                                    + ": stored=" + stored + " computed(raw)=" + crcRaw.getValue()
                                    + " computed(decompressed)=" + crcDecompressed.getValue());
                        }
                    } catch (Exception e) {
                        unreadable++;
                        System.out.println("[" + type + "] UNREADABLE archive " + a.getArchiveId() + ": " + e);
                    }
                }
                System.out.println(type + ": " + total + " archives, " + checked + " decompressed cleanly, "
                        + mismatches + " CRC mismatches, " + unreadable + " unreadable");
                grandTotal += total;
                grandMismatches += mismatches;
                grandUnreadable += unreadable;
            }

            System.out.println("=== TOTAL: " + grandTotal + " archives across all indices, "
                    + grandMismatches + " CRC mismatches, " + grandUnreadable + " unreadable ===");
        }
    }
}
