import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.ArchiveFiles;
import net.runelite.cache.fs.FSFile;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;

import java.io.File;

// Read-only. Reports whether each target id is currently occupied in the given cache, for
// npc/sequence/spotanim (all inside IndexType.CONFIGS, different archives) and models (their own
// top-level index, one archive per model id -- see DumpModelInfo's same pattern).
public class CollisionCheck {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Index configs = store.getIndex(IndexType.CONFIGS);
            Index models = store.getIndex(IndexType.MODELS);

            System.out.println("--- NPC ids (config archive " + ConfigType.NPC.getId() + ") ---");
            checkConfigArchive(storage, configs, ConfigType.NPC.getId(), new int[]{16305, 16306, 16307, 16308});

            System.out.println("--- Sequence ids (config archive " + ConfigType.SEQUENCE.getId() + ") ---");
            checkConfigArchive(storage, configs, ConfigType.SEQUENCE.getId(), new int[]{
                    832, 3321, 4589, 4590, 8543, 14443, 14448,
                    14429, 14430, 14431, 14432, 14433, 14434, 14435, 14436,
                    14437, 14438, 14439, 14440, 14441, 14442
            });

            System.out.println("--- Spotanim ids (config archive " + ConfigType.SPOTANIM.getId() + ") ---");
            checkConfigArchive(storage, configs, ConfigType.SPOTANIM.getId(), new int[]{
                    4010, 4011, 4012, 4013, 4014, 4015, 4016, 4017, 1448, 2184
            });

            System.out.println("--- Model ids (IndexType.MODELS) ---");
            for (int id : new int[]{61845, 61847, 61786}) {
                Archive a = models.getArchive(id);
                if (a == null) {
                    System.out.println("model " + id + ": FREE");
                } else {
                    byte[] data = a.decompress(storage.loadArchive(a));
                    System.out.println("model " + id + ": OCCUPIED (" + data.length + " raw bytes)");
                }
            }
        }
    }

    private static void checkConfigArchive(Storage storage, Index configs, int archiveId, int[] ids) throws Exception {
        // Uses the same (proven-working, on both caches) pattern as VerifyNpc.java --
        // decompress once, split by FileData -- rather than Archive#getFiles, which throws
        // "duplicate file ids" on Zelus's own npc archive (a real quirk in that archive, not a
        // bug in this checker; VerifyNpc's approach sidesteps it entirely).
        Archive archive = configs.getArchive(archiveId);
        byte[] decompressed = archive.decompress(storage.loadArchive(archive));
        FileData[] fileData = archive.getFileData();
        java.util.List<byte[]> fileContents = SpliceItemOption.splitChunks(decompressed, fileData.length);

        int maxKnownId = -1;
        for (FileData fd : fileData) maxKnownId = Math.max(maxKnownId, fd.getId());

        for (int id : ids) {
            int slot = -1;
            for (int j = 0; j < fileData.length; j++) if (fileData[j].getId() == id) slot = j;
            boolean occupied = slot != -1;
            System.out.println("  id " + id + ": " + (occupied ? "OCCUPIED (" + fileContents.get(slot).length + " bytes)" : "free")
                    + (id > maxKnownId ? "  [beyond current max id " + maxKnownId + "]" : ""));
        }
    }
}
