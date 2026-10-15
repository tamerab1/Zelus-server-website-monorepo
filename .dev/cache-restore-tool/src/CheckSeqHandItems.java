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

// Read-only: checks a sequence's leftHandItem/rightHandItem override fields (opcodes 6/7) --
// these force a specific item's model to render in that hand for the duration of the animation,
// REGARDLESS of what's actually equipped. Used to confirm whether real pickaxe tier swing
// animations have this baked in (which would explain "wrong pickaxe model in hand" when a
// different tier's animation id is reused for a custom item).
public class CheckSeqHandItems {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Archive seqArchive = store.getIndex(IndexType.CONFIGS).getArchive(ConfigType.SEQUENCE.getId());
            ArchiveFiles seqFiles = seqArchive.getFiles(storage.loadArchive(seqArchive));
            SequenceLoader loader = new SequenceLoader();

            for (int i = 1; i < args.length; i++) {
                int seqId = Integer.parseInt(args[i]);
                FSFile file = seqFiles.findFile(seqId);
                if (file == null || file.getContents() == null) {
                    System.out.println("seq " + seqId + " -- NOT FOUND");
                    continue;
                }
                SequenceDefinition def = loader.load(seqId, file.getContents());
                System.out.println("seq " + seqId + ": leftHandItem=" + def.leftHandItem
                        + " rightHandItem=" + def.rightHandItem);
            }
        }
    }
}
