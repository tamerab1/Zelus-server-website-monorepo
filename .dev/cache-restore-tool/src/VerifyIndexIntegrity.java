import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Container;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.IndexData;

import java.io.File;
import java.util.zip.CRC32;

// Read-only integrity check: recomputes the CRC of every archive in the given index by actually
// reading and decompressing its stored bytes, and separately recomputes the index's own reference
// table CRC, comparing both against what's recorded. A mismatch means real corruption; a clean pass
// across every archive is the strongest possible evidence there isn't any.
public class VerifyIndexIntegrity {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        int indexId = Integer.parseInt(args[1]); // 5=MAPS, 2=CONFIGS

        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Index index = store.getIndex(IndexType.valueOf(indexId == 5 ? "MAPS" : "CONFIGS"));

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
                        System.out.println("CRC MISMATCH archive " + a.getArchiveId() + ": stored=" + stored
                                + " computed(raw)=" + crcRaw.getValue() + " computed(decompressed)=" + crcDecompressed.getValue());
                    } else if (!matchesRaw) {
                        System.out.println("archive " + a.getArchiveId() + ": matches DECOMPRESSED content CRC, not raw (compression=" + a.getCompression() + ")");
                    }
                } catch (Exception e) {
                    unreadable++;
                    System.out.println("UNREADABLE archive " + a.getArchiveId() + ": " + e);
                }
            }
            System.out.println("index " + indexId + ": " + total + " archives, " + checked + " decompressed cleanly, "
                    + mismatches + " CRC mismatches, " + unreadable + " unreadable");

            // Recompute the index's own reference-table CRC the same way writeIndexReferenceTable does.
            IndexData indexData = index.toIndexData();
            byte[] rawIndex = indexData.writeIndexData();
            Container idxContainer = new Container(index.getCompression(), -1);
            idxContainer.compress(rawIndex, null);
            CRC32 crc = new CRC32();
            crc.update(idxContainer.data);
            System.out.println("index reference table: recomputed CRC=" + crc.getValue() + " (stored index.getCrc()=" + (index.getCrc() & 0xFFFFFFFFL) + ")");
        }
    }
}
