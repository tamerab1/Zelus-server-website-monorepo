import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;

import java.util.HashMap;
import java.util.Map;
import java.io.File;

// Read source + Zelus. The 117 Cathedral objects already imported include 4 (62263, 62265, 62372,
// 62374 -- likely door/candle open-closed or lit-unlit multi-state variants) that reference 4
// SIBLING loc ids via opcode 77/92 (multi-loc/varbit variant lists) which the map's own loc-PLACED
// object scan (DecodeCathedralLocs) never saw, since these siblings are referenced from WITHIN an
// object's definition, not placed directly on the map. Same crash class as the 117: any client
// subsystem that resolves a multi-loc's variant list and looks up ITS composition would hit an
// unknown id. Found via CheckMultiLocRefs; none of the 4 siblings have further multi-loc chains
// (verified). 3 of their 4 referenced models collide with unrelated Zelus content (byte-different,
// same as the earlier 5) and are remapped to 70006-70008; the 4th (61758) is free, 1:1.
//
// mode: verify | apply
public class ImportCathedralMultiLocSiblings {
    static final int[] OBJECT_IDS = {62264, 62266, 62373, 62375};

    static final Map<Integer, Integer> MODEL_REMAP = new HashMap<>();
    static {
        MODEL_REMAP.put(819, 70006);
        MODEL_REMAP.put(1032, 70007);
        MODEL_REMAP.put(9237, 70008);
        MODEL_REMAP.put(61758, 61758); // free, 1:1
    }
    static final int[] MODELS_TO_IMPORT = {819, 1032, 9237, 61758};

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

            boolean ok = true;
            for (int m : MODELS_TO_IMPORT) {
                int target = MODEL_REMAP.get(m);
                boolean free = dst.getIndex(IndexType.MODELS).getArchive(target) == null;
                System.out.println("model target " + target + " (from source " + m + "): " + (free ? "free" : "OCCUPIED -- CONFLICT"));
                ok &= free;
            }
            ok &= PackMadAngel.verifySingleTarget(dst, ConfigType.OBJECT.getId(), OBJECT_IDS);

            System.out.println(ok ? "VERIFY: all target ids free." : "VERIFY: FAILED -- see above.");
            if (!apply) {
                System.out.println("(dry run -- pass 'apply' to write)");
                return;
            }
            if (!ok) {
                System.out.println("ABORTING apply: verify failed above. Nothing written.");
                return;
            }

            System.out.println("=== APPLY: models ===");
            int[][] modelPairs = new int[MODELS_TO_IMPORT.length][2];
            for (int i = 0; i < MODELS_TO_IMPORT.length; i++) {
                modelPairs[i] = new int[]{MODELS_TO_IMPORT[i], MODEL_REMAP.get(MODELS_TO_IMPORT[i])};
            }
            PackMadAngel.applySingle(srcStorage, src.getIndex(IndexType.MODELS), dstStorage, dst.getIndex(IndexType.MODELS), modelPairs);

            System.out.println("=== APPLY: sibling object definitions (4), model refs patched ===");
            int[][] objectPairs = new int[OBJECT_IDS.length][2];
            for (int i = 0; i < OBJECT_IDS.length; i++) objectPairs[i] = new int[]{OBJECT_IDS[i], OBJECT_IDS[i]};
            // Swap in this class's own remap tables (819/1032/9237/61758) for the shared patcher --
            // see ImportCathedralObjects.activeModelRemap's javadoc.
            ImportCathedralObjects.activeModelRemap = MODEL_REMAP;
            ImportCathedralObjects.activeAnimRemap = new HashMap<>(); // none of these 4 have anim refs
            PackMadAngel.applyConfig(srcStorage, src.getIndex(IndexType.CONFIGS), dstStorage, dst.getIndex(IndexType.CONFIGS),
                    ConfigType.OBJECT.getId(), objectPairs, ImportCathedralObjects::patchObjectRefs);

            System.out.println("=== DONE. ===");
        }
    }
}
