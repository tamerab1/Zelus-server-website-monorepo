import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Container;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;

import java.io.File;
import java.util.Map;
import java.util.LinkedHashMap;

// Read + write Zelus cache only (no source needed -- these models are already correctly imported,
// just sitting at ids that don't fit a NARROW (2-byte) opcode 1/5 model reference). The 8 models
// originally remapped to 70001-70008 (because they collided with unrelated pre-existing content)
// need to move to ids under 65536, because the fix for the client's ::madangel crash (see
// ScanClientUnsafeOpcodes.java's findings) requires downgrading every Cathedral object's wide
// opcode 6/7 model references to narrow opcode 1/5 -- which physically cannot encode an id above
// 65535. New targets 65510-65517 verified free via CheckModelsExist.
//
// mode: verify | apply
public class RelocateModelsSub65535 {
    static final Map<Integer, Integer> RELOCATE = new LinkedHashMap<>();
    static {
        RELOCATE.put(70001, 65510); // was source 3509
        RELOCATE.put(70002, 65511); // was source 9235
        RELOCATE.put(70003, 65512); // was source 12415
        RELOCATE.put(70004, 65513); // was source 18579
        RELOCATE.put(70005, 65514); // was source 22838
        RELOCATE.put(70006, 65515); // was source 819
        RELOCATE.put(70007, 65516); // was source 1032
        RELOCATE.put(70008, 65517); // was source 9237
    }

    public static void main(String[] args) throws Exception {
        String mode = args[0];
        String cachePath = args[1];
        boolean apply = mode.equals("apply");
        if (!apply && !mode.equals("verify")) throw new IllegalArgumentException("mode must be verify or apply");

        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Index models = store.getIndex(IndexType.MODELS);

            boolean ok = true;
            for (int target : RELOCATE.values()) {
                boolean free = models.getArchive(target) == null;
                System.out.println("target " + target + ": " + (free ? "free" : "OCCUPIED -- CONFLICT"));
                ok &= free;
            }
            System.out.println(ok ? "VERIFY: all targets free." : "VERIFY: FAILED.");
            if (!apply) { System.out.println("(dry run)"); return; }
            if (!ok) { System.out.println("ABORTING."); return; }

            for (Map.Entry<Integer, Integer> e : RELOCATE.entrySet()) {
                int oldId = e.getKey(), newId = e.getValue();
                Archive oldArchive = models.getArchive(oldId);
                byte[] rawContainer = storage.loadArchive(oldArchive);

                Archive newArchive = models.addArchive(newId);
                newArchive.setNameHash(oldArchive.getNameHash());
                newArchive.setCompression(oldArchive.getCompression());
                newArchive.setRevision(1);
                newArchive.setCrc(oldArchive.getCrc());
                newArchive.setCompressedSize(oldArchive.getCompressedSize());
                newArchive.setDecompressedSize(oldArchive.getDecompressedSize());
                FileData[] oldFileData = oldArchive.getFileData();
                FileData[] newFileData = new FileData[oldFileData.length];
                for (int i = 0; i < oldFileData.length; i++) {
                    FileData fd = new FileData();
                    fd.setId(oldFileData[i].getId());
                    fd.setNameHash(oldFileData[i].getNameHash());
                    newFileData[i] = fd;
                }
                newArchive.setFileData(newFileData);
                storage.store(models.getId(), newId, rawContainer);
                System.out.println("  " + oldId + " -> " + newId + ": " + rawContainer.length + " bytes copied");
            }

            IndexData_writeBack(storage, models);
            System.out.println("DONE.");
        }
    }

    static void IndexData_writeBack(Storage storage, Index index) throws Exception {
        var indexData = index.toIndexData();
        byte[] rawIndex = indexData.writeIndexData();
        Container idxContainer = new Container(index.getCompression(), -1);
        idxContainer.compress(rawIndex, null);
        storage.store(255, index.getId(), idxContainer.data);
        index.setCrc(idxContainer.crc);
    }
}
