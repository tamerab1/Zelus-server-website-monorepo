import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Container;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;
import net.runelite.cache.index.IndexData;

import java.io.File;
import java.util.List;

// Fixes a FileData id corrupted by IndexData's own delta-encoding silently overflowing when the
// gap to the previous sorted entry exceeds 65535 (discovered 2026-09-18 -- id 139780 immediately
// after neighbor 62388, delta 77392, wrapped to 62388+(77392 mod 65536)=74244 on write-back).
// Content at that FileData slot is untouched; only the id label was wrong -- but since content
// chunks are positionally paired 1:1 with the FileData array (same order), moving the FileData
// entry to its correct sorted position for the new id REQUIRES moving its content chunk in
// lockstep, or the pairing desyncs. This does both together.
// Usage: verify|apply <cachePath> <currentWrongId> <newSafeId>
public class FixObjectId {
    public static void main(String[] args) throws Exception {
        String mode = args[0];
        String cachePath = args[1];
        int wrongId = Integer.parseInt(args[2]);
        int newId = Integer.parseInt(args[3]);

        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Index index = store.getIndex(IndexType.CONFIGS);
            Archive archive = index.getArchive(ConfigType.OBJECT.getId());

            byte[] decompressed = archive.decompress(storage.loadArchive(archive));
            FileData[] fds = archive.getFileData();
            List<byte[]> fileContents = SpliceItemOption.splitChunks(decompressed, fds.length);

            for (FileData fd : fds) {
                if (fd.getId() == newId) throw new IllegalStateException("target id " + newId + " already exists -- ABORTING");
            }

            int slot = -1;
            for (int i = 0; i < fds.length; i++) if (fds[i].getId() == wrongId) slot = i;
            if (slot == -1) throw new IllegalStateException("id " + wrongId + " not found -- ABORTING");

            byte[] movingContent = fileContents.get(slot);
            FileData movingFd = fds[slot];

            // Remove from current position (both arrays, same index).
            fileContents.remove(slot);
            FileData[] withoutOld = new FileData[fds.length - 1];
            System.arraycopy(fds, 0, withoutOld, 0, slot);
            System.arraycopy(fds, slot + 1, withoutOld, slot, fds.length - slot - 1);

            int insertAt = withoutOld.length;
            for (int i = 0; i < withoutOld.length; i++) {
                if (withoutOld[i].getId() > newId) { insertAt = i; break; }
            }
            int prevId = insertAt > 0 ? withoutOld[insertAt - 1].getId() : -1;
            int nextId = insertAt < withoutOld.length ? withoutOld[insertAt].getId() : -1;
            System.out.println("Inserting id " + newId + " between prev=" + prevId + " next=" + nextId
                    + " (deltas: " + (prevId == -1 ? "n/a" : (newId - prevId)) + ", " + (nextId == -1 ? "n/a" : (nextId - newId)) + ")");
            if (prevId != -1 && (newId - prevId) > 60000) throw new IllegalStateException("delta to prev too large -- would risk the same overflow bug, ABORTING");
            if (nextId != -1 && (nextId - newId) > 60000) throw new IllegalStateException("delta to next too large -- would risk the same overflow bug, ABORTING");

            movingFd.setId(newId);

            FileData[] newFds = new FileData[fds.length];
            System.arraycopy(withoutOld, 0, newFds, 0, insertAt);
            newFds[insertAt] = movingFd;
            System.arraycopy(withoutOld, insertAt, newFds, insertAt + 1, withoutOld.length - insertAt);

            fileContents.add(insertAt, movingContent);

            if (mode.equals("verify")) {
                System.out.println("VERIFY mode -- not writing. Re-run with 'apply' to persist.");
                return;
            }
            if (!mode.equals("apply")) throw new IllegalArgumentException("mode must be verify or apply");

            archive.setFileData(newFds);

            byte[] newDecompressed = SpliceItemOption.joinChunks(fileContents);
            Container container = new Container(archive.getCompression(), -1);
            container.compress(newDecompressed, null);

            storage.store(index.getId(), archive.getArchiveId(), container.data);
            archive.setCrc(container.crc);
            archive.setRevision(archive.getRevision() + 1);
            archive.setCompressedSize(container.data.length);
            archive.setDecompressedSize(newDecompressed.length);

            IndexData indexData = index.toIndexData();
            byte[] rawIndex = indexData.writeIndexData();
            Container idxContainer = new Container(index.getCompression(), -1);
            idxContainer.compress(rawIndex, null);
            storage.store(255, index.getId(), idxContainer.data);
            index.setCrc(idxContainer.crc);

            System.out.println("APPLY complete. Id " + wrongId + " -> " + newId + " fixed. Archive revision now " + archive.getRevision() + ".");
        }
    }
}
