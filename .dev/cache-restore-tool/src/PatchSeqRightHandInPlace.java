import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.SequenceDefinition;
import net.runelite.cache.definitions.loaders.SequenceLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.ArchiveFiles;
import net.runelite.cache.fs.Container;
import net.runelite.cache.fs.FSFile;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

// Patches an EXISTING sequence's rightHandItem (opcode 7) in place, same id. Same surgical
// find-and-overwrite-2-bytes approach as CloneSeqWithHandItem.setRightHandItem, just applied to a
// record that already exists in the archive instead of a freshly-cloned one.
// Usage: <cachePath> <seqId> <newRightHandItemId>
public class PatchSeqRightHandInPlace {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        int seqId = Integer.parseInt(args[1]);
        int newValue = Integer.parseInt(args[2]);

        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Index configs = store.getIndex(IndexType.CONFIGS);
            Archive archive = configs.getArchive(ConfigType.SEQUENCE.getId());
            ArchiveFiles files = archive.getFiles(storage.loadArchive(archive));

            FSFile target = files.findFile(seqId);
            if (target == null) throw new IllegalStateException("seq " + seqId + " not found");

            byte[] before = target.getContents();
            byte[] after = CloneSeqWithHandItem.setRightHandItem(before, newValue);
            target.setContents(after);

            SequenceLoader loader = new SequenceLoader();
            SequenceDefinition def = loader.load(seqId, after);
            System.out.println("seq " + seqId + " AFTER: rightHandItem=" + def.rightHandItem);
            if (def.rightHandItem != newValue) throw new IllegalStateException("patch failed -- ABORTING write");

            byte[] newDecompressed = files.saveContents();
            Container container = new Container(archive.getCompression(), -1);
            container.compress(newDecompressed, null);
            storage.store(configs.getId(), archive.getArchiveId(), container.data);
            archive.setCrc(container.crc);
            archive.setRevision(archive.getRevision() + 1);
            archive.setCompressedSize(container.data.length);
            archive.setDecompressedSize(newDecompressed.length);

            PackMadAngel.writeIndexReferenceTable(storage, configs);
            System.out.println("APPLY complete. config[" + ConfigType.SEQUENCE.getId() + "] revision now "
                    + archive.getRevision());
        }
    }
}
