import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;

import java.io.File;

// Read source + Zelus. Imports the single missing "Exit"/"Quick-exit" Church pew object (62251) --
// confirmed via DecodeExitPew.java to reference model 61680 only (no anim), the SAME model 62250
// ("Climb" pew) already uses and which ImportCathedralObjects.java already imported into Zelus --
// so this needs no new model/anim import, just the one object record, patched through the same
// remap table (identity for 61680) as every other Cathedral object for consistency.
//
// mode: verify | apply
public class ImportExitPew {
    static final int OBJECT_ID = 62251;

    public static void main(String[] args) throws Exception {
        String mode = args[0];
        String srcPath = args[1];
        String dstPath = args[2];
        boolean apply = mode.equals("apply");
        if (!apply && !mode.equals("verify")) throw new IllegalArgumentException("mode must be verify or apply");

        try (Store src = new Store(new File(srcPath)); Store dst = new Store(new File(dstPath))) {
            src.load();
            dst.load();
            Storage srcStorage = src.getStorage();
            Storage dstStorage = dst.getStorage();

            boolean modelFree = dst.getIndex(IndexType.MODELS).getArchive(61680) != null;
            System.out.println("model 61680 present in Zelus: " + modelFree + " (must be true -- already imported by ImportCathedralObjects)");

            Index dstConfigs = dst.getIndex(IndexType.CONFIGS);
            Archive dstObjArchive = dstConfigs.getArchive(ConfigType.OBJECT.getId());
            boolean occupied = false;
            for (FileData fd : dstObjArchive.getFileData()) if (fd.getId() == OBJECT_ID) occupied = true;
            System.out.println("object target " + OBJECT_ID + ": " + (occupied ? "OCCUPIED -- CONFLICT" : "free"));

            boolean ok = modelFree && !occupied;
            System.out.println(ok ? "VERIFY: ok." : "VERIFY: FAILED -- see above.");
            if (!apply) {
                System.out.println("(dry run -- pass 'apply' to write)");
                return;
            }
            if (!ok) {
                System.out.println("ABORTING apply: verify failed above. Nothing written.");
                return;
            }

            System.out.println("=== APPLY: object 62251 (Exit/Quick-exit pew) ===");
            int[][] objectPairs = {{OBJECT_ID, OBJECT_ID}};
            PackMadAngel.applyConfig(srcStorage, src.getIndex(IndexType.CONFIGS), dstStorage, dstConfigs,
                    ConfigType.OBJECT.getId(), objectPairs, ImportCathedralObjects::patchObjectRefs);

            System.out.println("=== DONE. ===");
        }
    }
}
