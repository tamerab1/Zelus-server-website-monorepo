import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;

import java.io.File;

// Read-only. Mad Angel's npc DEFINITIONS (16305/16307 readyAnim=walkAnim=4588; 16306/16308
// readyAnim=walkAnim=1991) reference these two sequence ids directly, unremapped from source --
// missed entirely during the original pack pass, which only remapped the 4 ids MadAngel.java's
// own animate() calls use. Checking whether 4588/1991 are free or already occupied by unrelated
// Zelus content (same collision-check pattern as CollisionCheck.java).
public class CheckIdleWalkAnimIds {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Index configs = store.getIndex(IndexType.CONFIGS);
            Archive archive = configs.getArchive(ConfigType.SEQUENCE.getId());
            byte[] decompressed = archive.decompress(storage.loadArchive(archive));
            FileData[] fileData = archive.getFileData();
            java.util.List<byte[]> fileContents = SpliceItemOption.splitChunks(decompressed, fileData.length);

            int maxKnownId = -1;
            for (FileData fd : fileData) maxKnownId = Math.max(maxKnownId, fd.getId());

            for (int id : new int[]{4588, 1991}) {
                int slot = -1;
                for (int j = 0; j < fileData.length; j++) if (fileData[j].getId() == id) slot = j;
                boolean occupied = slot != -1;
                System.out.println("seq " + id + ": " + (occupied ? "OCCUPIED (" + fileContents.get(slot).length + " bytes)" : "free")
                        + (id > maxKnownId ? "  [beyond current max id " + maxKnownId + "]" : ""));
            }
        }
    }
}
