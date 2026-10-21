import net.runelite.cache.IndexType;
import net.runelite.cache.fs.*;

import java.io.File;

// js5crc crash, 2026-10-21: JS5 group ids are 16-bit, so model archives 65536 and 70001-70010 are requested as models
// 0 and 4465-4474 (id & 0xffff) -- real, different models -> 4 crc mismatches -> error_game_js5crc. Nothing references
// these 11 models (server LocType / NPCType / ObjType decode and a raw scan of spotanims and interfaces, 2026-10-21), but
// the client's background download walks EVERY archive in the reference table, so ~15 minutes after a fresh cache it
// reached them and crashed (two players at once after the v1.1.109 deploy wipe; "idling at ::sz" in September).
// Fix: drop them from the models reference table so no client ever requests them. Their bytes stay in dat2, unused.
// Refuses any id <= 65535. Usage: verify|apply <cachePath>
public class RemoveUnreachableModels {
    static final int[] IDS = {65536, 70001, 70002, 70003, 70004, 70005, 70006, 70007, 70008, 70009, 70010};

    public static void main(String[] args) throws Exception {
        boolean apply = args[0].equals("apply");
        int before;
        try (Store store = new Store(new File(args[1]))) {
            store.load();
            Index models = store.getIndex(IndexType.MODELS);
            before = models.getArchives().size();
            int found = 0;
            for (int id : IDS) {
                BuildSanctumMap.check(id > 65535, "refusing to remove model " + id + ": it is reachable over JS5");
                Archive a = models.getArchive(id);
                if (a == null) { System.out.println("model " + id + ": not listed"); continue; }
                found++;
                System.out.println("model " + id + ": listed (crc " + Integer.toHexString(a.getCrc()) + "), requested as " + (id & 0xffff) + " -> remove");
                if (apply) BuildSanctumMap.check(models.removeArchive(a), "remove " + id);
            }
            if (!apply) { System.out.println("VERIFY OK -- would remove " + found + " of " + before + " archives, nothing written."); return; }
            ImportCathedralMap.writeIndexReferenceTable(store.getStorage(), models);
        }
        try (Store store = new Store(new File(args[1]))) {
            store.load();
            Index models = store.getIndex(IndexType.MODELS);
            for (Archive a : models.getArchives())
                BuildSanctumMap.check(a.getArchiveId() <= 65535, "model " + a.getArchiveId() + " still listed");
            System.out.println("APPLY complete: models " + before + " -> " + models.getArchives().size() + ", none above 65535.");
        }
    }
}
