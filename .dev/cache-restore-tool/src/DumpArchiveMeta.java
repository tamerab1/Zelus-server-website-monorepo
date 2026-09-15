import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;

import java.io.File;

public class DumpArchiveMeta {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Index models = store.getIndex(IndexType.MODELS);
            for (int i = 1; i < args.length; i++) {
                int id = Integer.parseInt(args[i]);
                Archive a = models.getArchive(id);
                if (a == null) { System.out.println(id + ": NULL ARCHIVE"); continue; }
                System.out.println("=== model archive " + id + " ===");
                System.out.println("  archiveId=" + a.getArchiveId() + " nameHash=" + a.getNameHash()
                        + " crc=" + a.getCrc() + " revision=" + a.getRevision()
                        + " compression=" + a.getCompression()
                        + " compressedSize=" + a.getCompressedSize() + " decompressedSize=" + a.getDecompressedSize());
                FileData[] fds = a.getFileData();
                System.out.println("  fileData.length=" + (fds == null ? "null" : fds.length));
                if (fds != null) for (FileData fd : fds) System.out.println("    fd.id=" + fd.getId() + " nameHash=" + fd.getNameHash());
            }
            System.out.println("models index: compression=" + models.getCompression() + " revision=" + models.getRevision() + " crc=" + models.getCrc());
        }
    }
}
