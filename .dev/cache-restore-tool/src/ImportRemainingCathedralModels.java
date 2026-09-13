import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Store;
import net.runelite.cache.fs.Storage;

import java.io.File;

// Read source + Zelus. Fixes a real bug in the first ImportCathedralObjects.java pass: its
// MODELS_TO_IMPORT list only contained the 5 REMAPPED (colliding) model ids, on the wrong
// assumption that BulkCollisionCheck's "free" meant "already present" (it means the opposite --
// available to import). The 109 non-colliding models these already-imported 117 object records
// reference were consequently never written, confirmed via CheckModelsExist. The object records
// themselves are already correct (they were patched against the intended target ids, which for
// these 109 equal the source id) -- this only needs to backfill the missing model archives
// themselves, 1:1, no remap, no object/sequence re-touch.
//
// mode: verify | apply
public class ImportRemainingCathedralModels {
    static final int[] MODELS = {
            58532, 58596, 58598, 58599, 58600, 58601, 58602, 58603, 58604,
            61615, 61616, 61617, 61618, 61619, 61620, 61621, 61622, 61623, 61624,
            61625, 61626, 61627, 61628, 61629, 61630, 61631, 61632, 61633, 61634,
            61635, 61636, 61637, 61638, 61639, 61640, 61641, 61642, 61643, 61644,
            61645, 61646, 61647, 61648, 61649, 61650, 61651, 61652, 61653, 61654,
            61655, 61656, 61657, 61658, 61659, 61660, 61661, 61662, 61663, 61664,
            61665, 61666, 61667, 61668, 61669, 61670, 61671, 61672, 61673, 61674,
            61675, 61676, 61677, 61678, 61679, 61680, 61681, 61682, 61683, 61684,
            61713, 61714, 61715, 61716, 61717, 61718, 61719, 61720, 61721, 61722,
            61723, 61724, 61725, 61726, 61727, 61728, 61729, 61730, 61731,
            61735, 61736, 61737, 61738, 61739, 61740, 61741, 61742, 61748, 61754, 61852,
    };

    public static void main(String[] args) throws Exception {
        String mode = args[0];
        String srcPath = args[1];
        String dstPath = args[2];
        boolean apply = mode.equals("apply");
        if (!apply && !mode.equals("verify")) throw new IllegalArgumentException("mode must be verify or apply");

        try (Store src = new Store(new File(srcPath)); Store dst = new Store(new File(dstPath))) {
            src.load();
            dst.load();

            boolean ok = PackMadAngel.verifySingle(dst, IndexType.MODELS, MODELS);
            System.out.println(ok ? "VERIFY: all target model ids free." : "VERIFY: FAILED -- see above.");
            if (!apply) {
                System.out.println("(dry run -- pass 'apply' to write)");
                return;
            }
            if (!ok) {
                System.out.println("ABORTING apply: verify failed above. Nothing written.");
                return;
            }

            int[][] pairs = new int[MODELS.length][2];
            for (int i = 0; i < MODELS.length; i++) pairs[i] = new int[]{MODELS[i], MODELS[i]};

            Storage srcStorage = src.getStorage();
            Storage dstStorage = dst.getStorage();
            PackMadAngel.applySingle(srcStorage, src.getIndex(IndexType.MODELS), dstStorage, dst.getIndex(IndexType.MODELS), pairs);
            System.out.println("DONE. " + MODELS.length + " models imported.");
        }
    }
}
