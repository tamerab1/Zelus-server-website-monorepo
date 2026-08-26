import net.runelite.cache.IndexType;
import net.runelite.cache.ConfigType;
import net.runelite.cache.definitions.ItemDefinition;
import net.runelite.cache.definitions.loaders.ItemLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.ArchiveFiles;
import net.runelite.cache.fs.FSFile;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

// ItemManager.load() throws "duplicate file ids" on this live cache (a pre-existing
// reference-table corruption in the item config archive, unrelated to the shop bug) --
// this rebuilds the same file list manually, keeping only the first occurrence of each
// duplicated id, so individual item names can still be looked up.
public class DumpItemNames {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Index index = store.getIndex(IndexType.CONFIGS);
            Archive archive = index.getArchive(ConfigType.ITEM.getId());
            byte[] archiveData = storage.loadArchive(archive);
            byte[] decompressed = archive.decompress(archiveData);

            ArchiveFiles files = new ArchiveFiles();
            Set<Integer> seen = new HashSet<>();
            for (FileData fd : archive.getFileData()) {
                if (!seen.add(fd.getId())) {
                    continue;
                }
                FSFile f = new FSFile(fd.getId());
                f.setNameHash(fd.getNameHash());
                files.addFile(f);
            }
            files.loadContents(decompressed);

            ItemLoader loader = new ItemLoader();
            for (int i = 1; i < args.length; i++) {
                int id = Integer.parseInt(args[i]);
                FSFile f = files.findFile(id);
                if (f == null) {
                    System.out.println(id + " -> <no file>");
                    continue;
                }
                ItemDefinition def = loader.load(id, f.getContents());
                System.out.println(id + " -> \"" + def.name + "\"");
            }
        }
    }
}
