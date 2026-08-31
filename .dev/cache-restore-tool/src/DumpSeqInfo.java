import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.SequenceDefinition;
import net.runelite.cache.definitions.loaders.SequenceLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.ArchiveFiles;
import net.runelite.cache.fs.FSFile;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;

import java.io.File;
import java.util.HashSet;

// Read-only: dumps a sequence definition (frame count, referenced frame archives, skeleton
// linkage via each frame archive's own referenced skeleton id) and checks whether every frame
// archive it points at actually exists in the cache -- same "does the referenced data actually
// exist" check DumpModelInfo does for models, but for animations.
public class DumpSeqInfo {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        int[] seqIds = new int[args.length - 1];
        for (int i = 1; i < args.length; i++) seqIds[i - 1] = Integer.parseInt(args[i]);

        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Index configs = store.getIndex(IndexType.CONFIGS);
            Archive seqArchive = configs.getArchive(ConfigType.SEQUENCE.getId());
            ArchiveFiles seqFiles = seqArchive.getFiles(storage.loadArchive(seqArchive));
            Index animations = store.getIndex(IndexType.ANIMATIONS);
            SequenceLoader loader = new SequenceLoader();

            for (int seqId : seqIds) {
                FSFile file = seqFiles.findFile(seqId);
                if (file == null || file.getContents() == null) {
                    System.out.println("seq " + seqId + " -- NOT FOUND in SEQUENCE config archive");
                    continue;
                }
                SequenceDefinition def = loader.load(seqId, file.getContents());

                if (def.frameIDs == null || def.frameIDs.length == 0) {
                    System.out.println("seq " + seqId + " -- NO FRAME DATA (frameIDs null/empty), this sequence plays nothing");
                    continue;
                }

                HashSet<Integer> frameArchiveIds = new HashSet<>();
                for (int frame : def.frameIDs) frameArchiveIds.add(frame >> 16);

                int missingArchives = 0;
                for (int archiveId : frameArchiveIds) {
                    Archive a = animations.getArchive(archiveId);
                    if (a == null) {
                        System.out.println("  seq " + seqId + ": MISSING frame archive " + archiveId + " (referenced by frameIDs)");
                        missingArchives++;
                    }
                }

                System.out.println("seq " + seqId + ": frames=" + def.frameIDs.length
                        + " frameArchives=" + frameArchiveIds
                        + " missingFrameArchives=" + missingArchives
                        + " forcedPriority=" + def.forcedPriority
                        + " maxLoops=" + def.maxLoops);
            }
        }
    }
}
