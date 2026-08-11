import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.NpcDefinition;
import net.runelite.cache.definitions.loaders.NpcLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;

import java.io.File;
import java.util.List;

// Read-only: same idea as LookupItemNames but for the NPC config archive.
// Usage:
//   lookup <cachePath> <id1,id2,...>
//   scan   <cachePath> <substring>
public class LookupNpcNames {
    public static void main(String[] args) throws Exception {
        String mode = args[0];
        String cachePath = args[1];

        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Index index = store.getIndex(IndexType.CONFIGS);
            Archive archive = index.getArchive(ConfigType.NPC.getId());

            byte[] compressed = storage.loadArchive(archive);
            byte[] decompressed = archive.decompress(compressed);
            FileData[] fileData = archive.getFileData();
            List<byte[]> fileContents = SpliceItemOption.splitChunks(decompressed, fileData.length);

            NpcLoader loader = new NpcLoader();

            if (mode.equals("lookup")) {
                String[] idsStr = args[2].split(",");
                for (String idStr : idsStr) {
                    int targetId = Integer.parseInt(idStr.trim());
                    boolean found = false;
                    for (int i = 0; i < fileData.length; i++) {
                        if (fileData[i].getId() == targetId) {
                            found = true;
                            try {
                                NpcDefinition def = loader.load(targetId, fileContents.get(i));
                                System.out.println("id=" + targetId + " slot=" + i + " name=\"" + def.name
                                        + "\" models=" + java.util.Arrays.toString(def.models)
                                        + " size=" + def.size + " widthScale=" + def.widthScale + " heightScale=" + def.heightScale
                                        + " standingAnimation=" + def.standingAnimation + " walkingAnimation=" + def.walkingAnimation
                                        + " isFollower=" + def.isFollower + " actions=" + java.util.Arrays.toString(def.actions));
                            } catch (Exception e) {
                                System.out.println("id=" + targetId + " slot=" + i + " -- FAILED TO DECODE: " + e);
                            }
                        }
                    }
                    if (!found) {
                        System.out.println("id=" + targetId + " -- NOT FOUND in archive (no file slot with this id)");
                    }
                }
            } else if (mode.equals("scan")) {
                String needle = args[2].toLowerCase();
                int matches = 0;
                for (int i = 0; i < fileData.length; i++) {
                    int id = fileData[i].getId();
                    try {
                        NpcDefinition def = loader.load(id, fileContents.get(i));
                        if (def.name != null && def.name.toLowerCase().contains(needle)) {
                            System.out.println("id=" + id + " slot=" + i + " name=\"" + def.name + "\"");
                            matches++;
                        }
                    } catch (Exception e) {
                        // skip broken/duplicate slots
                    }
                }
                System.out.println("Scan complete. " + matches + " match(es) for \"" + needle + "\".");
            }
        }
    }
}
