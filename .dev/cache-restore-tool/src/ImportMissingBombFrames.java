import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Container;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;
import net.runelite.cache.index.IndexData;

import java.io.File;

// Read source cache, write Zelus cache. Second root-cause fix for the bomb special's missing GFX:
// importing the 4 missing sequences (14444-14447, see ImportMissingBombSeqs) was necessary but not
// sufficient -- each of those sequences' own frame data (opcode 1's third array, the real frame
// archive id, NOT the small per-frame index numbers in the second array that an earlier version of
// DumpSeqFrameGroups was misreading) points at a frame archive that was ALSO never imported:
//   14444 (GFX_BOMB_THROW's anim) -> frame archive 14523
//   14445 (GFX_BOMB_TRAVEL's anim) -> frame archive 14526
//   14446 (unused spotanim's anim) -> frame archive 14524
//   14447 (GFX_BOMB_HIT/REFLECT's anim) -> frame archive 14525
// None of these fall inside PackMadAngel's own FRAME_ARCHIVES list (14542-14560) -- they are a
// separate, never-imported range entirely. Confirmed free in Zelus and present with real content,
// byte-identical, in the source cache before writing. Same verify-then-apply safety pattern as
// every other cache write this session.
//
// mode: verify | apply
public class ImportMissingBombFrames {

    static final int[][] FRAMES = {
            {14523, 14523}, {14524, 14524}, {14525, 14525}, {14526, 14526},
    };

    public static void main(String[] args) throws Exception {
        String mode = args[0];
        String srcPath = args[1];
        String dstPath = args[2];
        boolean apply = mode.equals("apply");
        if (!apply && !mode.equals("verify")) {
            throw new IllegalArgumentException("mode must be verify or apply");
        }

        try (Store src = new Store(new File(srcPath)); Store dst = new Store(new File(dstPath))) {
            src.load();
            dst.load();
            Storage srcStorage = src.getStorage();
            Storage dstStorage = dst.getStorage();

            Index srcIndex = src.getIndex(IndexType.ANIMATIONS);
            Index dstIndex = dst.getIndex(IndexType.ANIMATIONS);

            boolean ok = true;
            for (int[] pair : FRAMES) {
                boolean srcPresent = srcIndex.getArchive(pair[0]) != null;
                boolean dstFree = dstIndex.getArchive(pair[1]) == null;
                System.out.println("frame " + pair[0] + " -> " + pair[1] + ": source=" + (srcPresent ? "present" : "MISSING")
                        + " dest=" + (dstFree ? "free" : "OCCUPIED -- CONFLICT"));
                ok &= srcPresent && dstFree;
            }
            System.out.println(ok ? "VERIFY: all good." : "VERIFY: FAILED -- see above.");
            if (!apply) {
                System.out.println("(dry run -- pass 'apply' to write)");
                return;
            }
            if (!ok) {
                System.out.println("ABORTING apply: verify failed above. Nothing written.");
                return;
            }

            for (int[] pair : FRAMES) {
                int srcId = pair[0], dstId = pair[1];
                Archive srcArchive = srcIndex.getArchive(srcId);
                if (srcArchive == null) {
                    throw new IllegalStateException("source frame archive " + srcId + " not found -- aborting mid-write.");
                }
                if (dstIndex.getArchive(dstId) != null) {
                    throw new IllegalStateException("target frame archive " + dstId + " now occupied -- changed since verify, aborting mid-write.");
                }

                byte[] rawContainer = srcStorage.loadArchive(srcArchive);
                Container c = Container.decompress(rawContainer, null);

                Archive dstArchive = dstIndex.addArchive(dstId);
                dstArchive.setNameHash(-1);
                dstArchive.setCompression(srcArchive.getCompression());
                dstArchive.setRevision(1);
                dstArchive.setCrc(c.crc);
                dstArchive.setCompressedSize(rawContainer.length);
                dstArchive.setDecompressedSize(c.data.length);

                FileData[] srcFileData = srcArchive.getFileData();
                FileData[] dstFileData = new FileData[srcFileData.length];
                for (int i = 0; i < srcFileData.length; i++) {
                    FileData fd = new FileData();
                    fd.setId(srcFileData[i].getId());
                    fd.setNameHash(-1);
                    dstFileData[i] = fd;
                }
                dstArchive.setFileData(dstFileData);

                dstStorage.store(dstIndex.getId(), dstId, rawContainer);
                System.out.println("  " + srcId + " -> " + dstId + ": " + rawContainer.length
                        + " bytes written (" + srcFileData.length + " sub-file(s))");
            }

            IndexData indexData = dstIndex.toIndexData();
            byte[] rawIndex = indexData.writeIndexData();
            Container idxContainer = new Container(dstIndex.getCompression(), -1);
            idxContainer.compress(rawIndex, null);
            dstStorage.store(255, dstIndex.getId(), idxContainer.data);
            dstIndex.setCrc(idxContainer.crc);

            System.out.println("=== DONE. All writes complete. ===");
        }
    }
}
