import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Container;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;
import net.runelite.cache.index.IndexData;

import java.io.File;
import java.nio.file.Files;

// Replaces the content of an EXISTING map archive (terrain or locs) in place -- same archive id,
// same name hash, bumped revision, fresh CRC -- as opposed to ImportCathedralMap.java's addArchive
// path (which is only for brand-new, previously-unoccupied archive ids). Used for region 13364
// (rx=52,ry=52), the live vanilla Digsite/Exam Centre area, being replaced with new custom content.
//
// Modes:
//   verify <cachePath> <archiveId> <contentFile>  -- confirms the archive exists and prints before/after sizes, does not write
//   apply  <cachePath> <archiveId> <contentFile>
public class ReplaceMapRegion {
    public static void main(String[] args) throws Exception {
        String mode = args[0];
        String cachePath = args[1];
        int archiveId = Integer.parseInt(args[2]);
        File contentFile = new File(args[3]);
        byte[] newContent = Files.readAllBytes(contentFile.toPath());

        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Index maps = store.getIndex(IndexType.MAPS);
            Archive archive = maps.getArchive(archiveId);
            if (archive == null) {
                throw new IllegalStateException("archive " + archiveId + " does not exist -- refusing (this tool only replaces existing archives).");
            }

            byte[] oldCompressed = storage.loadArchive(archive);
            byte[] oldDecompressed = archive.decompress(oldCompressed);
            System.out.println("archive " + archiveId + " (nameHash=" + archive.getNameHash() + ", revision=" + archive.getRevision()
                    + "): old decompressed size=" + oldDecompressed.length + ", new content size=" + newContent.length);

            if (mode.equals("verify")) {
                System.out.println("VERIFY mode -- not writing. Re-run with 'apply' to persist.");
                return;
            }
            if (!mode.equals("apply")) {
                throw new IllegalArgumentException("mode must be 'verify' or 'apply', got: " + mode);
            }

            Container container = new Container(archive.getCompression(), -1);
            container.compress(newContent, null);

            storage.store(maps.getId(), archiveId, container.data);
            archive.setCrc(container.crc);
            archive.setRevision(archive.getRevision() + 1);
            archive.setCompressedSize(container.data.length);
            archive.setDecompressedSize(newContent.length);
            // FileData stays a single file (id=0), matching how map archives are already structured
            // (confirmed via ImportCathedralMap.java's writeSingleFileArchive) -- no change needed
            // unless the archive somehow wasn't single-file already.
            FileData[] fds = archive.getFileData();
            if (fds == null || fds.length != 1 || fds[0].getId() != 0) {
                throw new IllegalStateException("archive " + archiveId + " is not the expected single-file (fileId=0) shape -- ABORTING");
            }

            IndexData indexData = maps.toIndexData();
            byte[] rawIndex = indexData.writeIndexData();
            Container idxContainer = new Container(maps.getCompression(), -1);
            idxContainer.compress(rawIndex, null);
            storage.store(255, maps.getId(), idxContainer.data);
            maps.setCrc(idxContainer.crc);

            System.out.println("APPLY complete. Archive " + archiveId + " revision now " + archive.getRevision()
                    + ", new decompressed size " + newContent.length + " bytes.");
        }
    }
}
