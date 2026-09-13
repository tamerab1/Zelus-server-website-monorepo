import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Store;

import java.io.File;

// Read-only. Second pass: frame archives (ANIMATIONS), the shared skeleton (SKELETONS), and the
// extra vfx models referenced by Mad Angel's spotanims (not her npc def).
public class CollisionCheck2 {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Index animations = store.getIndex(IndexType.ANIMATIONS);
            Index skeletons = store.getIndex(IndexType.SKELETONS);
            Index models = store.getIndex(IndexType.MODELS);

            System.out.println("--- Frame archives (ANIMATIONS) ---");
            for (int id : new int[]{14542, 14543, 14544, 14545, 14546, 14549, 14550, 14551, 14552,
                    14553, 14554, 14555, 14556, 14557, 14560}) {
                Archive a = animations.getArchive(id);
                System.out.println("frame archive " + id + ": " + (a == null ? "FREE" : "OCCUPIED"));
            }

            System.out.println("--- Skeleton (SKELETONS) ---");
            Archive skel = skeletons.getArchive(2675);
            System.out.println("skeleton 2675: " + (skel == null ? "FREE" : "OCCUPIED"));

            System.out.println("--- Extra VFX models (MODELS) ---");
            for (int id : new int[]{61842, 60639, 48289, 60632}) {
                Archive a = models.getArchive(id);
                System.out.println("model " + id + ": " + (a == null ? "FREE" : "OCCUPIED"));
            }
        }
    }
}
