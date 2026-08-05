import net.runelite.cache.fs.*;
import java.io.File;
import java.util.Arrays;

// Run this against the LIVE cache immediately after any `build_cache` regen.
// build_cache rebuilds the whole binary cache purely from the toml source tree,
// which silently discards anything that only ever existed as a direct binary
// patch (custom NPCs/objects/items/placeholders added this way, and any sprite
// archive tool-cache-packer.exe mis-packs -- it has a reproducible bug that
// writes empty stubs for a specific handful of sprite ids on every run).
//
// This tool diffs every archive in every index against a known-good "golden"
// snapshot and, for each archive:
//   - present in golden but missing in the live regen  -> copied in from golden
//   - present in both but byte-different                -> overwritten from golden
//   - present in live only (genuinely new toml content)  -> left alone
//
// Rule of thumb: golden always wins on conflict. If you intentionally change
// existing content in toml and want the regen's new version to stick, refresh
// the golden snapshot afterwards (copy the live cache over cache_golden) --
// otherwise this script will keep reverting it to the old golden version.
public class SafeCacheRestore {
    public static void main(String[] args) throws Exception {
        String livePath = args[0];
        String goldenPath = args[1];

        Store live = new Store(new File(livePath));
        live.load();
        Store golden = new Store(new File(goldenPath));
        golden.load();

        int totalAdded = 0, totalFixed = 0;

        for (int indexId = 0; indexId <= 23; indexId++) {
            Index liveIdx, goldenIdx;
            try { liveIdx = live.findIndex(indexId); } catch (Exception e) { continue; }
            try { goldenIdx = golden.findIndex(indexId); } catch (Exception e) { continue; }
            if (liveIdx == null || goldenIdx == null) continue;

            java.util.TreeSet<Integer> allIds = new java.util.TreeSet<>();
            for (Archive a : liveIdx.getArchives()) allIds.add(a.getArchiveId());
            for (Archive a : goldenIdx.getArchives()) allIds.add(a.getArchiveId());
            boolean indexChanged = false;

            for (int id : allIds) {
                Archive goldenArc = goldenIdx.getArchive(id);
                if (goldenArc == null) continue; // nothing golden has to offer for this id

                Archive liveArc = liveIdx.getArchive(id);
                byte[] goldenRaw = golden.getStorage().loadArchive(goldenArc);

                if (liveArc == null) {
                    // missing entirely in the fresh regen -- add it back
                    Archive newArc = liveIdx.addArchive(id);
                    copyArchiveMeta(newArc, goldenArc);
                    live.getStorage().store(indexId, id, goldenRaw);
                    totalAdded++;
                    indexChanged = true;
                    System.out.println("index " + indexId + " archive " + id + " -> ADDED (missing from regen)");
                    continue;
                }

                byte[] liveRaw = live.getStorage().loadArchive(liveArc);
                if (!Arrays.equals(liveRaw, goldenRaw)) {
                    copyArchiveMeta(liveArc, goldenArc);
                    live.getStorage().store(indexId, id, goldenRaw);
                    totalFixed++;
                    indexChanged = true;
                    System.out.println("index " + indexId + " archive " + id + " -> RESTORED (differed from golden)");
                }
            }

            if (indexChanged) {
                net.runelite.cache.index.IndexData indexData = liveIdx.toIndexData();
                byte[] raw = indexData.writeIndexData();
                Container container = new Container(liveIdx.getCompression(), -1);
                container.compress(raw, null);
                live.getStorage().store(255, liveIdx.getId(), container.data);
                liveIdx.setCrc(container.crc);
            }
        }

        System.out.println("Done. Added " + totalAdded + " missing archives, restored " + totalFixed + " differing archives.");
    }

    static void copyArchiveMeta(Archive dest, Archive src) throws Exception {
        dest.setNameHash(src.getNameHash());
        dest.setCrc(src.getCrc());
        dest.setRevision(src.getRevision());
        dest.setCompressedSize(src.getCompressedSize());
        dest.setDecompressedSize(src.getDecompressedSize());
        dest.setCompression(src.getCompression());
        dest.setFileData(src.getFileData());
    }
}
