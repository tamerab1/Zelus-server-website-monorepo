import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.ArchiveFiles;
import net.runelite.cache.fs.FSFile;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;
import net.runelite.cache.io.InputStream;

import java.io.File;

// Read-only. For each given frame archive (IndexType.ANIMATIONS), reports the framemap
// (skeleton, IndexType.SKELETONS) id its first sub-frame references -- the first 2 bytes of every
// individual frame's data, per FrameLoader.java. Also lists every sub-file id (so the real
// multi-file FileData structure can be replicated when copying archives into Zelus's cache).
public class DumpFrameSkeleton {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Index animations = store.getIndex(IndexType.ANIMATIONS);

            for (int i = 1; i < args.length; i++) {
                int archiveId = Integer.parseInt(args[i]);
                Archive archive = animations.getArchive(archiveId);
                if (archive == null) {
                    System.out.println("frame archive " + archiveId + " -- NOT FOUND");
                    continue;
                }
                ArchiveFiles files = archive.getFiles(storage.loadArchive(archive));
                FileData[] fileData = archive.getFileData();
                int[] ids = new int[fileData.length];
                for (int j = 0; j < fileData.length; j++) ids[j] = fileData[j].getId();

                FSFile first = files.getFiles().iterator().next();
                InputStream is = new InputStream(first.getContents());
                int framemapId = is.readUnsignedShort();

                System.out.println("frame archive " + archiveId + ": subFiles=" + fileData.length
                        + " ids=" + java.util.Arrays.toString(ids) + " framemapId=" + framemapId);
            }
        }
    }
}
