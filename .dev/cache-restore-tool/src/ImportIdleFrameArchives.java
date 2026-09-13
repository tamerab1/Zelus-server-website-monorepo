import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import java.io.File;

// Read source + Zelus. Imports frame archives 14558 (referenced by seq 14453, her idle/hover for
// the fighting+rising forms) and 14559 (referenced by seq 14454, idle/hover for dormant+dead) --
// both missed by the original PackMadAngel pass, which only pulled the 15 frame archives her
// explicit combat-script animate() calls use. Both are free in Zelus (verified via
// CheckFrameArchives) so this is a straight 1:1 copy, reusing PackMadAngel's own proven
// applySingle byte-for-byte copy logic.
public class ImportIdleFrameArchives {
    public static void main(String[] args) throws Exception {
        String mode = args[0];
        String srcPath = args[1];
        String dstPath = args[2];
        boolean apply = mode.equals("apply");
        if (!apply && !mode.equals("verify")) throw new IllegalArgumentException("mode must be verify or apply");

        try (Store src = new Store(new File(srcPath)); Store dst = new Store(new File(dstPath))) {
            src.load();
            dst.load();
            boolean ok = PackMadAngel.verifySingle(dst, IndexType.ANIMATIONS, new int[]{14558, 14559});
            System.out.println(ok ? "VERIFY: both frame archive ids still free." : "VERIFY: FAILED.");
            if (!apply) { System.out.println("(dry run)"); return; }
            if (!ok) { System.out.println("ABORTING."); return; }

            Storage srcStorage = src.getStorage();
            Storage dstStorage = dst.getStorage();
            PackMadAngel.applySingle(srcStorage, src.getIndex(IndexType.ANIMATIONS), dstStorage, dst.getIndex(IndexType.ANIMATIONS),
                    new int[][]{{14558, 14558}, {14559, 14559}});
            System.out.println("DONE.");
        }
    }
}
